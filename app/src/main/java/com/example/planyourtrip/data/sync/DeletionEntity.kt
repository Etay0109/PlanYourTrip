package com.example.planyourtrip.data.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A local delete that still has to reach the cloud (as a tombstone). The row is removed once uploaded, or when
 * a newer edit from another device brings the item back. One per account and item (two accounts on this device
 * may share a trip).
 */
@Entity(tableName = "deletions", primaryKeys = ["owner_id", "uuid"], indices = [Index("owner_id")])
data class DeletionEntity(
    val uuid: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    /** Stored as the enum name (see Converters). */
    val kind: SyncKind,
    /** The trip's uuid for a child item (the cloud path needs it); null for a trip. */
    @ColumnInfo(name = "trip_uuid") val tripUuid: String?,
    /** Epoch millis of the delete; compared with edits made on other devices. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)

/** uuid of an expense or activity and of its trip, read just before deleting it. */
data class ChildRef(
    val uuid: String,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)
