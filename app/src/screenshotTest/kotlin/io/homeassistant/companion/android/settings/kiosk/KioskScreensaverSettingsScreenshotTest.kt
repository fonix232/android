package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import kotlin.time.Duration.Companion.minutes

class KioskScreensaverSettingsScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Screensaver settings with the screensaver off`() {
        HAThemeForPreview {
            KioskScreensaverSettingsContent(
                viewState = KioskScreensaverSettingsViewState(mode = KioskScreensaverMode.DISABLED),
                onModeChanged = {},
                onIdleTimeoutChanged = {},
            )
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Screensaver settings with a clock screensaver`() {
        HAThemeForPreview {
            KioskScreensaverSettingsContent(
                viewState = KioskScreensaverSettingsViewState(
                    mode = KioskScreensaverMode.CLOCK,
                    idleTimeout = 10.minutes,
                ),
                onModeChanged = {},
                onIdleTimeoutChanged = {},
            )
        }
    }
}
