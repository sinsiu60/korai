package com.siwoo.party.games.curling

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.FixedStep
import com.siwoo.party.common.physics.PhysicsMath
import com.siwoo.party.common.physics.World
import kotlin.math.cos
import kotlin.math.sin

/** 스톤 상태: 아직 안 던짐 → 시트 위 → (밖으로 나가 / 호그 라인을 못 넘어) 사라지는 중 → 사라짐 */
enum class StoneState { WAITING, IN_PLAY, FADING, GONE }

/**
 * 컬링 스톤 하나 = 공통 물리 Body (반지름 STONE_R, 질량 1) + 주인·편·상태.
 * 시트 위(IN_PLAY)일 때만 물리 세계가 다룬다 (state 를 바꾸면 Body.active 도 따라 바뀜).
 */
class CurlStone(id: Int, val owner: Int, val side: Int) : Body(id, GameConfig.STONE_R) {
    var state = StoneState.WAITING
        set(value) {
            field = value
            active = value == StoneState.IN_PLAY
        }
    /** 사라지는 진행 0..1 (화면은 이만큼 흐리게) */
    var fade = 0f
    /** 던진 뒤 다른 스톤에 부딪혔는지 (부딪히면 더는 휘지 않음) */
    var hit = false
    /** 손잡이 각도 (그림용, 라디안) */
    var handle = 0f

    val inPlay: Boolean get() = state == StoneState.IN_PLAY

    init {
        tag = side
        restitution = GameConfig.STONE_E
        active = false
    }
}

/**
 * 컬링 시트 위의 물리 = 공통 물리 World (벽 없음, 경계 = 옆줄·백 라인) + 던진 스톤의 컬(휘어짐)·스위핑 + 사라지는 연출.
 * 공통 World 에는 단계마다 힘을 넣는 자리가 없으므로, 고정 1/60초 단계를 여기서 직접 세어
 * [step] 마다 "스위핑 감소 → 컬 회전 → World.step()" 순서로 진행한다 (알까기 Table 과 같은 방식).
 */
class Sheet(fadeMs: Long = GameConfig.FADE_MS) {

    interface Listener {
        fun onCollide(a: CurlStone, b: CurlStone, x: Float, y: Float, speed: Float)
        /** 시트 밖(옆줄·백 라인)으로 나감 → 사라지기 시작 */
        fun onOut(stone: CurlStone)
    }

    var listener: Listener? = null

    var stones: Array<CurlStone> = emptyArray()
        private set

    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        // 옆줄에 닿거나(중심이 옆줄에서 반지름 안) 백 라인을 완전히 넘으면 아웃
        setBounds(0f, GameConfig.STONE_R, GameConfig.BACK_X + GameConfig.STONE_R, GameConfig.SHEET_W - GameConfig.STONE_R)
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {
                val sa = a as CurlStone
                val sb = b as CurlStone
                val t = thrown
                if (t != null && (sa === t || sb === t)) t.hit = true
                this@Sheet.listener?.onCollide(sa, sb, x, y, speed)
            }

