package com.siwoo.party.games.alkkagi

import kotlin.math.sqrt

/** 알 상태: 판 위 → (중심이 판 밖으로) 떨어지는 중 → 사라짐 */
enum class StoneState { ON_BOARD, FALLING, GONE }

/** 알 하나 (판 단위 좌표·속도). 질량은 모두 같다 */
class Stone(val id: Int, val owner: Int) {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var state = StoneState.ON_BOARD
    /** 떨어지는 진행 0..1 (1 이면 사라짐) — 화면은 이만큼 작아지게 그린다 */
    var fall = 0f

    val onBoard: Boolean get() = state == StoneState.ON_BOARD
    val moving: Boolean get() = vx != 0f || vy != 0f
    val speed: Float get() = sqrt(vx * vx + vy * vy)
}

/** 물리 기본 동작 (순수 함수 — 단위 테스트 대상) */
object Physics {

    /** 중심이 판(0..BOARD_W × 0..BOARD_H) 밖이면 true. 몸이 걸쳐 있어도 중심이 안이면 아직 판 위 */
    fun isOffBoard(x: Float, y: Float): Boolean =
        x < 0f || x > GameConfig.BOARD_W || y < 0f || y > GameConfig.BOARD_H

    /**
     * 두 알이 겹치면 겹친 만큼 반씩 밀어내고, 서로 다가오던 중이면 탄성 충돌(질량 같음)시킨다.
     * 질량이 같은 탄성 충돌 = 중심을 잇는 방향(법선) 속도 성분을 서로 맞바꾼다 → 운동량·에너지 보존.
     * 반환: 닿지 않았으면 −1, 겹침만 풀었으면 0, 부딪혔으면 법선 방향 상대 속도(> 0, 충돌 세기).
     */
    fun collide(a: Stone, b: Stone, r: Float = GameConfig.STONE_R): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val min = 2f * r
        val d2 = dx * dx + dy * dy
        if (d2 >= min * min) return -1f
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d < 1e-6f) {
            // 완전히 같은 자리: 아무 방향(가로)으로 떼어 놓는다
            nx = 1f
            ny = 0f
        } else {
            nx = dx / d
            ny = dy / d
        }
        val push = (min - d) / 2f
        a.x -= nx * push
        a.y -= ny * push
        b.x += nx * push
        b.y += ny * push
        // 법선 방향 상대 속도 (양수 = 다가오는 중)
        val rel = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
        if (rel <= 0f) return 0f
        a.vx -= rel * nx
        a.vy -= rel * ny
        b.vx += rel * nx
        b.vy += rel * ny
        return rel
    }

    /** 마찰(일정 감속) + 공기 저항으로 dt 초만큼 느리게. STOP_SPEED 아래면 완전히 멈춘다 */
    fun applyFriction(s: Stone, dt: Float) {
        val sp = s.speed
        if (sp == 0f) return
        var n = sp - GameConfig.FRICTION * dt
        n -= n * GameConfig.DRAG * dt
        if (n < GameConfig.STOP_SPEED) {
            s.vx = 0f
            s.vy = 0f
            return
        }
        val k = n / sp
        s.vx *= k
        s.vy *= k
    }
}

/**
 * 알까기 판 위의 물리 세계. 고정 시간 단계(1/60초)로만 진행한다.
 * advance(ms) 는 받은 시간을 정수 틱으로 모아(1ms = 60틱, 한 단계 = 1000틱) 단계 수만큼 step() —
 * 프레임 시간을 어떻게 쪼개도(각 프레임 ≤ MAX_FRAME_MS) 같은 결과가 나온다.
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

    /** 한 단계당 떨어짐 진행 (fallMs 가 0 이하면 바로 사라짐) */
    private val fallPerStep = if (fallMs <= 0L) 1f else 1000f / GameConfig.STEPS_PER_SEC / fallMs
    private var ticks = 0L

    /** 지금까지 진행한 단계 수 */
    var steps = 0L
        private set

    /** 판 위의 모든 알이 멈췄고 떨어지는 중인 알도 없음 */
    val settled: Boolean
        get() {
            for (s in stones) {
                if (s.state == StoneState.FALLING) return false
                if (s.state == StoneState.ON_BOARD && s.moving) return false
            }
            return true
        }

    /** dtMs 만큼 시간을 흘리고, 모인 만큼 고정 단계를 진행한다. 진행한 단계 수를 돌려준다 */
    fun advance(dtMs: Long): Int {
        ticks += dtMs.coerceIn(0L, GameConfig.MAX_FRAME_MS) * GameConfig.TICKS_PER_MS
        var n = 0
        while (ticks >= GameConfig.TICKS_PER_STEP) {
            ticks -= GameConfig.TICKS_PER_STEP
            step()
            n++
        }
        return n
    }

    /** 남은 자투리 시간 버리기 (새로 튕길 때) */
    fun resetClock() {
        ticks = 0L
    }

    /** 고정 1/60초 한 단계: 이동 → 충돌(여러 번) → 마찰·정지 → 판 밖 판정 */
    fun step() {
        val dt = GameConfig.STEP_SEC
        steps++
        for (s in stones) {
            when (s.state) {
                StoneState.ON_BOARD -> {
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                }
                StoneState.FALLING -> {
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
                StoneState.GONE -> Unit
            }
        }
        // 여러 알이 한꺼번에 닿을 때를 위해 겹침이 없어질 때까지 몇 번 되풀이
        for (pass in 0 until GameConfig.COLLISION_PASSES) {
            var touched = false
            for (i in stones.indices) {
                val a = stones[i]
                if (!a.onBoard) continue
                for (j in i + 1 until stones.size) {
                    val b = stones[j]
                    if (!b.onBoard) continue
                    val hit = Physics.collide(a, b)
                    if (hit < 0f) continue
                    touched = true
                    if (hit > 0f) listener?.onCollide((a.x + b.x) / 2f, (a.y + b.y) / 2f, hit)
                }
            }
            if (!touched) break
        }
        for (s in stones) {
            if (!s.onBoard) continue
            Physics.applyFriction(s, dt)
            if (Physics.isOffBoard(s.x, s.y)) {
                s.state = StoneState.FALLING
                s.fall = 0f
                listener?.onFall(s)
            }
        }
    }
}
