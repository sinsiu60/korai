package com.siwoo.party.games.curling

import kotlin.math.PI
import kotlin.math.atan2

/** 진행 단계: 조준 → 스톤이 움직이는 중 → 엔드 득점 계산됨(점수판) → 끝 */
enum class Phase { AIM, MOVING, SCORED, OVER }

/**
 * 컬링 규칙 (2~4인, 차례대로, GameConfig.ENDS 엔드). 순수 Kotlin — 시간은 밖에서 advance(ms) 로 넣는다.
 * - 2인 1인당 4스톤, 3인 1인당 3스톤, 4인은 2 대 2 팀(0·2번 / 1·3번) 1인당 2스톤
 * - 엔드마다 편이 번갈아 던지고(CurlingRules.throwOrder), 모두 멈추면 다음 스톤
 * - 다 멈췄을 때 호그 라인을 못 넘은 스톤은 빠진다 (핵 앞이 막히지 않게)
 * - 마지막 스톤이 멈추면 CurlingRules.scoreEnd 로 득점 → SCORED. 화면이 점수판을 보여 준 뒤 nextEnd()
 * - 다음 엔드는 득점한 편이 먼저 던진다 (블랭크면 다음 편)
 *
 * @param firstSide 첫 엔드에 먼저 던질 편
 */
class CurlingGame(val players: Int, firstSide: Int = 0, fadeMs: Long = GameConfig.FADE_MS) {

    /** 진행 알림 (화면 연출·소리용) */
    interface Listener {
        fun onCollide(x: Float, y: Float, speed: Float) {}
        /** 스톤이 빠지기 시작. hog = 호그 라인을 못 넘어서, 아니면 시트 밖으로 */
        fun onOut(stone: CurlStone, hog: Boolean) {}
        /** 다 멈추고 다음 사람이 던질 차례 */
        fun onNextThrow(player: Int) {}
        /** 엔드의 마지막 스톤까지 멈춤 → 득점 */
        fun onEndScored(end: Int, result: EndResult) {}
    }

    var listener: Listener? = null

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val teamMode = CurlingRules.isTeamMode(players)
    val sides = CurlingRules.sideCount(players)
    val stonesPerEnd = CurlingRules.stonesPerEnd(players)

    val sheet = Sheet(fadeMs)

    /** 편마다 합계 점수 */
    val sideTotals = IntArray(sides)
    /** [엔드][편] 점수 */
    val endScores = Array(GameConfig.ENDS) { IntArray(sides) }
    /** 엔드별 결과 (아직 안 끝난 엔드는 null) */
    val results = arrayOfNulls<EndResult>(GameConfig.ENDS)

    /** 지금 엔드 (0부터) */
    var end = 0
        private set
    /** 이번 엔드에 먼저 던지는 편 */
    var startSide = firstSide
        private set
    /** 이번 엔드 던지는 순서 (플레이어 번호) */
    var order: IntArray = CurlingRules.throwOrder(players, firstSide, 0)
        private set
    /** 이번 엔드에서 다음에 던질 스톤 번호 */
    var throwIndex = 0
        private set
    /** 이번 엔드 스톤 (번호 = 던지는 순서) */
    var stones: Array<CurlStone> = makeStones()
        private set
    var phase = Phase.AIM
        private set
    /** 다음 던지기의 컬 방향 (+1 오른쪽, −1 왼쪽) */
    var curlDir = 1
        private set
    /** 마지막 엔드 결과 */
    var lastResult: EndResult = EndResult.EMPTY
        private set
    /** 지금까지 던진 수 (전체) */
    var throwsMade = 0
        private set

    private var shooterSide = 0

    init {
        require(firstSide in 0 until sides)
        sheet.setStones(stones)
        sheet.listener = object : Sheet.Listener {
            override fun onCollide(a: CurlStone, b: CurlStone, x: Float, y: Float, speed: Float) {
                listener?.onCollide(x, y, speed)
            }

            override fun onOut(stone: CurlStone) {
                listener?.onOut(stone, false)
            }
        }
    }

    private fun makeStones(): Array<CurlStone> =
        Array(stonesPerEnd) { CurlStone(it, order[it], CurlingRules.sideOf(order[it], players)) }

    /** 지금 던질 사람 (엔드가 끝났으면 마지막에 던진 사람) */
    val current: Int get() = order[minOf(throwIndex, stonesPerEnd - 1)]
    /** 지금 던질 사람의 편 */
    val currentSide: Int get() = sideOf(current)

    fun sideOf(player: Int): Int = CurlingRules.sideOf(player, players)

    /** 지금 던질(던진) 스톤 */
    val currentStone: CurlStone get() = stones[minOf(throwIndex, stonesPerEnd - 1)]

    /** 이번 엔드에 이 플레이어가 아직 던지지 않은 스톤 수 */
    fun remaining(player: Int): Int {
        var n = 0
        for (k in throwIndex until stonesPerEnd) {
            if (order[k] == player && !(k == throwIndex && phase == Phase.MOVING)) n++
        }
        return n
    }

    /** 이번 엔드에 이 편이 아직 던지지 않은 스톤 수 */
    fun remainingForSide(side: Int): Int {
        var n = 0
        for (k in throwIndex until stonesPerEnd) {
            if (stones[k].side == side && !(k == throwIndex && phase == Phase.MOVING)) n++
        }
        return n
    }

    /** 이번 엔드 마지막 스톤(해머)을 던지는 편 */
    val hammerSide: Int get() = sideOf(order[stonesPerEnd - 1])

