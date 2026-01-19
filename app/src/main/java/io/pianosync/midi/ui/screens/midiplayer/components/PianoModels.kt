package io.pianosync.midi.ui.screens.midiplayer.components

data class PianoConfiguration(
    val minNote: Int,
    val maxNote: Int,
    val keyWidth: Float
)

enum class HandMode {
    BOTH_HANDS,
    LEFT_HAND_ONLY,
    RIGHT_HAND_ONLY
}

data class MidiNote(
    val note: Int,
    val isLeftHand: Boolean,
    val startTime: Long,
    val duration: Long = 0L,
    val velocity: Int = 64  // Changed from Velocity to Int for better MIDI compatibility
)
