package com.siwoo.party.games.marble.model

enum class CardEffect {
    MOVE_TO,          // value = 목표 칸 (앞으로 이동, 출발 통과 시 월급)
    MOVE_BACK,        // value = 뒤로 갈 칸 수
    RECEIVE,          // value = 받는 돈
    PAY,              // value = 내는 돈 (은행)
    BIRTHDAY,         // value = 다른 플레이어 1명당 받는 돈
    KEEP_ESCAPE,      // 무인도 탈출권 (보관)
    KEEP_TOLL_FREE,   // 통행료 1회 면제권 (보관)
    KEEP_HALF_BUILD,  // 반값 건설권 (보관, 다음 건설 시 자동 적용)
    KEEP_PENALTY_FREE, // 벌칙 면제권 (보관, 벌칙 칸에서 사용 여부를 묻는다)
    REPAIR,           // value/value2/value3 = 별장/빌딩/호텔 1채당 수리비
    DONATE,           // value = 사회복지기금 기부액
}

/** 황금열쇠 카드 */
class Card(
    val id: Int,
    val title: String,
    val description: String,
    val effect: CardEffect,
    val value: Int = 0,
    val value2: Int = 0,
    val value3: Int = 0,
) {
    val isKeepable: Boolean
        get() = effect == CardEffect.KEEP_ESCAPE ||
            effect == CardEffect.KEEP_TOLL_FREE ||
            effect == CardEffect.KEEP_HALF_BUILD ||
            effect == CardEffect.KEEP_PENALTY_FREE
}
