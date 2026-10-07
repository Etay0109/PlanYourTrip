package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/** One item of a trip's checklists. Deleting the trip deletes its items (foreign key CASCADE). */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id"), Index(value = ["uuid"], unique = true)],
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    /** Which list; stored as the enum name (see [Converters]). */
    val list: ChecklistKind,
    val text: String,
    val checked: Boolean,
    /** Epoch day; null = no due date (always null for packing items). */
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    /** Epoch millis of when the item was added; items are listed by it (then id). */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Stable id shared by every device and Firestore (the document id); the Long [id] is local only. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Epoch millis of the last change on any device; the newer change wins when syncing. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    /** True while this version still has to be uploaded to the cloud. */
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
)

fun ChecklistItemEntity.toChecklistItem() = ChecklistItem(
    id = id,
    tripId = tripId,
    kind = list,
    text = text,
    checked = checked,
    dueDate = dueDate,
    createdAt = createdAt,
)
