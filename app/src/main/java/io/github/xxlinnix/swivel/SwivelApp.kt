package io.github.xxlinnix.swivel

import android.app.Application

class SwivelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
