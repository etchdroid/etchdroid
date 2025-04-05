package eu.depau.etchdroid.utils.ktexts

import android.os.Build

val Thread.threadID: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        this.threadId()
    } else {
        @Suppress("DEPRECATION")
        this.id
    }
