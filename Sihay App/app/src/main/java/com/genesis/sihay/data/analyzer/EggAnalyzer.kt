package com.genesis.sihay.data.analyzer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.genesis.sihay.AnalyzedFailedException
import com.genesis.sihay.EggClassifier
import com.genesis.sihay.EggClassificationResult
import com.genesis.sihay.TryAgainException
import com.genesis.sihay.data.model.ColorMetrics
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.genesis.sihay.data.model.FertilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs
import com.genesis.sihay.data.model.CaptureSource

class EggAnalyzer(
    private val context: Context,
    private val classifier: EggClassifier
) {
    // --- MESSAGES ---
    private val errorAnalyzedFailed = "Analysis Failed: Object detected is not a candled egg."
    private val errorTryAgain = "Egg not clear. Please center the egg and try again."

    // --- FILTERS ---
    private val minCenterEggRatio = 0.15f
    private val minEggCoverage = 0.05f
    private val maxShellTextureVariance = 0.08f
    private val maxEdgeDensity = 0.05f
    private val maxHueVariance = 20.0f

    // Zoom/Crop Level (0.85f is safer than 0.90f for AI context)
    private val CROP_SCALE = 0.85f

    // --- ENTRY POINT 1: PHONE CAMERA (URI) ---
    suspend fun analyze(uri: Uri, source: CaptureSource): EggAnalysisRecord =
        withContext(Dispatchers.Default) {
            val originalBitmap = decodeBitmap(context.contentResolver, uri)
            // Delegate to common logic
            return@withContext analyzeBitmap(originalBitmap, source, uri.toString())
        }

    // --- ENTRY POINT 2: ESP32 SERVER (BITMAP) ---
    suspend fun analyze(bitmap: Bitmap, source: CaptureSource): EggAnalysisRecord =
        withContext(Dispatchers.Default) {
            // Delegate to common logic with a dummy/generated URI string
            return@withContext analyzeBitmap(bitmap, source, "esp32_stream_${System.currentTimeMillis()}")
        }

    // --- SHARED ANALYSIS LOGIC ---
    private suspend fun analyzeBitmap(originalBitmap: Bitmap, source: CaptureSource, uriString: String): EggAnalysisRecord {
        // 1. Crop
        val croppedBitmap = centerCropBitmap(originalBitmap, CROP_SCALE)

        // 2. Downscale
        val scaledForColors = croppedBitmap.scaleDown(maxDimension = 256)

        // 3. Run Segmentation
        val segmentation = ColorSegmentationEngine.analyze(scaledForColors)
        val metrics = segmentation.metrics

        // 4. Calculate Crop Rect
        val cropRect = segmentation.boundingBox
            ?.toRect(croppedBitmap.width, croppedBitmap.height, paddingFraction = 0.15f)
            ?.coerceWithin(croppedBitmap.width, croppedBitmap.height)
            ?: Rect(0, 0, croppedBitmap.width, croppedBitmap.height)

        val finalCroppedBitmap = cropBitmapIfPossible(croppedBitmap, cropRect)

        try {
            // STEP A: AI NON-EGG CHECK
            if (classifier.isNonEggObject(finalCroppedBitmap)) {
                throw AnalyzedFailedException(errorAnalyzedFailed)
            }

            // STEP B: MANUAL VALIDATION
            validateSegmentation(segmentation, cropRect)

            // STEP C: AI EGG CHECK
            if (classifier.isEggObject(finalCroppedBitmap)) {

                val aiResult = classifier.classifyFertility(finalCroppedBitmap)

                // Clean up bitmaps (Recycle to save memory)
                cleanupBitmaps(originalBitmap, croppedBitmap, finalCroppedBitmap)

                val (status, aiConfidence) = parseAiResult(aiResult)
                val smartInsights = generateInsights(aiResult.label)

                return EggAnalysisRecord(
                    id = UUID.randomUUID().toString(),
                    imageUri = uriString,
                    status = status,
                    confidence = aiConfidence,
                    timestamp = System.currentTimeMillis(),
                    insights = smartInsights,
                    metrics = metrics,
                    source = source
                )
            }

            throw TryAgainException(errorTryAgain)

        } catch (e: Exception) {
            cleanupBitmaps(originalBitmap, croppedBitmap, finalCroppedBitmap)
            throw e
        }
    }

    private fun validateSegmentation(
        segmentation: SegmentationResult,
        cropRect: Rect
    ) {
        val metrics = segmentation.metrics

        // 1. Texture/Edge Check
        if (metrics.edgeDensity > maxEdgeDensity) {
            throw AnalyzedFailedException(errorAnalyzedFailed)
        }
        if (metrics.shellTextureVariance > maxShellTextureVariance) {
            throw AnalyzedFailedException(errorAnalyzedFailed)
        }

        // 2. Color Variance Check
        if (metrics.hueVariance > maxHueVariance) {
            throw AnalyzedFailedException(errorAnalyzedFailed)
        }

        // 3. Luminosity Check
        // Lowered to 0.12f to allow Darker Eggs
        if (metrics.averageValue < 0.12f) {
            throw TryAgainException("Image is too dark. Please ensure egg is glowing.")
        }

        // 4. Aspect Ratio Check
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            val w = cropRect.width().toFloat()
            val h = cropRect.height().toFloat()
            val aspectRatio = max(w, h) / min(w, h)
            if (aspectRatio > 2.5f) throw AnalyzedFailedException(errorAnalyzedFailed)
        }

        // 5. Existence Check
        val centerRatio = segmentation.centerEggRatio
        val coverage = segmentation.eggCoverage
        if (centerRatio < minCenterEggRatio && coverage < minEggCoverage) {
            throw TryAgainException(errorTryAgain)
        }
    }

    private fun cleanupBitmaps(vararg bitmaps: Bitmap) {
        bitmaps.forEach {
            if (!it.isRecycled) it.recycle()
        }
    }

    private fun generateInsights(aiLabel: String): List<String> {
        val insights = mutableListOf<String>()
        insights.add("AI Classification: $aiLabel")

        val isFertile = aiLabel.equals("Fertile", ignoreCase = true)

        if (isFertile) {
            insights.add("Significant embryo mass or vascular network visible.")
            insights.add("Visible embryonic movement during candling.")
        } else {
            insights.add("No embryo mass or vascular structures present.")
            insights.add("Absence of any movement or development.")
        }
        return insights
    }

    private fun centerCropBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        val smallerDimension = min(bitmap.width, bitmap.height)
        val cropSize = (smallerDimension * scale).toInt()
        val startX = (bitmap.width - cropSize) / 2
        val startY = (bitmap.height - cropSize) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize)
    }

    private fun parseAiResult(result: EggClassificationResult): Pair<FertilityStatus, Float> {
        val status = if (result.label.equals("Fertile", ignoreCase = true)) {
            FertilityStatus.FERTILE
        } else {
            FertilityStatus.INFERTILE
        }
        return Pair(status, result.confidence)
    }

    private fun cropBitmapIfPossible(source: Bitmap, rect: Rect): Bitmap {
        val safeRect = rect.coerceWithin(source.width, source.height)
        if (safeRect.width() < 48 || safeRect.height() < 48 ||
            (safeRect.width() == source.width && safeRect.height() == source.height)
        ) {
            return source
        }
        return Bitmap.createBitmap(
            source, safeRect.left, safeRect.top, safeRect.width(), safeRect.height()
        )
    }

    private fun Rect.coerceWithin(imageWidth: Int, imageHeight: Int): Rect {
        if (imageWidth <= 0 || imageHeight <= 0) return Rect(0, 0, 0, 0)
        val clampedLeft = left.coerceIn(0, imageWidth - 1)
        val clampedTop = top.coerceIn(0, imageHeight - 1)
        val clampedRight = right.coerceIn(clampedLeft + 1, imageWidth)
        val clampedBottom = bottom.coerceIn(clampedTop + 1, imageHeight)
        return Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }

    private fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
        if (width <= maxDimension && height <= maxDimension) return this
        val aspect = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (aspect >= 1f) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / aspect).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * aspect).toInt()
        }
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}

