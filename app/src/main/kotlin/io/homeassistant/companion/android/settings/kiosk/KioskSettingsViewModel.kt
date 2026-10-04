package io.homeassistant.companion.android.settings.kiosk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.kiosk.KioskAutoReloadInterval
import io.homeassistant.companion.android.common.data.kiosk.KioskBrightness
import io.homeassistant.companion.android.common.data.kiosk.KioskCornerPosition
import io.homeassistant.companion.android.common.data.kiosk.KioskSettings
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import io.homeassistant.companion.android.common.data.kiosk.KioskStreamCredentials
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A brightness the settings screen offers.
 *
 * [SystemAdjusted] is not "zero brightness": it means kiosk mode does not set a brightness at all
 * and leaves the display to the system, including its automatic adjustment.
 */
internal sealed interface KioskBrightnessOption {
    data object SystemAdjusted : KioskBrightnessOption

    data class Fixed(val percent: Int) : KioskBrightnessOption
}

/** A server the kiosk can be pinned to, named for the picker. */
internal data class KioskServerOption(val id: Int, val name: String)

/**
 * One of the display choices on the kiosk settings screen.
 *
 * They arrive through a single event rather than a function each because they are the same kind of
 * choice made in the same section, and one exhaustive `when` is easier to keep complete than five
 * one-line functions are to keep in step.
 */
internal sealed interface KioskDisplaySetting {
    data class KeepScreenOn(val enabled: Boolean) : KioskDisplaySetting

    data class HideStatusBar(val hidden: Boolean) : KioskDisplaySetting

    data class HideNavigationBar(val hidden: Boolean) : KioskDisplaySetting

    data class Brightness(val option: KioskBrightnessOption) : KioskDisplaySetting

    data class AutoReload(val interval: KioskAutoReloadInterval) : KioskDisplaySetting
}

/** What the kiosk settings screen renders. */
/** Whether the kiosk settings are readable, or still waiting behind the device's lock credential. */
internal enum class KioskSettingsLock {
    /**
     * The stored settings have not arrived yet, so it is not known whether they are protected.
     *
     * The screen shows neither the settings nor the lock until this resolves. Guessing either way
     * would flash the wrong thing: assuming unprotected would put protected settings on screen,
     * and assuming protected would make everyone else watch a lock appear and vanish.
     */
    UNKNOWN,

    /** Nothing is hidden; the user did not ask for the settings to be protected. */
    NOT_REQUIRED,

    /** Protected and not yet unlocked, so the settings must stay off screen. */
    LOCKED,

    /** Protected, and unlocked for as long as this screen lives. */
    UNLOCKED,
}

internal data class KioskSettingsViewState(
    val lock: KioskSettingsLock = KioskSettingsLock.UNKNOWN,
    val enabled: Boolean = false,
    val requireAuthentication: Boolean = false,
    val acceptRemoteCommands: Boolean = true,
    val showRemoteCommandConfirmations: Boolean = true,
    val settingsEntryPosition: KioskCornerPosition = KioskCornerPosition.BOTTOM_END,
    val settingsEntryHidden: Boolean = false,
    val servers: List<KioskServerOption> = emptyList(),
    val serverId: Int? = null,
    val dashboardPath: String = "",
    val autoReload: KioskAutoReloadInterval = KioskAutoReloadInterval.NEVER,
    val keepScreenOn: Boolean = false,
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val brightness: KioskBrightnessOption = KioskBrightnessOption.SystemAdjusted,
    val cameraStreamEnabled: Boolean = false,
    val cameraStreamPort: String = KioskSettings.DEFAULT_CAMERA_STREAM_PORT.toString(),
    val cameraStreamPassword: String? = null,
)

/**
 * One of the camera stream choices on the kiosk settings screen.
 *
 * One event rather than a function each, for the same reason as [KioskDisplaySetting]: they are
 * the same kind of choice made in the same section.
 */
/** Ports a user may pick; below 1024 needs privileges the app does not have. */
private const val MIN_PORT = 1024
private const val MAX_PORT = 65535

/**
 * One of the dashboard choices on the kiosk settings screen.
 *
 * One event rather than a function each, for the same reason as the display and stream choices.
 */
internal sealed interface KioskDashboardSetting {
    data class Server(val serverId: Int?) : KioskDashboardSetting
    data class Path(val path: String) : KioskDashboardSetting
}

internal sealed interface KioskCameraStreamSetting {
    data class Enabled(val enabled: Boolean) : KioskCameraStreamSetting
    data class Port(val port: String) : KioskCameraStreamSetting
    data object RegeneratePassword : KioskCameraStreamSetting
}

