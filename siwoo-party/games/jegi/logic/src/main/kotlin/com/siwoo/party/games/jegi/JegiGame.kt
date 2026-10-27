package com.siwoo.party.games.jegi

import com.siwoo.party.common.Ranking
import kotlin.math.abs
import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 도전 사이 (배너 등) — [JegiGame.startAttempt] 를 기다림 */
    WAIT,
    /** 제기가 날고 있음 — 터치로 차기 */
    FLYING,
    /** 이번 도전 끝 (놓침·완벽) — 제기가 땅에 떨어진 뒤 [JegiGame.endAttempt] */
    ENDED,
    /** 경기 끝 */
    OVER,
}

/** 터치 한 번의 결과 */
enum class Tap {
    /** 받지 않음 (제기가 날지 않을 때) */
    IGNORED,
    /** 제기가 아직 높이 있음 → 발만 옮김 */
    MOVED,
    /** 성공! (한 개 더) */
    KICK,
    /** 너무 일찍 참 → 도전 끝 */
    EARLY,
    /** 너무 늦음 (제기가 이미 발을 지나감) → 도전 끝 */
    LATE,
    /** 때는 맞았지만 발이 제기에 안 닿음 → 도전 끝 */
    OUT_OF_REACH,
}

/** 도전이 끝난 까닭 */
enum class EndReason { NONE, EARLY, LATE, REACH, PERFECT }

/**
 * 제기차기 규칙 (순수 Kotlin, 시간은 밖에서 넣는다).
 *
 * - 차례: 도전 1회차를 [first] 부터 번호 순으로 한 바퀴, 그다음 2회차 … (한 사람 [attempts] 번).
 * - 도전 시작([startAttempt]): 발은 가운데, 제기를 손으로 살짝 던져 올린다.
 * - 터치([tap]) = 발이 터치한 가로 자리로 옮겨 가서 찬다.
 *   · 제기가 발 높이에 오는 순간([Flight.idealTime])보다 [GameConfig.KICK_ZONE_MS] 넘게 이르면 → 발만 옮김(실패 아님).
 *   · 그 뒤로는 차기: 딱 좋은 순간 ± [window] 안(경계 포함)이고, 발 가운데 ± [GameConfig.REACH] 안에 제기가 있으면 성공 → 개수 +1,
 *     제기가 다시 떠오른다 (떨어질 자리가 ±[GameConfig.KICK_DRIFT] 안에서 무작위로 바뀜).
 *   · 구간보다 이르면 [Tap.EARLY], 발이 안 닿으면 [Tap.OUT_OF_REACH] → 도전 끝.
 * - 안 차고 구간이 지나가면 [update] 가 [EndReason.LATE] 로 도전을 끝낸다.
 * - 판정 구간은 찬 개수가 늘수록 줄어든다 ([windowFor]).
 * - 순위: 한 사람의 가장 좋은 기록 순. 같으면 나머지 도전 기록으로 가르고, 그래도 같으면 같은 순위.
 */
