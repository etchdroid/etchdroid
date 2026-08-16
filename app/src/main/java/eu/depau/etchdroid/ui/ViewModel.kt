package eu.depau.etchdroid.ui

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import eu.depau.etchdroid.AppSettings
import eu.depau.etchdroid.Intents
import eu.depau.etchdroid.JobStatusInfo
import eu.depau.etchdroid.SettingChangeListener
import eu.depau.etchdroid.ThemeMode
import eu.depau.etchdroid.massstorage.EtchDroidUsbMassStorageDevice.Companion.massStorageDevices
import eu.depau.etchdroid.massstorage.UsbMassStorageDeviceDescriptor
import eu.depau.etchdroid.plugins.telemetry.Telemetry
import eu.depau.etchdroid.plugins.telemetry.TelemetryLevel
import eu.depau.etchdroid.utils.exception.ServiceTimeoutException
import eu.depau.etchdroid.utils.exception.base.EtchDroidException
import eu.depau.etchdroid.utils.exception.base.RecoverableException
import eu.depau.etchdroid.utils.ktexts.safeParcelableExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


interface IThemeState {
    val dynamicColors: Boolean
    val themeMode: ThemeMode
}

interface IViewModel<T> {
    val state: StateFlow<T>
}

interface IThemeViewModel<T> : IViewModel<T> where T : IThemeState {
    val darkMode: State<Boolean>
        @Composable get() {
            val stateValue by state.collectAsState()
            val systemInDarkMode = isSystemInDarkTheme()
            return remember(stateValue.themeMode) {
                derivedStateOf {
                    when (stateValue.themeMode) {
                        ThemeMode.SYSTEM -> systemInDarkMode
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                    }
                }
            }
        }
}

data class ThemeState(
    override val dynamicColors: Boolean = false,
    override val themeMode: ThemeMode = ThemeMode.SYSTEM,
) : IThemeState {
    companion object {
        val Empty: ThemeState
            get() = ThemeState()
    }
}

class ThemeViewModel : ViewModel(), SettingChangeListener, IThemeViewModel<ThemeState> {
    private val _state = MutableStateFlow(ThemeState.Empty)
    override val state: StateFlow<ThemeState> = _state.asStateFlow()

    override fun refreshSettings(settings: AppSettings) {
        _state.update {
            it.copy(
                dynamicColors = settings.dynamicColors, themeMode = settings.themeMode
            )
        }
    }
}

data class MainActivityState(
    override val dynamicColors: Boolean = false,
    override val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showWindowsAlertForUri: Uri? = null,
    val openedImage: Uri? = null,
    val massStorageDevices: Set<UsbMassStorageDeviceDescriptor> = emptySet(),
    val telemetry: Boolean = false,
    val showTelemetry: Boolean = true,
) : IThemeState {
    companion object {
        val Empty: MainActivityState
            get() = MainActivityState()
    }
}

class MainActivityViewModel : ViewModel(), SettingChangeListener, IThemeViewModel<MainActivityState> {
    private val _state = MutableStateFlow(MainActivityState.Empty)
    override val state: StateFlow<MainActivityState> = _state.asStateFlow()

    override fun refreshSettings(settings: AppSettings) {
        _state.update {
            it.copy(
                    dynamicColors = settings.dynamicColors,
                    themeMode = settings.themeMode,
            )
        }
    }

    fun setState(state: MainActivityState) {
        _state.update { state }
    }

    fun setShowWindowsAlertUri(uri: Uri?) {
        _state.update {
            it.copy(showWindowsAlertForUri = uri)
        }
    }

    fun setOpenedImage(uri: Uri?) {
        _state.update {
            it.copy(openedImage = uri)
        }
    }


    fun usbDeviceAttached(device: UsbDevice) {
        _state.update {
            val new = it.copy(
                massStorageDevices = it.massStorageDevices + device.massStorageDevices
            )
            // println("usbDeviceAttached: $new")
            new
        }
    }

    fun usbDeviceDetached(device: UsbDevice) {
        _state.update { state ->
            state.copy(massStorageDevices = state.massStorageDevices.filter { it.usbDevice != device }.toSet())
        }
    }


    fun replaceUsbDevices(devices: Collection<UsbDevice>) {
        _state.update { state ->
            state.copy(
                massStorageDevices = devices.flatMap { it.massStorageDevices }.toSet()
            )
        }
    }

    fun setTelemetry(enabled: Boolean) {
        _state.update {
            it.copy(telemetry = enabled)
        }
    }

    fun setTelemetryShown(shown: Boolean) {
        _state.update {
            it.copy(showTelemetry = shown)
        }
    }

    override fun toString(): String {
        return "MainActivityViewModel(state=${_state.value})"
    }
}

data class ConfirmOperationActivityState(
    override val dynamicColors: Boolean = false,
    override val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val openedImage: Uri? = null,
    val selectedDevice: UsbMassStorageDeviceDescriptor? = null,
    val hasUsbPermission: Boolean = false,
) : IThemeState {
    companion object {
        val Empty: ConfirmOperationActivityState
            get() = ConfirmOperationActivityState()
    }
}


class ConfirmOperationActivityViewModel : ViewModel(), SettingChangeListener,
    IThemeViewModel<ConfirmOperationActivityState> {
    private val _state = MutableStateFlow(ConfirmOperationActivityState.Empty)
    override val state: StateFlow<ConfirmOperationActivityState> = _state.asStateFlow()

    override fun refreshSettings(settings: AppSettings) {
        _state.update {
            it.copy(
                dynamicColors = settings.dynamicColors, themeMode = settings.themeMode
            )
        }
    }

    fun setState(state: ConfirmOperationActivityState) {
        _state.update { state }
    }

    fun init(openedImage: Uri?, selectedDevice: UsbMassStorageDeviceDescriptor?) = _state.update {
        it.copy(
            openedImage = openedImage,
            selectedDevice = selectedDevice,
            hasUsbPermission = false,
        )
    }

    fun setPermission(permission: Boolean) {
        _state.update {
            it.copy(hasUsbPermission = permission)
        }
    }
}

