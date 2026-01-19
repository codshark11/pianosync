package io.pianosync.midi.ui.screens.player

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.pianosync.midi.data.manager.MetronomeManager
import io.pianosync.midi.data.manager.MidiConnectionManager
import io.pianosync.midi.data.manager.MidiPlaybackManager
import io.pianosync.midi.data.model.MidiFile
import io.pianosync.midi.data.model.PerformanceRecord
import io.pianosync.midi.data.model.PlayedNote
import io.pianosync.midi.data.parser.MidiParser
import io.pianosync.midi.data.parser.midi.MidiFile as ParsedMidiFile
import io.pianosync.midi.ui.screens.midiplayer.components.TimeSignatureInfo
import io.pianosync.midi.data.repository.MidiFileRepository
import io.pianosync.midi.data.repository.MidiRecordingRepository
import io.pianosync.midi.data.repository.PerformanceRepository
import io.pianosync.midi.data.repository.SettingsRepository
import io.pianosync.midi.data.model.AppSettings
import io.pianosync.midi.data.model.DifficultyLevel
import io.pianosync.midi.ui.screens.player.components.LoopControl
import io.pianosync.midi.ui.screens.player.components.MetronomeVisualizer
import io.pianosync.midi.ui.screens.midiplayer.components.SheetMusicView
import io.pianosync.midi.ui.screens.midiplayer.components.*
import io.pianosync.midi.ui.theme.AccentRose
import io.pianosync.midi.ui.theme.RoyalPurple40
import io.pianosync.midi.ui.theme.WarmGold60
import io.pianosync.midi.ui.theme.WarmGold80
import io.pianosync.midi.ui.theme.highlightAccentColor
import io.pianosync.midi.ui.theme.leftHandNoteColor
import io.pianosync.midi.ui.theme.rightHandNoteColor
import io.pianosync.midi.ui.theme.successAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import io.pianosync.midi.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiPlayerScreen(
    midiFile: MidiFile,
    repository: MidiFileRepository,
    performanceRepository: PerformanceRepository,
    recordingRepository: MidiRecordingRepository, // This is passed from MainActivity
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

    var countdownSeconds by remember { mutableStateOf(3) }
    var showCountdown by remember { mutableStateOf(true) }
    var showSheetMusic by remember { mutableStateOf(false) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = 16 // Total horizontal padding
    val scope = rememberCoroutineScope()
    var currentBpm by remember { mutableStateOf(midiFile.currentBpm) }
    var showBpmDialog by remember { mutableStateOf(false) }
    val midiConnectionManager = remember { MidiConnectionManager.getInstance(context) }
    val pressedKeys by midiConnectionManager.pressedKeys.collectAsState()
    var isPreLoading by remember { mutableStateOf(true) }
    var midiNotes by remember { mutableStateOf<List<MidiNote>>(emptyList()) }
    var currentHandMode by remember { mutableStateOf(HandMode.BOTH_HANDS) }
    var pianoConfig by remember { mutableStateOf<PianoConfiguration?>(null) }
    val playbackManager = remember { MidiPlaybackManager(context, midiConnectionManager) }
    var songDurationMs by remember { mutableStateOf(0L) }
    val isPlaybackActive by playbackManager.isPlaying.collectAsState()
    val isLoopEnabled by playbackManager.isLoopEnabled.collectAsState()
    val loopStartMs by playbackManager.loopStartMs.collectAsState()
    val loopEndMs by playbackManager.loopEndMs.collectAsState()
    val currentTimeMs by playbackManager.currentTimeMs.collectAsState()
    val metronomeManager = remember { MetronomeManager(context) }
    var metronomeEnabled by remember { mutableStateOf(false) }
    val currentMetronomeBeat by metronomeManager.currentBeat.collectAsState()
    val isMetronomeRunning by metronomeManager.isRunning.collectAsState()
    var metronomeBeatCount by remember { mutableStateOf(4) }
    var sessionStartTimeMs by remember { mutableStateOf(0L) }
    var isSavingPerformance by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val recordingManager = remember { midiConnectionManager.getRecordingManager() }
    var wasManuallyPaused by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    val isConnected by midiConnectionManager.isConnected.collectAsState()

    val view = LocalView.current
    DisposableEffect(isPlaybackActive) {
        if (isPlaybackActive) {
            // Keep screen on when playback is active
            view.keepScreenOn = true
        } else {
            // Allow screen to turn off when playback is inactive
            view.keepScreenOn = false
        }
        onDispose {
            // Make sure to reset when leaving the screen
            view.keepScreenOn = false
        }
    }

    val activeNotes = remember(currentTimeMs, midiNotes) {
        midiNotes.filter { note ->
            note.startTime <= currentTimeMs &&
                    note.startTime + note.duration > currentTimeMs
        }
    }

    // Calculate upcoming notes at parent level so they're available in both views
    // Get original BPM and calculate speed ratio
    val originalBpm = playbackManager.getOriginalBpm()
    val speedRatio = remember(currentBpm, originalBpm) {
        if (originalBpm > 0) (currentBpm ?: 120).toFloat() / originalBpm.toFloat() else 1f
    }
    
    // Filter notes based on current hand mode
    val filteredNotes = remember(midiNotes, currentHandMode) {
        when (currentHandMode) {
            HandMode.LEFT_HAND_ONLY -> midiNotes.filter { it.isLeftHand }
            HandMode.RIGHT_HAND_ONLY -> midiNotes.filter { !it.isLeftHand }
            HandMode.BOTH_HANDS -> midiNotes
        }
    }
    
    // Map of actively playing notes (note number -> isLeftHand) for piano key highlighting
    // These are notes that should be held down based on their duration
    // Show active notes even when paused (based on currentTimeMs)
    val activePlayingNotes = remember(currentTimeMs, filteredNotes, isPreLoading, speedRatio, pianoConfig) {
        val config = pianoConfig
        if (isPreLoading || config == null) {
            emptyMap<Int, Boolean>()
        } else {
            filteredNotes.filter { note ->
                val notePlaybackTime = (note.startTime / speedRatio).toLong()
                val noteEndTime = notePlaybackTime + (note.duration / speedRatio).toLong()
                // Note is actively playing if current time is between start and end
                // This works both when playing and when paused
                currentTimeMs >= notePlaybackTime && currentTimeMs < noteEndTime &&
                        note.note in config.minNote..config.maxNote
            }.associate { it.note to it.isLeftHand }
        }
    }
    
    // Calculate upcoming notes (notes approaching the play line within 200ms)
    // Only show upcoming notes when actually playing (not when paused)
    val upcomingNotes = remember(currentTimeMs, filteredNotes, isPlaybackActive, isPreLoading, speedRatio, pianoConfig) {
        val config = pianoConfig // Store in local variable to avoid smart cast issue
        if (!isPlaybackActive || isPreLoading || config == null) {
            emptyMap<Int, Boolean>()
        } else {
            val upcomingTimeWindow = 200L // Show keys 200ms before they need to be pressed
            filteredNotes.filter { note ->
                val notePlaybackTime = (note.startTime / speedRatio).toLong()
                val timeDiff = notePlaybackTime - currentTimeMs
                // Note is upcoming if it's in the future but within the time window
                timeDiff > 0 && timeDiff <= upcomingTimeWindow &&
                        note.note in config.minNote..config.maxNote
            }.associate { it.note to it.isLeftHand }
        }
    }

    var showScoreDialog by remember { mutableStateOf(false) }
    var totalNotesPlayed by remember { mutableStateOf(0) }
    var totalNotesInSong by remember { mutableStateOf(0) }
    val correctlyPlayedNotes = remember { mutableStateOf<Set<Int>>(emptySet()) }
    val missedNotes = remember { mutableStateOf<Set<Int>>(emptySet()) }
    var hasStartedPlaying by remember { mutableStateOf(false) }
    var isNearEndOfSong by remember { mutableStateOf(false) }
    var endOfSongTimerStarted by remember { mutableStateOf(false) }
    var timeSignatureInfo by remember { mutableStateOf<TimeSignatureInfo?>(null) }

    // Track which notes have passed the play line
    val processedNotes = remember { mutableStateOf<Set<MidiNote>>(emptySet()) }

    // Calculate score as a derived state
    val score = remember {
        derivedStateOf {
            if (totalNotesInSong == 0) 0
            else (correctlyPlayedNotes.value.size.toFloat() / totalNotesInSong * 100).roundToInt()
        }
    }

    LaunchedEffect(midiNotes, currentHandMode) {
        // Update the note count whenever the hand mode changes
        when (currentHandMode) {
            HandMode.LEFT_HAND_ONLY -> totalNotesInSong = midiNotes.count { it.isLeftHand }
            HandMode.RIGHT_HAND_ONLY -> totalNotesInSong = midiNotes.count { !it.isLeftHand }
            HandMode.BOTH_HANDS -> totalNotesInSong = midiNotes.size
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                recordingDuration = recordingManager.getRecordingDuration()
                delay(100) // Update every 100ms
            }
        }
    }

    // This monitors the playback position but doesn't restart on each time update
    LaunchedEffect(Unit) {
        while (true) {
            if (isPlaybackActive && midiNotes.isNotEmpty() && !isNearEndOfSong && !endOfSongTimerStarted) {
                // Calculate the total estimated song duration
                val lastNoteTime = midiNotes.maxOf { it.startTime + it.duration }
                val speedRatio = (currentBpm ?: 120).toFloat() / (midiFile.originalBpm ?: 120).toFloat()
                val estimatedDuration = lastNoteTime / speedRatio

                if (currentTimeMs > estimatedDuration * 0.999) {
                    isNearEndOfSong = true
                    Log.d("MidiPlayer", "Near end of song detected at $currentTimeMs / $estimatedDuration")
                }

            }
            delay(100) // Check every 100ms instead of every frame
        }
    }

    LaunchedEffect(isNearEndOfSong) {
        if (isNearEndOfSong && !endOfSongTimerStarted) {
            endOfSongTimerStarted = true
            Log.d("MidiPlayer", "Starting end of song timer")

            // Wait for 2 seconds
            delay(2000)

            // If we're still near the end, show the dialog and stop recording
            if (isPlaybackActive) {
                Log.d("MidiPlayer", "Song completion timer finished, showing score dialog")

                // Stop recording automatically when song ends
                if (isRecording) {
                    recordingManager.stopRecording()
                    isRecording = false
                    Log.d("MidiPlayer", "Stopped recording automatically - song ended")
                }

                playbackManager.pausePlayback()
                showScoreDialog = true
            }

            // Reset tracking variables
            isNearEndOfSong = false
            endOfSongTimerStarted = false
        }
    }

    LaunchedEffect(isPlaybackActive) {
        if (!isPlaybackActive && hasStartedPlaying && !isPreLoading && !showScoreDialog) {
            // Only show score dialog if the song actually ended naturally, not if manually paused
            if (!wasManuallyPaused) {
                hasStartedPlaying = false

                // Stop recording when playback stops after significant progress
                if (isRecording) {
                    recordingManager.stopRecording()
                    isRecording = false
                    Log.d("MidiPlayer", "Stopped recording automatically - playback ended")
                }

                // Check if we've played a significant portion and song ended naturally
                val lastNoteTime = if (midiNotes.isNotEmpty()) {
                    midiNotes.maxOf { it.startTime + it.duration }
                } else 0L

                // Only show score if we're very close to the actual end (95% instead of 70%)
                // and this wasn't a manual pause
                if (currentTimeMs > lastNoteTime * 0.95) {
                    Log.d("MidiPlayer", "Playback stopped after significant progress, showing score")
                    showScoreDialog = true
                }
            }
            // Reset the manual pause flag when playback stops
            wasManuallyPaused = false
        } else if (isPlaybackActive && !hasStartedPlaying) {
            hasStartedPlaying = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Stop recording if active when leaving the screen
            if (isRecording) {
                recordingManager.stopRecording()
                Log.d("MidiPlayer", "Stopped recording - screen disposed")
            }

            playbackManager.cleanup()
            metronomeManager.cleanup()
        }
    }

    LaunchedEffect(currentBpm) {
        metronomeManager.updateBpm(currentBpm ?: 120)
    }

    LaunchedEffect(isPlaybackActive, metronomeEnabled, settings.metronomeVolume) {
        if (isPlaybackActive && metronomeEnabled) {
            metronomeManager.start(currentBpm ?: 120, metronomeBeatCount, volume = settings.metronomeVolume)
        } else if (!isPlaybackActive && isMetronomeRunning) {
            metronomeManager.stop()
        }
    }

    LaunchedEffect(midiFile, currentBpm) {
        try {
            val uri = Uri.parse(midiFile.path)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Parse MIDI file to get time signature
                val midiBytes = inputStream.readBytes()
                val parsedMidiFile = ParsedMidiFile(midiBytes, "")
                val timeSig = parsedMidiFile.time
                timeSignatureInfo = TimeSignatureInfo(
                    numerator = timeSig.numerator,
                    denominator = timeSig.denominator,
                    quarter = timeSig.quarter,
                    measure = timeSig.measure
                )
                
                // Always parse with the original BPM to get correct absolute times
                val originalBpm = midiFile.originalBpm ?: 120
                // Re-parse notes (we need to read the stream again)
                context.contentResolver.openInputStream(uri)?.use { notesInputStream ->
                    val notes = MidiParser.parseMidiNotes(notesInputStream, originalBpm)
                    midiNotes = notes

                    // Calculate song duration based on the last note end time
                    if (notes.isNotEmpty()) {
                    val lastNoteEndTime = notes.maxOf { it.startTime + it.duration }
                    songDurationMs = lastNoteEndTime
                    Log.d("MidiPlayer", "Song duration calculated: $songDurationMs ms")

                    // Set initial loop end to song duration if not already set via playbackManager
                    if (loopEndMs == 0L) {
                        playbackManager.setLoopPoints(0L, songDurationMs)
                    }

                    val minNote = notes.minOf { it.note }
                    val maxNote = notes.maxOf { it.note }

                    val paddedMin = (minNote - 2).coerceAtLeast(21)
                    val paddedMax = (maxNote + 2).coerceAtMost(108)

                    sessionStartTimeMs = System.currentTimeMillis()

                    // Calculate key width based on available screen width
                    // The keyboard Box structure:
                    //   - Outer container: fillMaxWidth() -> fills (screenWidth - horizontalPadding)
                    //   - Inner Box: width = keyWidth * whiteKeyCount + 8dp (to account for padding)
                    //   - Border: 2dp on each side (drawn on edge, doesn't change measured size)
                    //   - Padding: 4dp on each side (reduces Row's available space by 8dp)
                    // The Row inside needs space for: keyWidth * whiteKeyCount (one keyWidth per key)
                    // After padding, Row has: (keyWidth * whiteKeyCount + 8dp) - 8dp = keyWidth * whiteKeyCount ✓
                    // To fit within screen: keyWidth * whiteKeyCount + 8dp <= screenWidth - horizontalPadding
                    // Therefore: keyWidth = (screenWidth - horizontalPadding - 8dp) / whiteKeyCount
                    val whiteKeyCount = (paddedMin..paddedMax).count { isWhiteKey(it) }
                    val keyboardInternalPadding = 8f // 4dp on each side inside Box (reduces Row space)
                    // Border doesn't affect measured size, so we don't subtract it
                    val availableWidthForBox = screenWidth - horizontalPadding
                    val availableWidthForKeys = availableWidthForBox - keyboardInternalPadding
                    val calculatedKeyWidth = availableWidthForKeys.toFloat() / whiteKeyCount
                    val keyWidth = calculatedKeyWidth.coerceIn(20f, 60f)

                    pianoConfig = PianoConfiguration(
                        minNote = paddedMin,
                        maxNote = paddedMax,
                        keyWidth = keyWidth
                    )
                }

                while (countdownSeconds > 0) {
                    delay(1000)
                    countdownSeconds--
                }
                showCountdown = false

                delay(100)

                    playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                    isPreLoading = false
                }
            }
        } catch (e: Exception) {
            Log.e("MidiPlayer", "Error loading MIDI file", e)
        }
    }

    LaunchedEffect(isLoopEnabled, loopStartMs, loopEndMs) {
        playbackManager.toggleLoopMode(isLoopEnabled)
        playbackManager.setLoopPoints(loopStartMs, loopEndMs)
    }

    if (pianoConfig == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top app bar
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
            ) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                        title = {
                            Text(
                                text = midiFile.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            // Left side icons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            // Check if we have a recording to save
                                            if (isRecording || recordingManager.hasRecordedEvents()) {
                                                Log.d("MidiPlayer", "Saving recording before navigation back...")

                                                // Stop recording if still active
                                                if (isRecording) {
                                                    recordingManager.stopRecording()
                                                    isRecording = false
                                                    Log.d("MidiPlayer", "Stopped recording - navigation back")
                                                }

                                                // Create and save the recording (events are still available)
                                                val recording = recordingManager.createRecording(
                                                    originalMidiFilePath = midiFile.path,
                                                    originalMidiFileName = midiFile.name,
                                                    bpm = currentBpm ?: 120,
                                                    handMode = currentHandMode,
                                                    score = null // No score since we're leaving early
                                                )

                                                recording?.let { rec ->
                                                    Log.d("MidiPlayer", "Created recording with ${rec.recordedEvents.size} events for navigation back")
                                                    try {
                                                        recordingRepository.saveRecording(rec)
                                                        Log.d("MidiPlayer", "MIDI recording saved successfully on navigation back with ${rec.recordedEvents.size} events")

                                                        // Verify it was saved
                                                        val allRecordings = recordingRepository.allRecordings.first()
                                                        Log.d("MidiPlayer", "Total recordings in repository after navigation back: ${allRecordings.size}")

                                                    } catch (e: Exception) {
                                                        Log.e("MidiPlayer", "Failed to save recording on navigation back", e)
                                                    }
                                                } ?: Log.d("MidiPlayer", "No recording created - no events available")
                                            }

                                            playbackManager.cleanup()
                                            onBackPressed()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }

                                // Hand mode selector
                                Box {
                                    var handMenuExpanded by remember { mutableStateOf(false) }

                                    TextButton(
                                        onClick = {
                                            handMenuExpanded = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (currentHandMode) {
                                                HandMode.BOTH_HANDS -> Icons.Default.PanoramaHorizontal
                                                HandMode.LEFT_HAND_ONLY -> Icons.Default.SwipeLeft
                                                HandMode.RIGHT_HAND_ONLY -> Icons.Default.SwipeRight
                                            },
                                            contentDescription = "Hand Mode",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = when (currentHandMode) {
                                                HandMode.BOTH_HANDS -> stringResource(R.string.both)
                                                HandMode.LEFT_HAND_ONLY -> stringResource(R.string.left)
                                                HandMode.RIGHT_HAND_ONLY -> stringResource(R.string.right)
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = handMenuExpanded,
                                        onDismissRequest = { handMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.both_hands)) },
                                            onClick = {
                                                currentHandMode = HandMode.BOTH_HANDS
                                                handMenuExpanded = false
                                                // Reset and restart playback with the new hand mode
                                                playbackManager.resetPlayback()
                                                playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.PanoramaHorizontal,
                                                    contentDescription = stringResource(R.string.both_hands)
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.left_hand)) },
                                            onClick = {
                                                currentHandMode = HandMode.LEFT_HAND_ONLY
                                                handMenuExpanded = false
                                                playbackManager.resetPlayback()
                                                playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.SwipeLeft,
                                                    contentDescription = stringResource(R.string.left_hand)
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.right_hand)) },
                                            onClick = {
                                                currentHandMode = HandMode.RIGHT_HAND_ONLY
                                                handMenuExpanded = false
                                                playbackManager.resetPlayback()
                                                playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.SwipeRight,
                                                    contentDescription = stringResource(R.string.right_hand)
                                                )
                                            }
                                        )
                                    }
                                }

                                // BPM button
                                TextButton(
                                    onClick = {
                                        showBpmDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = "BPM",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "${currentBpm ?: 0}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Surface(
                                    color = when(settings.difficultyLevel) {
                                        DifficultyLevel.EASY -> successAccentColor()
                                        DifficultyLevel.MEDIUM -> WarmGold60
                                        DifficultyLevel.HARD -> AccentRose
                                        DifficultyLevel.EXPERT -> RoyalPurple40
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = settings.difficultyLevel.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            // Right side icons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(48.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isRecording) {
                                            recordingManager.stopRecording()
                                            isRecording = false
                                            Log.d("MidiPlayer", "Stopped recording manually")
                                        } else {
                                            if (isConnected) {
                                                recordingManager.startRecording()
                                                isRecording = true
                                                Log.d("MidiPlayer", "Started recording manually")
                                            }
                                        }
                                    },
                                    enabled = isConnected
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (isRecording) stringResource(R.string.stop_recording) else stringResource(R.string.start_recording),
                                        tint = when {
                                            !isConnected -> Color.Gray
                                            isRecording -> Color.Red
                                            else -> Color.White
                                        }
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        metronomeEnabled = !metronomeEnabled

                                        if (metronomeEnabled) {
                                            if (isPlaybackActive) {
                                                metronomeManager.start(currentBpm ?: 120, metronomeBeatCount, volume = settings.metronomeVolume)
                                            }
                                        } else {
                                            metronomeManager.stop()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = stringResource(R.string.metronome),
                                        modifier = Modifier.size(20.dp),
                                        tint = if (metronomeEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Sheet Music / Falling Notes toggle button
                                IconButton(
                                    onClick = {
                                        showSheetMusic = !showSheetMusic
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (showSheetMusic) Icons.Default.MusicNote else Icons.Default.Piano,
                                        contentDescription = if (showSheetMusic) "Switch to falling notes" else "Switch to sheet music",
                                        tint = if (showSheetMusic) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Loop controls
                                // Set loop start button (A)
                                IconButton(
                                    onClick = {
                                        Log.d("MidiPlayer", "Setting loop start to current time: $currentTimeMs")
                                        val endPoint = if (loopEndMs <= currentTimeMs) songDurationMs else loopEndMs
                                        playbackManager.setLoopPoints(currentTimeMs, endPoint)
                                        playbackManager.toggleLoopMode(true)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        text = "A",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLoopEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Loop toggle button
                                IconButton(
                                    onClick = {
                                        playbackManager.toggleLoopMode(!isLoopEnabled)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLoopEnabled) Icons.Default.Loop else Icons.Default.Piano,
                                        contentDescription = "Toggle Loop",
                                        tint = if (isLoopEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Set loop end button (B)
                                IconButton(
                                    onClick = {
                                        // Only set end if it's after start
                                        if (currentTimeMs > loopStartMs) {
                                            Log.d("MidiPlayer", "Setting loop end to current time: $currentTimeMs")
                                            playbackManager.setLoopPoints(loopStartMs, currentTimeMs)
                                            playbackManager.toggleLoopMode(true)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        text = "B",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLoopEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                // Restart button
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            // Save recording before restarting if there's one
                                            if (isRecording || recordingManager.getRecordingDuration() > 0) {
                                                Log.d("MidiPlayer", "Saving recording before restart...")

                                                // Stop recording if still active
                                                if (isRecording) {
                                                    recordingManager.stopRecording()
                                                    isRecording = false
                                                    Log.d("MidiPlayer", "Stopped recording - restart")
                                                }

                                                // Create and save the recording
                                                val recording = recordingManager.createRecording(
                                                    originalMidiFilePath = midiFile.path,
                                                    originalMidiFileName = midiFile.name,
                                                    bpm = currentBpm ?: 120,
                                                    handMode = currentHandMode,
                                                    score = null // No score since we're restarting
                                                )

                                                recording?.let { rec ->
                                                    Log.d("MidiPlayer", "Created recording with ${rec.recordedEvents.size} events for restart")
                                                    try {
                                                        recordingRepository.saveRecording(rec)
                                                        Log.d("MidiPlayer", "MIDI recording saved successfully on restart with ${rec.recordedEvents.size} events")
                                                    } catch (e: Exception) {
                                                        Log.e("MidiPlayer", "Failed to save recording on restart", e)
                                                    }
                                                } ?: Log.d("MidiPlayer", "No recording to save on restart")
                                            }

                                            playbackManager.resetPlayback()
                                            playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Restart"
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isPlaybackActive) {
                                            wasManuallyPaused = true
                                            playbackManager.pausePlayback()
                                        } else {
                                            wasManuallyPaused = false
                                            if (currentTimeMs > 0) {
                                                playbackManager.resumePlayback(midiFile)
                                            } else {
                                                playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaybackActive)
                                            Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaybackActive)
                                            stringResource(R.string.pause) else stringResource(R.string.play)
                                    )
                                }
                            }
                        }
                    )
            }

            // Loop control - positioned above sheet music to avoid overlap
            LoopControl(
                modifier = Modifier
                    .fillMaxWidth(),
                isLoopEnabled = isLoopEnabled,
                loopStartMs = loopStartMs,
                loopEndMs = loopEndMs,
                songDurationMs = songDurationMs,
                currentTimeMs = currentTimeMs,
                onLoopToggled = { enabled ->
                    playbackManager.toggleLoopMode(enabled)
                },
                onSetLoopStart = {
                    Log.d("MidiPlayer", "Setting loop start to current time: $currentTimeMs")
                    val endPoint = if (loopEndMs <= currentTimeMs) songDurationMs else loopEndMs
                    playbackManager.setLoopPoints(currentTimeMs, endPoint)
                    playbackManager.toggleLoopMode(true)
                },
                onSetLoopEnd = {
                    // Only set end if it's after start
                    if (currentTimeMs > loopStartMs) {
                        Log.d("MidiPlayer", "Setting loop end to current time: $currentTimeMs")
                        playbackManager.setLoopPoints(loopStartMs, currentTimeMs)
                        playbackManager.toggleLoopMode(true)
                    }
                },
                onSeekTo = { position ->
                    Log.d("MidiPlayer", "Seeking to position: $position")
                    playbackManager.seekTo(position)
                }
            )

            // Sheet music or note fall visualizer container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (showSheetMusic) {
                    SheetMusicView(
                        modifier = Modifier.fillMaxSize(),
                        midiFile = midiFile,
                        currentTimeMs = currentTimeMs,
                        isPlaying = isPlaybackActive,
                        onSeekTo = { position ->
                            Log.d("MidiPlayer", "Seeking to position from sheet music: $position")
                            playbackManager.seekTo(position)
                        }
                    )
                } else {
                    NoteFallVisualizer(
                        modifier = Modifier.fillMaxSize(),
                        notes = when (currentHandMode) {
                            HandMode.LEFT_HAND_ONLY -> midiNotes.filter { it.isLeftHand }
                            HandMode.RIGHT_HAND_ONLY -> midiNotes.filter { !it.isLeftHand }
                            HandMode.BOTH_HANDS -> midiNotes
                        },
                        currentTimeMs = currentTimeMs,
                        isPlaying = isPlaybackActive,
                        bpm = currentBpm ?: 120,
                        pianoConfig = pianoConfig!!,
                        isPreLoading = isPreLoading,
                        playbackManager = playbackManager,
                        correctlyPlayedNotes = correctlyPlayedNotes,
                        pressedKeys = pressedKeys,
                        settings = settings, // Pass settings to visualizer
                        onNoteProcessed = {
                            // Make sure we're tracking processed notes
                            totalNotesPlayed++
                        },
                        timeSignature = timeSignatureInfo
                    )
                }

                if (showScoreDialog) {
                    val sessionDurationMs = System.currentTimeMillis() - sessionStartTimeMs
                    Dialog(
                        onDismissRequest = {
                            if (!isSavingPerformance) {
                                scope.launch {
                                    isSavingPerformance = true

                                    try {
                                        // Stop recording if still active (backup safety)
                                        if (isRecording) {
                                            recordingManager.stopRecording()
                                            isRecording = false
                                            Log.d("MidiPlayer", "Stopped recording in dialog dismissal")
                                        }

                                        // Save performance data
                                        val performanceRecord = PerformanceRecord(
                                            midiFilePath = midiFile.path,
                                            midiFileName = midiFile.name,
                                            timestamp = System.currentTimeMillis(),
                                            score = score.value,
                                            notesHit = correctlyPlayedNotes.value.size,
                                            notesMissed = totalNotesInSong - correctlyPlayedNotes.value.size,
                                            totalNotes = totalNotesInSong,
                                            bpm = currentBpm ?: 120,
                                            handMode = currentHandMode,
                                            durationMs = sessionDurationMs,
                                            notesPlayed = processedNotes.value.mapIndexed { index, note ->
                                                PlayedNote(
                                                    noteValue = note.note,
                                                    wasCorrect = note.note in correctlyPlayedNotes.value,
                                                    timestamp = System.currentTimeMillis() - (processedNotes.value.size - index) * 100L,
                                                    isLeftHand = note.isLeftHand
                                                )
                                            }.take(200)
                                        )

                                        // Save performance record
                                        performanceRepository.savePerformanceRecord(performanceRecord)
                                        Log.d("MidiPlayer", "Performance record saved successfully")

                                        val recording = recordingManager.createRecording(
                                            originalMidiFilePath = midiFile.path,
                                            originalMidiFileName = midiFile.name,
                                            bpm = currentBpm ?: 120,
                                            handMode = currentHandMode,
                                            score = score.value
                                        )

                                        recording?.let { rec ->
                                            Log.d("MidiPlayer", "Created recording with ${rec.recordedEvents.size} events, duration: ${rec.durationMs}ms")
                                            Log.d("MidiPlayer", "Recording details:")
                                            Log.d("MidiPlayer", "  - MIDI file: ${rec.originalMidiFileName}")
                                            Log.d("MidiPlayer", "  - Path: ${rec.originalMidiFilePath}")
                                            Log.d("MidiPlayer", "  - BPM: ${rec.bpm}")
                                            Log.d("MidiPlayer", "  - Hand mode: ${rec.handMode}")
                                            Log.d("MidiPlayer", "  - Score: ${rec.score}")

                                            try {
                                                recordingRepository.saveRecording(rec)
                                                Log.d("MidiPlayer", "MIDI recording saved successfully with ${rec.recordedEvents.size} events")

                                                // Verify it was saved by checking the repository
                                                val allRecordings = recordingRepository.allRecordings.first()
                                                Log.d("MidiPlayer", "Total recordings in repository after save: ${allRecordings.size}")

                                            } catch (e: Exception) {
                                                Log.e("MidiPlayer", "Failed to save recording", e)
                                            }
                                        } ?: Log.d("MidiPlayer", "No recording to save - recording manager returned null")

                                    } catch (e: Exception) {
                                        Log.e("MidiPlayer", "Failed to save performance/recording", e)
                                    } finally {
                                        isSavingPerformance = false
                                        showScoreDialog = false
                                        isRecording = false  // Ensure recording is stopped

                                        // Reset all tracking variables
                                        correctlyPlayedNotes.value = emptySet()
                                        missedNotes.value = emptySet()
                                        processedNotes.value = emptySet()
                                        totalNotesPlayed = 0
                                        hasStartedPlaying = false
                                        isNearEndOfSong = false
                                        endOfSongTimerStarted = false
                                    }
                                }
                            }
                        }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.performance_results),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (recordingManager.getRecordingDuration() > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = stringResource(R.string.recording),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.recording_saved_format, formatRecordingTime(recordingManager.getRecordingDuration())),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Show loading indicator while saving
                                if (isSavingPerformance) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.saving_progress),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    // Existing score display content
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .padding(vertical = 4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                color = when(score.value) {
                                                    in 90..100 -> successAccentColor()
                                                    in 80..89 -> WarmGold60
                                                    in 70..79 -> WarmGold80
                                                    in 60..69 -> AccentRose.copy(alpha = 0.7f)
                                                    else -> MaterialTheme.colorScheme.error
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${score.value}%",
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        CompactStatisticItem(
                                            label = stringResource(R.string.notes_hit),
                                            value = "${correctlyPlayedNotes.value.size}",
                                            modifier = Modifier.weight(1f)
                                        )

                                        CompactStatisticItem(
                                            label = stringResource(R.string.notes_missed),
                                            value = "${missedNotes.value.size}",
                                            modifier = Modifier.weight(1f)
                                        )

                                        CompactStatisticItem(
                                            label = stringResource(R.string.total),
                                            value = "$totalNotesInSong",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Grade display
                                    val grade = when(score.value) {
                                        in 95..100 -> "A+"
                                        in 90..94 -> "A"
                                        in 85..89 -> "B+"
                                        in 80..84 -> "B"
                                        in 75..79 -> "C+"
                                        in 70..74 -> "C"
                                        in 60..69 -> "D"
                                        else -> "Keep practicing!"
                                    }

                                    Text(
                                        text = if (score.value >= 60) "Grade: $grade" else grade,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when(score.value) {
                                            in 90..100 -> Color(0xFF4CAF50)
                                            in 80..89 -> Color(0xFF8BC34A)
                                            in 70..79 -> Color(0xFFFFC107)
                                            in 60..69 -> Color(0xFFFF9800)
                                            else -> Color(0xFFF44336)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Action buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                scope.launch {
                                                    isSavingPerformance = true

                                                    try {
                                                        // Save the current performance
                                                        val performanceRecord = PerformanceRecord(
                                                            midiFilePath = midiFile.path,
                                                            midiFileName = midiFile.name,
                                                            timestamp = System.currentTimeMillis(),
                                                            score = score.value,
                                                            notesHit = correctlyPlayedNotes.value.size,
                                                            notesMissed = totalNotesInSong - correctlyPlayedNotes.value.size,
                                                            totalNotes = totalNotesInSong,
                                                            bpm = currentBpm ?: 120,
                                                            handMode = currentHandMode,
                                                            durationMs = sessionDurationMs,
                                                            notesPlayed = processedNotes.value.mapIndexed { index, note ->
                                                                PlayedNote(
                                                                    noteValue = note.note,
                                                                    wasCorrect = note.note in correctlyPlayedNotes.value,
                                                                    timestamp = System.currentTimeMillis() - (processedNotes.value.size - index) * 100L,
                                                                    isLeftHand = note.isLeftHand
                                                                )
                                                            }.take(200)
                                                        )

                                                        // Save performance record
                                                        performanceRepository.savePerformanceRecord(performanceRecord)
                                                        Log.d("MidiPlayer", "Performance record saved before retry")

                                                        // ALSO SAVE THE RECORDING (this was missing from retry too!)
                                                        Log.d("MidiPlayer", "Checking for recording to save before retry...")
                                                        val recording = recordingManager.createRecording(
                                                            originalMidiFilePath = midiFile.path,
                                                            originalMidiFileName = midiFile.name,
                                                            bpm = currentBpm ?: 120,
                                                            handMode = currentHandMode,
                                                            score = score.value
                                                        )

                                                        recording?.let { rec ->
                                                            Log.d("MidiPlayer", "Created recording with ${rec.recordedEvents.size} events for retry button")
                                                            try {
                                                                recordingRepository.saveRecording(rec)
                                                                Log.d("MidiPlayer", "MIDI recording saved successfully before retry with ${rec.recordedEvents.size} events")
                                                            } catch (e: Exception) {
                                                                Log.e("MidiPlayer", "Failed to save recording before retry", e)
                                                            }
                                                        } ?: Log.d("MidiPlayer", "No recording to save before retry")

                                                    } catch (e: Exception) {
                                                        Log.e("MidiPlayer", "Failed to save performance record before retry", e)
                                                    }

                                                    // Stop recording before restarting
                                                    if (isRecording) {
                                                        recordingManager.stopRecording()
                                                        isRecording = false
                                                        Log.d("MidiPlayer", "Stopped recording before retry")
                                                    }

                                                    isSavingPerformance = false
                                                    showScoreDialog = false

                                                    // Reset and restart
                                                    correctlyPlayedNotes.value = emptySet()
                                                    missedNotes.value = emptySet()
                                                    processedNotes.value = emptySet()
                                                    totalNotesPlayed = 0
                                                    hasStartedPlaying = false
                                                    isNearEndOfSong = false
                                                    endOfSongTimerStarted = false

                                                    // Restart playback
                                                    playbackManager.resetPlayback()
                                                    playbackManager.startPlayback(midiFile, currentBpm ?: 120, 0L, midiNotes, currentHandMode)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = stringResource(R.string.try_again),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.try_again),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        Button(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                scope.launch {
                                                    isSavingPerformance = true

                                                    try {
                                                        // Save the current performance before going back
                                                        val performanceRecord = PerformanceRecord(
                                                            midiFilePath = midiFile.path,
                                                            midiFileName = midiFile.name,
                                                            timestamp = System.currentTimeMillis(),
                                                            score = score.value,
                                                            notesHit = correctlyPlayedNotes.value.size,
                                                            notesMissed = totalNotesInSong - correctlyPlayedNotes.value.size,
                                                            totalNotes = totalNotesInSong,
                                                            bpm = currentBpm ?: 120,
                                                            handMode = currentHandMode,
                                                            durationMs = sessionDurationMs,
                                                            notesPlayed = processedNotes.value.mapIndexed { index, note ->
                                                                PlayedNote(
                                                                    noteValue = note.note,
                                                                    wasCorrect = note.note in correctlyPlayedNotes.value,
                                                                    timestamp = System.currentTimeMillis() - (processedNotes.value.size - index) * 100L,
                                                                    isLeftHand = note.isLeftHand
                                                                )
                                                            }.take(200)
                                                        )

                                                        // Save performance record
                                                        performanceRepository.savePerformanceRecord(performanceRecord)
                                                        Log.d("MidiPlayer", "Performance record saved before going back")

                                                        // ALSO SAVE THE RECORDING (this was missing!)
                                                        Log.d("MidiPlayer", "Checking for recording to save before going back...")
                                                        val recording = recordingManager.createRecording(
                                                            originalMidiFilePath = midiFile.path,
                                                            originalMidiFileName = midiFile.name,
                                                            bpm = currentBpm ?: 120,
                                                            handMode = currentHandMode,
                                                            score = score.value
                                                        )

                                                        recording?.let { rec ->
                                                            Log.d("MidiPlayer", "Created recording with ${rec.recordedEvents.size} events for back button")
                                                            try {
                                                                recordingRepository.saveRecording(rec)
                                                                Log.d("MidiPlayer", "MIDI recording saved successfully before going back with ${rec.recordedEvents.size} events")

                                                                // Verify it was saved
                                                                val allRecordings = recordingRepository.allRecordings.first()
                                                                Log.d("MidiPlayer", "Total recordings in repository after back button save: ${allRecordings.size}")

                                                            } catch (e: Exception) {
                                                                Log.e("MidiPlayer", "Failed to save recording before going back", e)
                                                            }
                                                        } ?: Log.d("MidiPlayer", "No recording to save before going back")

                                                    } catch (e: Exception) {
                                                        Log.e("MidiPlayer", "Failed to save performance record before going back", e)
                                                    }

                                                    isSavingPerformance = false
                                                    showScoreDialog = false
                                                    playbackManager.cleanup()
                                                    onBackPressed()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = stringResource(R.string.back_to_library),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.back),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            color = AccentRose.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = stringResource(R.string.recording),
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.rec_format, formatRecordingTime(recordingDuration)),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.auto_stops_at_song_end),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                if (metronomeEnabled) {
                    MetronomeVisualizer(
                        currentBeat = currentMetronomeBeat,
                        beatsPerMeasure = metronomeBeatCount,
                        isRunning = isMetronomeRunning && isPlaybackActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    )
                }

                if (showCountdown) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownSeconds.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                    }
                }
            }

            // Piano keyboard - placed below SheetMusic view to avoid overlap
            EnhancedPianoLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                pianoConfig = pianoConfig!!,
                pressedKeys = pressedKeys,
                currentNotes = activeNotes,
                activePlayingNotes = activePlayingNotes,
                upcomingNotes = upcomingNotes,
                syncedNotes = emptySet(),
                showKeyNames = settings.difficultyLevel.showKeyNames && settings.showKeyNames,
                onNotePressed = { note ->
                    // Send note on when virtual key is pressed
                    midiConnectionManager.sendNoteOn(note, 64)
                },
                onNoteReleased = { note ->
                    // Send note off when virtual key is released
                    midiConnectionManager.sendNoteOff(note)
                }
            )
        }

        // BPM Dialog
        BpmDialog(
            showDialog = showBpmDialog,
            currentBpm = currentBpm,
            midiFile = midiFile,
            onDismiss = { showBpmDialog = false },
            onConfirm = { newBpm ->
                currentBpm = newBpm
                scope.launch {
                    repository.updateMidiBpm(
                        midiFile.copy(currentBpm = newBpm)
                    )
                }
                playbackManager.resetPlayback()
                playbackManager.startPlayback(midiFile.copy(currentBpm = newBpm), newBpm, 0L, midiNotes, currentHandMode)
                metronomeManager.updateBpm(newBpm)
                showBpmDialog = false
            }
        )
    }
}