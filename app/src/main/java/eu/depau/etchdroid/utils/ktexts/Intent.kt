package eu.depau.etchdroid.utils.ktexts

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.depau.etchdroid.plugins.telemetry.Telemetry

// IntentCompat only uses the typed overload on API 34+: on API 33 it NPEs in
// Parcel.readParcelableCreatorInternal for lazily-stored extras.
fun <T> Intent.safeParcelableExtra(key: String, clazz: Class<T>): T? =
    try {
        IntentCompat.getParcelableExtra(this, key, clazz)
    } catch (e: Exception) {
        Telemetry.captureException("Failed to read parcelable extra '$key'", e)
        null
    }

inline fun <reified T> Intent.safeParcelableExtra(key: String): T? {
    return safeParcelableExtra(key, T::class.java)
}

val Intent.usbDevice: UsbDevice?
    get() = safeParcelableExtra<UsbDevice?>(UsbManager.EXTRA_DEVICE)

fun Intent.broadcastLocally(context: Context) {
    LocalBroadcastManager.getInstance(context).sendBroadcast(this)
}

fun Intent.broadcastLocallySync(context: Context) {
    LocalBroadcastManager.getInstance(context).sendBroadcastSync(this)
}