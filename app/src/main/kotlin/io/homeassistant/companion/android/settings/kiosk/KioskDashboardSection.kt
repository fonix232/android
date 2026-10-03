package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens

/**
 * Which server and dashboard this kiosk shows.
 *
 * [servers] is empty until they have loaded, which hides the picker rather than showing an empty
 * one.
 */
@Composable
internal fun DashboardSection(
    servers: List<KioskServerOption>,
    selectedServerId: Int?,
    dashboardPath: String,
    onServerChanged: (Int?) -> Unit,
    onDashboardPathChanged: (String) -> Unit,
) {
    SectionHeader(stringResource(commonR.string.kiosk_dashboard_title))

    HASettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
            if (servers.isNotEmpty()) {
                ServerRow(servers = servers, selectedServerId = selectedServerId, onServerChanged = onServerChanged)
            }

            HATextField(
                value = dashboardPath,
                onValueChange = onDashboardPathChanged,
                label = { Text(stringResource(commonR.string.kiosk_dashboard_path)) },
                placeholder = { Text(stringResource(commonR.string.kiosk_dashboard_path_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    FooterText(stringResource(commonR.string.kiosk_dashboard_footer))
}

/**
 * The server picker, with an entry for following whichever server is active.
 *
 * The keys are nullable because "whichever is active" is the absence of a choice rather than a
 * server of its own.
 */
@Composable
private fun ServerRow(servers: List<KioskServerOption>, selectedServerId: Int?, onServerChanged: (Int?) -> Unit) {
    val activeLabel = stringResource(commonR.string.kiosk_dashboard_server_active)
    val items = buildList<HADropdownItem<Int?>> {
        add(HADropdownItem(key = null, label = activeLabel))
        servers.forEach { server -> add(HADropdownItem(key = server.id, label = server.name)) }
    }

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = onServerChanged,
        label = stringResource(commonR.string.kiosk_dashboard_server),
        placeholder = activeLabel,
        modifier = Modifier.fillMaxWidth(),
    )
}
