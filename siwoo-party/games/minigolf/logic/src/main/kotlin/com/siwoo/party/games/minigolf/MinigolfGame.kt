package com.siwoo.party.games.minigolf

import com.siwoo.party.common.Ranking

/** 진행 단계 */
enum class Phase {
    /** 지금 사람이 조준·치기 */
    AIM,
    /** 공이 구르는 중 */
    ROLLING,
    /** 지금 사람이 이 홀을 끝냄 (홀인 또는 최대 타수) — 화면이 배너를 보인 뒤 nextTurn() */
    TURN_END,
    /** 모두 이 홀을 끝냄 — 화면이 점수표를 보인 뒤 nextHole() */
    HOLE_END,
    /** 모든 홀 끝 */
    OVER,
}

/** 한 사람이 홀을 끝낸 결과 */
class TurnResult(
    val player: Int,
    val hole: Int,
    /** 기록된 타수 (최대 MAX_STROKES) */
    val score: Int,
    /** 컵에 넣었는지 (false = 최대 타수로 끝남) */
    val holed: Boolean,
) {
    /** 한 번에 넣음 */
    val holeInOne: Boolean get() = holed && score == 1
}

/**
 * 미니 골프 규칙 (순수 Kotlin).
 * - 한 홀씩, 모든 사람이 차례로 그 홀을 끝까지 친다 (넣거나 최대 타수). 홀마다 시작하는 사람이 한 칸씩 돈다
 *   (1번 홀은 1번 사람부터, 2번 홀은 2번 사람부터 …) — 남이 치는 것을 먼저 보는 이득을 고르게.
 * - 코스에는 지금 치는 사람의 공 하나만 있다 (공끼리 부딪히지 않음). 사람마다 코스 시계(장애물)를 처음부터.
 * - 물: 벌타 +1, 공은 친 자리로. 타수(벌타 포함)가 MAX_STROKES 에 닿으면 넣지 못해도 MAX_STROKES 로 기록하고 끝.
 * - 순위: 타수 합이 적을수록 위 (같으면 같은 순위).
 *
 * @param holes 칠 홀들 (테스트·짧은 판은 앞 몇 개만)
 */
class MinigolfGame(val playerCount: Int, val holes: List<HoleDef> = Course.HOLES) {

    /** 화면 알림 (advance 안에서 불림) */
    interface Listener {
        fun onWall(x: Float, y: Float, speed: Float) {}
        fun onLip(x: Float, y: Float) {}
        /** 컵에 들어감 (result.holeInOne 이면 홀인원) */
        fun onHoled(result: TurnResult) {}
        /** 물에 빠짐 (x, y = 빠진 자리). 벌타를 더한 뒤 — turnOver 면 최대 타수로 이 사람 끝 */
        fun onWater(x: Float, y: Float, turnOver: Boolean) {}
        /** 코스 위에 멈춤 — turnOver 면 최대 타수로 이 사람 끝 */
        fun onStopped(turnOver: Boolean) {}
    }

