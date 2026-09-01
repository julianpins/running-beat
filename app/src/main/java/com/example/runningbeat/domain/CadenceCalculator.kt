package com.example.runningbeat.domain

import java.util.ArrayDeque

class CadenceCalculator(
    private var startingBpm: Int = 155
) {
    private val stepTimestamps = ArrayDeque<Long>()
    private var smoothedBpm: Double = startingBpm.toDouble()
    private var isInitialized: Boolean = startingBpm > 0

    private val smoothingFactor = 0.2
    private val minSmoothed = 100.0
    private val maxSmoothed = 200.0

    //in Nanoseconds
    private val minWindow = 4_000_000_000L
    private val maxWindow = 10_000_000_000L

    private val falseStepInterval = 200_000_000L
    private val missBeatInterval = 630_000_000L
    private val breakInterval = 1_280_000_000L

    fun updateStartingBpm(bpm: Int) {
        if (bpm > 0) {
            startingBpm = bpm
            if (stepTimestamps.isEmpty()) {
                smoothedBpm = bpm.toDouble()
                isInitialized = true
            }
        } else {
            startingBpm = 0
            if (stepTimestamps.isEmpty()) {
                smoothedBpm = 0.0
                isInitialized = false
            }
        }
    }

    fun resetToStartingBpm(initialBpm: Int = startingBpm) {
        stepTimestamps.clear()
        startingBpm = initialBpm
        smoothedBpm = initialBpm.toDouble()
        isInitialized = initialBpm > 0
    }

    fun processStep(timestamp: Long): Double {
        if (stepTimestamps.isNotEmpty()) {
            val interval = timestamp - stepTimestamps.peekLast()!!
            if (interval < falseStepInterval) {
                return smoothedBpm
            }
            if (interval > breakInterval) {
                stepTimestamps.clear()
                stepTimestamps.addLast(timestamp)
                return smoothedBpm
            }
            if (interval > missBeatInterval) {
                stepTimestamps.addLast(stepTimestamps.peekLast()!! + interval / 2)
            }
        }
        stepTimestamps.addLast(timestamp)

        val cutoffTimestamp = timestamp - maxWindow
        while (stepTimestamps.isNotEmpty() && stepTimestamps.peekFirst()!! < cutoffTimestamp) {
            stepTimestamps.removeFirst()
        }

        if (stepTimestamps.size < 5) return if (isInitialized) smoothedBpm else 0.0
        val currentWindowDuration = stepTimestamps.peekLast()!! - stepTimestamps.peekFirst()!!

        // 4. Minimum Window Guard: Only calculate a new raw BPM if we have accumulated at least 5s of data
        if (currentWindowDuration >= minWindow) {
            val durationMinutes = currentWindowDuration / 60_000_000_000.0
            val rawBpm = (stepTimestamps.size - 1) / durationMinutes

            if (!isInitialized) {
                smoothedBpm = rawBpm
                isInitialized = true
            } else {
                smoothedBpm = (smoothingFactor * rawBpm) + ((1.0 - smoothingFactor) * smoothedBpm)
            }
        }
        
        if (isInitialized) {
            if (smoothedBpm < minSmoothed) smoothedBpm = minSmoothed
            if (smoothedBpm > maxSmoothed) smoothedBpm = maxSmoothed
        }
        
        return smoothedBpm
    }
}
