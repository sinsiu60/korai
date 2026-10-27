package com.siwoo.party.games.rhythm

import com.siwoo.party.common.Ranking
import com.siwoo.party.common.lane.Judgement
import com.siwoo.party.common.lane.LaneJudgeConfig
import com.siwoo.party.common.lane.LanePattern
import com.siwoo.party.common.lane.LanePatternConfig
import com.siwoo.party.common.lane.LaneTrack

/**
 * 리듬 노트 규칙 (순수 Kotlin).
 * - 시드 하나로 만든 노트 패턴(LanePattern, 레인 1개)을 모든 구역이 함께 쓴다 → 모두 같은 노트, 같은 박자.
 * - 구역마다 LaneTrack 이 자기 판정·점수·콤보를 갖는다. 판정은 게임 시계(일시정지 시간을 뺀 시간)로.
 * - 판정: |어긋남| ≤ 60 퍼펙트 3점, ≤ 140 굿 1점, 그보다 이르게 ≤ 220 미스 0점(노트 소모·콤보 끊김),
 *   판정선을 140 넘게 지나간 노트도 미스. 노트에서 먼 헛손질은 무시(점수·콤보 그대로).
 *   한 노트는 한 번만 판정 → 두 번 쳐도 두 번 점수가 나지 않는다.
 * - 60초 뒤 점수 순. 같은 점수면 최대 콤보가 큰 쪽이 위, 그것도 같으면 같은 순위.
 *
 * 시간: start(clock) 의 clock 이 경기 0ms. tap/update/elapsed 는 같은 시계 값을 받는다.
 */
class RhythmGame(val playerCount: Int, val seed: Long) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    /** 모든 구역 공통 노트 패턴 */
    val pattern: LanePattern = LanePattern.generate(patternConfig(), seed)

    /** 구역(플레이어)별 진행 */
    val tracks: Array<LaneTrack> = Array(playerCount) { LaneTrack(pattern, judgeConfig(), zone = it) }

    /** 박자음 시각 (경기 시작 기준 ms, 오름차순): 준비 박자 + 패턴의 박자 격자 */
    val beats: LongArray = beatTimes(pattern.config)

    /** 준비 박자 수 (beats 앞쪽, 첫 노트 전) */
    val countIn: Int = beats.count { it < pattern.config.leadInMs }

    /** 박자 k 가 강박인지 (준비 박자는 약박) */
    fun isAccent(k: Int): Boolean {
        val n = k - countIn
        return n >= 0 && n % GameConfig.ACCENT_EVERY == 0
    }

    private var startClock = 0L
    var started = false
        private set

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

    /**
     * 구역 zone 이 clock 에 쳤다 → 판정 (경기 전·후 또는 헛손질이면 NONE).
     */
    fun tap(zone: Int, clock: Long): Judgement {
        if (zone !in 0 until playerCount || !isRunning(clock)) return Judgement.NONE
        return tracks[zone].judge(elapsed(clock))
    }

    /** 지나간 노트 미스 처리 (매 프레임) */
    fun update(clock: Long) {
        if (!started) return
        val t = elapsed(clock).coerceAtMost(GameConfig.GAME_MS)
        for (tr in tracks) tr.update(t)
    }

    fun score(i: Int): Int = tracks[i].score
    fun combo(i: Int): Int = tracks[i].combo
    fun maxCombo(i: Int): Int = tracks[i].maxCombo

    /** 지금 빠르기 (분당 박자) */
    fun bpmAt(clock: Long): Int = Math.round(LanePattern.bpmAt(pattern.config, elapsed(clock)))

    /** elapsedMs 까지 지난 박자 수 (beats 중 시각 ≤ elapsedMs 인 것) */
    fun beatsUpTo(elapsedMs: Long): Int {
        var lo = 0
        var hi = beats.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (beats[m] <= elapsedMs) lo = m + 1 else hi = m
        }
        return lo
    }

    /** 순위 정렬 값: 점수가 먼저, 같으면 최대 콤보 */
    fun rankKey(i: Int): Long = rankKey(score(i), maxCombo(i))

    /** 최종 순위 (players 순서, 1부터, 동점 허용) */
    fun finalRanks(): IntArray = Ranking.competition(List(playerCount) { rankKey(it) })

    companion object {
        fun patternConfig() = LanePatternConfig(
            lanes = 1,
            startBpm = GameConfig.START_BPM,
            endBpm = GameConfig.END_BPM,
            rampMs = GameConfig.RAMP_MS,
            leadInMs = GameConfig.LEAD_IN_MS,
            durationMs = GameConfig.LAST_NOTE_MS,
            restChance = GameConfig.REST_CHANCE,
            offbeatChance = GameConfig.OFFBEAT_CHANCE,
            startFallMs = GameConfig.START_FALL_MS,
            endFallMs = GameConfig.END_FALL_MS,
            minGapMs = GameConfig.MIN_GAP_MS,
        )

        fun judgeConfig() = LaneJudgeConfig(
            perfectMs = GameConfig.PERFECT_MS,
            goodMs = GameConfig.GOOD_MS,
            missMs = GameConfig.MISS_MS,
            perfectScore = GameConfig.PERFECT_SCORE,
            goodScore = GameConfig.GOOD_SCORE,
            missScore = GameConfig.MISS_SCORE,
        )

        fun rankKey(score: Int, maxCombo: Int): Long = score.toLong() * 1_000_000L + maxCombo

        /**
         * 박자 격자: LanePattern.generate 와 같은 계산(첫 박 leadIn, 다음 박 = 60000 / 그때 BPM)으로
         * 노트(정박)와 박자음이 정확히 같은 시각이 되게 한다. 앞에 준비 박자 COUNT_IN_BEATS 개 (시작 빠르기 간격).
         */
        fun beatTimes(c: LanePatternConfig): LongArray {
            val list = ArrayList<Long>()
            val first = 60_000.0 / LanePattern.bpmAt(c, 0L)
            for (k in GameConfig.COUNT_IN_BEATS downTo 1) {
                val t = (c.leadInMs - first * k).toLong()
                if (t >= 0) list.add(t)
            }
            var beat = c.leadInMs.toDouble()
            while (beat <= c.durationMs) {
                val t = beat.toLong()
                list.add(t)
                beat += 60_000.0 / LanePattern.bpmAt(c, t)
            }
            return list.toLongArray()
        }
    }
}
