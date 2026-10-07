package com.example.planyourtrip.data.document

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A travel document of a trip, on this device only (never synced). Linked to its trip by the trip's **uuid**, not
 * by a foreign key: signing out deletes the account's trips from the device, the documents stay (hidden by
 * [ownerId]) and belong to the trip again when it comes back from the cloud at the next sign-in.
 */
@Entity(
    tableName = "documents",
    indices = [Index("owner_id"), Index("trip_uuid"), Index(value = ["uuid"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    /** Stored as the enum name (see Converters). */
    val type: DocumentType,
    val detail: String?,
    /** The copy's name in the app's documents folder. */
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    /** Epoch millis; documents are listed by it (then id). */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun DocumentEntity.toTripDocument() = TripDocument(
    id = id,
    name = name,
    type = type,
    detail = detail,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    createdAt = createdAt,
)
