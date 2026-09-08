package pl.przeor.photocleaner

import android.app.Application
import pl.przeor.photocleaner.di.AppContainer

class PhotoCleanerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