class JegiGame(
    val players: Int,
    seed: Long,
    val first: Int = 0,
    val attempts: Int = GameConfig.ATTEMPTS,
) {
    init {
        require(players in 1..GameConfig.MAX_PLAYERS)
        require(first in 0 until players)
        require(attempts >= 1)
    }

    private val random = Random(seed)

    /** records[p][a] = p 의 a 번째 도전 기록 (-1 = 아직) */
    val records: Array<IntArray> = Array(players) { IntArray(attempts) { -1 } }

    /** 전체 도전 순서에서 몇 번째 (0 부터) */
    var turn = 0
        private set
    var phase = Phase.WAIT
        private set

    /** 이번 도전에서 찬 개수 */
    var count = 0
        private set
    /** 발 가운데 가로 위치 */
    var footX = GameConfig.FOOT_START
        private set
    /** 지금 나는 제기 */
    var flight = Flight(0L, GameConfig.FOOT_START, 0f, GameConfig.FOOT_START, 0f)
        private set
    var endReason = EndReason.NONE
        private set
    /** 도전이 끝난 시각 (놓친 터치 · 구간이 지난 시각) */
    var endTime = 0L
        private set

    /** 지금 차례 사람 */
    val current: Int get() = (first + turn % players) % players

    /** 지금 몇 번째 도전 (0 부터) */
    val attempt: Int get() = minOf(turn / players, attempts - 1)

    val totalTurns: Int get() = players * attempts

    val over: Boolean get() = phase == Phase.OVER

    /** 지금 판정 구간 (± ms) */
    fun window(): Long = windowFor(count)

    /** 제기가 땅에 닿는 시각 (도전이 끝난 뒤 이때 기록 발표) */
    val landTime: Long get() = flight.groundTime

    fun jegiX(now: Long): Float = flight.x(now)
    fun jegiY(now: Long): Float = flight.y(now)

    /** 지금 터치하면 "차기" 인지 (아니면 발만 옮김) */
    fun inKickZone(now: Long): Boolean = now >= flight.idealTime - GameConfig.KICK_ZONE_MS

    /** 도전 시작: 발은 가운데, 제기를 살짝 던져 올림 */
    fun startAttempt(now: Long): Boolean {
        if (phase != Phase.WAIT) return false
        count = 0
        endReason = EndReason.NONE
        footX = GameConfig.FOOT_START
        val tx = clampTarget(footX + (random.nextFloat() * 2f - 1f) * GameConfig.TOSS_DRIFT)
        flight = Flight(now, footX, GameConfig.TOSS_Y, tx, randomPhase())
        phase = Phase.FLYING
        return true
    }

    /**
     * 시간이 흐름: 차지 않고 판정 구간이 지나가면 도전 끝 ([EndReason.LATE]).
     * 이번 호출로 끝났으면 true.
     */
    fun update(now: Long): Boolean {
        if (phase != Phase.FLYING) return false
        val deadline = flight.idealTime + window()
        if (now > deadline) {
            end(EndReason.LATE, deadline)
            return true
        }
        return false
    }

    /** 가로 x(마당 좌표)를 now 에 터치 → 발이 그 자리로 가서 참 */
    fun tap(x: Float, now: Long): Tap {
        if (phase != Phase.FLYING) return Tap.IGNORED
        if (update(now)) return Tap.LATE
        footX = x.coerceIn(GameConfig.FOOT_MIN, GameConfig.FOOT_MAX)
        val ideal = flight.idealTime
        if (now < ideal - GameConfig.KICK_ZONE_MS) return Tap.MOVED
        val w = window()
        if (now < ideal - w) {
            end(EndReason.EARLY, now)
            return Tap.EARLY
        }
        val jx = flight.x(now)
        if (!inReach(jx, footX)) {
            end(EndReason.REACH, now)
            return Tap.OUT_OF_REACH
        }
        // 성공: 제기가 다시 떠오른다
        count++
        val jy = flight.y(now)
        val tx = clampTarget(jx + (random.nextFloat() * 2f - 1f) * GameConfig.KICK_DRIFT)
        flight = Flight(now, jx, jy, tx, randomPhase())
        if (count >= GameConfig.MAX_KICKS) end(EndReason.PERFECT, now)
        return Tap.KICK
    }

    private fun end(reason: EndReason, at: Long) {
        endReason = reason
        endTime = at
        phase = Phase.ENDED
    }

    /** 끝난 도전의 기록을 남기고 다음 차례로. 경기가 끝났으면 true */
    fun endAttempt(): Boolean {
        if (phase != Phase.ENDED) return phase == Phase.OVER
        records[current][attempt] = count
        turn++
        phase = if (turn >= totalTurns) Phase.OVER else Phase.WAIT
        return phase == Phase.OVER
    }

    /** p 의 가장 좋은 기록 (아직 없으면 -1) */
    fun best(p: Int): Int = records[p].maxOrNull() ?: -1

    /** players 순서의 순위 (1 부터, 가장 좋은 기록 → 나머지 기록, 같으면 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(records.map { rankKey(it) })

    private fun randomPhase(): Float = random.nextFloat() * TWO_PI

    companion object {
        private const val TWO_PI = (2.0 * Math.PI).toFloat()

        /** 찬 개수 → 판정 구간 (± ms): 한 개마다 줄어들고 최소값에서 멈춘다 */
        fun windowFor(count: Int): Long =
            (GameConfig.WINDOW_START_MS - count.coerceAtLeast(0).toLong() * GameConfig.WINDOW_SHRINK_MS)
                .coerceAtLeast(GameConfig.WINDOW_MIN_MS)

        /** 발 가운데에서 제기까지 가로 거리가 닿는 거리 안인지 (경계 포함) */
        fun inReach(jegiX: Float, footX: Float): Boolean = abs(jegiX - footX) <= GameConfig.REACH

        /** 떨어질 자리를 흔들림까지 마당 안에 들어오게 */
        fun clampTarget(x: Float): Float =
            x.coerceIn(GameConfig.JEGI_MIN + 2f * GameConfig.SWAY_AMP, GameConfig.JEGI_MAX - 2f * GameConfig.SWAY_AMP)

        /**
         * 순위 비교 값: 기록을 큰 순으로 늘어놓고 자릿값(128)으로 묶는다 → 가장 좋은 기록이 먼저, 같으면 다음 기록.
         * (기록은 [GameConfig.MAX_KICKS] ≤ 127, 아직 안 한 도전은 0)
         */
        fun rankKey(rec: IntArray): Long {
            val sorted = rec.map { it.coerceAtLeast(0) }.sortedDescending()
            var key = 0L
            for (v in sorted) key = key * 128 + v
            return key
        }
    }
}
