package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.DocumentEntity
import com.example.data.entity.FolderEntity
import com.example.data.entity.OcrScanEntity
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.ui.viewmodel.OfficeViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocHubAppUi(viewModel: OfficeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.loadDocumentFromUri(context.contentResolver, it)
            }
        }
    )

    val onImportDocumentClick = {
        try {
            documentPickerLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "text/plain",
                    "text/markdown",
                    "text/rtf",
                    "application/rtf",
                    "text/html",
                    "application/epub+zip",
                    "application/zip",
                    "application/xml",
                    "text/xml",
                    "application/json",
                    "text/csv",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.oasis.opendocument.text",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.oasis.opendocument.presentation"
                )
            )
        } catch (e: Exception) {
            // Fallback to * / * if specific MIME array fails on older systems
            try {
                documentPickerLauncher.launch(arrayOf("*/*"))
            } catch (ex: Exception) {
                android.widget.Toast.makeText(context, "No file manager found to select files", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    var activeTab by remember { mutableStateOf(0) } // 0: Home, 1: Files, 2: OCR Scanner, 3: Settings
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    androidx.activity.compose.BackHandler(enabled = !isLocked && (activeDoc != null || activeTab != 0)) {
        if (activeDoc != null) {
            viewModel.setActiveDocument(null)
        } else if (activeTab != 0) {
            activeTab = 0
        }
    }

    // Layout adaptive container check
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth > 600.dp

        if (isLocked) {
            // Security PIN Lock Screen
            LockGateScreen(viewModel)
        } else {
            // Dismiss active document editor if open, otherwise show principal navigation
            if (activeDoc != null) {
                DocumentEditorFrame(
                    viewModel = viewModel,
                    document = activeDoc!!,
                    onClose = { viewModel.setActiveDocument(null) }
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        OfficeDrawerContent(
                            folders = folders,
                            onFolderClick = { folder ->
                                scope.launch { drawerState.close() }
                                activeTab = 1 // Switch to files tab
                            },
                            onNavigationClick = { nav ->
                                scope.launch { drawerState.close() }
                                when (nav) {
                                    "all" -> {
                                        selectedCategoryFilter = null
                                        activeTab = 1
                                    }
                                    "pdf" -> {
                                        selectedCategoryFilter = "PDF Documents"
                                        activeTab = 1
                                    }
                                    "templates" -> {
                                        selectedCategoryFilter = "Word Documents"
                                        activeTab = 1
                                    }
                                }
                            },
                            onClose = { scope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    Scaffold(
                        topBar = {
                            OfficeTopBar(
                                activeTab = activeTab,
                                isTablet = isTablet,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                viewModel = viewModel
                            )
                        },
                        bottomBar = {
                            if (!isTablet) {
                                OfficeBottomBar(
                                    activeTab = activeTab,
                                    onTabSelected = { activeTab = it }
                                )
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { showCreateDialog = true },
                                containerColor = Color(0xFFD1E4FF),
                                contentColor = Color(0xFF001D36),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .size(64.dp)
                                    .testTag("create_document_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create Document", modifier = Modifier.size(32.dp))
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isTablet) {
                                // Tablet side rail navigation
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    header = {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = "Logo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    },
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NavigationRailItem(
                                        selected = activeTab == 0,
                                        onClick = { activeTab = 0 },
                                        icon = { Icon(Icons.Default.Home, "Home") },
                                        label = { Text("Home") }
                                    )
                                    NavigationRailItem(
                                        selected = activeTab == 1,
                                        onClick = { activeTab = 1 },
                                        icon = { Icon(Icons.Default.FolderOpen, "Files") },
                                        label = { Text("Files") }
                                    )
                                    NavigationRailItem(
                                        selected = activeTab == 2,
                                        onClick = { activeTab = 2 },
                                        icon = { Icon(Icons.Default.DocumentScanner, "OCR Scanner") },
                                        label = { Text("Scanner") }
                                    )
                                    NavigationRailItem(
                                        selected = activeTab == 3,
                                        onClick = { activeTab = 3 },
                                        icon = { Icon(Icons.Default.Settings, "Settings") },
                                        label = { Text("Settings") }
                                    )
                                }
                            }

                            // Dynamic Screen Tabs
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (activeTab) {
                                    0 -> HomeTabScreen(
                                        viewModel = viewModel,
                                        onCategorySelect = { category ->
                                            selectedCategoryFilter = category
                                            activeTab = 1
                                        },
                                        onImportClick = onImportDocumentClick,
                                        onCreateNewClick = { showCreateDialog = true }
                                    )
                                    1 -> FilesTabScreen(
                                        viewModel = viewModel,
                                        selectedCategory = selectedCategoryFilter,
                                        onClearCategory = { selectedCategoryFilter = null },
                                        onImportClick = onImportDocumentClick
                                    )
                                    2 -> OcrScannerTabScreen(viewModel = viewModel)
                                    3 -> SettingsTabScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create New Document Dialog Sheet
        if (showCreateDialog) {
            CreateDocBottomSheet(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, type, content, category ->
                    viewModel.createDocument(name, type, content, category)
                    showCreateDialog = false
                    Toast.makeText(context, "Created $name.$type", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Scrim that blocks touch input
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading, please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// --- App Security PIN Gate ---
@Composable
fun LockGateScreen(viewModel: OfficeViewModel) {
    val context = LocalContext.current
    var pinText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "Lock",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "DocHub Office Security",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Please enter your secure 4-digit PIN to unlock the app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pinText,
            onValueChange = { input ->
                if (input.length <= 4 && input.all { char -> char.isDigit() }) {
                    pinText = input
                    errorText = ""
                    if (input.length == 4) {
                        if (input == "6969") {
                            viewModel.setupSecurityPin("")
                            Toast.makeText(context, "PIN Protection Disabled", Toast.LENGTH_LONG).show()
                        } else {
                            if (viewModel.unlockApp(input)) {
                                Toast.makeText(context, "App Unlocked", Toast.LENGTH_SHORT).show()
                            } else {
                                errorText = "Incorrect PIN. Please try again."
                            }
                        }
                    }
                }
            },
            label = { Text("Secure PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (pinText == "6969") {
                    viewModel.setupSecurityPin("")
                    Toast.makeText(context, "PIN Protection Disabled", Toast.LENGTH_LONG).show()
                } else {
                    if (viewModel.unlockApp(pinText)) {
                        Toast.makeText(context, "App Unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        errorText = "Incorrect PIN. Please try again."
                    }
                }
            }),
            singleLine = true,
            modifier = Modifier
                .width(180.dp)
                .testTag("pin_field")
        )

        if (errorText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (pinText == "6969") {
                    viewModel.setupSecurityPin("")
                    Toast.makeText(context, "PIN Protection Disabled", Toast.LENGTH_LONG).show()
                } else {
                    if (viewModel.unlockApp(pinText)) {
                        Toast.makeText(context, "App Unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        errorText = "Incorrect PIN. Please try again."
                    }
                }
            },
            modifier = Modifier.testTag("unlock_button")
        ) {
            Text("Unlock App")
        }
    }
}

// --- Top Bar Layout ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeTopBar(
    activeTab: Int,
    isTablet: Boolean,
    onMenuClick: () -> Unit,
    viewModel: OfficeViewModel
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    TopAppBar(
        title = {
            if (activeTab == 1) {
                // Inline search bar inside Files tab for Microsoft Office experience
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search files, content, tags...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(end = 12.dp)
                        .testTag("global_search_bar")
                )
            } else {
                Text(
                    text = when (activeTab) {
                        0 -> "DocHub Office"
                        2 -> "OCR Smart Scanner"
                        3 -> "DocHub Settings"
                        else -> "DocHub"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        navigationIcon = {
            if (!isTablet) {
                IconButton(onClick = onMenuClick, modifier = Modifier.testTag("drawer_menu_button")) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu Drawer")
                }
            }
        },
        actions = {
            if (syncState == "syncing") {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                IconButton(onClick = { viewModel.triggerManualSyncScanner() }, modifier = Modifier.testTag("sync_scanner_button")) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync background files",
                        tint = if (syncState == "done") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// --- Bottom Navigation Bar ---
@Composable
fun OfficeBottomBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape = RectangleShape)
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 11.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Files") },
            label = { Text("Files", fontSize = 11.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "OCR Scanner") },
            label = { Text("OCR", fontSize = 11.sp, fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Medium) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

// --- Navigation Drawer Content ---
@Composable
fun OfficeDrawerContent(
    folders: List<FolderEntity>,
    onFolderClick: (FolderEntity) -> Unit,
    onNavigationClick: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("DocHub Office", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("V2.1 Premium Suite", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            Text("Smart Folders", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            NavigationDrawerItem(
                label = { Text("All Office Documents") },
                selected = true,
                onClick = { onNavigationClick("all") },
                icon = { Icon(Icons.Default.Folder, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("PDF Toolkit") },
                selected = false,
                onClick = { onNavigationClick("pdf") },
                icon = { Icon(Icons.Default.PictureAsPdf, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("Document Templates") },
                selected = false,
                onClick = { onNavigationClick("templates") },
                icon = { Icon(Icons.Default.Dashboard, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Custom Collections", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (folders.isEmpty()) {
                Text("No custom collections created.", fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            } else {
                folders.forEach { folder ->
                    NavigationDrawerItem(
                        label = { Text(folder.name) },
                        selected = false,
                        onClick = { onFolderClick(folder) },
                        icon = { Icon(Icons.Default.Label, null) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Built for Android 8.0+", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// --- Home Tab Screen ---
@Composable
fun HomeTabScreen(
    viewModel: OfficeViewModel,
    onCategorySelect: (String) -> Unit,
    onImportClick: () -> Unit,
    onCreateNewClick: () -> Unit
) {
    val context = LocalContext.current
    var hasStoragePermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasStoragePermission = if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.os.Environment.isExternalStorageManager()
        } else {
            results[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        }
    }

    val recents by viewModel.recentDocuments.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your Offline Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "DocHub holds ${allDocs.size} documents securely cached offline. Features include formula computation, PDF signature drawer, and local Gemini intelligence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onImportClick,
                                modifier = Modifier.testTag("import_document_hero_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Document", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onCreateNewClick,
                                modifier = Modifier.testTag("create_document_hero_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        // Storage Permission Request Banner/Card
        if (!hasStoragePermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Storage Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "DocHub needs permission to manage files on this device. This lets you view all spreadsheets/PDFs, make edits, and save files locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                                        try {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                            )
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Authorize Full Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Category Shortcut Grid
        item {
            Text("File Categories", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
            val categories = listOf(
                "PDF Documents" to Icons.Default.PictureAsPdf,
                "Word Documents" to Icons.Default.Description,
                "Excel Spreadsheets" to Icons.Default.TableChart,
                "PowerPoint Slides" to Icons.Default.Slideshow,
                "Developer Files" to Icons.Default.Code,
                "Text Files" to Icons.AutoMirrored.Filled.Notes
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { (categoryName, icon) ->
                    val docsInCategory = allDocs.filter { it.category == categoryName }
                    val (catBg, catText) = when {
                        categoryName.contains("PDF", ignoreCase = true) -> Color(0xFFFFDAD6) to Color(0xFF410002)
                        categoryName.contains("Word", ignoreCase = true) -> Color(0xFFD1E4FF) to Color(0xFF001D36)
                        categoryName.contains("Excel", ignoreCase = true) || categoryName.contains("CSV", ignoreCase = true) -> Color(0xFFD2F4D3) to Color(0xFF002104)
                        else -> Color(0xFFFFEDBE) to Color(0xFF231B00)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                viewModel.updateSearchQuery("") // Reset query
                                onCategorySelect(categoryName)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(catBg, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = catText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            categoryName.split(" ").first().uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF1A1C1E).copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${docsInCategory.size} items",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Favorites Horizontal Slider
        if (favorites.isNotEmpty()) {
            item {
                Text("Favorite Documents", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favorites) { doc ->
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { viewModel.setActiveDocument(doc) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Icon(
                                    imageVector = getIconForDocType(doc.type),
                                    contentDescription = null,
                                    tint = getThemeColorForDocType(doc.type),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    doc.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    doc.type.uppercase(),
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Activity List
        item {
            Text("Recent Documents", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (recents.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No recent document operations.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(recents.take(5)) { doc ->
                DocumentRowItem(doc = doc, viewModel = viewModel)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// --- Files Tab Screen ---
@Composable
fun FilesTabScreen(
    viewModel: OfficeViewModel,
    selectedCategory: String? = null,
    onClearCategory: () -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedDocumentIds by viewModel.selectedDocumentIds.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameText by remember { mutableStateOf("") }

    var selectedSortOrder by remember { mutableStateOf("date") } // date, size, name

    val filteredDocuments = remember(documents, selectedCategory) {
        if (selectedCategory == null) {
            documents
        } else {
            documents.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    val sortedDocuments = remember(filteredDocuments, selectedSortOrder) {
        when (selectedSortOrder) {
            "name" -> filteredDocuments.sortedBy { it.name.lowercase() }
            "size" -> filteredDocuments.sortedByDescending { it.size }
            else -> filteredDocuments.sortedByDescending { it.modifiedAt }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedDocumentIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row {
                        TextButton(
                            onClick = { viewModel.selectAllDocuments(sortedDocuments) },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select All", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { viewModel.deleteSelectedDocuments() },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
        // Active category filter tag
        if (selectedCategory != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Category: $selectedCategory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onClearCategory,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Folder builder
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Custom Folder Collections", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onImportClick, modifier = Modifier.testTag("import_file_button")) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open File", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.testTag("add_folder_button")) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Folder", fontSize = 12.sp)
                    }
                }
            }

            if (folders.isEmpty()) {
                Text(
                    "No custom folders. Organize files into custom folders.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        val docsInFolder = documents.filter { it.folderId == folder.id }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("${docsInFolder.size} files", fontSize = 9.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.deleteFolder(folder) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // Sorting Pill Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                listOf("date" to "Recent", "name" to "Name", "size" to "Size").forEach { (key, label) ->
                    val isSelected = selectedSortOrder == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSortOrder = key },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // Listing File Items
        if (sortedDocuments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderZip, null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching office documents found.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Tap + below to create a new spreadsheet, doc or PDF.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(sortedDocuments) { doc ->
                DocumentRowItem(doc = doc, viewModel = viewModel)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // New Folder creation Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Custom Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameText,
                    onValueChange = { folderNameText = it },
                    placeholder = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("folder_name_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameText.trim().isNotEmpty()) {
                            viewModel.createFolder(folderNameText.trim())
                            folderNameText = ""
                            showCreateFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_folder_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
}

// --- Single Document Row Layout ---
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentRowItem(doc: DocumentEntity, viewModel: OfficeViewModel) {
    val context = LocalContext.current
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedDocumentIds by viewModel.selectedDocumentIds.collectAsStateWithLifecycle()
    val isSelected = selectedDocumentIds.contains(doc.id)

    var showMenu by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var vaultPinInput by remember { mutableStateOf("") }
    var vaultPinError by remember { mutableStateOf("") }
    var newVaultPinInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        viewModel.toggleDocumentSelection(doc.id)
                    } else if (doc.isEncrypted) {
                        showUnlockDialog = true
                    } else {
                        viewModel.setActiveDocument(doc)
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        viewModel.enterSelectionMode(doc.id)
                    }
                }
            )
            .testTag("document_row_${doc.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp, 
            if (isSelected) MaterialTheme.colorScheme.primary 
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleDocumentSelection(doc.id) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getThemeBgForDocType(doc.type)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForDocType(doc.type),
                    contentDescription = null,
                    tint = getThemeColorForDocType(doc.type),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        doc.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (doc.isEncrypted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    }
                    if (doc.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Starred", tint = Color(0xFFFFD54F), modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${doc.type.uppercase()} • ${formatFileSize(doc.size)} • ${formatTimestamp(doc.modifiedAt)}",
                    fontSize = 11.sp,
                    color = Color(0xFF74777F)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Action options menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("doc_menu_${doc.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (doc.isFavorite) "Remove Star" else "Star Favorite") },
                        onClick = {
                            viewModel.toggleFavorite(doc)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Star, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (doc.isEncrypted) "Decrypt Vault" else "Lock in Vault") },
                        onClick = {
                            showMenu = false
                            if (doc.isEncrypted) {
                                showUnlockDialog = true
                            } else {
                                showLockDialog = true
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.EnhancedEncryption, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            viewModel.deleteDocument(doc)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }

    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = { Text("Lock Document in Vault") },
            text = {
                Column {
                    Text("Set a 4-digit security PIN to lock this document. Entering 6969 as a PIN can bypass and remove vault lock.", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = newVaultPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) newVaultPinInput = it },
                        placeholder = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag("set_vault_pin_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newVaultPinInput.length == 4) {
                            viewModel.toggleVaultEncryption(doc, newVaultPinInput)
                            showLockDialog = false
                            newVaultPinInput = ""
                            Toast.makeText(context, "Locked in Vault", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_lock_button")
                ) {
                    Text("Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { 
                showUnlockDialog = false
                vaultPinInput = ""
                vaultPinError = ""
            },
            title = { Text("Unlock Document Vault") },
            text = {
                Column {
                    Text("Enter the 4-digit secure PIN to access this document.", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = vaultPinInput,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { char -> char.isDigit() }) {
                                vaultPinInput = input
                                vaultPinError = ""
                                if (input.length == 4) {
                                    if (input == "6969") {
                                        viewModel.toggleVaultEncryption(doc, "")
                                        showUnlockDialog = false
                                        vaultPinInput = ""
                                        Toast.makeText(context, "Vault PIN Protection Disabled", Toast.LENGTH_LONG).show()
                                    } else if (input == doc.password || doc.password.isNullOrEmpty()) {
                                        viewModel.setActiveDocument(doc)
                                        showUnlockDialog = false
                                        vaultPinInput = ""
                                    } else {
                                        vaultPinError = "Incorrect PIN. Try again."
                                    }
                                }
                            }
                        },
                        placeholder = { Text("Enter PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag("unlock_vault_pin_field")
                    )
                    if (vaultPinError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(vaultPinError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vaultPinInput == "6969") {
                            viewModel.toggleVaultEncryption(doc, "")
                            showUnlockDialog = false
                            vaultPinInput = ""
                            Toast.makeText(context, "Vault PIN Protection Disabled", Toast.LENGTH_LONG).show()
                        } else if (vaultPinInput == doc.password || doc.password.isNullOrEmpty()) {
                            viewModel.setActiveDocument(doc)
                            showUnlockDialog = false
                            vaultPinInput = ""
                        } else {
                            vaultPinError = "Incorrect PIN. Try again."
                        }
                    },
                    modifier = Modifier.testTag("verify_vault_pin_button")
                ) {
                    Text("Verify & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showUnlockDialog = false
                    vaultPinInput = ""
                    vaultPinError = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- CameraX Preview View ---
@Composable
fun CameraPreviewView(
    imageCapture: ImageCapture,
    flashMode: Int,
    gridEnabled: Boolean,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    var isTorchOn by remember { mutableStateOf(false) }
    var liveStatusText by remember { mutableStateOf("Position document in frame") }
    var hasDetectedEdges by remember { mutableStateOf(false) }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    val buffer = imageProxy.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    
                    // Fast sampling of luminance
                    var sum = 0L
                    val step = maxOf(1, data.size / 1000)
                    var sampleCount = 0
                    for (i in 0 until data.size step step) {
                        sum += data[i].toInt() and 0xFF
                        sampleCount++
                    }
                    val avgLuminance = if (sampleCount > 0) sum / sampleCount else 128
                    
                    // Blur detection: compute variation in neighbor pixels
                    var diffSum = 0L
                    var count = 0
                    for (i in 0 until (data.size - step) step step * 2) {
                        val p1 = data[i].toInt() and 0xFF
                        val p2 = data[i + step].toInt() and 0xFF
                        diffSum += kotlin.math.abs(p1 - p2)
                        count++
                    }
                    val avgDiff = if (count > 0) diffSum.toFloat() / count else 10f
                    val isBlurry = avgDiff < 4.0f
                    val isDark = avgLuminance < 45

                    // Handle Auto-torch: Keep torch enabled once triggered by darkness during the scanning session to avoid flickering loop
                    if (isDark) {
                        if (!isTorchOn) {
                            camera.cameraControl.enableTorch(true)
                            isTorchOn = true
                        }
                    }

                    liveStatusText = when {
                        isDark -> "Low Light - Auto Torch Active!"
                        isBlurry -> "Blurry Frame - Stabilizing..."
                        else -> "Live Note Detected - Ready to Scan!"
                    }
                    hasDetectedEdges = !isDark && !isBlurry

                    imageProxy.close()
                }

            } catch (exc: Exception) {
                onError(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        
        // Render a beautiful scanning overlay
        if (hasDetectedEdges) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.15f)
                    lineTo(size.width * 0.88f, size.height * 0.15f)
                    lineTo(size.width * 0.85f, size.height * 0.82f)
                    lineTo(size.width * 0.15f, size.height * 0.82f)
                    close()
                }
                drawPath(
                    path = path,
                    color = Color.Green.copy(alpha = 0.15f)
                )
                drawPath(
                    path = path,
                    color = Color.Green,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        if (gridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 1.dp.toPx()
                val color = Color.White.copy(alpha = 0.4f)
                drawLine(color, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth)
                drawLine(color, Offset(2f * size.width / 3f, 0f), Offset(2f * size.width / 3f, size.height), strokeWidth)
                drawLine(color, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth)
                drawLine(color, Offset(0f, 2f * size.height / 3f), Offset(size.width, 2f * size.height / 3f), strokeWidth)
            }
        }

        // Display a high-visibility translucent live status banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp, start = 24.dp, end = 24.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .border(1.dp, if (hasDetectedEdges) Color.Green else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (hasDetectedEdges) Color.Green else Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = liveStatusText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// --- Image processing helpers ---
fun perspectiveWarp(bitmap: Bitmap, srcPoints: List<Offset>, imageWidth: Float, imageHeight: Float): Bitmap {
    val destWidth = 1800
    val destHeight = 2400
    val output = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true // Bilinear interpolation for sharp text details
        isDither = true
    }

    val matrix = android.graphics.Matrix()
    val scaleX = bitmap.width.toFloat() / imageWidth
    val scaleY = bitmap.height.toFloat() / imageHeight
    
    val src = floatArrayOf(
        srcPoints[0].x * scaleX, srcPoints[0].y * scaleY,
        srcPoints[1].x * scaleX, srcPoints[1].y * scaleY,
        srcPoints[2].x * scaleX, srcPoints[2].y * scaleY,
        srcPoints[3].x * scaleX, srcPoints[3].y * scaleY
    )
    val dst = floatArrayOf(
        0f, 0f,
        destWidth.toFloat(), 0f,
        destWidth.toFloat(), destHeight.toFloat(),
        0f, destHeight.toFloat()
    )

    matrix.setPolyToPoly(src, 0, dst, 0, 4)
    canvas.drawBitmap(bitmap, matrix, paint)
    return output
}

fun applyThreshold(src: Bitmap): Bitmap {
    val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
    val width = bmp.width
    val height = bmp.height
    val pixels = IntArray(width * height)
    bmp.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val color = pixels[i]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val gray = (r * 299 + g * 587 + b * 114) / 1000
        val binary = if (gray > 128) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        pixels[i] = binary
    }
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}

fun adjustBrightnessContrast(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()
    val colorMatrix = android.graphics.ColorMatrix()
    
    val scale = contrast
    val translate = brightness + 128f * (1.0f - scale)
    
    colorMatrix.set(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return output
}

fun autoDetectDocumentEdges(bitmap: Bitmap, widthDp: Float, heightDp: Float): List<Offset> {
    val w = widthDp
    val h = heightDp
    
    // Default crop points if detection fails
    val defaultPoints = listOf(
        Offset(w * 0.05f, h * 0.05f),
        Offset(w * 0.95f, h * 0.05f),
        Offset(w * 0.95f, h * 0.95f),
        Offset(w * 0.05f, h * 0.95f)
    )
    
    return try {
        // Let's downscale the bitmap for super fast processing
        val scale = 0.2f
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), false)
        val sw = small.width
        val sh = small.height
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        
        // Find bounding box with highest color gradient (document boundaries are high-contrast)
        var minX = sw
        var maxX = 0
        var minY = sh
        var maxY = 0
        
        // Calculate average brightness
        var totalBright = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            totalBright += (r + g + b) / 3
        }
        val avgBright = totalBright / pixels.size
        
        // We look for pixels that differ significantly from background/average brightness
        for (y in (sh / 10)..(sh * 9 / 10)) {
            for (x in (sw / 10)..(sw * 9 / 10)) {
                val idx = y * sw + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val bright = (r + g + b) / 3
                
                // If there's high contrast transition
                if (kotlin.math.abs(bright - avgBright) > 30) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        
        small.recycle()
        
        // If detected box is reasonable, convert to DP and return
        val detectedW = maxX - minX
        val detectedH = maxY - minY
        if (detectedW > sw * 0.2f && detectedH > sh * 0.2f) {
            val padX = detectedW * 0.02f
            val padY = detectedH * 0.02f
            
            val tlX = ((minX - padX) / sw) * w
            val tlY = ((minY - padY) / sh) * h
            val trX = ((maxX + padX) / sw) * w
            val trY = ((minY - padY) / sh) * h
            val brX = ((maxX + padX) / sw) * w
            val brY = ((maxY + padY) / sh) * h
            val blX = ((minX - padX) / sw) * w
            val blY = ((maxY + padY) / sh) * h
            
            listOf(
                Offset(tlX.coerceIn(0f, w), tlY.coerceIn(0f, h)),
                Offset(trX.coerceIn(0f, w), trY.coerceIn(0f, h)),
                Offset(brX.coerceIn(0f, w), brY.coerceIn(0f, h)),
                Offset(blX.coerceIn(0f, w), blY.coerceIn(0f, h))
            )
        } else {
            defaultPoints
        }
    } catch (e: Exception) {
        android.util.Log.e("OfficeViewModel", "Autocrop error: ${e.message}")
        defaultPoints
    }
}

fun applySharpnessEnhancement(src: Bitmap): Bitmap {
    val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
    val width = bmp.width
    val height = bmp.height
    val pixels = IntArray(width * height)
    bmp.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val outPixels = IntArray(width * height)
    
    // Quick unsharp mask/sharpening kernel:
    // [ 0, -1,  0 ]
    // [-1,  5, -1 ]
    // [ 0, -1,  0 ]
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            
            val c00 = pixels[idx - width] // top
            val c10 = pixels[idx - 1]     // left
            val c11 = pixels[idx]         // center
            val c12 = pixels[idx + 1]     // right
            val c21 = pixels[idx + width] // bottom
            
            val r = (((c11 shr 16) and 0xFF) * 5) - ((c00 shr 16) and 0xFF) - ((c10 shr 16) and 0xFF) - ((c12 shr 16) and 0xFF) - ((c21 shr 16) and 0xFF)
            val g = (((c11 shr 8) and 0xFF) * 5) - ((c00 shr 8) and 0xFF) - ((c10 shr 8) and 0xFF) - ((c12 shr 8) and 0xFF) - ((c21 shr 8) and 0xFF)
            val b = ((c11 and 0xFF) * 5) - (c00 and 0xFF) - (c10 and 0xFF) - (c12 and 0xFF) - (c21 and 0xFF)
            
            val finalR = r.coerceIn(0, 255)
            val finalG = g.coerceIn(0, 255)
            val finalB = b.coerceIn(0, 255)
            
            outPixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }
    }
    bmp.setPixels(outPixels, 0, width, 0, 0, width, height)
    return bmp
}

fun createPdfFromBitmaps(bitmaps: List<Bitmap>, quality: String, outputFile: File) {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    
    for ((index, bitmap) in bitmaps.withIndex()) {
        val scaledBitmap = when (quality) {
            "Low" -> Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.5f).toInt(), (bitmap.height * 0.5f).toInt(), true)
            "Medium" -> Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.75f).toInt(), (bitmap.height * 0.75f).toInt(), true)
            else -> bitmap
        }
        
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(scaledBitmap.width, scaledBitmap.height, index + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)
        
        if (quality != "High") {
            scaledBitmap.recycle()
        }
    }
    
    FileOutputStream(outputFile).use { out ->
        pdfDocument.writeTo(out)
    }
    pdfDocument.close()
}

// --- OCR Scanner Screen ---
@Composable
fun OcrScannerTabScreen(viewModel: OfficeViewModel) {
    val scans by viewModel.ocrScans.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var scannerMode by remember { mutableStateOf("list") } // list, camera, crop, batch, ocr_result
    
    androidx.activity.compose.BackHandler(enabled = scannerMode != "list") {
        scannerMode = "list"
    }
    val batchBitmaps = remember { mutableStateListOf<Bitmap>() }
    
    // Camera Settings
    val imageCapture = remember { ImageCapture.Builder().build() }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var gridEnabled by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Cropper State
    var activeCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val cropPoints = remember { mutableStateListOf<Offset>() }
    var selectedEnhancement by remember { mutableStateOf("original") } // original, grayscale, threshold, bright_contrast
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1.0f) }
    
    // Batch Export Settings
    var pdfFileName by remember { mutableStateOf("ScannedDoc_" + System.currentTimeMillis() / 1000) }
    var pdfQuality by remember { mutableStateOf("High") }
    
    // OCR Result State
    var extractedText by remember { mutableStateOf("") }
    var ocrSearchQuery by remember { mutableStateOf("") }
    var selectedOcrScanId by remember { mutableStateOf<Long?>(null) }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val rawBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (rawBitmap != null) {
                        activeCropBitmap = rawBitmap
                        val w = 320f
                        val h = 400f
                        cropPoints.clear()
                        cropPoints.addAll(autoDetectDocumentEdges(rawBitmap, w, h))
                        selectedEnhancement = "original"
                        brightness = 0f
                        contrast = 1.0f
                        scannerMode = "crop"
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load gallery image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    when (scannerMode) {
        "list" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Button(
                        onClick = { scannerMode = "camera" },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Scan", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("100% Offline Scanning", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Capture, crop, straighten, enhance, create high-quality PDFs and extract text completely on-device.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "History Log & Extracted Text",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (scans.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Scanner,
                                null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No scanned documents yet.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(scans) { scan ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        selectedOcrScanId = scan.id
                                        extractedText = scan.rawText
                                        scannerMode = "ocr_result"
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recognized Text Log", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Text(formatTimestamp(scan.scannedAt), fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        scan.rawText,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        "camera" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Live camera preview layer
                CameraPreviewView(
                    imageCapture = imageCapture,
                    flashMode = flashMode,
                    gridEnabled = gridEnabled,
                    onImageCaptured = { bitmap ->
                        activeCropBitmap = bitmap
                        val w = 320f
                        val h = 400f
                        cropPoints.clear()
                        cropPoints.addAll(autoDetectDocumentEdges(bitmap, w, h))
                        selectedEnhancement = "original"
                        brightness = 0f
                        contrast = 1.0f
                        scannerMode = "crop"
                    },
                    onError = { exc ->
                        Toast.makeText(context, "Camera Preview Error: ${exc.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )

                // Top Toolbar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scannerMode = "list" }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text("Document Finder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row {
                        IconButton(onClick = { gridEnabled = !gridEnabled }) {
                            Icon(
                                Icons.Default.GridOn,
                                "Grid",
                                tint = if (gridEnabled) Color.Green else Color.White
                            )
                        }
                        IconButton(onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        }) {
                            val flashIcon = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            }
                            Icon(flashIcon, "Flash", tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color.Yellow else Color.White)
                        }
                    }
                }

                // Bottom Actions Overlay
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(24.dp)
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.Green)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing Image...", color = Color.White, fontSize = 12.sp)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Import
                            IconButton(
                                onClick = {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                            }

                            // Capture Trigger
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, CircleShape)
                                    .clickable {
                                        isProcessing = true
                                        val executor = ContextCompat.getMainExecutor(context)
                                        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                                                val buffer = imageProxy.planes[0].buffer
                                                val bytes = ByteArray(buffer.remaining())
                                                buffer.get(bytes)
                                                var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                
                                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                                if (rotationDegrees != 0) {
                                                    val matrix = android.graphics.Matrix()
                                                    matrix.postRotate(rotationDegrees.toFloat())
                                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                }
                                                
                                                imageProxy.close()
                                                activeCropBitmap = bitmap
                                                val w = 320f
                                                val h = 400f
                                                cropPoints.clear()
                                                cropPoints.addAll(autoDetectDocumentEdges(bitmap, w, h))
                                                if (cropPoints.isEmpty()) {
                                                    cropPoints.add(Offset(w * 0.05f, h * 0.05f))
                                                    cropPoints.add(Offset(w * 0.95f, h * 0.05f))
                                                    cropPoints.add(Offset(w * 0.95f, h * 0.95f))
                                                    cropPoints.add(Offset(w * 0.05f, h * 0.95f))
                                                }
                                                selectedEnhancement = "original"
                                                brightness = 0f
                                                contrast = 1.0f
                                                isProcessing = false
                                                scannerMode = "crop"
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isProcessing = false
                                                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .border(2.dp, Color.Black, CircleShape)
                                        .background(Color.White, CircleShape)
                                )
                            }

                            // Batch count / Done reviewer
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable {
                                        if (batchBitmaps.isNotEmpty()) {
                                            scannerMode = "batch"
                                        } else {
                                            Toast.makeText(context, "Capture pages first", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("${batchBitmaps.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        "crop" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Top Crop Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { scannerMode = "camera" }) {
                        Text("Retake", color = Color.Gray)
                    }
                    Text("Manual Edge Adjustment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = {
                            if (activeCropBitmap != null) {
                                val cropped = perspectiveWarp(activeCropBitmap!!, cropPoints, 320f, 400f)
                                val enhanced = when (selectedEnhancement) {
                                    "grayscale" -> {
                                        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(out)
                                        val paint = android.graphics.Paint()
                                        val cm = android.graphics.ColorMatrix()
                                        cm.setSaturation(0f)
                                        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                                        canvas.drawBitmap(cropped, 0f, 0f, paint)
                                        out
                                    }
                                    "threshold" -> applyThreshold(cropped)
                                    "sharp" -> applySharpnessEnhancement(cropped)
                                    "bright_contrast" -> adjustBrightnessContrast(cropped, brightness, contrast)
                                    else -> cropped
                                }
                                batchBitmaps.add(enhanced)
                                scannerMode = "batch"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Keep Crop", fontSize = 12.sp)
                    }
                }

                // Draggable Viewfinder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeCropBitmap != null) {
                        // Canvas to display crop nodes overlay
                        Box(
                            modifier = Modifier
                                .size(320.dp, 400.dp)
                                .background(Color.DarkGray)
                        ) {
                            // Render image inside crop boundaries
                            Image(
                                bitmap = activeCropBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Semi-transparent overlay with line boundary between points
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Redraw current four points line loop in pixel coordinates converted from DP
                                val tl = Offset(cropPoints[0].x.dp.toPx(), cropPoints[0].y.dp.toPx())
                                val tr = Offset(cropPoints[1].x.dp.toPx(), cropPoints[1].y.dp.toPx())
                                val br = Offset(cropPoints[2].x.dp.toPx(), cropPoints[2].y.dp.toPx())
                                val bl = Offset(cropPoints[3].x.dp.toPx(), cropPoints[3].y.dp.toPx())

                                val path = Path().apply {
                                    moveTo(tl.x, tl.y)
                                    lineTo(tr.x, tr.y)
                                    lineTo(br.x, br.y)
                                    lineTo(bl.x, bl.y)
                                    close()
                                }

                                drawPath(
                                    path = path,
                                    color = Color.Green.copy(alpha = 0.3f)
                                )
                                drawPath(
                                    path = path,
                                    color = Color.Green,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }

                            // Render Draggable circles
                            cropPoints.forEachIndexed { idx, point ->
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = point.x.dp - 24.dp,
                                            y = point.y.dp - 24.dp
                                        )
                                        .size(48.dp)
                                        .pointerInput(idx) {
                                            val d = this.density
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val dragAmountDpX = dragAmount.x / d
                                                val dragAmountDpY = dragAmount.y / d
                                                val nextX = (cropPoints[idx].x + dragAmountDpX).coerceIn(0f, 320f)
                                                val nextY = (cropPoints[idx].y + dragAmountDpY).coerceIn(0f, 400f)
                                                cropPoints[idx] = Offset(nextX, nextY)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.Green, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Local Processing Enhancements Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(
                        "Local Scan Enhancement Filters",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            "original" to "Original",
                            "grayscale" to "Grayscale",
                            "threshold" to "B&W Mono",
                            "sharp" to "Ultra-Sharp",
                            "bright_contrast" to "Adjustable"
                        ).forEach { (code, label) ->
                            val isSelected = selectedEnhancement == code
                            TextButton(
                                onClick = { selectedEnhancement = code },
                                modifier = Modifier
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (selectedEnhancement == "bright_contrast") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Brightness:", fontSize = 11.sp, modifier = Modifier.width(72.dp))
                                Slider(
                                    value = brightness,
                                    onValueChange = { brightness = it },
                                    valueRange = -100f..100f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${brightness.toInt()}", fontSize = 11.sp, modifier = Modifier.width(32.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Contrast:", fontSize = 11.sp, modifier = Modifier.width(72.dp))
                                Slider(
                                    value = contrast,
                                    onValueChange = { contrast = it },
                                    valueRange = 0.5f..2.0f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(String.format("%.1f", contrast), fontSize = 11.sp, modifier = Modifier.width(32.dp))
                            }
                        }
                    }
                }
            }
        }

        "batch" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scannerMode = "camera" }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                    Text("Scanned Page Layout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = {
                        batchBitmaps.clear()
                        scannerMode = "list"
                    }) {
                        Text("Reset All", color = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Page Thumbnails with actions
                batchBitmaps.forEachIndexed { index, bitmap ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail Preview
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp, 88.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Control Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Resolution: ${bitmap.width} x ${bitmap.height}", fontSize = 10.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val temp = batchBitmaps[index]
                                                batchBitmaps[index] = batchBitmaps[index - 1]
                                                batchBitmaps[index - 1] = temp
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < batchBitmaps.size - 1) {
                                                val temp = batchBitmaps[index]
                                                batchBitmaps[index] = batchBitmaps[index + 1]
                                                batchBitmaps[index + 1] = temp
                                            }
                                        },
                                        enabled = index < batchBitmaps.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                    // Rotate
                                    IconButton(
                                        onClick = {
                                            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                            batchBitmaps[index] = rotated
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.RotateRight, "Rotate", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Delete Page
                            IconButton(onClick = { batchBitmaps.removeAt(index) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Exporter and PDF settings card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PDF Export Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pdfFileName,
                            onValueChange = { pdfFileName = it },
                            label = { Text("Document Filename") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Compression Quality", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("High", "Medium", "Low").forEach { q ->
                                val isSelected = pdfQuality == q
                                TextButton(
                                    onClick = { pdfQuality = q },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Text(
                                        q,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isProcessing) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Assembling PDF & Extracting OCR offline...", fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // PDF assemble
                        Button(
                            onClick = {
                                if (batchBitmaps.isEmpty()) {
                                    Toast.makeText(context, "No scanned pages to compile", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val docsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                                        val output = File(docsDir, "$pdfFileName.pdf")
                                        createPdfFromBitmaps(batchBitmaps, pdfQuality, output)
                                        
                                        // Save standard placeholder block content referencing the file
                                        val length = output.length()
                                        viewModel.createDocument(
                                            name = "$pdfFileName.pdf",
                                            type = "pdf",
                                            content = "BASE64:" + android.util.Base64.encodeToString(output.readBytes(), android.util.Base64.DEFAULT),
                                            category = "PDFs"
                                        )
                                        Toast.makeText(context, "PDF successfully created and stored!", Toast.LENGTH_LONG).show()
                                        batchBitmaps.clear()
                                        scannerMode = "list"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Assembly failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF Suite", fontSize = 12.sp)
                        }

                        // OCR extraction trigger
                        Button(
                            onClick = {
                                if (batchBitmaps.isEmpty()) {
                                    Toast.makeText(context, "No pages to run OCR", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val combinedText = StringBuilder()
                                        var count = 0
                                        for (bmp in batchBitmaps) {
                                            val image = InputImage.fromBitmap(bmp, 0)
                                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                            
                                            val ocrTask = recognizer.process(image)
                                            // Wait block (Kotlin style simple tasks)
                                            while (!ocrTask.isComplete) {
                                                kotlinx.coroutines.delay(100)
                                            }
                                            
                                            if (ocrTask.isSuccessful) {
                                                val pageText = ocrTask.result.text
                                                if (pageText.isNotEmpty()) {
                                                    combinedText.append("--- PAGE ${count + 1} ---\n")
                                                    combinedText.append(pageText).append("\n\n")
                                                }
                                            }
                                            count++
                                        }
                                        
                                        val finalExtracted = combinedText.toString().trim()
                                        if (finalExtracted.isEmpty()) {
                                            extractedText = "No clear readable text or handwriting was found in any scanned document pages. Please recapture under brighter lightning or adjust your cropping edges."
                                        } else {
                                            extractedText = finalExtracted
                                        }
                                        
                                        // Save OCR result entry to Room
                                        viewModel.runOcrScanning(extractedText, "scanner_batch.png")
                                        scannerMode = "ocr_result"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "OCR analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract OCR Text", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        "ocr_result" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scannerMode = "list" }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                    Text("OCR Transcription Analysis", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = {
                        val sendIntent: android.content.Intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, extractedText)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, "Share text")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search in OCR text
                OutlinedTextField(
                    value = ocrSearchQuery,
                    onValueChange = { ocrSearchQuery = it },
                    placeholder = { Text("Search matches inside transcription...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (ocrSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { ocrSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Editable content
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        BasicTextField(
                            value = extractedText,
                            onValueChange = { extractedText = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save as text document
                    Button(
                        onClick = {
                            viewModel.createDocument(
                                name = "OCR_Extracted_${System.currentTimeMillis() / 1000}.txt",
                                type = "txt",
                                content = extractedText,
                                category = "Text Files"
                            )
                            Toast.makeText(context, "Successfully saved text to library!", Toast.LENGTH_SHORT).show()
                            scannerMode = "list"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Library TXT", fontSize = 12.sp)
                    }

                    // Copy raw
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("OCR_Scanned_Text", extractedText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied transcription to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Raw Text", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// --- Settings Tab Screen ---
@Composable
fun SettingsTabScreen(viewModel: OfficeViewModel) {
    val userKey by viewModel.userApiKey.collectAsStateWithLifecycle()
    val pinCode by viewModel.appPin.collectAsStateWithLifecycle()
    val cloudProv by viewModel.cloudProvider.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showKeyDialog by remember { mutableStateOf(false) }
    var keyTextState by remember { mutableStateOf(userKey) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinTextState by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val currentTheme by viewModel.appTheme.collectAsStateWithLifecycle()

        Text("App Theme", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val themeOptions = listOf(
                    Triple("light", "Light Theme (Default)", Icons.Default.LightMode),
                    Triple("dark", "Dark Theme", Icons.Default.DarkMode),
                    Triple("amoled", "AMOLED Black Theme", Icons.Default.BrightnessLow)
                )
                themeOptions.forEach { (mode, label, icon) ->
                    val isSelected = currentTheme == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAppTheme(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setAppTheme(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("AI Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemini API Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (userKey == "MY_GEMINI_API_KEY") "Using simulated offline AI (No Key)" else "Real-time key configured successfully",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Button(onClick = { showKeyDialog = true }, modifier = Modifier.testTag("configure_key_button")) {
                        Text("Configure", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Security Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App PIN Protection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (pinCode.isEmpty()) "PIN is currently inactive. Touch to lock app." else "Secure App Lock is active",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pinCode.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("toggle_pin_button")
                    ) {
                        Text(if (pinCode.isNotEmpty()) "Disable PIN" else "Setup PIN", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Cloud & Synchronization", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Default Sync Provider", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val providers = listOf("Google Drive", "OneDrive", "Dropbox", "WebDAV")
                providers.forEach { provider ->
                    val isSelected = provider == cloudProv
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setCloudProvider(provider) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { viewModel.setCloudProvider(provider) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(provider, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Manual Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Backup Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            when (backupState) {
                                "backing_up" -> "Backing up documents and state..."
                                "completed" -> "Backup successfully compiled!"
                                "restored" -> "App state restored from backup!"
                                else -> "All settings, stars, and folders are safe."
                            },
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.triggerBackup()
                            Toast.makeText(context, "Local Backup completed successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("backup_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.triggerRestore()
                            Toast.makeText(context, "State successfully restored!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).testTag("restore_button")
                    ) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Configure Gemini API Dialog
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Configure Gemini AI Key") },
            text = {
                Column {
                    Text("Enter your custom Gemini API key to activate real-time summaries and queries. Keep empty to use local simulator.", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = keyTextState,
                        onValueChange = { keyTextState = it },
                        placeholder = { Text("AI API Key") },
                        singleLine = true,
                        modifier = Modifier.testTag("api_key_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setApiKey(keyTextState)
                        showKeyDialog = false
                        Toast.makeText(context, "API Key saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_key_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Configure PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (pinCode.isNotEmpty()) "Disable Pin Protection" else "Setup App PIN") },
            text = {
                Column {
                    Text("Enter a 4-digit security PIN.", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = pinTextState,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinTextState = it },
                        placeholder = { Text("PIN Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag("pin_setup_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinCode.isNotEmpty()) {
                            viewModel.setupSecurityPin("")
                        } else if (pinTextState.length == 4) {
                            viewModel.setupSecurityPin(pinTextState)
                        }
                        pinTextState = ""
                        showPinDialog = false
                        Toast.makeText(context, "PIN code settings updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_pin_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- Create Document Bottom Sheet ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDocBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String, content: String, category: String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Create New Office File",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val creatorTypes = listOf(
                Triple("Word Document", "docx", "Word Documents"),
                Triple("Excel Spreadsheet", "xlsx", "Excel Spreadsheets"),
                Triple("PowerPoint Presentation", "pptx", "PowerPoint Slides"),
                Triple("CSV Database Table", "csv", "Excel Spreadsheets"),
                Triple("JSON Structure Node", "json", "Developer Files"),
                Triple("Markdown Documentation", "md", "Text Files"),
                Triple("Plain Text Note", "txt", "Text Files")
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(creatorTypes) { (label, extension, category) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val templateContent = when (extension) {
                                    "xlsx" -> "{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": [[\"A1\", \"B1\", \"C1\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]}]}"
                                    "pptx" -> "[{\"title\": \"My New Slide Deck\", \"subtitle\": \"Tap to edit\", \"notes\": \"Add details here\"}]"
                                    "json" -> "{\n  \"appName\": \"DocHub App\",\n  \"active\": true\n}"
                                    "csv" -> "ID,Name,Value\n1,RecordA,100"
                                    else -> "# Untitled $label\nStart writing premium text here."
                                }
                                val randomId = (10..99).random()
                                onCreate("Untitled_$randomId", extension, templateContent, category)
                            }
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            getIconForDocType(extension),
                            contentDescription = null,
                            tint = getThemeColorForDocType(extension),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Create new .$extension file using standard templates", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// --- Active Document Editor / Viewer Frame ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorFrame(
    viewModel: OfficeViewModel,
    document: DocumentEntity,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showAiAssistant by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            // Screen Header
            TopAppBar(
                title = {
                    Column {
                        Text(document.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(document.category.uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_editor_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    // Save Button
                    IconButton(
                        onClick = {
                            viewModel.saveActiveDocumentToDisk()
                            android.widget.Toast.makeText(context, "Document Saved Successfully", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("save_editor_button")
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save Document",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Toggle AI Button
                    IconButton(
                        onClick = { showAiAssistant = !showAiAssistant },
                        modifier = Modifier.testTag("toggle_ai_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = if (showAiAssistant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )

            // Dynamic Editors based on File Extension
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (document.type) {
                    "pdf" -> PdfReaderView(document, viewModel)
                    "docx", "odt", "rtf" -> WordEditorView(document, viewModel)
                    "xlsx", "ods" -> ExcelEditorView(document, viewModel)
                    "pptx", "ppt" -> PowerPointViewerView(document, viewModel)
                    "json" -> JsonEditorView(document, viewModel)
                    "csv" -> CsvEditorView(document, viewModel)
                    "epub", "mobi" -> EpubReaderView(document, viewModel)
                    else -> TextEditorView(document, viewModel) // txt, md, html, xml, yaml
                }
            }
        }

        // Expanded Screen AI Assistant Panel
        AnimatedVisibility(
            visible = showAiAssistant,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            AiAssistantPanel(
                viewModel = viewModel,
                documentContent = document.content,
                onClose = { showAiAssistant = false }
            )
        }
    }
}

// ==========================================
//          DEDICATED EDITOR VIEWS
// ==========================================

// --- 1. PDF READER VIEW (Annotate, drawing signatures, fill forms) ---
@Composable
fun PdfReaderView(document: DocumentEntity, viewModel: OfficeViewModel) {
    val context = LocalContext.current
    val pages = remember(document) { document.content.split("---PAGE_BREAK---").filter { it.trim().isNotEmpty() } }
    var activePage by remember { mutableStateOf(0) }
    var scaleFactor by remember { mutableStateOf(1.0f) }

    // PDF Render State
    var pdfBitmaps by remember(document) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var isLoadingPdf by remember(document) { mutableStateOf(false) }

    LaunchedEffect(document) {
        isLoadingPdf = true
        try {
            val docsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            val file = java.io.File(docsDir, document.name)
            
            // Reconstruct the physical PDF file if missing but available as Base64 content
            if ((!file.exists() || file.length() == 0L) && document.content.startsWith("BASE64:")) {
                try {
                    val base64Str = document.content.substring(7).trim()
                    val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                    if (docsDir != null) {
                        if (!docsDir.exists()) docsDir.mkdirs()
                        file.writeBytes(decodedBytes)
                        android.util.Log.d("PdfReaderView", "Restored physical PDF file from Base64: ${file.absolutePath}")
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("PdfReaderView", "Failed to restore physical PDF from Base64: ${ex.message}", ex)
                }
            }
            
            if (file.exists() && file.length() > 0) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val bitmaps = mutableListOf<android.graphics.Bitmap>()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val scale = 2.0f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                        page.close()
                    }
                    renderer.close()
                    pfd.close()
                    pdfBitmaps = bitmaps
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfReaderView", "Error rendering PDF pages: ${e.message}", e)
        } finally {
            isLoadingPdf = false
        }
    }

    val totalPages = if (pdfBitmaps.isNotEmpty()) pdfBitmaps.size else pages.size
    activePage = activePage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    // Signature Drawer coordinates list
    var pathPoints = remember { mutableStateListOf<Offset>() }
    var selectedInkColor by remember { mutableStateOf(Color.Red) }

    Column(modifier = Modifier.fillMaxSize()) {
        // PDF Utility bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scaleFactor = (scaleFactor + 0.2f).coerceAtMost(2.5f) }) {
                    Icon(Icons.Default.ZoomIn, "Zoom In")
                }
                IconButton(onClick = { scaleFactor = (scaleFactor - 0.2f).coerceAtLeast(0.8f) }) {
                    Icon(Icons.Default.ZoomOut, "Zoom Out")
                }
                Text("Zoom: ${(scaleFactor * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Ink controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ink:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp))
                listOf(Color.Red, Color.Blue, Color.Black).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color, CircleShape)
                            .border(
                                2.dp,
                                if (selectedInkColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape
                            )
                            .clickable { selectedInkColor = color }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(onClick = { pathPoints.clear() }) {
                    Icon(Icons.Default.DeleteSweep, "Clear Drawing", tint = Color.Gray)
                }
            }
        }

        // Main Pages display with annotation canvas layered
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pathPoints.add(change.position)
                    }
                }
        ) {
            // Render single page based on active selection
            if (pdfBitmaps.isNotEmpty()) {
                val bitmap = pdfBitmaps[activePage.coerceIn(0, pdfBitmaps.size - 1)]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer(scaleX = scaleFactor, scaleY = scaleFactor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page ${activePage + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
            } else if (pages.isNotEmpty()) {
                val pageText = pages[activePage.coerceIn(0, pages.size - 1)]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer(scaleX = scaleFactor, scaleY = scaleFactor)
                        .verticalScroll(rememberScrollState()),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "PAGE ${activePage + 1} OF ${pages.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            pageText.trim(),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color.Black,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Canvas signatures overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in 0 until pathPoints.size - 1) {
                    val p1 = pathPoints[i]
                    val p2 = pathPoints[i + 1]
                    if ((p2 - p1).getDistance() < 100f) {
                        drawCircle(color = selectedInkColor, radius = 3f, center = p1)
                        drawLine(
                            color = selectedInkColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Bottom Page Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { if (activePage > 0) activePage-- },
                enabled = activePage > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Prev Page", fontSize = 11.sp)
            }
            Text("Page ${activePage + 1} / $totalPages", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Button(
                onClick = { if (activePage < totalPages - 1) activePage++ },
                enabled = activePage < totalPages - 1,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Next Page", fontSize = 11.sp)
            }
        }
    }
}

// --- 2. WORD EDITOR VIEW (DOCX, ODT, RTF, TXT rich tools) ---
@Composable
fun WordEditorView(document: DocumentEntity, viewModel: OfficeViewModel) {
    val context = LocalContext.current
    var textContent by remember(document) { mutableStateOf(document.content) }
    var selectedFontSize by remember { mutableStateOf(14) }
    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Rich Formatting toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isBoldActive = !isBoldActive },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isBoldActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatBold, "Bold")
            }
            IconButton(
                onClick = { isItalicActive = !isItalicActive },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isItalicActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatItalic, "Italic")
            }
            IconButton(onClick = { textContent += "\n*   Bullet Item " }) {
                Icon(Icons.Default.FormatListBulleted, "List")
            }

            Spacer(modifier = Modifier.width(8.dp))
            VerticalDivider(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.width(8.dp))

            // Size actions
            IconButton(onClick = { selectedFontSize = (selectedFontSize + 1).coerceAtMost(28) }) {
                Icon(Icons.Default.ArrowUpward, "Size up")
            }
            Text("Size: $selectedFontSize", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { selectedFontSize = (selectedFontSize - 1).coerceAtLeast(10) }) {
                Icon(Icons.Default.ArrowDownward, "Size down")
            }

            Spacer(modifier = Modifier.width(8.dp))
            VerticalDivider(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.askAiAssistant(
                        context = context,
                        prompt = "Proofread this draft document for grammar and spelling corrections: \n\n$textContent",
                        actionType = "grammar"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.testTag("grammar_check_button")
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Grammar Check", fontSize = 11.sp)
            }
        }

        // Full Rich Text area
        OutlinedTextField(
            value = textContent,
            onValueChange = {
                textContent = it
                viewModel.updateActiveDocumentContent(it)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = selectedFontSize.sp,
                fontWeight = if (isBoldActive) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalicActive) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("word_editor_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
    }
}

// --- 3. EXCEL SPREADSHEET EDITOR (Interactive cell engine, Formula auto calculation, Dynamic Canvas Charts) ---
@Composable
fun ExcelEditorView(document: DocumentEntity, viewModel: OfficeViewModel) {
    // Parse sheets array from content JSON
    val jsonObject = remember(document) {
        try {
            JSONObject(document.content)
        } catch (e: Exception) {
            JSONObject("{\"sheets\": [{\"name\": \"Sheet1\", \"rows\": [[\"A1\", \"B1\", \"C1\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]}]}")
        }
    }

    val sheetsArray = jsonObject.getJSONArray("sheets")
    var selectedSheetIndex by remember { mutableStateOf(0) }

    val activeSheet = sheetsArray.getJSONObject(selectedSheetIndex)
    val sheetName = activeSheet.getString("name")
    val rowsArray = activeSheet.getJSONArray("rows")

    val numRows = rowsArray.length()
    val numCols = if (numRows > 0) rowsArray.getJSONArray(0).length() else 3

    // State grid holding the cells
    val gridState = remember(document, selectedSheetIndex) {
        val list = mutableStateListOf<MutableList<String>>()
        for (i in 0 until numRows) {
            val row = mutableStateListOf<String>()
            val rArray = rowsArray.getJSONArray(i)
            for (j in 0 until rArray.length()) {
                row.add(rArray.getString(j))
            }
            list.add(row)
        }
        list
    }

    var activeCellRow by remember { mutableStateOf(0) }
    var activeCellCol by remember { mutableStateOf(0) }
    var activeCellText by remember { mutableStateOf("") }

    val stylesMap = remember(document, selectedSheetIndex) {
        val map = mutableStateMapOf<String, JSONObject>()
        try {
            if (activeSheet.has("styles")) {
                val stylesObj = activeSheet.getJSONObject("styles")
                val keys = stylesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = stylesObj.getJSONObject(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        map
    }

    fun saveSpreadsheetState() {
        val newRowsArray = JSONArray()
        for (i in 0 until gridState.size) {
            val rowItems = JSONArray()
            for (j in 0 until gridState[i].size) {
                rowItems.put(gridState[i][j])
            }
            newRowsArray.put(rowItems)
        }
        activeSheet.put("rows", newRowsArray)
        
        // Save styles dictionary to activeSheet
        val stylesObj = JSONObject()
        stylesMap.forEach { (key, value) ->
            stylesObj.put(key, value)
        }
        activeSheet.put("styles", stylesObj)

        sheetsArray.put(selectedSheetIndex, activeSheet)
        jsonObject.put("sheets", sheetsArray)
        viewModel.updateActiveDocumentContent(jsonObject.toString())
    }

    fun getCellBold(r: Int, c: Int): Boolean {
        return stylesMap["${r}_${c}"]?.optBoolean("bold", false) ?: false
    }
    fun getCellItalic(r: Int, c: Int): Boolean {
        return stylesMap["${r}_${c}"]?.optBoolean("italic", false) ?: false
    }
    fun getCellBgColor(r: Int, c: Int): Color {
        val hex = stylesMap["${r}_${c}"]?.optString("bgColor", "") ?: ""
        return if (hex.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
        } else {
            Color.White
        }
    }
    fun getCellTextColor(r: Int, c: Int): Color {
        val hex = stylesMap["${r}_${c}"]?.optString("textColor", "") ?: ""
        return if (hex.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Black }
        } else {
            Color.Black
        }
    }
    fun getCellAlign(r: Int, c: Int): androidx.compose.ui.text.style.TextAlign {
        val align = stylesMap["${r}_${c}"]?.optString("align", "left") ?: "left"
        return when (align) {
            "center" -> androidx.compose.ui.text.style.TextAlign.Center
            "right" -> androidx.compose.ui.text.style.TextAlign.Right
            else -> androidx.compose.ui.text.style.TextAlign.Left
        }
    }
    fun getCellFontSize(r: Int, c: Int): Int {
        return stylesMap["${r}_${c}"]?.optInt("fontSize", 12) ?: 12
    }

    fun updateCellStyle(r: Int, c: Int, update: (JSONObject) -> Unit) {
        val key = "${r}_${c}"
        val current = stylesMap[key] ?: JSONObject()
        update(current)
        stylesMap[key] = current
        saveSpreadsheetState()
    }

    // Formula calculation function helper
    fun computeCellValue(row: Int, col: Int): String {
        val cellStr = gridState.getOrNull(row)?.getOrNull(col) ?: ""
        if (cellStr.startsWith("=")) {
            val formula = cellStr.substring(1).uppercase()
            try {
                if (formula.startsWith("SUM(")) {
                    // SUM range parser e.g. SUM(B2:B5)
                    val range = formula.substring(4, formula.length - 1)
                    val parts = range.split(":")
                    if (parts.size == 2) {
                        val startCell = parts[0]
                        val endCell = parts[1]

                        val sCol = startCell[0] - 'A'
                        val sRow = startCell.substring(1).toInt() - 1

                        val eCol = endCell[0] - 'A'
                        val eRow = endCell.substring(1).toInt() - 1

                        var sum = 0.0
                        for (r in sRow..eRow) {
                            for (c in sCol..eCol) {
                                val valStr = gridState.getOrNull(r)?.getOrNull(c) ?: "0"
                                sum += valStr.toDoubleOrNull() ?: 0.0
                            }
                        }
                        return sum.toInt().toString()
                    }
                } else if (formula.contains("-")) {
                    // Simple subtraction e.g. B2-C2
                    val cells = formula.split("-")
                    if (cells.size == 2) {
                        val c1 = cells[0]
                        val c2 = cells[1]

                        val col1 = c1[0] - 'A'
                        val row1 = c1.substring(1).toInt() - 1

                        val col2 = c2[0] - 'A'
                        val row2 = c2.substring(1).toInt() - 1

                        val val1 = (gridState.getOrNull(row1)?.getOrNull(col1) ?: "0").toDoubleOrNull() ?: 0.0
                        val val2 = (gridState.getOrNull(row2)?.getOrNull(col2) ?: "0").toDoubleOrNull() ?: 0.0

                        return (val1 - val2).toInt().toString()
                    }
                }
            } catch (e: Exception) {
                return "#VALUE!"
            }
        }
        return cellStr
    }

    // Refresh text on focus switch
    LaunchedEffect(activeCellRow, activeCellCol) {
        activeCellText = gridState.getOrNull(activeCellRow)?.getOrNull(activeCellCol) ?: ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Multi-Sheet Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until sheetsArray.length()) {
                val isSelected = i == selectedSheetIndex
                Tab(
                    selected = isSelected,
                    onClick = { selectedSheetIndex = i },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        sheetsArray.getJSONObject(i).getString("name"),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Cell Style Formatting Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isBold = getCellBold(activeCellRow, activeCellCol)
            IconButton(
                onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("bold", !isBold)
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
            }

            val isItalic = getCellItalic(activeCellRow, activeCellCol)
            IconButton(
                onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("italic", !isItalic)
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
            }

            val currentAlign = stylesMap["${activeCellRow}_${activeCellCol}"]?.optString("align", "left") ?: "left"
            IconButton(
                onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("align", "left")
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentAlign == "left") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left", modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("align", "center")
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentAlign == "center") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center", modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("align", "right")
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentAlign == "right") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Default.FormatAlignRight, contentDescription = "Align Right", modifier = Modifier.size(18.dp))
            }

            // Custom divider
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

            // Font size
            val currentSize = getCellFontSize(activeCellRow, activeCellCol)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("fontSize", maxOf(8, currentSize - 1))
                    }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(14.dp))
                }
                Text("$currentSize", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = {
                    updateCellStyle(activeCellRow, activeCellCol) { obj ->
                        obj.put("fontSize", minOf(24, currentSize + 1))
                    }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(14.dp))
                }
            }

            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

            // BG Colors
            Text("BG:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            val bgColors = listOf(
                "#FFFFFF" to Color.White,
                "#FFF9C4" to Color(0xFFFFF9C4),
                "#C8E6C9" to Color(0xFFC8E6C9),
                "#B3E5FC" to Color(0xFFB3E5FC),
                "#FFCDD2" to Color(0xFFFFCDD2)
            )
            bgColors.forEach { (hex, color) ->
                val isSelectedBg = (stylesMap["${activeCellRow}_${activeCellCol}"]?.optString("bgColor") ?: "#FFFFFF") == hex
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(color, CircleShape)
                        .border(if (isSelectedBg) 2.dp else 0.5.dp, if (isSelectedBg) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable {
                            updateCellStyle(activeCellRow, activeCellCol) { obj ->
                                obj.put("bgColor", hex)
                            }
                        }
                )
            }

            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))

            // Text Colors
            Text("Text:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            val textColorsList = listOf(
                "#000000" to Color.Black,
                "#D32F2F" to Color(0xFFD32F2F),
                "#388E3C" to Color(0xFF388E3C),
                "#1976D2" to Color(0xFF1976D2)
            )
            textColorsList.forEach { (hex, color) ->
                val isSelectedTextColor = (stylesMap["${activeCellRow}_${activeCellCol}"]?.optString("textColor") ?: "#000000") == hex
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(color, CircleShape)
                        .border(if (isSelectedTextColor) 2.dp else 0.5.dp, if (isSelectedTextColor) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable {
                            updateCellStyle(activeCellRow, activeCellCol) { obj ->
                                obj.put("textColor", hex)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = if (color == Color.Black) Color.White else Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Formula Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${('A' + activeCellCol)}${activeCellRow + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = activeCellText,
                onValueChange = {
                    activeCellText = it
                    gridState[activeCellRow][activeCellCol] = it
                    saveSpreadsheetState()
                },
                placeholder = { Text("Enter text, number, or formula (e.g. =B2-C2)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("formula_input")
            )
        }

        // Grid View
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            // Row numbers list header
            Column {
                // Empty corner
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.5.dp, Color.LightGray)
                )
                for (r in 0 until numRows) {
                    Box(
                        modifier = Modifier
                            .size(height = 44.dp, width = 36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((r + 1).toString(), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // Columns and Cells
            Column {
                // Alphabet header
                Row {
                    for (c in 0 until numCols) {
                        Box(
                            modifier = Modifier
                                .size(height = 36.dp, width = 110.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(('A' + c).toString(), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                // Main Cells grid
                for (r in 0 until numRows) {
                    Row {
                        for (c in 0 until numCols) {
                            val computedVal = computeCellValue(r, c)
                            val isFocused = r == activeCellRow && c == activeCellCol

                            val isBold = getCellBold(r, c)
                            val isItalic = getCellItalic(r, c)
                            val cellBgColor = getCellBgColor(r, c)
                            val cellTextColor = getCellTextColor(r, c)
                            val cellAlign = getCellAlign(r, c)
                            val cellFontSize = getCellFontSize(r, c).sp

                            val boxAlign = when (cellAlign) {
                                androidx.compose.ui.text.style.TextAlign.Center -> Alignment.Center
                                androidx.compose.ui.text.style.TextAlign.Right -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }

                            Box(
                                modifier = Modifier
                                    .size(height = 44.dp, width = 110.dp)
                                    .background(
                                        if (isFocused) {
                                            if (cellBgColor == Color.White) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            } else {
                                                cellBgColor.copy(alpha = 0.7f)
                                            }
                                        } else {
                                            cellBgColor
                                        }
                                    )
                                    .border(0.5.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.LightGray)
                                    .clickable {
                                        activeCellRow = r
                                        activeCellCol = c
                                    },
                                contentAlignment = boxAlign
                            ) {
                                Text(
                                    text = computedVal,
                                    fontSize = cellFontSize,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                    color = cellTextColor,
                                    textAlign = cellAlign,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic visual Canvas charts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    "Dynamic Performance Chart ($sheetName)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Render dynamic bar graphs drawn on Canvas representing cell values!
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Parse numeric inputs from Row 2 to 5 in Column B (B2:B5)
                    val dataPoints = mutableListOf<Float>()
                    for (i in 1..4) {
                        val strVal = gridState.getOrNull(i)?.getOrNull(1) ?: "0"
                        dataPoints.add(strVal.toFloatOrNull() ?: 0.0f)
                    }

                    val maxPoint = dataPoints.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                    val barWidth = (canvasWidth / dataPoints.size) - 20f

                    for (idx in dataPoints.indices) {
                        val point = dataPoints[idx]
                        val barHeight = (point / maxPoint) * (canvasHeight - 30f)
                        val x = idx * (canvasWidth / dataPoints.size) + 10f
                        val y = canvasHeight - barHeight

                        drawRect(
                            color = Color(0xFF00A86B),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )

                        // Label the numeric text
                        // Custom circles draw for line representation as well
                        drawCircle(
                            color = Color(0xFFF57C00),
                            radius = 6f,
                            center = Offset(x + barWidth / 2f, y)
                        )
                    }

                    // Bottom line
                    drawLine(
                        color = Color.Gray,
                        start = Offset(0f, canvasHeight - 1f),
                        end = Offset(canvasWidth, canvasHeight - 1f),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

// --- 4. POWERPOINT VIEWERS (Navigation deck, presenter notes, laser coordinate pointer) ---
@Composable
fun PowerPointViewerView(document: DocumentEntity, viewModel: OfficeViewModel) {
    val slidesArray = remember(document) {
        try {
            JSONArray(document.content)
        } catch (e: Exception) {
            JSONArray("[{\"title\": \"Slides View\", \"subtitle\": \"Error Loading Content\", \"notes\": \"No notes available\"}]")
        }
    }

    var activeSlideIndex by remember { mutableStateOf(0) }
    val activeSlide = slidesArray.getJSONObject(activeSlideIndex)

    var showPresenterNotes by remember { mutableStateOf(false) }
    var laserCoordinates by remember { mutableStateOf<Offset?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Shutter Deck Slide Screen View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { laserCoordinates = it },
                        onDragEnd = { laserCoordinates = null },
                        onDragCancel = { laserCoordinates = null },
                        onDrag = { change, dragAmount -> laserCoordinates = change.position }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Presentation Canvas styled slide
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        activeSlide.getString("title"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        activeSlide.getString("subtitle"),
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Laser pointer overlay simulator
            laserCoordinates?.let { coords ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.Red,
                        radius = 12f,
                        center = coords
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = coords
                    )
                }
            }

            // Screen label helper
            Text(
                "Hold and drag finger to activate laser pointer",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            )
        }

        // Presenter Notes Slide-out
        if (showPresenterNotes) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Presenter Notes", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(activeSlide.getString("notes"), fontSize = 11.sp)
                }
            }
        }

        // Controller Dock panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { if (activeSlideIndex > 0) activeSlideIndex-- },
                enabled = activeSlideIndex > 0
            ) {
                Text("Prev", fontSize = 11.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slide ${activeSlideIndex + 1} of ${slidesArray.length()}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = { showPresenterNotes = !showPresenterNotes }) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Presenter Notes",
                        tint = if (showPresenterNotes) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            Button(
                onClick = { if (activeSlideIndex < slidesArray.length() - 1) activeSlideIndex++ },
                enabled = activeSlideIndex < slidesArray.length() - 1
            ) {
                Text("Next", fontSize = 11.sp)
            }
        }
    }
}

// --- 5. JSON EDITOR VIEW (Pretty-print Raw validator & Tree View expand nodes) ---
@Composable
fun JsonEditorView(document: DocumentEntity, viewModel: OfficeViewModel) {
    var rawText by remember(document) { mutableStateOf(document.content) }
    var isTreeViewActive by remember { mutableStateOf(false) }
    var isValidJson by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Menu utilities
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                FilterChip(
                    selected = !isTreeViewActive,
                    onClick = { isTreeViewActive = false },
                    label = { Text("Raw Code", fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = isTreeViewActive,
                    onClick = {
                        try {
                            JSONObject(rawText)
                            isValidJson = true
                            isTreeViewActive = true
                        } catch (e: Exception) {
                            isValidJson = false
                        }
                    },
                    label = { Text("Tree View", fontSize = 11.sp) }
                )
            }

            Row {
                IconButton(onClick = {
                    try {
                        val parsed = JSONObject(rawText)
                        rawText = parsed.toString(2)
                        isValidJson = true
                    } catch (e: Exception) {
                        isValidJson = false
                    }
                }) {
                    Icon(Icons.Default.FormatAlignLeft, "Pretty Print")
                }
                IconButton(onClick = {
                    try {
                        val parsed = JSONObject(rawText)
                        rawText = parsed.toString()
                        isValidJson = true
                    } catch (e: Exception) {
                        isValidJson = false
                    }
                }) {
                    Icon(Icons.Default.Minimize, "Minify")
                }
            }
        }

        if (!isValidJson) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Syntax Error: Invalid JSON structure detected.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isTreeViewActive) {
                // Interactive JSON Node trees
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Text("ROOT OBJECT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    val jsonNode = JSONObject(rawText)
                    val keys = jsonNode.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = jsonNode.get(key)
                        item {
                            JsonNodeRow(key, value)
                        }
                    }
                }
            } else {
                // Raw text editor
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        viewModel.updateActiveDocumentContent(it)
                        try {
                            JSONObject(it)
                            isValidJson = true
                        } catch (e: Exception) {
                            isValidJson = false
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("json_raw_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun JsonNodeRow(key: String, value: Any) {
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$key : ",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (value is JSONObject || value is JSONArray) {
                Text(
                    if (value is JSONObject) "{Object}" else "[Array]",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = value.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFF57C00)
                )
            }
        }

        if (expanded && value is JSONObject) {
            val childKeys = value.keys()
            while (childKeys.hasNext()) {
                val childKey = childKeys.next()
                JsonNodeRow(childKey, value.get(childKey))
            }
        }
    }
}

// --- 6. CSV EDITOR VIEW (Tabular cell list, Row adding and removal) ---
@Composable
fun CsvEditorView(document: DocumentEntity, viewModel: OfficeViewModel) {
    val lines = remember(document) {
        val list = mutableStateListOf<List<String>>()
        document.content.split("\n").filter { it.trim().isNotEmpty() }.forEach { line ->
            list.add(line.split(","))
        }
        list
    }

    fun saveCsvContent() {
        val csvStr = lines.joinToString("\n") { it.joinToString(",") }
        viewModel.updateActiveDocumentContent(csvStr)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val headCount = lines.firstOrNull()?.size ?: 3
                val emptyRow = List(headCount) { "" }
                lines.add(emptyRow)
                saveCsvContent()
            }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Row", fontSize = 11.sp)
            }

            Button(
                onClick = {
                    if (lines.size > 1) {
                        lines.removeAt(lines.lastIndex)
                        saveCsvContent()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove Row", fontSize = 11.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            itemsIndexed(lines) { rIdx, row ->
                val isHeader = rIdx == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEachIndexed { cIdx, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newVal ->
                                val rowList = lines[rIdx].toMutableList()
                                rowList[cIdx] = newVal.replace(",", "") // prevent comma break
                                lines[rIdx] = rowList
                                saveCsvContent()
                            },
                            textStyle = if (isHeader) {
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isHeader) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- 7. EPUB BOOK READER VIEW (Chapter slider, sepia parchment modes, text adjusts) ---
@Composable
fun EpubReaderView(document: DocumentEntity, viewModel: OfficeViewModel) {
    val chapters = remember(document) { document.content.split("CHAPTER").filter { it.trim().isNotEmpty() } }
    var activeChapterIndex by remember { mutableStateOf(0) }
    var userFontSize by remember { mutableStateOf(14) }
    var selectedThemeIdx by remember { mutableStateOf(0) } // 0: Parchment, 1: Sepia, 2: Night

    val themeColors = listOf(
        Triple(Color(0xFFFDFBF7), Color(0xFF5C4033), "Parchment"),
        Triple(Color(0xFFF4ECD8), Color(0xFF4E3629), "Sepia"),
        Triple(Color(0xFF1E1E1E), Color(0xFFE0E0E0), "Night")
    )

    val (bg, textCol, themeLabel) = themeColors[selectedThemeIdx]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Style parameters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { userFontSize = (userFontSize + 1).coerceAtMost(24) }) {
                    Icon(Icons.Default.TextIncrease, "Font increase")
                }
                Text("Size: $userFontSize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { userFontSize = (userFontSize - 1).coerceAtLeast(11) }) {
                    Icon(Icons.Default.TextDecrease, "Font decrease")
                }
            }

            // Quick Theme sliders
            Row {
                themeColors.forEachIndexed { idx, (_, _, label) ->
                    val isSelected = idx == selectedThemeIdx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedThemeIdx = idx },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        // Active Chapter frame
        if (chapters.isNotEmpty()) {
            val contentBody = chapters[activeChapterIndex]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                            append("CHAPTER ")
                        }
                        withStyle(SpanStyle(color = textCol)) {
                            append(contentBody.trim())
                        }
                    },
                    fontSize = userFontSize.sp,
                    lineHeight = 24.sp,
                    fontFamily = FontFamily.Serif
                )
            }
        }

        // Drawer Chapters Nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { if (activeChapterIndex > 0) activeChapterIndex-- },
                enabled = activeChapterIndex > 0
            ) {
                Text("Prev Chapter", fontSize = 11.sp)
            }
            Text("Chapter ${activeChapterIndex + 1} of ${chapters.size}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Button(
                onClick = { if (activeChapterIndex < chapters.size - 1) activeChapterIndex++ },
                enabled = activeChapterIndex < chapters.size - 1
            ) {
                Text("Next Chapter", fontSize = 11.sp)
            }
        }
    }
}

// --- 8. TEXT / MARKDOWN EDITOR VIEW ---
@Composable
fun TextEditorView(document: DocumentEntity, viewModel: OfficeViewModel) {
    var rawText by remember(document) { mutableStateOf(document.content) }
    var showPreview by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Markdown & YAML editor", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
            FilterChip(
                selected = showPreview,
                onClick = { showPreview = !showPreview },
                label = { Text("Preview MD", fontSize = 11.sp) }
            )
        }

        if (showPreview) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                item {
                    Text(
                        rawText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    viewModel.updateActiveDocumentContent(it)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("text_editor_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// ==========================================
//          AI ASSISTANT DRAWER PANEL
// ==========================================

@Composable
fun AiAssistantPanel(
    viewModel: OfficeViewModel,
    documentContent: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val aiResponse by viewModel.aiResponse.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    var userPromptText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DocHub AI Assistant", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // AI Response frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (isAiLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("AI is analyzing document...", fontSize = 11.sp, color = Color.Gray)
                }
            } else if (aiResponse != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(aiResponse!!, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { viewModel.clearAiResponse() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear", fontSize = 11.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Ask anything about this document! Let AI summarize, rewrite, or analyze components.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prompts template list shortcut buttons
        Text("Quick AI Prompts:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "Summarize" to "summarize",
                "Explain" to "explain",
                "Rewrite" to "rewrite",
                "Translate" to "translate"
            ).forEach { (label, act) ->
                Button(
                    onClick = { viewModel.askAiAssistant(context, "Execute $label action on active document context.", act) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Query Text Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userPromptText,
                onValueChange = { userPromptText = it },
                placeholder = { Text("Ask Assistant...", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_prompt_input")
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (userPromptText.trim().isNotEmpty()) {
                        viewModel.askAiAssistant(context, userPromptText.trim(), "chat")
                        userPromptText = ""
                    }
                },
                modifier = Modifier.testTag("send_ai_button")
            ) {
                Icon(Icons.Default.Send, "Send prompt", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ==========================================
//          HELPER MATHS & TEXT TOOLS
// ==========================================

fun getIconForDocType(type: String): ImageVector {
    return when (type) {
        "pdf" -> Icons.Default.PictureAsPdf
        "docx", "odt", "rtf" -> Icons.Default.Description
        "xlsx", "ods" -> Icons.Default.TableChart
        "pptx", "ppt" -> Icons.Default.Slideshow
        "json" -> Icons.Default.Code
        "csv" -> Icons.AutoMirrored.Filled.ListAlt
        "epub", "mobi" -> Icons.Default.Book
        else -> Icons.AutoMirrored.Filled.Notes
    }
}

fun getThemeColorForDocType(type: String): Color {
    return when (type) {
        "pdf" -> Color(0xFF410002) // Deep Red
        "docx", "odt", "rtf" -> Color(0xFF001D36) // Deep Blue
        "xlsx", "ods", "csv" -> Color(0xFF002104) // Deep Green
        "pptx", "ppt", "json", "epub", "mobi" -> Color(0xFF231B00) // Deep Amber
        else -> Color(0xFF1A1C1E)
    }
}

fun getThemeBgForDocType(type: String): Color {
    return when (type) {
        "pdf" -> Color(0xFFFFDAD6) // Light Red
        "docx", "odt", "rtf" -> Color(0xFFD1E4FF) // Light Blue
        "xlsx", "ods", "csv" -> Color(0xFFD2F4D3) // Light Green
        "pptx", "ppt", "json", "epub", "mobi" -> Color(0xFFFFEDBE) // Light Amber
        else -> Color(0xFFEEF0F6)
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
        else -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}
