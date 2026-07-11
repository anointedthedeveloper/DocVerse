package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_scans")
data class OcrScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val rawText: String,
    val translatedText: String? = null,
    val scannedAt: Long = System.currentTimeMillis()
)
