package com.siwoo.party.games.jegi

import kotlin.math.PI
import kotlin.math.sin

/**
 * 제기 한 번의 비행 (차거나 던진 순간 [t0] 부터).
 * - 높이: [y0] 에서 꼭대기([GameConfig.APEX_Y])까지 [GameConfig.RISE_MS] 동안 감속하며 오르고,
 *   그 뒤 일정한 속도로 내려와 [GameConfig.FALL_MS] 뒤 발 높이([GameConfig.KICK_Y]) → 계속 내려가 땅(0).
 * - 가로: [x0] 에서 [targetX] 로 발 높이에 올 때까지 부드럽게 옮겨 가고(smoothstep),
 *   여기에 사인 흔들림(크기 [GameConfig.SWAY_AMP], 시작 위상 [swayPhase])을 더한다. 흔들림은 t0 에서 0 이 되게 빼 준다(차는 순간 순간이동 없음).
 */
class Flight(
    val t0: Long,
    val x0: Float,
    val y0: Float,
    val targetX: Float,
    /** 흔들림 시작 위상 (라디안) */
    val swayPhase: Float,
) {
    /** 딱 좋은 순간 (제기가 발 높이에 오는 시각) */
    val idealTime: Long get() = t0 + IDEAL_AFTER_MS

    /** 땅에 닿는 시각 */
    val groundTime: Long get() = t0 + GROUND_AFTER_MS

    /** now 의 높이 (땅 아래로는 안 내려감) */
    fun y(now: Long): Float {
        val t = (now - t0).toFloat()
        if (t <= 0f) return y0
        val rise = GameConfig.RISE_MS.toFloat()
        if (t < rise) {
            val u = 1f - t / rise
            return y0 + (GameConfig.APEX_Y - y0) * (1f - u * u)
        }
        return (GameConfig.APEX_Y - FALL_SPEED * (t - rise)).coerceAtLeast(0f)
    }

    /** now 의 가로 위치 */
    fun x(now: Long): Float {
        val t = (now - t0).coerceAtLeast(0L).toFloat()
        val u = (t / IDEAL_AFTER_MS).coerceAtMost(1f)
        val base = x0 + (targetX - x0) * (u * u * (3f - 2f * u))
        // 마당 끝에서 차면 흔들림이 밖으로 나갈 수 있어 가둔다
        return (base + sway(t, swayPhase)).coerceIn(GameConfig.JEGI_MIN, GameConfig.JEGI_MAX)
    }

    /** 위로 오르는 중인지 (그림: 술이 아래로 처짐) */
    fun rising(now: Long): Boolean = now - t0 < GameConfig.RISE_MS

    companion object {
        /** 내려오는 속도 (칸/ms) */
        const val FALL_SPEED: Float = (GameConfig.APEX_Y - GameConfig.KICK_Y) / GameConfig.FALL_MS
        /** t0 부터 딱 좋은 순간까지 */
        const val IDEAL_AFTER_MS: Long = GameConfig.RISE_MS + GameConfig.FALL_MS
        /** t0 부터 땅까지 (꼭대기 → 땅) */
        val GROUND_AFTER_MS: Long = GameConfig.RISE_MS + kotlin.math.ceil(GameConfig.APEX_Y / FALL_SPEED).toLong()

        private const val TWO_PI = (2.0 * PI).toFloat()

        /** 흔들림 (t = t0 부터 지난 ms) — t = 0 에서 0 */
        fun sway(t: Float, phase: Float): Float {
            val w = TWO_PI / GameConfig.SWAY_PERIOD_MS
            return GameConfig.SWAY_AMP * (sin(w * t + phase) - sin(phase))
        }

        /** 판정 구간 ± ms 가 화면에서 몇 칸 높이인지 (발 높이 위아래로 이만큼) */
        fun bandHalf(windowMs: Long): Float = FALL_SPEED * windowMs
    }
}
