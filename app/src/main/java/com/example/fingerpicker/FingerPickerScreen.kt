package com.example.fingerpicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Offset from a figure's body center to the potato center when arm is raised
private val POTATO_HAND_OFFSET = Offset(96f, -96f)

// How far above the midpoint the throw arc peaks (pixels)
private const val ARC_HEIGHT = 300f

/** Quadratic bezier: P0 → P1 (control) → P2, evaluated at t ∈ [0,1]. */
private fun bezier(t: Float, p0: Offset, p1: Offset, p2: Offset): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
        u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
    )
}

@Composable
fun FingerPickerScreen(snapshot: GameSnapshot, vm: GameViewModel, onClose: () -> Unit = {}) {

    // Resolve whose hand the potato should land in
    val holderId = when (snapshot.phase) {
        Phase.SELECTING -> snapshot.highlightedId
        Phase.SELECTED  -> snapshot.winnerId
        Phase.WAITING   -> snapshot.lastWinnerId
        else            -> -1
    }
    val holderPos = when (snapshot.phase) {
        Phase.WAITING -> snapshot.lastWinnerFingers[holderId]?.position
        else          -> snapshot.lockedFingers[holderId]?.position ?: snapshot.activeFingers[holderId]?.position
    }
    val potatoTarget = holderPos?.let { Offset(it.x + POTATO_HAND_OFFSET.x, it.y + POTATO_HAND_OFFSET.y) }

    // Arc throw animation — only used during the slow phase
    val throwProgress = remember { Animatable(1f) }
    var throwSource by remember { mutableStateOf(Offset.Zero) }
    var throwTarget by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(potatoTarget, snapshot.isSlowPhase) {
        val target = potatoTarget ?: return@LaunchedEffect
        if (!snapshot.isSlowPhase) {
            // Fast phase: keep source/target in sync so the first slow-phase throw
            // arcs from the correct position
            throwSource = target
            throwTarget = target
            throwProgress.snapTo(1f)
        } else {
            // Slow phase: arc from current mid-flight position to new target
            val t = throwProgress.value
            val cx = (throwSource.x + throwTarget.x) / 2f
            val cy = (throwSource.y + throwTarget.y) / 2f - ARC_HEIGHT
            throwSource = bezier(t, throwSource, Offset(cx, cy), throwTarget)
            throwTarget = target
            throwProgress.snapTo(0f)
            throwProgress.animateTo(1f, tween(225))
        }
    }

    // Fast phase: derive position directly from snapshot (no async lag)
    // Slow phase: evaluate the animated bezier
    val potatoPos = if (!snapshot.isSlowPhase) {
        potatoTarget ?: Offset.Zero
    } else {
        val tp = throwProgress.value
        val arcCx = (throwSource.x + throwTarget.x) / 2f
        val arcCy = (throwSource.y + throwTarget.y) / 2f - ARC_HEIGHT
        bezier(tp, throwSource, Offset(arcCx, arcCy), throwTarget)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .multiTouchInput(vm),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (snapshot.phase) {
                Phase.WAITING -> drawLastWinner(snapshot)
                Phase.COUNTDOWN ->
                    drawFingers(snapshot.activeFingers, null, snapshot.fingerShape)
                Phase.SELECTING ->
                    if (snapshot.fingerShape == FingerShape.STICK_FIGURE)
                        drawHotPotatoScene(snapshot.lockedFingers, snapshot.highlightedId, potatoPos)
                    else
                        drawFingers(snapshot.lockedFingers, snapshot.highlightedId, snapshot.fingerShape)
                Phase.SELECTED ->
                    if (snapshot.fingerShape == FingerShape.STICK_FIGURE)
                        drawHotPotatoScene(snapshot.lockedFingers, snapshot.winnerId, potatoPos, dimOthers = true)
                    else
                        drawWinner(snapshot.lockedFingers, snapshot.winnerId, snapshot.fingerShape)
            }
        }

        when (snapshot.phase) {
            Phase.WAITING  -> WaitingOverlay()
            Phase.COUNTDOWN -> CountdownOverlay(snapshot.countdown, snapshot.activeFingers.size)
            Phase.SELECTING -> Unit
            Phase.SELECTED  -> SelectedOverlay(snapshot.winnerCountdown)
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
                text = when (current) {
                    FingerShape.CIRCLE       -> "● Circles"
                    FingerShape.SQUARE       -> "■ Squares"
                    FingerShape.STICK_FIGURE -> "Hot Potato"
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("● Circles") },    onClick = { onSelect(FingerShape.CIRCLE);       expanded = false })
            DropdownMenuItem(text = { Text("■ Squares") },    onClick = { onSelect(FingerShape.SQUARE);       expanded = false })
            DropdownMenuItem(text = { Text("Hot Potato") },   onClick = { onSelect(FingerShape.STICK_FIGURE); expanded = false })
        }
    }
}

