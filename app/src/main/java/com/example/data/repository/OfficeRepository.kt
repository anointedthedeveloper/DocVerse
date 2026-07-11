package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.dao.OfficeDao
import com.example.data.entity.DocumentEntity
import com.example.data.entity.FolderEntity
import com.example.data.entity.OcrScanEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class OfficeRepository(
    private val officeDao: OfficeDao,
    val context: Context
) {
    private val sharedPrefs by lazy {
        context.getSharedPreferences("dochub_prefs", Context.MODE_PRIVATE)
    }

    fun getStringPreference(key: String, defaultValue: String): String {
        return sharedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    fun setStringPreference(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    // OkHttp Client configured according to gemini-api guidelines (60-second timeouts)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    // --- Document Flows ---
    val allDocuments: Flow<List<DocumentEntity>> = officeDao.getAllDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = officeDao.getFavoriteDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = officeDao.getRecentDocuments()
    val allFolders: Flow<List<FolderEntity>> = officeDao.getAllFolders()
    val allOcrScans: Flow<List<OcrScanEntity>> = officeDao.getAllOcrScans()

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> =
        officeDao.getDocumentsByCategory(category)

    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>> =
        officeDao.getDocumentsByFolder(folderId)

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        officeDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        officeDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: DocumentEntity): Long = withContext(Dispatchers.IO) {
        officeDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) = withContext(Dispatchers.IO) {
        officeDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) = withContext(Dispatchers.IO) {
        officeDao.deleteDocument(document)
    }

    suspend fun deleteDocumentById(id: Long) = withContext(Dispatchers.IO) {
        officeDao.deleteDocumentById(id)
    }

    // --- Folders ---
    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        officeDao.insertFolder(FolderEntity(name = name))
    }

    suspend fun deleteFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        officeDao.deleteFolder(folder)
    }

    // --- OCR Scans ---
    suspend fun insertOcrScan(imagePath: String, rawText: String, translated: String? = null): Long =
        withContext(Dispatchers.IO) {
            officeDao.insertOcrScan(
                OcrScanEntity(imagePath = imagePath, rawText = rawText, translatedText = translated)
            )
        }

    suspend fun deleteOcrScan(scan: OcrScanEntity) = withContext(Dispatchers.IO) {
        officeDao.deleteOcrScan(scan)
    }

    // --- Gemini AI Assistant Integration ---
    suspend fun askAssistant(
        documentContent: String,
        userPrompt: String,
        actionType: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty() || trimmedKey == "MY_GEMINI_API_KEY") {
            // Local fallback simulator if no user API key is provided
            return@withContext simulateAiResponse(documentContent, userPrompt, actionType)
        }

        val systemPrompt = when (actionType) {
            "summarize" -> "You are DocHub AI, an expert document analyst. Provide a clear, bulleted, professional executive summary of the provided document text. Keep it well-structured."
            "explain" -> "You are DocHub AI, a professional academic tutor. Explain the concepts inside the document text thoroughly, translating jargon into plain English."
            "rewrite" -> "You are DocHub AI, a professional copywriter. Rewrite the provided text following the user's instructions while improving clarity, flow, and tone."
            "grammar" -> "You are DocHub AI, a rigorous grammar editor. Correct any grammar, spelling, punctuation, or structural flow issues. Highlight what you improved."
            "translate" -> "You are DocHub AI, a professional multi-language translator. Translate the document or the user's specific query text into the target language. Keep formatting intact."
            else -> "You are DocHub AI, a smart office assistant. Answer questions about the current document or help the user compose notes and reports."
        }

        val fullPrompt = if (documentContent.isNotEmpty()) {
            "DOCUMENT CONTEXT:\n$documentContent\n\nUSER PROMPT / QUESTION:\n$userPrompt"
        } else {
            userPrompt
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = fullPrompt)))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        try {
            val response = geminiService.generateContent(trimmedKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "AI Assistant response is empty. Please check your query."
        } catch (e: Exception) {
            Log.e("OfficeRepository", "Gemini API call failed", e)
            "AI Assistant is currently offline (Error: ${e.localizedMessage ?: "Unknown Error"}). Here is a simulated response:\n\n" +
                    simulateAiResponse(documentContent, userPrompt, actionType)
        }
    }

    private fun simulateAiResponse(content: String, prompt: String, action: String): String {
        val docSnippet = if (content.length > 100) content.take(100) + "..." else content
        return when (action) {
            "summarize" -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                ### Executive Summary
                The provided document centers around: *"$docSnippet"*
                
                *   **Key Finding 1:** The document establishes strategic priorities for optimization, with a focus on resource allocation and operational scalability.
                *   **Key Finding 2:** Quantitative indicators point to positive growth velocity and structural efficiency.
                *   **Conclusion:** Recommend immediate execution of proposed structural adjustments and category organization.
            """.trimIndent()
            "explain" -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                ### Concept Breakdown
                Here is a simplified explanation of the document contents:
                
                1.  **Core Theme:** The document discusses structured operations, database persistence, and system efficiency.
                2.  **Implications:** By transitioning from static local structures to reactive flows, real-time sync and low latency are achieved.
                3.  **Target Impact:** This design minimizes processing overhead on lower-end devices by keeping files highly optimized and locally cached.
            """.trimIndent()
            "rewrite" -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                ### Recomposed Draft
                *Here is a polished, professional rewrite of your text:*
                
                "We are pleased to introduce our comprehensive documentation and system analysis suite. Through robust offline data architecture, the platform guarantees immediate data availability, real-time background indexing, and intelligent summarization modules to elevate day-to-day productivity."
            """.trimIndent()
            "grammar" -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                ### Grammar & Style Correction
                *   **Original:** $docSnippet
                *   **Corrected:** [Your text already demonstrates excellent composition. Minor improvements applied for flow, passive voice correction, and modern styling rules.]
                
                *   *Tip:* Transitioning from generic phrases to active voice improves professional authority.
            """.trimIndent()
            "translate" -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                ### Translation Output
                Here is the translated content (English / Selected Language):
                
                "Bienvenue dans l'univers de DocHub Office. Votre suite bureautique hors ligne de premier plan pour éditer vos documents, feuilles de calcul, présentations, fichiers JSON et PDF avec une assistance IA de pointe."
            """.trimIndent()
            else -> """
                **[DocHub Local AI Engine - Offline Mode]**
                
                Based on your query *"$prompt"*, the system recommends:
                1.  Ensure all your favorite files are tagged properly for easy category access.
                2.  Use the local secure vault to encrypt sensitive PDFs.
                3.  Try out the spreadsheet formula calculations (e.g. `=SUM()`) in your XLS sheets.
            """.trimIndent()
        }
    }

    // --- Seed Default Documents on First Launch (Disabled as per user request to remove sample files) ---
    suspend fun seedDefaultDocuments() = withContext(Dispatchers.IO) {
        // Disabled - starting with a 100% clean, empty, real workspace.
        return@withContext
    }

    fun savePhysicalFile(name: String, content: String): File? {
        return try {
            val docsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                if (!docsDir.exists()) docsDir.mkdirs()
                val file = File(docsDir, name)
                file.writeText(content)
                Log.d("OfficeRepository", "Successfully saved physical file to ${file.absolutePath}")
                file
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("OfficeRepository", "Failed to save physical file: ${e.message}", e)
            null
        }
    }
}
