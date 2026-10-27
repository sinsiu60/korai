package com.siwoo.party.games.bobber

import com.siwoo.party.common.Ranking
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** 가짜 입질 종류 */
object FakeKind {
    /** 찌가 좌우로 살랑살랑 흔들림 */
    const val WOBBLE = 0
    /** 찌가 톡톡 살짝 잠겼다 올라옴 */
    const val DIP = 1
}

/**
 * 한 라운드의 입질 예정표 (모든 구역 공통 — 구역마다 따로 만들지 않는다).
 * 시각은 라운드 시작 기준 ms. fakeAt[k] 부터 FAKE_MS 동안 가짜 입질, biteAt 에 진짜 입질(쑥!).
 * 찌의 움직임(sinkAt / tiltAt)도 시각만의 함수라 어느 구역에서 그려도 똑같다.
 * 배열이라 그리기 경로에서 할당 없이 읽을 수 있다.
 */
class BiteSchedule(val fakeAt: LongArray, val fakeKind: IntArray, val biteAt: Long) {

    init {
        require(fakeAt.size == fakeKind.size)
    }

    val fakeCount: Int get() = fakeAt.size

    /** 아무도 안 누를 때 라운드가 끝나는 시각 */
    val endAt: Long get() = biteAt + GameConfig.BITE_WINDOW_MS

    /** 시각 t 에 진행 중인 가짜 입질 번호 (없으면 -1) */
    fun fakeIndexAt(t: Long): Int {
        for (k in fakeAt.indices) if (t >= fakeAt[k] && t < fakeAt[k] + GameConfig.FAKE_MS) return k
        return -1
    }

    fun isFakeAt(t: Long): Boolean = fakeIndexAt(t) >= 0

    /** 진짜 입질이 왔는지 */
    fun isBitten(t: Long): Boolean = t >= biteAt

    /**
     * 찌가 잠긴 정도 (0 = 떠 있음, 1 = 몸통이 다 잠김, 음수 = 살짝 떠오름).
     * 기다리는 동안 물결에 아주 살짝 오르내림 + 가짜 입질 톡톡 + 진짜 입질 쑥.
     */
    fun sinkAt(t: Long): Float {
        if (t >= biteAt) {
            val f = ((t - biteAt).toFloat() / GameConfig.PLUNGE_MS).coerceIn(0f, 1f)
            val e = 1f - (1f - f) * (1f - f) // 빠르게 쑥
            return idle(t) * (1f - e) + e
        }
        var s = idle(t)
        val k = fakeIndexAt(t)
        if (k >= 0) {
            val f = (t - fakeAt[k]).toFloat() / GameConfig.FAKE_MS
            s += if (fakeKind[k] == FakeKind.DIP) {
                // 톡, 톡 두 번
                GameConfig.DIP_DEPTH * abs(sin(2.0 * PI * f)).toFloat()
            } else {
                GameConfig.WOBBLE_DEPTH * sin(PI * f).toFloat()
            }
        }
        return s
    }

    /** 찌 기울기 (-1..1, 흔들 가짜 입질 때만) */
    fun tiltAt(t: Long): Float {
        if (t >= biteAt) return 0f
        val k = fakeIndexAt(t)
        if (k < 0 || fakeKind[k] != FakeKind.WOBBLE) return 0f
        val f = (t - fakeAt[k]).toFloat() / GameConfig.FAKE_MS
        return (sin(2.0 * PI * GameConfig.WOBBLE_CYCLES * f) * sin(PI * f)).toFloat()
    }

    private fun idle(t: Long): Float =
        GameConfig.IDLE_BOB * sin(2.0 * PI * t / GameConfig.IDLE_BOB_PERIOD_MS).toFloat()

    companion object {
        /** 무작위 예정표 (같은 난수 → 같은 예정표) */
        fun generate(random: Random): BiteSchedule {
            val n = GameConfig.FAKES_MIN + random.nextInt(GameConfig.FAKES_MAX - GameConfig.FAKES_MIN + 1)
            val at = LongArray(n)
            val kind = IntArray(n)
            var t = between(random, GameConfig.FIRST_MIN_MS, GameConfig.FIRST_MAX_MS)
            for (k in 0 until n) {
                at[k] = t
                kind[k] = if (random.nextBoolean()) FakeKind.DIP else FakeKind.WOBBLE
                t += GameConfig.FAKE_MS + between(random, GameConfig.GAP_MIN_MS, GameConfig.GAP_MAX_MS)
            }
            return BiteSchedule(at, kind, t)
        }

        private fun between(random: Random, lo: Long, hi: Long): Long = lo + random.nextLong(hi - lo + 1)
    }
}

/** 라운드 진행 단계 */
enum class Phase { IDLE, OPEN, DONE }

/** 터치 결과 */
sealed class Tap {
    /** 무시 (라운드 밖·이미 실격·이미 누름·너무 늦음) */
    object Ignored : Tap()
    /** 진짜 입질 전에 누름 → 이번 라운드 실격. duringFake = 가짜 입질 중이었는지, allOut = 전원 실격 */
    class Early(val player: Int, val duringFake: Boolean, val allOut: Boolean) : Tap()
    /** 진짜 입질 뒤 정상 터치. first = 이번 라운드 첫 정상 터치 (아직 1등 확정 아님 — resolve 에서 시각으로 가림) */
    class Valid(val player: Int, val reactionMs: Long, val first: Boolean) : Tap()
}

