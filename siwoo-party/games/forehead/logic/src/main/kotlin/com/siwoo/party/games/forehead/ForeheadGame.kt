package com.siwoo.party.games.forehead

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 단어 하나의 결과 */
enum class Answer { CORRECT, PASS, TIMEOUT }

class WordResult(val word: String, val answer: Answer)

enum class Phase {
    /** 카테고리·바퀴 수 고르기 */
    SETUP,
    /** 차례 시작 (이마에 대기·카운트) */
    READY,
    /** 60초 동안 단어 맞히기 */
    PLAYING,
    /** 시간 끝 → 맞힌/패스한 단어 공개 */
    REVIEW,
    /** 모든 차례 끝 */
    DONE,
}

/**
 * 이마 퀴즈 규칙 (순수 Kotlin). 시간은 화면이 재고 timeUp() 을 부른다.
 * 차례: 0번부터 한 명씩, laps 바퀴. 차례마다 맞힌 개수가 그 사람 점수에 더해지고, 합계로 순위(동점 같은 순위).
 */
class ForeheadGame(val playerCount: Int, words: List<List<String>>, private val random: Random = Random.Default) {

    private val words = ForeheadWords.sanitizeAll(words)

    var phase = Phase.SETUP
        private set
    /** 고른 카테고리 (GameConfig.MIXED = 모두 섞기) */
    var category = GameConfig.MIXED
        private set
    var laps = GameConfig.LAPS_DEFAULT
        private set
    /** 지금 차례 번호 (0부터, 시작 전 -1) */
    var turnIndex = -1
        private set
    val totalTurns: Int get() = playerCount * laps
    /** 지금 차례인 사람 */
    val current: Int get() = if (turnIndex < 0) -1 else turnIndex % playerCount
    /** 지금 몇 바퀴째 (1부터) */
    val lap: Int get() = if (turnIndex < 0) 0 else turnIndex / playerCount + 1
    val isLastTurn: Boolean get() = turnIndex == totalTurns - 1

    /** 누적 점수 (맞힌 개수 합) */
    val scores = IntArray(playerCount)
    /** 사람마다 차례별 맞힌 개수 */
    val turnScores: List<MutableList<Int>> = List(playerCount) { ArrayList<Int>() }

    /** 지금 보여 주는 단어 (PLAYING 일 때) */
    var word: String = ""
        private set
    private val results = ArrayList<WordResult>()
    /** 이번 차례 결과 (순서대로) */
    val turnResults: List<WordResult> get() = results
    val correctCount: Int get() = results.count { it.answer == Answer.CORRECT }
    val passCount: Int get() = results.count { it.answer == Answer.PASS }

    private lateinit var picker: WordPicker
    /** 이번 게임에 나온 단어 (중복 확인용) */
    val shownWords = ArrayList<String>()

    val categoryName: String get() = if (category == GameConfig.MIXED) "모두 섞기" else ForeheadWords.CATEGORIES[category]

    /** 카테고리·바퀴 수 정하고 첫 차례 준비 */
    fun setup(category: Int, laps: Int) {
        check(phase == Phase.SETUP)
        this.category = if (category in 0 until GameConfig.CATEGORY_COUNT) category else GameConfig.MIXED
        this.laps = laps.coerceIn(GameConfig.LAPS_MIN, GameConfig.LAPS_MAX)
        picker = WordPicker(words, this.category, random)
        turnIndex = 0
        results.clear()
        phase = Phase.READY
    }

    /** 카운트 끝 → 첫 단어 */
    fun beginPlay(): String {
        check(phase == Phase.READY)
        phase = Phase.PLAYING
        results.clear()
        return pick()
    }

    private fun pick(): String {
        word = picker.next()
        shownWords.add(word)
        return word
    }

    /** 정답(true)/패스(false) → 다음 단어 */
    fun answer(correct: Boolean): String {
        check(phase == Phase.PLAYING)
        results.add(WordResult(word, if (correct) Answer.CORRECT else Answer.PASS))
        return pick()
    }

    /**
     * 60초 끝: 보고 있던 단어는 TIMEOUT 으로, 맞힌 개수를 점수에 더한다. 이번 차례 맞힌 개수를 돌려준다.
     * currentShown = false: 다음 단어가 아직 화면에 안 나왔음 (정답·패스 화면 도중) → 목록에 넣지 않음
     */
    fun timeUp(currentShown: Boolean = true): Int {
        check(phase == Phase.PLAYING)
        if (word.isNotEmpty() && currentShown) results.add(WordResult(word, Answer.TIMEOUT))
        word = ""
        val n = correctCount
        scores[current] += n
        turnScores[current].add(n)
        phase = Phase.REVIEW
        return n
    }

    /** 다음 차례로 (true) 또는 게임 끝 (false) */
    fun nextTurn(): Boolean {
        check(phase == Phase.REVIEW)
        if (isLastTurn) {
            phase = Phase.DONE
            return false
        }
        turnIndex++
        results.clear()
        phase = Phase.READY
        return true
    }

    /** 합계 순위 (players 순서, 1부터, 동점 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}
