package io.pianosync.midi.data.manager

import android.annotation.SuppressLint
import android.content.Context
import android.media.midi.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.pianosync.midi.data.ble.BleMidiConnector
import io.pianosync.midi.data.ble.MidiMessageCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionMode { NONE, USB, BLE }

@SuppressLint("NewApi")
class MidiConnectionManager private constructor(private val context: Context) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private var currentDevice: MidiDevice? = null
    private var midiReceiver: MidiReceiver? = null
    private var currentDeviceInfo: MidiDeviceInfo? = null
    @Volatile
    private var midiInputPort: MidiInputPort? = null
    private var midiOutputPort: MidiOutputPort? = null

    private val bleConnector = BleMidiConnector(context)
    @Volatile
    private var connectionMode = ConnectionMode.NONE

    // Add recording manager
    private val recordingManager = MidiRecordingManager()
    
    // Add synthesizer manager for generating sounds
    private val synthesizerManager = MidiSynthesizerManager.getInstance(context)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pressedKeys = MutableStateFlow<Set<Int>>(emptySet())
    private val _releasedKeys = MutableStateFlow<Set<Int>>(emptySet())
    val pressedKeys: StateFlow<Set<Int>> = _pressedKeys.asStateFlow()
    val releasedKeys: StateFlow<Set<Int>> = _releasedKeys.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Expose recording manager
    fun getRecordingManager(): MidiRecordingManager = recordingManager

    /** Expose BLE connector for UI (scan, connect). */
    fun getBleConnector(): BleMidiConnector = bleConnector

    private val deviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                Log.d("MidiConnection", "Device added: ${device.properties}")
                handleDeviceConnection(device)
            }

            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                if (currentDeviceInfo == device) {
                    closeCurrentDevice()
                }
            }
        }
    } else null

    fun initialize() {
        if (midiManager == null) {
            _errorMessage.value = "MIDI not supported on this device"
            return
        }

        // Initialize synthesizer for key press sounds
        synthesizerManager.initialize()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            midiManager.registerDeviceCallback(
                deviceCallback,
                Handler(Looper.getMainLooper())
            )
        }
        // Do not auto-connect; user chooses Bluetooth or USB from UI
        // checkExistingDevices()
    }

    private fun handleDeviceConnection(deviceInfo: MidiDeviceInfo) {
        Log.d("MidiConnection", "Handling device connection")
        // The permission logic for MIDI was introduced later, but openDevice exists since API 23
        openDevice(deviceInfo)
    }

    private fun openDevice(deviceInfo: MidiDeviceInfo) {
        midiManager?.openDevice(
            deviceInfo,
            { device ->
                if (device == null) {
                    _errorMessage.value = "Failed to open device"
                    return@openDevice
                }
                connectionMode = ConnectionMode.USB
                currentDevice = device
                currentDeviceInfo = deviceInfo
                setupMidiInput(device)
            },
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * Returns list of USB MIDI devices (API 23+). On older API returns empty list.
     */
    fun getUsbDevices(): List<MidiDeviceInfo> {
        if (midiManager == null) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val typeUsb = MidiDeviceInfo.TYPE_USB
        return midiManager.devices.filter { it.type == typeUsb }
            .filter { it.outputPortCount > 0 && it.inputPortCount > 0 }
    }

    /**
     * Open a USB MIDI device by MidiDeviceInfo. Closes any current connection first.
     */
    fun openUsbDevice(deviceInfo: MidiDeviceInfo) {
        disconnect()
        _errorMessage.value = null
        openDevice(deviceInfo)
    }

    /**
     * Connect to a BLE MIDI device by address. Closes any current connection first.
     * Uses BleMidiConnector (same UUIDs as decompiled project).
     */
    fun connectBleDevice(
        address: String,
        onConnecting: () -> Unit,
        onConnected: (String?) -> Unit,
        onFailed: (String) -> Unit
    ) {
        disconnect()
        _errorMessage.value = null
        bleConnector.parser.callback = object : MidiMessageCallback {
            override fun onMidiNoteMessage(status: Int, note: Int, velocity: Int, channel: Int) {
                onMidiMessageFromBle(status, note, velocity, channel)
            }
        }
        bleConnector.connect(address, object : BleMidiConnector.ConnectCallback {
            override fun onConnecting() = onConnecting()
            override fun onConnected(deviceName: String?) {
                connectionMode = ConnectionMode.BLE
                _isConnected.value = true
                _errorMessage.value = null
                onConnected(deviceName)
            }
            override fun onConnectFailed(message: String) {
                _errorMessage.value = message
                onFailed(message)
            }
            override fun onDisconnected() {
                if (connectionMode == ConnectionMode.BLE) {
                    connectionMode = ConnectionMode.NONE
                    _isConnected.value = false
                    _pressedKeys.value = emptySet()
                }
            }
        })
    }

    /**
     * Called when BLE parser receives a MIDI note message. Same pipeline as USB MIDI.
     */
    private fun onMidiMessageFromBle(status: Int, note: Int, velocity: Int, channel: Int) {
        recordingManager.recordMidiEvent(status, note, velocity, channel)
        when (status) {
            0x90 -> {
                if (velocity > 0) {
                    _pressedKeys.value = _pressedKeys.value + note
                    _releasedKeys.value = _releasedKeys.value - note
                    synthesizerManager.noteOn(note, velocity, channel)
                } else {
                    _pressedKeys.value = _pressedKeys.value - note
                    _releasedKeys.value = _releasedKeys.value + note
                    synthesizerManager.noteOff(note, 0, channel)
                }
            }
            0x80 -> {
                _pressedKeys.value = _pressedKeys.value - note
                _releasedKeys.value = _releasedKeys.value + note
                synthesizerManager.noteOff(note, 0, channel)
            }
        }
    }

    /**
     * Disconnect current device (USB or BLE).
     */
    fun disconnect() {
        if (connectionMode == ConnectionMode.BLE) {
            bleConnector.disconnect()
            connectionMode = ConnectionMode.NONE
        }
        closeCurrentDevice()
    }

    private fun setupMidiInput(device: MidiDevice) {
        try {
            Log.d("MidiConnection", "Setting up MIDI input for device: ${device.info.properties}")

            // Close existing ports if any
            midiOutputPort?.close()
            midiInputPort?.close()

            // Open new ports
            midiOutputPort = device.openOutputPort(0)
            midiInputPort = device.openInputPort(0)

            if (midiOutputPort == null) {
                _errorMessage.value = "Could not open MIDI output port"
                Log.e("MidiConnection", "No output port available")
                return
            }

            if (midiInputPort == null) {
                _errorMessage.value = "Could not open MIDI input port"
                Log.e("MidiConnection", "No input port available")
                return
            }

            val midiReceiver = object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    if (count > 2) {
                        val status = msg[offset].toInt() and 0xF0
                        val note = msg[offset + 1].toInt()
                        val velocity = msg[offset + 2].toInt()
                        val channel = msg[offset].toInt() and 0x0F

                        // Debug logging for MIDI events
                        Log.d("MidiConnection", "MIDI Event: status=${status.toString(16)}, note=$note, velocity=$velocity")

                        // Record MIDI event if recording is active
                        recordingManager.recordMidiEvent(status, note, velocity, channel)

                        when (status) {
                            0x90 -> { // Note On
                                if (velocity > 0) {
                                    _pressedKeys.value = _pressedKeys.value + note
                                    _releasedKeys.value = _releasedKeys.value - note
                                    // Play sound when key is pressed
                                    synthesizerManager.noteOn(note, velocity, channel)
                                } else { // Velocity 0 is treated as Note Off
                                    _pressedKeys.value = _pressedKeys.value - note
                                    _releasedKeys.value = _releasedKeys.value + note
                                    synthesizerManager.noteOff(note, 0, channel)
                                }
                            }
                            0x80 -> { // Note Off
                                _pressedKeys.value = _pressedKeys.value - note
                                _releasedKeys.value = _releasedKeys.value + note
                                // Stop sound when key is released
                                synthesizerManager.noteOff(note, 0, channel)
                            }
                        }
                    }
                }
            }

            midiOutputPort?.connect(midiReceiver)
            _isConnected.value = true
            _errorMessage.value = null
            Log.d("MidiConnection", "MIDI setup completed successfully with input port: ${midiInputPort != null}")

        } catch (e: Exception) {
            _errorMessage.value = "Error setting up MIDI: ${e.message}"
            Log.e("MidiConnection", "Error in setupMidiInput", e)
            e.printStackTrace()
        }
    }

    private fun checkExistingDevices() {
        if (midiManager == null) return
        
        val devices = midiManager.devices
        Log.d("MidiConnection", "Found ${devices.size} MIDI devices")

        devices.forEach { device ->
            Log.d("MidiConnection", """
            Device Info:
            - Manufacturer: ${device.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)}
            - Product: ${device.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)}
            - Input Ports: ${device.inputPortCount}
            - Output Ports: ${device.outputPortCount}
        """.trimIndent())
        }

        devices.firstOrNull { device ->
            device.outputPortCount > 0 && device.inputPortCount > 0
        }?.let { device ->
            Log.d("MidiConnection", "Selected device with ${device.outputPortCount} output ports and ${device.inputPortCount} input ports")
            handleDeviceConnection(device)
        }
    }

    private fun closeCurrentDevice() {
        try {
            midiReceiver?.let { receiver ->
                midiOutputPort?.disconnect(receiver)
            }
            midiOutputPort?.close()
            midiInputPort?.close()
            currentDevice?.close()
        } catch (e: Exception) {
            _errorMessage.value = "Error closing device: ${e.message}"
            e.printStackTrace()
        } finally {
            midiInputPort = null
            midiOutputPort = null
            midiReceiver = null
            currentDevice = null
            currentDeviceInfo = null
            connectionMode = ConnectionMode.NONE
            _isConnected.value = false
            _pressedKeys.value = emptySet()
        }
    }

    /**
     * Send a note on event (for virtual keyboard presses)
     * @param note MIDI note number (0-127)
     * @param velocity Note velocity (0-127), default 64
     */
    fun sendNoteOn(note: Int, velocity: Int = 64) {
        // Update pressed keys state
        _pressedKeys.value = _pressedKeys.value + note
        _releasedKeys.value = _releasedKeys.value - note
        
        // Play sound through synthesizer
        synthesizerManager.noteOn(note, velocity)
        
        // Record the event if recording is active
        recordingManager.recordMidiEvent(0x90, note, velocity, 0)
    }

    /**
     * Send a note off event (for virtual keyboard releases)
     * @param note MIDI note number (0-127)
     */
    fun sendNoteOff(note: Int) {
        // Update pressed keys state
        _pressedKeys.value = _pressedKeys.value - note
        _releasedKeys.value = _releasedKeys.value + note
        
        // Stop sound through synthesizer
        synthesizerManager.noteOff(note, 0)
        
        // Record the event if recording is active
        recordingManager.recordMidiEvent(0x80, note, 0, 0)
    }

    fun cleanup() {
        if (midiManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            midiManager.unregisterDeviceCallback(deviceCallback)
        }
        bleConnector.disconnect()
        closeCurrentDevice()
        synthesizerManager.cleanup()
    }

    companion object {
        @Volatile
        private var INSTANCE: MidiConnectionManager? = null

        fun getInstance(context: Context): MidiConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MidiConnectionManager(context).also { INSTANCE = it }
            }
        }
    }
}