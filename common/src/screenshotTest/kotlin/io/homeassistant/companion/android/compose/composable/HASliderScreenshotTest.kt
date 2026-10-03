package io.homeassistant.companion.android.compose.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HASlider
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HASliderScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Continuous HASlider`() {
        HAThemeForPreview {
            HASlider(value = 0.4f, onValueChange = {})
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Stepped HASlider`() {
        HAThemeForPreview {
            HASlider(value = 0.5f, onValueChange = {}, steps = 4)
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Disabled HASlider`() {
        HAThemeForPreview {
            HASlider(value = 0.7f, onValueChange = {}, enabled = false)
        }
    }
}
