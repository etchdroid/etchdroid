package eu.depau.etchdroid.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.depau.etchdroid.R
import eu.depau.etchdroid.utils.exception.base.RecoverableException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconnectUsbDriveDialog(exception: RecoverableException) {
    BasicAlertDialog(
        onDismissRequest = { },
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .padding(all = 24.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {
                val context = LocalContext.current
                Text(
                    text = exception.getUiMessage(context),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.Companion.CenterHorizontally),
                    textAlign = TextAlign.Companion.Center,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.to_recover_unplug),
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .align(Alignment.Companion.CenterHorizontally)
                        .weight(weight = 1f, fill = false),
                    textAlign = TextAlign.Companion.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                val vectorRes = if (LocalLayoutDirection.current == LayoutDirection.Ltr)
                    ImageVector.Companion.vectorResource(R.drawable.unplug_reconnect_accept)
                else
                    ImageVector.Companion.vectorResource(R.drawable.unplug_reconnect_accept_rtl)
                Image(
                    imageVector = vectorRes,
                    contentDescription = stringResource(
                        R.string.representation_of_the_required_steps
                    ),
                    contentScale = ContentScale.Companion.Fit,
                    colorFilter = ColorFilter.Companion.tint(AlertDialogDefaults.iconContentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(vectorRes.defaultWidth / vectorRes.defaultHeight)
                        .padding(horizontal = 32.dp)
                )

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )

                RecoverableExceptionExplanationCard(exception = exception)

                // Add a little bit of space so the vertical scroll doesn't clip the shadow
                Spacer(modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}