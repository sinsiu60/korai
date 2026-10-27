package com.siwoo.party.games.yut

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 지금 할 일: 던지기 / 대기열 결과로 말 옮기기 / 끝 */
enum class Phase { THROW, MOVE, OVER }

/** 말 상태: 판 밖 대기 / 판 위 / 골인 */
enum class PieceState { WAITING, BOARD, DONE }

/** 게임 설정 (말 수·낙·빽도 대기말 올리기) */
data class YutRules(
    val pieces: Int = GameConfig.YUT_PIECES,
    val nak: Boolean = GameConfig.NAK_DEFAULT,
    val backdoEnter: Boolean = GameConfig.BACKDO_ENTER_DEFAULT,
) {
    init { require(pieces in GameConfig.PIECES_MIN..GameConfig.PIECES_MAX) }
}

/**
 * 이동 계획 (미리보기·이동 공통).
 * @param pieces 함께 움직이는 내 말들 (업힌 묶음 전체, 대기 말이면 그 말 하나)
 * @param from 출발 칸 (-1 = 대기에서 올라옴)
 * @param path 지나가는 칸 차례 (마지막이 도착 칸, 골인이면 마지막이 참먹이 0)
 * @param dest 도착 칸 (-1 = 골인)
 */
class MovePlan(
    val player: Int,
    val result: YutResult,
    val pieces: IntArray,
    val from: Int,
    val path: IntArray,
    val dest: Int,
    val goal: Boolean,
    internal val route: Int,
    internal val index: Int,
)

/**
 * 던지기 결과.
 * @param result null = 낙 (무효)
 * @param again 윷·모라서 한 번 더 던짐
 * @param discarded 던지기가 끝났는데 쓸 수 있는 결과가 없어 자동으로 버린 결과
 * @param turnEnded 차례가 넘어감 (next = 다음 사람)
 */
class ThrowOutcome(
    val player: Int,
    val sticks: BooleanArray,
    val result: YutResult?,
    val nak: Boolean,
    val again: Boolean,
    val discarded: List<YutResult>,
    val turnEnded: Boolean,
    val next: Int,
)

/**
 * 이동 결과.
 * @param stackCount 도착 칸의 내 말 수 (업기 포함, 골인이면 0)
 * @param joined 내 말 위에 도착해 업음
 * @param capturedPlayer 잡힌 상대 (-1 = 없음), captured = 잡혀서 대기로 돌아간 그 사람 말들
 * @param extraThrow 잡아서 한 번 더 던짐
 * @param playerFinished 이 이동으로 말을 모두 골인 → rank 확정
 */
class MoveOutcome(
    val plan: MovePlan,
    val stackCount: Int,
    val joined: Boolean,
    val capturedPlayer: Int,
    val captured: IntArray,
    val extraThrow: Boolean,
    val playerFinished: Boolean,
    val rank: Int,
    val discarded: List<YutResult>,
    val turnEnded: Boolean,
    val next: Int,
    val gameOver: Boolean,
) {
    val player: Int get() = plan.player
    val goal: Boolean get() = plan.goal
}

