package com.genesis.sihay.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class FertilityStatus {
    @SerialName("fertile")
    FERTILE,

    @SerialName("infertile")
    INFERTILE,

    @SerialName("dead")
    DEAD;

    val label: String
        get() = when (this) {
            FERTILE -> "Fertile"
            INFERTILE -> "Infertile"
            DEAD -> "Dead"
        }
}

@Serializable
data class ColorMetrics(
    val averageHue: Float,
    val averageSaturation: Float,
    val averageValue: Float,
    val warmSpotRatio: Float,
    val embryoShadowRatio: Float,
    val shellTextureVariance: Float,
    val centerHasEggColors: Boolean = true,
    val hueVariance: Float = 0f,
    val edgeDensity: Float = 0f

) {
    val pigmentationScore: Float
        get() = (warmSpotRatio * 0.6f) + (averageSaturation * 0.4f)

    val embryogenesisScore: Float
        get() = embryoShadowRatio.coerceIn(0f, 1f)

    val shellIntegrityScore: Float
        get() = (1f - shellTextureVariance).coerceIn(0f, 1f)
}

@Serializable
data class EggAnalysisRecord(
    val id: String,
    val imageUri: String,
    val status: FertilityStatus,
    val confidence: Float,
    val timestamp: Long,
    val insights: List<String>,
    val metrics: ColorMetrics,
    val source: CaptureSource
)


