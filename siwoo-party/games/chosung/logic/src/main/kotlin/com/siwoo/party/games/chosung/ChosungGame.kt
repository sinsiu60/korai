package com.siwoo.party.games.chosung

import com.siwoo.party.common.Chosung
import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 문제 단계 */
enum class Phase {
    /** 문제 사이 (버저 안 받음) */
    IDLE,
    /** 문제가 열려 있음 — 버저 받음, 힌트·정답 공개 시계가 흐름 */
    OPEN,
    /** 누군가 버저를 눌러 답하는 중 — 다른 버저는 막힘, 힌트·정답 공개 시계는 멈춤 */
    ANSWERING,
}

/** 문제 하나 */
class Question(val number: Int, val entry: Entry) {
    val word: String get() = entry.word
    val category: String get() = entry.categoryName
    /** 초성 (예: "ㄱㅊㅉㄱ") */
    val initials: String = Chosung.extract(entry.word)
}

/** 버저 결과 */
sealed class Buzz {
    /** 무시 (문제 밖·범위 밖) */
    object Ignored : Buzz()
    /** 이 문제에서 이미 틀려 잠김 */
    class Locked(val player: Int) : Buzz()
    /** 다른 사람이 답하는 중 */
    class Busy(val player: Int, val answerer: Int) : Buzz()
    /** 버저 획득 → deadlineMs(게임 시간)까지 말로 답하고 판정을 받아야 함 */
    class Accepted(val player: Int, val deadlineMs: Long) : Buzz()
}

/** 판정 결과 */
sealed class Verdict {
    object Ignored : Verdict()
    /** 정답 → +1, 문제 끝 */
    class Correct(val player: Int, val score: Int) : Verdict()
    /**
     * 오답 → -1, 그 문제 버저 잠금. timeout = 5초 안에 판정이 없어 오답 처리됨.
     * allLocked = 모두 잠겨 문제 끝 (정답 공개, 아무도 점수 없음). 아니면 문제가 다시 열림
     */
    class Wrong(val player: Int, val score: Int, val timeout: Boolean, val allLocked: Boolean) : Verdict()
}

/** 시계 진행 결과 (update) */
sealed class Tick {
    object None : Tick()
    /** 힌트: index 번째 글자 공개 */
    class Hint(val index: Int) : Tick()
    /** 20초 동안 아무도 못 맞힘 → 정답 공개, 문제 끝 */
    object Reveal : Tick()
    /** 답하는 사람이 5초 안에 판정을 못 받음 → 오답 처리 */
    class AnswerTimeout(val verdict: Verdict.Wrong) : Tick()
}

/**
 * 초성 퀴즈 규칙.
 * - 15문제. 문제마다 카테고리 + 초성이 나온다 (단어는 겹치지 않게 무작위).
 * - 구역(버저)을 가장 먼저 누른 사람이 5초 안에 말로 답하고, 나머지 사람들이 [정답]/[오답]으로 판정한다.
 *   답하는 동안 다른 버저는 막힌다.
 * - 정답 +1 (문제 끝). 오답 -1 + 그 문제 버저 잠금 → 문제가 다시 열려 다른 사람이 누를 수 있다.
 *   5초 안에 판정이 없으면 오답과 같다. 모두 잠기면 정답 공개 후 다음 문제.
 * - 아무도 못 맞히면 문제가 "열려 있던" 시간 기준 10초에 힌트(한글 글자 하나 공개), 20초에 정답 공개(점수 없음).
 *   누군가 답하는 동안(ANSWERING)은 이 시계가 멈춘다.
 * - 최종 순위: 점수 높은 순, 같은 점수 같은 순위.
 * 시간은 화면이 넘겨준다(ms, 게임 시간 = 일시정지 제외) → 테스트에서 그대로 재현 가능.
 */
