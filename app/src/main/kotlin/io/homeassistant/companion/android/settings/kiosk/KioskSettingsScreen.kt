package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import org.jetbrains.annotations.VisibleForTesting

/** Fixed brightness levels offered, as whole percentages. */
private val BRIGHTNESS_PERCENT_CHOICES = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

/** A mid-range brightness, so the preview shows the picker holding a value rather than its default. */
private const val PREVIEW_BRIGHTNESS_PERCENT = 40

@Composable
internal fun KioskSettingsScreen(
    viewModel: KioskSettingsViewModel,
    onScreensaverClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onRequireAuthenticationChanged: (Boolean) -> Unit,
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
        onRequireAuthenticationChanged = onRequireAuthenticationChanged,
        onAcceptRemoteCommandsChanged = viewModel::onAcceptRemoteCommandsChanged,
        onShowRemoteCommandConfirmationsChanged = viewModel::onShowRemoteCommandConfirmationsChanged,
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
            keepScreenOn = viewState.keepScreenOn,
            hideStatusBar = viewState.hideStatusBar,
            hideNavigationBar = viewState.hideNavigationBar,
            brightness = viewState.brightness,
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
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
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
}

@Composable
private fun DisplaySection(
    keepScreenOn: Boolean,
    hideStatusBar: Boolean,
    hideNavigationBar: Boolean,
    brightness: KioskBrightnessOption,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onHideStatusBarChanged: (Boolean) -> Unit,
    onHideNavigationBarChanged: (Boolean) -> Unit,
    onBrightnessChanged: (KioskBrightnessOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        SectionHeader(stringResource(commonR.string.kiosk_display_title))

        HASettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_keep_screen_on),
                    subtitle = null,
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOnChanged,
                )
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_hide_status_bar),
                    subtitle = null,
                    checked = hideStatusBar,
                    onCheckedChange = onHideStatusBarChanged,
                )
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_hide_navigation_bar),
                    subtitle = null,
                    checked = hideNavigationBar,
                    onCheckedChange = onHideNavigationBarChanged,
                )
                BrightnessRow(selected = brightness, onBrightnessChanged = onBrightnessChanged)
            }
        }

        FooterText(stringResource(commonR.string.kiosk_display_footer))
    }
}

/**
 * Shown in place of the settings while they are protected and not yet unlocked.
 *
 * It replaces the content rather than covering it, so a protected setting is never briefly on
 * screen behind an overlay.
 */
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

/** The brightness picker. */
@Composable
private fun BrightnessRow(selected: KioskBrightnessOption, onBrightnessChanged: (KioskBrightnessOption) -> Unit) {
    val systemLabel = stringResource(commonR.string.kiosk_brightness_system)
    // Any whole percentage is a valid stored brightness, and a server command can set one that is
    // not among the offered steps. Without its own entry the picker would render blank.
    val percentChoices = remember(selected) {
        (BRIGHTNESS_PERCENT_CHOICES + listOfNotNull((selected as? KioskBrightnessOption.Fixed)?.percent))
            .distinct()
            .sorted()
    }
    val percentLabels = percentChoices.map { stringResource(commonR.string.kiosk_brightness_percent, it) }
    val items = remember(systemLabel, percentChoices, percentLabels) {
        buildList {
            add(HADropdownItem<KioskBrightnessOption>(key = KioskBrightnessOption.SystemAdjusted, label = systemLabel))
            percentChoices.forEachIndexed { index, percent ->
                add(HADropdownItem<KioskBrightnessOption>(KioskBrightnessOption.Fixed(percent), percentLabels[index]))
            }
        }
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onBrightnessChanged,
        label = stringResource(commonR.string.kiosk_brightness),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
            Text(
                text = title,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
                color = LocalHAColorScheme.current.colorTextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                    color = LocalHAColorScheme.current.colorTextSecondary,
                )
            }
        }
        HASwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A section heading.
 *
 * Start-aligned explicitly: the shared body styles centre their text, which suits a prompt but not a
 * heading sitting above a left-aligned list.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Explanatory text under a section, start-aligned for the same reason as [SectionHeader]. */
@Composable
private fun FooterText(text: String) {
    Text(
        text = text,
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

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
            onKeepScreenOnChanged = {},
            onHideStatusBarChanged = {},
            onHideNavigationBarChanged = {},
            onBrightnessChanged = {},
            onScreensaverClick = {},
        )
    }
}
