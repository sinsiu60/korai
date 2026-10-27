package com.siwoo.party.games.charades

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 4인 = 2 대 2 팀전, 3인 = 개인전 */
enum class CharadesMode(val label: String) {
    TEAM("2 대 2 팀전"),
    SOLO("개인전"),
}

/** 단어 하나의 결과 */
enum class Answer { CORRECT, PASS, TIMEOUT }

/** guesser = 맞힌 사람 (정답일 때만, 아니면 -1) */
class WordResult(val word: String, val category: Int, val answer: Answer, val guesser: Int)

enum class Phase {
    /** 카테고리 고르기 */
    SETUP,
    /** 설명자에게 기기 넘기기·카운트 (단어 아직 안 보임) */
    READY,
    /** 90초 동안 몸으로 설명 */
    PLAYING,
    /** 시간 끝 → 맞힌/패스한 단어 공개 */
    REVIEW,
    /** 모든 차례 끝 */
    DONE,
}

/**
 * 2 대 2 팀 나누기: 플레이어를 섞어 앞의 두 명 = A팀(0), 뒤의 두 명 = B팀(1). 언제나 2:2.
 * members[t] 의 순서가 그 팀 안의 설명 순서.
 */
class CharadesTeams(val teamOf: IntArray, val members: Array<IntArray>) {
    fun teammate(p: Int): Int = members[teamOf[p]].first { it != p }

    companion object {
        fun assign(playerCount: Int, random: Random): CharadesTeams {
            require(playerCount == GameConfig.TEAM_PLAYERS)
            val order = (0 until playerCount).shuffled(random)
            val teamOf = IntArray(playerCount)
            for ((k, p) in order.withIndex()) teamOf[p] = if (k < GameConfig.TEAM_SIZE) 0 else 1
            val a = IntArray(GameConfig.TEAM_SIZE) { order[it] }
            val b = IntArray(GameConfig.TEAM_SIZE) { order[GameConfig.TEAM_SIZE + it] }
            return CharadesTeams(teamOf, arrayOf(a, b))
        }
    }
}

/**
 * 몸으로 말해요 규칙 (순수 Kotlin). 시간은 화면이 재고 timeUp() 을 부른다.
 * - 4인 팀전: 팀을 무작위로 2:2 로 나누고 A1 → B1 → A2 → B2 순서로 한 명씩 설명 (모두 한 번씩).
 *   설명자의 팀원이 맞히면 그 팀 +1. 순위는 팀 점수 → 같은 팀은 같은 순위 ([1,1,3,3], 동점이면 모두 1).
 * - 3인 개인전: 0 → 1 → 2 번 순서로 한 명씩 설명, 나머지 둘이 맞힌다. 맞히면 맞힌 사람 +1, 설명자 +1. 개인 점수로 순위.
 * - 차례마다 패스는 MAX_PASSES 번까지. 한 게임 동안 같은 단어는 다시 나오지 않는다.
 */
