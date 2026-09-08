package pl.przeor.photocleaner.data.db

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import pl.przeor.photocleaner.analysis.ImageFeatures
import pl.przeor.photocleaner.data.media.ImageEntry
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer

/**
 * Cached analysis result for one file. A row is reused as long as the file's
 * size and lastModified are unchanged; otherwise it is recomputed.
 */
@Entity(tableName = "image_features")
data class ImageFeatureEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val key: String,
    val uri: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val width: Int,
    val height: Int,
    val aHash: Long,
    val dHash: Long,
    val pHash: Long,
    /** Float histogram packed as big-endian bytes. */
    val histogram: ByteArray,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: Long?,
    /** true when the file could not be decoded; kept so we don't retry every scan. */
    val failed: Boolean,
) {
    fun toDomain(): ImageFeatures = ImageFeatures(
        key = key,
        uri = Uri.parse(uri),
        name = name,
        size = size,
        lastModified = lastModified,
        width = width,
        height = height,
        aHash = aHash,
        dHash = dHash,
        pHash = pHash,
        histogram = histogram.toFloatArray(),
        latitude = latitude,
        longitude = longitude,
        takenAt = takenAt,
    )

    companion object {
        fun failed(entry: ImageEntry) = ImageFeatureEntity(
            key = entry.key,
            uri = entry.uri.toString(),
            name = entry.name,
            size = entry.size,
            lastModified = entry.lastModified,
            width = 0,
            height = 0,
            aHash = 0L,
            dHash = 0L,
            pHash = 0L,
            histogram = ByteArray(0),
            latitude = null,
            longitude = null,
            takenAt = null,
            failed = true,
        )
    }
}

@Dao
interface ImageFeatureDao {
    @Query("SELECT * FROM image_features")
    suspend fun getAll(): List<ImageFeatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ImageFeatureEntity>)

    @Query("DELETE FROM image_features WHERE cache_key IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("DELETE FROM image_features")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM image_features")
    fun count(): Flow<Int>
}

@Database(entities = [ImageFeatureEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageFeatureDao(): ImageFeatureDao
}

fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val out = FloatArray(size / 4)
    ByteBuffer.wrap(this).asFloatBuffer().get(out)
    return out
}
