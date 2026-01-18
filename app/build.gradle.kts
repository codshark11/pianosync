plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.pianosync.midi"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.pianosync.midi"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                arguments += listOf("-DPROJECT_ROOT=${rootDir.absolutePath}")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.json.json)
    implementation("com.github.pdrogfer:MidiDroid:1.3")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}

////////////////////////////////////////////
// Generate git_commit.h file for Verovio
tasks.register("generateGitCommitHeader") {
    group = "build"
    description = "Generate git_commit.h header file for Verovio"
    
    val gitCommitHeaderFile = rootDir.resolve("external/verovio/include/vrv/git_commit.h")
    
    doLast {
        gitCommitHeaderFile.parentFile.mkdirs()
        
        // Try to get git commit hash
        var commit = ""
        try {
            val process = ProcessBuilder("git", "describe", "--exclude", "*", "--abbrev=7", "--always", "--dirty")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            
            if (process.exitValue() == 0 && output.isNotEmpty()) {
                commit = "-$output"
            }
        } catch (e: Exception) {
            // Git not available or not in a git repo, use default
        }
        
        // Generate the header file
        val timestamp = System.currentTimeMillis() / 1000
        
        gitCommitHeaderFile.writeText("""
            ////////////////////////////////////////////////////////
            /// Git commit version file generated at compilation ///
            /// Timestamp: $timestamp                  ///
            ////////////////////////////////////////////////////////

            #define GIT_COMMIT "$commit"

        """.trimIndent())
    }
}

////////////////////////////////////////////
// Copy the verovio resource directory from the submodule to the project
tasks.register<Copy>("copyVerovioData") {
    from(rootDir.resolve("external/verovio/data"))
    into("src/main/assets/verovio/data")
}

tasks.named("preBuild") {
    dependsOn("copyVerovioData", "generateGitCommitHeader")
}

// Ensure git commit header is generated before CMake runs
tasks.matching { it.name.startsWith("externalNativeBuild") }.configureEach {
    dependsOn("generateGitCommitHeader")
}

////////////////////////////////////////////
// Generate the java and cpp files using swig and write them into the project
val swigOutputJava = file("src/main/java/io/pianosync/midi/lib")
val swigOutputCpp = file("src/main/cpp/verovio_wrap.cxx")
val swigInterfaceFile = file("${rootDir.absolutePath}/external/verovio/bindings/java/verovio.i")

tasks.register<Exec>("generateSwigBindings") {
    group = "build"
    description = "Generate JNI bindings with SWIG"

    workingDir = rootProject.projectDir

    doFirst {
        swigOutputJava.mkdirs()
        swigOutputCpp.parentFile.mkdirs()
    }

    val swigCommand = project.findProperty("swig.path") as String?
        ?: when {
            System.getProperty("os.name").lowercase().contains("windows") -> "swig.exe"
            else -> "swig"
        }

    commandLine = listOf(
        swigCommand,
        "-java",
        "-c++",
        "-package", "io.pianosync.midi.lib",
        "-outdir", swigOutputJava.absolutePath,
        "-o", swigOutputCpp.absolutePath,
        swigInterfaceFile.absolutePath
    )
}

// Ensure SWIG runs before compilation
tasks.named("preBuild") {
    dependsOn("generateSwigBindings")
}

tasks.named("clean") {
    doFirst {
        delete("src/main/java/io/pianosync/midi/lib/*")
        delete("src/main/cpp/verovio_wrap.cxx")
    }
}