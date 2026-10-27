package com.siwoo.party.games.marble.logic

object Money {
    /** 천 단위 콤마: 1234567 → "1,234,567", -5000 → "-5,000" */
    fun comma(v: Int): String {
        val neg = v < 0
        val s = (if (neg) -v.toLong() else v.toLong()).toString()
        val sb = StringBuilder(s.length + s.length / 3 + 1)
        if (neg) sb.append('-')
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(s[i])
        }
        return sb.toString()
    }

    /** "1,234,567원" */
    fun won(v: Int): String = comma(v) + "원"

    /** 부호를 붙인 금액: "+200,000원" / "-200,000원" */
    fun signed(v: Int): String = (if (v > 0) "+" else "") + won(v)
}