/** 라운드 결과 */
enum class Outcome {
    /** 누군가 잡음 */
    CAUGHT,
    /** 1.5초 안에 아무도 안 누름 */
    NO_TAP,
    /** 전원 실격 */
    ALL_OUT,
}

/** winners = 가장 빨리 누른 사람(같은 ms 면 모두), bestMs = 그 반응 시간 (없으면 -1) */
class RoundResult(val outcome: Outcome, val winners: IntArray, val bestMs: Long)

/**
 * 낚시 찌 대결 규칙.
 * - 라운드: 가짜 입질 0~3번(살짝 흔들림·톡톡) 뒤 진짜 입질(쑥!). 예정표는 모든 구역 공통
 * - 진짜 입질 전에 누르면 그 라운드만 실격, 진짜 입질 뒤 가장 빨리 누른 사람 +1 (터치 시각으로 판정)
 * - 1.5초 안에 아무도 안 누르거나 전원 실격이면 점수 없이 다음 라운드 (라운드 수에는 들어감)
 * - 10라운드 뒤 잡은 물고기 수로 순위 (같으면 공동 순위)
 * 시각은 화면이 넘겨준다 (라운드 시작 기준 ms) → 테스트에서 그대로 재현 가능.
 */
class BobberGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 잡은 물고기 수 */
    val score = IntArray(playerCount)
    /** 잡은 라운드 반응 시간 합 (기록·표시용, 순위에는 안 씀) */
    val caughtMs = LongArray(playerCount)
    /** 실격 횟수 (기록용) */
    val earlyCount = IntArray(playerCount)

    var roundsDone = 0
        private set
    var phase = Phase.IDLE
        private set
    /** 이번 라운드 예정표 */
    var schedule: BiteSchedule? = null
        private set
    /** 이번 라운드 실격 */
    val disqualified = BooleanArray(playerCount)
    /** 이번 라운드 반응 시간 (-1 = 안 누름) */
    val reaction = LongArray(playerCount) { -1L }
    var lastResult: RoundResult? = null
        private set

    val gameOver: Boolean get() = roundsDone >= GameConfig.ROUNDS
    /** 지금 하는(라운드 사이엔 다음) 라운드 번호 (1부터, 끝나면 마지막 라운드) */
    val roundNumber: Int get() = (roundsDone + 1).coerceAtMost(GameConfig.ROUNDS)

    /** 새 라운드: 예정표를 정한다 */
    fun startRound(): BiteSchedule {
        check(!gameOver) { "게임이 끝났습니다" }
        check(phase != Phase.OPEN) { "라운드 진행 중" }
        disqualified.fill(false)
        reaction.fill(-1L)
        lastResult = null
        val s = BiteSchedule.generate(random)
        schedule = s
        phase = Phase.OPEN
        return s
    }

    /** 정상 터치가 하나라도 있는지 */
    fun hasValidTap(): Boolean = reaction.any { it >= 0 }

    /** 실격 아닌 사람이 모두 눌렀는지 */
    fun allResponded(): Boolean = (0 until playerCount).all { disqualified[it] || reaction[it] >= 0 }

    /** 플레이어 터치. tMs = 라운드 시작 기준 터치 시각 */
    fun touch(player: Int, tMs: Long): Tap {
        val s = schedule
        if (s == null || phase != Phase.OPEN || player !in 0 until playerCount) return Tap.Ignored
        if (disqualified[player] || reaction[player] >= 0) return Tap.Ignored
        if (tMs < s.biteAt) {
            disqualified[player] = true
            earlyCount[player]++
            return Tap.Early(player, s.isFakeAt(tMs), disqualified.all { it })
        }
        if (tMs > s.endAt) return Tap.Ignored
        val first = !hasValidTap()
        val ms = tMs - s.biteAt
        reaction[player] = ms
        return Tap.Valid(player, ms, first)
    }

    /** 라운드 마무리: 가장 빠른 사람(같은 ms 면 모두) +1. 라운드 수에 들어간다 */
    fun resolve(): RoundResult {
        check(phase == Phase.OPEN) { "진행 중인 라운드가 없습니다" }
        var best = -1L
        for (p in 0 until playerCount) {
            val r = reaction[p]
            if (r >= 0 && (best < 0 || r < best)) best = r
        }
        val result = when {
            best >= 0 -> {
                val w = (0 until playerCount).filter { reaction[it] == best }.toIntArray()
                for (p in w) {
                    score[p]++
                    caughtMs[p] += best
                }
                RoundResult(Outcome.CAUGHT, w, best)
            }
            disqualified.all { it } -> RoundResult(Outcome.ALL_OUT, IntArray(0), -1)
            else -> RoundResult(Outcome.NO_TAP, IntArray(0), -1)
        }
        roundsDone++
        phase = Phase.DONE
        lastResult = result
        return result
    }

    /** 최종 순위 (1부터, 동점 허용): 잡은 물고기 수 많은 순. 반응 시간으로 동점을 가르지 않는다 */
    fun finalRanks(): IntArray = Ranking.competition(score.map { it.toLong() })
}