/**
 * 윷놀이 규칙 (2~4인, 차례대로). 판·길은 YutBoard, 던지기는 YutThrow, 규칙 선택은 GameConfig 설명 참고.
 *
 * 차례 흐름 (상태 기계)
 * 1. 차례 시작: phase = THROW, 남은 던지기(throwsLeft) 1, 대기열 비움
 * 2. 던지기: throwsLeft−1. 낙이 아니면 결과를 대기열에 넣고, 윷·모면 throwsLeft+1 (이동 전에 한 번 더)
 * 3. 던지기가 남았으면 THROW 그대로. 다 던졌으면
 *    - 대기열이 비었으면(낙) 차례 끝
 *    - 대기열에 쓸 수 있는 결과가 하나도 없으면 모두 버리고 차례 끝 (discarded)
 *    - 아니면 phase = MOVE
 * 4. 이동: 대기열 결과 하나 + 내 말(판 위 묶음 또는 대기 말) 하나. 결과를 대기열에서 빼고
 *    - 상대 말 위에 도착 → 그 묶음 전부 대기로 + throwsLeft+1 (이동이 끝난 뒤 한 번 더 던짐, 남은 결과는 그대로 둠)
 *    - 내 말 위에 도착 → 업기 (묶음으로 같이 이동)
 *    - 말을 모두 골인 → 순위 확정, 남은 결과·던지기는 버리고 차례 끝 (한 명만 남으면 그 사람 꼴찌, 끝)
 *    그다음 3번과 같은 정리 (던지기가 남았으면 THROW, 쓸 결과가 없으면 버리고 차례 끝, 아니면 MOVE)
 *
 * 순위: 말을 모두 골인시킨 순서. 중간 종료(stopHere)면 남은 사람은 골인 수 → 판 위 말 진행 거리 합 순 (같으면 공동).
 * 시간·난수는 밖에서 주입한다 (Android 의존성 없음).
 */
