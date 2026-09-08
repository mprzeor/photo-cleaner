package pl.przeor.photocleaner.data.prefs

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import pl.przeor.photocleaner.analysis.MatchSettings

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("photocleaner_prefs", Context.MODE_PRIVATE)

    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(value) = prefs.edit { putString(KEY_FOLDER, value?.toString()) }

    var recursive: Boolean
        get() = prefs.getBoolean(KEY_RECURSIVE, true)
        set(value) = prefs.edit { putBoolean(KEY_RECURSIVE, value) }

    fun loadMatchSettings(): MatchSettings {
        val d = MatchSettings()
        return MatchSettings(
            pHashEnabled = prefs.getBoolean("phash_on", d.pHashEnabled),
            pHashMaxDistance = prefs.getInt("phash_max", d.pHashMaxDistance),
            dHashEnabled = prefs.getBoolean("dhash_on", d.dHashEnabled),
            dHashMaxDistance = prefs.getInt("dhash_max", d.dHashMaxDistance),
            aHashEnabled = prefs.getBoolean("ahash_on", d.aHashEnabled),
            aHashMaxDistance = prefs.getInt("ahash_max", d.aHashMaxDistance),
            histogramEnabled = prefs.getBoolean("hist_on", d.histogramEnabled),
            histogramMinSimilarity = prefs.getFloat("hist_min", d.histogramMinSimilarity),
            requireAllVisual = prefs.getBoolean("require_all", d.requireAllVisual),
            geoEnabled = prefs.getBoolean("geo_on", d.geoEnabled),
            geoMaxDistanceMeters = prefs.getInt("geo_max_m", d.geoMaxDistanceMeters),
            geoSkipIfMissing = prefs.getBoolean("geo_skip_missing", d.geoSkipIfMissing),
            timeEnabled = prefs.getBoolean("time_on", d.timeEnabled),
            timeMaxMinutes = prefs.getInt("time_max_min", d.timeMaxMinutes),
        )
    }

    fun saveMatchSettings(s: MatchSettings) = prefs.edit {
        putBoolean("phash_on", s.pHashEnabled)
        putInt("phash_max", s.pHashMaxDistance)
        putBoolean("dhash_on", s.dHashEnabled)
        putInt("dhash_max", s.dHashMaxDistance)
        putBoolean("ahash_on", s.aHashEnabled)
        putInt("ahash_max", s.aHashMaxDistance)
        putBoolean("hist_on", s.histogramEnabled)
        putFloat("hist_min", s.histogramMinSimilarity)
        putBoolean("require_all", s.requireAllVisual)
        putBoolean("geo_on", s.geoEnabled)
        putInt("geo_max_m", s.geoMaxDistanceMeters)
        putBoolean("geo_skip_missing", s.geoSkipIfMissing)
        putBoolean("time_on", s.timeEnabled)
        putInt("time_max_min", s.timeMaxMinutes)
    }

    private companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_RECURSIVE = "recursive"
    }
}
