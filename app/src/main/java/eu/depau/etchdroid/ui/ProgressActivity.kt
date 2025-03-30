package eu.depau.etchdroid.ui

import android.Manifest.permission
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import eu.depau.etchdroid.AppSettings
import eu.depau.etchdroid.Intents
import eu.depau.etchdroid.R
import eu.depau.etchdroid.getStartJobIntent
import eu.depau.etchdroid.massstorage.EtchDroidUsbMassStorageDevice.Companion.isMassStorageDevice
import eu.depau.etchdroid.massstorage.UsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.massstorage.doesNotMatch
import eu.depau.etchdroid.plugins.reviews.WriteReviewHelper
import eu.depau.etchdroid.plugins.telemetry.Telemetry
import eu.depau.etchdroid.plugins.telemetry.Telemetry.telemetryTag
import eu.depau.etchdroid.plugins.telemetry.TelemetryLevel
import eu.depau.etchdroid.plugins.telemetry.TelemetryTraced
import eu.depau.etchdroid.service.WorkerService
import eu.depau.etchdroid.ui.composables.GifImage
import eu.depau.etchdroid.ui.composables.KeepScreenOn
import eu.depau.etchdroid.ui.composables.MainView
import eu.depau.etchdroid.ui.composables.ReconnectUsbDriveDialog
import eu.depau.etchdroid.ui.composables.RecoverableExceptionExplanationCard
import eu.depau.etchdroid.ui.composables.ScreenSizeLayoutSelector
import eu.depau.etchdroid.ui.utils.rtlMirror
import eu.depau.etchdroid.utils.broadcastReceiver
import eu.depau.etchdroid.utils.exception.InitException
import eu.depau.etchdroid.utils.exception.MissingPermissionException
import eu.depau.etchdroid.utils.exception.NotEnoughSpaceException
import eu.depau.etchdroid.utils.exception.UsbCommunicationException
import eu.depau.etchdroid.utils.exception.VerificationFailedException
import eu.depau.etchdroid.utils.exception.base.FatalException
import eu.depau.etchdroid.utils.exception.base.RecoverableException
import eu.depau.etchdroid.utils.ktexts.activity
import eu.depau.etchdroid.utils.ktexts.broadcastLocally
import eu.depau.etchdroid.utils.ktexts.getDisplayName
import eu.depau.etchdroid.utils.ktexts.registerExportedReceiver
import eu.depau.etchdroid.utils.ktexts.startForegroundServiceCompat
import eu.depau.etchdroid.utils.ktexts.toHRSize
import eu.depau.etchdroid.utils.ktexts.toast
import eu.depau.etchdroid.utils.ktexts.usbDevice
import kotlinx.coroutines.delay
import me.jahnen.libaums.libusbcommunication.LibusbError
import me.jahnen.libaums.libusbcommunication.LibusbException

private const val TAG = "ProgressActivity"
private const val LAST_NOTIFICATION_TIMEOUT = 11 * 1000L

class ProgressActivity : ActivityBase() {
    private lateinit var mSettings: AppSettings
    private val mViewModel: ProgressActivityViewModel by viewModels()

