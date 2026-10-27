package com.siwoo.party.games.minigolf

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.FixedStep
import com.siwoo.party.common.physics.Hole
import com.siwoo.party.common.physics.PhysicsMath
import com.siwoo.party.common.physics.Wall
import com.siwoo.party.common.physics.World
import com.siwoo.party.common.physics.Zone
import kotlin.math.sqrt

/** 한 번 친 공이 어떻게 끝났는지 */
enum class Outcome {
    /** 아직 구르는 중 (또는 치기 전) */
    NONE,
    /** 컵에 들어감 */
    CUP,
    /** 물에 빠짐 (또는 혹시라도 코스 밖으로 나감) */
    WATER,
    /** 코스 위에 멈춤 */
    STOPPED,
}

/**
 * 홀 하나의 물리 = 공통 물리 World (테두리·블록 벽, 모래·물·경사 구역, 컵) + 움직이는 장애물 + 공 하나.
 * 다른 사람 공은 코스에 없다 (한 사람씩 홀을 끝까지 치므로 공끼리 부딪히지 않음).
 *
 * 시간: 고정 1/60초 단계로만 진행 (FixedStep). **코스 시계**(장애물 위치를 정하는 시간)는 공이 구르는 단계에서만 흐른다 →
 * 공이 멈추면 장애물도 그 자리에 멈추고, 화면 프레임 루프도 완전히 멈출 수 있다 (멈춘 화면에서 조준할 때 보이는 자리 그대로).
 * 장애물은 단계마다 다음 자리까지의 속도(vx, vy)를 벽에 넣어 World 가 옮기게 한다 → 부딪힌 공이 장애물 속도를 받는다.
 */
class CourseSim(val hole: HoleDef) {

    /** 구르는 동안 생기는 일 (소리·연출용) */
    interface Listener {
        fun onWall(x: Float, y: Float, speed: Float) {}
        /** 컵 위를 빠르게 지나감 (가장자리에 걸려 꺾임) */
        fun onLip(x: Float, y: Float) {}
        /** 공이 끝남 (outcome = CUP / WATER / STOPPED) */
        fun onEnd(outcome: Outcome) {}
    }

    var listener: Listener? = null

    val ball = Body(0, GameConfig.BALL_R, 1f).apply {
        restitution = GameConfig.BALL_E
    }
    val cup = Hole(hole.cupX, hole.cupY, GameConfig.CUP_R, GameConfig.CUP_CAPTURE_SPEED)

