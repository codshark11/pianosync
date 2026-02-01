package io.pianosync.midi.data.ble

/**
 * Callback for parsed MIDI messages from BLE (note on/off and optionally others).
 */
interface MidiMessageCallback {
    /**
     * Called when a MIDI note on (0x90) or note off (0x80) is parsed.
     * @param status 0x90 for note on, 0x80 for note off
     * @param note MIDI note number (0-127)
     * @param velocity 0-127 (for note off typically 0)
     * @param channel MIDI channel (0-15)
     */
    fun onMidiNoteMessage(status: Int, note: Int, velocity: Int, channel: Int)
}
