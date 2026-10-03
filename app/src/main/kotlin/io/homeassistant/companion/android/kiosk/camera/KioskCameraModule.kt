package io.homeassistant.companion.android.kiosk.camera

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the kiosk camera.
 *
 * [KioskCameraCapture] is built here rather than with `@Inject` so that its dispatcher and sharing
 * scope stay constructor defaults a test can replace, matching
 * [io.homeassistant.companion.android.common.util.VoiceAudioRecorder].
 */
@Module
@InstallIn(SingletonComponent::class)
internal object KioskCameraModule {

    @Provides
    @Singleton
    fun provideKioskCameraCapture(@ApplicationContext context: Context): KioskCameraCapture =
        KioskCameraCapture(context)
}
