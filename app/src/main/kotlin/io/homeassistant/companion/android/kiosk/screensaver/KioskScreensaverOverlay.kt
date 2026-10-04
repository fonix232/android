package io.homeassistant.companion.android.kiosk.screensaver

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import java.util.Date
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Draws the screensaver described by [state] over whatever else is on screen.
 *
 * It swallows touches so the gesture that dismisses the screensaver cannot also press whatever sits
 * underneath it, and takes the dashboard underneath out of the accessibility tree so a screen
 * reader cannot reach a control the screensaver is covering. [onDismiss] is what its description
 * promises: activating the screensaver puts the dashboard back.
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun KioskScreensaverOverlay(
    state: KioskScreensaverUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(context, state.now) { context.formatScreensaverTime(state.now) }
    val date = remember(context, state.now) { context.formatScreensaverDate(state.now) }

    KioskScreensaverContent(
        mode = state.mode,
        time = time,
        date = date,
        modifier = modifier
            .fillMaxSize()
            // One node carrying the surface's description and a real activation, so a screen
            // reader can dismiss the screensaver rather than only being told that touching does.
            .semantics(mergeDescendants = true) {
                isTraversalGroup = true
                onClick {
                    onDismiss()
                    true
                }
            }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    )
}

/** Formats [instant] as a clock time, honoring the device's 12- or 24-hour preference. */
@OptIn(ExperimentalTime::class)
private fun Context.formatScreensaverTime(instant: Instant): String =
    DateFormat.getTimeFormat(this).format(Date(instant.toEpochMilliseconds()))

/** Formats [instant] as a weekday and date in the device's locale, without the year. */
@OptIn(ExperimentalTime::class)
private fun Context.formatScreensaverDate(instant: Instant): String = DateUtils.formatDateTime(
    this,
    instant.toEpochMilliseconds(),
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_NO_YEAR,
)
