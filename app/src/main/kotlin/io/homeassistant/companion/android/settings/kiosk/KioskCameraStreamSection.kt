package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * The camera stream settings: the switch, the credentials it requires, and the port it listens on.
 *
 * The password is shown rather than hidden. It is generated, used once when adding the camera to
 * Home Assistant, and never typed again, so a user who cannot read it cannot use the feature at
 * all -- and anyone who can see this screen can already turn the stream off.
 */
@Composable
internal fun CameraStreamSection(
    enabled: Boolean,
    port: String,
    password: String?,
    onSettingChanged: (KioskCameraStreamSetting) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        SectionHeader(stringResource(commonR.string.kiosk_camera_stream_title))

        HASettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_camera_stream),
                    subtitle = null,
                    checked = enabled,
                    onCheckedChange = { onSettingChanged(KioskCameraStreamSetting.Enabled(it)) },
                )

                AnimatedVisibility(visible = enabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                        HATextField(
                            value = port,
                            onValueChange = { onSettingChanged(KioskCameraStreamSetting.Port(it)) },
                            label = { Text(stringResource(commonR.string.kiosk_camera_stream_port)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ReadOnlyValue(
                            label = stringResource(commonR.string.kiosk_camera_stream_url),
                            value = ":$port$STREAM_PATH",
                        )

                        password?.let {
                            ReadOnlyValue(
                                label = stringResource(commonR.string.kiosk_camera_stream_password),
                                value = it,
                            )
                        }

                        HAFilledButton(
                            text = stringResource(commonR.string.kiosk_camera_stream_password_regenerate),
                            onClick = { onSettingChanged(KioskCameraStreamSetting.RegeneratePassword) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        FooterText(stringResource(commonR.string.kiosk_camera_stream_footer))
    }
}

/** Shows a value the user reads and copies rather than edits. */
@Composable
private fun ReadOnlyValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
        Text(
            text = label,
            style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            color = LocalHAColorScheme.current.colorTextPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The path the stream answers on, repeated here so the settings show what to point a camera at. */
private const val STREAM_PATH = "/camera"
