# Development Guide

This guide provides detailed information for developers working on PianoSync, including setup instructions, coding standards, testing guidelines, and best practices.

## Table of Contents

- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Project Structure](#project-structure)
- [Common Tasks](#common-tasks)
- [Testing](#testing)
- [Debugging](#debugging)
- [Build & Release](#build--release)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Development Setup

### Prerequisites

1. **Android Studio**
   - Latest stable version (recommended)
   - Download from [developer.android.com](https://developer.android.com/studio)

2. **JDK 21**
   - Required for Android Gradle Plugin 8.7.0
   - Android Studio bundles JDK 21 (recommended)
   - Or download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)

3. **Android SDK**
   - Minimum SDK: 21 (Android 5.0)
   - Target SDK: 35 (Android 15)
   - Install via Android Studio SDK Manager

4. **Kotlin Plugin**
   - Included with Android Studio
   - Version: 2.0.0 (as per `libs.versions.toml`)

### Initial Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/PianoSync.git
   cd PianoSync
   ```

2. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

3. **Sync Gradle**
   - Android Studio should auto-sync
   - If not: File → Sync Project with Gradle Files

4. **Download dependencies**
   - **Windows**: Run `download-dependencies.bat`
   - **Linux/Mac**: Run `./download-dependencies.sh`
   - Or: `./gradlew build --refresh-dependencies`

### Offline Development

After downloading dependencies, you can work offline:

1. Open `gradle.properties`
2. Uncomment: `org.gradle.offline=true`
3. Builds will use cached dependencies

## Coding Standards

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

#### Naming Conventions

- **Classes**: PascalCase
  ```kotlin
  class MidiConnectionManager
  ```

- **Functions/Variables**: camelCase
  ```kotlin
  fun startPlayback()
  val isConnected: Boolean
  ```

- **Constants**: UPPER_SNAKE_CASE
  ```kotlin
  private const val MAX_RETRY_COUNT = 3
  ```

- **Private properties**: Prefer `_` prefix for backing fields
  ```kotlin
  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
  ```

#### Code Formatting

- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations
- Format with Android Studio's auto-formatter (Ctrl+Alt+L / Cmd+Option+L)

#### Best Practices

1. **Immutability First**
   ```kotlin
   // Prefer val over var
   val midiFile: MidiFile = ...
   
   // Use data classes for immutable data
   data class MidiFile(...)
   ```

2. **Null Safety**
   ```kotlin
   // Use safe calls
   val name = midiFile?.name ?: "Unknown"
   
   // Use let for null checks
   midiFile?.let { processFile(it) }
   ```

3. **Type Inference**
   ```kotlin
   // Prefer type inference
   val files = listOf<MidiFile>()
   // Instead of
   val files: List<MidiFile> = listOf<MidiFile>()
   ```

4. **Extension Functions**
   ```kotlin
   // Use extension functions for utility operations
   fun String.toMidiNote(): MidiNote = ...
   ```

5. **Sealed Classes for State**
   ```kotlin
   sealed class PlaybackState {
       object Stopped : PlaybackState()
       data class Playing(val position: Long) : PlaybackState()
       data class Error(val message: String) : PlaybackState()
   }
   ```

### Compose Guidelines

1. **Composable Naming**
   ```kotlin
   @Composable
   fun MidiFileCard(...) { }
   ```

2. **State Hoisting**
   ```kotlin
   // Hoist state to parent when needed by multiple children
   @Composable
   fun ParentScreen() {
       var count by remember { mutableStateOf(0) }
       ChildA(count) { count++ }
       ChildB(count)
   }
   ```

3. **Remember Usage**
   ```kotlin
   // Remember expensive computations
   val sortedFiles = remember(files) {
       files.sortedBy { it.name }
   }
   ```

4. **LaunchedEffect for Side Effects**
   ```kotlin
   LaunchedEffect(key) {
       // Side effect when key changes
       loadData(key)
   }
   ```

5. **Modifier Ordering**
   ```kotlin
   // Order: size → padding → other modifiers
   Modifier
       .fillMaxWidth()
       .padding(16.dp)
       .clickable { }
   ```

### Architecture Patterns

1. **Repository Pattern**
   - All data access through repositories
   - Repositories expose Flow/StateFlow
   - No direct database/file access from UI

2. **Manager Pattern**
   - Business logic in managers
   - Managers are singletons or scoped
   - Expose state via StateFlow

3. **State Management**
   - Use StateFlow for reactive state
   - Collect state in composables
   - Update state in managers/repositories

## Project Structure

### Directory Organization

```
app/src/main/java/io/pianosync/midi/
├── data/
│   ├── manager/          # Business logic
│   ├── model/            # Data models
│   ├── parser/           # MIDI parsing
│   └── repository/       # Data access
├── ui/
│   ├── screens/          # Screen composables
│   └── theme/            # Theming
└── MainActivity.kt       # Entry point
```

### File Naming

- **Kotlin files**: PascalCase matching class name
  - `MidiConnectionManager.kt`
  - `HomeScreen.kt`

- **Layout files**: snake_case
  - `player_toolbar.xml`

- **Resource files**: snake_case
  - `ic_launcher_background.xml`
  - `strings.xml`

## Common Tasks

### Adding a New Screen

1. **Create screen composable**
   ```kotlin
   // ui/screens/newscreen/NewScreen.kt
   @Composable
   fun NewScreen(
       onNavigateBack: () -> Unit,
       modifier: Modifier = Modifier
   ) {
       // Screen implementation
   }
   ```

2. **Add navigation route**
   ```kotlin
   // MainActivity.kt
   composable("newscreen") {
       NewScreen(
           onNavigateBack = { navController.popBackStack() }
       )
   }
   ```

3. **Add navigation call**
   ```kotlin
   navController.navigate("newscreen")
   ```

### Adding a New Manager

1. **Create manager class**
   ```kotlin
   // data/manager/NewManager.kt
   class NewManager(private val context: Context) {
       private val _state = MutableStateFlow(InitialState())
       val state: StateFlow<State> = _state.asStateFlow()
       
       fun doSomething() {
           // Implementation
       }
   }
   ```

2. **Initialize in MainActivity**
   ```kotlin
   val newManager = remember(context) { NewManager(context) }
   ```

### Adding a New Repository

1. **Create repository class**
   ```kotlin
   // data/repository/NewRepository.kt
   class NewRepository(private val context: Context) {
       val data: Flow<List<Data>> = dataStore.data.map { ... }
       
       suspend fun saveData(data: List<Data>) {
           // Implementation
       }
   }
   ```

2. **Use in screens**
   ```kotlin
   val repository = remember(context) { NewRepository(context) }
   ```

### Adding a New Setting

1. **Add to SettingsModel**
   ```kotlin
   data class SettingsModel(
       val newSetting: Boolean = false
   )
   ```

2. **Update SettingsRepository**
   ```kotlin
   suspend fun updateNewSetting(value: Boolean) {
       context.dataStore.edit { preferences ->
           preferences[NEW_SETTING_KEY] = value
       }
   }
   ```

3. **Add UI in SettingsScreen**
   ```kotlin
   Switch(
       checked = settings.newSetting,
       onCheckedChange = { repository.updateNewSetting(it) }
   )
   ```

## Testing

### Unit Tests

**Location**: `app/src/test/java/`

```kotlin
class MidiParserTest {
    @Test
    fun `parse MIDI file returns correct notes`() {
        // Arrange
        val midiFile = ...
        
        // Act
        val result = MidiParser.parse(midiFile)
        
        // Assert
        assertEquals(expected, result)
    }
}
```

### Instrumentation Tests

**Location**: `app/src/androidTest/java/`

```kotlin
@RunWith(AndroidJUnit4::class)
class MidiConnectionTest {
    @Test
    fun testMidiConnection() {
        // Test MIDI device connection
    }
}
```

### Compose UI Tests

```kotlin
@ComposeTest
fun testHomeScreen() {
    composeTestRule.setContent {
        HomeScreen(...)
    }
    
    composeTestRule.onNodeWithText("Import MIDI").performClick()
}
```

### Running Tests

```bash
# All tests
./gradlew test

# Unit tests only
./gradlew testDebugUnitTest

# Instrumentation tests
./gradlew connectedAndroidTest
```

## Debugging

### Logging

Use Android's Log class:

```kotlin
import android.util.Log

Log.d("Tag", "Debug message")
Log.e("Tag", "Error message", exception)
```

**Log Tags**: Use class name or feature name
- `MidiConnectionManager`
- `MidiPlayback`
- `HomeScreen`

### Debug Mode

The app includes a debug mode for testing without physical piano:

```kotlin
var debugMode by remember { mutableStateOf(true) }
```

### Breakpoints

- Set breakpoints in Android Studio
- Use conditional breakpoints for specific scenarios
- Use logpoints for non-intrusive logging

### MIDI Debugging

1. **Check MIDI device connection**
   ```kotlin
   Log.d("MidiConnection", "Devices: ${midiManager.devices.size}")
   ```

2. **Log MIDI events**
   ```kotlin
   Log.d("MidiEvent", "Note: $note, Velocity: $velocity")
   ```

3. **Monitor state changes**
   ```kotlin
   LaunchedEffect(isConnected) {
       Log.d("State", "Connected: $isConnected")
   }
   ```

## Build & Release

### Debug Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

1. **Generate signing key** (first time only)
   ```bash
   keytool -genkey -v -keystore pianosync.keystore \
     -alias pianosync -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure signing** in `app/build.gradle.kts`
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("pianosync.keystore")
           storePassword = "..."
           keyAlias = "pianosync"
           keyPassword = "..."
       }
   }
   ```

3. **Build release**
   ```bash
   ./gradlew assembleRelease
   ```

Output: `app/build/outputs/apk/release/app-release.apk`

### Version Management

Update version in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 7        // Increment for each release
    versionName = "0.5.7"  // Semantic versioning
}
```

## Best Practices

### Performance

1. **Lazy Loading**
   ```kotlin
   LazyColumn {
       items(midiFiles) { file ->
           MidiFileCard(file)
       }
   }
   ```

2. **Remember Expensive Operations**
   ```kotlin
   val sortedFiles = remember(files) {
       files.sortedBy { it.name }
   }
   ```

3. **Avoid Unnecessary Recomposition**
   ```kotlin
   // Use derivedStateOf for computed values
   val visibleItems by remember {
       derivedStateOf { items.filter { it.isVisible } }
   }
   ```

### Memory Management

1. **Clean up resources**
   ```kotlin
   DisposableEffect(Unit) {
       onDispose {
           // Cleanup
           manager.cleanup()
       }
   }
   ```

2. **Close file streams**
   ```kotlin
   file.use { stream ->
       // Use stream
   }
   ```

3. **Release MediaPlayer**
   ```kotlin
   mediaPlayer?.release()
   mediaPlayer = null
   ```

### Error Handling

1. **Use Result type**
   ```kotlin
   fun parseMidiFile(): Result<List<MidiNote>> {
       return try {
           Result.success(notes)
       } catch (e: Exception) {
           Result.failure(e)
       }
   }
   ```

2. **Handle errors gracefully**
   ```kotlin
   when (val result = parseMidiFile()) {
       is Result.Success -> showNotes(result.data)
       is Result.Failure -> showError(result.exception.message)
   }
   ```

3. **Log errors**
   ```kotlin
   catch (e: Exception) {
       Log.e("Tag", "Error occurred", e)
       // Handle error
   }
   ```

### Code Organization

1. **Single Responsibility**: Each class/function has one purpose
2. **DRY (Don't Repeat Yourself)**: Extract common code
3. **KISS (Keep It Simple)**: Prefer simple solutions
4. **YAGNI (You Aren't Gonna Need It)**: Don't over-engineer

## Troubleshooting

### Build Issues

**Problem**: Gradle sync fails
- **Solution**: Check JDK version (must be 21)
- **Solution**: Clear Gradle cache: `./gradlew clean`

**Problem**: Dependency download fails
- **Solution**: Check internet connection
- **Solution**: Clear Gradle cache and retry

**Problem**: "JVM is incompatible"
- **Solution**: Set JAVA_HOME to JDK 21
- **Solution**: Clear Gradle daemon: `./gradlew --stop`

### Runtime Issues

**Problem**: MIDI device not detected
- **Solution**: Check device compatibility (Android 6.0+)
- **Solution**: Verify USB/Bluetooth connection
- **Solution**: Enable debug mode for testing

**Problem**: Audio playback issues
- **Solution**: Check MediaPlayer initialization
- **Solution**: Verify file permissions
- **Solution**: Check audio focus

**Problem**: App crashes on startup
- **Solution**: Check logcat for errors
- **Solution**: Verify all dependencies are included
- **Solution**: Check AndroidManifest.xml

### Development Issues

**Problem**: Compose preview not working
- **Solution**: Rebuild project
- **Solution**: Invalidate caches: File → Invalidate Caches

**Problem**: Auto-complete not working
- **Solution**: File → Sync Project with Gradle Files
- **Solution**: Rebuild project

## Additional Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Android MIDI API](https://developer.android.com/reference/android/media/midi/package-summary)
- [Android Architecture Guidelines](https://developer.android.com/topic/architecture)

---

For architecture details, see [ARCHITECTURE.md](ARCHITECTURE.md).
For API documentation, see [API.md](API.md).
