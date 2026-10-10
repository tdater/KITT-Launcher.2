package com.aiconsole.kittlauncher2.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Finds OBD adapters: paired Bluetooth devices, nearby Classic devices and nearby BLE devices. */
object AdapterDiscovery {

    /** Runtime permissions needed to connect (and scan for BLE) on this Android version. */
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<AdapterConfig> = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .map { AdapterConfig(AdapterType.CLASSIC, it.address, it.name ?: it.address) }
            // Likely OBD dongles first.
            .sortedByDescending { OBD_NAME.containsMatchIn(it.name) }
    } catch (_: SecurityException) {
        emptyList()
    }

    /**
     * Emits nearby Bluetooth Classic devices with a name as they're found, paired or not, the way
     * Torque and Car Scanner list adapters. Connecting to an unpaired one lets Android pair it
     * (showing a PIN prompt if the dongle asks). Collect with a timeout; inquiry runs ~12 s.
     */
    @SuppressLint("MissingPermission")
    fun scanClassic(context: Context): Flow<AdapterConfig> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                        val info = try {
                            device.type to (device.name ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME))
                        } catch (_: SecurityException) {
                            return
                        }
                        // Inquiry can report BLE-only devices too; those belong to the BLE transport.
                        val name = info.second ?: return
                        if (info.first == BluetoothDevice.DEVICE_TYPE_LE) return
                        trySend(AdapterConfig(AdapterType.CLASSIC, device.address, name))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // System broadcasts still arrive with NOT_EXPORTED.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val started = try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
        if (!started) close()
        awaitClose {
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
            }
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Android 10-11 return no Classic or BLE scan results while Location is switched off,
     * even with the permission granted. Android 12+ scans don't depend on it.
     */
    fun scanNeedsLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(location)
    }

    /** Emits BLE devices with a name as they're found. Collect with a timeout. */
    @SuppressLint("MissingPermission")
    fun scanBle(context: Context): Flow<AdapterConfig> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                } ?: result.scanRecord?.deviceName ?: return
                trySend(AdapterConfig(AdapterType.BLE, result.device.address, name))
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }
        try {
            scanner.startScan(callback)
        } catch (_: SecurityException) {
            close()
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
                // Bluetooth turned off mid-scan.
            }
        }
    }

    fun isLikelyObd(name: String): Boolean = OBD_NAME.containsMatchIn(name)

    private val OBD_NAME = Regex("obd|elm|vlink|vgate|icar|obdlink|veepeak|konnwei|carista|v-link|kiwi", RegexOption.IGNORE_CASE)
}
