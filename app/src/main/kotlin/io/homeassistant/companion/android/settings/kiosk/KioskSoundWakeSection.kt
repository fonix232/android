package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HASlider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/** Height of the level meter bar. */
private val METER_HEIGHT = HADimens.SPACE3

/**
 * The sound-waking settings: the toggle, and the meter and slider used to calibrate it.
 *
 * [level] is the current microphone level, 0..1, or `null` when nothing is listening.
 */
@Composable
internal fun SoundWakeSection(
    wakeOnSound: Boolean,
    threshold: Float,
    level: Float?,
    onWakeOnSoundChanged: (Boolean) -> Unit,
    onThresholdChanged: (Float) -> Unit,
) {
    // One Column rather than three siblings: this section is placed inside an AnimatedVisibility,
    // whose content is a single slot, so loose siblings would be stacked on top of each other.
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        SectionHeader(stringResource(commonR.string.kiosk_sound_wake_title))

        HASettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
                SwitchRow(
                    title = stringResource(commonR.string.kiosk_sound_wake),
                    subtitle = null,
                    checked = wakeOnSound,
                    onCheckedChange = onWakeOnSoundChanged,
                )

                // The meter and the slider are only useful together, and only while something is
                // listening, so they appear and disappear with the toggle.
                AnimatedVisibility(visible = wakeOnSound) {
                    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                        SoundLevelMeter(level = level, threshold = threshold)
                        HASlider(
                            value = threshold,
                            onValueChange = onThresholdChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        FooterText(stringResource(commonR.string.kiosk_sound_wake_footer))
    }
}

/**
 * A bar showing the current input [level] with the [threshold] marked on it.
 *
 * Showing both on one scale is the point: the user can talk, watch where the bar reaches, and put
 * the line just under it. A number on its own would mean nothing.
 */
@Composable
private fun SoundLevelMeter(level: Float?, threshold: Float) {
    val colorScheme = LocalHAColorScheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(METER_HEIGHT)
            .clip(RoundedCornerShape(HARadius.Pill))
            // Normal rather than quiet: the track sits on a settings card, which is already a
            // pale surface, and a quiet fill is invisible against it.
            .background(colorScheme.colorFillNeutralNormalResting),
    ) {
        // Laid out by hand rather than with fractional widths so the filled bar and the threshold
        // marker always measure against exactly the same track.
        Layout(
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(colorScheme.colorFillPrimaryLoudResting),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(colorScheme.colorTextPrimary),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val trackWidth = constraints.maxWidth
            val levelWidth = ((level ?: 0f).coerceIn(0f, 1f) * trackWidth).toInt()
            val markerWidth = MARKER_WIDTH_PX
            val markerX = (threshold.coerceIn(0f, 1f) * trackWidth).toInt()
                .coerceAtMost(trackWidth - markerWidth)

            val bar = measurables[0].measure(constraints.copy(minWidth = levelWidth, maxWidth = levelWidth))
            val marker = measurables[1].measure(constraints.copy(minWidth = markerWidth, maxWidth = markerWidth))

            layout(trackWidth, constraints.maxHeight) {
                bar.place(0, 0)
                marker.place(markerX, 0)
            }
        }
    }
}

/** Width of the threshold marker in pixels; a hairline would be hard to see against the bar. */
private const val MARKER_WIDTH_PX = 6
