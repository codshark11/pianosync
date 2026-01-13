package io.pianosync.midi.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import io.pianosync.midi.ui.screens.player.MidiNote
import java.io.InputStream
import io.pianosync.midi.data.parser.midi.MidiFile
import io.pianosync.midi.data.parser.midi.MidiEvent
import io.pianosync.midi.data.parser.midi.MidiTrack
import io.pianosync.midi.ui.screens.player.HandMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.roundToLong


private fun Int.toBigEndianByteArray(size: Int): ByteArray {
    val bytes = ByteArray(size)
    for (i in 0 until size) {
        bytes[size - 1 - i] = (this ushr (i * 8)).toByte()
    }
    return bytes
}

// Helper to convert Short to Big-Endian byte array
// MOVED TO TOP LEVEL and specific for Short
private fun Short.toBigEndianByteArray(size: Int): ByteArray {
    if (size != 2) throw IllegalArgumentException("Short toBigEndianByteArray size must be 2")
    val value = this.toInt()
    return byteArrayOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}


object MidiWriter {

    // Writes a variable-length quantity (used for delta times)
    private fun writeVariableLengthValue(outputStream: FileOutputStream, value: Long) {
        if (value < 0) throw IllegalArgumentException("VLQ value cannot be negative: $value")

        if (value == 0L) {
            outputStream.write(0)
            return
        }

        val buffer = ByteArray(4)
        var count = 0
        var tempValue = value

        do {
            if (count >= buffer.size && tempValue > 0) {
                Log.w("MidiWriter", "VLQ value $value may be too large for standard 4-byte MIDI delta-time.")
                if (count >= buffer.size) throw IllegalStateException("VLQ value too large for 4-byte buffer: $value")
            }
            buffer[count++] = (tempValue and 0x7F).toByte()
            tempValue = tempValue ushr 7
        } while (tempValue > 0)

        for (i in count - 1 downTo 0) {
            val byteToWrite = if (i > 0) {
                (buffer[i].toInt() and 0xFF) or 0x80
            } else {
                buffer[i].toInt() and 0xFF
            }
            outputStream.write(byteToWrite)
        }
    }


    fun writeFilteredMidiFile(
        context: Context,
        notes: List<MidiNote>,
        targetBpm: Int, // This is now the target BPM, not necessarily original
        handMode: HandMode
    ): Uri {
        val filteredNotes = when (handMode) {
            HandMode.LEFT_HAND_ONLY -> notes.filter { it.isLeftHand }
            HandMode.RIGHT_HAND_ONLY -> notes.filter { !it.isLeftHand }
            HandMode.BOTH_HANDS -> notes
        }

        val sortedNotes = filteredNotes.sortedWith(compareBy({ it.startTime }, { it.note }))
        val tempFile = File(context.cacheDir, "filtered_midi_${System.currentTimeMillis()}.mid")

        try {
            FileOutputStream(tempFile).use { fos ->
                // --- MIDI Header Chunk (MThd) ---
                fos.write(byteArrayOf(0x4D, 0x54, 0x68, 0x64)) // MThd
                fos.write(byteArrayOf(0x00, 0x00, 0x00, 0x06)) // Length (6 bytes)
                fos.write(byteArrayOf(0x00, 0x00))             // Format (Type 0: single track)
                fos.write(byteArrayOf(0x00, 0x01))             // Number of tracks (1 for Type 0)
                val ticksPerQuarterNote: Short = 480           // Higher resolution for better timing
                fos.write(ticksPerQuarterNote.toBigEndianByteArray(2)) // Division

                // --- MIDI Track Chunk (MTrk) ---
                val trackChunkStartPos = fos.channel.position()
                fos.write(byteArrayOf(0x4D, 0x54, 0x72, 0x6B)) // MTrk
                val trackLengthPos = fos.channel.position()
                fos.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // Placeholder for track length

                var currentMidiTickTime = 0L

                // Use the target BPM for timing calculations
                val ticksPerMs = ticksPerQuarterNote / (60000.0 / targetBpm)

                // Set tempo meta event using target BPM
                val microSecsPerQuarterNote = (60000000 / targetBpm)
                writeVariableLengthValue(fos, 0)
                fos.write(0xFF)
                fos.write(0x51)
                fos.write(0x03)
                val tempoBytes = microSecsPerQuarterNote.toBigEndianByteArray(4)
                fos.write(tempoBytes, 1, 3) // Write 3 MSBs of the 4-byte array

                // Convert notes to MIDI events
                val midiEvents = mutableListOf<Pair<Long, ByteArray>>()

                sortedNotes.forEach { note ->
                    val noteStartTick = (note.startTime * ticksPerMs).toLong().coerceAtLeast(0)
                    val noteEndTick = ((note.startTime + note.duration) * ticksPerMs).toLong().coerceAtLeast(noteStartTick + 1)

                    // Note on event
                    midiEvents.add(noteStartTick to byteArrayOf(0x90.toByte(), note.note.toByte(), note.velocity.toByte()))
                    // Note off event
                    midiEvents.add(noteEndTick to byteArrayOf(0x80.toByte(), note.note.toByte(), 0x00))
                }

                // Sort events by tick time
                midiEvents.sortBy { it.first }

                // Write MIDI events with proper delta times
                midiEvents.forEach { (tick, eventData) ->
                    val deltaTime = (tick - currentMidiTickTime).coerceAtLeast(0)
                    writeVariableLengthValue(fos, deltaTime)
                    fos.write(eventData)
                    currentMidiTickTime = tick
                }

                // End of track
                writeVariableLengthValue(fos, 0)
                fos.write(0xFF)
                fos.write(0x2F)
                fos.write(0x00)

                // Write track length
                val trackLength = fos.channel.position() - trackLengthPos - 4
                fos.channel.position(trackLengthPos)
                fos.write(trackLength.toInt().toBigEndianByteArray(4))
                fos.channel.position(trackChunkStartPos + 8 + trackLength)
            }
        } catch (e: IOException) {
            Log.e("MidiWriter", "Error writing MIDI file: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            throw e
        }

        Log.d("MidiWriter", "Created filtered MIDI file with ${sortedNotes.size} notes at ${targetBpm} BPM")
        return Uri.fromFile(tempFile)
    }
}

/**
 * Utility object for parsing MIDI files
 */
object MidiParser {

