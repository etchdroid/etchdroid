/*
 * (C) Copyright 2014-2019 magnusja <github@mgns.tech>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package eu.depau.etchdroid.massstorage

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Parcelable
import android.util.Log
import eu.depau.etchdroid.massstorage.EtchDroidUsbMassStorageDevice.Companion.massStorageDevices
import eu.depau.etchdroid.utils.exception.InitException
import eu.depau.etchdroid.utils.exception.MissingPermissionException
import eu.depau.etchdroid.utils.ktexts.name
import eu.depau.etchdroid.utils.ktexts.vidpid
import kotlinx.parcelize.Parcelize
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.driver.scsi.commands.sense.MediaNotInserted
import me.jahnen.libaums.core.usb.UsbCommunication
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import me.jahnen.libaums.libusbcommunication.LibusbCommunicationCreator
import java.io.IOException

infix fun UsbDevice.matches(other: UsbDevice): Boolean {
    /*
    println("Comparing $this to $other")
    println("deviceName: $deviceName == ${other.deviceName}: ${deviceName == other.deviceName}")
    println("manufacturerName: $manufacturerName == ${other.manufacturerName}: ${manufacturerName == other.manufacturerName}")
    println("productName: $productName == ${other.productName}: ${productName == other.productName}")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        println("serialNumber: $serialNumber == ${other.serialNumber}: ${serialNumber == other.serialNumber}")
    println("deviceId: $deviceId == ${other.deviceId}: ${deviceId == other.deviceId}")
    println("vendorId: $vendorId == ${other.vendorId}: ${vendorId == other.vendorId}")
    println("productId: $productId == ${other.productId}: ${productId == other.productId}")
    println("deviceClass: $deviceClass == ${other.deviceClass}: ${deviceClass == other.deviceClass}")
    println("deviceSubclass: $deviceSubclass == ${other.deviceSubclass}: ${deviceSubclass == other.deviceSubclass}")
    println("deviceProtocol: $deviceProtocol == ${other.deviceProtocol}: ${deviceProtocol == other.deviceProtocol}")
    println("configurationCount: $configurationCount == ${other.configurationCount}: ${configurationCount == other.configurationCount}")
    println("interfaceCount: $interfaceCount == ${other.interfaceCount}: ${interfaceCount == other.interfaceCount}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        println("version: $version == ${other.version}: ${version == other.version}")
    }
    */

    // deviceId and deviceName are not consistent across reconnects
    val match = manufacturerName == other.manufacturerName &&
            productName == other.productName &&
            vendorId == other.vendorId &&
            productId == other.productId &&
            deviceClass == other.deviceClass &&
            deviceSubclass == other.deviceSubclass &&
            deviceProtocol == other.deviceProtocol &&
            configurationCount == other.configurationCount &&
            interfaceCount == other.interfaceCount &&
            version == other.version

    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        match && serialNumber == other.serialNumber
    else
        match
}

infix fun UsbDevice.doesNotMatch(other: UsbDevice) = !matches(other)

infix fun UsbInterface.matches(other: UsbInterface): Boolean {
    return id == other.id &&
            interfaceClass == other.interfaceClass &&
            interfaceSubclass == other.interfaceSubclass &&
            interfaceProtocol == other.interfaceProtocol &&
            endpointCount == other.endpointCount
}

infix fun UsbInterface.doesNotMatch(other: UsbInterface) = !matches(other)

infix fun UsbEndpoint.matches(other: UsbEndpoint): Boolean {
    return address == other.address &&
            attributes == other.attributes &&
            maxPacketSize == other.maxPacketSize &&
            interval == other.interval
}

infix fun UsbEndpoint.doesNotMatch(other: UsbEndpoint) = !matches(other)

@Parcelize
data class PreviewUsbDevice(
    val name: String,
    val vidpid: String,
) : Parcelable

interface IUsbMassStorageDeviceDescriptor {
    val name: String
    val vidpid: String
}

@Parcelize
data class UsbMassStorageDeviceDescriptor(
    private val maybeUsbDevice: UsbDevice? = null,
    val usbInterface: UsbInterface? = null,
    val inEndpoint: UsbEndpoint? = null,
    val outEndpoint: UsbEndpoint? = null,
    private val previewUsbDevice: PreviewUsbDevice? = null,
) : Parcelable, IUsbMassStorageDeviceDescriptor {
    val usbDevice
        get() = maybeUsbDevice!!

    fun buildDevice(context: Context): EtchDroidUsbMassStorageDevice =
        buildDevice(context.getSystemService(Context.USB_SERVICE) as UsbManager)

    fun buildDevice(usbManager: UsbManager): EtchDroidUsbMassStorageDevice {
        return EtchDroidUsbMassStorageDevice(
            usbManager,
            maybeUsbDevice!!,
            usbInterface!!,
            inEndpoint!!,
            outEndpoint!!
        )
    }

    override val name: String
        get() = maybeUsbDevice?.name ?: previewUsbDevice!!.name

    override val vidpid: String
        get() = maybeUsbDevice?.vidpid ?: previewUsbDevice!!.vidpid

    infix fun matches(other: UsbMassStorageDeviceDescriptor): Boolean {
        if (previewUsbDevice != null)
            return previewUsbDevice == other.previewUsbDevice

        /*
        println("Comparing $this to $other")
        println("usbInterface: $usbInterface matches ${other.usbInterface}: ${usbInterface!! matches other.usbInterface!!}")
        println("inEndpoint: $inEndpoint matches ${other.inEndpoint}: ${inEndpoint!! matches other.inEndpoint!!}")
        println("outEndpoint: $outEndpoint matches ${other.outEndpoint}: ${outEndpoint!! matches other.outEndpoint!!}")
        */

        return usbInterface!! matches other.usbInterface!! &&
                inEndpoint!! matches other.inEndpoint!! &&
                outEndpoint!! matches other.outEndpoint!!
    }

    fun findMatchingForNew(newDevice: UsbDevice): UsbMassStorageDeviceDescriptor? {
        if (usbDevice doesNotMatch newDevice)
            return null

        for (msd in newDevice.massStorageDevices)
            if (msd matches this)
                return msd
        return null
    }
}

