package eu.depau.etchdroid.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.depau.etchdroid.R
import eu.depau.etchdroid.utils.exception.InitException
import eu.depau.etchdroid.utils.exception.UsbCommunicationException
import eu.depau.etchdroid.utils.exception.base.RecoverableException
import eu.depau.etchdroid.utils.exception.base.isUnplugged

@Composable
fun RecoverableExceptionExplanationCard(
    exception: RecoverableException,
    modifier: Modifier = Modifier,
) {
    val title = when {
        exception.isUnplugged -> stringResource(R.string.i_did_not_unplug_it)
        else -> stringResource(R.string.how_did_this_happen)
    }
    val body = when {
        exception.isUnplugged -> stringResource(R.string.perhaps_your_usb_port_is_dirty)
        exception is UsbCommunicationException || exception is InitException -> stringResource(
                R.string.usb_drives_are_unreliable
        )

        else -> stringResource(R.string.this_is_unexpected_it_s_probably_a_bug_please_report_it)
    }

    Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(3.dp),
            colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
    ) {
        Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
        ) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
            )
            Text(
                    text = body,
                    style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}