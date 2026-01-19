package io.pianosync.midi.ui.screens.midiplayer.components

import android.app.Activity
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

/**
 * Compose wrapper for the SheetMusic SurfaceView
 */
@Composable
fun SheetMusicView(
    modifier: Modifier = Modifier,
    midiFile: MidiFile,
    currentTimeMs: Long,
    isPlaying: Boolean
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
    
    // Update note shading during playback
    LaunchedEffect(currentTimeMs, isPlaying, sheetMusicView) {
        if (isPlaying && sheetMusicView != null && sheetMusicMidiFile != null) {
            // Convert currentTimeMs to pulses for shading
            // This is a simplified conversion - you may need to adjust based on tempo
            val timeSig = sheetMusicMidiFile!!.getTime()
            val pulsesPerMs = timeSig.getQuarter().toFloat() / (timeSig.getTempo() / 1000f)
            val currentPulses = (currentTimeMs * pulsesPerMs).toInt()
            
            // Use DontScroll (3) to prevent automatic scrolling, or GradualScroll (2) for smooth scrolling
            sheetMusicView?.ShadeNotes(currentPulses, prevPulseTime, SheetMusic.GradualScroll)
            prevPulseTime = currentPulses
        } else if (!isPlaying) {
            // Reset previous time when playback stops
            prevPulseTime = -1
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
