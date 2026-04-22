package com.example.fingerpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

val FingerColors = listOf(
    Color(0xFFE53935), // Red
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFFDD835), // Yellow
    Color(0xFFFB8C00), // Orange
    Color(0xFFEC407A), // Pink
    Color(0xFF00ACC1), // Cyan
    Color(0xFF8E24AA), // Purple
    Color(0xFFFF7043), // Deep Orange
    Color(0xFF7CB342), // Light Green
)

enum class Phase { WAITING, COUNTDOWN, SELECTING, SELECTED }
enum class FingerShape { CIRCLE, SQUARE }

data class FingerData(val position: Offset, val color: Color)

data class GameSnapshot(
    val phase: Phase = Phase.WAITING,
    val activeFingers: Map<Int, FingerData> = emptyMap(),
    val lockedFingers: Map<Int, FingerData> = emptyMap(),
    val highlightedId: Int = -1,
    val winnerId: Int = -1,
    val countdown: Int = 10,
    val winnerCountdown: Int = 10,
    val fingerShape: FingerShape = FingerShape.CIRCLE,
)
