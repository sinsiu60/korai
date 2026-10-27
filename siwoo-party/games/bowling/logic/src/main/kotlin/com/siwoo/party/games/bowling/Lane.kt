package com.siwoo.party.games.bowling

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.FixedStep
import com.siwoo.party.common.physics.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 핀 상태: 서 있음 → (쓰러짐: 회색으로 누운 표시) → 치움 */
enum class PinState { STANDING, FALLEN, REMOVED }

/**
 * 핀 하나 = 공통 물리 Body (반지름 PIN_R, 가벼움). number = 1..10 (정식 번호).
 * 서 있거나 쓰러져 미끄러지는 동안은 물리 세계가 다루고, 레인 밖(거터·핏)으로 나가면 그 자리에 누운 채 멈춘다.
 */
class Pin(index: Int) : Body(index, GameConfig.PIN_R, GameConfig.PIN_MASS) {
    val number = index + 1
    /** 정식 자리 */
    val spotX = Lane.spotX(index)
    val spotY = Lane.spotY(index)
    /** 이번 투구를 시작할 때 서 있던 자리 (밀린 거리 판정용) */
    var restX = spotX
    var restY = spotY
    var state = PinState.STANDING
    /** 쓰러진 방향 (라디안, 그림에서 누운 방향) */
    var fallAngle = 0f

    val standing: Boolean get() = state == PinState.STANDING

    init {
        restitution = GameConfig.PIN_E
        friction = GameConfig.PIN_FRICTION
    }
}

/** 볼링공 = 공통 물리 Body (반지름 BALL_R, 무거움) + 훅·거터 상태 */
class Ball : Body(BALL_ID, GameConfig.BALL_R, GameConfig.BALL_MASS) {
    /** 훅 −1..1 (+ = 진행 방향 오른쪽 = 화면 아래로 휨) */
    var hook = 0f
    /** 거터에 빠짐 (아무것도 못 맞힘) */
    var inGutter = false
    /** 이번 투구에서 핀을 맞혔는지 */
    var hitPin = false
    /** 굴러간 각도 (그림: 손가락 구멍 회전) */
    var roll = 0f

    init {
        restitution = GameConfig.BALL_E
        active = false
    }

    companion object {
        const val BALL_ID = 100
    }
}

/**
 * 레인 위의 물리 = 공통 물리 World (벽 없음) + 훅(옆 가속) + 거터 + 핀 쓰러짐 판정.
 * 공통 World 에는 단계마다 힘을 넣는 자리가 없으므로, 고정 1/60초 단계를 여기서 직접 세어
 * [step] 마다 "훅 → World.step() → 거터·쓰러짐·핏 판정" 순서로 진행한다 (컬링 Sheet 와 같은 방식).
 * 굴림이 끝나 모두 멈추면 rolling = false → 화면의 프레임 루프도 멈춘다.
 */
class Lane {

    /** 소리·연출 알림 */
    interface Listener {
        /** 공·핀이 (x, y) 에서 세기 speed 로 부딪힘 */
        fun onHit(x: Float, y: Float, speed: Float, ball: Boolean) {}
        /** 공이 거터에 빠짐 (gutterBall = 핀을 하나도 못 맞히고 빠짐) */
        fun onGutter(gutterBall: Boolean) {}
        /** 핀이 쓰러짐 */
        fun onKnock(pin: Pin) {}
    }

    var listener: Listener? = null

