package com.noor.library.presentation

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.library.ReminderWorker
import com.noor.library.data.Settings
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appVm: AppViewModel, settings: Settings, onBack: () -> Unit) {
    val repo = appVm.repo
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by repo.books.collectAsState(initial = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = repo.exportUserData()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "پشتیبان ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val n = repo.importUserData(text)
            Toast.makeText(context, if (n < 0) "فایل پشتیبان معتبر نیست" else "${TextNormalizer.toPersianDigits(n)} مورد بازیابی شد", Toast.LENGTH_LONG).show()
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "بدون اجازه اعلان، یادآور نمایش داده نمی‌شود", Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReadingSettingsPanel(settings) { appVm.update(it) }
            HorizontalDivider()

            Text("جستجو", fontWeight = FontWeight.Bold)
            SwitchRow("نادیده‌گرفتن حرکات در جستجوی عربی", settings.ignoreHarakat) { v -> appVm.update { it.setIgnoreHarakat(v) } }
            Text("جستجوی فارسی همیشه بدون حساسیت به ی/ي و ک/ك انجام می‌شود.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            Text("هدف مطالعه روزانه", fontWeight = FontWeight.Bold)
            PrefSlider("تعداد آیه/فراز در روز", settings.dailyGoal.toFloat(), 5f..100f, TextNormalizer.toPersianDigits(settings.dailyGoal)) { v ->
                appVm.update { it.setDailyGoal(v.toInt()) }
            }
            SwitchRow("یادآور روزانه (ساعت ${TextNormalizer.toPersianDigits(settings.reminderHour)})", settings.reminderEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                appVm.update { it.setReminder(on, settings.reminderHour) }
                if (on) ReminderWorker.schedule(context, settings.reminderHour) else ReminderWorker.cancel(context)
            }
            PrefSlider("ساعت یادآور", settings.reminderHour.toFloat(), 5f..23f, TextNormalizer.toPersianDigits(settings.reminderHour)) { v ->
                appVm.update { it.setReminder(settings.reminderEnabled, v.toInt()) }
                if (settings.reminderEnabled) ReminderWorker.schedule(context, v.toInt())
            }
            HorizontalDivider()

            Text("پشتیبان‌گیری (نشانک‌ها و یادداشت‌ها)", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportLauncher.launch("noor-backup.json") }) { Text("ذخیره پشتیبان") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("بازیابی") }
            }
            HorizontalDivider()

            Text("منابع و صحت متون", fontWeight = FontWeight.Bold)
            for (b in books) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.title, fontWeight = FontWeight.Bold)
                            StatusChip(b.status)
                        }
                        if (b.status == "missing") {
                            Text("متن معتبر هنوز افزوده نشده است؛ هیچ متن جایگزین یا ساختگی نمایش داده نمی‌شود.", fontSize = 12.sp)
                        } else {
                            Text("منبع: ${b.source}", fontSize = 12.sp)
                            if (b.translator.isNotBlank()) Text("مترجم: ${b.translator}", fontSize = 12.sp)
                            Text("نسخه: ${b.version}", fontSize = 12.sp)
                            Text("مجوز: ${b.license}", fontSize = 12.sp)
                            Text(
                                "${TextNormalizer.toPersianDigits(b.sectionCount)} بخش · ${TextNormalizer.toPersianDigits(b.passageCount)} آیه/فراز",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                "سازنده: محمدطاها طاهریان",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
