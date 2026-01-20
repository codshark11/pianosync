package io.pianosync.midi.ui.screens.midiplayer.components

/**
 * Get the note name for a MIDI note number
 */
fun getNoteNameForMidiNote(midiNote: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (midiNote / 12) - 1
    val noteIndex = midiNote % 12
    return "${noteNames[noteIndex]}$octave"
}

/**
 * Check if a MIDI note is a white key
 */
fun isWhiteKey(note: Int): Boolean {
    return when (note % 12) {
        0, 2, 4, 5, 7, 9, 11 -> true // C, D, E, F, G, A, B
        else -> false
    }
}

fun calculateNotePosition(
    note: Int,
    minNote: Int,
    keyWidth: Float,
    isBlackKey: Boolean
): Float {
    val whiteKeysBefore = (minNote until note).count { isWhiteKey(it) }
    return if (isBlackKey) {
        // Position black key in the gap between white keys
        // White keys have 1dp horizontal padding on each side
        // Find the previous white key position
        val prevWhiteKey = (note - 1 downTo minNote).firstOrNull { isWhiteKey(it) }
        
        if (prevWhiteKey == null) {
            // No white key before this black key - position it at the start
            // Use a small offset to account for padding
            1f
        } else {
            val whiteKeysBeforePrev = (minNote until prevWhiteKey).count { isWhiteKey(it) }
            
            // The previous white key ends at: (whiteKeysBeforePrev * keyWidth + keyWidth - 1dp)
            // The next white key starts at: ((whiteKeysBeforePrev + 1) * keyWidth + 1dp)
            // Center of gap is between these two points
            val prevEnd = (whiteKeysBeforePrev * keyWidth) + keyWidth - 1f
            val nextStart = ((whiteKeysBeforePrev + 1) * keyWidth) + 1f
            (prevEnd + nextStart) / 2f
        }
    } else {
        whiteKeysBefore * keyWidth
    }
}

fun formatRecordingTime(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Calculate which note (if any) was touched at the given coordinates
 * @param x Touch X coordinate in pixels
 * @param y Touch Y coordinate in pixels
 * @param pianoConfig Piano configuration
 * @param density Density for dp to px conversion
 * @param keyboardPadding Padding around the keyboard in pixels
 * @param keyPadding Padding between white keys in pixels
 * @param blackKeyWidth Width of black keys in pixels
 * @param blackKeyHeight Height of black keys in pixels
 * @param whiteKeyHeight Height of white keys in pixels
 * @return Note number if touched, null otherwise
 */
fun calculateNoteFromPosition(
    x: Float,
    y: Float,
    pianoConfig: PianoConfiguration,
    density: androidx.compose.ui.unit.Density,
    keyboardPadding: Float,
    keyPadding: Float,
    blackKeyWidth: Float,
    blackKeyHeight: Float,
    whiteKeyHeight: Float
): Int? {
    val blackKeyTop = keyboardPadding
    val blackKeyBottom = keyboardPadding + blackKeyHeight
    val whiteKeyTop = keyboardPadding
    val whiteKeyBottom = keyboardPadding + whiteKeyHeight
    
    // First check black keys (they're on top and should be checked first)
    (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
        if (!isWhiteKey(note)) {
            val xPos = calculateNotePosition(note, pianoConfig.minNote, pianoConfig.keyWidth, true)
            val blackKeyX = keyboardPadding + xPos - blackKeyWidth / 2f
            val blackKeyRight = blackKeyX + blackKeyWidth
            
            // Check if touch is within black key bounds (both X and Y)
            if (x >= blackKeyX && x <= blackKeyRight && 
                y >= blackKeyTop && y <= blackKeyBottom) {
                return note
            }
        }
    }
    
    // Then check white keys (they cover the full height)
    (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
        if (isWhiteKey(note)) {
            val keyPosition = calculateNotePosition(note, pianoConfig.minNote, pianoConfig.keyWidth, false)
            val whiteKeyX = keyboardPadding + keyPosition + keyPadding
            val whiteKeyRight = whiteKeyX + pianoConfig.keyWidth - keyPadding * 2
            
            // White keys cover full height, so check X and Y
            if (x >= whiteKeyX && x <= whiteKeyRight && 
                y >= whiteKeyTop && y <= whiteKeyBottom) {
                return note
            }
        }
    }
    
    return null
}
