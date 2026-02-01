package io.pianosync.midi.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE MIDI connector using the same UUIDs as the decompiled project:
 * SERVICE_UUID = 03B80E5A-EDE8-4B33-A751-6CE34EC4C700
 * CHA_UUID = 7772e5db-3868-4112-a1a9-f2669d106bf3
 *
 * Uses Android BLE API (no FastBLE). Scans for devices advertising the service,
 * connects, enables notify on the characteristic, and passes received bytes to [BleMidiParser].
 */
@SuppressLint("MissingPermission")
class BleMidiConnector(private val context: Context) {

    companion object {
        private const val TAG = "BleMidiConnector"
        const val SERVICE_UUID_STR = "03B80E5A-EDE8-4B33-A751-6CE34EC4C700"
        const val CHA_UUID_STR = "7772e5db-3868-4112-a1a9-f2669d106bf3"
        private val SERVICE_UUID: UUID = UUID.fromString(SERVICE_UUID_STR)
        private val CHA_UUID: UUID = UUID.fromString(CHA_UUID_STR)
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val BLE_SCAN_TIMEOUT_MS = 15_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var leScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null

    val parser = BleMidiParser()

    private var scanCallback: ScanCallback? = null
    private var scanTimeoutRunnable: Runnable? = null

    interface ScanResultCallback {
        fun onScanStarted()
        fun onDeviceFound(device: BluetoothDevice, name: String?, rssi: Int)
        fun onScanFinished()
    }

    interface ConnectCallback {
        fun onConnecting()
        fun onConnected(deviceName: String?)
        fun onConnectFailed(message: String)
        fun onDisconnected()
    }

    private var connectCallback: ConnectCallback? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "GATT connected")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "GATT disconnected, status=$status")
                        cleanupGatt()
                        connectCallback?.onDisconnected()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    connectCallback?.onConnectFailed("Service discovery failed: $status")
                    cleanupGatt()
                    return@post
                }
                val service: BluetoothGattService? = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    connectCallback?.onConnectFailed("MIDI service not found")
                    cleanupGatt()
                    return@post
                }
                val characteristic: BluetoothGattCharacteristic? = service.getCharacteristic(CHA_UUID)
                if (characteristic == null) {
                    connectCallback?.onConnectFailed("MIDI characteristic not found")
                    cleanupGatt()
                    return@post
                }
                val notifyOk = gatt.setCharacteristicNotification(characteristic, true)
                if (!notifyOk) {
                    connectCallback?.onConnectFailed("Could not enable notification")
                    cleanupGatt()
                    return@post
                }
                val descriptor = characteristic.getDescriptor(CCCD_UUID)
                if (descriptor == null) {
                    connectCallback?.onConnectFailed("CCCD descriptor not found")
                    cleanupGatt()
                    return@post
                }
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val name = gatt.device.name ?: gatt.device.address
                    connectCallback?.onConnected(name)
                } else {
                    connectCallback?.onConnectFailed("Descriptor write failed: $status")
                    cleanupGatt()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHA_UUID && value.isNotEmpty()) {
                if (value.size >= 3 && value.size % 3 == 0) {
                    val status = value[0].toInt() and 0xF0
                    if (status == 0x90 || status == 0x80) {
                        parser.parseSimple3Byte(value)
                        return
                    }
                }
                parser.parse(value)
            }
        }
    }

    fun startScan(callback: ScanResultCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        leScanner = bluetoothAdapter?.bluetoothLeScanner
        if (leScanner == null) {
            callback.onScanFinished()
            return
        }
        stopScan()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: device.address
                callback.onDeviceFound(device, name, result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                handler.post { callback.onScanFinished() }
            }
        }
        callback.onScanStarted()
        leScanner?.startScan(listOf(filter), settings, scanCallback!!)
        scanTimeoutRunnable = Runnable {
            stopScan()
            callback.onScanFinished()
        }
        handler.postDelayed(scanTimeoutRunnable!!, BLE_SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
        scanCallback?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                leScanner?.stopScan(it)
            }
        }
        scanCallback = null
    }

    fun connect(device: BluetoothDevice, callback: ConnectCallback) {
        disconnect()
        connectCallback = callback
        callback.onConnecting()
        currentDevice = device
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun connect(address: String, callback: ConnectCallback) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            callback.onConnectFailed("Device not found: $address")
            return
        }
        connect(device, callback)
    }

    fun disconnect() {
        connectCallback = null
        cleanupGatt()
        parser.reset()
    }

    private fun cleanupGatt() {
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
        currentDevice = null
    }

    fun isConnected(): Boolean = bluetoothGatt != null

    fun getCurrentDeviceAddress(): String? = currentDevice?.address
}
