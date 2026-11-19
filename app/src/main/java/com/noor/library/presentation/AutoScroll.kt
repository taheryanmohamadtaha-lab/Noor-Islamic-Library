package com.noor.library.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.library.util.TextNormalizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Teleprompter-style continuous scrolling. Moves the list upward (content flows up) at [speedDpPerSec].
 * Touching the screen interrupts it only while the finger is down; it resumes afterwards.
 * Calls [onFinished] when the end of the list is reached.
 */
@Composable
fun AutoScrollEffect(listState: LazyListState, running: Boolean, speedDpPerSec: Float, onFinished: () -> Unit) {
    val density = LocalDensity.current
    val speedNow by androidx.compose.runtime.rememberUpdatedState(speedDpPerSec)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (currentCoroutineContext().isActive) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val px = with(density) { speedNow.dp.toPx() } * dt
            try {
                if (px > 0f && !listState.canScrollForward) {
                    onFinished()
                    return@LaunchedEffect
                }
                listState.scrollBy(px)
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                delay(50) // interrupted by the user's finger; try again shortly
                last = androidx.compose.runtime.withFrameNanos { it }
            }
        }
    }
}

@Composable
fun AutoScrollBar(
    running: Boolean,
    speed: Float,
    onToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedCommit: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (running) "توقف پیمایش" else "شروع پیمایش")
                }
                Text("سرعت ${TextNormalizer.toPersianDigits(speed.toInt())}", fontSize = 13.sp)
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    onValueChangeFinished = onSpeedCommit,
                    valueRange = 8f..300f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "بستن") }
            }
        }
    }
}
