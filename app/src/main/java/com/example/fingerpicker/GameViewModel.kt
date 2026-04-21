package com.example.fingerpicker

import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel() {

    private val handler = Handler(Looper.getMainLooper())

    var state = GameSnapshot()
        private set

    var onStateChanged: (() -> Unit)? = null

    private val colorPool = ArrayDeque(FingerColors.toMutableList())
    private val activeFingers = mutableMapOf<Int, FingerData>()
    private var lockedFingers = mapOf<Int, FingerData>()

    private var countdownTimer: CountDownTimer? = null
    private var selectionRunnable: Runnable? = null

    private var selectionStep = 0
    private var selectionStartMs = 0L
    private var slowPhaseStarted = false
    private var slowPhaseDelay = 300L
    private var slowPhaseStepsLeft = 0
    private var winnerId = -1

    // --- Touch input ---

    fun onFingerDown(pointerId: Int, position: Offset) {
        if (state.phase == Phase.SELECTING || state.phase == Phase.SELECTED) return
        if (colorPool.isEmpty()) return

        val color = colorPool.removeFirst()
        activeFingers[pointerId] = FingerData(position, color)

        if (state.phase == Phase.WAITING) startCountdown()
        publish()
    }

    fun onFingerMove(updates: Map<Int, Offset>) {
        if (state.phase != Phase.COUNTDOWN) return
        for ((id, pos) in updates) {
            activeFingers[id]?.let { activeFingers[id] = it.copy(position = pos) }
        }
        publish()
    }

    fun onFingerUp(pointerId: Int) {
        if (state.phase == Phase.SELECTING || state.phase == Phase.SELECTED) {
            reset()
            return
        }
        activeFingers.remove(pointerId)?.let { colorPool.addFirst(it.color) }
        if (state.phase == Phase.COUNTDOWN && activeFingers.isEmpty()) {
            countdownTimer?.cancel()
            state = state.copy(phase = Phase.WAITING, countdown = 10)
            publish()
        } else {
            publish()
        }
    }

    // --- Countdown ---

    private fun startCountdown() {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(10_000L, 100L) {
            override fun onTick(ms: Long) {
                val c = ((ms + 999L) / 1000L).toInt()
                state = state.copy(phase = Phase.COUNTDOWN, countdown = c)
                publish()
            }
            override fun onFinish() {
                startSelection()
            }
        }.start()
    }

    // --- Selection ---

    private fun startSelection() {
        countdownTimer?.cancel()
        if (activeFingers.isEmpty()) { reset(); return }

        lockedFingers = activeFingers.toMap()
        activeFingers.clear()
        colorPool.clear()
        colorPool.addAll(FingerColors)

        val ids = lockedFingers.keys.toList()
        if (ids.size == 1) {
            winnerId = ids[0]
            state = state.copy(
                phase = Phase.SELECTED,
                lockedFingers = lockedFingers,
                activeFingers = emptyMap(),
                highlightedId = winnerId,
                winnerId = winnerId
            )
            publish()
            return
        }

        winnerId = ids.random()
        selectionStep = 0
        selectionStartMs = System.currentTimeMillis()
        slowPhaseStarted = false

        state = state.copy(
            phase = Phase.SELECTING,
            lockedFingers = lockedFingers,
            activeFingers = emptyMap()
        )
        publish()
        scheduleNextBlink()
    }

    private fun scheduleNextBlink() {
        val elapsed = System.currentTimeMillis() - selectionStartMs
        val ids = lockedFingers.keys.toList()
        val n = ids.size

        if (!slowPhaseStarted && elapsed >= 4000L) {
            slowPhaseStarted = true
            val winnerIdx = ids.indexOf(winnerId)
            val currIdx = selectionStep % n
            val raw = (winnerIdx - currIdx + n) % n
            slowPhaseStepsLeft = if (raw == 0) n else raw
            slowPhaseDelay = (1000L / slowPhaseStepsLeft).coerceIn(200L, 600L)
        }

        val delay: Long
        if (slowPhaseStarted) {
            if (slowPhaseStepsLeft == 0) {
                state = state.copy(phase = Phase.SELECTED, highlightedId = winnerId, winnerId = winnerId)
                publish()
                return
            }
            state = state.copy(highlightedId = ids[selectionStep % n])
            selectionStep++
            slowPhaseStepsLeft--
            delay = slowPhaseDelay
        } else {
            state = state.copy(highlightedId = ids[selectionStep % n])
            selectionStep++
            delay = 80L
        }

        publish()
        val r = Runnable { scheduleNextBlink() }
        selectionRunnable = r
        handler.postDelayed(r, delay)
    }

    // --- Reset ---

    private fun reset() {
        selectionRunnable?.let { handler.removeCallbacks(it) }
        countdownTimer?.cancel()
        activeFingers.clear()
        lockedFingers = emptyMap()
        colorPool.clear()
        colorPool.addAll(FingerColors)
        selectionStep = 0
        slowPhaseStarted = false
        winnerId = -1
        state = GameSnapshot()
        publish()
    }

    private fun publish() {
        state = state.copy(activeFingers = activeFingers.toMap())
        onStateChanged?.invoke()
    }

    override fun onCleared() {
        super.onCleared()
        selectionRunnable?.let { handler.removeCallbacks(it) }
        countdownTimer?.cancel()
    }
}
