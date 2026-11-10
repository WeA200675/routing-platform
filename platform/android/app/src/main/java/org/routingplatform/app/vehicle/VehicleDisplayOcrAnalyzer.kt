package org.routingplatform.app.vehicle

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal class VehicleDisplayOcrAnalyzer(
    private val onText: (String) -> Unit,
    private val onError: (Throwable) -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var processing = false

    override fun analyze(image: ImageProxy) {
        if (processing || image.format != ImageFormat.YUV_420_888) {
            image.close()
            return
        }
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        processing = true
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isNotEmpty()) onText(text)
            }
            .addOnFailureListener(onError)
            .addOnCompleteListener {
                processing = false
                image.close()
            }
    }

    fun close() {
        recognizer.close()
    }
}
