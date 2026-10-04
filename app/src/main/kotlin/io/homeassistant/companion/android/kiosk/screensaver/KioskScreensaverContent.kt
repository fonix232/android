package io.homeassistant.companion.android.kiosk.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAFontSize
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode

/**
 * The kiosk screensaver for [mode], filling whatever it is placed in.
 *
 * [time] and [date] are already formatted for display; the screensaver neither reads a clock nor
 * formats one, so it stays a pure function of what it is given and can be rendered in a preview or
 * a screenshot test.
 *
 * [KioskScreensaverMode.DISABLED] renders nothing.
 */
@Composable
internal fun KioskScreensaverContent(
    mode: KioskScreensaverMode,
    time: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        KioskScreensaverMode.CLOCK -> ClockScreensaver(time = time, date = date, modifier = modifier)
        KioskScreensaverMode.BLANK -> BlankScreensaver(modifier = modifier)
        KioskScreensaverMode.DISABLED -> Unit
    }
}

@Composable
private fun ClockScreensaver(time: String, date: String, modifier: Modifier = Modifier) {
    ScreensaverSurface(
        contentDescription = stringResource(commonR.string.kiosk_screensaver_clock_description, time, date),
        background = LocalHAColorScheme.current.colorSurfaceDefault,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(HADimens.SPACE4),
        ) {
            Text(
                text = time,
                style = HATextStyle.Headline.copy(
                    fontSize = CLOCK_TIME_FONT_SIZE,
                    lineHeight = CLOCK_TIME_LINE_HEIGHT,
                    fontWeight = FontWeight.W200,
                ),
            )
            Text(
                text = date,
                style = HATextStyle.Body.copy(
                    fontSize = HAFontSize.X2L,
                    lineHeight = HAFontSize.X3L,
                ),
            )
        }
    }
}

/** Nothing but black. */
@Composable
private fun BlankScreensaver(modifier: Modifier = Modifier) {
    ScreensaverSurface(
        contentDescription = stringResource(commonR.string.kiosk_screensaver_blank_description),
        background = BLANK_BACKGROUND,
        modifier = modifier,
    )
}

/**
 * The surface both screensavers sit on, labelled with [contentDescription] so a screen reader
 * announces what covers the dashboard.
 *
 * It forces the dark theme instead of following the app's: the screensaver's job is to leave the
 * room dark, so it must not turn pale when the user runs the app in the light theme.
 */
@Composable
private fun ScreensaverSurface(
    contentDescription: String,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    HATheme(darkTheme = true) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .semantics { this.contentDescription = contentDescription },
        ) {
            content()
        }
    }
}

/**
 * The background a blank screensaver uses: black, not the dark theme's surface.
 *
 * The dark surface is a dark grey, which lights every pixel of an OLED panel. The blank mode exists
 * to light none of them.
 */
private val BLANK_BACKGROUND = Color.Black

/**
 * Size and line height of the clock's time, deliberately above the [HAFontSize] scale: that scale
 * tops out at the largest size in-app text needs, while this clock has to stay readable from across
 * a room.
 */
private val CLOCK_TIME_FONT_SIZE = 96.sp
private val CLOCK_TIME_LINE_HEIGHT = 112.sp

@Preview
@Composable
private fun ClockScreensaverPreview() {
    HAThemeForPreview {
        KioskScreensaverContent(mode = KioskScreensaverMode.CLOCK, time = "09:41", date = "Friday, 2 October")
    }
}

@Preview
@Composable
private fun BlankScreensaverPreview() {
    HAThemeForPreview {
        KioskScreensaverContent(mode = KioskScreensaverMode.BLANK, time = "09:41", date = "Friday, 2 October")
    }
}
