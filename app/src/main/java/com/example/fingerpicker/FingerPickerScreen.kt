package com.example.fingerpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
fun FingerPickerScreen(snapshot: GameSnapshot, vm: GameViewModel, onClose: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .multiTouchInput(vm),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (snapshot.phase) {
                Phase.WAITING -> Unit
                Phase.COUNTDOWN -> drawFingers(snapshot.activeFingers, null, snapshot.fingerShape)
                Phase.SELECTING -> drawFingers(snapshot.lockedFingers, snapshot.highlightedId, snapshot.fingerShape)
                Phase.SELECTED -> drawWinner(snapshot.lockedFingers, snapshot.winnerId, snapshot.fingerShape)
            }
        }

        when (snapshot.phase) {
            Phase.WAITING -> WaitingOverlay()
            Phase.COUNTDOWN -> CountdownOverlay(snapshot.countdown, snapshot.activeFingers.size)
            Phase.SELECTING -> Unit
            Phase.SELECTED -> SelectedOverlay(snapshot.winnerCountdown)
        }

        ShapeDropdown(
            current = snapshot.fingerShape,
            onSelect = { vm.setShape(it) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ShapeDropdown(
    current: FingerShape,
    onSelect: (FingerShape) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = if (current == FingerShape.CIRCLE) "● Circles" else "■ Squares",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("● Circles") },
                onClick = { onSelect(FingerShape.CIRCLE); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("■ Squares") },
                onClick = { onSelect(FingerShape.SQUARE); expanded = false }
            )
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
private fun SelectedOverlay(countdown: Int) {
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
            text = "New game in $countdown…",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

private fun DrawScope.drawFingers(
    fingers: Map<Int, FingerData>,
    highlightedId: Int?,
    shape: FingerShape
) {
    val radius = 170f
    for ((id, finger) in fingers) {
        val alpha = when {
            highlightedId == null -> 1f
            id == highlightedId -> 1f
            else -> 0.18f
        }
        drawMarker(finger.position, finger.color, alpha, radius, shape)
    }
}

private fun DrawScope.drawWinner(
    fingers: Map<Int, FingerData>,
    winnerId: Int,
    shape: FingerShape
) {
    val radius = 170f
    fingers[winnerId]?.let { drawMarker(it.position, it.color, 1f, radius, shape) }
}

private fun DrawScope.drawMarker(
    pos: Offset,
    color: Color,
    alpha: Float,
    radius: Float,
    shape: FingerShape
) {
    when (shape) {
        FingerShape.CIRCLE -> drawRing(pos, color, alpha, radius)
        FingerShape.SQUARE -> drawSquare(pos, color, alpha, radius)
    }
}

private fun DrawScope.drawRing(pos: Offset, color: Color, alpha: Float, radius: Float) {
    drawCircle(color = color.copy(alpha = alpha * 0.08f), radius = radius + 54f, center = pos, style = Stroke(width = 48f))
    drawCircle(color = color.copy(alpha = alpha * 0.15f), radius = radius + 26f, center = pos, style = Stroke(width = 30f))
    drawCircle(color = color.copy(alpha = alpha), radius = radius, center = pos, style = Stroke(width = 16f))
    drawCircle(color = color.copy(alpha = alpha * 0.3f), radius = 22f, center = pos)
}

private fun DrawScope.drawSquare(pos: Offset, color: Color, alpha: Float, radius: Float) {
    fun rectAround(pad: Float) = Rect(
        left = pos.x - radius - pad,
        top = pos.y - radius - pad,
        right = pos.x + radius + pad,
        bottom = pos.y + radius + pad
    )
    // Outer glow layers
    drawRect(color = color.copy(alpha = alpha * 0.08f), topLeft = rectAround(54f).topLeft, size = rectAround(54f).size, style = Stroke(width = 48f))
    drawRect(color = color.copy(alpha = alpha * 0.15f), topLeft = rectAround(26f).topLeft, size = rectAround(26f).size, style = Stroke(width = 30f))
    // Main square
    drawRect(color = color.copy(alpha = alpha), topLeft = rectAround(0f).topLeft, size = rectAround(0f).size, style = Stroke(width = 16f))
    // Center dot
    drawRect(
        color = color.copy(alpha = alpha * 0.3f),
        topLeft = Offset(pos.x - 22f, pos.y - 22f),
        size = Size(44f, 44f)
    )
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
