package com.example.runningbeat.data

import kotlinx.coroutines.flow.Flow

interface StepSensor {
    val stepEvents: Flow<Long>
    fun startListening()
    fun stopListening()
}
