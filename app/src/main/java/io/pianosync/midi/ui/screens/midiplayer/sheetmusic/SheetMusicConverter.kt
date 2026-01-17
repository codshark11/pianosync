package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import io.pianosync.midi.ui.screens.player.MidiNote
import io.pianosync.midi.data.parser.midi.NoteDuration
import io.pianosync.midi.data.parser.midi.TimeSignature
import io.pianosync.midi.data.parser.midi.MidiNote as PulseMidiNote
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.BarSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.BlankSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ChordSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ClefSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.MusicSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.RestSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.TimeSigSymbol

/**
 * Converts MIDI notes to sheet music symbols.
 * Groups notes by start time into chords, determines clef, and creates symbols.
 * 
 * Provides two conversion methods:
 * 1. convertToSymbols() - Works with millisecond-based notes (for backward compatibility)
 * 2. convertToSymbolsFromPulses() - Works with pulse-based notes (more accurate, matches reference)
 */
object SheetMusicConverter {
    
    /**
     * Convert a list of pulse-based MIDI notes to a list of MusicSymbols for sheet music rendering.
     * This method works entirely in pulses, matching the reference implementation exactly.
     * 
     * @param notes List of pulse-based MIDI notes (from MidiFile parser)
     * @param timeSignature Time signature from the MIDI file (works in pulses)
     * @param clef The clef to use for this staff (optional, will be determined if not provided)
     * @return List of MusicSymbols ready for rendering
     */
    fun convertToSymbolsFromPulses(
        notes: List<PulseMidiNote>,
        timeSignature: TimeSignature,
        clef: Clef? = null
    ): List<MusicSymbol> {
        if (notes.isEmpty()) return emptyList()

        // Measure length in pulses (matching MidiSheetMusic-Android)
        val measureLength = timeSignature.measure
        if (measureLength <= 0) return emptyList()

        val clefMeasures = ClefMeasuresPulses(notes, measureLength)
        val chordGroups = groupNotesIntoChordsPulses(notes)
        val symbols = mutableListOf<MusicSymbol>()

        // Initial clef (left side of staff). Matching MidiSheetMusic-Android: clef per measure.
        symbols.add(ClefSymbol(clefMeasures.getClef(0), -1L, false))
        symbols.add(TimeSigSymbol(timeSignature.numerator, timeSignature.denominator))

        var lastMeasureTime = -1
        val sortedStartTimes = chordGroups.keys.sorted()
        
        // Create accidental tracker to manage accidentals per measure
        val accidentalTracker = AccidentalTracker()

        sortedStartTimes.forEach { startTime ->
            val currentMeasureTime = (startTime / measureLength) * measureLength
            if (currentMeasureTime > lastMeasureTime && lastMeasureTime >= 0) {
                symbols.add(BarSymbol(currentMeasureTime.toLong()))
            }
            lastMeasureTime = currentMeasureTime

            val chordNotes = chordGroups[startTime] ?: emptyList()
            if (chordNotes.isNotEmpty()) {
                val chordClef = clefMeasures.getClef(startTime)
                // Convert pulse-based notes to millisecond-based for ChordSymbol (temporary)
                // TODO: Create pulse-based ChordSymbol
                val msNotes = chordNotes.map { note ->
                    MidiNote(
                        note = note.number,
                        isLeftHand = false, // Will be determined by clef
                        startTime = note.startTime.toLong(), // Convert to Long for compatibility
                        duration = note.duration.toLong(),
                        velocity = 64
                    )
                }
                symbols.add(ChordSymbol(msNotes, chordClef, timeSignature, accidentalTracker, currentMeasureTime.toLong()))
            }
        }

        val lastNoteEnd = notes.maxOfOrNull { it.endTime } ?: 0
        val finalMeasureTime = ((lastNoteEnd / measureLength) + 1) * measureLength
        symbols.add(BarSymbol(finalMeasureTime.toLong()))

        var result = addRestsPulses(symbols, timeSignature)
        result = addClefChangesPulses(result, clefMeasures)
        return result
    }
    
