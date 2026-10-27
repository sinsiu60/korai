package com.siwoo.party.games.horserace

import kotlin.random.Random

/**
 * 배당 계산.
 * 받는 칩 = 건 칩 × 배당 (건 칩이 포함된 금액). 1착 ×3, 2착 ×1.5, 그 밖은 0 (건 칩을 잃음).
 * 금액은 늘 BET_UNIT(10)의 배수라 ×1.5 도 정확히 나누어떨어진다 (10 → 15, 30 → 45).
 * 혹시 홀수 금액이 들어오면 내림 (정수 나눗셈).
 */
object Payout {
    /** 착순별 배당 ×10 (1 → 30, 2 → 15, 그 밖 0) */
    fun multiplierX10(place: Int): Int = when (place) {
        1 -> GameConfig.WIN_PAYOUT_X10
        2 -> GameConfig.PLACE_PAYOUT_X10
        else -> 0
    }

    /** 한 베팅의 받는 칩 */
    fun of(stake: Int, place: Int): Int = stake * multiplierX10(place) / 10

    /** 말별 건 칩(bets[말]) 전체의 받는 칩 */
    fun total(bets: IntArray, first: Int, second: Int): Int {
        var sum = 0
        for (h in bets.indices) {
            val place = when (h) {
                first -> 1
                second -> 2
                else -> 0
            }
            sum += of(bets[h], place)
        }
        return sum
    }
}

/** 해설 종류 (중요한 순서: FIRST·SECOND > LAST_STRETCH > TAKES_LEAD > CATCHES_UP) */
enum class CommentKind { NONE, WASTED, CATCHES_UP, TAKES_LEAD, LAST_STRETCH, SECOND, FIRST }

/**
 * 주사위 한 번의 결과.
 * @param roll 주사위 눈 (1~6)
 * @param horse 말 번호 0~5 (= roll - 1)
 * @param moved 한 칸 나아갔는지 (이미 들어온 말이면 false)
 * @param position 그 말의 지금 칸 (0~TRACK)
 * @param place 이번에 들어왔으면 1·2, 아니면 0
 */
class RaceStep(
    val roll: Int,
    val horse: Int,
    val moved: Boolean,
    val position: Int,
    val place: Int,
    val comment: CommentKind,
    /** 해설 문구 (NONE 이면 "") */
    val commentText: String,
)

/**
 * 레이스 한 판. 주사위(난수 주입)를 굴려 나온 번호의 말이 1칸 전진, TRACK 칸에 먼저 닿은 말이 1착.
 * 2착 규칙: 1착이 들어와도 레이스는 계속되고, **두 번째로 결승에 닿은 말이 2착**이다.
 * (한 번에 한 말만 한 칸 움직이므로 동착은 없다. 이미 들어온 말 번호가 나오면 그 굴림은 헛굴림.)
 */
class Race(private val random: Random) {
    val positions = IntArray(GameConfig.HORSES)
    /** 들어온 순서 (말 번호) */
    val finishOrder = ArrayList<Int>(GameConfig.PLACES_PAID)
    /** 굴린 횟수 */
    var rolls = 0
        private set
    /** 결승 한 칸 앞 해설을 이미 한 말 */
    private val stretchCalled = BooleanArray(GameConfig.HORSES)

    val isOver: Boolean get() = finishOrder.size >= GameConfig.PLACES_PAID
    val first: Int get() = finishOrder.getOrElse(0) { -1 }
    val second: Int get() = finishOrder.getOrElse(1) { -1 }

    fun finished(horse: Int): Boolean = positions[horse] >= GameConfig.TRACK

    /** 말의 착순 (1·2, 아직이면 0) */
    fun placeOf(horse: Int): Int {
        val k = finishOrder.indexOf(horse)
        return if (k < 0) 0 else k + 1
    }

    /** 혼자 가장 앞선 말 (동률이거나 다 출발선이면 -1) */
    fun soleLeader(): Int {
        var best = -1
        var bestPos = 0
        var tie = false
        for (h in 0 until GameConfig.HORSES) {
            val p = positions[h]
            if (p > bestPos) { best = h; bestPos = p; tie = false } else if (p == bestPos && p > 0) tie = true
        }
        return if (tie) -1 else best
    }

    /** 가장 앞선 칸 */
    fun leadPosition(): Int = positions.maxOrNull() ?: 0

    /** 주사위 한 번 → 말 이동 + 해설 */
    fun step(): RaceStep {
        check(!isOver) { "끝난 레이스" }
        val horse = random.nextInt(GameConfig.HORSES)
        rolls++
        val label = (horse + 1).toString() + "번 말"
        if (finished(horse)) {
            return RaceStep(horse + 1, horse, false, positions[horse], 0, CommentKind.WASTED, label + "은 이미 들어왔어요")
        }
        val leaderBefore = soleLeader()
        val leadBefore = leadPosition()
        positions[horse]++
        val pos = positions[horse]
        var place = 0
        if (pos >= GameConfig.TRACK) {
            finishOrder.add(horse)
            place = finishOrder.size
        }
        var kind = CommentKind.NONE
        var text = ""
        when {
            place == 1 -> { kind = CommentKind.FIRST; text = label + " 1착!" }
            place == 2 -> { kind = CommentKind.SECOND; text = label + " 2착!" }
            pos == GameConfig.TRACK - 1 && !stretchCalled[horse] -> {
                stretchCalled[horse] = true
                kind = CommentKind.LAST_STRETCH
                text = label + " 결승 코앞!"
            }
            soleLeader() == horse && leaderBefore != horse -> {
                kind = CommentKind.TAKES_LEAD
                text = label + LEAD_LINES[rolls % LEAD_LINES.size]
            }
            pos == leadBefore && leaderBefore != horse && pos >= CATCH_UP_MIN -> {
                kind = CommentKind.CATCHES_UP
                text = label + " 따라붙습니다!"
            }
        }
        return RaceStep(horse + 1, horse, true, pos, place, kind, text)
    }

    /** 끝날 때까지 굴림 (테스트·시뮬레이션용) */
    fun runToEnd(maxRolls: Int = 10_000): Race {
        while (!isOver && rolls < maxRolls) step()
        return this
    }

    companion object {
        /** 선두 해설 (굴린 횟수로 돌아가며) */
        private val LEAD_LINES = arrayOf(" 치고 나갑니다!", " 선두로 나섭니다!", " 앞으로 쭉!")
        /** 이 칸 이상에서 선두와 나란해질 때만 "따라붙습니다" */
        private const val CATCH_UP_MIN = 3
    }
}
