package com.siwoo.party.games.janggi

/** 진행 단계 */
enum class Phase { PLAY, OVER }

/** 판이 끝난 이유 */
enum class EndReason {
    /** 장군을 피할 수 없음 (외통) */
    CHECKMATE,
    /** 장군은 아니지만 둘 수 있는 수가 하나도 없음 → 그 사람 패배 */
    NO_MOVES,
    /** 기권 */
    RESIGN,
    /** 빅장을 풀지 않음 → 무승부 (빅장 옵션 켬일 때만) */
    BIKJANG,
    /** 수 제한 → 점수 판정 */
    MOVE_LIMIT,
}

/** 둔 수 기록 (무르기용) */
class MoveRecord(
    val from: Int,
    val to: Int,
    val piece: Int,
    val captured: Int,
    /** 이 수를 두기 전 빅장이 불린 상태였는지 */
    val bikjangBefore: Boolean,
)

/**
 * 장기 규칙 (2인). 순수 Kotlin.
 *
 * 좌표: 세로줄 x 0~8 (왼쪽→오른쪽), 가로줄 y 0~9 (위→아래). 칸 번호 = y × 9 + x.
 * 초(파랑)는 아래(y 6~9), 한(빨강)은 위(y 0~3). 궁성은 x 3~5 · y 0~2(한) / y 7~9(초), 가운데 (4,1)·(4,8).
 * 초가 먼저 둔다.
 *
 * 이동 규칙
 * - 궁·사: 자기 궁성 안에서 선을 따라 한 칸 (가로·세로, 궁성 대각선 위라면 대각선도).
 * - 차: 가로·세로로 막히지 않은 만큼. 궁성 안에서는 대각선을 따라서도 (모서리→가운데→반대 모서리).
 * - 포: 기물 하나(포가 아닌 것)를 정확히 하나 넘어서 이동·공격. 포는 포를 넘을 수도, 잡을 수도 없다. 궁성 대각선도 가운데 기물을 넘어 가능.
 * - 마: 가로·세로 한 칸 + 바깥쪽 대각선 한 칸. 첫 칸(멱)이 막히면 못 감.
 * - 상: 가로·세로 한 칸 + 바깥쪽 대각선 두 칸. 지나는 두 점(멱) 중 하나라도 막히면 못 감.
 * - 졸·병: 앞 또는 옆으로 한 칸 (뒤로 못 감). 상대 궁성 안에서는 궁성 대각선을 따라 앞쪽 대각선 한 칸.
 * - 자기 궁이 공격받는(장군) 상태로 남는 수는 둘 수 없다. 궁끼리 마주 보는 것(빅장)은 금지수가 아니다.
 *
 * 승패
 * - 상대가 둘 수 있는 수가 없으면 이긴다: 장군 상태면 외통(CHECKMATE), 아니면 NO_MOVES (둘 다 상대 패배).
 * - 기권(RESIGN).
 * - 빅장 옵션 켬: 어떤 수로 궁 둘이 같은 세로줄에서 사이에 아무것도 없이 마주 보면 "빅장"이 불린다.
 *   상대가 바로 다음 수에 빅장을 풀지 않으면(풀 수 없어도) 무승부. 외통이 빅장보다 먼저.
 * - 수 제한(0 이 아니면): 두 사람이 합쳐 moveLimit 수를 두면 점수 판정 (차13 포7 마5 상3 사3 졸2, 한 덤 1.5).
 * - 무르기: 방금 둔 사람이 상대가 두기 전에 자기 수를 물릴 수 있다 (한 판에 UNDO_PER_PLAYER 번, undoEnabled 일 때).
 */
