package com.siwoo.party.games.bowling

/**
 * 한 사람의 정식 10핀 볼링 점수표 (프레임 수는 10 또는 5 등 자유 — 마지막 프레임이 정식 10프레임처럼 보너스 투구).
 * - 스트라이크(X) = 10 + 다음 두 투구, 스페어(/) = 10 + 다음 한 투구
 * - 마지막 프레임: 스트라이크나 스페어면 보너스 투구 (최대 3구), 아니면 2구로 끝
 * 투구는 순서대로 rolls 에 쌓이고, 프레임 점수는 그때그때 계산한다 (보너스가 아직이면 null).
 */
class ScoreCard(val frames: Int) {
    init {
        require(frames >= 1) { "frames >= 1" }
    }

    /** 최대 투구 수: 마지막 앞 프레임 2구씩 + 마지막 3구 */
    val maxRolls = 2 * (frames - 1) + 3
    private val rolls = IntArray(maxRolls)

    /** 지금까지 굴린 수 */
    var rollCount = 0
        private set
    /** 지금 프레임 (0부터, 다 끝나면 frames) */
    var frame = 0
        private set
    /** 지금 프레임의 몇 번째 투구인지 (0부터) */
    var rollInFrame = 0
        private set
    /** 다음 투구 전에 서 있는 핀 수 (10 이면 새로 세운 판) */
    var standing = PINS
        private set
    /** 다음 투구가 새로 세운 10개에 굴리는 것인지 (프레임 첫 구, 마지막 프레임의 스트라이크·스페어 뒤) */
    var freshRack = true
        private set

    val done: Boolean get() = frame >= frames
    val isLastFrame: Boolean get() = frame == frames - 1

    /** i 번째 투구에 쓰러뜨린 핀 수 */
    fun rollAt(i: Int): Int = rolls[i]

    /** 이번 투구로 pins 개를 쓰러뜨림 (0..standing). 프레임·투구 차례가 따라 넘어간다 */
    fun roll(pins: Int) {
        check(!done) { "이미 끝난 점수표" }
        require(pins in 0..standing) { "핀 수 $pins (서 있던 핀 $standing)" }
        rolls[rollCount++] = pins
        if (frame < frames - 1) {
            if (rollInFrame == 0 && pins < PINS) {
                rollInFrame = 1
                standing = PINS - pins
                freshRack = false
            } else {
                nextFrame()
            }
            return
        }
        // 마지막 프레임
        when (rollInFrame) {
            0 -> {
                rollInFrame = 1
                freshRack = pins == PINS
                standing = if (freshRack) PINS else PINS - pins
            }
            1 -> {
                val first = rolls[rollCount - 2]
                when {
                    first == PINS -> { // 첫 구 스트라이크 → 보너스 3구째
                        rollInFrame = 2
                        freshRack = pins == PINS
                        standing = if (freshRack) PINS else PINS - pins
                    }
                    first + pins == PINS -> { // 스페어 → 보너스 1구 (새로 세움)
                        rollInFrame = 2
                        freshRack = true
                        standing = PINS
                    }
                    else -> finish()
                }
            }
            else -> finish()
        }
    }

    private fun nextFrame() {
        frame++
        rollInFrame = 0
        standing = PINS
        freshRack = true
    }

    private fun finish() {
        frame = frames
        rollInFrame = 0
        standing = PINS
        freshRack = true
    }

    /** f 프레임의 첫 투구 번호 (아직 시작 안 했으면 rollCount 이상) */
    fun frameStart(f: Int): Int {
        var i = 0
        for (k in 0 until f) {
            if (i >= rollCount) return rollCount + (f - k) // 아직 오지 않은 프레임
            i += if (rolls[i] == PINS) 1 else 2
        }
        return i
    }

    /** f 프레임만의 점수 (보너스 포함). 아직 굴리지 않았거나 보너스 투구가 남았으면 null */
    fun frameScore(f: Int): Int? {
        val s = frameStart(f)
        if (s >= rollCount) return null
        if (f < frames - 1) {
            val r0 = rolls[s]
            if (r0 == PINS) return if (s + 2 < rollCount) PINS + rolls[s + 1] + rolls[s + 2] else null
            if (s + 1 >= rollCount) return null
            val sum = r0 + rolls[s + 1]
            if (sum == PINS) return if (s + 2 < rollCount) PINS + rolls[s + 2] else null
            return sum
        }
        // 마지막 프레임: 다 굴려야 확정
        if (!done) return null
        var sum = 0
        for (i in s until rollCount) sum += rolls[i]
        return sum
    }

    /** f 프레임까지의 누적 점수 (중간에 확정 안 된 프레임이 있으면 null) */
    fun cumulative(f: Int): Int? {
        var sum = 0
        for (k in 0..f) sum += frameScore(k) ?: return null
        return sum
    }

    /** 지금까지 확정된 점수 (앞에서부터 확정된 프레임의 합) */
    val total: Int
        get() {
            var sum = 0
            for (k in 0 until frames) sum += frameScore(k) ?: return sum
            return sum
        }

    /** 프레임 f 의 칸 수 (마지막 프레임 3칸, 나머지 2칸) */
    fun boxes(f: Int): Int = if (f == frames - 1) 3 else 2

    /**
     * 점수판 칸 글자: "X" 스트라이크, "/" 스페어, "-" 0개, "1".."9", 빈 칸 "".
     * 보통 프레임의 스트라이크는 종이 점수표처럼 오른쪽 칸에 X.
     */
    fun mark(f: Int, box: Int): String {
        val s = frameStart(f)
        if (s >= rollCount) return ""
        if (f < frames - 1) {
            val r0 = rolls[s]
            return when (box) {
                0 -> if (r0 == PINS) "" else sym(r0)
                1 -> when {
                    r0 == PINS -> STRIKE
                    s + 1 >= rollCount -> ""
                    r0 + rolls[s + 1] == PINS -> SPARE
                    else -> sym(rolls[s + 1])
                }
                else -> ""
            }
        }
        val i = s + box
        if (box > 2 || i >= rollCount) return ""
        val r = rolls[i]
        if (box == 0) return sym(r)
        val prev = rolls[i - 1]
        // 앞 투구 뒤에 핀을 새로 세웠는지 (앞이 스트라이크·스페어)
        val fresh = if (box == 1) prev == PINS else (rolls[s] == PINS && prev == PINS) || (rolls[s] != PINS && rolls[s] + prev == PINS)
        if (fresh) return sym(r)
        return if (prev + r == PINS) SPARE else sym(r)
    }

    companion object {
        const val PINS = GameConfig.PINS
        const val STRIKE = "X"
        const val SPARE = "/"
        const val ZERO = "-"
        private val DIGITS = arrayOf("-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "X")

        /** 핀 수 → 글자 (0 "-", 10 "X") */
        fun sym(n: Int): String = DIGITS[n.coerceIn(0, PINS)]
    }
}
