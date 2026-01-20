# PianoSync Architecture

This document provides a comprehensive overview of the PianoSync application architecture, design patterns, and codebase structure.

## Table of Contents

- [Overview](#overview)
- [Architecture Pattern](#architecture-pattern)
- [Project Structure](#project-structure)
- [Layer Breakdown](#layer-breakdown)
- [Key Components](#key-components)
- [Data Flow](#data-flow)
- [State Management](#state-management)
- [MIDI Processing](#midi-processing)

## Overview

PianoSync follows a **clean architecture** approach with clear separation of concerns. The application is built using:

- **Jetpack Compose** for UI
- **MVVM (Model-View-ViewModel)** pattern
- **Repository Pattern** for data access
- **StateFlow/Flow** for reactive state management
- **Dependency Injection** via manual composition

## Architecture Pattern

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ HomeScreen   │  │ PlayerScreen │  │ SettingsScreen│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              Presentation Layer (State)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ StateFlow    │  │ ViewModel    │  │ Composables  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                  Domain Layer (Business)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Managers     │  │ Parsers      │  │ Models       │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   Data Layer (Storage)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Repositories │  │ DataStore    │  │ File System  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

```
app/src/main/java/io/pianosync/midi/
├── MainActivity.kt                 # Entry point, navigation setup
│
├── data/                           # Data layer
│   ├── manager/                    # Business logic managers
│   │   ├── MidiConnectionManager.kt      # MIDI device connection
│   │   ├── MidiPlaybackManager.kt        # MIDI file playback
│   │   ├── MidiRecordingManager.kt       # MIDI recording
│   │   ├── MidiSynthesizerManager.kt     # Audio synthesis
│   │   ├── MetronomeManager.kt           # Metronome functionality
│   │   └── RecordingPlaybackManager.kt   # Recording playback
│   │
│   ├── model/                      # Data models
│   │   ├── MidiFile.kt             # MIDI file metadata
│   │   ├── MidiRecording.kt        # Recording data
│   │   ├── PerformanceRecord.kt    # Performance statistics
│   │   └── SettingsModel.kt        # App settings
│   │
│   ├── parser/                     # MIDI parsing
│   │   ├── MidiParser.kt           # Main parser interface
│   │   └── midi/                   # MIDI format parsers
│   │       ├── MidiFileReader.kt
│   │       ├── MidiTrack.kt
│   │       ├── MidiNote.kt
│   │       └── ...
│   │
│   └── repository/                 # Data repositories
│       ├── MidiFileRepository.kt   # MIDI file storage
│       ├── MidiRecordingRepository.kt
│       ├── PerformanceRepository.kt
│       └── SettingsRepository.kt
│
├── ui/                             # UI layer
│   ├── screens/                    # Screen composables
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── components/
│   │   ├── midiplayer/
│   │   │   ├── MidiPlayerScreen.kt
│   │   │   └── components/
│   │   ├── progress/
│   │   │   └── ProgressTrackingScreen.kt
│   │   └── settings/
│   │       └── SettingsScreen.kt
│   │
│   └── theme/                      # UI theming
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
└── sheetmusic/                     # Sheet music rendering (Java)
    ├── SheetMusic.java
    ├── Piano.java
    ├── MidiPlayer.java
    └── sheets/                     # Sheet music components
```

## Layer Breakdown

### 1. UI Layer (Presentation)

**Location**: `ui/screens/`

The UI layer consists of Jetpack Compose screens and components. Each screen is a composable function that:

- Observes state from managers/repositories via StateFlow
- Handles user interactions
- Navigates between screens
- Displays data and visualizations

**Key Screens:**
- `HomeScreen`: Main menu, file import, MIDI file list
- `MidiPlayerScreen`: Main practice interface with piano, falling notes, controls
- `ProgressTrackingScreen`: Performance statistics and charts
- `SettingsScreen`: App configuration

### 2. Data Layer

#### Managers (`data/manager/`)

Managers handle business logic and coordinate between different components:

- **MidiConnectionManager**: Singleton managing MIDI device connections
  - Handles device discovery and connection
  - Processes MIDI input events
  - Manages virtual keyboard input
  - Exposes StateFlow for connection status and pressed keys

- **MidiPlaybackManager**: MIDI file playback
  - Controls MediaPlayer for audio playback
  - Manages playback state (play, pause, stop)
  - Handles BPM changes and tempo adjustment
  - Supports looping

- **MidiRecordingManager**: Recording functionality
  - Captures MIDI events during performance
  - Tracks timing and events
  - Generates recording data

- **MidiSynthesizerManager**: Audio synthesis
  - Generates sounds for virtual keyboard
  - Handles note on/off events
  - Manages audio resources

- **MetronomeManager**: Metronome functionality
  - Generates metronome ticks
  - Manages BPM and timing
  - Provides visual and audio feedback

#### Repositories (`data/repository/`)

Repositories handle data persistence:

- **MidiFileRepository**: Manages imported MIDI files
  - Stores file metadata (name, path, BPM)
  - Uses DataStore for persistence
  - Provides Flow of MIDI files

- **PerformanceRepository**: Performance data storage
  - Saves practice session results
  - Tracks scores and statistics
  - Provides historical data

- **MidiRecordingRepository**: Recording storage
  - Saves recorded performances
  - Manages recording metadata
  - Handles export functionality

- **SettingsRepository**: App settings
  - Stores user preferences
  - Manages difficulty levels
  - Handles audio settings

#### Models (`data/model/`)

Data classes representing domain entities:

- `MidiFile`: MIDI file metadata
- `MidiRecording`: Recorded performance data
- `PerformanceRecord`: Practice session statistics
- `SettingsModel`: App configuration

#### Parsers (`data/parser/`)

MIDI file parsing and processing:

- `MidiParser`: Main parsing interface
- `MidiFileReader`: Reads MIDI file format
- `MidiTrack`: Represents MIDI tracks
- `MidiNote`: Represents individual notes

### 3. Sheet Music Layer

**Location**: `sheetmusic/` (Java)

Legacy Java code for sheet music rendering:

- `SheetMusic`: Main sheet music renderer
- `Piano`: Piano keyboard visualization
- `MidiPlayer`: MIDI playback integration
- Various symbol classes for music notation

## Key Components

### MainActivity

The entry point of the application:

- Initializes MIDI connection manager
- Sets up navigation with NavHost
- Configures fullscreen landscape mode
- Provides CompositionLocal for MIDI manager access

### Navigation

Uses Jetpack Navigation Compose:

- Routes: `home`, `player/{midiFilePath}`, `progress`, `settings`
- Navigation handled via `NavController`
- Deep linking support for MIDI files

### State Management

**StateFlow/Flow Pattern:**

```kotlin
// Manager exposes state
private val _isConnected = MutableStateFlow(false)
val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

// UI observes state
val isConnected by midiManager.isConnected.collectAsState()
```

**Benefits:**
- Reactive updates
- Lifecycle-aware
- Type-safe
- Easy testing

## Data Flow

### MIDI File Import Flow

```
User selects file
    ↓
HomeScreen (FilePicker)
    ↓
MidiParser.extractBPM()
    ↓
MidiFileRepository.saveMidiFiles()
    ↓
DataStore (persistence)
    ↓
StateFlow update
    ↓
UI refresh
```

### Playback Flow

```
User clicks play
    ↓
MidiPlayerScreen
    ↓
MidiPlaybackManager.startPlayback()
    ↓
MediaPlayer (audio)
    ↓
Note visualization (UI)
    ↓
MIDI input processing
    ↓
Performance tracking
    ↓
Score calculation
```

### MIDI Input Flow

```
Physical piano key press
    ↓
Android MIDI API
    ↓
MidiConnectionManager.onSend()
    ↓
StateFlow update (_pressedKeys)
    ↓
UI observes and updates
    ↓
MidiRecordingManager (if recording)
    ↓
Performance evaluation
```

## State Management

### Manager State Pattern

Each manager exposes its state via StateFlow:

```kotlin
class MidiConnectionManager {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _pressedKeys = MutableStateFlow<Set<Int>>(emptySet())
    val pressedKeys: StateFlow<Set<Int>> = _pressedKeys.asStateFlow()
}
```

### Repository State Pattern

Repositories expose data as Flow:

```kotlin
class MidiFileRepository {
    val midiFiles: Flow<List<MidiFile>> = dataStore.data
        .map { preferences ->
            // Parse and return files
        }
}
```

### UI State Observation

Composables observe state:

```kotlin
@Composable
fun HomeScreen() {
    val isConnected by midiManager.isConnected.collectAsState()
    val midiFiles by repository.midiFiles.collectAsState(initial = emptyList())
    
    // Use state in UI
}
```

## MIDI Processing

### MIDI File Structure

```
MidiFile (metadata)
    ↓
MidiParser.parse()
    ↓
MidiFileReader.read()
    ↓
List<MidiTrack>
    ↓
List<MidiNote> (with timing)
    ↓
Filter by hand mode
    ↓
Visualization & Playback
```

### MIDI Event Processing

```
MIDI Event (byte array)
    ↓
Parse status byte (0x90 = Note On, 0x80 = Note Off)
    ↓
Extract note, velocity, channel
    ↓
Update state (_pressedKeys)
    ↓
Trigger synthesizer (if virtual)
    ↓
Record event (if recording)
    ↓
Evaluate performance (if playing)
```

### Hand Mode Filtering

- **Both Hands**: All notes
- **Left Hand Only**: Notes in lower range (typically C3 and below)
- **Right Hand Only**: Notes in upper range (typically C4 and above)

## Design Patterns Used

1. **Singleton Pattern**: `MidiConnectionManager`, `MidiSynthesizerManager`
2. **Repository Pattern**: All repositories
3. **Observer Pattern**: StateFlow/Flow
4. **Factory Pattern**: MIDI file creation
5. **Strategy Pattern**: Hand mode filtering
6. **Composition**: UI components

## Dependencies

### Core Dependencies

- **Jetpack Compose**: UI framework
- **Navigation Compose**: Navigation
- **DataStore**: Data persistence
- **Lifecycle**: Lifecycle-aware components
- **Coroutines**: Asynchronous operations

### MIDI Dependencies

- **Android MIDI API**: Native MIDI support
- **MediaPlayer**: Audio playback
- **Custom MIDI Parser**: File parsing

## Threading Model

- **Main Thread**: UI updates, Compose recomposition
- **IO Dispatcher**: File operations, data storage
- **Default Dispatcher**: CPU-intensive operations
- **Main.immediate**: Immediate UI updates

## Memory Management

- **StateFlow**: Automatic lifecycle management
- **CompositionLocal**: Scoped dependencies
- **remember**: Cached computations
- **LaunchedEffect**: Scoped coroutines

## Testing Strategy

- **Unit Tests**: Business logic, parsers
- **Integration Tests**: Repository operations
- **UI Tests**: Compose testing framework
- **Instrumentation Tests**: MIDI device interaction

## Future Architecture Improvements

1. **Dependency Injection**: Migrate to Hilt/Koin
2. **Use Cases**: Add domain use case layer
3. **Error Handling**: Centralized error handling
4. **Logging**: Structured logging framework
5. **Analytics**: Optional analytics (privacy-preserving)

---

For development guidelines, see [DEVELOPMENT.md](DEVELOPMENT.md).
For API documentation, see [API.md](API.md).
