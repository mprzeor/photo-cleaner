package pl.przeor.photocleaner.processing

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import pl.przeor.photocleaner.analysis.FeatureExtractor
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.data.db.ImageFeatureDao
import pl.przeor.photocleaner.data.db.ImageFeatureEntity
import pl.przeor.photocleaner.data.media.FolderScanner
import pl.przeor.photocleaner.data.media.SystemTrash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

sealed interface ScanState {
    data object Idle : ScanState

    data class Running(
        val total: Int,
        val done: Int,
        val cached: Int,
        val failed: Int,
        val currentName: String,
    ) : ScanState {
        val percent: Int get() = if (total == 0) 0 else done * 100 / total
    }

    data class Done(
        val features: List<ImageFeatures>,
        val total: Int,
        val cached: Int,
        val failed: Int,
        val durationMs: Long,
    ) : ScanState

    data class Error(val message: String) : ScanState
}

/**
 * Application-scoped so a scan keeps running while the user navigates between screens.
 *
 * Flow: list files → look up cache by (key, size, lastModified) → extract features for
 * misses on N worker coroutines → write to Room in batches → publish [ScanState.Done]
 * with the feature list the results screen groups against.
 */
class ScanEngine(
    private val context: Context,
    private val dao: ImageFeatureDao,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start(treeUri: Uri, recursive: Boolean) {
        if (isRunning) return
        job = scope.launch {
            try {
                runScan(treeUri, recursive)
            } catch (e: CancellationException) {
                _state.value = ScanState.Idle
                throw e
            } catch (t: Throwable) {
                _state.value = ScanState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (!isRunning) _state.value = ScanState.Idle
    }

    /** Called after the user deletes photos: drop them from the current result and the cache. */
    suspend fun removeFeatures(keys: Set<String>) {
        if (keys.isEmpty()) return
        _state.update { s ->
            if (s is ScanState.Done) s.copy(features = s.features.filterNot { it.key in keys }) else s
        }
        keys.chunked(400).forEach { dao.deleteByKeys(it) }
    }

    private suspend fun runScan(treeUri: Uri, recursive: Boolean) {
        val startedAt = SystemClock.elapsedRealtime()
        _state.value = ScanState.Running(0, 0, 0, 0, "Listing files…")

        val entries = withContext(Dispatchers.IO) {
            val listed = FolderScanner(context.contentResolver).listImages(treeUri, recursive)
            // Cross-check against MediaStore: files sitting in the system Trash
            // must never show up as duplicates, however the provider names them.
            val trashedPaths = SystemTrash.trashedImagePaths(context.contentResolver)
            if (trashedPaths.isEmpty()) {
                listed
            } else {
                listed.filterNot { e ->
                    val path = SystemTrash.docIdToAbsolutePath(e.key.substringAfter('|'))
                    path != null && path in trashedPaths
                }
            }
        }

        // Cache lookup ---------------------------------------------------------------
        val cached = dao.getAll().associateBy { it.key }
        val results = arrayOfNulls<ImageFeatureEntity>(entries.size)
        val toProcess = ArrayList<Int>()
        var cachedCount = 0
        entries.forEachIndexed { i, e ->
            val hit = cached[e.key]
            if (hit != null && hit.lastModified == e.lastModified && hit.size == e.size) {
                // Re-point to a URI that is valid for the current folder grant.
                results[i] = hit.copy(uri = e.uri.toString())
                cachedCount++
            } else {
                toProcess.add(i)
            }
        }

        val done = AtomicInteger(cachedCount)
        val failed = AtomicInteger(0)
        fun publish(current: String) {
            _state.value = ScanState.Running(entries.size, done.get(), cachedCount, failed.get(), current)
        }
        publish(if (toProcess.isEmpty()) "" else entries[toProcess[0]].name)

        // Parallel extraction ----------------------------------------------------------
        if (toProcess.isNotEmpty()) {
            val extractor = FeatureExtractor(context.contentResolver)
            val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

            val work = Channel<Int>(Channel.UNLIMITED).apply {
                toProcess.forEach { trySend(it) }
                close()
            }
            val toWrite = Channel<ImageFeatureEntity>(Channel.UNLIMITED)

            coroutineScope {
                val writer = launch(Dispatchers.IO) {
                    val batch = ArrayList<ImageFeatureEntity>(BATCH_SIZE)
                    for (item in toWrite) {
                        batch.add(item)
                        if (batch.size >= BATCH_SIZE) {
                            dao.upsertAll(batch)
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) dao.upsertAll(batch)
                }

                coroutineScope {
                    repeat(workerCount) {
                        launch(Dispatchers.Default) {
                            for (i in work) {
                                val entry = entries[i]
                                val entity = try {
                                    extractor.extract(entry)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) { // includes OutOfMemoryError on odd files
                                    null
                                } ?: ImageFeatureEntity.failed(entry)

                                if (entity.failed) failed.incrementAndGet()
                                results[i] = entity
                                toWrite.send(entity)
                                done.incrementAndGet()
                                publish(entry.name)
                            }
                        }
                    }
                }
                toWrite.close()
                writer.join()
            }
        }

        val features = results.filterNotNull().filterNot { it.failed }.map { it.toDomain() }
        _state.value = ScanState.Done(
            features = features,
            total = entries.size,
            cached = cachedCount,
            failed = failed.get(),
            durationMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private companion object {
        const val BATCH_SIZE = 32
    }
}
