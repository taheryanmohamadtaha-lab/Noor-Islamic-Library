package com.noor.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noor.library.data.ContentImporter
import com.noor.library.data.Repository
import com.noor.library.data.Settings
import com.noor.library.data.db.BookEntity
import com.noor.library.data.db.PassageEntity
import com.noor.library.data.db.SectionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VMFactory(private val make: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = make() as T
}

sealed interface ImportState {
    data object Loading : ImportState
    data object Ready : ImportState
    data class Failed(val message: String) : ImportState
}

class AppViewModel(val repo: Repository, private val importer: ContentImporter) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.prefs.settings
        .map<Settings, Settings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _import = MutableStateFlow<ImportState>(ImportState.Loading)
    val importState: StateFlow<ImportState> = _import

    init { runImport() }

    fun runImport() {
        _import.value = ImportState.Loading
        viewModelScope.launch {
            _import.value = try {
                importer.importIfNeeded()
                ImportState.Ready
            } catch (e: Throwable) {
                ImportState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun update(block: suspend (com.noor.library.data.Prefs) -> Unit) {
        viewModelScope.launch { block(repo.prefs) }
    }
}

class HomeViewModel(private val repo: Repository) : ViewModel() {
    val books: StateFlow<List<BookEntity>> =
        repo.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repo.history(8).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarkCount: StateFlow<Int> =
        repo.bookmarkCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lastPosition = repo.prefs.lastPosition.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _daily = MutableStateFlow<PassageEntity?>(null)
    val daily: StateFlow<PassageEntity?> = _daily
    private val _dailyTitle = MutableStateFlow("")
    val dailyTitle: StateFlow<String> = _dailyTitle

    fun loadDaily() {
        viewModelScope.launch {
            val p = repo.dailyPassage()
            _daily.value = p
            _dailyTitle.value = p?.let { repo.sectionTitle(it.bookId, it.sectionType, it.sectionNumber) } ?: ""
        }
    }
}

class BookViewModel(repo: Repository, bookId: String) : ViewModel() {
    val book: StateFlow<BookEntity?> = repo.book(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val sections: StateFlow<List<SectionEntity>> =
        repo.sections(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class ReaderViewModel(
    private val repo: Repository,
    private val bookId: String,
    private val type: String,
    private val number: Int,
) : ViewModel() {
    val passages: StateFlow<List<PassageEntity>?> =
        repo.passages(bookId, type, number).map<List<PassageEntity>, List<PassageEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bookmarked: StateFlow<Set<Int>> = repo.bookmarkedPassages(bookId, type, number)
        .map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val notes: StateFlow<Map<Int, String>> = repo.notesFor(bookId, type, number)
        .map { list -> list.associate { it.passageNumber to it.content } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _section = MutableStateFlow<SectionEntity?>(null)
    val section: StateFlow<SectionEntity?> = _section
    private val _next = MutableStateFlow<SectionEntity?>(null)
    val next: StateFlow<SectionEntity?> = _next
    private val _prev = MutableStateFlow<SectionEntity?>(null)
    val prev: StateFlow<SectionEntity?> = _prev

    init {
        viewModelScope.launch {
            _section.value = repo.section(bookId, type, number)
            _next.value = repo.nextSection(bookId, type, number)
            _prev.value = repo.previousSection(bookId, type, number)
        }
    }

    fun toggleBookmark(p: PassageEntity) {
        viewModelScope.launch { repo.toggleBookmark(p, p.passageNumber in bookmarked.value) }
    }

    fun saveNote(p: PassageEntity, text: String) {
        viewModelScope.launch { repo.saveNote(p, text) }
    }

    private var lastMax = -1
    fun onPositionChanged(passage: Int, index: Int) {
        viewModelScope.launch {
            repo.recordVisit(bookId, type, number, passage)
            if (index > lastMax) {
                if (lastMax >= 0) repo.prefs.addProgress(index - lastMax)
                lastMax = index
            }
        }
    }
}

data class SearchResult(val passage: PassageEntity, val sectionTitle: String, val snippet: String)

class SearchViewModel(private val repo: Repository) : ViewModel() {
    val query = MutableStateFlow("")
    val bookFilter = MutableStateFlow("")
    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched

    fun run(ignoreHarakat: Boolean) {
        val q = query.value
        viewModelScope.launch {
            val hits = repo.search(q, bookFilter.value, ignoreHarakat)
            _results.value = hits.map { p ->
                val title = repo.sectionTitle(p.bookId, p.sectionType, p.sectionNumber) ?: ""
                SearchResult(p, title, snippet(p, q))
            }
            _searched.value = true
        }
    }

    private fun snippet(p: PassageEntity, q: String): String {
        val src = if (p.persianTranslation.contains(q.trim(), ignoreCase = true)) p.persianTranslation else p.arabicText
        val idx = src.indexOf(q.trim(), ignoreCase = true)
        if (idx < 0) return if (src.length > 180) src.take(180) + "…" else src
        val start = (idx - 60).coerceAtLeast(0)
        val end = (idx + q.length + 100).coerceAtMost(src.length)
        return (if (start > 0) "…" else "") + src.substring(start, end) + (if (end < src.length) "…" else "")
    }
}

class BookmarksViewModel(private val repo: Repository) : ViewModel() {
    val bookmarks = repo.bookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes = repo.notes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repo.history(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = repo.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(b: com.noor.library.data.db.BookmarkWithText) { viewModelScope.launch { repo.removeBookmark(b) } }
    fun move(id: Long, folder: String) { viewModelScope.launch { repo.moveBookmark(id, folder) } }
    fun deleteNote(id: Long) { viewModelScope.launch { repo.deleteNote(id) } }
    fun clearHistory() { viewModelScope.launch { repo.clearHistory() } }
}
