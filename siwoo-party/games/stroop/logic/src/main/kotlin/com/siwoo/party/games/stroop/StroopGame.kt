package com.siwoo.party.games.stroop

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 문제에 쓰는 네 가지 색 (버튼 순서도 이 순서, 모든 구역 같음) */
enum class Hue(val label: String) {
    RED("빨강"),
    BLUE("파랑"),
    GREEN("초록"),
    YELLOW("노랑"),
}

/** 이번 문제 규칙 */
enum class Rule(val label: String, val short: String) {
    /** 글자가 칠해진 색을 누른다 */
    INK("글자 색을 눌러라", "색"),
    /** 글자가 뜻하는 색을 누른다 */
    WORD("글자 뜻을 눌러라", "뜻"),
}

/** 문제 하나: word 라는 글자가 ink 색으로 칠해져 있다 (word != ink) */
class Question(val number: Int, val word: Hue, val ink: Hue, val rule: Rule) {
    val answer: Hue get() = if (rule == Rule.INK) ink else word
}

enum class Phase { IDLE, ASKING }

/** 누른 결과 */
sealed class Press {
    /** 무시 (문제 밖·범위 밖) */
    object Ignored : Press()
    /** 잠금 중이라 무시 */
    class Locked(val player: Int, val untilMs: Long) : Press()
    /** 정답 (먼저 맞힘 → 문제 끝) */
    class Correct(val player: Int, val hue: Hue) : Press()
    /** 오답 → -1, untilMs 까지 잠금 */
    class Wrong(val player: Int, val hue: Hue, val untilMs: Long) : Press()
}

/**
 * 색깔 함정 규칙 (스트룹 효과).
 * - 문제마다 색 이름 글자가 다른 색으로 칠해져 나오고, 규칙(글자 색 / 글자 뜻)이 무작위로 정해진다.
 * - 규칙에 맞는 색 버튼을 가장 먼저 누른 사람 +1, 그 문제는 바로 끝.
 * - 틀리면 -1, 그리고 1초 동안 누를 수 없다 (잠금은 게임 시간 기준이라 다음 문제까지 이어질 수 있음).
 * - 6초 동안 아무도 못 맞히면 넘어간다. 15문제 뒤 점수 순 (동점 같은 순위).
 * 시간은 화면이 넘겨준다(ms, 게임 시간) → 테스트에서 그대로 재현 가능.
 */
class StroopGame(val playerCount: Int, private val random: Random = Random.Default) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val scores = IntArray(playerCount)
    /** 플레이어별 잠금 끝 시각 (게임 시간 ms) */
    val lockUntil = LongArray(playerCount) { Long.MIN_VALUE }
    /** 끝난 문제 수 (맞힘·시간 초과 모두) */
    var questionsDone = 0
        private set
    var phase = Phase.IDLE
        private set
    var current: Question? = null
        private set
    /** 이번 문제를 맞힌 사람 (-1 = 아직/아무도) */
    var questionWinner = -1
        private set

    val gameOver: Boolean get() = questionsDone >= GameConfig.STROOP_QUESTIONS

    /** 무작위 다음 문제 (글자 ≠ 색, 앞 문제와 같은 글자는 피함) */
    fun nextQuestion(): Question {
        val prev = current
        var word: Hue
        do {
            word = HUES[random.nextInt(HUES.size)]
        } while (prev != null && word == prev.word)
        var ink: Hue
        do {
            ink = HUES[random.nextInt(HUES.size)]
        } while (ink == word)
        val rule = if (random.nextBoolean()) Rule.INK else Rule.WORD
        return ask(word, ink, rule)
    }

    /** 정해진 문제를 낸다 (테스트·재현용) */
    fun ask(word: Hue, ink: Hue, rule: Rule): Question {
        check(!gameOver) { "게임이 끝났습니다" }
        require(word != ink) { "글자와 색은 달라야 합니다" }
        val q = Question(questionsDone + 1, word, ink, rule)
        current = q
        questionWinner = -1
        phase = Phase.ASKING
        return q
    }

    fun isLocked(player: Int, nowMs: Long): Boolean = nowMs < lockUntil[player]

    /** player 가 hue 버튼을 nowMs(게임 시간)에 눌렀다 */
    fun press(player: Int, hue: Hue, nowMs: Long): Press {
        if (player !in 0 until playerCount) return Press.Ignored
        val q = current
        if (phase != Phase.ASKING || q == null) return Press.Ignored
        if (isLocked(player, nowMs)) return Press.Locked(player, lockUntil[player])
        return if (hue == q.answer) {
            scores[player] += GameConfig.CORRECT_POINTS
            questionWinner = player
            endQuestion()
            Press.Correct(player, hue)
        } else {
            scores[player] -= GameConfig.WRONG_PENALTY
            lockUntil[player] = nowMs + GameConfig.WRONG_LOCK_MS
            Press.Wrong(player, hue, lockUntil[player])
        }
    }

    /** 시간 초과: 아직 묻는 중이면 아무도 점수 없이 끝. 끝냈으면 true */
    fun timeout(): Boolean {
        if (phase != Phase.ASKING) return false
        endQuestion()
        return true
    }

    private fun endQuestion() {
        phase = Phase.IDLE
        questionsDone++
    }

    /** 최종 순위 (1부터): 점수 높은 순, 같은 점수 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })

    companion object {
        private val HUES = Hue.values()
    }
}
