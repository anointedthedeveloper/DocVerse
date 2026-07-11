package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // pdf, docx, xlsx, pptx, txt, json, csv, epub, md, html, xml, yaml
    val content: String, // Text, or serialized CSV / JSON array
    val size: Long,
    val modifiedAt: Long = System.currentTimeMillis(),
    val category: String, // PDFs, Word Documents, Excel Sheets, PowerPoint, Books, Notes, Text Files
    val isFavorite: Boolean = false,
    val isRecent: Boolean = false,
    val folderId: Long? = null,
    val tags: String = "",
    val isEncrypted: Boolean = false,
    val password: String? = null
)
