package eu.depau.etchdroid.utils.exception.base

import eu.depau.etchdroid.utils.ktexts.rootCause
import me.jahnen.libaums.libusbcommunication.LibusbError
import me.jahnen.libaums.libusbcommunication.LibusbException

abstract class RecoverableException(message: String, cause: Throwable? = null) :
    EtchDroidException(message, cause)

val RecoverableException.isUnplugged: Boolean
    get() = rootCause is LibusbException && (rootCause as LibusbException).libusbError == LibusbError.NO_DEVICE

/**
 * A known non-app-fault: a recoverable EtchDroid error or a libusb hardware error
 * anywhere in the cause chain, unless the chain also contains a programming error
 * (NPE, IllegalArgument/IllegalState incl. coroutine cancellation, IndexOutOfBounds,
 * ClassCast), which the worker may have wrapped. Used to de-prioritize such failures
 * in telemetry.
 */
fun Throwable?.isEnvironmentalFault(): Boolean {
    var t = this
    var environmental = false
    val seen = HashSet<Throwable>()
    while (t != null && seen.add(t)) {
        when (t) {
            is NullPointerException, is IllegalArgumentException, is IllegalStateException,
            is IndexOutOfBoundsException, is ClassCastException -> return false
            is RecoverableException, is LibusbException -> environmental = true
        }
        t = t.cause
    }
    return environmental
}