    /**
     * Convert a list of MIDI notes to a list of MusicSymbols for sheet music rendering.
     * This method works with millisecond-based notes (for backward compatibility).
     * 
     * @param notes List of MIDI notes to convert (millisecond-based)
     * @param timeSignature Time signature from the MIDI file
     * @param clef The clef to use for this staff (optional, will be determined if not provided)
     * @return List of MusicSymbols ready for rendering
     */
    fun convertToSymbols(
        notes: List<MidiNote>,
        timeSignature: TimeSignature,
        clef: Clef? = null
    ): List<MusicSymbol> {
        if (notes.isEmpty()) return emptyList()

        // Measure length in ms (matching MidiSheetMusic-Android)
        val measureLengthMs = (timeSignature.measure.toLong() * timeSignature.tempo) / (1000L * timeSignature.quarter)
        if (measureLengthMs <= 0) return emptyList()

        val clefMeasures = ClefMeasures(notes, measureLengthMs)
        val chordGroups = groupNotesIntoChords(notes)
        val symbols = mutableListOf<MusicSymbol>()

        // Initial clef (left side of staff). Matching MidiSheetMusic-Android: clef per measure.
        symbols.add(ClefSymbol(clefMeasures.getClef(0), -1L, false))
        symbols.add(TimeSigSymbol(timeSignature.numerator, timeSignature.denominator))

        var lastMeasureTime = -1L
        val sortedStartTimes = chordGroups.keys.sorted()
        
        // Create accidental tracker to manage accidentals per measure
        val accidentalTracker = AccidentalTracker()

        sortedStartTimes.forEach { startTime ->
            val currentMeasureTime = (startTime / measureLengthMs) * measureLengthMs
            if (currentMeasureTime > lastMeasureTime && lastMeasureTime >= 0) {
                symbols.add(BarSymbol(currentMeasureTime))
            }
            lastMeasureTime = currentMeasureTime

            val chordNotes = chordGroups[startTime] ?: emptyList()
            if (chordNotes.isNotEmpty()) {
                val chordClef = clefMeasures.getClef(startTime)
                symbols.add(ChordSymbol(chordNotes, chordClef, timeSignature, accidentalTracker, currentMeasureTime))
            }
        }

        val lastNoteEnd = notes.maxOfOrNull { it.startTime + it.duration } ?: 0L
        val finalMeasureTime = ((lastNoteEnd / measureLengthMs) + 1) * measureLengthMs
        symbols.add(BarSymbol(finalMeasureTime))

        var result = addRests(symbols, timeSignature)
        result = addClefChanges(result, clefMeasures)
        return result
    }

    /**
     * Insert ClefSymbol when the clef changes at a measure boundary.
     * Matching MidiSheetMusic-Android AddClefChanges.
     */
    private fun addClefChanges(symbols: List<MusicSymbol>, clefMeasures: ClefMeasures): List<MusicSymbol> {
        var prevClef = clefMeasures.getClef(0)
        val result = mutableListOf<MusicSymbol>()
        for (symbol in symbols) {
            if (symbol is BarSymbol) {
                val newClef = clefMeasures.getClef(symbol.getStartTime())
                if (newClef != prevClef) {
                    result.add(ClefSymbol(newClef, symbol.getStartTime() - 1, true))
                    prevClef = newClef
                }
            }
            result.add(symbol)
        }
        return result
    }
    
    /**
     * Insert RestSymbols to fill gaps between symbols. All times in ms.
     * Matching MidiSheetMusic-Android AddRests().
     */
    private fun addRests(symbols: List<MusicSymbol>, timeSignature: TimeSignature): List<MusicSymbol> {
        val result = mutableListOf<MusicSymbol>()
        var prevTime = 0L
        
        for (symbol in symbols) {
            val startTime = symbol.getStartTime()
            
            // Skip header symbols (clef, time sig) for rest calculation; they don't affect prevTime
            if (startTime < 0) {
                result.add(symbol)
                continue
            }
            
            // Insert rests to fill [prevTime, startTime)
            // Matching reference: GetRests(time, prevtime, starttime)
            val rests = getRests(timeSignature, prevTime, startTime)
            if (rests.isNotEmpty()) {
                result.addAll(rests)
            }
            
            result.add(symbol)
            
            // Update prevTime: for chords use end time (max with prevTime), for others use startTime (max with prevTime)
            // Matching reference: prevtime = Math.max(chord.getEndTime(), prevtime) or Math.max(starttime, prevtime)
            prevTime = when (symbol) {
                is ChordSymbol -> maxOf(symbol.endTime, prevTime)
                else -> maxOf(startTime, prevTime)
            }
        }
        
        return result
    }
    
