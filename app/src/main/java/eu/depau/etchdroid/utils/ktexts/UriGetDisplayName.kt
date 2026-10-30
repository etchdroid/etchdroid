package eu.depau.etchdroid.utils.ktexts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import eu.depau.etchdroid.plugins.telemetry.Telemetry

const val TAG = "UriGetFileNameExt"

fun Uri.getDisplayName(context: Context): String? {
    var result: String? = null

    if (this.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(this, null, null, null, null)
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    val colIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    result = it.getString(colIndex)
                }
            }
        } catch (e: Exception) {
            // Remote providers may throw anything (lost grant, removed volume, provider bugs):
            // fall back to the URI path
            Telemetry.warning("getDisplayName: Failed to query content resolver: $e")
        }
    }
    if (result == null) {
        result = this.path
        val cut = result!!.lastIndexOf('/')
        if (cut != -1) {
            result = result.substring(cut + 1)
        }
    }

    return result
}

/**
 * Whether the provider still lets us query this URI. False when the read grant is gone or the
 * volume holding the file was removed, in which case the file can't be read either.
 */
fun Uri.isAccessible(context: Context): Boolean {
    if (scheme != ContentResolver.SCHEME_CONTENT) return true
    return try {
        context.contentResolver.query(this, null, null, null, null)?.close()
        true
    } catch (e: Exception) {
        Telemetry.warning("Source URI is not accessible: $e")
        false
    }
}