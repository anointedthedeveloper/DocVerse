package com.example.data.dao

import androidx.room.*
import com.example.data.entity.DocumentEntity
import com.example.data.entity.FolderEntity
import com.example.data.entity.OcrScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficeDao {
    // --- Documents ---
    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE category = :category ORDER BY modifiedAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY modifiedAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isRecent = 1 ORDER BY modifiedAt DESC")
    fun getRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY modifiedAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    // --- Folders ---
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    // --- OCR Scans ---
    @Query("SELECT * FROM ocr_scans ORDER BY scannedAt DESC")
    fun getAllOcrScans(): Flow<List<OcrScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrScan(scan: OcrScanEntity): Long

    @Delete
    suspend fun deleteOcrScan(scan: OcrScanEntity)
}
