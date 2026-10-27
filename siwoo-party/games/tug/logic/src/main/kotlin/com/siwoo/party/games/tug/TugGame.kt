package com.siwoo.party.games.tug

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 줄 양쪽: 왼쪽 팀 / 오른쪽 팀 */
enum class Side {
    LEFT, RIGHT;

    val other: Side get() = if (this == LEFT) RIGHT else LEFT
}

/** 경기 단계 */
enum class Phase { READY, PLAYING, OVERTIME, DONE }

/** 탭 결과 */
enum class Tap {
    /** 경기 중이 아님 (시작 전·끝난 뒤·시간 지남) */
    IGNORED,
    /** 1초에 15회를 넘어 인정 안 됨 */
    LIMITED,
    /** 인정 → 자기 팀 쪽으로 힘 +1 */
    COUNTED,
    /** 이 탭으로 깃발이 승리선에 닿아 승리 */
    WON,
}

/**
 * 팀 배정. sideOf[p] = 플레이어 p(원래 순서)의 팀.
 * 화면 구역(공통 MultiTouchZoneView: 2인 0왼쪽·1오른쪽, 4인 0왼쪽위·1오른쪽위·2왼쪽아래·3오른쪽아래)에
 * 같은 팀이 같은 쪽에 오도록 zonePlayer(구역 → 플레이어) 를 만든다.
 */
class Teams(sides: List<Side>) {
    val playerCount = sides.size
    private val sideOf = sides.toTypedArray()

    /** 팀별 플레이어 번호 (원래 순서 오름차순) */
    val left: IntArray = (0 until playerCount).filter { sideOf[it] == Side.LEFT }.toIntArray()
    val right: IntArray = (0 until playerCount).filter { sideOf[it] == Side.RIGHT }.toIntArray()

    /** 구역 번호 → 플레이어 번호. 2인 [왼, 오], 4인 [왼1, 오1, 왼2, 오2] */
    val zonePlayer: IntArray
    /** 플레이어 번호 → 구역 번호 */
    val playerZone: IntArray

    init {
        require(playerCount == GameConfig.SOLO_PLAYERS || playerCount == GameConfig.TEAM_PLAYERS) { "2인 또는 4인" }
        require(left.size == right.size) { "양 팀 인원이 같아야 합니다" }
        zonePlayer = IntArray(playerCount)
        for (k in left.indices) {
            zonePlayer[k * 2] = left[k]
            zonePlayer[k * 2 + 1] = right[k]
        }
        playerZone = IntArray(playerCount)
        for (z in 0 until playerCount) playerZone[zonePlayer[z]] = z
    }

    fun sideOf(player: Int): Side = sideOf[player]

    fun members(side: Side): IntArray = if (side == Side.LEFT) left else right

    companion object {
        /**
         * 2인: 첫 번째 사람 왼쪽, 두 번째 오른쪽 (1 대 1).
         * 4인: 무작위로 섞어 두 명씩 (2 대 2).
         */
        fun assign(playerCount: Int, random: Random = Random.Default): Teams {
            if (playerCount == GameConfig.SOLO_PLAYERS) return Teams(listOf(Side.LEFT, Side.RIGHT))
            require(playerCount == GameConfig.TEAM_PLAYERS) { "2인 또는 4인" }
            val order = (0 until playerCount).shuffled(random)
            val sides = MutableList(playerCount) { Side.LEFT }
            for (k in 0 until playerCount) sides[order[k]] = if (k < playerCount / 2) Side.LEFT else Side.RIGHT
            return Teams(sides)
        }
    }
}

/**
 * 1인당 1초(슬라이딩 창)에 max 회까지만 인정.
 * 인정한 탭 시각을 사람마다 최근 max 개 기억해, 가장 오래된 것이 1초 안이면 거절한다
 * → 어느 1초 구간을 잘라 봐도 인정된 탭은 max 회 이하.
 */
class TapLimiter(playerCount: Int, private val max: Int = GameConfig.TUG_MAX_TAPS_PER_SEC, private val windowMs: Long = GameConfig.TAP_WINDOW_MS) {
    private val times = Array(playerCount) { LongArray(max) }
    private val count = IntArray(playerCount)
    private val head = IntArray(playerCount) // 다음에 쓸 칸 (= 꽉 찼을 때 가장 오래된 칸)

    fun accept(player: Int, timeMs: Long): Boolean {
        val buf = times[player]
        if (count[player] == max && timeMs - buf[head[player]] < windowMs) return false
        buf[head[player]] = timeMs
        head[player] = (head[player] + 1) % max
        if (count[player] < max) count[player]++
        return true
    }

    fun reset() {
        count.fill(0)
        head.fill(0)
    }
}