class EtchDroidUsbMassStorageDevice
internal constructor(
    private val usbManager: UsbManager,
    val usbDevice: UsbDevice,
    private val usbInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
) {
    private lateinit var usbCommunication: UsbCommunication
    lateinit var blockDevices: Map<Int, BlockDeviceDriver>

    private var inited = false

    @Throws(IOException::class)
    fun init() {
        if (inited) {
            throw IllegalStateException("Mass storage device already initialized")
        }

        if (usbManager.hasPermission(usbDevice))
            setupDevice()
        else
            throw MissingPermissionException()

        inited = true
    }


    @Throws(IOException::class)
    private fun setupDevice() {
        try {
            usbCommunication = UsbCommunicationFactory
                .createUsbCommunication(
                    usbManager, usbDevice, usbInterface, outEndpoint, inEndpoint
                )
        } catch (cause: Exception) {
            throw InitException("Initialization failed", cause)
        }

        val maxLun = ByteArray(1)
        usbCommunication.controlTransfer(161, 254, 0, usbInterface.id, maxLun, 1)

        Log.d(TAG, "Max LUN " + maxLun[0].toInt())

        val mutableBlockDevices = mutableMapOf<Int, BlockDeviceDriver>()

        for (lun in 0..maxLun[0]) {
            val blockDevice =
                BlockDeviceDriverFactory.createBlockDevice(usbCommunication, lun = lun.toByte())
            try {
                blockDevice.init()
                mutableBlockDevices[lun] = blockDevice
            } catch (e: MediaNotInserted) {
                // This LUN does not have media inserted. Ignore it.
                continue
            }
        }

        blockDevices = mutableBlockDevices.toMap()
    }

    fun close() {
        usbCommunication.close()
        inited = false
    }

    companion object {
        private val TAG = UsbMassStorageDevice::class.java.simpleName

        /**
         * subclass 6 means that the usb mass storage device implements the SCSI
         * transparent command set
         */
        private const val INTERFACE_SUBCLASS = 6

        /**
         * protocol 80 means the communication happens only via bulk transfers
         */
        private const val INTERFACE_PROTOCOL = 80

        val UsbDevice.isMassStorageDevice: Boolean
            get() = massStorageDevices.isNotEmpty()

        val UsbDevice.massStorageDevices: List<UsbMassStorageDeviceDescriptor>
            @JvmStatic
            get() = (0 until interfaceCount)
                .map { i ->
                    val usbInterface = getInterface(i)
                    usbInterface
                }
                .filter { usbInterface ->
                    // we currently only support SCSI transparent command set with
                    // bulk transfers only!
                    !(usbInterface.interfaceClass != UsbConstants.USB_CLASS_MASS_STORAGE
                            || usbInterface.interfaceSubclass != INTERFACE_SUBCLASS
                            || usbInterface.interfaceProtocol != INTERFACE_PROTOCOL)
                }
                .map { usbInterface ->
                    // Every mass storage device has exactly two endpoints
                    // One IN and one OUT endpoint
                    val endpointCount = usbInterface.endpointCount
                    if (endpointCount != 2) {
                        Log.w(TAG, "inteface endpoint count != 2")
                    }

                    var outEndpoint: UsbEndpoint? = null
                    var inEndpoint: UsbEndpoint? = null

                    for (j in 0 until endpointCount) {
                        val endpoint = usbInterface.getEndpoint(j)
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                outEndpoint = endpoint
                            } else {
                                inEndpoint = endpoint
                            }
                        }
                    }

                    if (outEndpoint == null || inEndpoint == null) {
                        Log.e(TAG, "Not all needed endpoints found!")
                        return@map null
                    }

                    return@map UsbMassStorageDeviceDescriptor(
                        this, usbInterface, inEndpoint, outEndpoint
                    )
                }
                .filterNotNull()

        @JvmStatic
        fun UsbDevice.getMassStorageDevices(usbManager: UsbManager) =
            massStorageDevices.map { it.buildDevice(usbManager) }

        @JvmStatic
        fun getMassStorageDevices(context: Context): Array<EtchDroidUsbMassStorageDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            return usbManager.deviceList
                .map {
                    val device = it.value
                    device.getMassStorageDevices(usbManager)
                }
                .flatten()
                .toTypedArray()
        }
    }
}

private var libusbSetup = false

fun setUpLibUSB() {
    if (libusbSetup) return
    UsbCommunicationFactory.apply {
        registerCommunication(LibusbCommunicationCreator())
        underlyingUsbCommunication = UsbCommunicationFactory.UnderlyingUsbCommunication.OTHER
    }
    libusbSetup = true
}