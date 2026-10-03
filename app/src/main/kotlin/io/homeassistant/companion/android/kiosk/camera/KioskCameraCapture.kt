package io.homeassistant.companion.android.kiosk.camera

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Provides the kiosk camera as a shared [Flow] of frames.
 *
 * Every collector shares one capture session, the way [io.homeassistant.companion.android.common.util.VoiceAudioRecorder]
 * shares one recording: the first starts the camera, later ones join the same stream, and the
 * camera is released when the last one goes. Motion detection, the MJPEG stream and the camera
 * popup can therefore all run at once without competing for a device that only opens once.
 *
 * Nothing here checks the camera permission or whether the user asked for any of this. Callers do,
 * because the answer differs between them, and a capture that started itself would be the worst
 * kind of bug to find on a wall tablet.
 */
internal class KioskCameraCapture(
    private val context: Context,
    private val captureDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sharingScope: CoroutineScope = CoroutineScope(Job() + captureDispatcher),
) {

    private val mutex = Mutex()
    private var sharedFlow: Flow<KioskCameraFrame>? = null

    /**
     * Returns the shared frame flow, starting the camera on the first collector.
     *
     * Frames arrive at whatever rate the camera and the slowest consumer allow: the analyzer keeps
     * only the newest frame, so a slow reader drops frames rather than delaying everyone else.
     */
    fun frames(): Flow<KioskCameraFrame> = flow {
        emitAll(mutex.withLock { sharedFlow ?: createSharedFlow().also { sharedFlow = it } })
    }

    @SuppressLint("MissingPermission")
    private fun createSharedFlow(): Flow<KioskCameraFrame> {
        val upstream = channelFlow {
            val provider = ProcessCameraProvider.getInstance(context).awaitCameraProvider()
            val executor = Executors.newSingleThreadExecutor()
            val lifecycle = CaptureLifecycleOwner()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            analysis.setAnalyzer(executor) { image ->
                image.use { trySend(it.toFrame()) }
            }

            try {
                withContext(Dispatchers.Main) {
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                    lifecycle.start()
                }
                awaitClose { }
            } finally {
                withContext(Dispatchers.Main) {
                    lifecycle.stop()
                    provider.unbind(analysis)
                }
                analysis.clearAnalyzer()
                executor.shutdown()
                Timber.d("Kiosk camera released")
            }
        }

        return upstream.shareIn(
            scope = sharingScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 0,
        )
    }
}

/**
 * Suspends until this camera provider is ready.
 *
 * CameraX hands back a `ListenableFuture`; awaiting it here avoids a dependency on the
 * concurrent-futures coroutine adapter for one call.
 */
private suspend fun ListenableFuture<ProcessCameraProvider>.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        addListener({
            runCatching { get() }
                .onSuccess(continuation::resume)
                .onFailure(continuation::resumeWithException)
        }, Runnable::run)
        continuation.invokeOnCancellation { cancel(false) }
    }

/**
 * A lifecycle that exists only to hold the capture open.
 *
 * CameraX binds to a lifecycle rather than to a start and stop call, and the lifetime wanted here
 * is the shared flow's, which belongs to no screen.
 */
private class CaptureLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * Copies this YUV_420_888 image into NV21.
 *
 * NV21 is the luminance plane followed by interleaved V and U samples, which is the one layout both
 * readers want: motion detection reads the luminance prefix, and `YuvImage` only accepts NV21 or
 * YUY2 when compressing to JPEG.
 *
 * Every plane is read through its own strides. Devices pad rows to a hardware-friendly width often
 * enough that assuming a tight packing gives a picture skewed diagonally instead of a crash.
 */
@VisibleForTesting
internal fun ImageProxy.toFrame(): KioskCameraFrame {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val ySize = width * height
    val nv21 = ByteArray(ySize + ySize / 2)

    val yBuffer = yPlane.buffer
    if (yPlane.rowStride == width) {
        yBuffer.get(nv21, 0, minOf(yBuffer.remaining(), ySize))
    } else {
        for (row in 0 until height) {
            yBuffer.position(row * yPlane.rowStride)
            yBuffer.get(nv21, row * width, width)
        }
    }

    // V and U are interleaved starting at the luminance plane's end, V first.
    val chromaRowStride = vPlane.rowStride
    val chromaPixelStride = vPlane.pixelStride
    val vBuffer = vPlane.buffer
    val uBuffer = uPlane.buffer
    var offset = ySize
    for (row in 0 until height / 2) {
        for (column in 0 until width / 2) {
            val index = row * chromaRowStride + column * chromaPixelStride
            if (index < vBuffer.limit()) nv21[offset] = vBuffer.get(index)
            if (index < uBuffer.limit()) nv21[offset + 1] = uBuffer.get(index)
            offset += 2
        }
    }

    return KioskCameraFrame(
        nv21 = nv21,
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
    )
}
