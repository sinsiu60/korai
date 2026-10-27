package com.siwoo.party.common.lane

import kotlin.math.abs

/** 판정 결과 */
enum class Judgement {
    /** 근처에 물체가 없는 헛손질 (점수·콤보에 영향 없음 — 게임이 정하기 나름) */
    NONE,
    PERFECT,
    GOOD,
    MISS,
}

/** 물체 상태 (LaneTrack.state) */
object ItemState {
    /** 아직 판정 전 */
    const val PENDING = 0
    const val PERFECT = 1
    const val GOOD = 2
    /** 리듬: 놓침(판정선을 지나감 또는 너무 이르게/늦게 침) */
    const val MISS = 3
    /** 받기: 바구니로 받음 */
    const val CAUGHT = 4
    /** 받기: 바구니 밖으로 떨어짐 (폭탄이면 잘 피한 것) */
    const val DROPPED = 5

    /** 처리 끝(맞춤·받음)이라 더 그리지 않아도 되는 상태 */
    fun isTaken(s: Int) = s == PERFECT || s == GOOD || s == CAUGHT
}

/** 판정 창·점수 설정 */
class LaneJudgeConfig(
    /** |어긋난 시간| ≤ perfectMs → PERFECT */
    var perfectMs: Long = 60L,
    /** ≤ goodMs → GOOD */
    var goodMs: Long = 140L,
    /** ≤ missMs → MISS (너무 이르거나 늦게 친 것, 물체 소모). 이보다 멀면 NONE (헛손질) */
    var missMs: Long = 220L,
    var perfectScore: Int = 3,
    var goodScore: Int = 1,
    var missScore: Int = 0,
    /** 받기 게임: 종류별 받았을 때 점수 (예: [1, 3, -3] = 계란·황금·폭탄). 비면 모두 1 */
    var catchScores: IntArray = intArrayOf(1),
    /** 받기 게임: 물체 반 폭 (레인 폭 배수, 레인 하나 = 1.0) */
    var itemHalfWidth: Float = 0.35f,
    /** 판정선을 지난 뒤에도 이 시간 동안 그리기 범위에 넣는다 (놓친 물체가 떨어져 나가는 모습) */
    var afterMs: Long = 400L,
)

/**
 * 구역 하나의 레인 진행 상태: 공유 LanePattern + 자기 맞춤/놓침 상태·점수·콤보.
 * 리듬: judge(now, lane) 로 치고, update(now) 로 지나간 물체를 MISS 처리.
 * 받기: updateCatch(now, basketX, basketHalfWidth) 로 판정선에 닿은 물체를 받음/떨어짐 처리.
 * 상태 배열은 만들 때 한 번만 할당 (이후 할당 없음).
 */
