package com.example.runningbeat.domain

import java.util.ArrayDeque

class CadenceCalculator(
    private var startingBpm: Int = 155
) {
    private val stepTimestamps = ArrayDeque<Long>()
    private var smoothedBpm: Double = startingBpm.toDouble()

    private val smoothingFactor = 0.2

    //in Nanoseconds
    private val minWindow = 5_000_000_000L
    private val maxWindow = 10_000_000_000L

    private val falseStepInterval = 200_000_000L
    private val missBeatInterval = 630_000_000L
    private val breakInterval = 1_280_000_000L

    fun updateStartingBpm(bpm: Int) {
        if (bpm > 0) {
            startingBpm = bpm
            if (stepTimestamps.isEmpty()) {
                smoothedBpm = bpm.toDouble()
            }
        }
    }

    fun resetToStartingBpm() {
        stepTimestamps.clear()
        smoothedBpm = startingBpm.toDouble()
    }

    fun processStep(timestamp: Long): Int {
        if (stepTimestamps.isNotEmpty()) {
            val interval = timestamp - stepTimestamps.peekLast()!!
            if (interval < falseStepInterval) {
                return smoothedBpm.toInt()
            }
            if (interval > breakInterval) {
                stepTimestamps.clear()
                stepTimestamps.addLast(timestamp)
                return smoothedBpm.toInt()
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

        if (stepTimestamps.size < 5) return smoothedBpm.toInt()
        val currentWindowDuration = stepTimestamps.peekLast()!! - stepTimestamps.peekFirst()!!

        // 4. Minimum Window Guard: Only calculate a new raw BPM if we have accumulated at least 6s of data
        if (currentWindowDuration >= minWindow) {
            val durationMinutes = currentWindowDuration / 60_000_000_000.0
            val rawBpm = (stepTimestamps.size - 1) / durationMinutes

            smoothedBpm = (smoothingFactor * rawBpm) + ((1.0 - smoothingFactor) * smoothedBpm)
        }
        if(smoothedBpm < 100) {
            smoothedBpm = 100.0
        }
        if(smoothedBpm > 200) {
            smoothedBpm = 200.0
        }
        return smoothedBpm.toInt()
    }
}