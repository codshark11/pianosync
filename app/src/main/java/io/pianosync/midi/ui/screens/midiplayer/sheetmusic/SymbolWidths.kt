package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.BarSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.MusicSymbol

/**
 * SymbolWidths is used to vertically align notes in different staffs
 * that occur at the same time (that have the same startTime).
 * 
 * This is done by:
 * - Storing a list of all the start times across all staffs
 * - Storing the width of symbols for each start time, for each staff
 * - Storing the maximum width for each start time, across all staffs
 * - Getting the extra width needed for each staff to match the maximum
 *   width for that start time.
 */
class SymbolWidths(
    private val staffs: List<List<MusicSymbol>>
) {
    /** Map of startTime -> symbol width, one per staff */
    private val widths: List<Map<Long, Float>>
    
    /** Map of startTime -> maximum symbol width across all staffs */
    private val maxWidths: Map<Long, Float>
    
    /** Array of all start times across all staffs, sorted */
    private val startTimes: List<Long>
    
    init {
        // Get the symbol widths for all staffs
        widths = staffs.map { getStaffWidths(it) }
        
        // Calculate the maximum symbol widths
        val maxWidthsMap = mutableMapOf<Long, Float>()
        widths.forEach { staffWidths ->
            staffWidths.forEach { (time, width) ->
                val currentMax = maxWidthsMap[time] ?: 0f
                if (width > currentMax) {
                    maxWidthsMap[time] = width
                }
            }
        }
        maxWidths = maxWidthsMap
        
        // Store all start times, sorted
        startTimes = maxWidths.keys.sorted()
    }
    
    /**
     * Create a map of startTime -> symbol width for a staff.
     * BarSymbols and header symbols (startTime < 0) are excluded from width calculations.
     */
    private fun getStaffWidths(symbols: List<MusicSymbol>): Map<Long, Float> {
        val widthsMap = mutableMapOf<Long, Float>()
        
        symbols.forEach { symbol ->
            if (symbol is BarSymbol) {
                // BarSymbols are not included in width calculations
                return@forEach
            }
            
            val start = symbol.getStartTime()
            if (start < 0) {
                // Header symbols (ClefSymbol, TimeSigSymbol with startTime -1) are not included
                return@forEach
            }
            
            val width = symbol.getMinWidth()
            
            if (widthsMap.containsKey(start)) {
                // If multiple symbols at same start time, sum their widths
                widthsMap[start] = widthsMap[start]!! + width
            } else {
                widthsMap[start] = width
            }
        }
        
        return widthsMap
    }
    
    /**
     * Given a staff index and a start time, return the extra width needed
     * so that the symbols for that start time align with the other staffs.
     */
    fun getExtraWidth(staffIndex: Int, startTime: Long): Float {
        val staffWidths = widths[staffIndex]
        val maxWidth = maxWidths[startTime] ?: 0f
        
        if (!staffWidths.containsKey(startTime)) {
            // Staff doesn't have a symbol at this time, needs full width
            return maxWidth
        } else {
            // Staff has symbol, return difference to match max width
            return maxWidth - staffWidths[startTime]!!
        }
    }
    
    /**
     * Return a list of all start times across all staffs, sorted.
     */
    fun getStartTimes(): List<Long> = startTimes
}
