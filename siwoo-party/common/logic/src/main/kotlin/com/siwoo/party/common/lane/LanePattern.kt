package com.siwoo.party.common.lane

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/**
 * 떨어지는 물체 패턴 설정 (박자 기반).
 * 시간은 모두 게임 시간 ms, 0 = 게임 시작. 물체의 "시간" = 판정선(받는 선)에 닿는 순간.
 */
class LanePatternConfig(
    /** 구역 하나의 레인 수 (리듬 1개, 계란 받기 3~5개) */
    var lanes: Int = 1,
    /** 시작·끝 빠르기 (분당 박자). rampMs 동안 직선으로 빨라진 뒤 endBpm 유지 */
    var startBpm: Float = 90f,
    var endBpm: Float = 150f,
    var rampMs: Long = 60_000L,
    /** 첫 물체가 판정선에 닿는 시간 */
    var leadInMs: Long = 2_000L,
    /** 이 시간 이후로는 물체를 만들지 않는다 */
    var durationMs: Long = 60_000L,
    /** 박자마다 쉴 확률 (0 = 매 박자 물체) */
    var restChance: Float = 0.15f,
    /** 반 박자(엇박) 물체 확률 — 시작 0 에서 rampMs 동안 이 값까지 늘어남 */
    var offbeatChance: Float = 0f,
    /** 판정선까지 떨어지는 시간: 시작 → 끝 (rampMs 동안 짧아짐 = 빨라짐). 끝 ≤ 시작이어야 추월이 없다 */
    var startFallMs: Long = 2_000L,
    var endFallMs: Long = 1_200L,
    /** 종류별 가중치 (예: 계란 [80, 8, 12] = 보통·황금·폭탄). 종류 번호 = 배열 위치 */
    var kindWeights: IntArray = intArrayOf(1),
    /** 이전 물체에서 레인이 최대 몇 칸 옮겨 갈 수 있는지 (바구니가 따라갈 수 있게) */
    var maxLaneStep: Int = Int.MAX_VALUE,
    /** 같은 순간(반 박자 포함) 물체 사이 최소 간격 ms — 이보다 가까우면 만들지 않는다 */
    var minGapMs: Long = 120L,
)

/**
 * 시드로 만든 떨어지는 물체 목록 (시간순, 만든 뒤에는 바뀌지 않음).
 * 모든 구역이 같은 LanePattern 하나를 함께 쓰고, 구역마다 LaneTrack 으로 맞춤/놓침 상태만 따로 갖는다 → 공정.
 * 게임 시작 때 한 번 만든다 (이때만 배열 할당).
 */
