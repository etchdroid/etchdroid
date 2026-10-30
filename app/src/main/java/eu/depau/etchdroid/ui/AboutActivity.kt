package eu.depau.etchdroid.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import eu.depau.etchdroid.AppSettings
import eu.depau.etchdroid.BuildConfig
import eu.depau.etchdroid.PRIVACY_URL
import eu.depau.etchdroid.R
import eu.depau.etchdroid.plugins.reviews.WriteReviewHelper
import eu.depau.etchdroid.plugins.telemetry.Telemetry
import eu.depau.etchdroid.ui.composables.MainView
import eu.depau.etchdroid.ui.composables.ScreenSizeLayoutSelector
import eu.depau.etchdroid.ui.composables.coloredShadow
import eu.depau.etchdroid.utils.ktexts.activity
import eu.depau.etchdroid.websiteUrl

class AboutActivity : ActivityBase() {
    private val mViewModel: ThemeViewModel by viewModels()
    private lateinit var mSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSettings = AppSettings(this).apply {
            addListener(mViewModel)
            mViewModel.refreshSettings(this)
        }

        setContent {
            MainView(viewModel = mViewModel) {
                AboutView(mViewModel)
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutViewLayout(
    modifier: Modifier = Modifier,
    appTitle: @Composable () -> Unit,
    versionInfo: @Composable () -> Unit,
    logo: @Composable () -> Unit,
    contributorsInfo: @Composable () -> Unit,
    actionButtons: @Composable () -> Unit,
    content: @Composable () -> Unit = {},
) {
    ScreenSizeLayoutSelector(
        modifier = modifier,
        normal = {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    appTitle()
                    versionInfo()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    logo()
                }

                contributorsInfo()

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    actionButtons()
                }

            }
        },
        compact = {
            Box(
                Modifier
                    .sizeIn(minWidth = 550.dp)
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .horizontalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(
                        48.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.padding(32.dp)) {
                        logo()
                    }
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        appTitle()
                        versionInfo()
                        contributorsInfo()

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            actionButtons()
                        }

                    }
                }
            }
        }
    )

    content()
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutView(viewModel: ThemeViewModel) {
    AboutViewLayout(
        modifier = Modifier.fillMaxSize(),
        appTitle = {
            Text(
                text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                textAlign = TextAlign.Center,
            )
        },
        versionInfo = {
            SelectionContainer {
                Text(
                    text = "${BuildConfig.APPLICATION_ID}\n v${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR}+${BuildConfig.BUILD_TYPE}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        fontFamily = FontFamily(Typeface.MONOSPACE)
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        },
        logo = {
            val darkMode by viewModel.darkMode
            val iconBackgroundColor = MaterialTheme.colorScheme.onSurfaceVariant
            Icon(
                modifier = Modifier
                    .size(128.dp)
                    .run {
                        if (darkMode) {
                            coloredShadow(
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                borderRadius = 64.dp, shadowRadius = 128.dp, alpha = 0.5f
                            )
                        } else {
                            drawBehind {
                                drawCircle(
                                    color = iconBackgroundColor, radius = 96.dp.toPx()
                                )
                            }
                        }
                    }, imageVector = getEtchDroidIcon(
                    headColor = if (darkMode) MaterialTheme.colorScheme.primary.toArgb()
                        .toLong() else MaterialTheme.colorScheme.primaryContainer.toArgb()
                        .toLong(),
                ), contentDescription = "EtchDroid", tint = Color.Unspecified
            )
        },
        contributorsInfo = {
            val annotatedText = buildAnnotatedString {
                val name = "Davide Depau"
                val contributors = stringResource(R.string.contributors)
                val github = "GitHub"
                val str = stringResource(R.string.developed_by, name, contributors, github)
                val nameStart = str.indexOf(name)
                val nameEnd = nameStart + name.length
                val contributorsStart = str.indexOf(contributors)
                val contributorsEnd = contributorsStart + contributors.length
                val githubStart = str.indexOf(github)
                val githubEnd = githubStart + github.length
                append(str)

                for ((start, end) in listOfNotNull(
                    // depau.eu links to donations, which Google Play forbids
                    (nameStart to nameEnd).takeUnless { BuildConfig.PLAY_STORE },
                    contributorsStart to contributorsEnd,
                    githubStart to githubEnd
                )) {
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                }
                if (!BuildConfig.PLAY_STORE) {
                    addLink(
                        LinkAnnotation.Url("https://depau.eu"),
                        nameStart,
                        nameEnd
                    )
                }
                addLink(
                    LinkAnnotation.Url("https://github.com/EtchDroid/EtchDroid/graphs/contributors"),
                    contributorsStart,
                    contributorsEnd
                )
                addLink(
                    LinkAnnotation.Url("https://github.com/etchdroid/etchdroid"),
                    githubStart,
                    githubEnd
                )

                if (!Telemetry.isStub) {
                    val privacyPolicyStr = stringResource(R.string.privacy_policy)
                    append("\n$privacyPolicyStr")
                    val privacyPolicyStart = str.length + 1
                    val privacyPolicyEnd = privacyPolicyStart + privacyPolicyStr.length
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = privacyPolicyStart,
                        end = privacyPolicyEnd
                    )
                    addLink(
                        LinkAnnotation.Url(PRIVACY_URL),
                        privacyPolicyStart,
                        privacyPolicyEnd
                    )
                }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = annotatedText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            )

        },
        actionButtons = {
            val activity = LocalContext.current.activity
            OutlinedButton(
                onClick = {
                    activity?.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            websiteUrl("/").toUri()
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.website))
            }
            if (!BuildConfig.PLAY_STORE) {
                OutlinedButton(
                    onClick = {
                        activity?.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://etchdroid.app/donate".toUri()
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.support_the_project))
                }
            }
            val reviewHelper = remember { activity?.let { WriteReviewHelper(it) } }
            if (reviewHelper != null) {
                OutlinedButton(onClick = { reviewHelper.launchReviewFlow() }) {
                    Text(
                        text = if (reviewHelper.isGPlayFlavor) stringResource(
                            R.string.write_a_review
                        )
                        else stringResource(R.string.star_on_github)
                    )
                }
            }
        },
    )
}


@PreviewScreenSizes
@Composable
fun AboutViewPreview() {
    val viewModel = remember { ThemeViewModel() }
    MainView(viewModel) {
        AboutView(viewModel)
    }
}