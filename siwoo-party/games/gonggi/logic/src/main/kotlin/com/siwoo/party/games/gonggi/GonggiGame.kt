package com.siwoo.party.games.gonggi

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/**
 * 진행 단계.
 * WAIT(차례 안내) → [READY(던질 돌을 손에 듦, 터치로 던짐) → AIR(공중: 바닥 돌 집기 + 받기) → CAUGHT(받음)] × 단계의 던지기 수
 * → … 4단 → KK_READY(손바닥에 5개) → KK_AIR(공중, 손등으로 받기) → KK_BACK(손등에 얹힘, 터치로 튕김) → KK_FLIP(공중, 낚아채기)
 * → TURN_END(실패 또는 꺾기 끝 — 차례 넘김) → 다음 사람 WAIT … → OVER
 */
enum class Phase { WAIT, READY, AIR, CAUGHT, KK_READY, KK_AIR, KK_BACK, KK_FLIP, TURN_END, OVER }

/** 실패 이유 (NONE = 실패 아님) */
enum class Miss {
    NONE,
    /** 필요한 것보다 많이 집음 */
    TOO_MANY,
    /** 덜 집고 받음 */
    TOO_FEW,
    /** 받기 구간 전에 받으려 함 */
    EARLY,
    /** 받기 구간이 지나 떨어뜨림 (늦은 터치 포함) */
    LATE,
    /** 꺾기: 손등에 하나도 못 얹음 */
    BACK_ZERO,
    /** 꺾기: 낚아채기 너무 일찍 */
    GRAB_EARLY,
    /** 꺾기: 낚아채기 놓침 */
    GRAB_LATE,
}

/** 공깃돌 자리 */
enum class StoneState {
    /** 돗자리 위 (집을 수 있음 / 떨어진 돌) */
    FLOOR,
    /** 던질 돌 (손 또는 공중) / 꺾기 때 손바닥 */
    HAND,
    /** 이번 던지기에서 집은 돌 (손바닥 안) */
    PICKED,
    /** 이번 단계에서 이미 집어 옆에 둔 돌 */
    ASIDE,
    /** 꺾기: 손등 위 */
    BACK,
}

/** 공깃돌 하나 (돗자리 좌표) */
class Stone {
    var x = 0f
    var y = 0f
    var state = StoneState.FLOOR
}

/**
 * 공기놀이 규칙 (순수 Kotlin). 시간(ms)은 밖에서 넣는다 (일시정지 시간이 빠진 게임 시계). 돌 흩기는 주입한 Random.
 *
 *  - 단계: 1단(하나씩 ×4) → 2단(둘씩 ×2) → 3단(셋 → 하나, 순서 고정) → 4단(넷 한꺼번에) → 꺾기.
 *    1~4단을 시작할 때마다 돌 4개를 돗자리에 새로 흩고 1개(던질 돌)를 손에 든다
 *    (차례가 바뀌면 안내 배너 동안 보이도록 nextTurn 에서 미리 흩어 둔다).
 *  - 던지기: 터치 → 던질 돌이 [GameConfig.AIR_MS] 동안 공중. 그동안 바닥 돌을 필요한 수만큼 터치해 집고,
 *    받기 구간 [착지 − CATCH_EARLY, 착지 + CATCH_LATE] 안에 돌이 아닌 곳(손)을 터치해 받는다.
 *    더 집으면(TOO_MANY) / 덜 집고 받으면(TOO_FEW) / 구간 전에 받으면(EARLY) / 구간이 지나면(LATE) 실패.
 *  - 실패: 차례가 넘어가고, 그 사람은 다음 차례에 "실패한 단계의 처음부터" 다시 (돌도 새로 흩음).
 *  - 꺾기: 손바닥의 5개를 던짐 → 손등 받기 터치의 정확도만큼 손등에 남음([GameConfig.KK_BACK_THRESHOLDS], 0개면 실패)
 *    → 터치로 튕겨 올림 → 낚아채기 구간 안에 터치하면 손등에 있던 수만큼 점수, 못 하면 0점 = 실패.
 *    꺾기 실패는 다른 실패와 같이 다음 차례에 꺾기부터 다시. 점수를 얻으면 차례가 끝나고 다음 차례는 1단부터.
 *  - 목표 점수 이상이 되면 완주 (완주 순서 = 순위). 남은 사람이 1명 이하가 되거나 [GameConfig.MAX_ROUNDS] 바퀴가 지나면 끝.
 *    남은 사람은 점수 → 단계 진행 순 (같으면 같은 순위).
 *  - 차례: 완주한 사람은 건너뛴다.
 */
