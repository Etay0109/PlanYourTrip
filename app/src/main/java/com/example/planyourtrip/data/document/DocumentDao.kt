package com.example.planyourtrip.data.document

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Every query is scoped to one owner, so an account never sees or changes another account's documents. */
@Dao
interface DocumentDao {
    @Query(
        "SELECT d.* FROM documents d JOIN trips t ON t.uuid = d.trip_uuid AND t.owner_id = d.owner_id " +
            "WHERE t.id = :tripId AND d.owner_id = :ownerId ORDER BY d.created_at, d.id"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id AND owner_id = :ownerId")
    suspend fun byId(id: Long, ownerId: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query(
        "UPDATE documents SET name = :name, type = :type, detail = :detail WHERE id = :id AND owner_id = :ownerId"
    )
    suspend fun update(id: Long, ownerId: String, name: String, type: DocumentType, detail: String?)

    @Query("DELETE FROM documents WHERE id = :id AND owner_id = :ownerId")
    suspend fun delete(id: Long, ownerId: String)

    @Query("SELECT * FROM documents WHERE trip_uuid = :tripUuid AND owner_id = :ownerId")
    suspend fun forTrip(tripUuid: String, ownerId: String): List<DocumentEntity>

    /** The owner's documents whose trip isn't on this device (any more) for that owner. */
    @Query(
        "SELECT * FROM documents WHERE owner_id = :ownerId " +
            "AND trip_uuid NOT IN (SELECT uuid FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun orphans(ownerId: String): List<DocumentEntity>

    /** Every stored file name, of every account (the files other accounts keep must survive the sweep too). */
    @Query("SELECT file_name FROM documents")
    suspend fun allFileNames(): List<String>
}