class LanePattern private constructor(
    val config: LanePatternConfig,
    val seed: Long,
    private val times: LongArray,
    private val laneArr: IntArray,
    private val kindArr: IntArray,
    private val fallArr: LongArray,
    /** 물체 수 */
    val count: Int,
) {
    val lanes: Int get() = config.lanes

    /** i 번째 물체가 판정선에 닿는 시간 */
    fun time(i: Int): Long = times[i]
    fun lane(i: Int): Int = laneArr[i]
    fun kind(i: Int): Int = kindArr[i]
    /** 생성(맨 위)→판정선까지 걸리는 시간 */
    fun fallMs(i: Int): Long = fallArr[i]
    /** 맨 위에 나타나는 시간 */
    fun spawnTime(i: Int): Long = times[i] - fallArr[i]

    /** 마지막 물체 시간 (없으면 0) */
    val lastTime: Long get() = if (count == 0) 0L else times[count - 1]

    /**
     * 위치 0..1: 0 = 맨 위(생성), 1 = 판정선. 판정선을 지나면 1 보다 커진다 (같은 속도로 계속 떨어짐).
     * 아직 안 나타났으면 음수.
     */
    fun position(i: Int, nowMs: Long): Float = 1f - (times[i] - nowMs).toFloat() / fallArr[i].toFloat()

    /** time ≥ t 인 첫 물체 번호 (없으면 count) — 이진 탐색 */
    fun firstAtOrAfter(t: Long): Int {
        var lo = 0; var hi = count
        while (lo < hi) { val m = (lo + hi) ushr 1; if (times[m] < t) lo = m + 1 else hi = m }
        return lo
    }

    /** spawnTime ≤ t 인 물체 수 (= 이미 나타난 물체는 0 until 이 값). 떨어지는 시간이 줄기만 하므로 정렬돼 있다 */
    fun spawnedCount(t: Long): Int {
        var lo = 0; var hi = count
        while (lo < hi) { val m = (lo + hi) ushr 1; if (times[m] - fallArr[m] <= t) lo = m + 1 else hi = m }
        return lo
    }

    companion object {
        /** t 시점 빠르기 (분당 박자) */
        fun bpmAt(c: LanePatternConfig, t: Long): Float = lerp(c.startBpm, c.endBpm, progress(c, t))

        /** t 시점 떨어지는 시간 */
        fun fallAt(c: LanePatternConfig, t: Long): Long =
            lerp(c.startFallMs.toFloat(), c.endFallMs.toFloat(), progress(c, t)).toLong().coerceAtLeast(1L)

        /** 0..1 빨라진 정도 */
        fun progress(c: LanePatternConfig, t: Long): Float =
            if (c.rampMs <= 0) 1f else (t.toFloat() / c.rampMs).coerceIn(0f, 1f)

        /** 같은 config + seed → 언제나 같은 패턴 */
        fun generate(config: LanePatternConfig, seed: Long): LanePattern {
            require(config.lanes >= 1) { "lanes >= 1" }
            require(config.startBpm > 0f && config.endBpm > 0f) { "bpm > 0" }
            require(config.kindWeights.isNotEmpty() && config.kindWeights.all { it >= 0 } && config.kindWeights.sum() > 0) { "kindWeights" }
            val rnd = Random(seed)
            val minStep = 60_000f / maxOf(config.startBpm, config.endBpm)
            val span = (config.durationMs - config.leadInMs).coerceAtLeast(0L)
            val cap = (ceil(span / minStep).toInt() + 2) * 2
            val times = LongArray(cap)
            val lanes = IntArray(cap)
            val kinds = IntArray(cap)
            val falls = LongArray(cap)
            var n = 0
            var prevLane = rnd.nextInt(config.lanes)
            var lastT = Long.MIN_VALUE
            var beat = config.leadInMs.toDouble()
            val totalWeight = config.kindWeights.sum()

            fun add(t: Long) {
                if (n >= cap || t > config.durationMs) return
                if (lastT != Long.MIN_VALUE && t - lastT < config.minGapMs) return
                val step = config.maxLaneStep.coerceAtLeast(0)
                val lo = if (step >= config.lanes) 0 else maxOf(0, prevLane - step)
                val hi = if (step >= config.lanes) config.lanes - 1 else minOf(config.lanes - 1, prevLane + step)
                val lane = lo + rnd.nextInt(hi - lo + 1)
                var r = rnd.nextInt(totalWeight)
                var k = 0
                while (r >= config.kindWeights[k]) { r -= config.kindWeights[k]; k++ }
                times[n] = t; lanes[n] = lane; kinds[n] = k; falls[n] = fallAt(config, t)
                n++
                prevLane = lane
                lastT = t
            }

            var first = true
            while (beat <= config.durationMs && n < cap) {
                val t = beat.toLong()
                val step = 60_000.0 / bpmAt(config, t)
                // 첫 박자는 항상 물체 (시작을 알 수 있게)
                if (first || rnd.nextFloat() >= config.restChance) add(t)
                first = false
                val off = config.offbeatChance * progress(config, t)
                if (off > 0f && rnd.nextFloat() < off) add((beat + step / 2).toLong())
                beat += step
            }
            return LanePattern(config, seed, times, lanes, kinds, falls, n)
        }

        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
    }

    /** 디버그용 요약 */
    override fun toString(): String = "LanePattern(count=$count, seed=$seed, last=${lastTime}ms, lanes=$lanes)"

    /** 두 패턴이 같은 물체 목록인지 (테스트용) */
    fun sameAs(o: LanePattern): Boolean {
        if (count != o.count) return false
        for (i in 0 until count) {
            if (times[i] != o.times[i] || laneArr[i] != o.laneArr[i] || kindArr[i] != o.kindArr[i] || abs(fallArr[i] - o.fallArr[i]) > 0) return false
        }
        return true
    }
}