    private val mNotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private var mPermissionAsked = false
    private val mNotificationPermissionRequester =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Telemetry.addBreadcrumb {
                message = "Notifications permission granted: $granted"
                category = "notifications"
                data["notifications.granted"] = granted
            }
            mViewModel.setNotificationsPermission(granted)
        }

    private val mBroadcastReceiver = broadcastReceiver { intent ->
        mViewModel.updateFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mBroadcastReceiver, IntentFilter().apply {
                addAction(Intents.JOB_PROGRESS)
                addAction(Intents.ERROR)
                addAction(Intents.FINISHED)
            })
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }

    private fun refreshNotificationsPermission() {
        mViewModel.setNotificationsPermission(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) mNotificationManager.areNotificationsEnabled()
            else true
        )
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (mNotificationManager.areNotificationsEnabled()) return refreshNotificationsPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !mPermissionAsked && shouldShowRequestPermissionRationale(
                permission.POST_NOTIFICATIONS
            )
        ) {
            mPermissionAsked = true
            Telemetry.addBreadcrumb("Requesting notifications runtime permission", "notifications")
            return mNotificationPermissionRequester.launch(permission.POST_NOTIFICATIONS)
        }

        Telemetry.addBreadcrumb("Opening system settings to enable notifications", "notifications")
        startActivity(Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", packageName)
                putExtra("app_uid", applicationInfo.uid)
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println(
            "ProgressActivity running in thread ${Thread.currentThread().name} (${Thread.currentThread().id})"
        )

        mSettings = AppSettings(this).apply {
            mViewModel.refreshSettings(this)
        }
        refreshNotificationsPermission()
        mViewModel.updateFromIntent(intent)

        Telemetry.configureScope {
            val state = mViewModel.state.value
            setTag("job.id", state.jobId.toString())
            setTag("job.isVerifying", state.isVerifying.toString())
            setTag("usb.device", state.destDevice?.name ?: "null")
            setTag("usb.vidpid", state.destDevice?.vidpid ?: "null")
        }

        setContent {
            MainView(mViewModel) {
                val appState by mViewModel.state.collectAsState()

                if (appState.jobState == JobState.IN_PROGRESS) {
                    LaunchedEffect(key1 = appState.lastNotificationTime) {
                        delay(LAST_NOTIFICATION_TIMEOUT)
                        if (System.currentTimeMillis() - appState.lastNotificationTime >= LAST_NOTIFICATION_TIMEOUT) {
                            mViewModel.setTimeoutError()
                        }
                    }
                }

                when (appState.jobState) {
                    JobState.IN_PROGRESS, JobState.RECOVERABLE_ERROR -> {
                        BackHandler { println("Ignoring back button") }
                        TelemetryTraced("job_in_progress_screen") {
                            JobInProgressView(mViewModel, requestNotificationsPermission = {
                                requestNotificationsPermission()
                            }, dismissNotificationsBanner = {
                                mSettings.showNotificationsBanner = false
                            }, cancelVerification = {
                                Telemetry.addBreadcrumb("User skipped verification", "flow")
                                Intent(Intents.SKIP_VERIFY).broadcastLocally(this@ProgressActivity)
                            })
                        }
                    }

                    JobState.SUCCESS -> {
                        BackHandler { finish() }
                        TelemetryTraced("success_screen") {
                            SuccessView()
                        }
                    }

                    JobState.FATAL_ERROR -> {
                        TelemetryTraced("fatal_error_screen") {
                            FatalErrorView(
                                exception = appState.exception!! as FatalException,
                                imageUri = appState.sourceUri!!,
                                jobId = appState.jobId,
                                device = appState.destDevice!!
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun JobInProgressViewLayout(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subTitle: @Composable () -> Unit,
    graphic: @Composable (Modifier) -> Unit,
    showNotificationsBanner: Boolean,
    notificationsBanner: @Composable () -> Unit,
    progress: @Composable ColumnScope.() -> Unit,
) {

    ScreenSizeLayoutSelector(
        modifier = modifier,
        normal = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Column(
                    modifier = Modifier
                        .padding(16.dp, 48.dp, 16.dp, 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    title()
                    subTitle()
                }

                ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
                    val (graphicRef, notificationsBannerRef) = createRefs()

                    graphic(
                        Modifier
                            .fillMaxWidth()
                            .constrainAs(graphicRef) {
                                centerTo(parent)
                            })

                    if (showNotificationsBanner) {
                        Box(modifier = Modifier.constrainAs(notificationsBannerRef) {
                            bottom.linkTo(graphicRef.bottom)
                            centerHorizontallyTo(parent)
                        }) {
                            notificationsBanner()
                        }
                    }
                }

                Box {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .wrapContentSize(Alignment.TopStart)
                            .widthIn(max = CONTENT_WIDTH)
                            .align(Alignment.Center)
                            .fillMaxWidth()
                    ) {
                        progress()
                    }
                }
            }
        },
        compact = {
            Row(
                modifier = modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                ConstraintLayout(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight()
                ) {
                    val (graphicRef, notificationsBannerRef) = createRefs()

                    graphic(
                        Modifier
                            .constrainAs(graphicRef) {
                                centerTo(parent)
                            }
                    )

                    if (showNotificationsBanner) {
                        Box(modifier = Modifier.constrainAs(notificationsBannerRef) {
                            bottom.linkTo(graphicRef.bottom)
                            centerHorizontallyTo(parent)
                        }) {
                            notificationsBanner()
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(16.dp, 48.dp, 16.dp, 16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        title()
                        subTitle()
                    }
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        progress()
                    }
                }
            }
        }
    )
}

@Composable
fun JobInProgressView(
    viewModel: ProgressActivityViewModel,
    requestNotificationsPermission: () -> Unit = {},
    dismissNotificationsBanner: () -> Unit = {},
    cancelVerification: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()

    KeepScreenOn()
    JobInProgressViewLayout(
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                modifier = Modifier.testTag(
                    if (uiState.isVerifying)
                        "write_progress_title"
                    else
                        "verify_progress_title"
                ),
                text = if (uiState.isVerifying) stringResource(
                    R.string.verifying_image
                ) else stringResource(
                    R.string.writing_image
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                textAlign = TextAlign.Center
            )
        },
        subTitle = {
            Text(
                text = stringResource(R.string.please_avoid_using_your_device),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        graphic = { modifier ->
            var clickLastTime by remember { mutableStateOf(0L) }
            var clickCount by remember { mutableStateOf(0) }
            var easterEgg by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - clickLastTime < 500) {
                            clickCount++
                            if (clickCount >= 5) {
                                clickCount = 0
                                easterEgg = !easterEgg
                                Telemetry.addBreadcrumb {
                                    message = "Easter egg activated: $easterEgg"
                                    category = "easter_egg"
                                    level = TelemetryLevel.DEBUG
                                }
                            }
                        } else {
                            clickCount = 0
                        }
                        clickLastTime = now
                    }
                    .then(modifier)
            ) {
                if (easterEgg) {
                    GifImage(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 56.dp, start = 16.dp, end = 16.dp),
                        gifRes = if (uiState.isVerifying) R.drawable.win_xp_verify else R.drawable.win_xp_copy
                    )
                } else {
                    Box(Modifier.rtlMirror()) {
                        ConstraintLayout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(256.dp)
                        ) {
                            val (usbDrive, imagesRow, magnifyingGlass) = createRefs()

                            val numberOfImages = 3
                            val imageWidth = 64
                            val density = LocalDensity.current
                            var rowSize by remember { mutableStateOf(IntSize.Zero) }
                            val repeatWidth by remember(rowSize) {
                                derivedStateOf {
                                    val dpWidth = with(density) { rowSize.width.toDp() }.value
                                    (dpWidth - (imageWidth * numberOfImages)) / (numberOfImages - 1) + imageWidth
                                }
                            }

                            val anchorTransition =
                                rememberInfiniteTransition(label = "anchorTransition")
                            val anchor by anchorTransition.animateValue(
                                initialValue = if (uiState.isVerifying) 100.dp else (100 + repeatWidth).dp,
                                targetValue = if (uiState.isVerifying) (100 + repeatWidth).dp else 100.dp,
                                typeConverter = TwoWayConverter(
                                    convertToVector = {
                                        AnimationVector1D(
                                            it.value
                                        )
                                    },
                                    convertFromVector = { it.value.dp }),
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "anchor"
                            )

                            Row(
                                modifier = Modifier
                                    .constrainAs(imagesRow) {
                                        centerVerticallyTo(usbDrive)
                                        if (uiState.isVerifying) {
                                            absoluteLeft.linkTo(usbDrive.absoluteLeft, anchor)
                                        } else {
                                            absoluteRight.linkTo(usbDrive.absoluteRight, anchor)
                                        }
                                    }
                                    .padding(bottom = 32.dp)
                                    .fillMaxWidth()
                                    .onSizeChanged {
                                        rowSize = it
                                    }, horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (i in 0 until numberOfImages) Icon(
                                    imageVector = ImageVector.vectorResource(
                                        id = R.drawable.ic_disk_image_large
                                    ),
                                    modifier = Modifier.size(imageWidth.dp),
                                    contentDescription = "",
                                )
                            }

                            val bgColor = MaterialTheme.colorScheme.background
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    id = R.drawable.ic_usb_stick_large
                                ),
                                modifier = Modifier
                                    .constrainAs(usbDrive) {
                                        centerTo(parent)
                                    }
                                    .padding(
                                        // If we are verifying, the USB drive should be on the left
                                        // Invert if we're in RTL mode
                                        if (!(uiState.isVerifying xor (LocalLayoutDirection.current == LayoutDirection.Ltr)))
                                            PaddingValues(end = 128.dp)
                                        else
                                            PaddingValues(start = 128.dp)
                                    )
                                    .drawBehind {
                                        drawRect(
                                            bgColor,
                                            topLeft = with(density) {
                                                Offset(
                                                    88.dp.toPx(),
                                                    22.dp.toPx()
                                                )
                                            },
                                            size = with(density) {
                                                DpSize(
                                                    80.dp,
                                                    180.dp
                                                ).toSize()
                                            })
                                    }
                                    .size(256.dp),
                                contentDescription = "",
                            )

                            if (uiState.isVerifying) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(
                                        id = R.drawable.ic_magnifying_glass
                                    ),
                                    modifier = Modifier
                                        .constrainAs(magnifyingGlass) {
                                            top.linkTo(imagesRow.top, 24.dp)
                                            absoluteLeft.linkTo(usbDrive.absoluteLeft, 224.dp)
                                        }
                                        .size(96.dp),
                                    contentDescription = "",
                                )
                            }
                        }
                    }
                }
            }

        },
        showNotificationsBanner = uiState.showNotificationsBanner && !uiState.notificationsPermission,
        notificationsBanner = {
            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.elevatedCardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.would_you_like_to_be_notified),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.telemetryTag("notifications_dismiss_button"),
                            onClick = dismissNotificationsBanner
                        ) {
                            Telemetry.addBreadcrumb(
                                "User dismissed notifications banner",
                                "notifications"
                            )
                            Text(text = stringResource(R.string.no_thanks))
                        }
                        Button(
                            modifier = Modifier.telemetryTag("notifications_enable_button"),
                            onClick = requestNotificationsPermission
                        ) {
                            Telemetry.addBreadcrumb(
                                "User requested notifications",
                                "notifications"
                            )
                            Text(text = stringResource(R.string.sure))
                        }
                    }
                }
            }
        },
        progress = {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (uiState.isVerifying) stringResource(R.string.verifying) else stringResource(
                            R.string.copying
                        )
                    )
                    Text(
                        text = " " + if (uiState.isVerifying) uiState.destDevice?.name
                        else uiState.sourceUri?.getDisplayName(context),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (uiState.isVerifying) stringResource(
                            R.string.against
                        ) else stringResource(R.string.to)
                    )
                    Text(
                        text = " " + if (uiState.isVerifying) uiState.sourceUri?.getDisplayName(
                            context
                        )
                        else uiState.destDevice?.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    text = if (uiState.percent >= 0) "${uiState.processedBytes.toHRSize()} / ${uiState.totalBytes.toHRSize()}" + " ${if (uiState.isVerifying) "verified" else "written"}, ${uiState.speed.toHRSize()}/s"
                    else stringResource(R.string.getting_ready),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (uiState.percent >= 0) {
                LinearProgressIndicator(
                    progress = { uiState.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.isVerifying) {
                OutlinedButton(
                    modifier = Modifier
                        .testTag("skip_verification_button")
                        .telemetryTag("skip_verification_button")
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    onClick = cancelVerification
                ) {
                    Text(text = stringResource(R.string.skip_verification))
                }
            }
        },
    )

    if (uiState.jobState == JobState.RECOVERABLE_ERROR && uiState.exception != null && uiState.exception is RecoverableException) {
        AutoJobRestarter(
            uiState.sourceUri!!,
            uiState.jobId,
            uiState.isVerifying,
            uiState.destDevice!!,
            uiState.processedBytes
        )
        ReconnectUsbDriveDialog(exception = uiState.exception as RecoverableException)
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuccessViewLayout(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    animation: @Composable () -> Unit,
    buttons: @Composable FlowRowScope.() -> Unit,
    bottomCard: @Composable () -> Unit,
) {
    ScreenSizeLayoutSelector(
        modifier = modifier,
        normal = {
            Column(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart)
                    .widthIn(max = CONTENT_WIDTH)
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Box(
                    Modifier
                        .padding(48.dp, 48.dp, 48.dp, 16.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    title()
                }

                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    animation()
                }

                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        buttons()
                    }
                    bottomCard()
                }
            }
        },
        compact = {
            Row(
                modifier = modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    animation()
                }

                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    title()
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        buttons()
                    }
                    bottomCard()
                }
            }
        }
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuccessView() {
    SuccessViewLayout(
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                modifier = Modifier.testTag("success_write_title"),
                text = stringResource(R.string.image_written_successfully),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            )
        },
        animation = {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.animated_check)
            )

            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .rtlMirror(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                val progress by animateLottieCompositionAsState(composition)
                val lottieDynamicProperties = rememberLottieDynamicProperties(
                    rememberLottieDynamicProperty(
                        property = LottieProperty.COLOR_FILTER,
                        value = PorterDuffColorFilter(
                            MaterialTheme.colorScheme.primary.toArgb(),
                            PorterDuff.Mode.SRC_ATOP
                        ),
                        keyPath = arrayOf("**")
                    )
                )
                LottieAnimation(
                    composition, progress = { progress }, modifier = Modifier.size(256.dp),
                    dynamicProperties = lottieDynamicProperties
                )

            }
        },
        buttons = {
            val activity = LocalContext.current.activity
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
            OutlinedButton(onClick = {
                activity?.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://etchdroid.app/donate/".toUri()
                    )
                )
            }) {
                Text(stringResource(R.string.support_the_project))
            }
            val context = LocalContext.current
            OutlinedButton(onClick = {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                activity?.finish()
            }) {
                Text(stringResource(R.string.write_another_image))
            }
        },
        bottomCard = {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.got_an_unsupported_drive_notification),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    val annotatedString = buildAnnotatedString {
                        val learnMoreStr = stringResource(R.string.learn_what_it_means)
                        val str = stringResource(R.string.it_s_safe_to_ignore, learnMoreStr)
                        val startIndex = str.indexOf(learnMoreStr)
                        val endIndex = startIndex + learnMoreStr.length
                        append(str)
                        addStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ), start = startIndex, end = endIndex
                        )
                        addLink(
                            LinkAnnotation.Url("https://etchdroid.app/broken_usb/"),
                            startIndex,
                            endIndex
                        )
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = annotatedString,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FatalErrorViewLayout(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    message: @Composable () -> Unit,
    suggestion: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    buttons: @Composable FlowRowScope.() -> Unit,
) {
    ScreenSizeLayoutSelector(
        modifier = modifier,
        normal = {
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart)
                    .widthIn(max = CONTENT_WIDTH)
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    title()
                    message()
                    suggestion()
                    icon()

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        buttons()
                    }
                }
            }
        },
        compact = {
            Row(
                modifier = modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    title()
                    message()
                    suggestion()

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        buttons()
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FatalErrorView(
    exception: FatalException,
    imageUri: Uri,
    jobId: Int,
    device: UsbMassStorageDeviceDescriptor,
) {
    FatalErrorViewLayout(
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                modifier = Modifier.testTag("fatal_error_title"),
                text = stringResource(R.string.there_was_an_error),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            )
        },
        message = {
            val context = LocalContext.current
            Text(
                text = exception.getUiMessage(context),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        suggestion = {
            if (exception !is NotEnoughSpaceException) {
                Text(
                    text = stringResource(R.string.please_report_fatal_issue),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        icon = {
            Icon(
                modifier = Modifier.size(256.dp),
                imageVector = ImageVector.vectorResource(
                    id = R.drawable.ic_write_to_usb_failed_large
                ),
                contentDescription = stringResource(R.string.error),
            )
        },
        buttons = {
            val context = LocalContext.current
            val activity = context.activity
            OutlinedButton(onClick = {
                activity?.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/etchdroid/etchdroid/issues".toUri()
                    )
                )
            }) {
                Text(text = stringResource(R.string.view_github_issues))
            }

            if (exception is VerificationFailedException && activity != null) {
                Button(onClick = {
                    val serviceIntent = getStartJobIntent(
                        imageUri,
                        device,
                        jobId,
                        0,
                        false,
                        activity,
                        WorkerService::class.java
                    )
                    Log.d(TAG, "Starting service with intent: $serviceIntent")
                    activity.startForegroundServiceCompat(serviceIntent)
                }) {
                    Text(stringResource(R.string.try_again))
                }
            }

            Button({
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                activity?.finish()
            }) {
                Text(text = stringResource(R.string.start_over))
            }
        }
    )
}

@Composable
fun AutoJobRestarter(
    imageUri: Uri,
    jobId: Int,
    isVerifying: Boolean,
    expectedDevice: UsbMassStorageDeviceDescriptor,
    resumeOffset: Long,
) {
    val context = LocalContext.current
    val activity = remember { context.activity } ?: run {
        Log.e(TAG, "AutoJobRestarter: activity not found")
        return
    }
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val forceFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
    } else {
        0
    }
    val pendingIntentFlags =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or forceFlag
        else 0
    val pendingIntent = remember {
        PendingIntent.getBroadcast(
            activity, 0, Intent(Intents.USB_PERMISSION), pendingIntentFlags
        )
    }

    fun onUsbAttached(usbDevice: UsbDevice) {
        expectedDevice.findMatchingForNew(usbDevice) ?: return
        usbManager.requestPermission(usbDevice, pendingIntent)
    }

    val broadcastReceiver = remember {
        broadcastReceiver { intent ->
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val usbDevice = intent.usbDevice!!
                    if (!usbDevice.isMassStorageDevice) return@broadcastReceiver
                    if (usbDevice doesNotMatch expectedDevice.usbDevice) {
                        activity.toast(
                            context.getString(R.string.plug_in_the_same_usb), Toast.LENGTH_SHORT
                        )
                    } else {
                        onUsbAttached(usbDevice)
                    }
                }

                Intents.USB_PERMISSION -> {
                    val usbDevice = intent.usbDevice!!
                    if (!usbDevice.isMassStorageDevice) return@broadcastReceiver
                    val msd = expectedDevice.findMatchingForNew(usbDevice)
                    if (msd == null) {
                        activity.toast(
                            context.getString(R.string.plug_in_the_same_usb), Toast.LENGTH_SHORT
                        )
                        return@broadcastReceiver
                    }

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) {
                        activity.toast(
                            context.getString(
                                R.string.permission_denied_for_usb_device, usbDevice.deviceName
                            )
                        )
                    } else {
                        activity.toast(context.getString(R.string.usb_device_reconnected_resuming))
                        val serviceIntent = getStartJobIntent(
                            imageUri,
                            msd,
                            jobId,
                            resumeOffset,
                            isVerifying,
                            activity,
                            WorkerService::class.java
                        )
                        Log.d(TAG, "Starting service with intent: $serviceIntent")
                        activity.startForegroundServiceCompat(serviceIntent)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        Log.d(TAG, "Registering broadcast receiver")
        activity.apply {
            registerExportedReceiver(broadcastReceiver, IntentFilter(Intents.USB_PERMISSION))
            registerExportedReceiver(
                broadcastReceiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            )
            for (usbDevice in usbManager.deviceList.values) onUsbAttached(usbDevice)
        }
        onDispose {
            Log.d(TAG, "Unregistering broadcast receiver")
            activity.unregisterReceiver(broadcastReceiver)
        }
    }
}

@PreviewScreenSizes
@Composable
fun SuccessViewPreview() {
    val viewModel = remember { ProgressActivityViewModel() }
    MainView(viewModel) {
        SuccessView()
    }
}

@PreviewScreenSizes
@Composable
fun WriteErrorViewPreview() {
    val viewModel = remember { ProgressActivityViewModel() }
    MainView(viewModel) {
        FatalErrorView(
            exception = NotEnoughSpaceException(1234, 5678),
            Uri.EMPTY,
            1337,
            UsbMassStorageDeviceDescriptor()
        )
    }
}

@PreviewScreenSizes
@Composable
fun VerificationErrorViewPreview() {
    val viewModel = remember { ProgressActivityViewModel() }
    MainView(viewModel) {
        FatalErrorView(
            exception = VerificationFailedException(),
            Uri.EMPTY,
            1337,
            UsbMassStorageDeviceDescriptor()
        )
    }
}

@PreviewScreenSizes
@Composable
fun ExceptionCardsPreview() {
    val viewModel = remember { ProgressActivityViewModel() }
    MainView(viewModel) {
        LazyColumn(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                RecoverableExceptionExplanationCard(
                    exception = UsbCommunicationException(
                        LibusbException("yolo", LibusbError.NO_DEVICE)
                    )
                )
            }
            item {
                RecoverableExceptionExplanationCard(
                    exception = UsbCommunicationException()
                )
            }
            item {
                RecoverableExceptionExplanationCard(
                    exception = InitException("yolo")
                )
            }
            item {
                RecoverableExceptionExplanationCard(
                    exception = MissingPermissionException()
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ReconnectUsbDriveDialogPreview() {
    val viewModel = remember { ProgressActivityViewModel() }
    MainView(viewModel) {
        ReconnectUsbDriveDialog(
            UsbCommunicationException(LibusbException("yolo", LibusbError.NO_DEVICE))
        )
    }
}

@PreviewScreenSizes
@Composable
fun ProgressViewPreview() {
    val viewModel = remember {
        ProgressActivityViewModel().apply {
            setState(state.value.copy(showNotificationsBanner = false))
        }
    }

    val progressTransition = rememberInfiniteTransition(label = "progressTransition")
    val progress by progressTransition.animateFloat(
        initialValue = 0f, targetValue = 2f, animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    LaunchedEffect(progress) {
        viewModel.setState(
            viewModel.state.value.copy(
                percent = ((progress * 100) % 100).toInt(),
                processedBytes = ((progress * 1000000000) % 1000000000).toLong(),
                totalBytes = 1000000000,
                speed = 10000000f,
                isVerifying = progress > 1
            )
        )
    }

    MainView(viewModel) {
        JobInProgressView(
            viewModel,
            dismissNotificationsBanner = {
                viewModel.setState(
                    viewModel.state.value.copy(showNotificationsBanner = false)
                )
            },
            cancelVerification = {
                viewModel.setState(
                    viewModel.state.value.copy(showNotificationsBanner = true)
                )
            }
        )
    }
}