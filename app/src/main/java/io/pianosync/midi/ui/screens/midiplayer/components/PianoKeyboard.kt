package io.pianosync.midi.ui.screens.midiplayer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    syncedNotes: Set<Int>,
    showKeyNames: Boolean = false,
    onNotePressed: (Int) -> Unit,
    onNoteReleased: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current
    val totalWhiteKeys = (pianoConfig.minNote..pianoConfig.maxNote)
        .count { isWhiteKey(it) }
    
    // Calculate dimensions
    val keyboardPadding = with(density) { 4.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }
    val keyPadding = with(density) { 1.dp.toPx() }
    // Convert keyWidth from dp to pixels for Canvas drawing and touch detection
    val keyWidthPx = with(density) { pianoConfig.keyWidth.dp.toPx() }
    // Calculate total width in pixels for Canvas, and in dp for modifier
    val totalWidthPx = keyWidthPx * totalWhiteKeys + keyboardPadding * 2
    val totalWidthDp = pianoConfig.keyWidth * totalWhiteKeys + 8f // 4dp on each side = 8dp total
    val blackKeyWidth = keyWidthPx * 0.4f
    val blackKeyHeightRatio = 0.62f
    
    // Text measurer for key names
    val textMeasurer = rememberTextMeasurer()
    
    // Get theme colors (must be called in @Composable context)
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val leftHandColor = leftHandNoteColor()
    val rightHandColor = rightHandNoteColor()
    
    // Calculate key colors and states
    val keyStates = remember(pressedKeys, activePlayingNotes) {
        (pianoConfig.minNote..pianoConfig.maxNote).associateWith { note ->
            when {
                note in pressedKeys -> KeyState.PRESSED
                note in activePlayingNotes.keys -> KeyState.ACTIVE_PLAYING
                else -> KeyState.NORMAL
            }
        }
    }
    
    Box(
        modifier = modifier
            .width(with(density) { totalWidthDp.dp })
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2A2A2A),
                        Color(0xFF1A1A1A)
                    )
                )
            )
            .horizontalScroll(rememberScrollState())
    ) {
        var canvasHeightState by remember { mutableStateOf(0f) }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    canvasHeightState = size.height.toFloat()
                }
                .pointerInput(pianoConfig, keyboardPadding, keyPadding, blackKeyWidth, blackKeyHeightRatio, canvasHeightState) {
                    detectTapGestures(
                        onPress = { offset ->
                            val whiteKeyHeight = canvasHeightState - keyboardPadding * 2
                            val blackKeyHeight = whiteKeyHeight * blackKeyHeightRatio
                            
                            val note = calculateNoteFromPosition(
                                offset.x,
                                offset.y,
                                pianoConfig,
                                density,
                                keyboardPadding,
                                keyPadding,
                                blackKeyWidth,
                                blackKeyHeight,
                                whiteKeyHeight
                            )
                            note?.let {
                                onNotePressed(it)
                                tryAwaitRelease()
                                onNoteReleased?.invoke(it)
                            }
                        }
                    )
                }
        ) {
            val canvasHeight = size.height
            val whiteKeyHeight = canvasHeight - keyboardPadding * 2
            val blackKeyHeight = whiteKeyHeight * blackKeyHeightRatio
            
            // Convert keyWidth from dp to pixels for Canvas drawing
            val keyWidthPx = with(density) { pianoConfig.keyWidth.dp.toPx() }
            val blackKeyWidthPx = keyWidthPx * 0.4f
            
            // Draw border
            drawRect(
                color = Color(0xFF444444),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height),
                style = Stroke(width = borderWidth)
            )
            
            // Draw white keys background
            drawRect(
                color = Color.White,
                topLeft = Offset(keyboardPadding, keyboardPadding),
                size = Size(keyWidthPx * totalWhiteKeys, whiteKeyHeight)
            )
            
            // Draw white keys
            (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                if (isWhiteKey(note)) {
                    val keyPosition = calculateNotePosition(note, pianoConfig.minNote, keyWidthPx, false)
                    val x = keyboardPadding + keyPosition + keyPadding
                    val width = keyWidthPx - keyPadding * 2
                    
                    val state = keyStates[note] ?: KeyState.NORMAL
                    val keyColor = when (state) {
                        KeyState.PRESSED -> outlineColor
                        KeyState.ACTIVE_PLAYING -> {
                            val isLeftHand = activePlayingNotes[note] == true
                            if (isLeftHand) {
                                leftHandColor
                            } else {
                                rightHandColor
                            }
                        }
                        KeyState.NORMAL -> Color.White
                    }
                    
                    // Draw white key with gradient or solid color
                    when (state) {
                        KeyState.NORMAL -> {
                            // Draw solid white for normal state to ensure visibility
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(x, keyboardPadding),
                                size = Size(width, whiteKeyHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                        KeyState.ACTIVE_PLAYING -> {
                            // Draw active playing keys with solid color for better visibility
                            drawRoundRect(
                                color = keyColor,
                                topLeft = Offset(x, keyboardPadding),
                                size = Size(width, whiteKeyHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                        else -> {
                            // Draw with gradient for pressed state
                            val gradientColors = listOf(
                                keyColor.copy(alpha = 0.8f),
                                keyColor.copy(alpha = 0.6f)
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(gradientColors),
                                topLeft = Offset(x, keyboardPadding),
                                size = Size(width, whiteKeyHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                    }
                    
                    // Draw white key border
                    drawRoundRect(
                        color = Color(0xFFD0D0D0),
                        topLeft = Offset(x, keyboardPadding),
                        size = Size(width, whiteKeyHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        style = Stroke(width = 1f)
                    )
                    
                    // Draw shadow effect on the right edge of white keys (between keys)
                    // This creates depth by making keys appear slightly raised
                    val whiteKeys = (pianoConfig.minNote..pianoConfig.maxNote).filter { isWhiteKey(it) }
                    val isLastWhiteKey = note == whiteKeys.maxOrNull()
                    if (!isLastWhiteKey) {
                        val shadowWidth = 2.5f
                        val shadowX = x + width
                        // Draw a subtle shadow gradient on the right edge
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Black.copy(alpha = 0.2f)
                                ),
                                startX = shadowX - shadowWidth,
                                endX = shadowX + shadowWidth
                            ),
                            topLeft = Offset(shadowX - shadowWidth, keyboardPadding),
                            size = Size(shadowWidth * 2, whiteKeyHeight)
                        )
                    }
                    
                    // Draw key name if enabled
                    if (showKeyNames) {
                        val keyName = getNoteNameForMidiNote(note)
                        val textStyle = TextStyle(
                            fontSize = 10.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                        val textLayoutResult = textMeasurer.measure(keyName, textStyle)
                        val textX = x + (width - textLayoutResult.size.width) / 2f
                        val textY = keyboardPadding + whiteKeyHeight - with(density) { 12.dp.toPx() }
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = keyName,
                            style = textStyle,
                            topLeft = Offset(textX, textY)
                        )
                    }
                }
            }
            
            // Draw black keys
            (pianoConfig.minNote..pianoConfig.maxNote).forEach { note ->
                if (!isWhiteKey(note)) {
                    val xPos = calculateNotePosition(note, pianoConfig.minNote, keyWidthPx, true)
                    val x = keyboardPadding + xPos - blackKeyWidthPx / 2f
                    
                    val state = keyStates[note] ?: KeyState.NORMAL
                    var keyColor = when (state) {
                        KeyState.PRESSED -> surfaceVariantColor
                        KeyState.ACTIVE_PLAYING -> {
                            val isLeftHand = activePlayingNotes[note] == true
                            if (isLeftHand) {
                                leftHandColor
                            } else {
                                rightHandColor
                            }
                        }
                        KeyState.NORMAL -> Color(0xFF1A1A1A)
                    }
                    
                    // Apply beautiful color transformation for active black keys (same as falling notes)
                    if (state == KeyState.ACTIVE_PLAYING) {
                        val isLeftHand = activePlayingNotes[note] == true
                        // Blend with a rich complementary color for elegance
                        // For blue notes: blend with deep purple
                        // For pink/rose notes: blend with rich magenta
                        val complementaryColor = if (!isLeftHand) {
                            // Right hand (pink/rose) -> blend with rich magenta
                            Color(0xFF9C27B0) // Vibrant magenta
                        } else {
                            // Left hand (blue) -> blend with deep indigo
                            Color(0xFF5E35B1) // Deep indigo
                        }
                        
                        val blendFactor = 0.5f // 50% blend for rich, vibrant result
                        
                        keyColor = Color(
                            red = keyColor.red * (1f - blendFactor) + complementaryColor.red * blendFactor,
                            green = keyColor.green * (1f - blendFactor) + complementaryColor.green * blendFactor,
                            blue = keyColor.blue * (1f - blendFactor) + complementaryColor.blue * blendFactor,
                            alpha = keyColor.alpha
                        )
                    }
                    
                    // Draw black key with gradient or solid color
                    when (state) {
                        KeyState.ACTIVE_PLAYING -> {
                            // Draw active playing keys with solid color for better visibility
                            drawRoundRect(
                                color = keyColor,
                                topLeft = Offset(x, keyboardPadding),
                                size = Size(blackKeyWidthPx, blackKeyHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                        else -> {
                            // Draw with gradient for other states
                            val gradientColors = when (state) {
                                KeyState.PRESSED -> listOf(
                                    keyColor.copy(alpha = 0.8f),
                                    keyColor.copy(alpha = 0.6f)
                                )
                                else -> listOf(
                                    Color(0xFF1A1A1A),
                                    Color(0xFF0A0A0A)
                                )
                            }
                            drawRoundRect(
                                brush = Brush.verticalGradient(gradientColors),
                                topLeft = Offset(x, keyboardPadding),
                                size = Size(blackKeyWidthPx, blackKeyHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                    }
                    
                    // Draw black key border
                    drawRoundRect(
                        color = Color(0xFF2A2A2A),
                        topLeft = Offset(x, keyboardPadding),
                        size = Size(blackKeyWidthPx, blackKeyHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        style = Stroke(width = 1f)
                    )
                    
                    // Draw key name if enabled
                    if (showKeyNames) {
                        val keyName = getNoteNameForMidiNote(note)
                        val textStyle = TextStyle(
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                        val textLayoutResult = textMeasurer.measure(keyName, textStyle)
                        val textX = x + (blackKeyWidthPx - textLayoutResult.size.width) / 2f
                        val textY = keyboardPadding + blackKeyHeight - with(density) { 8.dp.toPx() }
                        
                        drawText(
                            textMeasurer = textMeasurer,
                            text = keyName,
                            style = textStyle,
                            topLeft = Offset(textX, textY)
                        )
                    }
                }
            }
        }
    }
}

private enum class KeyState {
    NORMAL,
    PRESSED,
    ACTIVE_PLAYING
}