    /** 움직이는 장애물마다 벽 4개 (위·오른쪽·아래·왼쪽) */
    private val sliderWalls = Array(hole.sliders.size) { arrayOfNulls<Wall>(4) }
    /** 장애물마다 지금 옮겨진 양 */
    private val sliderOffX = FloatArray(hole.sliders.size)
    private val sliderOffY = FloatArray(hole.sliders.size)

    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        substeps = GameConfig.SUBSTEPS
        // 혹시라도 테두리를 뚫으면 코스 상자 밖 → 물처럼 처리
        setBounds(0f, 0f, GameConfig.COURSE_W, GameConfig.COURSE_H)
        val o = hole.outline
        val n = hole.corners
        for (i in 0 until n) {
            val j = (i + 1) % n
            addWall(o[2 * i], o[2 * i + 1], o[2 * j], o[2 * j + 1], GameConfig.WALL_E)
        }
        for (b in hole.blocks) addBox(b.left, b.top, b.right, b.bottom, GameConfig.WALL_E)
        for (a in hole.areas) {
            val z = if (a.circle) Zone.circle(a.box.centerX, a.box.centerY, a.box.width / 2f)
            else Zone.rect(a.box.left, a.box.top, a.box.right, a.box.bottom)
            when (a.kind) {
                AreaKind.SAND -> z.friction = GameConfig.SAND_FRICTION
                AreaKind.WATER -> z.trap = true
                AreaKind.SLOPE -> {
                    z.ax = a.ax
                    z.ay = a.ay
                }
            }
            addZone(z)
        }
        for ((k, s) in hole.sliders.withIndex()) {
            val b = s.box
            val walls = sliderWalls[k]
            walls[0] = addWall(b.left, b.top, b.right, b.top, GameConfig.WALL_E)
            walls[1] = addWall(b.right, b.top, b.right, b.bottom, GameConfig.WALL_E)
            walls[2] = addWall(b.right, b.bottom, b.left, b.bottom, GameConfig.WALL_E)
            walls[3] = addWall(b.left, b.bottom, b.left, b.top, GameConfig.WALL_E)
        }
        addHole(cup)
        addBody(ball)
    }

    private val clock = FixedStep()

    /** 코스 시계 (공이 구른 단계 수) — 장애물 위치를 정한다 */
    var courseSteps = 0L
        private set

    /** 공이 구르는 중 */
    var rolling = false
        private set

    /** 마지막으로 친 공의 결과 */
    var outcome = Outcome.NONE
        private set

    /** 이번에 친 뒤 진행한 단계 수 */
    var rollSteps = 0L
        private set

    /** 컵 위를 지나는 중 (가장자리 꺾임을 한 번만) */
    private var overCup = false

    init {
        world.listener = object : World.Listener {
            override fun onWall(body: Body, wall: Wall, x: Float, y: Float, speed: Float) {
                listener?.onWall(x, y, speed)
            }

            override fun onCaptured(body: Body, hole: Hole) = finish(Outcome.CUP)
            override fun onTrapped(body: Body, zone: Zone) = finish(Outcome.WATER)
            override fun onOut(body: Body) = finish(Outcome.WATER)
            override fun onSettled() = finish(Outcome.STOPPED)
        }
        placeBall(hole.teeX, hole.teeY)
        snapSliders()
    }

    private fun finish(o: Outcome) {
        if (!rolling) return
        rolling = false
        outcome = o
        ball.vx = 0f
        ball.vy = 0f
        listener?.onEnd(o)
    }

    /** 공을 (x, y) 에 멈춰 놓기 (티·물에 빠진 뒤 원래 자리) */
    fun placeBall(x: Float, y: Float) {
        ball.place(x, y)
        ball.active = true
        rolling = false
        overCup = false
    }

    /** 코스 시계를 처음으로 (새 사람이 홀을 시작할 때 — 모두 같은 장애물 위치에서 출발) */
    fun resetCourseClock() = setCourseClock(0L)

    /** 코스 시계를 steps 단계로 (장애물이 그 자리로) */
    fun setCourseClock(steps: Long) {
        courseSteps = steps
        snapSliders()
    }

    /** 공 치기 (속도 단위/초). 구르는 중이면 무시 */
    fun shoot(vx: Float, vy: Float) {
        if (rolling) return
        ball.active = true
        ball.setVelocity(vx, vy)
        outcome = Outcome.NONE
        rolling = vx != 0f || vy != 0f
        rollSteps = 0L
        overCup = false
        clock.reset()
        world.resetClock()
        if (!rolling) {
            outcome = Outcome.STOPPED
            listener?.onEnd(outcome)
        }
    }

    /** dtMs 만큼 진행 (구르는 동안만). 아직 구르면 true */
    fun advance(dtMs: Long): Boolean {
        if (!rolling) {
            clock.reset()
            return false
        }
        val n = clock.add(dtMs)
        for (i in 0 until n) {
            if (!rolling) break
            step()
        }
        return rolling
    }

    /** 고정 1/60초 한 단계: 장애물 속도 정하기 → World 한 단계 → 장애물 자리 맞춤 → 컵 가장자리 → 안전장치 */
    fun step() {
        if (!rolling) return
        val dt = GameConfig.STEP_SEC
        val tNext = (courseSteps + 1) * dt
        for (k in hole.sliders.indices) {
            val s = hole.sliders[k]
            val vx = (s.offsetX(tNext) - sliderOffX[k]) / dt
            val vy = (s.offsetY(tNext) - sliderOffY[k]) / dt
            for (w in sliderWalls[k]) {
                w!!.vx = vx
                w.vy = vy
            }
        }
        world.step()
        courseSteps++
        rollSteps++
        snapSliders()
        if (!rolling) return
        lip()
        if (rolling && rollSteps * 1000L / GameConfig.STEPS_PER_SEC >= GameConfig.MAX_ROLL_MS) {
            ball.stop()
            finish(Outcome.STOPPED)
        }
    }

    /** 장애물을 코스 시계 자리에 정확히 놓고 속도는 0 (멈춘 동안 움직이지 않게) */
    private fun snapSliders() {
        val t = courseSteps * GameConfig.STEP_SEC
        for (k in hole.sliders.indices) {
            val s = hole.sliders[k]
            val ox = s.offsetX(t)
            val oy = s.offsetY(t)
            sliderOffX[k] = ox
            sliderOffY[k] = oy
            val b = s.box
            val l = b.left + ox
            val r = b.right + ox
            val tp = b.top + oy
            val bt = b.bottom + oy
            val w = sliderWalls[k]
            set(w[0]!!, l, tp, r, tp)
            set(w[1]!!, r, tp, r, bt)
            set(w[2]!!, r, bt, l, bt)
            set(w[3]!!, l, bt, l, tp)
        }
    }

    private fun set(w: Wall, x1: Float, y1: Float, x2: Float, y2: Float) {
        w.x1 = x1
        w.y1 = y1
        w.x2 = x2
        w.y2 = y2
        w.vx = 0f
        w.vy = 0f
    }

    /** 컵 위를 빠르게 지나가면 가장자리에 걸려 살짝 꺾인다 (중심에서 벗어난 쪽으로) */
    private fun lip() {
        val dx = ball.x - cup.x
        val dy = ball.y - cup.y
        val inside = dx * dx + dy * dy <= cup.radius * cup.radius
        if (!inside) {
            overCup = false
            return
        }
        if (overCup) return
        overCup = true
        val sp = ball.speed
        if (sp <= 0f) return
        // 진행 방향 기준 옆으로 벗어난 정도 (−1..1)
        val side = ((ball.vx * dy - ball.vy * dx) / sp / cup.radius).coerceIn(-1f, 1f)
        PhysicsMath.rotateVelocity(ball, side * GameConfig.CUP_LIP_ANGLE)
        listener?.onLip(ball.x, ball.y)
    }

    /** 장애물 k 의 지금 자리 (옮겨진 양) */
    fun sliderOffsetX(k: Int): Float = sliderOffX[k]
    fun sliderOffsetY(k: Int): Float = sliderOffY[k]

    /** 지금 공이 있는 바닥 종류 (없으면 null = 잔디) */
    fun areaUnderBall(): AreaKind? = hole.areaAt(ball.x, ball.y)?.kind

    companion object {
        /** 새총 힘 0..1 → 처음 속도 */
        fun speedFor(power: Float): Float = power.coerceIn(0f, 1f) * GameConfig.MAX_SPEED

        fun length(x: Float, y: Float): Float = sqrt(x * x + y * y)
    }
}