class JanggiGame(
    val choForm: Formation = Formation.MSMS,
    val hanForm: Formation = Formation.MSMS,
    val undoEnabled: Boolean = GameConfig.UNDO_DEFAULT,
    val bikjangRule: Boolean = GameConfig.BIKJANG_DEFAULT,
    val moveLimit: Int = GameConfig.MOVE_LIMIT_DEFAULT,
) {
    val board = IntArray(GameConfig.CELLS)

    var phase = Phase.PLAY
        private set
    /** 지금 둘 진영 */
    var turn = Side.CHO
        private set
    /** 이긴 진영 (−1 = 아직 없음 / 무승부) */
    var winner = -1
        private set
    var endReason: EndReason? = null
        private set
    val isDraw: Boolean get() = phase == Phase.OVER && winner < 0

    /** 지금까지 둔 수 (두 사람 합계) */
    val moveCount: Int get() = history.size
    private val history = ArrayList<MoveRecord>()
    val lastMove: MoveRecord? get() = history.lastOrNull()

    /** 빅장이 불려 있어서 지금 둘 사람이 풀어야 하는 상태 */
    var bikjangCalled = false
        private set

    /** 남은 무르기 횟수 (진영별) */
    val undosLeft = IntArray(2) { if (undoEnabled) GameConfig.UNDO_PER_PLAYER else 0 }

    /** 진영별로 잡은 상대 기물 (잡은 순서) */
    val captured = Array(2) { IntArray(16) }
    val capturedCount = IntArray(2)

    // 임시 배열 (할당 줄이기). 호출이 겹치는 함수끼리는 서로 다른 배열을 쓴다.
    private val scratchAttack = IntArray(MAX_TARGETS)
    private val scratchLegal = IntArray(MAX_TARGETS)
    private val scratchAny = IntArray(MAX_TARGETS)

    init {
        setupInitial()
    }

    // ── 좌표 ──

    fun index(x: Int, y: Int): Int = y * GameConfig.COLS + x
    fun inside(x: Int, y: Int): Boolean = x in 0 until GameConfig.COLS && y in 0 until GameConfig.ROWS
    fun at(x: Int, y: Int): Int = board[index(x, y)]

    // ── 처음 배치 · 사용자 배치 ──

    /** 정식 시작 배치 (양쪽 상차림 반영) */
    fun setupInitial() {
        board.fill(Piece.EMPTY)
        placeSide(Side.CHO, choForm)
        placeSide(Side.HAN, hanForm)
        reset(Side.CHO)
    }

    private fun placeSide(side: Int, form: Formation) {
        val back = if (side == Side.CHO) 9 else 0
        val fwd = if (side == Side.CHO) -1 else 1
        // 자기 쪽에서 본 왼쪽부터 → 실제 x (한은 판 건너편에서 보므로 좌우가 뒤집힘)
        fun fx(f: Int) = if (side == Side.CHO) f else 8 - f
        put(fx(0), back, side, Kind.CHARIOT)
        put(fx(8), back, side, Kind.CHARIOT)
        for (k in 0 until 4) put(fx(Formation.FILES[k]), back, side, form.kinds[k])
        put(3, back, side, Kind.GUARD)
        put(5, back, side, Kind.GUARD)
        put(4, back + fwd, side, Kind.GENERAL)
        put(1, back + 2 * fwd, side, Kind.CANNON)
        put(7, back + 2 * fwd, side, Kind.CANNON)
        for (x in 0..8 step 2) put(x, back + 3 * fwd, side, Kind.SOLDIER)
    }

    /** 판 비우기 (테스트·연습 배치용) */
    fun clearBoard() {
        board.fill(Piece.EMPTY)
    }

    /** 기물 하나 놓기 (테스트·연습 배치용) */
    fun put(x: Int, y: Int, side: Int, kind: Kind) {
        board[index(x, y)] = Piece.code(side, kind)
    }

    /** 칸 비우기 */
    fun remove(x: Int, y: Int) {
        board[index(x, y)] = Piece.EMPTY
    }

    /** 지금 판에서 새로 시작: 기록·잡은 기물·승패를 지우고 turn 차례로 (판은 그대로) */
    fun reset(turn: Int) {
        this.turn = turn
        phase = Phase.PLAY
        winner = -1
        endReason = null
        history.clear()
        bikjangCalled = false
        capturedCount.fill(0)
        for (s in 0..1) undosLeft[s] = if (undoEnabled) GameConfig.UNDO_PER_PLAYER else 0
    }

    // ── 궁성 ──

    /** (x, y) 가 어느 진영 궁성인지 (−1 = 궁성 밖) */
    fun palaceOf(x: Int, y: Int): Int = when {
        x !in 3..5 -> -1
        y in 0..2 -> Side.HAN
        y in 7..9 -> Side.CHO
        else -> -1
    }

    private fun isPalaceCenter(x: Int, y: Int): Boolean = x == 4 && (y == 1 || y == 8)

    /** (x, y) → (x+dx, y+dy) 대각선 한 칸이 궁성 대각선을 따라가는지 (같은 궁성 안, 한쪽이 가운데) */
    fun diagonalStep(x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val p = palaceOf(x, y)
        if (p < 0 || palaceOf(x + dx, y + dy) != p) return false
        return isPalaceCenter(x, y) || isPalaceCenter(x + dx, y + dy)
    }

    // ── 수 만들기 (자기 궁 안전은 따지지 않음) ──

    /** sq 에 있는 기물이 갈 수 있는 칸 (장군 무시). out 에 칸 번호를 쓰고 개수를 돌려준다 */
    fun pseudoTargets(sq: Int, out: IntArray): Int {
        val code = board[sq]
        if (code == Piece.EMPTY) return 0
        val side = Piece.side(code)
        val x = sq % GameConfig.COLS
        val y = sq / GameConfig.COLS
        var n = 0
        when (Piece.kind(code)) {
            Kind.GENERAL, Kind.GUARD -> {
                for (d in 0 until 4) {
                    val nx = x + ODX[d]
                    val ny = y + ODY[d]
                    if (palaceOf(nx, ny) == side) n = addIf(nx, ny, side, out, n)
                }
                for (d in 0 until 4) {
                    if (diagonalStep(x, y, DDX[d], DDY[d]) && palaceOf(x, y) == side) n = addIf(x + DDX[d], y + DDY[d], side, out, n)
                }
            }
            Kind.CHARIOT -> {
                for (d in 0 until 4) n = slide(x, y, ODX[d], ODY[d], false, side, out, n)
                for (d in 0 until 4) n = slide(x, y, DDX[d], DDY[d], true, side, out, n)
            }
            Kind.CANNON -> {
                for (d in 0 until 4) n = jump(x, y, ODX[d], ODY[d], false, side, out, n)
                for (d in 0 until 4) n = jump(x, y, DDX[d], DDY[d], true, side, out, n)
            }
            Kind.HORSE -> {
                for (d in 0 until 4) {
                    val dx = ODX[d]
                    val dy = ODY[d]
                    if (!emptyAt(x + dx, y + dy)) continue // 멱
                    for (s in -1..1 step 2) {
                        val px = if (dx == 0) s else 0
                        val py = if (dy == 0) s else 0
                        n = addIf(x + 2 * dx + px, y + 2 * dy + py, side, out, n)
                    }
                }
            }
            Kind.ELEPHANT -> {
                for (d in 0 until 4) {
                    val dx = ODX[d]
                    val dy = ODY[d]
                    if (!emptyAt(x + dx, y + dy)) continue // 첫 멱
                    for (s in -1..1 step 2) {
                        val px = if (dx == 0) s else 0
                        val py = if (dy == 0) s else 0
                        if (!emptyAt(x + 2 * dx + px, y + 2 * dy + py)) continue // 둘째 멱
                        n = addIf(x + 3 * dx + 2 * px, y + 3 * dy + 2 * py, side, out, n)
                    }
                }
            }
            Kind.SOLDIER -> {
                val fwd = if (side == Side.CHO) -1 else 1
                n = addIf(x, y + fwd, side, out, n)
                n = addIf(x - 1, y, side, out, n)
                n = addIf(x + 1, y, side, out, n)
                for (s in -1..1 step 2) if (diagonalStep(x, y, s, fwd)) n = addIf(x + s, y + fwd, side, out, n)
            }
        }
        return n
    }

    private fun emptyAt(x: Int, y: Int): Boolean = inside(x, y) && board[index(x, y)] == Piece.EMPTY

    /** 판 안이고 내 기물이 없으면 추가 */
    private fun addIf(x: Int, y: Int, side: Int, out: IntArray, n: Int): Int {
        if (!inside(x, y)) return n
        val i = index(x, y)
        val t = board[i]
        if (t != Piece.EMPTY && Piece.side(t) == side) return n
        out[n] = i
        return n + 1
    }

    /** 차: 한 방향으로 미끄러지기 (diagonal 이면 궁성 대각선만) */
    private fun slide(x: Int, y: Int, dx: Int, dy: Int, diagonal: Boolean, side: Int, out: IntArray, n0: Int): Int {
        var n = n0
        var cx = x
        var cy = y
        while (true) {
            if (diagonal && !diagonalStep(cx, cy, dx, dy)) break
            val nx = cx + dx
            val ny = cy + dy
            if (!inside(nx, ny)) break
            val i = index(nx, ny)
            val t = board[i]
            if (t == Piece.EMPTY) {
                out[n++] = i
            } else {
                if (Piece.side(t) != side) out[n++] = i
                break
            }
            cx = nx
            cy = ny
        }
        return n
    }

    /** 포: 포가 아닌 기물 하나를 넘은 뒤의 빈 칸들 + 처음 만나는 상대 기물(포 제외) */
    private fun jump(x: Int, y: Int, dx: Int, dy: Int, diagonal: Boolean, side: Int, out: IntArray, n0: Int): Int {
        var n = n0
        var cx = x
        var cy = y
        var screened = false
        while (true) {
            if (diagonal && !diagonalStep(cx, cy, dx, dy)) break
            val nx = cx + dx
            val ny = cy + dy
            if (!inside(nx, ny)) break
            val i = index(nx, ny)
            val t = board[i]
            if (!screened) {
                if (t != Piece.EMPTY) {
                    if (Piece.isKind(t, Kind.CANNON)) break // 포는 포를 못 넘음
                    screened = true
                }
            } else {
                if (t == Piece.EMPTY) {
                    out[n++] = i
                } else {
                    if (Piece.side(t) != side && !Piece.isKind(t, Kind.CANNON)) out[n++] = i // 포는 포를 못 잡음
                    break
                }
            }
            cx = nx
            cy = ny
        }
        return n
    }

    // ── 장군 ──

    /** side 의 궁 자리 (없으면 −1) */
    fun generalSquare(side: Int): Int {
        val g = Piece.code(side, Kind.GENERAL)
        for (i in board.indices) if (board[i] == g) return i
        return -1
    }

    /** bySide 의 기물 중 하나라도 sq 로 갈 수 있는지 */
    fun isAttacked(sq: Int, bySide: Int): Boolean {
        for (i in board.indices) {
            val c = board[i]
            if (c == Piece.EMPTY || Piece.side(c) != bySide) continue
            val n = pseudoTargets(i, scratchAttack)
            for (k in 0 until n) if (scratchAttack[k] == sq) return true
        }
        return false
    }

    /** side 의 궁이 장군 받는 중인지 */
    fun inCheck(side: Int): Boolean {
        val g = generalSquare(side)
        return g >= 0 && isAttacked(g, Side.other(side))
    }

    /** from → to 를 두어도 자기 궁이 안전한지 (잠깐 두어 보고 되돌림) */
    private fun safeAfter(from: Int, to: Int): Boolean {
        val piece = board[from]
        val cap = board[to]
        board[to] = piece
        board[from] = Piece.EMPTY
        val ok = !inCheck(Piece.side(piece))
        board[from] = piece
        board[to] = cap
        return ok
    }

    /** sq 기물이 실제로 둘 수 있는 칸 (자기 궁을 장군 상태로 남기는 수 제외) */
    fun legalTargets(sq: Int, out: IntArray): Int {
        val n = pseudoTargets(sq, scratchLegal)
        var k = 0
        for (j in 0 until n) {
            val to = scratchLegal[j]
            if (safeAfter(sq, to)) out[k++] = to
        }
        return k
    }

    fun isLegal(from: Int, to: Int): Boolean {
        if (from !in board.indices || to !in board.indices) return false
        val c = board[from]
        if (c == Piece.EMPTY) return false
        val n = pseudoTargets(from, scratchLegal)
        for (j in 0 until n) if (scratchLegal[j] == to) return safeAfter(from, to)
        return false
    }

    /** side 가 둘 수 있는 수가 하나라도 있는지 */
    fun hasLegalMove(side: Int): Boolean {
        for (i in board.indices) {
            val c = board[i]
            if (c == Piece.EMPTY || Piece.side(c) != side) continue
            val n = pseudoTargets(i, scratchAny)
            for (j in 0 until n) if (safeAfter(i, scratchAny[j])) return true
        }
        return false
    }

    /** side 가 외통인지 (장군 + 둘 수 있는 수 없음) */
    fun isCheckmate(side: Int): Boolean = inCheck(side) && !hasLegalMove(side)

    /** 두 궁이 같은 세로줄에서 사이에 아무것도 없이 마주 보는지 */
    fun isBikjang(): Boolean {
        val a = generalSquare(Side.CHO)
        val b = generalSquare(Side.HAN)
        if (a < 0 || b < 0) return false
        val x = a % GameConfig.COLS
        if (b % GameConfig.COLS != x) return false
        val y0 = minOf(a, b) / GameConfig.COLS
        val y1 = maxOf(a, b) / GameConfig.COLS
        for (y in y0 + 1 until y1) if (board[index(x, y)] != Piece.EMPTY) return false
        return true
    }

    // ── 두기 ──

    /**
     * 지금 차례인 진영이 from → to 로 둔다. 규칙에 맞으면 true.
     * 둔 뒤 승패(외통·둘 수 없음·빅장·수 제한)를 판정하고 차례를 넘긴다.
     */
    fun move(from: Int, to: Int): Boolean {
        if (phase != Phase.PLAY) return false
        if (from !in board.indices || board[from] == Piece.EMPTY || Piece.side(board[from]) != turn) return false
        if (!isLegal(from, to)) return false
        val mover = turn
        val piece = board[from]
        val cap = board[to]
        history.add(MoveRecord(from, to, piece, cap, bikjangCalled))
        board[to] = piece
        board[from] = Piece.EMPTY
        if (cap != Piece.EMPTY) captured[mover][capturedCount[mover]++] = cap
        val opp = Side.other(mover)
        turn = opp
        if (!hasLegalMove(opp)) {
            end(mover, if (inCheck(opp)) EndReason.CHECKMATE else EndReason.NO_MOVES)
            return true
        }
        if (bikjangRule) {
            val bj = isBikjang()
            if (bj && bikjangCalled) {
                bikjangCalled = false
                end(-1, EndReason.BIKJANG)
                return true
            }
            bikjangCalled = bj
        }
        if (moveLimit > 0 && history.size >= moveLimit) {
            end(if (score(Side.CHO) > score(Side.HAN)) Side.CHO else Side.HAN, EndReason.MOVE_LIMIT)
        }
        return true
    }

    private fun end(winnerSide: Int, reason: EndReason) {
        winner = winnerSide
        endReason = reason
        phase = Phase.OVER
    }

    /** 기권: side 가 진다 */
    fun resign(side: Int): Boolean {
        if (phase != Phase.PLAY) return false
        end(Side.other(side), EndReason.RESIGN)
        return true
    }

    /** 방금 둔 진영이 side 이고, 상대가 아직 안 뒀고, 무르기가 남았는지 */
    fun canUndo(side: Int): Boolean {
        val last = history.lastOrNull() ?: return false
        return phase == Phase.PLAY && Piece.side(last.piece) == side && turn != side && undosLeft[side] > 0
    }

    /** 무르기: side 의 마지막 수를 되돌리고 다시 side 차례 */
    fun undo(side: Int): Boolean {
        if (!canUndo(side)) return false
        val m = history.removeAt(history.size - 1)
        board[m.from] = m.piece
        board[m.to] = m.captured
        if (m.captured != Piece.EMPTY) capturedCount[side]--
        bikjangCalled = m.bikjangBefore
        undosLeft[side]--
        turn = side
        return true
    }

    /** 점수 판정용 점수 (0.1점 단위): 남은 기물 점수 합 (+ 한 덤) */
    fun score(side: Int): Int {
        var s = if (side == Side.HAN) GameConfig.HAN_BONUS else 0
        for (c in board) if (c != Piece.EMPTY && Piece.side(c) == side) s += Piece.kind(c).score
        return s
    }

    /**
     * 순위 (players 순서): choPlayer 번 사람이 초, 다른 사람이 한.
     * 이긴 사람 1위·진 사람 2위, 무승부는 둘 다 1위.
     */
    fun ranks(choPlayer: Int): IntArray {
        if (winner < 0) return IntArray(GameConfig.PLAYERS) { 1 }
        val winPlayer = if (winner == Side.CHO) choPlayer else 1 - choPlayer
        return IntArray(GameConfig.PLAYERS) { if (it == winPlayer) 1 else 2 }
    }

    companion object {
        /** 한 기물이 한 번에 갈 수 있는 칸 수의 넉넉한 상한 (차: 17 + 궁성 대각 2) */
        const val MAX_TARGETS = 40
        /** 가로·세로 방향 */
        private val ODX = intArrayOf(1, -1, 0, 0)
        private val ODY = intArrayOf(0, 0, 1, -1)
        /** 대각선 방향 */
        private val DDX = intArrayOf(1, 1, -1, -1)
        private val DDY = intArrayOf(1, -1, 1, -1)
    }
}
