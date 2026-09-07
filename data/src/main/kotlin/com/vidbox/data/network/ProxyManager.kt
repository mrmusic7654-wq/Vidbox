package com.vidbox.data.network

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.util.ErrorMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Authenticator as JavaAuthenticator
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes the app's own network stack through the configured proxy.
 *
 * Scope, stated plainly: this covers OkHttp (analysis probes, direct downloads, filter-list
 * updates), the yt-dlp engine (`--proxy`), and the in-app WebView when the system WebView
 * supports proxy override (applied by the app layer through androidx.webkit). It is not a
 * VPN: other apps and system traffic are unaffected, and a proxy only sees the connections
 * this app makes through it.
 *
 * The shared OkHttpClient consults this object as its [ProxySelector], so a settings change
 * takes effect for the next connection without rebuilding clients; pooled connections are
 * keyed by route and are therefore never reused across a proxy change.
 */
@Singleton
class ProxyManager @Inject constructor(
    settings: SettingsRepository,
    private val logger: EventLogger,
) : ProxySelector() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableStatus = MutableStateFlow(ProxyStatus())
    val status: StateFlow<ProxyStatus> = mutableStatus.asStateFlow()
    @Volatile private var current: ProxyConfig? = null
    @Volatile private var socksAuthenticatorInstalled = false

    /** The active configuration, or null when traffic goes direct. */
    val active: ProxyConfig? get() = current

    init {
        scope.launch {
            settings.settings.map { it.proxy }.distinctUntilChanged().collect { config -> apply(config) }
        }
    }

    // ProxySelector -------------------------------------------------------------------------

    override fun select(uri: URI?): List<Proxy> {
        val config = current ?: return listOf(Proxy.NO_PROXY)
        return listOf(javaProxy(config))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        if (current != null) logger.event("proxy.connect_failed", "network", mapOf("reason" to (ioe?.javaClass?.simpleName ?: "unknown")))
    }

    /** Answers HTTP proxy challenges once per request; a second 407 surfaces as PROXY_AUTH. */
    val authenticator: Authenticator = Authenticator { _, response ->
        val config = current
        if (config == null || !config.hasCredentials || config.type == ProxyType.SOCKS5) null
        else if (response.request.header("Proxy-Authorization") != null) null
        else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(config.username.orEmpty(), config.password.orEmpty())).build()
    }

    /** Turns proxy-hop connection failures into [ProxyUnavailableException] so error mapping is exact. */
    val interceptor: Interceptor = Interceptor { chain ->
        try {
            chain.proceed(chain.request())
        } catch (error: IOException) {
            if (current != null && isProxyHopFailure(error)) throw ProxyUnavailableException(error) else throw error
        }
    }

    // Engine and WebView --------------------------------------------------------------------

    /** yt-dlp `--proxy` value, or null for direct. Credentials are embedded because the engine has no other channel. */
    fun engineProxyUrl(): String? {
        val config = current ?: return null
        val scheme = when (config.type) { ProxyType.HTTP -> "http"; ProxyType.HTTPS -> "https"; ProxyType.SOCKS5 -> "socks5h" }
        val auth = if (config.hasCredentials) {
            val user = java.net.URLEncoder.encode(config.username.orEmpty(), "UTF-8")
            val pass = java.net.URLEncoder.encode(config.password.orEmpty(), "UTF-8")
            "$user:$pass@"
        } else ""
        return "$scheme://$auth${config.host.trim()}:${config.port}"
    }

    /** Called by the app layer after applying (or failing to apply) the WebView override. */
    fun webViewState(supported: Boolean, applied: Boolean) {
        mutableStatus.update { it.copy(webViewSupported = supported, webViewApplied = applied) }
    }

    // Connection test -----------------------------------------------------------------------

    /**
     * Performs a real request through [config] and reports the outcome. Success requires a
     * 2xx/3xx answer from the probe endpoint via the proxy; nothing is inferred from settings.
     */
    suspend fun test(config: ProxyConfig = mutableStatus.value.config): ProxyTestResult = withContext(Dispatchers.IO) {
        ProxyConfig.validate(config.type, config.host, config.port)?.let { message ->
            return@withContext ProxyTestResult.Failure(Errors.of(ErrorCode.PROXY_UNAVAILABLE).copy(detail = message))
        }
        mutableStatus.update { it.copy(testing = true) }
        val credential = if (config.hasCredentials && config.type != ProxyType.SOCKS5)
            Credentials.basic(config.username.orEmpty(), config.password.orEmpty()) else null
        val probe = OkHttpClient.Builder()
            .proxy(javaProxy(config))
            .proxyAuthenticator { _, response ->
                if (credential == null || response.request.header("Proxy-Authorization") != null) null
                else response.request.newBuilder().header("Proxy-Authorization", credential).build()
            }
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).build()
        if (config.type == ProxyType.SOCKS5 && config.hasCredentials) installSocksAuthenticator(config)
        val started = System.nanoTime()
        val result = try {
            val request = Request.Builder().url(PROBE_URL).head().header("Cache-Control", "no-cache").build()
            probe.newCall(request).withResponse { response ->
                val mapped = ErrorMapper.httpStatus(response.code, viaProxy = true)
                if (mapped != null) ProxyTestResult.Failure(mapped)
                else ProxyTestResult.Success((System.nanoTime() - started) / 1_000_000, response.header("Via")?.take(80))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            ProxyTestResult.Failure(ErrorMapper.from(error, viaProxy = true))
        } finally {
            probe.dispatcher.executorService.shutdown()
            probe.connectionPool.evictAll()
        }
        mutableStatus.update { it.copy(testing = false, lastTest = if (it.config.endpoint == config.endpoint) result else it.lastTest) }
        logger.event("proxy.tested", "network", mapOf("result" to (if (result is ProxyTestResult.Success) "ok" else (result as ProxyTestResult.Failure).error.code.name)))
        result
    }

    // Internals -----------------------------------------------------------------------------

    private fun apply(config: ProxyConfig) {
        val enabled = config.enabled && ProxyConfig.validate(config.type, config.host, config.port) == null
        current = if (enabled) config else null
        if (enabled && config.type == ProxyType.SOCKS5 && config.hasCredentials) installSocksAuthenticator(config)
        else if (socksAuthenticatorInstalled) { JavaAuthenticator.setDefault(null); socksAuthenticatorInstalled = false }
        mutableStatus.update { previous ->
            val sameEndpoint = previous.config.endpoint == config.endpoint && previous.config.type == config.type
            ProxyStatus(route = if (enabled) NetworkRoute.PROXY else NetworkRoute.DIRECT, config = config,
                // A different endpoint invalidates any earlier result; "connected" must be re-proven.
                lastTest = if (sameEndpoint) previous.lastTest else null,
                testing = previous.testing, webViewSupported = previous.webViewSupported, webViewApplied = false)
        }
        logger.event("proxy.configured", "network", mapOf("route" to (if (enabled) "proxy" else "direct"), "type" to config.type.name))
    }

    private fun javaProxy(config: ProxyConfig): Proxy {
        // Unresolved: the proxy resolves target hosts, and a SOCKS5 proxy receives names (socks5h semantics).
        val address = InetSocketAddress.createUnresolved(config.host.trim(), config.port)
        return Proxy(if (config.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP, address)
    }

    /** java.net SOCKS5 authentication goes through the process-wide Authenticator, scoped to the proxy host. */
    private fun installSocksAuthenticator(config: ProxyConfig) {
        val user = config.username.orEmpty()
        val pass = config.password.orEmpty().toCharArray()
        val host = config.host.trim()
        JavaAuthenticator.setDefault(object : JavaAuthenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? =
                if (requestingHost.equals(host, ignoreCase = true) && requestorType == RequestorType.SERVER)
                    PasswordAuthentication(user, pass) else null
        })
        socksAuthenticatorInstalled = true
    }

    private fun isProxyHopFailure(error: IOException): Boolean {
        if (error is ProxyUnavailableException) return false
        // With a proxy configured, OkHttp only resolves and connects to the proxy itself.
        if (error is ConnectException || error is UnknownHostException) return true
        if (error is SocketTimeoutException) return error.message?.contains("connect", ignoreCase = true) == true
        val message = error.message.orEmpty()
        return message.startsWith("Failed to connect to") || message.contains("SOCKS", ignoreCase = true)
    }

    private companion object {
        /** Tiny, cache-free endpoint that answers HEAD with 204; only reachability is measured. */
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
    }
}
