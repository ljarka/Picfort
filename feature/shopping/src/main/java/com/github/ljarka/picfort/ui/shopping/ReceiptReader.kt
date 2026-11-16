package com.github.ljarka.picfort.ui.shopping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.github.ljarka.picfort.core.model.ReceiptAiStatus
import com.github.ljarka.picfort.core.model.ReceiptAiStatusProvider
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads shop, date and total from a receipt photo. Faked in unit tests. */
interface ReceiptReader {
    suspend fun isSupported(): Boolean
    suspend fun read(photoBytes: ByteArray): ReceiptReading?
}

@Singleton
class GeminiNanoReceiptReader @Inject constructor() :
    ReceiptReader,
    ReceiptAiStatusProvider {
    override suspend fun status(): ReceiptAiStatus = withContext(Dispatchers.IO) {
        runCatching {
            when (Generation.getClient().checkStatus()) {
                FeatureStatus.AVAILABLE -> ReceiptAiStatus.Available
                FeatureStatus.DOWNLOADABLE -> ReceiptAiStatus.Downloadable
                FeatureStatus.DOWNLOADING -> ReceiptAiStatus.Downloading
                else -> ReceiptAiStatus.Unavailable
            }
        }.getOrDefault(ReceiptAiStatus.Unavailable)
    }

    override suspend fun isSupported(): Boolean = status() == ReceiptAiStatus.Available

    override suspend fun read(photoBytes: ByteArray): ReceiptReading? = withContext(Dispatchers.IO) {
        runCatching {
            val client = Generation.getClient()
            if (client.checkStatus() != FeatureStatus.AVAILABLE) return@withContext null
            val bitmap = decodeDownscaled(photoBytes, maxEdge = 1600) ?: return@withContext null
            try {
                val request = generateContentRequest(
                    ImagePart(bitmap),
                    TextPart(PROMPT),
                ) {
                    temperature = 0.1f
                    maxOutputTokens = 256
                }
                val response = client.generateContent(request)
                val text = response.candidates.firstOrNull()?.text ?: return@withContext null
                ReceiptReadingParser.parse(text)
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    private fun decodeDownscaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private companion object {
        val PROMPT = """
            Read this receipt photo (usually a Polish fiscal receipt).
            Return ONLY a JSON object with keys:
            - "shop": everyday store name (e.g. "Biedronka", not legal entity), or null
            - "date": purchase date as YYYY-MM-DD, or null
            - "total": amount paid as decimal string (e.g. "45.20", on Polish receipts the SUMA PLN line), or null
            Do not guess anything that is not clearly legible.
        """.trimIndent()
    }
}
