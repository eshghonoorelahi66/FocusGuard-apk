package com.focusguard.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.focusguard.app.engine.BlockEngine
import com.focusguard.app.engine.Notifs

class AppContainer(ctx: Context) {
    val db = AppDb.get(ctx)
    val prefs = Prefs(ctx)
}

class FocusApp : Application() {
    companion object {
        lateinit var instance: FocusApp
            private set
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        AppCompatDelegate.setApplicationLocales(container.prefs.localeList())
        BlockEngine.boot(this)
        Notifs.ensureChannels(this)
        UsageSyncWorker.schedule(this)
        Scheduler.rescheduleAll(this)
    }
}
