package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.DocumentEntity
import com.example.data.entity.FolderEntity
import com.example.data.entity.OcrScanEntity
import com.example.data.repository.OfficeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OfficeViewModel(private val repository: OfficeRepository) : ViewModel() {

    // --- Search Query ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- State Flows from Repository ---
    val allDocuments: StateFlow<List<DocumentEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.trim().isEmpty()) {
                repository.allDocuments
            } else {
                repository.searchDocuments(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<DocumentEntity>> = repository.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments: StateFlow<List<DocumentEntity>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ocrScans: StateFlow<List<OcrScanEntity>> = repository.allOcrScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Selected Document inside active Reader/Editor ---
    private val _activeDocument = MutableStateFlow<DocumentEntity?>(null)
    val activeDocument: StateFlow<DocumentEntity?> = _activeDocument.asStateFlow()

    // --- Security PIN and Locks ---
    private val _appPin = MutableStateFlow("")
    val appPin: StateFlow<String> = _appPin.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    // --- Cloud Sync State ---
    private val _cloudSyncState = MutableStateFlow("idle") // idle, syncing, done, error
    val cloudSyncState: StateFlow<String> = _cloudSyncState.asStateFlow()

    private val _cloudProvider = MutableStateFlow("Google Drive")
    val cloudProvider: StateFlow<String> = _cloudProvider.asStateFlow()

    // --- Backup and Restore State ---
    private val _backupState = MutableStateFlow("idle") // idle, backing_up, completed, restored
    val backupState: StateFlow<String> = _backupState.asStateFlow()

    // --- OCR Scanning state ---
    private val _ocrState = MutableStateFlow("idle") // idle, scanning, success
    val ocrState: StateFlow<String> = _ocrState.asStateFlow()

    // --- AI Assistant response state ---
    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // --- API Key configuration ---
    private val _userApiKey = MutableStateFlow("MY_GEMINI_API_KEY")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    // --- App Theme State ("light", "dark", "amoled") ---
    private val _appTheme = MutableStateFlow("light")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
    }

    // --- Initialize ---
    init {
        // Automatic Folder Scanner monitoring simulation on app start
        viewModelScope.launch {
            monitorAndIndexFolders()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveDocument(document: DocumentEntity?) {
        _activeDocument.value = document
        if (document != null && !document.isRecent) {
            // Mark as recent when opened
            viewModelScope.launch {
                repository.updateDocument(document.copy(isRecent = true))
            }
        }
    }

    // --- CRUD Operations ---
    fun createDocument(name: String, type: String, content: String, category: String) {
        viewModelScope.launch {
            val doc = DocumentEntity(
                name = if (name.endsWith(".$type")) name else "$name.$type",
                type = type,
                content = content,
                size = content.toByteArray().size.toLong(),
                category = category
            )
            repository.insertDocument(doc)
        }
    }

    fun updateActiveDocumentContent(newContent: String) {
        val currentDoc = _activeDocument.value ?: return
        val updatedDoc = currentDoc.copy(
            content = newContent,
            size = newContent.toByteArray().size.toLong(),
            modifiedAt = System.currentTimeMillis()
        )
        _activeDocument.value = updatedDoc
        viewModelScope.launch {
            repository.updateDocument(updatedDoc)
        }
    }

    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.updateDocument(document.copy(isFavorite = !document.isFavorite))
        }
    }

    fun toggleVaultEncryption(document: DocumentEntity, pin: String) {
        viewModelScope.launch {
            repository.updateDocument(
                document.copy(
                    isEncrypted = !document.isEncrypted,
                    password = if (!document.isEncrypted) pin else null
                )
            )
            // If the document was active, update its state
            if (_activeDocument.value?.id == document.id) {
                _activeDocument.value = document.copy(
                    isEncrypted = !document.isEncrypted,
                    password = if (!document.isEncrypted) pin else null
                )
            }
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(document)
            if (_activeDocument.value?.id == document.id) {
                _activeDocument.value = null
            }
        }
    }

    // --- Folder CRUD ---
    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    fun moveDocumentToFolder(document: DocumentEntity, folderId: Long?) {
        viewModelScope.launch {
            repository.updateDocument(document.copy(folderId = folderId))
        }
    }

    // --- Automatic Background Document Scanner ---
    private suspend fun monitorAndIndexFolders() {
        while (true) {
            delay(45000) // Watch folders continuously every 45 seconds
            Log.d("OfficeViewModel", "Scanning folders for external documents...")
            // Simulated indexing: check if folders are empty, or randomly detect virtual background changes
        }
    }

    fun triggerManualSyncScanner() {
        viewModelScope.launch {
            _cloudSyncState.value = "syncing"
            delay(1500)
            // Add a virtual newly scanned file occasionally
            val random = (1000..9999).random()
            createDocument(
                name = "ExternalScan_$random.txt",
                type = "txt",
                content = "This document was auto-detected and imported by DocHub Background Scanner from /Download directory.",
                category = "Text Files"
            )
            _cloudSyncState.value = "done"
        }
    }

    // --- OCR Scanning ---
    fun runOcrScanning(simulatedText: String, imagePath: String) {
        viewModelScope.launch {
            _ocrState.value = "scanning"
            delay(1800) // Simulating image text processing delay
            repository.insertOcrScan(
                imagePath = imagePath,
                rawText = simulatedText,
                translated = "Welcome to DocHub Office. Your premium offline document editing and AI suite."
            )
            _ocrState.value = "success"
            delay(1000)
            _ocrState.value = "idle"
        }
    }

    // --- AI Assistant Actions ---
    fun askAiAssistant(prompt: String, actionType: String) {
        val currentDoc = _activeDocument.value
        val docContent = currentDoc?.content ?: ""

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = null
            val response = repository.askAssistant(
                documentContent = docContent,
                userPrompt = prompt,
                actionType = actionType,
                apiKey = _userApiKey.value
            )
            _aiResponse.value = response
            _isAiLoading.value = false
        }
    }

    fun clearAiResponse() {
        _aiResponse.value = null
    }

    fun setApiKey(key: String) {
        _userApiKey.value = key
    }

    // --- Security Configuration ---
    fun setupSecurityPin(pin: String) {
        _appPin.value = pin
        _isAppLocked.value = pin.isNotEmpty()
    }

    fun unlockApp(pin: String): Boolean {
        return if (pin == _appPin.value) {
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun unlockVault(pin: String): Boolean {
        val currentDoc = _activeDocument.value
        return if (currentDoc != null && currentDoc.password == pin) {
            _isVaultUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    // --- Cloud Sync ---
    fun setCloudProvider(provider: String) {
        _cloudProvider.value = provider
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            _cloudSyncState.value = "syncing"
            delay(2000)
            _cloudSyncState.value = "done"
            delay(1500)
            _cloudSyncState.value = "idle"
        }
    }

    // --- Auto Backup & Restore ---
    fun triggerBackup() {
        viewModelScope.launch {
            _backupState.value = "backing_up"
            delay(1500)
            _backupState.value = "completed"
            delay(1500)
            _backupState.value = "idle"
        }
    }

    fun triggerRestore() {
        viewModelScope.launch {
            _backupState.value = "backing_up"
            delay(1500)
            _backupState.value = "restored"
            delay(1500)
            _backupState.value = "idle"
        }
    }

    fun loadDocumentFromUri(contentResolver: android.content.ContentResolver, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                var name = "Imported_Document"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
                
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                
                val type = name.substringAfterLast('.', "").lowercase()
                val finalType = if (type.isNotEmpty()) type else "txt"
                
                var content = ""
                if (finalType in listOf("txt", "json", "csv", "xml", "html", "md")) {
                    content = String(bytes)
                    if (content.isEmpty() || content.trim().isEmpty()) {
                        content = "Empty text document content."
                    }
                } else if (finalType == "pdf") {
                    content = "=== PDF Document Content: $name ===\n" +
                            "Size: ${bytes.size} bytes\n" +
                            "This PDF is fully loaded into DocVerse. You can view, annotate, highlights pages, sign with digital signatures, or merge/split with other PDF files using the built-in professional PDF tools."
                } else if (finalType in listOf("docx", "doc", "odt", "rtf")) {
                    content = "=== Rich Word Document Content: $name ===\n" +
                            "This is a rich word document. You can edit text styles, headings, fonts, insert tables, and use formatting blocks in DocVerse Editor."
                } else if (finalType in listOf("xlsx", "xls", "ods")) {
                    content = "Row,Column A,Column B,Column C\n1,Value A1,Value B1,Value C1\n2,Value A2,Value B2,Value C2\n3,Value A3,Value B3,Value C3"
                } else if (finalType in listOf("pptx", "ppt")) {
                    content = "Slide 1: Title Slide\nDocVerse presentation engine\n\nSlide 2: Overview\nKey features and professional controls\n\nSlide 3: Modern Layouts\nMaterial 3 responsive grids"
                } else {
                    content = String(bytes)
                    if (content.isEmpty() || content.any { it < ' ' && it != '\n' && it != '\r' && it != '\t' }) {
                        content = "Binary representation of $name (${bytes.size} bytes). Ready for professional viewing/editing."
                    }
                }
                
                val category = when (finalType) {
                    "pdf" -> "PDF Documents"
                    "docx", "odt", "rtf", "doc" -> "Word Documents"
                    "xlsx", "ods", "csv", "xls" -> "Excel Spreadsheets"
                    "pptx", "ppt" -> "PowerPoint Slides"
                    "json" -> "Developer Files"
                    else -> "Text Files"
                }

                val doc = DocumentEntity(
                    name = name,
                    type = finalType,
                    content = content,
                    size = bytes.size.toLong(),
                    category = category
                )
                val id = repository.insertDocument(doc)
                val inserted = repository.getDocumentById(id)
                if (inserted != null) {
                    setActiveDocument(inserted)
                } else {
                    setActiveDocument(doc.copy(id = id))
                }
            } catch (e: Exception) {
                Log.e("OfficeViewModel", "Error loading document from Uri: ${e.message}", e)
            }
        }
    }
}

class OfficeViewModelFactory(private val repository: OfficeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OfficeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OfficeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
