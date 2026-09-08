package pl.przeor.photocleaner.analysis

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import pl.przeor.photocleaner.data.db.ImageFeatureEntity
import pl.przeor.photocleaner.data.db.toByteArray
import pl.przeor.photocleaner.data.media.ExifReader
import pl.przeor.photocleaner.data.media.ImageEntry

/**
 * The expensive per-image step. Decodes the picture at a small size (≤ ~160 px on the long
 * edge, using JPEG's native sub-sampling so it is cheap), computes every hash + the colour
 * histogram, and reads EXIF GPS/time. Returns null when the file cannot be decoded.
 */
class FeatureExtractor(private val resolver: ContentResolver) {

    fun extract(entry: ImageEntry): ImageFeatureEntity? {
        // 1. Read dimensions only.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(entry.uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) } // returns null by design here
        val fullW = bounds.outWidth
        val fullH = bounds.outHeight
        if (fullW <= 0 || fullH <= 0) return null

        // 2. Decode down-sampled (power-of-two) so the long edge is ≤ TARGET_MAX_DIM.
        var sample = 1
        while (maxOf(fullW, fullH) / sample > TARGET_MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(entry.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // 3. Hash + histogram.
        val hashes = try {
            ImageHasher.compute(bitmap)
        } finally {
            bitmap.recycle()
        }

        // 4. Metadata.
        val meta = ExifReader.read(resolver, entry.uri)

        return ImageFeatureEntity(
            key = entry.key,
            uri = entry.uri.toString(),
            name = entry.name,
            size = entry.size,
            lastModified = entry.lastModified,
            width = fullW,
            height = fullH,
            aHash = hashes.aHash,
            dHash = hashes.dHash,
            pHash = hashes.pHash,
            histogram = hashes.histogram.toByteArray(),
            latitude = meta.latitude,
            longitude = meta.longitude,
            takenAt = meta.takenAt,
            failed = false,
        )
    }

    private companion object {
        const val TARGET_MAX_DIM = 160
    }
}
