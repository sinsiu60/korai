package com.siwoo.party.games.airhockey

import com.siwoo.party.common.physics.Body
import com.siwoo.party.common.physics.Wall
import com.siwoo.party.common.physics.World
import kotlin.math.abs
import kotlin.math.sqrt

/** 경기 단계: 시작 전 → 경기 중 → (골 → 쉬는 중 → 다시 경기 중)… → 끝 */
enum class Phase { READY, PLAY, GOAL, OVER }

/**
 * 에어하키 규칙 + 물리 (순수 Kotlin). 판은 TABLE_W × TABLE_H, 왼쪽 반 = 0번(1번 사람), 오른쪽 반 = 1번.
 *
 * - 물리는 공통 물리 World: 위·아래 긴 벽 + 양 끝 짧은 벽(가운데에 골문 구멍, addWallWithGap), 경계 = 판.
 *   퍽 중심이 골문을 지나 판 밖으로 나가면 World.Listener.onOut → 골.
 * - 말렛 = 움직일 수 없는 물체(질량 ∞). 손가락 목표(setTarget)는 자기 반쪽 안으로 잘리고,
 *   고정 단계마다 말렛 속도 = (목표 − 지금 자리) / 한 단계 시간 (최대 MALLET_MAX_SPEED) 으로 넣어 움직인다
 *   → 말렛이 움직이는 속도가 충돌로 퍽에 그대로 전해진다.
 * - 퍽 빠르기는 PUCK_MAX_SPEED 로 자른다.
 * - 골 → 점수 +1, 단계 GOAL (물리 멈춤). restart() 로 득점당한 쪽, 가운데 선 근처에 퍽을 멈춰 놓고 다시 시작.
 *   WIN_SCORE 점을 먼저 내면 OVER.
 * 시간은 밖에서 advance(ms) 로 넣는다 (고정 1/60초 단계 → 같은 입력이면 늘 같은 결과).
 */
class AirHockeyGame {

    /** 경기에서 일어나는 일 (소리·연출용) */
    interface Listener {
        /** player 의 말렛이 (x, y) 에서 세기 speed 로 퍽을 침 */
        fun onHit(player: Int, x: Float, y: Float, speed: Float) {}
        /** 퍽이 벽 (x, y) 에 speed 로 부딪힘 */
        fun onWall(x: Float, y: Float, speed: Float) {}
        /** scorer 가 득점 (phase 는 이미 GOAL 또는 OVER) */
        fun onGoal(scorer: Int) {}
    }

    var listener: Listener? = null

    /** 퍽 */
    val puck = Body(ID_PUCK, GameConfig.PUCK_R, 1f).apply { restitution = GameConfig.PUCK_E }

    /** 말렛 (0 = 왼쪽, 1 = 오른쪽). 질량 ∞ — 퍽에 밀리지 않고 벽·경계도 받지 않는다 */
    val mallets = Array(GameConfig.PLAYERS) { p ->
        Body(ID_MALLET + p, GameConfig.MALLET_R, Float.POSITIVE_INFINITY).apply {
            restitution = GameConfig.MALLET_E
            tag = p
        }
    }

    /** 말렛이 따라갈 목표 자리 (손가락, 자기 반쪽 안으로 잘린 값) */
    val targetX = FloatArray(GameConfig.PLAYERS)
    val targetY = FloatArray(GameConfig.PLAYERS)

    /** 점수 */
    val score = IntArray(GameConfig.PLAYERS)

    var phase = Phase.READY
        private set
    /** 이긴 사람 (끝나기 전엔 −1) */
    var winner = -1
        private set
    /** 마지막으로 득점한 사람 (−1 = 아직 없음) */
    var lastScorer = -1
        private set

