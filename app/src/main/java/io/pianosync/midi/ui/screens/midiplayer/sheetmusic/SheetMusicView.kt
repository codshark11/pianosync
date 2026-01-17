package io.pianosync.midi.ui.screens.midiplayer.sheetmusic

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.pianosync.midi.ui.screens.player.MidiNote
import io.pianosync.midi.data.parser.midi.TimeSignature
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.ClefSymbol
import io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.TimeSigSymbol

/**
 * Main composable for displaying sheet music view
 */
@Composable
fun SheetMusicView(
    modifier: Modifier = Modifier,
    notes: List<MidiNote>,
    currentTimeMs: Long,
    isPlaying: Boolean,
    bpm: Int,
    isPreLoading: Boolean,
    timeSignature: TimeSignature? = null,
    showMeasureNumbers: Boolean = true
) {
    val context = LocalContext.current
    
    // Load images on first composition
    LaunchedEffect(Unit) {
        ClefSymbol.loadImages(context)
        TimeSigSymbol.loadImages(context)
    }
    
    // Create default time signature if not provided (4/4 time)
    val defaultTimeSignature = remember {
        TimeSignature(4, 4, 480, 500000) // 4/4, 480 ticks per quarter, 120 BPM
    }
    val ts = timeSignature ?: defaultTimeSignature
    
    // Separate notes into treble and bass ranges
    val (trebleNotes, bassNotes) = remember(notes) {
        if (notes.isEmpty()) {
            Pair(emptyList(), emptyList())
        } else {
            val middleC = 60
            val treble = notes.filter { it.note >= middleC }
            val bass = notes.filter { it.note < middleC }
            Pair(treble, bass)
        }
    }
    
    // Convert notes to symbols for each staff
    val trebleSymbols = remember(trebleNotes, ts) {
        if (trebleNotes.isNotEmpty()) {
            SheetMusicConverter.convertToSymbols(trebleNotes, ts, Clef.Treble)
        } else {
            emptyList()
        }
    }
    
    val bassSymbols = remember(bassNotes, ts) {
        if (bassNotes.isNotEmpty()) {
            SheetMusicConverter.convertToSymbols(bassNotes, ts, Clef.Bass)
        } else {
            emptyList()
        }
    }
    
    // Align symbols across staffs so measures align vertically
    val alignedSymbols = remember(trebleSymbols, bassSymbols, showMeasureNumbers) {
        val staffSymbols = mutableListOf<List<io.pianosync.midi.ui.screens.midiplayer.sheetmusic.symbols.MusicSymbol>>()
        if (trebleSymbols.isNotEmpty()) {
            staffSymbols.add(trebleSymbols)
        } else {
            staffSymbols.add(emptyList())
        }
        if (bassSymbols.isNotEmpty()) {
            staffSymbols.add(bassSymbols)
        } else {
            staffSymbols.add(emptyList())
        }

        SheetMusicConverter.alignSymbols(staffSymbols, showMeasures = showMeasureNumbers)
    }
    
    // Create staffs from aligned symbols. Show measure numbers only on the first staff.
    val staffs = remember(alignedSymbols, ts, showMeasureNumbers) {
        val staffList = mutableListOf<Staff>()
        if (trebleSymbols.isNotEmpty() && alignedSymbols[0].isNotEmpty()) {
            staffList.add(Staff(alignedSymbols[0], Clef.Treble, ts, showMeasureNumbers = showMeasureNumbers))
        }
        if (bassSymbols.isNotEmpty() && alignedSymbols.size > 1 && alignedSymbols[1].isNotEmpty()) {
            staffList.add(Staff(alignedSymbols[1], Clef.Bass, ts, showMeasureNumbers = showMeasureNumbers && trebleSymbols.isEmpty()))
        }
        staffList
    }
    
    Box(
        modifier = modifier
            .background(Color.White)
            .fillMaxSize()
    ) {
        if (isPreLoading || staffs.isEmpty()) {
            // Show loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isPreLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "No notes to display",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            // Render sheet music with scrolling support
            var scrollVertically by remember { mutableStateOf(false) }
            
            SheetMusicRenderer(
                modifier = Modifier.fillMaxSize(),
                staffs = staffs,
                currentTimeMs = currentTimeMs,
                color = Color.Black,
                scrollVertically = scrollVertically
            )
        }
    }
}
