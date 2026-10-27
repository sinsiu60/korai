package com.siwoo.party.games.baseball

/** 스트라이크·볼 판정 (0S 0B = 아웃) */
data class Judge(val strikes: Int, val balls: Int) {
    val isOut: Boolean get() = strikes == 0 && balls == 0
    val isHit: Boolean get() = strikes == GameConfig.DIGITS

    /** "2S 1B" / "아웃" / "3S" */
    val label: String
        get() = when {
            isOut -> "아웃"
            isHit -> "${strikes}S"
            else -> "${strikes}S ${balls}B"
        }
}

/** 추측 한 번 (기록표 한 줄) */
data class Guess(val player: Int, val number: String, val judge: Judge, val inning: Int)

enum class Phase {
    /** 비밀 숫자 정하는 중 */
    SETUP,
    /** 번갈아 추측 */
    PLAYING,
    /** 선공이 맞혀서 후공의 마지막 1회 */
    LAST_CHANCE,
    OVER,
}

/** 추측 결과: 판정 + 그 뒤 상태 */
data class GuessResult(
    val guess: Guess,
    /** 이 추측으로 마지막 기회가 시작됨 (선공이 맞힘) */
    val lastChanceStarted: Boolean,
    /** 판이 끝남 */
    val gameOver: Boolean,
    /** 끝났을 때 승자 (무승부면 -1, 안 끝났으면 -1) */
    val winner: Int,
)

/**
 * 숫자 야구 규칙 (2인, 순수 Kotlin).
 * - 각자 서로 다른 숫자 3자리 비밀 (0 포함, 맨 앞 0 가능)
 * - 선공(first)부터 번갈아 상대의 비밀 숫자를 추측 → 자리·숫자 모두 맞으면 스트라이크, 숫자만 맞으면 볼, 하나도 없으면 아웃
 * - 3스트라이크면 승리. 단 선공이 맞히면 후공에게 마지막 1회 (맞히면 무승부, 못 맞히면 선공 승)
 * - 후공이 (선공이 못 맞힌 회에) 먼저 맞히면 바로 승리
 */
class BaseballGame(val first: Int = 0) {

    init {
        require(first in 0 until GameConfig.PLAYERS)
    }

    val second: Int get() = 1 - first

    private val secrets = arrayOfNulls<String>(GameConfig.PLAYERS)

    var phase = Phase.SETUP
        private set

    /** 지금 추측할 사람 */
    var current = first
        private set

    /** 지금 회 (1부터; 선공·후공이 한 번씩 추측하면 다음 회) */
    var inning = 1
        private set

    /** 사람별 추측 기록 (공개) */
    val history: List<List<Guess>> get() = histories
    private val histories = List(GameConfig.PLAYERS) { ArrayList<Guess>() }

    /** 승자 (무승부·진행 중이면 -1) */
    var winner = -1
        private set

    val isDraw: Boolean get() = phase == Phase.OVER && winner < 0

    fun hasSecret(p: Int): Boolean = secrets[p] != null

    /** 비밀 숫자 (끝난 뒤 공개용) */
    fun secretOf(p: Int): String? = secrets[p]

    /** 비밀 숫자 정하기 (규칙에 안 맞으면 false). 둘 다 정하면 PLAYING */
    fun setSecret(p: Int, s: String): Boolean {
        if (phase != Phase.SETUP || p !in 0 until GameConfig.PLAYERS || !isValidNumber(s)) return false
        secrets[p] = s
        if (secrets.all { it != null }) {
            phase = Phase.PLAYING
            current = first
        }
        return true
    }

    /** 지금 차례 사람이 상대 숫자를 추측. 규칙에 안 맞는 숫자·진행 중이 아니면 null */
    fun guess(s: String): GuessResult? {
        if (phase != Phase.PLAYING && phase != Phase.LAST_CHANCE) return null
        if (!isValidNumber(s)) return null
        val p = current
        val target = secrets[1 - p] ?: return null
        val j = judge(target, s)
        val g = Guess(p, s, j, inning)
        histories[p].add(g)
        var lastStarted = false
        if (phase == Phase.LAST_CHANCE) {
            // 후공의 마지막 1회: 맞히면 무승부, 아니면 선공 승
            winner = if (j.isHit) -1 else first
            phase = Phase.OVER
        } else if (j.isHit) {
            if (p == first) {
                phase = Phase.LAST_CHANCE
                current = second
                lastStarted = true
            } else {
                winner = second
                phase = Phase.OVER
            }
        } else {
            if (p == second) inning++
            current = 1 - p
        }
        return GuessResult(g, lastStarted, phase == Phase.OVER, winner)
    }

    /** 순위 (players 순서, 1부터). 무승부 [1, 1] */
    fun ranks(): IntArray {
        check(phase == Phase.OVER)
        return IntArray(GameConfig.PLAYERS) { if (winner < 0 || it == winner) 1 else 2 }
    }

    companion object {
        /** 서로 다른 숫자 DIGITS 개 (0 포함, 맨 앞 0 가능) */
        fun isValidNumber(s: String): Boolean {
            if (s.length != GameConfig.DIGITS) return false
            for (i in s.indices) {
                if (s[i] !in '0'..'9') return false
                for (k in 0 until i) if (s[k] == s[i]) return false
            }
            return true
        }

        /** 비밀 secret 에 대한 추측 guess 의 판정 */
        fun judge(secret: String, guess: String): Judge {
            require(secret.length == guess.length)
            var s = 0
            var b = 0
            for (i in guess.indices) {
                if (guess[i] == secret[i]) s++
                else if (secret.indexOf(guess[i]) >= 0) b++
            }
            return Judge(s, b)
        }
    }
}
