package io.pianosync.midi.data.ble


/**
 * Parses BLE MIDI packets (same format as decompiled project) and invokes
 * [MidiMessageCallback] for note on (0x90) and note off (0x80) messages.
 *
 * BLE MIDI packet format: first byte is header (timestamp high bits), followed by
 * alternating timestamp/data bytes. We use a minimal state machine to extract
 * status byte (0x80/0x90), then note, then velocity, and call the callback.
 */
class BleMidiParser {

    companion object {
        private const val TAG = "BleMidiParser"
        private const val MIDI_STATE_TIMESTAMP = 0
        private const val MIDI_STATE_WAIT = 1
        private const val MIDI_STATE_2BYTES = 21   // e.g. program change
        private const val MIDI_STATE_3BYTES_2 = 31 // expect first data byte (note)
        private const val MIDI_STATE_3BYTES_3 = 32 // expect second data byte (velocity)
        private const val STATUS_NOTE_OFF = 0x80
        private const val STATUS_NOTE_ON = 0x90
        private const val MASK_STATUS = 0xF0
    }

    @Volatile
    var callback: MidiMessageCallback? = null

    private var midiState = MIDI_STATE_TIMESTAMP
    private var timestamp = 0
    private var midiEventKind = 0
    private var midiEventNote = 0

    /**
     * Parse a BLE MIDI packet. First byte is header (timestamp high bits),
     * then a sequence of bytes interpreted by the state machine.
     * See decompiled BleMidiParser.parse() and parseMidiEvent().
     */
    fun parse(data: ByteArray?) {
        if (data == null || data.size < 2) return
        val header = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            parseMidiEvent(header, data[i].toInt() and 0xFF)
        }
    }

    /**
     * Parse a simple 3-byte MIDI packet: [status, note, velocity].
     * Used when the device sends raw MIDI (e.g. length % 3 == 0 and status 0x90/0x80).
     */
    fun parseSimple3Byte(data: ByteArray?) {
        if (data == null || data.size < 3) return
        val status = data[0].toInt() and 0xF0
        if (status != STATUS_NOTE_OFF && status != STATUS_NOTE_ON) return
        val note = data[1].toInt() and 0x7F
        val velocity = data[2].toInt() and 0x7F
        val channel = data[0].toInt() and 0x0F
        callback?.onMidiNoteMessage(status, note, velocity, channel)
    }

    private fun parseMidiEvent(header: Int, b: Int) {
        when (midiState) {
            MIDI_STATE_TIMESTAMP -> {
                if (b and 0x80 != 0) {
                    timestamp = (header and 0x3F) shl 7 or (b and 0x7F)
                    midiState = MIDI_STATE_WAIT
                }
            }
            MIDI_STATE_WAIT -> {
                val status = b and MASK_STATUS
                when (status) {
                    STATUS_NOTE_OFF, STATUS_NOTE_ON -> {
                        midiEventKind = b
                        midiState = MIDI_STATE_3BYTES_2
                    }
                    0xC0, 0xD0 -> {
                        midiEventKind = b
                        midiState = MIDI_STATE_2BYTES
                    }
                    0xA0, 0xB0, 0xE0, 0xF0 -> {
                        midiEventKind = b
                        midiState = MIDI_STATE_3BYTES_2
                    }
                    else -> {
                        if (b and 0x80 == 0) {
                            midiState = MIDI_STATE_WAIT
                        } else {
                            midiState = MIDI_STATE_TIMESTAMP
                            timestamp = (header and 0x3F) shl 7 or (b and 0x7F)
                            midiState = MIDI_STATE_WAIT
                        }
                    }
                }
            }
            MIDI_STATE_2BYTES -> {
                midiState = MIDI_STATE_WAIT
            }
            MIDI_STATE_3BYTES_2 -> {
                midiEventNote = b and 0x7F
                midiState = MIDI_STATE_3BYTES_3
            }
            MIDI_STATE_3BYTES_3 -> {
                val kind = midiEventKind and MASK_STATUS
                if (kind == STATUS_NOTE_OFF || kind == STATUS_NOTE_ON) {
                    val velocity = b and 0x7F
                    val channel = midiEventKind and 0x0F
                    callback?.onMidiNoteMessage(kind, midiEventNote, velocity, channel)
                }
                midiState = MIDI_STATE_WAIT
            }
        }
    }

    fun reset() {
        midiState = MIDI_STATE_TIMESTAMP
    }
}
