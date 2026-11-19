package com.noor.library.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.library.data.BookCatalog
import com.noor.library.data.Repository
import com.noor.library.data.Settings
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: Repository,
    settings: Settings,
    onBack: () -> Unit,
    onOpenReader: (String, String, Int, Int) -> Unit,
) {
    val vm: SearchViewModel = viewModel(factory = VMFactory { SearchViewModel(repo) })
    val query by vm.query.collectAsState()
    val filter by vm.bookFilter.collectAsState()
    val results by vm.results.collectAsState()
    val searched by vm.searched.collectAsState()

    LaunchedEffect(query, filter, settings.ignoreHarakat) {
        if (query.trim().length >= 2) {
            delay(300)
            vm.run(settings.ignoreHarakat)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("جستجو", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("کلمه یا عبارت (عربی یا فارسی)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.run(settings.ignoreHarakat) })
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ChipRow(
                    listOf("" to "همه کتاب‌ها") + BookCatalog.all.map { it.id to it.title },
                    filter
                ) { vm.bookFilter.value = it }
            }
            if (searched && results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("نتیجه‌ای یافت نشد.") }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (results.isNotEmpty()) item {
                    Text("${TextNormalizer.toPersianDigits(results.size)} نتیجه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { it.passage.id }) { r ->
                    val p = r.passage
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenReader(p.bookId, p.sectionType, p.sectionNumber, p.passageNumber) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                formatRef(p.bookId, p.sectionType, r.sectionTitle, p.sectionNumber, p.passageNumber),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                            )
                            Text(r.snippet, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
