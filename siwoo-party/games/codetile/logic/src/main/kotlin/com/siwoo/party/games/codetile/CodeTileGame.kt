package com.siwoo.party.games.codetile

import kotlin.random.Random

/**
 * 타일 번호 도우미. 타일 한 장 = 코드 0..23 (숫자 × 2 + 색, 검정 0 / 흰색 1).
 * 코드 오름차순 = "숫자 오름차순, 같은 숫자는 검정이 왼쪽" 정렬 규칙 그대로.
 */
object Tiles {
    fun code(number: Int, black: Boolean): Int = number * 2 + if (black) 0 else 1
    fun number(code: Int): Int = code / 2
    fun isBlack(code: Int): Boolean = code % 2 == 0

    /** 정렬된 코드 목록(rowCodes)에 code 를 넣을 자리 (그 앞까지는 모두 code 보다 작다) */
    fun insertIndex(rowCodes: List<Int>, code: Int): Int {
        var i = 0
        while (i < rowCodes.size && rowCodes[i] < code) i++
        return i
    }

    /** 글자 (테스트·기록용): "5검" / "5흰" */
    fun label(code: Int): String = "${number(code)}${if (isBlack(code)) "검" else "흰"}"
}

/** 줄에 놓인 타일 한 장 */
class Tile(val code: Int, revealed: Boolean = false) {
    var revealed = revealed
        internal set
    val number: Int get() = Tiles.number(code)
    val black: Boolean get() = Tiles.isBlack(code)
}

/** 진행 단계 */
enum class Phase {
    /** 현재 차례가 상대의 숨은 타일 하나를 골라 숫자를 추리 */
    GUESS,
    /** 정답 뒤: 계속 추리하거나 멈추기 */
    DECIDE,
    /** 더미가 비어 뽑은 타일 없이 틀림 → 내 숨은 타일 하나를 골라 공개 */
    PENALTY,
    OVER,
}

/**
 * 추리 한 번의 결과. 오답이면 상대 타일의 진짜 숫자는 담지 않는다 (공개 화면에 새지 않게).
 * @param insertedIndex 오답으로 뽑은 타일이 공개된 채 끼워진 자리 (-1 = 없음)
 * @param insertedCode 그 타일 (-1 = 없음)
 * @param penalty 더미가 비어 있어 내 타일 하나를 공개해야 함
 * @param eliminated 이번 추리로 탈락한 사람 (-1 = 없음)
 */
class GuessResult(
    val guesser: Int,
    val target: Int,
    val index: Int,
    val number: Int,
    val correct: Boolean,
    val insertedIndex: Int,
    val insertedCode: Int,
    val penalty: Boolean,
    val eliminated: Int,
    val gameOver: Boolean,
)

/** 내 타일 공개(벌칙) 결과 */
class RevealResult(val player: Int, val index: Int, val code: Int, val eliminated: Boolean, val gameOver: Boolean)

/**
 * 숨은 숫자 타일 규칙 (2~4인). 화면·시간과 무관한 순수 Kotlin.
 * - 0~11 검정·흰색 타일 24장, 2~3인은 4장, 4인은 3장씩. 자기 줄은 오름차순(같은 숫자는 검정 왼쪽)
 * - 차례 시작에 더미에서 1장 뽑음(더미가 비면 안 뽑음) → 상대의 숨은 타일 하나를 골라 숫자 추리
 *   - 정답: 그 타일 공개 → 계속 추리 또는 멈추기 (멈추면 뽑은 타일을 숨긴 채 정렬 자리에 넣음)
 *   - 오답: 뽑은 타일을 공개한 채 정렬 자리에 넣음.
 *     뽑은 타일이 없으면(더미가 빔) 내 숨은 타일 하나를 직접 골라 공개 (다빈치 코드 표준 규칙)
 * - 숨은 타일이 하나도 없으면 탈락, 최후의 1인 승리. 순위: 승자 1위, 나머지는 늦게 탈락한 순
 */
