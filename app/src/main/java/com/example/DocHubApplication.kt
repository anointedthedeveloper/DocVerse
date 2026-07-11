package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.repository.OfficeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DocHubApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "dochub_office_db"
        ).build()
    }

    val repository by lazy {
        OfficeRepository(database.officeDao(), this)
    }

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Pre-seed default documents on background thread
        applicationScope.launch {
            repository.seedDefaultDocuments()
        }
    }
}
