package com.vidbox.data.util

import android.util.Log
import com.vidbox.domain.repository.EventLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidEventLogger @Inject constructor() : EventLogger {
    private val safe = Regex("[A-Za-z0-9_.:-]{1,96}")
    override fun event(name: String, id: String?, fields: Map<String, String>) {
        if (!safe.matches(name)) return
        val body = buildString {
            append("event=").append(name)
            id?.takeIf(safe::matches)?.let { append(" id=").append(it) }
            fields.entries.filter { safe.matches(it.key) && safe.matches(it.value) }.forEach {
                append(' ').append(it.key).append('=').append(it.value)
            }
        }
        if (name.endsWith("failed")) Log.w("Vidbox", body) else Log.d("Vidbox", body)
    }
}
