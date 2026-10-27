package com.siwoo.party.games.colorcode

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 핀 판정: 금색 = 색·자리 모두 맞음, 은색 = 색만 맞음 (자리는 다름) */
data class Pegs(val gold: Int, val silver: Int) {
    /** 4칸 모두 금색 = 정답 */
    val isSolved: Boolean get() = gold == GameConfig.SLOTS
}

/** 추리 한 번 (기록판 한 줄, 공개 정보) */
class GuessRecord(
    /** 추리한 사람 */
    val player: Int,
    /** 추리한 색 (길이 SLOTS, 복사본) */
    val colors: IntArray,
    val pegs: Pegs,
    /** 이 사람의 몇 번째 추리인지 (1부터) */
    val number: Int,
)

enum class Phase {
    /** 비밀 코드 정하는 중 */
    SETUP,
    /** 번갈아 추리 */
    PLAYING,
    OVER,
}

/** 추리 결과 + 그 뒤 상태 */
class GuessResult(
    val record: GuessRecord,
    /** 선공이 맞혀서, 후공의 같은 번째 추리가 마지막 기회가 됨 */
    val lastChanceStarted: Boolean,
    /** 판이 끝남 */
    val gameOver: Boolean,
)

/**
 * 색 코드 맞히기 규칙 (2인, 순수 Kotlin).
 * - 각자 6색 중 4칸 코드를 비밀로 정한다. 중복 허용 옵션이 꺼져 있으면(기본) 서로 다른 4색,
 *   켜져 있으면 같은 색을 여러 번 써도 된다. 추리도 같은 규칙을 따른다 (끔이면 추리도 서로 다른 4색만).
 * - 선공(first)부터 번갈아 상대 코드를 추리 → 금색 핀(색·자리 모두 맞음) / 은색 핀(색만 맞음) 개수를 알려 준다.
 *   은색 = 색마다 min(코드 속 개수, 추리 속 개수) 의 합 − 금색.
 * - 한 사람 최대 MAX_TRIES 번. 공정하게 두 사람은 항상 같은 횟수만큼 추리한다:
 *   선공이 k 번째에 맞히면 후공도 k 번째 추리까지 하고 끝 (후공도 맞히면 무승부).
 *   후공이 k 번째에 먼저 맞히면(선공은 k 번째까지 못 맞힘) 후공 승리.
 * - 판이 끝나면 더 적은 횟수로 맞힌 사람이 1위 (못 맞히면 MAX_TRIES+1 로 계산, 같으면 무승부 = 둘 다 1위).
 */
class ColorCodeGame(val allowDuplicates: Boolean = GameConfig.DUPLICATES_DEFAULT, val first: Int = 0) {

    init {
        require(first in 0 until GameConfig.PLAYERS)
    }

    val second: Int get() = 1 - first

    private val secrets = arrayOfNulls<IntArray>(GameConfig.PLAYERS)

    var phase = Phase.SETUP
        private set

    /** 지금 추리할 사람 */
    var current = first
        private set

    /** 지금 몇 번째 추리 차례인지 (1부터; 선공·후공이 한 번씩 하면 다음) */
    var round = 1
        private set

    /** 지금 차례가 마지막 기회인지 (선공이 이번 번째에 맞혀서 후공 차례) */
    var lastChance = false
        private set

    /** 사람별 추리 기록 (공개) */
    val history: List<List<GuessRecord>> get() = histories
    private val histories = List(GameConfig.PLAYERS) { ArrayList<GuessRecord>() }

    /** 맞힌 번째 (못 맞혔으면 -1) */
    private val solved = IntArray(GameConfig.PLAYERS) { -1 }

    fun hasSecret(p: Int): Boolean = secrets[p] != null

    /** 비밀 코드 (끝난 뒤 공개용, 복사본) */
    fun secretOf(p: Int): IntArray? = secrets[p]?.copyOf()

    /** p 가 맞힌 번째 (-1 = 못 맞힘) */
    fun solvedAt(p: Int): Int = solved[p]

    /** p 가 지금까지 한 추리 수 */
    fun tries(p: Int): Int = histories[p].size