// --- SEGMENTATION ENGINE ---

private data class SegmentationResult(
    val metrics: ColorMetrics,
    val centerEggRatio: Float,
    val eggCoverage: Float,
    val boundingBox: NormalizedBounds?
)

private data class NormalizedBounds(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    fun toRect(imageWidth: Int, imageHeight: Int, paddingFraction: Float): Rect {
        val padX = (right - left) * paddingFraction
        val padY = (bottom - top) * paddingFraction
        val l = ((left - padX) * imageWidth).toInt()
        val t = ((top - padY) * imageHeight).toInt()
        val r = ((right + padX) * imageWidth).toInt()
        val b = ((bottom + padY) * imageHeight).toInt()
        return Rect(l, t, r, b)
    }
}

private object ColorSegmentationEngine {
    fun analyze(bitmap: Bitmap): SegmentationResult {
        var hueSum = 0f
        var satSum = 0f
        var valueSum = 0f
        var warmCount = 0
        var total = 0
        val hueValues = ArrayList<Float>()
        var textureAccumulator = 0f
        var edgePixelCount = 0
        var centerEggColorCount = 0
        var centerTotal = 0
        var eggPixels = 0
        var minX = bitmap.width; var maxX = 0
        var minY = bitmap.height; var maxY = 0
        val width = bitmap.width
        val height = bitmap.height
        val hsv = FloatArray(3)
        val sampleStep = max(1, min(width, height) / 100)
        val centerLeft = (width * 0.30f).toInt()
        val centerRight = (width * 0.70f).toInt()
        val centerTop = (height * 0.30f).toInt()
        val centerBottom = (height * 0.70f).toInt()

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val color = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(color, hsv)

                val isEgg = isEggColor(hsv)

                if (isEgg) {
                    eggPixels++
                    hueSum += hsv[0]
                    satSum += hsv[1]
                    valueSum += hsv[2]
                    hueValues.add(hsv[0])
                    if (isWarmHue(hsv)) warmCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                if (x in centerLeft..centerRight && y in centerTop..centerBottom) {
                    centerTotal++
                    if (isEgg) centerEggColorCount++
                }
                if (x + sampleStep < width) {
                    val neighbor = bitmap.getPixel(x + sampleStep, y)
                    val diff = colorDifference(color, neighbor)
                    if (hsv[2] > 0.15f) {
                        textureAccumulator += diff
                        if (diff > 0.08f) edgePixelCount++
                    }
                }
                total++
            }
        }

