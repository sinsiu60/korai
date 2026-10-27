package com.siwoo.party.games.marbles

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.World
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 진행 단계: 내 구슬 놓기 → 조준(놓은 뒤, 옮기기도 가능) → 굴러가는 중 → 끝 */
enum class Phase { PLACE, AIM, MOVING, OVER }

/** 구슬 상태 */
enum class MarbleState {
    /** 마당 위 (물리 세계가 다룸) */
    IN_PLAY,
    /** 중심이 마당 밖으로 나감 (이번 샷이 끝나면 쏜 사람이 획득 — 가운데 구슬만) */
    LEFT_FIELD,
    /** 누군가 획득함 */
    CAPTURED,
    /** 마당에 없음 (내 구슬: 아직 안 놓음 / 쏜 뒤 주인에게 돌아감) */
    REMOVED,
}

/** 발사 결과 */
enum class Fire {
    SHOT,
    /** 아직 내 구슬을 안 놓음 */
    NOT_PLACED,
    /** 힘이 MIN_POWER 보다 약함 → 취소 */
    TOO_WEAK,
    /** 지금은 못 쏨 (굴러가는 중·끝남) */
    NOT_NOW,
}

/**
 * 구슬 하나 = 공통 물리 Body + 상태. state 를 바꾸면 Body.active 도 따라 바뀐다 (IN_PLAY 일 때만 물리).
 * @param color 가운데 구슬 색 번호 0..9 (내 구슬은 −1 — 화면이 플레이어 색으로 그림)
 */
class Marble(id: Int, val isShooter: Boolean, radius: Float, mass: Float, val color: Int) : Body(id, radius, mass) {
    var state = MarbleState.IN_PLAY
        set(value) {
            field = value
            active = value == MarbleState.IN_PLAY
        }

    /** 획득한 사람 (−1 = 아직) */
    var capturedBy = -1

    val inPlay: Boolean get() = state == MarbleState.IN_PLAY

    init {
        restitution = GameConfig.RESTITUTION
    }
}

/** 한 번 쏜 결과 (모두 멈춘 뒤) */
class ShotEnd(
    /** 쏜 사람 */
    val shooter: Int,
    /** 이번에 획득한 가운데 구슬 번호들 */
    val captured: List<Int>,
    /** 하나라도 획득 → 같은 사람이 한 번 더 */
    val extraTurn: Boolean,
    /** 다음에 쏠 사람 (끝났으면 쏜 사람 그대로) */
    val next: Int,
    val gameOver: Boolean,
    /** 내 구슬이 멈춘 자리 (마당 밖으로 나갔으면 마지막 자리) — 주인에게 돌아가는 연출용 */
    val shooterX: Float,
    val shooterY: Float,
    /** 내 구슬이 마당 밖으로 나갔는지 */
    val shooterLeftField: Boolean,
    /** 내 구슬이 링 안에서 멈췄는지 (규칙상 그래도 치움) */
    val shooterInRing: Boolean,
)

/**
 * 구슬치기 규칙 (2~4인, 차례대로). 순수 Kotlin — 시간은 밖에서 advance(ms) 로 넣는다.
 * - 링 가운데 구슬 TARGETS 개 (시드로 정해지는 겹치지 않는 무리)
 * - 차례인 사람은 링 밖(마당 안) 원하는 곳에 내 구슬을 놓고(place, 링 안·마당 밖은 가장 가까운 허용 자리로 당김) 새총으로 쏜다(shoot)
 * - 모두 멈추면 판정: 중심이 링 밖에 있는 가운데 구슬 + 마당 밖으로 나간 가운데 구슬 = 쏜 사람이 획득
 *   (몸이 링 선에 걸쳐 있어도 중심이 링 안(거리 ≤ RING_R)이면 아직 링 안)
 * - 하나라도 획득하면 같은 사람이 한 번 더, 아니면 다음 사람. 내 구슬은 쏜 뒤 언제나 치운다 (링 안에 멈춰도)
 * - 가운데 구슬이 모두 사라지면 끝, 또는 못 맞힌 차례가 MAX_ROUNDS × 인원 번 쌓이면 끝 (모두 같은 수의 차례)
 * - 순위: 획득 수 많은 순 (경쟁 순위, 동점 같은 순위)
 */
