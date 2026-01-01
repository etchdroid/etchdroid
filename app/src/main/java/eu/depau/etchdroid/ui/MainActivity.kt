package eu.depau.etchdroid.ui

import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Clear
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import eu.depau.etchdroid.AppSettings
import eu.depau.etchdroid.PRIVACY_URL
import eu.depau.etchdroid.R
import eu.depau.etchdroid.ThemeMode
import eu.depau.etchdroid.getConfirmOperationActivityIntent
import eu.depau.etchdroid.massstorage.IUsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.massstorage.UsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.plugins.telemetry.Telemetry
import eu.depau.etchdroid.plugins.telemetry.TelemetryLevel
import eu.depau.etchdroid.ui.composables.MainView
import eu.depau.etchdroid.ui.composables.ScreenSizeLayoutSelector
import eu.depau.etchdroid.ui.composables.appiumTag
import eu.depau.etchdroid.ui.composables.coloredShadow
import eu.depau.etchdroid.ui.theme.notSupportedRed
import eu.depau.etchdroid.ui.theme.partiallySupportedYellow
import eu.depau.etchdroid.ui.theme.supportedGreen
import eu.depau.etchdroid.ui.utils.rememberPorkedAroundSheetState
import eu.depau.etchdroid.utils.broadcastReceiver
import eu.depau.etchdroid.utils.ktexts.getFileName
import eu.depau.etchdroid.utils.ktexts.registerExportedReceiver
import eu.depau.etchdroid.utils.ktexts.usbDevice

private const val TAG = "MainActivity"


class MainActivity : ActivityBase() {
    private val mViewModel: MainActivityViewModel by viewModels()
    private lateinit var mSettings: AppSettings

