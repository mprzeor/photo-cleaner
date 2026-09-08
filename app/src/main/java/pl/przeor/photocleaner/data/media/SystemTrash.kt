package pl.przeor.photocleaner.data.media

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore

/** Helpers around the system (MediaStore) Trash that gallery apps show as the bin. */
object SystemTrash {

    private val TRASH_PREFIX = Regex("^\\.?trashed-\\d+-")

    /**
     * Absolute paths of every image currently in the system Trash, plus the path
     * each one had before it was trashed (the OS renames a trashed file in place
     * to ".trashed-<expiry>-<name>", so both spellings are covered no matter how
     * the documents provider reports the name). Best effort: empty when photo
     * access is not granted or the device predates the Trash (Android < 11).
     */
    fun trashedImagePaths(resolver: ContentResolver): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
        val out = HashSet<String>()
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        runCatching {
            resolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.MediaColumns.DATA),
                args,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    out.add(path)
                    val dir = path.substringBeforeLast('/')
                    val file = path.substringAfterLast('/')
                    val original = file.replace(TRASH_PREFIX, "")
                    if (original != file) out.add("$dir/$original")
                }
            }
        }
        return out
    }

    /**
     * Whether the given MediaStore item is currently in the Trash.
     * Null when it cannot be determined (old Android, item gone, no access).
     */
    fun isTrashed(resolver: ContentResolver, mediaUri: Uri): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        return runCatching {
            resolver.query(
                mediaUri,
                arrayOf(MediaStore.MediaColumns.IS_TRASHED),
                args,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getInt(0) == 1 else null }
        }.getOrNull()
    }

    /** Best-effort absolute path for a SAF document id such as "primary:DCIM/x.jpg". */
    fun docIdToAbsolutePath(docId: String): String? = runCatching {
        val volume = docId.substringBefore(':')
        val relativePath = docId.substringAfter(':', "")
        when (volume) {
            "primary" -> "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            else -> "/storage/$volume/$relativePath"
        }
    }.getOrNull()
}
