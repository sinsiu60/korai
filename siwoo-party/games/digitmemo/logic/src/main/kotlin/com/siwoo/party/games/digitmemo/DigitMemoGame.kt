package com.siwoo.party.games.digitmemo

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 라운드 사이·새 숫자열을 만든 뒤 보이기 전 (배너·준비) */
    READY,
    /** 숫자열이 보이는 중 (입력 불가) */
    SHOWING,
    /** 각자 패드로 입력 중 */
    INPUT,
    /** 정답 공개 (라운드 끝) */
    REVEAL,
}

/**
 * 한 사람의 제출.
 * @param timeMs 제출 순간 (게임 시간, 일시정지 제외)
 * @param seq 이 라운드에서 몇 번째로 들어온 제출인지 (0부터) — 같은 시각이면 먼저 들어온 쪽이 빠름
 */
class Submission(val text: String, val timeMs: Long, val seq: Int)

/** 라운드 결과 */
class RoundResult(
    val round: Int,
    val answer: String,
    /** 사람별 제출 (안 냈으면 null) */
    val submissions: List<Submission?>,
    /** 사람별 정답 여부 */
    val correct: BooleanArray,
    /** 사람별 이번 라운드 점수 (가장 빠른 정답 2, 다른 정답 1, 그 외 0) */
    val points: IntArray,
    /** 가장 빨리 맞힌 사람 (-1 = 정답자 없음) */
    val fastest: Int,
)

/** 채점 (순수 함수) */
object DigitScoring {
    /** 정답 여부: 글자 그대로 같아야 정답 (자릿수·앞자리 0 포함) */
    fun isCorrect(answer: String, text: String?): Boolean = text != null && text == answer

    /** 가장 빠른 정답자: 시각이 가장 이른 사람, 같은 시각이면 먼저 들어온(seq 작은) 사람. 없으면 -1 */
    fun fastest(answer: String, subs: List<Submission?>): Int {
        var best = -1
        for (i in subs.indices) {
            val s = subs[i] ?: continue
            if (!isCorrect(answer, s.text)) continue
            val b = if (best >= 0) subs[best] else null
            if (b == null || s.timeMs < b.timeMs || (s.timeMs == b.timeMs && s.seq < b.seq)) best = i
        }
        return best
    }

    /** 사람별 점수: 가장 빠른 정답 POINTS_FASTEST, 다른 정답 POINTS_CORRECT, 오답·미제출 0 */
    fun score(answer: String, subs: List<Submission?>): IntArray {
        val f = fastest(answer, subs)
        return IntArray(subs.size) { i ->
            when {
                i == f -> GameConfig.POINTS_FASTEST
                isCorrect(answer, subs[i]?.text) -> GameConfig.POINTS_CORRECT
                else -> 0
            }
        }
    }
}

/**
 * 숫자 순간 기억 규칙 (Android 의존성 없음 — 시간·난수는 밖에서 넣는다).
 * 라운드: startRound(숫자열 생성) → show(보이기) → startInput(now)(입력·제한 시간 시작) → type/backspace/confirm
 *        → (모두 제출 또는 시간 끝) closeRound() → 다음 라운드 … ROUNDS 라운드 → ranks()
 * 제출은 라운드마다 한 번, 자릿수를 다 채워야 할 수 있고, 제한 시간이 지나면 받지 않는다.
 */
class DigitMemoGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    val totalRounds = GameConfig.ROUNDS

    /** 지금 라운드 (1부터, 시작 전 0) */
    var round = 0
        private set
    var phase = Phase.READY
        private set
    /** 이번 라운드 숫자열 */
    var answer = ""
        private set
    val scores = IntArray(playerCount)

    private val inputs = Array(playerCount) { "" }
    private val subs = arrayOfNulls<Submission>(playerCount)
    private var seq = 0

    /** 입력 시작 시각 / 마감 시각 (게임 시간) */
    var inputStart = 0L
        private set
    var deadline = 0L
        private set
    var lastResult: RoundResult? = null
        private set

    val digits: Int get() = answer.length

    /** 마지막 라운드까지 공개했으면 끝 */
    val gameOver: Boolean get() = round >= totalRounds && phase == Phase.REVEAL

    /** 새 라운드: 숫자열을 만들고 준비 단계로 (아직 안 보임). 만든 숫자열을 돌려준다 */
    fun startRound(): String {
        check(phase == Phase.READY && round == 0 || phase == Phase.REVEAL) { "라운드 진행 중" }
        check(round < totalRounds) { "게임 끝" }
        round++
        answer = generate(digitsFor(round), random)
        inputs.fill("")
        subs.fill(null)
        seq = 0
        lastResult = null
        phase = Phase.READY
        return answer
    }

    /** 숫자열 보이기 시작 (입력 불가) */
    fun show() {
        check(phase == Phase.READY && answer.isNotEmpty()) { "준비 단계가 아님" }
        phase = Phase.SHOWING
    }

    /** 숫자를 가리고 입력 시작 (제한 시간 = timeLimitMs(자릿수)) */
    fun startInput(nowMs: Long) {
        check(phase == Phase.SHOWING) { "보이기 단계가 아님" }
        phase = Phase.INPUT
        inputStart = nowMs
        deadline = nowMs + timeLimitMs(answer.length)
    }

    fun input(i: Int): String = inputs[i]

    fun submission(i: Int): Submission? = subs[i]

    fun isSubmitted(i: Int): Boolean = subs[i] != null

    val submittedCount: Int get() = subs.count { it != null }

    val allSubmitted: Boolean get() = subs.all { it != null }

    private fun canEdit(i: Int): Boolean = phase == Phase.INPUT && subs[i] == null

    /** 숫자 d 넣기 (자릿수가 다 찼거나 제출했으면 false) */
    fun type(i: Int, d: Int): Boolean {
        if (!canEdit(i) || d !in 0..9 || inputs[i].length >= answer.length) return false
        inputs[i] = inputs[i] + ('0' + d)
        return true
    }

    /** 마지막 숫자 지우기 */
    fun backspace(i: Int): Boolean {
        if (!canEdit(i) || inputs[i].isEmpty()) return false
        inputs[i] = inputs[i].substring(0, inputs[i].length - 1)
        return true
    }

    /** [확인]을 누를 수 있는지: 입력 중·아직 안 냄·자릿수를 다 채움 */
    fun canConfirm(i: Int): Boolean = canEdit(i) && inputs[i].length == answer.length

    /** 제출 (라운드마다 한 번, 마감 시각 전까지만). 받아들였으면 true */
    fun confirm(i: Int, nowMs: Long): Boolean {
        if (!canConfirm(i) || nowMs >= deadline) return false
        subs[i] = Submission(inputs[i], nowMs.coerceAtLeast(inputStart), seq++)
        return true
    }

    fun isTimeUp(nowMs: Long): Boolean = phase == Phase.INPUT && nowMs >= deadline

    /** 남은 입력 시간 (ms, 0 이상) */
    fun timeLeft(nowMs: Long): Long = if (phase != Phase.INPUT) 0L else (deadline - nowMs).coerceAtLeast(0L)

    /** 라운드 마감 → 채점 (점수 반영) */
    fun closeRound(): RoundResult {
        check(phase == Phase.INPUT) { "입력 단계가 아님" }
        phase = Phase.REVEAL
        val list = subs.toList()
        val points = DigitScoring.score(answer, list)
        for (i in 0 until playerCount) scores[i] += points[i]
        val r = RoundResult(
            round, answer, list,
            BooleanArray(playerCount) { DigitScoring.isCorrect(answer, list[it]?.text) },
            points, DigitScoring.fastest(answer, list),
        )
        lastResult = r
        return r
    }

    /** 점수 순 경쟁 순위 (동점 같은 순위) */
    fun ranks(): IntArray = ranksOf(scores)

    companion object {
        /** r 라운드(1부터)의 자릿수: 4, 5, … 11 */
        fun digitsFor(round: Int): Int = GameConfig.START_DIGITS + round - 1

        /** 자릿수에 따른 입력 제한 시간 */
        fun timeLimitMs(digits: Int): Long = GameConfig.INPUT_BASE_MS + GameConfig.INPUT_PER_DIGIT_MS * digits

        /** 0~9 숫자 len 개 (앞자리 0 가능) */
        fun generate(len: Int, random: Random): String {
            val sb = StringBuilder(len)
            repeat(len) { sb.append('0' + random.nextInt(10)) }
            return sb.toString()
        }

        fun ranksOf(scores: IntArray): IntArray = Ranking.competition(scores.map { it.toLong() })
    }
}
