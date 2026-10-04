package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HASlider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundLevel
import io.homeassistant.companion.android.common.data.kiosk.KioskSoundThreshold
import kotlin.math.roundToInt

/** Height of the level meter bar. */
private val METER_HEIGHT = HADimens.SPACE3

/**
 * The sound-waking settings: the toggle, and the meter and slider used to calibrate it.
 *
 * [level] is the current microphone level, or `null` when nothing is listening. Both it and
 * [threshold] are in dBFS and are placed on the meter by the same mapping, so the bar and the
 * marker can be read against each other.
 *
 * [onThresholdChanged] receives a point on the meter's 0..1 travel, which is what the slider works
 * in; turning that into a level is the model's job.
 */
@Composable
internal fun SoundWakeSection(
    wakeOnSound: Boolean,
    threshold: KioskSoundThreshold,
    level: KioskSoundLevel?,
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
                            value = threshold.meterPosition,
                            onValueChange = onThresholdChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SoundLevelReadout(level = level, threshold = threshold)
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
 * Both sit on one scale so the user can talk, watch where the bar reaches, and put the line just
 * under it.
 */
@Composable
private fun SoundLevelMeter(level: KioskSoundLevel?, threshold: KioskSoundThreshold) {
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
            val levelWidth = ((level?.meterPosition ?: 0f) * trackWidth).toInt()
            val markerWidth = MARKER_WIDTH_PX
            val markerX = (threshold.meterPosition * trackWidth).toInt()
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

/**
 * The numbers behind the bar: where the line is set, and what the microphone hears right now.
 *
 * The bar alone is enough to calibrate by, but it cannot be reported or compared. Decibels can:
 * "it only reaches -45 dB when I shout" is something a user can tell somebody, and it is also the
 * quickest way to see that the scale itself is behaving.
 */
@Composable
private fun SoundLevelReadout(level: KioskSoundLevel?, threshold: KioskSoundThreshold) {
    val colorScheme = LocalHAColorScheme.current
    val thresholdText = stringResource(
        commonR.string.kiosk_sound_wake_threshold_value,
        threshold.dbfs.roundToInt(),
    )
    val levelText = level?.let {
        stringResource(commonR.string.kiosk_sound_wake_level_value, it.dbfs.roundToInt())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = thresholdText,
            style = HATextStyle.BodyMedium,
            color = colorScheme.colorTextSecondary,
        )
        if (levelText != null) {
            Text(
                text = levelText,
                style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.End),
                color = colorScheme.colorTextSecondary,
            )
        }
    }
}

/** Width of the threshold marker in pixels; a hairline would be hard to see against the bar. */
private const val MARKER_WIDTH_PX = 6
