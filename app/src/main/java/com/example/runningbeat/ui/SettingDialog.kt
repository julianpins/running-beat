package com.example.runningbeat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    minBpm: Int,
    maxBpm: Int,
    startingBpm: Int,
    allowSkipping: Boolean,
    bpmDiffSwitch: Int,
    switchDelaySeconds: Int,
    useFallbackTracks: Boolean,
    onBpmWindowChange: (Int, Int) -> Unit,
    onStartingBpmChange: (Int) -> Unit,
    onAllowSkippingChange: (Boolean) -> Unit,
    onBpmDiffSwitchChange: (Int) -> Unit,
    onSwitchDelaySecondsChange: (Int) -> Unit,
    onUseFallbackTracksChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Running Settings",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. BPM Window (100 - 200 BPM)
                Column {
                    Text(
                        text = "BPM Window: $minBpm - $maxBpm BPM",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "100 = slow walking, 175 = very fast running",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RangeSlider(
                        value = minBpm.toFloat()..maxBpm.toFloat(),
                        onValueChange = { range ->
                            val newMin = range.start.roundToInt()
                            val newMax = range.endInclusive.roundToInt()
                            onBpmWindowChange(newMin, newMax)
                        },
                        valueRange = 100f..200f,
                        steps = 99,
                        modifier = Modifier.padding(vertical = 0.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // 2. Starting BPM (Tempo for first song)
                Column {
                    Text(
                        text = "Starting BPM: $startingBpm BPM",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Initial song tempo at session start",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val clampedStartingBpm = startingBpm.coerceIn(minBpm, maxBpm)
                    Slider(
                        value = clampedStartingBpm.toFloat(),
                        onValueChange = { onStartingBpmChange(it.roundToInt()) },
                        valueRange = minBpm.toFloat()..maxBpm.toFloat(),
                        steps = (maxBpm - minBpm - 1).coerceAtLeast(0),
                        modifier = Modifier.padding(vertical = 0.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // 3. Fallback Tracks Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Default Tracks",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Play backup songs if BPM playlist is missing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useFallbackTracks,
                        onCheckedChange = onUseFallbackTracksChange,
                        modifier = Modifier.scale(0.85f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // 4. Allow Skipping Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Skipping on BPM Change",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Automatically switch tracks when cadence shifts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowSkipping,
                        onCheckedChange = onAllowSkippingChange,
                        modifier = Modifier.scale(0.85f)
                    )
                }

                // Conditional Sub-fields when Allow Skipping is enabled
                if (allowSkipping) {
                    Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                        Text(
                            text = "BPM Difference Before Switch: $bpmDiffSwitch BPM",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = bpmDiffSwitch.toFloat(),
                            onValueChange = { onBpmDiffSwitchChange(it.roundToInt()) },
                            valueRange = 2f..15f,
                            steps = 12,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )
                    }

                    // 4b. Time Before Switching (3 - 20 Sec)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "Time Before Switch: $switchDelaySeconds sec",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = switchDelaySeconds.toFloat(),
                            onValueChange = { onSwitchDelaySecondsChange(it.roundToInt()) },
                            valueRange = 3f..20f,
                            steps = 16,
                            modifier = Modifier.padding(vertical = 0.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}