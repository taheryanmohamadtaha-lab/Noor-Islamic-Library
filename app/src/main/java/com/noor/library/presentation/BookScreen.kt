package com.noor.library.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.library.data.BookCatalog
import com.noor.library.data.Repository
import com.noor.library.util.TextNormalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(repo: Repository, bookId: String, onBack: () -> Unit, onOpenSection: (String, Int) -> Unit) {
    val vm: BookViewModel = viewModel(key = "book_$bookId", factory = VMFactory { BookViewModel(repo, bookId) })
    val book by vm.book.collectAsState()
    val sections by vm.sections.collectAsState()
    val info = BookCatalog.byId(bookId)
    var filter by remember { mutableStateOf("") }
    val types = sections.map { it.sectionType }.distinct()
    val shown = if (filter.isEmpty()) sections else sections.filter { it.sectionType == filter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info.title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val b = book
            if (b != null && b.status != "complete") item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(b.status)
                        Text(
                            if (b.status == "missing")
                                "متن معتبر این کتاب هنوز به برنامه افزوده نشده است. به‌جای نمایش متن ساختگی، این بخش خالی نگه داشته شده است. جزئیات در «تنظیمات › منابع و صحت متون» آمده است."
                            else
                                "این کتاب به‌صورت ناقص در دسترس است؛ فقط بخش‌های تأییدشده نمایش داده می‌شوند.",
                            fontSize = 13.sp
                        )
                    }
                }
            }
            if (b != null && b.status != "missing") {
                item {
                    Text(
                        "منبع: ${b.source}" + if (b.translator.isNotBlank()) " · مترجم: ${b.translator}" else "",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (types.size > 1) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow(listOf("" to "همه") + types.map { it to BookCatalog.sectionTypeLabel(it) }, filter) { filter = it }
                }
            }
            items(shown, key = { it.id }) { s ->
                Card(
                    Modifier.fillMaxWidth().clickable { onOpenSection(s.sectionType, s.number) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberBadge(s.number)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(sectionDisplayTitle(bookId, s.sectionType, s.number, s.title), fontWeight = FontWeight.Medium)
                            Text(
                                "${TextNormalizer.toPersianDigits(s.passageCount)} ${info.passageNoun}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