    /** 공통 물리 세계 */
    val world = World().apply {
        friction = GameConfig.FRICTION
        drag = GameConfig.DRAG
        stopSpeed = GameConfig.STOP_SPEED
        substeps = GameConfig.SUBSTEPS
        clock.maxFrameMs = GameConfig.MAX_FRAME_MS
        val w = GameConfig.TABLE_W
        val h = GameConfig.TABLE_H
        // 위·아래 긴 벽
        addBox(0f, 0f, w, h, GameConfig.WALL_E, World.SIDE_TOP or World.SIDE_BOTTOM)
        // 양 끝 짧은 벽: 가운데 골문 구멍 (구멍 끝은 둥근 기둥)
        addWallWithGap(0f, 0f, 0f, h, GameConfig.GOAL_TOP, GameConfig.GOAL_BOTTOM, GameConfig.WALL_E)
        addWallWithGap(w, 0f, w, h, GameConfig.GOAL_TOP, GameConfig.GOAL_BOTTOM, GameConfig.WALL_E)
        setBounds(0f, 0f, w, h)
        addBody(puck)
        for (m in mallets) addBody(m)
        listener = object : World.Listener {
            override fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {
                val m = if (a === puck) b else a
                this@AirHockeyGame.listener?.onHit(m.tag, x, y, speed)
            }

            override fun onWall(body: Body, wall: Wall, x: Float, y: Float, speed: Float) {
                if (body === puck) this@AirHockeyGame.listener?.onWall(x, y, speed)
            }

            override fun onOut(body: Body) {
                if (body === puck) puckOut()
            }
        }
    }

    init {
        reset()
    }

    /** 처음 배치: 말렛은 자기 골문 앞, 퍽은 판 한가운데 (멈춤) */
    fun reset() {
        for (p in 0 until GameConfig.PLAYERS) {
            val x = if (p == 0) GameConfig.MALLET_START_X else GameConfig.TABLE_W - GameConfig.MALLET_START_X
            mallets[p].place(x, GameConfig.TABLE_H / 2f)
            targetX[p] = x
            targetY[p] = GameConfig.TABLE_H / 2f
        }
        puck.place(GameConfig.CENTER_X, GameConfig.TABLE_H / 2f)
        puck.active = true
        score.fill(0)
        winner = -1
        lastScorer = -1
        phase = Phase.READY
        world.resetClock()
    }

    // ── 말렛 ──

    /** player 말렛 중심이 갈 수 있는 x 범위 (자기 반쪽, 가운데 선에 말렛 가장자리가 닿는 데까지) */
    fun minX(player: Int): Float = if (player == 0) GameConfig.MALLET_R else GameConfig.CENTER_X + GameConfig.MALLET_R
    fun maxX(player: Int): Float = if (player == 0) GameConfig.CENTER_X - GameConfig.MALLET_R else GameConfig.TABLE_W - GameConfig.MALLET_R
    /** 말렛 중심이 갈 수 있는 y 범위 */
    val minY: Float get() = GameConfig.MALLET_R
    val maxY: Float get() = GameConfig.TABLE_H - GameConfig.MALLET_R

    /** 손가락 자리 (판 단위) → 자기 반쪽 안으로 잘라 말렛 목표로 */
    fun setTarget(player: Int, x: Float, y: Float) {
        targetX[player] = x.coerceIn(minX(player), maxX(player))
        targetY[player] = y.coerceIn(minY, maxY)
    }

    /** 손가락을 뗌 → 그 자리에 멈춤 */
    fun release(player: Int) {
        targetX[player] = mallets[player].x
        targetY[player] = mallets[player].y
        mallets[player].stop()
    }

    // ── 진행 ──

    /** 시작 (READY → PLAY) */
    fun start() {
        if (phase != Phase.READY) return
        phase = Phase.PLAY
        world.resetClock()
    }

    /**
     * 경기 중일 때만 dtMs 만큼 진행 (모인 만큼 고정 1/60초 단계). 진행한 단계 수를 돌려준다.
     * 단계 중에 골이 나면 거기서 멈춘다 (남은 시간은 버림).
     */
    fun advance(dtMs: Long): Int {
        if (phase != Phase.PLAY) return 0
        val n = world.clock.add(dtMs)
        for (i in 0 until n) {
            step()
            if (phase != Phase.PLAY) {
                world.resetClock()
                return i + 1
            }
        }
        return n
    }

