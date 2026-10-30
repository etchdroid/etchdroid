package eu.depau.etchdroid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.depau.etchdroid.utils.ktexts.getDisplayName
import eu.depau.etchdroid.utils.ktexts.isAccessible
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Behaves like ExternalStorageProvider after the volume holding the image was removed. */
class RemovedVolumeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = throw IllegalArgumentException(
        "Failed to determine if 80D9-10FE:image.iso is child of 80D9-10FE:"
    )

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?,
    ) = 0
}

@ExtendWith(RobolectricExtension::class)
@Config(application = EtchDroidApplication::class)
class UriQueryTest {
    @Test
    fun testUnreachableUriDoesNotThrow() {
        setUpMockTelemetry()
        Robolectric.setupContentProvider(RemovedVolumeProvider::class.java, "test.removed")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test.removed/document/80D9-10FE%3Aimage.iso")

        assertEquals("80D9-10FE:image.iso", uri.getDisplayName(context))
        assertFalse(uri.isAccessible(context))
    }
}
