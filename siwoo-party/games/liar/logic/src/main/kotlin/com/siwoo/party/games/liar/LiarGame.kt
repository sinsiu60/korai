package com.siwoo.party.games.liar

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 모드: 일반(라이어는 자신이 라이어인 걸 앎) / 바보(라이어도 같은 카테고리의 다른 단어를 받아 모름) */
enum class LiarMode(val label: String) {
    NORMAL("일반 모드"),
    FOOL("바보 모드"),
}

/** 한 판 단계 */
enum class Phase {
    /** 첫 판 전 모드 고르기 */
    MODE_SELECT,
    /** 판과 판 사이 (결과 카드) */
    READY,
    /** 카테고리 공개 */
    CATEGORY,
    /** 한 명씩 제시어 확인 */
    WORD_CHECK,
    /** 한 문장 설명 (2바퀴) */
    EXPLAIN,
    /** 토론 */
    DISCUSS,
    /** 비밀 투표 (재투표 포함) */
    VOTE,
    /** 개표 */
    COUNT,
    /** 지목된 라이어가 정답 맞히기 */
    GUESS,
    /** 판 끝 (결과) */
    ROUND_OVER,
    /** 모든 판 끝 */
    GAME_OVER,
}

/** 판 결과 */
enum class Outcome(val label: String) {
    NOT_CAUGHT("라이어가 안 걸림"),
    CAUGHT_CORRECT("걸렸지만 정답 맞힘"),
    CAUGHT_WRONG("걸리고 정답 틀림"),
}

/** 개표 결과 */
sealed class Tally(val counts: IntArray) {
    /** 최다 득표 한 명 */
    class Accused(val player: Int, counts: IntArray) : Tally(counts)
    /** 최다 득표 동점 → 동점자만 놓고 재투표 */
    class Revote(val tied: List<Int>, counts: IntArray) : Tally(counts)
    /** 재투표도 동점 → 라이어가 안 걸림 */
    class TieAgain(val tied: List<Int>, counts: IntArray) : Tally(counts)
}

/** 끝난 판 하나 */
class RoundResult(
    val round: Int,
    val liar: Int,
    val category: Int,
    val word: String,
    /** 바보 모드에서 라이어가 받은 단어 (일반 모드 null) */
    val liarWord: String?,
    /** 지목된 사람 (재투표도 동점이면 -1) */
    val accused: Int,
    val outcome: Outcome,
    /** 라이어가 고른 답 (맞히기를 안 했으면 null) */
    val guess: String?,
    /** 이번 판에 얻은 점수 (players 순서) */
    val gained: IntArray,
)

/**
 * 라이어 게임 규칙 (3~4인, 판 수 = 인원수).
 * - 판마다 카테고리 무작위(한 게임 안에서 겹치지 않음), 그 카테고리의 단어 하나가 제시어
 * - 라이어: 매 판 무작위이되 N판 동안 모두 한 번씩 (liarOrder)
 * - 제시어 확인·투표는 시작 사람(starter)부터 순서대로, 설명은 starter 부터 2바퀴
 * - 투표: 자기 자신은 못 고름. 최다 득표 동점이면 동점자만 놓고 재투표 1회 (투표는 모두가, 후보인 사람은 다른 후보 중에서),
 *   그래도 동점이면 라이어가 안 걸린 것
 * - 지목된 사람이 라이어면 같은 카테고리 단어 8개(정답 포함) 중 정답 맞히기
 * - 점수: 안 걸림 라이어 +2 / 걸렸지만 정답 라이어 +1 / 걸리고 틀림 라이어 빼고 모두 +1
 * - 최종 순위: 누적 점수 (같으면 공동 순위)
 * 난수는 밖에서 주입 → 테스트에서 그대로 재현 가능. 시간(설명·토론 타이머)은 화면이 관리한다.
 */
