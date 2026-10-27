package com.siwoo.party.games.blackjack.ui

import android.graphics.RectF
import com.siwoo.party.games.blackjack.GameConfig
import kotlin.math.max
import kotlin.math.min

/**
 * 블랙잭 테이블 자리 계산 (px). 화면 크기·인원이 바뀔 때만 [update] — 그리기 중에는 읽기만 한다.
 *
 * 위: 초록 펠트(가운데로 둥글게 내려옴) — 왼쪽 위 라운드 표시 + 버린 카드, 가운데 딜러 카드(왼쪽에 딜러 판), 오른쪽 위 카드 통
 * 가운데 띠: 왼쪽 "○○ 차례" 글자, 오른쪽 버튼 3개
 * 아래: 플레이어 자리 2~4칸 (이름·보유 칩 / 손패 / 합계 / 베팅 칩 더미)
 */
class TableGeom {
    var w = 0f; private set
    var h = 0f; private set
    /** 1dp 픽셀 */
    var d = 1f; private set
    var n = 2; private set
    /** 기본 카드 크기 (CardBitmaps, scale 1) */
    var cardW = 0f; private set
    var cardH = 0f; private set

    // 펠트: 양 끝 높이 / 가운데 높이
    var feltEdgeY = 0f; private set
    var feltMidY = 0f; private set

    val roundPill = RectF()

    // 딜러
    var dealerCx = 0f; private set
    var dealerCy = 0f; private set
    var dealerScale = 1f; private set
    /** 딜러 카드가 펼쳐질 수 있는 폭 */
    var dealerAvail = 0f; private set
    val dealerPlate = RectF()

    // 카드 통·버린 카드
    val shoeBox = RectF()
    var shoeX = 0f; private set
    var shoeY = 0f; private set
    val shoeScale = 0.7f
    val shoeRot = -12f
    var discardX = 0f; private set
    var discardY = 0f; private set
    val discardScale = 0.6f
    val discardRot = 8f

    // 가운데 띠
    var bandCy = 0f; private set
    var labelLeft = 0f; private set
    var labelRight = 0f; private set
    var buttonW = 0f; private set
    var buttonH = 0f; private set
    var buttonGap = 0f; private set
    var buttonsRightMargin = 0f; private set

    // 자리
    val seats: Array<RectF> = Array(GameConfig.MAX_PLAYERS) { RectF() }
    val handScale = 0.95f
    var handCy = 0f; private set
    var badgeCy = 0f; private set
    var headerCy = 0f; private set
    var betBaseY = 0f; private set
    /** 칩 하나 반지름(가로)·두께 */
    var chipRx = 0f; private set
    var chipTh = 0f; private set

    fun update(width: Int, height: Int, density: Float, players: Int, baseCardW: Float, baseCardH: Float) {
        w = width.toFloat()
        h = height.toFloat()
        d = density
        n = players
        cardW = baseCardW
        cardH = baseCardH

        feltEdgeY = h * 0.245f
        feltMidY = h * 0.315f

        roundPill.set(10f * d, 8f * d, 104f * d, 34f * d)
        dealerCy = h * 0.16f
        dealerCx = w / 2f
        dealerScale = min(1f, h * 0.235f / max(cardH, 1f))
        dealerPlate.set(w / 2f - 128f * d - 92f * d, dealerCy - 33f * d, w / 2f - 128f * d, dealerCy + 33f * d)
        shoeBox.set(w - 12f * d - 84f * d, h * 0.075f, w - 12f * d, h * 0.075f + 56f * d)
        shoeX = shoeBox.centerX() - 6f * d
        shoeY = shoeBox.top + 4f * d
        discardX = roundPill.left + 32f * d
        discardY = roundPill.bottom + 34f * d
        dealerAvail = 2f * (min(w / 2f - dealerPlate.right, shoeBox.left - w / 2f) - 10f * d)

        bandCy = h * 0.40f
        buttonW = 100f * d
        buttonH = 46f * d
        buttonGap = 8f * d
        buttonsRightMargin = 12f * d
        labelLeft = 16f * d
        labelRight = w - buttonsRightMargin - 3f * buttonW - 2f * buttonGap - 12f * d

        val gap = 8f * d
        val margin = 8f * d
        val seatW = min(260f * d, (w - 2f * margin - (n - 1) * gap) / n)
        val total = n * seatW + (n - 1) * gap
        val left0 = (w - total) / 2f
        val top = h * 0.48f
        val bottom = h - 6f * d
        for (i in 0 until GameConfig.MAX_PLAYERS) {
            val l = left0 + i * (seatW + gap)
            seats[i].set(l, top, l + seatW, bottom)
        }
        headerCy = top + 15f * d
        val ch = cardH * handScale
        handCy = top + 29f * d + ch / 2f
        badgeCy = handCy + ch / 2f + 13f * d
        betBaseY = bottom - 11f * d
        chipRx = 14f * d
        chipTh = 3.4f * d
    }

    /** 자리 i 의 손패 k 번째 카드 가운데 x (count 장일 때) */
    fun handX(i: Int, k: Int, count: Int): Float {
        val s = seats[i]
        val cw = cardW * handScale
        return spread(s.centerX(), s.width() - 16f * d, cw, k, count, 0.6f)
    }

    /** 딜러 k 번째 카드 가운데 x */
    fun dealerX(k: Int, count: Int): Float = spread(dealerCx, dealerAvail, cardW * dealerScale, k, count, 0.7f)

    /** 베팅 칩 더미 가운데 x */
    fun betX(i: Int): Float = seats[i].centerX() - min(42f * d, seats[i].width() * 0.27f)

    private fun spread(cx: Float, avail: Float, cw: Float, k: Int, count: Int, maxGapFrac: Float): Float {
        if (count <= 1) return cx
        val gap = max(0f, min(cw * maxGapFrac, (avail - cw) / (count - 1)))
        val span = cw + gap * (count - 1)
        return cx - span / 2f + cw / 2f + k * gap
    }
}
