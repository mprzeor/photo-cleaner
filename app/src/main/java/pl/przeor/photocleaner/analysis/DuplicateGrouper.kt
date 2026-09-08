package pl.przeor.photocleaner.analysis

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/**
 * Turns a flat list of cached features into duplicate groups for the given settings.
 *
 * Pairwise comparison with union-find. Cheap constraints (time, GPS) are checked before
 * hashes, and when the time filter is on the list is sorted so each photo is only compared
 * against neighbours inside the window – this makes large libraries fast.
 *
 * This is a `suspend` function so a running computation can be cancelled when the user
 * moves a slider again before it finishes.
 */
object DuplicateGrouper {

    private val bestFirst = compareByDescending<ImageFeatures> { it.pixels }
        .thenByDescending { it.size }
        .thenBy { it.effectiveTime }

    suspend fun group(features: List<ImageFeatures>, s: MatchSettings): List<DuplicateGroup> {
        if (features.size < 2 || !s.anyVisualEnabled) return emptyList()

        val n = features.size
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val order: List<Int> =
            if (s.timeEnabled) features.indices.sortedBy { features[it].effectiveTime }
            else features.indices.toList()
        val windowMs = s.timeMaxMinutes * 60_000L

        for (a in order.indices) {
            currentCoroutineContext().ensureActive()
            val i = order[a]
            val fi = features[i]
            for (b in a + 1 until order.size) {
                val j = order[b]
                val fj = features[j]
                if (s.timeEnabled && fj.effectiveTime - fi.effectiveTime > windowMs) break
                if (matches(fi, fj, s)) union(i, j)
            }
        }

        val buckets = HashMap<Int, MutableList<ImageFeatures>>()
        for (i in 0 until n) buckets.getOrPut(find(i)) { ArrayList() }.add(features[i])

        return buckets.values
            .filter { it.size > 1 }
            .map { DuplicateGroup(it.sortedWith(bestFirst)) }
            .sortedByDescending { it.reclaimableBytes }
    }

    /** Decides whether two photos should be linked under the given settings. */
    fun matches(a: ImageFeatures, b: ImageFeatures, s: MatchSettings): Boolean {
        // --- constraints -------------------------------------------------------
        if (s.timeEnabled) {
            if (abs(a.effectiveTime - b.effectiveTime) > s.timeMaxMinutes * 60_000L) return false
        }
        if (s.geoEnabled) {
            if (a.hasLocation && b.hasLocation) {
                val d = Similarity.distanceMeters(a.latitude!!, a.longitude!!, b.latitude!!, b.longitude!!)
                if (d > s.geoMaxDistanceMeters) return false
            } else if (!s.geoSkipIfMissing) {
                return false
            }
        }

        // --- visual algorithms --------------------------------------------------
        var checked = 0
        var passed = 0

        if (s.pHashEnabled) {
            checked++
            val ok = Similarity.hamming(a.pHash, b.pHash) <= s.pHashMaxDistance
            if (ok) passed++ else if (s.requireAllVisual) return false
            if (ok && !s.requireAllVisual) return true
        }
        if (s.dHashEnabled) {
            checked++
            val ok = Similarity.hamming(a.dHash, b.dHash) <= s.dHashMaxDistance
            if (ok) passed++ else if (s.requireAllVisual) return false
            if (ok && !s.requireAllVisual) return true
        }
        if (s.aHashEnabled) {
            checked++
            val ok = Similarity.hamming(a.aHash, b.aHash) <= s.aHashMaxDistance
            if (ok) passed++ else if (s.requireAllVisual) return false
            if (ok && !s.requireAllVisual) return true
        }
        if (s.histogramEnabled) {
            checked++
            val ok = Similarity.histogramIntersection(a.histogram, b.histogram) >= s.histogramMinSimilarity
            if (ok) passed++ else if (s.requireAllVisual) return false
            if (ok && !s.requireAllVisual) return true
        }

        return checked > 0 && passed == checked
    }
}
