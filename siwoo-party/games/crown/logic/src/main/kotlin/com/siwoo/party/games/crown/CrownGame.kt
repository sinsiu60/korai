package com.siwoo.party.games.crown

import com.siwoo.party.common.Ranking

/** 게임 단계 */
enum class Phase { READY, PLAYING, OVER }

/** 누르기(새로 손가락을 댐) 결과 */
enum class Press {
    /** 게임 중이 아님 / 잘못된 번호 */
    IGNORED,
    /** 이미 왕관 주인 (손가락을 하나 더 댐) → 변화 없음 */
    KEEP,
    /** 가운데 있던 왕관을 가져감 */
    TOOK_CENTER,
    /** 다른 사람에게서 빼앗음 (빼앗긴 사람 = lastVictim) */
    STOLE,
    /** 빼앗기 쿨다운 중이라 못 빼앗음 */
    COOLDOWN,
}

/**
 * 왕관 쟁탈 규칙 (시간은 밖에서 넣는 게임 시간 ms — 일시정지 시간 제외).
 * - 왕관은 가운데에서 시작. 왕관이 가운데 있을 때 가장 먼저 "새로 누른" 사람이 가져간다
 *   (이미 누르고 있던 사람도 손을 떼고 다시 눌러야 한다).
 * - 왕관 주인이 버튼을 누르고 있는 동안만 점수가 쌓인다 (POINTS_PER_SEC, ms 단위 누적). 다른 사람은 누르고 있어도 0.
 * - 다른 사람이 자기 버튼을 새로 누르면 왕관을 빼앗는다. 단 그 사람의 마지막 빼앗기로부터 STEAL_COOLDOWN_MS 가
 *   지나지 않았으면 못 빼앗는다 (쿨다운은 사람마다 따로, 빼앗을 때만 생긴다). 빼앗긴 사람이 그동안 얻은 점수는 그대로.
 * - 왕관 주인이 손을 모두 떼면 왕관은 가운데로 돌아간다.
 * - 동시에 누른 것은 들어온 순서대로 처리한다 (먼저 처리된 사람이 가운데 왕관을 줍고, 뒤 사람은 빼앗기 판정).
 * - GAME_TIME_MS 가 지나면 끝. 점수(정수) 높은 순, 같은 점수는 같은 순위.
 */
class CrownGame(val playerCount: Int) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    var phase = Phase.READY
        private set
    /** 왕관 주인 (CENTER = 가운데) */
    var holder = CENTER
        private set
    /** 마지막으로 빼앗긴 사람 (STOLE 결과 직후 확인용, 없으면 CENTER) */
    var lastVictim = CENTER
        private set
    /** 버튼을 누르고 있는지 (게임이 받은 누르기/떼기 기준) */
    val holding = BooleanArray(playerCount)
    /** 왕관을 가진 채 누른 시간 누적 (ms) */
    val heldMs = LongArray(playerCount)
    /** 빼앗기 성공 횟수 */
    val steals = IntArray(playerCount)
    /** 마지막 빼앗기 시각 (없으면 아주 먼 과거) */
    private val lastSteal = LongArray(playerCount) { NEVER }
    private var startMs = 0L
    private var lastMs = 0L

    /** 게임이 끝나는 시각 */
    val endMs: Long get() = startMs + GameConfig.GAME_TIME_MS
    val gameOver: Boolean get() = phase == Phase.OVER

    /** 게임 시작 (왕관 가운데, 점수 0) */
    fun start(nowMs: Long) {
        check(phase == Phase.READY) { "이미 시작했습니다" }
        startMs = nowMs
        lastMs = nowMs
        phase = Phase.PLAYING
    }

    /** 시간을 now 까지 진행: 왕관 주인이 누르고 있으면 그만큼 쌓는다 (끝 시각을 넘지 않음) */
    fun update(nowMs: Long) {
        if (phase != Phase.PLAYING) return
        val t = minOf(nowMs, endMs)
        if (t > lastMs) {
            val h = holder
            if (h != CENTER && holding[h]) heldMs[h] += t - lastMs
            lastMs = t
        }
        if (nowMs >= endMs) finish()
    }

    /** 끝 시각이 지났는지 */
    fun isTimeUp(nowMs: Long): Boolean = phase == Phase.OVER || nowMs >= endMs

    /** 남은 시간 (ms, 0 이상) */
    fun timeLeft(nowMs: Long): Long = when (phase) {
        Phase.READY -> GameConfig.GAME_TIME_MS
        Phase.OVER -> 0L
        Phase.PLAYING -> (endMs - nowMs).coerceIn(0L, GameConfig.GAME_TIME_MS)
    }

    /** 플레이어 p 가 자기 버튼을 새로 누름 (손가락 하나 더 대기 포함) */
    fun press(p: Int, nowMs: Long): Press {
        if (p !in 0 until playerCount || phase != Phase.PLAYING) return Press.IGNORED
        update(nowMs)
        if (phase != Phase.PLAYING) return Press.IGNORED
        holding[p] = true
        val h = holder
        return when {
            h == p -> Press.KEEP
            h == CENTER -> { holder = p; Press.TOOK_CENTER }
            cooldownLeft(p, nowMs) > 0L -> Press.COOLDOWN
            else -> {
                holder = p
                lastVictim = h
                lastSteal[p] = nowMs
                steals[p]++
                Press.STOLE
            }
        }
    }

    /**
     * 플레이어 p 가 버튼에서 손을 모두 뗌. 왕관 주인이었으면 왕관이 가운데로 돌아가고 true.
     * (게임이 끝난 뒤에도 누름 상태는 정리한다)
     */
    fun release(p: Int, nowMs: Long): Boolean {
        if (p !in 0 until playerCount) return false
        update(nowMs)
        holding[p] = false
        if (phase != Phase.PLAYING || holder != p) return false
        holder = CENTER
        return true
    }

    /** 빼앗기 쿨다운 남은 시간 (ms, 0 = 빼앗기 가능) */
    fun cooldownLeft(p: Int, nowMs: Long): Long {
        val last = lastSteal[p]
        if (last == NEVER) return 0L
        return (last + GameConfig.STEAL_COOLDOWN_MS - nowMs).coerceAtLeast(0L)
    }

    /** 점수 (정수, 화면 표시·순위 기준) */
    fun score(p: Int): Int = (heldMs[p] * GameConfig.POINTS_PER_SEC / 1000L).toInt()

    /** 시간 끝 (update 가 끝 시각을 넘기면 자동). 점수는 더 쌓이지 않는다 (왕관 위치는 그대로) */
    fun finish() {
        phase = Phase.OVER
    }

    /** 최종 순위 (1부터, 점수 높은 순, 같은 점수는 같은 순위) */
    fun ranks(): IntArray = Ranking.competition(List(playerCount) { score(it).toLong() })

    companion object {
        /** 왕관이 가운데 있음 */
        const val CENTER = -1
        private const val NEVER = Long.MIN_VALUE
    }
}
