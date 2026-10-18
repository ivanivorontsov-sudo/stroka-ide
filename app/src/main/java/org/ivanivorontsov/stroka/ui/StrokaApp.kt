package org.ivanivorontsov.stroka.ui

import android.app.Application

class StrokaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        System.setProperty("user.home", filesDir.absolutePath)
        Prefs(this).applyTheme()
    }
}
