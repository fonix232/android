package io.homeassistant.companion.android.common.data.kiosk

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** An order to the screensaver that did not come from the user touching the device. */
sealed interface KioskScreensaverRequest {
    /** Cover the dashboard now, without waiting for the idle timeout. */
    data object Show : KioskScreensaverRequest

    /** Uncover the dashboard now, and restart the idle countdown. */
    data object Hide : KioskScreensaverRequest
}

/**
 * Carries screensaver requests to the screen showing it, and its visibility back out.
 *
 * It exists because the two ends live apart: requests come from a server command handled
 * process-wide, visibility is reported by a sensor in this module, and the screensaver itself is
 * owned by the screen that draws it. This is the one place they meet, so neither end has to know
 * the other.
 *
 * Requests are dropped when no screen is listening. That is deliberate: a command to cover a
 * dashboard nobody is showing has nothing to act on, and replaying it later would cover the
 * dashboard at some unrelated moment.
 */
@Singleton
class KioskScreensaverController @Inject constructor() {

    private val _requests = MutableSharedFlow<KioskScreensaverRequest>(extraBufferCapacity = REQUEST_BUFFER)

    /** Requests waiting to be honored by whichever screen can show the screensaver. */
    val requests: Flow<KioskScreensaverRequest> = _requests.asSharedFlow()

    private val _isVisible = MutableStateFlow(false)

    /** Whether the screensaver is covering the dashboard right now. */
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** Asks the screen to show or hide the screensaver. */
    fun request(request: KioskScreensaverRequest) {
        _requests.tryEmit(request)
    }

    /** Reports whether the screensaver is on screen, for the sensor to pick up. */
    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
    }

    private companion object {
        /**
         * Enough room that a burst of commands is not lost while the screen catches up, small
         * enough that a backlog cannot build up unnoticed.
         */
        const val REQUEST_BUFFER = 8
    }
}
