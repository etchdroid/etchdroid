@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("RunBlockingInSuspendFunction")

package eu.depau.etchdroid

import eu.depau.etchdroid.massstorage.BlockDeviceInputStream
import eu.depau.etchdroid.massstorage.BlockDeviceOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import java.lang.Math.min
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * A block device that parks inside its first [read] until [releaseRead] is counted down, signaling
 * via [readStarted] once it has entered the call. Used to prove that closing a stream waits for an
 * in-flight native transfer to finish (the libusb concurrency crash, ETCHDROID write->verify).
 */
private class ParkingBlockDeviceDriver(
    sizeBytes: Long,
    override val blockSize: Int,
) : BlockDeviceDriver {
    private val backing = ByteArray(sizeBytes.toInt())
    override val blocks: Long get() = (backing.size / blockSize).toLong()

    val activeReads = AtomicInteger(0)
    val readStarted = CountDownLatch(1)
    val releaseRead = CountDownLatch(1)

    override fun init() {}

    override fun read(deviceOffset: Long, buffer: ByteBuffer) {
        activeReads.incrementAndGet()
        try {
            readStarted.countDown()
            releaseRead.await()
            val start = (deviceOffset * blockSize).toInt()
            buffer.put(backing, start, buffer.remaining())
        } finally {
            activeReads.decrementAndGet()
        }
    }

    override fun write(deviceOffset: Long, buffer: ByteBuffer) {
        val start = (deviceOffset * blockSize).toInt()
        buffer.get(backing, start, buffer.remaining())
    }
}

