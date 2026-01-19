package io.pianosync.midi.ui.screens.midiplayer.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.pianosync.midi.data.manager.MidiPlaybackManager
import io.pianosync.midi.data.model.AppSettings
import io.pianosync.midi.ui.theme.highlightAccentColor
import io.pianosync.midi.ui.theme.leftHandNoteColor
import io.pianosync.midi.ui.theme.rightHandNoteColor
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun NoteFallVisualizer(
    modifier: Modifier = Modifier,
    notes: List<MidiNote>,
    currentTimeMs: Long,
    isPlaying: Boolean,
    bpm: Int,
    pianoConfig: PianoConfiguration,
    isPreLoading: Boolean,
    playbackManager: MidiPlaybackManager,
    correctlyPlayedNotes: MutableState<Set<Int>>,
    pressedKeys: Set<Int>,
    settings: AppSettings,
    onNoteProcessed: () -> Unit
) {
    // Get device configuration
    val configuration = LocalConfiguration.current

    // Use settings for timing values
    val PLAYBACK_OFFSET_MS = settings.playbackOffsetMs
    val CORRECT_NOTE_WINDOW = settings.difficultyLevel.correctNoteWindowMs

    val noteHeight = 16.dp
    val futureTimeWindow = 8000L
    val pastTimeWindow = 2000L
    val topBarHeight = 64.dp
    val pianoKeyboardHeight = 120.dp
    val spacingAboveKeyboard = 4.dp // Small spacing between play line and keyboard
    val visualizerHeight = (configuration.screenHeightDp).dp - topBarHeight
    val playLinePosition = visualizerHeight - pianoKeyboardHeight - spacingAboveKeyboard
    val processedNotes = remember { mutableStateOf<Set<MidiNote>>(emptySet()) }

    val whiteKeyWidth = pianoConfig.keyWidth
    val whiteNoteWidth = whiteKeyWidth * 0.6f
    val blackNoteWidth = whiteKeyWidth * 0.4f

    // Get original BPM from playback manager
    val originalBpm = playbackManager.getOriginalBpm()
    val speedRatio = if (originalBpm > 0) bpm.toFloat() / originalBpm.toFloat() else 1f

    // This function positions notes vertically based on their time
    fun timeToYPosition(noteTime: Long): Float {
        // Convert the note's time to playback time domain
        val playbackTime = (noteTime / speedRatio).toLong()

        // Add the fixed offset to compensate for the consistent delay
        val adjustedPlaybackTime = playbackTime + PLAYBACK_OFFSET_MS

        // Calculate position based on time difference to current playback time
        val timeDiff = adjustedPlaybackTime - currentTimeMs
        val pixelsPerMs = playLinePosition.value / futureTimeWindow.toFloat()

        return when {
            timeDiff <= -pastTimeWindow -> visualizerHeight.value + 135f
            timeDiff >= futureTimeWindow -> {
                // Position future notes off-screen based on how far in the future they are
                val extraOffset = ((timeDiff - futureTimeWindow) / 500f).coerceAtMost(200f)
                -noteHeight.value - extraOffset
            }
            else -> playLinePosition.value - (timeDiff * pixelsPerMs)
        }
    }

    fun calculateNoteXPosition(note: Int): Float {
        // Use the same positioning logic as the keyboard for consistency
        // The piano keyboard Box has padding(4.dp), so we need to account for that offset
        val keyboardPadding = 4f
        
        val isBlackKey = !isWhiteKey(note)
        val keyPosition = calculateNotePosition(note, pianoConfig.minNote, whiteKeyWidth, isBlackKey)
        
        return if (isBlackKey) {
            // For black keys: keyPosition is already the center of the gap
            // Center the note on that position, accounting for keyboard padding
            keyboardPadding + keyPosition - (blackNoteWidth / 2f)
        } else {
            // For white keys: keyPosition is the left edge of the white key
            // White keys have 1dp padding on each side, so the visible area starts at keyPosition + 1dp
            // Center the note within the visible white key area
            // Visible width = whiteKeyWidth - 2dp (1dp padding on each side)
            // Note should be centered: keyPosition + 1dp + (visibleWidth - noteWidth) / 2
            val visibleKeyWidth = whiteKeyWidth - 2f // Account for 1dp padding on each side
            keyboardPadding + keyPosition + 1f + ((visibleKeyWidth - whiteNoteWidth) / 2f)
        }
    }

    val totalWhiteKeys = (pianoConfig.minNote..pianoConfig.maxNote).count { isWhiteKey(it) }
    val totalWidth = whiteKeyWidth.dp * totalWhiteKeys

    // Calculate total content height needed for all notes
    val totalContentHeight = if (notes.isNotEmpty()) {
        val lastNoteEndTime = notes.maxOf { it.startTime + it.duration }
        val lastNoteY = timeToYPosition(lastNoteEndTime)
        val firstNoteY = timeToYPosition(notes.minOf { it.startTime })
        maxOf(visualizerHeight.value, (lastNoteY - firstNoteY).absoluteValue + 200f)
    } else {
        visualizerHeight.value
    }

    // Vertical scroll state
    val verticalScrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    // Auto-scroll to keep play line visible during playback
    LaunchedEffect(currentTimeMs, isPlaying) {
        if (isPlaying && totalContentHeight > visualizerHeight.value) {
            // Calculate the scroll position to keep play line visible
            // Play line should be at approximately 80% from top of visible area
            val targetScrollY = (playLinePosition.value - visualizerHeight.value * 0.2f).coerceIn(0f, totalContentHeight - visualizerHeight.value)
            
            // Smoothly scroll to target position
            scrollScope.launch {
                verticalScrollState.animateScrollTo(targetScrollY.toInt())
            }
        }
    }

    val visibleNotes = if (isPreLoading) {
        emptyList()
    } else {
        notes.filter { note ->
            val startY = timeToYPosition(note.startTime)
            val endY = timeToYPosition(note.startTime + note.duration)

            // More efficient filtering: only render notes that are within or approaching the visible area
            // Extend the range slightly above screen (-300) to ensure smooth entry
            endY <= visualizerHeight.value + noteHeight.value &&
                    startY <= visualizerHeight.value + 300f &&
                    startY >= -300f &&  // Allow notes to start from further above
                    note.note in pianoConfig.minNote..pianoConfig.maxNote
        }
    }


    LaunchedEffect(currentTimeMs, pressedKeys, isPlaying) {
        if (isPlaying) {
            // Find notes that are currently at the play line (NO offset for input timing)
            val notesAtPlayLine = notes.filter { note ->
                val notePlaybackTime = (note.startTime / speedRatio).toLong()
                val timeDiff = currentTimeMs - notePlaybackTime

                // Simple timing log for notes at play line
                if (timeDiff in 0..CORRECT_NOTE_WINDOW && note !in processedNotes.value) {
                    Log.d("NoteTiming", "Note ${getNoteNameForMidiNote(note.note)} - Expected: ${notePlaybackTime}ms, Current: ${currentTimeMs}ms, Diff: ${timeDiff}ms")
                }

                timeDiff in 0..CORRECT_NOTE_WINDOW && // Within the correct timing window
                        note !in processedNotes.value // Not already processed
            }

            // Check if any of these notes match keys being pressed
            notesAtPlayLine.forEach { note ->
                // Mark this note as processed so we don't count it twice
                if (note !in processedNotes.value) {
                    processedNotes.value = processedNotes.value + note
                    onNoteProcessed() // Tell parent we processed a note

                    if (note.note in pressedKeys) {
                        // Note was correctly played!
                        correctlyPlayedNotes.value = correctlyPlayedNotes.value + note.note
                        Log.d("NoteTiming", "✅ ${getNoteNameForMidiNote(note.note)} HIT")
                    } else {
                        Log.d("NoteTiming", "❌ ${getNoteNameForMidiNote(note.note)} MISSED")
                    }
                }
            }

            // Also check for notes that have passed the play line without being played (NO offset)
            val passedNotes = notes.filter { note ->
                val notePlaybackTime = (note.startTime / speedRatio).toLong()
                val timeDiff = currentTimeMs - notePlaybackTime
                timeDiff > CORRECT_NOTE_WINDOW && // Past the correct timing window
                        note !in processedNotes.value // Not already processed
            }

            passedNotes.forEach { note ->
                // Mark as processed so we don't count it twice
                processedNotes.value = processedNotes.value + note
                onNoteProcessed() // Tell parent we processed a note
            }
        }
    }

    LaunchedEffect(currentTimeMs, isPlaying) {
        if (isPlaying) {
            visibleNotes.forEach { note ->
                // Convert note times to playback time with the same offset
                val playbackStartTime = (note.startTime / speedRatio).toLong() + PLAYBACK_OFFSET_MS
                val playbackEndTime = ((note.startTime + note.duration) / speedRatio).toLong() + PLAYBACK_OFFSET_MS

                // Check if note is crossing the play line in the playback time domain
                if (playbackStartTime <= currentTimeMs &&
                    playbackEndTime > currentTimeMs - 100) {
                    playbackManager.processNoteAtPlayLine(note, currentTimeMs)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background) // Dark background for falling notes
            .fillMaxSize()
    ) {
        // Scrollable notes container with both horizontal and vertical scrolling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState, enabled = !isPlaying || totalContentHeight > visualizerHeight.value)
        ) {
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(totalContentHeight.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                // Vertical guide lines - drawn only at E-F and B-C boundaries (natural semitones)
                // These boundaries have no black key between them, so guide lines help with alignment
                // E is note % 12 == 4, F is note % 12 == 5
                // B is note % 12 == 11, C is note % 12 == 0
                (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                    val noteInOctave = note % 12
                    
                    // Draw line at the right edge of E keys (E-F boundary) and B keys (B-C boundary)
                    if ((noteInOctave == 4 || noteInOctave == 11) && isWhiteKey(note)) {
                        // Calculate the left edge position of the key (without note centering)
                        val keyboardPadding = 4f
                        val keyPosition = calculateNotePosition(note, pianoConfig.minNote, whiteKeyWidth, false)
                        
                        // Right edge of the key = left edge + full key width
                        // Account for keyboard padding and key positioning
                        val rightEdgeX = keyboardPadding + keyPosition + whiteKeyWidth
                        
                        Box(
                            modifier = Modifier
                                .offset(x = rightEdgeX.dp, y = 0.dp)
                                .width(1.dp)
                                .height(totalContentHeight.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.12f)
                                )
                        )
                    }
                }
                
                // Play line with theme accent - positioned relative to scroll
                Box(
                    modifier = Modifier
                        .offset(y = playLinePosition)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            color = highlightAccentColor(), // Use theme accent
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                // Notes
                visibleNotes.forEach { note ->
                    val startY = timeToYPosition(note.startTime)
                    val endY = timeToYPosition(note.startTime + note.duration)
                    val topY = minOf(startY, endY)
                    val baseHeight = maxOf((endY - startY).absoluteValue, noteHeight.value)
                    val noteHeightPx = baseHeight

                    if (noteHeightPx > 0) {
                        val isBlackKey = !isWhiteKey(note.note)
                        val xPos = calculateNoteXPosition(note.note)
                        val noteWidth = if (isBlackKey) blackNoteWidth.dp else whiteNoteWidth.dp

                        Box(
                            modifier = Modifier
                                .offset(x = xPos.dp, y = topY.dp)
                                .width(noteWidth)
                                .height(noteHeightPx.dp)
                                .background(
                                    color = if (!note.isLeftHand) {
                                        rightHandNoteColor() // Use theme color instead of hardcoded
                                    } else {
                                        leftHandNoteColor() // Use theme color instead of hardcoded
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = if (isBlackKey) 0.4f else 0.2f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
