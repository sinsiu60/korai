package com.siwoo.party.games.sprint

import com.siwoo.party.common.Ranking

/** 발 (각자 구역의 [왼발] [오른발] 버튼) */
enum class Foot { LEFT, RIGHT }

/** 경주 단계 */
enum class Phase {
    /** 소개 중: 눌러도 무시 */
    WAITING,
    /** "준비… 차렷…" — 누르면 부정 출발 */
    SET,
    /** "땅!" 뒤 달리는 중 */
    RUNNING,
    /** 모두 도착했거나 제한 시간 종료 */
    OVER,
}

/** 누른 결과 */
enum class Press {
    /** 한 걸음 전진 */
    STEP,
    /** 한 걸음 전진 + 결승선 통과 */
    FINISH,
    /** 같은 발 연속 → 무효 */
    SAME_FOOT,
    /** 신호 전에 누름 → 부정 출발 */
    FALSE_START,
    /** 부정 출발 벌칙으로 정지 중 → 무효 */
    FROZEN,
    /** 소개 중·도착 뒤·경주 끝 → 무시 */
    IGNORED,
}

/**
 * 100m 달리기 규칙 (순수 Kotlin). 시간은 모두 밖에서 주입한다 (화면은 일시정지를 뺀 게임 시간).
 *
 * - arm(): "준비…" — 이때부터 신호 전에 누르면 부정 출발
 * - signal(at): "땅!" — 부정 출발한 사람은 at + FALSE_START_PENALTY_MS 까지 정지
 *   (부정 출발을 여러 번 해도 벌칙은 한 번, 신호 시각 기준이라 늦게 누른 것과 관계없이 같다)
 * - press(i, foot, at): 왼발·오른발을 번갈아 눌러야 한 걸음(STEP_CM) 전진. 처음 걸음은 어느 발이든 된다.
 *   같은 발을 연속으로 누르면 무효(전진 없음, 마지막 발은 그대로).
 *   at 이 신호보다 앞서면(신호 직전에 누른 터치가 늦게 처리됨) 부정 출발로 친다.
 * - 기록 = 결승선을 넘은 걸음의 시각 − 신호 시각 (ms). 순위는 기록이 빠른 순, 같은 ms 면 같은 순위.
 * - timeUp(): 제한 시간 종료 → 못 들어온 사람은 달린 거리가 긴 순(같으면 같은 순위)으로 도착한 사람 뒤.
 */
