#!/bin/bash
echo "Downloading all Gradle dependencies for offline development..."
echo "This may take a few minutes depending on your internet connection..."
echo ""

# Try to use Android Studio's bundled JDK (Java 21) if available
if [ -d "$HOME/Library/Application Support/Google/AndroidStudio*/jbr" ]; then
    export JAVA_HOME="$(ls -d "$HOME/Library/Application Support/Google/AndroidStudio"*/jbr | head -1)"
    echo "Using Android Studio's bundled JDK (Java 21)"
elif [ -d "/Applications/Android Studio.app/Contents/jbr" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr"
    echo "Using Android Studio's bundled JDK (Java 21)"
elif [ -d "$HOME/.local/share/Google/AndroidStudio*/jbr" ]; then
    export JAVA_HOME="$(ls -d "$HOME/.local/share/Google/AndroidStudio"*/jbr | head -1)"
    echo "Using Android Studio's bundled JDK (Java 21)"
else
    echo "Warning: Android Studio JDK not found. Using system Java."
    echo "Note: Java 21 is recommended. Java 25 may cause compatibility issues."
fi

echo ""
echo "Step 1: Downloading project dependencies..."
./gradlew dependencies --refresh-dependencies
if [ $? -ne 0 ]; then
    echo ""
    echo "Warning: Some dependencies may not have been downloaded."
    echo "Continuing with dependency download..."
fi
echo ""
echo "Step 2: Downloading dependencies for all configurations..."
./gradlew dependencies --configuration runtimeClasspath --refresh-dependencies
./gradlew dependencies --configuration compileClasspath --refresh-dependencies
./gradlew dependencies --configuration testRuntimeClasspath --refresh-dependencies
echo ""
echo "Dependencies download process completed!"
echo "To enable offline mode, uncomment 'org.gradle.offline=true' in gradle.properties"
echo ""
echo "Note: If you encounter build errors, they may be due to missing Android SDK components."
echo "The dependencies should still be cached for offline use."