    /**
     * Create rest symbols for the gap [startMs, endMs). 
     * Matching MidiSheetMusic-Android GetRests().
     * Converts ms to pulses for TimeSignature.GetNoteDuration.
     * For dotted durations, splits into two rests using pulse-based calculations.
     */
    private fun getRests(timeSignature: TimeSignature, startMs: Long, endMs: Long): List<RestSymbol> {
        if (endMs <= startMs) return emptyList()
        
        // Convert gap (ms) to pulses: pulses = (ms * quarter * 1000) / tempo
        val gapMs = endMs - startMs
        val durationPulses = (gapMs * timeSignature.quarter * 1000L) / timeSignature.tempo
        val dur = timeSignature.GetNoteDuration(durationPulses.toInt().coerceIn(0, Int.MAX_VALUE))
        
        return when (dur) {
            NoteDuration.Whole, NoteDuration.Half, NoteDuration.Quarter, NoteDuration.Eighth -> {
                // Simple rest - single symbol
                listOf(RestSymbol(startMs, dur))
            }
            NoteDuration.DottedHalf -> {
                // Matching reference: r1 = Half, r2 = Quarter at start + quarter*2 (in pulses)
                // Convert quarter*2 pulses to ms: (quarter*2 * tempo) / (quarter * 1000) = (2 * tempo) / 1000
                val secondRestMs = startMs + (2L * timeSignature.tempo) / 1000L
                listOf(
                    RestSymbol(startMs, NoteDuration.Half),
                    RestSymbol(secondRestMs, NoteDuration.Quarter)
                )
            }
            NoteDuration.DottedQuarter -> {
                // Matching reference: r1 = Quarter, r2 = Eighth at start + quarter (in pulses)
                // Convert quarter pulses to ms: (quarter * tempo) / (quarter * 1000) = tempo / 1000
                val secondRestMs = startMs + timeSignature.tempo / 1000L
                listOf(
                    RestSymbol(startMs, NoteDuration.Quarter),
                    RestSymbol(secondRestMs, NoteDuration.Eighth)
                )
            }
            NoteDuration.DottedEighth -> {
                // Matching reference: r1 = Eighth, r2 = Sixteenth at start + quarter/2 (in pulses)
                // Convert quarter/2 pulses to ms: (quarter/2 * tempo) / (quarter * 1000) = tempo / 2000
                val secondRestMs = startMs + timeSignature.tempo / 2000L
                listOf(
                    RestSymbol(startMs, NoteDuration.Eighth),
                    RestSymbol(secondRestMs, NoteDuration.Sixteenth)
                )
            }
            else -> {
                // For other durations (Sixteenth, ThirtySecond, Triplet), return null/empty
                // Matching reference: default case returns null
                emptyList()
            }
        }
    }
    
    /**
     * Group notes that start at the same time into chords (pulse-based).
     * Returns a map where key is start time (in pulses) and value is list of notes at that time.
     */
    private fun groupNotesIntoChordsPulses(notes: List<PulseMidiNote>): Map<Int, List<PulseMidiNote>> {
        return notes.groupBy { it.startTime }
            .mapValues { (_, noteList) -> noteList.sortedBy { it.number } }
    }
    
    /**
     * Group notes that start at the same time into chords (millisecond-based).
     * Returns a map where key is start time and value is list of notes at that time.
     */
    private fun groupNotesIntoChords(notes: List<MidiNote>): Map<Long, List<MidiNote>> {
        return notes.groupBy { it.startTime }
            .mapValues { (_, noteList) -> noteList.sortedBy { it.note } }
    }
    
    /**
     * Insert RestSymbols to fill gaps between symbols. All times in pulses.
     * Matching MidiSheetMusic-Android AddRests().
     */
    private fun addRestsPulses(symbols: List<MusicSymbol>, timeSignature: TimeSignature): List<MusicSymbol> {
        val result = mutableListOf<MusicSymbol>()
        var prevTime = 0
        
        for (symbol in symbols) {
            val startTime = symbol.getStartTime().toInt() // Convert from Long to Int for pulse-based
            
            // Skip header symbols (clef, time sig) for rest calculation; they don't affect prevTime
            if (startTime < 0) {
                result.add(symbol)
                continue
            }
            
            // Insert rests to fill [prevTime, startTime)
            // Matching reference: GetRests(time, prevtime, starttime)
            val rests = getRestsPulses(timeSignature, prevTime, startTime)
            if (rests.isNotEmpty()) {
                result.addAll(rests)
            }
            
            result.add(symbol)
            
            // Update prevTime: for chords use end time (max with prevTime), for others use startTime (max with prevTime)
            // Matching reference: prevtime = Math.max(chord.getEndTime(), prevtime) or Math.max(starttime, prevtime)
            prevTime = when (symbol) {
                is ChordSymbol -> maxOf(symbol.endTime.toInt(), prevTime)
                else -> maxOf(startTime, prevTime)
            }
        }
        
        return result
    }
    
