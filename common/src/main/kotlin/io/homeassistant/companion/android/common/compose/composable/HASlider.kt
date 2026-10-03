package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A slider for choosing a value from a continuous range, based on [Slider].
 *
 * Material's slider only lets a caller adjust its colors, so this wrapper exists to apply
 * [io.homeassistant.companion.android.common.compose.theme.HAColorScheme] rather than to change
 * how it behaves.
 *
 * @param value the current value, coerced into [valueRange] by [Slider]
 * @param onValueChange called continuously while the handle is dragged. Use [onValueChangeFinished]
 * for work that should happen once the user settles on a value, such as persisting it
 * @param modifier the [Modifier] to be applied to this slider
 * @param enabled whether this slider responds to input
 * @param valueRange the range this slider spans
 * @param steps the number of discrete values between the ends of [valueRange], or `0` for a
 * continuous slider
 * @param onValueChangeFinished called when the user stops dragging
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 * emitting interactions
 */
@Composable
fun HASlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors(),
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}

@Composable
private fun sliderColors(): SliderColors {
    val scheme = LocalHAColorScheme.current
    return with(scheme) {
        SliderDefaults.colors(
            thumbColor = colorFillPrimaryLoudResting,
            activeTrackColor = colorFillPrimaryLoudResting,
            activeTickColor = colorOnPrimaryLoud,
            // Normal rather than quiet, so the unfilled track stays visible on a settings card
            // and not just on a plain surface.
            inactiveTrackColor = colorFillNeutralNormalResting,
            inactiveTickColor = colorFillNeutralLoudResting,
            disabledThumbColor = colorFillDisabledLoudResting,
            disabledActiveTrackColor = colorFillDisabledLoudResting,
            disabledActiveTickColor = colorOnDisabledLoud,
            disabledInactiveTrackColor = colorFillDisabledQuietResting,
            disabledInactiveTickColor = colorFillDisabledNormalResting,
        )
    }
}

@Preview
@Composable
private fun HASliderPreview() {
    HAThemeForPreview {
        HASlider(value = 0.4f, onValueChange = {})
    }
}

@Preview
@Composable
private fun HASliderSteppedPreview() {
    HAThemeForPreview {
        HASlider(value = 0.5f, onValueChange = {}, steps = 4)
    }
}

@Preview
@Composable
private fun HASliderDisabledPreview() {
    HAThemeForPreview {
        HASlider(value = 0.7f, onValueChange = {}, enabled = false)
    }
}