class GonggiGame(
    val players: Int,
    val target: Int = GameConfig.DEFAULT_TARGET,
    private val random: Random = Random.Default,
) {


    var phase = Phase.WAIT
        private set

    /** 지금 차례 */
    var current = 0
        private set

    /** 바퀴 수 (차례가 앞 번호로 돌아올 때마다 +1) */
    var round = 0
        private set

    /** 사람마다 지금 단계 (0..3 = 1단~4단, 4 = 꺾기) — 실패하면 이 단계 처음부터 다시 */
    val stage = IntArray(players)
    val scores = IntArray(players)
    /** 완주 순서 (1부터, 0 = 아직) */
    val place = IntArray(players)
    var finishedCount = 0
        private set
    /** 사람마다 한 차례 수 */
    val turns = IntArray(players)

    /** 지금 단계 안의 던지기 번호 */
    var subStep = 0
        private set

    val stones = Array(GameConfig.STONES) { Stone() }

    /** 돌을 미리 놓아 두었는지 (차례 안내 중에도 그 사람의 판이 보이도록 — startTurn 이 그대로 씀) */
    private var prepared = false

    init {
        require(players in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
        require(target >= 1)
        prepareStones()
    }

    /** 이번 던지기에서 집은 수 */
    var picked = 0
        private set

    // ── 던지기 시각 ──
    var tossAt = 0L
        private set
    var airMs = 0L
        private set
    val landAt: Long get() = tossAt + airMs
    val catchStart: Long get() = landAt - GameConfig.CATCH_EARLY_MS
    val catchEnd: Long get() = landAt + GameConfig.CATCH_LATE_MS

    // ── 꺾기 시각·결과 ──
    var kkTossAt = 0L
        private set
    val kkLandAt: Long get() = kkTossAt + GameConfig.KK_AIR_MS
    var kkBackAt = 0L
        private set
    /** 손등에 얹힌 수 */
    var backCount = 0
        private set
    var kkFlipAt = 0L
        private set
    val kkGrabAt: Long get() = kkFlipAt + GameConfig.KK_FLIP_AIR_MS

    /** 마지막 실패 이유 (이번 차례) */
    var miss = Miss.NONE
        private set
    /** 마지막 꺾기 점수 (이번 차례, 없으면 0) */
    var lastPoints = 0
        private set
    /** 이번 차례에 목표를 넘어 완주했는지 */
    var justFinished = false
        private set
    /** 마지막 받기·손등 받기의 시각 오차 (터치 − 기준, ms) */
    var lastErrMs = 0L
        private set

    val finished: Boolean get() = phase == Phase.OVER
    fun isDone(p: Int): Boolean = place[p] > 0
    val remaining: Int get() = players - finishedCount

    /** 지금 단계 (지금 사람) */
    val curStage: Int get() = stage[current]
    val inKkeokgi: Boolean get() = stage[current] == GameConfig.STAGE_KKEOKGI

    /** 이번 던지기에서 집어야 할 수 (꺾기면 0) */
    val required: Int get() = requiredOf(stage[current], subStep)

    /** 이번 단계의 던지기 수 */
    val stepsInStage: Int get() = stepsOf(stage[current])

    /** 공중에 떠 있는 동안 (터치를 받는 동안) */
    val airborne: Boolean get() = phase == Phase.AIR || phase == Phase.KK_AIR || phase == Phase.KK_FLIP

    // ── 차례 ──

    /** 차례 안내가 끝나면: 단계 준비 (1~4단은 돌을 새로 흩음, 꺾기는 5개를 손바닥에) */
    fun startTurn(): Boolean {
        if (phase != Phase.WAIT) return false
        miss = Miss.NONE
        lastPoints = 0
        justFinished = false
        subStep = 0
        turns[current]++
        setupStage()
        return true
    }

    /** 지금 사람의 단계에 맞게 돌 놓기: 1~4단은 새로 흩고, 꺾기는 5개 모두 손바닥에 */
    private fun prepareStones() {
        if (inKkeokgi) {
            for (s in stones) s.state = StoneState.HAND
        } else {
            scatter(random, stones)
        }
        prepared = true
    }

    private fun setupStage() {
        if (!prepared) prepareStones()
        prepared = false
        picked = 0
        phase = if (inKkeokgi) Phase.KK_READY else Phase.READY
    }

    /**
     * 터치 하나. stone = 터치한 바닥 돌 번호 (돌이 아니면 −1).
     * READY 던지기 / AIR 돌 집기(stone ≥ 0)·받기(−1) / 꺾기 단계마다 던지기·손등 받기·튕기기·낚아채기.
     * 반응했으면 true.
     */
    fun tap(nowMs: Long, stone: Int = -1): Boolean = when (phase) {
        Phase.READY -> toss(nowMs)
        Phase.AIR -> if (stone >= 0) pick(stone, nowMs) else catchTap(nowMs)
        Phase.KK_READY -> kkToss(nowMs)
        Phase.KK_AIR -> kkBack(nowMs)
        Phase.KK_BACK -> kkFlip(nowMs)
        Phase.KK_FLIP -> kkGrab(nowMs)
        else -> false
    }

    private fun toss(nowMs: Long): Boolean {
        tossAt = nowMs
        airMs = GameConfig.AIR_MS[stage[current]]
        picked = 0
        phase = Phase.AIR
        return true
    }

    /** 바닥 돌 집기 (공중일 때) */
    private fun pick(i: Int, nowMs: Long): Boolean {
        if (nowMs > catchEnd) { fail(Miss.LATE); return true }
        if (i !in stones.indices || stones[i].state != StoneState.FLOOR) return false
        if (picked >= required) { fail(Miss.TOO_MANY); return true }
        stones[i].state = StoneState.PICKED
        picked++
        return true
    }

    /** 받기 (공중일 때 돌이 아닌 곳 터치) */
    private fun catchTap(nowMs: Long): Boolean {
        lastErrMs = nowMs - landAt
        when {
            nowMs < catchStart -> fail(Miss.EARLY)
            nowMs > catchEnd -> fail(Miss.LATE)
            picked < required -> fail(Miss.TOO_FEW)
            else -> {
                for (s in stones) if (s.state == StoneState.PICKED) s.state = StoneState.ASIDE
                phase = Phase.CAUGHT
            }
        }
        return true
    }

    /**
     * 받은 뒤 다음으로: 같은 단계의 다음 던지기 / 다음 단계(돌 새로 흩기) / 꺾기.
     * 반환: 새 단계로 넘어갔으면 true.
     */
    fun advance(): Boolean {
        if (phase != Phase.CAUGHT) return false
        subStep++
        if (subStep < stepsInStage) {
            picked = 0
            stones[GameConfig.TOSS_STONE].state = StoneState.HAND
            phase = Phase.READY
            return false
        }
        stage[current]++
        subStep = 0
        setupStage()
        return true
    }

    private fun kkToss(nowMs: Long): Boolean {
        kkTossAt = nowMs
        backCount = 0
        phase = Phase.KK_AIR
        return true
    }

    /** 손등 받기: 정확도만큼 손등에 남는다 */
    private fun kkBack(nowMs: Long): Boolean {
        lastErrMs = nowMs - kkLandAt
        kkBackAt = nowMs
        backCount = backCountFor(lastErrMs)
        if (backCount == 0) {
            failKk(Miss.BACK_ZERO)
            return true
        }
        for (i in stones.indices) {
            if (i < backCount) stones[i].state = StoneState.BACK else drop(i, i)
        }
        phase = Phase.KK_BACK
        return true
    }

    private fun kkFlip(nowMs: Long): Boolean {
        kkFlipAt = nowMs
        phase = Phase.KK_FLIP
        return true
    }

    private fun kkGrab(nowMs: Long): Boolean {
        lastErrMs = nowMs - kkGrabAt
        when {
            nowMs < kkGrabAt - GameConfig.KK_GRAB_EARLY_MS -> failKk(Miss.GRAB_EARLY)
            nowMs > kkGrabAt + GameConfig.KK_GRAB_LATE_MS -> failKk(Miss.GRAB_LATE)
            else -> {
                val p = current
                lastPoints = backCount
                scores[p] += backCount
                for (s in stones) if (s.state == StoneState.BACK) s.state = StoneState.HAND
                stage[p] = 0
                subStep = 0
                if (scores[p] >= target && place[p] == 0) {
                    finishedCount++
                    place[p] = finishedCount
                    justFinished = true
                }
                phase = Phase.TURN_END
            }
        }
        return true
    }

    /** 공중에 있는 동안 시간이 지나면 실패 처리 (프레임마다 부름). 실패했으면 true */
    fun update(nowMs: Long): Boolean {
        when (phase) {
            Phase.AIR -> if (nowMs > catchEnd) { lastErrMs = nowMs - landAt; fail(Miss.LATE); return true }
            Phase.KK_AIR -> if (nowMs > kkLandAt + GameConfig.KK_BACK_THRESHOLDS[0]) {
                lastErrMs = nowMs - kkLandAt
                kkBackAt = nowMs
                backCount = 0
                failKk(Miss.BACK_ZERO)
                return true
            }
            Phase.KK_FLIP -> if (nowMs > kkGrabAt + GameConfig.KK_GRAB_LATE_MS) { lastErrMs = nowMs - kkGrabAt; failKk(Miss.GRAB_LATE); return true }
            else -> {}
        }
        return false
    }

    /** 1~4단 실패: 던질 돌과 이번에 집은 돌이 떨어지고 차례 끝 (단계는 그대로 → 다음엔 그 단계 처음부터) */
    private fun fail(m: Miss) {
        miss = m
        drop(GameConfig.TOSS_STONE, 0)
        var k = 1
        for (i in stones.indices) if (stones[i].state == StoneState.PICKED) drop(i, k++)
        subStep = 0
        phase = Phase.TURN_END
    }

    /** 꺾기 실패: 손에 있던 돌이 모두 떨어짐, 다음엔 꺾기부터 */
    private fun failKk(m: Miss) {
        miss = m
        for (i in stones.indices) if (stones[i].state != StoneState.FLOOR) drop(i, i)
        lastPoints = 0
        phase = Phase.TURN_END
    }

    /** 돌 i 를 손 근처 slot 자리 바닥에 떨굼 */
    private fun drop(i: Int, slot: Int) {
        val s = stones[i]
        s.state = StoneState.FLOOR
        s.x = GameConfig.HAND_X + DROP_DX[slot % DROP_DX.size]
        s.y = GameConfig.HAND_Y + DROP_DY[slot % DROP_DY.size]
    }

    /** 다음 차례로 (완주한 사람 건너뜀). 계속하면 true (WAIT), 끝났으면 false (OVER) */
    fun nextTurn(): Boolean {
        if (phase == Phase.OVER) return false
        if (phase != Phase.TURN_END) return true
        if (remaining <= 1) { phase = Phase.OVER; return false }
        val next = nextPlayer(current, place)
        if (next <= current) round++
        if (round >= GameConfig.MAX_ROUNDS) { phase = Phase.OVER; return false }
        current = next
        prepareStones()
        phase = Phase.WAIT
        return true
    }

    /** 최종 순위: 완주 순서 → 남은 사람은 점수 → 단계 순 (경쟁 순위) */
    fun ranks(): IntArray = ranksOf(place, scores, stage)

    companion object {
        /** 떨어진 돌 자리 (손 기준) */
        private val DROP_DX = floatArrayOf(0.2f, -0.24f, 0.3f, -0.34f, 0.08f)
        private val DROP_DY = floatArrayOf(-0.1f, -0.05f, 0.06f, 0.08f, -0.2f)

        fun stepsOf(stage: Int): Int = if (stage in 0 until GameConfig.STAGE_KKEOKGI) GameConfig.PICKS[stage].size else 1

        /** 단계·던지기 번호 → 집을 수 (꺾기 0) */
        fun requiredOf(stage: Int, subStep: Int): Int =
            if (stage in 0 until GameConfig.STAGE_KKEOKGI) GameConfig.PICKS[stage][subStep.coerceIn(0, GameConfig.PICKS[stage].size - 1)] else 0

        /** 꺾기 손등 받기: 오차(ms) → 손등에 남는 수 (0..5) */
        fun backCountFor(errMs: Long): Int {
            val e = if (errMs < 0) -errMs else errMs
            var n = 0
            for (t in GameConfig.KK_BACK_THRESHOLDS) if (e <= t) n++
            return n
        }

        /** from 다음의 완주 안 한 사람 (모두 완주면 from) */
        fun nextPlayer(from: Int, place: IntArray): Int {
            val n = place.size
            for (k in 1..n) {
                val p = (from + k) % n
                if (place[p] == 0) return p
            }
            return from
        }

        /** 완주 순서 → 남은 사람은 (점수, 단계) 큰 순 */
        fun ranksOf(place: IntArray, scores: IntArray, stages: IntArray): IntArray {
            val n = place.size
            val out = IntArray(n)
            var done = 0
            for (i in 0 until n) if (place[i] > 0) { out[i] = place[i]; done++ }
            val rest = (0 until n).filter { place[it] == 0 }
            val r = Ranking.competition(rest.map { scores[it].toLong() * 10L + stages[it] }, higherIsBetter = true)
            for (k in rest.indices) out[rest[k]] = done + r[k]
            return out
        }

        /**
         * 돌 흩기: 던질 돌(0번)은 손에, 나머지는 흩는 구역 안에 서로 MIN_GAP 이상 떨어지게 (씨앗 고정이면 늘 같은 자리).
         * 400번 안에 자리를 못 찾으면 정해진 격자 자리.
         */
        fun scatter(rnd: Random, stones: Array<Stone>) {
            val toss = stones[GameConfig.TOSS_STONE]
            toss.state = StoneState.HAND
            toss.x = GameConfig.HAND_X
            toss.y = GameConfig.HAND_Y
            var placed = 0
            for (i in stones.indices) {
                if (i == GameConfig.TOSS_STONE) continue
                val s = stones[i]
                s.state = StoneState.FLOOR
                var ok = false
                for (tries in 0 until 400) {
                    val x = GameConfig.SCATTER_LEFT + rnd.nextFloat() * (GameConfig.SCATTER_RIGHT - GameConfig.SCATTER_LEFT)
                    val y = GameConfig.SCATTER_TOP + rnd.nextFloat() * (GameConfig.SCATTER_BOTTOM - GameConfig.SCATTER_TOP)
                    if (farFromOthers(stones, i, x, y)) { s.x = x; s.y = y; ok = true; break }
                }
                if (!ok) {
                    s.x = GameConfig.SCATTER_LEFT + (GameConfig.SCATTER_RIGHT - GameConfig.SCATTER_LEFT) * (placed + 0.5f) / (GameConfig.STONES - 1)
                    s.y = if (placed % 2 == 0) GameConfig.SCATTER_TOP + 0.05f else GameConfig.SCATTER_BOTTOM - 0.05f
                }
                placed++
            }
        }

        private fun farFromOthers(stones: Array<Stone>, upTo: Int, x: Float, y: Float): Boolean {
            for (j in 0 until upTo) {
                if (j == GameConfig.TOSS_STONE) continue
                val dx = stones[j].x - x
                val dy = stones[j].y - y
                if (dx * dx + dy * dy < GameConfig.MIN_GAP * GameConfig.MIN_GAP) return false
            }
            return true
        }
    }
}