    private val mFilePickerActivity: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            Telemetry.addBreadcrumb {
                message = "User selected file: $result"
                level = TelemetryLevel.DEBUG
                category = "flow"
                data["image.filename"] = result?.getFileName(this@MainActivity) ?: "null"
            }
            openUri(result ?: return@registerForActivityResult)
        }

    private fun ActivityResultLauncher<Array<String>>.launch() = launch(arrayOf("*/*"))

    private val mUsbDevicesReceiver = broadcastReceiver { intent ->

        if (intent.usbDevice == null) {
            Log.w(TAG, "Received USB broadcast without device, ignoring: $intent")
            return@broadcastReceiver
        }

        Log.d(
            TAG,
            "Received USB broadcast: $intent, action: ${intent.action}, device: ${intent.usbDevice}"
        )

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                mViewModel.usbDeviceAttached(intent.usbDevice!!)
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                mViewModel.usbDeviceDetached(intent.usbDevice!!)
            }

            else -> {
                Log.w(TAG, "Received unknown broadcast: ${intent.action}")
            }
        }
    }

    private fun registerUsbReceiver() {
        val usbAttachedFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        val usbDetachedFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

        registerExportedReceiver(mUsbDevicesReceiver, usbAttachedFilter)
        registerExportedReceiver(mUsbDevicesReceiver, usbDetachedFilter)

        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        mViewModel.replaceUsbDevices(usbManager.deviceList.values)
    }

    private fun unregisterUsbReceiver() {
        unregisterReceiver(mUsbDevicesReceiver)
    }

    private fun launchConfirmationActivity(
        selectedDevice: UsbMassStorageDeviceDescriptor,
        openedImage: Uri,
    ) {
        startActivity(
            getConfirmOperationActivityIntent(
                openedImage,
                selectedDevice,
                this,
                ConfirmOperationActivity::class.java
            )
        )
    }

    private fun openUri(uri: Uri, userSaysYolo: Boolean = false) {
        val filename = uri.getFileName(this)
        Telemetry.addBreadcrumb {
            message = "Opening image: $filename"
            category = "flow"
            level = TelemetryLevel.DEBUG
            data["image.filename"] = filename ?: "null"
            data["bypass_windows_alert"] = userSaysYolo.toString()
        }

        if (!userSaysYolo) {
            try {
                // Check if it's a Windows image and alert the user; fail-open
                filename?.let {
                    val lowercase = it.lowercase()
                    if ("windows" in lowercase || lowercase.startsWith("win")) {
                        Telemetry.addBreadcrumb {
                            message = "Windows image detected"
                            category = "flow"
                            level = TelemetryLevel.DEBUG
                            data["image.filename"] = it
                        }

                        mViewModel.setShowWindowsAlertUri(uri)
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w("Failed to get filename", e)
            }
        }
        mViewModel.setOpenedImage(uri)

        if (mViewModel.state.value.massStorageDevices.size == 1) {
            Telemetry.addBreadcrumb {
                message = "Only one USB device, opening confirmation activity"
                category = "flow"
            }

            launchConfirmationActivity(
                mViewModel.state.value.massStorageDevices.first(),
                uri
            )
        }
    }


    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mViewModel.apply {
            setTelemetry(Telemetry.enabled)
            setTelemetryShown(!Telemetry.isStub)
        }

        mSettings = AppSettings(this).apply {
            addListener(mViewModel)
            mViewModel.refreshSettings(this)
        }

        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { openUri(it) }
        }

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            var telemetryDialogOpen by remember { mutableStateOf(false) }

            fun setTelemetry(enabled: Boolean) {
                Telemetry.setEnabled(this.applicationContext, enabled)
                mViewModel.setTelemetry(enabled)
            }


            MainView(mViewModel, snackbarHost = { SnackbarHost(snackbarHostState) }) {
                StartView(
                    mViewModel,
                    setThemeMode = { mSettings.themeMode = it },
                    setDynamicTheme = { mSettings.dynamicColors = it },
                    onCTAClick = {
                        Telemetry.addBreadcrumb {
                            message = "User clicked on Write an image"
                            category = "flow"
                            level = TelemetryLevel.DEBUG
                        }
                        mFilePickerActivity.launch()
                    },
                    openAboutView = {
                        startActivity(Intent(this, AboutActivity::class.java))
                    },
                    toggleTelemetry = {
                        if (Telemetry.enabled) {
                            telemetryDialogOpen = true
                        } else {
                            setTelemetry(true)
                        }
                    }
                )
                val uiState by mViewModel.state.collectAsState()
                if (uiState.showWindowsAlertForUri != null) {
                    WindowsImageAlertDialog(
                        onDismissRequest = { mViewModel.setShowWindowsAlertUri(null) },
                        onConfirm = { openUri(uiState.showWindowsAlertForUri!!, true) })
                }
                if (uiState.openedImage != null) {
                    UsbDevicePickerBottomSheet(
                        onDismissRequest = {
                            mViewModel.setOpenedImage(null)
                        },
                        selectDevice = {
                            Telemetry.addBreadcrumb {
                                message =
                                    "User selected USB device, opening confirmation activity: $it"
                                category = "flow"
                                level = TelemetryLevel.INFO
                                data["usb.device"] = it.toString()
                                data["usb.name"] = it.name
                                data["usb.vidpid"] = it.vidpid
                            }

                            launchConfirmationActivity(
                                it as UsbMassStorageDeviceDescriptor,
                                mViewModel.state.value.openedImage!!,
                            )
                        },
                        availableDevices = { uiState.massStorageDevices }
                    )
                }

                if (telemetryDialogOpen) {
                    TelemetryAlertDialog(
                        onDismissRequest = { telemetryDialogOpen = false },
                        onOptOut = { setTelemetry(false) },
                        onCancel = { setTelemetry(true) }
                    )
                }

                if (!Telemetry.isStub && mSettings.showTelemetryBanner) {
                    mSettings.showTelemetryBanner = false
                    LaunchedEffect(null) {
                        val result = snackbarHostState.showSnackbar(
                            getString(R.string.etchdroid_uses_telemetry),
                            actionLabel = getString(R.string.learn_more),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            telemetryDialogOpen = true
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerUsbReceiver()
    }

    override fun onStop() {
        super.onStop()
        unregisterUsbReceiver()
    }
}

@Composable
fun StartViewLayout(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    logo: @Composable () -> Unit,
    mainButton: @Composable () -> Unit,
    bottomButton: @Composable () -> Unit,
    menuButton: @Composable () -> Unit,
    dropdownMenu: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    ScreenSizeLayoutSelector(
        modifier = modifier,
        normal = {
            ConstraintLayout(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart)
                    .widthIn(max = CONTENT_WIDTH)
                    .align(Alignment.Center)
                    .fillMaxSize()
            ) {
                val (titleRef, centerBoxRef, bottomButtonRef, menuButtonRef) = createRefs()
                Box(
                    modifier = Modifier.constrainAs(titleRef) {
                        top.linkTo(parent.top, 24.dp)
                        start.linkTo(parent.start, 16.dp)
                        end.linkTo(parent.end, 16.dp)
                    }
                ) {
                    title()
                }

                Column(
                    modifier = Modifier.constrainAs(centerBoxRef) {
                        top.linkTo(parent.top, 16.dp)
                        bottom.linkTo(parent.bottom, 16.dp)
                        start.linkTo(parent.start, 16.dp)
                        end.linkTo(parent.end, 16.dp)
                    }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(64.dp)
                ) {
                    logo()
                    mainButton()
                }

                Box(modifier = Modifier.constrainAs(bottomButtonRef) {
                    bottom.linkTo(parent.bottom, 16.dp)
                    start.linkTo(parent.start, 16.dp)
                    end.linkTo(parent.end, 16.dp)
                }) {
                    bottomButton()
                }

                Box(modifier = Modifier.constrainAs(menuButtonRef) {
                    bottom.linkTo(parent.bottom, 16.dp)
                    end.linkTo(parent.end, 16.dp)
                }) {
                    menuButton()
                    dropdownMenu()
                }
            }
        },
        compact = {
            ConstraintLayout(
                Modifier
                    .sizeIn(minWidth = 550.dp)
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                val (mainBox, bottomButtonRef) = createRefs()

                Row(
                    modifier = Modifier.constrainAs(mainBox) {
                        centerTo(parent)
                    },
                    horizontalArrangement = Arrangement.spacedBy(
                        80.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.padding(32.dp)) {
                        logo()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        title()
                        mainButton()
                        bottomButton()
                    }
                }

                Box(
                    modifier = Modifier.constrainAs(bottomButtonRef) {
                        bottom.linkTo(parent.bottom, 16.dp)
                        start.linkTo(mainBox.end, 16.dp)
                    }
                ) {
                    menuButton()
                    dropdownMenu()
                }
            }
        }
    )

    content()
}

@Composable
fun StartView(
    viewModel: MainActivityViewModel,
    setThemeMode: (ThemeMode) -> Unit = {},
    setDynamicTheme: (Boolean) -> Unit = {},
    onCTAClick: () -> Unit = {},
    openAboutView: () -> Unit = {},
    toggleTelemetry: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var whatCanIWriteOpen by remember { mutableStateOf(false) }

    val iconBackgroundColor = MaterialTheme.colorScheme.onSurfaceVariant
    val systemInDarkMode = isSystemInDarkTheme()
    val darkMode by remember {
        derivedStateOf {
            when (uiState.themeMode) {
                ThemeMode.SYSTEM -> systemInDarkMode
                ThemeMode.DARK -> true
                else -> false
            }
        }
    }
    val menuScrollState = rememberScrollState()

    StartViewLayout(
        modifier = Modifier
            .safeDrawingPadding()
            .fillMaxSize(),
        title = {
            Text(
                text = "EtchDroid",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            )
        },
        logo = {
            Icon(
                modifier = Modifier
                    .size(128.dp)
                    .run {
                        if (darkMode) {
                            coloredShadow(
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                borderRadius = 64.dp,
                                shadowRadius = 128.dp,
                                alpha = 0.5f
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
        mainButton = {
            val label = stringResource(R.string.write_an_image)
            ExtendedFloatingActionButton(
                onClick = onCTAClick,
                modifier = Modifier.appiumTag("writeImageCTA"),
                text = { Text(label) },
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(
                            id = R.drawable.ic_write_to_usb
                        ),
                        // button isn't focusable without this for some reason
                        contentDescription = label
                    )
                },
            )
        },
        bottomButton = {
            OutlinedButton(onClick = { whatCanIWriteOpen = true }) {
                Text(stringResource(R.string.whats_supported))
            }
        },
        menuButton = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu"
                )
            }
        },
        dropdownMenu = {
            DropdownMenu(
                expanded = menuOpen,
                scrollState = menuScrollState,
                onDismissRequest = { menuOpen = false },
            ) {
                Text(
                    stringResource(R.string.style),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(12.dp, 0.dp)
                )

                // Radio buttons for theme modes SYSTEM, LIGHT, DARK
                DropdownMenuItem(
                    onClick = { setThemeMode(ThemeMode.SYSTEM) },
                    text = { Text(stringResource(R.string.device_setting)) },
                    leadingIcon = {
                        RadioButton(
                            modifier = Modifier.size(20.dp),
                            selected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { setThemeMode(ThemeMode.SYSTEM) })
                    })
                DropdownMenuItem(
                    onClick = { setThemeMode(ThemeMode.LIGHT) },
                    text = { Text(stringResource(R.string.light)) }, leadingIcon = {
                        RadioButton(
                            modifier = Modifier.size(20.dp),
                            selected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { setThemeMode(ThemeMode.LIGHT) })
                    })
                DropdownMenuItem(
                    onClick = { setThemeMode(ThemeMode.DARK) },
                    text = { Text(stringResource(R.string.dark)) }, leadingIcon = {
                        RadioButton(
                            modifier = Modifier.size(20.dp),
                            selected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { setThemeMode(ThemeMode.DARK) })
                    })

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    DropdownMenuItem(
                        onClick = { setDynamicTheme(!uiState.dynamicColors) },
                        text = { Text(stringResource(R.string.dynamic_colors)) },
                        leadingIcon = {
                            Checkbox(
                                modifier = Modifier.size(20.dp),
                                checked = uiState.dynamicColors,
                                onCheckedChange = { setDynamicTheme(!uiState.dynamicColors) })
                        })
                }

                if (!Telemetry.isStub) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.telemetry),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(12.dp, 0.dp)
                    )

                    DropdownMenuItem(
                        onClick = { toggleTelemetry() },
                        text = { Text(stringResource(R.string.send_anonymous_data)) },
                        leadingIcon = {
                            Checkbox(
                                modifier = Modifier.size(20.dp),
                                checked = Telemetry.enabled,
                                onCheckedChange = { toggleTelemetry() }
                            )
                        }
                    )

                    val context = LocalContext.current
                    DropdownMenuItem(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    PRIVACY_URL.toUri()
                                )
                            )
                        },
                        text = { Text(stringResource(R.string.privacy_policy)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.TwoTone.Policy,
                                contentDescription = stringResource(R.string.privacy_policy)
                            )
                        }
                    )
                }

                HorizontalDivider()

                DropdownMenuItem(
                    onClick = { openAboutView() },
                    text = { Text(stringResource(R.string.about)) }, leadingIcon = {
                        Icon(
                            imageVector = Icons.TwoTone.Info,
                            contentDescription = stringResource(R.string.about)
                        )
                    })
            }
        },
    ) {
        if (whatCanIWriteOpen) {
            WhatCanIWriteBottomSheet(
                onDismissRequest = {
                    whatCanIWriteOpen = false
                }, darkTheme = darkMode
            )
        }
    }
}

