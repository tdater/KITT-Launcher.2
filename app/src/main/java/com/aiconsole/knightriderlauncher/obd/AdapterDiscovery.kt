package com.aiconsole.knightriderlauncher.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Finds OBD adapters: paired Bluetooth devices and nearby BLE devices. */
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
