package io.homeassistant.companion.android.launch

/**
 * Which system bars the hosting activity window should hide.
 *
 * The fullscreen preference and the frontend's transient fullscreen requests hide both bars
 * together, while kiosk mode hides the status bar and the navigation bar independently.
 */
internal data class SystemBars(val statusBarHidden: Boolean = false, val navigationBarHidden: Boolean = false) {

    /** Whether either bar is hidden. */
    val anyHidden: Boolean
        get() = statusBarHidden || navigationBarHidden

    companion object {
        val VISIBLE = SystemBars()

        val HIDDEN = SystemBars(statusBarHidden = true, navigationBarHidden = true)
    }
}
