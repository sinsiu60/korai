package com.siwoo.party.games.highlow.ui

/**
 * 카드 테이블 자리 계산 (px). FeltView·OverlayView·카드 연출이 같은 값을 쓴다.
 * 왼쪽 세로줄: 위 = 덱(뒷면 더미), 아래 = 버린 카드 / 가운데 = 큰 기준 카드 / 오른쪽 = 다음 카드 자리(점선 + ?)
 * 맨 위 줄 = 숫자 순서 안내, 맨 아래 줄 = 높은·낮은 숫자 가짓수 안내.
 */
class TableGeom {
    var w = 0f; private set
    var h = 0f; private set
    /** density */
    var d = 1f; private set

    /** 큰 카드(기준·다음) 폭·높이와 CardSprite 배율 */
    var bigW = 0f; private set
    var bigH = 0f; private set
    var bigScale = 1f; private set

    /** 작은 카드(덱·버린 카드) */
    var smallW = 0f; private set
    var smallH = 0f; private set
    var smallScale = 0.5f; private set

    /** 큰 카드 가운데 y */
    var cy = 0f; private set
    var baseX = 0f; private set
    var slotX = 0f; private set
    var deckX = 0f; private set
    var deckY = 0f; private set
    var discardX = 0f; private set
    var discardY = 0f; private set

    /** 맨 위 안내 줄 가운데 y, 큰 카드 위 이름표 글자 기준선, 맨 아래 안내 줄 가운데 y */
    var topLineY = 0f; private set
    var captionY = 0f; private set
    var oddsY = 0f; private set
    var oddsH = 0f; private set

    val ready: Boolean get() = w > 0f

    /** 판 크기 w×h, 카드 그림 폭·높이(px, 배율 1) */
    fun update(w: Int, h: Int, density: Float, cardW: Float, cardH: Float) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        d = density
        val top = 26f * d
        oddsH = 26f * d
        val bottom = this.h - oddsH - 10f * d
        val caption = 18f * d
        val zone = bottom - top - caption
        bigH = minOf(zone - 8f * d, this.w * 0.28f * RATIO).coerceAtLeast(40f * d)
        bigW = bigH / RATIO
        bigScale = bigW / cardW.coerceAtLeast(1f)
        smallScale = bigScale * SMALL
        smallW = bigW * SMALL
        smallH = bigH * SMALL
        cy = top + caption + zone / 2f
        topLineY = top / 2f + 2f * d
        captionY = cy - bigH / 2f - 6f * d
        oddsY = this.h - oddsH / 2f - 6f * d
        deckX = maxOf(this.w * 0.11f, smallW / 2f + 12f * d)
        deckY = cy - smallH / 2f - 7f * d
        discardX = deckX
        discardY = cy + smallH / 2f + 7f * d
        slotX = minOf(this.w * 0.80f, this.w - bigW / 2f - 14f * d)
        // 기준 카드: 덱 오른쪽과 다음 카드 자리 사이, 화살표 자리를 남긴다
        val left = deckX + smallW / 2f + 10f * d + bigW / 2f
        val right = slotX - bigW - 34f * d
        baseX = maxOf(left, minOf(this.w * 0.43f, right))
    }

    companion object {
        /** 카드 높이 / 폭 */
        const val RATIO = 1.4f
        /** 작은 카드 크기 비율 */
        const val SMALL = 0.55f
    }
}