            override fun onOut(body: Body) {
                val s = body as CurlStone
                s.state = StoneState.FADING
                s.fade = 0f
                this@Sheet.listener?.onOut(s)
            }
        }
    }

    /** 지금 던진(움직이는) 스톤 */
    var thrown: CurlStone? = null
        private set
    /** 던진 스톤의 휘는 방향: +1 = 오른쪽(던지는 사람이 보기에, 화면 아래쪽), −1 = 왼쪽(화면 위쪽) */
    var curlDir = 1
        private set
    /** 스위핑 세기 0..1 (연타로 오르고 시간이 지나면 줄어듦) */
    var sweep = 0f
        private set
    /** 이번 던지기에서 스위핑 연타 수 */
    var sweepTaps = 0
        private set

    private val fadePerStep = if (fadeMs <= 0L) 1f else 1000f / GameConfig.STEPS_PER_SEC / fadeMs
    private val clock = FixedStep(GameConfig.MAX_FRAME_MS)

    /** 지금까지 진행한 단계 수 */
    val steps: Long get() = world.steps

    /** 이번 엔드의 스톤을 세계에 넣는다 (지난 엔드 스톤은 뺌) */
    fun setStones(list: Array<CurlStone>) {
        world.bodies.clear()
        for (s in list) world.addBody(s)
        stones = list
        thrown = null
        sweep = 0f
        sweepTaps = 0
        clock.reset()
    }

    /** 스톤을 핵에서 angle(라디안, 0 = 하우스 쪽 정면, 양수 = 화면 아래쪽) 방향, speed 로 던짐 */
    fun launch(s: CurlStone, angle: Float, speed: Float, curl: Int) {
        s.place(GameConfig.HACK_X, GameConfig.HACK_Y)
        s.state = StoneState.IN_PLAY
        s.hit = false
        s.fade = 0f
        s.friction = 1f
        s.setVelocity(cos(angle) * speed, sin(angle) * speed)
        thrown = s
        curlDir = if (curl < 0) -1 else 1
        sweep = 0f
        sweepTaps = 0
        clock.reset()
    }

    /** 던진 스톤이 아직 움직임 (스위핑할 수 있음) */
    val sweepable: Boolean
        get() {
            val t = thrown ?: return false
            return t.inPlay && t.moving
        }

    /** 스위핑 한 번 (연타). 던진 스톤이 움직이는 중일 때만 → true */
    fun sweepTap(): Boolean {
        if (!sweepable) return false
        sweep = minOf(1f, sweep + GameConfig.SWEEP_PER_TAP)
        sweepTaps++
        return true
    }

    /** 던지기 끝 (다 멈춤) — 던진 스톤 마찰을 처음대로 */
    fun endThrow() {
        thrown?.friction = 1f
        thrown = null
        sweep = 0f
    }

    /** 시트 위 스톤이 모두 멈췄고 사라지는 중인 스톤도 없음 */
    val settled: Boolean
        get() {
            for (s in stones) if (s.state == StoneState.FADING) return false
            return world.settled
        }

    /** dtMs 만큼 시간을 흘리고 모인 만큼 고정 단계를 진행 (진행한 단계 수) */
    fun advance(dtMs: Long): Int {
        val n = clock.add(dtMs)
        for (i in 0 until n) step()
        return n
    }

    /** 고정 1/60초 한 단계: 스위핑 감소 → 던진 스톤 컬·마찰 → 사라지는 스톤 → 공통 물리 한 단계 */
    fun step() {
        val dt = GameConfig.STEP_SEC
        if (sweep > 0f) sweep = maxOf(0f, sweep - GameConfig.SWEEP_DECAY * dt)
        val t = thrown
        if (t != null && t.inPlay && t.moving) {
            // 스위핑: 마찰이 줄어 더 멀리
            t.friction = 1f - GameConfig.SWEEP_FRICTION_CUT * sweep
            // 컬: 진행 방향 옆으로 가속 (느릴수록 크게, 스위핑하면 덜) → 빠르기는 그대로 방향만 돌림
            if (!t.hit) {
                val v = t.speed
                val lat = curlAccel(v) * (1f - GameConfig.SWEEP_CURL_CUT * sweep)
                if (v > 0f) PhysicsMath.rotateVelocity(t, curlDir * lat * dt / v)
            }
            t.handle += curlDir * GameConfig.HANDLE_SPIN * dt
        }
        for (s in stones) {
            if (s.state != StoneState.FADING) continue
            s.x += s.vx * dt
            s.y += s.vy * dt
            val k = (1f - GameConfig.FADE_SLOW * dt).coerceAtLeast(0f)
            s.vx *= k
            s.vy *= k
            s.fade = (s.fade + fadePerStep).coerceAtMost(1f)
            if (s.fade >= 1f) {
                s.state = StoneState.GONE
                s.vx = 0f
                s.vy = 0f
            }
        }
        world.step()
    }

    /** 사라지게 하기 (호그 라인 등) — 제자리에서 흐려진다 */
    fun fadeOut(s: CurlStone) {
        s.state = StoneState.FADING
        s.fade = 0f
        s.vx = 0f
        s.vy = 0f
    }

    companion object {
        /** 빠르기 v 일 때 컬 옆 가속도 (스위핑 없을 때) — 느릴수록 크다 */
        fun curlAccel(v: Float): Float = GameConfig.CURL_K / (v + GameConfig.CURL_V0)
    }
}
