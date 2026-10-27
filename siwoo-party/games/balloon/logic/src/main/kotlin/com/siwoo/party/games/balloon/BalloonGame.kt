package com.siwoo.party.games.balloon

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 풍선 하나의 상태 (라운드마다) */
enum class BalloonState {
    /** 아직 키우는 중 */
    INFLATING,
    /** [멈춤] 또는 시간 끝 → 지금 크기로 점수 확정 */
    STOPPED,
    /** 터짐 → 이번 라운드 0점 */
    POPPED,
}

/** 라운드 단계 */
enum class Phase { READY, PLAYING, ROUND_OVER, GAME_OVER }

/** 탭(키우기) 결과 */
enum class Pump {
    /** 라운드 중이 아니거나 이미 멈춤/터짐 */
    IGNORED,
    /** 1초에 MAX_TAPS_PER_SEC 회를 넘어 인정 안 됨 */
    LIMITED,
    /** 커짐 */
    GREW,
    /** 이 탭으로 터짐 */
    POPPED,
}

/**
 * 1인당 1초(슬라이딩 창)에 max 회까지만 인정.
 * 인정한 탭 시각을 사람마다 최근 max 개 기억해, 가장 오래된 것이 1초 안이면 거절한다.
 */
class TapLimiter(playerCount: Int, private val max: Int = GameConfig.MAX_TAPS_PER_SEC, private val windowMs: Long = GameConfig.TAP_WINDOW_MS) {
    private val times = Array(playerCount) { LongArray(max) }
    private val count = IntArray(playerCount)
    private val head = IntArray(playerCount)

    fun accept(player: Int, timeMs: Long): Boolean {
        val buf = times[player]
        if (count[player] == max && timeMs - buf[head[player]] < windowMs) return false
        buf[head[player]] = timeMs
        head[player] = (head[player] + 1) % max
        if (count[player] < max) count[player]++
        return true
    }

    fun reset() {
        count.fill(0)
        head.fill(0)
    }
}

/**
 * 풍선 치킨 게임 규칙.
 * - 라운드마다 풍선마다 숨겨진 터지는 크기(POP_MIN..POP_MAX, 구역마다 서로 다름)를 새로 뽑는다
 * - 자기 구역을 탭할 때마다 크기 +1 (1인당 초당 MAX_TAPS_PER_SEC 회까지만 인정)
 * - 크기가 터지는 크기에 닿으면(같거나 넘으면) 펑 → 그 라운드 0점
 * - [멈춤] 을 누르면 지금 크기가 그 라운드 점수로 확정, 이후 탭은 무시
 * - 모두 멈춤/터짐이면 라운드 끝. 제한 시간(ROUND_TIME_MS)이 끝나면 남은 풍선은 그때 크기로 자동 확정
 * - BALLOON_ROUNDS 라운드 합계로 순위 (동점 같은 순위)
 * 시간·난수는 밖에서 넣는다 → 테스트에서 그대로 재현 가능.
 */
class BalloonGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 이번 라운드 풍선 크기 */
    val sizes = IntArray(playerCount)
    /** 이번 라운드 풍선 상태 */
    val states = Array(playerCount) { BalloonState.INFLATING }
    /** 이번 라운드 숨겨진 터지는 크기 (화면에는 보여 주지 않는다) */
    private val popAt = IntArray(playerCount)
    /** 라운드별 점수 [라운드][플레이어] */
    val roundScores = Array(GameConfig.BALLOON_ROUNDS) { IntArray(playerCount) }
    /** 합계 */
    val totals = IntArray(playerCount)
    private val limiter = TapLimiter(playerCount)

    var phase = Phase.READY
        private set
    /** 끝난 라운드 수 */
    var roundsDone = 0
        private set
    /** 지금(또는 다음) 라운드 번호 1.. */
    val roundNumber: Int get() = (roundsDone + 1).coerceAtMost(GameConfig.BALLOON_ROUNDS)
    val gameOver: Boolean get() = phase == Phase.GAME_OVER

    /** 라운드 시작: 상태 초기화 + 터지는 크기 새로 뽑기 (구역마다 서로 다른 값) */
    fun startRound() {
        check(phase == Phase.READY || phase == Phase.ROUND_OVER) { "라운드를 시작할 수 없습니다: $phase" }
        sizes.fill(0)
        states.fill(BalloonState.INFLATING)
        limiter.reset()
        val pool = (GameConfig.POP_MIN..GameConfig.POP_MAX).shuffled(random)
        for (p in 0 until playerCount) popAt[p] = pool[p]
        phase = Phase.PLAYING
    }

    /** 테스트용: 터지는 크기 */
    fun popThreshold(player: Int): Int = popAt[player]

    /** 테스트용: 터지는 크기를 직접 정함 (라운드 중) */
    fun setThresholdForTest(player: Int, value: Int) {
        popAt[player] = value
    }

    /** 플레이어 player 가 풍선을 탭 (nowMs = 게임 시간) */
    fun pump(player: Int, nowMs: Long): Pump {
        if (player !in 0 until playerCount) return Pump.IGNORED
        if (phase != Phase.PLAYING || states[player] != BalloonState.INFLATING) return Pump.IGNORED
        if (!limiter.accept(player, nowMs)) return Pump.LIMITED
        sizes[player] += GameConfig.GROW_PER_TAP
        if (sizes[player] >= popAt[player]) {
            states[player] = BalloonState.POPPED
            return Pump.POPPED
        }
        return Pump.GREW
    }

    /** [멈춤]: 지금 크기로 확정. 확정되면 true (이미 멈춤/터짐·라운드 밖이면 false) */
    fun stop(player: Int): Boolean {
        if (player !in 0 until playerCount) return false
        if (phase != Phase.PLAYING || states[player] != BalloonState.INFLATING) return false
        states[player] = BalloonState.STOPPED
        return true
    }

    /** 이번 라운드 점수 (멈춤 = 크기, 터짐·진행 중 = 0) */
    fun scoreOf(player: Int): Int = if (states[player] == BalloonState.STOPPED) sizes[player] else 0

    /** 모두 멈춤 또는 터짐 */
    val allDone: Boolean get() = states.all { it != BalloonState.INFLATING }

    /** 제한 시간 끝: 아직 키우던 풍선은 지금 크기로 자동 확정. 자동 확정된 사람 수 */
    fun timeUp(): Int {
        if (phase != Phase.PLAYING) return 0
        var n = 0
        for (p in 0 until playerCount) if (states[p] == BalloonState.INFLATING) { states[p] = BalloonState.STOPPED; n++ }
        return n
    }

    /**
     * 라운드 마무리: 점수를 합계에 더한다 (모두 끝났어야 함 — 아니면 timeUp 먼저).
     * 마지막 라운드면 GAME_OVER. 이번 라운드 점수를 돌려준다.
     */
    fun endRound(): IntArray {
        check(phase == Phase.PLAYING) { "라운드 중이 아닙니다" }
        check(allDone) { "아직 키우는 풍선이 있습니다" }
        val scores = roundScores[roundsDone]
        for (p in 0 until playerCount) {
            scores[p] = scoreOf(p)
            totals[p] += scores[p]
        }
        roundsDone++
        phase = if (roundsDone >= GameConfig.BALLOON_ROUNDS) Phase.GAME_OVER else Phase.ROUND_OVER
        return scores
    }

    /** 이번 라운드 최고 점수를 낸 사람들 (모두 0점이면 없음) */
    fun roundBest(scores: IntArray): List<Int> {
        val best = scores.maxOrNull() ?: 0
        if (best <= 0) return emptyList()
        return (0 until playerCount).filter { scores[it] == best }
    }

    /** 최종 순위 (1부터, 합계 많은 순, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(totals.map { it.toLong() })
}

/** 구역 안 그림 배치용 상자 (구역 가운데가 원점, 그 구역 사람이 보는 방향 기준: x 오른쪽, y 아래쪽) */
class Box(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/**
 * 모든 구역에 똑같이 쓰는 배치 (공정성: 같은 크기·같은 위치, 구역 방향으로만 회전).
 * 칸 크기 = 모든 구역 중 가장 작은 폭 × (가장 작은 높이와 폭×0.85 중 작은 것) — 구역 가운데에 놓는다.
 * 좌표는 칸 가운데가 원점인 "그 사람 기준" 좌표.
 */
class BalloonLayout {
    var cellW = 0f
        private set
    var cellH = 0f
        private set
    /** [멈춤] 버튼 */
    val stop = Box()
    /** 가장 큰 풍선의 중심과 반지름 범위 (크기 0 → rMin, POP_MAX → rMax). 꼭지(knotY)는 고정, 풍선은 위로 부푼다 */
    var balloonX = 0f
        private set
    var balloonY = 0f
        private set
    var rMin = 0f
        private set
    var rMax = 0f
        private set
    /** 풍선 꼭지 y (크기와 상관없이 고정) */
    var knotY = 0f
        private set
    /** 시간 막대 */
    val timeBar = Box()
    /** 이름·합계 글자 기준선 y */
    var headerY = 0f
        private set
    /** 크기 숫자 (버튼 위) 중심 */
    var sizeX = 0f
        private set
    var sizeY = 0f
        private set

    fun layout(minZoneW: Float, minZoneH: Float) {
        cellW = minZoneW
        cellH = minOf(minZoneH, minZoneW * 0.85f)
        val w = cellW
        val h = cellH
        val bw = w * 0.27f
        val bh = minOf(h * 0.32f, w * 0.19f)
        stop.set(w * 0.46f - bw, h * 0.44f - bh, w * 0.46f, h * 0.44f)
        rMax = minOf(h * 0.27f, w * 0.26f)
        rMin = rMax * 0.3f
        balloonX = -w * 0.13f
        balloonY = h * 0.1f
        knotY = balloonY + rMax * STRETCH
        headerY = -h * 0.33f
        timeBar.set(-w * 0.44f, -h * 0.28f, w * 0.44f, -h * 0.28f + maxOf(h * 0.025f, 3f))
        sizeX = stop.centerX
        sizeY = (timeBar.bottom + stop.top) / 2f
    }

    /** 크기 → 풍선 반지름 */
    fun radius(size: Int): Float = rMin + (rMax - rMin) * (size.toFloat() / GameConfig.POP_MAX).coerceIn(0f, 1f)

    /** 크기 → 풍선 중심 y (꼭지 고정) */
    fun centerY(size: Int): Float = knotY - radius(size) * STRETCH

    companion object {
        /** 풍선 세로 / 가로 비 */
        const val STRETCH = 1.12f

        /**
         * 화면 좌표 (x, y) 를 구역(가운데 cx, cy, 회전 rotation°) 사람 기준 좌표로 바꿔 out[0], out[1] 에.
         * 위쪽 구역(180°)은 x·y 가 뒤집힌다.
         */
        fun toLocal(cx: Float, cy: Float, rotation: Float, x: Float, y: Float, out: FloatArray) {
            val dx = x - cx
            val dy = y - cy
            val a = Math.toRadians(-rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = dx * cos - dy * sin
            out[1] = dx * sin + dy * cos
        }

        /** 사람 기준 좌표 (lx, ly) → 화면 좌표 out[0], out[1] */
        fun toScreen(cx: Float, cy: Float, rotation: Float, lx: Float, ly: Float, out: FloatArray) {
            val a = Math.toRadians(rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = cx + lx * cos - ly * sin
            out[1] = cy + lx * sin + ly * cos
        }
    }
}
