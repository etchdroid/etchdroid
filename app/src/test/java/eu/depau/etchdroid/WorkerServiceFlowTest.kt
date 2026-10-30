package eu.depau.etchdroid

import eu.depau.etchdroid.service.BUFFER_BLOCKS
import eu.depau.etchdroid.service.WorkerServiceFlowImpl
import eu.depau.etchdroid.utils.exception.UnknownException
import eu.depau.etchdroid.utils.exception.UsbCommunicationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.jahnen.libaums.core.usb.PipeException
import me.jahnen.libaums.libusbcommunication.LibusbError
import me.jahnen.libaums.libusbcommunication.LibusbException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import java.util.Random

@ExperimentalCoroutinesApi
@ExtendWith(RobolectricExtension::class)
@Config(application = EtchDroidApplication::class)
class WorkerServiceFlowTest {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun testBasicWriteVerifyFlow(devSize: Int, blockSize: Int) = runBlocking {
        val random = Random()

        // Generate random image
        val image = ByteArray(devSize / 2 + random.nextInt(devSize / 2))
        random.nextBytes(image)

        val blockDev = MemoryBufferBlockDeviceDriver(devSize.toLong(), blockSize)

        var currentOffset = 0L
        assertDoesNotThrow {
            WorkerServiceFlowImpl.writeImage(
                image.inputStream(),
                blockDev,
                image.size.toLong(),
                BUFFER_BLOCKS * blockSize,
                0L,
                { currentOffset = it },
                coroutineScope,
                grabWakeLock = {},
                sendProgressUpdate = { _, _, _, _ -> }
            )
        }
        assert(currentOffset == image.size.toLong())

        currentOffset = 0L
        assertDoesNotThrow {
            WorkerServiceFlowImpl.verifyImage(
                image.inputStream(),
                blockDev,
                image.size.toLong(),
                BUFFER_BLOCKS * blockSize,
                { currentOffset = it },
                coroutineScope,
                sendProgressUpdate = { _, _, _, _ -> },
                isVerificationCanceled = { false },
                grabWakeLock = {}
            )
        }
        assert(currentOffset == image.size.toLong())
    }

    @Test
    fun testBasicWriteVerifyFlow512() = testBasicWriteVerifyFlow(1024 * 1024 * 512, 512)

    @Test
    fun testBasicWriteVerifyFlow4096() = testBasicWriteVerifyFlow(1024 * 1024 * 512, 4096)

    @Test
    fun testBasicWriteVerifyFlow733() = testBasicWriteVerifyFlow(1024 * 512 * 733, 733)

    @Test
    fun testLibaumsTransferFailuresMapToUsbCommunication() {
        for (cause in listOf(PipeException(), LibusbException("x", LibusbError.NO_DEVICE))) {
            val e = IOException("MAX_RECOVERY_ATTEMPTS Exceeded", cause)
            assert(WorkerServiceFlowImpl.toJobException(e) is UsbCommunicationException)
        }
        assert(WorkerServiceFlowImpl.toJobException(IOException("x")) is UnknownException)
    }

    @Test
    fun testNoSpaceLeftOnDevice() = runBlocking {
        val random = Random()

        // Generate random image
        val image = ByteArray(1024 * 256)
        random.nextBytes(image)

        val blockDev = MemoryBufferBlockDeviceDriver(1024 * 128, 512)

        assertThrows<UsbCommunicationException> {
            withTimeout(1000) {
                WorkerServiceFlowImpl.writeImage(
                    image.inputStream(),
                    blockDev,
                    image.size.toLong(),
                    BUFFER_BLOCKS * 512,
                    0L,
                    {},
                    coroutineScope,
                    grabWakeLock = {},
                    sendProgressUpdate = { _, _, _, _ -> }
                )
            }
        }.run {
            assert(cause is IOException)
            assert(cause!!.message!!.contains("No space left on device"))
        }
    }

    @Test
    fun testUnplugMidWrite() = runBlocking {
        val random = Random()

        // Generate random image
        val image = ByteArray((BUFFER_BLOCKS * 512 * 4).toInt())
        random.nextBytes(image)

        val blockDev = MemoryBufferBlockDeviceDriver(BUFFER_BLOCKS * 512 * 8, 512).apply {
            throwAtBlockOffset = BUFFER_BLOCKS * 2L
            exceptionToThrow = {
                IOException(
                    "MAX_RECOVERY_ATTEMPTS Exceeded while trying to transfer command to device, please reattach device and try again"
                )
            }
        }

        assertThrows<UsbCommunicationException> {
            withTimeout(1000) {
                WorkerServiceFlowImpl.writeImage(
                    image.inputStream(),
                    blockDev,
                    image.size.toLong(),
                    BUFFER_BLOCKS * 512,
                    0L,
                    {},
                    coroutineScope,
                    grabWakeLock = {},
                    sendProgressUpdate = { _, _, _, _ -> }
                )
            }
        }.run {
            assert(cause is IOException)
            assert(cause!!.message!!.contains("MAX_RECOVERY_ATTEMPTS"))
        }
    }

    @Test
    fun testUnplugMidVerify() = runBlocking {
        val random = Random()

        // Generate random image
        val image = ByteArray((BUFFER_BLOCKS * 512 * 4).toInt())
        random.nextBytes(image)

        val blockDev = MemoryBufferBlockDeviceDriver(BUFFER_BLOCKS * 512 * 8, 512)

        assertDoesNotThrow {
            WorkerServiceFlowImpl.writeImage(
                image.inputStream(),
                blockDev,
                image.size.toLong(),
                BUFFER_BLOCKS * 512,
                0L,
                {},
                coroutineScope,
                grabWakeLock = {},
                sendProgressUpdate = { _, _, _, _ -> }
            )
        }

        blockDev.apply {
            throwAtBlockOffset = BUFFER_BLOCKS * 2L
            exceptionToThrow = {
                IOException(
                    "MAX_RECOVERY_ATTEMPTS Exceeded while trying to transfer command to device, please reattach device and try again"
                )
            }
        }

        assertThrows<UsbCommunicationException> {
            withTimeout(1000) {
                WorkerServiceFlowImpl.verifyImage(
                    image.inputStream(),
                    blockDev,
                    image.size.toLong(),
                    BUFFER_BLOCKS * 512,
                    {},
                    coroutineScope,
                    sendProgressUpdate = { _, _, _, _ -> },
                    isVerificationCanceled = { false },
                    grabWakeLock = {}
                )
            }
        }.run {
            assert(cause is IOException)
            assert(cause!!.message!!.contains("MAX_RECOVERY_ATTEMPTS"))
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUp() {
            DebugProbes.install()
            setUpMockTelemetry()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            DebugProbes.uninstall()
        }
    }
}