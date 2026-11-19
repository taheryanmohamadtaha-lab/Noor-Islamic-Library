package com.noor.library.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.library.data.BookCatalog
import com.noor.library.data.Repository
import com.noor.library.data.Settings
import com.noor.library.data.db.BookEntity
import com.noor.library.presentation.theme.EmeraldDeep
import com.noor.library.presentation.theme.Emerald
import com.noor.library.presentation.theme.Gold
import com.noor.library.presentation.theme.arabicFamily
import com.noor.library.presentation.theme.islamicPattern
import com.noor.library.util.TextNormalizer

private fun bookIcon(id: String): ImageVector = when (id) {
    "quran" -> Icons.Filled.MenuBook
    "mafatih" -> Icons.Filled.AutoStories
    "sahifa" -> Icons.Filled.Spa
    else -> Icons.Filled.HistoryEdu
}

@Composable
fun HomeScreen(
    repo: Repository,
    settings: Settings,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String, String, Int, Int) -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = VMFactory { HomeViewModel(repo) })
    val books by vm.books.collectAsState()
    val history by vm.history.collectAsState()
    val bookmarkCount by vm.bookmarkCount.collectAsState()
    val last by vm.lastPosition.collectAsState()
    val daily by vm.daily.collectAsState()
    val dailyTitle by vm.dailyTitle.collectAsState()
    LaunchedEffect(books) { vm.loadDaily() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        // Header
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Emerald, EmeraldDeep)))
                    .islamicPattern()
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("کتابخانه نور", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("NOOR ISLAMIC LIBRARY", color = Color(0xFFCFE3DA), fontSize = 12.sp, letterSpacing = 2.sp)
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "تنظیمات", tint = Gold) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .clickable { onSearch() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Color.White)
                        Text("جستجو در قرآن، دعاها و نهج‌البلاغه…", Modifier.padding(start = 10.dp), color = Color(0xFFE3EDE8), fontSize = 14.sp)
                    }
                }
            }
        }

        // Continue reading
        val l = last
        if (l != null) item {
            val info = BookCatalog.byId(l.bookId)
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable {
                    onOpenReader(l.bookId, l.sectionType, l.sectionNumber, l.passage)
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("ادامه مطالعه", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${info.title} · ${BookCatalog.sectionTypeLabel(l.sectionType)} ${TextNormalizer.toPersianDigits(l.sectionNumber)} · ${info.passageNoun} ${TextNormalizer.toPersianDigits(l.passage)}",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }

        // Four books
        item {
            val byId = books.associateBy { it.id }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in BookCatalog.all.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (info in row) {
                            BookCard(info.id, info.title, info.subtitle, byId[info.id], Modifier.weight(1f)) { onOpenBook(info.id) }
                        }
                    }
                }
            }
        }

        // Daily goal
        item {
            val goal = settings.dailyGoal.coerceAtLeast(1)
            val progress = (settings.todayCount.toFloat() / goal).coerceIn(0f, 1f)
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("هدف مطالعه امروز", fontWeight = FontWeight.Bold)
                        Text("${TextNormalizer.toPersianDigits(settings.todayCount)} از ${TextNormalizer.toPersianDigits(goal)}", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                }
            }
        }

        // Bookmarks entry
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onLibrary() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmarks, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("نشانک‌ها، یادداشت‌ها و تاریخچه", fontWeight = FontWeight.Bold)
                        Text("${TextNormalizer.toPersianDigits(bookmarkCount)} نشانک", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Daily passage (real content from the database only)
        val d = daily
        if (d != null) item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable {
                    onOpenReader(d.bookId, d.sectionType, d.sectionNumber, d.passageNumber)
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("متن منتخب روز", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        d.arabicText, fontFamily = arabicFamily(settings.arabicFont), fontSize = 22.sp, lineHeight = 40.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (d.persianTranslation.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(d.persianTranslation, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(formatRef(d.bookId, d.sectionType, dailyTitle, d.sectionNumber, d.passageNumber), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Recently viewed
        if (history.isNotEmpty()) {
            item {
                Text("آخرین مطالب مشاهده‌شده", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.Bold)
            }
            items(history, key = { it.id }) { h ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenReader(h.bookId, h.sectionType, h.sectionNumber, h.passageNumber) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(bookIcon(h.bookId), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "${BookCatalog.byId(h.bookId).title} · ${sectionDisplayTitle(h.bookId, h.sectionType, h.sectionNumber, h.title)}",
                        Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCard(id: String, title: String, subtitle: String, book: BookEntity?, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(150.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize().islamicPattern(alpha = 0.07f)) {
            Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(bookIcon(id), null, tint = Gold, modifier = Modifier.size(34.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book != null && book.status != "complete") {
                        Spacer(Modifier.height(4.dp))
                        StatusChip(book.status)
                    }
                }
            }
        }
    }
}
