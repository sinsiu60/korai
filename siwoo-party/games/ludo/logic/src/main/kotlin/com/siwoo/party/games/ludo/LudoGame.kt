package com.siwoo.party.games.ludo

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 지금 할 일: 굴리기 / 말 고르기 / 끝 */
enum class Phase { ROLL, MOVE, OVER }

/**
 * 이동 하나 (지금 차례 사람의 말 piece 를 지금 눈만큼).
 * @param from 출발 진행 (-1 = 대기)
 * @param to 도착 진행 (0 = 출발 칸, 56 = 집)
 * @param path 한 걸음씩 지나가는 진행 값 (마지막이 도착). 출발이면 [0]
 * @param captures 도착 칸에서 잡히는 상대 말 (player * 4 + piece)
 */
class Move(
    val player: Int,
    val piece: Int,
    val from: Int,
    val to: Int,
    val path: IntArray,
    val captures: IntArray,
) {
    val release: Boolean get() = from < 0
    val home: Boolean get() = to == GameConfig.HOME
    val capture: Boolean get() = captures.isNotEmpty()
}

/**
 * 굴리기 결과.
 * @param thirdSix 3연속 6 → 이 6은 무효, 차례 끝
 * @param moves 지금 고를 수 있는 이동 (비었으면 움직일 말 없음)
 * @param again 움직일 말 없이 6이 나와 바로 한 번 더 굴림
 * @param turnEnded 차례가 넘어감 (next = 다음 사람)
 */
class RollOutcome(
    val player: Int,
    val value: Int,
    val sixesInRow: Int,
    val thirdSix: Boolean,
    val moves: List<Move>,
    val again: Boolean,
    val turnEnded: Boolean,
    val next: Int,
)

/**
 * 이동 결과.
 * @param extraRoll 같은 사람이 한 번 더 굴림 (6 또는 잡기)
 * @param playerFinished 이 이동으로 말을 모두 집에 넣음 → rank 확정
 */
class MoveOutcome(
    val move: Move,
    val extraRoll: Boolean,
    val bySix: Boolean,
    val byCapture: Boolean,
    val playerFinished: Boolean,
    val rank: Int,
    val turnEnded: Boolean,
    val next: Int,
    val gameOver: Boolean,
) {
    val player: Int get() = move.player
}

/**
 * 말판 레이스 규칙 (2~4인, 차례대로). 판 모양은 LudoBoard, 규칙 선택은 GameConfig 설명 참고.
 *
 * 말 위치는 "진행" 값: −1 대기, 0 = 자기 출발 칸, 50 = 바깥 길 마지막, 51~55 = 집 통로, 56 = 집.
 *
 * 차례 흐름
 * 1. ROLL: 굴림 → 6이면 연속 수+1 (3이면 무효·차례 끝), 아니면 연속 수 0
 * 2. 이동 가능한 말이 없으면 6이면 다시 ROLL, 아니면 차례 끝. 있으면 MOVE
 * 3. MOVE: 말 하나를 움직임 → 잡기 → 모두 집이면 순위 (한 명 남으면 끝)
 *    → 6이었거나 잡았으면 같은 사람 ROLL, 아니면 다음 사람
 * 시간·난수는 밖에서 주입한다 (Android 의존성 없음).
 */
