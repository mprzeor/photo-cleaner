package pl.przeor.photocleaner.analysis

import android.net.Uri

/**
 * Everything the matcher needs to know about one photo. Produced once per file
 * (expensive: decode + hash + EXIF) and cached in Room. Matching only reads
 * these values, so changing filters never requires re-processing the image.
 */
class ImageFeatures(
    /** Stable cache key: `<authority>|<documentId>` – survives picking a different parent folder. */
    val key: String,
    /** URI valid for the folder grant of the *current* session. Used for thumbnails / deletion. */
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val width: Int,
    val height: Int,
    val aHash: Long,
    val dHash: Long,
    val pHash: Long,
    /** Normalised HSV histogram, [ImageHasher.HIST_BINS] entries summing to 1. */
    val histogram: FloatArray,
    val latitude: Double?,
    val longitude: Double?,
    /** EXIF DateTimeOriginal in millis, if present. */
    val takenAt: Long?,
) {
    val effectiveTime: Long get() = takenAt ?: lastModified
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val pixels: Long get() = width.toLong() * height
}

/** A set of photos the matcher considers to be the same shot. [items] is sorted best-first. */
class DuplicateGroup(val items: List<ImageFeatures>) {
    val id: String = items.first().key
    val totalBytes: Long = items.sumOf { it.size }
    /** Space freed if everything except the best photo is deleted. */
    val reclaimableBytes: Long = totalBytes - items.first().size
}
