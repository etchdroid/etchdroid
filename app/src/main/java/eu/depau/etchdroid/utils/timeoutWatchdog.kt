package eu.depau.etchdroid.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

interface TimeoutBump {
    fun bump()
}

/** Thrown by [timeoutWatchdog] when [timeMillis] elapses without a [TimeoutBump.bump]. */
class TimeoutExpiredException : CancellationException("Timeout expired")

suspend fun <T> timeoutWatchdog(
    timeMillis: Long,
    block: suspend CoroutineScope.(TimeoutBump) -> T,
): T = coroutineScope {
    val expirationTime = AtomicLong(System.currentTimeMillis() + timeMillis)
    val bump = object : TimeoutBump {
        override fun bump() {
            expirationTime.set(System.currentTimeMillis() + timeMillis)
        }
    }

    val result = async {
        block(bump)
    }

    val cancelJob = launch {
        while (true) {
            val delayTime = expirationTime.get() - System.currentTimeMillis()
            if (delayTime <= 0) {
                result.cancel(TimeoutExpiredException())
                break
            } else {
                // Wait for the remaining time
                delay(delayTime)
            }
        }
    }

    result
        .apply { invokeOnCompletion { cancelJob.cancel() } }
        .await()
}
