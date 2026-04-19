package eu.depau.etchdroid.massstorage

import android.util.Log
import eu.depau.etchdroid.utils.AsyncInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "BlockDeviceInputStream"

private const val TRACE_IO = false

/**
 * Helper function to trace I/O operations.
 *
 * @param msg The message to print if tracing is enabled.
 */
private fun BlockDeviceInputStream.traceIo(msg: String) {
    if (TRACE_IO) println("ISTREAM: ${Thread.currentThread().name} time ${System.nanoTime()} pos $mCurrentOffset $msg")
}

/**
 * An input stream that reads from a block device.
 *
 * A background thread asynchronously reads blocks from the block device starting at a given offset.
 * The blocks are then submitted to a channel, which the main thread reads from.
 *
 * The general idea is that, since this is an InputStream, API users will want to perform sequential
 * reads. Therefore, once a read() call tells us where to start reading, it is most likely
 * beneficial if the worker thread reads the next blocks in advance.
 *
 * The stream is seekable; seeking to a new position introduces a delay while the worker thread
 * finishes any ongoing reads and starts reading the new blocks.
 *
 * Marking and resetting is supported, but only one mark is kept; resetting to a marked position
 * causes the same delay as seeking.
 *
 * @param blockDev The block device to read from.
 * @param coroutineScope The coroutine scope to use for the I/O thread.
 * @param bufferBlocks The number of blocks to read at once.
 * @param prefetchBuffers The number of subsequent buffers to prefetch.
 */
