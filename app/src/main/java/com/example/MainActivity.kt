package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Document
import com.example.ui.DocumentViewModel
import com.example.ui.TemplateItem
import com.example.utils.DocumentFormatter
import java.text.SimpleDateFormat
import java.util.*

// Clean Minimalism Theme Color Palette
val WriteFlowBlue = Color(0xFF005CB9)
val WriteFlowLightBlue = Color(0xFFDDE2F1)
val WriteFlowBgLight = Color(0xFFFDFBFF)
val WriteFlowSurfaceLight = Color(0xFFF0F3FA)
val WriteFlowBorderLight = Color(0xFFE0E2EC)

val WriteFlowBgDark = Color(0xFF111318)
val WriteFlowSurfaceDark = Color(0xFF1B1E24)
val WriteFlowCardDark = Color(0xFF242930)
val WriteFlowBorderDark = Color(0xFF333842)
val WriteFlowBlueDark = Color(0xFF7EACFA)
val WriteFlowLightBlueDark = Color(0xFF2D3540)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            val colorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = WriteFlowBlueDark,
                    onPrimary = Color(0xFF001D35),
                    primaryContainer = WriteFlowLightBlueDark,
                    background = WriteFlowBgDark,
                    surface = WriteFlowSurfaceDark,
                    surfaceVariant = WriteFlowCardDark,
                    outline = WriteFlowBorderDark
                )
            } else {
                lightColorScheme(
                    primary = WriteFlowBlue,
                    onPrimary = Color.White,
                    primaryContainer = WriteFlowLightBlue,
                    background = WriteFlowBgLight,
                    surface = Color.White,
                    surfaceVariant = WriteFlowSurfaceLight,
                    outline = WriteFlowBorderLight
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WriteFlowApp()
                }
            }
        }
    }
}

