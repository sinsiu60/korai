package com.siwoo.party.games.bingo

import kotlin.random.Random

/**
 * 빙고 줄 12개. 칸 번호 = 행 × 5 + 열 (0..24, 왼쪽 위부터).
 * 0..4 = 가로(위→아래), 5..9 = 세로(왼→오), 10 = 대각선 ↘, 11 = 대각선 ↙
 */
object BingoLines {
    private const val N = GameConfig.SIZE

    val LINES: List<IntArray> = buildList {
        for (r in 0 until N) add(IntArray(N) { c -> r * N + c })
        for (c in 0 until N) add(IntArray(N) { r -> r * N + c })
        add(IntArray(N) { k -> k * N + k })
        add(IntArray(N) { k -> k * N + (N - 1 - k) })
    }

    /** line 번 줄이 모두 표시됐는지 */
    fun isDone(marked: BooleanArray, line: Int): Boolean {
        for (cell in LINES[line]) if (!marked[cell]) return false
        return true
    }

    /** 완성된 줄 수 */
    fun count(marked: BooleanArray): Int {
        var n = 0
        for (k in LINES.indices) if (isDone(marked, k)) n++
        return n
    }
}

/**
 * 한 사람의 5×5 빙고판.
 * - 채우기: place(칸) 은 다음 숫자(1, 2, 3 …)를 빈칸에 넣는다. undoLast 는 마지막 숫자를 뺀다. fillRandom 은 빈칸을 남은 숫자로 무작위로 채운다.
 * - 부르기: mark(숫자) 는 그 숫자가 있는 칸에 표시한다. lines = 완성된 줄 수.
 */
class BingoBoard {
    private val cells = IntArray(GameConfig.CELLS)
    /** 숫자 → 칸 (없으면 -1) */
    private val where = IntArray(GameConfig.CELLS + 1) { -1 }
    /** 칸마다 표시(부른 숫자) 여부 */
    val marked = BooleanArray(GameConfig.CELLS)

    /** 지금까지 넣은 숫자 개수 (1..placedCount 가 들어 있음) */
    var placedCount = 0
        private set

    /** 다음에 넣을 숫자 (다 찼으면 CELLS + 1) */
    val nextNumber: Int get() = placedCount + 1

    val isComplete: Boolean get() = placedCount == GameConfig.CELLS

    val isEmpty: Boolean get() = placedCount == 0

    /** 칸의 숫자 (빈칸 0) */
    fun numberAt(cell: Int): Int = cells[cell]

    /** 숫자가 있는 칸 (없으면 -1) */
    fun cellOf(number: Int): Int = if (number in 1..GameConfig.CELLS) where[number] else -1

    /** 빈칸에 다음 숫자 넣기 (빈칸이 아니거나 다 찼으면 false) */
    fun place(cell: Int): Boolean {
        if (cell !in 0 until GameConfig.CELLS || cells[cell] != 0 || isComplete) return false
        val n = nextNumber
        cells[cell] = n
        where[n] = cell
        placedCount++
        return true
    }

    /** 마지막에 넣은 숫자 빼기 (뺀 칸, 없으면 -1) */
    fun undoLast(): Int {
        if (placedCount == 0) return -1
        val cell = where[placedCount]
        cells[cell] = 0
        where[placedCount] = -1
        placedCount--
        return cell
    }

    /** 모두 비우기 (표시도 지움) */
    fun clear() {
        cells.fill(0)
        where.fill(-1)
        marked.fill(false)
        placedCount = 0
    }

    /**
     * 무작위 채우기: 비어 있거나 다 찬 판은 1..25 전체를 새로 섞고,
     * 일부만 채운 판은 이미 넣은 숫자는 두고 빈칸만 남은 숫자로 섞어 채운다.
     */
    fun fillRandom(random: Random) {
        if (isComplete) clear()
        val empty = ArrayList<Int>()
        for (c in cells.indices) if (cells[c] == 0) empty.add(c)
        empty.shuffle(random)
        for (c in empty) place(c)
    }

    /** 칸 순서대로 숫자를 한꺼번에 넣기 (1..25 의 순열이어야 함 — 테스트·저장용) */
    fun setNumbers(numbers: IntArray) {
        require(numbers.size == GameConfig.CELLS && numbers.sorted() == (1..GameConfig.CELLS).toList()) { "1..${GameConfig.CELLS} 순열이 아님" }
        clear()
        for (c in numbers.indices) {
            cells[c] = numbers[c]
            where[numbers[c]] = c
        }
        placedCount = GameConfig.CELLS
    }

    /** 칸 순서대로 숫자 복사본 */
    fun numbers(): IntArray = cells.copyOf()

    /** 부른 숫자 표시 (판에 있고 처음 표시하면 true) */
    fun mark(number: Int): Boolean {
        val c = cellOf(number)
        if (c < 0 || marked[c]) return false
        marked[c] = true
        return true
    }

    fun isMarked(cell: Int): Boolean = marked[cell]

    /** 완성된 줄 수 */
    val lines: Int get() = BingoLines.count(marked)

    /** line 번 줄 완성 여부 (BingoLines 순서) */
    fun lineDone(line: Int): Boolean = BingoLines.isDone(marked, line)
}