@ExperimentalCoroutinesApi
@ExtendWith(RobolectricExtension::class)
@Config(application = EtchDroidApplication::class)
class BlockDeviceInputStreamTest {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    @Test
    fun testWithCommonParams() {
        runReadTest(
            10L * 1024 * 1024,  // 10 MiB
            512,
            2048
        )
        runPseudoRandomReadTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            2048
        )
    }

    @Test
    fun closeWithoutReadingDoesNotThrow() = runBlocking {
        // Regression: closeAsync used to touch the lateinit channel that is only
        // created on first read, crashing for 0-byte/unreadable sources (ETCHDROID-2T).
        val testDev = MemoryBufferBlockDeviceDriver(10L * 1024 * 1024, 512).apply {
            fillWithGrowingSequence()
        }
        val inputStream = BlockDeviceInputStream(testDev, coroutineScope)
        assertDoesNotThrow { runBlocking { inputStream.closeAsync() } }
    }

    @Test
    fun closeWaitsForInFlightRead() = runBlocking {
        // Regression: closeAsync() used to return while the I/O worker was still inside a native
        // blockDev.read(). At the write->verify handoff that let two coroutines submit transfers on
        // the same libusb handle concurrently, crashing the app natively in libusb_submit_transfer.
        val testDev = ParkingBlockDeviceDriver(1L * 1024 * 1024, 512)
        val inputStream =
            BlockDeviceInputStream(testDev, coroutineScope, bufferBlocks = 1, prefetchBuffers = 1)

        // Trigger the worker; it enters read() and parks. readAsync won't return until the worker
        // delivers a block, so run it detached (and swallow the close-induced cancellation).
        val readJob = coroutineScope.launch {
            try {
                inputStream.readAsync(ByteArray(4))
            } catch (_: Exception) {
                // Expected: the channel is closed under us by closeAsync().
            }
        }

        assertTrue(testDev.readStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            "Worker never entered read()"
        }

        val closeJob = coroutineScope.launch { inputStream.closeAsync() }

        // Give closeAsync ample time to run; it must NOT complete while a read is in flight.
        delay(500.milliseconds)
        assertFalse(closeJob.isCompleted) { "closeAsync returned while a read was still in flight" }
        assertTrue(testDev.activeReads.get() > 0) { "Expected a read to be in flight" }

        // Let the read finish; close must now complete and no transfer may remain in flight.
        testDev.releaseRead.countDown()
        closeJob.join()
        readJob.join()
        assertEquals(0, testDev.activeReads.get())
    }

    @Test
    fun testWithWeirdBlockSize() {
        runReadTest(
            10 * 666 * 2 * 666 * 2,  // 10 MegaDevils
            666,
            2048
        )
        runPseudoRandomReadTest(
            10 * 666 * 2 * 666 * 2,  // 10 MegaDevils
            666,
            2048
        )
    }

    @Test
    fun testNoPrefetch() {
        runReadTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            1
        )
        runPseudoRandomReadTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            1
        )
    }

    private fun runReadTest(testDevSize: Long, testBlockSize: Int, testPrefetchBlocks: Long) =
        runBlocking {
            val testDev = MemoryBufferBlockDeviceDriver(testDevSize, testBlockSize).apply {
                fillWithGrowingSequence()
            }
            val slots = if (testPrefetchBlocks == 1L) 1 else 4
            val bufferBlocks = testPrefetchBlocks / slots

            val inputStream =
                BlockDeviceInputStream(
                    testDev,
                    coroutineScope,
                    bufferBlocks = bufferBlocks,
                    prefetchBuffers = slots
                )

            // Ensure seek(0) works
            assertEquals(0L, inputStream.seekAsync(0L))

            // Ensure that read(byteArray) fetches
            val byteArray0 = ByteArray(4)
            assertEquals(4, inputStream.readAsync(byteArray0))
            assertArrayEquals(
                byteArrayOf(0, 1, 2, 3),
                byteArray0
            )
            assertEquals(4, inputStream.readAsync(byteArray0))
            assertArrayEquals(
                byteArrayOf(4, 5, 6, 7),
                byteArray0
            )
            inputStream.skipAsync(-8)

            // Read one block
            val byteArray1 = ByteArray(testBlockSize)
            assertEquals(testBlockSize, inputStream.readAsync(byteArray1))
            assertArrayEquals(
                (0 until testBlockSize).map { (it % 0xFF).toByte() }.toByteArray(),
                byteArray1
            )

            // Read another block
            assertEquals(testBlockSize, inputStream.readAsync(byteArray1))
            assertArrayEquals(
                (testBlockSize until testBlockSize * 2).map { (it % 0xFF).toByte() }.toByteArray(),
                byteArray1
            )

            // Rewind
            inputStream.skipAsync((-2 * testBlockSize).toLong())

            // Read first bytes
            assertEquals(0, inputStream.readAsync())
            assertEquals(1, inputStream.readAsync())
            assertEquals(2, inputStream.readAsync())
            assertEquals(3, inputStream.readAsync())

            // Seek within prefetched buffer
            val skipBytes1: Long = 2L * 0xFF - 4
            assertEquals(skipBytes1, inputStream.skipAsync(skipBytes1))

            assertEquals(0, inputStream.readAsync())
            assertEquals(1, inputStream.readAsync())
            assertEquals(2, inputStream.readAsync())
            assertEquals(3, inputStream.readAsync())

            // Seek outside prefetched buffer
            val skipBytes2 = 5L * 0xFF * testPrefetchBlocks - 4 + 100
            assertEquals(skipBytes2, inputStream.skipAsync(skipBytes2))

            assertEquals(100 and 0xFF, inputStream.readAsync())
            assertEquals(101 and 0xFF, inputStream.readAsync())
            assertEquals(102 and 0xFF, inputStream.readAsync())
            assertEquals(103 and 0xFF, inputStream.readAsync())

            // Mark stream to get back here later
            // Implementation ignores readlimit so anything works
            inputStream.markAsync(0)

            // Seek to EOF
            val remainingBytes = testDevSize - (4 + skipBytes1 + 4 + skipBytes2 + 4)
            assertEquals(remainingBytes, inputStream.skipAsync(remainingBytes * 20))

            // Ensure EOF
            assertEquals(-1, inputStream.readAsync())

            // Seek to last byte (previous byte)
            assertEquals(-1, inputStream.skipAsync(-1))
            assertEquals((testDevSize - 1).toInt() % 0xFF, inputStream.readAsync())

            // Go back to marked position
            inputStream.resetAsync()
            assertEquals(104 and 0xFF, inputStream.readAsync())
            assertEquals(105 and 0xFF, inputStream.readAsync())
            assertEquals(106 and 0xFF, inputStream.readAsync())
            assertEquals(107 and 0xFF, inputStream.readAsync())

            // Go back to beginning
            inputStream.skipAsync(-testDevSize)

            // Read to array
            var byteArray = ByteArray(8)
            assertEquals(byteArray.size, inputStream.readAsync(byteArray))

            assertArrayEquals(
                byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7),
                byteArray
            )

            // Read to array with offset + length
            byteArray = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
            assertEquals(4, inputStream.readAsync(byteArray, 4, 4))
            assertArrayEquals(
                byteArrayOf(0, 0, 0, 0, 8, 9, 10, 11),
                byteArray
            )

            // Read to array with length > array size
            assertEquals(4, inputStream.readAsync(byteArray, 4, 200))
            assertArrayEquals(
                byteArrayOf(0, 0, 0, 0, 12, 13, 14, 15),
                byteArray
            )

            // Read to array with offset outside of array
            assertEquals(0, inputStream.readAsync(byteArray, 10, 4))

            // Go to end of prefetched blocks
            inputStream.skipAsync(-testDevSize)
            inputStream.skipAsync((testPrefetchBlocks * testBlockSize - 4))

            // Read to array, second half needs to be fetched
            assertEquals(byteArray.size, inputStream.readAsync(byteArray))

            val currentPos = testPrefetchBlocks * testBlockSize - 4

            assertArrayEquals(
                byteArrayOf(
                    ((currentPos + 0) % 0xFF).toByte(),
                    ((currentPos + 1) % 0xFF).toByte(),
                    ((currentPos + 2) % 0xFF).toByte(),
                    ((currentPos + 3) % 0xFF).toByte(),
                    ((currentPos + 4) % 0xFF).toByte(),
                    ((currentPos + 5) % 0xFF).toByte(),
                    ((currentPos + 6) % 0xFF).toByte(),
                    ((currentPos + 7) % 0xFF).toByte()
                ),
                byteArray
            )
        }

    private fun runPseudoRandomReadTest(
        testDevSize: Long,
        testBlockSize: Int,
        testPrefetchBlocks: Long,
    ) = runBlocking {
        val testDev = MemoryBufferBlockDeviceDriver(testDevSize, testBlockSize).apply {
            fillWithRandom()
        }
        val slots = if (testPrefetchBlocks == 1L) 1 else 4
        val bufferBlocks = testPrefetchBlocks / slots

        val inputStream =
            BlockDeviceInputStream(
                testDev,
                coroutineScope,
                bufferBlocks = bufferBlocks,
                prefetchBuffers = slots
            )

        val byteBuffer = ByteBuffer.allocate(testDevSize.toInt())
        testDev.backingBuffer.copyInto(byteBuffer.array())
        byteBuffer.position(0)
        byteBuffer.limit(byteBuffer.capacity())

        @Suppress("UNUSED_VARIABLE")
        val originalBuffer = byteBuffer.duplicate()

        val byteArray = ByteArray((testBlockSize * testPrefetchBlocks).toInt())
        while (byteBuffer.hasRemaining()) {
            val readBytes = inputStream.readAsync(byteArray)
            @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
            assertEquals(
                min(byteBuffer.remaining(), byteArray.size),
                readBytes
            )

            val expectedBytes = ByteArray(readBytes)
            byteBuffer.get(expectedBytes)
            assertArrayEquals(expectedBytes, byteArray.copyOf(readBytes))
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

@ExperimentalCoroutinesApi
@ExtendWith(RobolectricExtension::class)
@Config(application = EtchDroidApplication::class)
class BlockDeviceOutputStreamTest {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    @Test
    fun testWithCommonParams() {
        runWriteTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            2048
        )
        runPseudoRandomWriteTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            2048
        )
        runPseudoRandomSmallWritesTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            2048
        )
    }

    @Test
    fun testWithWeirdBlockSize() {
        runWriteTest(
            10 * 666 * 2 * 666 * 2,  // 10 MegaDevils
            666,
            2048
        )
        runPseudoRandomWriteTest(
            10 * 666 * 2 * 666 * 2,  // 10 MegaDevils
            666,
            2048
        )
        runPseudoRandomSmallWritesTest(
            10 * 666 * 2 * 666 * 2,  // 10 MegaDevils
            666,
            2048
        )
    }

    @Test
    fun testUnbuffered() {
        runWriteTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            1
        )
        runPseudoRandomWriteTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            1
        )
        runPseudoRandomSmallWritesTest(
            10 * 1024 * 1024,  // 10 MiB
            512,
            1
        )
    }


    private fun runWriteTest(testDevSize: Long, testBlockSize: Int, testBufferBlocks: Long) =
        runBlocking {
            val testDev = MemoryBufferBlockDeviceDriver(testDevSize, testBlockSize).apply {
                fillWithReverseGrowingSequence()
            }

            val queueSlots = if (testBufferBlocks == 1L) 1 else 4
            val bufferBlocks = testBufferBlocks / queueSlots

            val outputStream =
                BlockDeviceOutputStream(
                    testDev, coroutineScope = coroutineScope, bufferBlocks = bufferBlocks,
                    queueSize = queueSlots
                )

            // Ensure seek(0) works
            assertEquals(0L, outputStream.seekAsync(0L))

            // Write some bytes
            outputStream.apply {
                writeAsync(0)
                writeAsync(1)
                writeAsync(2)
                writeAsync(3)
                flushAsync()
            }

            var byteArray = ByteArray(8)
            testDev.backingBuffer.copyInto(byteArray, endIndex = 8)
            assertArrayEquals(
                byteArrayOf(
                    0, 1, 2, 3, 0xFA.toByte(), 0xF9.toByte(), 0xF8.toByte(), 0xF7.toByte()
                ),
                byteArray
            )

            // Fill current block
            byteArray = ByteArray(testBlockSize - 4)
            outputStream.apply {
                writeAsync(byteArray)
                flushAsync()
            }

            byteArray = ByteArray(testBlockSize + 8)
            testDev.backingBuffer.copyInto(byteArray, endIndex = testBlockSize + 8)
            assertArrayEquals(
                byteArrayOf(
                    0, 1, 2, 3
                ),
                byteArray.copyOfRange(0, 4)
            )
            assertArrayEquals(
                (4 until testBlockSize).map { 0.toByte() }.toByteArray(),
                byteArray.copyOfRange(4, testBlockSize)
            )
            assertArrayEquals(
                (testBlockSize until testBlockSize + 8).map { (0xFE - it % 0xFF).toByte() }
                    .toByteArray(),
                byteArray.copyOfRange(testBlockSize, testBlockSize + 8)
            )

            // Fill the buffer except for the last 8 bytes
            // Note that flush successfully wrote one full block, so the block offset is now 1
            byteArray = ByteArray((testBufferBlocks * testBlockSize - 8).toInt())
            outputStream.writeAsync(byteArray)

            // Now do a write that goes out of the buffer
            byteArray = (0 until 16).map { 42.toByte() }.toByteArray()
            outputStream.apply {
                writeAsync(byteArray)
                flushAsync()
            }

            val fullBufferOffset = (testBufferBlocks + 1) * testBlockSize

            val byteArray1 = ByteArray(20)
            testDev.backingBuffer.copyInto(
                byteArray1, 0, (fullBufferOffset - 8).toInt(), (fullBufferOffset + 12).toInt()
            )

            assertArrayEquals(
                byteArray,
                byteArray1.copyOfRange(0, 16)
            )
            assertArrayEquals(
                (fullBufferOffset + 8 until fullBufferOffset + 12)
                    .map { (0xFE - it % 0xFF).toByte() }
                    .toByteArray(),
                byteArray1.copyOfRange(16, 20)
            )

            // Go to end of file - 4 bytes
            val remainingBytes = testDevSize - (fullBufferOffset + 8)

            byteArray = ByteArray((remainingBytes - 4).toInt())
            outputStream.writeAsync(byteArray)

            // This should work
            byteArray = ByteArray(4)
            outputStream.writeAsync(byteArray)

            // This should not
            try {
                outputStream.writeAsync(0)
                fail("Did not throw exception on EOF")
            } catch (e: IOException) {
                assertEquals("No space left on device", e.message)
            }

        }

    private fun runPseudoRandomWriteTest(
        testDevSize: Long,
        testBlockSize: Int,
        testBufferBlocks: Long,
    ) = runBlocking {
        val testDev = MemoryBufferBlockDeviceDriver(testDevSize, testBlockSize).apply {
            fillWithRandom()
        }

        val queueSlots = if (testBufferBlocks == 1L) 1 else 4
        val bufferBlocks = testBufferBlocks / queueSlots

        val outputStream =
            BlockDeviceOutputStream(
                testDev, coroutineScope = coroutineScope, bufferBlocks = bufferBlocks,
                queueSize = queueSlots
            )


        val byteBuffer = ByteBuffer.allocate(testDevSize.toInt())
        testDev.backingBuffer.copyInto(byteBuffer.array())
        byteBuffer.position(0)
        byteBuffer.limit(byteBuffer.capacity())

        outputStream.writeAsync(byteBuffer.array())
        outputStream.flushAsync()

        val byteArray2 = ByteArray(testDevSize.toInt())
        testDev.backingBuffer.copyInto(byteArray2)
        assertArrayEquals(byteBuffer.array(), byteArray2)
    }

    private fun runPseudoRandomSmallWritesTest(
        testDevSize: Long,
        testBlockSize: Int,
        testBufferBlocks: Long,
    ) = runBlocking {
        // Same as above, but instead of writing the entire buffer in one go, write it in small chunks of testBlockSize * testBufferBlocks
        val testDev = MemoryBufferBlockDeviceDriver(testDevSize, testBlockSize).apply {
            fillWithRandom()
        }

        val queueSlots = if (testBufferBlocks == 1L) 1 else 4
        val bufferBlocks = testBufferBlocks / queueSlots

        val outputStream =
            BlockDeviceOutputStream(
                testDev, coroutineScope = coroutineScope, bufferBlocks = bufferBlocks,
                queueSize = queueSlots
            )

        val byteBuffer = ByteBuffer.allocate(testDevSize.toInt())
        testDev.backingBuffer.copyInto(byteBuffer.array())
        byteBuffer.position(0)
        byteBuffer.limit(byteBuffer.capacity())

        while (byteBuffer.hasRemaining()) {
            val byteArray =
                ByteArray(
                    (testBlockSize * testBufferBlocks).coerceAtMost(
                        byteBuffer.remaining().toLong()
                    ).toInt()
                )
            byteBuffer.get(byteArray)
            outputStream.writeAsync(byteArray)
        }
        outputStream.flushAsync()

        val byteArray2 = ByteArray(testDevSize.toInt())
        testDev.backingBuffer.copyInto(byteArray2)
        assertArrayEquals(byteBuffer.array(), byteArray2)
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