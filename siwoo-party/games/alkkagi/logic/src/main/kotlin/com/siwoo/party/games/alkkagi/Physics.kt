package com.siwoo.party.games.alkkagi

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.FixedStep
import com.siwoo.party.common.physics.PhysicsMath
import com.siwoo.party.common.physics.World

/** 알 상태: 판 위 → (중심이 판 밖으로) 떨어지는 중 → 사라짐 */
enum class StoneState { ON_BOARD, FALLING, GONE }

/**
 * 알 하나 (판 단위 좌표·속도). 공통 물리 Body (반지름 STONE_R, 질량 1, 완전 탄성) 에 알까기 상태를 더한 것.
 * 판 위(ON_BOARD)일 때만 물리 세계가 다룬다 (state 를 바꾸면 Body.active 도 따라 바뀜).
 */
class Stone(id: Int, val owner: Int) : Body(id, GameConfig.STONE_R) {
    var state = StoneState.ON_BOARD
        set(value) {
            field = value
            active = value == StoneState.ON_BOARD
        }
    /** 떨어지는 진행 0..1 (1 이면 사라짐) — 화면은 이만큼 작아지게 그린다 */
    var fall = 0f

    val onBoard: Boolean get() = state == StoneState.ON_BOARD

    init {
        tag = owner
    }
}

/** 물리 기본 동작 (공통 물리 PhysicsMath 를 알까기 수치로 — 단위 테스트 대상) */
object Physics {

    /** 중심이 판(0..BOARD_W × 0..BOARD_H) 밖이면 true. 몸이 걸쳐 있어도 중심이 안이면 아직 판 위 */
    fun isOffBoard(x: Float, y: Float): Boolean =
        x < 0f || x > GameConfig.BOARD_W || y < 0f || y > GameConfig.BOARD_H

    /**
     * 두 알이 겹치면 겹친 만큼 반씩 밀어내고, 서로 다가오던 중이면 탄성 충돌(질량 같음)시킨다.
     * 질량이 같은 탄성 충돌 = 중심을 잇는 방향(법선) 속도 성분을 서로 맞바꾼다 → 운동량·에너지 보존.
     * 반환: 닿지 않았으면 −1, 겹침만 풀었으면 0, 부딪혔으면 법선 방향 상대 속도(> 0, 충돌 세기).
     */
    fun collide(a: Stone, b: Stone): Float = PhysicsMath.collideCircles(a, b)

    /** 마찰(일정 감속) + 공기 저항으로 dt 초만큼 느리게. STOP_SPEED 아래면 완전히 멈춘다 */
    fun applyFriction(s: Stone, dt: Float) =
        PhysicsMath.applyFriction(s, GameConfig.FRICTION, GameConfig.DRAG, GameConfig.STOP_SPEED, dt)
}

/**
 * 알까기 판 위의 물리 세계 = 공통 물리 World (벽 없음, 경계 = 판) + 떨어지는 알 연출.
 * 고정 시간 단계(1/60초)로만 진행한다. advance(ms) 는 받은 시간을 정수 틱으로 모아(1ms = 60틱, 한 단계 = 1000틱)
 * 단계 수만큼 step() — 프레임 시간을 어떻게 쪼개도(각 프레임 ≤ MAX_FRAME_MS) 같은 결과가 나온다.
 */
class Table(val stones: Array<Stone>, fallMs: Long = GameConfig.FALL_MS) {

    /** 판에서 일어나는 일 (소리·연출용) */
    interface Listener {
        /** (x, y) 에서 세기 speed 로 부딪힘 */
        fun onCollide(x: Float, y: Float, speed: Float)
        /** 중심이 판 밖으로 나가 떨어지기 시작 */
        fun onFall(stone: Stone)
    }

    var listener: Listener? = null

    /** 판 위의 알을 다루는 공통 물리 세계 (중심이 판 밖으로 나가면 onOut → 떨어지기 시작) */
    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        passes = GameConfig.COLLISION_PASSES
        setBounds(0f, 0f, GameConfig.BOARD_W, GameConfig.BOARD_H)
        for (s in stones) addBody(s)
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {
                this@Table.listener?.onCollide(x, y, speed)
            }

            override fun onOut(body: Body) {
                val s = body as Stone
                s.state = StoneState.FALLING
                s.fall = 0f
                this@Table.listener?.onFall(s)
            }
        }
    }

    /** 한 단계당 떨어짐 진행 (fallMs 가 0 이하면 바로 사라짐) */
    private val fallPerStep = if (fallMs <= 0L) 1f else 1000f / GameConfig.STEPS_PER_SEC / fallMs
    private val clock = FixedStep(GameConfig.MAX_FRAME_MS)

    /** 지금까지 진행한 단계 수 */
    val steps: Long get() = world.steps

    /** 판 위의 모든 알이 멈췄고 떨어지는 중인 알도 없음 */
    val settled: Boolean
        get() {
            for (s in stones) if (s.state == StoneState.FALLING) return false
            return world.settled
        }

    /** dtMs 만큼 시간을 흘리고, 모인 만큼 고정 단계를 진행한다. 진행한 단계 수를 돌려준다 */
    fun advance(dtMs: Long): Int {
        val n = clock.add(dtMs)
        for (i in 0 until n) step()
        return n
    }

    /** 남은 자투리 시간 버리기 (새로 튕길 때) */
    fun resetClock() {
        clock.reset()
    }

    /** 고정 1/60초 한 단계: 떨어지는 알 미끄러짐·줄어듦 → 공통 물리 한 단계 (이동 → 충돌 → 마찰·정지 → 판 밖 판정) */
    fun step() {
        val dt = GameConfig.STEP_SEC
        for (s in stones) {
            if (s.state != StoneState.FALLING) continue
            s.x += s.vx * dt
            s.y += s.vy * dt
            val k = (1f - GameConfig.FALL_SLOW * dt).coerceAtLeast(0f)
            s.vx *= k
            s.vy *= k
            s.fall = (s.fall + fallPerStep).coerceAtMost(1f)
            if (s.fall >= 1f) {
                s.state = StoneState.GONE
                s.vx = 0f
                s.vy = 0f
            }
        }
        world.step()
    }
}
