package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.dao.OfficeDao
import com.example.data.entity.DocumentEntity
import com.example.data.entity.FolderEntity
import com.example.data.entity.OcrScanEntity

@Database(
    entities = [DocumentEntity::class, FolderEntity::class, OcrScanEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun officeDao(): OfficeDao
}
