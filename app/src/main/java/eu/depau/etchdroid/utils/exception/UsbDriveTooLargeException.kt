package eu.depau.etchdroid.utils.exception

import android.content.Context
import eu.depau.etchdroid.R
import eu.depau.etchdroid.utils.exception.base.FatalException
import kotlinx.parcelize.Parcelize

@Parcelize
class UsbDriveTooLargeException :
    FatalException("The USB drive is too large and EtchDroid does not support it") {
    override fun getUiMessage(context: Context): String {
        return context.getString(R.string.the_usb_drive_is_too_large)
    }
}