@Composable
fun WindowsImageAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {},
) = AlertDialog(
    modifier = Modifier.appiumTag("windows_image_alert"),
    onDismissRequest = onDismissRequest, title = {
        Text(
            modifier = Modifier.appiumTag("windows_image_alert_title"),
            text = stringResource(R.string.is_this_a_windows_iso),
            textAlign = TextAlign.Center
        )
    }, text = {
        Text(
            text = stringResource(R.string.a_regular_windows_iso_won_t_work)
        )
    }, icon = {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_windows),
            contentDescription = "Windows logo"
        )
    }, confirmButton = {
        TextButton(
            modifier = Modifier.appiumTag("confirmWindowsAlert"),
            onClick = {
                onConfirm()
                onDismissRequest()
            }
        ) {
            Text(stringResource(R.string.continue_anyway))
        }
    }, dismissButton = {
        TextButton(
            modifier = Modifier.appiumTag("cancelWindowsAlert"),
            onClick = {
                onCancel()
                onDismissRequest()
            }) {
            Text(stringResource(R.string.cancel))
        }
    })

@Composable
fun TelemetryAlertDialog(
    onDismissRequest: () -> Unit,
    onOptOut: () -> Unit,
    onCancel: () -> Unit = {},
) = AlertDialog(onDismissRequest = onDismissRequest, title = {
    Text(text = stringResource(R.string.we_need_your_help), textAlign = TextAlign.Center)
}, text = {
    val scrollState = rememberScrollState()
    val privacyPolicy = stringResource(R.string.privacy_policy)
    val annotatedString = buildAnnotatedString {
        val text = stringResource(R.string.telemetry_rationale, privacyPolicy)
        val startIndex = text.indexOf(privacyPolicy)
        val endIndex = startIndex + privacyPolicy.length

        append(text)

        addStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            ), start = startIndex, end = endIndex
        )
        addLink(LinkAnnotation.Url(PRIVACY_URL), startIndex, endIndex)
    }
    Text(
        modifier = Modifier.verticalScroll(scrollState),
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
    )
}, icon = {
    Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_telemetry),
        contentDescription = stringResource(R.string.telemetry_icon)
    )
}, confirmButton = {
    TextButton(onClick = {
        onOptOut()
        onDismissRequest()
    }) {
        Text(stringResource(R.string.telemetry_opt_out))
    }
}, dismissButton = {
    TextButton(onClick = {
        onCancel()
        onDismissRequest()
    }) {
        Text(stringResource(R.string.telemetry_keep_enabled))
    }
})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDevicePickerBottomSheet(
    onDismissRequest: () -> Unit,
    selectDevice: (IUsbMassStorageDeviceDescriptor) -> Unit,
    availableDevices: () -> Set<IUsbMassStorageDeviceDescriptor>,
    skipHalfExpanded: Boolean = true,
) {
    val bottomSheetState =
        rememberPorkedAroundSheetState(onDismissRequest, skipPartiallyExpanded = skipHalfExpanded)
    val anyDeviceAvailable by remember(availableDevices) {
        derivedStateOf { availableDevices().isNotEmpty() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = bottomSheetState,
    ) {
        Text(
            text = if (anyDeviceAvailable) stringResource(
                R.string.select_a_usb_drive
            ) else stringResource(
                R.string.connect_a_usb_drive
            ), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(32.dp, 0.dp, 32.dp, 16.dp)
                .fillMaxWidth()
        )
        if (anyDeviceAvailable) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableDevices().toList()) { device ->
                    ListItem(
                        modifier = Modifier
                            .appiumTag("usbDeviceListItem")
                            .clickable { selectDevice(device) },
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.vidpid, fontStyle = FontStyle.Italic) },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    id = R.drawable.ic_usb_stick
                                ),
                                contentDescription = "USB drive"
                            )
                        },
                    )
                }
            }
        } else {
            Icon(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp)
                    .height(128.dp),
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_usb_stick_search),
                contentDescription = stringResource(R.string.looking_for_usb_drives),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 24.dp, 0.dp, 0.dp),
        )
    }
}


