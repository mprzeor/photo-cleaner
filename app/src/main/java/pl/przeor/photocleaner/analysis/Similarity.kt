package pl.przeor.photocleaner.analysis

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Similarity {
    /** Number of differing bits between two 64-bit hashes (0 = identical, 64 = opposite). */
    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Histogram intersection of two normalised histograms, in 0..1 (1 = identical). */
    fun histogramIntersection(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        var s = 0f
        for (i in 0 until n) s += min(a[i], b[i])
        return s
    }

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