class ChosungGame(val playerCount: Int, pool: List<Entry>, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 이번 게임 문제 순서 (같은 단어 없음) */
    private val deck: List<Entry> = pool.distinctBy { it.word }.shuffled(random).take(GameConfig.CHOSUNG_QUESTIONS)

    /** 문제 수 (보통 15, 단어가 모자라면 그만큼) */
    val total: Int get() = deck.size

    val scores = IntArray(playerCount)
    /** 맞힌 개수 */
    val corrects = IntArray(playerCount)
    /** 이번 문제에서 틀려 잠긴 사람 */
    val locked = BooleanArray(playerCount)
    var phase = Phase.IDLE
        private set
    var current: Question? = null
        private set
    /** 끝난 문제 수 */
    var questionsDone = 0
        private set
    /** 지금 답하는 사람 (-1 = 없음) */
    var answerer = -1
        private set
    /** 답 마감 (게임 시간) */
    var answerDeadline = 0L
        private set
    /** 이번 문제를 맞힌 사람 (-1 = 아직/아무도) */
    var questionWinner = -1
        private set
    /** 힌트로 공개한 글자 위치 (-1 = 아직) */
    var hintIndex = -1
        private set

    /** 문제가 열려 있던 시간 합 (ANSWERING 이전까지), openSince = 지금 열린 구간 시작 */
    private var openAccum = 0L
    private var openSince = 0L
    private var reveal = BooleanArray(0)

    val gameOver: Boolean get() = questionsDone >= total

    /** 다음 문제를 연다 */
    fun nextQuestion(nowMs: Long): Question {
        check(!gameOver) { "게임이 끝났습니다" }
        check(phase == Phase.IDLE) { "문제가 진행 중입니다" }
        val q = Question(questionsDone + 1, deck[questionsDone])
        current = q
        locked.fill(false)
        answerer = -1
        questionWinner = -1
        hintIndex = -1
        reveal = BooleanArray(q.word.length)
        openAccum = 0L
        openSince = nowMs
        phase = Phase.OPEN
        return q
    }

    /** 이번 게임에 낼 단어들 (테스트용) */
    fun deckWords(): List<String> = deck.map { it.word }

    /** 문제가 열려 있던 시간 (답하는 시간 제외) */
    fun openElapsed(nowMs: Long): Long = openAccum + if (phase == Phase.OPEN) (nowMs - openSince).coerceAtLeast(0) else 0L

    /** 화면에 보일 문제 글자: 초성 + (힌트면) 공개한 글자 하나 */
    fun display(): String {
        val q = current ?: return ""
        return Chosung.masked(q.word, reveal)
    }

    /** player 가 버저를 nowMs 에 눌렀다 */
    fun buzz(player: Int, nowMs: Long): Buzz {
        if (player !in 0 until playerCount || current == null) return Buzz.Ignored
        return when (phase) {
            Phase.IDLE -> Buzz.Ignored
            Phase.ANSWERING -> Buzz.Busy(player, answerer)
            Phase.OPEN -> {
                if (locked[player]) return Buzz.Locked(player)
                openAccum = openElapsed(nowMs)
                phase = Phase.ANSWERING
                answerer = player
                answerDeadline = nowMs + GameConfig.ANSWER_MS
                Buzz.Accepted(player, answerDeadline)
            }
        }
    }

    /** 나머지 사람들이 [정답](correct=true) / [오답] 을 눌렀다 */
    fun judge(correct: Boolean, nowMs: Long): Verdict {
        if (phase != Phase.ANSWERING) return Verdict.Ignored
        return if (correct) {
            val p = answerer
            scores[p] += GameConfig.CORRECT_POINTS
            corrects[p]++
            questionWinner = p
            endQuestion()
            Verdict.Correct(p, scores[p])
        } else {
            wrong(nowMs, timeout = false)
        }
    }

    private fun wrong(nowMs: Long, timeout: Boolean): Verdict.Wrong {
        val p = answerer
        scores[p] -= GameConfig.WRONG_PENALTY
        locked[p] = true
        answerer = -1
        val allLocked = locked.all { it }
        if (allLocked) {
            endQuestion()
        } else {
            phase = Phase.OPEN
            openSince = nowMs
        }
        return Verdict.Wrong(p, scores[p], timeout, allLocked)
    }

    /**
     * 시계 진행: 답 마감(오답 처리) → 정답 공개(20초) → 힌트(10초) 순으로 확인한다.
     * 화면은 msUntilNextEvent 만큼 뒤에 이걸 부르면 된다 (여러 번 불러도 안전).
     */
    fun update(nowMs: Long): Tick {
        when (phase) {
            Phase.IDLE -> return Tick.None
            Phase.ANSWERING -> {
                if (nowMs < answerDeadline) return Tick.None
                return Tick.AnswerTimeout(wrong(nowMs, timeout = true))
            }
            Phase.OPEN -> {
                val e = openElapsed(nowMs)
                if (e >= GameConfig.REVEAL_AT_MS) {
                    endQuestion()
                    return Tick.Reveal
                }
                if (e >= GameConfig.HINT_AT_MS && hintIndex < 0) {
                    hintIndex = pickHint()
                    if (hintIndex >= 0) reveal[hintIndex] = true
                    return Tick.Hint(hintIndex)
                }
                return Tick.None
            }
        }
    }

    /** 다음 시계 일(힌트·정답 공개·답 마감)까지 남은 시간 (없으면 -1) */
    fun msUntilNextEvent(nowMs: Long): Long = when (phase) {
        Phase.IDLE -> -1L
        Phase.ANSWERING -> (answerDeadline - nowMs).coerceAtLeast(0)
        Phase.OPEN -> {
            val e = openElapsed(nowMs)
            val at = if (hintIndex < 0) GameConfig.HINT_AT_MS else GameConfig.REVEAL_AT_MS
            (at - e).coerceAtLeast(0)
        }
    }

    /** 힌트로 공개할 글자: 한글 음절 중 무작위 하나 (첫 글자 포함) */
    private fun pickHint(): Int {
        val w = current?.word ?: return -1
        val idx = w.indices.filter { Chosung.isSyllable(w[it]) }
        return if (idx.isEmpty()) -1 else idx[random.nextInt(idx.size)]
    }

    private fun endQuestion() {
        phase = Phase.IDLE
        answerer = -1
        questionsDone++
    }

    /** 최종 순위 (1부터): 점수 높은 순, 같은 점수 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}
