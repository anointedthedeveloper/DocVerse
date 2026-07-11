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
import com.example.ui.viewmodel.OfficeViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocHubAppUi(viewModel: OfficeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Home, 1: Files, 2: OCR Scanner, 3: Settings
    var showCreateDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
                                    0 -> HomeTabScreen(viewModel = viewModel, onCategorySelect = { activeTab = 1 })
                                    1 -> FilesTabScreen(viewModel = viewModel)
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
            onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinText = it },
            label = { Text("Secure PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (viewModel.unlockApp(pinText)) {
                    Toast.makeText(context, "App Unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    errorText = "Incorrect PIN. Please try again."
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
                if (viewModel.unlockApp(pinText)) {
                    Toast.makeText(context, "App Unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    errorText = "Incorrect PIN. Please try again."
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
                onClick = { onClose() },
                icon = { Icon(Icons.Default.Folder, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("PDF Toolkit") },
                selected = false,
                onClick = { onClose() },
                icon = { Icon(Icons.Default.PictureAsPdf, null) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("Document Templates") },
                selected = false,
                onClick = { onClose() },
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
fun HomeTabScreen(viewModel: OfficeViewModel, onCategorySelect: (String) -> Unit) {
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

        // Category Shortcut Grid
        item {
            Text("File Categories", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
            val categories = listOf(
                "PDFs" to Icons.Default.PictureAsPdf,
                "Word Documents" to Icons.Default.Description,
                "Excel Sheets" to Icons.Default.TableChart,
                "PowerPoint" to Icons.Default.Slideshow,
                "Books" to Icons.Default.Book,
                "Notes" to Icons.Default.EditNote,
                "JSON Files" to Icons.Default.Code,
                "CSV Files" to Icons.AutoMirrored.Filled.ListAlt,
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
            items(recents) { doc ->
                DocumentRowItem(doc = doc, viewModel = viewModel)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// --- Files Tab Screen ---
@Composable
fun FilesTabScreen(viewModel: OfficeViewModel) {
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameText by remember { mutableStateOf("") }

    var selectedSortOrder by remember { mutableStateOf("date") } // date, size, name

    val sortedDocuments = remember(documents, selectedSortOrder) {
        when (selectedSortOrder) {
            "name" -> documents.sortedBy { it.name.lowercase() }
            "size" -> documents.sortedByDescending { it.size }
            else -> documents.sortedByDescending { it.modifiedAt }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
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
                TextButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.testTag("add_folder_button")) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Folder", fontSize = 12.sp)
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

// --- Single Document Row Layout ---
@Composable
fun DocumentRowItem(doc: DocumentEntity, viewModel: OfficeViewModel) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { viewModel.setActiveDocument(doc) }
            .testTag("document_row_${doc.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                            if (doc.isEncrypted) {
                                viewModel.toggleVaultEncryption(doc, "")
                            } else {
                                viewModel.toggleVaultEncryption(doc, "1234") // Default secure PIN simulation
                            }
                            showMenu = false
                            Toast.makeText(context, if (doc.isEncrypted) "Decrypted" else "Locked with default PIN 1234", Toast.LENGTH_SHORT).show()
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
}

// --- OCR Scanner Screen ---
@Composable
fun OcrScannerTabScreen(viewModel: OfficeViewModel) {
    val scans by viewModel.ocrScans.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dummyImageTexts = listOf(
        "DOCHUB SOLUTIONS INC\nOFFICE REVENUE SUMMARY\nQ1 total: ${'$'}1,240,000\nQ2 total: ${'$'}1,580,000\nNet profitability: 74%",
        "MINDFUL DEV JOURNAL\nPrinciples of building premium responsive Android apps called DocHub. Keep it functional, offline, and beautifully structured.",
        "TO-DO LIST\n1. Seed database with SQLite structures\n2. Integrate direct REST calls to Gemini 3.5 flash\n3. Code beautiful signature pad Canvas"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Handwriting & Text OCR Scanner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Take a photo or import from gallery to instantly extract text, recognize handwriting, and translate content.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Simulated camera shutter viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .drawBehind {
                            // Viewfinder brackets draw
                            val bracketSize = 20.dp.toPx()
                            val stroke = 3.dp.toPx()
                            val paddingValue = 10.dp.toPx()
                            // Top-Left
                            drawLine(Color.Green, Offset(paddingValue, paddingValue), Offset(paddingValue + bracketSize, paddingValue), stroke)
                            drawLine(Color.Green, Offset(paddingValue, paddingValue), Offset(paddingValue, paddingValue + bracketSize), stroke)
                            // Top-Right
                            drawLine(Color.Green, Offset(size.width - paddingValue, paddingValue), Offset(size.width - paddingValue - bracketSize, paddingValue), stroke)
                            drawLine(Color.Green, Offset(size.width - paddingValue, paddingValue), Offset(size.width - paddingValue, paddingValue + bracketSize), stroke)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (ocrState == "scanning") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Green)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Running ML Kit Extraction...", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            viewModel.runOcrScanning(dummyImageTexts.random(), "camera_capture.png")
                        } else {
                            Toast.makeText(context, "Camera permission is required to scan documents", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            
                            if (hasCameraPermission) {
                                viewModel.runOcrScanning(dummyImageTexts.random(), "camera_capture.png")
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("capture_ocr_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Capture Photo", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.runOcrScanning(dummyImageTexts.random(), "gallery_import.png")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("gallery_ocr_button")
                    ) {
                        Icon(Icons.Default.Photo, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Gallery", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Scan History Logs", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (scans.isEmpty()) {
            Text(
                "No documents scanned yet. Trigger your first capture above.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            scans.forEach { scan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recognized Text", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(formatTimestamp(scan.scannedAt), fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            scan.rawText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                viewModel.createDocument(
                                    name = "ExtractedText_${scan.id}.txt",
                                    type = "txt",
                                    content = scan.rawText,
                                    category = "Text Files"
                                )
                                Toast.makeText(context, "Saved as ExtractedText_${scan.id}.txt", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save as Doc", fontSize = 11.sp)
                            }
                        }
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
                Triple("Excel Spreadsheet", "xlsx", "Excel Sheets"),
                Triple("PowerPoint Presentation", "pptx", "PowerPoint"),
                Triple("CSV Database Table", "csv", "CSV Files"),
                Triple("JSON Structure Node", "json", "JSON Files"),
                Triple("Markdown Documentation", "md", "Notes"),
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
    val pages = remember(document) { document.content.split("---PAGE_BREAK---").filter { it.trim().isNotEmpty() } }
    var activePage by remember { mutableStateOf(0) }
    var scaleFactor by remember { mutableStateOf(1.0f) }

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
            if (pages.isNotEmpty()) {
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
                    // Skip large gaps to prevent continuous lines across releases
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
            Text("Page ${activePage + 1} / ${pages.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Button(
                onClick = { if (activePage < pages.size - 1) activePage++ },
                enabled = activePage < pages.size - 1,
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
        sheetsArray.put(selectedSheetIndex, activeSheet)
        jsonObject.put("sheets", sheetsArray)
        viewModel.updateActiveDocumentContent(jsonObject.toString())
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

                            Box(
                                modifier = Modifier
                                    .size(height = 44.dp, width = 110.dp)
                                    .background(if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.White)
                                    .border(0.5.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.LightGray)
                                    .clickable {
                                        activeCellRow = r
                                        activeCellCol = c
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = computedVal,
                                    fontSize = 12.sp,
                                    color = Color.Black,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp)
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
                        lines.removeLast()
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
                    onClick = { viewModel.askAiAssistant("Execute $label action on active document context.", act) },
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
                        viewModel.askAiAssistant(userPromptText.trim(), "chat")
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