    val pins = Array(GameConfig.PINS) { Pin(it) }
    val ball = Ball()

    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        substeps = GameConfig.SUBSTEPS
        for (p in pins) addBody(p)
        addBody(ball)
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {
                val isBall = a === ball || b === ball
                if (isBall) ball.hitPin = true
                if (a is Pin) hitPin(a, b, speed)
                if (b is Pin) hitPin(b, a, speed)
                this@Lane.listener?.onHit(x, y, speed, isBall)
            }
        }
    }

    /** 공이 굴러가는 중 (프레임 루프를 돌려야 함) */
    var rolling = false
        private set
    /** 이번 투구에서 쓰러진 핀 수 */
    var knockedThisRoll = 0
        private set
    /** 핀을 하나도 못 맞히고 거터에 빠짐 */
    var gutterBall = false
        private set
    /** 이번 투구 단계 수 */
    var rollSteps = 0
        private set

    private val clock = FixedStep(GameConfig.MAX_FRAME_MS)

    /** 지금까지 진행한 단계 수 */
    val steps: Long get() = world.steps

    init {
        rack()
    }

    /** 서 있는 핀 수 */
    val standingCount: Int
        get() {
            var n = 0
            for (p in pins) if (p.standing) n++
            return n
        }

    /** 핀 10개를 모두 제자리에 세움 + 공은 대기 */
    fun rack() {
        for (p in pins) {
            p.place(p.spotX, p.spotY)
            p.restX = p.spotX
            p.restY = p.spotY
            p.state = PinState.STANDING
            p.radius = GameConfig.PIN_R
            p.active = true
            p.fallAngle = 0f
        }
        resetBall()
        knockedThisRoll = 0
        gutterBall = false
        rolling = false
    }

    /** 쓰러진 핀 치우기 (굴림이 끝난 뒤) */
    fun removeFallen() {
        for (p in pins) if (p.state == PinState.FALLEN) {
            p.state = PinState.REMOVED
            p.active = false
            p.stop()
        }
        resetBall()
    }

    private fun resetBall() {
        ball.active = false
        ball.place(GameConfig.BALL_REST_X, GameConfig.LANE_W / 2f)
        ball.inGutter = false
        ball.hitPin = false
        ball.hook = 0f
        ball.mask = 1
    }

    /**
     * 공 굴리기: 파울 라인 바로 뒤 레인 높이 startY 에서, 진행 각도 angleDeg(+ = 화면 아래쪽), 빠르기 speed, 훅 hook(−1..1).
     * 굴리는 중이면 false.
     */
    fun launch(startY: Float, angleDeg: Float, speed: Float, hook: Float): Boolean {
        if (rolling) return false
        val y = startY.coerceIn(GameConfig.BALL_R, GameConfig.LANE_W - GameConfig.BALL_R)
        for (p in pins) if (p.standing) {
            p.stop()
            p.restX = p.x
            p.restY = p.y
        }
        val a = Math.toRadians(angleDeg.toDouble()).toFloat()
        ball.place(GameConfig.BALL_START_X, y)
        ball.setVelocity(cos(a) * speed, sin(a) * speed)
        ball.hook = hook.coerceIn(-1f, 1f)
        ball.inGutter = false
        ball.hitPin = false
        ball.mask = 1
        ball.active = true
        knockedThisRoll = 0
        gutterBall = false
        rollSteps = 0
        rolling = true
        clock.reset()
        return true
    }

    /** dtMs 만큼 시간을 흘리고 모인 만큼 고정 단계를 진행. 아직 굴러가는 중이면 true */
    fun advance(dtMs: Long): Boolean {
        if (!rolling) {
            clock.reset()
            return false
        }
        val n = clock.add(dtMs)
        for (i in 0 until n) {
            step()
            if (!rolling) break
        }
        return rolling
    }

    /** 고정 1/60초 한 단계 */
    fun step() {
        if (!rolling) return
        val dt = GameConfig.STEP_SEC
        rollSteps++
        val b = ball
        // 훅: 굴러갈수록 커지는 옆 가속 (핀을 맞히기 전, 레인 위에서만)
        if (b.active && !b.inGutter && !b.hitPin && b.moving && b.hook != 0f) {
            val p = (b.x / GameConfig.HEAD_X).coerceIn(0f, 1f)
            b.vy += b.hook * GameConfig.HOOK_ACCEL * p * p * dt
        }
        // 거터 안: 거터 가운데로 미끄러짐
        if (b.active && b.inGutter) {
            val gy = if (b.y < GameConfig.LANE_W / 2f) -GameConfig.GUTTER_W / 2f else GameConfig.LANE_W + GameConfig.GUTTER_W / 2f
            b.vy = (gy - b.y) * GameConfig.GUTTER_SINK
        }
        world.step()
        if (b.active) {
            b.roll += b.speed * dt / GameConfig.BALL_R
            if (b.roll > TWO_PI) b.roll -= TWO_PI
            if (!b.inGutter && isOffLane(b.y)) {
                b.inGutter = true
                b.mask = 0
                if (!b.hitPin) gutterBall = true
                listener?.onGutter(gutterBall)
            }
            if (b.x > GameConfig.PIT_X + GameConfig.BALL_R) {
                b.active = false
                b.stop()
            }
        }
        for (p in pins) {
            if (!p.active) continue
            if (p.standing && displaced(p.x - p.restX, p.y - p.restY)) knock(p, p.vx, p.vy)
            if (isOffLane(p.y) || p.x > GameConfig.PIT_X || p.x < -GameConfig.APPROACH) {
                // 거터·핏으로 떨어짐 → 그 자리에 누운 채 멈춤
                if (p.standing) knock(p, p.vx, p.vy)
                p.fallAngle = angleOf(p.vx, p.vy, p.fallAngle)
                p.active = false
                p.stop()
            }
        }
        if (rollSteps >= GameConfig.MAX_ROLL_STEPS) {
            for (body in world.bodies) body.stop()
            b.active = false
        }
        if (world.settled) {
            rolling = false
            b.active = false
            for (p in pins) if (p.standing) p.stop()
            clock.reset()
        }
    }

    private fun hitPin(p: Pin, other: Body, speed: Float) {
        if (!p.standing || !knocks(speed)) return
        knock(p, p.x - other.x, p.y - other.y)
    }

    /** 핀 쓰러뜨림 (dx, dy = 쓰러지는 방향) */
    private fun knock(p: Pin, dx: Float, dy: Float) {
        if (!p.standing) return
        p.state = PinState.FALLEN
        p.radius = GameConfig.PIN_FALLEN_R
        p.fallAngle = angleOf(dx, dy, 0f)
        knockedThisRoll++
        listener?.onKnock(p)
    }

    /** 테스트·건너뛰기용: 서 있는 핀 중 앞 번호부터 n 개를 쓰러뜨리고 굴림을 끝낸 상태로 (물리 없이) */
    fun forceKnock(n: Int) {
        var left = n
        knockedThisRoll = 0
        gutterBall = false
        for (p in pins) {
            if (left <= 0) break
            if (!p.standing) continue
            knock(p, 1f, (p.number % 3 - 1).toFloat() * 0.4f)
            left--
        }
        ball.active = false
        rolling = false
    }

    companion object {
        private const val TWO_PI = 6.2831855f

        /** 핀 번호 순서(0..9)의 줄·칸: 1 / 2 3 / 4 5 6 / 7 8 9 10 (번호가 작은 쪽이 던지는 사람 왼쪽 = 화면 위) */
        private val ROW = intArrayOf(0, 1, 1, 2, 2, 2, 3, 3, 3, 3)
        private val COL = floatArrayOf(0f, -0.5f, 0.5f, -1f, 0f, 1f, -1.5f, -0.5f, 0.5f, 1.5f)

        fun spotX(i: Int): Float = GameConfig.HEAD_X + ROW[i] * GameConfig.ROW_DX
        fun spotY(i: Int): Float = GameConfig.LANE_W / 2f + COL[i] * GameConfig.PIN_SPACING

        /** 중심이 레인 바깥(거터 쪽)인지 = 거터에 빠짐 */
        fun isOffLane(y: Float): Boolean = y < 0f || y > GameConfig.LANE_W

        /** 제자리에서 (dx, dy) 만큼 밀린 핀이 쓰러진 것인지 */
        fun displaced(dx: Float, dy: Float): Boolean = sqrt(dx * dx + dy * dy) > GameConfig.KNOCK_DIST

        /** 이 세기로 부딪힌 핀이 쓰러지는지 */
        fun knocks(hitSpeed: Float): Boolean = hitSpeed >= GameConfig.KNOCK_SPEED

        private fun angleOf(dx: Float, dy: Float, fallback: Float): Float =
            if (dx == 0f && dy == 0f) fallback else atan2(dy, dx)
    }
}
