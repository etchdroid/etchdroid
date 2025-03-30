package eu.depau.etchdroid.ui.composables

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.appiumTag(tag: String): Modifier {
    return this
        .semantics { this.testTagsAsResourceId = true }
        .testTag(tag)
}