class CharadesGame(val playerCount: Int, words: List<List<String>>, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    private val words = CharadesWords.sanitizeAll(words)

    val mode: CharadesMode = if (playerCount == GameConfig.TEAM_PLAYERS) CharadesMode.TEAM else CharadesMode.SOLO
    val isTeam: Boolean get() = mode == CharadesMode.TEAM

    /** 팀전일 때만 (개인전 null) */
    val teams: CharadesTeams? = if (isTeam) CharadesTeams.assign(playerCount, random) else null

    /** 설명 순서 (players 번호) — 팀전 A1, B1, A2, B2 / 개인전 0, 1, 2 */
    val order: IntArray = teams?.let { t -> intArrayOf(t.members[0][0], t.members[1][0], t.members[0][1], t.members[1][1]) }
        ?: IntArray(playerCount) { it }

    var phase = Phase.SETUP
        private set
    /** 고른 카테고리 (GameConfig.MIXED = 모두 섞기) */
    var category = GameConfig.MIXED
        private set
    val categoryName: String get() = if (category == GameConfig.MIXED) "모두 섞기" else CharadesWords.CATEGORIES[category]

    /** 지금 차례 번호 (0부터, 시작 전 -1) */
    var turnIndex = -1
        private set
    val totalTurns: Int get() = order.size
    val isLastTurn: Boolean get() = turnIndex == totalTurns - 1
    /** 지금 설명자 */
    val explainer: Int get() = if (turnIndex < 0) -1 else order[turnIndex]

    /** 개인 점수 (개인전: 맞힌 +1, 설명 +1 / 팀전: 설명한 차례에 팀이 맞힌 개수) */
    val scores = IntArray(playerCount)
    /** 팀 점수 (팀전만, A·B) */
    val teamScores = IntArray(2)

    /** 플레이어 p 의 팀 (개인전 -1) */
    fun teamOf(p: Int): Int = teams?.teamOf?.get(p) ?: -1

    /** 순위·점수판에 쓰는 점수: 팀전은 팀 점수, 개인전은 개인 점수 */
    fun displayScore(p: Int): Int = if (isTeam) teamScores[teamOf(p)] else scores[p]

    /** 설명자 e 일 때 맞히는 사람들 (팀전: 팀원 한 명 / 개인전: 나머지 둘) */
    fun guessersOf(e: Int): List<Int> =
        teams?.let { listOf(it.teammate(e)) } ?: (0 until playerCount).filter { it != e }

    /** 지금 보여 주는 단어 (PLAYING 일 때) */
    var word: CharadesWord? = null
        private set
    private val results = ArrayList<WordResult>()
    /** 이번 차례 결과 (순서대로) */
    val turnResults: List<WordResult> get() = results
    val correctCount: Int get() = results.count { it.answer == Answer.CORRECT }
    val passCount: Int get() = results.count { it.answer == Answer.PASS }
    val passesLeft: Int get() = (GameConfig.MAX_PASSES - passCount).coerceAtLeast(0)
    val canPass: Boolean get() = phase == Phase.PLAYING && passesLeft > 0

    private lateinit var picker: WordPicker
    /** 이번 게임에 나온 단어 (중복 확인용) */
    val shownWords = ArrayList<String>()

    /** 카테고리 정하고 첫 차례 준비 */
    fun setup(category: Int) {
        check(phase == Phase.SETUP)
        this.category = if (category in 0 until GameConfig.CATEGORY_COUNT) category else GameConfig.MIXED
        picker = WordPicker(words, this.category, random)
        turnIndex = 0
        results.clear()
        phase = Phase.READY
    }

    /** 넘기기·카운트 끝 → 첫 단어 */
    fun beginTurn(): CharadesWord {
        check(phase == Phase.READY)
        phase = Phase.PLAYING
        results.clear()
        return pick()
    }

    private fun pick(): CharadesWord {
        val w = picker.next()
        word = w
        shownWords.add(w.text)
        return w
    }

    /**
     * 정답 → 점수 → 다음 단어. guesser: 개인전은 맞힌 사람(필수, 설명자 아닌 사람), 팀전은 생략하면 팀원.
     */
    fun correct(guesser: Int = -1): CharadesWord {
        check(phase == Phase.PLAYING)
        val e = explainer
        val g = if (isTeam) guessersOf(e)[0] else guesser
        require(g in guessersOf(e)) { "맞힌 사람이 올바르지 않음: $g" }
        val w = word!!
        results.add(WordResult(w.text, w.category, Answer.CORRECT, g))
        if (isTeam) {
            teamScores[teamOf(e)] += GameConfig.POINTS_TEAM
            scores[e] += GameConfig.POINTS_TEAM
        } else {
            scores[g] += GameConfig.POINTS_GUESSER
            scores[e] += GameConfig.POINTS_EXPLAINER
        }
        return pick()
    }

    /** 패스 → 다음 단어. 패스가 남지 않았으면 null (단어 그대로) */
    fun pass(): CharadesWord? {
        check(phase == Phase.PLAYING)
        if (passesLeft <= 0) return null
        val w = word!!
        results.add(WordResult(w.text, w.category, Answer.PASS, -1))
        return pick()
    }

    /**
     * 90초 끝: 보고 있던 단어는 TIMEOUT 으로 목록에. 이번 차례 맞힌 개수를 돌려준다.
     * currentShown = false: 다음 단어가 아직 화면에 안 나왔음 (정답·패스 화면 도중) → 목록에 넣지 않음
     */
    fun timeUp(currentShown: Boolean = true): Int {
        check(phase == Phase.PLAYING)
        val w = word
        if (w != null && currentShown) results.add(WordResult(w.text, w.category, Answer.TIMEOUT, -1))
        word = null
        phase = Phase.REVIEW
        return correctCount
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

    /** 순위 (players 순서, 1부터, 동점 같은 순위). 팀전은 팀 점수 → 같은 팀은 같은 순위 */
    fun ranks(): IntArray = Ranking.competition((0 until playerCount).map { displayScore(it).toLong() })

    /** 팀전 이긴 팀 (0/1, 비기면 -1, 개인전 -1) */
    val winningTeam: Int
        get() = if (!isTeam || teamScores[0] == teamScores[1]) -1 else if (teamScores[0] > teamScores[1]) 0 else 1
}
