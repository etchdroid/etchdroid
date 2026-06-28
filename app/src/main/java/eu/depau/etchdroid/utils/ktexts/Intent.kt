package eu.depau.etchdroid.utils.ktexts

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.depau.etchdroid.plugins.telemetry.Telemetry

private fun assertNotNull(value: Any?) {
    if (value == null) {
        throw IllegalArgumentException("Value cannot be null")
    }

    try {
        val clazz = value::class
        Log.d("KTexts", "Value is of type $clazz")
    } catch (e: NullPointerException) {
        throw IllegalArgumentException("Value cannot be null", e)
    } catch (e: KotlinNullPointerException) {
        throw IllegalArgumentException("Value cannot be null", e)
    }
}

@Suppress("DEPRECATION")
fun <T> Intent.safeParcelableExtra(key: String, clazz: Class<T>): T? {
    assertNotNull(clazz)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, clazz)
        } else {
            getParcelableExtra(key)
        }
    } catch (e: Exception) {
        // Android 13 (API 33) has a framework bug in the typed getParcelableExtra
        // overload (NPE in Parcel.readParcelableCreatorInternal for lazily-stored
        // extras; fixed in API 34). Capture the failure (records the real NPE +
        // stacktrace as a handled event), then fall back to the unaffected
        // deprecated untyped reader to recover the value.
        Telemetry.captureException(
            "Typed getParcelableExtra('$key') failed, falling back to untyped", e
        )
        try {
            getParcelableExtra(key)
        } catch (e2: Exception) {
            Telemetry.captureException("Failed to read parcelable extra '$key'", e2)
            null
        }
    }
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