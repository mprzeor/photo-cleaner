package pl.przeor.photocleaner.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.przeor.photocleaner.analysis.MatchSettings
import pl.przeor.photocleaner.analysis.Preset
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    settings: MatchSettings,
    onChange: (MatchSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Matching rules", style = MaterialTheme.typography.titleLarge)
            Text(
                "Changes apply instantly to the cached analysis — no rescan needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Presets -----------------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preset.entries.forEach { preset ->
                    FilterChip(
                        selected = preset.isActive(settings),
                        onClick = { onChange(preset.apply(settings)) },
                        label = { Text(preset.label) },
                    )
                }
            }

            // --- Visual algorithms -------------------------------------------------
            SectionHeader("Visual similarity")

            AlgorithmCard(
                title = "pHash — perceptual hash",
                description = "DCT of a 32×32 grayscale copy. Robust to resizing, compression and small edits. Best all-round choice.",
                enabled = settings.pHashEnabled,
                onEnabled = { onChange(settings.copy(pHashEnabled = it)) },
            ) {
                IntSlider(
                    label = "Max Hamming distance",
                    value = settings.pHashMaxDistance,
                    range = 0..24,
                    onChange = { onChange(settings.copy(pHashMaxDistance = it)) },
                )
            }

            AlgorithmCard(
                title = "dHash — difference hash",
                description = "Compares neighbouring pixel gradients in a 9×8 copy. Fast and structure-sensitive; excellent for burst shots.",
                enabled = settings.dHashEnabled,
                onEnabled = { onChange(settings.copy(dHashEnabled = it)) },
            ) {
                IntSlider(
                    label = "Max Hamming distance",
                    value = settings.dHashMaxDistance,
                    range = 0..24,
                    onChange = { onChange(settings.copy(dHashMaxDistance = it)) },
                )
            }

            AlgorithmCard(
                title = "aHash — average hash",
                description = "Each pixel of an 8×8 copy vs. the mean brightness. Simple and loose — finds more, including false positives.",
                enabled = settings.aHashEnabled,
                onEnabled = { onChange(settings.copy(aHashEnabled = it)) },
            ) {
                IntSlider(
                    label = "Max Hamming distance",
                    value = settings.aHashMaxDistance,
                    range = 0..24,
                    onChange = { onChange(settings.copy(aHashMaxDistance = it)) },
                )
            }

            AlgorithmCard(
                title = "Colour histogram",
                description = "HSV colour distribution (128 bins). Catches the same scene with different framing; combine with a hash to limit false positives.",
                enabled = settings.histogramEnabled,
                onEnabled = { onChange(settings.copy(histogramEnabled = it)) },
            ) {
                IntSlider(
                    label = "Min similarity",
                    value = (settings.histogramMinSimilarity * 100).roundToInt(),
                    range = 50..100,
                    onChange = { onChange(settings.copy(histogramMinSimilarity = it / 100f)) },
                    valueLabel = { "$it%" },
                )
            }

            SwitchRow(
                title = "Require all enabled algorithms to agree",
                description = "Off: any single algorithm can link two photos (finds more). On: stricter, fewer false positives.",
                checked = settings.requireAllVisual,
                enabled = settings.enabledVisualCount > 1,
                onChange = { onChange(settings.copy(requireAllVisual = it)) },
            )

            // --- Location ------------------------------------------------------------
            SectionHeader("Location")

            AlgorithmCard(
                title = "GPS proximity",
                description = "Only link photos whose EXIF positions are within this distance of each other.",
                enabled = settings.geoEnabled,
                onEnabled = { onChange(settings.copy(geoEnabled = it)) },
            ) {
                PresetSlider(
                    label = "Max distance",
                    presets = MatchSettings.GEO_PRESETS_METERS,
                    value = settings.geoMaxDistanceMeters,
                    format = { if (it >= 1000) "${it / 1000} km" else "$it m" },
                    onChange = { onChange(settings.copy(geoMaxDistanceMeters = it)) },
                )
                SwitchRow(
                    title = "Skip check when a photo has no GPS",
                    description = "Off: photos without location data never match.",
                    checked = settings.geoSkipIfMissing,
                    onChange = { onChange(settings.copy(geoSkipIfMissing = it)) },
                )
            }

            // --- Time ----------------------------------------------------------------
            SectionHeader("Time")

            AlgorithmCard(
                title = "Taken close in time",
                description = "Only link photos taken within this window (EXIF date, or file date if missing). Also makes matching much faster on big libraries.",
                enabled = settings.timeEnabled,
                onEnabled = { onChange(settings.copy(timeEnabled = it)) },
            ) {
                PresetSlider(
                    label = "Max gap",
                    presets = MatchSettings.TIME_PRESETS_MINUTES,
                    value = settings.timeMaxMinutes,
                    format = { if (it >= 60) "${it / 60} h" else "$it min" },
                    onChange = { onChange(settings.copy(timeMaxMinutes = it)) },
                )
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                Text("Reset to defaults")
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun AlgorithmCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            if (enabled) {
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    valueLabel: (Int) -> String = { it.toString() },
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

/** Slider over a fixed list of sensible values (e.g. 10 m … 5 km) rather than a linear range. */
@Composable
private fun PresetSlider(
    label: String,
    presets: List<Int>,
    value: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val index = presets.indexOf(value).let { found ->
        if (found >= 0) found else presets.indices.minByOrNull { abs(presets[it] - value) } ?: 0
    }
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(presets[it.roundToInt().coerceIn(0, presets.lastIndex)]) },
            valueRange = 0f..presets.lastIndex.toFloat(),
            steps = (presets.size - 2).coerceAtLeast(0),
        )
    }
}
