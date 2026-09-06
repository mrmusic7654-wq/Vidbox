package com.vidbox.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase

/**
 * Engine-level browsing data clearing shared by the browser menu and Settings.
 * Only Vidbox's own WebView profile is touched; never other apps or system state.
 */
object BrowsingData {
    fun clear(context: Context) {
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).apply {
            clearFormData()
            clearHttpAuthUsernamePassword()
        }
    }
}
