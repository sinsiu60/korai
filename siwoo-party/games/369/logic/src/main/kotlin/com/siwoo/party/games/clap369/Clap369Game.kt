package com.siwoo.party.games.clap369

/** 369 판정·제한 시간 (순수 함수) */
object Clap369 {
    /** n 에 들어 있는 3·6·9 숫자 개수 = 쳐야 할 [짝] 횟수 (33 → 2, 13 → 1, 10 → 0) */
    fun claps(n: Int): Int {
        require(n >= 0)
        var v = n
        var c = 0
        do {
            val d = v % 10
            if (d == 3 || d == 6 || d == 9) c++
            v /= 10
        } while (v > 0)
        return c
    }

    /** n 차례의 제한 시간: 1~9 3.0초, 10의 배수마다 0.2초씩 줄어 90부터 1.2초 (최소) */
    fun limitMs(n: Int): Long =
        (GameConfig.LIMIT_START_MS - GameConfig.LIMIT_STEP_MS * (n / 10)).coerceAtLeast(GameConfig.LIMIT_MIN_MS)
}

/** 누를 수 있는 버튼 */
enum class Key { NUMBER, CLAP }

enum class Phase {
    /** 차례 사이 (탈락 연출 중·시작 전) */
    IDLE,
    /** 지금 사람이 입력 중 (제한 시간 흐름) */
    INPUT,
    /** 필요한 입력을 마침 — SETTLE_MS 동안 더 누르면 탈락, 지나면 다음 사람 */
    SETTLE,
    /** 한 명만 남음 */
    OVER,
}

/** 탈락 이유 */
enum class Fail(val label: String) {
    /** 짝을 쳐야 하는데 [숫자] */
    SHOULD_CLAP("짝을 쳐야 해요!"),
    /** 숫자를 말해야 하는데 [짝] */
    SHOULD_SAY("숫자를 말해야 해요!"),
    /** 필요한 것보다 더 누름 (짝이 너무 많음 / 숫자 두 번) */
    TOO_MANY("너무 많이 눌렀어요!"),
    /** 시간 안에 짝을 다 못 침 */
    TOO_FEW("짝이 모자라요!"),
    /** 시간 안에 아무것도 안 누름 */
    TIMEOUT("시간 초과!"),
}

/** 누른 결과 */
sealed class Press {
    /** 무시 (차례 밖) */
    object Ignored : Press()
    /** 짝 하나 셈 (아직 모자람) */
    class Clapped(val count: Int) : Press()
    /** 필요한 입력 완료 → settleUntil(게임 시간)에 다음 사람 */
    class Done(val number: Int, val claps: Int, val settleUntil: Long) : Press()
    /** 탈락 */
    class Out(val player: Int, val number: Int, val reason: Fail, val gameOver: Boolean) : Press()
}

/** 시계 진행 결과 (update) */
sealed class Tick {
    object None : Tick()
    /** 확인 시간이 지나 다음 사람 차례 시작 */
    class Next(val player: Int, val number: Int, val deadline: Long) : Tick()
    /** 시간 초과로 탈락 */
    class Out(val out: Press.Out) : Tick()
}

/**
 * 369 박수 규칙.
 * - 1번 사람부터 차례대로 1, 2, 3 … 숫자를 이어 간다.
 * - 숫자에 3·6·9 가 없으면 [숫자]를 1번, 있으면 그 개수만큼 [짝]을 정확히 친다 (33 → 짝 2번, 13 → 짝 1번).
 * - 입력 판정 (제한 시간 = Clap369.limitMs, 차례 시작부터):
 *   · 3·6·9 없는 수: [숫자] 1번 → 완료. [짝] → 탈락(SHOULD_SAY).
 *   · 짝 N번인 수: [짝]을 셀 때마다 +1, N번째에서 완료. [숫자] → 탈락(SHOULD_CLAP).
 *   · 완료 뒤 SETTLE_MS(0.4초) 동안 아무 버튼이나 더 누르면 탈락(TOO_MANY). 이 시간이 지나야 다음 차례가 시작된다
 *     (완료 확인 시간은 제한 시간 밖이라 늦게 끝나도 된다).
 *   · 제한 시간까지 완료 못 함 → 탈락 (짝을 조금이라도 쳤으면 TOO_FEW, 아무것도 안 눌렀으면 TIMEOUT).
 *   · 제한 시간 판정: 누른 시각 < 마감 이면 인정, 마감 시각과 같거나 늦으면 시간 초과.
 * - 탈락하면 다음 살아 있는 사람이 같은 숫자부터 이어 간다 (화면이 연출 뒤 resumeAfterOut 호출).
 * - 한 명 남으면 끝. 순위: 남은 사람 1위, 나중에 탈락한 사람일수록 높은 순위 (탈락 순서의 반대).
 * 시간은 화면이 넘겨준다(ms, 게임 시간) → 테스트에서 그대로 재현 가능.
 */
