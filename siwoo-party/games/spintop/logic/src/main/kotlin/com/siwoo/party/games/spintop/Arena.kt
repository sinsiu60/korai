package com.siwoo.party.games.spintop

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.FixedStep
import com.siwoo.party.common.physics.PhysicsMath
import com.siwoo.party.common.physics.Wall
import com.siwoo.party.common.physics.World
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 팽이 하나 = 공통 물리 Body (반지름 TOP_R, 질량 1) + 주인·회전력·안정성·쓰러짐.
 * 회전력(power 0..1)은 이 게임이 직접 관리한다 (공통 Body 의 회전 감속은 끄고, Body.spin 은 그림용 무늬 속도로만 씀).
 */
class Top(id: Int, val owner: Int) : Body(id, GameConfig.TOP_R) {
    /** 지금 회전력 0..1 (0 이 되면 쓰러짐) */
    var power = 0f
    /** 안정성 0..1 (높을수록 충돌에 덜 깎임) */
    var stability = 0f
    /** 쓰러졌는지 */
    var fallen = false
    /** 쓰러진 단계 (−1 = 아직 도는 중) */
    var fallStep = -1L
    /** 쓰러질 때 넘어가는 방향 (라디안, 그림용) */
    var tipAngle = 0f
    /** 이번 판에 던져졌는지 */
    var launched = false

    // 비틀거림 (무작위 휘청 방향, 단계 수)
    internal var wobX = 0f
    internal var wobY = 0f
    internal var wobLeft = 0

    /** 도는 중 (던져졌고 아직 안 쓰러짐) */
    val spinning: Boolean get() = launched && !fallen

    init {
        restitution = GameConfig.TOP_E
        // 공통 물리의 회전 감속은 쓰지 않음 (회전력은 power 로 직접)
        spinFriction = 0f
        spinDamping = 0f
        active = false
    }
}

/**
 * 둥근 경기장 = 공통 물리 World (테두리 = 벽 조각 RIM_SEGMENTS 개) + 팽이마다의 힘·회전력.
 * 공통 World 에는 단계마다 힘을 넣는 자리가 없으므로 (컬링 Sheet 와 같은 방식) 고정 1/60초 단계를 여기서 직접 세어
 * [step] 마다 "회전력 기본 감소 → 가운데로 기운 경사 → 회전 휘어짐 → 비틀거림 → World.step() → 쓰러짐 판정 → 끝 판정" 순서로 진행한다.
 * 충돌(onCollide/onWall)에서 회전력이 깎이고, 도는 팽이는 상대를 옆으로 튕긴다.
 * 판이 끝나면(도는 팽이 ≤ 1 또는 제한 시간) [running] = false → [advance] 는 더 진행하지 않는다 (화면 루프도 멈춤).
 */
class Arena(private val random: Random, val capMs: Long = GameConfig.BATTLE_CAP_MS) {

    interface Listener {
        /** 팽이끼리 (x, y) 에서 speed 로 부딪힘 */
        fun onHit(a: Top, b: Top, x: Float, y: Float, speed: Float) {}
        /** 테두리에 부딪힘 */
        fun onRim(top: Top, x: Float, y: Float, speed: Float) {}
        /** 회전력 0 → 쓰러짐 (물리에서 빠짐) */
        fun onFall(top: Top) {}
        /** 대결 끝 (timeUp = 제한 시간으로 끝남) */
        fun onEnd(timeUp: Boolean) {}
    }

    var listener: Listener? = null

