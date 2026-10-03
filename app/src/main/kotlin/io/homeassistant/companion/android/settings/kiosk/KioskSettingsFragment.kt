package io.homeassistant.companion.android.settings.kiosk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme

@AndroidEntryPoint
class KioskSettingsFragment : Fragment() {

    private val viewModel: KioskSettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    KioskSettingsScreen(
                        viewModel = viewModel,
                        onScreensaverClick = ::openScreensaverSettings,
                        onUnlockClick = ::authenticate,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.kiosk_title)
        if (viewModel.viewState.value.lock == KioskSettingsLock.LOCKED) {
            authenticate()
        }
    }

    /**
     * Asks for the device's lock credential.
     *
     * A failed or cancelled attempt leaves the screen locked rather than closing it, so the user
     * can try again without finding their way back here.
     */
    private fun authenticate() {
        val activity = activity ?: return
        Authenticator(activity) { result ->
            if (result == Authenticator.Companion.AuthenticationResult.SUCCESS) {
                viewModel.onAuthenticated()
            }
        }.authenticate(getString(commonR.string.kiosk_locked_title))
    }

    private fun openScreensaverSettings() {
        parentFragmentManager.commit {
            replace(R.id.content, KioskScreensaverSettingsFragment::class.java, null)
            addToBackStack(getString(commonR.string.kiosk_screensaver_title))
        }
    }
}
