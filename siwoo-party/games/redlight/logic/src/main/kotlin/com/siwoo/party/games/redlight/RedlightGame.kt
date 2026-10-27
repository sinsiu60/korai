package com.siwoo.party.games.redlight

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 단계: 시작 전 / 술래가 뒤돌아 외치는 중 / 술래가 쳐다보는 중 / 끝 */
enum class Phase { READY, CHANT, LOOK, OVER }

/**
 * 한 바퀴 예정 (모든 시간은 그 바퀴 시작 기준 ms).
 * letterAt[i] = i번째 글자가 나타나는 시각, turnAt = 술래가 돌아보는 시각, lookMs = 쳐다보는 시간,
 * speedUpAt = 이 글자부터 빨라짐 (-1 = 안 빨라짐)
 */
class CyclePlan(val letterAt: LongArray, val turnAt: Long, val lookMs: Long, val speedUpAt: Int) {
    /** 글자 사이 간격 (첫 글자는 바퀴 시작부터) */
    fun interval(i: Int): Long = if (i == 0) letterAt[0] else letterAt[i] - letterAt[i - 1]

    companion object {
        fun random(random: Random): CyclePlan {
            val n = GameConfig.PHRASE_LEN
            val base = GameConfig.LETTER_MIN_MS + random.nextDouble() * (GameConfig.LETTER_MAX_MS - GameConfig.LETTER_MIN_MS)
            val speedUp = if (random.nextDouble() < GameConfig.SPEEDUP_CHANCE) {
                GameConfig.SPEEDUP_FROM + random.nextInt(GameConfig.SPEEDUP_TO - GameConfig.SPEEDUP_FROM + 1)
            } else -1
            val at = LongArray(n)
            var t = 0L
            for (i in 0 until n) {
                val jitter = 1.0 + (random.nextDouble() * 2 - 1) * GameConfig.LETTER_JITTER
                var d = (base * jitter).toLong()
                if (speedUp in 0..i) d = (base * GameConfig.SPEEDUP_FACTOR * jitter).toLong().coerceAtLeast(GameConfig.LETTER_FAST_MIN_MS)
                t += d.coerceAtLeast(1)
                at[i] = t
            }
            val look = GameConfig.LOOK_MIN_MS + (random.nextDouble() * (GameConfig.LOOK_MAX_MS - GameConfig.LOOK_MIN_MS)).toLong()
            return CyclePlan(at, t + GameConfig.TURN_DELAY_MS, look, speedUp)
        }
    }
}

/**
 * 무궁화 꽃이 피었습니다 규칙.
 * - 자기 구역을 누르고 있는 동안(술래가 뒤돌아 있을 때만) 말이 앞으로 간다. 출발점 0 → 술래 1.
 * - 술래가 글자를 한 글자씩 외친다(바퀴마다 속도 무작위, 중간에 빨라지기도). 마지막 글자 뒤 돌아본다.
 * - 돌아본 뒤 GRACE_MS 가 지났을 때 누르고 있는 사람(또는 그 뒤에 누른 사람)은 출발점으로.
 * - 먼저 도착한 순서대로 순위. 한 명만 남으면 끝(그 사람이 꼴찌). 제한 시간이면 도착한 사람 → 진행 거리 순.
 * 시간은 화면이 넘겨준다(게임 시계 ms) → update 를 몇 번에 나눠 부르든 결과가 같다.
 */
class RedlightGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 진행 거리 0..1 */
    val progress = DoubleArray(playerCount)
    /** 지금 누르고 있는지 */
    val holding = BooleanArray(playerCount)
    /** 도착 시각 (시작 기준 ms, -1 = 아직) */
    val arrivedAt = LongArray(playerCount) { -1L }
    /** 걸린 횟수 */
    val caughtCount = IntArray(playerCount)
    /** 마지막으로 걸렸을 때의 위치 (화면이 되돌아가는 연출에 사용) */
    val caughtFrom = DoubleArray(playerCount)
    /** 도착한 순서 (플레이어 번호) */
    val arrivalOrder = ArrayList<Int>()

    var phase = Phase.READY
        private set
    lateinit var plan: CyclePlan
        private set
    /** 지금 바퀴 시작 시각 / 술래가 돌아본 시각 (게임 시계) */
    var cycleStart = 0L
        private set
    var lookStart = 0L
        private set
    /** 몇 번째 바퀴 (0부터) */
    var cycle = 0
        private set
    var timeUp = false
        private set
    private var graceDone = false
    private var startAt = 0L
    /** 여기까지 계산함 (게임 시계) */
    var cursor = 0L
        private set

    val isOver: Boolean get() = phase == Phase.OVER
    val arrivedCount: Int get() = arrivalOrder.size
    fun arrived(p: Int): Boolean = arrivedAt[p] >= 0

    fun start(now: Long) {
        check(phase == Phase.READY)
        startAt = now
        cursor = now
        newCycle(now)
    }

    private fun newCycle(at: Long) {
        cycleStart = at
        plan = CyclePlan.random(random)
        phase = Phase.CHANT
        graceDone = false
    }

    /** 술래가 쳐다보고 있고 유예도 끝났는지 (이때 누르면 걸린다) */
    val watching: Boolean get() = phase == Phase.LOOK && graceDone

    /** 지금 나타난 글자 수 (술래가 쳐다보는 동안은 전부) */
    val lettersShown: Int
        get() = when (phase) {
            Phase.CHANT -> {
                val t = cursor - cycleStart
                var n = 0
                while (n < GameConfig.PHRASE_LEN && plan.letterAt[n] <= t) n++
                n
            }
            Phase.LOOK -> GameConfig.PHRASE_LEN
            else -> 0
        }

    /** 남은 제한 시간 (update 로 계산한 시각 기준) */
    fun remainingMs(): Long =
        if (phase == Phase.READY) GameConfig.TIME_LIMIT_MS
        else (startAt + GameConfig.TIME_LIMIT_MS - cursor).coerceAtLeast(0)

    /** 누르기 시작. 술래가 쳐다보는 중(유예 뒤)이면 바로 걸려 true */
    fun press(p: Int, now: Long): Boolean {
        update(now)
        if (p !in 0 until playerCount || phase == Phase.READY || isOver || arrived(p)) return false
        holding[p] = true
        if (watching) {
            catch(p)
            return true
        }
        return false
    }

    fun release(p: Int, now: Long) {
        update(now)
        if (p in 0 until playerCount) holding[p] = false
    }

    /** 일시정지 등: 모두 손 뗀 것으로 */
    fun releaseAll(now: Long) {
        update(now)
        holding.fill(false)
    }

    private fun nextEvent(): Long = when (phase) {
        Phase.CHANT -> cycleStart + plan.turnAt
        Phase.LOOK -> if (graceDone) lookStart + plan.lookMs else lookStart + GameConfig.GRACE_MS
        else -> Long.MAX_VALUE
    }

    /** now(게임 시계)까지 진행 */
    fun update(now: Long) {
        if (phase == Phase.READY || phase == Phase.OVER) return
        val limit = startAt + GameConfig.TIME_LIMIT_MS
        val end = minOf(now, limit)
        while (cursor < end && phase != Phase.OVER) {
            val next = nextEvent()
            val segEnd = minOf(end, next)
            if (phase == Phase.CHANT) move(cursor, segEnd)
            cursor = segEnd
            if (phase == Phase.OVER) break
            if (segEnd >= next) transition(next)
        }
        // 정확히 지금 일어나는 일 (cursor == next)
        while (phase != Phase.OVER && cursor == end && nextEvent() <= end) transition(nextEvent())
        if (phase != Phase.OVER && now >= limit) {
            cursor = limit
            timeUp = true
            phase = Phase.OVER
        }
    }

    private fun transition(at: Long) {
        when (phase) {
            Phase.CHANT -> {
                phase = Phase.LOOK
                lookStart = at
                graceDone = false
            }
            Phase.LOOK -> if (!graceDone) {
                graceDone = true
                for (p in 0 until playerCount) if (holding[p] && !arrived(p)) catch(p)
            } else {
                cycle++
                newCycle(at)
            }
            else -> Unit
        }
    }

    private fun catch(p: Int) {
        caughtFrom[p] = progress[p]
        progress[p] = 0.0
        caughtCount[p]++
    }

    private fun move(t0: Long, t1: Long) {
        if (t1 <= t0) return
        val step = (t1 - t0).toDouble() / GameConfig.TRACK_MS
        for (p in 0 until playerCount) {
            if (!holding[p] || arrived(p)) continue
            val np = progress[p] + step
            if (np >= 1.0 - EPS) {
                val need = ((1.0 - progress[p]) * GameConfig.TRACK_MS)
                arrivedAt[p] = t0 + Math.round(need) - startAt
                progress[p] = 1.0
                holding[p] = false
            } else {
                progress[p] = np
            }
        }
        // 같은 구간에 도착한 사람은 도착 시각 순으로 기록
        if (arrivalOrder.size < countArrived()) {
            val fresh = (0 until playerCount).filter { arrived(it) && it !in arrivalOrder }.sortedBy { arrivedAt[it] }
            arrivalOrder.addAll(fresh)
            if (arrivalOrder.size >= playerCount - 1) phase = Phase.OVER
        }
    }

    private fun countArrived(): Int {
        var n = 0
        for (p in 0 until playerCount) if (arrived(p)) n++
        return n
    }

    /** 최종 순위 (1부터, 동점 허용): 도착한 사람은 빨리 도착한 순 → 나머지는 진행 거리 순 */
    fun finalRanks(): IntArray {
        val keys = (0 until playerCount).map { p ->
            if (arrived(p)) ARRIVED_BASE - arrivedAt[p]
            else Math.round(progress[p] * PROGRESS_SCALE)
        }
        return Ranking.competition(keys)
    }

    companion object {
        private const val EPS = 1e-9
        private const val ARRIVED_BASE = 1_000_000_000_000L
        private const val PROGRESS_SCALE = 1_000_000.0
    }
}
