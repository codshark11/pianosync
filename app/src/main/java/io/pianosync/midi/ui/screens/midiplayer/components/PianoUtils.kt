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
