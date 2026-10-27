package com.siwoo.party.hub.tools

import kotlin.random.Random

/**
 * 손가락 추첨 규칙 (순수 Kotlin — Android import 없음, 시간·난수는 밖에서 주입).
 *
 * - 손가락(pointer id)마다 빈 칸(slot 0..9)을 하나 준다. 칸 번호 = 색 번호.
 * - 손가락이 2개 이상이고 [STABLE_MS] 동안 수가 바뀌지 않으면 카운트다운 3·2·1 ([COUNT_STEP_MS] 간격).
 *   손가락이 하나라도 오르내리면 기다림·카운트다운이 처음부터 다시.
 * - 카운트다운이 끝나면: 1명 뽑기 → [winner] 한 칸, 팀 나누기 → [team] 0/1 (인원 차이 1 이하).
 * - 결과가 나온 뒤 새로 올린 손가락은 참가하지 않는다. 손가락을 모두 떼면 다음 손가락이 새 판을 시작한다.
 */
class FingerPick(private val random: Random = Random.Default) {

    enum class Mode { ONE, TEAMS }
    enum class Phase { IDLE, WAITING, COUNTDOWN, RESULT }

    var mode: Mode = Mode.ONE
        private set
    var phase: Phase = Phase.IDLE
        private set

    /** 칸별 pointer id (-1 = 빈 칸) */
    val slotId = IntArray(MAX_FINGERS) { -1 }
    /** 칸별 팀 (결과 뒤 팀 나누기에서만, -1 = 없음) */
    val team = IntArray(MAX_FINGERS) { -1 }
    /** 결과에 참가한 칸 */
    val participant = BooleanArray(MAX_FINGERS)
    /** 1명 뽑기 당첨 칸 (-1 = 없음) */
    var winner = -1
        private set
    /** 마지막으로 손가락 수가 바뀐 시각 */
    var changedAt = 0L
        private set
    /** 결과 뒤 손가락을 모두 뗐는지 (다음 손가락이 새 판) */
    var allLifted = false
        private set

    val count: Int get() = slotId.count { it >= 0 }

    fun slotOf(id: Int): Int = slotId.indexOf(id)

    fun teamSize(t: Int): Int = team.count { it == t }

    fun setMode(m: Mode, now: Long) {
        if (mode == m) return
        mode = m
        reset(now)
    }

    /** 결과·카운트다운을 지우고 지금 손가락들로 다시 기다리기 */
    fun reset(now: Long) {
        winner = -1
        allLifted = false
        team.fill(-1)
        participant.fill(false)
        changedAt = now
        phase = if (count >= MIN_FINGERS) Phase.WAITING else Phase.IDLE
    }

    /** 손가락 올림 → 칸 번호 (무시되면 -1) */
    fun down(id: Int, now: Long): Int {
        if (slotOf(id) >= 0) return -1
        if (phase == Phase.RESULT && allLifted) reset(now)
        // 결과 화면에서는 참가자 칸(자리에 남아 그려짐)을 비워 둔다
        var slot = -1
        for (k in slotId.indices) if (slotId[k] < 0 && !participant[k]) { slot = k; break }
        if (slot < 0) return -1 // 10개 초과
        slotId[slot] = id
        if (phase != Phase.RESULT) changed(now)
        return slot
    }

    /** 손가락 뗌 → 칸 번호 (모르는 손가락이면 -1) */
    fun up(id: Int, now: Long): Int {
        val slot = slotOf(id)
        if (slot < 0) return -1
        slotId[slot] = -1
        if (phase == Phase.RESULT) {
            if (count == 0) allLifted = true
        } else {
            changed(now)
        }
        return slot
    }

    /** 화면을 벗어남(일시정지 등): 모든 손가락을 뗀 것으로 */
    fun cancelAll(now: Long) {
        for (k in slotId.indices) if (slotId[k] >= 0) up(slotId[k], now)
    }

    private fun changed(now: Long) {
        changedAt = now
        phase = if (count >= MIN_FINGERS) Phase.WAITING else Phase.IDLE
    }

    /** 시간 진행. 단계가 바뀌면 true */
    fun update(now: Long): Boolean {
        when (phase) {
            Phase.WAITING -> if (now - changedAt >= STABLE_MS) {
                phase = Phase.COUNTDOWN
                return true
            }
            Phase.COUNTDOWN -> if (now - changedAt >= STABLE_MS + COUNT_FROM * COUNT_STEP_MS) {
                decide()
                phase = Phase.RESULT
                return true
            }
            else -> {}
        }
        return false
    }

    /** 카운트다운 숫자 (3·2·1, 카운트다운이 아니면 0) */
    fun countNumber(now: Long): Int {
        if (phase != Phase.COUNTDOWN) return 0
        val e = now - changedAt - STABLE_MS
        return (COUNT_FROM - (e / COUNT_STEP_MS).toInt()).coerceIn(1, COUNT_FROM)
    }

    /** 기다림 진행 0..1 (WAITING 동안) */
    fun waitFraction(now: Long): Float =
        if (phase != Phase.WAITING) 0f else ((now - changedAt).toFloat() / STABLE_MS).coerceIn(0f, 1f)

    private fun decide() {
        val slots = ArrayList<Int>()
        for (k in slotId.indices) if (slotId[k] >= 0) { slots.add(k); participant[k] = true }
        if (mode == Mode.ONE) {
            winner = slots[random.nextInt(slots.size)]
        } else {
            slots.shuffle(random)
            // 홀수면 어느 팀이 한 명 더 많을지도 무작위
            val first = if (slots.size % 2 == 0 || random.nextBoolean()) slots.size / 2 else slots.size / 2 + 1
            for ((k, s) in slots.withIndex()) team[s] = if (k < first) 0 else 1
        }
    }

    companion object {
        const val MAX_FINGERS = 10
        const val MIN_FINGERS = 2
        /** 손가락 수가 이만큼 그대로면 카운트다운 (규칙 시간: 속도 배율 적용 안 함) */
        const val STABLE_MS = 2000L
        const val COUNT_STEP_MS = 1000L
        const val COUNT_FROM = 3
    }
}
