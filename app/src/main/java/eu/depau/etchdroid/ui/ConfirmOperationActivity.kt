package eu.depau.etchdroid.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.atLeastWrapContent
import androidx.core.net.toUri
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
import eu.depau.etchdroid.getProgressUpdateIntent
import eu.depau.etchdroid.getStartJobIntent
import eu.depau.etchdroid.massstorage.PreviewUsbDevice
import eu.depau.etchdroid.massstorage.UsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.plugins.telemetry.Telemetry
import eu.depau.etchdroid.service.WorkerService
import eu.depau.etchdroid.ui.composables.MainView
import eu.depau.etchdroid.ui.composables.ScreenSizeLayoutSelector
import eu.depau.etchdroid.ui.composables.appiumTag
import eu.depau.etchdroid.ui.utils.rememberPorkedAroundSheetState
import eu.depau.etchdroid.utils.broadcastReceiver
import eu.depau.etchdroid.utils.ktexts.formatID
import eu.depau.etchdroid.utils.ktexts.getFileName
import eu.depau.etchdroid.utils.ktexts.getFileSize
import eu.depau.etchdroid.utils.ktexts.registerExportedReceiver
import eu.depau.etchdroid.utils.ktexts.safeParcelableExtra
import eu.depau.etchdroid.utils.ktexts.startForegroundServiceCompat
import eu.depau.etchdroid.utils.ktexts.toHRSize
import eu.depau.etchdroid.utils.ktexts.toast
import eu.depau.etchdroid.utils.ktexts.usbDevice
import kotlin.random.Random

private const val TAG = "ConfirmOperationActivit"

class ConfirmOperationActivity : ActivityBase() {
    private val mViewModel: ConfirmOperationActivityViewModel by viewModels()
    private lateinit var mSettings: AppSettings
    private lateinit var mUsbPermissionIntent: PendingIntent


