package com.siwoo.marble.logic

/** 금액 표시: 3_000_000 → "300만", 25_000 → "2만5천", 5_000 → "5천" */
object Money {
    fun short(won: Int): String {
        if (won == 0) return "0"
        val sign = if (won < 0) "-" else ""
        val v = if (won < 0) -won else won
        val man = v / 10_000
        val chun = (v % 10_000) / 1_000
        val sb = StringBuilder(sign)
        if (man > 0) sb.append(man).append('만')
        if (chun > 0) sb.append(chun).append('천')
        if (man == 0 && chun == 0) sb.append(v)
        return sb.toString()
    }

    fun won(won: Int): String = short(won) + "원"
}
