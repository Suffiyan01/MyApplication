package com.ridervoice.cockpit

import android.app.Application
import com.ridervoice.cockpit.di.AppContainer
import com.ridervoice.cockpit.di.AppContainerImpl

class RiderVoiceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}
