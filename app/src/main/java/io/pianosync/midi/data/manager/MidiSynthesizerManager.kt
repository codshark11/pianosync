package io.pianosync.midi.data.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.midi.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.exp

/**
 * Manages MIDI synthesizer for generating sounds when keys are pressed.
 * Uses Android's built-in MIDI synthesizer if available, otherwise falls back to AudioTrack tone generation.
 */
class MidiSynthesizerManager private constructor(private val context: Context) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private var synthesizerDevice: MidiDevice? = null
    private var synthesizerInputPort: MidiInputPort? = null
    private var isInitialized = false
    private var useMidiSynthesizer = false
    
    // AudioTrack fallback for tone generation
    private val activeTones = mutableMapOf<Int, AudioTrack>()
    private val sampleRate = 44100
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    companion object {
        @Volatile
        private var INSTANCE: MidiSynthesizerManager? = null

        fun getInstance(context: Context): MidiSynthesizerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MidiSynthesizerManager(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Initialize the MIDI synthesizer
     */
    fun initialize() {
        if (isInitialized) return
        
        if (midiManager == null) {
            Log.e("MidiSynthesizer", "MIDI not supported on this device")
            return
        }

        try {
            // Find the virtual MIDI synthesizer device
            val devices = midiManager.devices
            Log.d("MidiSynthesizer", "Found ${devices.size} MIDI devices")
            
            // Log all devices for debugging
            devices.forEach { device ->
                val name = device.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown"
                Log.d("MidiSynthesizer", "Device: $name, Output ports: ${device.outputPortCount}, Input ports: ${device.inputPortCount}")
            }
            
            // Try to find a synthesizer device
            // Priority: devices with "Synth" in name, then any device with input ports (to send TO it)
            val synthesizerInfo = devices.firstOrNull { device ->
                val name = device.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""
                device.inputPortCount > 0 && 
                (name.contains("Synth", ignoreCase = true) || 
                 name.contains("Virtual", ignoreCase = true) ||
                 name.contains("Software", ignoreCase = true))
            } ?: devices.firstOrNull { device ->
                // Fallback: any device with input ports that's not an output device
                device.inputPortCount > 0 && device.outputPortCount == 0
            } ?: devices.firstOrNull { device ->
                // Last resort: any device with input ports
                device.inputPortCount > 0
            }

            if (synthesizerInfo == null) {
                // No synthesizer device found - use AudioTrack fallback
                Log.w("MidiSynthesizer", "No MIDI synthesizer device found.")
                Log.w("MidiSynthesizer", "Falling back to AudioTrack tone generation (works on emulators).")
                useMidiSynthesizer = false
                isInitialized = true
            } else {
                val name = synthesizerInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown"
                val manufacturer = synthesizerInfo.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Unknown"
                Log.d("MidiSynthesizer", "Found synthesizer: $name (Manufacturer: $manufacturer)")
                Log.d("MidiSynthesizer", "Input ports: ${synthesizerInfo.inputPortCount}, Output ports: ${synthesizerInfo.outputPortCount}")
                openSynthesizer(synthesizerInfo)
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error initializing synthesizer", e)
            // Still mark as initialized to allow attempts
            isInitialized = true
        }
    }

    /**
     * Open a hardware synthesizer device
     */
    private fun openSynthesizer(deviceInfo: MidiDeviceInfo) {
        Log.d("MidiSynthesizer", "Attempting to open synthesizer device...")
        midiManager?.openDevice(
            deviceInfo,
            { device ->
                if (device == null) {
                    Log.e("MidiSynthesizer", "Failed to open synthesizer device")
                    Log.e("MidiSynthesizer", "Device info: ${deviceInfo.properties}")
                    openVirtualSynthesizer() // Fallback to virtual
                    return@openDevice
                }
                Log.d("MidiSynthesizer", "Successfully opened synthesizer device")
                synthesizerDevice = device
                setupSynthesizerReceiver(device)
            },
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * Open a virtual synthesizer (Android's built-in software synthesizer)
     */
    private fun openVirtualSynthesizer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // On Android M and above, we can create a virtual MIDI device
                val devices: Array<MidiDeviceInfo> = midiManager?.devices ?: emptyArray()
                
                // Look for virtual MIDI devices or any device with input ports (to send TO it)
                val virtualDevice = devices.firstOrNull { device: MidiDeviceInfo ->
                    val name = device.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""
                    name.contains("Virtual", ignoreCase = true) || 
                    name.contains("Synth", ignoreCase = true) ||
                    device.inputPortCount > 0
                }
                
                if (virtualDevice != null) {
                    openSynthesizer(virtualDevice)
                } else {
                    // Create a virtual MIDI output port
                    // Note: This requires API 23+ and proper permissions
                    Log.d("MidiSynthesizer", "No virtual device found, synthesizer may not work")
                    // We'll still mark as initialized to allow note sending attempts
                    isInitialized = true
                }
            } else {
                // For older Android versions, mark as initialized but may not work
                Log.d("MidiSynthesizer", "Android version too old for virtual MIDI")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error opening virtual synthesizer", e)
            // Still mark as initialized to allow attempts
            isInitialized = true
        }
    }

    /**
     * Setup the MIDI input port for the synthesizer (to send messages TO the synthesizer)
     */
    private fun setupSynthesizerReceiver(device: MidiDevice) {
        try {
            Log.d("MidiSynthesizer", "Setting up synthesizer - Device info: ${device.info.properties}")
            Log.d("MidiSynthesizer", "Input ports: ${device.info.inputPortCount}, Output ports: ${device.info.outputPortCount}")
            
            if (device.info.inputPortCount > 0) {
                synthesizerInputPort = device.openInputPort(0)
                if (synthesizerInputPort != null) {
                    isInitialized = true
                    useMidiSynthesizer = true
                    Log.d("MidiSynthesizer", "MIDI synthesizer initialized successfully with input port")
                } else {
                    Log.e("MidiSynthesizer", "Failed to open input port 0, falling back to AudioTrack")
                    useMidiSynthesizer = false
                    isInitialized = true
                }
            } else {
                Log.e("MidiSynthesizer", "Synthesizer device has no input ports")
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error setting up synthesizer input port", e)
            e.printStackTrace()
        }
    }

    /**
     * Send a MIDI note on message
     * @param note MIDI note number (0-127)
     * @param velocity Note velocity (0-127), default 64
     * @param channel MIDI channel (0-15), default 0
     */
    fun noteOn(note: Int, velocity: Int = 64, channel: Int = 0) {
        if (!isInitialized) {
            initialize()
        }

        try {
            if (useMidiSynthesizer && synthesizerInputPort != null) {
                // Use MIDI synthesizer if available
                val noteOnMessage = byteArrayOf(
                    (0x90 or channel).toByte(),
                    note.coerceIn(0, 127).toByte(),
                    velocity.coerceIn(0, 127).toByte()
                )
                sendMidiMessage(noteOnMessage)
                Log.d("MidiSynthesizer", "Note ON (MIDI): note=$note, velocity=$velocity")
            } else {
                // Use AudioTrack fallback
                playTone(note, velocity)
                Log.d("MidiSynthesizer", "Note ON (AudioTrack): note=$note, velocity=$velocity")
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error sending note on", e)
        }
    }

    /**
     * Send a MIDI note off message
     * @param note MIDI note number (0-127)
     * @param velocity Note off velocity (usually 0), default 0
     * @param channel MIDI channel (0-15), default 0
     */
    fun noteOff(note: Int, velocity: Int = 0, channel: Int = 0) {
        if (!isInitialized) {
            return
        }

        try {
            if (useMidiSynthesizer && synthesizerInputPort != null) {
                // Use MIDI synthesizer if available
                val noteOffMessage = byteArrayOf(
                    (0x80 or channel).toByte(),
                    note.coerceIn(0, 127).toByte(),
                    velocity.coerceIn(0, 127).toByte()
                )
                sendMidiMessage(noteOffMessage)
                Log.d("MidiSynthesizer", "Note OFF (MIDI): note=$note")
            } else {
                // Use AudioTrack fallback
                stopTone(note)
                Log.d("MidiSynthesizer", "Note OFF (AudioTrack): note=$note")
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error sending note off", e)
        }
    }

    /**
     * Send a raw MIDI message
     */
    private fun sendMidiMessage(message: ByteArray) {
        try {
            synthesizerInputPort?.send(message, 0, message.size, System.nanoTime()) ?: run {
                // If no input port, synthesizer is not available
                Log.d("MidiSynthesizer", "No synthesizer input port, message not sent")
            }
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error sending MIDI message", e)
        }
    }

    /**
     * Convert MIDI note number to frequency in Hz
     */
    private fun midiNoteToFrequency(note: Int): Double {
        // A4 (MIDI note 69) = 440 Hz
        val semitoneOffset = (note - 69).toDouble() / 12.0
        return 440.0 * 2.0.pow(semitoneOffset)
    }

    /**
     * Generate sine wave samples for a given frequency
     */
    private fun generateSineWave(frequency: Double, durationMs: Int, sampleRate: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val amplitude = (Short.MAX_VALUE * volume.coerceIn(0f, 1f)).toInt()

        for (i in samples.indices) {
            val sample = sin(2.0 * PI * frequency * i / sampleRate)
            samples[i] = (sample * amplitude).toInt().toShort()
        }

        return samples
    }

    /**
     * Generate piano-like sound with ADSR envelope
     * Creates a more natural piano sound that decays over time
     */
    private fun generatePianoTone(frequency: Double, durationMs: Int, sampleRate: Int, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val maxAmplitude = (Short.MAX_VALUE * volume.coerceIn(0f, 1f)).toInt()
        
        // ADSR envelope parameters (in samples)
        // Piano notes decay quickly and don't sustain - they fade out naturally
        val attackTime = (sampleRate * 0.005).toInt()  // 5ms quick attack
        val decayTime = (sampleRate * 0.05).toInt()    // 50ms quick decay
        val sustainLevel = 0.0f                        // No sustain - decays to silence
        val releaseTime = (sampleRate * 0.8).toInt()   // 800ms release (most of the duration)
        
        // Add harmonics for more piano-like sound (fundamental + 2nd and 3rd harmonics)
        val fundamental = frequency
        val harmonic2 = frequency * 2.0
        val harmonic3 = frequency * 3.0

        for (i in samples.indices) {
            // Calculate envelope
            val envelope = when {
                i < attackTime -> {
                    // Attack phase: linear increase from 0 to 1
                    i.toFloat() / attackTime
                }
                i < attackTime + decayTime -> {
                    // Decay phase: exponential decay from 1 to 0 (no sustain for piano)
                    val decayProgress = (i - attackTime).toFloat() / decayTime
                    // Exponential decay curve for more natural sound
                    exp(-decayProgress * 3.0f)
                }
                else -> {
                    // Release phase: exponential decay to 0 (most of the duration)
                    val releaseStart = attackTime + decayTime
                    val releaseProgress = (i - releaseStart).toFloat() / releaseTime
                    // Exponential decay curve
                    exp(-releaseProgress * 2.0f)
                }
            }
            
            // Generate sound with harmonics
            val t = i.toDouble() / sampleRate
            val fundamentalWave = sin(2.0 * PI * fundamental * t)
            val harmonic2Wave = sin(2.0 * PI * harmonic2 * t) * 0.5
            val harmonic3Wave = sin(2.0 * PI * harmonic3 * t) * 0.25
            
            // Combine waves and apply envelope
            val combinedWave = (fundamentalWave + harmonic2Wave + harmonic3Wave) / 1.75
            val sample = (combinedWave * envelope * maxAmplitude).toInt().coerceIn(-32768, 32767)
            samples[i] = sample.toShort()
        }

        return samples
    }

    /**
     * Play a tone using AudioTrack (fallback when MIDI synthesizer is not available)
     */
    private fun playTone(note: Int, velocity: Int) {
        // Stop any existing tone for this note
        stopTone(note)

        try {
            val frequency = midiNoteToFrequency(note.coerceIn(0, 127))
            val volume = (velocity / 127.0f).coerceIn(0f, 1f)

            // Create AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Generate piano-like tone with natural decay (1.2 seconds duration)
            // Real piano notes decay and stop after about 1-1.5 seconds even if key is held
            val toneDuration = 1200 // ms - piano note decays and stops after 1.2 seconds
            val samples = generatePianoTone(frequency, toneDuration, sampleRate, volume)
            
            audioTrack.play()
            
            // Write all samples - the sound will automatically stop after the duration
            // even if the key is still held (like a real piano)
            Thread {
                try {
                    var offset = 0
                    val startTime = System.currentTimeMillis()
                    val maxDuration = toneDuration.toLong()
                    
                    // Play all samples or until stopped early
                    while (offset < samples.size && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        // Check if key was released early (stop immediately)
                        if (!activeTones.containsKey(note)) {
                            Log.d("MidiSynthesizer", "Key released early, stopping tone for note $note")
                            break
                        }
                        
                        // Check if maximum duration reached (stop even if key is held)
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed >= maxDuration) {
                            Log.d("MidiSynthesizer", "Maximum duration reached, stopping tone for note $note")
                            break
                        }
                        
                        val remaining = samples.size - offset
                        val chunkSize = minOf(remaining, sampleRate / 10) // Write in small chunks
                        val bytesWritten = audioTrack.write(samples, offset, chunkSize)
                        
                        if (bytesWritten < 0) {
                            // Error writing, break the loop
                            Log.e("MidiSynthesizer", "Error writing samples: $bytesWritten")
                            break
                        }
                        
                        offset += bytesWritten / 2 // Each sample is 2 bytes (16-bit)
                        
                        // Small delay to prevent overwhelming the audio system
                        Thread.sleep(10)
                    }
                } catch (e: Exception) {
                    Log.e("MidiSynthesizer", "Error playing tone", e)
                } finally {
                    try {
                        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.stop()
                        }
                        // Remove from active tones and release
                        activeTones.remove(note)
                        audioTrack.release()
                        Log.d("MidiSynthesizer", "Tone finished for note $note")
                    } catch (e: Exception) {
                        Log.e("MidiSynthesizer", "Error stopping AudioTrack in thread", e)
                    }
                }
            }.start()

            activeTones[note] = audioTrack
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error creating tone for note $note", e)
        }
    }

    /**
     * Stop a tone for a given note
     */
    private fun stopTone(note: Int) {
        activeTones.remove(note)?.let { audioTrack ->
            try {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.pause()
                    audioTrack.flush()
                }
                audioTrack.stop()
                audioTrack.release()
                Log.d("MidiSynthesizer", "Stopped tone for note $note")
            } catch (e: Exception) {
                Log.e("MidiSynthesizer", "Error stopping tone", e)
            }
        }
    }

    /**
     * Cleanup and release resources
     */
    fun cleanup() {
        try {
            // Stop all active tones
            activeTones.keys.toList().forEach { note ->
                stopTone(note)
            }
            activeTones.clear()

            synthesizerInputPort?.close()
            synthesizerDevice?.close()
            synthesizerInputPort = null
            synthesizerDevice = null
            isInitialized = false
            useMidiSynthesizer = false
            Log.d("MidiSynthesizer", "Synthesizer cleaned up")
        } catch (e: Exception) {
            Log.e("MidiSynthesizer", "Error cleaning up synthesizer", e)
        }
    }
}
