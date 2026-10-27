package com.siwoo.party.common.physics

/**
 * 고정 시간 단계 누적기. 받은 프레임 시간(ms)을 정수 틱으로 모아(1ms = 60틱, 한 단계 = 1000틱)
 * 몇 단계(1/60초)를 진행할지 알려 준다 → 프레임 시간을 어떻게 쪼개도(각 프레임 ≤ maxFrameMs) 같은 단계 수.
 * 자투리는 다음 프레임으로 넘어간다.
 */
class FixedStep(var maxFrameMs: Long = PhysicsConfig.MAX_FRAME_MS) {
    private var ticks = 0L

    /** dtMs 를 더하고(0..maxFrameMs 로 자름) 지금 진행할 단계 수를 돌려준다 */
    fun add(dtMs: Long): Int {
        ticks += dtMs.coerceIn(0L, maxFrameMs) * PhysicsConfig.TICKS_PER_MS
        val n = ticks / PhysicsConfig.TICKS_PER_STEP
        ticks -= n * PhysicsConfig.TICKS_PER_STEP
        return n.toInt()
    }

    /** 자투리 시간 버리기 (새로 출발할 때) */
    fun reset() {
        ticks = 0L
    }

    /** 다음 단계까지 모인 비율 0..1 (그릴 때 보간하고 싶으면) */
    val fraction: Float get() = ticks.toFloat() / PhysicsConfig.TICKS_PER_STEP
}
