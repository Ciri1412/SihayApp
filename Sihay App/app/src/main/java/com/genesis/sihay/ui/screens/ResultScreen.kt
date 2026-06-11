package com.genesis.sihay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.genesis.sihay.ui.components.SihayGradientBackground
import com.genesis.sihay.data.model.CaptureSource


@Composable
fun ResultScreen(
    result: EggAnalysisRecord?,
    isAnalyzing: Boolean,
    onGoHome: () -> Unit,
    onBackToSource: (CaptureSource) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SihayGradientBackground(modifier = Modifier.fillMaxSize()) {}

        if (result == null || isAnalyzing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing egg...",
                    color = Color.White
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Result",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        AsyncImage(
                            model = result.imageUri,
                            contentDescription = "Analyzed egg",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = result.status.label,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                            )
                            Text(
                                text = "Confidence: ${(result.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Insights",
                                style = MaterialTheme.typography.titleMedium
                            )
                            result.insights.forEach { insight ->
                                Text("• $insight")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            MetricsRow(result)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGoHome,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Home")
                    }
                    Button(
                        onClick = { result?.source?.let(onBackToSource) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(result: EggAnalysisRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Color segmentation:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Warmth ${(result.metrics.warmSpotRatio * 100).toInt()}% • Shadows ${(result.metrics.embryoShadowRatio * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Shell integrity ${(result.metrics.shellIntegrityScore * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

