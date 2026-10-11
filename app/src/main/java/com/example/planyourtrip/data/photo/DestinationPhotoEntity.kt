package com.example.planyourtrip.data.photo

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The photo lookup of one destination, on this device only (never synced, shared by every account: no personal
 * data). All URLs null = nothing found (retried after [PHOTO_RETRY_AFTER_MILLIS]).
 */
@Entity(tableName = "destination_photos")
data class DestinationPhotoEntity(
    /** [photoKey]: destination text + coordinates. */
    @PrimaryKey @ColumnInfo(name = "photo_key") val key: String,
    @ColumnInfo(name = "card_url") val cardUrl: String?,
    @ColumnInfo(name = "thumb_url") val thumbUrl: String?,
    /** The image's description page (author, license), for a photo credit later. */
    @ColumnInfo(name = "file_page_url") val filePageUrl: String?,
    /** Epoch millis of the lookup. */
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
) {
    fun toPhoto(): DestinationPhoto? =
        if (cardUrl != null && thumbUrl != null) DestinationPhoto(cardUrl, thumbUrl) else null
}

@Dao
interface DestinationPhotoDao {
    @Query("SELECT * FROM destination_photos WHERE photo_key IN (:keys)")
    fun observe(keys: List<String>): Flow<List<DestinationPhotoEntity>>

    @Query("SELECT * FROM destination_photos WHERE photo_key = :key")
    suspend fun get(key: String): DestinationPhotoEntity?

    @Upsert
    suspend fun upsert(row: DestinationPhotoEntity)
}