class CodeTileGame private constructor(
    val playerCount: Int,
    hands: List<List<Int>>,
    pileCodes: List<Int>,
    val starter: Int,
) {
    private val rows: List<MutableList<Tile>> = hands.map { h -> h.sorted().mapTo(ArrayList()) { Tile(it) } }
    private val pile = ArrayList(pileCodes)

    var phase = Phase.GUESS
        private set
    /** 지금 차례 */
    var current = starter
        private set
    /** 이번 차례에 뽑은 타일 (-1 = 없음: 더미가 비었거나 이미 줄에 넣음) */
    var drawn = -1
        private set
    /** 이번 차례 정답 수 */
    var correctThisTurn = 0
        private set
    /** 지금까지의 차례 수 (1부터) */
    var turn = 0
        private set
    /** 탈락한 순서 */
    val eliminationOrder: List<Int> get() = eliminated
    private val eliminated = ArrayList<Int>()
    private val out = BooleanArray(playerCount)

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(hands.size == playerCount)
        beginTurn()
    }

    /** 더미에 남은 타일 수 */
    val pileSize: Int get() = pile.size

    fun rowSize(p: Int): Int = rows[p].size
    fun tileAt(p: Int, i: Int): Tile = rows[p][i]
    fun codes(p: Int): List<Int> = rows[p].map { it.code }
    fun hiddenCount(p: Int): Int = rows[p].count { !it.revealed }
    fun isOut(p: Int): Boolean = out[p]
    fun isAlive(p: Int): Boolean = !out[p]
    val aliveCount: Int get() = out.count { !it }
    /** 남은 한 사람 (끝났을 때) */
    val winner: Int get() = if (phase == Phase.OVER) out.indexOfFirst { !it } else -1

    /** 공개 화면용: 공개된 타일이면 숫자, 숨은 타일이면 -1 (숨은 숫자는 절대 돌려주지 않음) */
    fun publicNumber(p: Int, i: Int): Int = rows[p][i].let { if (it.revealed) it.number else -1 }
    fun isBlackAt(p: Int, i: Int): Boolean = rows[p][i].black
    fun isRevealed(p: Int, i: Int): Boolean = rows[p][i].revealed

    /** 이 숫자·색 타일이 누군가의 줄에 공개돼 있는지 (공개 정보) */
    fun isPubliclyRevealed(code: Int): Boolean = rows.any { r -> r.any { it.code == code && it.revealed } }

    /** 고를 수 있는 상대 타일인지 */
    fun canTarget(p: Int, i: Int): Boolean =
        phase == Phase.GUESS && p != current && p in 0 until playerCount && !out[p] && i in rows[p].indices && !rows[p][i].revealed

    /** 추리. 잘못된 입력이면 null */
    fun guess(target: Int, index: Int, number: Int): GuessResult? {
        if (!canTarget(target, index) || number !in 0 until GameConfig.NUMBERS) return null
        val me = current
        val tile = rows[target][index]
        if (tile.number == number) {
            tile.revealed = true
            correctThisTurn++
            var elim = -1
            if (hiddenCount(target) == 0) {
                eliminate(target)
                elim = target
            }
            val over = aliveCount <= 1
            phase = if (over) Phase.OVER else Phase.DECIDE
            return GuessResult(me, target, index, number, true, -1, -1, false, elim, over)
        }
        // 오답
        val d = drawn
        if (d >= 0) {
            drawn = -1
            val at = insert(me, d, revealed = true)
            endTurn()
            return GuessResult(me, target, index, number, false, at, d, false, -1, false)
        }
        phase = Phase.PENALTY
        return GuessResult(me, target, index, number, false, -1, -1, true, -1, false)
    }

    /** 정답 뒤 계속 추리 */
    fun continueGuess(): Boolean {
        if (phase != Phase.DECIDE) return false
        phase = Phase.GUESS
        return true
    }

    /** 정답 뒤 멈추기: 뽑은 타일을 숨긴 채 넣고 다음 사람. 넣은 자리 (-1 = 뽑은 타일 없음), 못 하면 null */
    fun stop(): Int? {
        if (phase != Phase.DECIDE) return null
        val d = drawn
        var at = -1
        if (d >= 0) {
            drawn = -1
            at = insert(current, d, revealed = false)
        }
        endTurn()
        return at
    }

    /** 고를 수 있는 내 벌칙 타일인지 */
    fun canReveal(i: Int): Boolean = phase == Phase.PENALTY && i in rows[current].indices && !rows[current][i].revealed

    /** 벌칙: 내 숨은 타일 하나 공개 */
    fun revealOwn(index: Int): RevealResult? {
        if (!canReveal(index)) return null
        val me = current
        val tile = rows[me][index]
        tile.revealed = true
        var elim = false
        if (hiddenCount(me) == 0) {
            eliminate(me)
            elim = true
        }
        val over = aliveCount <= 1
        if (over) phase = Phase.OVER else endTurn()
        return RevealResult(me, index, tile.code, elim, over)
    }

    /** 순위 (players 순서, 1부터): 승자 1위, 탈락한 사람은 늦게 탈락할수록 높음 */
    fun ranks(): IntArray {
        val r = IntArray(playerCount) { 1 }
        for ((k, p) in eliminated.withIndex()) r[p] = playerCount - k
        return r
    }

    private fun insert(p: Int, code: Int, revealed: Boolean): Int {
        val row = rows[p]
        var i = 0
        while (i < row.size && row[i].code < code) i++
        row.add(i, Tile(code, revealed))
        return i
    }

    private fun eliminate(p: Int) {
        if (out[p]) return
        out[p] = true
        eliminated.add(p)
    }

    private fun endTurn() {
        var p = current
        do { p = (p + 1) % playerCount } while (out[p] && p != current)
        current = p
        beginTurn()
    }

    private fun beginTurn() {
        turn++
        correctThisTurn = 0
        drawn = if (pile.isEmpty()) -1 else pile.removeAt(pile.size - 1)
        phase = Phase.GUESS
    }

    companion object {
        /** 섞어서 나눠 주기 (시작하는 사람도 무작위) */
        fun create(players: Int, random: Random): CodeTileGame {
            val all = (0 until GameConfig.TILE_COUNT).shuffled(random)
            val n = GameConfig.startTiles(players)
            val hands = List(players) { p -> all.subList(p * n, p * n + n) }
            val pile = all.subList(players * n, all.size)
            return CodeTileGame(players, hands, pile, random.nextInt(players))
        }

        /**
         * 정해진 배치로 시작 (테스트용). pile 은 끝에서부터 뽑는다.
         * 시작하는 사람은 곧바로 pile 의 마지막 타일을 뽑는다.
         */
        fun withSetup(hands: List<List<Int>>, pile: List<Int>, starter: Int = 0): CodeTileGame =
            CodeTileGame(hands.size, hands, pile, starter)
    }
}
