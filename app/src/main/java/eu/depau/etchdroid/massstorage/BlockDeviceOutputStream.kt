package eu.depau.etchdroid.massstorage

import android.util.Log
import eu.depau.etchdroid.utils.AsyncOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BlockDeviceOutputStream"

private const val BARRIER_TAG = -0xBA881E8L

/**
 * Upper bound on how long [BlockDeviceOutputStream.closeAsync] waits for the I/O worker to
 * terminate. On a healthy device the worker exits in milliseconds once the channel is closed; the
 * timeout only trips if a native libusb transfer is genuinely stuck, in which case the job is
 * failing anyway and we must not block close() forever.
 */
private const val IO_THREAD_JOIN_TIMEOUT_MS = 10_000L

private const val TRACE_IO = false

/**
 * Helper function to trace I/O operations.
 *
 * @param msg The message to print if tracing is enabled.
 */
private fun BlockDeviceOutputStream.traceIo(msg: String) {
    if (TRACE_IO) println("OSTREAM: ${Thread.currentThread().name} time ${System.nanoTime()} pos $mCurrentOffset $msg")
}

/**
 * A buffered [AsyncOutputStream] that writes to a [BlockDeviceDriver].
 *
 * The stream uses a background worker thread to write the data to the block device while buffering
 * data coming from the API methods.
 *
 * The typical workflow is:
 * - write() appends data to the buffer
 * - When the buffer is full, a commit() operation is triggered, which places the buffer in a queue
 * - The background worker pops the buffer from the queue and writes it to the block device
 *
 * flush() can be used to force the buffer to be committed and written to the block device. This can
 * cause additional latency, since the non-written parts of the data buffer will have to be read
 * from the block device before the new data can be written. The flush() operation blocks until the
 * queue is empty.
 *
 * The stream is seekable. The seek() operation will flush(), then jump to the desired offset.
 *
 * @param blockDev The block device to write to.
 * @param coroutineScope The coroutine scope to use for the background worker thread.
 * @param bufferBlocks The number of blocks to buffer in memory before writing to the block device.
 * @param queueSize The size of the queue used to send write requests to the I/O thread.
 */
