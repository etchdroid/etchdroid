package eu.depau.etchdroid

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import eu.depau.etchdroid.massstorage.UsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.utils.exception.base.EtchDroidException
import eu.depau.etchdroid.utils.ktexts.safeParcelableExtra
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.Random

object Intents {
    const val START_JOB = "eu.depau.etchdroid.action.START_JOB"
    const val USB_PERMISSION = "eu.depau.etchdroid.action.USB_PERMISSION"
    const val SKIP_VERIFY = "eu.depau.etchdroid.action.CANCEL_VERIFY"
    const val JOB_PROGRESS = "eu.depau.etchdroid.broadcast.JOB_PROGRESS"
    const val ERROR = "eu.depau.etchdroid.broadcast.ERROR"
    const val FINISHED = "eu.depau.etchdroid.broadcast.FINISHED"
}

@Parcelize
data class JobStatusInfo(
    val sourceUri: Uri,
    val destDevice: UsbMassStorageDeviceDescriptor,
    val processedBytes: Long,
    val totalBytes: Long,
    val speed: Float = -1f,
    val jobId: Int,
    val isVerifying: Boolean = false,
    val exception: EtchDroidException? = null,
) : Parcelable {
    @IgnoredOnParcel
    val percent =
        if (totalBytes <= 0L) -1 else (processedBytes.toDouble() * 100 / totalBytes).toInt()
}

private fun mkIntent(
    packageContext: Context? = null, cls: Class<*>? = null,
) = if (packageContext != null && cls != null) Intent(packageContext, cls)
else Intent()

fun getConfirmOperationActivityIntent(
    sourceUri: Uri,
    destDevice: UsbMassStorageDeviceDescriptor,
    packageContext: Context? = null,
    cls: Class<*>? = null,
): Intent {
    return mkIntent(packageContext, cls).apply {
        data = sourceUri
        putExtra("sourceUri", sourceUri)
        putExtra("destDevice", destDevice)
    }
}

fun getStartJobIntent(
    sourceUri: Uri,
    destDevice: UsbMassStorageDeviceDescriptor,
    jobId: Int,
    offset: Long = 0L,
    verifyOnly: Boolean = false,
    packageContext: Context? = null,
    cls: Class<*>? = null,
): Intent {
    return mkIntent(packageContext, cls).apply {
        action = Intents.START_JOB
        data = sourceUri
        putExtra("sourceUri", sourceUri)
        putExtra("destDevice", destDevice)
        putExtra("jobId", jobId)
        putExtra("offset", offset)
        putExtra("verifyOnly", verifyOnly)
    }
}

fun getProgressUpdateIntent(
    sourceUri: Uri,
    destDevice: UsbMassStorageDeviceDescriptor,
    jobId: Int,
    speed: Float,
    processedBytes: Long,
    totalBytes: Long,
    isVerifying: Boolean = false,
    packageContext: Context? = null,
    cls: Class<*>? = null,
) = mkIntent(packageContext, cls).apply {
    action = Intents.JOB_PROGRESS
    putExtra("sourceUri", sourceUri)
    putExtra(
        "status", JobStatusInfo(
            sourceUri, destDevice, processedBytes, totalBytes, speed, jobId,
            isVerifying = isVerifying
        )
    )
}

fun getErrorIntent(
    sourceUri: Uri,
    destDevice: UsbMassStorageDeviceDescriptor,
    jobId: Int,
    processedBytes: Long,
    totalBytes: Long,
    exception: EtchDroidException,
    packageContext: Context? = null,
    cls: Class<*>? = null,
) = mkIntent(packageContext, cls).apply {
    action = Intents.ERROR
    putExtra("sourceUri", sourceUri)
    putExtra(
        "status",
        JobStatusInfo(
            sourceUri, destDevice, processedBytes, totalBytes,
            jobId = jobId, exception = exception
        )
    )
}

fun getFinishedIntent(
    sourceUri: Uri,
    destDevice: UsbMassStorageDeviceDescriptor,
    totalBytes: Long,
    packageContext: Context? = null,
    cls: Class<*>? = null,
) = mkIntent(packageContext, cls).apply {
    action = Intents.FINISHED
    putExtra("sourceUri", sourceUri)
    putExtra("status", JobStatusInfo(sourceUri, destDevice, totalBytes, totalBytes, jobId = -1))
}

fun Intent.getProgressActivityPendingIntent(
    context: Context, requestCode: Int = Random().nextInt(),
): PendingIntent = PendingIntent.getActivity(context, requestCode, this.apply {
    // Set data uri to propagate the permission
    if (data == null) data = safeParcelableExtra<Uri?>("sourceUri")

    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
}, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
