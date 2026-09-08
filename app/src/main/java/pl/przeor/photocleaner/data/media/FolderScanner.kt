package pl.przeor.photocleaner.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** One image file found inside the selected folder. */
class ImageEntry(
    /** `<authority>|<documentId>` – independent of which parent folder was picked. */
    val key: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
)

/**
 * Walks a folder chosen with ACTION_OPEN_DOCUMENT_TREE using DocumentsContract directly
 * (one query per directory) – far faster than DocumentFile.listFiles() on large trees.
 */
class FolderScanner(private val resolver: ContentResolver) {

    fun listImages(treeUri: Uri, recursive: Boolean): List<ImageEntry> {
        val authority = treeUri.authority ?: "unknown"
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val result = ArrayList<ImageEntry>()
        val pending = ArrayDeque<String>()
        pending.add(rootId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        while (pending.isNotEmpty()) {
            val dirId = pending.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
            val cursor = try {
                resolver.query(childrenUri, projection, null, null, null)
            } catch (e: Exception) {
                null
            } ?: continue

            cursor.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Skip hidden folders such as .thumbnails / .trashed
                        if (recursive && !name.startsWith(".")) pending.add(docId)
                        continue
                    }
                    if (!mime.startsWith("image/")) continue
                    // Skip hidden files: the system Trash keeps photos in place,
                    // renamed to ".trashed-<id>-<name>", so they must not be re-listed.
                    if (name.startsWith(".")) continue
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val lastModified = if (c.isNull(4)) 0L else c.getLong(4)
                    result.add(
                        ImageEntry(
                            key = "$authority|$docId",
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            name = name,
                            size = size,
                            lastModified = lastModified,
                            mimeType = mime,
                        )
                    )
                }
            }
        }
        return result
    }
}