class MarblesGame(
    val playerCount: Int,
    firstPlayer: Int = 0,
    seed: Int = 0,
) {

    /** 진행 알림 (소리·연출용) */
    interface Listener {
        fun onCollide(x: Float, y: Float, speed: Float) {}
        /** 구슬 중심이 마당 밖으로 나감 */
        fun onLeaveField(m: Marble) {}
        /** 모두 멈춤 → 판정 결과 */
        fun onShotEnd(r: ShotEnd)
    }

    var listener: Listener? = null

    /** 가운데 구슬 0..TARGETS-1 */
    val targets = Array(GameConfig.TARGETS) {
        Marble(it, false, GameConfig.TARGET_R, GameConfig.TARGET_MASS, it % GameConfig.TARGETS)
    }
    /** 내 구슬 (차례마다 같은 물체를 다시 놓는다) */
    val shooter = Marble(GameConfig.TARGETS, true, GameConfig.SHOOTER_R, GameConfig.SHOOTER_MASS, -1).apply {
        state = MarbleState.REMOVED
    }

    /** 사람마다 획득한 구슬 수 */
    val captured = IntArray(playerCount)

    var current = firstPlayer
        private set
    var phase = Phase.PLACE
        private set
    /** 지금까지 쏜 횟수 */
    var shots = 0
        private set
    /** 못 맞혀 넘긴 차례 수 (MAX_ROUNDS × 인원이면 끝) */
    var missedTurns = 0
        private set
    /** 이번 샷에서 쏜 사람 */
    var shotBy = firstPlayer
        private set
    /** 이번 샷이 시작된 뒤 단계 수 */
    private var shotSteps = 0L
    private var settledFlag = false
    /** 이번 샷에서 내 구슬이 마당 밖으로 나갔는지 */
    private var shooterLeft = false
    private val tmp = FloatArray(2)

    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        passes = GameConfig.COLLISION_PASSES
        substeps = GameConfig.SUBSTEPS
        clock.maxFrameMs = GameConfig.MAX_FRAME_MS
        setBounds(0f, 0f, GameConfig.FIELD_W, GameConfig.FIELD_H)
        for (t in targets) addBody(t)
        addBody(shooter)
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {
                this@MarblesGame.listener?.onCollide(x, y, speed)
            }

            override fun onOut(body: Body) {
                val m = body as Marble
                m.state = if (m.isShooter) MarbleState.REMOVED else MarbleState.LEFT_FIELD
                shooterLeft = shooterLeft || m.isShooter
                this@MarblesGame.listener?.onLeaveField(m)
            }

            override fun onSettled() {
                settledFlag = true
            }
        }
    }

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(firstPlayer in 0 until playerCount)
        layoutTargets(seed)
    }

    // ── 상태 ──

    /** 마당 위에 남은 가운데 구슬 수 */
    val remaining: Int
        get() {
            var n = 0
            for (t in targets) if (t.state == MarbleState.IN_PLAY || t.state == MarbleState.LEFT_FIELD) n++
            return n
        }

    /** 내 구슬이 놓여 있는지 */
    val shooterPlaced: Boolean get() = shooter.inPlay

    /** 지금 몇 바퀴째 (1부터, 못 맞힌 차례 기준) */
    val round: Int get() = (missedTurns / playerCount + 1).coerceAtMost(GameConfig.MAX_ROUNDS)

    /** 끝났는지 */
    val over: Boolean get() = phase == Phase.OVER

    /** 지금 다시 놓거나 쏠 수 있는지 */
    val canAct: Boolean get() = phase == Phase.PLACE || phase == Phase.AIM

    // ── 시작 배치 ──

    /**
     * 가운데 구슬을 링 중심 CLUSTER_R 안에 겹치지 않게 무작위로 (seed 가 같으면 늘 같은 배치).
     * 무작위로 못 채우면(아주 드묾) 육각 격자 배치로.
     */
    fun layoutTargets(seed: Int) {
        val rnd = Random(seed)
        val minD = 2f * GameConfig.TARGET_R + GameConfig.CLUSTER_GAP
        repeat(LAYOUT_RESTARTS) {
            if (tryRandomLayout(rnd, minD)) return
        }
        hexLayout(minD)
    }

    private fun tryRandomLayout(rnd: Random, minD: Float): Boolean {
        for (i in targets.indices) {
            var ok = false
            for (attempt in 0 until LAYOUT_TRIES) {
                val a = rnd.nextFloat() * TWO_PI
                val r = GameConfig.CLUSTER_R * sqrt(rnd.nextFloat())
                val x = GameConfig.RING_X + r * cos(a)
                val y = GameConfig.RING_Y + r * sin(a)
                var free = true
                for (j in 0 until i) {
                    val dx = targets[j].x - x
                    val dy = targets[j].y - y
                    if (dx * dx + dy * dy < minD * minD) { free = false; break }
                }
                if (free) {
                    resetTarget(targets[i], x, y)
                    ok = true
                    break
                }
            }
            if (!ok) return false
        }
        return true
    }

    /** 육각 격자에서 중심에 가까운 순서로 */
    private fun hexLayout(minD: Float) {
        val pts = ArrayList<FloatArray>()
        for (j in -4..4) for (i in -4..4) {
            val x = (i + j * 0.5f) * minD
            val y = j * minD * 0.8660254f
            pts.add(floatArrayOf(x, y, x * x + y * y))
        }
        pts.sortBy { it[2] }
        for (k in targets.indices) resetTarget(targets[k], GameConfig.RING_X + pts[k][0], GameConfig.RING_Y + pts[k][1])
    }

    private fun resetTarget(t: Marble, x: Float, y: Float) {
        t.place(x, y)
        t.state = MarbleState.IN_PLAY
        t.capturedBy = -1
    }

    // ── 내 구슬 놓기 ──

    /**
     * 내 구슬을 (x, y) 근처의 허용 자리에 놓는다 (이미 놓았으면 옮김). 놓은 자리는 shooter.x / shooter.y.
     * 허용 자리 = 마당 안(가장자리 FIELD_MARGIN) + 링 밖(중심 거리 ≥ PLACE_MIN_DIST) + 가운데 구슬과 안 겹침.
     * 반환: 놓았으면 true (놓을 단계가 아니면 false)
     */
    fun place(x: Float, y: Float): Boolean {
        if (!canAct) return false
        clampPlacement(x, y, tmp)
        var px = tmp[0]
        var py = tmp[1]
        // 링 선 근처의 가운데 구슬과 겹치면 밀어내고 다시 맞춤 (몇 번이면 충분)
        for (iter in 0 until 8) {
            var moved = false
            for (t in targets) {
                if (!t.inPlay) continue
                val dx = px - t.x
                val dy = py - t.y
                val need = t.radius + GameConfig.SHOOTER_R + PLACE_EPS
                val d2 = dx * dx + dy * dy
                if (d2 >= need * need) continue
                val d = sqrt(d2)
                val nx = if (d > 1e-5f) dx / d else (px - GameConfig.RING_X) / GameConfig.PLACE_MIN_DIST
                val ny = if (d > 1e-5f) dy / d else (py - GameConfig.RING_Y) / GameConfig.PLACE_MIN_DIST
                clampPlacement(t.x + nx * need, t.y + ny * need, tmp)
                px = tmp[0]
                py = tmp[1]
                moved = true
            }
            if (!moved) break
        }
        shooter.place(px, py)
        shooter.state = MarbleState.IN_PLAY
        phase = Phase.AIM
        return true
    }

    // ── 쏘기 ──

    /** 방향(dirX, dirY 단위 벡터)·힘(0..1)으로 내 구슬을 쏜다 */
    fun shoot(dirX: Float, dirY: Float, power: Float): Fire {
        if (!canAct) return Fire.NOT_NOW
        if (!shooterPlaced) return Fire.NOT_PLACED
        if (power < GameConfig.MIN_POWER) return Fire.TOO_WEAK
        val len = sqrt(dirX * dirX + dirY * dirY)
        if (len < 1e-5f) return Fire.TOO_WEAK
        val v = GameConfig.MAX_SPEED * power.coerceAtMost(1f)
        shooter.setVelocity(dirX / len * v, dirY / len * v)
        shotBy = current
        shotSteps = 0L
        settledFlag = false
        shooterLeft = false
        shots++
        phase = Phase.MOVING
        world.resetClock()
        return Fire.SHOT
    }

    /**
     * 화면 프레임마다 흐른 시간(ms). 굴러가는 중에만 물리가 돈다.
     * 반환: 아직 움직이면 true (false 면 프레임 루프를 멈춰도 됨 — 그 사이 판정·onShotEnd 가 끝남)
     */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.MOVING) return false
        val n = world.advance(dtMs)
        shotSteps += n
        if (shotSteps >= GameConfig.MAX_SHOT_STEPS && !world.settled) {
            // 안전장치: 너무 오래 구르면 모두 세움
            for (b in world.bodies) b.stop()
            settledFlag = true
        }
        if (settledFlag || world.settled) {
            resolve()
            return false
        }
        return true
    }

    /** 모두 멈춤 → 획득 판정 → 한 번 더 / 다음 사람 / 끝 */
    private fun resolve() {
        val p = shotBy
        val got = ArrayList<Int>()
        for (t in targets) {
            val out = t.state == MarbleState.LEFT_FIELD || (t.state == MarbleState.IN_PLAY && isOutsideRing(t.x, t.y))
            if (!out) continue
            t.state = MarbleState.CAPTURED
            t.capturedBy = p
            t.stop()
            got.add(t.id)
        }
        val sx = shooter.x
        val sy = shooter.y
        val inRing = shooter.inPlay && !isOutsideRing(sx, sy)
        val left = shooterLeft
        shooter.stop()
        shooter.state = MarbleState.REMOVED
        captured[p] += got.size
        val extra = got.isNotEmpty()
        if (!extra) missedTurns++
        val end = remaining == 0 || missedTurns >= GameConfig.MAX_ROUNDS * playerCount
        if (end) {
            phase = Phase.OVER
        } else {
            if (!extra) current = (current + 1) % playerCount
            phase = Phase.PLACE
        }
        world.resetClock()
        listener?.onShotEnd(ShotEnd(p, got, extra, current, end, sx, sy, left, inRing))
    }

    /** players 순서의 순위 (획득 수 많은 순, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(captured.map { it.toLong() })

    // ── 테스트·연출 확인용 ──

    /** 가운데 구슬을 (x, y) 에 멈춰 놓기 */
    fun putTarget(id: Int, x: Float, y: Float) = resetTarget(targets[id], x, y)

    /** 가운데 구슬을 마당에서 치우기 (아무도 획득하지 않음) */
    fun removeTarget(id: Int) {
        targets[id].stop()
        targets[id].state = MarbleState.REMOVED
    }

    companion object {
        private const val TWO_PI = 6.2831855f
        private const val LAYOUT_TRIES = 400
        private const val LAYOUT_RESTARTS = 40
        private const val PLACE_EPS = 0.02f

        /** 중심이 링 밖인지 (링 선 위 = 거리가 정확히 RING_R 이면 아직 안) */
        fun isOutsideRing(x: Float, y: Float): Boolean {
            val dx = x - GameConfig.RING_X
            val dy = y - GameConfig.RING_Y
            return dx * dx + dy * dy > GameConfig.RING_R * GameConfig.RING_R
        }

        /**
         * (x, y) 를 내 구슬 허용 자리로 당겨 out[0], out[1] 에 (할당 없음).
         * 마당 안으로 자른 뒤, 링 중심에서 PLACE_MIN_DIST 보다 가까우면 바깥쪽으로 밀어낸다
         * (링+여유는 마당 안에 들어가므로 밀어낸 자리도 마당 안). 정확히 중심이면 아래쪽으로.
         */
        fun clampPlacement(x: Float, y: Float, out: FloatArray) {
            val m = GameConfig.FIELD_MARGIN
            var px = x.coerceIn(m, GameConfig.FIELD_W - m)
            var py = y.coerceIn(m, GameConfig.FIELD_H - m)
            val dx = px - GameConfig.RING_X
            val dy = py - GameConfig.RING_Y
            val d2 = dx * dx + dy * dy
            val minD = GameConfig.PLACE_MIN_DIST
            if (d2 < minD * minD) {
                val d = sqrt(d2)
                if (d < 1e-5f) {
                    px = GameConfig.RING_X
                    py = GameConfig.RING_Y + minD
                } else {
                    px = GameConfig.RING_X + dx / d * minD
                    py = GameConfig.RING_Y + dy / d * minD
                }
            }
            out[0] = px
            out[1] = py
        }

        /** (x, y) 가 내 구슬 허용 자리인지 (경계 위 포함) */
        fun isPlaceable(x: Float, y: Float): Boolean {
            val m = GameConfig.FIELD_MARGIN
            if (x < m - 1e-4f || x > GameConfig.FIELD_W - m + 1e-4f || y < m - 1e-4f || y > GameConfig.FIELD_H - m + 1e-4f) return false
            val dx = x - GameConfig.RING_X
            val dy = y - GameConfig.RING_Y
            val minD = GameConfig.PLACE_MIN_DIST - 1e-3f
            return dx * dx + dy * dy >= minD * minD
        }
    }
}
