package pl.przeor.photocleaner.ui.common

import android.net.Uri
import android.provider.DocumentsContract
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

fun formatDuration(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> String.format(Locale.getDefault(), "%.1f s", ms / 1000.0)
    else -> "${ms / 60_000} min ${(ms % 60_000) / 1000} s"
}

/** Turns a SAF tree URI such as `primary:DCIM/Camera` into "DCIM/Camera". */
fun folderLabel(uri: Uri): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(uri)
    val volume = id.substringBefore(':')
    val path = id.substringAfter(':', "")
    when {
        path.isBlank() -> if (volume == "primary") "Internal storage" else volume
        volume == "primary" -> path
        else -> "$volume/$path"
    }
}.getOrDefault(uri.toString())
