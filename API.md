# API Documentation

This document provides detailed API documentation for PianoSync's key components, managers, repositories, and utilities.

## Table of Contents

- [Managers](#managers)
- [Repositories](#repositories)
- [Models](#models)
- [Parsers](#parsers)
- [UI Components](#ui-components)
- [Utilities](#utilities)

## Managers

### MidiConnectionManager

Manages MIDI device connections and input processing.

**Location**: `data/manager/MidiConnectionManager.kt`

**Singleton Pattern**: Use `getInstance(context)` to get the instance.

#### Properties

```kotlin
val isConnected: StateFlow<Boolean>
// Current connection status

val pressedKeys: StateFlow<Set<Int>>
// Set of currently pressed MIDI note numbers (0-127)

val releasedKeys: StateFlow<Set<Int>>
// Set of recently released MIDI note numbers
```

#### Methods

```kotlin
fun initialize()
// Initialize MIDI manager and start device discovery

fun sendNoteOn(note: Int, velocity: Int = 64)
// Send note on event (for virtual keyboard)
// Parameters:
//   - note: MIDI note number (0-127)
//   - velocity: Note velocity (0-127), default 64

fun sendNoteOff(note: Int)
// Send note off event (for virtual keyboard)
// Parameters:
//   - note: MIDI note number (0-127)

fun getRecordingManager(): MidiRecordingManager
// Get the recording manager instance

fun cleanup()
// Clean up resources and close connections
```

#### Usage Example

```kotlin
val midiManager = MidiConnectionManager.getInstance(context)
midiManager.initialize()

// Observe connection status
val isConnected by midiManager.isConnected.collectAsState()

// Observe pressed keys
val pressedKeys by midiManager.pressedKeys.collectAsState()

// Send virtual key press
midiManager.sendNoteOn(60, 100) // Middle C
midiManager.sendNoteOff(60)
```

---

### MidiPlaybackManager

Manages MIDI file playback using MediaPlayer.

**Location**: `data/manager/MidiPlaybackManager.kt`

#### Properties

```kotlin
val isPlaying: StateFlow<Boolean>
// Current playback state

val currentTimeMs: StateFlow<Long>
// Current playback position in milliseconds

val isLoopEnabled: StateFlow<Boolean>
// Whether loop mode is enabled

val loopStartMs: StateFlow<Long>
// Loop start position in milliseconds

val loopEndMs: StateFlow<Long>
// Loop end position in milliseconds
```

#### Methods

```kotlin
fun startPlayback(
    midiFile: MidiFile,
    bpm: Int,
    offset: Long = 0L,
    allMidiNotes: List<MidiNote>,
    handMode: HandMode
)
// Start MIDI file playback
// Parameters:
//   - midiFile: MIDI file to play
//   - bpm: Playback tempo
//   - offset: Start offset in milliseconds
//   - allMidiNotes: All notes from parsed MIDI file
//   - handMode: Hand mode filter (LEFT, RIGHT, BOTH)

fun pausePlayback()
// Pause playback (can be resumed)

fun resumePlayback()
// Resume paused playback

fun stopPlayback()
// Stop playback completely

fun setBpm(bpm: Int)
// Change playback tempo
// Parameters:
//   - bpm: New tempo in beats per minute

fun setLoopPoints(startMs: Long, endMs: Long)
// Set loop start and end points
// Parameters:
//   - startMs: Loop start in milliseconds
//   - endMs: Loop end in milliseconds

fun toggleLoopMode(enabled: Boolean)
// Enable or disable loop mode
// Parameters:
//   - enabled: Whether to enable looping

fun getOriginalBpm(): Int
// Get the original BPM from MIDI file

fun processNoteAtPlayLine(note: MidiNote, currentTime: Long)
// Process note when it reaches the play line
// Used for performance evaluation
```

#### Usage Example

```kotlin
val playbackManager = MidiPlaybackManager(context, midiManager)

// Start playback
playbackManager.startPlayback(
    midiFile = midiFile,
    bpm = 120,
    allMidiNotes = notes,
    handMode = HandMode.BOTH_HANDS
)

// Observe playback state
val isPlaying by playbackManager.isPlaying.collectAsState()
val currentTime by playbackManager.currentTimeMs.collectAsState()

// Control playback
playbackManager.pausePlayback()
playbackManager.resumePlayback()
playbackManager.stopPlayback()

// Loop control
playbackManager.setLoopPoints(0, 10000)
playbackManager.toggleLoopMode(true)
```

---

### MidiRecordingManager

Manages MIDI recording during performance sessions.

**Location**: `data/manager/MidiRecordingManager.kt`

#### Properties

```kotlin
val recordingState: StateFlow<RecordingState>
// Current recording state (Stopped, Recording)
```

#### Methods

```kotlin
fun startRecording()
// Start recording MIDI events

fun stopRecording(): List<RecordedMidiEvent>
// Stop recording and return recorded events
// Returns: List of recorded MIDI events

fun recordMidiEvent(status: Int, note: Int, velocity: Int, channel: Int = 0)
// Record a MIDI event (called internally)
// Parameters:
//   - status: MIDI status byte (0x90 = Note On, 0x80 = Note Off)
//   - note: MIDI note number (0-127)
//   - velocity: Note velocity (0-127)
//   - channel: MIDI channel (0-15)
```

#### Usage Example

```kotlin
val recordingManager = midiManager.getRecordingManager()

// Start recording
recordingManager.startRecording()

// Observe recording state
val recordingState by recordingManager.recordingState.collectAsState()

// Stop recording
val events = recordingManager.stopRecording()
```

---

### MidiSynthesizerManager

Manages audio synthesis for virtual keyboard.

**Location**: `data/manager/MidiSynthesizerManager.kt`

**Singleton Pattern**: Use `getInstance(context)` to get the instance.

#### Methods

```kotlin
fun initialize()
// Initialize synthesizer

fun noteOn(note: Int, velocity: Int, channel: Int = 0)
// Play a note
// Parameters:
//   - note: MIDI note number (0-127)
//   - velocity: Note velocity (0-127)
//   - channel: MIDI channel (0-15)

fun noteOff(note: Int, velocity: Int, channel: Int = 0)
// Stop a note
// Parameters:
//   - note: MIDI note number (0-127)
//   - velocity: Release velocity (usually 0)
//   - channel: MIDI channel (0-15)

fun cleanup()
// Clean up synthesizer resources
```

#### Usage Example

```kotlin
val synthesizer = MidiSynthesizerManager.getInstance(context)
synthesizer.initialize()

// Play note
synthesizer.noteOn(60, 100) // Middle C

// Stop note
synthesizer.noteOff(60, 0)
```

---

### MetronomeManager

Manages metronome functionality.

**Location**: `data/manager/MetronomeManager.kt`

#### Properties

```kotlin
val isPlaying: StateFlow<Boolean>
// Whether metronome is playing

val currentBeat: StateFlow<Int>
// Current beat number (1-4 for 4/4 time)
```

#### Methods

```kotlin
fun start(bpm: Int)
// Start metronome
// Parameters:
//   - bpm: Tempo in beats per minute

fun stop()
// Stop metronome

fun setBpm(bpm: Int)
// Change metronome tempo
// Parameters:
//   - bpm: New tempo
```

---

## Repositories

### MidiFileRepository

Manages MIDI file storage and retrieval.

**Location**: `data/repository/MidiFileRepository.kt`

#### Properties

```kotlin
val midiFiles: Flow<List<MidiFile>>
// Flow of all stored MIDI files
```

#### Methods

```kotlin
suspend fun saveMidiFiles(files: List<MidiFile>)
// Save list of MIDI files
// Parameters:
//   - files: List of MIDI files to save

suspend fun updateMidiBpm(midiFile: MidiFile)
// Update BPM for a MIDI file
// Parameters:
//   - midiFile: MIDI file with updated BPM

suspend fun getMidiFileByPath(path: String): MidiFile?
// Get MIDI file by path
// Parameters:
//   - path: File path/URI
// Returns: MidiFile or null if not found
```

#### Usage Example

```kotlin
val repository = MidiFileRepository(context)

// Observe files
LaunchedEffect(Unit) {
    repository.midiFiles.collect { files ->
        // Handle files
    }
}

// Save files
coroutineScope.launch {
    repository.saveMidiFiles(files)
}

// Update BPM
coroutineScope.launch {
    repository.updateMidiBpm(updatedFile)
}
```

---

### PerformanceRepository

Manages performance data storage.

**Location**: `data/repository/PerformanceRepository.kt`

#### Properties

```kotlin
val performanceHistory: Flow<List<PerformanceRecord>>
// Flow of all performance records
```

#### Methods

```kotlin
suspend fun savePerformance(record: PerformanceRecord)
// Save a performance record
// Parameters:
//   - record: Performance record to save

suspend fun getPerformancesForFile(midiFilePath: String): List<PerformanceRecord>
// Get all performances for a specific MIDI file
// Parameters:
//   - midiFilePath: Path to MIDI file
// Returns: List of performance records
```

---

### MidiRecordingRepository

Manages MIDI recording storage.

**Location**: `data/repository/MidiRecordingRepository.kt`

#### Methods

```kotlin
suspend fun saveRecording(recording: MidiRecording)
// Save a recording
// Parameters:
//   - recording: Recording to save

suspend fun getRecordingsForFile(midiFilePath: String): List<MidiRecording>
// Get all recordings for a MIDI file
// Parameters:
//   - midiFilePath: Path to MIDI file
// Returns: List of recordings

suspend fun deleteRecording(recordingId: String)
// Delete a recording
// Parameters:
//   - recordingId: ID of recording to delete

suspend fun exportRecording(recording: MidiRecording): Uri?
// Export recording as MIDI file
// Parameters:
//   - recording: Recording to export
// Returns: URI of exported file or null on failure
```

---

### SettingsRepository

Manages app settings storage.

**Location**: `data/repository/SettingsRepository.kt`

#### Properties

```kotlin
val settings: Flow<SettingsModel>
// Flow of current settings
```

#### Methods

```kotlin
suspend fun updateSettings(settings: SettingsModel)
// Update app settings
// Parameters:
//   - settings: New settings

suspend fun updateDifficulty(difficulty: DifficultyLevel)
// Update difficulty level
// Parameters:
//   - difficulty: New difficulty level

suspend fun updateSyncOffset(offsetMs: Long)
// Update playback sync offset
// Parameters:
//   - offsetMs: Offset in milliseconds
```

---

## Models

### MidiFile

Represents a MIDI file in the application.

**Location**: `data/model/MidiFile.kt`

```kotlin
data class MidiFile(
    val name: String,              // Display name
    val path: String,               // URI path
    val dateImported: Long,         // Import timestamp
    val originalBpm: Int? = null,   // Original tempo from file
    val currentBpm: Int? = null     // User-set tempo
)
```

---

### MidiRecording

Represents a recorded MIDI performance.

**Location**: `data/model/MidiRecording.kt`

```kotlin
data class MidiRecording(
    val id: String = UUID.randomUUID().toString(),
    val originalMidiFilePath: String,
    val originalMidiFileName: String,
    val timestamp: Long,
    val durationMs: Long,
    val recordedEvents: List<RecordedMidiEvent>,
    val bpm: Int,
    val handMode: HandMode,
    val score: Int? = null,
    val isSaved: Boolean = false,
    val title: String = ""
)
```

---

### PerformanceRecord

Represents a practice session performance.

**Location**: `data/model/PerformanceRecord.kt`

```kotlin
data class PerformanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val midiFilePath: String,
    val midiFileName: String,
    val timestamp: Long,
    val notesHit: Int,
    val notesMissed: Int,
    val totalNotes: Int,
    val score: Int,                 // Percentage (0-100)
    val bpm: Int,
    val handMode: HandMode,
    val durationMs: Long
)
```

---

### RecordedMidiEvent

Represents a single recorded MIDI event.

**Location**: `data/model/MidiRecording.kt`

```kotlin
data class RecordedMidiEvent(
    val timestamp: Long,             // Relative to recording start
    val midiCommand: Int,            // 0x90 (Note On), 0x80 (Note Off)
    val note: Int,                   // MIDI note number (0-127)
    val velocity: Int,               // Note velocity (0-127)
    val channel: Int = 0             // MIDI channel (0-15)
)
```

---

## Parsers

### MidiParser

Main interface for MIDI file parsing.

**Location**: `data/parser/MidiParser.kt`

#### Methods

```kotlin
fun extractBPM(context: Context, uri: Uri): Int?
// Extract BPM from MIDI file
// Parameters:
//   - context: Android context
//   - uri: MIDI file URI
// Returns: BPM or null if not found

fun parse(context: Context, uri: Uri): List<MidiNote>
// Parse MIDI file and extract notes
// Parameters:
//   - context: Android context
//   - uri: MIDI file URI
// Returns: List of MIDI notes
```

---

## UI Components

### HandMode

Enum for hand mode selection.

**Location**: `ui/screens/midiplayer/components/PianoModels.kt`

```kotlin
enum class HandMode {
    BOTH_HANDS,
    LEFT_HAND_ONLY,
    RIGHT_HAND_ONLY
}
```

---

### MidiNote

Represents a MIDI note with timing information.

**Location**: `ui/screens/midiplayer/components/PianoModels.kt`

```kotlin
data class MidiNote(
    val note: Int,                   // MIDI note number (0-127)
    val startTime: Long,             // Start time in milliseconds
    val duration: Long,              // Duration in milliseconds
    val velocity: Int,               // Note velocity (0-127)
    val channel: Int = 0,            // MIDI channel
    val isLeftHand: Boolean = false  // Whether note is for left hand
)
```

---

## Utilities

### rememberMidiManager

Composable function to access MIDI manager.

**Location**: `MainActivity.kt`

```kotlin
@Composable
fun rememberMidiManager(): MidiConnectionManager
// Get MIDI manager from CompositionLocal
// Returns: MidiConnectionManager instance
```

#### Usage Example

```kotlin
@Composable
fun MyScreen() {
    val midiManager = rememberMidiManager()
    val isConnected by midiManager.isConnected.collectAsState()
    // Use midiManager
}
```

---

## Error Handling

### Common Exceptions

- **MidiFileException**: MIDI file parsing errors
- **IOException**: File I/O errors
- **SecurityException**: Permission errors

### Error Patterns

```kotlin
try {
    val notes = MidiParser.parse(context, uri)
} catch (e: MidiFileException) {
    Log.e("Tag", "Failed to parse MIDI file", e)
    // Handle error
} catch (e: IOException) {
    Log.e("Tag", "File I/O error", e)
    // Handle error
}
```

---

## Threading

### Coroutine Scopes

- **Main Dispatcher**: UI updates, StateFlow collection
- **IO Dispatcher**: File operations, database access
- **Default Dispatcher**: CPU-intensive operations

### Example

```kotlin
// UI thread
val isConnected by midiManager.isConnected.collectAsState()

// Background thread
coroutineScope.launch(Dispatchers.IO) {
    repository.saveMidiFiles(files)
}
```

---

For architecture details, see [ARCHITECTURE.md](ARCHITECTURE.md).
For development guidelines, see [DEVELOPMENT.md](DEVELOPMENT.md).
