package io.homeassistant.companion.android.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Cog
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.kiosk.KioskCornerPosition

/**
 * The way back into the kiosk settings from a locked-down dashboard.
 *
 * It matters because kiosk mode can hide the system bars, leaving no obvious route anywhere else.
 *
 * A hidden entry is drawn fully transparent but keeps its place and stays tappable, so whoever set
 * the device up can still reach the settings while nobody looking at the dashboard sees a way in.
 * It keeps its accessibility label either way: hiding it from sight is the point, hiding it from a
 * screen reader user who needs it is not.
 */
@Composable
internal fun KioskSettingsEntryButton(entry: KioskSettingsEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // Kiosk mode draws edge to edge and can leave the system bars visible, so without this
            // the button sits under the navigation bar or a cutout: a way back in that cannot be
            // tapped is not a way back in.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Icon(
            imageVector = Mdi.Cog.rememberImageVector(),
            contentDescription = stringResource(commonR.string.kiosk_settings_entry_description),
            // Explicit: the icon inherits a black content colour here, which all but disappears
            // against its own dark background in the dark theme.
            tint = LocalHAColorScheme.current.colorOnNeutralQuiet,
            modifier = Modifier
                .align(entry.position.toAlignment())
                .padding(HADimens.SPACE4)
                .alpha(if (entry.hidden) 0f else 1f)
                .clip(CircleShape)
                .background(LocalHAColorScheme.current.colorFillNeutralQuietResting)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(HADimens.SPACE2)
                .size(HASize.X2L),
        )
    }
}

/** Where in the containing box this corner sits, respecting right-to-left layouts. */
private fun KioskCornerPosition.toAlignment(): Alignment = when (this) {
    KioskCornerPosition.TOP_START -> Alignment.TopStart
    KioskCornerPosition.TOP_END -> Alignment.TopEnd
    KioskCornerPosition.BOTTOM_START -> Alignment.BottomStart
    KioskCornerPosition.BOTTOM_END -> Alignment.BottomEnd
}
