package io.pianosync.midi.ui.screens.midiplayer.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.pianosync.midi.data.manager.MidiPlaybackManager
import io.pianosync.midi.data.model.AppSettings
import io.pianosync.midi.ui.theme.highlightAccentColor
import io.pianosync.midi.ui.theme.leftHandNoteColor
import io.pianosync.midi.ui.theme.rightHandNoteColor
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor

data class TimeSignatureInfo(
    val numerator: Int,
    val denominator: Int,
    val quarter: Int,  // pulses per quarter note
    val measure: Int   // pulses per measure
)

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
    onNoteProcessed: () -> Unit,
    timeSignature: TimeSignatureInfo? = null
) {
    // Get device configuration
    val configuration = LocalConfiguration.current

    // Use settings for timing values
    // Note: playbackOffsetMs is not used here - timing is unified through MidiPlaybackManager
    val CORRECT_NOTE_WINDOW = settings.difficultyLevel.correctNoteWindowMs

    val noteHeight = 16.dp
    val pastTimeWindow = 2000L
    val topBarHeight = 64.dp
    val pianoKeyboardHeight = 120.dp
    val spacingAboveKeyboard = 4.dp // Small spacing between play line and keyboard
    
    // Get original BPM from playback manager (needed for calculations)
    val originalBpm = playbackManager.getOriginalBpm()
    val speedRatio = if (originalBpm > 0) bpm.toFloat() / originalBpm.toFloat() else 1f
    
    // Calculate 2 measures duration in milliseconds at original BPM scale
    // Note: We work at original BPM scale (same as SheetMusicView) for consistency
    val twoMeasuresDurationMs = if (timeSignature != null && originalBpm > 0) {
        // 1 measure in ms = (measure pulses / quarter pulses) * (60,000 ms/min / BPM)
        // 2 measures = 2 * (measure / quarter) * (60,000 / BPM)
        // Use originalBpm since we're working at original BPM scale
        val measureDurationMs = (timeSignature.measure.toLong() * 60_000L) / (timeSignature.quarter.toLong() * originalBpm.toLong())
        measureDurationMs * 2
    } else {
        // Fallback: assume 4/4 time at original BPM
        // 4 beats per measure, each beat = 60,000 / BPM ms
        (4 * 60_000L) / originalBpm.coerceAtLeast(1) * 2
    }
    
    val futureTimeWindow = twoMeasuresDurationMs
    val visualizerHeight = (configuration.screenHeightDp).dp - topBarHeight
    val playLinePosition = visualizerHeight - pianoKeyboardHeight - spacingAboveKeyboard
    val processedNotes = remember { mutableStateOf<Set<MidiNote>>(emptySet()) }

    val whiteKeyWidth = pianoConfig.keyWidth
    val whiteNoteWidth = whiteKeyWidth * 0.6f
    val blackNoteWidth = whiteKeyWidth * 0.4f

    // This function positions notes vertically based on their time
    // Note: currentTimeMs and noteTime are both at original BPM scale (same as SheetMusicView)
    fun timeToYPosition(noteTime: Long): Float {
        // Both noteTime and currentTimeMs are at original BPM scale, so compare directly
        // Use unified timing system - no offset needed as timing is handled by MidiPlaybackManager
        // Calculate position based on time difference to current playback time
        val timeDiff = noteTime - currentTimeMs
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
            // Find notes that are currently at the play line
            // Note: currentTimeMs and note.startTime are both at original BPM scale (same as SheetMusicView)
            // Convert CORRECT_NOTE_WINDOW from current BPM scale to original BPM scale
            val correctWindowAtOriginalBpm = (CORRECT_NOTE_WINDOW * speedRatio).toLong()
            
            val notesAtPlayLine = notes.filter { note ->
                // Both are at original BPM scale, so compare directly
                val timeDiff = currentTimeMs - note.startTime

                // Simple timing log for notes at play line
                if (timeDiff in 0..correctWindowAtOriginalBpm && note !in processedNotes.value) {
                    Log.d("NoteTiming", "Note ${getNoteNameForMidiNote(note.note)} - Expected: ${note.startTime}ms, Current: ${currentTimeMs}ms, Diff: ${timeDiff}ms")
                }

                timeDiff in 0..correctWindowAtOriginalBpm && // Within the correct timing window
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

            // Also check for notes that have passed the play line without being played
            val passedNotes = notes.filter { note ->
                // Both are at original BPM scale, so compare directly
                val timeDiff = currentTimeMs - note.startTime
                timeDiff > correctWindowAtOriginalBpm && // Past the correct timing window
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
                // Both currentTimeMs and note times are at original BPM scale (same as SheetMusicView)
                // Use unified timing system - compare directly without offset
                // Check if note is crossing the play line
                if (note.startTime <= currentTimeMs &&
                    note.startTime + note.duration > currentTimeMs - 100) {
                    playbackManager.processNoteAtPlayLine(note, currentTimeMs)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background) // Dark background for falling notes
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp)) // Clip content to prevent overflow above LoopControl
    ) {
        // Scrollable notes container with horizontal scrolling only
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0.dp)) // Ensure inner content is also clipped
        ) {
            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(visualizerHeight)
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
                                .height(visualizerHeight)
                                .background(
                                    color = Color.White.copy(alpha = 0.12f)
                                )
                        )
                    }
                }
                
                // Calculate measure boundaries and draw measure splitter lines
                val measureLines = if (timeSignature != null && originalBpm > 0) {
                    // Calculate measure duration in milliseconds at original BPM
                    // Notes are stored in milliseconds at original BPM
                    val measureDurationMsAtOriginalBpm = (timeSignature.measure.toLong() * 60_000L) / (timeSignature.quarter.toLong() * originalBpm.toLong())
                    
                    // Find the current measure based on currentTimeMs
                    // currentTimeMs is at original BPM scale (same as SheetMusicView)
                    // Both currentTimeMs and measure lines are at original BPM scale, so compare directly
                    // Use unified timing system - no offset needed
                    val currentMeasure = floor((currentTimeMs / measureDurationMsAtOriginalBpm.toFloat())).toInt()
                    
                    // Generate measure lines for visible range (current measure and next 2 measures)
                    val startMeasure = (currentMeasure - 1).coerceAtLeast(0)
                    val endMeasure = currentMeasure + 3 // Show previous + current + 2 ahead
                    
                    (startMeasure..endMeasure).map { measureNum ->
                        // Calculate the time for this measure start in original BPM domain (same as note times)
                        val measureStartTimeMs = measureNum * measureDurationMsAtOriginalBpm
                        measureNum to measureStartTimeMs
                    }
                } else {
                    emptyList<Pair<Int, Long>>()
                }
                
                // Draw measure splitter lines (draw before play line so they're visible)
                measureLines.forEach { (measureNum, measureStartTimeMs) ->
                    val lineY = timeToYPosition(measureStartTimeMs)
                    
                    // Only draw if line is within visible range (extend range to catch lines near edges)
                    if (lineY >= -200f && lineY <= visualizerHeight.value + 200f) {
                        // Draw the measure splitter line with 3D style - shadow and gradient for depth
                        Box(
                            modifier = Modifier
                                .offset(y = lineY.dp)
                                .width(totalWidth)
                                .height(2.dp)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = RoundedCornerShape(1.dp)
                                )
                                .drawBehind {
                                    // Draw a gradient to create 3D effect - lighter on top, darker on bottom
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.6f),
                                                Color.White.copy(alpha = 0.4f),
                                                Color.White.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                                }
                        )
                        
                        // Display measure number just above the measure line with 3D text effect
                        if (lineY >= -30f && lineY <= visualizerHeight.value + 30f) {
                            // Create 3D text effect with shadow
                            Box(
                                modifier = Modifier
                                    .offset(x = 4.dp, y = (lineY - 18).dp)
                            ) {
                                // Shadow layer (behind text)
                                Text(
                                    text = "$measureNum",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = Color.Black.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier
                                        .offset(x = 1.dp, y = 1.dp) // Slight offset for shadow
                                )
                                // Main text layer (on top)
                                Text(
                                    text = "$measureNum",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            // Add subtle elevation
                                            shadowElevation = 2f
                                        }
                                )
                            }
                        }
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
