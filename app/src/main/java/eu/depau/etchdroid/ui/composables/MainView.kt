package eu.depau.etchdroid.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import eu.depau.etchdroid.ui.IThemeViewModel
import eu.depau.etchdroid.ui.theme.EtchDroidTheme


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainView(
    viewModel: IThemeViewModel<*>,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val uiState by viewModel.state.collectAsState()
    val darkMode by viewModel.darkMode

    EtchDroidTheme(
        darkTheme = darkMode, dynamicColor = uiState.dynamicColors
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(snackbarHost = snackbarHost) { contentPadding ->
                contentPadding // silence unused warning
                Box(Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}
