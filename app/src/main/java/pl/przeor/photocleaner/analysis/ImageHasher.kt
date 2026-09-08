package pl.przeor.photocleaner.analysis

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos

class HashResult(
    val aHash: Long,
    val dHash: Long,
    val pHash: Long,
    val histogram: FloatArray,
)

/**
 * Computes all supported fingerprints of a (small, already down-sampled) bitmap in one pass.
 *
 *  - aHash: 8×8 grayscale, bit = pixel > mean. Simple, loose.
 *  - dHash: 9×8 grayscale, bit = left pixel < right pixel. Encodes gradients; fast and
 *    quite robust for burst shots.
 *  - pHash: 32×32 grayscale → 2-D DCT → top-left 8×8 low frequencies, bit = coeff > median.
 *    Robust to scaling, compression and mild edits. The standard "perceptual hash".
 *  - Colour histogram: 8 hue × 4 saturation × 4 value bins, normalised. Captures the
 *    overall look of a scene regardless of framing.
 */
object ImageHasher {
    private const val HUE_BINS = 8
    private const val SAT_BINS = 4
    private const val VAL_BINS = 4
    const val HIST_BINS = HUE_BINS * SAT_BINS * VAL_BINS

    private const val P_SIZE = 32
    private const val P_LOW = 8

    fun compute(bitmap: Bitmap): HashResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        val hist = FloatArray(HIST_BINS)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            hist[hsvBin(r, g, b)] += 1f
        }
        val inv = 1f / (w * h)
        for (i in hist.indices) hist[i] *= inv

        val g32 = Resampler.areaAverage(gray, w, h, P_SIZE, P_SIZE)
        val g8 = Resampler.areaAverage(g32, P_SIZE, P_SIZE, 8, 8)
        val g9x8 = Resampler.areaAverage(gray, w, h, 9, 8)

        return HashResult(
            aHash = averageHash(g8),
            dHash = differenceHash(g9x8),
            pHash = perceptualHash(g32),
            histogram = hist,
        )
    }

    private fun averageHash(g8: FloatArray): Long {
        var sum = 0f
        for (v in g8) sum += v
        val mean = sum / g8.size
        var hash = 0L
        for (i in 0 until 64) if (g8[i] > mean) hash = hash or (1L shl i)
        return hash
    }

    /** Input is 9 columns × 8 rows. */
    private fun differenceHash(g: FloatArray): Long {
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            val row = y * 9
            for (x in 0 until 8) {
                if (g[row + x] < g[row + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private fun perceptualHash(g32: FloatArray): Long {
        val low = Dct.lowFrequency(g32)
        val sorted = low.copyOf()
        sorted.sort()
        val median = (sorted[31] + sorted[32]) / 2f
        var hash = 0L
        for (i in 0 until 64) if (low[i] > median) hash = hash or (1L shl i)
        return hash
    }

    private fun hsvBin(r: Int, g: Int, b: Int): Int {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val v = max / 255f
        val s = if (max == 0) 0f else delta / max.toFloat()
        var hue = when {
            delta == 0 -> 0f
            max == r -> 60f * (((g - b) / delta.toFloat()) % 6f)
            max == g -> 60f * ((b - r) / delta.toFloat() + 2f)
            else -> 60f * ((r - g) / delta.toFloat() + 4f)
        }
        if (hue < 0f) hue += 360f
        val hb = ((hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
        val sb = (s * SAT_BINS).toInt().coerceIn(0, SAT_BINS - 1)
        val vb = (v * VAL_BINS).toInt().coerceIn(0, VAL_BINS - 1)
        return (hb * SAT_BINS + sb) * VAL_BINS + vb
    }

    /** Fixed 32→8 DCT-II used by pHash. Cosine table is built once. */
    private object Dct {
        private const val N = ImageHasher.P_SIZE
        private const val K = ImageHasher.P_LOW
        private val cosTable: Array<FloatArray> = Array(K) { u ->
            FloatArray(N) { x -> cos((2 * x + 1) * u * PI / (2 * N)).toFloat() }
        }

        /** Returns the K×K low-frequency block of the 2-D DCT of an N×N row-major input. */
        fun lowFrequency(input: FloatArray): FloatArray {
            // Row pass: for each row y keep only the first K frequencies.
            val rows = FloatArray(N * K)
            for (y in 0 until N) {
                val base = y * N
                for (u in 0 until K) {
                    val cu = cosTable[u]
                    var s = 0f
                    for (x in 0 until N) s += input[base + x] * cu[x]
                    rows[y * K + u] = s
                }
            }
            // Column pass.
            val out = FloatArray(K * K)
            for (v in 0 until K) {
                val cv = cosTable[v]
                for (u in 0 until K) {
                    var s = 0f
                    for (y in 0 until N) s += rows[y * K + u] * cv[y]
                    out[v * K + u] = s
                }
            }
            return out
        }
    }
}

/** Box-filter (area averaging) down-sampler. Much better than nearest/bilinear for hashing. */
object Resampler {
    fun areaAverage(src: FloatArray, w: Int, h: Int, tw: Int, th: Int): FloatArray {
        val out = FloatArray(tw * th)
        for (ty in 0 until th) {
            val y0 = ty * h / th
            val y1 = maxOf(y0 + 1, (ty + 1) * h / th).coerceAtMost(h)
            for (tx in 0 until tw) {
                val x0 = tx * w / tw
                val x1 = maxOf(x0 + 1, (tx + 1) * w / tw).coerceAtMost(w)
                var sum = 0f
                var n = 0
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) {
                        sum += src[row + x]
                        n++
                    }
                }
                out[ty * tw + tx] = if (n > 0) sum / n else 0f
            }
        }
        return out
    }
}
