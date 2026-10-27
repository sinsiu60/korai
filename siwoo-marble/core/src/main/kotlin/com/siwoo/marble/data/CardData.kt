package com.siwoo.marble.data

import com.siwoo.marble.model.Card
import com.siwoo.marble.model.CardEffect

/**
 * 황금열쇠 카드 목록 (직접 창작). 금액은 여기서 바로 고치면 된다.
 * 사용한 카드는 덱 맨 아래로 돌아가고, 보관형 카드는 사용할 때 돌아간다.
 */
object CardData {

    val cards: Array<Card> = arrayOf(
        // ── 이동형 ──
        Card(0, "출발지로!", "출발 칸으로 이동해 월급을 받습니다.", CardEffect.MOVE_TO, 0),
        Card(1, "제주도 여행", "제주도로 이동합니다. 출발 칸을 지나면 월급을 받습니다.", CardEffect.MOVE_TO, 5),
        Card(2, "크루즈 여행", "퀸엘리자베스호로 이동합니다. 출발 칸을 지나면 월급을 받습니다.", CardEffect.MOVE_TO, 28),
        Card(3, "뒤로 3칸", "길을 잘못 들었습니다. 뒤로 3칸 이동합니다.", CardEffect.MOVE_BACK, 3),
        Card(4, "우주여행 초대장", "우주여행 칸으로 이동합니다. 다음 턴에 원하는 칸으로 날아갈 수 있습니다.", CardEffect.MOVE_TO, 30),
        // ── 돈형 ──
        Card(5, "복권 당첨", "복권에 당첨되었습니다! 20만 원을 받습니다.", CardEffect.RECEIVE, 200_000),
        Card(6, "장학금", "성적 우수 장학금 10만 원을 받습니다.", CardEffect.RECEIVE, 100_000),
        Card(7, "병원비", "감기에 걸렸습니다. 병원비 5만 원을 냅니다.", CardEffect.PAY, 50_000),
        Card(8, "과속 벌금", "과속으로 적발되었습니다. 벌금 10만 원을 냅니다.", CardEffect.PAY, 100_000),
        Card(9, "생일 축하!", "오늘은 내 생일! 다른 플레이어에게서 5만 원씩 받습니다.", CardEffect.BIRTHDAY, 50_000),
        // ── 특수형 ──
        Card(10, "무인도 탈출권", "무인도에 갇혔을 때 한 번 즉시 탈출할 수 있습니다. (보관)", CardEffect.KEEP_ESCAPE),
        Card(11, "통행료 면제권", "남의 땅 통행료를 한 번 면제받습니다. (보관)", CardEffect.KEEP_TOLL_FREE),
        Card(12, "반값 건설권", "다음 건물 건설비가 절반이 됩니다. (보관, 자동 적용)", CardEffect.KEEP_HALF_BUILD),
        Card(13, "건물 수리비", "건물을 수리합니다. 별장 3만, 빌딩 6만, 호텔 10만 원씩 냅니다.", CardEffect.REPAIR, 30_000, 60_000, 100_000),
        Card(14, "사회복지기금 기부", "사회복지기금에 10만 원을 기부합니다.", CardEffect.DONATE, 100_000),
        Card(15, "벌칙 면제권", "벌칙 칸에서 룰렛을 돌리지 않고 넘어갈 수 있습니다. (보관)", CardEffect.KEEP_PENALTY_FREE),
    )

    init {
        cards.forEachIndexed { i, c -> check(c.id == i) { "카드 id가 어긋났습니다: $i" } }
    }

    fun byId(id: Int): Card = cards[id]

    fun idOf(effect: CardEffect): Int = cards.first { it.effect == effect }.id
}