@HiltViewModel
internal class KioskSettingsViewModel @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
    serverManager: ServerManager,
) : ViewModel() {

    private val unlocked = MutableStateFlow(false)

    private val servers: Flow<List<KioskServerOption>> = flow {
        emit(serverManager.servers().map { KioskServerOption(id = it.id, name = it.friendlyName) })
    }

    val viewState: StateFlow<KioskSettingsViewState> =
        combine(kioskSettingsRepository.settingsFlow(), unlocked, servers, KioskSettings::toViewState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KioskSettingsViewState())

    /** Turns kiosk mode on or off. */
    fun onEnabledChanged(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Chooses whether reading these settings asks for the device's lock credential first. */
    fun onRequireAuthenticationChanged(require: Boolean) = update { it.copy(requireAuthentication = require) }

    /**
     * Records whether the user has cleared the lock.
     *
     * Cleared again when the screen leaves the foreground, so returning to protected settings asks
     * for the credential rather than showing what was already unlocked.
     */
    fun onUnlockedChanged(unlocked: Boolean) {
        this.unlocked.value = unlocked
    }

    /** Chooses whether kiosk commands from the server are obeyed. */
    fun onAcceptRemoteCommandsChanged(accept: Boolean) = update { it.copy(acceptRemoteCommands = accept) }

    /** Chooses whether obeying a kiosk command from the server tells the user it happened. */
    fun onShowRemoteCommandConfirmationsChanged(show: Boolean) = update {
        it.copy(showRemoteCommandConfirmations = show)
    }

    /** Chooses which corner the button that opens these settings sits in. */
    fun onSettingsEntryPositionChanged(position: KioskCornerPosition) = update {
        it.copy(settingsEntryPosition = position)
    }

    /** Chooses whether that button is invisible; it stays tappable either way. */
    fun onSettingsEntryHiddenChanged(hidden: Boolean) = update { it.copy(settingsEntryHidden = hidden) }

    /** Applies one of the dashboard choices. */
    fun onDashboardSettingChanged(setting: KioskDashboardSetting) = update { settings ->
        when (setting) {
            is KioskDashboardSetting.Server -> settings.copy(serverId = setting.serverId)
            // Blank is stored as absent, so a cleared field reads back as the server's default.
            is KioskDashboardSetting.Path -> settings.copy(dashboardPath = setting.path.takeIf { it.isNotBlank() })
        }
    }

    /**
     * Applies one of the camera stream choices.
     *
     * Turning the stream on generates a password when there is none. The server has no
     * unauthenticated mode, so a stream without credentials is not a state this can reach.
     */
    fun onCameraStreamSettingChanged(setting: KioskCameraStreamSetting) = update { settings ->
        when (setting) {
            is KioskCameraStreamSetting.Enabled -> settings.copy(
                cameraStreamEnabled = setting.enabled,
                cameraStreamPassword = settings.cameraStreamPassword
                    ?: KioskStreamCredentials.generate().password.takeIf { setting.enabled },
            )

            is KioskCameraStreamSetting.Port -> settings.copy(
                cameraStreamPort = setting.port.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
                    ?: settings.cameraStreamPort,
            )

            KioskCameraStreamSetting.RegeneratePassword ->
                settings.copy(cameraStreamPassword = KioskStreamCredentials.generate().password)
        }
    }

    /** Applies one of the display choices. */
    fun onDisplaySettingChanged(setting: KioskDisplaySetting) = update { settings ->
        when (setting) {
            is KioskDisplaySetting.KeepScreenOn -> settings.copy(keepScreenOn = setting.enabled)
            is KioskDisplaySetting.HideStatusBar -> settings.copy(hideStatusBar = setting.hidden)
            is KioskDisplaySetting.HideNavigationBar -> settings.copy(hideNavigationBar = setting.hidden)
            is KioskDisplaySetting.AutoReload -> settings.copy(autoReload = setting.interval)
            is KioskDisplaySetting.Brightness -> settings.copy(
                brightness = when (val option = setting.option) {
                    KioskBrightnessOption.SystemAdjusted -> null
                    is KioskBrightnessOption.Fixed -> KioskBrightness.fromPercent(option.percent)
                },
            )
        }
    }

    /**
     * Applies [change] to the stored settings.
     *
     * Reads the settings back rather than editing the last rendered state, so a change made
     * elsewhere between render and tap is not silently reverted.
     */
    private fun update(change: (KioskSettings) -> KioskSettings) {
        viewModelScope.launch {
            kioskSettingsRepository.updateSettings(change)
        }
    }
}

private fun KioskSettings.toViewState(unlocked: Boolean, servers: List<KioskServerOption>): KioskSettingsViewState =
    KioskSettingsViewState(
        settingsEntryPosition = settingsEntryPosition,
        settingsEntryHidden = settingsEntryHidden,
        servers = servers,
        serverId = serverId,
        dashboardPath = dashboardPath.orEmpty(),
        lock = when {
            !requireAuthentication -> KioskSettingsLock.NOT_REQUIRED
            unlocked -> KioskSettingsLock.UNLOCKED
            else -> KioskSettingsLock.LOCKED
        },
        enabled = enabled,
        requireAuthentication = requireAuthentication,
        acceptRemoteCommands = acceptRemoteCommands,
        showRemoteCommandConfirmations = showRemoteCommandConfirmations,
        autoReload = autoReload,
        keepScreenOn = keepScreenOn,
        hideStatusBar = hideStatusBar,
        hideNavigationBar = hideNavigationBar,
        brightness = brightness
            ?.let { KioskBrightnessOption.Fixed(it.toPercent()) }
            ?: KioskBrightnessOption.SystemAdjusted,
        cameraStreamEnabled = cameraStreamEnabled,
        cameraStreamPort = cameraStreamPort.toString(),
        cameraStreamPassword = cameraStreamPassword,
    )
