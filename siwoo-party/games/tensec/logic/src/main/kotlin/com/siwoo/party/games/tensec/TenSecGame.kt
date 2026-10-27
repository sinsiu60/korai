package com.siwoo.party.games.tensec

import com.siwoo.party.common.Ranking
import kotlin.math.abs
import kotlin.random.Random

/** 라운드 단계: IDLE(라운드 사이) → READY(목표 공개·카운트다운) → RUNNING(입력) → DONE(공개) */
enum class Phase { IDLE, READY, RUNNING, DONE }

/** 터치 결과 */
enum class Tap { RECORDED, IGNORED }

/**
 * 딱 10초 규칙.
 * - 라운드마다 목표 시간(5~15초 정수)을 공개 → 타이머 시작(begin). 타이머는 처음 3초만 보인다(화면 몫).
 * - 각자 목표 시간이라고 생각할 때 한 번 터치 → 시작 후 경과 시간을 0.01초 단위(내림)로 기록.
 *   두 번째 터치는 무시. 목표 + 10초가 지나도록 안 누른 사람은 fillMissing() 으로 목표+10초 기록(차이 10초).
 * - 차이 = |기록 − 목표| (0.01초 단위). 3라운드 차이 합이 작은 순으로 순위, 같으면 같은 순위.
 * 시간(ms)은 화면이 넘겨준다(일시정지 시간을 뺀 게임 시계) → 테스트에서 그대로 재현 가능.
 */
class TenSecGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    var phase = Phase.IDLE
        private set
    /** 끝난 라운드 수 */
    var roundsDone = 0
        private set
    /** 이번 라운드 목표 (초) */
    var targetSec = 0
        private set
    private var startAt = 0L

    /** 이번 라운드 기록 (0.01초 단위, -1 = 아직 안 누름) */
    val recordCs = LongArray(playerCount) { -1 }
    /** 이번 라운드에 시간 초과로 자동 기록됐는지 */
    val autoCapped = BooleanArray(playerCount)
    /** 라운드별 차이 (0.01초 단위) */
    val roundDiffCs = Array(GameConfig.ROUNDS) { LongArray(playerCount) }
    /** 차이 합 (0.01초 단위) */
    val totalCs = LongArray(playerCount)

    val gameOver: Boolean get() = roundsDone >= GameConfig.ROUNDS
    /** 지금 하는 라운드 번호 (1부터) */
    val roundNumber: Int get() = (roundsDone + 1).coerceAtMost(GameConfig.ROUNDS)
    val targetCs: Long get() = targetSec * 100L
    /** 시작 후 이 시간(ms)이 지나면 남은 사람을 자동 기록 */
    val capMs: Long get() = targetSec * 1000L + GameConfig.CAP_EXTRA_MS

    /** 새 라운드: 목표 시간을 뽑는다 (초) */
    fun startRound(): Int {
        check(!gameOver) { "게임이 끝났습니다" }
        check(phase == Phase.IDLE) { "라운드 진행 중" }
        recordCs.fill(-1)
        autoCapped.fill(false)
        targetSec = GameConfig.TARGET_MIN_SEC + random.nextInt(GameConfig.TARGET_MAX_SEC - GameConfig.TARGET_MIN_SEC + 1)
        phase = Phase.READY
        return targetSec
    }

    /** 타이머 시작 (nowMs = 게임 시계) */
    fun begin(nowMs: Long) {
        check(phase == Phase.READY)
        startAt = nowMs
        phase = Phase.RUNNING
    }

    /** 시작 후 경과 (ms) */
    fun elapsedMs(nowMs: Long): Long = if (phase == Phase.RUNNING) (nowMs - startAt).coerceAtLeast(0) else 0

    /** 플레이어 터치 — 라운드마다 한 번만 기록 */
    fun tap(player: Int, nowMs: Long): Tap {
        if (phase != Phase.RUNNING || player !in 0 until playerCount || recordCs[player] >= 0) return Tap.IGNORED
        val ms = (nowMs - startAt).coerceIn(0, capMs)
        recordCs[player] = ms / 10
        return Tap.RECORDED
    }

    /** 모두 기록했는지 */
    val allIn: Boolean get() = recordCs.all { it >= 0 }

    /** 시간 초과: 안 누른 사람을 목표+10초로 기록. 자동 기록한 사람 수 */
    fun fillMissing(): Int {
        if (phase != Phase.RUNNING) return 0
        var n = 0
        for (p in 0 until playerCount) if (recordCs[p] < 0) {
            recordCs[p] = capMs / 10
            autoCapped[p] = true
            n++
        }
        return n
    }

    /** 이번 라운드 차이 (0.01초 단위, 기록 전이면 -1) */
    fun diffCs(player: Int): Long = if (recordCs[player] < 0) -1 else abs(recordCs[player] - targetCs)

    /** 라운드 마감 (전원 기록 후): 차이를 합계에 더한다 */
    fun endRound() {
        check(phase == Phase.RUNNING && allIn) { "아직 기록 안 한 사람이 있음" }
        for (p in 0 until playerCount) {
            val d = diffCs(p)
            roundDiffCs[roundsDone][p] = d
            totalCs[p] += d
        }
        roundsDone++
        phase = Phase.DONE
    }

    /** 공개 화면이 끝나고 다음 라운드를 받을 준비 */
    fun nextRound() {
        if (phase == Phase.DONE) phase = Phase.IDLE
    }

    /** 이번 라운드에 가장 가까운 사람들 (동점이면 여럿) */
    fun closest(): BooleanArray {
        var best = Long.MAX_VALUE
        for (p in 0 until playerCount) if (diffCs(p) in 0 until best) best = diffCs(p)
        return BooleanArray(playerCount) { diffCs(it) == best }
    }

    /** 공개 순서: 차이가 큰 사람부터 (가장 가까운 사람이 마지막), 같으면 앞 번호부터 */
    fun revealOrder(): IntArray =
        (0 until playerCount).sortedWith(compareByDescending<Int> { diffCs(it) }.thenBy { it }).toIntArray()

    /** 최종 순위 (1부터): 차이 합이 작은 순, 같으면 같은 순위 */
    fun ranks(): IntArray = Ranking.competition(totalCs.toList(), higherIsBetter = false)
}
