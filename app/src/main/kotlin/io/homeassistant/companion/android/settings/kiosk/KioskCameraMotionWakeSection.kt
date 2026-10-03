package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HADimens

/**
 * The movement-waking setting: one switch, and the disclosure that goes with opening a camera.
 *
 * There is no calibration here, unlike sound. What counts as movement is a property of the scene
 * rather than of the room's volume, and the thresholds that do need tuning -- how often to look,
 * how much of the frame has to change -- belong with the camera's own sensor settings rather than
 * in front of someone who only wants the screensaver to step aside as they walk up.
 */
@Composable
internal fun CameraMotionWakeSection(wakeOnCameraMotion: Boolean, onWakeOnCameraMotionChanged: (Boolean) -> Unit) {
    // One Column rather than three siblings: this section is placed inside an AnimatedVisibility,
    // whose content is a single slot, so loose siblings would be stacked on top of each other.
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        SectionHeader(stringResource(commonR.string.kiosk_camera_wake_title))

        HASettingsCard {
            SwitchRow(
                title = stringResource(commonR.string.kiosk_camera_wake),
                subtitle = null,
                checked = wakeOnCameraMotion,
                onCheckedChange = onWakeOnCameraMotionChanged,
            )
        }

        FooterText(stringResource(commonR.string.kiosk_camera_wake_footer))
    }
}