class SprintGame(val players: Int) {

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) { "2~4명" }
    }

    var phase = Phase.WAITING
        private set
    /** "땅!" 시각 (게임 시간), 신호 전이면 −1 */
    var signalAt = -1L
        private set
    /** 올바른 걸음 수 */
    val steps = IntArray(players)
    /** 마지막으로 인정된 발 (아직 없으면 null) */
    val lastFoot = arrayOfNulls<Foot>(players)
    /** 부정 출발 여부 */
    val falseStart = BooleanArray(players)
    private val frozenUntil = LongArray(players) { Long.MIN_VALUE }
    /** 기록 (신호부터 도착까지 ms), 아직이면 −1 */
    val finishMs = LongArray(players) { -1L }
    /** 도착한 차례 (1부터, 처리된 순서), 아직이면 0 */
    val arrival = IntArray(players)
    var finishedCount = 0
        private set
    /** 제한 시간으로 끝났는지 */
    var timedOut = false
        private set

    /** 도착에 필요한 걸음 수 */
    val stepsToFinish: Int = (GameConfig.TRACK_CM + GameConfig.STEP_CM - 1) / GameConfig.STEP_CM

    /** "준비…": 이제부터 누르면 부정 출발 */
    fun arm() {
        check(phase == Phase.WAITING)
        phase = Phase.SET
    }

    /** "땅!" */
    fun signal(at: Long) {
        check(phase == Phase.SET)
        signalAt = at
        phase = Phase.RUNNING
        for (i in 0 until players) if (falseStart[i]) frozenUntil[i] = at + GameConfig.FALSE_START_PENALTY_MS
    }

    fun finished(i: Int): Boolean = finishMs[i] >= 0L

    /** 부정 출발 벌칙으로 정지 중인지 (now = 게임 시간) */
    fun frozen(i: Int, now: Long): Boolean = phase == Phase.RUNNING && now < frozenUntil[i]

    /** 벌칙이 풀리는 시각 (부정 출발이 아니면 Long.MIN_VALUE) */
    fun frozenUntil(i: Int): Long = frozenUntil[i]

    /** 달린 거리 (cm, 최대 TRACK_CM) */
    fun distanceCm(i: Int): Int = minOf(steps[i] * GameConfig.STEP_CM, GameConfig.TRACK_CM)

    /** 신호부터 지난 시간 (경주 시계, 0..TIME_LIMIT_MS). 끝난 뒤에는 멈춘 값 */
    fun elapsed(now: Long): Long = when {
        signalAt < 0L -> 0L
        phase == Phase.OVER -> endElapsed
        else -> (now - signalAt).coerceIn(0L, GameConfig.TIME_LIMIT_MS)
    }

    private var endElapsed = 0L

    /** player 가 foot 을 at(게임 시간)에 눌렀다 */
    fun press(player: Int, foot: Foot, at: Long): Press {
        when (phase) {
            Phase.WAITING, Phase.OVER -> return Press.IGNORED
            Phase.SET -> {
                falseStart[player] = true
                return Press.FALSE_START
            }
            Phase.RUNNING -> Unit
        }
        if (finished(player)) return Press.IGNORED
        if (at < signalAt) {
            // 신호 직전의 터치가 늦게 처리됨 → 부정 출발 (벌칙은 신호 기준)
            if (!falseStart[player] && steps[player] == 0) {
                falseStart[player] = true
                frozenUntil[player] = signalAt + GameConfig.FALSE_START_PENALTY_MS
                return Press.FALSE_START
            }
            return Press.FROZEN
        }
        if (at < frozenUntil[player]) return Press.FROZEN
        if (lastFoot[player] == foot) return Press.SAME_FOOT
        lastFoot[player] = foot
        steps[player]++
        if (steps[player] < stepsToFinish) return Press.STEP
        finishMs[player] = at - signalAt
        finishedCount++
        arrival[player] = finishedCount
        if (finishedCount == players) end(finishMs.max())
        return Press.FINISH
    }

    /** 제한 시간 종료 (달리는 중일 때만). 끝났으면 true */
    fun timeUp(): Boolean {
        if (phase != Phase.RUNNING) return false
        timedOut = true
        end(GameConfig.TIME_LIMIT_MS)
        return true
    }

    /** 제한 시간이 지났으면 끝낸다 (now = 게임 시간). 이번에 끝났으면 true */
    fun update(now: Long): Boolean =
        if (phase == Phase.RUNNING && now - signalAt >= GameConfig.TIME_LIMIT_MS) timeUp() else false

    private fun end(elapsedMs: Long) {
        endElapsed = elapsedMs.coerceIn(0L, GameConfig.TIME_LIMIT_MS)
        phase = Phase.OVER
    }

    /** 지금까지 도착한 사람 중 i 의 순위 (기록 기준, 같은 ms 는 같은 순위). 도착 전이면 0 */
    fun finishRank(i: Int): Int {
        if (!finished(i)) return 0
        var r = 1
        for (k in 0 until players) if (finished(k) && finishMs[k] < finishMs[i]) r++
        return r
    }

    /**
     * 최종 순위 (players 순서, 1부터, 동점 같은 순위).
     * 도착한 사람: 기록 빠른 순 → 못 들어온 사람: 달린 거리 긴 순.
     */
    fun ranks(): IntArray {
        val values = ArrayList<Long>(players)
        for (i in 0 until players) {
            values.add(if (finished(i)) finishMs[i] else UNFINISHED - distanceCm(i))
        }
        return Ranking.competition(values, higherIsBetter = false)
    }

    companion object {
        /** 못 들어온 사람 순위 값의 기준 (어떤 기록보다 크다) */
        private const val UNFINISHED = 1_000_000_000L

        /** 기록 글자: 12345 → "12.34초" (1/100초 버림) */
        fun formatRecord(ms: Long): String {
            val cs = ms.coerceAtLeast(0L) / 10L
            val frac = (cs % 100L).toInt()
            return (cs / 100L).toString() + "." + (if (frac < 10) "0" else "") + frac + "초"
        }

        /** 거리 글자: 6012 cm → "60m" (버림) */
        fun formatMeters(cm: Int): String = (cm / 100).toString() + "m"
    }
}
