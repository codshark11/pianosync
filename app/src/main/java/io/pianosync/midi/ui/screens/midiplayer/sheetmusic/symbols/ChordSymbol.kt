package io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.pianosync.midi.ui.screens.player.MidiNote
import io.pianosync.midi.data.parser.midi.NoteDuration
import io.pianosync.midi.data.parser.midi.TimeSignature
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.AccidentalTracker
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.Clef
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.SheetMusicConverter
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.WhiteNote

/**
 * A ChordSymbol represents a group of notes that are played at the same time.
 * This is a simplified version that draws notes as circles on the staff.
 * Matching MidiSheetMusic-Android ChordSymbol.
 */
class ChordSymbol(
    private val notes: List<MidiNote>,
    private val clef: Clef,
    private val timeSignature: TimeSignature,
    private val accidentalTracker: AccidentalTracker? = null,
    private val measureTime: Long = 0L
) : MusicSymbol {
    
    companion object {
        private const val ScaleFactor = 2.5f
        private const val LineSpace = 7f * ScaleFactor
        private const val LineWidth = 1f * ScaleFactor
        private const val NoteHeight = LineSpace + LineWidth
        private const val NoteWidth = 3f * LineSpace / 2f
    }
    
    private val startTime: Long = notes.firstOrNull()?.startTime ?: 0L
    /** End time in ms (max of note start+duration in this chord). Used for rest gap calculation. */
    val endTime: Long = notes.maxOfOrNull { note -> note.startTime + note.duration } ?: startTime
    private var width: Float = 0f  // set in init to getMinWidth()

    /** Accidentals for notes that need them.
     * Matching MidiSheetMusic-Android: uses AccidentalTracker to show accidentals only once per measure.
     */
    private val accidSymbols: List<AccidSymbol> = run {
        notes.mapNotNull { note ->
            val wn = WhiteNote.fromMidiNote(note.note)
            // Use accidental tracker if available, otherwise fall back to simple black key check
            val accid = if (accidentalTracker != null) {
                accidentalTracker.getAccidental(note.note, measureTime)
            } else {
                // Fallback: simple black key detection
                val notescale = (note.note + 3) % 12
                if (notescale in listOf(1, 4, 6, 9, 11)) Accid.Sharp else Accid.None
            }
            if (accid != Accid.None) AccidSymbol(accid, wn, clef) else null
        }
    }

    init { width = getMinWidth() }

    // Calculate note duration for beaming
    val noteDuration: NoteDuration by lazy {
        if (notes.isEmpty()) NoteDuration.Quarter
        else {
            // Convert duration in ms to NoteDuration using time signature
            val avgDuration = notes.map { it.duration }.average().toLong()
            // Convert ms to pulses: need BPM to calculate properly
            // Approximate: assume 120 BPM = 500ms per quarter note
            // quarterNoteMs = 60000 / BPM, but we don't have BPM here
            // Use time signature's quarter note duration in pulses
            // For now, use a simple heuristic based on duration ranges
            when {
                avgDuration >= 2000 -> NoteDuration.Whole
                avgDuration >= 1500 -> NoteDuration.Half
                avgDuration >= 750 -> NoteDuration.Quarter
                avgDuration >= 375 -> NoteDuration.Eighth
                avgDuration >= 187 -> NoteDuration.Sixteenth
                else -> NoteDuration.ThirtySecond
            }
        }
    }
    
    /**
     * Check if this chord should be beamed (eighth notes, sixteenth notes, etc.)
     */
    fun shouldBeBeamed(): Boolean {
        return noteDuration in listOf(
            NoteDuration.Eighth,
            NoteDuration.DottedEighth,
            NoteDuration.Sixteenth,
            NoteDuration.ThirtySecond,
            NoteDuration.Triplet
        )
    }
    
    override fun getStartTime(): Long = startTime
    
    override fun getMinWidth(): Float {
        // Matching MidiSheetMusic-Android: note circles + accidental symbols
        var result = 2 * NoteHeight + NoteHeight * 3 / 4
        if (accidSymbols.isNotEmpty()) {
            result += accidSymbols[0].getMinWidth()
            for (i in 1 until accidSymbols.size) {
                if (accidSymbols[i].getNote().dist(accidSymbols[i - 1].getNote()) < 6) {
                    result += accidSymbols[i].getMinWidth()
                }
            }
        }
        return result
    }
    
    override fun getWidth(): Float = width
    
    override fun setWidth(value: Float) {
        width = value
    }
    
    override fun getAboveStaff(): Float {
        var result = 0f
        val maxLine = notes.maxOfOrNull { note: MidiNote ->
            SheetMusicConverter.midiNoteToStaffLine(note.note, clef)
        } ?: 0f
        if (maxLine > 4) result = (maxLine - 4) * LineSpace
        accidSymbols.forEach { result = maxOf(result, it.getAboveStaff()) }
        return result
    }

    override fun getBelowStaff(): Float {
        var result = 0f
        val minLine = notes.minOfOrNull { note: MidiNote ->
            SheetMusicConverter.midiNoteToStaffLine(note.note, clef)
        } ?: 0f
        if (minLine < 0) result = (-minLine) * LineSpace
        accidSymbols.forEach { result = maxOf(result, it.getBelowStaff()) }
        return result
    }
    
    override fun draw(drawScope: DrawScope, color: Color, ytop: Float) {
        draw(drawScope, color, ytop, isHighlighted = false, isBeamed = false)
    }

    fun draw(drawScope: DrawScope, color: Color, ytop: Float, isHighlighted: Boolean, isBeamed: Boolean = false) {
        // Align the chord to the right (matching MidiSheetMusic-Android: canvas.translate(getWidth() - getMinWidth(), 0))
        drawScope.withTransform({
            translate(getWidth() - getMinWidth(), 0f)
        }) {
            drawChordContent(color, ytop, isHighlighted, isBeamed)
        }
    }
    
    /** Total x width used by accidentals (for placing notes and getStemX). */
    private fun getAccidWidth(): Float {
        var xpos = 0f
        var prev: AccidSymbol? = null
        for (symbol in accidSymbols) {
            if (prev != null && symbol.getNote().dist(prev.getNote()) < 6) {
                xpos += symbol.getWidth()
            }
            prev = symbol
        }
        if (prev != null) xpos += prev.getWidth()
        return xpos
    }

    private fun DrawScope.drawChordContent(color: Color, ytop: Float, isHighlighted: Boolean, isBeamed: Boolean) {
        val noteColor = color

        // Draw accidentals first (matching MidiSheetMusic-Android DrawAccid)
        var accidWidth = 0f
        var prevAccid: AccidSymbol? = null
        for (symbol in accidSymbols) {
            if (prevAccid != null && symbol.getNote().dist(prevAccid.getNote()) < 6) {
                accidWidth += symbol.getWidth()
            }
            withTransform({ translate(accidWidth, 0f) }) {
                symbol.draw(this, noteColor, ytop)
            }
            prevAccid = symbol
        }
        if (prevAccid != null) accidWidth += prevAccid.getWidth()

        // Draw notes and stem to the right of accidentals
        withTransform({ translate(accidWidth, 0f) }) {
            drawNotesAndStem(noteColor, ytop, isBeamed)
        }
    }

    private fun DrawScope.drawNotesAndStem(noteColor: Color, ytop: Float, isBeamed: Boolean) {
        val topStaff = when (clef) {
            Clef.Treble -> WhiteNote.TopTreble
            Clef.Bass -> WhiteNote.TopBass
        }

        data class NotePosition(val whiteNote: WhiteNote, val leftside: Boolean, val y: Float, val x: Float)

        val whiteNotes = notes.map { WhiteNote.fromMidiNote(it.note) }
        val leftsides = BooleanArray(notes.size) { true }
        for (i in 1 until notes.size) {
            val d = whiteNotes[i].dist(whiteNotes[i - 1])
            if (d == 1 || d == -1) leftsides[i] = !leftsides[i - 1]
        }

        val notePositions = notes.mapIndexed { i, n ->
            val wn = whiteNotes[i]
            val ynote = ytop + topStaff.dist(wn) * NoteHeight / 2
            val baseX = LineSpace / 4f
            val xnote = if (leftsides[i]) baseX else baseX + NoteWidth
            NotePosition(wn, leftsides[i], ynote, xnote + NoteWidth / 2 + 1)
        }

        val middleLine = when (clef) {
            Clef.Treble -> WhiteNote(WhiteNote.B, 5)
            Clef.Bass -> WhiteNote(WhiteNote.D, 3)
        }
        val bottomNote = notePositions.minByOrNull { middleLine.dist(it.whiteNote) }?.whiteNote
        val topNote = notePositions.maxByOrNull { middleLine.dist(it.whiteNote) }?.whiteNote
        val stemUp = if (bottomNote != null && topNote != null) {
            middleLine.dist(bottomNote) + middleLine.dist(topNote) >= 0
        } else true

        val notesOverlap = whiteNotes.zipWithNext().any { (a, b) -> val d = a.dist(b); d == 1 || d == -1 }
        val rightSide = stemUp || notesOverlap
        val xstart = if (rightSide) LineSpace / 4f + NoteWidth else LineSpace / 4f + 1

        // Draw note heads (matching MidiSheetMusic-Android DrawNotes)
        notePositions.forEach { pos ->
            val centerY = pos.y - LineWidth + NoteHeight / 2
            val path = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        offset = Offset(-NoteWidth / 2, -NoteHeight / 2),
                        size = androidx.compose.ui.geometry.Size(NoteWidth, NoteHeight - 1)
                    )
                )
            }
            withTransform({ translate(pos.x, centerY); rotate(-45f, Offset(0f, 0f)) }) {
                drawPath(path, noteColor, style = androidx.compose.ui.graphics.drawscope.Fill)
            }
        }

        // Draw horizontal lines (ledger lines) for notes outside the staff
        // Matching MidiSheetMusic-Android DrawNotes lines 594-619
        notePositions.forEachIndexed { index, pos ->
            // Calculate xnote (left edge of note) - matching reference: LineSpace/4 or LineSpace/4 + NoteWidth
            val baseX = LineSpace / 4f
            val xnote = if (leftsides[index]) baseX else baseX + NoteWidth
            
            // For notes above the staff
            val top = topStaff.add(1)  // One note above the top staff line
            val distAbove = pos.whiteNote.dist(top)
            var y = ytop - LineWidth
            
            if (distAbove >= 2) {
                for (i in 2..distAbove step 2) {
                    y -= NoteHeight
                    drawLine(
                        color = noteColor,
                        start = Offset(xnote - LineSpace / 4, y),
                        end = Offset(xnote + NoteWidth + LineSpace / 4, y),
                        strokeWidth = LineWidth
                    )
                }
            }
            
            // For notes below the staff
            val bottom = top.add(-8)  // Bottom of the staff (8 white notes below top)
            val distBelow = bottom.dist(pos.whiteNote)
            y = ytop + (LineSpace + LineWidth) * 4 - 1
            
            if (distBelow >= 2) {
                for (i in 2..distBelow step 2) {
                    y += NoteHeight
                    drawLine(
                        color = noteColor,
                        start = Offset(xnote - LineSpace / 4, y),
                        end = Offset(xnote + NoteWidth + LineSpace / 4, y),
                        strokeWidth = LineWidth
                    )
                }
            }
        }

        // Draw stem (matching MidiSheetMusic-Android Stem.Draw)
        if (noteDuration == NoteDuration.Whole || notePositions.isEmpty()) return

        val endNote = calculateStemEndWhiteNote(stemUp) ?: return

        // DrawVerticalLine
        val ystem = ytop + topStaff.dist(endNote) * NoteHeight / 2
        val y1: Float
        val ystemDraw: Float
        if (stemUp) {
            val bottom = notePositions.maxByOrNull { it.y }!!.whiteNote
            y1 = ytop + topStaff.dist(bottom) * NoteHeight / 2 + NoteHeight / 4
            ystemDraw = ystem
            drawLine(noteColor, Offset(xstart, y1), Offset(xstart, ystemDraw), strokeWidth = 1f)
        } else {
            val top = notePositions.minByOrNull { it.y }!!.whiteNote
            y1 = ytop + topStaff.dist(top) * NoteHeight / 2 + NoteHeight
            val y1Adj = if (!rightSide) y1 - NoteHeight / 4 else y1 - NoteHeight / 2
            ystemDraw = ystem + NoteHeight
            drawLine(noteColor, Offset(xstart, y1Adj), Offset(xstart, ystemDraw), strokeWidth = 1f)
        }

        // Skip tail for quarter, half, dotted variants; or when beamed (beam drawn by Staff)
        if (noteDuration in listOf(
                NoteDuration.Quarter, NoteDuration.DottedQuarter,
                NoteDuration.Half, NoteDuration.DottedHalf
            ) || isBeamed) return

        // DrawCurvyStem (matching MidiSheetMusic-Android Stem.DrawCurvyStem)
        drawCurvyStem(noteColor, ytop, topStaff, xstart, stemUp, ystemDraw)
    }

    /** Curvy stem tail for un-beamed 8th/16th/32nd. Matching MidiSheetMusic-Android DrawCurvyStem. */
    private fun DrawScope.drawCurvyStem(color: Color, ytop: Float, topStaff: WhiteNote, xstart: Float, stemUp: Boolean, ystemBase: Float) {
        if (noteDuration !in listOf(
                NoteDuration.Eighth, NoteDuration.DottedEighth, NoteDuration.Triplet,
                NoteDuration.Sixteenth, NoteDuration.ThirtySecond
            )) return

        if (stemUp) {
            var ystem = ystemBase
            // First flag
            val p1 = Path().apply {
                moveTo(xstart, ystem)
                cubicTo(xstart, ystem + 3 * LineSpace / 2, xstart + LineSpace * 2, ystem + NoteHeight * 2, xstart + LineSpace / 2, ystem + NoteHeight * 3)
            }
            drawPath(p1, color, style = Stroke(width = 2f))
            ystem += NoteHeight
            if (noteDuration == NoteDuration.Sixteenth || noteDuration == NoteDuration.ThirtySecond) {
                val p2 = Path().apply {
                    moveTo(xstart, ystem)
                    cubicTo(xstart, ystem + 3 * LineSpace / 2, xstart + LineSpace * 2, ystem + NoteHeight * 2, xstart + LineSpace / 2, ystem + NoteHeight * 3)
                }
                drawPath(p2, color, style = Stroke(width = 2f))
            }
            ystem += NoteHeight
            if (noteDuration == NoteDuration.ThirtySecond) {
                val p3 = Path().apply {
                    moveTo(xstart, ystem)
                    cubicTo(xstart, ystem + 3 * LineSpace / 2, xstart + LineSpace * 2, ystem + NoteHeight * 2, xstart + LineSpace / 2, ystem + NoteHeight * 3)
                }
                drawPath(p3, color, style = Stroke(width = 2f))
            }
        } else {
            // Down: ystemBase = ytop + topstaff.Dist(end)*NoteHeight/2 + NoteHeight
            var y = ystemBase
            val curveDown = { yy: Float ->
                Path().apply {
                    moveTo(xstart, yy)
                    cubicTo(xstart, yy - LineSpace, xstart + LineSpace * 2, yy - NoteHeight * 2, xstart + LineSpace, yy - NoteHeight * 2 - LineSpace / 2)
                }
            }
            drawPath(curveDown(y), color, style = Stroke(width = 2f))
            y -= NoteHeight
            if (noteDuration == NoteDuration.Sixteenth || noteDuration == NoteDuration.ThirtySecond) {
                drawPath(curveDown(y), color, style = Stroke(width = 2f))
            }
            y -= NoteHeight
            if (noteDuration == NoteDuration.ThirtySecond) {
                drawPath(curveDown(y), color, style = Stroke(width = 2f))
            }
        }
    }
    
    /**
     * Calculate the stem end WhiteNote position (matching MidiSheetMusic-Android Stem.CalculateEnd)
     * For upward stems: end = top + 6 (or +8 for sixteenth, +10 for thirty-second)
     * For downward stems: end = bottom - 6 (or -8 for sixteenth, -10 for thirty-second)
     */
    private fun calculateStemEndWhiteNote(stemUp: Boolean): WhiteNote? {
        val whiteNotes = notes.map { WhiteNote.fromMidiNote(it.note) }
        if (whiteNotes.isEmpty()) return null
        
        val topNote = whiteNotes.maxByOrNull { it.octave * 7 + it.letter } ?: return null
        val bottomNote = whiteNotes.minByOrNull { it.octave * 7 + it.letter } ?: return null
        
        return if (stemUp) {
            var end = topNote.add(6)
            when (noteDuration) {
                NoteDuration.Sixteenth -> end = end.add(2)
                NoteDuration.ThirtySecond -> end = end.add(4)
                else -> {}
            }
            end
        } else {
            var end = bottomNote.add(-6)
            when (noteDuration) {
                NoteDuration.Sixteenth -> end = end.add(-2)
                NoteDuration.ThirtySecond -> end = end.add(-4)
                else -> {}
            }
            end
        }
    }
    
    /**
     * Get the stem end position for beaming
     * Returns the Y coordinate where the stem ends (matching MidiSheetMusic-Android Stem.DrawHorizBarStem)
     * For upward stems: y = ytop + topstaff.Dist(end) * NoteHeight/2
     * For downward stems: y = ytop + topstaff.Dist(end) * NoteHeight/2 + NoteHeight
     */
    fun getStemEndY(ytop: Float, stemUp: Boolean): Float {
        val topStaff = when (clef) {
            Clef.Treble -> WhiteNote.TopTreble
            Clef.Bass -> WhiteNote.TopBass
        }
        val endNote = calculateStemEndWhiteNote(stemUp) ?: return ytop
        
        val ystem = ytop + topStaff.dist(endNote) * NoteHeight / 2
        return if (stemUp) {
            ystem
        } else {
            ystem + NoteHeight
        }
    }
    
    /**
     * Get the stem X position for beaming (position within symbol, relative to symbol's left edge).
     * Matching MidiSheetMusic-Android Stem.DrawVerticalLine:
     * - Left side: LineSpace/4 + 1
     * - Right side: LineSpace/4 + NoteWidth
     * Stem side is determined by: if direction is Up OR notes overlap, then RightSide, else LeftSide
     */
    fun getStemX(): Float {
        val stemUp = getStemDirection()
        
        // Check if notes overlap (adjacent notes with distance == 1)
        val whiteNotes = notes.map { WhiteNote.fromMidiNote(it.note) }
        val notesOverlap = whiteNotes.zipWithNext().any { (a, b) ->
            val dist = a.dist(b)
            dist == 1 || dist == -1
        }
        
        // Determine stem side: if Up OR notes overlap, then RightSide, else LeftSide
        val rightSide = stemUp || notesOverlap
        
        val baseX = LineSpace / 4f
        val xstart = if (rightSide) {
            baseX + NoteWidth
        } else {
            baseX + 1
        }
        
        // Account for right-alignment and accidentals: (getWidth() - getMinWidth()) + accidWidth + xstart
        return (getWidth() - getMinWidth()) + getAccidWidth() + xstart
    }

    /**
     * Determine stem direction for this chord
     */
    fun getStemDirection(): Boolean {
        val middleLine = when (clef) {
            Clef.Treble -> WhiteNote(WhiteNote.B, 5)
            Clef.Bass -> WhiteNote(WhiteNote.D, 3)
        }
        val whiteNotes = notes.map { WhiteNote.fromMidiNote(it.note) }
        val bottomNote = whiteNotes.minByOrNull { middleLine.dist(it) }
        val topNote = whiteNotes.maxByOrNull { middleLine.dist(it) }
        return if (bottomNote != null && topNote != null) {
            val dist = middleLine.dist(bottomNote) + middleLine.dist(topNote)
            dist >= 0
        } else {
            true
        }
    }
    
    override fun toString(): String {
        return "ChordSymbol notes=${notes.size} starttime=$startTime"
    }
}
