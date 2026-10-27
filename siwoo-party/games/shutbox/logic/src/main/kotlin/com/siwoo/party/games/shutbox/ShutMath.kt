package com.siwoo.party.games.shutbox

/**
 * 숫자판 조합 계산 (비트 마스크: 숫자 n 은 비트 1 shl n, n = 1..9).
 * 열린 숫자가 많아야 9개라 부분집합(최대 512개)을 다 훑어도 충분히 빠르다.
 */
object ShutMath {
    /** 숫자 1~9 가 모두 열린 상자 */
    val FULL: Int = run {
        var m = 0
        for (n in 1..GameConfig.TILES) m = m or bit(n)
        m
    }

    fun bit(n: Int): Int = 1 shl n

    fun has(mask: Int, n: Int): Boolean = mask and bit(n) != 0

    /** 마스크에 든 숫자들의 합 */
    fun sumOf(mask: Int): Int {
        var s = 0
        for (n in 1..GameConfig.TILES) if (has(mask, n)) s += n
        return s
    }

    /** 마스크에 든 숫자 개수 */
    fun countOf(mask: Int): Int = Integer.bitCount(mask and FULL)

    /** 열린 숫자(open) 중 몇 개를 골라 합이 target 이 되는 조합이 하나라도 있는지 */
    fun hasCombination(open: Int, target: Int): Boolean {
        if (target <= 0) return false
        val o = open and FULL
        var sub = o
        while (sub != 0) {
            if (sumOf(sub) == target) return true
            sub = (sub - 1) and o
        }
        return false
    }

    /**
     * 강조할 숫자: 합이 target 인 조합 중 적어도 하나에 들어가는 열린 숫자들 (마스크).
     * 조합이 없으면 0.
     */
    fun highlightMask(open: Int, target: Int): Int {
        if (target <= 0) return 0
        val o = open and FULL
        var union = 0
        var sub = o
        while (sub != 0) {
            if (sumOf(sub) == target) union = union or sub
            sub = (sub - 1) and o
        }
        return union
    }

    /** 고른 숫자(selected)가 모두 열려 있고, 하나 이상이며, 합이 target 과 같으면 닫을 수 있다 */
    fun isValidSelection(open: Int, selected: Int, target: Int): Boolean =
        selected != 0 && (selected and FULL) == selected && (selected and open) == selected && sumOf(selected) == target

    /**
     * 이미 고른 숫자(selected)에 더 골라서 target 을 맞출 수 있는 숫자들 (화면 강조용).
     * = 고르지 않은 열린 숫자 중 (target - 고른 합) 을 만드는 조합에 들어가는 것들.
     * 고른 합이 이미 target 이면 0 (더 고를 것 없음).
     */
    fun extendMask(open: Int, selected: Int, target: Int): Int {
        if ((selected and open) != selected) return 0
        val rest = target - sumOf(selected)
        if (rest <= 0) return 0
        return highlightMask(open and selected.inv(), rest)
    }

    /** 합이 target 인 조합 하나 (테스트·자동 진행용, 큰 숫자부터 욕심내어 찾음). 없으면 0 */
    fun anyCombination(open: Int, target: Int): Int {
        val o = open and FULL
        var best = 0
        var sub = o
        while (sub != 0) {
            if (sumOf(sub) == target && (best == 0 || sub > best)) best = sub
            sub = (sub - 1) and o
        }
        return best
    }
}
