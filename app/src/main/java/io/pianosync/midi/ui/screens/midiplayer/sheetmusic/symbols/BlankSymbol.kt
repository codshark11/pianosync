package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Blank symbol that doesn't draw anything.
 * Used for alignment purposes to align notes in different staffs
 * which occur at the same time.
 */
class BlankSymbol(
    private val startTime: Long,
    private var width: Float = 0f
) : MusicSymbol {
    
    override fun getStartTime(): Long = startTime
    
    override fun getMinWidth(): Float = 0f
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float = 0f
    
    override fun getBelowStaff(): Float = 0f
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        // Draw nothing - this is a blank symbol for alignment only
    }
    
    override fun toString(): String {
        return "BlankSymbol starttime=$startTime width=$width"
    }
}
