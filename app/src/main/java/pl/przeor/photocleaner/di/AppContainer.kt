package pl.przeor.photocleaner.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import pl.przeor.photocleaner.data.db.AppDatabase
import pl.przeor.photocleaner.data.prefs.SettingsStore
import pl.przeor.photocleaner.processing.ScanEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; small enough that a DI framework isn't worth it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val contentResolver: ContentResolver get() = appContext.contentResolver

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "photocleaner.db")
        .fallbackToDestructiveMigration()
        .build()

    val settingsStore = SettingsStore(appContext)

    val scanEngine = ScanEngine(appContext, database.imageFeatureDao(), appScope)
}