    /** 컬 방향 고르기 (조준 중에만, +1 오른쪽 / −1 왼쪽) */
    fun setCurl(dir: Int): Boolean {
        if (phase != Phase.AIM) return false
        curlDir = if (dir < 0) -1 else 1
        return true
    }

    /**
     * 던지기: 새총 방향(dirX, dirY — 화면 좌표, x 가 하우스 쪽)과 힘 0..1.
     * 방향은 ±MAX_AIM_DEG 로 제한, 빠르기 = MIN_SPEED + 힘 × (MAX_SPEED − MIN_SPEED).
     */
    fun throwStone(dirX: Float, dirY: Float, power: Float): Boolean {
        if (phase != Phase.AIM) return false
        val s = stones[throwIndex]
        sheet.launch(s, aimAngle(dirX, dirY), launchSpeed(power), curlDir)
        shooterSide = s.side
        phase = Phase.MOVING
        throwsMade++
        return true
    }

    /** 스위핑 연타 한 번 (던진 스톤이 움직이는 중일 때만) */
    fun sweep(): Boolean = phase == Phase.MOVING && sheet.sweepTap()

    /**
     * 화면 프레임마다 흐른 시간(ms). 움직이는 중에만 물리가 돈다.
     * 반환: 아직 움직이는 중이면 true (false 면 프레임 루프를 멈춰도 됨)
     */
    fun advance(dtMs: Long): Boolean {
        if (phase != Phase.MOVING) return false
        sheet.advance(dtMs)
        if (sheet.settled) afterSettle()
        return phase == Phase.MOVING
    }

    /** 모두 멈춤: 호그 라인 검사 → 다음 스톤 또는 엔드 득점 */
    private fun afterSettle() {
        var hog = false
        for (s in stones) {
            if (s.inPlay && s.x < GameConfig.HOG_X) {
                sheet.fadeOut(s)
                hog = true
                listener?.onOut(s, true)
            }
        }
        if (hog) return // 사라지는 연출이 끝나면 다시 여기로
        sheet.endThrow()
        throwIndex++
        if (throwIndex < stonesPerEnd) {
            phase = Phase.AIM
            listener?.onNextThrow(current)
            return
        }
        scoreEnd()
    }

    private fun scoreEnd() {
        val side = IntArray(stonesPerEnd) { stones[it].side }
        val dist = FloatArray(stonesPerEnd) { val s = stones[it]; if (s.inPlay) CurlingRules.distance(s.x, s.y) else -1f }
        val r = CurlingRules.scoreEnd(side, dist)
        lastResult = r
        results[end] = r
        if (r.side >= 0) {
            endScores[end][r.side] = r.points
            sideTotals[r.side] += r.points
        }
        phase = Phase.SCORED
        listener?.onEndScored(end, r)
    }

    /** 마지막 엔드였는지 */
    val lastEnd: Boolean get() = end >= GameConfig.ENDS - 1

    /**
     * 점수판을 본 뒤 다음 엔드로. 마지막 엔드였으면 OVER 가 되고 false.
     * 다음 엔드는 득점한 편이 먼저 던진다 (블랭크면 다음 편).
     */
    fun nextEnd(): Boolean {
        if (phase != Phase.SCORED) return false
        if (lastEnd) {
            phase = Phase.OVER
            return false
        }
        startSide = CurlingRules.nextStartSide(startSide, lastResult.side, sides)
        end++
        order = CurlingRules.throwOrder(players, startSide, end)
        throwIndex = 0
        stones = makeStones()
        sheet.setStones(stones)
        curlDir = 1
        phase = Phase.AIM
        return true
    }

    /** players 순서의 순위 (편 합계 점수, 팀전은 팀원이 같은 순위) */
    fun ranks(): IntArray = CurlingRules.ranks(players, sideTotals)

    /** 플레이어의 합계 점수 (팀전은 팀 점수) */
    fun total(player: Int): Int = sideTotals[sideOf(player)]

    // ── 테스트·연출 확인용 ──

    /** 이번 엔드 스톤 k 를 (x, y) 에 멈춰 놓기 (던진 것으로 침) */
    fun place(k: Int, x: Float, y: Float) {
        val s = stones[k]
        s.place(x, y)
        s.state = StoneState.IN_PLAY
    }

    /** 다음 스톤을 던진 것으로 치고 (x, y) 에 놓기 → 다음 차례 또는 득점 (AIM 때만) */
    fun skipThrowTo(x: Float, y: Float) {
        if (phase != Phase.AIM) return
        place(throwIndex, x, y)
        shooterSide = stones[throwIndex].side
        phase = Phase.MOVING
        throwsMade++
        afterSettle()
    }

    companion object {
        private const val MAX_AIM = (GameConfig.MAX_AIM_DEG * PI / 180.0).toFloat()

        /** 새총 방향 → 던지는 각도 (라디안, 0 = 정면, ±MAX_AIM_DEG 로 제한. 뒤쪽을 향하면 가까운 끝으로) */
        fun aimAngle(dirX: Float, dirY: Float): Float {
            if (dirX == 0f && dirY == 0f) return 0f
            return atan2(dirY, dirX).coerceIn(-MAX_AIM, MAX_AIM)
        }

        /** 힘 0..1 → 처음 빠르기 */
        fun launchSpeed(power: Float): Float =
            GameConfig.MIN_SPEED + (GameConfig.MAX_SPEED - GameConfig.MIN_SPEED) * power.coerceIn(0f, 1f)
    }
}
