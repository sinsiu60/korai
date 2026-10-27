package com.siwoo.party.games.matches

/** 성냥 하나를 눌렀을 때 결과 */
enum class Pick {
    /** 새로 고름 */
    SELECTED,
    /** 다시 눌러서 고르기 취소 */
    DESELECTED,
    /** 이미 최대 개수만큼 골라서 더 못 고름 (화면은 고개 젓기) */
    FULL,
    /** 없는 자리 (이미 가져간 성냥·범위 밖) */
    ABSENT,
}

/**
 * 판 위 성냥 자리 (화면용 상태, 순수 Kotlin).
 * 어떤 자리에 성냥이 남았는지와 지금 고른 자리를 기억한다. 규칙 판정(개수)은 MatchesGame 이 한다.
 */
class MatchPile(val size: Int = GameConfig.MATCHES) {

    private val present = BooleanArray(size) { true }
    private val selected = BooleanArray(size)

    /** 고른 순서 (가져갈 때 이 순서로 날아간다) */
    private val order = IntArray(GameConfig.MAX_TAKE)

    var selectedCount = 0
        private set

    val presentCount: Int get() = present.count { it }

    fun isPresent(i: Int): Boolean = i in 0 until size && present[i]
    fun isSelected(i: Int): Boolean = i in 0 until size && selected[i]

    /** i 번 자리를 누름: 고르기/취소. limit = 이번에 고를 수 있는 최대 개수 */
    fun toggle(i: Int, limit: Int): Pick {
        if (!isPresent(i)) return Pick.ABSENT
        if (selected[i]) {
            selected[i] = false
            val k = indexInOrder(i)
            for (j in k until selectedCount - 1) order[j] = order[j + 1]
            selectedCount--
            return Pick.DESELECTED
        }
        if (selectedCount >= minOf(limit, GameConfig.MAX_TAKE)) return Pick.FULL
        selected[i] = true
        order[selectedCount++] = i
        return Pick.SELECTED
    }

    private fun indexInOrder(i: Int): Int {
        for (k in 0 until selectedCount) if (order[k] == i) return k
        return selectedCount
    }

    /** 고른 순서대로 k 번째 자리 */
    fun selectedAt(k: Int): Int = order[k]

    /** 고른 것 모두 취소 */
    fun clearSelection() {
        selected.fill(false)
        selectedCount = 0
    }

    /** 고른 성냥을 판에서 빼고 그 자리들을 고른 순서대로 돌려준다 */
    fun takeSelected(): IntArray {
        val out = IntArray(selectedCount) { order[it] }
        for (i in out) present[i] = false
        clearSelection()
        return out
    }

    /** 새 라운드: 모든 자리에 다시 성냥 */
    fun reset() {
        present.fill(true)
        clearSelection()
    }
}
