package pl.przeor.photocleaner.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przeor.photocleaner.processing.ScanState
import pl.przeor.photocleaner.ui.common.folderLabel
import pl.przeor.photocleaner.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onShowResults: () -> Unit) {
    val context = LocalContext.current
    val folder by vm.folder.collectAsStateWithLifecycle()
    val recursive by vm.recursive.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val cacheCount by vm.cacheCount.collectAsStateWithLifecycle()
    val running = scanState is ScanState.Running

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.onFolderPicked(uri)
        }
    }

    // Jump to results exactly once when a scan we watched finishes.
    var wasRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scanState) {
        when (scanState) {
            is ScanState.Running -> wasRunning = true
            is ScanState.Done -> if (wasRunning) {
                wasRunning = false
                onShowResults()
            }
            else -> wasRunning = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Photo Cleaner") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Folder ------------------------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Folder to scan", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = folder?.let { folderLabel(it) } ?: "No folder selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Include subfolders",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = recursive, onCheckedChange = vm::setRecursive, enabled = !running)
                    }
                    OutlinedButton(
                        onClick = { pickFolder.launch(folder) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                    ) {
                        Text(if (folder == null) "Choose folder" else "Change folder")
                    }
                }
            }

            // --- Start -------------------------------------------------------------
            Button(
                onClick = vm::startScan,
                enabled = folder != null && !running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start scan")
            }

            // --- Status ------------------------------------------------------------
            when (val s = scanState) {
                is ScanState.Running -> ProgressCard(s, onCancel = vm::cancelScan)
                is ScanState.Done -> DoneCard(s, onShowResults)
                is ScanState.Error -> ErrorCard(s.message)
                ScanState.Idle -> HowItWorksCard()
            }

            Spacer(Modifier.weight(1f))

            // --- Cache -------------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cache: $cacheCount photos analysed",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = vm::clearCache, enabled = !running && cacheCount > 0) {
                    Text("Clear cache")
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(s: ScanState.Running, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Analysing photos…", style = MaterialTheme.typography.titleMedium)
            if (s.total == 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { s.done / s.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.percent}%", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (s.total == 0) "" else "${s.done} / ${s.total}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                s.currentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${s.cached} from cache · ${s.failed} unreadable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun DoneCard(s: ScanState.Done, onShowResults: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan complete", style = MaterialTheme.typography.titleMedium)
            Text(
                "${s.total} photos in ${formatDuration(s.durationMs)} · ${s.cached} from cache" +
                    if (s.failed > 0) " · ${s.failed} unreadable" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onShowResults, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View duplicates")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Scan failed", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleSmall)
            Text(
                "Each photo is analysed once: perceptual hashes (pHash, dHash, aHash), a colour " +
                    "histogram, GPS position and capture time. Results are cached, so the next scan " +
                    "only touches new or changed files.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Which algorithms are used and how strict they are can be changed on the results " +
                    "screen — groups update instantly without rescanning.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
