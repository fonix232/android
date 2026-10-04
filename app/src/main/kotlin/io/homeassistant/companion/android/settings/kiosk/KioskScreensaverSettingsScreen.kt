package io.homeassistant.companion.android.settings.kiosk

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundLevel
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.annotations.VisibleForTesting

@Composable
internal fun KioskScreensaverSettingsScreen(
    viewModel: KioskScreensaverSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val soundLevel by viewModel.soundLevel.collectAsStateWithLifecycle()
    val requestMicrophone = rememberMicrophonePermissionRequest { viewModel.onWakeOnSoundChanged(true) }

    KioskScreensaverSettingsContent(
        viewState = viewState,
        soundLevel = soundLevel,
        onModeChanged = viewModel::onModeChanged,
        onIdleTimeoutChanged = viewModel::onIdleTimeoutChanged,
        onWakeOnSoundChanged = { wake ->
            // Asking only when turning it on: the permission is what capture needs, and turning it
            // off never needs one.
            if (wake) requestMicrophone() else viewModel.onWakeOnSoundChanged(false)
        },
        onSoundWakeThresholdChanged = viewModel::onSoundWakeThresholdChanged,
        modifier = modifier,
    )
}

@Composable
@VisibleForTesting
internal fun KioskScreensaverSettingsContent(
    viewState: KioskScreensaverSettingsViewState,
    soundLevel: KioskSoundLevel?,
    onModeChanged: (KioskScreensaverMode) -> Unit,
    onIdleTimeoutChanged: (Duration) -> Unit,
    onWakeOnSoundChanged: (Boolean) -> Unit,
    onSoundWakeThresholdChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(all = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false)),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        HASettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                ModeRow(selected = viewState.mode, onModeChanged = onModeChanged)

                // The timeout is meaningless with no screensaver to wait for, so it appears only
                // once one is chosen rather than sitting there disabled.
                AnimatedVisibility(visible = viewState.isTimeoutRelevant) {
                    TimeoutRow(selected = viewState.idleTimeout, onIdleTimeoutChanged = onIdleTimeoutChanged)
                }
            }
        }

        FooterText(stringResource(commonR.string.kiosk_screensaver_footer))

        // Only meaningful with a screensaver to wake from, so it travels with the timeout.
        AnimatedVisibility(visible = viewState.isTimeoutRelevant) {
            SoundWakeSection(
                wakeOnSound = viewState.wakeOnSound,
                threshold = viewState.soundWakeThreshold,
                level = soundLevel,
                onWakeOnSoundChanged = onWakeOnSoundChanged,
                onThresholdChanged = onSoundWakeThresholdChanged,
            )
        }
    }
}

/**
 * Returns a function that asks for the microphone permission and runs [onGranted] once it is held.
 *
 * Nothing happens when the user refuses: the toggle stays off, which is the honest outcome, and
 * they can try again.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberMicrophonePermissionRequest(onGranted: () -> Unit): () -> Unit {
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        if (granted) onGranted()
    }
    return {
        if (permissionState.status.isGranted) onGranted() else permissionState.launchPermissionRequest()
    }
}

@Composable
private fun ModeRow(selected: KioskScreensaverMode, onModeChanged: (KioskScreensaverMode) -> Unit) {
    val items = KioskScreensaverMode.entries.map { mode ->
        HADropdownItem(key = mode, label = stringResource(mode.labelRes()))
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onModeChanged,
        label = stringResource(commonR.string.kiosk_screensaver_mode),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeoutRow(selected: Duration, onIdleTimeoutChanged: (Duration) -> Unit) {
    val items = SCREENSAVER_TIMEOUT_CHOICES.map { timeout ->
        HADropdownItem(key = timeout, label = formatTimeout(timeout))
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onIdleTimeoutChanged,
        label = stringResource(commonR.string.kiosk_screensaver_idle_timeout),
        // A stored timeout outside the offered choices still renders, through the placeholder.
        placeholder = formatTimeout(selected),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The user-facing name of this screensaver mode. */
private fun KioskScreensaverMode.labelRes(): Int = when (this) {
    KioskScreensaverMode.DISABLED -> commonR.string.kiosk_screensaver_mode_off
    KioskScreensaverMode.CLOCK -> commonR.string.kiosk_screensaver_mode_clock
    KioskScreensaverMode.BLANK -> commonR.string.kiosk_screensaver_mode_blank
}

/**
 * Formats [timeout] as whole minutes or whole hours, whichever reads better.
 *
 * Rounded up to a minute, because a stored value below that would otherwise read as "0 minutes".
 */
@Composable
private fun formatTimeout(timeout: Duration): String {
    val minutes = timeout.inWholeMinutes.toInt().coerceAtLeast(1)
    return if (minutes >= MINUTES_PER_HOUR && minutes % MINUTES_PER_HOUR == 0) {
        pluralStringResource(
            commonR.plurals.kiosk_screensaver_hours,
            minutes / MINUTES_PER_HOUR,
            minutes / MINUTES_PER_HOUR,
        )
    } else {
        pluralStringResource(commonR.plurals.kiosk_screensaver_minutes, minutes, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60

/** A level in the middle of the meter, where someone speaking at the device lands. */
private const val PREVIEW_SOUND_LEVEL_DBFS = -28f

@Preview
@Composable
private fun KioskScreensaverSettingsContentPreview() {
    HAThemeForPreview {
        KioskScreensaverSettingsContent(
            viewState = KioskScreensaverSettingsViewState(
                mode = KioskScreensaverMode.CLOCK,
                idleTimeout = 5.minutes,
                wakeOnSound = true,
            ),
            soundLevel = KioskSoundLevel.ofDbfs(PREVIEW_SOUND_LEVEL_DBFS),
            onModeChanged = {},
            onIdleTimeoutChanged = {},
            onWakeOnSoundChanged = {},
            onSoundWakeThresholdChanged = {},
        )
    }
}
