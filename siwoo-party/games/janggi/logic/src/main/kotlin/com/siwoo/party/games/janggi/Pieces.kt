package com.siwoo.party.games.janggi

/** 진영. 초(楚, 파랑)는 판 아래쪽(y 6~9)에서 시작하고 먼저 둔다. 한(漢, 빨강)은 위쪽(y 0~3). */
object Side {
    const val CHO = 0
    const val HAN = 1

    fun other(side: Int): Int = 1 - side

    /** "초" / "한" */
    fun label(side: Int): String = if (side == CHO) "초" else "한"
}

/**
 * 기물 종류. 한자는 화면에 그대로 쓴다 (궁·졸은 진영마다 글자가 다름).
 * score = 수 제한 점수 판정용 점수 (0.1점 단위, 궁은 0)
 */
enum class Kind(val hanjaCho: String, val hanjaHan: String, val nameCho: String, val nameHan: String, val score: Int) {
    GENERAL("楚", "漢", "초", "한", 0),
    CHARIOT("車", "車", "차", "차", GameConfig.SCORE_CHARIOT),
    CANNON("包", "包", "포", "포", GameConfig.SCORE_CANNON),
    HORSE("馬", "馬", "마", "마", GameConfig.SCORE_HORSE),
    ELEPHANT("象", "象", "상", "상", GameConfig.SCORE_ELEPHANT),
    GUARD("士", "士", "사", "사", GameConfig.SCORE_GUARD),
    SOLDIER("卒", "兵", "졸", "병", GameConfig.SCORE_SOLDIER),
    ;

    fun hanja(side: Int): String = if (side == Side.CHO) hanjaCho else hanjaHan
    fun koName(side: Int): String = if (side == Side.CHO) nameCho else nameHan

    companion object {
        /** values() 는 부를 때마다 배열을 새로 만들므로 한 번만 */
        val ALL: Array<Kind> = values()
    }
}

/**
 * 판 위 기물 값 (Int 하나). 0 = 빈 칸, 그 밖에는 진영 × 8 + 종류 번호 + 1 (초 1~7, 한 9~15).
 */
object Piece {
    const val EMPTY = 0

    fun code(side: Int, kind: Kind): Int = side * 8 + kind.ordinal + 1
    fun side(code: Int): Int = code shr 3
    fun kindIndex(code: Int): Int = (code and 7) - 1
    fun kind(code: Int): Kind = Kind.ALL[kindIndex(code)]
    fun isKind(code: Int, kind: Kind): Boolean = code != EMPTY && kindIndex(code) == kind.ordinal
}

/**
 * 상차림: 뒷줄 차·사 사이의 마·상 배치. 이름은 **자기 쪽에서 보아 왼쪽부터** 읽는다
 * (초는 화면 왼쪽부터, 판 건너편에 앉는 한은 화면 오른쪽부터).
 */
enum class Formation(val label: String, val kinds: Array<Kind>, val nick: String) {
    MSMS("마상마상", arrayOf(Kind.HORSE, Kind.ELEPHANT, Kind.HORSE, Kind.ELEPHANT), "오른상"),
    SMSM("상마상마", arrayOf(Kind.ELEPHANT, Kind.HORSE, Kind.ELEPHANT, Kind.HORSE), "왼상"),
    MSSM("마상상마", arrayOf(Kind.HORSE, Kind.ELEPHANT, Kind.ELEPHANT, Kind.HORSE), "안상"),
    SMMS("상마마상", arrayOf(Kind.ELEPHANT, Kind.HORSE, Kind.HORSE, Kind.ELEPHANT), "바깥상"),
    ;

    companion object {
        val ALL: Array<Formation> = values()
        /** 상차림 칸 (자기 쪽에서 본 세로줄 번호, 왼쪽 0) */
        val FILES = intArrayOf(1, 2, 6, 7)
    }
}
