package com.siwoo.party.common.numpad

/**
 * 숫자 입력 패드의 규칙 (순수 Kotlin — NumPadView 가 쓴다).
 * 숫자 야구: NumInput(maxDigits = 3, distinctDigits = true, exactDigits = true)  → 서로 다른 숫자 3개 (0 가능)
 * 업다운:   NumInput(maxDigits = 3, allowLeadingZero = false, min = 1, max = 100) → setRange 로 범위를 좁혀 감
 *
 * @param maxDigits 최대 자릿수 (1~9)
 * @param allowLeadingZero false 면 첫 자리에 0 을 못 넣음
 * @param distinctDigits true 면 이미 넣은 숫자는 다시 못 넣음
 * @param exactDigits true 면 maxDigits 자리를 모두 채워야 확인 가능
 * @param min/max 확인 가능한 값의 범위 (null = 제한 없음)
 */
class NumInput(
    maxDigits: Int,
    allowLeadingZero: Boolean = true,
    distinctDigits: Boolean = false,
    exactDigits: Boolean = false,
    min: Int? = null,
    max: Int? = null,
) {
    var maxDigits = maxDigits.coerceIn(1, 9)
        private set
    var allowLeadingZero = allowLeadingZero
        private set
    var distinctDigits = distinctDigits
        private set
    var exactDigits = exactDigits
        private set
    var min: Int? = min
        private set
    var max: Int? = max
        private set

    /** 지금까지 넣은 숫자 */
    var text: String = ""
        private set

    /** 넣은 값 (비어 있으면 null) */
    val value: Int? get() = text.toIntOrNull()

    val isEmpty: Boolean get() = text.isEmpty()
    val isFull: Boolean get() = text.length >= maxDigits

    /** 설정 바꾸기 (넣은 값은 지움) */
    fun configure(
        maxDigits: Int,
        allowLeadingZero: Boolean = true,
        distinctDigits: Boolean = false,
        exactDigits: Boolean = false,
        min: Int? = null,
        max: Int? = null,
    ) {
        this.maxDigits = maxDigits.coerceIn(1, 9)
        this.allowLeadingZero = allowLeadingZero
        this.distinctDigits = distinctDigits
        this.exactDigits = exactDigits
        this.min = min
        this.max = max
        text = ""
    }

    /** 범위만 바꾸기 (업다운에서 범위가 좁혀질 때). 넣은 값은 그대로 */
    fun setRange(min: Int?, max: Int?) {
        this.min = min
        this.max = max
    }

    /** 숫자 d(0~9)를 지금 넣을 수 있는지 (자리 다 참·첫 자리 0·이미 쓴 숫자면 false) */
    fun canType(d: Int): Boolean {
        if (d !in 0..9 || isFull) return false
        if (d == 0 && text.isEmpty() && !allowLeadingZero) return false
        if (distinctDigits && text.indexOf('0' + d) >= 0) return false
        return true
    }

    /** 숫자 넣기 (못 넣으면 false) */
    fun type(d: Int): Boolean {
        if (!canType(d)) return false
        text += ('0' + d)
        return true
    }

    /** 마지막 숫자 지우기 (비어 있으면 false) */
    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        text = text.substring(0, text.length - 1)
        return true
    }

    fun clear() {
        text = ""
    }

    /** 값을 통째로 넣기 (규칙에 맞는 앞부분까지만 들어감). 들어간 글자 */
    fun set(s: String): String {
        text = ""
        for (ch in s) if (ch in '0'..'9') type(ch - '0')
        return text
    }

    /** 확인할 수 없는 이유 (확인 가능하면 null) */
    val error: String?
        get() {
            if (text.isEmpty()) return hint
            if (exactDigits && text.length < maxDigits) return "숫자 ${maxDigits}개를 모두 넣으세요"
            val v = value ?: return hint
            val lo = min
            val hi = max
            if ((lo != null && v < lo) || (hi != null && v > hi)) return rangeText()
            return null
        }

    val canConfirm: Boolean get() = error == null

    /** 비어 있을 때 안내 */
    val hint: String
        get() = when {
            distinctDigits && exactDigits -> "서로 다른 숫자 ${maxDigits}개를 누르세요"
            exactDigits -> "숫자 ${maxDigits}개를 누르세요"
            min != null || max != null -> rangeText() + " 숫자를 누르세요"
            else -> "숫자를 누르세요"
        }

    /** "1~100 사이" / "5 이상" / "99 이하" */
    fun rangeText(): String {
        val lo = min
        val hi = max
        return when {
            lo != null && hi != null -> "${lo}~${hi} 사이"
            lo != null -> "$lo 이상"
            hi != null -> "$hi 이하"
            else -> ""
        }
    }
}