    private val mUsbDevicesReceiver = broadcastReceiver { intent ->
        val usbDevice: UsbDevice? = if (intent.usbDevice == null) {
            Log.w(TAG, "Received USB broadcast without device, using selected device: $intent")
            mViewModel.state.value.selectedDevice?.usbDevice
        } else {
            intent.usbDevice
        }
        if (usbDevice == null) {
            Log.w(
                TAG,
                "Received USB broadcast without device and no selected device, ignoring: $intent"
            )
            Telemetry.captureMessage("Received USB broadcast without device and no selected device")
            return@broadcastReceiver
        }

        Telemetry.addBreadcrumb {
            category = "usb"
            message = "Received USB broadcast: ${intent.action}, device: $usbDevice"
            data["intent.action"] = intent.action.toString()
            data["usb.device"] = usbDevice.toString()
        }

        when (intent.action) {
            Intents.USB_PERMISSION -> {
                // Since we're using an immutable PendingIntent as recommended by the latest API
                // we won't receive a USB device or grant status; we need to check back with the
                // USB manager
                val usbManager = getSystemService(USB_SERVICE) as UsbManager
                val granted = usbManager.hasPermission(usbDevice)

                Telemetry.addBreadcrumb {
                    category = "usb"
                    message = "USB permission granted: $granted"
                    data["usb.device"] = usbDevice.toString()
                    data["usb.permission"] = granted.toString()
                }
                mViewModel.setPermission(granted)

                if (!granted) {
                    toast(
                        getString(
                            R.string.permission_denied_for_usb_device, usbDevice.deviceName
                        )
                    )
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                if (usbDevice == mViewModel.state.value.selectedDevice?.usbDevice) {
                    Telemetry.addBreadcrumb {
                        category = "usb"
                        message = "Selected USB device was unplugged"
                        data["usb.device"] = usbDevice.toString()
                    }
                    toast(getString(R.string.usb_device_was_unplugged))
                    finish()
                } else {
                    Telemetry.addBreadcrumb("Unplugged USB device was not selected", "usb")
                }
            }

            else -> {
                Telemetry.captureMessage("Received unknown USB broadcast: ${intent.action}")
                Log.w(TAG, "Received unknown broadcast: ${intent.action}")
            }
        }
    }

    private fun registerUsbReceiver() {
        val usbPermissionFilter = IntentFilter(Intents.USB_PERMISSION)
        val usbDetachedFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

        registerExportedReceiver(mUsbDevicesReceiver, usbPermissionFilter)
        registerExportedReceiver(mUsbDevicesReceiver, usbDetachedFilter)
    }

    private fun unregisterUsbReceiver() {
        unregisterReceiver(mUsbDevicesReceiver)
    }

    override fun onStart() {
        super.onStart()
        registerUsbReceiver()
    }

    override fun onStop() {
        super.onStop()
        unregisterUsbReceiver()
    }

    private fun writeImage(uri: Uri, device: UsbMassStorageDeviceDescriptor) {
        val jobId = Random.nextInt()
        val intent =
            getStartJobIntent(uri, device, jobId, 0, false, this, WorkerService::class.java)
        Telemetry.addBreadcrumb {
            category = "flow"
            message = "Starting worker service; job ID: $jobId"
            data["intent"] = intent.toString()
            data["job.id"] = jobId.toString()
        }
        startForegroundServiceCompat(intent)
        startActivity(
            getProgressUpdateIntent(
                uri, device, jobId, 0f, 0, 0, false, this, ProgressActivity::class.java
            )
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openedImage = intent.safeParcelableExtra<Uri>("sourceUri") ?: run {
            Log.e(TAG, "No source image URI provided")
            toast(getString(R.string.no_image_uri_provided))
            finish()
            return
        }
        val selectedDevice =
            intent.safeParcelableExtra<UsbMassStorageDeviceDescriptor>("destDevice") ?: run {
                Log.e(TAG, "No destination device selected")
                toast(getString(R.string.no_destination_device_selected))
                finish()
                return
            }
        mViewModel.init(openedImage, selectedDevice)

        val imageFileName = openedImage.getFileName(this) ?: "unknown"
        Telemetry.addBreadcrumb {
            category = "flow"
            message = "Image opened"
            data["image.filename"] = imageFileName
        }

        Telemetry.configureScope {
            setTag("usb.vid", formatID(selectedDevice.usbDevice.vendorId))
            setTag("usb.pid", formatID(selectedDevice.usbDevice.productId))
            setTag("usb.vidpid", selectedDevice.vidpid)
            setTag("usb.name", selectedDevice.name)
            setTag("image.filename", imageFileName)
            try {
                setTag(
                    "image.size",
                    openedImage.getFileSize(this@ConfirmOperationActivity).toString()
                )
            } catch (_: Exception) {
                setTag("image.size", "unknown")
            }
        }

        // Use an immutable PendingIntent as recommended by the latest API
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        mUsbPermissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent().apply {
                action = Intents.USB_PERMISSION
            }, pendingIntentFlags
        )

        mSettings = AppSettings(this).apply {
            addListener(mViewModel)
            mViewModel.refreshSettings(this)
        }

        setContent {
            MainView(mViewModel) {
                val uiState by mViewModel.state.collectAsState()
                var showLayFlatDialog by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(uiState.selectedDevice) {
                    if (uiState.selectedDevice == null) {
                        toast(getString(R.string.usb_device_was_unplugged))
                        finish()
                    }
                }

                ConfirmationView(mViewModel, onConfirm = {
                    showLayFlatDialog = true
                }, onCancel = {
                    finish()
                }, askUsbPermission = {
                    val usbManager = getSystemService(USB_SERVICE) as UsbManager
//                    Monitoring.addBreadcrumb("Requesting USB permission", "usb")
                    Telemetry.addBreadcrumb {
                        category = "usb"
                        message = "Requesting USB permission: ${uiState.selectedDevice}"
                        data["usb.name"] = uiState.selectedDevice?.name ?: "unknown"
                        data["usb.vidpid"] = uiState.selectedDevice?.vidpid ?: "unknown"
                    }
                    usbManager.requestPermission(
                        uiState.selectedDevice!!.usbDevice, mUsbPermissionIntent
                    )
                })

                LaunchedEffect(uiState.hasUsbPermission, showLayFlatDialog) {
                    println("hasUsbPermission: ${uiState.hasUsbPermission}")
                }

                if (uiState.hasUsbPermission && showLayFlatDialog) {
                    LayFlatOnTableBottomSheet(
                        onReady = {
                            writeImage(
                                uiState.openedImage!!, uiState.selectedDevice!!
                            )
                        },
                        onDismissRequest = { showLayFlatDialog = false },
                    )
                }
            }
        }

    }
}

@Composable
fun ConfirmationViewLayout(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    sourceCard: @Composable (modifier: Modifier, fillMaxSize: Boolean) -> Unit,
    destinationCard: @Composable (modifier: Modifier) -> Unit,
    warningCard: @Composable () -> Unit,
    cancelButton: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable () -> Unit = {},
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
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Box(
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    title()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    sourceCard(Modifier.fillMaxWidth(), false)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            modifier = Modifier.size(48.dp),
                            imageVector = Icons.TwoTone.ArrowDownward,
                            contentDescription = null
                        )
                    }

