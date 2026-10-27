package com.siwoo.party.games.stack

import com.siwoo.party.common.Ranking

/** 놓인 블록: 왼쪽 끝 x, 폭 w (칸), 놓은 사람 (-1 = 받침) */
class Block(val x: Float, val w: Float, val owner: Int) {
    val right: Float get() = x + w
}

enum class Phase { MOVING, ROUND_OVER, OVER }

enum class DropKind { PERFECT, CUT, MISS }

/**
 * 한 번 떨어뜨린 결과.
 * placedX/placedW: 탑에 남은 부분 (MISS 면 블록 전체 = 떨어지는 블록),
 * cutX/cutW: 잘려 떨어지는 조각 (없으면 cutW = 0).
 */
class DropResult(
    val kind: DropKind,
    val player: Int,
    val level: Int,
    val droppedX: Float,
    val placedX: Float,
    val placedW: Float,
    val cutX: Float,
    val cutW: Float,
)

/**
 * 탑 쌓기 규칙 (2~4인, 차례대로, 하나의 탑).
 * 라운드마다 받침 블록 하나에서 시작해, 차례대로 좌우로 움직이는 블록을 터치해 떨어뜨린다.
 * 아래 블록 밖으로 나간 부분은 잘려 나가고 남은 폭이 다음 블록 폭이 된다. 거의 정확하면 퍼펙트(폭 유지).
 * 완전히 빗나간 사람이 그 라운드 패배 → 다음 라운드는 그다음 사람부터. STACK_ROUNDS 라운드 뒤 패배 수 적은 순.
 * 시간은 밖에서 주입한다 (게임 시계 ms). 블록 위치는 이동 시작 시각부터 흐른 시간으로 계산한다.
 */
class StackGame(val players: Int, firstPlayer: Int, val rounds: Int = GameConfig.STACK_ROUNDS) {

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val losses = IntArray(players)
    /** 라운드 (1부터) */
    var round = 1
        private set
    var current = firstPlayer.coerceIn(0, players - 1)
        private set
    var phase = Phase.MOVING
        private set
    /** 이번 라운드 탑 (0번 = 받침) */
    val tower = ArrayList<Block>()
    /** 지금까지 놓은 블록 수 (모든 라운드, 받침 제외) */
    var placedTotal = 0
        private set
    var perfects = 0
        private set
    /** 마지막 라운드 패배자 (-1 = 아직) */
    var lastLoser = -1
        private set

    private var moveStart = 0L
    private var fromLeft = true

    init {
        resetTower()
    }

    /** 지금 놓을 블록의 층 (받침 = 0층, 첫 블록 = 1층) */
    val level: Int get() = tower.size
    val top: Block get() = tower[tower.size - 1]
    /** 움직이는 블록 폭 = 맨 위 블록 폭 */
    val width: Float get() = top.w
    /** 받침 위로 쌓인 층 수 */
    val height: Int get() = tower.size - 1

    private fun resetTower() {
        tower.clear()
        tower.add(Block((GameConfig.FIELD_W - GameConfig.START_W) / 2f, GameConfig.START_W, -1))
    }

    /** 블록 이동 시작 (차례 시작). 층마다 왼쪽·오른쪽에서 번갈아 출발 */
    fun startMove(nowMs: Long) {
        moveStart = nowMs
        fromLeft = level % 2 == 1
    }

    /** 지금 움직이는 블록 왼쪽 끝 */
    fun blockX(nowMs: Long): Float = position(nowMs - moveStart, speed(level), GameConfig.FIELD_W - width, fromLeft)

    /** 터치: 지금 위치에 떨어뜨린다 */
    fun drop(nowMs: Long): DropResult {
        check(phase == Phase.MOVING) { "지금은 못 떨어뜨림: $phase" }
        val x = blockX(nowMs)
        val lv = level
        val c = cut(top.x, top.w, x, width, GameConfig.STACK_PERFECT_TOL)
        val r = DropResult(c.kind, current, lv, x, c.placedX, c.placedW, c.cutX, c.cutW)
        if (c.kind == DropKind.MISS) {
            losses[current]++
            lastLoser = current
            phase = if (round >= rounds) Phase.OVER else Phase.ROUND_OVER
        } else {
            tower.add(Block(c.placedX, c.placedW, current))
            placedTotal++
            if (c.kind == DropKind.PERFECT) perfects++
            current = (current + 1) % players
        }
        return r
    }

    /** 다음 라운드: 새 탑, 진 사람 다음 사람부터 */
    fun nextRound() {
        check(phase == Phase.ROUND_OVER)
        round++
        current = (lastLoser + 1) % players
        resetTower()
        phase = Phase.MOVING
    }

    /** 패배 수 적은 순 (같으면 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(losses.map { it.toLong() }, higherIsBetter = false)

    /** 잘림 계산 결과 */
    class Cut(val kind: DropKind, val placedX: Float, val placedW: Float, val cutX: Float, val cutW: Float)

    companion object {
        /** level 층 블록의 이동 속도 (칸/초) */
        fun speed(level: Int): Float =
            minOf(GameConfig.SPEED_MAX, GameConfig.SPEED_BASE + GameConfig.SPEED_STEP * (maxOf(level, 1) - 1))

        /** 0 ~ range 를 speed(칸/초)로 왕복할 때 elapsedMs 뒤 위치 (fromLeft 면 0 에서, 아니면 range 에서 출발) */
        fun position(elapsedMs: Long, speed: Float, range: Float, fromLeft: Boolean): Float {
            if (range <= 0f) return 0f
            val d = speed * maxOf(elapsedMs, 0L) / 1000f
            val m = d % (2f * range)
            val p = if (m <= range) m else 2f * range - m
            return if (fromLeft) p else range - p
        }

        /**
         * 아래 블록(tx, tw) 위에 블록(x, w)을 놓을 때.
         * 왼쪽 끝 차이가 tol 이하 → 퍼펙트 (아래 블록 위치·폭 그대로),
         * 겹치는 부분이 없으면 → 빗나감, 아니면 겹친 부분만 남고 튀어나온 쪽이 잘림.
         */
        fun cut(tx: Float, tw: Float, x: Float, w: Float, tol: Float): Cut {
            if (kotlin.math.abs(x - tx) <= tol && w > 0f) return Cut(DropKind.PERFECT, tx, minOf(w, tw), 0f, 0f)
            val l = maxOf(x, tx)
            val r = minOf(x + w, tx + tw)
            if (r - l <= 0f) return Cut(DropKind.MISS, x, w, x, w)
            return if (x < tx) Cut(DropKind.CUT, l, r - l, x, l - x) // 왼쪽으로 튀어나옴
            else Cut(DropKind.CUT, l, r - l, r, x + w - r) // 오른쪽으로 튀어나옴
        }
    }
}
