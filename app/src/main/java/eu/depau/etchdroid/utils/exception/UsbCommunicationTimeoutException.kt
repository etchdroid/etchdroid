package eu.depau.etchdroid.utils.exception

import kotlinx.parcelize.Parcelize

/**
 * The USB transfer stalled long enough for the IO watchdog to time out. Distinct from
 * [UsbCommunicationException] so it groups separately in telemetry, but inherits its
 * recoverable behavior and "the USB drive stopped responding" UI message.
 */
@Parcelize
class UsbCommunicationTimeoutException(
    override val cause: Throwable? = null,
) : UsbCommunicationException(cause)
