package pl.przeor.photocleaner.data.media

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ExifReader {
    class Meta(val latitude: Double?, val longitude: Double?, val takenAt: Long?)

    private val EMPTY = Meta(null, null, null)

    fun read(resolver: ContentResolver, uri: Uri): Meta = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val latLong = exif.latLong
            val taken = exif.dateTimeOriginal ?: exif.dateTimeDigitized ?: exif.dateTime
            Meta(latLong?.getOrNull(0), latLong?.getOrNull(1), taken)
        } ?: EMPTY
    } catch (e: Exception) {
        EMPTY
    }
}