class BlockDeviceOutputStream(
    private val blockDev: BlockDeviceDriver,
    private val coroutineScope: CoroutineScope,
    private val bufferBlocks: Long = 512,
    queueSize: Int = 4,
) : AsyncOutputStream(), ISeekableStream {

    /**
     * The current block number from the start of the block device.
     */
    private var mCurrentBlockOffset: Long = 0

    /**
     * The current byte from the start of the block device.
     */
    internal val mCurrentOffset: Long
        get() = mCurrentBlockOffset * blockDev.blockSize + mByteBuffer.position()

    /**
     * The total size of the block device in bytes.
     */
    private val mSizeBytes: Long
        get() = blockDev.blocks * blockDev.blockSize.toLong()

    /**
     * The buffer used to store the data before it is written to the block device.
     */
    private var mByteBuffer = ByteBuffer.allocate(minOf((blockDev.blockSize.toLong() * bufferBlocks), mSizeBytes).toInt())

    /**
     * Whether the stream position is at or past the end of the block device.
     */
    private val isEOF: Boolean
        get() = mCurrentOffset >= mSizeBytes

    /**
     * Mutex that protects the mBlockChannel and mFlushRendezvous channels.
     */
    private val mChannelMutex = Mutex()

    /**
     * The channel (queue) used to send write requests to the I/O thread.
     */
    private val mBlockChannel = Channel<Pair<Long, ByteBuffer>>(queueSize)

    /**
     * The channel used by the I/O thread to signal that the queue is empty in order to unblock an
     * ongoing flush() operation.
     */
    private val mFlushRendezvous = Channel<Unit>()

    /**
     * Mutex that protects accesses to the underlying block device object.
     */
    private val blockDeviceMutex = Mutex()

    /**
     * Atomic flag that indicates whether the I/O thread is running.
     */
    private val mIoThreadRunning: AtomicBoolean = AtomicBoolean(false)

    /**
     * The coroutine running the I/O thread, so [closeAsync] can wait for it to fully terminate
     * (i.e. no native block-device transfer is still in flight) before returning.
     */
    private var mIoThreadJob: Job? = null

    /**
     * Atomic flag that indicates whether the stream has been closed.
     */
    private val closed = AtomicBoolean(false)

    /**
     * Makes sure the I/O thread is running, starting it if necessary. It is guaranteed that only
     * one instance of the I/O thread is running at any given time (if this isn't true, it's a bug).
     */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private suspend fun ensureIoThread() {
        if (mIoThreadRunning.get()) return

        val rendezvous = Channel<Unit>()

        mChannelMutex.withLock {
            mBlockChannel.let {
                if (it.isClosedForReceive) {
                    // Try to fetch the exception
                    while (!it.isEmpty) it.receive()
                    throw IllegalStateException("Channel closed for receive")
                } else if (it.isClosedForSend) {
                    it.send(Pair(BARRIER_TAG, ByteBuffer.allocate(0)))
                    throw IllegalStateException("Channel closed for send")
                }
            }
        }

        mIoThreadJob = coroutineScope.launch(Dispatchers.IO) {
            Thread.currentThread().name = "BlockDeviceOutputStream I/O thread"

            if (!mIoThreadRunning.compareAndSet(false, true)) {
                rendezvous.send(Unit)
                return@launch
            }

            // ensureIoThread() will return at this point
            rendezvous.send(Unit)

            traceIo("start")

            val blockChannel = mChannelMutex.withLock { mBlockChannel }
            val flushRendezvous = mChannelMutex.withLock { mFlushRendezvous }

            try {
                while (true) {
                    val (blockNumber, buffer) = blockChannel.receive()

                    if (blockNumber == BARRIER_TAG) {
                        traceIo("pop BARRIER")
                        flushRendezvous.send(Unit)
                        continue
                    }
                    traceIo("block $blockNumber pop size ${buffer.limit()}")

                    require(buffer.position() == 0) { "Buffer position must be 0" }
                    if (buffer.limit() == 0) continue

                    // Partial block write
                    if ((buffer.limit() % blockDev.blockSize) != 0) {
                        traceIo("block $blockNumber partial write")
                        val lastBlockNumber = blockNumber + buffer.limit() / blockDev.blockSize
                        val lastBlockOffset = buffer.limit() - buffer.limit() % blockDev.blockSize
                        val lastBlockBlankOffset = buffer.limit() % blockDev.blockSize

                        val partialData = ByteBuffer.allocate(blockDev.blockSize).apply {
                            blockDeviceMutex.withLock {
                                blockDev.read(lastBlockNumber, this)
                            }
                        }
                        buffer.apply {
                            position(lastBlockOffset + lastBlockBlankOffset)
                            limit(lastBlockOffset + blockDev.blockSize)
                            put(
                                partialData.array(), lastBlockBlankOffset, blockDev.blockSize - lastBlockBlankOffset
                            )
                            position(0)
                        }
                        traceIo("block $blockNumber partial write done")
                    }

                    traceIo("block $blockNumber write")
                    blockDeviceMutex.withLock {
                        blockDev.write(blockNumber, buffer)
                    }
                    traceIo("block $blockNumber write done")
                }
            } catch (e: ClosedReceiveChannelException) {
                // Channel closed, stop
            } catch (e: Exception) {
                closed.set(true)

                Log.e(TAG, "Exception in I/O thread", e)
                // Try to empty the channel to unblock any send() calls already in progress
                while (!blockChannel.isEmpty) blockChannel.tryReceive()

                blockChannel.close(e)
                flushRendezvous.close(e)
            }

            traceIo("end")

            // Only set the flag to false if no exception was thrown; exceptions are not recoverable
            mIoThreadRunning.set(false)
        }

        rendezvous.receive()
    }

    /**
     * Sends the current buffer to the I/O thread for writing to the block device. It does not wait
     * for the write to complete or for the queue to be empty.
     */
    private suspend fun commit() {
        if (mByteBuffer.position() == 0) return

        val oldBuffer = mByteBuffer.apply { flip() }
        // Capture before the buffer is handed off to the I/O thread, which may mutate its
        // limit (partial-block path below). Reading oldBuffer after channel.send is a data race.
        val committedLength = oldBuffer.limit()

        val newOffset = mCurrentOffset + committedLength
        val capacity = minOf(
            blockDev.blockSize.toLong() * bufferBlocks, mSizeBytes - newOffset
        )
        val newBuffer = ByteBuffer.allocate(capacity.toInt())

        if (oldBuffer.limit() % blockDev.blockSize != 0) {
            // Copy the unfinished block to the new buffer, without changing the position in the old buffer
            oldBuffer.apply {
                position(limit() - limit() % blockDev.blockSize)
                newBuffer.put(this)
                position(0)
            }
        }

        ensureIoThread()
        val channel = mChannelMutex.withLock { mBlockChannel }
        traceIo("block $mCurrentBlockOffset push size ${oldBuffer.limit()}")
        channel.send(Pair(mCurrentBlockOffset, oldBuffer))
        traceIo("block $mCurrentBlockOffset push done")
        mByteBuffer = newBuffer
        mCurrentBlockOffset += committedLength / blockDev.blockSize
    }

    /**
     * Ensures that all unwritten data is written to the block device. This method blocks until the
     * I/O thread has written all the data to the block device, and the write queue is empty.
     */
    override suspend fun flushAsync() {
        commit()
        ensureIoThread()
        val channel = mChannelMutex.withLock { mBlockChannel }
        channel.send(Pair(BARRIER_TAG, ByteBuffer.allocate(0)))
        val rendezvous = mChannelMutex.withLock { mFlushRendezvous }
        rendezvous.receive()
    }

    /**
     * Seeks to the given offset. This method may flush the buffer and block until the I/O thread
     * has written all the data to the block device, therefore expect delays when seeking.
     *
     * @param offset The offset to seek to.
     * @return The skipped distance.
     */
    override suspend fun seekAsync(offset: Long): Long {
        if (offset == 0L) return 0L

        val actualSkipDistance = when {
            mCurrentOffset + offset > mSizeBytes -> mSizeBytes - mCurrentOffset
            mCurrentOffset + offset < 0 -> -mCurrentOffset
            else -> offset
        }

        val newByteOffset = mCurrentOffset + actualSkipDistance
        val newBlockOffset = newByteOffset / blockDev.blockSize

        // Check if the new offset is within the written range of the current buffer
        if (newBlockOffset in mCurrentBlockOffset until mCurrentBlockOffset + mByteBuffer.position() / blockDev.blockSize) {
            val bufferByteOffset = (newBlockOffset - mCurrentBlockOffset) * blockDev.blockSize + newByteOffset
            mByteBuffer.position(bufferByteOffset.toInt())
            return actualSkipDistance
        }

        // Flush all the unwritten changes to disk
        flushAsync()

        // Jump to the closest block
        mCurrentBlockOffset = newBlockOffset
        mByteBuffer.clear()

        if (offset % blockDev.blockSize != 0L) {
            // Read the part between the start of the new block and the sought position
            val blockByteOffset = offset % blockDev.blockSize
            mByteBuffer.apply {
                position(0)
                limit(blockDev.blockSize)
                blockDeviceMutex.withLock {
                    withContext(Dispatchers.IO) {
                        blockDev.read(newBlockOffset, this@apply)
                    }
                }
                clear()
                position(blockByteOffset.toInt())
            }
        }

        return actualSkipDistance
    }

    /**
     * Writes a single byte to the stream.
     *
     * @param b The byte to write.
     */
    override suspend fun writeAsync(b: Int) {
        if (isEOF) throw IOException("No space left on device")

        mByteBuffer.put((b and 0xFF).toByte())
        if (!mByteBuffer.hasRemaining()) commit()
    }

    /**
     * Writes a portion of a byte array to the stream.
     *
     * @param b The byte array to write.
     * @param off The offset in the array to start writing from.
     * @param len The number of bytes to write.
     */
    override suspend fun writeAsync(b: ByteArray, off: Int, len: Int) {
        if (len <= 0 || off > b.size) return

        if (isEOF) throw IOException("No space left on device")

        val actualLen = len.coerceAtMost(b.size - off)
        var toWrite = actualLen

        while (toWrite > 0) {
            if (isEOF) throw IOException("No space left on device")

            val toWriteNow = toWrite.coerceAtMost(mByteBuffer.remaining())
            mByteBuffer.put(b, off + actualLen - toWrite, toWriteNow)
            toWrite -= toWriteNow

            if (toWrite > 0 && !mByteBuffer.hasRemaining()) commit()
        }

        if (!mByteBuffer.hasRemaining()) commit()
    }

    /**
     * Writes a byte array to the stream.
     *
     * @param b The byte array to write.
     */
    override suspend fun writeAsync(b: ByteArray) {
        writeAsync(b, 0, b.size)
    }

    /**
     * Closes the stream, blocking until all the data has been written to the block device.
     */
    override suspend fun closeAsync() {
        if (closed.getAndSet(true)) return
        flushAsync()
        val channel = mChannelMutex.withLock { mBlockChannel }
        channel.close()

        // Wait for the I/O worker to fully terminate so no native block-device transfer is still
        // in flight when the caller moves on (e.g. the write->verify handoff): the underlying
        // libusb handle is not safe against concurrent transfers and would crash natively.
        withTimeoutOrNull(IO_THREAD_JOIN_TIMEOUT_MS) { mIoThreadJob?.join() }
            ?: Log.w(TAG, "I/O worker did not terminate before close")
    }
}
