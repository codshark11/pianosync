package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.Accid

/**
 * Tracks which notes have had accidentals shown in the current measure.
 * Matching MidiSheetMusic-Android KeySignature.GetAccidental() behavior:
 * - Once an accidental is shown for a note in a measure, it doesn't show again
 * - Resets when moving to a new measure
 */
class AccidentalTracker {
    // Track which MIDI note numbers have had accidentals shown in current measure
    private val shownAccidentals = mutableSetOf<Int>()
    private var currentMeasure: Long = -1L
    
    /**
     * Get the accidental for a MIDI note in the given measure.
     * Returns Accid.None if the accidental was already shown in this measure.
     * 
     * @param midiNote MIDI note number (0-127)
     * @param measure Measure number (in ms, used to detect measure changes)
     * @return Accid to display, or Accid.None if already shown or not needed
     */
    fun getAccidental(midiNote: Int, measure: Long): Accid {
        // Reset when moving to a new measure
        if (measure != currentMeasure) {
            shownAccidentals.clear()
            currentMeasure = measure
        }
        
        // Check if this note already had an accidental in this measure
        if (midiNote in shownAccidentals) {
            return Accid.None
        }
        
        // Determine if this note needs an accidental (black keys need sharps/flats)
        // Using NoteScale.FromNumber = (midiNote + 3) % 12
        val notescale = (midiNote + 3) % 12
        val isBlackKey = notescale in listOf(1, 4, 6, 9, 11) // A#, C#, D#, F#, G#
        
        if (!isBlackKey) {
            return Accid.None
        }
        
        // Mark this note as having shown an accidental
        shownAccidentals.add(midiNote)
        
        // For now, use sharp for all black keys (matching simple behavior)
        // In full implementation, this would use KeySignature to determine sharp/flat/natural
        return Accid.Sharp
    }
    
    /**
     * Reset the tracker (called when starting a new staff or section)
     */
    fun reset() {
        shownAccidentals.clear()
        currentMeasure = -1L
    }
}
