package com.siwoo.party.games.drawing

import com.siwoo.party.common.Ranking
import kotlin.random.Random

enum class Phase {
    /** 그리는 사람이 몰래 제시어 확인 */
    SECRET,
    /** 80초 동안 그리기 */
    DRAWING,
    /** 정답 또는 시간 끝 → 정답 공개 */
    RESULT,
    /** 모든 차례 끝 */
    DONE,
}

/** 차례 하나의 결과 */
class TurnResult(val drawer: Int, val word: String, val guesser: Int)

/**
 * 그림 맞히기 규칙 (순수 Kotlin). 시간은 화면이 재고 timeout() 을 부른다.
 * 차례: 0번부터 한 명씩, 모두 DRAWS_PER_PLAYER(2)번씩 그린다.
 * 맞히면 맞힌 사람 +2, 그린 사람 +1. 시간 초과면 아무도 점수 없음. 합계로 순위 (동점 같은 순위).
 * 제시어는 모든 카테고리 단어를 섞어 한 게임 안에서 겹치지 않게 낸다.
 */
class DrawingGame(val playerCount: Int, words: List<List<String>>, private val random: Random = Random.Default) {

    private val lists = DrawingWords.sanitizeAll(words)
    /** (카테고리, 단어) 섞은 줄 */
    private val queue = ArrayList<Pair<Int, String>>()

    val totalTurns: Int = playerCount * GameConfig.DRAWS_PER_PLAYER
    var turnIndex = 0
        private set
    val drawer: Int get() = turnIndex % playerCount
    /** 몇 번째 바퀴 (1부터) */
    val round: Int get() = turnIndex / playerCount + 1
    val isLastTurn: Boolean get() = turnIndex == totalTurns - 1

    var phase = Phase.SECRET
        private set
    var word = ""
        private set
    var category = 0
        private set
    val categoryName: String get() = DrawingWords.CATEGORIES[category]

    val scores = IntArray(playerCount)
    /** 이번 차례에 맞힌 사람 (없으면 -1) */
    var guesser = -1
        private set
    val history = ArrayList<TurnResult>()
    /** 이번 게임에 나온 단어 */
    val usedWords = ArrayList<String>()

    init {
        refill()
        pickWord()
    }

    private fun refill() {
        val seen = HashSet(usedWords)
        val all = ArrayList<Pair<Int, String>>()
        for ((k, l) in lists.withIndex()) for (w in l) if (w !in seen) all.add(k to w)
        if (all.isEmpty()) for ((k, l) in lists.withIndex()) for (w in l) all.add(k to w)
        all.shuffle(random)
        queue.addAll(all)
    }

    private fun pickWord() {
        if (queue.isEmpty()) refill()
        val (c, w) = queue.removeAt(queue.size - 1)
        category = c
        word = w
        usedWords.add(w)
    }

    /** 제시어 확인 끝 → 그리기 시작 */
    fun startDrawing() {
        check(phase == Phase.SECRET)
        phase = Phase.DRAWING
        guesser = -1
    }

    /** 이 사람이 맞힘 (그린 사람은 안 됨) → 맞힌 사람 +2, 그린 사람 +1 */
    fun guess(player: Int): Boolean {
        if (phase != Phase.DRAWING || player == drawer || player !in 0 until playerCount) return false
        guesser = player
        scores[player] += GameConfig.POINTS_GUESSER
        scores[drawer] += GameConfig.POINTS_DRAWER
        history.add(TurnResult(drawer, word, player))
        phase = Phase.RESULT
        return true
    }

    /** 80초 끝: 아무도 점수 없음 */
    fun timeout() {
        check(phase == Phase.DRAWING)
        guesser = -1
        history.add(TurnResult(drawer, word, -1))
        phase = Phase.RESULT
    }

    /** 다음 차례 (true) 또는 끝 (false) */
    fun nextTurn(): Boolean {
        check(phase == Phase.RESULT)
        if (isLastTurn) {
            phase = Phase.DONE
            return false
        }
        turnIndex++
        guesser = -1
        pickWord()
        phase = Phase.SECRET
        return true
    }

    fun ranks(): IntArray = Ranking.competition(scores.map { it.toLong() })
}
