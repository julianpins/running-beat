package com.example.runningbeat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun StatsScreen(
    bpmHistory: List<Pair<Long, Double>>,
    onDismiss: () -> Unit,
) {
    val averageBpm = if (bpmHistory.isNotEmpty()) bpmHistory.map { it.second }.average().toInt() else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Run Statistics",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = averageBpm.toString(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "AVERAGE BPM",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Cadence Over Time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (bpmHistory.size > 1) {
                    BpmGraphEnhanced(
                        bpmHistory = bpmHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Not enough data to display graph",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
fun BpmGraphEnhanced(
    bpmHistory: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    
    // State for zoom and pan
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scaleX = (scaleX * zoomChange).coerceIn(1f, 20f)
        scaleY = (scaleY * zoomChange).coerceIn(1f, 20f)
        offsetX += offsetChange.x
        offsetY += offsetChange.y
    }

    // Min/Max for scaling
    val minBpm = (bpmHistory.minOf { it.second } - 2).coerceAtMost(140.0)
    val maxBpm = (bpmHistory.maxOf { it.second } + 2).coerceAtLeast(180.0)
    val bpmRange = (maxBpm - minBpm).toFloat()
    val totalTime = bpmHistory.last().first.toFloat()

    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(state = state)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingLeft = 50.dp.toPx()
            val paddingBottom = 40.dp.toPx()
            val paddingTop = 20.dp.toPx()
            val paddingRight = 20.dp.toPx()
            
            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            // Constrain offsets
            val maxOffsetX = 0f
            val minOffsetX = -(chartWidth * scaleX - chartWidth)
            offsetX = offsetX.coerceIn(minOffsetX, maxOffsetX)

            val minOffsetY = 0f
            val maxOffsetY = (chartHeight * scaleY - chartHeight)
            offsetY = offsetY.coerceIn(minOffsetY, maxOffsetY)

            // Draw Y-axis labels (BPM)
            val stepBpm = if (scaleY > 2.5f) 5 else 10
            val paint = android.graphics.Paint().apply {
                color = labelColor
                textSize = 30f
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            
            var labelBpm = (minBpm / stepBpm).toInt() * stepBpm
            while (labelBpm <= maxBpm + stepBpm) {
                if (labelBpm >= minBpm) {
                    val normalizedY = (labelBpm - minBpm).toFloat() / bpmRange
                    val y = (height - paddingBottom) + offsetY - (normalizedY * chartHeight * scaleY)
                    
                    if (y in (paddingTop - 1f)..(height - paddingBottom + 1f)) {
                        drawContext.canvas.nativeCanvas.drawText(
                            labelBpm.toString(),
                            paddingLeft - 10f,
                            y + 10f,
                            paint
                        )
                        drawLine(
                            Color.LightGray.copy(alpha = 0.3f),
                            Offset(paddingLeft, y),
                            Offset(width - paddingRight, y),
                            1f
                        )
                    }
                }
                labelBpm += stepBpm
            }

            // Draw Data with Clipping
            clipRect(
                left = paddingLeft,
                top = paddingTop,
                right = width - paddingRight,
                bottom = height - paddingBottom
            ) {
                // Draw Data Path
                val path = Path().apply {
                    bpmHistory.forEachIndexed { index, (time, bpm) ->
                        val normalizedX = time.toFloat() / totalTime
                        val x = paddingLeft + offsetX + (normalizedX * chartWidth * scaleX)
                        val normalizedY = (bpm.toFloat() - minBpm.toFloat()) / bpmRange
                        val y = (height - paddingBottom) + offsetY - (normalizedY * chartHeight * scaleY)
                        
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))
            }

            // Draw Time Markers (outside clipRect so labels are visible)
            val timeStep = 30000L // 30 seconds
            var currentTime = 0L
            while (currentTime <= totalTime) {
                val normalizedX = currentTime.toFloat() / totalTime
                val x = paddingLeft + offsetX + (normalizedX * chartWidth * scaleX)
                
                if (x >= paddingLeft && x <= width - paddingRight) {
                    val minutes = (currentTime / 60000).toInt()
                    val seconds = ((currentTime % 60000) / 1000).toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        "%d:%02d".format(minutes, seconds),
                        x,
                        height - 10f,
                        android.graphics.Paint().apply {
                            color = labelColor
                            textSize = 25f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
                currentTime += timeStep
            }
            
            // Draw Main Axes on top
            drawLine(Color.Gray, Offset(paddingLeft, paddingTop), Offset(paddingLeft, height - paddingBottom), 2f)
            drawLine(Color.Gray, Offset(paddingLeft, height - paddingBottom), Offset(width - paddingRight, height - paddingBottom), 2f)
        }
    }
}
