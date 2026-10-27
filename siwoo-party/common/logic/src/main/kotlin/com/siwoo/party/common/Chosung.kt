package com.siwoo.party.common

/**
 * 한글 초성 뽑기 (유니코드 분해).
 * 완성형 한글 음절(U+AC00 '가' ~ U+D7A3 '힣')은 (초성 19 × 중성 21 × 종성 28) 순서로 놓여 있으므로
 * 초성 번호 = (코드 - 0xAC00) / (21 × 28). 결과는 호환용 자모(ㄱ U+3131 …)로 돌려준다 (화면 글꼴에 잘 나옴).
 * 한글 음절이 아닌 글자(공백·숫자·영문·기호·이미 자모인 글자)는 그대로 둔다.
 */
object Chosung {
    const val FIRST = 0xAC00
    const val LAST = 0xD7A3
    private const val PER_INITIAL = 21 * 28

    /** 초성 19개 (호환용 자모): ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ */
    val INITIALS: CharArray = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    fun isSyllable(c: Char): Boolean = c.code in FIRST..LAST

    /** 글자 하나의 초성 (한글 음절이 아니면 그대로) */
    fun initialOf(c: Char): Char = if (isSyllable(c)) INITIALS[(c.code - FIRST) / PER_INITIAL] else c

    /** 단어 전체 초성: "김치찌개" → "ㄱㅊㅉㄱ", "비행기 조종사" → "ㅂㅎㄱ ㅈㅈㅅ", "TV 2대" → "TV 2ㄷ" */
    fun extract(word: String): String {
        val sb = StringBuilder(word.length)
        for (c in word) sb.append(initialOf(c))
        return sb.toString()
    }

    /** 한글 음절 개수 */
    fun syllableCount(word: String): Int = word.count { isSyllable(it) }

    /** 초성으로 보여 주되 reveal[i] 인 글자는 원래 글자 그대로 (힌트) */
    fun masked(word: String, reveal: BooleanArray): String {
        val sb = StringBuilder(word.length)
        for ((i, c) in word.withIndex()) sb.append(if (reveal.getOrElse(i) { false }) c else initialOf(c))
        return sb.toString()
    }

    /** 호환용 자모 중 초성으로 쓰이는 글자인지 (ㄱ~ㅎ) */
    fun isInitial(c: Char): Boolean = INITIALS.indexOf(c) >= 0

    /**
     * 이름 검색: query 가 text 안 어딘가에 이어서 들어 있으면 true. 공백은 무시하고 영문은 대소문자 무시.
     * 각 글자는 같은 글자이거나, query 쪽이 초성(ㄱ~ㅎ)이면 text 쪽 글자의 초성과 같으면 맞음.
     * 예) "에어하키" ← "ㅇㅎㅋ", "ㅇㅇ하", "어하", "에어 하키" / 빈 query 는 항상 true
     */
    fun matches(text: String, query: String): Boolean {
        val t = compact(text)
        val q = compact(query)
        if (q.isEmpty()) return true
        if (q.length > t.length) return false
        for (start in 0..t.length - q.length) {
            var ok = true
            for (k in q.indices) {
                val qc = q[k]
                val tc = t[start + k]
                if (qc == tc) continue
                if (isInitial(qc) && initialOf(tc) == qc) continue
                ok = false
                break
            }
            if (ok) return true
        }
        return false
    }

    private fun compact(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) if (!c.isWhitespace()) sb.append(c.lowercaseChar())
        return sb.toString()
    }
}