                    destinationCard(Modifier.fillMaxWidth())
                }

                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        warningCard()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                    ) {
                        cancelButton()
                        confirmButton()
                    }
                }
            }
        },
        compact = {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    title()
                }

                ConstraintLayout(Modifier.fillMaxWidth()) {
                    val (sourceCardRef, arrowRef, destinationCardRef) = createRefs()
                    val chain =
                        if (LocalLayoutDirection.current == LayoutDirection.Ltr) createHorizontalChain(
                            sourceCardRef, arrowRef, destinationCardRef,
                            chainStyle = ChainStyle.Packed
                        ) else createHorizontalChain(
                            destinationCardRef, arrowRef, sourceCardRef,
                            chainStyle = ChainStyle.Packed
                        )
                    constrain(chain) {
                        start.linkTo(parent.start, 16.dp)
                        end.linkTo(parent.end, 16.dp)
                    }

                    sourceCard(
                        Modifier
                            .constrainAs(sourceCardRef) {
                                top.linkTo(parent.top, 16.dp)
                                bottom.linkTo(parent.bottom, 16.dp)
                                height = Dimension.fillToConstraints.atLeastWrapContent
                                width = Dimension.fillToConstraints
                                horizontalChainWeight = 1f
                            },
                        true
                    )

                    Icon(
                        modifier = Modifier
                            .size(48.dp)
                            .constrainAs(arrowRef) {
                                centerVerticallyTo(parent)
                                width = Dimension.value(48.dp)
                                horizontalChainWeight = 0f
                            },
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null
                    )

                    destinationCard(
                        Modifier
                            .constrainAs(destinationCardRef) {
                                top.linkTo(parent.top, 16.dp)
                                bottom.linkTo(parent.bottom, 16.dp)
                                height = Dimension.wrapContent
                                width = Dimension.fillToConstraints
                                horizontalChainWeight = 1f
                            }
                    )
                }

                warningCard()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                ) {
                    cancelButton()
                    confirmButton()
                }
            }
        }
    )

    content()
}

