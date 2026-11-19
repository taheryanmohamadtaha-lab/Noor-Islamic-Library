package com.noor.library.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.library.data.BookCatalog
import com.noor.library.data.Prefs
import com.noor.library.data.Settings
import com.noor.library.presentation.theme.Gold
import com.noor.library.util.TextNormalizer

fun sectionDisplayTitle(bookId: String, type: String, number: Int, title: String): String {
    val label = BookCatalog.sectionTypeLabel(type)
    return when {
        title.isBlank() -> "$label ${TextNormalizer.toPersianDigits(number)}"
        bookId == "quran" -> "$label $title"
        else -> title
    }
}

fun formatRef(bookId: String, type: String, sectionTitle: String, number: Int, passage: Int): String {
    val info = BookCatalog.byId(bookId)
    return "${info.title} · ${sectionDisplayTitle(bookId, type, number, sectionTitle)} · ${info.passageNoun} ${TextNormalizer.toPersianDigits(passage)}"
}

@Composable
fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            TextNormalizer.toPersianDigits(number),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "complete" -> "کامل" to Color(0xFF3E9B6E)
        "partial" -> "ناقص" to Gold
        else -> "محتوا افزوده نشده" to Color(0xFFC0583F)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun PrefSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(display, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, label) in options) {
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label, fontSize = 13.sp) })
        }
    }
}

/** Reading options shared by the reader sheet and the settings screen. */
@Composable
fun ReadingSettingsPanel(s: Settings, update: (suspend (Prefs) -> Unit) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("حالت نمایش", fontWeight = FontWeight.Bold)
        ChipRow(
            listOf("dark" to "شب", "light" to "روز", "cream" to "کرم"),
            s.theme
        ) { v -> update { it.setTheme(v) } }

        PrefSlider("اندازه متن عربی", s.arabicSize, 18f..48f, "${s.arabicSize.toInt()}") { v -> update { it.setArabicSize(v) } }
        PrefSlider("اندازه ترجمه", s.persianSize, 12f..28f, "${s.persianSize.toInt()}") { v -> update { it.setPersianSize(v) } }
        PrefSlider("فاصله خطوط", s.lineSpacing, 1.2f..2.6f, String.format("%.1f", s.lineSpacing)) { v -> update { it.setLineSpacing(v) } }
        SwitchRow("نمایش ترجمه فارسی", s.showTranslation) { v -> update { it.setShowTranslation(v) } }

        Text("فونت عربی", fontSize = 14.sp)
        ChipRow(
            listOf("amiri" to "امیری قرآن", "naskh" to "نسخ", "system" to "سیستم"),
            s.arabicFont
        ) { v -> update { it.setArabicFont(v) } }
        Text("فونت فارسی", fontSize = 14.sp)
        ChipRow(listOf("vazir" to "وزیرمتن", "system" to "سیستم"), s.persianFont) { v -> update { it.setPersianFont(v) } }
    }
}

@Composable
fun SoftBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
