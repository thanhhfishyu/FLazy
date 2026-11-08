package io.github.thanhhfishyu.flazy

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Offline Chinese (and Latin) text recognition with the model bundled in the APK. */
class OcrEngine {
    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /** Returns the lines on the image, in reading order (top to bottom, left to right). */
    suspend fun read(bitmap: Bitmap): List<OcrLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val blocks = result.textBlocks.sortedWith(
            compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
        )
        val lines = ArrayList<OcrLine>()
        blocks.forEachIndexed { index, block ->
            block.lines
                .sortedBy { it.boundingBox?.top ?: 0 }
                .forEach { lines.add(OcrLine(it.text, index)) }
        }
        return lines
    }

    fun close() = recognizer.close()
}