@Composable
fun ConfirmationView(
    viewModel: ConfirmOperationActivityViewModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    askUsbPermission: () -> Unit,
) {
    val uiState by viewModel.state.collectAsState()

    ConfirmationViewLayout(
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                text = stringResource(R.string.confirm_operation),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            )
        },
        sourceCard = { modifier, fillMaxSize ->
            Card(modifier) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        modifier = Modifier
                            .size(128.dp)
                            .padding(16.dp, 16.dp, 0.dp, 16.dp),
                        imageVector = ImageVector.vectorResource(
                            id = R.drawable.ic_disk_image_large
                        ), contentDescription = stringResource(R.string.disk_image)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically
                        )
                    ) {
                        val sourceUri = uiState.openedImage
                        val context = LocalContext.current
                        val sourceFileName by remember {
                            derivedStateOf {
                                sourceUri?.getFileName(context)
                                    ?: context.getString(R.string.unknown_filename)
                            }
                        }
                        val sourceFileSize by remember {
                            derivedStateOf {
                                try {
                                    sourceUri!!.getFileSize(context).toHRSize()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get file size", e)
                                    "Unknown file size"
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.image_to_write),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 8.dp)
                        )

                        Column {
                            Text(
                                text = sourceFileName,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sourceFileSize,
                                style = MaterialTheme.typography.labelMedium,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        destinationCard = { modifier ->
            Card(modifier) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        modifier = Modifier
                            .size(128.dp)
                            .padding(16.dp, 16.dp, 0.dp, 16.dp),
                        imageVector = ImageVector.vectorResource(
                            id = R.drawable.ic_usb_stick_large
                        ), contentDescription = stringResource(R.string.usb_drive)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.destination_usb_device),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Column {
                            Text(
                                text = uiState.selectedDevice?.name ?: stringResource(
                                    R.string.unknown_device
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.selectedDevice?.vidpid ?: "Unknown VID:PID",
                                style = MaterialTheme.typography.labelMedium,
                                softWrap = true,
                                fontStyle = FontStyle.Italic, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            modifier = Modifier.appiumTag("grantUsbPermissionButton"),
                            onClick = askUsbPermission,
                            enabled = !uiState.hasUsbPermission,
                            contentPadding = if (!uiState.hasUsbPermission) PaddingValues(
                                24.dp, 8.dp
                            )
                            else PaddingValues(24.dp, 8.dp, 16.dp, 8.dp),
                        ) {
                            Text(text = stringResource(R.string.grant_access))
                            if (uiState.hasUsbPermission) {
                                Icon(
                                    modifier = Modifier.padding(8.dp, 0.dp, 0.dp, 0.dp),
                                    imageVector = Icons.TwoTone.Check,
                                    contentDescription = stringResource(R.string.permission_granted)
                                )
                            }
                        }
                    }
                }
            }
        },
        warningCard = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp, 16.dp, 16.dp, 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(48.dp),
                        imageVector = Icons.TwoTone.Warning,
                        contentDescription = null
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.be_careful),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(
                                R.string.writing_the_image_will_erase
                            ), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        cancelButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.appiumTag("writeImageButton"),
                onClick = onConfirm,
                enabled = uiState.selectedDevice != null && uiState.hasUsbPermission
            ) {
                Text(text = stringResource(R.string.write_image))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayFlatOnTableBottomSheet(
    onReady: () -> Unit,
    onDismissRequest: () -> Unit,
    useGravitySensor: Boolean = true,
) {
    val context = LocalContext.current
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.animated_check))
    val sensorManager =
        remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) }
    val sheetState = rememberPorkedAroundSheetState(onDismissRequest, skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        if (sensor == null || !useGravitySensor) {
            Text(
                text = stringResource(R.string.lay_your_device_flat_no_accel),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp, 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                var hideSheet by remember { mutableStateOf(false) }
                LaunchedEffect(hideSheet) {
                    if (hideSheet) {
                        sheetState.hide()
                        onReady()
                    }
                }
                Button(onClick = { hideSheet = true }) {
                    Text(
                        modifier = Modifier.appiumTag("layFlatSkipButton"),
                        text = stringResource(R.string.continue_)
                    )
                }
            }
        } else {
            val readings = 10
            var hasBeenFlatOnTable by rememberSaveable { mutableStateOf(false) }
            var gravityCircularBuffer by remember { mutableStateOf(emptyList<Float>()) }

            val sensorListener = remember {
                object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

                    override fun onSensorChanged(event: SensorEvent) {
                        gravityCircularBuffer = gravityCircularBuffer + event.values[2]
                        if (gravityCircularBuffer.size > readings) {
                            gravityCircularBuffer = gravityCircularBuffer.drop(1)
                        }
                    }
                }
            }
            val movingAverage = remember(gravityCircularBuffer) {
                if (gravityCircularBuffer.size >= readings) gravityCircularBuffer.average()
                    .toFloat()
                else 0f
            }

            val isFlatOnTable by remember(movingAverage) {
                derivedStateOf {
                    movingAverage > 9.7f
                }
            }
            LaunchedEffect(isFlatOnTable) {
                if (isFlatOnTable) {
                    hasBeenFlatOnTable = true
                }
            }

            DisposableEffect(sensor, sensorListener, sensorManager) {
                println("Registering sensor listener")
                sensorManager.registerListener(
                    sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL
                )
                onDispose {
                    println("Unregistering sensor listener")
                    sensorManager.unregisterListener(sensorListener)
                }
            }

            Text(
                text = stringResource(R.string.lay_your_device_flat),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (!hasBeenFlatOnTable) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(128.dp)
                            .padding(15.dp),
                        strokeWidth = 5.dp,
                    )
                } else {
                    val progress by animateLottieCompositionAsState(
                        composition, isPlaying = hasBeenFlatOnTable
                    )
                    val lottieDynamicProperties = rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            property = LottieProperty.COLOR_FILTER, value = PorterDuffColorFilter(
                                MaterialTheme.colorScheme.primary.toArgb(), PorterDuff.Mode.SRC_ATOP
                            ), keyPath = arrayOf("**")
                        )
                    )
                    LottieAnimation(
                        composition, progress = { progress }, modifier = Modifier.size(128.dp),
                        dynamicProperties = lottieDynamicProperties
                    )
                    LaunchedEffect(progress) {
                        if (progress == 1f) {
                            sheetState.hide()
                            onReady()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp, 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                var hideSheet by remember { mutableStateOf(false) }
                LaunchedEffect(hideSheet) {
                    if (hideSheet) {
                        sheetState.hide()
                        onReady()
                    }
                }
                OutlinedButton(onClick = { hideSheet = true }) {
                    Text(
                        modifier = Modifier.appiumTag("layFlatSkipButton"),
                        text = stringResource(R.string.skip)
                    )
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ConfirmationViewPreview() {
    val viewModel = remember {
        ConfirmOperationActivityViewModel().apply {
            setState(
                state.value.copy(
                    selectedDevice = UsbMassStorageDeviceDescriptor(
                        previewUsbDevice = PreviewUsbDevice(
                            name = "Kingston DataTraveler 3.0",
                            vidpid = "dead:beef",
                        )
                    ),
                    openedImage = "file:///storage/emulated/0/Download/etchdroid-test-image-very-long-name-lol-rip-ive-never-seen-a-filename-this-long-its-absolutely-crazy.img".toUri(),
                )
            )
        }
    }
    var showLayFlatSheet by rememberSaveable { mutableStateOf(false) }

    MainView(viewModel) {
        ConfirmationView(viewModel, onCancel = {
            viewModel.setState(
                viewModel.state.value.copy(
                    hasUsbPermission = false
                )
            )
        }, onConfirm = {
            showLayFlatSheet = true
        }, askUsbPermission = {
            viewModel.setState(
                viewModel.state.value.copy(
                    hasUsbPermission = true
                )
            )
        })

        if (showLayFlatSheet) {
            LayFlatOnTableBottomSheet(
                onReady = { showLayFlatSheet = false },
                onDismissRequest = { showLayFlatSheet = false }, useGravitySensor = true
            )
        }
    }
}
