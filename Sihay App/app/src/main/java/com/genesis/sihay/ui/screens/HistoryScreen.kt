package com.genesis.sihay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // <--- UPDATED IMPORT
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.genesis.sihay.data.model.FertilityStatus
import com.genesis.sihay.ui.components.SihayGradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<EggAnalysisRecord>,
    title: String = "History & Logs",
    onDelete: (Set<String>) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onStartAnalyzing: () -> Unit
) {
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    val stats = remember(history) { buildStats(history) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // UPDATED ICON USAGE
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { onClearAll() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        onDelete(selectedItems)
                        selectedItems = emptySet()
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    ) { padding ->
        SihayGradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (history.isEmpty()) {
                HistoryEmptyState(onStartAnalyzing = onStartAnalyzing)
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    StatsCard(stats)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = onClearAll,
                            enabled = history.isNotEmpty()
                        ) {
                            Text("Clear All")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.id }) { record ->
                            HistoryRow(
                                record = record,
                                isSelected = selectedItems.contains(record.id),
                                onToggle = {
                                    selectedItems = selectedItems.toMutableSet().also { set ->
                                        if (set.contains(record.id)) set.remove(record.id) else set.add(record.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(onStartAnalyzing: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(24.dp)
                )
            }
            Text(
                text = "No History Yet",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Start analyzing eggs to see your history here",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Button(onClick = onStartAnalyzing) {
                Text("Start Analyzing")
            }
        }
    }
}

@Composable
private fun StatsCard(stats: HistoryStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Total Analyses: ${stats.total}",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Fertile: ${stats.fertile}",
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Infertile: ${stats.infertile}",
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            text = "Last scan: ${stats.lastScan ?: "—"}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HistoryRow(
    record: EggAnalysisRecord,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
            .clickable { onToggle() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = record.status.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = formatDate(record.timestamp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "Confidence ${(record.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = record.insights.firstOrNull() ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

private data class HistoryStats(
    val total: Int,
    val fertile: Int,
    val infertile: Int,
    val lastScan: String?
)

private fun buildStats(history: List<EggAnalysisRecord>): HistoryStats {
    val total = history.size
    val fertile = history.count { it.status == FertilityStatus.FERTILE }
    val infertile = history.count { it.status == FertilityStatus.INFERTILE }
    val last = history.maxByOrNull { it.timestamp }?.timestamp?.let(::formatDate)
    return HistoryStats(total, fertile, infertile, last)
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}