class LudoGame(
    val playerCount: Int,
    private val random: Random = Random.Default,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val pieces: Int = GameConfig.PIECES

    /** 플레이어 p 가 앉는 판 자리 (2인은 마주 보는 0·2) */
    val seats: IntArray = when (playerCount) {
        2 -> intArrayOf(0, 2)
        3 -> intArrayOf(0, 1, 2)
        else -> intArrayOf(0, 1, 2, 3)
    }

    private val pos = Array(playerCount) { IntArray(pieces) { -1 } }

    /** 순위 (0 = 아직) — 말을 모두 집에 넣은 순서 */
    val finishRank = IntArray(playerCount)
    var finishedCount = 0
        private set

    var current = 0
        private set
    var phase = Phase.ROLL
        private set
    /** 마지막으로 굴린 눈 (0 = 아직) */
    var dice = 0
        private set
    /** 이 차례에 연속으로 나온 6 수 */
    var sixesInRow = 0
        private set
    private var moves: List<Move> = NO_MOVES
    /** 지금 고를 수 있는 이동 (MOVE 일 때만) */
    val legalMoves: List<Move> get() = moves

    /** 끝났을 때 최종 순위 (players 순서, 1부터) */
    var ranks: IntArray? = null
        private set
    /** [여기서 종료]로 끝남 */
    var stoppedEarly = false
        private set

    // ── 조회 ──

    fun seatOf(p: Int): Int = seats[p]

    /** 말의 진행 (−1 대기, 56 집) */
    fun pos(p: Int, k: Int): Int = pos[p][k]

    fun homeCount(p: Int): Int {
        var n = 0
        for (k in 0 until pieces) if (pos[p][k] == GameConfig.HOME) n++
        return n
    }

    fun yardCount(p: Int): Int {
        var n = 0
        for (k in 0 until pieces) if (pos[p][k] < 0) n++
        return n
    }

    /** 진행 합 (대기 0, 출발 칸 1 … 집 57) — 중간 종료 순위의 둘째 기준 */
    fun progressSum(p: Int): Int {
        var s = 0
        for (k in 0 until pieces) s += pos[p][k] + 1
        return s
    }

    /** 바깥 길 칸 n 에 서 있는 말들 (player * 4 + piece) */
    fun piecesOnTrack(n: Int): IntArray {
        var c = 0
        for (p in 0 until playerCount) for (k in 0 until pieces) if (LudoBoard.trackIndex(seats[p], pos[p][k]) == n) c++
        if (c == 0) return NONE
        val out = IntArray(c)
        var j = 0
        for (p in 0 until playerCount) for (k in 0 until pieces) if (LudoBoard.trackIndex(seats[p], pos[p][k]) == n) out[j++] = p * GameConfig.PIECES + k
        return out
    }

    // ── 이동 만들기 ──

    /** 플레이어 p 의 말 k 를 눈 v 로 움직이는 이동 (못 움직이면 null) */
    fun moveFor(p: Int, k: Int, v: Int): Move? {
        val from = pos[p][k]
        if (from == GameConfig.HOME || v !in 1..6) return null
        val to: Int
        val path: IntArray
        if (from < 0) {
            if (v != GameConfig.RELEASE_ROLL) return null
            to = 0
            path = RELEASE_PATH
        } else {
            to = from + v
            if (to > GameConfig.HOME) return null // 집은 정확한 눈으로만
            path = IntArray(v) { from + 1 + it }
        }
        return Move(p, k, from, to, path, capturesAt(p, to))
    }

    /** p 의 말이 진행 to 에 멈출 때 잡히는 상대 말 */
    private fun capturesAt(p: Int, to: Int): IntArray {
        val n = LudoBoard.trackIndex(seats[p], to)
        if (n < 0 || LudoBoard.SAFE[n]) return NONE
        var c = 0
        for (o in 0 until playerCount) if (o != p) for (j in 0 until pieces) if (LudoBoard.trackIndex(seats[o], pos[o][j]) == n) c++
        if (c == 0) return NONE
        val out = IntArray(c)
        var i = 0
        for (o in 0 until playerCount) if (o != p) for (j in 0 until pieces) if (LudoBoard.trackIndex(seats[o], pos[o][j]) == n) out[i++] = o * GameConfig.PIECES + j
        return out
    }

    /** 플레이어 p 가 눈 v 로 할 수 있는 모든 이동 (말 번호 순) */
    fun movesFor(p: Int, v: Int): List<Move> {
        val out = ArrayList<Move>(pieces)
        for (k in 0 until pieces) moveFor(p, k, v)?.let { out.add(it) }
        return out
    }

    /** 지금 고를 수 있는 이동 중 말 k 의 것 (없으면 null) */
    fun legalMove(k: Int): Move? {
        if (phase != Phase.MOVE) return null
        for (m in moves) if (m.piece == k) return m
        return null
    }

    /** 고를 수 있는 이동이 모두 같은 결과인지 (하나뿐이거나, 대기 말 여러 개 출발처럼 출발·도착이 같음) */
    val onlyOneChoice: Boolean
        get() {
            if (moves.isEmpty()) return false
            val a = moves[0]
            for (m in moves) if (m.from != a.from || m.to != a.to) return false
            return true
        }

    // ── 굴리기 ──

    /** 주입한 난수로 굴린다 */
    fun roll(): RollOutcome = rollWith(1 + random.nextInt(6))

    /** 정해진 눈으로 굴린다 (테스트·연출용) */
    fun rollWith(v: Int): RollOutcome {
        check(phase == Phase.ROLL) { "지금은 굴릴 차례가 아님" }
        require(v in 1..6)
        val p = current
        dice = v
        if (v == GameConfig.RELEASE_ROLL) sixesInRow++ else sixesInRow = 0
        val sixes = sixesInRow
        if (sixesInRow >= GameConfig.MAX_SIXES) {
            // 3연속 6: 이 6은 무효, 차례 끝
            endTurn()
            return RollOutcome(p, v, sixes, true, NO_MOVES, false, true, current)
        }
        val list = movesFor(p, v)
        if (list.isEmpty()) {
            if (v == GameConfig.RELEASE_ROLL) {
                // 움직일 말은 없지만 6이라 한 번 더
                return RollOutcome(p, v, sixes, false, NO_MOVES, true, false, current)
            }
            endTurn()
            return RollOutcome(p, v, sixes, false, NO_MOVES, false, true, current)
        }
        moves = list
        phase = Phase.MOVE
        return RollOutcome(p, v, sixes, false, list, false, false, current)
    }

    // ── 이동 ──

    /** 말 k 를 지금 눈만큼 움직인다 (고를 수 없는 말이면 null) */
    fun move(k: Int): MoveOutcome? {
        val m = legalMove(k) ?: return null
        val p = m.player
        pos[p][k] = m.to
        for (c in m.captures) pos[c / GameConfig.PIECES][c % GameConfig.PIECES] = -1
        moves = NO_MOVES
        val bySix = dice == GameConfig.RELEASE_ROLL
        val byCapture = GameConfig.CAPTURE_BONUS && m.capture
        if (homeCount(p) == pieces) {
            // 모두 집: 순위 확정, 남은 보너스 굴림은 버림
            finishedCount++
            finishRank[p] = finishedCount
            if (finishedCount >= playerCount - 1) {
                for (o in 0 until playerCount) if (finishRank[o] == 0) finishRank[o] = playerCount
                phase = Phase.OVER
                ranks = finishRank.copyOf()
            } else {
                endTurn()
            }
            return MoveOutcome(m, false, bySix, byCapture, true, finishRank[p], phase != Phase.OVER, current, phase == Phase.OVER)
        }
        if (bySix || byCapture) {
            phase = Phase.ROLL
            return MoveOutcome(m, true, bySix, byCapture, false, 0, false, current, false)
        }
        endTurn()
        return MoveOutcome(m, false, false, false, false, 0, true, current, false)
    }

    private fun endTurn() {
        moves = NO_MOVES
        sixesInRow = 0
        if (finishedCount >= playerCount) return
        var n = current
        do { n = (n + 1) % playerCount } while (finishRank[n] != 0)
        current = n
        phase = Phase.ROLL
    }

    // ── 끝 ──

    /**
     * [여기서 종료]: 지금 순위로 끝낸다.
     * 다 들어온 사람은 그 순위, 나머지는 집에 넣은 수 → 진행 합 순 (같으면 공동 순위).
     */
    fun stopHere(): IntArray {
        val out = finishRank.copyOf()
        val rest = ArrayList<Int>()
        for (p in 0 until playerCount) if (out[p] == 0) rest.add(p)
        if (rest.isNotEmpty()) {
            val keys = rest.map { homeCount(it).toLong() * 1000L + progressSum(it) }
            val r = Ranking.competition(keys)
            for ((j, p) in rest.withIndex()) out[p] = finishedCount + r[j]
        }
        phase = Phase.OVER
        stoppedEarly = true
        moves = NO_MOVES
        ranks = out
        return out.copyOf()
    }

    // ── 테스트·연출 확인용 ──

    /** 말 k 를 진행 position 에 놓는다 (−1 대기, 56 집) */
    fun setupPiece(p: Int, k: Int, position: Int) {
        require(position in -1..GameConfig.HOME)
        pos[p][k] = position
    }

    /** p 차례로 바꾸고 굴릴 차례로 (연속 6 수 지정 가능) */
    fun setupTurn(p: Int, sixes: Int = 0) {
        require(finishRank[p] == 0)
        current = p
        sixesInRow = sixes
        moves = NO_MOVES
        phase = Phase.ROLL
    }

    companion object {
        private val NONE = IntArray(0)
        private val NO_MOVES: List<Move> = emptyList()
        private val RELEASE_PATH = intArrayOf(0)
    }
}