enum class SupportStatus {
    SUPPORTED, MAYBE_SUPPORTED, UNSUPPORTED,
}

@Composable
fun ItemSupportEntry(
    description: String,
    supportStatus: SupportStatus,
    darkTheme: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (supportStatus) {
                SupportStatus.SUPPORTED -> Icons.TwoTone.Check
                SupportStatus.MAYBE_SUPPORTED -> Icons.AutoMirrored.Outlined.HelpCenter
                SupportStatus.UNSUPPORTED -> Icons.TwoTone.Clear
            }, contentDescription = when (supportStatus) {
                SupportStatus.SUPPORTED -> stringResource(R.string.supported)
                SupportStatus.MAYBE_SUPPORTED -> stringResource(R.string.maybe_supported)
                SupportStatus.UNSUPPORTED -> stringResource(R.string.not_supported)
            }, modifier = Modifier
                .size(20.dp)
                .padding(0.dp, 4.dp, 0.dp, 0.dp),
            tint = when (supportStatus) {
                SupportStatus.SUPPORTED -> supportedGreen(darkTheme)
                SupportStatus.MAYBE_SUPPORTED -> partiallySupportedYellow(darkTheme)
                SupportStatus.UNSUPPORTED -> notSupportedRed(darkTheme)
            }
        )
        Text(
            text = description, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatCanIWriteBottomSheet(onDismissRequest: () -> Unit, darkTheme: Boolean = false) {
    val sheetState = rememberPorkedAroundSheetState(onDismissRequest, false)
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp, 16.dp, 32.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            item {
                Text(
                    text = stringResource(R.string.supported_devices_and_images),
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
            }
            item {
                Text(
                    text = stringResource(R.string.devices),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 8.dp, top = 8.dp)
                        .fillMaxWidth()
                )
            }
            item {
                ItemSupportEntry(
                    stringResource(R.string.usb_flash_drives), SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    stringResource(R.string.memory_cards_using_a_usb_adapter),
                    SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.usb_hard_drives_ssds_some_might_work),
                    SupportStatus.MAYBE_SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(
                        R.string.usb_docks_and_hubs_might_have_power_issues
                    ), SupportStatus.MAYBE_SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    stringResource(R.string.memory_cards_using_the_internal_slot),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.optical_disk_drives),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.floppy_disk_drives),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.any_thunderbolt_only_device),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                Text(
                    text = stringResource(R.string.disk_images),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 8.dp, top = 32.dp)
                        .fillMaxWidth()
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.microsoft_windows_isos_any_version),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.community_windows_images),
                    supportStatus = SupportStatus.MAYBE_SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.apple_dmg_disk_images),
                    SupportStatus.UNSUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(
                        R.string.arch_linux_ubuntu_debian_fedora_pop_os_linux_mint_freebsd_etc
                    ), SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(
                        R.string.other_modern_gnu_linux_and_bsd_distributions_live_isos
                    ), SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.raspberry_pi_sd_card_images_unzip_first),
                    SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(
                        R.string.any_other_image_that_works_with_balena_etcher_or_dd
                    ), SupportStatus.SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = stringResource(R.string.older_gnu_linux_distributions_isos_2010),
                    SupportStatus.MAYBE_SUPPORTED, darkTheme
                )
            }
            item {
                ItemSupportEntry(
                    description = "Damn Small Linux", SupportStatus.UNSUPPORTED,
                    darkTheme
                )
            }
            item {
                val annotatedString = buildAnnotatedString {
                    val str = stringResource(R.string.support_for_dmg_images_was_removed, "GitHub")
                    val startIndex = str.indexOf("GitHub")
                    val endIndex = startIndex + "GitHub".length
                    append(str)
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ), start = startIndex, end = endIndex
                    )
                    addLink(
                        LinkAnnotation.Url("https://github.com/EtchDroid/EtchDroid/releases/tag/dmg-support"),
                        startIndex,
                        endIndex
                    )
                }
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

