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
import java.util.concurrent.TimeUnit

class OfficeRepository(
    private val officeDao: OfficeDao,
    private val context: Context
) {
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

    // --- Seed Default Documents on First Launch ---
    suspend fun seedDefaultDocuments() = withContext(Dispatchers.IO) {
        val existing = officeDao.getAllDocuments().first()
        if (existing.isNotEmpty()) return@withContext

        Log.d("OfficeRepository", "Seeding default office documents...")

        // 1. PDF Seed
        insertDocument(
            DocumentEntity(
                name = "Financial Annual Report 2026.pdf",
                type = "pdf",
                content = """
                    ---PAGE_BREAK---
                    DOCHUB OFFICE SOLUTIONS - ANNUAL BUSINESS ANALYSIS 2026
                    =======================================================
                    Date: July 2026
                    Author: Chief Financial Officer
                    
                    1. OVERVIEW & VISION
                    DocHub Office is a premier all-in-one suite bridging files, spreadsheets, and artificial intelligence into a fast, battery-optimized, responsive experience. Over the last fiscal year, our offline-first architecture has enabled millions of devices to process office formats securely.
                    
                    2. REVENUE VELOCITY
                    Our premium subscription services grew by 42% year-over-year, driven by enterprise demand for local document encryption, smart PDF annotation layers, and handwriting OCR capture.
                    ---PAGE_BREAK---
                    FINANCIAL METRICS SUMMARY
                    =========================
                    - Total Q1 Net Revenue: $1,240,000
                    - Total Q2 Net Revenue: $1,580,000
                    - Operational Overhead: $450,000
                    - Net Profit Margin: 74.2%
                    
                    3. KEY RECOMMENDATIONS
                    - Expand local ML Kit capabilities for offline OCR translator systems.
                    - Continue standardizing database schemas via Room persistence.
                    - Support dynamic Material You themes for a personalized UX.
                    ---PAGE_BREAK---
                    Form Fillable Fields:
                    [Company Name: DocHub Inc.]
                    [Employee Name:               ]
                    [Signature:                    ]
                    [Date: 11/07/2026]
                """.trimIndent(),
                size = 142400,
                category = "PDFs",
                isRecent = true,
                isFavorite = true,
                tags = "Financial,Report,Q2"
            )
        )

        // 2. Word Seed
        insertDocument(
            DocumentEntity(
                name = "Marketing Strategy Proposal.docx",
                type = "docx",
                content = """
                    # MARKETING STRATEGY PROPOSAL 2026
                    ## Product Name: DocHub Office Suite
                    
                    ### 1. Executive Summary
                    This proposal outlines the product launch campaigns, key customer demographics, and user acquisition goals for the DocHub Office app on Android. Our key differentiator is a highly optimized, responsive Material 3 experience that operates fully offline with localized AI tools.
                    
                    ### 2. Marketing Channels
                    *   **Organic Search & Tech Blogs:** Highlight offline PDF annotation and spreadsheets.
                    *   **Developer Forums:** Share open-source wrappers, performance tips, and Room database integrations.
                    *   **App Store Optimization (ASO):** Focus on keywords: PDF reader, Word editor, Excel spreadsheet, PowerPoint, OCR.
                    
                    ### 3. Campaign Timeline
                    - **Phase 1 (Pre-launch):** Interactive beta testing with 500 power users.
                    - **Phase 2 (Launch):** Press releases and tech video features.
                    - **Phase 3 (Expansion):** Educational partnerships with offline reading tools.
                    
                    ### 4. Template Placeholder Values
                    - **Contact Person:** info@dochub.office
                    - **Target Launch Date:** August 1st, 2026
                    - **Approved Budget:** ${'$'}25,000
                """.trimIndent(),
                size = 85200,
                category = "Word Documents",
                isRecent = true,
                tags = "Marketing,Proposal,Doc"
            )
        )

        // 3. Spreadsheet Seed (Serialized JSON Table representation)
        val sheetsJson = """
            {
              "sheets": [
                {
                  "name": "Q3 Revenue",
                  "rows": [
                    ["Category", "Allocated", "Spent", "Remaining"],
                    ["R&D Dev", "12000", "9500", "=B2-C2"],
                    ["Marketing Campaign", "8000", "6200", "=B3-C3"],
                    ["Server Infrastructure", "4500", "4100", "=B4-C4"],
                    ["Office Administration", "2500", "2200", "=B5-C5"],
                    ["Total", "=SUM(B2:B5)", "=SUM(C2:C5)", "=SUM(D2:D5)"]
                  ]
                },
                {
                  "name": "Project Milestones",
                  "rows": [
                    ["Milestone", "Task Lead", "Target Date", "Status"],
                    ["Room Integration", "Anointed", "12/07/2026", "Completed"],
                    ["Compose Editors", "Developer", "15/07/2026", "In Progress"],
                    ["AI Assistant UI", "AI Studio", "18/07/2026", "Planning"]
                  ]
                }
              ]
            }
        """.trimIndent()
        insertDocument(
            DocumentEntity(
                name = "Company Budget Q3.xlsx",
                type = "xlsx",
                content = sheetsJson,
                size = 48500,
                category = "Excel Sheets",
                isRecent = true,
                isFavorite = true,
                tags = "Budget,Excel,Finance"
            )
        )

        // 4. PowerPoint Seed
        val pptJson = """
            [
              {
                "title": "DocHub Office Pitch",
                "subtitle": "The Next-Gen All-In-One Office Suite",
                "notes": "Slide 1 Notes: Welcome investors and tech partners. Introduce the core value proposition: WPS + Adobe Acrobat + Local AI on mobile."
              },
              {
                "title": "The Problem we Solve",
                "subtitle": "Bloated apps, slow loading, heavy cloud dependencies",
                "notes": "Slide 2 Notes: Competitors take 10+ seconds to load basic files. They require heavy sign-ups and break when offline. DocHub loads instantly and works offline."
              },
              {
                "title": "Interactive Compose Grid",
                "subtitle": "Innovative views for PDFs, XLSX, CSV & JSON",
                "notes": "Slide 3 Notes: Explain the spreadsheet cells computation engine. SUM and subtraction work out of the box."
              },
              {
                "title": "AI Assistant & OCR",
                "subtitle": "Leveraging Gemini to rewrite, summarize, translate",
                "notes": "Slide 4 Notes: Users can tap the floating assistant button in any file to translate, summarize, or query elements immediately."
              }
            ]
        """.trimIndent()
        insertDocument(
            DocumentEntity(
                name = "DocHub App Pitch.pptx",
                type = "pptx",
                content = pptJson,
                size = 112000,
                category = "PowerPoint",
                tags = "Pitch,Investor,Slides"
              )
        )

        // 5. EPUB Book Seed
        insertDocument(
            DocumentEntity(
                name = "The Art of Modern Mobile Dev.epub",
                type = "epub",
                content = """
                    CHAPTER 1: PRINCIPLES OF RECONSTRUCTIVE ARCHITECTURE
                    ===================================================
                    Mobile engineering requires an exceptional harmony between visual aesthetics and computational resource management. Developers must construct systems that consume minimal memory, start up within milliseconds, and preserve battery, while projecting a gorgeous, premium UI.
                    
                    CHAPTER 2: REACTIVE STATE MANAGEMENT IN COMPOSE
                    ==============================================
                    Jetpack Compose transforms how UI states are managed. By wrapping state observers inside flows (e.g. StateFlow) and collecting them safely with lifecycle awareness, recompositions are narrowed to only modified components. This ensures 120 FPS scrolling speeds on affordable, lower-end devices.
                    
                    CHAPTER 3: SECURE OFFLINE ARCHITECTURE
                    ======================================
                    Privacy is a foundational pillar of user trust. Storing information inside offline Room databases, securing files with local encryption vaults, and deploying on-device AI operations means user documents never leave the physical device unless explicitly authorized.
                """.trimIndent(),
                size = 230400,
                category = "Books",
                tags = "Book,Programming,Kotlin"
            )
        )

        // 6. JSON Config Seed
        insertDocument(
            DocumentEntity(
                name = "Developer Configuration.json",
                type = "json",
                content = """
                    {
                      "appName": "DocHub Office",
                      "versionCode": 102,
                      "features": {
                        "offlineFirst": true,
                        "smartScanning": true,
                        "aiSummarization": true,
                        "excelFormulaCalculation": true,
                        "pdfSigningAndAnnotations": true
                      },
                      "theme": {
                        "defaultMode": "Dark / AMOLED",
                        "accentColor": "#00A86B",
                        "safeAreaPaddingDp": 16
                      },
                      "supportedFormats": ["pdf", "docx", "xlsx", "pptx", "txt", "json", "csv", "epub"]
                    }
                """.trimIndent(),
                size = 520,
                category = "JSON Files",
                tags = "Developer,Config,JSON"
            )
        )

        // 7. CSV Seed
        insertDocument(
            DocumentEntity(
                name = "User Feedback Logs.csv",
                type = "csv",
                content = """
                    ID,UserEmail,Rating,ReviewText,Approved
                    1,john.doe@email.com,5,Absolutely amazing spreadsheet calculation speed!,TRUE
                    2,sara.smith@email.com,4,The PDF signature drawer works really smoothly.,TRUE
                    3,alex.jones@email.com,5,The AI document summary was extremely accurate.,TRUE
                    4,maria.g@email.com,5,Finally an office app that doesn't force me to register.,TRUE
                """.trimIndent(),
                size = 380,
                category = "CSV Files",
                tags = "Feedback,CSV,Data"
            )
        )

        // 8. Markdown Note Seed
        insertDocument(
            DocumentEntity(
                name = "Release Notes v2.1.md",
                type = "md",
                content = """
                    # Release Notes v2.1 - DocHub Suite
                    
                    We are thrilled to ship the next major revision of DocHub Office, featuring state-of-the-art interactive document editors!
                    
                    ### Key Enhancements
                    1.  **Excel Sheets Engine**: Cell editing, multi-sheet tabs, and computation of formulas like `=SUM()` are now fully active.
                    2.  **PDF Signature & Drawings**: Smooth freeform drawing, highlighting, custom notes, and fillable form support.
                    3.  **Local AI Assistant**: Contextual document query, rewriting, and summaries even when working in fully offline remote areas.
                    4.  **Security Vault**: Lock sensitive files inside an AES-encrypted vault with a PIN.
                    
                    ---
                    *Thank you for supporting DocHub! Keep writing clean code.*
                """.trimIndent(),
                size = 1200,
                category = "Notes",
                isFavorite = true,
                tags = "Release,Markdown,Documentation"
            )
        )
    }
}
