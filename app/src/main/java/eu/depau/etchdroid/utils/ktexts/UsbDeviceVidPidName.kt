package eu.depau.etchdroid.utils.ktexts

import android.hardware.usb.UsbDevice
import android.os.Build

fun formatID(id: Int): String = Integer.toHexString(id).padStart(4, '0')

val UsbDevice.vidpid: String
    get() = "${formatID(this.vendorId)}:${formatID(this.productId)}"


val UsbDevice.name: String
    get() = "${this.manufacturerName} ${this.productName}"