class YutGame(
    val playerCount: Int,
    val rules: YutRules = YutRules(),
    private val random: Random = Random.Default,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    /** 1인당 말 수 */
    val pieces: Int = rules.pieces

    private val state = Array(playerCount) { Array(pieces) { PieceState.WAITING } }
    private val route = Array(playerCount) { IntArray(pieces) }
    private val index = Array(playerCount) { IntArray(pieces) }

    /** 순위 (0 = 아직) — 말을 모두 골인한 순서 */
    val finishRank = IntArray(playerCount)
    var finishedCount = 0
        private set

    var current = 0
        private set
    var phase = Phase.THROW
        private set
    /** 이 차례에 남은 던지기 수 */
    var throwsLeft = 1
        private set
    private val q = ArrayList<YutResult>()
    /** 이동 대기열 (던진 순서) */
    val queue: List<YutResult> get() = q

    /** 끝났을 때 최종 순위 (players 순서, 1부터) */
    var ranks: IntArray? = null
        private set
    /** [여기서 종료]로 끝남 */
    var stoppedEarly = false
        private set

    // ── 말 조회 ──

    fun state(p: Int, k: Int): PieceState = state[p][k]

    /** 말이 서 있는 칸 (-1 = 판 위가 아님) */
    fun node(p: Int, k: Int): Int = if (state[p][k] == PieceState.BOARD) YutBoard.nodeAt(route[p][k], index[p][k]) else -1

    fun waitingCount(p: Int): Int = count(p, PieceState.WAITING)
    fun goalCount(p: Int): Int = count(p, PieceState.DONE)
    fun boardCount(p: Int): Int = count(p, PieceState.BOARD)

    private fun count(p: Int, s: PieceState): Int {
        var n = 0
        for (k in 0 until pieces) if (state[p][k] == s) n++
        return n
    }

    /** 칸 node 에 말이 있는 사람 (-1 = 빈 칸) */
    fun ownerAt(node: Int): Int {
        for (p in 0 until playerCount) for (k in 0 until pieces) if (node(p, k) == node) return p
        return -1
    }

    /** p 의 말 중 node 에 있는 말들 */
    fun piecesAt(p: Int, node: Int): IntArray {
        if (node < 0) return NONE
        var n = 0
        for (k in 0 until pieces) if (node(p, k) == node) n++
        if (n == 0) return NONE
        val out = IntArray(n)
        var j = 0
        for (k in 0 until pieces) if (node(p, k) == node) out[j++] = k
        return out
    }

    /** 말 k 와 같은 칸에 업혀 있는 내 말 수 (자기 포함, 판 위가 아니면 0) */
    fun stackSize(p: Int, k: Int): Int {
        val n = node(p, k)
        if (n < 0) return 0
        var c = 0
        for (j in 0 until pieces) if (node(p, j) == n) c++
        return c
    }

    /** 묶음 대표 말 (같은 칸에서 번호가 가장 작은 말) 인지 */
    fun isStackLead(p: Int, k: Int): Boolean {
        val n = node(p, k)
        if (n < 0) return false
        for (j in 0 until k) if (node(p, j) == n) return false
        return true
    }

    /**
     * 진행 거리: 바깥 한 바퀴(20) − 골인까지 남은 걸음 (지름길 반영, 남은 걸음은 최소 1).
     * 대기 0, 골인 20. 같은 칸이라도 지름길에 들어선 말은 더 앞선 것으로 친다.
     */
    fun progress(p: Int, k: Int): Int = when (state[p][k]) {
        PieceState.WAITING -> 0
        PieceState.DONE -> YutBoard.OUTER_STEPS
        PieceState.BOARD -> {
            val n = YutBoard.normalize(route[p][k], index[p][k])
            val rt = n / 100
            val i = n % 100
            YutBoard.OUTER_STEPS - maxOf(1, YutBoard.goalIndex(rt) - i)
        }
    }

    /** 판 위 말들의 진행 거리 합 (중간 종료 순위의 둘째 기준) */
    fun progressSum(p: Int): Int {
        var s = 0
        for (k in 0 until pieces) if (state[p][k] == PieceState.BOARD) s += progress(p, k)
        return s
    }

    // ── 결과 쓰기 가능 여부 ──

    /** 지금 차례인 사람이 결과 r 을 쓸 수 있는지 */
    fun isUsable(r: YutResult): Boolean {
        val p = current
        if (r.steps > 0) return goalCount(p) < pieces
        return boardCount(p) > 0 || (rules.backdoEnter && waitingCount(p) > 0)
    }

    /** 대기열 qi 번째 결과로 말 k 를 움직일 수 있는지 */
    fun canMove(qi: Int, k: Int): Boolean = phase == Phase.MOVE && plan(qi, k) != null

    /** 대기열 qi 번째 결과로 말 k(대기 말이면 새로 올림)를 움직이는 계획 (못 움직이면 null) */
    fun plan(qi: Int, k: Int): MovePlan? {
        if (phase != Phase.MOVE || qi !in q.indices || k !in 0 until pieces) return null
        val p = current
        val r = q[qi]
        val s = state[p][k]
        if (s == PieceState.DONE) return null
        val moving = if (s == PieceState.WAITING) intArrayOf(k) else piecesAt(p, node(p, k))
        val from = node(p, k)
        if (r == YutResult.BACKDO) {
            if (s == PieceState.WAITING) {
                if (!rules.backdoEnter) return null
                // 대기 말을 출발점 바로 뒤(날윷 19)에 올림
                val i = YutBoard.goalIndex(YutBoard.ROUTE_OUTER) - 1
                val n = YutBoard.nodeAt(YutBoard.ROUTE_OUTER, i)
                return MovePlan(p, r, moving, -1, intArrayOf(n), n, false, YutBoard.ROUTE_OUTER, i)
            }
            val nm = YutBoard.normalize(route[p][k], index[p][k])
            var rt = nm / 100
            var i = nm % 100 - 1
            if (i <= 0) {
                // 도에서 빽도: 참먹이에 선 채로 남음 (바깥 길 끝 위치)
                rt = YutBoard.ROUTE_OUTER
                i = YutBoard.goalIndex(rt)
            }
            val n = YutBoard.nodeAt(rt, i)
            return MovePlan(p, r, moving, from, intArrayOf(n), n, false, rt, i)
        }
        val rt: Int
        val i0: Int
        if (s == PieceState.WAITING) {
            rt = YutBoard.ROUTE_OUTER
            i0 = 0
        } else {
            val nm = YutBoard.normalize(route[p][k], index[p][k])
            rt = nm / 100
            i0 = nm % 100
        }
        val goalIdx = YutBoard.goalIndex(rt)
        val steps = r.steps
        // 참먹이에 선 말(i0 == goalIdx)은 어떤 앞 결과든 바로 골인 (지나갈 칸 없음)
        val len = if (i0 >= goalIdx) 0 else minOf(steps, goalIdx - i0)
        val path = IntArray(len)
        for (s2 in 1..len) path[s2 - 1] = YutBoard.nodeAt(rt, i0 + s2)
        val goal = i0 + steps >= goalIdx
        val dest = if (goal) -1 else path[len - 1]
        return MovePlan(p, r, moving, from, path, dest, goal, rt, if (goal) goalIdx else i0 + steps)
    }

    // ── 던지기 ──

    /** 주입한 난수로 던진다 (낙 설정 반영) */
    fun throwYut(): ThrowOutcome {
        val sticks = YutThrow.roll(random)
        val nak = rules.nak && YutThrow.nak(random)
        return throwWith(sticks, nak)
    }

    /** 정해진 가락 결과로 던진다 (화면 테스트·연출용) */
    fun throwWith(sticks: BooleanArray, nak: Boolean = false): ThrowOutcome {
        check(phase == Phase.THROW) { "지금은 던질 차례가 아님" }
        val p = current
        throwsLeft--
        val r = if (nak) null else YutThrow.result(sticks)
        if (r != null) {
            q.add(r)
            if (r.again) throwsLeft++
        }
        val before = turnCounter
        val discarded = settle()
        return ThrowOutcome(p, sticks.copyOf(), r, nak, r?.again == true, discarded, turnCounter != before, current)
    }

    // ── 이동 ──

    /** 대기열 qi 번째 결과로 말 k 를 움직인다 (못 움직이면 null) */
    fun move(qi: Int, k: Int): MoveOutcome? {
        val pl = plan(qi, k) ?: return null
        val p = pl.player
        q.removeAt(qi)
        for (j in pl.pieces) {
            if (pl.goal) {
                state[p][j] = PieceState.DONE
            } else {
                state[p][j] = PieceState.BOARD
                route[p][j] = pl.route
                index[p][j] = pl.index
            }
        }
        var capturedPlayer = -1
        var captured = NONE
        var joined = false
        var stack = 0
        if (!pl.goal) {
            // 잡기: 상대 묶음 전체 대기로
            for (o in 0 until playerCount) {
                if (o == p) continue
                val there = piecesAt(o, pl.dest)
                if (there.isNotEmpty()) {
                    capturedPlayer = o
                    captured = there
                    for (j in there) state[o][j] = PieceState.WAITING
                }
            }
            // 업기: 이미 있던 내 말도 움직인 말과 같은 길·위치로 (빽도는 이 묶음이 지나온 길로)
            for (j in 0 until pieces) {
                if (state[p][j] == PieceState.BOARD && node(p, j) == pl.dest && !pl.pieces.contains(j)) {
                    joined = true
                    route[p][j] = pl.route
                    index[p][j] = pl.index
                }
            }
            stack = piecesAt(p, pl.dest).size
        }
        val extra = capturedPlayer >= 0
        if (extra) throwsLeft++

        val before = turnCounter
        if (goalCount(p) == pieces) {
            // 모두 골인: 순위 확정, 남은 결과·던지기는 버림
            finishedCount++
            finishRank[p] = finishedCount
            q.clear()
            throwsLeft = 0
            if (finishedCount >= playerCount - 1) {
                for (o in 0 until playerCount) if (finishRank[o] == 0) finishRank[o] = playerCount
                phase = Phase.OVER
                ranks = finishRank.copyOf()
            } else {
                endTurn()
            }
            return MoveOutcome(pl, 0, false, capturedPlayer, captured, false, true, finishRank[p], NO_RESULTS,
                turnCounter != before, current, phase == Phase.OVER)
        }
        val discarded = settle()
        return MoveOutcome(pl, stack, joined, capturedPlayer, captured, extra, false, 0, discarded,
            turnCounter != before, current, false)
    }

    // ── 차례 정리 ──

    private var turnCounter = 0

    /** 던지기·이동 뒤 다음 할 일 정하기 (버린 결과를 돌려줌) */
    private fun settle(): List<YutResult> {
        if (throwsLeft > 0) {
            phase = Phase.THROW
            return NO_RESULTS
        }
        if (q.isEmpty()) {
            endTurn()
            return NO_RESULTS
        }
        var any = false
        for (r in q) if (isUsable(r)) { any = true; break }
        if (!any) {
            val d = ArrayList(q)
            q.clear()
            endTurn()
            return d
        }
        phase = Phase.MOVE
        return NO_RESULTS
    }

    private fun endTurn() {
        var n = current
        do { n = (n + 1) % playerCount } while (finishRank[n] != 0)
        current = n
        q.clear()
        throwsLeft = 1
        phase = Phase.THROW
        turnCounter++
    }

    // ── 끝 ──

    /** 말을 모두 골인한 사람이 아직 남아 있는지 / 진행 중인 사람 수 */
    val activeCount: Int get() = playerCount - finishedCount

    /**
     * [여기서 종료]: 지금 순위로 끝낸다.
     * 골인을 끝낸 사람은 그 순위, 나머지는 골인 수 → 판 위 말 진행 거리 합 순 (같으면 공동 순위).
     */
    fun stopHere(): IntArray {
        val out = finishRank.copyOf()
        val rest = ArrayList<Int>()
        for (p in 0 until playerCount) if (out[p] == 0) rest.add(p)
        if (rest.isNotEmpty()) {
            val keys = rest.map { goalCount(it).toLong() * 1000L + progressSum(it) }
            val r = Ranking.competition(keys)
            for ((j, p) in rest.withIndex()) out[p] = finishedCount + r[j]
        }
        phase = Phase.OVER
        stoppedEarly = true
        q.clear()
        throwsLeft = 0
        ranks = out
        return out.copyOf()
    }

    // ── 테스트·연출 확인용 준비 ──

    /**
     * 테스트·연출 확인용: 말 k 를 칸 node 에 놓는다 (route 를 안 주면 칸으로 길을 고름:
     * 바깥 0~19 → 바깥 길 (0 은 참먹이에 선 말), 20~24 → 모 지름길, 25~28 → 뒷모 지름길).
     * node = -1 이면 대기, -2 면 골인.
     */
    fun setupPiece(p: Int, k: Int, node: Int, routeId: Int = -1) {
        when (node) {
            -1 -> state[p][k] = PieceState.WAITING
            -2 -> state[p][k] = PieceState.DONE
            else -> {
                val rt = if (routeId >= 0) routeId else when (node) {
                    in 0..19 -> YutBoard.ROUTE_OUTER
                    in 20..24 -> YutBoard.ROUTE_MO
                    else -> YutBoard.ROUTE_DWIMO
                }
                val i = if (node == YutBoard.START) YutBoard.goalIndex(rt) else YutBoard.ROUTES[rt].indexOf(node)
                require(i > 0) { "길 $rt 에 칸 $node 이 없음" }
                state[p][k] = PieceState.BOARD
                route[p][k] = rt
                index[p][k] = i
            }
        }
    }

    /** 테스트·연출 확인용: p 차례로 바꾸고 대기열을 results 로 채움 (비었으면 던질 차례) */
    fun setupTurn(p: Int, vararg results: YutResult) {
        current = p
        q.clear()
        q.addAll(results)
        throwsLeft = if (results.isEmpty()) 1 else 0
        phase = if (results.isEmpty()) Phase.THROW else Phase.MOVE
    }

    companion object {
        private val NONE = IntArray(0)
        private val NO_RESULTS: List<YutResult> = emptyList()
    }
}
