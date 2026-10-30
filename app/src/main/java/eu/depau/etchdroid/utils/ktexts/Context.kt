package eu.depau.etchdroid.utils.ktexts

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import eu.depau.etchdroid.R
import eu.depau.etchdroid.plugins.telemetry.Telemetry

val Context.activity: Activity?
    get() {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

fun Context.toast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.startForegroundServiceCompat(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

// The system can refuse the request with "package not found" (e.g. when the app runs in a
// secondary user/profile); report it to the user instead of crashing.
fun Context.requestUsbPermission(usbDevice: UsbDevice, pendingIntent: PendingIntent) {
    try {
        (getSystemService(Context.USB_SERVICE) as UsbManager).requestPermission(
            usbDevice, pendingIntent
        )
    } catch (e: IllegalArgumentException) {
        Telemetry.captureException("UsbManager.requestPermission failed", e)
        toast(getString(R.string.usb_permission_request_failed), Toast.LENGTH_LONG)
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerExportedReceiver(
    receiver: android.content.BroadcastReceiver,
    intentFilter: android.content.IntentFilter,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
    } else {
        registerReceiver(receiver, intentFilter)
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerUnexportedReceiver(
    receiver: android.content.BroadcastReceiver,
    intentFilter: android.content.IntentFilter,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, intentFilter)
    }
}