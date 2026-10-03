package io.homeassistant.companion.android.settings.kiosk

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.data.kiosk.KioskCornerPosition

/**
 * Where the button that opens these settings sits, and whether it can be seen.
 *
 * Hiding it asks for confirmation, because on a device with the system bars hidden too this button
 * can be the only obvious way back in, and a user who forgets where they put it has a puzzle rather
 * than a setting.
 */
@Composable
internal fun SettingsEntrySection(
    position: KioskCornerPosition,
    hidden: Boolean,
    onPositionChanged: (KioskCornerPosition) -> Unit,
    onHiddenChanged: (Boolean) -> Unit,
) {
    var confirmingHide by remember { mutableStateOf(false) }

    SectionHeader(stringResource(commonR.string.kiosk_settings_entry_title))

    HASettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
            PositionRow(position = position, onPositionChanged = onPositionChanged)
            SwitchRow(
                title = stringResource(commonR.string.kiosk_settings_entry_hidden),
                subtitle = null,
                checked = hidden,
                onCheckedChange = { hide -> if (hide) confirmingHide = true else onHiddenChanged(false) },
            )
        }
    }

    FooterText(stringResource(commonR.string.kiosk_settings_entry_footer))

    if (confirmingHide) {
        AlertDialog(
            onDismissRequest = { confirmingHide = false },
            title = { Text(stringResource(commonR.string.kiosk_settings_entry_hide_confirm_title)) },
            text = { Text(stringResource(commonR.string.kiosk_settings_entry_hide_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingHide = false
                        onHiddenChanged(true)
                    },
                ) {
                    Text(stringResource(commonR.string.kiosk_settings_entry_hide_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingHide = false }) {
                    Text(stringResource(commonR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PositionRow(position: KioskCornerPosition, onPositionChanged: (KioskCornerPosition) -> Unit) {
    val context = LocalContext.current
    val items = remember(context) {
        KioskCornerPosition.entries.map { HADropdownItem(key = it, label = context.positionLabel(it)) }
    }

    HADropdownMenu(
        items = items,
        selectedKey = position,
        onItemSelected = onPositionChanged,
        label = stringResource(commonR.string.kiosk_settings_entry_position),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Names [position] for the picker. */
private fun Context.positionLabel(position: KioskCornerPosition): String = getString(
    when (position) {
        KioskCornerPosition.TOP_START -> commonR.string.kiosk_corner_top_start
        KioskCornerPosition.TOP_END -> commonR.string.kiosk_corner_top_end
        KioskCornerPosition.BOTTOM_START -> commonR.string.kiosk_corner_bottom_start
        KioskCornerPosition.BOTTOM_END -> commonR.string.kiosk_corner_bottom_end
    },
)
