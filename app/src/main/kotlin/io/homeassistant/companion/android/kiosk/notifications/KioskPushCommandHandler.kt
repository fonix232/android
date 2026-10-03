package io.homeassistant.companion.android.kiosk.notifications

import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverController
import io.homeassistant.companion.android.common.data.kiosk.KioskScreensaverRequest
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Obeys the kiosk commands that arrive from Home Assistant as notification messages.
 *
 * Commands are refused outright unless the user has left remote commands enabled, so a server
 * cannot reconfigure a device whose owner has opted out.
 */
@Singleton
class KioskPushCommandHandler @Inject constructor(
    private val kioskSettingsRepository: KioskSettingsRepository,
    private val screensaverController: KioskScreensaverController,
) {

    /**
     * Applies [command], and returns whether it was obeyed.
     *
     * False means the user refuses remote commands; the caller surfaces nothing in that case,
     * because a refused command is a configuration choice rather than an error to report.
     */
    suspend fun handle(command: KioskPushCommand): Boolean {
        if (!kioskSettingsRepository.getSettings().acceptRemoteCommands) {
            Timber.d("Ignoring kiosk command, remote commands are refused")
            return false
        }

        when (command) {
            // Through updateSettings rather than a read followed by a write: two commands arriving
            // together, or a command racing the settings screen, would otherwise each persist a
            // whole configuration read before the other landed.
            is KioskPushCommand.SetBrightness ->
                kioskSettingsRepository.updateSettings { it.copy(brightness = command.brightness) }

            is KioskPushCommand.SetScreensaverMode ->
                kioskSettingsRepository.updateSettings { it.copy(screensaverMode = command.mode) }

            // These two change nothing stored: they ask the screen showing the dashboard to cover
            // or uncover it now, leaving the configured timeout alone.
            KioskPushCommand.ShowScreensaver -> screensaverController.request(KioskScreensaverRequest.Show)
            KioskPushCommand.HideScreensaver -> screensaverController.request(KioskScreensaverRequest.Hide)
        }
        return true
    }

    /** Whether obeying a command should tell the user it happened. */
    suspend fun shouldConfirm(): Boolean = kioskSettingsRepository.getSettings().showRemoteCommandConfirmations
}