class LaneTrack(
    val pattern: LanePattern,
    val judgeConfig: LaneJudgeConfig = LaneJudgeConfig(),
    /** 구역(플레이어) 번호 — 알림에 함께 넘긴다 */
    val zone: Int = 0,
) {
    /** 판정이 일어날 때 알림 (update·updateCatch 에서 자동 처리된 것 포함) */
    fun interface Listener {
        fun onResolved(track: LaneTrack, index: Int, state: Int)
    }

    private val states = IntArray(pattern.count)
    /** 이 번호 앞의 물체는 모두 판정 끝 */
    private var head = 0

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var perfects = 0
        private set
    var goods = 0
        private set
    var misses = 0
        private set
    var caught = 0
        private set
    var dropped = 0
        private set
    /** 마지막 판정 (화면 글자용) · 그 물체 번호 · 그때 시간 · 어긋난 ms(+ = 늦음) */
    var lastJudgement = Judgement.NONE
        private set
    var lastIndex = -1
        private set
    var lastJudgeTime = 0L
        private set
    var lastOffsetMs = 0L
        private set

    var listener: Listener? = null

    fun state(i: Int): Int = states[i]

    /** 다시 시작 (같은 패턴) */
    fun reset() {
        states.fill(ItemState.PENDING)
        head = 0
        score = 0; combo = 0; maxCombo = 0; perfects = 0; goods = 0; misses = 0; caught = 0; dropped = 0
        lastJudgement = Judgement.NONE; lastIndex = -1; lastJudgeTime = 0L; lastOffsetMs = 0L
    }

    /** 모든 물체 판정이 끝났는지 */
    val finished: Boolean get() = head >= pattern.count

    /** 판정 남은 물체 중 가장 앞 번호 */
    val nextIndex: Int get() = head

    // ---------------- 리듬 ----------------

    /**
     * nowMs 에 쳤다. lane < 0 이면 레인 무시(레인 1개 게임).
     * 아직 판정 안 된 물체 중 그 레인에서 시간이 가장 가까운 것을 골라 PERFECT/GOOD/MISS, 없으면 NONE.
     * 너무 이르게(goodMs < 이른 정도 ≤ missMs) 치면 그 물체는 MISS 로 소모된다.
     * 판정선을 goodMs 넘게 지난 물체는 이미 update 에서 MISS 이므로 늦은 헛손질은 NONE.
     */
    fun judge(nowMs: Long, lane: Int = -1): Judgement {
        update(nowMs)
        val from = pattern.firstAtOrAfter(nowMs - judgeConfig.missMs).coerceAtLeast(head)
        var best = -1
        var bestDt = Long.MAX_VALUE
        var i = from
        while (i < pattern.count) {
            val dt = nowMs - pattern.time(i)
            if (-dt > judgeConfig.missMs) break
            if (states[i] == ItemState.PENDING && (lane < 0 || pattern.lane(i) == lane) && abs(dt) < bestDt) {
                best = i; bestDt = abs(dt)
            }
            i++
        }
        if (best < 0) return Judgement.NONE
        val j = judgeOffset(nowMs - pattern.time(best), judgeConfig)
        apply(best, if (j == Judgement.PERFECT) ItemState.PERFECT else if (j == Judgement.GOOD) ItemState.GOOD else ItemState.MISS, nowMs)
        return j
    }

    /** 판정선을 goodMs 넘게 지나간 물체를 MISS 처리 (매 프레임 불러도 됨) */
    fun update(nowMs: Long) {
        while (head < pattern.count && states[head] != ItemState.PENDING) head++
        var i = head
        while (i < pattern.count && nowMs - pattern.time(i) > judgeConfig.goodMs) {
            if (states[i] == ItemState.PENDING) apply(i, ItemState.MISS, nowMs)
            i++
        }
        while (head < pattern.count && states[head] != ItemState.PENDING) head++
    }

    // ---------------- 받기 ----------------

    /**
     * 판정선(받는 선)에 닿은 물체를 처리: 바구니(가운데 basketX, 반 폭 basketHalfWidth — 둘 다 레인 단위,
     * 레인 k 의 가운데 = k + 0.5) 와 겹치면 CAUGHT(종류 점수), 아니면 DROPPED. 매 프레임 부른다.
     */
    fun updateCatch(nowMs: Long, basketX: Float, basketHalfWidth: Float) {
        while (head < pattern.count && pattern.time(head) <= nowMs) {
            if (states[head] == ItemState.PENDING) {
                val hit = catchOverlap(pattern.lane(head), basketX, basketHalfWidth, judgeConfig.itemHalfWidth)
                apply(head, if (hit) ItemState.CAUGHT else ItemState.DROPPED, nowMs)
            }
            head++
        }
    }

    /** 받기 게임 점수표에서 종류 점수 */
    fun catchScore(kind: Int): Int {
        val s = judgeConfig.catchScores
        return if (s.isEmpty()) 1 else s[kind.coerceIn(0, s.size - 1)]
    }

    private fun apply(i: Int, st: Int, nowMs: Long) {
        states[i] = st
        lastIndex = i
        lastJudgeTime = nowMs
        lastOffsetMs = nowMs - pattern.time(i)
        when (st) {
            ItemState.PERFECT -> { perfects++; score += judgeConfig.perfectScore; bump(); lastJudgement = Judgement.PERFECT }
            ItemState.GOOD -> { goods++; score += judgeConfig.goodScore; bump(); lastJudgement = Judgement.GOOD }
            ItemState.MISS -> { misses++; score += judgeConfig.missScore; combo = 0; lastJudgement = Judgement.MISS }
            ItemState.CAUGHT -> {
                caught++
                val s = catchScore(pattern.kind(i))
                score += s
                if (s >= 0) bump() else combo = 0
                lastJudgement = Judgement.NONE
            }
            ItemState.DROPPED -> {
                dropped++
                if (catchScore(pattern.kind(i)) > 0) combo = 0 // 좋은 물체를 놓치면 콤보 끊김 (폭탄은 피한 것)
                lastJudgement = Judgement.NONE
            }
        }
        listener?.onResolved(this, i, st)
    }

    private fun bump() {
        combo++
        if (combo > maxCombo) maxCombo = combo
    }

    // ---------------- 그리기 범위 ----------------

    /** 그릴 물체 범위 시작 (판정선을 afterMs 넘게 지난 것은 제외) */
    fun drawFrom(nowMs: Long): Int = pattern.firstAtOrAfter(nowMs - judgeConfig.afterMs)

    /** 그릴 물체 범위 끝 (미포함) — 이미 나타난 물체까지 */
    fun drawTo(nowMs: Long): Int = pattern.spawnedCount(nowMs)

    /** 위치 0..1 (0 = 맨 위, 1 = 판정선, 넘으면 >1) */
    fun position(i: Int, nowMs: Long): Float = pattern.position(i, nowMs)

    companion object {
        /** 어긋난 시간 dtMs(+ 늦음, - 이름) → 판정. |dt| > missMs 이면 NONE */
        fun judgeOffset(dtMs: Long, c: LaneJudgeConfig): Judgement {
            val a = abs(dtMs)
            return when {
                a <= c.perfectMs -> Judgement.PERFECT
                a <= c.goodMs -> Judgement.GOOD
                a <= c.missMs -> Judgement.MISS
                else -> Judgement.NONE
            }
        }

        /**
         * 받기 겹침: 레인 itemLane 의 물체(가운데 itemLane + 0.5, 반 폭 itemHalf) 와
         * 바구니(가운데 basketX, 반 폭 basketHalf) 가 가로로 겹치는지. 모두 레인 단위.
         */
        fun catchOverlap(itemLane: Int, basketX: Float, basketHalf: Float, itemHalf: Float): Boolean =
            abs(itemLane + 0.5f - basketX) <= basketHalf + itemHalf
    }
}
