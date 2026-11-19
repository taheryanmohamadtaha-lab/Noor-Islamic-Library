package com.noor.library.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.library.data.BookCatalog
import com.noor.library.data.Repository
import com.noor.library.data.db.BookmarkWithText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: Repository, onBack: () -> Unit, onOpenReader: (String, String, Int, Int) -> Unit) {
    val vm: BookmarksViewModel = viewModel(factory = VMFactory { BookmarksViewModel(repo) })
    val bookmarks by vm.bookmarks.collectAsState()
    val notes by vm.notes.collectAsState()
    val history by vm.history.collectAsState()
    val folders by vm.folders.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var folderFilter by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<BookmarkWithText?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کتابخانه من", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("نشانک‌ها") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("یادداشت‌ها") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("تاریخچه") })
            }
            when (tab) {
                0 -> {
                    if (folders.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        ChipRow(listOf("" to "همه") + folders.map { it to it }, folderFilter) { folderFilter = it }
                    }
                    val shown = if (folderFilter.isEmpty()) bookmarks else bookmarks.filter { it.folder == folderFilter }
                    if (shown.isEmpty()) EmptyHint("هنوز نشانکی ندارید. در حالت مطالعه روی آیکون نشانک بزنید.")
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it.id }) { b ->
                            Card(
                                Modifier.fillMaxWidth().clickable { onOpenReader(b.bookId, b.sectionType, b.sectionNumber, b.passageNumber) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            formatRef(b.bookId, b.sectionType, b.sectionTitle ?: "", b.sectionNumber, b.passageNumber),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(b.arabicText ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                                        Text("پوشه: ${b.folder}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { moveTarget = b }) { Icon(Icons.Filled.Folder, "انتقال") }
                                    IconButton(onClick = { vm.remove(b) }) { Icon(Icons.Filled.Delete, "حذف") }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (notes.isEmpty()) EmptyHint("هنوز یادداشتی ندارید.")
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(notes, key = { it.id }) { n ->
                            Card(
                                Modifier.fillMaxWidth().clickable { onOpenReader(n.bookId, n.sectionType, n.sectionNumber, n.passageNumber) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            formatRef(n.bookId, n.sectionType, n.sectionTitle ?: "", n.sectionNumber, n.passageNumber),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(n.content, modifier = Modifier.padding(top = 4.dp))
                                    }
                                    IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Filled.Delete, "حذف") }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (history.isEmpty()) EmptyHint("تاریخچه‌ای وجود ندارد.")
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.clearHistory() }) { Text("پاک‌کردن تاریخچه") }
                    }
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id }) { h ->
                            Card(
                                Modifier.fillMaxWidth().clickable { onOpenReader(h.bookId, h.sectionType, h.sectionNumber, h.passageNumber) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    "${BookCatalog.byId(h.bookId).title} · ${sectionDisplayTitle(h.bookId, h.sectionType, h.sectionNumber, h.title)}",
                                    Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    moveTarget?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.folder) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("انتقال به پوشه") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("نام پوشه") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.move(b.id, name.trim())
                    moveTarget = null
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
