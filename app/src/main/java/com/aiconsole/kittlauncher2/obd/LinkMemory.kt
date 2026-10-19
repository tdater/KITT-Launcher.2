package com.aiconsole.kittlauncher2.obd

import android.content.Context

/**
 * Remembers which way in last worked for each adapter (Classic: secure SPP, insecure SPP or
 * RFCOMM channel 1; BLE: direct, auto or random address), so the next connect tries that first
 * instead of walking the whole list, with a pause after each failure, every time the car starts.
 */
object LinkMemory {

    private const val PREFS = "obd_link_methods"

    fun get(context: Context, address: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(address), null)

    fun put(context: Context, address: String, method: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(address), method).apply()
    }

    private fun key(address: String) = BluetoothAddress.normalize(address) ?: address.uppercase()
}

/** Pure ordering for [LinkMemory] (unit-tested). */
object LinkOrder {
    /** [attempts] with the one named [preferred] moved to the front (order otherwise kept). */
    fun <T> preferred(attempts: List<T>, preferred: String?, name: (T) -> String): List<T> {
        val first = attempts.firstOrNull { name(it) == preferred } ?: return attempts
        return listOf(first) + attempts.filter { it !== first }
    }
}