@Composable
fun WriteFlowApp(viewModel: DocumentViewModel = viewModel()) {
    var isEditing by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }
    
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val totalDocs by viewModel.totalDocuments.collectAsStateWithLifecycle()
    val totalWordsCount by viewModel.totalWords.collectAsStateWithLifecycle()
    val starredDocsCount by viewModel.starredCount.collectAsStateWithLifecycle()

    // Activity Result Launchers for SAF (Storage Access Framework) file import/export
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(viewModel.editContent.toByteArray())
                    }
                    Toast.makeText(context, "Document exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    val fileName = getFileName(context, uri) ?: "ImportedDoc.txt"
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                    viewModel.importDocumentFromFile(fileName, content)
                    isEditing = true
                    Toast.makeText(context, "Document imported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (isEditing) {
            EditorScreen(
                viewModel = viewModel,
                onClose = {
                    viewModel.saveAndClose {
                        isEditing = false
                    }
                },
                onExport = {
                    // Trigger native file save
                    val formattedTitle = viewModel.editTitle.ifBlank { "Untitled" }
                        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    exportDocumentLauncher.launch("$formattedTitle.txt")
                }
            )
        } else {
            DashboardScreen(
                viewModel = viewModel,
                documents = documents,
                totalDocs = totalDocs,
                totalWordsCount = totalWordsCount,
                starredDocsCount = starredDocsCount,
                onSelectDocument = { doc ->
                    viewModel.setActiveDocument(doc)
                    isEditing = true
                },
                onCreateBlank = {
                    viewModel.createNewDocument("New Document", "Draft", "")
                    isEditing = true
                },
                onOpenTemplates = { showTemplateDialog = true },
                onDeleteDocument = { doc -> documentToDelete = doc },
                onImportFile = {
                    importDocumentLauncher.launch(arrayOf("text/plain", "text/markdown", "text/html"))
                },
                onOpenAbout = { showAboutDialog = true }
            )
        }

        // About Developer & Company Dialog
        if (showAboutDialog) {
            AboutDeveloperDialog(onDismiss = { showAboutDialog = false })
        }

        // Templates Selection Dialog
        if (showTemplateDialog) {
            TemplateSelectorDialog(
                templates = viewModel.getTemplatesList(),
                onDismiss = { showTemplateDialog = false },
                onSelectTemplate = { template ->
                    viewModel.createNewDocument(template.name, template.category, template.content)
                    showTemplateDialog = false
                    isEditing = true
                }
            )
        }

        // Delete Confirmation Dialog
        if (documentToDelete != null) {
            AlertDialog(
                onDismissRequest = { documentToDelete = null },
                title = { Text("Delete Document") },
                text = { Text("Are you sure you want to delete '${documentToDelete?.title}'? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            documentToDelete?.let { viewModel.deleteDocument(it) }
                            documentToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { documentToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DocumentViewModel,
    documents: List<Document>,
    totalDocs: Int,
    totalWordsCount: Int,
    starredDocsCount: Int,
    onSelectDocument: (Document) -> Unit,
    onCreateBlank: () -> Unit,
    onOpenTemplates: () -> Unit,
    onDeleteDocument: (Document) -> Unit,
    onImportFile: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    val categories = listOf("All", "Work", "Personal", "Draft", "Imported")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.writeflow_logo),
                                contentDescription = "WriteFlow Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "WriteFlow",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF22C55E), CircleShape)
                                )
                                Text(
                                    text = "OFFLINE READY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onImportFile,
                        modifier = Modifier.testTag("import_document_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Import file from device storage",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.testTag("about_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Developer & Company",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateBlank,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("add_document_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Blank Document", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Analytics / Stats Panel
            StatsPanel(totalDocs, totalWordsCount, starredDocsCount)

            // Local Storage Tracking Banner
            StorageInfoBanner(documents.size)

            // Search Bar & Filter Options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search your offline documents...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateCategoryFilter(category) },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            // Quick Template Banner Row
            Text(
                text = "Start with a Template",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Template Selector trigger card
                item {
                    Card(
                        onClick = onOpenTemplates,
                        modifier = Modifier
                            .width(130.dp)
                            .height(90.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("See Templates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Quick pre-filled shortcuts
                items(viewModel.getTemplatesList().take(3)) { template ->
                    Card(
                        onClick = { viewModel.createNewDocument(template.name, template.category, template.content) },
                        modifier = Modifier
                            .width(140.dp)
                            .height(90.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = when (template.name) {
                                    "Professional Resume" -> Icons.Default.ContactPage
                                    "Meeting Notes" -> Icons.Default.EventNote
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = template.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = template.description,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Documents",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { showSortMenu = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(sortBy, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Last Modified") },
                                onClick = { viewModel.updateSortBy("Modified"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Alphabetical") },
                                onClick = { viewModel.updateSortBy("Title"); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Word Count") },
                                onClick = { viewModel.updateSortBy("Words"); showSortMenu = false }
                            )
                        }
                    }
                }
            }

            // Documents List / Empty state
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = "Empty state icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Documents Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results match your search query." else "Get started by creating a blank document, choosing a professional template, or importing an existing file.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onSelect = { onSelectDocument(doc) },
                            onToggleStar = { viewModel.toggleDocumentStarred(doc) },
                            onDelete = { onDeleteDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageInfoBanner(documentsCount: Int) {
    val usedStorageStr = remember(documentsCount) {
        val extraMB = documentsCount * 0.12f
        String.format(Locale.US, "%.2f GB", 1.20f + extraMB)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LOCAL STORAGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$usedStorageStr of 128 GB used",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Linear Progress Bar
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.15f)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun StatsPanel(totalDocs: Int, totalWordsCount: Int, starredDocsCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(icon = Icons.Default.Description, value = totalDocs.toString(), label = "Documents")
            Divider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
            )
            StatItem(icon = Icons.Default.Segment, value = formatLargeNumber(totalWordsCount), label = "Total Words")
            Divider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
            )
            StatItem(icon = Icons.Default.Star, value = starredDocsCount.toString(), label = "Starred")
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DocumentItemCard(
    document: Document,
    onSelect: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(document.lastModified))
    val cleanSnippet = remember(document.content) {
        // Strip tags for a clean snippet
        document.content.replace(Regex("<[^>]*>"), " ").trim()
    }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge representation based on Category
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (document.category.lowercase()) {
                        "work" -> Icons.Default.Work
                        "personal" -> Icons.Default.Person
                        "imported" -> Icons.Default.FileOpen
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Snippet Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = document.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = document.category.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanSnippet.ifBlank { "Empty document..." },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedDate  •  ${document.wordCount} words",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleStar) {
                    Icon(
                        imageVector = if (document.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Star toggle",
                        tint = if (document.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete document",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: DocumentViewModel,
    onClose: () -> Unit,
    onExport: () -> Unit
) {
    var editTabSelected by remember { mutableStateOf(0) } // 0: Editor, 1: A4 Page View
    val keyboardController = LocalSoftwareKeyboardController.current

    // Set up local text field value state to track cursor position during style tag injection
    var textFieldValue by remember(viewModel.editContent) {
        mutableStateOf(TextFieldValue(
            text = viewModel.editContent,
            selection = androidx.compose.ui.text.TextRange(viewModel.editContent.length)
        ))
    }

    // Keep categories list for tags dropdown
    val tags = listOf("Work", "Personal", "Draft", "Imported")
    var showCategoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = viewModel.editTitle,
                        onValueChange = {
                            viewModel.editTitle = it
                            viewModel.autoSave()
                        },
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("document_title_input"),
                        singleLine = true,
                        decorationBox = @Composable { innerTextField ->
                            if (viewModel.editTitle.isEmpty()) {
                                Text("Untitled Document", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                            innerTextField()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and Back")
                    }
                },
                actions = {
                    // Category selector chip
                    Box {
                        TextButton(
                            onClick = { showCategoryMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(viewModel.editCategory, fontWeight = FontWeight.Bold, color = WriteFlowBlue)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            tags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag) },
                                    onClick = {
                                        viewModel.editCategory = tag
                                        viewModel.autoSave()
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Star document
                    IconButton(onClick = { viewModel.toggleStarred() }) {
                        Icon(
                            imageVector = if (viewModel.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star active document",
                            tint = if (viewModel.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Export Document
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.testTag("export_document_button")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Export file to storage", tint = WriteFlowBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Bottom stats and status
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Words: ${DocumentFormatter.getWordCount(viewModel.editContent)}  |  Chars: ${DocumentFormatter.getCharCount(viewModel.editContent)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Autosaved offline",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF107C41)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Autosaved",
                            fontSize = 11.sp,
                            color = Color(0xFF107C41)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (editTabSelected == 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
        ) {
            // Document tab selector mimicking MS Word options
            SecondaryTabRow(
                selectedTabIndex = editTabSelected,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = WriteFlowBlue
            ) {
                Tab(
                    selected = editTabSelected == 0,
                    onClick = { editTabSelected = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Draft Editor", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = editTabSelected == 1,
                    onClick = { editTabSelected = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Page Layout View", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            AnimatedContent(
                targetState = editTabSelected,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "EditorTabTransition"
            ) { tab ->
                if (tab == 0) {
                    // Draft Editor Workspace with Styles Toolbar
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Formatting Actions Toolbar
                        StyleBar(
                            onTagInsert = { tagOpen, tagClose ->
                                val selectionStart = textFieldValue.selection.start
                                val selectionEnd = textFieldValue.selection.end
                                viewModel.insertFormattingTag(tagOpen, tagClose, selectionStart, selectionEnd)
                            },
                            canUndo = viewModel.canUndo(),
                            canRedo = viewModel.canRedo(),
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() }
                        )

                        // Rich editor text typing area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    // Update content state in VM
                                    viewModel.editContent = it.text
                                    viewModel.autoSave()
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("document_content_input"),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("Start typing your offline word document... Use formatting shortcuts above for styling!") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                } else {
                    // A4 Page Print Preview rendering parsed styled text
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Mock Page representing A4 sheet
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(16.dp)
                                .shadow(4.dp, RoundedCornerShape(4.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color.LightGray)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp)
                            ) {
                                // Dynamic parsed content rendering
                                Text(
                                    text = DocumentFormatter.toAnnotatedString(
                                        viewModel.editContent,
                                        Color.Black
                                    ),
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        color = Color.Black,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StyleBar(
    onTagInsert: (String, String) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Undo and Redo operations
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo change",
                    tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo change",
                    tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Stylized headers
            StyleButton(text = "H1", onClick = { onTagInsert("<h1>", "</h1>") })
            StyleButton(text = "H2", onClick = { onTagInsert("<h2>", "</h2>") })
            StyleButton(text = "H3", onClick = { onTagInsert("<h3>", "</h3>") })

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Bold, Italic, Underline
            StyleIconButton(icon = Icons.Default.FormatBold, description = "Bold text", onClick = { onTagInsert("<b>", "</b>") })
            StyleIconButton(icon = Icons.Default.FormatItalic, description = "Italic text", onClick = { onTagInsert("<i>", "</i>") })
            StyleIconButton(icon = Icons.Default.FormatUnderlined, description = "Underline text", onClick = { onTagInsert("<u>", "</u>") })

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // List Bullet
            StyleIconButton(icon = Icons.Default.FormatListBulleted, description = "Bullet point", onClick = { onTagInsert("• ", "") })

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Text colors shortcuts
            ColorPaletteShortcut(color = Color.Red, colorHex = "#FF0000", onTagInsert)
            ColorPaletteShortcut(color = MaterialTheme.colorScheme.primary, colorHex = "#185ABD", onTagInsert)
            ColorPaletteShortcut(color = Color(0xFF107C41), colorHex = "#107C41", onTagInsert)
            ColorPaletteShortcut(color = Color(0xFFFFB300), colorHex = "#FFB300", onTagInsert)

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Highlighter text shortcuts
            HighlighterShortcut(color = Color.Yellow, colorHex = "#FFFF00", onTagInsert)
            HighlighterShortcut(color = Color(0xFF00FF00), colorHex = "#00FF00", onTagInsert)
            HighlighterShortcut(color = Color(0xFF00FFFF), colorHex = "#00FFFF", onTagInsert)
        }
    }
}

@Composable
fun StyleButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun StyleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ColorPaletteShortcut(color: Color, colorHex: String, onTagInsert: (String, String) -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onTagInsert("<color:$colorHex>", "</color>") }
            .border(1.dp, Color.LightGray, CircleShape)
    )
}

@Composable
fun HighlighterShortcut(color: Color, colorHex: String, onTagInsert: (String, String) -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color)
            .clickable { onTagInsert("<bg:$colorHex>", "</bg>") }
            .border(1.dp, Color.LightGray)
    )
}

@Composable
fun TemplateSelectorDialog(
    templates: List<TemplateItem>,
    onDismiss: () -> Unit,
    onSelectTemplate: (TemplateItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose a Template",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close templates")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(templates) { template ->
                        Card(
                            onClick = { onSelectTemplate(template) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (template.name) {
                                            "Blank Document" -> Icons.Default.Description
                                            "Professional Resume" -> Icons.Default.ContactPage
                                            "Meeting Notes" -> Icons.Default.EventNote
                                            "Project Proposal" -> Icons.Default.Feed
                                            else -> Icons.Default.Book
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = template.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = template.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to format larger numbers (e.g. 10450 -> 10.5K)
fun formatLargeNumber(number: Int): String {
    return if (number >= 1000) {
        String.format(Locale.getDefault(), "%.1fK", number / 1000.0)
    } else {
        number.toString()
    }
}

// SAF Display Name Extraction Helper
fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

@Composable
fun AboutDeveloperDialog(onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 16.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with custom icon and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "About App",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: About Developer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ABOUT DEVELOPER",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Prince AR Abdur Rahman",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Independent App Developer passionate about building modern Android applications, productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Contacts Header
                        Text(
                            text = "Contact & Socials",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Contacts
                        ContactRow(
                            icon = Icons.Default.Phone,
                            text = "WhatsApp: 01707424006",
                            onClick = { uriHandler.openUri("https://wa.me/8801707424006") }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ContactRow(
                            icon = Icons.Default.Phone,
                            text = "WhatsApp: 01796951709",
                            onClick = { uriHandler.openUri("https://wa.me/8801796951709") }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ContactRow(
                            icon = Icons.Default.Language,
                            text = "Facebook Profile",
                            onClick = { uriHandler.openUri("https://www.facebook.com/share/1BNn32qoJo/") }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ContactRow(
                            icon = Icons.Default.Language,
                            text = "Instagram Profile",
                            onClick = { uriHandler.openUri("https://www.instagram.com/ur___abdur____rahman__2008") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: About Company
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ABOUT COMPANY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NexVora Lab's Ofc",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "NexVora Lab's Ofc focuses on creating innovative Android applications designed to improve productivity, entertainment, learning, and digital experiences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Mission Row
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Our Mission",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Build fast, beautiful, privacy-friendly, and user-focused applications accessible to everyone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: Technical Info & Credits
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "1.0.0",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Developed by Prince AR Abdur Rahman",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Published by NexVora Lab's Ofc",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "© 2026 NexVora Lab's Ofc. All Rights Reserved.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Open Link",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
