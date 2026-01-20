package io.pianosync.midi.ui.screens.midiplayer.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pianosync.midi.data.manager.MidiPlaybackManager
import io.pianosync.midi.data.model.AppSettings
import io.pianosync.midi.ui.theme.highlightAccentColor
import io.pianosync.midi.ui.theme.leftHandNoteColor
import io.pianosync.midi.ui.theme.rightHandNoteColor
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
    val visualizerHeight = (configuration.screenHeightDp).dp + topBarHeight
    val playLinePosition = visualizerHeight
    val processedNotes = remember { mutableStateOf<Set<MidiNote>>(emptySet()) }

    val whiteKeyWidth = pianoConfig.keyWidth
    val whiteNoteWidth = whiteKeyWidth * 0.6f
    val blackNoteWidth = whiteKeyWidth * 0.4f

    fun calculateNoteXPosition(note: Int, density: androidx.compose.ui.unit.Density): Float {
        // Use the same positioning logic as the keyboard for consistency
        // The piano keyboard has padding(4.dp), so we need to account for that offset
        val keyboardPadding = with(density) { 4.dp.toPx() }
        
        // Convert keyWidth from dp to pixels for Canvas drawing
        val keyWidthPx = with(density) { whiteKeyWidth.dp.toPx() }
        
        val isBlackKey = !isWhiteKey(note)
        val keyPosition = calculateNotePosition(note, pianoConfig.minNote, keyWidthPx, isBlackKey)
        
        val whiteNoteWidthPx = keyWidthPx * 0.6f
        val blackNoteWidthPx = keyWidthPx * 0.4f
        
        return if (isBlackKey) {
            // For black keys: keyPosition is already the center of the gap
            // Center the note on that position, accounting for keyboard padding
            keyboardPadding + keyPosition - (blackNoteWidthPx / 2f)
        } else {
            // For white keys: keyPosition is the left edge of the white key
            // White keys have 1dp padding on each side, so the visible area starts at keyPosition + 1dp
            // Center the note within the visible white key area
            val paddingPx = with(density) { 1.dp.toPx() }
            val visibleKeyWidth = keyWidthPx - (paddingPx * 2f)
            keyboardPadding + keyPosition + paddingPx + ((visibleKeyWidth - whiteNoteWidthPx) / 2f)
        }
    }

    val totalWhiteKeys = (pianoConfig.minNote..pianoConfig.maxNote).count { isWhiteKey(it) }
    
    // Calculate total content height based on song duration for vertical scrolling
    val firstNoteTime = remember(notes) { notes.minOfOrNull { it.startTime } ?: 0L }
    val lastNoteTime = remember(notes) { 
        notes.maxOfOrNull { it.startTime + it.duration } ?: 0L 
    }
    val songDurationMs = lastNoteTime - firstNoteTime
    
    // Calculate pixels per millisecond for vertical scrolling
    val pixelsPerMs = remember(playLinePosition, futureTimeWindow) {
        playLinePosition.value / futureTimeWindow.toFloat()
    }
    
    // Scroll states - declared early so it can be used in timeToAbsoluteYPosition
    val verticalScrollState = rememberScrollState()
    
    // Calculate total content height - needs to be large enough for scrolling
    // We need space for past notes (below playline) and future notes (above playline)
    val pastTimeWindow = 2000L // Show 2 seconds of past notes
    val totalTimeWindow = futureTimeWindow + pastTimeWindow
    val totalContentHeight = remember(totalTimeWindow, pixelsPerMs, visualizerHeight) {
        max((totalTimeWindow * pixelsPerMs).toFloat(), visualizerHeight.value * 2f)
    }
    
    // This function positions notes vertically relative to the static playline
    // Future notes (noteTime > currentTimeMs) appear above playline
    // Past notes (noteTime < currentTimeMs) appear below playline
    // The playline is at playLinePosition.value from the top of the viewport
    fun timeToAbsoluteYPosition(noteTime: Long): Float {
        val timeFromCurrent = (noteTime - currentTimeMs).toFloat()
        // Future notes have positive timeFromCurrent, so they're above playline (negative offset)
        // Past notes have negative timeFromCurrent, so they're below playline (positive offset)
        // The playline in Canvas coordinates is: scrollY + playLinePosition.value
        // Notes are positioned relative to that
        val scrollY = verticalScrollState.value.toFloat()
        val playLineYInCanvas = scrollY + playLinePosition.value
        return playLineYInCanvas - (timeFromCurrent * pixelsPerMs)
    }
    
    // Get density for dp to px conversion
    val density = LocalDensity.current
    
    // Calculate total width to match PianoKeyboard: keyWidth * totalWhiteKeys + keyboardPadding * 2
    // This ensures falling notes align properly with the piano keyboard
    // Note: keyWidth is in dp, so we need to convert it to pixels for the calculation
    val keyboardPadding = with(density) { 4.dp.toPx() }
    val keyWidthPx = with(density) { whiteKeyWidth.dp.toPx() }
    val totalWidth = keyWidthPx * totalWhiteKeys + keyboardPadding * 2
    
    // Text measurer for measure numbers
    val textMeasurer = rememberTextMeasurer()
    
    // Get theme colors (must be called in @Composable context)
    val backgroundColor = MaterialTheme.colorScheme.background
    val accentColor = highlightAccentColor()
    val leftHandColor = leftHandNoteColor()
    val rightHandColor = rightHandNoteColor()


    // Calculate visible notes with viewport culling
    val visibleNotes = remember(notes, currentTimeMs, verticalScrollState.value, isPreLoading, firstNoteTime, pixelsPerMs) {
        if (isPreLoading) {
            emptyList()
        } else {
            val scrollY = verticalScrollState.value.toFloat()
            val viewportTop = scrollY
            val viewportBottom = scrollY + visualizerHeight.value
            
            notes.filter { note ->
                val noteStartY = timeToAbsoluteYPosition(note.startTime)
                val noteEndY = timeToAbsoluteYPosition(note.startTime + note.duration)
                
                // Viewport culling: only render notes that intersect with visible area
                noteEndY >= viewportTop - 300f && // Margin for smooth entry
                noteStartY <= viewportBottom + 300f && // Margin for smooth exit
                note.note in pianoConfig.minNote..pianoConfig.maxNote
            }
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

    // Auto-scroll to keep playline visible (optional - can be removed if manual scroll is preferred)
    // The playline stays at fixed screen position playLinePosition.value
    // Notes scroll past it as time progresses

    LaunchedEffect(currentTimeMs, isPlaying, visibleNotes) {
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
    
    // Calculate measure lines
    val measureLines = remember(timeSignature, originalBpm, currentTimeMs, firstNoteTime, lastNoteTime) {
        if (timeSignature != null && originalBpm > 0) {
            val measureDurationMsAtOriginalBpm = (timeSignature.measure.toLong() * 60_000L) / 
                (timeSignature.quarter.toLong() * originalBpm.toLong())
            
            if (measureDurationMsAtOriginalBpm > 0) {
                val firstMeasure = floor((firstNoteTime / measureDurationMsAtOriginalBpm.toFloat())).toInt()
                val lastMeasure = ceil((lastNoteTime / measureDurationMsAtOriginalBpm.toFloat())).toInt()
                
                (firstMeasure..lastMeasure).map { measureNum ->
                    val measureStartTimeMs = measureNum * measureDurationMsAtOriginalBpm
                    measureNum to measureStartTimeMs
                }
            } else {
                emptyList<Pair<Int, Long>>()
            }
        } else {
            emptyList<Pair<Int, Long>>()
        }
    }
    
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .width(with(density) { totalWidth.dp })
                .height(with(density) { totalContentHeight.dp })
                .verticalScroll(verticalScrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(with(density) { totalWidth.dp })
                    .height(with(density) { totalContentHeight.dp })
            ) {
                val scrollY = verticalScrollState.value.toFloat()
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // Draw background
                drawRect(
                    color = backgroundColor,
                    size = Size(canvasWidth, canvasHeight)
                )
                
                // Draw vertical guide lines at E-F and B-C boundaries
                val keyboardPadding = with(density) { 4.dp.toPx() }
                val keyWidthPxForDrawing = with(density) { whiteKeyWidth.dp.toPx() }
                (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                    val noteInOctave = note % 12
                    
                    if ((noteInOctave == 4 || noteInOctave == 11) && isWhiteKey(note)) {
                        val keyPosition = calculateNotePosition(note, pianoConfig.minNote, keyWidthPxForDrawing, false)
                        val rightEdgeX = keyboardPadding + keyPosition + keyWidthPxForDrawing
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(rightEdgeX, 0f),
                            end = Offset(rightEdgeX, canvasHeight),
                            strokeWidth = 1f
                        )
                    }
                }
                
                // Draw measure lines
                measureLines.filter { (measureNum, _) -> measureNum > 0 }.forEach { (measureNum, measureStartTimeMs) ->
                    val lineY = timeToAbsoluteYPosition(measureStartTimeMs)
                    
                    // Only draw if line is within visible range
                    // lineY is already in Canvas coordinates, so check against viewport
                    if (lineY >= scrollY - 200f && lineY <= scrollY + canvasHeight + 200f) {
                        // Draw measure line with gradient effect
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.4f),
                                    Color.White.copy(alpha = 0.25f)
                                )
                            ),
                            start = Offset(0f, lineY),
                            end = Offset(canvasWidth, lineY),
                            strokeWidth = 2f
                        )
                        
                        // Draw measure number
                        val lineYRelative = lineY - scrollY
                        // Ensure text is drawn within valid canvas bounds
                        // lineY is in Canvas coordinates, but we need to check if it's within actual canvas bounds
                        // Text should be at least 20px from top and bottom to avoid constraint issues
                        // Clamp lineY to valid canvas bounds before calculating textY
                        val clampedLineY = lineY.coerceIn(0f, canvasHeight)
                        val textY = clampedLineY - 18f
                        if (lineYRelative >= -30f && lineYRelative <= canvasHeight + 30f && 
                            textY >= 20f && textY <= canvasHeight - 20f) {
                            val text = "$measureNum"
                            
                            // Draw shadow
                            drawText(
                                textMeasurer = textMeasurer,
                                text = text,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium
                                ),
                                topLeft = Offset(4f + 1f, textY + 1f)
                            )
                            
                            // Draw main text
                            drawText(
                                textMeasurer = textMeasurer,
                                text = text,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                ),
                                topLeft = Offset(4f, textY)
                            )
                        }
                    }
                }
                
                // Draw play line - static at fixed position relative to viewport
                // In Canvas coordinates, this is scrollY + playLinePosition.value
                val playLineY = scrollY + playLinePosition.value
                drawLine(
                    color = accentColor,
                    start = Offset(0f, playLineY),
                    end = Offset(canvasWidth, playLineY),
                    strokeWidth = 2f
                )
                
                // Draw notes
                visibleNotes.forEach { note ->
                    // For falling notes: position the note rectangle
                    // startY = position when note starts (time T)
                    // endY = position when note ends (time T + duration)
                    val startY = timeToAbsoluteYPosition(note.startTime)
                    val endY = timeToAbsoluteYPosition(note.startTime + note.duration)
                    // With our positioning: later times = smaller Y (higher on screen)
                    // So endY < startY (end time position is above start time position)
                    // For the note rectangle: top should be at endY (later time), bottom at startY (earlier time)
                    // But visually, we want the note to extend from its start position downward
                    // So: topY = endY (where note ends, visually higher), bottomY = startY (where note starts, visually lower)
                    val topY = endY
                    val bottomY = startY
                    val noteHeightPx = maxOf((bottomY - topY).absoluteValue, with(density) { noteHeight.toPx() })
                    
                    // Viewport culling for notes
                    val noteYRelative = topY - scrollY
                    if (noteHeightPx > 0 && noteYRelative + noteHeightPx >= -50f && noteYRelative <= canvasHeight + 50f) {
                        val isBlackKey = !isWhiteKey(note.note)
                        val xPos = calculateNoteXPosition(note.note, density)
                        // Convert keyWidth to pixels for note width calculation
                        val keyWidthPxForNotes = with(density) { whiteKeyWidth.dp.toPx() }
                        val noteWidthPx = if (isBlackKey) {
                            keyWidthPxForNotes * 0.4f
                        } else {
                            keyWidthPxForNotes * 0.6f
                        }
                        
                        val noteColor = if (!note.isLeftHand) {
                            rightHandColor
                        } else {
                            leftHandColor
                        }
                        
                        // Draw note rectangle with rounded corners
                        val cornerRadius = minOf(noteWidthPx / 2f, noteHeightPx / 2f, 8f)
                        drawRoundRect(
                            color = noteColor,
                            topLeft = Offset(xPos, topY),
                            size = Size(noteWidthPx, noteHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                        )
                        
                        // Draw note border
                        drawRoundRect(
                            color = Color.White.copy(alpha = if (isBlackKey) 0.4f else 0.2f),
                            topLeft = Offset(xPos, topY),
                            size = Size(noteWidthPx, noteHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                            style = Stroke(width = 1f)
                        )
                    }
                }
            }
        }
    }
}
