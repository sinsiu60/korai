package com.siwoo.party.common.secret

import android.graphics.Canvas
import android.graphics.RectF
import android.view.View

/**
 * 꾹 누르고 있는 동안만 보이는 비밀 내용을 그리는 그리기 담당 (SecretHoldView 가 부른다).
 * 단어·숫자·주사위 눈·카드 등 게임마다 원하는 그림을 Canvas 로 그린다.
 * - layout: 카드 크기가 정해지거나 바뀔 때 (글자 크기 맞추기·Shader 만들기 등 할당은 여기서만)
 * - draw: 카드 영역(card)에 보이는 면 그리기 — **객체 할당 금지** (Paint·문자열은 미리 만들어 둘 것)
 * - text: 지금 내용을 글자로 (테스트·확인용. 미리 만들어 둔 문자열을 돌려줄 것)
 * - clear: 비밀 지우기 (글자·숫자 참조를 버림) — 다음 사람으로 넘어가기 전에 SecretHoldView.clear() 가 부른다
 */
interface SecretContent {
    /** 비밀을 글자로 (없으면 "") */
    val text: String

    /** 카드 크기가 정해질 때 (host = 그리는 View, card = 카드 영역 px) */
    fun layout(host: View, card: RectF) {}

    /** 보이는 면 그리기 (card 안을 모두 채울 것 — 아래 가려진 면은 그리지 않는다) */
    fun draw(c: Canvas, card: RectF)

    /** 비밀 지우기 */
    fun clear() {}
}