    var tops: List<Top> = emptyList()
        private set

    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        substeps = GameConfig.SUBSTEPS
        // 테두리: 원을 따라 벽 조각
        val n = GameConfig.RIM_SEGMENTS
        val r = GameConfig.ARENA_R
        for (k in 0 until n) {
            val a0 = (2.0 * PI * k / n).toFloat()
            val a1 = (2.0 * PI * (k + 1) / n).toFloat()
            addWall(cos(a0) * r, sin(a0) * r, cos(a1) * r, sin(a1) * r, GameConfig.RIM_E)
        }
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) = hit(a as Top, b as Top, x, y, speed)
            override fun onWall(body: Body, wall: Wall, x: Float, y: Float, speed: Float) = rim(body as Top, x, y, speed)
        }
    }

    private val clock = FixedStep(GameConfig.MAX_FRAME_MS)
    private val capSteps = capMs * GameConfig.STEPS_PER_SEC / 1000L

    /** 대결 진행 중 (던진 뒤 ~ 끝나기 전) */
    var running = false
        private set
    /** 끝남 */
    var ended = false
        private set
    /** 제한 시간으로 끝남 */
    var timeUp = false
        private set
    /** 이번 대결 진행 단계 수 */
    var steps = 0L
        private set

    /** 이번 대결 지난 시간 (ms, 규칙 시간) */
    val elapsedMs: Long get() = steps * 1000L / GameConfig.STEPS_PER_SEC

    /** 아직 도는 팽이 수 */
    val spinningCount: Int
        get() {
            var c = 0
            for (i in tops.indices) if (tops[i].spinning) c++
            return c
        }

    /** 출발 자리 i (n 명) — 왼쪽부터 시계 방향으로 고르게 */
    fun startAngle(i: Int, n: Int): Float = (PI + 2.0 * PI * i / n).toFloat()

    /** 팽이들을 출발 자리에 (아직 안 던짐) — 준비 단계에서 그림용 */
    fun setTops(list: List<Top>) {
        world.bodies.clear()
        for (t in list) {
            t.active = false
            t.launched = false
            t.fallen = false
            t.fallStep = -1L
            t.power = 0f
            t.stop()
            t.angle = 0f
            val a = startAngle(t.id, list.size)
            t.x = cos(a) * GameConfig.LAUNCH_DIST
            t.y = sin(a) * GameConfig.LAUNCH_DIST
            world.addBody(t)
        }
        tops = list
        running = false
        ended = false
        timeUp = false
        steps = 0L
        clock.reset()
    }

    /** 모두 동시에 던짐: 회전력·안정성을 넣고 출발 자리에서 가운데 쪽(± 무작위 각도)으로 */
    fun launch(powers: FloatArray, stabilities: FloatArray) {
        val n = tops.size
        for (t in tops) {
            val i = t.id
            val a = startAngle(i, n)
            t.place(cos(a) * GameConfig.LAUNCH_DIST, sin(a) * GameConfig.LAUNCH_DIST)
            t.power = powers[i].coerceIn(0f, 1f)
            t.stability = stabilities[i].coerceIn(0f, 1f)
            t.launched = true
            t.fallen = false
            t.fallStep = -1L
            t.active = true
            t.wobLeft = 0
            val spread = Math.toRadians(GameConfig.LAUNCH_SPREAD_DEG.toDouble()).toFloat()
            val dir = a + PI.toFloat() + (random.nextFloat() * 2f - 1f) * spread
            val sp = GameConfig.LAUNCH_SPEED_MIN + (GameConfig.LAUNCH_SPEED_MAX - GameConfig.LAUNCH_SPEED_MIN) * t.power
            t.setVelocity(cos(dir) * sp, sin(dir) * sp)
            t.spin = t.power * GameConfig.VISUAL_SPIN
            t.tipAngle = random.nextFloat() * 2f * PI.toFloat()
        }
        running = true
        ended = false
        timeUp = false
        steps = 0L
        clock.reset()
    }

    /** dtMs 만큼 시간을 흘리고 모인 만큼 고정 단계 진행. 반환: 아직 진행 중이면 true */
    fun advance(dtMs: Long): Boolean {
        if (!running) return false
        val n = clock.add(dtMs)
        for (i in 0 until n) {
            step()
            if (!running) break
        }
        return running
    }

    /** 고정 1/60초 한 단계 */
    fun step() {
        if (!running) return
        val dt = GameConfig.STEP_SEC
        steps++
        for (i in tops.indices) {
            val t = tops[i]
            if (!t.spinning) continue
            // 회전력 기본 감소
            t.power -= SpinRules.baseDecay(dt)
            // 가운데로 기운 경사 (가운데 가까이선 평평)
            val d = sqrt(t.x * t.x + t.y * t.y)
            if (d > 1e-4f) {
                val acc = GameConfig.SLOPE_ACCEL * (d / GameConfig.SLOPE_FLAT).coerceAtMost(1f)
                t.vx -= t.x / d * acc * dt
                t.vy -= t.y / d * acc * dt
            }
            // 도는 팽이는 진행 방향이 조금씩 휜다
            if (t.moving && t.power > 0f) PhysicsMath.rotateVelocity(t, GameConfig.DRIFT_TURN * t.power * dt)
            // 비틀거림: 회전력이 낮으면 무작위로 휘청
            if (t.power < GameConfig.WOBBLE_LEVEL) {
                if (t.wobLeft <= 0) {
                    val wa = random.nextFloat() * 2f * PI.toFloat()
                    t.wobX = cos(wa)
                    t.wobY = sin(wa)
                    t.wobLeft = GameConfig.WOBBLE_STEPS
                }
                t.wobLeft--
                val k = GameConfig.WOBBLE_ACCEL * (1f - t.power.coerceAtLeast(0f) / GameConfig.WOBBLE_LEVEL)
                t.vx += t.wobX * k * dt
                t.vy += t.wobY * k * dt
            }
        }
        world.step()
        // 쓰러짐 판정 (이 단계에 0 이 된 팽이는 모두 같은 단계 → 같은 순위)
        for (i in tops.indices) {
            val t = tops[i]
            if (t.spinning) {
                if (t.power <= 0f) fall(t) else t.spin = t.power * GameConfig.VISUAL_SPIN
            }
        }
        if (tops.size >= 2 && spinningCount <= 1) finish(false)
        else if (steps >= capSteps) finish(true)
    }

    private fun fall(t: Top) {
        t.power = 0f
        t.fallen = true
        t.fallStep = steps
        t.active = false
        t.stop()
        listener?.onFall(t)
    }

    private fun finish(byTime: Boolean) {
        running = false
        ended = true
        timeUp = byTime
        for (t in tops) if (t.spinning) t.setVelocity(0f, 0f)
        listener?.onEnd(byTime)
    }

    /** 팽이끼리 충돌: 둘 다 회전력 감소 + 도는 팽이가 상대를 접선 방향으로 튕김 */
    private fun hit(a: Top, b: Top, x: Float, y: Float, speed: Float) {
        val pa = a.power
        val pb = b.power
        a.power -= SpinRules.collisionLoss(speed, a.stability, own = pa, other = pb)
        b.power -= SpinRules.collisionLoss(speed, b.stability, own = pb, other = pa)
        // 접선 (a→b 법선을 시계 방향으로 90°): 시계 방향으로 도는 a 의 표면이 b 를 미는 방향
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 1e-4f) {
            val tx = -dy / dist
            val ty = dx / dist
            val f = GameConfig.SPIN_PUSH * (speed / GameConfig.PUSH_REF_SPEED).coerceIn(0.2f, 1f)
            val kb = f * pa.coerceAtLeast(0f)
            val ka = f * pb.coerceAtLeast(0f)
            b.vx += tx * kb
            b.vy += ty * kb
            a.vx -= tx * ka
            a.vy -= ty * ka
        }
        listener?.onHit(a, b, x, y, speed)
    }

    private fun rim(t: Top, x: Float, y: Float, speed: Float) {
        t.power -= SpinRules.collisionLoss(speed, t.stability, rim = true)
        listener?.onRim(t, x, y, speed)
    }

    /** 판 순위 (쓰러진 순서, 남은 팽이는 회전력 순) */
    fun ranks(): IntArray {
        val n = tops.size
        val falls = LongArray(n)
        val spins = FloatArray(n)
        for (t in tops) {
            falls[t.id] = t.fallStep
            spins[t.id] = if (t.fallen) 0f else t.power
        }
        return SpinRules.roundRanks(falls, spins)
    }
}
