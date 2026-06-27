package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Document
import com.example.data.DocumentDatabase
import com.example.data.DocumentRepository
import com.example.utils.DocumentFormatter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    
    init {
        val database = DocumentDatabase.getDatabase(application)
        repository = DocumentRepository(database.documentDao())
    }

    // Filter, Search and Sort States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _sortBy = MutableStateFlow("Modified") // Modified, Title, Words
    val sortBy = _sortBy.asStateFlow()

    // Combined Documents Flow
    val documents: StateFlow<List<Document>> = combine(
        repository.allDocuments,
        _searchQuery,
        _selectedCategory,
        _sortBy
    ) { docs, query, category, sort ->
        var filtered = docs
        
        // Search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.content.contains(query, ignoreCase = true) 
            }
        }
        
        // Category filter
        if (category != "All") {
            filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
        }
        
        // Sort filter
        when (sort) {
            "Title" -> filtered.sortedBy { it.title.lowercase() }
            "Words" -> filtered.sortedByDescending { it.wordCount }
            else -> filtered.sortedByDescending { it.lastModified }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Statistics Flows
    val totalDocuments: StateFlow<Int> = repository.allDocuments
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalWords: StateFlow<Int> = repository.allDocuments
        .map { list -> list.sumOf { it.wordCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val starredCount: StateFlow<Int> = repository.allDocuments
        .map { list -> list.count { it.isStarred } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Active Editor State
    var activeDocumentId by mutableStateOf<Long?>(null)
        private set

    var editTitle by mutableStateOf("")
    var editContent by mutableStateOf("")
    var editCategory by mutableStateOf("Work")
    var isStarred by mutableStateOf(false)

    // Undo/Redo Stacks
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    fun updateSortBy(sort: String) {
        _sortBy.value = sort
    }

    // Set active document
    fun setActiveDocument(document: Document?) {
        if (document != null) {
            activeDocumentId = document.id
            editTitle = document.title
            editContent = document.content
            editCategory = document.category
            isStarred = document.isStarred
            
            // Reset stacks
            undoStack.clear()
            redoStack.clear()
        } else {
            activeDocumentId = null
            editTitle = ""
            editContent = ""
            editCategory = "Work"
            isStarred = false
            undoStack.clear()
            redoStack.clear()
        }
    }

    // Formatting Insertion Helper
    fun insertFormattingTag(tagOpen: String, tagClose: String, selectionStart: Int, selectionEnd: Int) {
        val originalText = editContent
        val start = selectionStart.coerceIn(0, originalText.length)
        val end = selectionEnd.coerceIn(0, originalText.length)
        
        // Push state to undo stack before change
        pushUndoState(originalText)
        
        val newText = if (start != end) {
            // Some text is selected, wrap it
            val selected = originalText.substring(start, end)
            originalText.substring(0, start) + tagOpen + selected + tagClose + originalText.substring(end)
        } else {
            // Insertion mode
            originalText.substring(0, start) + tagOpen + tagClose + originalText.substring(start)
        }
        
        editContent = newText
        autoSave()
    }

    // Undo / Redo mechanics
    fun pushUndoState(state: String) {
        if (undoStack.isEmpty() || undoStack.last() != state) {
            undoStack.add(state)
            if (undoStack.size > 50) {
                undoStack.removeAt(0)
            }
        }
        // Redo gets cleared on a new manual user formatting change,
        // but not on standard typing. We only trigger this for style modifications
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prevState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(editContent)
            editContent = prevState
            autoSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(editContent)
            editContent = nextState
            autoSave()
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // Autosave Active Document
    fun autoSave() {
        val id = activeDocumentId ?: return
        val currentTitle = editTitle.ifBlank { "Untitled Document" }
        val currentContent = editContent
        val wCount = DocumentFormatter.getWordCount(currentContent)
        val cCount = DocumentFormatter.getCharCount(currentContent)
        
        viewModelScope.launch {
            val updatedDoc = Document(
                id = id,
                title = currentTitle,
                content = currentContent,
                wordCount = wCount,
                charCount = cCount,
                lastModified = System.currentTimeMillis(),
                isStarred = isStarred,
                category = editCategory
            )
            repository.updateDocument(updatedDoc)
        }
    }

    // Save and Close
    fun saveAndClose(onDone: () -> Unit) {
        val currentTitle = editTitle.ifBlank { "Untitled Document" }
        val currentContent = editContent
        val wCount = DocumentFormatter.getWordCount(currentContent)
        val cCount = DocumentFormatter.getCharCount(currentContent)
        
        viewModelScope.launch {
            val id = activeDocumentId
            if (id != null) {
                // Update existing
                val updatedDoc = Document(
                    id = id,
                    title = currentTitle,
                    content = currentContent,
                    wordCount = wCount,
                    charCount = cCount,
                    lastModified = System.currentTimeMillis(),
                    isStarred = isStarred,
                    category = editCategory
                )
                repository.updateDocument(updatedDoc)
            } else {
                // Create new
                val newDoc = Document(
                    title = currentTitle,
                    content = currentContent,
                    wordCount = wCount,
                    charCount = cCount,
                    lastModified = System.currentTimeMillis(),
                    isStarred = isStarred,
                    category = editCategory
                )
                repository.insertDocument(newDoc)
            }
            setActiveDocument(null)
            onDone()
        }
    }

    // Create New Document (or Template)
    fun createNewDocument(title: String, category: String, content: String = "") {
        viewModelScope.launch {
            val newDoc = Document(
                title = title,
                content = content,
                wordCount = DocumentFormatter.getWordCount(content),
                charCount = DocumentFormatter.getCharCount(content),
                lastModified = System.currentTimeMillis(),
                isStarred = false,
                category = category
            )
            val generatedId = repository.insertDocument(newDoc)
            // Retrieve created document to set active
            val docWithId = newDoc.copy(id = generatedId)
            setActiveDocument(docWithId)
        }
    }

    // Star/Unstar Document in Editor
    fun toggleStarred() {
        isStarred = !isStarred
        autoSave()
    }

    // Star/Unstar Document from List
    fun toggleDocumentStarred(document: Document) {
        viewModelScope.launch {
            val updated = document.copy(isStarred = !document.isStarred)
            repository.updateDocument(updated)
        }
    }

    // Delete Document
    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
            if (activeDocumentId == document.id) {
                setActiveDocument(null)
            }
        }
    }

    // Import Document from Local File Uri Content
    fun importDocumentFromFile(fileName: String, content: String) {
        val cleanName = fileName.substringBeforeLast(".")
        viewModelScope.launch {
            val newDoc = Document(
                title = cleanName,
                content = content,
                wordCount = DocumentFormatter.getWordCount(content),
                charCount = DocumentFormatter.getCharCount(content),
                lastModified = System.currentTimeMillis(),
                category = "Imported"
            )
            val id = repository.insertDocument(newDoc)
            setActiveDocument(newDoc.copy(id = id))
        }
    }

    // Load templates with professionally pre-formatted headers
    fun getTemplatesList(): List<TemplateItem> {
        return listOf(
            TemplateItem(
                name = "Blank Document",
                category = "Draft",
                description = "Start with a clean canvas",
                content = ""
            ),
            TemplateItem(
                name = "Professional Resume",
                category = "Work",
                description = "Clean modern CV with styled sections",
                content = """<h1><b>JOHN DOE</b></h1>
<color:#555555>Mobile: +1 234 567 890 | Email: john.doe@email.com | London, UK</color>

<h2><color:#185ABD>PROFESSIONAL SUMMARY</color></h2>
An experienced, highly motivated <b>Software Engineer</b> with 5+ years of building elegant user-centered designs. Proficient in crafting robust Kotlin and Android architectures.

<h2><color:#185ABD>EXPERIENCE</color></h2>
<b>Senior Android Engineer | TechCorp Inc.</b>
<i>2022 - Present</i>
• Lead developmental features resulting in a 25% performance improvement.
• Formulate cleaner Material 3 design implementations.

<b>Mobile Software Architect | Devflow Solutions</b>
<i>2020 - 2022</i>
• Pioneered responsive mobile offline capabilities using Room database.
• Engineered a document viewer utilized by over 50,000 active users.

<h2><color:#185ABD>EDUCATION</color></h2>
<b>B.Sc. in Computer Science</b>
<i>University of Oxford | 2016 - 2020</i>

<h2><color:#185ABD>TECHNICAL SKILLS</color></h2>
• <b>Languages:</b> Kotlin, Java, SQL, Markdown, HTML
• <b>Frameworks:</b> Jetpack Compose, Android SDK, Room DB, Coroutines
"""
            ),
            TemplateItem(
                name = "Meeting Notes",
                category = "Work",
                description = "Log agenda, attendees and action plans",
                content = """<h1><b>MEETING MINUTES</b></h1>
<b>Date:</b> 2026-06-27 | <b>Time:</b> 10:00 AM UTC
<b>Subject:</b> WriteFlow App Planning & Architecture Setup
<b>Organizer:</b> Product Development Team

<h2><b>1. ATTENDEES</b></h2>
• Sarah Jenkins (Product Manager)
• Alex Rivera (Lead UI/UX Designer)
• John Doe (Mobile Engineering Lead)

<h2><b>2. AGENDA DISCUSSIONS</b></h2>
• <b>Offline Architecture:</b> Settled on Room database as the primary persistence layer to support lightning-fast offline operations.
• <b>Visual Identity:</b> Reviewed and approved the gorgeous <b>WriteFlow</b> deep blue logo matching corporate aesthetics.
• <b>File Portability:</b> Emphasized the usage of the Android Storage Access Framework (SAF) to let users export documents securely to external downloads.

<h2><b>3. ACTION ITEMS</b></h2>
• <b>[Alex]</b> Finalize adaptive designs for tablet layouts. <color:#FF0000><i>(Due: July 2nd)</i></color>
• <b>[John]</b> Complete implementation of custom rich-text HTML parser. <color:#009900><i>(Completed)</i></color>
"""
            ),
            TemplateItem(
                name = "Project Proposal",
                category = "Work",
                description = "Formal document defining project scope & timeline",
                content = """<h1><b>PROJECT CHARTER: WRITEFLOW MOBILE</b></h1>
<i>Prepared by the Android Core Team | Version 1.0</i>

<h2><color:#185ABD>1. EXECUTIVE SUMMARY</color></h2>
The goal of Project <b>WriteFlow</b> is to provide a premium, lightning-fast offline-first document editor. It bridges the gap between raw text fields and complex, bulky office suites by introducing structured, aesthetic page layouts on mobile devices.

<h2><color:#185ABD>2. KEY DELIVERABLES</color></h2>
• Complete client-side document compiler.
• WYSIWYG tag formatting shortcuts (Bold, Italic, Underline, Headers, Highlights).
• Seamless local document sandbox storage with Room.
• Safe document sharing and local storage file extraction.

<h2><color:#185ABD>3. PROJECT TIMELINE</color></h2>
• <b>Phase 1: Database & Parser Engine:</b> Day 1 to 2 <color:#009900><b>(Active)</b></color>
• <b>Phase 2: Visual Page Rendering:</b> Day 3 to 4
• <b>Phase 3: Storage Access Integrations:</b> Day 5
"""
            ),
            TemplateItem(
                name = "Creative Journal",
                category = "Personal",
                description = "Express thoughts, reflections, or start stories",
                content = """<h1><b>DAILY JOURNAL ENTRY</b></h1>
<b>Date:</b> <color:#888888><i>June 27, 2026</i></color> | <b>Mood:</b> Inspired ☕

<h2><b>REFLECTIONS OF THE DAY</b></h2>
Today was a highly productive coding day. We initiated the layout design for <b>WriteFlow</b>. Seeing the custom flowing pen logo load perfectly as the launcher icon was incredibly satisfying. 

<h2><b>GOALS FOR TOMORROW</b></h2>
• Ensure responsive document viewing on large tablet screens.
• Enhance typing latency by profiling Room's suspend threads.
• Add extra highlighter color palettes to the floating style bar.

<i>"The secret of getting ahead is getting started." - Mark Twain</i>
"""
            )
        )
    }
}

data class TemplateItem(
    val name: String,
    val category: String,
    val description: String,
    val content: String
)
