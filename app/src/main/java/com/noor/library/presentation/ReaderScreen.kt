package com.noor.library.presentation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.noor.library.audio.AudioCache
import com.noor.library.audio.AudioController
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.library.data.BookCatalog
import com.noor.library.data.Settings
import com.noor.library.data.db.PassageEntity
import com.noor.library.presentation.theme.Gold
import com.noor.library.presentation.theme.arabicFamily
import com.noor.library.presentation.theme.persianFamily
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    appVm: AppViewModel,
    settings: Settings,
    bookId: String,
    type: String,
    number: Int,
    initialPassage: Int,
    onBack: () -> Unit,
    onOpenSection: (String, String, Int) -> Unit,
) {
    val repo = appVm.repo
    val vm: ReaderViewModel = viewModel(
        key = "reader_${bookId}_${type}_$number",
        factory = VMFactory { ReaderViewModel(repo, bookId, type, number) }
    )
    val passages by vm.passages.collectAsState()
    val bookmarked by vm.bookmarked.collectAsState()
    val notes by vm.notes.collectAsState()
    val section by vm.section.collectAsState()
    val next by vm.next.collectAsState()
    val prev by vm.prev.collectAsState()
    val info = BookCatalog.byId(bookId)
    val sectionTitle = section?.title ?: ""
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showSheet by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<PassageEntity?>(null) }

    // Jump to the requested passage once, when content has loaded
    var jumped by remember { mutableStateOf(false) }
    LaunchedEffect(passages) {
        val list = passages
        if (!jumped && list != null && list.isNotEmpty()) {
            val idx = list.indexOfFirst { it.passageNumber == initialPassage }
            if (idx > 0) listState.scrollToItem(idx)
            jumped = true
        }
    }
    // Persist reading position + progress
    LaunchedEffect(passages) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { idx ->
            val list = passages
            if (list != null && idx in list.indices) vm.onPositionChanged(list[idx].passageNumber, idx)
        }
    }

    // ---- Quran audio (EveryAyah streaming + cache)
    val isQuran = bookId == "quran"
    val audio = remember { AudioController(context.applicationContext) }
    DisposableEffect(Unit) {
        if (isQuran) audio.connect()
        onDispose { audio.release() }
    }
    val audioState by audio.state.collectAsState()
    var showAudio by remember { mutableStateOf(false) }
    var dlDone by remember { mutableIntStateOf(0) }
    var dlTotal by remember { mutableIntStateOf(0) }
    val audioScope = rememberCoroutineScope()
    val playingHere = audioState.active && audioState.playingSurah == number
    LaunchedEffect(audioState.playingAyah, audioState.playingSurah) {
        val list = passages
        if (isQuran && playingHere && list != null) {
            val idx = list.indexOfFirst { it.passageNumber == audioState.playingAyah }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }
    fun startAudio() {
        val list = passages ?: return
        if (list.isEmpty()) return
        val start = list.getOrNull(listState.firstVisibleItemIndex)?.passageNumber ?: 1
        audio.playSurah(settings.reciter, number, sectionTitle, list.size, start)
    }

    fun shareText(p: PassageEntity): String = buildString {
        append(p.arabicText)
        if (p.persianTranslation.isNotBlank()) append("\n\n").append(p.persianTranslation)
        append("\n\n— ").append(formatRef(bookId, type, sectionTitle, number, p.passageNumber))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sectionDisplayTitle(bookId, type, number, sectionTitle), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    if (isQuran) IconButton(onClick = { showAudio = !showAudio }) { Icon(Icons.Filled.Headphones, "تلاوت") }
                    IconButton(onClick = { showSheet = true }) { Icon(Icons.Filled.Tune, "تنظیمات مطالعه") }
                }
            )
        },
        bottomBar = {
            if (isQuran && showAudio) {
                AudioBar(
                    state = audioState,
                    reciterFolder = settings.reciter,
                    downloadDone = dlDone, downloadTotal = dlTotal,
                    onTogglePlay = { if (playingHere) audio.togglePlay() else startAudio() },
                    onNext = { audio.next() },
                    onPrevious = { audio.previous() },
                    onSpeed = { audio.setSpeed(it) },
                    onReciter = { r ->
                        appVm.update { it.setReciter(r) }
                        if (playingHere) {
                            val cur = audioState.playingAyah
                            audio.playSurah(r, number, sectionTitle, passages?.size ?: 0, cur.coerceAtLeast(1))
                        }
                    },
                    onDownload = {
                        val count = passages?.size ?: 0
                        if (count > 0) {
                            dlDone = 0; dlTotal = count
                            audioScope.launch {
                                try {
                                    AudioCache.downloadSurah(context.applicationContext, settings.reciter, number, count) { dlDone = it }
                                } catch (e: Exception) {
                                    dlTotal = 0
                                    android.widget.Toast.makeText(context, "دانلود ناموفق بود؛ اینترنت را بررسی کنید", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onClose = { audio.stop(); showAudio = false }
                )
            }
        }
    ) { pad ->
        val list = passages
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(pad))
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("متنی برای این بخش در دسترس نیست.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(list, key = { _, p -> p.id }) { _, p ->
                    PassageCard(
                        p = p, settings = settings, noun = info.passageNoun,
                        highlighted = playingHere && audioState.playingAyah == p.passageNumber,
                        bookmarked = p.passageNumber in bookmarked,
                        note = notes[p.passageNumber],
                        onBookmark = { vm.toggleBookmark(p) },
                        onNote = { noteTarget = p },
                        onCopy = { clipboard.setText(AnnotatedString(shareText(p))) },
                        onShare = {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                this.type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText(p))
                            }
                            context.startActivity(Intent.createChooser(i, "اشتراک‌گذاری"))
                        }
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val pv = prev
                        val nx = next
                        if (pv != null) TextButton(onClick = { onOpenSection(bookId, pv.sectionType, pv.number) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                            Text("قبلی", Modifier.padding(start = 4.dp))
                        } else Spacer(Modifier.size(1.dp))
                        if (nx != null) TextButton(onClick = { onOpenSection(bookId, nx.sectionType, nx.number) }) {
                            Text("بعدی", Modifier.padding(end = 4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                ReadingSettingsPanel(settings) { appVm.update(it) }
            }
        }
    }

    noteTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(notes[target.passageNumber] ?: "") }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("یادداشت — ${info.passageNoun} ${target.passageNumber}") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(160.dp))
            },
            confirmButton = { TextButton(onClick = { vm.saveNote(target, text); noteTarget = null }) { Text("ذخیره") } },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun PassageCard(
    p: PassageEntity,
    settings: Settings,
    noun: String,
    highlighted: Boolean,
    bookmarked: Boolean,
    note: String?,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = if (highlighted) androidx.compose.foundation.BorderStroke(2.dp, Gold) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                NumberBadge(p.passageNumber)
                Row {
                    IconButton(onClick = onBookmark, modifier = Modifier.size(36.dp)) {
                        Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "نشانک", tint = if (bookmarked) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNote, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Edit, "یادداشت", tint = if (note != null) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ContentCopy, "کپی", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Share, "اشتراک", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                p.arabicText,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = arabicFamily(settings.arabicFont),
                fontSize = settings.arabicSize.sp,
                lineHeight = (settings.arabicSize * settings.lineSpacing).sp,
                textAlign = TextAlign.Start,
            )
            if (settings.showTranslation && p.persianTranslation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                Spacer(Modifier.height(10.dp))
                Text(
                    p.persianTranslation,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = persianFamily(settings.persianFont),
                    fontSize = settings.persianSize.sp,
                    lineHeight = (settings.persianSize * settings.lineSpacing).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            if (note != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "یادداشت: $note",
                    fontSize = 13.sp,
                    color = Gold,
                    modifier = Modifier.fillMaxWidth().background(Gold.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(8.dp)
                )
            }
        }
    }
}
