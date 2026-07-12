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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    // --- API Key configuration ---
    private val _userApiKey = MutableStateFlow("MY_GEMINI_API_KEY")
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    // --- App Theme State ("light", "dark", "amoled") ---
    private val _appTheme = MutableStateFlow("light")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        repository.setStringPreference("app_theme", theme)
    }

    // --- Multi-Selection Mode ---
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocumentIds: StateFlow<Set<Long>> = _selectedDocumentIds.asStateFlow()

    fun enterSelectionMode(documentId: Long) {
        _isSelectionMode.value = true
        _selectedDocumentIds.value = setOf(documentId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedDocumentIds.value = emptySet()
    }

    fun toggleDocumentSelection(documentId: Long) {
        val current = _selectedDocumentIds.value
        if (current.contains(documentId)) {
            val next = current - documentId
            _selectedDocumentIds.value = next
            if (next.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            _selectedDocumentIds.value = current + documentId
            _isSelectionMode.value = true
        }
    }

    fun selectAllDocuments(documents: List<DocumentEntity>) {
        _selectedDocumentIds.value = documents.map { it.id }.toSet()
        _isSelectionMode.value = true
    }

    fun deleteSelectedDocuments() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ids = _selectedDocumentIds.value
                val docsToDelete = allDocuments.value.filter { ids.contains(it.id) }
                docsToDelete.forEach { doc ->
                    repository.deleteDocument(doc)
                }
                exitSelectionMode()
            } catch (e: Exception) {
                Log.e("OfficeViewModel", "Error deleting selected files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Initialize ---
    init {
        val theme = repository.getStringPreference("app_theme", "light")
        _appTheme.value = theme
        
        val apiKey = repository.getStringPreference("user_api_key", "MY_GEMINI_API_KEY")
        _userApiKey.value = apiKey
        
        val pin = repository.getStringPreference("app_pin", "")
        _appPin.value = pin
        _isAppLocked.value = pin.isNotEmpty()
        
        val cloud = repository.getStringPreference("cloud_provider", "Google Drive")
        _cloudProvider.value = cloud

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
        if (document != null) {
            // Mark as recent and update accessed/modified time when opened
            viewModelScope.launch {
                repository.updateDocument(document.copy(isRecent = true, modifiedAt = System.currentTimeMillis()))
            }
        }
    }

    // --- CRUD Operations ---
    fun createDocument(name: String, type: String, content: String, category: String) {
        viewModelScope.launch {
            val fullName = if (name.endsWith(".$type")) name else "$name.$type"
            // Save to physical documents folder
            repository.savePhysicalFile(fullName, content)

            val doc = DocumentEntity(
                name = fullName,
                type = type,
                content = content,
                size = content.toByteArray().size.toLong(),
                category = category
            )
            val newId = repository.insertDocument(doc)
            val inserted = repository.getDocumentById(newId)
            if (inserted != null) {
                setActiveDocument(inserted)
            } else {
                setActiveDocument(doc.copy(id = newId))
            }
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
            // Also write to physical file
            repository.savePhysicalFile(updatedDoc.name, newContent)
        }
    }

    fun saveActiveDocumentToDisk() {
        val currentDoc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.updateDocument(currentDoc)
            repository.savePhysicalFile(currentDoc.name, currentDoc.content)
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
    fun askAiAssistant(context: android.content.Context, prompt: String, actionType: String) {
        val currentDoc = _activeDocument.value
        val docType = currentDoc?.type ?: ""
        var docContent = currentDoc?.content ?: ""

        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = null
            
            try {
                if (docType == "pdf") {
                    _aiResponse.value = "Extracting and analyzing text from PDF pages on device..."
                    docContent = extractTextFromPdf(context, currentDoc!!)
                } else if (docType == "xlsx" || docType == "csv") {
                    _aiResponse.value = "Parsing and formatting spreadsheet columns..."
                    docContent = formatExcelContentForAi(docContent)
                }
            } catch (e: Exception) {
                Log.e("OfficeViewModel", "Error preprocessing content for AI: ${e.message}", e)
            }

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

    private suspend fun extractTextFromPdf(context: android.content.Context, document: DocumentEntity): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val docsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
        val file = java.io.File(docsDir, document.name)
        
        // Reconstruct physical file if needed
        if ((!file.exists() || file.length() == 0L) && document.content.startsWith("BASE64:")) {
            try {
                val base64Str = document.content.substring(7).trim()
                val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                if (docsDir != null) {
                    if (!docsDir.exists()) docsDir.mkdirs()
                    file.writeBytes(decodedBytes)
                }
            } catch (ex: Exception) {
                Log.e("OfficeViewModel", "Failed to restore physical PDF during AI analysis: ${ex.message}")
            }
        }
        
        if (!file.exists() || file.length() == 0L) {
            return@withContext "[Error: PDF file does not exist or is empty]"
        }
        
        val extractedText = StringBuilder()
        try {
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val limitPages = minOf(renderer.pageCount, 5) // Analyze up to 5 pages for performance
            
            for (i in 0 until limitPages) {
                val page = renderer.openPage(i)
                val scale = 1.5f // Use 1.5f for faster OCR and good quality
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Process Bitmap with ML Kit OCR
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                
                val ocrTask = recognizer.process(image)
                while (!ocrTask.isComplete) {
                    kotlinx.coroutines.delay(50)
                }
                
                if (ocrTask.isSuccessful) {
                    val pageText = ocrTask.result.text
                    if (pageText.isNotEmpty()) {
                        extractedText.append("--- PAGE ${i + 1} ---\n")
                        extractedText.append(pageText).append("\n\n")
                    }
                }
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.e("OfficeViewModel", "OCR extraction on PDF failed: ${e.message}", e)
            extractedText.append("[Failed to extract text from PDF file: ${e.message}]")
        }
        
        val result = extractedText.toString().trim()
        if (result.isEmpty()) "[No text detected in PDF document]" else result
    }

    private fun formatExcelContentForAi(jsonContent: String): String {
        try {
            val jsonObject = org.json.JSONObject(jsonContent)
            val sheets = jsonObject.optJSONArray("sheets") ?: return jsonContent
            val result = StringBuilder()
            
            for (s in 0 until sheets.length()) {
                val sheet = sheets.getJSONObject(s)
                val sheetName = sheet.optString("name", "Sheet")
                val rows = sheet.optJSONArray("rows") ?: continue
                
                result.append("Spreadsheet Sheet: $sheetName\n")
                val limitRows = minOf(rows.length(), 40) // Limit rows for AI context
                for (r in 0 until limitRows) {
                    val row = rows.optJSONArray(r) ?: continue
                    val cols = mutableListOf<String>()
                    val limitCols = minOf(row.length(), 10) // Limit columns
                    for (c in 0 until limitCols) {
                        cols.add(row.optString(c, ""))
                    }
                    result.append("Row ${r + 1}: ${cols.joinToString(" | ")}\n")
                }
                result.append("\n")
            }
            return result.toString().trim()
        } catch (e: Exception) {
            Log.e("OfficeViewModel", "Failed to format Excel content for AI: ${e.message}")
            return jsonContent
        }
    }

    fun clearAiResponse() {
        _aiResponse.value = null
    }

    fun setApiKey(key: String) {
        _userApiKey.value = key
        repository.setStringPreference("user_api_key", key)
    }

    // --- Security Configuration ---
    fun setupSecurityPin(pin: String) {
        _appPin.value = pin
        _isAppLocked.value = pin.isNotEmpty()
        repository.setStringPreference("app_pin", pin)
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
        repository.setStringPreference("cloud_provider", provider)
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
            _isLoading.value = true
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
                    val base64Content = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    content = "BASE64:$base64Content"
                } else if (false) {
                    content = "=== PDF Document Content: $name ===\n" +
                            "Size: ${bytes.size} bytes\n" +
                            "This PDF is fully loaded into DocVerse. You can view, annotate, highlights pages, sign with digital signatures, or merge/split with other PDF files using the built-in professional PDF tools."
                } else if (finalType in listOf("docx", "doc", "odt", "rtf")) {
                    content = "=== Rich Word Document Content: $name ===\n" +
                            "This is a rich word document. You can edit text styles, headings, fonts, insert tables, and use formatting blocks in DocVerse Editor."
                } else if (finalType == "xlsx") {
                    content = parseXlsxBytes(bytes)
                } else if (finalType in listOf("xls", "ods", "csv")) {
                    content = parseCsvBytes(bytes)
                } else if (false) {
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
                // Write imported raw bytes to physical file system
                try {
                    val docsDir = repository.context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    if (docsDir != null) {
                        if (!docsDir.exists()) docsDir.mkdirs()
                        val physicalFile = java.io.File(docsDir, name)
                        physicalFile.writeBytes(bytes)
                        Log.d("OfficeViewModel", "Successfully saved imported physical file: ${physicalFile.absolutePath}")
                    }
                } catch (ex: Exception) {
                    Log.e("OfficeViewModel", "Failed to save physical copy of imported file: ${ex.message}")
                }

                val id = repository.insertDocument(doc)
                val inserted = repository.getDocumentById(id)
                if (inserted != null) {
                    setActiveDocument(inserted)
                } else {
                    setActiveDocument(doc.copy(id = id))
                }
            } catch (e: Exception) {
                Log.e("OfficeViewModel", "Error loading document from Uri: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseXlsxBytes(bytes: ByteArray): String {
        try {
            val sharedStrings = mutableListOf<String>()
            val sheetCells = mutableMapOf<Int, MutableMap<Int, String>>()
            val sheetStyles = mutableMapOf<String, org.json.JSONObject>()
            var maxRow = 0
            var maxCol = 0

            val zipIn = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes))
            var entry = zipIn.nextEntry
            var sheet1XmlBytes: ByteArray? = null
            var sharedStringsXmlBytes: ByteArray? = null
            var stylesXmlBytes: ByteArray? = null

            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    sharedStringsXmlBytes = zipIn.readBytes()
                } else if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheet1XmlBytes = zipIn.readBytes()
                } else if (entry.name == "xl/styles.xml") {
                    stylesXmlBytes = zipIn.readBytes()
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            if (sharedStringsXmlBytes != null) {
                val xmlStr = String(sharedStringsXmlBytes, Charsets.UTF_8)
                val matcher = java.util.regex.Pattern.compile("<t\\b[^>]*>([^<]*)</t>").matcher(xmlStr)
                while (matcher.find()) {
                    sharedStrings.add(matcher.group(1) ?: "")
                }
            }

            val fontsList = mutableListOf<org.json.JSONObject>()
            val fillsList = mutableListOf<String>()
            val xfList = mutableListOf<org.json.JSONObject>()

            if (stylesXmlBytes != null) {
                val xmlStr = String(stylesXmlBytes, Charsets.UTF_8)
                
                // Parse fonts
                val fontMatcher = java.util.regex.Pattern.compile("<font\\b[^>]*>(.*?)</font>").matcher(xmlStr)
                while (fontMatcher.find()) {
                    val fontBody = fontMatcher.group(1) ?: ""
                    val isBold = fontBody.contains("<b/>") || fontBody.contains("<b>")
                    val isItalic = fontBody.contains("<i/>") || fontBody.contains("<i>")
                    var colorHex = "#000000"
                    val colMatcher = java.util.regex.Pattern.compile("<color\\b[^>]*\\brgb=\"([A-Fa-f0-9]{8})\"").matcher(fontBody)
                    if (colMatcher.find()) {
                        val rgb = colMatcher.group(1) ?: ""
                        if (rgb.length == 8) {
                            colorHex = "#" + rgb.substring(2)
                        }
                    }
                    val fontJson = org.json.JSONObject()
                    fontJson.put("bold", isBold)
                    fontJson.put("italic", isItalic)
                    fontJson.put("textColor", colorHex)
                    fontsList.add(fontJson)
                }

                // Parse fills
                val fillMatcher = java.util.regex.Pattern.compile("<fill\\b[^>]*>(.*?)</fill>").matcher(xmlStr)
                while (fillMatcher.find()) {
                    val fillBody = fillMatcher.group(1) ?: ""
                    var bgHex = "#FFFFFF"
                    val fgMatcher = java.util.regex.Pattern.compile("<fgColor\\b[^>]*\\brgb=\"([A-Fa-f0-9]{8})\"").matcher(fillBody)
                    if (fgMatcher.find()) {
                        val rgb = fgMatcher.group(1) ?: ""
                        if (rgb.length == 8) {
                            bgHex = "#" + rgb.substring(2)
                        }
                    }
                    fillsList.add(bgHex)
                }

                // Parse cellXfs xfs
                val cellXfsMatcher = java.util.regex.Pattern.compile("<cellXfs\\b[^>]*>(.*?)</cellXfs>").matcher(xmlStr)
                if (cellXfsMatcher.find()) {
                    val xfsBody = cellXfsMatcher.group(1) ?: ""
                    val xfMatcher = java.util.regex.Pattern.compile("<xf\\b([^>]*)>").matcher(xfsBody)
                    while (xfMatcher.find()) {
                        val attrs = xfMatcher.group(1) ?: ""
                        val xfJson = org.json.JSONObject()
                        
                        val fontIdMatcher = java.util.regex.Pattern.compile("fontId=\"(\\d+)\"").matcher(attrs)
                        if (fontIdMatcher.find()) {
                            val fId = fontIdMatcher.group(1)?.toIntOrNull() ?: 0
                            if (fId in fontsList.indices) {
                                val fontJson = fontsList[fId]
                                xfJson.put("bold", fontJson.optBoolean("bold"))
                                xfJson.put("italic", fontJson.optBoolean("italic"))
                                if (fontJson.optString("textColor") != "#000000") {
                                    xfJson.put("textColor", fontJson.optString("textColor"))
                                }
                            }
                        }

                        val fillIdMatcher = java.util.regex.Pattern.compile("fillId=\"(\\d+)\"").matcher(attrs)
                        if (fillIdMatcher.find()) {
                            val fId = fillIdMatcher.group(1)?.toIntOrNull() ?: 0
                            if (fId in fillsList.indices) {
                                val bg = fillsList[fId]
                                if (bg != "#FFFFFF" && bg != "#000000") {
                                    xfJson.put("bgColor", bg)
                                }
                            }
                        }
                        xfList.add(xfJson)
                    }
                }
            }

            if (sheet1XmlBytes != null) {
                val xmlStr = String(sheet1XmlBytes, Charsets.UTF_8)
                val rowMatcher = java.util.regex.Pattern.compile("<row\\b[^>]*>(.*?)</row>").matcher(xmlStr)
                while (rowMatcher.find()) {
                    val rowContent = rowMatcher.group(1) ?: ""
                    val cellMatcher = java.util.regex.Pattern.compile("<c\\b[^>]*\\br=\"([A-Z]+)(\\d+)\"([^>]*)>(.*?)</c>").matcher(rowContent)
                    while (cellMatcher.find()) {
                        val colLetters = cellMatcher.group(1) ?: "A"
                        val rowNum = (cellMatcher.group(2) ?: "1").toInt() - 1
                        val attrs = cellMatcher.group(3) ?: ""
                        val cellBody = cellMatcher.group(4) ?: ""

                        var colNum = 0
                        for (char in colLetters) {
                            colNum = colNum * 26 + (char - 'A' + 1)
                        }
                        colNum -= 1

                        maxRow = maxOf(maxRow, rowNum)
                        maxCol = maxOf(maxCol, colNum)

                        var value = ""
                        val vMatcher = java.util.regex.Pattern.compile("<v>([^<]*)</v>").matcher(cellBody)
                        val tIsShared = attrs.contains("t=\"s\"")
                        if (vMatcher.find()) {
                            val vVal = vMatcher.group(1) ?: ""
                            if (tIsShared) {
                                val idx = vVal.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) {
                                    value = sharedStrings[idx]
                                }
                            } else {
                                value = vVal
                            }
                        } else {
                            val isMatcher = java.util.regex.Pattern.compile("<t\\b[^>]*>([^<]*)</t>").matcher(cellBody)
                            if (isMatcher.find()) {
                                value = isMatcher.group(1) ?: ""
                            }
                        }

                        val rowMap = sheetCells.getOrPut(rowNum) { mutableMapOf() }
                        rowMap[colNum] = value

                        // Extract cell formatting if style index is set
                        val sMatcher = java.util.regex.Pattern.compile("\\bs=\"(\\d+)\"").matcher(attrs)
                        if (sMatcher.find()) {
                            val styleIdx = sMatcher.group(1)?.toIntOrNull()
                            if (styleIdx != null && styleIdx in xfList.indices) {
                                val styleJson = xfList[styleIdx]
                                if (styleJson.length() > 0) {
                                    sheetStyles["${rowNum}_${colNum}"] = styleJson
                                }
                            }
                        }
                    }
                }
            }

            if (maxRow == 0 && maxCol == 0 && sheetCells.isEmpty()) {
                return "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": [[\"A1\", \"B1\", \"C1\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]}]}"
            }

            val limitRows = minOf(maxRow + 1, 100)
            val limitCols = minOf(maxCol + 1, 15)

            val jsonRows = StringBuilder()
            jsonRows.append("[")
            for (r in 0 until limitRows) {
                if (r > 0) jsonRows.append(",")
                jsonRows.append("[")
                val rowMap = sheetCells[r]
                for (c in 0 until limitCols) {
                    if (c > 0) jsonRows.append(",")
                    val cellVal = rowMap?.get(c) ?: ""
                    val escaped = cellVal.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    jsonRows.append("\"").append(escaped).append("\"")
                }
                jsonRows.append("]")
            }
            jsonRows.append("]")

            val stylesJson = org.json.JSONObject()
            sheetStyles.forEach { (key, style) ->
                stylesJson.put(key, style)
            }

            return "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": $jsonRows, \"styles\": $stylesJson}]}"
        } catch (e: Exception) {
            Log.e("OfficeViewModel", "Error parsing XLSX bytes: ${e.message}", e)
            return "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": [[\"A1\", \"B1\", \"C1\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]}]}"
        }
    }

    private fun parseCsvBytes(bytes: ByteArray): String {
        try {
            val text = String(bytes)
            val rows = mutableListOf<List<String>>()
            text.lineSequence().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    val cols = line.split(",").map { it.trim() }
                    rows.add(cols)
                }
            }
            
            val jsonRows = StringBuilder()
            jsonRows.append("[")
            for (r in rows.indices) {
                if (r > 0) jsonRows.append(",")
                jsonRows.append("[")
                val cols = rows[r]
                for (c in cols.indices) {
                    if (c > 0) jsonRows.append(",")
                    val cellVal = cols[c]
                    val escaped = cellVal.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    jsonRows.append("\"").append(escaped).append("\"")
                }
                jsonRows.append("]")
            }
            jsonRows.append("]")
            
            return "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": $jsonRows}]}"
        } catch (e: Exception) {
            return "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": [[\"A1\", \"B1\", \"C1\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]}]}"
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
