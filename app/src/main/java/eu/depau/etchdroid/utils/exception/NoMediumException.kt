package eu.depau.etchdroid.utils.exception

import android.content.Context
import eu.depau.etchdroid.R
import kotlinx.parcelize.Parcelize

@Parcelize
class NoMediumException : InitException("No medium present in LUN 0 of the USB drive") {
    override fun getUiMessage(context: Context): String {
        return context.getString(R.string.no_medium_in_usb_drive)
    }
}
