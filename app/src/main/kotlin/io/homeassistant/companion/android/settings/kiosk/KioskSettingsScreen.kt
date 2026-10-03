package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.kiosk.KioskAutoReloadInterval
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import org.jetbrains.annotations.VisibleForTesting

/** A mid-range brightness, so the preview shows the picker holding a value rather than its default. */
private const val PREVIEW_BRIGHTNESS_PERCENT = 40

@Composable
internal fun KioskSettingsScreen(
    viewModel: KioskSettingsViewModel,
    onScreensaverClick: () -> Unit,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    when (viewState.lock) {
        KioskSettingsLock.UNKNOWN -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HALoading() }
            return
        }

        KioskSettingsLock.LOCKED -> {
            KioskSettingsLocked(onUnlockClick = onUnlockClick, modifier = modifier)
            return
        }

        KioskSettingsLock.NOT_REQUIRED, KioskSettingsLock.UNLOCKED -> Unit
    }

    KioskSettingsContent(
        viewState = viewState,
        onEnabledChanged = viewModel::onEnabledChanged,
        onRequireAuthenticationChanged = viewModel::onRequireAuthenticationChanged,
        onAcceptRemoteCommandsChanged = viewModel::onAcceptRemoteCommandsChanged,
        onShowRemoteCommandConfirmationsChanged = viewModel::onShowRemoteCommandConfirmationsChanged,
        onAutoReloadChanged = viewModel::onAutoReloadChanged,
        onKeepScreenOnChanged = viewModel::onKeepScreenOnChanged,
        onHideStatusBarChanged = viewModel::onHideStatusBarChanged,
        onHideNavigationBarChanged = viewModel::onHideNavigationBarChanged,
        onBrightnessChanged = viewModel::onBrightnessChanged,
        onScreensaverClick = onScreensaverClick,
        modifier = modifier,
    )
}

@Composable
@VisibleForTesting
internal fun KioskSettingsContent(
    viewState: KioskSettingsViewState,
    onEnabledChanged: (Boolean) -> Unit,
    onRequireAuthenticationChanged: (Boolean) -> Unit,
    onAcceptRemoteCommandsChanged: (Boolean) -> Unit,
    onShowRemoteCommandConfirmationsChanged: (Boolean) -> Unit,
    onAutoReloadChanged: (KioskAutoReloadInterval) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onHideStatusBarChanged: (Boolean) -> Unit,
    onHideNavigationBarChanged: (Boolean) -> Unit,
    onBrightnessChanged: (KioskBrightnessOption) -> Unit,
    onScreensaverClick: () -> Unit,
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
            SwitchRow(
                title = stringResource(commonR.string.kiosk_enabled),
                subtitle = stringResource(commonR.string.kiosk_enabled_summary),
                checked = viewState.enabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        HASettingsCard {
            SwitchRow(
                title = stringResource(commonR.string.kiosk_require_authentication),
                subtitle = stringResource(commonR.string.kiosk_require_authentication_summary),
                checked = viewState.requireAuthentication,
                onCheckedChange = onRequireAuthenticationChanged,
            )
        }

        RemoteCommandsSection(
            acceptRemoteCommands = viewState.acceptRemoteCommands,
            showRemoteCommandConfirmations = viewState.showRemoteCommandConfirmations,
            onAcceptRemoteCommandsChanged = onAcceptRemoteCommandsChanged,
            onShowRemoteCommandConfirmationsChanged = onShowRemoteCommandConfirmationsChanged,
        )

        DisplaySection(
            autoReload = viewState.autoReload,
            keepScreenOn = viewState.keepScreenOn,
            hideStatusBar = viewState.hideStatusBar,
            hideNavigationBar = viewState.hideNavigationBar,
            brightness = viewState.brightness,
            onAutoReloadChanged = onAutoReloadChanged,
            onKeepScreenOnChanged = onKeepScreenOnChanged,
            onHideStatusBarChanged = onHideStatusBarChanged,
            onHideNavigationBarChanged = onHideNavigationBarChanged,
            onBrightnessChanged = onBrightnessChanged,
        )

        HASettingsCard {
            NavigationRow(
                title = stringResource(commonR.string.kiosk_screensaver_title),
                subtitle = stringResource(commonR.string.kiosk_screensaver_summary),
                onClick = onScreensaverClick,
            )
        }
    }
}

/** A row that opens another settings screen. */
@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = title,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
        )
        Text(
            text = subtitle,
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextSecondary,
        )
    }
}

@Composable
private fun RemoteCommandsSection(
    acceptRemoteCommands: Boolean,
    showRemoteCommandConfirmations: Boolean,
    onAcceptRemoteCommandsChanged: (Boolean) -> Unit,
    onShowRemoteCommandConfirmationsChanged: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(commonR.string.kiosk_remote_commands_title))

    HASettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
            SwitchRow(
                title = stringResource(commonR.string.kiosk_accept_remote_commands),
                subtitle = null,
                checked = acceptRemoteCommands,
                onCheckedChange = onAcceptRemoteCommandsChanged,
            )

            // Nothing arrives to confirm while the commands are refused, so the row that configures
            // those confirmations goes away with them.
            AnimatedVisibility(visible = acceptRemoteCommands) {
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_show_remote_command_confirmations),
                    subtitle = null,
                    checked = showRemoteCommandConfirmations,
                    onCheckedChange = onShowRemoteCommandConfirmationsChanged,
                )
            }
        }
    }

    FooterText(stringResource(commonR.string.kiosk_remote_commands_footer))
}

@Composable
private fun KioskSettingsLocked(onUnlockClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(commonR.string.kiosk_locked_title),
            style = HATextStyle.Headline,
            color = LocalHAColorScheme.current.colorTextPrimary,
        )
        HAFilledButton(
            text = stringResource(commonR.string.kiosk_locked_unlock),
            onClick = onUnlockClick,
        )
    }
}

/** How often the dashboard reloads on its own. */
@Preview
@Composable
private fun KioskSettingsContentPreview() {
    HAThemeForPreview {
        KioskSettingsContent(
            viewState = KioskSettingsViewState(
                enabled = true,
                hideStatusBar = true,
                brightness = KioskBrightnessOption.Fixed(PREVIEW_BRIGHTNESS_PERCENT),
            ),
            onEnabledChanged = {},
            onRequireAuthenticationChanged = {},
            onAcceptRemoteCommandsChanged = {},
            onShowRemoteCommandConfirmationsChanged = {},
            onAutoReloadChanged = {},
            onKeepScreenOnChanged = {},
            onHideStatusBarChanged = {},
            onHideNavigationBarChanged = {},
            onBrightnessChanged = {},
            onScreensaverClick = {},
        )
    }
}