@PreviewScreenSizes()
@Composable
fun StartViewPreview() {
    val viewModel = remember { MainActivityViewModel() }
    val uiState by viewModel.state.collectAsState()

    MainView(viewModel) {
        StartView(
            viewModel,
            setThemeMode = {
                viewModel.setState(
                    uiState.copy(
                        themeMode = it
                    )
                )
            },
            setDynamicTheme = {
                viewModel.setState(
                    uiState.copy(
                        dynamicColors = it
                    )
                )
            },
            onCTAClick = { },
        )
    }
}

@PreviewScreenSizes()
@Composable
fun WindowsAlertDialogPreview() {
    val viewModel = remember { MainActivityViewModel() }

    MainView(viewModel) {
        WindowsImageAlertDialog(
            onDismissRequest = { }, onConfirm = { },
            onCancel = { })
    }
}

@PreviewScreenSizes()
@Composable
fun TelemetryAlertDialogPreview() {
    val viewModel = remember { MainActivityViewModel() }

    MainView(viewModel) {
        TelemetryAlertDialog(
            onDismissRequest = { }, onOptOut = { },
            onCancel = { })
    }
}

@PreviewScreenSizes()
@Composable
fun UsbDevicePickerBottomSheetPreview() {
    val viewModel = remember { MainActivityViewModel() }
    var openBottomSheet by rememberSaveable { mutableStateOf(true) }
    val availableDevices = setOf(
        object : IUsbMassStorageDeviceDescriptor {
            override val name: String = "USB Drive"
            override val vidpid: String = "1234:5678"
        }
    )

    MainView(viewModel) {
        Row(
            Modifier.toggleable(
                value = openBottomSheet, role = Role.Checkbox,
                onValueChange = { checked -> openBottomSheet = checked })
        ) {
            Checkbox(checked = openBottomSheet, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
            Text("Open bottom sheet")
        }

        LaunchedEffect(Unit) {
            openBottomSheet = true
        }

        if (openBottomSheet) {
            UsbDevicePickerBottomSheet(
                onDismissRequest = { openBottomSheet = false },
                selectDevice = { },
                availableDevices = { availableDevices }
            )
        }
    }
}

@PreviewScreenSizes()
@Composable
fun EmptyUsbDevicePickerBottomSheetPreview() {
    val viewModel = remember { MainActivityViewModel() }
    var openBottomSheet by remember { mutableStateOf(true) }

    MainView(viewModel) {
        Row(
            Modifier.toggleable(
                value = openBottomSheet, role = Role.Checkbox,
                onValueChange = { checked -> openBottomSheet = checked })
        ) {
            Checkbox(checked = openBottomSheet, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
            Text("Open bottom sheet")
        }

        LaunchedEffect(Unit) {
            openBottomSheet = true
        }

        if (openBottomSheet) {
            UsbDevicePickerBottomSheet(
                onDismissRequest = { openBottomSheet = false },
                selectDevice = { },
                availableDevices = { emptySet() },
            )
        }
    }
}