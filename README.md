# PianoSync 🎹

[![Get it on Google Play](https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=io.scoreflow.app&referrer=utm_source%3Dgithub%26utm_medium%3Dreadme%26utm_campaign%3Dcontributing)

**PianoSync** is an open-source Android application designed to help piano learners practice with MIDI files. It provides real-time visual feedback, performance tracking, and seamless integration with physical MIDI keyboards.

## 📱 Screenshots

| Main Menu | Settings | Progress Tracking |
|-----------|---------------|----------------|
| ![Main Menu](https://i.postimg.cc/yx19pq3B/Screenshot-20250529-170710.png) | ![Practice Mode](https://i.postimg.cc/cCF3VDf3/Screenshot-20250529-170723.png) | ![Song Selection](https://i.postimg.cc/J7xjDhSh/Screenshot-20250529-170749.png) |

| Piano Interface Without UI | Piano Interface With UI |
|----------------|----------|
| ![Piano Interface](https://i.postimg.cc/Wbfr8HNL/Screenshot-20250529-170814.png) | ![Settings](https://i.postimg.cc/VLNnj8cW/Screenshot-20250529-170819.png) |

## ✨ Features

### Core Features
- **MIDI File Import**: Import and manage MIDI files from your device
- **Real-time Visual Feedback**: Falling notes visualization synchronized with audio playback
- **Physical Piano Integration**: Connect and use physical MIDI keyboards via USB or Bluetooth
- **Virtual Piano Keyboard**: Practice without a physical piano using the on-screen keyboard
- **Performance Tracking**: Track your progress with detailed statistics and score history
- **Recording**: Record your performances and play them back
- **Sheet Music Display**: View sheet music notation alongside falling notes
- **Metronome**: Built-in metronome with visual and audio feedback
- **Hand Mode Selection**: Practice left hand, right hand, or both hands
- **BPM Control**: Adjust playback tempo to match your practice speed
- **Loop Mode**: Loop sections or entire songs for focused practice

### Advanced Features
- **Progress Analytics**: View detailed charts and statistics of your practice sessions
- **Performance Scoring**: Get graded feedback on your accuracy
- **Recording Playback**: Review your recorded performances
- **Difficulty Levels**: Adjustable difficulty with timing tolerance settings
- **Offline Operation**: Fully functional without internet connection
- **Privacy-First**: No data collection, all data stored locally

## 🏗️ Architecture

PianoSync follows a clean architecture pattern with clear separation of concerns:

- **UI Layer**: Jetpack Compose screens and components
- **Data Layer**: Repositories, managers, and data models
- **Domain Layer**: Business logic and MIDI parsing
- **Presentation Layer**: State management and UI state

For detailed architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest version recommended)
- JDK 21 (required for Android Gradle Plugin 8.7.0)
- Android SDK with API level 21+ (Android 5.0+)
- Kotlin plugin

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/PianoSync.git
   cd PianoSync
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle files
   - Wait for dependencies to download

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click "Run" or press `Shift+F10`

### Offline Development

This project can be developed offline after downloading all dependencies.

#### Setup for Offline Development

1. **Download all dependencies** (while online):
   - **Windows**: Run `download-dependencies.bat`
   - **Linux/Mac**: Run `./download-dependencies.sh`
   - Or manually: `./gradlew build --refresh-dependencies`

2. **Enable offline mode**:
   - Open `gradle.properties`
   - Uncomment the line: `org.gradle.offline=true`

3. **Work offline**:
   - Gradle will use cached dependencies
   - Builds will work without internet connection
   - To disable offline mode, comment out the line again

## 📚 Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Detailed architecture and codebase structure
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide, coding standards, and best practices
- **[API.md](API.md)** - API documentation for key components and managers
- **[USER_GUIDE.md](USER_GUIDE.md)** - User guide for end users
- **[PrivacyPolicy.md](PrivacyPolicy.md)** - Privacy policy and data handling

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **State Management**: Kotlin StateFlow/Flow
- **MIDI Processing**: Android MIDI API
- **Audio Playback**: MediaPlayer
- **Data Storage**: DataStore Preferences
- **Navigation**: Navigation Compose
- **Build System**: Gradle with Kotlin DSL

## 📦 Project Structure

```
PianoSync/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/io/pianosync/midi/
│   │   │   │   ├── data/
│   │   │   │   │   ├── manager/      # Business logic managers
│   │   │   │   │   ├── model/        # Data models
│   │   │   │   │   ├── parser/       # MIDI file parsing
│   │   │   │   │   └── repository/   # Data repositories
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/      # Compose screens
│   │   │   │   │   └── theme/        # UI theme
│   │   │   │   ├── sheetmusic/       # Sheet music rendering
│   │   │   │   └── MainActivity.kt
│   │   │   └── res/                   # Resources
│   │   └── test/                      # Unit tests
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml            # Dependency versions
└── build.gradle.kts
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](#contributing) below for details.

### Quick Contribution Guide

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Test thoroughly
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

For detailed contribution guidelines, see [DEVELOPMENT.md](DEVELOPMENT.md).

## 🐛 Troubleshooting

### Java Version Issues

If you see an error like "25.0.1" or "JVM is incompatible", it means you're using an unsupported Java version. This project requires **Java 21**.

**Windows (PowerShell):**
```powershell
# Stop existing Gradle daemons
.\gradlew.bat --stop

# Clear Gradle daemon cache
if (Test-Path "$env:USERPROFILE\.gradle\daemon") {
    Remove-Item "$env:USERPROFILE\.gradle\daemon" -Recurse -Force
}

# Set JAVA_HOME to Android Studio's JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Verify Java version (should show Java 21)
& "$env:JAVA_HOME\bin\java.exe" -version

# Now run the download script
.\download-dependencies.bat
```

**Linux/Mac:**
```bash
./gradlew --stop
export JAVA_HOME=/path/to/AndroidStudio/jbr
java -version  # Verify it's Java 21
./download-dependencies.sh
```

### MIDI Connection Issues

- Ensure your device supports MIDI (Android 6.0+)
- Check USB/Bluetooth connection
- Verify MIDI keyboard is powered on
- Try disconnecting and reconnecting the device

## 📄 License

See [LICENSE](LICENSE) file for details.

## 👥 Contributors

Thank you to all contributors who have helped make PianoSync better!

## 📧 Contact

- **Email**: raphaelboullaylefur@proton.me
- **Discord**: clarityhs

## 🙏 Acknowledgments

- Open source MIDI libraries and tools
- The Android MIDI API team
- All beta testers and contributors

---

## Contributing to PianoSync

### Welcome Contributors! 🎹🎶

First off, thank you for considering contributing to PianoSync. It's people like you that make PianoSync such a great tool for music learners and piano enthusiasts.

### How Can I Contribute?

#### Reporting Bugs 🐞
- **Ensure the bug has not already been reported** by searching existing Issues.
- If you can't find an open issue addressing the problem, open a new one.
- Be sure to include a **clear title and description**, as much relevant information as possible, and a **code sample** or steps to reproduce the issue.

#### Suggesting Enhancements 💡
- Open an issue with a clear title and description of your suggested enhancement.
- Provide context about why this feature would be useful.
- If possible, include mockups or design sketches.

#### Development Process 🛠️

**Getting Started**
1. Fork the repository
2. Create a new branch for your feature or bugfix
   - Use a clear and descriptive branch name
   - Example: `feature/add-difficulty-levels` or `bugfix/midi-playback-sync`
3. Make your changes
4. Test your changes
5. Submit a Pull Request

**Pull Request Guidelines**
- Fill out the PR template completely
- Include screenshots or GIFs if your changes affect the UI
- Ensure your code follows the project's coding standards
- Update documentation accordingly

### Coding Standards 📏

- Follow Kotlin best practices
- Use meaningful variable and function names
- Write clear, concise comments
- Maintain consistent code formatting
- Use Android Studio's built-in code formatter
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

#### Example Kotlin Style Guide Highlights:
- Use camelCase for names
- Use meaningful and intention-revealing names
- Prefer immutability (val over var)
- Use type inference where possible

### Contribution Areas We Need Help With 🤝

1. **Design**
   - Logo design
   - UI/UX improvements
   - App icon designs

2. **Features**
   - Difficulty level implementation
   - Visual cues for note hitting accuracy
   - Physical piano connection improvements

3. **Testing**
   - Comprehensive app testing
   - MIDI file compatibility testing
   - Performance optimization

### Feature Request Process 🚀

1. Open an issue describing the feature
2. Discuss the feature with maintainers
3. Once approved, assign yourself or wait for assignment
4. Implement the feature
5. Write unit and instrumentation tests
6. Update documentation
7. Submit a pull request

### Dependency Management

- Use Gradle for dependency management
- Keep dependencies up to date
- Prefer the latest stable versions of libraries

### Communication Channels 💬

- Email: raphaelboullaylefur@proton.me
- Discord: clarityhs

### Special Call-out for Designers 🎨

We are actively seeking designers to help improve our app's visual experience! If you're interested in:
- Creating a logo
- Designing UI mockups
- Improving app aesthetics

Please reach out directly via email or Discord.

## Thank You! 🙏

Your contributions make open-source communities amazing. We appreciate every contribution, no matter how small!

---

**Note:** This project is in active development. Guidelines may change, so always check the latest version.
