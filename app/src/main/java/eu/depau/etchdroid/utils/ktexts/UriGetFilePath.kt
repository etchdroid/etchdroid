package eu.depau.etchdroid.utils.ktexts

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import eu.depau.etchdroid.plugins.telemetry.Telemetry

/**
 * Get a file path from a Uri. This will get the path for Storage Access
 * Framework Documents, as well as the _data field for the MediaStore and
 * other file-based ContentProviders.
 *
 * @param context The context.
 * @author paulburke
 *
 * https://stackoverflow.com/a/27271131/1124621
 */
fun Uri.getFilePath(context: Context): String? {
    try {
        if (DocumentsContract.isDocumentUri(context, this)) {
            // DocumentProvider

            if (isExternalStorageDocument) {
                // ExternalStorageProvider

                val docId = DocumentsContract.getDocumentId(this)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                if (type.equals("primary", ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().path + "/" + split[1]
                }

                // TODO handle non-primary volumes

            } else if (isDownloadsDocument) {
                // DownloadsProvider

                val id = DocumentsContract.getDocumentId(this)

                if (id.startsWith("raw:/"))
                    return id.toUri().path

                return try {
                    val contentUri = ContentUris.withAppendedId(
                        "content://downloads/public_downloads".toUri(),
                        java.lang.Long.valueOf(id)
                    )
                    contentUri.getDataColumn(context, null, null)
                } catch (_: NumberFormatException) {
                    null
                }
            } else if (isMediaDocument) {
                // MediaProvider

                val docId = DocumentsContract.getDocumentId(this)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // Type check
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return contentUri?.getDataColumn(context, selection, selectionArgs)
            }

        } else if ("content".equals(scheme, ignoreCase = true)) {
            // MediaStore (and general)

            return getDataColumn(context, null, null)

        } else if ("file".equals(scheme, ignoreCase = true)) {
            // File

            return path
        }
    } catch (e: IllegalArgumentException) {
        Log.e("UriGetFilePath", "getFilePath: $e")
    } catch (e: Exception) {
        // TODO: Wrap into own exception to make debugging easier
        throw e
    }

    return null
}

/**
 * Get the value of the data column for this Uri. This is useful for
 * MediaStore Uris, and other file-based ContentProviders.
 *
 * @param context The context.
 * @param selection (Optional) Filter used in the query.
 * @param selectionArgs (Optional) Selection arguments used in the query.
 * @return The value of the _data column, which is typically a file path.
 */
fun Uri.getDataColumn(
    context: Context,
    selection: String?,
    selectionArgs: Array<String>?,
): String? {
    val column = "_data"
    val projection = arrayOf(column)

    try {
        context.contentResolver.query(this, projection, selection, selectionArgs, null)?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(column)
                return it.getString(columnIndex)
            }
        }
    } catch (e: SecurityException) {
        Telemetry.captureException("getDataColumn: Failed to query content resolver", e)
    }

    return null
}


/**
 * Whether the Uri authority is ExternalStorageProvider.
 */
val Uri.isExternalStorageDocument: Boolean
    get() = "com.android.externalstorage.documents" == authority

/**
 * Whether the Uri authority is DownloadsProvider.
 */
val Uri.isDownloadsDocument: Boolean
    get() = "com.android.providers.downloads.documents" == authority

/**
 * Whether the Uri authority is MediaProvider.
 */
val Uri.isMediaDocument: Boolean
    get() = "com.android.providers.media.documents" == authority