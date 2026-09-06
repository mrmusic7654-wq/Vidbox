package com.vidbox

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/** Hilt's stock test Application lacks WorkManager configuration; a boot broadcast can arrive before a test rule. */
open class TestApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.ERROR).build()
}

@CustomTestApplication(TestApplication::class)
interface VidboxTestApp