enum class JobState {
    IN_PROGRESS, SUCCESS, FATAL_ERROR, RECOVERABLE_ERROR
}

data class ProgressActivityState(
    override val dynamicColors: Boolean = false,
    override val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val jobState: JobState = JobState.IN_PROGRESS,
    val percent: Int = -1,
    val speed: Float = 0f,
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val jobId: Int = -1,
    val isVerifying: Boolean = false,
    val sourceUri: Uri? = null,
    val destDevice: UsbMassStorageDeviceDescriptor? = null,
    val exception: EtchDroidException? = null,
    val recoverableError: Boolean = false,
    val notificationsPermission: Boolean = false,
    val showNotificationsBanner: Boolean = true,
    val lastNotificationTime: Long = System.currentTimeMillis(),
    /** Whether the progress screen is currently receiving job updates. */
    val isForeground: Boolean = true,
) : IThemeState {
    companion object {
        val Empty: ProgressActivityState
            get() = ProgressActivityState()
    }
}

class ProgressActivityViewModel : ViewModel(), SettingChangeListener, IThemeViewModel<ProgressActivityState> {
    private val _state = MutableStateFlow(ProgressActivityState.Empty)
    override val state: StateFlow<ProgressActivityState> = _state.asStateFlow()
    private var launchIntentConsumed = false

    override fun refreshSettings(settings: AppSettings) {
        _state.update {
            it.copy(
                dynamicColors = settings.dynamicColors, themeMode = settings.themeMode,
                showNotificationsBanner = settings.showNotificationsBanner
            )
        }
    }

    fun setState(state: ProgressActivityState) {
        _state.update { state }
    }

    fun initializeFromIntent(intent: Intent) {
        if (launchIntentConsumed) return
        updateFromIntent(intent)
    }

    fun updateFromIntent(intent: Intent) {
        launchIntentConsumed = true
        val sourceUri = intent.safeParcelableExtra<Uri>("sourceUri")!!
        val status = intent.safeParcelableExtra<JobStatusInfo>("status")!!

        Telemetry.addBreadcrumb {
            message =
                "[${status.jobId}] Job progress notification: ${intent.action}, ${status.percent}%, verifying: ${status.isVerifying}"
            category = "job_broadcast"
            level = when (intent.action) {
                Intents.JOB_PROGRESS -> TelemetryLevel.DEBUG
                Intents.FINISHED -> TelemetryLevel.INFO
                Intents.ERROR -> TelemetryLevel.ERROR
                else -> TelemetryLevel.INFO
            }
            data["job.id"] = status.jobId.toString()
            data["job.percent"] = status.percent.toString()
            data["job.isVerifying"] = status.isVerifying.toString()
            data["job.processedBytes"] = status.processedBytes.toString()
            data["job.totalBytes"] = status.totalBytes.toString()
        }

        when (intent.action) {
            Intents.JOB_PROGRESS -> {
                _state.update {
                    it.copy(
                        jobState = JobState.IN_PROGRESS,
                        jobId = status.jobId,
                        isVerifying = status.isVerifying,
                        percent = status.percent,
                        speed = status.speed,
                        processedBytes = status.processedBytes,
                        totalBytes = status.totalBytes,
                        sourceUri = sourceUri,
                        destDevice = status.destDevice,
                        exception = null,
                        lastNotificationTime = System.currentTimeMillis(),
                    )
                }
            }

            Intents.FINISHED -> {
                _state.update {
                    it.copy(
                        jobState = JobState.SUCCESS,
                        exception = null,
                        sourceUri = sourceUri,
                        destDevice = status.destDevice,
                        totalBytes = status.totalBytes,
                        percent = 100,
                        lastNotificationTime = System.currentTimeMillis(),
                    )
                }
            }

            Intents.ERROR -> {
                _state.update {
                    it.copy(
                        jobState = if (status.exception is RecoverableException) JobState.RECOVERABLE_ERROR else JobState.FATAL_ERROR,
                        jobId = status.jobId,
                        exception = status.exception,
                        sourceUri = sourceUri,
                        destDevice = status.destDevice,
                        processedBytes = status.processedBytes,
                        totalBytes = status.totalBytes,
                        percent = status.percent,
                        lastNotificationTime = System.currentTimeMillis(),
                    )
                }
                if (status.exception != null) {
                    Telemetry.captureException(status.exception)
                }
            }
        }
    }

    fun setTimeoutError() {
        _state.update {
            it.copy(
                jobState = JobState.FATAL_ERROR,
                exception = ServiceTimeoutException(),
            )
        }
    }

    /**
     * The activity only receives job updates while resumed, so the silence watchdog must
     * not count time spent in the background (a system dialog, the settings screen, or
     * the user simply leaving during a long write) as the service having died.
     */
    fun setForeground(foreground: Boolean) {
        _state.update {
            it.copy(
                isForeground = foreground,
                // Restart the countdown on the way back in: the next update is only due
                // once the receiver is registered again.
                lastNotificationTime = if (foreground) System.currentTimeMillis()
                else it.lastNotificationTime,
            )
        }
    }

    fun setNotificationsPermission(permission: Boolean) {
        _state.update {
            it.copy(notificationsPermission = permission)
        }
    }
}