    /** 고정 한 단계: 말렛 속도 = 목표까지 / 한 단계 → 물리 한 단계 → 퍽 빠르기 제한 */
    fun step() {
        for (p in 0 until GameConfig.PLAYERS) drive(p)
        world.step()
        val s = puck.speed
        if (s > GameConfig.PUCK_MAX_SPEED) {
            val k = GameConfig.PUCK_MAX_SPEED / s
            puck.vx *= k
            puck.vy *= k
        }
    }

    private fun drive(p: Int) {
        val m = mallets[p]
        var vx = (targetX[p] - m.x) / GameConfig.STEP_SEC
        var vy = (targetY[p] - m.y) / GameConfig.STEP_SEC
        val s = sqrt(vx * vx + vy * vy)
        if (s > GameConfig.MALLET_MAX_SPEED) {
            val k = GameConfig.MALLET_MAX_SPEED / s
            vx *= k
            vy *= k
        }
        m.setVelocity(vx, vy)
    }

    /** 퍽 중심이 판 밖으로: 골문 쪽이면 골, 아니면(벽에 끼여 밀려난 경우) 판 안으로 되돌림 */
    private fun puckOut() {
        val x = puck.x
        val y = puck.y
        val inMouth = y >= GameConfig.GOAL_TOP - GameConfig.PUCK_R && y <= GameConfig.GOAL_BOTTOM + GameConfig.PUCK_R
        if (inMouth && (x < 0f || x > GameConfig.TABLE_W)) {
            // 왼쪽 골문(0번 쪽)에 들어가면 1번 득점
            goal(if (x < 0f) 1 else 0)
            return
        }
        rescue()
    }

    /** 판 밖으로 밀려난 퍽을 판 안으로 (튕긴 방향으로) */
    private fun rescue() {
        val r = GameConfig.PUCK_R
        if (puck.x < r) { puck.x = r; puck.vx = abs(puck.vx) }
        if (puck.x > GameConfig.TABLE_W - r) { puck.x = GameConfig.TABLE_W - r; puck.vx = -abs(puck.vx) }
        if (puck.y < r) { puck.y = r; puck.vy = abs(puck.vy) }
        if (puck.y > GameConfig.TABLE_H - r) { puck.y = GameConfig.TABLE_H - r; puck.vy = -abs(puck.vy) }
        puck.active = true
    }

    private fun goal(scorer: Int) {
        score[scorer]++
        lastScorer = scorer
        if (score[scorer] >= GameConfig.WIN_SCORE) {
            winner = scorer
            phase = Phase.OVER
        } else {
            phase = Phase.GOAL
        }
        listener?.onGoal(scorer)
    }

    /** 득점당한 사람 쪽에서 퍽을 다시 놓을 x (가운데 선 근처) */
    fun serveX(conceded: Int): Float =
        if (conceded == 0) GameConfig.CENTER_X - GameConfig.SERVE_OFFSET else GameConfig.CENTER_X + GameConfig.SERVE_OFFSET

    /** 골 뒤 다시 시작: 득점당한 쪽, 가운데 선 근처에 퍽을 멈춰 놓는다 */
    fun restart() {
        if (phase != Phase.GOAL) return
        val conceded = 1 - lastScorer
        puck.place(serveX(conceded), GameConfig.TABLE_H / 2f)
        puck.active = true
        phase = Phase.PLAY
        world.resetClock()
    }

    /** 순위 (players 순서): 이긴 사람 1위, 진 사람 2위 */
    fun ranks(): IntArray = IntArray(GameConfig.PLAYERS) { if (it == winner) 1 else 2 }

    companion object {
        const val ID_PUCK = 0
        const val ID_MALLET = 1
    }
}
