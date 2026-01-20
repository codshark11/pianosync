package io.pianosync.midi.ui.screens.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min

@Composable
fun LoopControl(
    modifier: Modifier = Modifier,
    isLoopEnabled: Boolean,
    loopStartMs: Long,
    loopEndMs: Long,
    songDurationMs: Long,
    currentTimeMs: Long,
    onLoopToggled: (Boolean) -> Unit,
    onSetLoopStart: () -> Unit,
    onSetLoopEnd: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    // Ensure we have a valid song duration
    val safeSongDuration = max(1L, songDurationMs)

    // Calculate the percentages
    val progress = min(1f, max(0f, currentTimeMs.toFloat() / safeSongDuration))
    val loopStartPct = min(1f, max(0f, loopStartMs.toFloat() / safeSongDuration))
    val loopEndPct = min(1f, max(0f, loopEndMs.toFloat() / safeSongDuration))

    // Synthesia-style progress bar (single layer) with time display inside
    var barWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp) // Thicker to accommodate text inside
            .onSizeChanged { size ->
                barWidth = size.width
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val seekPosition = (offset.x / size.width * safeSongDuration).toLong()
                    onSeekTo(seekPosition)
                }
            }
    ) {
        // Dark grey background for the progress bar track - with opacity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2E7D32)) // Dark grey background
        )
        // Green progress bar - with opacity
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(Color(0xFF4CAF50)) // Green like Synthesia
        )

        // Time display inside progress bar
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current time
            Text(
                text = formatTime(currentTimeMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.zIndex(10f)
            )

            // Total duration
            Text(
                text = formatTime(songDurationMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.zIndex(10f)
            )
        }

        // Orange triangular markers for loop points - only show when loop is enabled
        if (barWidth > 0 && isLoopEnabled) {
            // Loop start marker (A)
            if (loopStartMs > 0) {
                val loopStartOffsetPx = barWidth * loopStartPct - with(density) { 6.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { loopStartOffsetPx.toDp() })
                        .align(Alignment.CenterStart)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(12.dp, 14.dp)
                            .align(Alignment.Center)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFFFF9800) // Orange
                        )
                    }
                }
            }

            // Loop end marker (B)
            if (loopEndMs > 0 && loopEndMs < songDurationMs) {
                val loopEndOffsetPx = barWidth * loopEndPct - with(density) { 6.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { loopEndOffsetPx.toDp() })
                        .align(Alignment.CenterStart)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(12.dp, 14.dp)
                            .align(Alignment.Center)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFFFF9800) // Orange
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format milliseconds into mm:ss format
fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}