class Clap369Game(val playerCount: Int) {

    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val alive = BooleanArray(playerCount) { true }
    /** 탈락한 순서 (먼저 탈락한 사람이 앞) */
    val outOrder = ArrayList<Int>()
    var phase = Phase.IDLE
        private set
    /** 지금 차례 */
    var current = 0
        private set
    /** 지금 말해야 할 숫자 */
    var number = 1
        private set
    /** 이번 차례에 친 짝 수 */
    var clapCount = 0
        private set
    /** 이번 차례 시작·마감 (게임 시간) */
    var turnStart = 0L
        private set
    var deadline = 0L
        private set
    var settleUntil = 0L
        private set

    val aliveCount: Int get() = alive.count { it }
    val gameOver: Boolean get() = phase == Phase.OVER
    /** 지금 숫자에 쳐야 할 짝 수 */
    val needed: Int get() = Clap369.claps(number)
    val limitMs: Long get() = Clap369.limitMs(number)

    /** 지금 사람 차례 시작 (처음·탈락 뒤 다시) */
    fun startTurn(nowMs: Long) {
        check(phase == Phase.IDLE) { "차례 중입니다" }
        begin(nowMs)
    }

    /** 일시정지 뒤 지금 차례를 처음부터 다시 (같은 사람·같은 숫자, 제한 시간 새로) */
    fun restartTurn(nowMs: Long) {
        if (phase == Phase.INPUT) begin(nowMs)
    }

    private fun begin(at: Long) {
        phase = Phase.INPUT
        clapCount = 0
        turnStart = at
        deadline = at + limitMs
    }

    /** 지금 차례 사람이 key 를 nowMs 에 눌렀다 */
    fun press(key: Key, nowMs: Long): Press {
        // 확인 시간이 지났으면 먼저 다음 차례로 넘기고 그 사람 입력으로 처리
        if (phase == Phase.SETTLE && nowMs >= settleUntil) commit()
        return when (phase) {
            Phase.IDLE, Phase.OVER -> Press.Ignored
            Phase.SETTLE -> out(Fail.TOO_MANY)
            Phase.INPUT -> {
                if (nowMs >= deadline) return timeoutOut()
                val need = needed
                when (key) {
                    Key.NUMBER -> if (need == 0) done(nowMs) else out(Fail.SHOULD_CLAP)
                    Key.CLAP -> {
                        if (need == 0) return out(Fail.SHOULD_SAY)
                        clapCount++
                        if (clapCount >= need) done(nowMs) else Press.Clapped(clapCount)
                    }
                }
            }
        }
    }

    /** 시계 진행: 마감 지남 → 탈락, 확인 시간 지남 → 다음 사람 */
    fun update(nowMs: Long): Tick = when (phase) {
        Phase.INPUT -> if (nowMs >= deadline) Tick.Out(timeoutOut()) else Tick.None
        Phase.SETTLE -> if (nowMs >= settleUntil) { commit(); Tick.Next(current, number, deadline) } else Tick.None
        else -> Tick.None
    }

    /** 다음 시계 일(마감·확인 끝)까지 (없으면 -1) */
    fun msUntilNextEvent(nowMs: Long): Long = when (phase) {
        Phase.INPUT -> (deadline - nowMs).coerceAtLeast(0)
        Phase.SETTLE -> (settleUntil - nowMs).coerceAtLeast(0)
        else -> -1L
    }

    /** 탈락 연출 뒤: 다음 살아 있는 사람이 같은 숫자부터 */
    fun resumeAfterOut(nowMs: Long) {
        if (phase != Phase.IDLE || aliveCount < 2) return
        if (!alive[current]) current = nextAlive(current)
        begin(nowMs)
    }

    /** from 다음 차례의 살아 있는 사람 (탈락한 사람은 건너뜀) */
    fun nextAlive(from: Int): Int {
        var i = from
        repeat(playerCount) {
            i = (i + 1) % playerCount
            if (alive[i]) return i
        }
        return from
    }

    private fun done(nowMs: Long): Press.Done {
        phase = Phase.SETTLE
        settleUntil = nowMs + GameConfig.SETTLE_MS
        return Press.Done(number, clapCount, settleUntil)
    }

    /** 확인 시간 끝 → 다음 숫자, 다음 사람 (시작 시각 = 확인 끝 시각) */
    private fun commit() {
        number++
        current = nextAlive(current)
        begin(settleUntil)
    }

    private fun timeoutOut(): Press.Out = out(if (clapCount > 0) Fail.TOO_FEW else Fail.TIMEOUT)

    private fun out(reason: Fail): Press.Out {
        val p = current
        alive[p] = false
        outOrder.add(p)
        val over = aliveCount <= 1
        phase = if (over) Phase.OVER else Phase.IDLE
        return Press.Out(p, number, reason, over)
    }

    /** 남은 사람 (끝났을 때) */
    fun winner(): Int = alive.indexOfFirst { it }

    /** 최종 순위 (1부터): 남은 사람 1위, 탈락 순서의 반대 (먼저 탈락 = 꼴찌) */
    fun finalRanks(): IntArray {
        val ranks = IntArray(playerCount) { 1 }
        for ((k, p) in outOrder.withIndex()) ranks[p] = playerCount - k
        return ranks
    }
}
