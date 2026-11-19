package com.noor.library.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import com.noor.library.BuildConfig
import com.noor.library.data.BookCatalog
import com.noor.library.data.PdfPages
import com.noor.library.data.Settings
import com.noor.library.presentation.theme.Cream
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    appVm: AppViewModel,
    settings: Settings,
    bookId: String,
    initialPage: Int,
    onBack: () -> Unit,
) {
    val repo = appVm.repo
    val context = LocalContext.current
    val info = BookCatalog.byId(bookId)
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<PdfPages?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bookId) {
        try {
            pages = PdfPages.open(context.applicationContext, info.pdfAsset ?: "", "v${BuildConfig.VERSION_CODE}")
        } catch (e: Exception) {
            failure = e.message ?: "خطا در باز کردن کتاب"
        }
    }
    DisposableEffect(bookId) { onDispose { pages?.close() } }

    val listState = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var currentPage by remember { mutableStateOf(initialPage.coerceAtLeast(1)) }

    // jump to the requested page once the document is open
    var jumped by remember { mutableStateOf(false) }
    LaunchedEffect(pages) {
        val p = pages
        if (p != null && !jumped) {
            listState.scrollToItem((initialPage - 1).coerceIn(0, p.pageCount - 1))
            jumped = true
        }
    }
    // track + persist the page being read
    LaunchedEffect(pages) {
        val p = pages ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { idx ->
            if (!jumped) return@collectLatest
            val page = idx + 1
            currentPage = page
            val ps = repo.passageByPage(bookId, page)
            if (ps != null) repo.recordVisit(bookId, ps.sectionType, ps.sectionNumber, page)
            repo.prefs.addProgress(1)
        }
    }

    val bookmarkedCount by remember(currentPage) { repo.pageBookmarked(bookId, currentPage) }.collectAsState(initial = 0)
    val noteText by remember(currentPage) { repo.pageNote(bookId, currentPage) }.collectAsState(initial = null)
    var showNote by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }

    var showScroll by remember { mutableStateOf(false) }
    var scrolling by remember { mutableStateOf(false) }
    var liveSpeed by remember { mutableFloatStateOf(settings.autoScrollSpeed) }
    AutoScrollEffect(listState, scrolling && showScroll, liveSpeed) { scrolling = false }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val pageWidthDp = (screenWidthDp * zoom).dp
    val pageWidthPx = with(density) { pageWidthDp.toPx() }.toInt().coerceIn(300, 2600)
    val filter: ColorFilter? = when (settings.theme) {
        "dark" -> InvertFilter
        "cream" -> ColorFilter.tint(Cream, BlendMode.Multiply)
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(info.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        val total = pages?.pageCount ?: 0
                        if (total > 0) Text(
                            "صفحه ${TextNormalizer.toPersianDigits(currentPage)} از ${TextNormalizer.toPersianDigits(total)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { showScroll = !showScroll; if (!showScroll) scrolling = false }) {
                        Icon(Icons.Filled.KeyboardDoubleArrowUp, "پیمایش خودکار")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val ps = repo.passageByPage(bookId, currentPage) ?: return@launch
                            repo.toggleBookmark(ps, bookmarkedCount > 0)
                        }
                    }) {
                        Icon(if (bookmarkedCount > 0) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "نشانک")
                    }
                    IconButton(onClick = { showNote = true }) { Icon(Icons.Filled.Edit, "یادداشت") }
                }
            )
        },
        bottomBar = {
            Column {
                if (showScroll) {
                    AutoScrollBar(
                        running = scrolling, speed = liveSpeed,
                        onToggle = { scrolling = !scrolling },
                        onSpeedChange = { liveSpeed = it },
                        onSpeedCommit = { appVm.update { p -> p.setAutoScrollSpeed(liveSpeed) } },
                        onClose = { scrolling = false; showScroll = false }
                    )
                }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Icon(Icons.Filled.Remove, "کوچک‌نمایی") }
                            Text("${TextNormalizer.toPersianDigits((zoom * 100).toInt())}٪", fontSize = 12.sp)
                            IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }) { Icon(Icons.Filled.Add, "بزرگ‌نمایی") }
                        }
                        TextButton(onClick = { showJump = true }) { Text("رفتن به صفحه") }
                    }
                }
            }
        }
    ) { pad ->
        val p = pages
        when {
            failure != null -> Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("خطا در نمایش کتاب: $failure")
            }
            p == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Box(Modifier.fillMaxSize().padding(pad).horizontalScroll(rememberScrollState())) {
                LazyColumn(state = listState, modifier = Modifier.width(pageWidthDp).fillMaxSize()) {
                    items(p.pageCount) { index ->
                        PdfPageItem(p, index, pageWidthPx, filter)
                    }
                }
            }
        }
    }

    if (showJump) {
        var text by remember { mutableStateOf("") }
        val total = pages?.pageCount ?: 1
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("رفتن به صفحه (۱ تا ${TextNormalizer.toPersianDigits(total)})") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.toIntOrNull()
                    if (n != null) scope.launch { listState.scrollToItem((n - 1).coerceIn(0, total - 1)) }
                    showJump = false
                }) { Text("برو") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("انصراف") } }
        )
    }

    if (showNote) {
        var text by remember(currentPage) { mutableStateOf(noteText ?: "") }
        AlertDialog(
            onDismissRequest = { showNote = false },
            title = { Text("یادداشت — صفحه ${TextNormalizer.toPersianDigits(currentPage)}") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(160.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.passageByPage(bookId, currentPage)?.let { repo.saveNote(it, text) } }
                    showNote = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showNote = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun PdfPageItem(pages: PdfPages, index: Int, widthPx: Int, filter: ColorFilter?) {
    val bitmap by produceState(pages.cached(index, widthPx), index, widthPx) {
        if (value == null) value = pages.render(index, widthPx)
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f / pages.aspect), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = "صفحه ${index + 1}", modifier = Modifier.fillMaxSize(), colorFilter = filter)
        } else {
            CircularProgressIndicator()
        }
    }
}
