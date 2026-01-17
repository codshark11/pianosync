package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Base interface for all music symbols that can be displayed on a staff.
 * This includes notes, rests, clefs, time signatures, bars, etc.
 */
interface MusicSymbol {
    /**
     * Get the time (in milliseconds) this symbol occurs at.
     * Used to determine the measure this symbol belongs to.
     */
    fun getStartTime(): Long

    /**
     * Get the minimum width (in pixels) needed to draw this symbol
     */
    fun getMinWidth(): Float

    /**
     * Get/Set the width (in pixels) of this symbol.
     * The width is set during layout to align symbols vertically.
     */
    fun getWidth(): Float
    fun setWidth(value: Float)

    /**
     * Get the number of pixels this symbol extends above the staff.
     * Used to determine the minimum height needed for the staff.
     */
    fun getAboveStaff(): Float

    /**
     * Get the number of pixels this symbol extends below the staff.
     * Used to determine the minimum height needed for the staff.
     */
    fun getBelowStaff(): Float

    /**
     * Draw the symbol using DrawScope.
     * @param drawScope The DrawScope to draw with
     * @param color The color to use for drawing
     * @param ytop The y location (in pixels) where the top of the staff starts
     */
    fun draw(drawScope: DrawScope, color: Color, ytop: Float)
}
