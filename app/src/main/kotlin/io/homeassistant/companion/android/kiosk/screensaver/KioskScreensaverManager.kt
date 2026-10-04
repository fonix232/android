package io.homeassistant.companion.android.kiosk.screensaver

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverController
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverMode
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverRequest
import io.homeassistant.companion.android.kiosk.KioskScreensaver
import io.homeassistant.companion.android.kiosk.ObserveKioskStateUseCase
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * What the screensaver should currently show.
 *
 * [now] is the instant the clock screensaver displays; formatting it for the locale is the UI's job.
 */
internal data class KioskScreensaverUiState(val mode: KioskScreensaverMode, val now: Instant)

/**
 * Decides when the kiosk screensaver covers the dashboard, and keeps its clock current while it
 * does.
 *
 * The device counts as idle once [onUserInteraction] has not been called for the configured idle
 * timeout.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@ViewModelScoped
internal class KioskScreensaverManager @Inject constructor(
    private val observeKioskState: ObserveKioskStateUseCase,
    private val screensaverController: KioskScreensaverController,
    private val clock: Clock,
) {

    /**
     * What the idle countdown runs against.
     *
     * One value rather than two flows so a change can never be observed half-applied: hiding the
     * screensaver both clears the server's request and restarts the countdown, and those have to
     * land together.
     */
    private val idleInput = MutableStateFlow(IdleInput(interactedAt = clock.now(), requested = false))

    /** Records that the user is here, which hides the screensaver and restarts the idle countdown. */
    fun onUserInteraction() {
        idleInput.value = IdleInput(interactedAt = clock.now(), requested = false)
    }

    /**
     * Emits the screensaver to show, or `null` while the dashboard should stay visible.
     *
     * Once it is showing, the state re-emits on each minute boundary rather than on a fixed
     * interval, so the displayed time changes when the minute does instead of up to a minute late.
     */
    fun screensaverFlow(): Flow<KioskScreensaverUiState?> = observeKioskState()
        .map { it.screensaver }
        .distinctUntilChanged()
        .flatMapLatest { screensaver ->
            if (screensaver == null) flowOf(null) else idleFlow(screensaver)
        }
        .distinctUntilChanged()
        .onEach { screensaverController.setVisible(it != null) }
        // Cancellation emits nothing, so without this the sensor would keep reporting a screensaver
        // over a dashboard that is no longer being drawn.
        .onCompletion { screensaverController.setVisible(false) }

    /**
     * Honors the show and hide requests arriving from a server, for as long as it is collected.
     *
     * Showing is refused when no screensaver is configured: there would be nothing to show, and a
     * blank cover the user never asked for is worse than ignoring the command.
     */
    suspend fun observeRequests() {
        screensaverController.requests.collect { request ->
            when (request) {
                // Refused rather than remembered when nothing is configured: a retained request
                // would otherwise cover the dashboard later, when a screensaver is finally set up.
                KioskScreensaverRequest.Show -> if (observeKioskState().first().screensaver != null) {
                    idleInput.value = idleInput.value.copy(requested = true)
                }

                KioskScreensaverRequest.Hide -> onUserInteraction()
                // Reloading the dashboard is the frontend's to honor, not the screensaver's.
                KioskScreensaverRequest.Reload -> Unit
            }
        }
    }

    /**
     * Emits `null` until [screensaver]'s idle timeout passes without an interaction, then the
     * screensaver state, refreshed on every minute boundary.
     */
    private fun idleFlow(screensaver: KioskScreensaver): Flow<KioskScreensaverUiState?> = idleInput
        .flatMapLatest { input ->
            flow {
                if (!input.requested) {
                    emit(null)
                    delay(screensaver.idleTimeout - (clock.now() - input.interactedAt))
                }
                while (true) {
                    val now = clock.now()
                    emit(KioskScreensaverUiState(mode = screensaver.mode, now = now))
                    delay(now.untilNextMinute())
                }
            }
        }
}

/** When the user was last here, and whether a server has overridden that by asking for the screensaver. */
@OptIn(ExperimentalTime::class)
private data class IdleInput(val interactedAt: Instant, val requested: Boolean)

/**
 * How long until the start of the next minute.
 *
 * Waiting this long rather than a whole minute keeps the clock's displayed minute in step with the
 * real one no matter when the screensaver appeared.
 */
@OptIn(ExperimentalTime::class)
private fun Instant.untilNextMinute(): Duration =
    1.minutes - (epochSeconds.mod(SECONDS_PER_MINUTE)).seconds - nanosecondsOfSecond.nanoseconds

private const val SECONDS_PER_MINUTE = 60
