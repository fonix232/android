package io.homeassistant.companion.android.settings.kiosk

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks that the controls on the kiosk settings screen are wired to their callbacks.
 *
 * The screenshot tests render this screen with no-op callbacks and the ViewModel tests call its
 * functions directly, so without this nothing proves the two meet: a switch wired to the wrong
 * handler, or to none, would pass both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class KioskSettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val enabledChanges = mutableListOf<Boolean>()
    private val statusBarChanges = mutableListOf<Boolean>()
    private val navigationBarChanges = mutableListOf<Boolean>()
    private val brightnessChanges = mutableListOf<KioskBrightnessOption>()
    private val acceptRemoteCommandsChanges = mutableListOf<Boolean>()
    private val showConfirmationsChanges = mutableListOf<Boolean>()

    private fun setContent(viewState: KioskSettingsViewState = KioskSettingsViewState()) {
        composeTestRule.setContent {
            HATheme {
                KioskSettingsContent(
                    viewState = viewState,
                    onEnabledChanged = enabledChanges::add,
                    onHideStatusBarChanged = statusBarChanges::add,
                    onHideNavigationBarChanged = navigationBarChanges::add,
                    onBrightnessChanged = brightnessChanges::add,
                    onRequireAuthenticationChanged = {},
                    onKeepScreenOnChanged = {},
                    onAcceptRemoteCommandsChanged = acceptRemoteCommandsChanges::add,
                    onShowRemoteCommandConfirmationsChanged = showConfirmationsChanges::add,
                    onScreensaverClick = {},
                )
            }
        }
    }

    @Test
    fun `Given the kiosk mode switch when it is tapped then the change is reported`() {
        setContent()

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_enabled)).performScrollTo().performClick()

        assertEquals(listOf(true), enabledChanges)
    }

    @Test
    fun `Given the status bar switch when it is tapped then only that change is reported`() {
        setContent()

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_hide_status_bar)).performScrollTo().performClick()

        assertEquals(listOf(true), statusBarChanges)
        assertEquals(emptyList<Boolean>(), navigationBarChanges)
    }

    @Test
    fun `Given the navigation bar switch when it is tapped then only that change is reported`() {
        setContent()

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_hide_navigation_bar))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(true), navigationBarChanges)
        assertEquals(emptyList<Boolean>(), statusBarChanges)
    }

    @Test
    fun `Given an already enabled switch when it is tapped then turning it off is reported`() {
        setContent(KioskSettingsViewState(enabled = true))

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_enabled)).performScrollTo().performClick()

        assertEquals(listOf(false), enabledChanges)
    }

    @Test
    fun `Given remote commands are refused then the confirmation row is not offered`() {
        // There is nothing to confirm while commands are refused, and a row that configures
        // confirmations for commands that never arrive is worse than no row.
        setContent(KioskSettingsViewState(acceptRemoteCommands = false))

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_show_remote_command_confirmations))
            .assertDoesNotExist()
    }

    @Test
    fun `Given remote commands are accepted then the confirmation row is offered`() {
        setContent(KioskSettingsViewState(acceptRemoteCommands = true))

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_show_remote_command_confirmations))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `Given a stored brightness outside the offered steps then it still has a label`() {
        // A server command can set any whole percentage; without its own entry the picker renders
        // blank and the user cannot see what the kiosk is set to.
        setContent(KioskSettingsViewState(brightness = KioskBrightnessOption.Fixed(25)))

        composeTestRule.onNodeWithText(string(commonR.string.kiosk_brightness_percent, 25)).assertExists()
    }

    private fun string(id: Int, vararg args: Any): String = composeTestRule.activity.getString(id, *args)
}
