package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The BarSymbol represents the vertical bars which delimit measures.
 * The starttime of the symbol is the beginning of the new measure.
 */
class BarSymbol(
    private val startTime: Long
) : MusicSymbol {
    
    companion object {
        private const val ScaleFactor = 2.5f
        private const val LineSpace = 7f * ScaleFactor
        private const val LineWidth = 1f * ScaleFactor
        private const val NoteWidth = 3 * LineSpace / 2
    }
    
    private var width: Float = getMinWidth()
    
    override fun getStartTime(): Long = startTime
    
    override fun getMinWidth(): Float = LineSpace / 2f  // Minimal width for barline
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float = 0f
    
    override fun getBelowStaff(): Float = 0f
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        // Matching MidiSheetMusic-Android BarSymbol: draw at NoteWidth/2, yEnd = y + LineSpace*4 + LineWidth*4
        val y = ytop
        val yEnd = y + LineSpace * 4 + LineWidth * 4
        val x = NoteWidth / 2
        
        drawScope.drawLine(
            color = color,
            start = Offset(x, y),
            end = Offset(x, yEnd),
            strokeWidth = 1f
        )
    }
    
    override fun toString(): String {
        return "BarSymbol starttime=$startTime width=$width"
    }
}
