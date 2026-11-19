package com.noor.library.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.library.audio.AudioState
import com.noor.library.audio.Reciters
import com.noor.library.util.TextNormalizer

@Composable
fun AudioBar(
    state: AudioState,
    reciterFolder: String,
    downloadDone: Int,
    downloadTotal: Int,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeed: (Float) -> Unit,
    onReciter: (String) -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
) {
    var reciterMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // RTL layout: "previous" points to the right
                    IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "آیه قبل") }
                    IconButton(onClick = onTogglePlay) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "توقف" else "پخش")
                    }
                    IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "آیه بعد") }
                    if (state.active && state.playingAyah > 0) {
                        Text("آیه ${TextNormalizer.toPersianDigits(state.playingAyah)}", fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "دانلود برای پخش آفلاین") }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "بستن") }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { reciterMenu = true }) { Text(Reciters.byFolder(reciterFolder).name, fontSize = 12.sp) }
                DropdownMenu(expanded = reciterMenu, onDismissRequest = { reciterMenu = false }) {
                    for (r in Reciters.all) {
                        DropdownMenuItem(text = { Text(r.name) }, onClick = { reciterMenu = false; onReciter(r.folder) })
                    }
                }
                for (sp in listOf(0.75f, 1f, 1.25f, 1.5f)) {
                    TextButton(onClick = { onSpeed(sp) }) {
                        Text(
                            "${sp}x", fontSize = 12.sp,
                            color = if (state.speed == sp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (downloadTotal > 0 && downloadDone < downloadTotal) {
                LinearProgressIndicator(progress = { downloadDone.toFloat() / downloadTotal }, Modifier.fillMaxWidth())
                Text(
                    "در حال دانلود ${TextNormalizer.toPersianDigits(downloadDone)} از ${TextNormalizer.toPersianDigits(downloadTotal)}",
                    fontSize = 11.sp
                )
            } else if (downloadTotal > 0) {
                Text("این سوره برای پخش آفلاین ذخیره شد.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            Text("منبع صوت: EveryAyah.com", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
