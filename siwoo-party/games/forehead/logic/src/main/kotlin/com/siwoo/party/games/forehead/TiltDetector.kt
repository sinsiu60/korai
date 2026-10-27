package com.siwoo.party.games.forehead

import kotlin.math.atan2
import kotlin.math.sqrt

/** 기울기 자세 */
enum class Tilt {
    /** 이마에 똑바로 세움 (가운데) */
    NEUTRAL,
    /** 아래로 기울임 (화면이 바닥 쪽) → 정답 */
    DOWN,
    /** 위로 기울임 (화면이 천장 쪽) → 패스 */
    UP,
    /** 가운데와 동작 각도 사이 */
    BETWEEN,
    /** 판정 불가 (흔드는 중·거꾸로 뒤집힘) */
    INVALID,
}

/** 기울여서 생긴 동작 */
enum class TiltAction { CORRECT, PASS }

/**
 * 가속도 → 기울기 계산 (순수 함수).
 * 가속도 값은 기기 기준 좌표(세로 기본 방향: x 오른쪽, y 위, z 화면 바깥), 정지 상태에서 위쪽 축이 +g.
 * rotation 은 화면 회전 (Surface.ROTATION_0..270 = 0..3). 가로 화면에서 "화면의 위쪽"이 기기의 어느 축인지가 회전마다 다르다:
 *   0 → +y, 1(90°) → +x, 2(180°) → -y, 3(270°) → -x
 * 그래서 같은 자세라도 가로 방향(90°/270°)에 따라 x 부호가 반대다.
 */
object TiltMath {
    const val GRAVITY = 9.80665f
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    /** 화면 위쪽 방향 성분 (똑바로 세우면 +g) */
    fun up(x: Float, y: Float, rotation: Int): Float = when (rotation and 3) {
        0 -> y
        1 -> x
        2 -> -y
        else -> -x
    }

    /** 기울기 각 (도): 0 = 똑바로, 음수 = 화면이 아래(바닥)를 봄, 양수 = 화면이 위(천장)를 봄 */
    fun pitch(x: Float, y: Float, z: Float, rotation: Int): Float = atan2(z, up(x, y, rotation)) * RAD_TO_DEG

    /** 중력 크기 (g 배수) */
    fun gForce(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z) / GRAVITY

    /** 한 순간의 자세 판정 (히스테리시스 없음) */
    fun classify(
        x: Float, y: Float, z: Float, rotation: Int,
        trigger: Float = GameConfig.TILT_TRIGGER_DEG,
        neutral: Float = GameConfig.TILT_NEUTRAL_DEG,
        flip: Float = GameConfig.TILT_FLIP_DEG,
    ): Tilt {
        val g = gForce(x, y, z)
        if (g < GameConfig.G_MIN || g > GameConfig.G_MAX) return Tilt.INVALID
        return classifyPitch(pitch(x, y, z, rotation), trigger, neutral, flip)
    }

    fun classifyPitch(
        p: Float,
        trigger: Float = GameConfig.TILT_TRIGGER_DEG,
        neutral: Float = GameConfig.TILT_NEUTRAL_DEG,
        flip: Float = GameConfig.TILT_FLIP_DEG,
    ): Tilt = when {
        p > flip || p < -flip -> Tilt.INVALID
        p <= -trigger -> Tilt.DOWN
        p >= trigger -> Tilt.UP
        p > -neutral && p < neutral -> Tilt.NEUTRAL
        else -> Tilt.BETWEEN
    }
}

/**
 * 기울기 동작 감지 (히스테리시스 + 저역 통과 필터 + 최소 간격).
 * - 처음과 동작 직후에는 "잠김": 가운데(NEUTRAL)로 돌아와야 다시 동작한다 → 한 번 기울이면 한 번만.
 * - 동작 각도(50°)와 다시 풀리는 각도(25°)가 달라서 경계에서 떨려도 여러 번 나오지 않는다.
 * - 똑바로 든 시간(uprightMs)으로 "이마에 댔다"를 알아챈다 (카운트 시작용).
 * 시간(ms)은 밖에서 넣는다.
 */
class TiltDetector(
    private val smoothing: Float = GameConfig.TILT_SMOOTHING,
    private val trigger: Float = GameConfig.TILT_TRIGGER_DEG,
    private val neutral: Float = GameConfig.TILT_NEUTRAL_DEG,
    private val minGapMs: Long = GameConfig.ACTION_GAP_MS,
) {
    /** 다음 동작을 받을 수 있는지 (가운데로 돌아온 뒤) */
    var armed = false
        private set

    /** 마지막 (필터된) 자세 */
    var tilt = Tilt.INVALID
        private set

    /** 마지막 (필터된) 기울기 각 */
    var pitch = 0f
        private set

    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var hasSample = false
    private var lastActionAt = Long.MIN_VALUE / 2
    private var neutralSince = -1L

    /** 처음 상태로 (가운데로 돌아와야 동작) */
    fun reset() {
        armed = false
        hasSample = false
        tilt = Tilt.INVALID
        pitch = 0f
        neutralSince = -1L
        lastActionAt = Long.MIN_VALUE / 2
    }

    /** 가속도 한 번 → 동작이 생기면 돌려준다 */
    fun update(x: Float, y: Float, z: Float, rotation: Int, nowMs: Long): TiltAction? {
        if (!hasSample || smoothing >= 1f) {
            fx = x; fy = y; fz = z
            hasSample = true
        } else {
            fx += (x - fx) * smoothing
            fy += (y - fy) * smoothing
            fz += (z - fz) * smoothing
        }
        val g = TiltMath.gForce(fx, fy, fz)
        pitch = TiltMath.pitch(fx, fy, fz, rotation)
        tilt = if (g < GameConfig.G_MIN || g > GameConfig.G_MAX) Tilt.INVALID
        else TiltMath.classifyPitch(pitch, trigger, neutral)
        if (tilt == Tilt.NEUTRAL) {
            if (neutralSince < 0) neutralSince = nowMs
            armed = true
            return null
        }
        neutralSince = -1L
        if (!armed || nowMs - lastActionAt < minGapMs) return null
        val action = when (tilt) {
            Tilt.DOWN -> TiltAction.CORRECT
            Tilt.UP -> TiltAction.PASS
            else -> return null
        }
        armed = false
        lastActionAt = nowMs
        return action
    }

    /** 지금까지 똑바로(가운데) 들고 있던 시간 (아니면 0) */
    fun uprightMs(nowMs: Long): Long = if (neutralSince < 0) 0 else nowMs - neutralSince
}
