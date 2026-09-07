package com.vidbox.domain.model

/** How the app's own traffic leaves the device. A proxy is not a VPN and is never presented as one. */
enum class NetworkRoute { DIRECT, PROXY }

enum class ProxyType { HTTP, HTTPS, SOCKS5 }

/**
 * User proxy configuration. Credentials are never part of this object's `toString`, logs, or
 * plain DataStore rows: they are stored through the platform keystore and only attached at
 * connection time.
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
) {
    val hasCredentials: Boolean get() = !username.isNullOrEmpty()

    /** Address without secrets, suitable for screens and logs. */
    val endpoint: String get() = if (host.isBlank()) "" else "${type.name.lowercase()}://$host:$port"

    override fun toString(): String = "ProxyConfig(enabled=$enabled, type=$type, host=$host, port=$port, credentials=${hasCredentials})"

    companion object {
        /** Structural validation only; reachability is a separate, real connection test. */
        fun validate(type: ProxyType, host: String, port: Int): String? {
            val trimmed = host.trim()
            if (trimmed.isEmpty()) return "Enter the proxy host name or IP address."
            if (trimmed.length > 253 || trimmed.any { it.isWhitespace() || it == '/' || it == '@' || it == '#' || it == '?' }) return "The proxy host is not a valid host name."
            if (trimmed.contains("://")) return "Enter only the host, without a scheme like http://."
            val bracketed = trimmed.startsWith("[") && trimmed.endsWith("]")
            if (!bracketed && trimmed.contains(':') && trimmed.count { it == ':' } == 1) return "Enter the port in its own field."
            if (port !in 1..65535) return "The port must be between 1 and 65535."
            if (type == ProxyType.HTTPS && trimmed.all { it.isDigit() || it == '.' }) return "An HTTPS proxy needs a host name that its certificate covers."
            return null
        }
    }
}

/** Outcome of an actual connection through the proxy; never inferred from configuration alone. */
sealed interface ProxyTestResult {
    data class Success(val latencyMillis: Long, val proxiedFrom: String?) : ProxyTestResult
    data class Failure(val error: DownloadError) : ProxyTestResult
}

/** Live proxy state shown in the UI. "Connected" is only reported after a successful test. */
data class ProxyStatus(
    val route: NetworkRoute = NetworkRoute.DIRECT,
    val config: ProxyConfig = ProxyConfig(),
    val lastTest: ProxyTestResult? = null,
    val testing: Boolean = false,
    /** Whether the system WebView honours the override (androidx.webkit PROXY_OVERRIDE). */
    val webViewSupported: Boolean = true,
    val webViewApplied: Boolean = false,
)