class LiarGame(val playerCount: Int, words: List<List<String>> = LiarWords.DEFAULT, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 카테고리별 단어 (8개 미만이면 기본 단어로 채움) */
    val words: List<List<String>> = LiarWords.sanitizeAll(words)

    /** 판 수 = 인원수 */
    val totalRounds: Int = playerCount
    /** k 번째 판(0부터)의 라이어: 모두 한 번씩, 순서는 무작위 */
    val liarOrder: List<Int> = (0 until playerCount).shuffled(random)
    /** k 번째 판의 카테고리 (한 게임 안에서 겹치지 않음) */
    private val categoryOrder: List<Int> = LiarWords.CATEGORIES.indices.shuffled(random)

    val scores = IntArray(playerCount)
    val results = ArrayList<RoundResult>()

    var mode: LiarMode = LiarMode.NORMAL
        private set
    var phase = Phase.MODE_SELECT
        private set
    /** 끝난 판 수 */
    var roundsDone = 0
        private set
    /** 지금 하는(또는 방금 끝난) 판 번호 (1부터) */
    val roundNumber: Int
        get() = if (phase == Phase.ROUND_OVER || phase == Phase.GAME_OVER || phase == Phase.READY) roundsDone.coerceAtLeast(1) else roundsDone + 1
    val gameOver: Boolean get() = roundsDone >= totalRounds

    var liar = -1
        private set
    var category = -1
        private set
    var word = ""
        private set
    /** 바보 모드에서 라이어가 받는 다른 단어 (일반 모드 null) */
    var liarWord: String? = null
        private set
    /** 이번 판 시작 사람 (제시어 확인·설명·투표 순서의 처음) */
    var starter = 0
        private set

    val categoryName: String get() = if (category < 0) "" else LiarWords.CATEGORIES[category]

    /** 제시어 확인·투표 순서: starter 부터 한 바퀴 */
    val turnOrder: List<Int> get() = List(playerCount) { (starter + it) % playerCount }

    // ── 제시어 확인 ──
    private var checkIndex = 0
    /** 지금 제시어를 확인할 사람 */
    val checker: Int get() = turnOrder[checkIndex.coerceAtMost(playerCount - 1)]
    val checkedCount: Int get() = checkIndex

    // ── 설명 ──
    /** 설명 순서: starter 부터 2바퀴 */
    val explainOrder: List<Int> get() = explainOrder(starter, playerCount)
    var speakIndex = 0
        private set
    val speaker: Int get() = explainOrder[speakIndex.coerceAtMost(explainOrder.size - 1)]
    /** 지금 몇 바퀴째인지 (1부터) */
    val lap: Int get() = speakIndex / playerCount + 1

    // ── 투표 ──
    /** 표를 받을 수 있는 사람 (재투표면 동점자만) */
    var candidates: List<Int> = emptyList()
        private set
    /** 재투표 중인지 */
    var revote = false
        private set
    /** votes[투표한 사람] = 고른 사람 (-1 = 아직) */
    val votes = IntArray(playerCount) { -1 }
    private var voteIndex = 0
    /** 지금 투표할 사람 */
    val voter: Int get() = turnOrder[voteIndex.coerceAtMost(playerCount - 1)]
    val votedCount: Int get() = voteIndex
    /** 개표 결과 (COUNT 이후) */
    var lastTally: Tally? = null
        private set
    /** 지목된 사람 (-1 = 없음/재투표도 동점) */
    var accused = -1
        private set

    // ── 정답 맞히기 ──
    /** 보기 8개 (정답 포함, 섞임) */
    var guessChoices: List<String> = emptyList()
        private set

    val lastResult: RoundResult? get() = results.lastOrNull()

    /** 첫 판 전 모드 고르기 */
    fun chooseMode(m: LiarMode) {
        check(phase == Phase.MODE_SELECT) { "모드는 첫 판 전에만 고릅니다" }
        mode = m
        phase = Phase.READY
    }

    /** 새 판: 카테고리·제시어·라이어·시작 사람 정하기 (카테고리 공개 단계) */
    fun startRound() {
        check(phase == Phase.READY || phase == Phase.ROUND_OVER) { "판 진행 중입니다 ($phase)" }
        check(!gameOver) { "게임이 끝났습니다" }
        val k = roundsDone
        liar = liarOrder[k]
        category = categoryOrder[k % categoryOrder.size]
        val list = words[category]
        word = list[random.nextInt(list.size)]
        liarWord = if (mode == LiarMode.FOOL) foolWord(list, word, random) else null
        starter = random.nextInt(playerCount)
        checkIndex = 0
        speakIndex = 0
        candidates = emptyList()
        revote = false
        votes.fill(-1)
        voteIndex = 0
        lastTally = null
        accused = -1
        guessChoices = emptyList()
        phase = Phase.CATEGORY
    }

    /**
     * player 가 보는 제시어. 라이어면 일반 모드 null("당신은 라이어입니다"),
     * 바보 모드에서는 다른 단어 (일반 단어와 똑같이 보여 준다)
     */
    fun wordFor(player: Int): String? = if (player == liar) liarWord else word

    fun beginWordCheck() {
        check(phase == Phase.CATEGORY)
        checkIndex = 0
        phase = Phase.WORD_CHECK
    }

    /** 지금 사람이 확인 완료. 모두 확인했으면 true (설명 단계로) */
    fun wordChecked(): Boolean {
        check(phase == Phase.WORD_CHECK)
        checkIndex++
        if (checkIndex >= playerCount) {
            speakIndex = 0
            phase = Phase.EXPLAIN
            return true
        }
        return false
    }

    /** 다음 설명할 사람으로. 2바퀴가 끝났으면 true (토론 단계로) */
    fun nextSpeaker(): Boolean {
        check(phase == Phase.EXPLAIN)
        speakIndex++
        if (speakIndex >= explainOrder.size) {
            phase = Phase.DISCUSS
            return true
        }
        return false
    }

    /** 설명을 건너뛰고 토론으로 (화면 테스트·[바로 토론]용) */
    fun skipExplain() {
        check(phase == Phase.EXPLAIN)
        speakIndex = explainOrder.size
        phase = Phase.DISCUSS
    }

    /** 토론 끝 → 투표 (후보는 모두) */
    fun startVote() {
        check(phase == Phase.DISCUSS)
        candidates = (0 until playerCount).toList()
        revote = false
        beginBallot()
    }

    private fun beginBallot() {
        votes.fill(-1)
        voteIndex = 0
        phase = Phase.VOTE
    }

    /** voter 가 고를 수 있는 사람: 후보 중 자기 자신 빼고 */
    fun choicesFor(voter: Int): List<Int> = candidates.filter { it != voter }

    fun canVote(voter: Int, target: Int): Boolean =
        phase == Phase.VOTE && target != voter && target in candidates && target in 0 until playerCount

    /** 지금 투표할 사람(voter)이 target 에게 투표. 잘못된 표면 false. 모두 투표했으면 개표 단계로 */
    fun castVote(target: Int): Boolean {
        val v = voter
        if (!canVote(v, target)) return false
        votes[v] = target
        voteIndex++
        if (voteIndex >= playerCount) phase = Phase.COUNT
        return true
    }

    /** 개표 순서(투표한 순서)대로 받은 사람 */
    fun ballots(): IntArray = IntArray(playerCount) { votes[turnOrder[it]] }

    /**
     * 개표 → 다음 단계로.
     * - 한 명 지목: 라이어면 정답 맞히기(GUESS), 아니면 판 끝(라이어 안 걸림)
     * - 첫 투표 동점: 동점자만 후보로 재투표(VOTE)
     * - 재투표도 동점: 판 끝(라이어 안 걸림)
     */
    fun countVotes(): Tally {
        check(phase == Phase.COUNT)
        val t = tally(votes, candidates, revote)
        lastTally = t
        when (t) {
            is Tally.Accused -> {
                accused = t.player
                if (t.player == liar) {
                    guessChoices = guessChoices(words[category], word, random)
                    phase = Phase.GUESS
                } else {
                    finishRound(Outcome.NOT_CAUGHT, null)
                }
            }
            is Tally.Revote -> {
                candidates = t.tied
                revote = true
                beginBallot()
            }
            is Tally.TieAgain -> {
                accused = -1
                finishRound(Outcome.NOT_CAUGHT, null)
            }
        }
        return t
    }

    /** 지목된 라이어의 답. 맞았는지 결과 */
    fun guess(answer: String): Outcome {
        check(phase == Phase.GUESS)
        require(answer in guessChoices) { "보기에 없는 답" }
        val o = if (answer == word) Outcome.CAUGHT_CORRECT else Outcome.CAUGHT_WRONG
        finishRound(o, answer)
        return o
    }

    private fun finishRound(outcome: Outcome, guess: String?) {
        val gained = points(outcome, liar, playerCount)
        for (i in 0 until playerCount) scores[i] += gained[i]
        roundsDone++
        results.add(RoundResult(roundsDone, liar, category, word, liarWord, accused, outcome, guess, gained))
        phase = if (gameOver) Phase.GAME_OVER else Phase.ROUND_OVER
    }

    /** 누적 점수 순위 (같으면 공동 순위) */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })

    companion object {
        /** 설명 순서: start 부터 n 명씩 2바퀴 */
        fun explainOrder(start: Int, n: Int): List<Int> =
            List(n * GameConfig.LIAR_EXPLAIN_LAPS) { (start + it) % n }

        /** 판 결과에 따른 점수 (players 순서) */
        fun points(outcome: Outcome, liar: Int, n: Int): IntArray {
            val g = IntArray(n)
            when (outcome) {
                Outcome.NOT_CAUGHT -> g[liar] += GameConfig.POINTS_NOT_CAUGHT
                Outcome.CAUGHT_CORRECT -> g[liar] += GameConfig.POINTS_CAUGHT_CORRECT
                Outcome.CAUGHT_WRONG -> for (i in 0 until n) if (i != liar) g[i] += GameConfig.POINTS_CAUGHT_WRONG
            }
            return g
        }

        /** 표 세기: 후보 중 최다 득표가 한 명이면 지목, 동점이면 (재투표 전이면) 재투표 / (재투표면) 다시 동점 */
        fun tally(votes: IntArray, candidates: List<Int>, revote: Boolean): Tally {
            val counts = IntArray(votes.size)
            for (v in votes) if (v >= 0) counts[v]++
            val max = candidates.maxOf { counts[it] }
            val top = candidates.filter { counts[it] == max }
            return when {
                top.size == 1 -> Tally.Accused(top[0], counts)
                !revote -> Tally.Revote(top, counts)
                else -> Tally.TieAgain(top, counts)
            }
        }

        /** 바보 모드 라이어 단어: 같은 카테고리의 다른 단어 */
        fun foolWord(list: List<String>, word: String, random: Random): String {
            val others = list.filter { it != word }
            return others[random.nextInt(others.size)]
        }

        /** 정답 맞히기 보기: 카테고리 단어 중 서로 다른 8개 (정답 포함, 섞음) */
        fun guessChoices(list: List<String>, answer: String, random: Random): List<String> {
            val others = list.filter { it != answer }.distinct().shuffled(random).take(GameConfig.GUESS_CHOICES - 1)
            return (others + answer).shuffled(random)
        }
    }
}
