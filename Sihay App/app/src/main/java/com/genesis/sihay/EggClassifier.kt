package com.genesis.sihay

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.genesis.sihay.data.model.CaptureSource
import com.genesis.sihay.data.model.ColorMetrics
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.genesis.sihay.data.model.FertilityStatus
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnalyzedFailedException(message: String) : Exception(message)
class TryAgainException(message: String) : Exception(message)

data class EggClassificationResult(
    val label: String,
    val confidence: Float,
    val isEgg: Boolean,
    val summary: String
)

class EggClassifier(private val context: Context) {

    private val modelEggObject = "egg_objects.tflite"
    private val modelClassifier = "classifier_model.tflite"
    private val modelNonEggObject = "non_egg_objects.tflite"

    private var interpreterEggObject: Interpreter? = null
    private var interpreterClassifier: Interpreter? = null
    private var interpreterNonEgg: Interpreter? = null

    private val TAG = "EggClassifier"
    private val imageSizeX = 224
    private val imageSizeY = 224
    private val classifierLabels = listOf("Dead", "Fertile", "Infertile")

    private val EGG_DETECTION_THRESHOLD = 0.70f
    private val NON_EGG_DETECTION_THRESHOLD = 0.65f
    private val CLASSIFICATION_THRESHOLD = 0.60f

    init {
        loadModels()
    }

    private fun loadModels() {
        val options = Interpreter.Options().apply { setNumThreads(4) }

        // 1. Egg Detector (Safe Load)
        try {
            interpreterEggObject = Interpreter(FileUtil.loadMappedFile(context, modelEggObject), options)
        } catch (_: Exception) {
            Log.w(TAG, "⚠️ Failed to load $modelEggObject")
        }

        // 2. Classifier (Critical)
        try {
            interpreterClassifier = Interpreter(FileUtil.loadMappedFile(context, modelClassifier), options)
        } catch (_: Exception) {
            Log.e(TAG, "CRITICAL: Failed to load $modelClassifier")
        }

        // 3. Non-Egg Detector (Safe Load - Fixes Crash)
        try {
            interpreterNonEgg = Interpreter(FileUtil.loadMappedFile(context, modelNonEggObject), options)
        } catch (_: Exception) {
            Log.w(TAG, "⚠️ Failed to load $modelNonEggObject")
        }
    }

    fun isNonEggObject(bitmap: Bitmap): Boolean {
        val tflite = interpreterNonEgg ?: return false
        val maxScore = runInferenceGeneric(tflite, bitmap, outputSize = 10)
        return maxScore >= NON_EGG_DETECTION_THRESHOLD
    }

    fun isEggObject(bitmap: Bitmap): Boolean {
        val tflite = interpreterEggObject ?: return true // Default to true if missing to allow flow
        val maxScore = runInferenceGeneric(tflite, bitmap, outputSize = 2)
        return maxScore >= EGG_DETECTION_THRESHOLD
    }

    fun classifyFertility(bitmap: Bitmap): EggClassificationResult {
        val tflite = interpreterClassifier ?: return EggClassificationResult("Error", 0f, false, "Model Error")
        try {
            val tensorImage = processImage(bitmap)
            val outputBuffer = ByteBuffer.allocateDirect(4 * classifierLabels.size)
            outputBuffer.order(ByteOrder.nativeOrder())
            tflite.run(tensorImage.buffer, outputBuffer)
            outputBuffer.rewind()

            val probabilities = FloatArray(classifierLabels.size)
            outputBuffer.asFloatBuffer().get(probabilities)
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val maxScore = if (maxIndex != -1) probabilities[maxIndex] else 0f
            val label = classifierLabels.getOrElse(maxIndex) { "Unknown" }

            val isValid = maxScore >= CLASSIFICATION_THRESHOLD
            val summary = if (isValid) "$label Egg" else "Unsure"

            return EggClassificationResult(label, maxScore, isValid, summary)
        } catch (_: Exception) {
            return EggClassificationResult("Unknown", 0f, false, "Error")
        }
    }
    @Suppress("unused")
    fun analyze(bitmap: Bitmap): EggAnalysisRecord {
        if (isNonEggObject(bitmap)) throw AnalyzedFailedException("Object detected is likely not an egg.")
        if (!isEggObject(bitmap)) throw TryAgainException("No egg detected. Please center the egg.")

        val result = classifyFertility(bitmap)
        val status = when (result.label) {
            "Fertile" -> FertilityStatus.FERTILE
            "Infertile" -> FertilityStatus.INFERTILE
            "Dead" -> FertilityStatus.DEAD
            else -> FertilityStatus.INFERTILE
        }

        // Fix: Create valid ColorMetrics matching your Data Class
        val metrics = ColorMetrics(
            averageHue = 0f, averageSaturation = 0f, averageValue = 0f,
            warmSpotRatio = 0f, embryoShadowRatio = 0f, shellTextureVariance = 0f,
            centerHasEggColors = true, hueVariance = 0f, edgeDensity = 0f
        )

        return EggAnalysisRecord(
            id = System.currentTimeMillis().toString(),
            timestamp = System.currentTimeMillis(),
            imageUri = "",
            status = status,
            confidence = result.confidence,
            insights = listOf(result.summary),
            metrics = metrics,
            source = CaptureSource.CAMERA
        )
    }

    private fun runInferenceGeneric(interpreter: Interpreter, bitmap: Bitmap, outputSize: Int): Float {
        try {
            val tensorImage = processImage(bitmap)
            val outputBuffer = ByteBuffer.allocateDirect(4 * outputSize)
            outputBuffer.order(ByteOrder.nativeOrder())
            interpreter.run(tensorImage.buffer, outputBuffer)
            outputBuffer.rewind()
            val probabilities = FloatArray(outputSize)
            outputBuffer.asFloatBuffer().get(probabilities)
            return probabilities.maxOrNull() ?: 0f
        } catch (_: Exception) {
            return 0f
        }
    }

    private fun processImage(bitmap: Bitmap): TensorImage {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        return imageProcessor.process(tensorImage)
    }

    fun close() {
        interpreterEggObject?.close()
        interpreterClassifier?.close()
        interpreterNonEgg?.close()
    }
}