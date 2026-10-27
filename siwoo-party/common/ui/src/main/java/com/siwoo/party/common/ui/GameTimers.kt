package com.siwoo.party.common.ui

import android.os.Handler
import android.os.SystemClock

/**
 * 일시정지되는 게임 타이머.
 * - after(ms) 로 예약한 일은 pause() 동안 멈췄다가 resume() 하면 남은 시간만큼 뒤에 실행된다.
 * - now() 는 일시정지 시간을 뺀 "게임 시간" (반응 시간·제한 시간 계산용).
 * 백그라운드로 가거나 일시정지 메뉴를 열면 PartyGameActivity 가 자동으로 멈춘다.
 */
class GameTimers(private val handler: Handler) {

    inner class Task internal constructor(private val block: () -> Unit) : Runnable {
        internal var due = 0L
        internal var remaining = 0L
        internal var active = true
        override fun run() {
            if (!active) return
            active = false
            tasks.remove(this)
            block()
        }
        fun cancel() {
            if (!active) return
            active = false
            handler.removeCallbacks(this)
            tasks.remove(this)
        }
    }

    private val tasks = ArrayList<Task>()
    var paused = false
        private set
    private var pausedAt = 0L
    private var pausedTotal = 0L

    /** 일시정지 시간을 뺀 게임 시간(ms) */
    fun now(): Long = (if (paused) pausedAt else SystemClock.uptimeMillis()) - pausedTotal

    /** ms 뒤에 block 실행 (게임 시간 기준) */
    fun after(ms: Long, block: () -> Unit): Task {
        val t = Task(block)
        val d = ms.coerceAtLeast(0)
        tasks.add(t)
        if (paused) {
            t.remaining = d
        } else {
            t.due = SystemClock.uptimeMillis() + d
            handler.postDelayed(t, d)
        }
        return t
    }

    fun cancelAll() {
        for (t in ArrayList(tasks)) t.cancel()
    }

    fun pause() {
        if (paused) return
        paused = true
        pausedAt = SystemClock.uptimeMillis()
        for (t in tasks) {
            handler.removeCallbacks(t)
            t.remaining = (t.due - pausedAt).coerceAtLeast(0)
        }
    }

    fun resume() {
        if (!paused) return
        val n = SystemClock.uptimeMillis()
        pausedTotal += n - pausedAt
        paused = false
        for (t in tasks) {
            t.due = n + t.remaining
            handler.postDelayed(t, t.remaining)
        }
    }
}
