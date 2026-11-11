package org.routingplatform.app.navigation

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import java.io.File

class NavigationScreenRecorder(private val context: Context) {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(resultCode: Int, data: Intent): Boolean {
        if (recorder != null) return false
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val activeProjection = manager.getMediaProjection(resultCode, data) ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = (metrics.widthPixels / 2 * 2).coerceAtMost(1280)
        val height = (metrics.heightPixels / 2 * 2).coerceAtMost(720)
        val file = File(context.cacheDir, "navigation-error-"+System.currentTimeMillis()+".mp4")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return runCatching {
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setVideoEncodingBitRate(4_000_000)
            mediaRecorder.setVideoFrameRate(30)
            mediaRecorder.setVideoSize(width, height)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            val display = activeProjection.createVirtualDisplay(
                "routing-training-error", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface, null, null,
            )
            mediaRecorder.start()
            projection = activeProjection
            virtualDisplay = display
            recorder = mediaRecorder
            outputFile = file
            true
        }.getOrElse {
            mediaRecorder.release()
            activeProjection.stop()
            false
        }
    }

    fun stop(): Uri? {
        val currentRecorder = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        runCatching { currentRecorder.stop() }
        currentRecorder.reset()
        currentRecorder.release()
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
        if (file == null || !file.exists() || file.length() == 0L) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RoutingPlatform")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return Uri.fromFile(file)
        resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        file.delete()
        return uri
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.reset(); it.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }
}
