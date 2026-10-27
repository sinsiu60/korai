package com.siwoo.party.games.eggcatch

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.LaneJudgeConfig
import com.siwoo.party.common.lane.LanePattern
import com.siwoo.party.common.lane.LanePatternConfig
import com.siwoo.party.common.lane.LaneTrack

/**
 * 계란 받기 규칙 (순수 Kotlin).
 * - 시드 하나로 만든 낙하 패턴(LanePattern, 레인 4개)을 모든 구역이 함께 쓴다 → 모두 같은 계란, 같은 순간.
 * - 구역마다 바구니 위치(레인 단위 0..LANES, 가운데)와 LaneTrack(받음/떨어짐·점수)이 따로 있다.
 * - 물체가 바구니 선에 닿는 순간 바구니와 가로로 겹치면 받음: 계란 +1, 황금 계란 +3, 폭탄 −3.
 *   겹치지 않으면 떨어짐(0점) — 계란은 깨지고, 폭탄은 피한 것.
 * - 떨어지는 속도와 빈도는 경기 시간(규칙 시간)에 따라 점점 빨라진다.
 * - 45초 뒤 점수 순 (같은 점수는 같은 순위, 음수 점수 가능).
 *
 * 시간: start(clock) 의 clock 이 경기 0ms. update/elapsed 는 같은 시계 값을 받는다.
 */
class EggCatchGame(val playerCount: Int, val seed: Long) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    /** 모든 구역 공통 낙하 패턴 */
    val pattern: LanePattern = LanePattern.generate(patternConfig(), seed)

    /** 구역(플레이어)별 진행 */
    val tracks: Array<LaneTrack> = Array(playerCount) { LaneTrack(pattern, judgeConfig(), zone = it) }

    /** 바구니 가운데 (레인 단위, 처음엔 한가운데) */
    private val baskets = FloatArray(playerCount) { GameConfig.LANES / 2f }

    /** 구역별 받은 계란·황금 계란·폭탄, 깨진(놓친) 계란 수 */
    val eggs = IntArray(playerCount)
    val golds = IntArray(playerCount)
    val bombs = IntArray(playerCount)
    val broken = IntArray(playerCount)

    /** 받음/떨어짐 알림 (화면 연출용) — 집계 뒤에 불린다 */
    var listener: LaneTrack.Listener? = null

    private var startClock = 0L
    var started = false
        private set

    init {
        val count = LaneTrack.Listener { tr, i, st -> onResolved(tr, i, st) }
        for (tr in tracks) tr.listener = count
    }

    private fun onResolved(tr: LaneTrack, i: Int, st: Int) {
        val z = tr.zone
        val k = pattern.kind(i)
        if (st == ItemState.CAUGHT) {
            when (k) {
                GameConfig.KIND_EGG -> eggs[z]++
                GameConfig.KIND_GOLD -> golds[z]++
                else -> bombs[z]++
            }
        } else if (st == ItemState.DROPPED && k != GameConfig.KIND_BOMB) {
            broken[z]++
        }
        listener?.onResolved(tr, i, st)
    }

    fun start(clock: Long) {
        startClock = clock
        started = true
    }

    /** 경기 시작 기준 시간 */
    fun elapsed(clock: Long): Long = if (started) clock - startClock else 0L

    fun remainingMs(clock: Long): Long = (GameConfig.GAME_MS - elapsed(clock)).coerceAtLeast(0L)

    fun isOver(clock: Long): Boolean = started && elapsed(clock) >= GameConfig.GAME_MS

    /** 경기 중(시작 후·끝 전)인지 */
    fun isRunning(clock: Long): Boolean = started && !isOver(clock)

    /** 구역 zone 의 바구니 가운데 (레인 단위) */
    fun basket(zone: Int): Float = baskets[zone]

    /** 바구니를 units(레인 단위) 로 옮긴다 — 레인 영역 안으로 자름. 자른 값을 돌려준다 */
    fun setBasket(zone: Int, units: Float): Float {
        if (zone !in 0 until playerCount) return 0f
        val v = clampBasket(units)
        baskets[zone] = v
        return v
    }

    /** 바구니 선에 닿은 물체를 받음/떨어짐 처리 (매 프레임). 경기 끝 이후 시간은 끝 시각으로 자른다 */
    fun update(clock: Long) {
        if (!started) return
        val t = elapsed(clock).coerceAtMost(GameConfig.GAME_MS)
        for (z in 0 until playerCount) tracks[z].updateCatch(t, baskets[z], GameConfig.BASKET_HALF)
    }

    fun score(i: Int): Int = tracks[i].score

    /** 가장 높은 점수의 플레이어들 */
    fun leaders(): List<Int> {
        val best = (0 until playerCount).maxOf { score(it) }
        return (0 until playerCount).filter { score(it) == best }
    }

    /** 최종 순위 (players 순서, 1부터, 동점 허용) */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { score(it).toLong() })

    companion object {
        val MIN_BASKET = GameConfig.BASKET_HALF
        val MAX_BASKET = GameConfig.LANES - GameConfig.BASKET_HALF

        fun clampBasket(units: Float): Float =
            if (units.isNaN()) GameConfig.LANES / 2f else units.coerceIn(MIN_BASKET, MAX_BASKET)

        /** 종류별 받았을 때 점수 */
        fun scoreOf(kind: Int): Int = when (kind) {
            GameConfig.KIND_EGG -> GameConfig.EGG_SCORE
            GameConfig.KIND_GOLD -> GameConfig.GOLD_SCORE
            else -> GameConfig.BOMB_SCORE
        }

        fun patternConfig() = LanePatternConfig(
            lanes = GameConfig.LANES,
            startBpm = GameConfig.START_BPM,
            endBpm = GameConfig.END_BPM,
            rampMs = GameConfig.RAMP_MS,
            leadInMs = GameConfig.LEAD_IN_MS,
            durationMs = GameConfig.LAST_ITEM_MS,
            restChance = GameConfig.REST_CHANCE,
            offbeatChance = GameConfig.OFFBEAT_CHANCE,
            startFallMs = GameConfig.START_FALL_MS,
            endFallMs = GameConfig.END_FALL_MS,
            kindWeights = intArrayOf(GameConfig.EGG_WEIGHT, GameConfig.GOLD_WEIGHT, GameConfig.BOMB_WEIGHT),
            maxLaneStep = GameConfig.MAX_LANE_STEP,
            minGapMs = GameConfig.MIN_GAP_MS,
        )

        fun judgeConfig() = LaneJudgeConfig(
            catchScores = intArrayOf(GameConfig.EGG_SCORE, GameConfig.GOLD_SCORE, GameConfig.BOMB_SCORE),
            itemHalfWidth = GameConfig.ITEM_HALF,
            afterMs = GameConfig.AFTER_MS,
        )
    }
}
