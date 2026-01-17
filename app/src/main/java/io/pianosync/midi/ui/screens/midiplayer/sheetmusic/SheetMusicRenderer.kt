package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Renders sheet music using Compose Canvas.
 * Draws staffs and all music symbols.
 * Supports both horizontal and vertical scrolling.
 */
@Composable
fun SheetMusicRenderer(
    modifier: Modifier = Modifier,
    staffs: List<Staff>,
    currentTimeMs: Long,
    color: Color = Color.Black,
    scrollVertically: Boolean = false
) {
    val density = LocalDensity.current
    
    // Calculate total dimensions
    val totalWidth = remember(staffs) {
        staffs.maxOfOrNull { it.getWidth() } ?: 0f
    }
    val totalHeight = remember(staffs) {
        staffs.sumOf { it.getHeight().toDouble() }.toFloat()
    }
    
    if (scrollVertically) {
        // Vertical scrolling: staffs stack vertically
        val verticalScrollState = rememberScrollState()
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(with(density) { totalWidth.toDp() })
                    .height(with(density) { totalHeight.toDp() })
            ) {
                var yOffset = 0f
                staffs.forEach { staff ->
                    staff.draw(this, color, 0f, yOffset, currentTimeMs)
                    yOffset += staff.getHeight()
                }
            }
        }
    } else {
        // Horizontal scrolling: staffs flow horizontally
        val horizontalScrollState = rememberScrollState()
        
        Row(
            modifier = modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(with(density) { totalWidth.toDp() })
                    .height(with(density) { totalHeight.toDp() })
            ) {
                var yOffset = 0f
                staffs.forEach { staff ->
                    staff.draw(this, color, 0f, yOffset, currentTimeMs)
                    yOffset += staff.getHeight()
                }
            }
        }
    }
}