    /**
     * Extracts the BPM from a MIDI file
     *
     * @param context The application context
     * @param uri The URI of the MIDI file
     * @return The BPM value if found, null otherwise
     */
    fun extractBPM(context: Context, uri: Uri): Int? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val midiBytes = inputStream.readBytes()
                val midiFile = MidiFile(midiBytes, uri.toString())
                // Extract BPM from TimeSignature
                val tempo = midiFile.time.tempo
                if (tempo > 0) {
                    60_000_000 / tempo
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("MidiParser", "Error extracting BPM", e)
            null
        }
    }

    /**
     * Parses MIDI notes with proper hand separation and note durations
     * @param inputStream The MIDI file input stream
     * @param bpm The tempo in beats per minute
     * @return List of MidiNotes with timing, duration, and hand information
     */
    fun parseMidiNotes(inputStream: InputStream, bpm: Int): List<MidiNote> {
        try {
            val midiBytes = inputStream.readBytes()
            val midiFile = MidiFile(midiBytes, "")
            val notes = mutableListOf<MidiNote>()
            
            // Extract BPM from the MIDI file
            val tempo = midiFile.time.tempo
            val originalBpm = if (tempo > 0) {
                60_000_000 / tempo
            } else {
                bpm
            }
            
            val quarternote = midiFile.time.quarter

            // First pass: collect all note values by track to determine which tracks have notes
            val trackNotesData = mutableMapOf<Int, MutableList<Int>>()

            midiFile.getTracks().forEachIndexed { trackIndex, track ->
                val trackNotes = mutableListOf<Int>()

                track.notes.forEach { note ->
                    trackNotes.add(note.number)
                }

                if (trackNotes.isNotEmpty()) {
                    trackNotesData[trackIndex] = trackNotes
                }
            }

            // Determine hand assignment strategy
            val handAssignmentStrategy = when {
                // If we have exactly 2 tracks with notes, assume first is right, second is left
                // This is a common convention in piano MIDI files
                trackNotesData.size == 2 -> {
                    val sortedTracks = trackNotesData.keys.sorted()
                    mapOf(
                        sortedTracks[0] to false, // First track = right hand
                        sortedTracks[1] to true   // Second track = left hand
                    )
                }

                // If more than 2 tracks, use average pitch to determine
                trackNotesData.size > 2 -> {
                    // Calculate average pitch for each track
                    val trackAveragePitch = trackNotesData.mapValues { entry ->
                        entry.value.average()
                    }

                    // Sort tracks by average pitch (low to high)
                    val sortedTracks = trackAveragePitch.entries.sortedBy { it.value }

                    // Log the track data for debugging
                    sortedTracks.forEach { (trackIndex, avgPitch) ->
                        Log.d("MidiParser", "Track $trackIndex average pitch: $avgPitch")
                    }

                    // Assign left hand to lower half of tracks, right hand to upper half
                    sortedTracks.withIndex().associate { (index, entry) ->
                        entry.key to (index < sortedTracks.size / 2)
                    }
                }

                // If only 1 track or no tracks with notes, we'll use middle C detection
                else -> emptyMap()
            }

            // Log the hand assignment for debugging
            handAssignmentStrategy.forEach { (trackIndex, isLeftHand) ->
                Log.d("MidiParser", "Track $trackIndex assigned to ${if (isLeftHand) "left" else "right"} hand")
            }

            // Second pass: extract notes with proper hand assignment
            midiFile.getTracks().forEachIndexed { trackIndex, track ->
                // Skip tracks without notes
                if (trackIndex !in trackNotesData.keys) {
                    return@forEachIndexed
                }

                // Determine hand based on strategy or fall back to middle C
                val multipleTracksWithNotes = trackNotesData.size >= 2
                val isLeftHand = handAssignmentStrategy[trackIndex] ?: false

                // Convert MidiSheetMusic notes to our app's MidiNote format
                track.notes.forEach { midiNote ->
                    // Convert pulses to milliseconds
                    val startTimeMs = (midiNote.startTime * 60_000L) / (originalBpm * quarternote)
                    val durationMs = (midiNote.duration * 60_000L) / (originalBpm * quarternote)
                    
                    notes.add(
                        MidiNote(
                            note = midiNote.number,
                            startTime = startTimeMs,
                            duration = durationMs,
                            // If multiple tracks, use the track's hand assignment
                            // If single track, fall back to middle C detection
                            isLeftHand = if (multipleTracksWithNotes) isLeftHand else midiNote.number < 60,
                            velocity = 100 // Default velocity (MidiSheetMusic doesn't expose velocity per note)
                        )
                    )
                }
            }

            return notes.sortedBy { it.startTime }

        } catch (e: Exception) {
            Log.e("MidiParser", "Error parsing MIDI file", e)
            return emptyList()
        }
    }
}