    var listener: Listener? = null

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(holes.isNotEmpty())
    }

    /** scores[p][h] = 기록된 타수 (아직이면 −1) */
    val scores = Array(playerCount) { IntArray(holes.size) { -1 } }

    /** holed[p][h] = 컵에 넣었는지 (false 면 최대 타수로 끝났거나 아직) */
    private val holedFlags = Array(playerCount) { BooleanArray(holes.size) }

    /** p 가 홀 h 를 컵에 넣었는지 */
    fun holedAt(p: Int, h: Int): Boolean = holedFlags[p][h]

    var phase = Phase.AIM
        private set

    /** 지금 홀 (0부터) */
    var holeIndex = 0
        private set

    /** 이 홀에서 몇 번째 사람인지 (0..playerCount-1) */
    var turnPos = 0
        private set

    /** 지금 치는 사람 */
    val current: Int get() = playerAt(holeIndex, turnPos)

    /** 지금 홀 데이터 */
    val hole: HoleDef get() = holes[holeIndex]

    /** 지금 사람의 이 홀 타수 (벌타 포함, 아직 기록 전) */
    var strokes = 0
        private set

    /** 그중 벌타 수 */
    var penalties = 0
        private set

    /** 지금 사람이 마지막으로 친 자리 (물에 빠지면 여기로) */
    var lastX = 0f
        private set
    var lastY = 0f
        private set

    /** 이 홀에서 지금까지 넣은/끝낸 결과 (마지막 사람 것) */
    var lastResult: TurnResult? = null
        private set

    /** 지금 홀 물리 */
    var sim: CourseSim = newSim()
        private set

    /** 홀 h 에서 k 번째로 치는 사람 */
    fun playerAt(h: Int, k: Int): Int = (h + k) % playerCount

    /** 홀 h 를 치는 사람 순서 */
    fun order(h: Int): IntArray = IntArray(playerCount) { playerAt(h, it) }

    private fun newSim(): CourseSim {
        val s = CourseSim(hole)
        s.listener = object : CourseSim.Listener {
            override fun onWall(x: Float, y: Float, speed: Float) {
                listener?.onWall(x, y, speed)
            }

            override fun onLip(x: Float, y: Float) {
                listener?.onLip(x, y)
            }

            override fun onEnd(outcome: Outcome) = ballEnded(outcome)
        }
        lastX = hole.teeX
        lastY = hole.teeY
        return s
    }

    /** 지금 사람이 이 홀을 시작: 공을 티에, 코스 시계를 처음으로 */
    private fun startTurn() {
        strokes = 0
        penalties = 0
        sim.placeBall(hole.teeX, hole.teeY)
        sim.resetCourseClock()
        lastX = hole.teeX
        lastY = hole.teeY
        phase = Phase.AIM
    }

    /**
     * 치기: 방향 (dirX, dirY) (단위 벡터), 힘 0..1. AIM 단계가 아니거나 힘이 0 이면 false.
     * 타수 +1, 공이 구르기 시작 (ROLLING).
     */
    fun shoot(dirX: Float, dirY: Float, power: Float): Boolean {
        if (phase != Phase.AIM) return false
        val len = CourseSim.length(dirX, dirY)
        val sp = CourseSim.speedFor(power)
        if (len <= 0f || sp <= 0f) return false
        strokes++
        lastX = sim.ball.x
        lastY = sim.ball.y
        phase = Phase.ROLLING
        sim.shoot(dirX / len * sp, dirY / len * sp)
        return true
    }

    /** dtMs 만큼 굴림. 아직 구르면 true */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.ROLLING) return false
        sim.advance(dtMs)
        return phase == Phase.ROLLING
    }

    /** 고정 단계 하나 (테스트용) */
    fun step() {
        if (phase == Phase.ROLLING) sim.step()
    }

    private fun ballEnded(outcome: Outcome) {
        if (phase != Phase.ROLLING) return
        when (outcome) {
            Outcome.CUP -> {
                endTurn(holed = true)
                listener?.onHoled(lastResult!!)
            }
            Outcome.WATER -> {
                val wx = sim.ball.x
                val wy = sim.ball.y
                strokes += GameConfig.WATER_PENALTY
                penalties += GameConfig.WATER_PENALTY
                sim.placeBall(lastX, lastY)
                val ended = strokes >= GameConfig.MAX_STROKES
                if (ended) endTurn(holed = false) else phase = Phase.AIM
                listener?.onWater(wx, wy, ended)
            }
            Outcome.STOPPED, Outcome.NONE -> {
                val ended = strokes >= GameConfig.MAX_STROKES
                if (ended) endTurn(holed = false) else phase = Phase.AIM
                listener?.onStopped(ended)
            }
        }
    }

    private fun endTurn(holed: Boolean) {
        val score = minOf(strokes, GameConfig.MAX_STROKES)
        scores[current][holeIndex] = score
        holedFlags[current][holeIndex] = holed
        lastResult = TurnResult(current, holeIndex, score, holed)
        phase = Phase.TURN_END
    }

    /** TURN_END → 다음 사람 (AIM) 또는 홀 끝 (HOLE_END) */
    fun nextTurn(): Phase {
        if (phase != Phase.TURN_END) return phase
        if (turnPos + 1 < playerCount) {
            turnPos++
            startTurn()
        } else {
            phase = Phase.HOLE_END
        }
        return phase
    }

    /** HOLE_END → 다음 홀 첫 사람 (AIM) 또는 끝 (OVER) */
    fun nextHole(): Phase {
        if (phase != Phase.HOLE_END) return phase
        if (holeIndex + 1 >= holes.size) {
            phase = Phase.OVER
            return phase
        }
        holeIndex++
        turnPos = 0
        sim = newSim()
        startTurn()
        return phase
    }

    /** 마지막 홀인지 */
    val lastHole: Boolean get() = holeIndex == holes.size - 1

    /** p 의 지금까지 타수 합 */
    fun total(p: Int): Int {
        var s = 0
        for (v in scores[p]) if (v > 0) s += v
        return s
    }

    /** 지금까지 친 홀들의 파 합 (p 가 끝낸 홀만) */
    fun parSoFar(p: Int): Int {
        var s = 0
        for (h in holes.indices) if (scores[p][h] > 0) s += holes[h].par
        return s
    }

    /** 전체 파 */
    val totalPar: Int get() = holes.sumOf { it.par }

    /** 순위 (타수 합이 적을수록 위, 같으면 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(playerCount) { total(it).toLong() }, higherIsBetter = false)

    /** 1위들 */
    fun winners(): List<Int> {
        val r = ranks()
        return (0 until playerCount).filter { r[it] == 1 }
    }

    companion object {
        /** 파와 비교한 이름 (홀인원·이글·버디·파·보기…) */
        fun scoreName(score: Int, par: Int, holed: Boolean): String {
            if (!holed) return "최대 타수"
            if (score == 1) return "홀인원"
            return when (score - par) {
                -3 -> "알바트로스"
                -2 -> "이글"
                -1 -> "버디"
                0 -> "파"
                1 -> "보기"
                2 -> "더블 보기"
                else -> "트리플 보기"
            }
        }
    }
}