/**
 * 연타 줄다리기 규칙.
 * - 자기 구역을 탭할 때마다 자기 팀 쪽으로 힘 +1 (1인당 초당 15회까지만 인정)
 * - 깃발 위치 = (오른쪽 팀 합계 − 왼쪽 팀 합계) ÷ winDiff  (−1 = 왼쪽 승리선, +1 = 오른쪽 승리선)
 * - 승리선에 닿으면 바로 그 팀 승리
 * - 30초가 지나면 깃발이 더 가까운 팀(탭 합계가 많은 팀) 승리
 * - 시간 종료 때 정확히 동점이면 5초 연장전: 승리선에 닿거나, 연장이 끝났을 때 앞선 팀 승리.
 *   연장 뒤에도 동점이면 무승부 → 모두 1위
 * - 순위: 이긴 팀 전원 1위, 진 팀 전원 그다음 경쟁 순위 (2인 [1,2], 4인 이긴 둘 1위·진 둘 3위)
 * 시간은 화면이 넘겨준다(일시정지 시간을 뺀 게임 시간 ms) → 테스트에서 그대로 재현 가능.
 */
class TugGame(val teams: Teams) {

    val playerCount = teams.playerCount
    /** 깃발이 승리선까지 가는 탭 차이 */
    val winDiff = if (playerCount == GameConfig.SOLO_PLAYERS) GameConfig.TUG_WIN_DIFF_SOLO else GameConfig.TUG_WIN_DIFF_TEAM
    /** 사람별 인정된 탭 수 (원래 순서) */
    val taps = IntArray(playerCount)
    private val limiter = TapLimiter(playerCount)

    var phase = Phase.READY
        private set
    /** 이긴 팀 (무승부·진행 중이면 null) */
    var winner: Side? = null
        private set
    /** 연장 뒤에도 동점 */
    var draw = false
        private set
    private var startMs = 0L
    private var overtimeStartMs = 0L

    val isOver: Boolean get() = phase == Phase.DONE

    fun start(nowMs: Long) {
        check(phase == Phase.READY) { "이미 시작했습니다" }
        startMs = nowMs
        phase = Phase.PLAYING
    }

    fun teamTaps(side: Side): Int {
        var s = 0
        for (p in teams.members(side)) s += taps[p]
        return s
    }

    /** 오른쪽 팀이 앞서면 +, 왼쪽 팀이 앞서면 − */
    val pull: Int get() = teamTaps(Side.RIGHT) - teamTaps(Side.LEFT)

    /** 깃발이 가야 할 위치 −1(왼쪽 승리선) .. 0(가운데) .. +1(오른쪽 승리선) */
    val flagTarget: Float get() = (pull.toFloat() / winDiff).coerceIn(-1f, 1f)

    /** 깃발이 더 가까운 쪽 (가운데면 null) */
    val leading: Side? get() = when {
        pull > 0 -> Side.RIGHT
        pull < 0 -> Side.LEFT
        else -> null
    }

    /** 지금 단계의 남은 시간 (본 경기 30초 / 연장 5초) */
    fun remainingMs(nowMs: Long): Long = when (phase) {
        Phase.READY -> GameConfig.TUG_TIME_MS
        Phase.PLAYING -> (startMs + GameConfig.TUG_TIME_MS - nowMs).coerceAtLeast(0)
        Phase.OVERTIME -> (overtimeStartMs + GameConfig.TUG_OVERTIME_MS - nowMs).coerceAtLeast(0)
        Phase.DONE -> 0
    }

    /** 플레이어 player(원래 순서)의 탭 */
    fun tap(player: Int, nowMs: Long): Tap {
        if (player !in 0 until playerCount) return Tap.IGNORED
        if (phase != Phase.PLAYING && phase != Phase.OVERTIME) return Tap.IGNORED
        if (remainingMs(nowMs) <= 0) return Tap.IGNORED // 시간 끝 (판정 대기)
        if (!limiter.accept(player, nowMs)) return Tap.LIMITED
        taps[player]++
        val p = pull
        if (p >= winDiff || p <= -winDiff) {
            finish(if (p > 0) Side.RIGHT else Side.LEFT)
            return Tap.WON
        }
        return Tap.COUNTED
    }

    /**
     * 본 경기 시간 끝. 깃발이 더 가까운 팀 승리 → DONE,
     * 정확히 가운데면 연장전 → OVERTIME (nowMs 부터 TUG_OVERTIME_MS)
     */
    fun timeUp(nowMs: Long): Phase {
        if (phase != Phase.PLAYING) return phase
        val lead = leading
        if (lead != null) {
            finish(lead)
        } else {
            phase = Phase.OVERTIME
            overtimeStartMs = nowMs
        }
        return phase
    }

    /** 연장전 끝. 앞선 팀 승리, 그래도 같으면 무승부 */
    fun overtimeUp() {
        if (phase != Phase.OVERTIME) return
        val lead = leading
        if (lead != null) {
            finish(lead)
        } else {
            draw = true
            winner = null
            phase = Phase.DONE
        }
    }

    private fun finish(side: Side) {
        winner = side
        draw = false
        phase = Phase.DONE
    }

    /** 최종 순위 (원래 플레이어 순서, 1부터). 이긴 팀 전원 1위, 진 팀 전원 그다음 경쟁 순위, 무승부는 모두 1위 */
    fun ranks(): IntArray {
        check(phase == Phase.DONE) { "아직 끝나지 않았습니다" }
        val w = winner
        val values = (0 until playerCount).map { p -> if (w == null || teams.sideOf(p) == w) 1L else 0L }
        return Ranking.competition(values)
    }
}
