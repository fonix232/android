package io.homeassistant.companion.android.settings.kiosk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.authenticator.Authenticator
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
                        onRequireAuthenticationChanged = ::onRequireAuthenticationChanged,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The stored settings arrive asynchronously, so the lock state is usually still UNKNOWN
        // when this screen appears; watching it is what makes the prompt appear on first entry as
        // well as on return.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState
                    .map { it.lock }
                    .distinctUntilChanged()
                    .filter { it == KioskSettingsLock.LOCKED }
                    .collect { authenticate() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.kiosk_title)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen relocks it. Otherwise whoever picks the device up next finds the
        // protected settings already open, which is the thing this protects against.
        viewModel.onUnlockedChanged(false)
    }

    /**
     * Turns protection on only once the user has proved they can turn it off again.
     *
     * Persisting it first would strand anyone whose device has no enrolled credential: the switch
     * sits behind the same gate, every prompt fails, and clearing the app's data is the only way
     * out.
     */
    private fun onRequireAuthenticationChanged(require: Boolean) {
        val activity = activity
        if (!require || activity == null) {
            viewModel.onRequireAuthenticationChanged(false)
        } else if (BiometricManager.from(activity).canAuthenticate(Authenticator.AUTH_TYPES) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Toast.makeText(activity, commonR.string.kiosk_require_authentication_unavailable, Toast.LENGTH_LONG).show()
        } else {
            Authenticator(activity) { result ->
                if (result == Authenticator.Companion.AuthenticationResult.SUCCESS) {
                    viewModel.onUnlockedChanged(true)
                    viewModel.onRequireAuthenticationChanged(true)
                }
            }.authenticate(getString(commonR.string.kiosk_locked_title))
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
                viewModel.onUnlockedChanged(true)
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
