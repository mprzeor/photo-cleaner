package pl.przeor.photocleaner.ui.results

import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.przeor.photocleaner.analysis.DuplicateGroup
import pl.przeor.photocleaner.analysis.DuplicateGrouper
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.analysis.MatchSettings
import pl.przeor.photocleaner.data.media.SystemTrash
import pl.przeor.photocleaner.di.AppContainer
import pl.przeor.photocleaner.processing.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ResultsViewModel(private val container: AppContainer) : ViewModel() {
    private val engine = container.scanEngine
    private val store = container.settingsStore

    private val _settings = MutableStateFlow(store.loadMatchSettings())
    val settings: StateFlow<MatchSettings> = _settings.asStateFlow()

    private val _computing = MutableStateFlow(false)
    val computing: StateFlow<Boolean> = _computing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** System confirmation dialogs the screen must launch to complete a trash request. */
    private val _trashRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val trashRequests: SharedFlow<IntentSender> = _trashRequests.asSharedFlow()

    /** Photo awaiting the user's answer in the system trash dialog. */
    private var pendingTrashKey: String? = null
    private var pendingTrashUri: Uri? = null

    private val features: Flow<List<ImageFeatures>> = engine.state
        .map { (it as? ScanState.Done)?.features ?: emptyList() }
        .distinctUntilChanged()

    val totalPhotos: StateFlow<Int> = features.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Groups are recomputed whenever the features or the (debounced) settings change.
     * `mapLatest` cancels an in-flight computation if the user keeps dragging a slider.
     */
    val groups: StateFlow<List<DuplicateGroup>> =
        combine(features, _settings.debounce(150)) { f, s -> f to s }
            .mapLatest { (f, s) ->
                _computing.value = true
                val result = withContext(Dispatchers.Default) { DuplicateGrouper.group(f, s) }
                _computing.value = false
                result
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Settings -----------------------------------------------------------------

    fun updateSettings(s: MatchSettings) {
        _settings.value = s
        store.saveMatchSettings(s)
    }

    fun resetSettings() = updateSettings(MatchSettings())

    // --- Trash --------------------------------------------------------------------

    /**
     * Starts moving [photo] to the system Trash: finds the photo in MediaStore and
     * emits the confirmation dialog for the screen to launch. The photo is only
     * removed from the results once the user confirms ([onTrashResult]).
     */
    fun requestTrash(photo: ImageFeatures) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _messages.tryEmit("The system Trash requires Android 11 or newer")
            return
        }
        viewModelScope.launch {
            val mediaUri = withContext(Dispatchers.IO) { resolveMediaStoreUri(photo) }
            if (mediaUri == null) {
                _messages.tryEmit("Could not find this photo in the media library")
                return@launch
            }
            val pending = MediaStore.createTrashRequest(
                container.contentResolver, listOf(mediaUri), true,
            )
            pendingTrashKey = photo.key
            pendingTrashUri = mediaUri
            _trashRequests.emit(pending.intentSender)
        }
    }

    fun onTrashResult(confirmed: Boolean) {
        val key = pendingTrashKey ?: return
        val uri = pendingTrashUri
        pendingTrashKey = null
        pendingTrashUri = null
        viewModelScope.launch {
            // The dialog's result code is not reliable on every OEM, so ask
            // MediaStore whether the photo really is in the Trash now.
            val trashed = withContext(Dispatchers.IO) {
                uri?.let { SystemTrash.isTrashed(container.contentResolver, it) }
            } ?: confirmed
            if (trashed) {
                engine.removeFeatures(setOf(key))
                _messages.tryEmit("Photo moved to Trash")
            } else if (confirmed) {
                _messages.tryEmit("The system did not move the photo to the Trash")
            }
        }
    }

    fun notifyTrashPermissionDenied() {
        _messages.tryEmit("Photo access permission is needed to move photos to the Trash")
    }

    /**
     * The scan works on SAF document URIs, but the Trash only exists in MediaStore.
     * Locate the same file there: first by the absolute path reconstructed from the
     * document id (e.g. "primary:DCIM/x.jpg"), then by name + size as a fallback.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun resolveMediaStoreUri(photo: ImageFeatures): Uri? {
        val resolver = container.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        val path = runCatching {
            val docId = DocumentsContract.getDocumentId(photo.uri)
            val volume = docId.substringBefore(':')
            val relativePath = docId.substringAfter(':', "")
            when (volume) {
                "primary" -> "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                else -> "/storage/$volume/$relativePath"
            }
        }.getOrNull()

        if (path != null) {
            resolver.query(
                collection, projection,
                "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), null,
            )?.use { c ->
                if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }

        resolver.query(
            collection, projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?",
            arrayOf(photo.name, photo.size.toString()), null,
        )?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }
}
