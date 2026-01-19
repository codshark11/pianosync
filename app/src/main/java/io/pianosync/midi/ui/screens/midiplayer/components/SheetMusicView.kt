package io.pianosync.midi.ui.screens.midiplayer.components

import android.app.Activity
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.pianosync.midi.data.model.MidiFile
import io.pianosync.midi.sheetmusic.MidiFile as SheetMusicMidiFile
import io.pianosync.midi.sheetmusic.MidiOptions
import io.pianosync.midi.sheetmusic.SheetMusic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Compose wrapper for the SheetMusic SurfaceView
 */
@Composable
fun SheetMusicView(
    modifier: Modifier = Modifier,
    midiFile: MidiFile,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onSeekTo: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    var sheetMusicView: SheetMusic? by remember { mutableStateOf(null) }
    var sheetMusicMidiFile: SheetMusicMidiFile? by remember { mutableStateOf(null) }

    // Convert project MidiFile to sheetmusic MidiFile
    LaunchedEffect(midiFile) {
        sheetMusicMidiFile = MidiFileConverter.convertToSheetMusicMidiFile(context, midiFile)
    }

            // Initialize SheetMusic view when we have the converted MidiFile
            LaunchedEffect(sheetMusicMidiFile) {
                if (sheetMusicMidiFile != null) {
                    // Create default options
                    val options = MidiOptions(sheetMusicMidiFile!!)
                    options.showPiano = false // Don't show piano in sheet music view
                    options.scrollVert = false // Horizontal scrolling
                    options.useFullHeight = true // Use full height to eliminate gap
                    
                    // The SheetMusic view will be created in AndroidView
                }
            }

    // Track previous pulse time for shading
    var prevPulseTime by remember { mutableStateOf(-1) }
    
    // Track viewport and playline position for pagination
    var viewWidth by remember { mutableStateOf(0) }
    var lastPlaylineX by remember { mutableStateOf(-1) }
    var isAnimatingScroll by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Helper to get X position for pulse time
    // Uses proportional calculation based on total pulses and sheet width
    fun getXForPulseTime(sheet: SheetMusic, pulseTime: Int, sheetMusicMidiFile: SheetMusicMidiFile): Int {
        try {
            val sheetWidthField = SheetMusic::class.java.getDeclaredField("sheetwidth")
            sheetWidthField.isAccessible = true
            val zoomField = SheetMusic::class.java.getDeclaredField("zoom")
            zoomField.isAccessible = true
            
            val sheetWidth = sheetWidthField.getInt(sheet)
            val zoom = zoomField.getFloat(sheet)
            val totalPulses = sheetMusicMidiFile.getTotalPulses()
            
            if (totalPulses > 0 && sheetWidth > 0) {
                // Estimate X position based on proportion of pulses
                // Clamp pulseTime to valid range
                val clampedPulseTime = pulseTime.coerceIn(0, totalPulses)
                val proportion = clampedPulseTime.toFloat() / totalPulses
                val xPosition = (sheetWidth * zoom * proportion).toInt()
                return xPosition
            }
        } catch (e: Exception) {
            // Fallback: rough estimate
        }
        // Very rough fallback - this shouldn't be used in normal operation
        return (pulseTime * 2).coerceAtLeast(0)
    }
    
    // Update note shading with playline visibility tracking
    LaunchedEffect(currentTimeMs, isPlaying, sheetMusicView) {
        if (sheetMusicView != null && sheetMusicMidiFile != null) {
            val sheet = sheetMusicView!!
            
            // Get view width
            if (viewWidth == 0 && sheet.width > 0) {
                viewWidth = sheet.width
            }
            
            // Convert currentTimeMs to pulses for shading
            val timeSig = sheetMusicMidiFile!!.getTime()
            val pulsesPerMs = timeSig.getQuarter().toFloat() / (timeSig.getTempo() / 1000f)
            val currentPulses = (currentTimeMs * pulsesPerMs).toInt()
            
            if (isPlaying && viewWidth > 0) {
                // First, shade the notes without scrolling
                sheet.ShadeNotes(currentPulses, prevPulseTime, SheetMusic.DontScroll)
                
                try {
                    val scrollXField = SheetMusic::class.java.getDeclaredField("scrollX")
                    scrollXField.isAccessible = true
                    val sheetWidthField = SheetMusic::class.java.getDeclaredField("sheetwidth")
                    sheetWidthField.isAccessible = true
                    val zoomField = SheetMusic::class.java.getDeclaredField("zoom")
                    zoomField.isAccessible = true
                    
                    val scrollX = scrollXField.getInt(sheet)
                    val sheetWidth = sheetWidthField.getInt(sheet)
                    val zoom = zoomField.getFloat(sheet)
                    
                    // Get the actual X position of the playline in the sheet music coordinate system
                    val playlineX = getXForPulseTime(sheet, currentPulses, sheetMusicMidiFile!!)
                    
                    // Calculate playline position relative to the viewport
                    // playlineX is in sheet coordinates, scrollX is the left edge of viewport
                    val playlineInViewport = playlineX - scrollX
                    
                    // Get measure information to calculate when to scroll
                    val measureLength = timeSig.getMeasure() // pulses per measure
                    val totalPulses = sheetMusicMidiFile!!.getTotalPulses()
                    val scrollWidth = (sheetWidth * zoom).toInt()
                    
                    // Estimate average measure width in pixels
                    // Total measures = totalPulses / measureLength
                    // Average measure width = scrollWidth / totalMeasures
                    val totalMeasures = if (measureLength > 0) totalPulses / measureLength else 1
                    val avgMeasureWidth = if (totalMeasures > 0) scrollWidth.toFloat() / totalMeasures else viewWidth.toFloat()
                    
                    // Calculate how many measures are visible in the viewport
                    val visibleMeasures = (viewWidth / avgMeasureWidth).coerceAtLeast(2f)
                    
                    // Calculate the X position where the second-to-last measure ends
                    // We want to scroll when playline reaches the end of the second-to-last visible measure
                    val secondLastMeasureEndX = scrollX + (visibleMeasures - 1.5f) * avgMeasureWidth
                    val secondLastMeasureEndInViewport = secondLastMeasureEndX - scrollX
                    
                    // Define viewport boundaries: playline should stay between 10% and the second-to-last measure end
                    val leftMargin = (viewWidth * 0.1).toInt()  // 10% from left
                    // Use the second-to-last measure end as the right threshold, but ensure it's reasonable
                    val rightMargin = secondLastMeasureEndInViewport.toInt().coerceIn(
                        (viewWidth * 0.65).toInt(), 
                        (viewWidth * 0.75).toInt()
                    )
                    
                    // Calculate maximum scroll position
                    // SheetMusic uses: scrollX > scrollwidth - viewwidth/2 as max, so we use similar logic
                    val maxScrollX = (scrollWidth - viewWidth / 2).coerceAtLeast(0)
                    
                    var newScrollX = scrollX
                    var needsUpdate = false
                    
                    // Check if this is a section transition (large scroll distance)
                    val isSectionTransition = playlineInViewport >= rightMargin && 
                                             kotlin.math.abs(playlineX - lastPlaylineX) > avgMeasureWidth
                    
                    // If playline is too far left, scroll left to bring it into view
                    if (playlineInViewport < leftMargin) {
                        newScrollX = (playlineX - leftMargin).coerceAtLeast(0)
                        needsUpdate = true
                    }
                    // If playline reaches the end of second-to-last measure, scroll to show next section
                    else if (playlineInViewport >= rightMargin) {
                        // Calculate new scroll position to keep playline at leftMargin after scroll
                        // This positions the playline at the start of a measure in the new section
                        newScrollX = (playlineX - leftMargin).coerceAtMost(maxScrollX)
                        needsUpdate = true
                    }
                    // Also check if playline is beyond the viewport entirely
                    else if (playlineInViewport < 0) {
                        // Playline is off-screen to the left
                        newScrollX = (playlineX - leftMargin).coerceAtLeast(0)
                        needsUpdate = true
                    } else if (playlineInViewport >= viewWidth) {
                        // Playline is off-screen to the right - scroll to bring it back
                        newScrollX = (playlineX - leftMargin).coerceAtMost(maxScrollX)
                        needsUpdate = true
                    }
                    
                    // Update scroll position if needed
                    if (needsUpdate) {
                        // Ensure we don't scroll beyond bounds
                        newScrollX = newScrollX.coerceIn(0, maxScrollX)
                        
                        // Only update if there's a meaningful change (avoid micro-adjustments)
                        val scrollDistance = kotlin.math.abs(newScrollX - scrollX)
                        if (scrollDistance > 5) {
                            // If this is a section transition (large scroll), animate it smoothly
                            if (isSectionTransition && scrollDistance > viewWidth * 0.3 && !isAnimatingScroll) {
                                isAnimatingScroll = true
                                val startScrollX = scrollX
                                val targetScrollX = newScrollX
                                val animationDuration = 400L // 400ms for smooth visible animation
                                val steps = 20 // Number of animation steps
                                val stepDelay = animationDuration / steps
                                
                                coroutineScope.launch {
                                    for (step in 0..steps) {
                                        val progress = step.toFloat() / steps
                                        // Use ease-out curve for smooth deceleration
                                        val easedProgress = 1f - (1f - progress).pow(3f)
                                        val animatedScrollX = (startScrollX + (targetScrollX - startScrollX) * easedProgress).toInt()
                                        
                                        try {
                                            scrollXField.setInt(sheet, animatedScrollX.coerceIn(0, maxScrollX))
                                            sheet.invalidate()
                                        } catch (e: Exception) {
                                            break
                                        }
                                        
                                        if (step < steps) {
                                            delay(stepDelay)
                                        }
                                    }
                                    isAnimatingScroll = false
                                    lastPlaylineX = playlineX
                                }
                            } else if (!isAnimatingScroll) {
                                // Small adjustments: update immediately
                                scrollXField.setInt(sheet, newScrollX)
                                lastPlaylineX = playlineX
                                
                                // Force redraw
                                sheet.invalidate()
                                sheet.post {
                                    sheet.requestLayout()
                                }
                            }
                        } else {
                            lastPlaylineX = playlineX
                        }
                    } else {
                        lastPlaylineX = playlineX
                    }
                } catch (e: Exception) {
                    // If reflection fails, fall back to regular scrolling
                    sheet.ShadeNotes(currentPulses, prevPulseTime, SheetMusic.GradualScroll)
                }
            } else if (!isPlaying) {
                // When paused or seeking, only update the playline position
                // Do NOT auto-scroll the sheet music - let user see where they clicked
                // The sheet music will only scroll during playback when playline reaches the edge
                sheet.ShadeNotes(currentPulses, prevPulseTime, SheetMusic.DontScroll)
                lastPlaylineX = getXForPulseTime(sheet, currentPulses, sheetMusicMidiFile!!)
            }
            
            prevPulseTime = currentPulses
        }
    }

    AndroidView(
        factory = { ctx ->
            val frameLayout = FrameLayout(ctx)
            frameLayout.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            if (sheetMusicMidiFile != null) {
                val options = MidiOptions(sheetMusicMidiFile!!)
                options.showPiano = false
                options.scrollVert = false
                options.useFullHeight = true // Use full height to eliminate gap between playline and piano
                
                // Get Activity from context (in Compose, context is typically Activity)
                val activity = when {
                    ctx is Activity -> ctx
                    ctx is android.content.ContextWrapper -> {
                        var current: android.content.Context? = ctx
                        while (current is android.content.ContextWrapper && current !is Activity) {
                            current = current.baseContext
                        }
                        current as? Activity
                    }
                    else -> null
                }
                
                if (activity != null) {
                    val sheet = SheetMusic(activity)
                    sheet.init(sheetMusicMidiFile!!, options)
                    sheetMusicView = sheet
                    
                    // Set layout parameters to fill the container
                    val layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    sheet.layoutParams = layoutParams
                    
                    // Set up tap detection for seeking
                    if (onSeekTo != null && sheetMusicMidiFile != null) {
                        val timeSig = sheetMusicMidiFile!!.getTime()
                        val pulsesPerMs = timeSig.getQuarter().toFloat() / (timeSig.getTempo() / 1000f)
                        
                        // Use reflection to access private scrollX and scrollY fields
                        val scrollXField = SheetMusic::class.java.getDeclaredField("scrollX")
                        scrollXField.isAccessible = true
                        val scrollYField = SheetMusic::class.java.getDeclaredField("scrollY")
                        scrollYField.isAccessible = true
                        
                        var downX = 0f
                        var downY = 0f
                        var downTime = 0L
                        
                        sheet.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    downTime = System.currentTimeMillis()
                                }
                                MotionEvent.ACTION_UP -> {
                                    val upTime = System.currentTimeMillis()
                                    val deltaX = kotlin.math.abs(event.x - downX)
                                    val deltaY = kotlin.math.abs(event.y - downY)
                                    val timeDelta = upTime - downTime
                                    
                                    // Detect tap: less than 500ms and movement less than 20 pixels
                                    if (timeDelta < 500 && deltaX < 20 && deltaY < 20) {
                                        try {
                                            // Get scroll position using reflection
                                            val scrollX = scrollXField.getInt(sheet)
                                            val scrollY = scrollYField.getInt(sheet)
                                            
                                            // Get the pulse time for the tapped point
                                            // Need to account for scroll position (same as scrollTapped does)
                                            val point = Point(
                                                scrollX + event.x.toInt(),
                                                scrollY + event.y.toInt()
                                            )
                                            val pulseTime = sheet.PulseTimeForPoint(point)
                                            
                                            if (pulseTime >= 0) {
                                                // Convert pulse time to milliseconds
                                                val timeMs = (pulseTime / pulsesPerMs).toLong()
                                                onSeekTo(timeMs)
                                            }
                                        } catch (e: Exception) {
                                            // If reflection fails, fall back to just using event coordinates
                                            // (this might not work correctly if scrolled, but better than crashing)
                                            val point = Point(event.x.toInt(), event.y.toInt())
                                            val pulseTime = sheet.PulseTimeForPoint(point)
                                            if (pulseTime >= 0) {
                                                val timeMs = (pulseTime / pulsesPerMs).toLong()
                                                onSeekTo(timeMs)
                                            }
                                        }
                                    }
                                }
                            }
                            // Return false to let the event continue to onTouchEvent for scrolling
                            false
                        }
                    }
                    
                    frameLayout.addView(sheet)
                    
                    // Add a global layout listener to recalculate zoom when size changes
                    var lastWidth = 0
                    var lastHeight = 0
                    val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val currentWidth = sheet.width
                            val currentHeight = sheet.height
                            if (currentWidth > 0 && currentHeight > 0 && 
                                (currentWidth != lastWidth || currentHeight != lastHeight)) {
                                lastWidth = currentWidth
                                lastHeight = currentHeight
                                sheet.ReCalculateZoom()
                            }
                        }
                    }
                    sheet.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                    
                    // Post to ensure view is measured before recalculating zoom
                    sheet.post {
                        sheet.ReCalculateZoom()
                    }
                }
            }
            
            frameLayout
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Update view if needed
            if (sheetMusicMidiFile != null && sheetMusicView == null) {
                val options = MidiOptions(sheetMusicMidiFile!!)
                options.showPiano = false
                options.scrollVert = false
                options.useFullHeight = true // Use full height to eliminate gap between playline and piano
                
                // Get Activity from context
                val ctx = view.context
                val activity = when {
                    ctx is Activity -> ctx
                    ctx is android.content.ContextWrapper -> {
                        var current: android.content.Context? = ctx
                        while (current is android.content.ContextWrapper && current !is Activity) {
                            current = current.baseContext
                        }
                        current as? Activity
                    }
                    else -> null
                }
                
                if (activity != null) {
                    val sheet = SheetMusic(activity)
                    sheet.init(sheetMusicMidiFile!!, options)
                    sheetMusicView = sheet
                    
                    // Set layout parameters to fill the container
                    val layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    sheet.layoutParams = layoutParams
                    
                    // Set up tap detection for seeking
                    if (onSeekTo != null && sheetMusicMidiFile != null) {
                        val timeSig = sheetMusicMidiFile!!.getTime()
                        val pulsesPerMs = timeSig.getQuarter().toFloat() / (timeSig.getTempo() / 1000f)
                        
                        // Use reflection to access private scrollX and scrollY fields
                        val scrollXField = SheetMusic::class.java.getDeclaredField("scrollX")
                        scrollXField.isAccessible = true
                        val scrollYField = SheetMusic::class.java.getDeclaredField("scrollY")
                        scrollYField.isAccessible = true
                        
                        var downX = 0f
                        var downY = 0f
                        var downTime = 0L
                        
                        sheet.setOnTouchListener { view, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    downTime = System.currentTimeMillis()
                                }
                                MotionEvent.ACTION_UP -> {
                                    val upTime = System.currentTimeMillis()
                                    val deltaX = kotlin.math.abs(event.x - downX)
                                    val deltaY = kotlin.math.abs(event.y - downY)
                                    val timeDelta = upTime - downTime
                                    
                                    // Detect tap: less than 500ms and movement less than 20 pixels
                                    if (timeDelta < 500 && deltaX < 20 && deltaY < 20) {
                                        try {
                                            // Get scroll position using reflection
                                            val scrollX = scrollXField.getInt(sheet)
                                            val scrollY = scrollYField.getInt(sheet)
                                            
                                            // Get the pulse time for the tapped point
                                            // Need to account for scroll position (same as scrollTapped does)
                                            val point = Point(
                                                scrollX + event.x.toInt(),
                                                scrollY + event.y.toInt()
                                            )
                                            val pulseTime = sheet.PulseTimeForPoint(point)
                                            
                                            if (pulseTime >= 0) {
                                                // Convert pulse time to milliseconds
                                                val timeMs = (pulseTime / pulsesPerMs).toLong()
                                                onSeekTo(timeMs)
                                            }
                                        } catch (e: Exception) {
                                            // If reflection fails, fall back to just using event coordinates
                                            // (this might not work correctly if scrolled, but better than crashing)
                                            val point = Point(event.x.toInt(), event.y.toInt())
                                            val pulseTime = sheet.PulseTimeForPoint(point)
                                            if (pulseTime >= 0) {
                                                val timeMs = (pulseTime / pulsesPerMs).toLong()
                                                onSeekTo(timeMs)
                                            }
                                        }
                                    }
                                }
                            }
                            // Return false to let the event continue to onTouchEvent for scrolling
                            false
                        }
                    }
                    
                    view.removeAllViews()
                    view.addView(sheet)
                    
                    // Add a global layout listener to recalculate zoom when size changes
                    var lastWidth = 0
                    var lastHeight = 0
                    val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val currentWidth = sheet.width
                            val currentHeight = sheet.height
                            if (currentWidth > 0 && currentHeight > 0 && 
                                (currentWidth != lastWidth || currentHeight != lastHeight)) {
                                lastWidth = currentWidth
                                lastHeight = currentHeight
                                sheet.ReCalculateZoom()
                            }
                        }
                    }
                    sheet.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                    
                    // Post to ensure view is measured before recalculating zoom
                    sheet.post {
                        sheet.ReCalculateZoom()
                    }
                }
            }
        }
    )
}
