package pl.przeor.photocleaner.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.przeor.photocleaner.di.AppContainer
import pl.przeor.photocleaner.processing.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.settingsStore
    private val engine = container.scanEngine

    private val _folder = MutableStateFlow(store.folderUri)
    val folder: StateFlow<Uri?> = _folder.asStateFlow()

    private val _recursive = MutableStateFlow(store.recursive)
    val recursive: StateFlow<Boolean> = _recursive.asStateFlow()

    val scanState: StateFlow<ScanState> = engine.state

    val cacheCount: StateFlow<Int> = container.database.imageFeatureDao().count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onFolderPicked(uri: Uri) {
        store.folderUri = uri
        _folder.value = uri
        engine.reset() // previous results belong to another folder
    }

    fun setRecursive(value: Boolean) {
        store.recursive = value
        _recursive.value = value
    }

    fun startScan() {
        val target = _folder.value ?: return
        engine.start(target, _recursive.value)
    }

    fun cancelScan() = engine.cancel()

    fun clearCache() {
        viewModelScope.launch {
            container.database.imageFeatureDao().clear()
            engine.reset()
        }
    }
}
