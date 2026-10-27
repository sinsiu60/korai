package com.siwoo.party.common.ui

import android.os.Handler
import android.os.Looper

/**
 * 연출 큐: 연출을 하나씩 순서대로 실행한다 (동시에 겹치지 않게).
 * 각 단계는 끝나면 done() 을 불러야 다음 단계로 넘어간다.
 * cancel() 하면 남은 단계를 버리고, 늦게 도착한 done() 은 무시된다.
 */
class FxQueue {

    fun interface Step {
        fun run(done: () -> Unit)
    }

    private class Item(val name: String, val step: Step)

    private val handler = Handler(Looper.getMainLooper())
    private val items = ArrayDeque<Item>()
    private var token = 0

    /** 지금 단계를 실행 중이거나 남은 단계가 있음 */
    var running: Boolean = false
        private set

    /** 큐가 모두 끝났을 때 */
    var onIdle: (() -> Unit)? = null

    /** 실행된 단계 이름 기록 (연출 순서 확인용, 최근 300개) */
    val history = ArrayList<String>()

    fun add(name: String, step: Step) {
        items.addLast(Item(name, step))
    }

    /** 기다리기만 하는 단계 */
    fun pause(name: String, ms: Long) {
        add(name) { done -> handler.postDelayed({ done() }, ms) }
    }

    fun start() {
        if (running) return
        running = true
        next()
    }

    private fun next() {
        val item = items.removeFirstOrNull()
        if (item == null) {
            running = false
            onIdle?.invoke()
            return
        }
        history.add(item.name)
        if (history.size > 300) history.removeAt(0)
        val my = token
        var called = false
        item.step.run {
            if (my == token && !called) {
                called = true
                // 콜백 안에서 바로 다음 단계를 부르지 않고 한 박자 뒤에 (재귀 방지)
                handler.post { if (my == token) next() }
            }
        }
    }

    /** 남은 연출을 모두 버린다 (백그라운드 전환 등). onIdle 은 부르지 않는다. */
    fun cancel() {
        token++
        items.clear()
        handler.removeCallbacksAndMessages(null)
        running = false
    }
}
