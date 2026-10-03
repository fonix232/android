package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.data.kiosk.KioskAutoReloadInterval

/** Fixed brightness levels offered, as whole percentages. */
private val BRIGHTNESS_PERCENT_CHOICES = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

@Composable
internal fun DisplaySection(
    autoReload: KioskAutoReloadInterval,
    keepScreenOn: Boolean,
    hideStatusBar: Boolean,
    hideNavigationBar: Boolean,
    brightness: KioskBrightnessOption,
    onDisplaySettingChanged: (KioskDisplaySetting) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        SectionHeader(stringResource(commonR.string.kiosk_display_title))

        HASettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_keep_screen_on),
                    subtitle = null,
                    checked = keepScreenOn,
                    onCheckedChange = { onDisplaySettingChanged(KioskDisplaySetting.KeepScreenOn(it)) },
                )
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_hide_status_bar),
                    subtitle = null,
                    checked = hideStatusBar,
                    onCheckedChange = { onDisplaySettingChanged(KioskDisplaySetting.HideStatusBar(it)) },
                )
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_hide_navigation_bar),
                    subtitle = null,
                    checked = hideNavigationBar,
                    onCheckedChange = { onDisplaySettingChanged(KioskDisplaySetting.HideNavigationBar(it)) },
                )
                BrightnessRow(
                    selected = brightness,
                    onBrightnessChanged = { onDisplaySettingChanged(KioskDisplaySetting.Brightness(it)) },
                )
                AutoReloadRow(
                    selected = autoReload,
                    onAutoReloadChanged = { onDisplaySettingChanged(KioskDisplaySetting.AutoReload(it)) },
                )
            }
        }

        FooterText(stringResource(commonR.string.kiosk_display_footer))
    }
}

/** The picker for how often the dashboard reloads on its own. */
@Composable
private fun AutoReloadRow(selected: KioskAutoReloadInterval, onAutoReloadChanged: (KioskAutoReloadInterval) -> Unit) {
    val items = KioskAutoReloadInterval.entries.map { entry ->
        HADropdownItem(key = entry, label = autoReloadLabel(entry))
    }

    HADropdownMenu(
        items = items,
        selectedKey = selected,
        onItemSelected = onAutoReloadChanged,
        label = stringResource(commonR.string.kiosk_auto_reload),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Names [interval] for the picker, reusing the minute and hour plurals the screensaver uses. */
@Composable
private fun autoReloadLabel(interval: KioskAutoReloadInterval): String {
    val duration = interval.interval ?: return stringResource(commonR.string.kiosk_auto_reload_never)
    val minutes = duration.inWholeMinutes.toInt()
    return if (minutes % MINUTES_PER_HOUR == 0) {
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
