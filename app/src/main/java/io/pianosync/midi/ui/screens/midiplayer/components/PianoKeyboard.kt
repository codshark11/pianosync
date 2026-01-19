package io.pianosync.midi.ui.screens.midiplayer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.pianosync.midi.ui.theme.leftHandNoteColor
import io.pianosync.midi.ui.theme.rightHandNoteColor

@Composable
fun EnhancedPianoLayout(
    modifier: Modifier = Modifier,
    pianoConfig: PianoConfiguration,
    pressedKeys: Set<Int>,
    currentNotes: List<MidiNote>,
    activePlayingNotes: Map<Int, Boolean> = emptyMap(),
    upcomingNotes: Map<Int, Boolean> = emptyMap(),
    syncedNotes: Set<Int>,
    showKeyNames: Boolean = false,
    onNotePressed: (Int) -> Unit,
    onNoteReleased: ((Int) -> Unit)? = null
) {
    val totalWhiteKeys = (pianoConfig.minNote..pianoConfig.maxNote)
        .count { isWhiteKey(it) }
    val totalWidth = pianoConfig.keyWidth.dp * totalWhiteKeys

    Box(
        modifier = modifier
            .width(totalWidth)
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2A2A2A),
                        Color(0xFF1A1A1A)
                    )
                )
            )
            .border(2.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
            .padding(4.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // White keys
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Start
        ) {
            (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                if (isWhiteKey(note)) {
                    EnhancedWhiteKey(
                        modifier = Modifier.width(pianoConfig.keyWidth.dp),
                        note = note,
                        isPhysicallyPressed = note in pressedKeys,
                        isHighlighted = currentNotes.any { it.note == note && note in pressedKeys },
                        isActivePlaying = note in activePlayingNotes.keys,
                        isActivePlayingLeftHand = activePlayingNotes[note] == true,
                        isUpcoming = note in upcomingNotes.keys && note !in activePlayingNotes.keys,
                        isUpcomingLeftHand = upcomingNotes[note] == true,
                        showKeyName = showKeyNames,
                        onPressed = onNotePressed,
                        onReleased = onNoteReleased
                    )
                }
            }
        }

        // Black keys - positioned in gaps between white keys
        Box(modifier = Modifier.fillMaxSize()) {
            (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                if (!isWhiteKey(note)) {
                    val xPos = calculateNotePosition(note, pianoConfig.minNote, pianoConfig.keyWidth, true)
                    val blackKeyWidth = pianoConfig.keyWidth * 0.6f
                    // xPos is the center of the gap, subtract half black key width to get left edge
                    EnhancedBlackKey(
                        modifier = Modifier.offset(x = (xPos - blackKeyWidth / 2f).dp),
                        note = note,
                        isPhysicallyPressed = note in pressedKeys,
                        isHighlighted = currentNotes.any { it.note == note && note in pressedKeys },
                        isActivePlaying = note in activePlayingNotes.keys,
                        isActivePlayingLeftHand = activePlayingNotes[note] == true,
                        isUpcoming = note in upcomingNotes.keys && note !in activePlayingNotes.keys,
                        isUpcomingLeftHand = upcomingNotes[note] == true,
                        showKeyName = showKeyNames,
                        onPressed = onNotePressed,
                        onReleased = onNoteReleased,
                        keyWidth = pianoConfig.keyWidth
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedWhiteKey(
    modifier: Modifier = Modifier,
    note: Int,
    isPhysicallyPressed: Boolean = false,
    isHighlighted: Boolean = false,
    isActivePlaying: Boolean = false,
    isActivePlayingLeftHand: Boolean = false,
    isUpcoming: Boolean = false,
    isUpcomingLeftHand: Boolean = false,
    showKeyName: Boolean = false,
    onPressed: (Int) -> Unit,
    onReleased: ((Int) -> Unit)? = null
) {
    var isVirtuallyPressed by remember { mutableStateOf(false) }

    // Combine physical and virtual press states
    val isPressed = isPhysicallyPressed || isVirtuallyPressed

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val animatedColor by animateColorAsState(
        targetValue = when {
            isPhysicallyPressed -> MaterialTheme.colorScheme.outline
            isActivePlaying -> {
                // Use different colors for left and right hand actively playing notes
                // Higher opacity to show the key should be held down
                if (isActivePlayingLeftHand) {
                    leftHandNoteColor().copy(alpha = 0.7f) // Sky blue for left hand
                } else {
                    rightHandNoteColor().copy(alpha = 0.7f) // Rose for right hand
                }
            }
            isUpcoming -> {
                // Use subtle, different colors for left and right hand upcoming notes
                // Lower opacity to clearly indicate it's just a preview, not time to press
                if (isUpcomingLeftHand) {
                    leftHandNoteColor().copy(alpha = 0.25f) // Subtle sky blue for left hand
                } else {
                    rightHandNoteColor().copy(alpha = 0.25f) // Subtle rose for right hand
                }
            }
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(durationMillis = 50)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .scale(animatedScale)
            .background(
                brush = when {
                    isActivePlaying || isUpcoming -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.9f),
                            animatedColor.copy(alpha = 0.7f)
                        )
                    )
                    isPressed -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.8f),
                            animatedColor.copy(alpha = 0.6f)
                        )
                    )
                    else -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFEFE),
                            Color(0xFFF5F5F5)
                        )
                    )
                },
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFD0D0D0),
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isVirtuallyPressed = true
                        onPressed(note)
                        tryAwaitRelease()
                        isVirtuallyPressed = false
                        onReleased?.invoke(note)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (showKeyName) {
            androidx.compose.material3.Text(
                text = getNoteNameForMidiNote(note),
                fontSize = 10.sp,
                color = Color.Black.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
fun EnhancedBlackKey(
    modifier: Modifier = Modifier,
    note: Int,
    isPhysicallyPressed: Boolean = false,
    isHighlighted: Boolean = false,
    isActivePlaying: Boolean = false,
    isActivePlayingLeftHand: Boolean = false,
    isUpcoming: Boolean = false,
    isUpcomingLeftHand: Boolean = false,
    showKeyName: Boolean = false,
    onPressed: (Int) -> Unit,
    onReleased: ((Int) -> Unit)? = null,
    keyWidth: Float = 0f // Add keyWidth parameter
) {
    var isVirtuallyPressed by remember { mutableStateOf(false) }

    // Combine physical and virtual press states
    val isPressed = isPhysicallyPressed || isVirtuallyPressed

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val animatedColor by animateColorAsState(
        targetValue = when {
            isPhysicallyPressed -> MaterialTheme.colorScheme.surfaceVariant
            isActivePlaying -> {
                // Use different colors for left and right hand actively playing notes
                // Higher opacity to show the key should be held down
                if (isActivePlayingLeftHand) {
                    leftHandNoteColor().copy(alpha = 0.75f) // Sky blue for left hand (darker for black keys)
                } else {
                    rightHandNoteColor().copy(alpha = 0.75f) // Rose for right hand (darker for black keys)
                }
            }
            isUpcoming -> {
                // Use subtle, different colors for left and right hand upcoming notes
                // Lower opacity to clearly indicate it's just a preview, not time to press
                if (isUpcomingLeftHand) {
                    leftHandNoteColor().copy(alpha = 0.3f) // Subtle sky blue for left hand
                } else {
                    rightHandNoteColor().copy(alpha = 0.3f) // Subtle rose for right hand
                }
            }
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 50)
    )

    // Calculate black key width as proportion of white key width
    val blackKeyWidth = if (keyWidth > 0f) (keyWidth * 0.6f).dp else 24.dp

    Box(
        modifier = modifier
            .width(blackKeyWidth)
            .fillMaxHeight(0.62f)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .scale(animatedScale)
            .background(
                brush = when {
                    isActivePlaying || isUpcoming -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.9f),
                            animatedColor.copy(alpha = 0.7f)
                        )
                    )
                    isPressed -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.8f),
                            animatedColor.copy(alpha = 0.6f)
                        )
                    )
                    else -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0A0A0A)
                        )
                    )
                },
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isVirtuallyPressed = true
                        onPressed(note)
                        tryAwaitRelease()
                        isVirtuallyPressed = false
                        onReleased?.invoke(note)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (showKeyName) {
            androidx.compose.material3.Text(
                text = getNoteNameForMidiNote(note),
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
