package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.pianosync.midi.data.parser.midi.NoteDuration

/**
 * A Rest symbol represents a rest - whole, half, quarter, or eighth.
 * The Rest symbol has a starttime and a duration, just like a regular note.
 */
class RestSymbol(
    private val startTime: Long,
    private val duration: NoteDuration
) : MusicSymbol {
    
    companion object {
        private const val ScaleFactor = 2.5f
        private const val LineSpace = 7f * ScaleFactor
        private const val LineWidth = 1f * ScaleFactor
        private const val NoteHeight = LineSpace + LineWidth
        private const val NoteWidth = 3 * LineSpace / 2
    }
    
    private var width: Float = getMinWidth()
    
    override fun getStartTime(): Long = startTime
    
    override fun getMinWidth(): Float {
        return 2 * NoteHeight + NoteHeight / 2
    }
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float = 0f
    
    override fun getBelowStaff(): Float = 0f
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        // Align the rest symbol to the right (matching MidiSheetMusic-Android)
        drawScope.withTransform({
            translate(getWidth() - getMinWidth(), 0f)
            translate(NoteHeight / 2, 0f)
        }) {
            when (duration) {
                NoteDuration.Whole -> drawWhole(drawScope, color, ytop)
                NoteDuration.Half -> drawHalf(drawScope, color, ytop)
                NoteDuration.Quarter -> drawQuarter(drawScope, color, ytop)
                NoteDuration.Eighth -> drawEighth(drawScope, color, ytop)
                else -> {
                    // For other durations, draw as quarter rest
                    drawQuarter(drawScope, color, ytop)
                }
            }
        }
    }
    
    /**
     * Draw a whole rest symbol, a rectangle below a staff line.
     * Matching MidiSheetMusic-Android RestSymbol.DrawWhole
     */
    private fun drawWhole(drawScope: DrawScope, color: Color, ytop: Float) {
        val y = ytop + NoteHeight
        drawScope.drawRect(
            color = color,
            topLeft = Offset(0f, y),
            size = Size(NoteWidth, NoteHeight / 2)
        )
    }
    
    /**
     * Draw a half rest symbol, a rectangle above a staff line.
     * Matching MidiSheetMusic-Android RestSymbol.DrawHalf
     */
    private fun drawHalf(drawScope: DrawScope, color: Color, ytop: Float) {
        val y = ytop + NoteHeight + NoteHeight / 2
        drawScope.drawRect(
            color = color,
            topLeft = Offset(0f, y),
            size = Size(NoteWidth, NoteHeight / 2)
        )
    }
    
    /**
     * Draw a quarter rest symbol.
     * Matching MidiSheetMusic-Android RestSymbol.DrawQuarter
     */
    private fun drawQuarter(drawScope: DrawScope, color: Color, ytop: Float) {
        var y = ytop + NoteHeight / 2
        val x = 2f
        val xend = x + 2 * NoteHeight / 3
        
        // Line 1: thin line
        drawScope.drawLine(
            color = color,
            start = Offset(x, y),
            end = Offset(xend - 1, y + NoteHeight - 1),
            strokeWidth = 1f
        )
        
        // Line 2: thick line (LineSpace/2 width)
        y = ytop + NoteHeight + 1
        drawScope.drawLine(
            color = color,
            start = Offset(xend - 2, y),
            end = Offset(x, y + NoteHeight),
            strokeWidth = LineSpace / 2
        )
        
        // Line 3: thin line
        y = ytop + NoteHeight * 2 - 1
        drawScope.drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(xend + 2, y + NoteHeight),
            strokeWidth = 1f
        )
        
        // Line 4: thick line (LineSpace/2 width) - special handling for NoteHeight
        val yOffset = if (NoteHeight == 6f) {
            1 + 3 * NoteHeight / 4
        } else {
            3 * NoteHeight / 4
        }
        drawScope.drawLine(
            color = color,
            start = Offset(xend, y + yOffset),
            end = Offset(x / 2, y + yOffset),
            strokeWidth = LineSpace / 2
        )
        
        // Line 5: thin line
        drawScope.drawLine(
            color = color,
            start = Offset(0f, y + 2 * NoteHeight / 3 + 1),
            end = Offset(xend - 1, y + 3 * NoteHeight / 2),
            strokeWidth = 1f
        )
    }
    
    /**
     * Draw an eighth rest symbol.
     * Matching MidiSheetMusic-Android RestSymbol.DrawEighth
     */
    private fun drawEighth(drawScope: DrawScope, color: Color, ytop: Float) {
        val y = ytop + NoteHeight - 1
        
        // Draw filled oval
        val rect = Rect(0f, y + 1, LineSpace - 1, y + 1 + LineSpace - 1)
        drawScope.drawOval(
            color = color,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height)
        )
        
        // Draw first line of tail
        drawScope.drawLine(
            color = color,
            start = Offset((LineSpace - 2) / 2, y + LineSpace - 1),
            end = Offset(3 * LineSpace / 2, y + LineSpace / 2),
            strokeWidth = 1f
        )
        
        // Draw second line of tail
        drawScope.drawLine(
            color = color,
            start = Offset(3 * LineSpace / 2, y + LineSpace / 2),
            end = Offset(3 * LineSpace / 4, y + NoteHeight * 2),
            strokeWidth = 1f
        )
    }
    
    override fun toString(): String {
        return "RestSymbol starttime=$startTime duration=$duration width=$width"
    }
}
