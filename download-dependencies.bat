@echo off
echo Downloading all Gradle dependencies for offline development...
echo This may take a few minutes depending on your internet connection...
echo.

REM Try to use Android Studio's bundled JDK (Java 21) if available
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo Using Android Studio's bundled JDK (Java 21)
) else if exist "%LOCALAPPDATA%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Android\Android Studio\jbr"
    echo Using Android Studio's bundled JDK (Java 21)
) else (
    echo Warning: Android Studio JDK not found. Using system Java.
    echo Note: Java 21 is recommended. Java 25 may cause compatibility issues.
)

REM Verify Java version
if defined JAVA_HOME (
    echo Verifying Java version...
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"version"
    echo.
    echo JAVA_HOME is set to: %JAVA_HOME%
    echo.
) else (
    echo ERROR: JAVA_HOME is not set!
    echo Please ensure Android Studio is installed or set JAVA_HOME manually.
    echo.
    pause
    exit /b 1
)

REM Stop any existing Gradle daemons to ensure we use the correct Java version
echo Stopping existing Gradle daemons...
call gradlew.bat --stop >nul 2>&1

REM Clear Gradle daemon cache if it exists (fixes cached Java version issues)
if exist "%USERPROFILE%\.gradle\daemon" (
    echo Clearing Gradle daemon cache to remove cached Java version...
    rmdir /s /q "%USERPROFILE%\.gradle\daemon" 2>nul
    echo Daemon cache cleared.
)

echo.
echo Step 1: Downloading project dependencies...
echo Note: If you see "25.0.1" error, the Gradle daemon may still be using Java 25.
echo Try running: gradlew.bat --stop
echo Then set JAVA_HOME manually: set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
echo.
call gradlew.bat dependencies --refresh-dependencies
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to download dependencies!
    echo.
    echo Troubleshooting steps:
    echo 1. Ensure JAVA_HOME points to Java 21: set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
    echo 2. Stop all Gradle daemons: gradlew.bat --stop
    echo 3. Verify Java version: "%JAVA_HOME%\bin\java.exe" -version
    echo 4. Try running the script again
    echo.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo Step 2: Downloading dependencies for all configurations...
call gradlew.bat dependencies --configuration runtimeClasspath --refresh-dependencies
call gradlew.bat dependencies --configuration compileClasspath --refresh-dependencies
call gradlew.bat dependencies --configuration testRuntimeClasspath --refresh-dependencies
echo.
echo Dependencies download process completed!
echo To enable offline mode, uncomment "org.gradle.offline=true" in gradle.properties
echo.
echo Note: If you encounter build errors, they may be due to missing Android SDK components.
echo The dependencies should still be cached for offline use.
