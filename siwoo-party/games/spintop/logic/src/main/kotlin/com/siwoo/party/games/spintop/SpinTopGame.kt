package com.siwoo.party.games.spintop

import com.siwoo.party.common.aim.Easing
import com.siwoo.party.common.aim.Gauge
import kotlin.random.Random

/**
 * 진행 단계.
 * READY(준비할 사람이 줄 감기 버튼을 누르길 기다림) → MASH(5초 연타) → GAUGE(던지기 게이지) → SHOW(결과 표시)
 * → (다음 사람 READY …) → LAUNCH(모두 준비 끝, 던지기 직전) → BATTLE(대결) → ROUND_END(판 결과) → 다음 판 READY … → OVER
 */
enum class Phase { READY, MASH, GAUGE, SHOW, LAUNCH, BATTLE, ROUND_END, OVER }

/** 연타 결과 */
enum class MashTap { STARTED, COUNTED, IGNORED }

/** 한 판의 결과 */
class RoundResult(val ranks: IntArray, val points: IntArray, val timeUp: Boolean)

/**
 * 팽이 대결 규칙 (순수 Kotlin). 시간(ms)·난수는 밖에서 넣는다.
 * 판마다: 한 사람씩 준비(줄 감기 연타 → 회전력, 던지기 게이지 → 안정성) → 모두 동시에 던져 대결(Arena) → 쓰러진 순서로 판 순위·점수.
 * ROUNDS 판 합계 점수로 최종 순위.
 * 줄 감기 5초는 화면이 타이머로 재고 [endMash] 를 부른다 (일시정지 시간 제외).
 */
class SpinTopGame(val players: Int, random: Random = Random.Default, capMs: Long = GameConfig.BATTLE_CAP_MS) {

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    var phase = Phase.READY
        private set
    /** 지금 판 (0부터) */
    var round = 0
        private set
    /** 이번 판에서 몇 번째로 준비하는 사람인지 (0부터) */
    var prepIndex = 0
        private set

    /** 이번 판 준비 순서의 첫 사람 (판마다 한 칸씩 돌아감) */
    val firstPrep: Int get() = round % players
    /** 지금 준비하는 사람 */
    val preparer: Int get() = (firstPrep + prepIndex) % players

    /** 이번 판 연타 수·회전력·안정성·게이지 멈춘 값 */
    val taps = IntArray(players)
    val powers = FloatArray(players)
    val stabilities = FloatArray(players)
    val gaugeValues = FloatArray(players)
    /** 이번 판 준비를 마쳤는지 */
    val prepped = BooleanArray(players)

    /** 던지기 게이지 (가운데가 최적) */
    val gauge = Gauge(GameConfig.GAUGE_PERIOD_MS, Easing.LINEAR, listOf(SpinRules.sweetZone))

    /** 경기장 (판마다 같은 객체를 다시 씀) */
    val arena = Arena(random, capMs)
    val tops = List(players) { Top(it, it) }

    /** 판 결과 (null = 아직) · 합계 점수 */
    val results = arrayOfNulls<RoundResult>(GameConfig.ROUNDS)
    val totals = IntArray(players)

    val lastRound: Boolean get() = round >= GameConfig.ROUNDS - 1
    val lastResult: RoundResult? get() = results[round]

    init {
        arena.setTops(tops)
    }

    // ── 준비 ① 줄 감기 ──

    /** 줄 감기 버튼 한 번. READY 면 시작(첫 연타로 셈) → STARTED, MASH 중이면 COUNTED */
    fun mashTap(): MashTap {
        val p = preparer
        when (phase) {
            Phase.READY -> {
                phase = Phase.MASH
                taps[p] = 1
                return MashTap.STARTED
            }
            Phase.MASH -> {
                if (taps[p] >= GameConfig.MASH_MAX_TAPS) return MashTap.IGNORED
                taps[p]++
                return MashTap.COUNTED
            }
            else -> return MashTap.IGNORED
        }
    }

    /** 지금 사람의 회전력 (연타 수로 계산) */
    val currentPower: Float get() = SpinRules.spinPower(taps[preparer])

    /** 줄 감기 끝 (5초) → 회전력 확정, 게이지 단계로 (게이지는 [startGauge] 로 움직임) */
    fun endMash(): Float {
        if (phase != Phase.MASH) return powers[preparer]
        val p = preparer
        powers[p] = SpinRules.spinPower(taps[p])
        phase = Phase.GAUGE
        gauge.reset()
        return powers[p]
    }

    // ── 준비 ② 던지기 게이지 ──

    fun startGauge(nowMs: Long) {
        if (phase != Phase.GAUGE) return
        gauge.start(nowMs)
    }

    /** 게이지가 움직이고 있어 터치를 받는지 */
    val gaugeLive: Boolean get() = phase == Phase.GAUGE && gauge.running

    /** 게이지 멈춤 → 안정성 확정 (게이지가 안 움직이면 -1) */
    fun stopGauge(nowMs: Long): Float {
        if (!gaugeLive) return -1f
        val p = preparer
        val v = gauge.stop(nowMs)
        gaugeValues[p] = v
        stabilities[p] = SpinRules.stability(v)
        prepped[p] = true
        phase = Phase.SHOW
        return stabilities[p]
    }

    /** 다음 사람 준비로. 모두 끝났으면 LAUNCH 로 가고 false */
    fun nextPrep(): Boolean {
        if (phase != Phase.SHOW) return phase == Phase.READY
        if (prepIndex + 1 < players) {
            prepIndex++
            phase = Phase.READY
            return true
        }
        phase = Phase.LAUNCH
        return false
    }

    // ── 대결 ──

    /** 모두 동시에 던짐 */
    fun startBattle() {
        if (phase != Phase.LAUNCH) return
        arena.launch(powers, stabilities)
        phase = Phase.BATTLE
    }

    /** 프레임마다: dtMs 만큼 물리 진행. 대결이 끝나면 판 결과를 정하고 false */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.BATTLE) return false
        val going = arena.advance(dtMs)
        if (!going) finishRound()
        return going
    }

    /** 판 결과 정하기 (대결이 끝났을 때) */
    fun finishRound() {
        if (phase != Phase.BATTLE || !arena.ended) return
        val ranks = arena.ranks()
        val pts = SpinRules.points(ranks)
        for (i in 0 until players) totals[i] += pts[i]
        results[round] = RoundResult(ranks, pts, arena.timeUp)
        phase = Phase.ROUND_END
    }

    /** 다음 판 준비로. 마지막 판이었으면 OVER 로 가고 false */
    fun nextRound(): Boolean {
        if (phase != Phase.ROUND_END) return false
        if (lastRound) {
            phase = Phase.OVER
            return false
        }
        round++
        prepIndex = 0
        taps.fill(0)
        powers.fill(0f)
        stabilities.fill(0f)
        gaugeValues.fill(0f)
        prepped.fill(false)
        gauge.reset()
        arena.setTops(tops)
        phase = Phase.READY
        return true
    }

    /** 최종 순위 (합계 점수, 경쟁 순위) */
    fun ranks(): IntArray = SpinRules.finalRanks(totals)

    /** 판 승자 (1위가 한 명이면 그 사람, 아니면 -1) */
    fun roundWinner(): Int {
        val r = results[round] ?: return -1
        var w = -1
        for (i in 0 until players) if (r.ranks[i] == 1) { if (w >= 0) return -1; w = i }
        return w
    }
}
