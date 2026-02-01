package io.pianosync.midi.ui.screens.home.components

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.pianosync.midi.R
import io.pianosync.midi.data.ble.BleMidiConnector
import io.pianosync.midi.data.manager.MidiConnectionManager
import io.pianosync.midi.rememberMidiManager

private enum class ConnectStep { ChooseType, BluetoothList, UsbList }

data class BleDeviceItem(val device: BluetoothDevice, val name: String?, val rssi: Int)

@Composable
fun DeviceConnectDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
    midiManager: MidiConnectionManager = rememberMidiManager(),
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(ConnectStep.ChooseType) }
    var isConnecting by remember { mutableStateOf(false) }
    val errorMessage by midiManager.errorMessage.collectAsState(initial = null)

    val bleDevices = remember { mutableStateListOf<BleDeviceItem>() }
    val bleConnector = remember { midiManager.getBleConnector() }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun hasBluetoothPermissions(): Boolean = bluetoothPermissions.all { p ->
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }
    val scanCallback = remember {
        object : BleMidiConnector.ScanResultCallback {
            override fun onScanStarted() { }
            override fun onDeviceFound(device: BluetoothDevice, name: String?, rssi: Int) {
                mainHandler.post {
                    val addr = device.address
                    if (bleDevices.none { it.device.address == addr }) {
                        bleDevices.add(BleDeviceItem(device, name ?: addr, rssi))
                    }
                }
            }
            override fun onScanFinished() { }
        }
    }
    fun doBleScan() {
        bleConnector.startScan(scanCallback)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) doBleScan()
    }
    fun startBleScan() {
        bleDevices.clear()
        if (hasBluetoothPermissions()) doBleScan()
        else permissionLauncher.launch(bluetoothPermissions)
    }

    LaunchedEffect(step) {
        if (step == ConnectStep.BluetoothList) startBleScan()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_device)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (step) {
                    ConnectStep.ChooseType -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { step = ConnectStep.BluetoothList },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.connect_via_bluetooth))
                            }
                            OutlinedButton(
                                onClick = { step = ConnectStep.UsbList },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(R.string.connect_via_usb))
                            }
                        }
                    }
                    ConnectStep.BluetoothList -> {
                        OutlinedButton(onClick = { step = ConnectStep.ChooseType }, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(stringResource(R.string.back))
                        }
                        if (bleDevices.isEmpty()) {
                            Text(stringResource(R.string.no_bluetooth_devices), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { startBleScan() }) { Text(stringResource(R.string.scan_again)) }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(stringResource(R.string.open_bluetooth_settings))
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(bleDevices, key = { it.device.address }) { item ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isConnecting) {
                                                isConnecting = true
                                                bleConnector.stopScan()
                                                midiManager.connectBleDevice(
                                                    item.device.address,
                                                    onConnecting = { },
                                                    onConnected = { onConnected(); onDismiss() },
                                                    onFailed = { isConnecting = false }
                                                )
                                            },
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            item.name ?: item.device.address,
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ConnectStep.UsbList -> {
                        OutlinedButton(onClick = { step = ConnectStep.ChooseType }, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(stringResource(R.string.back))
                        }
                        val usbDevices = midiManager.getUsbDevices()
                        if (usbDevices.isEmpty()) {
                            Text(stringResource(R.string.no_usb_devices), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { step = ConnectStep.UsbList }) { Text(stringResource(R.string.refresh)) }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(usbDevices, key = { it.id }) { info ->
                                    UsbDeviceItem(
                                        info = info,
                                        enabled = !isConnecting,
                                        onClick = {
                                            isConnecting = true
                                            midiManager.openUsbDevice(info)
                                            onConnected()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (isConnecting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    )
}

@Composable
private fun UsbDeviceItem(
    info: MidiDeviceInfo,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        ?: "USB MIDI ${info.id}"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        tonalElevation = 1.dp
    ) {
        Text(
            name,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