        if (total == 0) total = 1
        if (centerTotal == 0) centerTotal = 1
        val safeEggPixels = if (eggPixels == 0) 1 else eggPixels
        val avgHue = hueSum / safeEggPixels
        val avgSat = satSum / safeEggPixels
        val avgVal = valueSum / safeEggPixels
        val hueVariance = calculateStdDev(hueValues, avgHue)
        val textureVariance = (textureAccumulator / total).coerceIn(0f, 1f)
        val edgeDensity = edgePixelCount / total.toFloat()
        val centerEggRatio = centerEggColorCount / centerTotal.toFloat()
        val eggCoverage = eggPixels / total.toFloat()
        val boundingBox = if (eggPixels > (total * 0.02f)) {
            NormalizedBounds(
                left = minX.toFloat() / width,
                top = minY.toFloat() / height,
                right = maxX.toFloat() / width,
                bottom = maxY.toFloat() / height
            )
        } else null
        val metrics = ColorMetrics(
            averageHue = avgHue,
            averageSaturation = avgSat,
            averageValue = avgVal,
            warmSpotRatio = warmCount / safeEggPixels.toFloat(),
            embryoShadowRatio = 0f,
            shellTextureVariance = textureVariance,
            hueVariance = hueVariance,
            edgeDensity = edgeDensity
        )
        return SegmentationResult(metrics, centerEggRatio, eggCoverage, boundingBox)
    }

    private fun calculateStdDev(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        for (v in values) {
            val diff = abs(v - mean)
            val correctedDiff = if (diff > 180) 360 - diff else diff
            sum += correctedDiff * correctedDiff
        }
        return sqrt(sum / values.size)
    }

    private fun colorDifference(a: Int, b: Int): Float {
        val r1 = (a shr 16) and 0xff
        val g1 = (a shr 8) and 0xff
        val b1 = a and 0xff
        val r2 = (b shr 16) and 0xff
        val g2 = (b shr 8) and 0xff
        val b2 = b and 0xff
        val diffR = (r1 - r2).toFloat()
        val diffG = (g1 - g2).toFloat()
        val diffB = (b1 - b2).toFloat()
        return sqrt((diffR * diffR) + (diffG * diffG) + (diffB * diffB)) / 441.67f
    }

    private fun isWarmHue(hsv: FloatArray): Boolean {
        val hue = hsv[0]; val sat = hsv[1]; val value = hsv[2]
        return hue in 10f..50f && sat >= 0.30f && value >= 0.30f
    }

    private fun isEggColor(hsv: FloatArray): Boolean {
        val hue = hsv[0]; val sat = hsv[1]; val value = hsv[2]

        // 1. TOO BRIGHT FIX (White/Hot center)
        if (value > 0.90f) return true

        // 2. TOO DARK FIX (Dim shell)
        if (value < 0.15f) return false

        // Standard Saturation Check
        if (sat < 0.15f) return false

        val isWarm = hue in 0f..65f
        val isRed = hue in 330f..360f
        return isWarm || isRed
    }
}