    /** 순위용 기록: 맞힌 번째, 못 맞혔으면 UNSOLVED_SCORE (적을수록 좋음) */
    fun score(p: Int): Int = if (solved[p] > 0) solved[p] else GameConfig.UNSOLVED_SCORE

    /** 이 판 규칙에 맞는 코드인지 */
    fun isValid(code: IntArray): Boolean = isValidCode(code, allowDuplicates)

    /** 비밀 코드 정하기 (규칙에 안 맞으면 false). 둘 다 정하면 PLAYING */
    fun setSecret(p: Int, code: IntArray): Boolean {
        if (phase != Phase.SETUP || p !in 0 until GameConfig.PLAYERS || !isValid(code)) return false
        secrets[p] = code.copyOf()
        if (secrets.all { it != null }) {
            phase = Phase.PLAYING
            current = first
        }
        return true
    }

    /** 지금 차례 사람이 상대 코드를 추리. 규칙에 안 맞는 코드·진행 중이 아니면 null */
    fun guess(code: IntArray): GuessResult? {
        if (phase != Phase.PLAYING || !isValid(code)) return null
        val p = current
        val target = secrets[1 - p] ?: return null
        val pegs = score(target, code)
        val rec = GuessRecord(p, code.copyOf(), pegs, histories[p].size + 1)
        histories[p].add(rec)
        if (pegs.isSolved) solved[p] = rec.number
        var lastStarted = false
        if (p == first) {
            // 후공은 항상 같은 번째 추리를 한다 (공정)
            current = second
            if (pegs.isSolved) {
                lastChance = true
                lastStarted = true
            }
        } else {
            // 한 번째가 끝남: 누가 맞혔거나 최대 횟수면 끝
            lastChance = false
            if (solved[0] > 0 || solved[1] > 0 || round >= GameConfig.MAX_TRIES) {
                phase = Phase.OVER
            } else {
                round++
                current = first
            }
        }
        return GuessResult(rec, lastStarted, phase == Phase.OVER)
    }

    /** 승자 (무승부·진행 중이면 -1) */
    val winner: Int
        get() {
            if (phase != Phase.OVER) return -1
            val a = score(0)
            val b = score(1)
            return if (a < b) 0 else if (b < a) 1 else -1
        }

    val isDraw: Boolean get() = phase == Phase.OVER && winner < 0

    /** 순위 (players 순서, 1부터). 적은 횟수가 위, 같으면 같은 순위 */
    fun ranks(): IntArray {
        check(phase == Phase.OVER)
        return Ranking.competition(List(GameConfig.PLAYERS) { score(it).toLong() }, higherIsBetter = false)
    }

    companion object {
        /** 길이 SLOTS, 색 0..COLORS-1, 중복 금지면 서로 다른 색 */
        fun isValidCode(code: IntArray, allowDuplicates: Boolean): Boolean {
            if (code.size != GameConfig.SLOTS) return false
            for (i in code.indices) {
                if (code[i] !in 0 until GameConfig.COLORS) return false
                if (!allowDuplicates) for (k in 0 until i) if (code[k] == code[i]) return false
            }
            return true
        }

        /**
         * 핀 계산: 금색 = 같은 자리 같은 색 수,
         * 은색 = 색마다 min(코드 속 개수, 추리 속 개수) 의 합 − 금색.
         */
        fun score(code: IntArray, guess: IntArray): Pegs {
            require(code.size == guess.size)
            var gold = 0
            val inCode = IntArray(GameConfig.COLORS)
            val inGuess = IntArray(GameConfig.COLORS)
            for (i in code.indices) {
                if (code[i] == guess[i]) gold++
                inCode[code[i]]++
                inGuess[guess[i]]++
            }
            var common = 0
            for (c in 0 until GameConfig.COLORS) common += minOf(inCode[c], inGuess[c])
            return Pegs(gold, common - gold)
        }

        /** 규칙에 맞는 무작위 코드 */
        fun randomCode(random: Random, allowDuplicates: Boolean): IntArray {
            if (allowDuplicates) return IntArray(GameConfig.SLOTS) { random.nextInt(GameConfig.COLORS) }
            val pool = (0 until GameConfig.COLORS).toMutableList()
            pool.shuffle(random)
            return IntArray(GameConfig.SLOTS) { pool[it] }
        }
    }
}
