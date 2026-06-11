package com.genesis.sihay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // <--- UPDATED IMPORT
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genesis.sihay.ui.components.SihayGradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instructions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // UPDATED ICON USAGE
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SihayGradientBackground(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HighlightCard(
                    title = "How to Get Accurate Results",
                    description = "Follow these guidelines to ensure the best possible analysis of your chicken eggs."
                )
                SectionTitle("Photography Tips")
                photographyTips.forEachIndexed { index, tip ->
                    NumberedTipCard(index + 1, tip.first, tip.second)
                }
                SectionTitle("Candling Technique")
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "For best results, candle the egg before photographing. Hold a bright light source behind the egg in a dark room to make internal features more visible.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SectionTitle("Signs of Fertilization")
                bulletTips.forEach { tip ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(tip.first, fontWeight = FontWeight.Bold)
                            Text(tip.second)
                        }
                    }
                }
                SectionTitle("Important Notes")
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        importantNotes.forEach {
                            Text("- $it")
                        }
                    }
                }
            }
        }
    }
}

private val photographyTips = listOf(
    "Good Lighting" to "Use natural daylight or bright, even lighting. Avoid shadows and harsh glare.",
    "Center the Egg" to "Make sure the entire egg is visible and fills most of the frame.",
    "Clean Background" to "Use a plain, light background to highlight egg features.",
    "Focus and Clarity" to "Hold the camera steady or use a tripod to avoid blur."
)

private val bulletTips = listOf(
    "Blood Ring or Vessels" to "Visible vessels or a red ring indicate embryo development.",
    "Dark Spot or Shadow" to "Dark areas often suggest the presence of an embryo.",
    "Opacity Changes" to "Less translucent regions may indicate developing tissue."
)

private val importantNotes = listOf(
    "Best to analyze eggs 5-7 days after incubation begins.",
    "Results are estimates based on visual analysis.",
    "Consult poultry experts for critical decisions.",
    "Clean the egg surface before photographing for clarity."
)

@Composable
private fun HighlightCard(title: String, description: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(description)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    )
}

@Composable
private fun NumberedTipCard(number: Int, title: String, description: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$number. $title", fontWeight = FontWeight.Bold)
            Text(description)
        }
    }
}