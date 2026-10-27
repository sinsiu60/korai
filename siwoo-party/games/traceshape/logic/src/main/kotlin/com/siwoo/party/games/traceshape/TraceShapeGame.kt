package com.siwoo.party.games.traceshape

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 라운드 사이 · 도형만 보이고 아직 못 그림 (배너·준비) */
    READY,
    /** 각자 그리는 중 */
    DRAWING,
    /** 라운드 끝 (채점 공개·비교) */
    REVEAL,
}

/** 점 하나를 넣은 결과 */
enum class AddResult {
    /** 받지 않음 (그리는 단계 아님·이미 완료·시간 끝·너무 가까운 점·버퍼 가득) */
    IGNORED,
    /** 넣음 */
    ADDED,
    /** 넣었고 이 점으로 자동 완료됨 */
    COMPLETED,
}

/** 한 사람의 라운드 결과 */
class PlayerRound(
    /** 정확도 0..100 */
    val accuracy: Float,
    /** 덮은 비율 0..1 */
    val coverage: Float,
    /** 평균 거리 (상자 한 변 = 1, 안 그렸으면 무한대) */
    val meanDist: Float,
    /** 자동 완료했는지 */
    val finished: Boolean,
    /** 완료까지 걸린 시간 (ms, 미완성 -1) */
    val timeMs: Long,
    /** 속도 보너스 배수 (미완성 1.0) */
    val bonus: Float,
    /** 라운드 점수 = 반올림(정확도 × 보너스) */
    val points: Int,
)

/** 라운드 결과 */
class RoundResult(val round: Int, val kind: ShapeKind, val players: List<PlayerRound>) {
    /** 이번 라운드 점수가 가장 높은 사람들 */
    fun best(): List<Int> {
        val m = players.maxOf { it.points }
        return players.indices.filter { players[it].points == m }
    }
}

/**
 * 도형 따라 그리기 규칙 (Android 의존성 없음 — 시간·난수는 밖에서 넣는다).
 * 라운드: startRound() (도형 정하고 획 비움, READY) → startDrawing(now) (DRAWING, 제한 시간 시작)
 *        → penDown/moveTo/penUp (사람마다 한 획, 손 떼고 다시 대면 이어서) → 자동 완료 또는 시간 끝
 *        → closeRound() (채점, REVEAL) … ROUNDS 라운드 → ranks()
 * 점 좌표는 도형 상자 좌표(0..1). 도형은 게임 시작 때 서로 다르게 ROUNDS 개 고른다.
 */
class TraceShapeGame(val playerCount: Int, random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    val totalRounds = GameConfig.ROUNDS
    /** 라운드별 도형 (서로 다름) */
    val kinds: List<ShapeKind> = Shapes.pick(totalRounds, random)

    var round = 0
        private set
    var phase = Phase.READY
        private set
    /** 이번 라운드 도형 */
    var shape: Shape = Shapes.make(kinds[0])
        private set
    /** 사람별 획 (버퍼는 처음 한 번만 만든다) */
    val traces = Array(playerCount) { Trace(GameConfig.MAX_POINTS) }
    /** 합계 점수 */
    val totals = IntArray(playerCount)
    private val finishAt = LongArray(playerCount) { -1L }
    var roundStart = 0L
        private set
    val history = ArrayList<RoundResult>()
    val lastResult: RoundResult? get() = history.lastOrNull()

    val deadline: Long get() = roundStart + GameConfig.ROUND_MS
    val gameOver: Boolean get() = round >= totalRounds && phase == Phase.REVEAL

    /** 다음 라운드 준비 (도형을 보여 주는 단계) */
    fun startRound(): Shape {
        check(!gameOver && (round == 0 || phase == Phase.REVEAL)) { "라운드 진행 중" }
        round++
        shape = Shapes.make(kinds[round - 1])
        for (t in traces) t.reset(shape)
        finishAt.fill(-1L)
        phase = Phase.READY
        return shape
    }

    /** 그리기 시작 (제한 시간 시작) */
    fun startDrawing(now: Long) {
        check(phase == Phase.READY && round > 0) { "준비 단계가 아님" }
        roundStart = now
        phase = Phase.DRAWING
    }

    fun isFinished(p: Int): Boolean = finishAt[p] >= 0
    /** 완료까지 걸린 시간 (미완성 -1) */
    fun finishTime(p: Int): Long = if (finishAt[p] >= 0) finishAt[p] - roundStart else -1L
    val finishedCount: Int get() = finishAt.count { it >= 0 }
    val allFinished: Boolean get() = finishAt.all { it >= 0 }
    fun timeLeft(now: Long): Long = (deadline - now).coerceAtLeast(0L)

    private fun accepting(p: Int, now: Long): Boolean =
        phase == Phase.DRAWING && p in 0 until playerCount && finishAt[p] < 0 && now < deadline

    /** 손가락이 닿음 → 새 조각 시작 (같은 획에 이어짐) */
    fun penDown(p: Int, x: Float, y: Float, now: Long): AddResult {
        if (!accepting(p, now)) return AddResult.IGNORED
        return after(p, traces[p].penDown(x, y), now)
    }

    /** 손가락이 움직임 */
    fun moveTo(p: Int, x: Float, y: Float, now: Long): AddResult {
        if (!accepting(p, now)) return AddResult.IGNORED
        return after(p, traces[p].add(x, y), now)
    }

    /** 손가락을 뗌 */
    fun penUp(p: Int) {
        if (p in 0 until playerCount) traces[p].penUp()
    }

    private fun after(p: Int, added: Boolean, now: Long): AddResult {
        if (!added) return AddResult.IGNORED
        if (traces[p].isComplete()) {
            traces[p].snapToEnd()
            finishAt[p] = now.coerceAtLeast(roundStart)
            return AddResult.COMPLETED
        }
        return AddResult.ADDED
    }

    /** 사람 p 의 지금까지 채점 (라운드 중 미리보기에도 씀) */
    fun evaluate(p: Int): PlayerRound {
        val t = traces[p]
        val done = isFinished(p)
        val acc = t.accuracy
        val time = finishTime(p)
        val bonus = TraceScoring.speedBonus(time, done)
        return PlayerRound(acc, t.coverage, t.meanDist, done, time, bonus, TraceScoring.roundPoints(acc, bonus))
    }

    /** 라운드 끝 (모두 완료 또는 시간 끝): 채점해 합계에 더한다. 미완성은 그린 만큼, 보너스 없음 */
    fun closeRound(): RoundResult {
        check(phase == Phase.DRAWING) { "그리는 중이 아님" }
        val list = List(playerCount) { evaluate(it) }
        for (i in 0 until playerCount) totals[i] += list[i].points
        val r = RoundResult(round, shape.kind, list)
        history.add(r)
        phase = Phase.REVEAL
        return r
    }

    /** 합계 점수 경쟁 순위 (동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(totals.map { it.toLong() })
}
