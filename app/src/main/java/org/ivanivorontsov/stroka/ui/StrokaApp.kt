package org.ivanivorontsov.stroka.ui

import org.ivanivorontsov.stroka.build.PonyatnyJs

import android.app.Application

class StrokaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        System.setProperty("user.home", filesDir.absolutePath)
        Prefs(this).applyTheme()
        PonyatnyJs.appContext = this
    }
}
