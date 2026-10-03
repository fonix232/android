package io.homeassistant.companion.android.kiosk.screensaver

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode

/**
 * Both screensavers are rendered in light and dark: they must look identical in either, because the
 * screensaver deliberately does not follow the app theme.
 */
class KioskScreensaverScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Clock screensaver`() {
        HAThemeForPreview {
            KioskScreensaverContent(
                mode = KioskScreensaverMode.CLOCK,
                time = "09:41",
                date = "Friday, 2 October",
            )
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Blank screensaver`() {
        HAThemeForPreview {
            KioskScreensaverContent(
                mode = KioskScreensaverMode.BLANK,
                time = "09:41",
                date = "Friday, 2 October",
            )
        }
    }
}
