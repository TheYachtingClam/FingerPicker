package com.example.fingerpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FingerPickerScreen(snapshot: GameSnapshot, vm: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .multiTouchInput(vm),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (snapshot.phase) {
                Phase.WAITING -> Unit
                Phase.COUNTDOWN -> drawFingers(snapshot.activeFingers, null)
                Phase.SELECTING -> drawFingers(snapshot.lockedFingers, snapshot.highlightedId)
                Phase.SELECTED -> drawWinner(snapshot.lockedFingers, snapshot.winnerId)
            }
        }

        when (snapshot.phase) {
            Phase.WAITING -> WaitingOverlay()
            Phase.COUNTDOWN -> CountdownOverlay(snapshot.countdown, snapshot.activeFingers.size)
            Phase.SELECTING -> Unit
            Phase.SELECTED -> SelectedOverlay()
        }
    }
}

@Composable
private fun WaitingOverlay() {
    Text(
        text = "Place your fingers\non the screen",
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = 36.sp
    )
}

@Composable
private fun CountdownOverlay(countdown: Int, fingerCount: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = countdown.toString(),
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )
        if (fingerCount < 2) {
            Text(
                text = "More players can join!",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun SelectedOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Chosen!",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        )
        Text(
            text = "Tap to play again",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

private fun DrawScope.drawFingers(fingers: Map<Int, FingerData>, highlightedId: Int?) {
    val radius = 120f
    for ((id, finger) in fingers) {
        val alpha = when {
            highlightedId == null -> 1f      // countdown: all full
            id == highlightedId -> 1f         // selecting: highlighted
            else -> 0.18f                     // selecting: dimmed
        }
        drawRing(finger.position, finger.color, alpha, radius)
    }
}

private fun DrawScope.drawWinner(fingers: Map<Int, FingerData>, winnerId: Int) {
    val radius = 120f
    fingers[winnerId]?.let { drawRing(it.position, it.color, 1f, radius) }
}

private fun DrawScope.drawRing(pos: Offset, color: Color, alpha: Float, radius: Float) {
    // Outer glow layers
    drawCircle(color = color.copy(alpha = alpha * 0.08f), radius = radius + 40f, center = pos, style = Stroke(width = 36f))
    drawCircle(color = color.copy(alpha = alpha * 0.15f), radius = radius + 18f, center = pos, style = Stroke(width = 20f))
    // Main ring
    drawCircle(color = color.copy(alpha = alpha), radius = radius, center = pos, style = Stroke(width = 6f))
    // Center dot
    drawCircle(color = color.copy(alpha = alpha * 0.3f), radius = 16f, center = pos)
}

private fun Modifier.multiTouchInput(vm: GameViewModel): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Press -> {
                        event.changes.forEach { c ->
                            if (c.pressed && !c.previousPressed) {
                                vm.onFingerDown(c.id.value.toInt(), c.position)
                                c.consume()
                            }
                        }
                    }
                    PointerEventType.Move -> {
                        val updates = event.changes
                            .filter { it.pressed }
                            .associate { it.id.value.toInt() to it.position }
                        vm.onFingerMove(updates)
                        event.changes.forEach { it.consume() }
                    }
                    PointerEventType.Release -> {
                        event.changes.forEach { c ->
                            if (!c.pressed && c.previousPressed) {
                                vm.onFingerUp(c.id.value.toInt())
                                c.consume()
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
