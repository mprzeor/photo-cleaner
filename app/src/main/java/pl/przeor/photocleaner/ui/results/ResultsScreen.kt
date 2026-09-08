package pl.przeor.photocleaner.ui.results

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.przeor.photocleaner.analysis.DuplicateGroup
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.analysis.MatchSettings
import pl.przeor.photocleaner.ui.common.formatBytes

/**
 * Identifies the group shown in the fullscreen viewer by its member photo keys,
 * so the viewer survives the group being re-built (and re-keyed) after one of
 * its photos is moved to the Trash.
 */
private class ViewerState(val groupKeys: Set<String>, val initialKey: String)

private val ViewerStateSaver = Saver<ViewerState?, ArrayList<String>>(
    save = { vs ->
        if (vs == null) ArrayList()
        else ArrayList<String>(vs.groupKeys.size + 1).apply {
            add(vs.initialKey)
            addAll(vs.groupKeys)
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) null else ViewerState(saved.drop(1).toSet(), saved.first())
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: ResultsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val computing by vm.computing.collectAsStateWithLifecycle()
    val totalPhotos by vm.totalPhotos.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var viewerState by rememberSaveable(stateSaver = ViewerStateSaver) {
        mutableStateOf<ViewerState?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // System dialog asking the user to confirm moving the photo to the Trash.
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onTrashResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        vm.trashRequests.collect { sender ->
            trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    var photoAwaitingPermission by remember { mutableStateOf<ImageFeatures?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val photo = photoAwaitingPermission
        photoAwaitingPermission = null
        if (granted && photo != null) vm.requestTrash(photo) else vm.notifyTrashPermissionDenied()
    }

    val onMoveToTrash: (ImageFeatures) -> Unit = { photo ->
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            vm.requestTrash(photo)
        } else {
            photoAwaitingPermission = photo
            permissionLauncher.launch(permission)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Matching rules")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SummaryBar(groups, settings, totalPhotos, onOpenFilters = { showFilters = true })
            if (computing) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (groups.isEmpty()) {
                if (!computing) {
                    EmptyState(
                        totalPhotos = totalPhotos,
                        onOpenFilters = { showFilters = true },
                        onBack = onBack,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            onOpen = { photo ->
                                viewerState = ViewerState(
                                    groupKeys = group.items.mapTo(HashSet()) { it.key },
                                    initialKey = photo.key,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    viewerState?.let { vs ->
        // Groups are re-keyed by their best photo after every recompute, so the
        // viewed group is found by membership: any photo the viewer was showing.
        val group = groups.firstOrNull { g -> g.items.any { it.key in vs.groupKeys } }
        if (group == null) {
            // The whole group disappeared (trashed or filtered away) – close the viewer.
            viewerState = null
        } else {
            val currentKeys = group.items.mapTo(HashSet()) { it.key }
            if (currentKeys != vs.groupKeys) {
                // Keep the membership snapshot fresh so the next deletion can
                // still locate the group.
                SideEffect { viewerState = ViewerState(currentKeys, vs.initialKey) }
            }
            PhotoViewer(
                group = group,
                initialKey = vs.initialKey,
                snackbarHostState = snackbarHostState,
                onMoveToTrash = onMoveToTrash,
                onDismiss = { viewerState = null },
            )
        }
    }
    }

    if (showFilters) {
        FilterSheet(
            settings = settings,
            onChange = vm::updateSettings,
            onReset = vm::resetSettings,
            onDismiss = { showFilters = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryBar(
    groups: List<DuplicateGroup>,
    settings: MatchSettings,
    totalPhotos: Int,
    onOpenFilters: () -> Unit,
) {
    val duplicatePhotos = groups.sumOf { it.items.size }
    val reclaimable = groups.sumOf { it.reclaimableBytes }
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${groups.size} groups · $duplicatePhotos photos · ${formatBytes(reclaimable)} reclaimable",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Scanned $totalPhotos photos. Tap a photo to view it fullscreen and " +
                    "move duplicates to the Trash.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                settings.summaryChips().forEach { label ->
                    AssistChip(onClick = onOpenFilters, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(totalPhotos: Int, onOpenFilters: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (totalPhotos == 0) {
            Text("No scan results yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Go back, pick a folder and start a scan.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back to scan") }
        } else {
            Text("No duplicates with the current rules", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Try the Loose preset, raise the distance thresholds, or turn off the GPS/time constraints.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenFilters) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adjust rules")
            }
        }
    }
}