class BlockDeviceInputStream(
    private val blockDev: BlockDeviceDriver,
    private val coroutineScope: CoroutineScope,
    private val bufferBlocks: Long = 512,
    private val prefetchBuffers: Int = 4,
) : AsyncInputStream(), ISeekableStream {
    private lateinit var mReadBuffer: ByteBuffer

    /**
     * The current block number from the start of the block device.
     */
    private var mCurrentBlockOffset: Long = 0

    /**
     * The current byte offset within the current block.
     */
    private val mCurrentByteOffset: Int
        get() = if (::mReadBuffer.isInitialized) mReadBuffer.position() else 0

    /**
     * The current byte offset from the start of the block device.
     */
    internal val mCurrentOffset: Long
        get() = mCurrentBlockOffset * blockDev.blockSize + mCurrentByteOffset

    /**
     * The size of the block device in bytes.
     */
    private val sizeBytes: Long
        get() = blockDev.blocks.toLong() * blockDev.blockSize

    /**
     * Whether the stream is at or past the end of the block device.
     */
    private val isEOF: Boolean
        get() = mCurrentOffset >= sizeBytes

    /**
     * The channel where the worker thread submits read blocks.
     */
    private lateinit var mBlockChannel: Channel<Pair<Long, ByteBuffer>>

    /**
     * The marked position.
     */
    private var markedPosition: Long? = null

    /**
     * The block number the worker thread should read next.
     */
    private val mReadNextBlockNumber: AtomicLong = AtomicLong(0)

    /**
     * The block number that is currently being read.
     */
    private val mInFlightBlockNumber: AtomicLong = AtomicLong(-1)

    /**
     * Whether the worker thread is running.
     */
    private val mIoThreadRunning: AtomicBoolean = AtomicBoolean(false)

    /**
     * Waits until the wanted block is available in the block channel and returns it. Note that the
     * returned data may not necessarily *start* at the wanted block, but it will contain it.
     *
     * @param wantedBlockNumber The block number to wait for.
     * @param timeoutMillis The timeout in milliseconds.
     * @return The starting block number and the data buffer.
     */
    private suspend fun waitAndGetBuffer(
        wantedBlockNumber: Long,
    ): Pair<Long, ByteBuffer> {
        if (!::mBlockChannel.isInitialized) throw IllegalStateException("Channel not initialized")

        while (true) {
            val (blockNumber, buffer) = mBlockChannel.receive()
            if (wantedBlockNumber in blockNumber until blockNumber + bufferBlocks)
                return Pair(blockNumber, buffer)
        }

        @Suppress("UNREACHABLE_CODE") throw IllegalStateException("Unreachable")
    }

    /**
     * Tries to get a buffer from the block channel without blocking. If the block channel is empty,
     * null is returned.
     * Note that the returned data may not necessarily *start* at the wanted block, but it will
     * contain it.
     *
     * @param wantedBlockNumber The block number to look for.
     * @return The starting block number and the data buffer, or null if the block is not available.
     */
    private fun tryGetBufferNonBlocking(wantedBlockNumber: Long): Pair<Long, ByteBuffer>? {
        if (!::mBlockChannel.isInitialized) return null
        while (true) {
            val result = mBlockChannel.tryReceive()
            if (result.isSuccess) {
                val (blockNumber, buffer) = result.getOrThrow()
                if (wantedBlockNumber in blockNumber until blockNumber + bufferBlocks) {
                    return Pair(blockNumber, buffer)
                }
            } else {
                return null
            }
        }
    }

    /**
     * Makes sure the I/O thread is running, starting it if necessary. It is guaranteed that only
     * one instance of the I/O thread is running at any given time (if this isn't true, it's a bug).
     */
    private suspend fun ensureIoThread() {
        if (mIoThreadRunning.get()) return

        val rendezvous = Channel<Unit>()

        coroutineScope.launch(Dispatchers.IO) {
            Thread.currentThread().name = "BlockDeviceInputStream I/O thread"

            try {
                if (!mIoThreadRunning.compareAndSet(false, true)) {
                    rendezvous.send(Unit)
                    return@launch
                }

                if (!::mBlockChannel.isInitialized) {
                    mBlockChannel = Channel(prefetchBuffers)
                } else {
                    // Consume any exceptions from the previous channel
                    val oldChannel = mBlockChannel
                    mBlockChannel = Channel(prefetchBuffers)

                    oldChannel.close()
                    try {
                        while (true) {
                            oldChannel.receive()
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Ignore
                    }
                }

                rendezvous.send(Unit)

                traceIo("start")

                while (true) {
                    val blockNumber = mReadNextBlockNumber.getAndAdd(bufferBlocks)
                    if (blockNumber !in 0 until blockDev.blocks) break
                    mInFlightBlockNumber.set(blockNumber)

                    traceIo("read $blockNumber buffer $bufferBlocks blocks start")

                    try {
                        val buffer = ByteBuffer.allocate(
                            (sizeBytes - (blockNumber * blockDev.blockSize))
                                .coerceAtMost(blockDev.blockSize.toLong() * bufferBlocks)
                                .toInt()
                        )
                        blockDev.read(blockNumber, buffer)
                        buffer.flip()
                        mBlockChannel.send(Pair(blockNumber, buffer))

                    } catch (_: ClosedSendChannelException) {
                        // Channel closed, stop
                        break
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Exception in I/O thread while reading blocks from $blockNumber to ${blockNumber + bufferBlocks}",
                            e
                        )
                        throw e
                    } finally {
                        traceIo("read $blockNumber buffer $bufferBlocks blocks end")
                    }
                }
            } catch (e: Exception) {
                mBlockChannel.close(e)
            }

            traceIo("end")

            mInFlightBlockNumber.set(-1)
            mIoThreadRunning.set(false)
        }

        rendezvous.receive()
    }

    private fun isInFlight(blockNumber: Long): Boolean {
        val inFlightBlockNumber = mInFlightBlockNumber.get()
        if (inFlightBlockNumber < 0)
            return false
        return blockNumber in inFlightBlockNumber until inFlightBlockNumber + bufferBlocks
    }

    /**
     * Seeks to the given position.
     *
     * @param position The absolute position to seek to.
     */
    private suspend fun setPosition(position: Long) {
        require(position >= 0) { "Position must be >= 0" }

        val newPosition = position.coerceAtMost(sizeBytes)

        if (newPosition == sizeBytes) {
            // EOF
            mCurrentBlockOffset = blockDev.blocks
            mReadBuffer = ByteBuffer.allocate(0)
            return
        }

        val newBlockOffset = newPosition / blockDev.blockSize
        val newByteOffset = newPosition % blockDev.blockSize

        // See if the new offset is within the current buffer
        if (::mReadBuffer.isInitialized) {
            if (newBlockOffset in mCurrentBlockOffset until mCurrentBlockOffset + bufferBlocks) {
                val bufferByteOffset = (newBlockOffset - mCurrentBlockOffset) * blockDev.blockSize + newByteOffset
                mReadBuffer.position(bufferByteOffset.toInt())
                return
            }
        }

        traceIo("request $newBlockOffset start")

        // Try finding the desired block in the channel
        var blockResult = tryGetBufferNonBlocking(newBlockOffset)

        // If it's not there, request it explicitly
        if (blockResult == null) {
            if (!isInFlight(newBlockOffset)) {
                traceIo("request $newBlockOffset not prefetched, requesting")
                mReadNextBlockNumber.set(newBlockOffset)
            } else {
                traceIo("request $newBlockOffset not prefetched, in flight")
            }
            ensureIoThread()
            blockResult = waitAndGetBuffer(newBlockOffset)
        } else {
            traceIo("request $newBlockOffset prefetched")
        }

        val (blockNumber, buffer) = blockResult

        traceIo("request $newBlockOffset end got $blockNumber size ${buffer.remaining()}")

        // Recompute the byte offset and set it as the buffer position
        val bufferByteOffset = (newBlockOffset - blockNumber) * blockDev.blockSize + newByteOffset
        buffer.position(bufferByteOffset.toInt())

        mCurrentBlockOffset = blockNumber
        mReadBuffer = buffer
    }

    /**
     * Makes sure the read buffer is initialized and that there is data to read.
     */
    private suspend fun ensureBuffer() {
        if (!::mReadBuffer.isInitialized) setPosition(mCurrentOffset)
        if (!isEOF && mReadBuffer.remaining() == 0) loadNextBuffer()
    }

    /**
     * Loads the next buffer from the block device.
     */
    private suspend fun loadNextBuffer() {
        setPosition((mCurrentBlockOffset + bufferBlocks) * blockDev.blockSize)
    }

    /**
     * Seeks to the given offset.
     *
     * @param offset The offset to seek to.
     * @return The skipped distance.
     */
    override suspend fun seekAsync(offset: Long): Long {
        if (offset == 0L) return 0L

        val actualSkipDistance = when {
            mCurrentOffset + offset > sizeBytes -> sizeBytes - mCurrentOffset
            mCurrentOffset + offset < 0 -> -mCurrentOffset
            else -> offset
        }

        val position = mCurrentOffset + actualSkipDistance

        if (position < 0) throw IOException("Cannot seek to position $position")
        if (position > sizeBytes) throw IOException("End of stream reached")

        setPosition(position)

        return actualSkipDistance
    }

    /**
     * Skips the given number of bytes.
     *
     * @param n The number of bytes to skip.
     * @return The number of bytes actually skipped.
     */
    override suspend fun skipAsync(n: Long): Long = seekAsync(n)

    /**
     * Reads a single byte.
     *
     * @return The byte read, or -1 if EOF.
     * @throws IOException If an I/O error occurs.
     */
    override suspend fun readAsync(): Int {
        if (isEOF) return -1

        ensureBuffer()

        val result = mReadBuffer.get().toInt() and 0xFF

        if (mReadBuffer.remaining() == 0) loadNextBuffer()

        return result
    }

    /**
     * Reads up to len bytes into the given buffer.
     *
     * @param b The buffer to read into.
     * @param off The offset in the buffer to start writing.
     * @param len The maximum number of bytes to read.
     * @return The number of bytes read, or -1 if EOF.
     * @throws IOException If an I/O error occurs.
     */
    override suspend fun readAsync(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (isEOF) return -1

        ensureBuffer()

        val bytesToRead = minOf(len.toLong(), sizeBytes - mCurrentByteOffset, b.size.toLong() - off)
        var bytesRead = 0L

        while (bytesRead < bytesToRead) {
            if (isEOF) break

            val bytesRemaining = bytesToRead - bytesRead
            val bytesInBuffer = mReadBuffer.remaining().toLong()
            val bytesToCopy = minOf(bytesRemaining, bytesInBuffer).toInt()

            mReadBuffer.get(b, (off + bytesRead).toInt(), bytesToCopy)
            bytesRead += bytesToCopy

            if (bytesRead < bytesToRead) loadNextBuffer()
        }

        return bytesRead.toInt()
    }

    /**
     * Reads up to b.size bytes into the given buffer.
     *
     * @param b The buffer to read into.
     * @return The number of bytes read, or -1 if EOF.
     * @throws IOException If an I/O error occurs.
     */
    override suspend fun readAsync(b: ByteArray): Int = readAsync(b, 0, b.size)

    /**
     * Returns the number of bytes available for reading without blocking.
     *
     * @return The number of bytes available.
     */
    override fun available(): Int {
        if (!::mReadBuffer.isInitialized) return 0
        return mReadBuffer.remaining()
    }

    /**
     * Closes the stream, shutting down the I/O thread.
     */
    override suspend fun closeAsync() {
        setPosition(sizeBytes) // Move to EOF
        mBlockChannel.close()

        try {
            while (true) {
                mBlockChannel.receive()
            }
        } catch (e: ClosedReceiveChannelException) {
            // Ignore
        }
    }

    /**
     * Marks the current position in the stream.
     *
     * @param readlimit Ignored.
     */
    override fun mark(readlimit: Int) {
        markedPosition = mCurrentOffset
    }

    /**
     * Resets the stream to the marked position.
     *
     * @throws IOException If the stream is not marked.
     */
    override suspend fun resetAsync() = markedPosition.let {
        if (it == null) throw IOException("Stream not marked")
        setPosition(it)
    }

    override fun markSupported(): Boolean = true
    override suspend fun markSupportedAsync(): Boolean = true
}
