package com.vidbox

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class VidboxTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, VidboxTestApp_Application::class.java.name, context)
}
