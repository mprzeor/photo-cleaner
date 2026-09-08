package pl.przeor.photocleaner.analysis

import kotlin.math.roundToInt

/**
 * User-tunable rules for deciding whether two photos are duplicates.
 *
 * All values here are applied at *matching* time against cached features, so the
 * user can change them freely on the results screen without a rescan.
 */
data class MatchSettings(
    // --- Visual similarity algorithms -------------------------------------
    val pHashEnabled: Boolean = true,
    /** Max Hamming distance (0..64) between 64-bit perceptual hashes. */
    val pHashMaxDistance: Int = 12,

    val dHashEnabled: Boolean = true,
    val dHashMaxDistance: Int = 12,

    val aHashEnabled: Boolean = false,
    val aHashMaxDistance: Int = 8,

    val histogramEnabled: Boolean = false,
    /** Minimum histogram intersection (0..1). */
    val histogramMinSimilarity: Float = 0.85f,

    /** true = every enabled visual algorithm must agree; false = any one is enough. */
    val requireAllVisual: Boolean = false,

    // --- Location ----------------------------------------------------------
    val geoEnabled: Boolean = false,
    val geoMaxDistanceMeters: Int = 100,
    /** When a photo has no GPS data, skip the check instead of rejecting the pair. */
    val geoSkipIfMissing: Boolean = true,

    // --- Time --------------------------------------------------------------
    val timeEnabled: Boolean = false,
    val timeMaxMinutes: Int = 10,
) {
    val enabledVisualCount: Int
        get() = listOf(pHashEnabled, dHashEnabled, aHashEnabled, histogramEnabled).count { it }

    val anyVisualEnabled: Boolean get() = enabledVisualCount > 0

    /** The visual part of the settings, used to detect which preset (if any) is active. */
    fun visualSignature(): List<Any> = listOf(
        pHashEnabled, pHashMaxDistance,
        dHashEnabled, dHashMaxDistance,
        aHashEnabled, aHashMaxDistance,
        histogramEnabled, histogramMinSimilarity,
        requireAllVisual,
    )

    /** Short human-readable labels for the active rules (shown as chips on the results screen). */
    fun summaryChips(): List<String> = buildList {
        if (pHashEnabled) add("pHash ≤ $pHashMaxDistance")
        if (dHashEnabled) add("dHash ≤ $dHashMaxDistance")
        if (aHashEnabled) add("aHash ≤ $aHashMaxDistance")
        if (histogramEnabled) add("Colour ≥ ${(histogramMinSimilarity * 100).roundToInt()}%")
        if (requireAllVisual && enabledVisualCount > 1) add("All must agree")
        if (geoEnabled) add("GPS ≤ " + if (geoMaxDistanceMeters >= 1000) "${geoMaxDistanceMeters / 1000} km" else "$geoMaxDistanceMeters m")
        if (timeEnabled) add("Time ≤ " + if (timeMaxMinutes >= 60) "${timeMaxMinutes / 60} h" else "$timeMaxMinutes min")
        if (!anyVisualEnabled) add("No visual algorithm enabled")
    }

    companion object {
        val GEO_PRESETS_METERS = listOf(10, 25, 50, 100, 250, 500, 1000, 2000, 5000)
        val TIME_PRESETS_MINUTES = listOf(1, 2, 5, 10, 30, 60, 180, 720, 1440)
    }
}

/** One-tap bundles of visual settings. Location/time rules are left untouched. */
enum class Preset(val label: String, val description: String) {
    STRICT("Strict", "Near-identical shots only"),
    BALANCED("Balanced", "Typical burst / retake duplicates"),
    LOOSE("Loose", "Same scene, different framing");

    fun apply(base: MatchSettings): MatchSettings = when (this) {
        STRICT -> base.copy(
            pHashEnabled = true, pHashMaxDistance = 6,
            dHashEnabled = true, dHashMaxDistance = 6,
            aHashEnabled = false,
            histogramEnabled = false,
            requireAllVisual = true,
        )
        BALANCED -> base.copy(
            pHashEnabled = true, pHashMaxDistance = 12,
            dHashEnabled = true, dHashMaxDistance = 12,
            aHashEnabled = false,
            histogramEnabled = false,
            requireAllVisual = false,
        )
        LOOSE -> base.copy(
            pHashEnabled = true, pHashMaxDistance = 16,
            dHashEnabled = true, dHashMaxDistance = 16,
            aHashEnabled = true, aHashMaxDistance = 12,
            histogramEnabled = true, histogramMinSimilarity = 0.80f,
            requireAllVisual = false,
        )
    }

    fun isActive(settings: MatchSettings): Boolean =
        apply(settings).visualSignature() == settings.visualSignature()
}
