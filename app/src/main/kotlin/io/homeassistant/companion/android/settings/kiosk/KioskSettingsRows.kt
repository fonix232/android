package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Explanatory text under a section, start-aligned for the same reason as [SectionHeader]. */
@Composable
internal fun FooterText(text: String) {
    Text(
        text = text,
        style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
        color = LocalHAColorScheme.current.colorTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}