    /**
     * Create rest symbols for the gap [startPulses, endPulses). 
     * Matching MidiSheetMusic-Android GetRests() - works entirely in pulses.
     * For dotted durations, splits into two rests using pulse-based calculations.
     */
    private fun getRestsPulses(timeSignature: TimeSignature, startPulses: Int, endPulses: Int): List<RestSymbol> {
        if (endPulses <= startPulses) return emptyList()
        
        val durationPulses = endPulses - startPulses
        val dur = timeSignature.GetNoteDuration(durationPulses)
        
        return when (dur) {
            NoteDuration.Whole, NoteDuration.Half, NoteDuration.Quarter, NoteDuration.Eighth -> {
                // Simple rest - single symbol
                listOf(RestSymbol(startPulses.toLong(), dur))
            }
            NoteDuration.DottedHalf -> {
                // Matching reference: r1 = Half, r2 = Quarter at start + quarter*2 (in pulses)
                val secondRestPulses = startPulses + timeSignature.quarter * 2
                listOf(
                    RestSymbol(startPulses.toLong(), NoteDuration.Half),
                    RestSymbol(secondRestPulses.toLong(), NoteDuration.Quarter)
                )
            }
            NoteDuration.DottedQuarter -> {
                // Matching reference: r1 = Quarter, r2 = Eighth at start + quarter (in pulses)
                val secondRestPulses = startPulses + timeSignature.quarter
                listOf(
                    RestSymbol(startPulses.toLong(), NoteDuration.Quarter),
                    RestSymbol(secondRestPulses.toLong(), NoteDuration.Eighth)
                )
            }
            NoteDuration.DottedEighth -> {
                // Matching reference: r1 = Eighth, r2 = Sixteenth at start + quarter/2 (in pulses)
                val secondRestPulses = startPulses + timeSignature.quarter / 2
                listOf(
                    RestSymbol(startPulses.toLong(), NoteDuration.Eighth),
                    RestSymbol(secondRestPulses.toLong(), NoteDuration.Sixteenth)
                )
            }
            else -> {
                // For other durations (Sixteenth, ThirtySecond, Triplet), return empty
                // Matching reference: default case returns null
                emptyList()
            }
        }
    }
    
    /**
     * Insert ClefSymbol when the clef changes at a measure boundary (pulse-based).
     * Matching MidiSheetMusic-Android AddClefChanges.
     */
    private fun addClefChangesPulses(symbols: List<MusicSymbol>, clefMeasures: ClefMeasuresPulses): List<MusicSymbol> {
        var prevClef = clefMeasures.getClef(0)
        val result = mutableListOf<MusicSymbol>()
        for (symbol in symbols) {
            if (symbol is BarSymbol) {
                val newClef = clefMeasures.getClef(symbol.getStartTime().toInt())
                if (newClef != prevClef) {
                    result.add(ClefSymbol(newClef, symbol.getStartTime() - 1, true))
                    prevClef = newClef
                }
            }
            result.add(symbol)
        }
        return result
    }
    
    /**
     * Calculate the staff line position for a MIDI note number.
     * Returns the line number (0 = bottom line, 4 = top line for standard staff).
     * Notes above/below the staff will have negative or >4 values.
     * 
     * Matching MidiSheetMusic-Android: uses WhiteNote conversion and dist calculation.
     */
    fun midiNoteToStaffLine(midiNote: Int, clef: Clef): Float {
        // Convert MIDI note to WhiteNote (matching the reference implementation)
        val whiteNote = WhiteNote.fromMidiNote(midiNote)
        
        // Get the top of the staff for this clef
        val topStaff = when (clef) {
            Clef.Treble -> WhiteNote.TopTreble  // E5
            Clef.Bass -> WhiteNote.TopBass      // G3
        }
        
        // Calculate distance in white notes from top of staff
        // Each white note step = 0.5 staff lines (since staff lines are whole steps)
        val dist = topStaff.dist(whiteNote)
        
        // Top of staff is at line 4 (top line), so:
        // line = 4 - (dist * 0.5)
        // But dist is in white notes, and each white note = 0.5 staff lines
        // So: line = 4 - dist * 0.5
        return 4.0f - (dist * 0.5f)
    }
    
