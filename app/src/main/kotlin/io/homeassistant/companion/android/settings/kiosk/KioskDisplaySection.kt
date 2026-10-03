package io.homeassistant.companion.android.settings.kiosk

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    onAutoReloadChanged: (KioskAutoReloadInterval) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onHideStatusBarChanged: (Boolean) -> Unit,
    onHideNavigationBarChanged: (Boolean) -> Unit,
    onBrightnessChanged: (KioskBrightnessOption) -> Unit,
) {
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
            AutoReloadRow(selected = autoReload, onAutoReloadChanged = onAutoReloadChanged)
        }
    }

    FooterText(stringResource(commonR.string.kiosk_display_footer))
}

/**
 * Shown in place of the settings while they are protected and not yet unlocked.
 *
 * It replaces the content rather than covering it, so a protected setting is never briefly on
 * screen behind an overlay.
 */
@Composable
private fun AutoReloadRow(selected: KioskAutoReloadInterval, onAutoReloadChanged: (KioskAutoReloadInterval) -> Unit) {
    val context = LocalContext.current
    val items = remember(context) {
        KioskAutoReloadInterval.entries.map { entry ->
            HADropdownItem(key = entry, label = context.autoReloadLabel(entry))
        }
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
private fun Context.autoReloadLabel(interval: KioskAutoReloadInterval): String {
    val duration = interval.interval ?: return getString(commonR.string.kiosk_auto_reload_never)
    val minutes = duration.inWholeMinutes.toInt()
    return if (minutes % MINUTES_PER_HOUR == 0) {
        resources.getQuantityString(
            commonR.plurals.kiosk_screensaver_hours,
            minutes / MINUTES_PER_HOUR,
            minutes / MINUTES_PER_HOUR,
        )
    } else {
        resources.getQuantityString(commonR.plurals.kiosk_screensaver_minutes, minutes, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60

/**
 * The brightness picker.
 *
 * A dropdown of fixed levels rather than a slider: the design system has no `HASlider`, and
 * introducing one belongs in its own change rather than being styled inline here.
 */
@Composable
private fun BrightnessRow(selected: KioskBrightnessOption, onBrightnessChanged: (KioskBrightnessOption) -> Unit) {
    val context = LocalContext.current
    val items = remember(context) {
        buildList {
            add(
                HADropdownItem<KioskBrightnessOption>(
                    key = KioskBrightnessOption.SystemAdjusted,
                    label = context.getString(commonR.string.kiosk_brightness_system),
                ),
            )
            BRIGHTNESS_PERCENT_CHOICES.forEach { percent ->
                add(
                    HADropdownItem<KioskBrightnessOption>(
                        key = KioskBrightnessOption.Fixed(percent),
                        label = context.getString(commonR.string.kiosk_brightness_percent, percent),
                    ),
                )
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
