package com.example.runningbeat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

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
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = averageBpm.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "AVERAGE BPM",
                            style = MaterialTheme.typography.labelMedium,
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
                    Column(modifier = Modifier.weight(1f)) {
                        BpmGraphEnhanced(
                            bpmHistory = bpmHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.5f)
                                .padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Cadence Distribution (%)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        
                        BpmDistributionChart(
                            bpmHistory = bpmHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 16.dp)
                        )
                    }
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

    // Min/Max for scaling
    val minBpm = (bpmHistory.minOf { it.second } - 2).coerceAtMost(140.0)
    val maxBpm = (bpmHistory.maxOf { it.second } + 2).coerceAtLeast(180.0)
    val bpmRange = (maxBpm - minBpm).toFloat()
    val totalTime = bpmHistory.last().first.toFloat()

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pan = event.calculatePan()
                            
                            if (event.changes.size >= 2) {
                                val p1 = event.changes[0]
                                val p2 = event.changes[1]
                                
                                val prevDistX = abs(p1.previousPosition.x - p2.previousPosition.x)
                                val currDistX = abs(p1.position.x - p2.position.x)
                                // Only zoom if distance is significant to avoid extreme sensitivity
                                if (prevDistX > 20f && currDistX > 20f) {
                                    val zX = currDistX / prevDistX
                                    if (abs(zX - 1f) > 0.01f) {
                                        scaleX = (scaleX * zX).coerceIn(1f, 50f)
                                    }
                                }
                                
                                val prevDistY = abs(p1.previousPosition.y - p2.previousPosition.y)
                                val currDistY = abs(p1.position.y - p2.position.y)
                                if (prevDistY > 20f && currDistY > 20f) {
                                    val zY = currDistY / prevDistY
                                    if (abs(zY - 1f) > 0.01f) {
                                        scaleY = (scaleY * zY).coerceIn(1f, 50f)
                                    }
                                }
                            }
                            
                            offsetX += pan.x
                            offsetY += pan.y
                            
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            }
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
            val stepBpm = if (scaleY > 5f) 2 else if (scaleY > 2.5f) 5 else 10
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

            // Dynamic Time Step Calculation to avoid overlapping (at most 12 points)
            val visibleTimeRange = totalTime / scaleX
            val timeSteps = listOf(5000L, 10000L, 15000L, 30000L, 60000L, 120000L, 300000L, 600000L)
            val idealStep = visibleTimeRange / 8 // Target 8 labels to be safe under 12
            val timeStep = timeSteps.firstOrNull { it >= idealStep } ?: timeSteps.last()

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

@Composable
fun BpmDistributionChart(
    bpmHistory: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // Calculate distribution
    val distribution = remember(bpmHistory) {
        val buckets = mutableMapOf<Int, Long>() // Center BPM to duration (ms)
        var totalDuration = 0L

        for (i in 0 until bpmHistory.size - 1) {
            val (time1, bpm1) = bpmHistory[i]
            val (time2, _) = bpmHistory[i + 1]
            val duration = time2 - time1
            
            val bucketCenter = (Math.round(bpm1 / 5.0) * 5).toInt()
            buckets[bucketCenter] = buckets.getOrDefault(bucketCenter, 0L) + duration
            totalDuration += duration
        }

        if (totalDuration == 0L) emptyList<Pair<Int, Float>>()
        else {
            val minBucket = buckets.keys.minOrNull() ?: 140
            val maxBucket = buckets.keys.maxOfOrNull { it } ?: 180
            
            val result = mutableListOf<Pair<Int, Float>>()
            for (b in minBucket..maxBucket step 5) {
                val duration = buckets.getOrDefault(b, 0L)
                result.add(b to (duration.toFloat() / totalDuration) * 100f)
            }
            result
        }
    }

    if (distribution.isEmpty()) return

    val maxPercentage = distribution.maxOf { it.second }.coerceAtLeast(10f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 50.dp.toPx()
        val paddingBottom = 40.dp.toPx()
        val paddingTop = 20.dp.toPx()
        val paddingRight = 20.dp.toPx()
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // Draw Y-axis labels (%)
        val yLabelCount = 4
        val paint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 28f
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        
        for (i in 0..yLabelCount) {
            val pct = (maxPercentage / yLabelCount) * i
            val y = (height - paddingBottom) - (i.toFloat() / yLabelCount) * chartHeight
            drawContext.canvas.nativeCanvas.drawText(
                "${pct.toInt()}%",
                paddingLeft - 10f,
                y + 10f,
                paint
            )
            drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(paddingLeft, y), Offset(width - paddingRight, y), 1f)
        }

        // Draw Columns
        val columnWidth = chartWidth / distribution.size
        val barPadding = columnWidth * 0.2f
        
        distribution.forEachIndexed { index, (bpm, pct) ->
            val left = paddingLeft + index * columnWidth + barPadding
            val right = paddingLeft + (index + 1) * columnWidth - barPadding
            val barHeight = (pct / maxPercentage) * chartHeight
            val top = (height - paddingBottom) - barHeight
            
            drawRect(
                color = primaryColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, barHeight)
            )

            // Draw X-axis label (BPM)
            val showLabel = if (distribution.size > 12) {
                bpm % 10 == 0
            } else {
                true
            }

            if (showLabel) {
                drawContext.canvas.nativeCanvas.drawText(
                    bpm.toString(),
                    (left + right) / 2f,
                    height - 10f,
                    android.graphics.Paint().apply {
                        color = labelColor
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        // Draw Main Axes
        drawLine(Color.Gray, Offset(paddingLeft, paddingTop), Offset(paddingLeft, height - paddingBottom), 2f)
        drawLine(Color.Gray, Offset(paddingLeft, height - paddingBottom), Offset(width - paddingRight, height - paddingBottom), 2f)
    }
}