    /**
     * Convert staff line position to Y pixel coordinate.
     * @param staffLine The staff line position (0 = bottom, 4 = top for standard staff)
     * @param staffTopY The Y coordinate of the top of the staff
     * @param lineSpacing The spacing between staff lines in pixels
     */
    fun staffLineToY(staffLine: Float, staffTopY: Float, lineSpacing: Float): Float {
        // Staff has 5 lines, numbered 0-4 from bottom to top
        // staffTopY is the Y position of the top line (line 4)
        // So line 0 (bottom) is at staffTopY + (4 * lineSpacing)
        return staffTopY + ((4.0f - staffLine) * lineSpacing)
    }
    
    /** Extra width added to BarSymbol when measure numbers are shown (matching MidiSheetMusic-Android). */
    private val MeasureNumberExtraWidth = 3 * (7 * 2.5f) / 2

    /**
     * Align symbols across multiple staffs so that measures align vertically.
     * This ensures that notes at the same start time have the same width
     * across all staffs (treble and bass), making measures align properly.
     *
     * Based on MidiSheetMusic-Android's AlignSymbols() method.
     *
     * @param staffSymbols List of symbol lists, one per staff
     * @param showMeasures If true, add extra width to BarSymbols for measure numbers
     * @return List of aligned symbol lists, one per staff
     */
    fun alignSymbols(staffSymbols: List<List<MusicSymbol>>, showMeasures: Boolean = true): List<List<MusicSymbol>> {
        if (staffSymbols.isEmpty() || staffSymbols.all { it.isEmpty() }) {
            return staffSymbols
        }

        // Create SymbolWidths to track widths across all staffs
        val widths = SymbolWidths(staffSymbols)

        // Align each staff
        val aligned = staffSymbols.mapIndexed { staffIndex, symbols ->
            alignStaffSymbols(symbols, widths, staffIndex)
        }

        // If we show measure numbers, increase bar symbol width (matching MidiSheetMusic-Android)
        if (showMeasures) {
            aligned.forEach { symbols ->
                symbols.filterIsInstance<BarSymbol>().forEach { bar ->
                    bar.setWidth(bar.getWidth() + MeasureNumberExtraWidth)
                }
            }
        }

        return aligned
    }
    
    /**
     * Align symbols for a single staff.
     * Adds BlankSymbols where needed and adjusts widths to match other staffs.
     */
    private fun alignStaffSymbols(
        symbols: List<MusicSymbol>,
        widths: SymbolWidths,
        staffIndex: Int
    ): List<MusicSymbol> {
        val result = mutableListOf<MusicSymbol>()
        var symbolIndex = 0
        
        // First, add header symbols (ClefSymbol, TimeSigSymbol with startTime < 0)
        while (symbolIndex < symbols.size && symbols[symbolIndex].getStartTime() < 0) {
            result.add(symbols[symbolIndex])
            symbolIndex++
        }
        
        // For each start time across all staffs (only positive start times)
        for (startTime in widths.getStartTimes()) {
            // Add any BarSymbols that come before this start time
            while (symbolIndex < symbols.size &&
                   symbols[symbolIndex] is BarSymbol &&
                   symbols[symbolIndex].getStartTime() <= startTime) {
                result.add(symbols[symbolIndex])
                symbolIndex++
            }
            
            // Check if this staff has a symbol at this start time
            if (symbolIndex < symbols.size && symbols[symbolIndex].getStartTime() == startTime) {
                // Add all symbols at this start time (chords can have multiple symbols)
                while (symbolIndex < symbols.size &&
                       symbols[symbolIndex].getStartTime() == startTime) {
                    result.add(symbols[symbolIndex])
                    symbolIndex++
                }
            } else {
                // No symbol at this start time, add a BlankSymbol
                result.add(BlankSymbol(startTime, 0f))
            }
        }
        
        // Add any remaining BarSymbols
        while (symbolIndex < symbols.size) {
            result.add(symbols[symbolIndex])
            symbolIndex++
        }
        
        // Adjust widths to match maximum width across all staffs
        var i = 0
        while (i < result.size) {
            val startTime = result[i].getStartTime()
            
            // Skip header symbols and BarSymbols
            if (startTime < 0 || result[i] is BarSymbol) {
                i++
                continue
            }
            
            val extraWidth = widths.getExtraWidth(staffIndex, startTime)
            
            // Set width for all symbols at this start time
            while (i < result.size && result[i].getStartTime() == startTime) {
                val currentWidth = result[i].getWidth()
                result[i].setWidth(currentWidth + extraWidth)
                i++
            }
        }
        
        return result
    }
}
