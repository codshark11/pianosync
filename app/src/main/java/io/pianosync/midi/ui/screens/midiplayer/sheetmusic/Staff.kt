package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import io.pianosync.midi.data.parser.midi.NoteDuration
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.BarSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.MusicSymbol

/**
 * Represents a single staff line in sheet music.
 * A staff contains music symbols (notes, rests, bars, clef, etc.) and draws them.
 * Matching MidiSheetMusic-Android Staff.
 */
class Staff(
    private val symbols: List<MusicSymbol>,
    private val clef: Clef,
    private val timeSignature: io.pianosync.midi.data.parser.midi.TimeSignature,
    private val showMeasureNumbers: Boolean = false
) {
    companion object {
        private const val ScaleFactor = 2.5f
        private const val LineWidth = 1f * ScaleFactor
        private const val LeftMargin = 4f * ScaleFactor
        private const val LineSpace = 7f * ScaleFactor
        private const val StaffHeight = LineSpace * 4 + LineWidth * 5
        private const val NoteHeight = LineSpace + LineWidth
        private const val NoteWidth = 3 * LineSpace / 2
    }
    
    private var yTop: Float = 0f
    private var width: Float = 0f
    private var height: Float = 0f
    
    init {
        calculateHeight()
        calculateWidth()
    }
    
    /**
     * Calculate the height of this staff based on symbols that extend above/below.
     * Matching MidiSheetMusic-Android: ytop = above + NoteHeight, height = NoteHeight*5 + ytop + below
     */
    private fun calculateHeight() {
        var above = 0f
        var below = 0f
        
        symbols.forEach { symbol ->
            above = maxOf(above, symbol.getAboveStaff())
            below = maxOf(below, symbol.getBelowStaff())
        }
        // Reserve space above staff for measure numbers (matching MidiSheetMusic-Android)
        if (showMeasureNumbers) {
            above = maxOf(above, NoteHeight * 3)
        }

        // yTop is the offset from the staff's origin to the top staff line (line 4)
        // Reference: ytop = above + SheetMusic.NoteHeight
        yTop = above + NoteHeight
        // Reference: height = SheetMusic.NoteHeight*5 + ytop + below
        height = NoteHeight * 5 + yTop + below
    }
    
    /**
     * Calculate the width of this staff based on symbol widths
     */
    private fun calculateWidth() {
        width = symbols.sumOf { it.getWidth().toDouble() }.toFloat() + LeftMargin * 2
    }
    
    fun getWidth(): Float = width
    fun getHeight(): Float = height
    fun getYTop(): Float = yTop
    
    /**
     * Draw the staff lines and all symbols
     */
    fun draw(drawScope: DrawScope, color: Color, xOffset: Float, yOffset: Float, currentTimeMs: Long = 0L) {
        // Calculate the Y position of the top staff line (line 4)
        // yTop is the offset from yOffset to account for symbols above staff
        // The actual top line is at: yOffset + yTop
        val staffYTop = yOffset + yTop
        
        // Draw the 5 horizontal staff lines (matching MidiSheetMusic-Android DrawHorizLines)
        // Start from staffYTop - LineWidth, increment by LineWidth + LineSpace for each line
        var y = staffYTop - LineWidth
        for (i in 1..5) {
            drawScope.drawLine(
                color = color,
                start = Offset(xOffset + LeftMargin, y),
                end = Offset(xOffset + width - 1, y),
                strokeWidth = LineWidth
            )
            y += LineWidth + LineSpace
        }
        
        // Draw vertical end lines at left and right edges (matching MidiSheetMusic-Android DrawEndLines)
        val yStart = staffYTop - LineWidth
        val yEnd = staffYTop + 4 * NoteHeight
        drawScope.drawLine(
            color = color,
            start = Offset(xOffset + LeftMargin, yStart),
            end = Offset(xOffset + LeftMargin, yEnd),
            strokeWidth = LineWidth
        )
        drawScope.drawLine(
            color = color,
            start = Offset(xOffset + width - 1, yStart),
            end = Offset(xOffset + width - 1, yEnd),
            strokeWidth = LineWidth
        )
        
        // Precompute beamed groups to know which chords get beams (and thus skip curvy stem)
        var runX = xOffset + LeftMargin
        val beamedGroups = mutableListOf<MutableList<Pair<io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol, Float>>>()
        var runBeamed: MutableList<Pair<io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol, Float>>? = null
        symbols.forEach { s ->
            when {
                s is io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol && s.shouldBeBeamed() -> {
                    if (runBeamed == null) { runBeamed = mutableListOf(); beamedGroups.add(runBeamed!!) }
                    runBeamed!!.add(Pair(s, runX))
                }
                else -> runBeamed = null
            }
            runX += s.getWidth()
        }
        val beamedChords = beamedGroups.filter { it.size >= 2 }.flatMap { it.map { p -> p.first } }.toSet()

        // Draw symbols
        var currentX = xOffset + LeftMargin
        symbols.forEach { symbol ->
            val isHighlighted = symbol is io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol &&
                currentTimeMs > 0 && symbol.getStartTime() <= currentTimeMs && currentTimeMs <= symbol.endTime
            val isBeamed = symbol is io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol && symbol in beamedChords

            drawScope.withTransform({ translate(left = currentX, top = yOffset) }) {
                when (symbol) {
                    is io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol ->
                        symbol.draw(this, color, yTop, isHighlighted, isBeamed)
                    else -> symbol.draw(this, color, yTop)
                }
            }
            currentX += symbol.getWidth()
        }

        // Draw measure numbers above the staff (matching MidiSheetMusic-Android DrawMeasureNumbers)
        if (showMeasureNumbers) {
            val measureLengthMs = (timeSignature.measure.toLong() * timeSignature.tempo) / (1000L * timeSignature.quarter)
            if (measureLengthMs > 0) {
                val paint = Paint().apply {
                    setColor(android.graphics.Color.BLACK)
                    textSize = NoteHeight * 1.2f
                    isAntiAlias = true
                }
                var x = xOffset + LeftMargin
                val y = yOffset + yTop - NoteHeight * 3
                symbols.forEach { s ->
                    if (s is BarSymbol) {
                        val measure = 1 + s.getStartTime() / measureLengthMs
                        drawScope.drawContext.canvas.nativeCanvas.drawText(
                            "$measure",
                            x + NoteWidth / 2,
                            y,
                            paint
                        )
                    }
                    x += s.getWidth()
                }
            }
        }

        // Draw beams for beamed groups (matching MidiSheetMusic-Android Stem.DrawHorizBarStem)
        beamedGroups.forEach { group ->
            if (group.size < 2) return@forEach // Need at least 2 notes to beam
            
            val firstChord = group.first().first
            val lastChord = group.last().first
            
            // Determine stem direction - all chords in a beam must have the same direction
            val stemUp = firstChord.getStemDirection()
            
            // Get stem X positions (absolute coordinates)
            // Stem X is relative to symbol's left edge, so add the symbol's X position
            val leftStemX = group.first().second + firstChord.getStemX()
            val rightStemX = group.last().second + lastChord.getStemX()
            
            // Beam Y in canvas: yOffset + stem end in staff coords
            val leftStemEndY = yOffset + firstChord.getStemEndY(yTop, stemUp)
            val rightStemEndY = yOffset + lastChord.getStemEndY(yTop, stemUp)
            
            // Get note duration to determine number of beams
            val duration = firstChord.noteDuration
            
            // Draw beams (matching MidiSheetMusic-Android: stroke width = NoteHeight/2)
            val beamStrokeWidth = NoteHeight / 2
            
            // First beam (for eighth, sixteenth, thirty-second, triplet, dotted eighth)
            if (duration in listOf(
                NoteDuration.Eighth,
                NoteDuration.DottedEighth,
                NoteDuration.Triplet,
                NoteDuration.Sixteenth,
                NoteDuration.ThirtySecond
            )) {
                drawScope.drawLine(
                    color = color,
                    start = Offset(leftStemX, leftStemEndY),
                    end = Offset(rightStemX, rightStemEndY),
                    strokeWidth = beamStrokeWidth
                )
            }
            
            // Second beam (for sixteenth, thirty-second)
            if (duration in listOf(NoteDuration.Sixteenth, NoteDuration.ThirtySecond)) {
                val yOffset = if (stemUp) NoteHeight else -NoteHeight
                drawScope.drawLine(
                    color = color,
                    start = Offset(leftStemX, leftStemEndY + yOffset),
                    end = Offset(rightStemX, rightStemEndY + yOffset),
                    strokeWidth = beamStrokeWidth
                )
            }
            
            // Third beam (for thirty-second)
            if (duration == NoteDuration.ThirtySecond) {
                val yOffset = if (stemUp) NoteHeight * 2 else -NoteHeight * 2
                drawScope.drawLine(
                    color = color,
                    start = Offset(leftStemX, leftStemEndY + yOffset),
                    end = Offset(rightStemX, rightStemEndY + yOffset),
                    strokeWidth = beamStrokeWidth
                )
            }
            
            // Handle dotted eighth to sixteenth beam (special case)
            if (duration == NoteDuration.DottedEighth && group.size >= 2) {
                val secondChord = group[1].first
                if (secondChord.noteDuration == NoteDuration.Sixteenth) {
                    val secondStemX = group[1].second + secondChord.getStemX()
                    val secondStemEndY = yOffset + secondChord.getStemEndY(yTop, stemUp)
                    val x = secondStemX - NoteHeight
                    val slope = if ((rightStemX - leftStemX) != 0f) {
                        (rightStemEndY - leftStemEndY) / (rightStemX - leftStemX)
                    } else {
                        0f
                    }
                    val y = slope * (x - rightStemX) + rightStemEndY
                    
                    drawScope.drawLine(
                        color = color,
                        start = Offset(x, y),
                        end = Offset(rightStemX, rightStemEndY),
                        strokeWidth = beamStrokeWidth
                    )
                }
            }
        }
    }
    
    /**
     * Get the start time of the first symbol
     */
    fun getStartTime(): Long {
        return symbols.firstOrNull()?.getStartTime() ?: 0L
    }
    
    /**
     * Get the end time of the last symbol
     */
    fun getEndTime(): Long {
        return symbols.lastOrNull()?.let { 
            if (it is io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol) {
                it.endTime
            } else {
                it.getStartTime()
            }
        } ?: 0L
    }
}