// ---------------------------------------------------------------------------
// Overlays
// ---------------------------------------------------------------------------

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
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        if (fingerCount < 2) {
            Text(
                text = "More players can join!",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
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
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        )
        Text(
            text = "New game in $countdown…",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Drawing — rings / squares
// ---------------------------------------------------------------------------

private fun DrawScope.drawFingers(
    fingers: Map<Int, FingerData>,
    highlightedId: Int?,
    shape: FingerShape
) {
    val radius = 170f
    for ((id, finger) in fingers) {
        val alpha = when {
            highlightedId == null -> 1f
            id == highlightedId  -> 1f
            else                 -> 0.18f
        }
        val hasPotato = highlightedId != null && id == highlightedId
        drawMarker(finger.position, finger.color, alpha, radius, shape, hasPotato)
    }
}

private fun DrawScope.drawWinner(fingers: Map<Int, FingerData>, winnerId: Int, shape: FingerShape) {
    val radius = 170f
    fingers[winnerId]?.let { drawMarker(it.position, it.color, 1f, radius, shape, hasPotato = false) }
}

private fun DrawScope.drawMarker(
    pos: Offset, color: Color, alpha: Float, radius: Float,
    shape: FingerShape, hasPotato: Boolean = false
) {
    when (shape) {
        FingerShape.CIRCLE       -> drawRing(pos, color, alpha, radius)
        FingerShape.SQUARE       -> drawSquare(pos, color, alpha, radius)
        FingerShape.STICK_FIGURE -> drawStickFigure(pos, color, alpha, armRaised = hasPotato)
    }
}

private fun DrawScope.drawRing(pos: Offset, color: Color, alpha: Float, radius: Float) {
    drawCircle(color = color.copy(alpha = alpha * 0.08f), radius = radius + 54f, center = pos, style = Stroke(width = 48f))
    drawCircle(color = color.copy(alpha = alpha * 0.15f), radius = radius + 26f, center = pos, style = Stroke(width = 30f))
    drawCircle(color = color.copy(alpha = alpha),         radius = radius,        center = pos, style = Stroke(width = 16f))
    drawCircle(color = color.copy(alpha = alpha * 0.3f),  radius = 22f,           center = pos)
}

private fun DrawScope.drawSquare(pos: Offset, color: Color, alpha: Float, radius: Float) {
    fun rectAround(pad: Float) = Rect(
        left   = pos.x - radius - pad, top    = pos.y - radius - pad,
        right  = pos.x + radius + pad, bottom = pos.y + radius + pad
    )
    drawRect(color = color.copy(alpha = alpha * 0.08f), topLeft = rectAround(54f).topLeft, size = rectAround(54f).size, style = Stroke(width = 48f))
    drawRect(color = color.copy(alpha = alpha * 0.15f), topLeft = rectAround(26f).topLeft, size = rectAround(26f).size, style = Stroke(width = 30f))
    drawRect(color = color.copy(alpha = alpha),         topLeft = rectAround(0f).topLeft,  size = rectAround(0f).size,  style = Stroke(width = 16f))
    drawRect(color = color.copy(alpha = alpha * 0.3f),  topLeft = Offset(pos.x - 22f, pos.y - 22f), size = Size(44f, 44f))
}

// ---------------------------------------------------------------------------
// Drawing — hot potato
// ---------------------------------------------------------------------------

/** Ghost of last game's chosen player shown during WAITING phase. */
private fun DrawScope.drawLastWinner(snapshot: GameSnapshot) {
    if (snapshot.lastWinnerId == -1) return
    val ghostAlpha = 0.35f
    val fingers = snapshot.lastWinnerFingers
    val winnerId = snapshot.lastWinnerId
    when (snapshot.fingerShape) {
        FingerShape.STICK_FIGURE -> {
            for ((id, finger) in fingers) {
                drawStickFigure(finger.position, finger.color, ghostAlpha, armRaised = id == winnerId)
            }
            fingers[winnerId]?.position?.let { pos ->
                drawPotato(Offset(pos.x + POTATO_HAND_OFFSET.x, pos.y + POTATO_HAND_OFFSET.y), alpha = ghostAlpha)
            }
        }
        else -> {
            fingers[winnerId]?.let { drawMarker(it.position, it.color, ghostAlpha, 170f, snapshot.fingerShape) }
        }
    }
}

/** Draw all figures (no blinking) with the potato travelling independently. */
private fun DrawScope.drawHotPotatoScene(
    fingers: Map<Int, FingerData>,
    holderId: Int,
    potatoPos: Offset,
    dimOthers: Boolean = false
) {
    for ((id, finger) in fingers) {
        val alpha = if (dimOthers && id != holderId) 0.25f else 1f
        drawStickFigure(finger.position, finger.color, alpha, armRaised = id == holderId)
    }
    drawPotato(potatoPos)
}

private fun DrawScope.drawStickFigure(pos: Offset, color: Color, alpha: Float, armRaised: Boolean) {
    val lw = 12f
    val c  = color.copy(alpha = alpha)

    val headC       = Offset(pos.x, pos.y - 92f)
    val headR       = 26f
    val neckY       = pos.y - 66f
    val shldY       = pos.y - 42f
    val hipY        = pos.y + 52f
    val leftArmEnd  = Offset(pos.x - 72f, pos.y + 8f)
    val rightArmEnd = if (armRaised) Offset(pos.x + 72f, pos.y - 90f)
                      else           Offset(pos.x + 72f, pos.y + 8f)
    val leftLegEnd  = Offset(pos.x - 52f, pos.y + 132f)
    val rightLegEnd = Offset(pos.x + 52f, pos.y + 132f)

    if (armRaised) {
        drawCircle(color = color.copy(alpha = alpha * 0.12f), radius = 140f, center = pos)
    }

    drawCircle(color = c, radius = headR, center = headC, style = Stroke(width = lw))
    drawLine(color = c, start = Offset(pos.x, neckY), end = Offset(pos.x, hipY), strokeWidth = lw, cap = StrokeCap.Round)
    drawLine(color = c, start = Offset(pos.x, shldY), end = leftArmEnd,  strokeWidth = lw, cap = StrokeCap.Round)
    drawLine(color = c, start = Offset(pos.x, shldY), end = rightArmEnd, strokeWidth = lw, cap = StrokeCap.Round)
    drawLine(color = c, start = Offset(pos.x, hipY),  end = leftLegEnd,  strokeWidth = lw, cap = StrokeCap.Round)
    drawLine(color = c, start = Offset(pos.x, hipY),  end = rightLegEnd, strokeWidth = lw, cap = StrokeCap.Round)
}

private fun DrawScope.drawPotato(center: Offset, alpha: Float = 1f) {
    drawOval(
        color   = Color(0xFFFF5500).copy(alpha = 0.35f * alpha),
        topLeft = Offset(center.x - 30f, center.y - 22f),
        size    = Size(60f, 44f)
    )
    drawOval(
        color   = Color(0xFF8B4513).copy(alpha = alpha),
        topLeft = Offset(center.x - 20f, center.y - 14f),
        size    = Size(40f, 28f)
    )
    drawOval(
        color   = Color(0xFFCC6633).copy(alpha = 0.6f * alpha),
        topLeft = Offset(center.x - 10f, center.y - 10f),
        size    = Size(16f, 10f)
    )
}

// ---------------------------------------------------------------------------
// Touch input
// ---------------------------------------------------------------------------

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
