package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class KioskSettingsScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Kiosk settings with kiosk mode off`() {
        HAThemeForPreview {
            KioskSettingsContent(
                viewState = KioskSettingsViewState(),
                onEnabledChanged = {},
                onAcceptRemoteCommandsChanged = {},
                onShowRemoteCommandConfirmationsChanged = {},
                onHideStatusBarChanged = {},
                onHideNavigationBarChanged = {},
                onBrightnessChanged = {},
                onScreensaverClick = {},
            )
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Kiosk settings with a fixed brightness`() {
        HAThemeForPreview {
            KioskSettingsContent(
                viewState = KioskSettingsViewState(
                    enabled = true,
                    hideStatusBar = true,
                    hideNavigationBar = true,
                    brightness = KioskBrightnessOption.Fixed(40),
                ),
                onEnabledChanged = {},
                onAcceptRemoteCommandsChanged = {},
                onShowRemoteCommandConfirmationsChanged = {},
                onHideStatusBarChanged = {},
                onHideNavigationBarChanged = {},
                onBrightnessChanged = {},
                onScreensaverClick = {},
            )
        }
    }
}
