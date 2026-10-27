package com.siwoo.party.common.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.LinearLayout

/** 여러 화면이 같이 쓰는 팝업 카드·버튼 도우미 */
object Ui {
    fun darkCard(ctx: Context, padDp: Float = 18f): LinearLayout = Theme.vertical(ctx).apply {
        background = Theme.verticalCard(ctx, Theme.CARD_LIGHT, Theme.CARD, 18f)
        elevation = Theme.dpf(ctx, 12f)
        val p = Theme.dp(ctx, padDp)
        setPadding(p, Theme.dp(ctx, 14f), p, Theme.dp(ctx, 14f))
    }

    fun button(ctx: Context, text: String, color: Int, sp: Float = 17f, onClick: (GameButton) -> Unit): GameButton =
        GameButton(ctx, color, sp).apply {
            this.text = text
            setOnClickListener { onClick(this) }
        }

    fun buttonRow(ctx: Context, vararg buttons: View, heightDp: Float = 48f): LinearLayout {
        val row = Theme.horizontal(ctx)
        for ((k, b) in buttons.withIndex()) {
            row.addView(b, Theme.lp(0, Theme.dp(ctx, heightDp), 1f).apply { if (k > 0) leftMargin = Theme.dp(ctx, 10f) })
        }
        return row
    }

    fun gap(ctx: Context, topDp: Float): LinearLayout.LayoutParams =
        Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(ctx, topDp) }

    fun wrapGap(ctx: Context, topDp: Float): LinearLayout.LayoutParams =
        Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, topDp) }

    /** 확인 팝업: [okText] [취소] */
    fun confirm(popups: PopupLayer, title: String, msg: String, okText: String, okColor: Int, onCancel: (() -> Unit)? = null, onOk: () -> Unit): PopupLayer.Popup {
        val ctx = popups.context
        val card = darkCard(ctx)
        card.addView(Theme.text(ctx, title, 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, msg, 15f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) }, wrapGap(ctx, 10f))
        var popup: PopupLayer.Popup? = null
        card.addView(buttonRow(ctx,
            button(ctx, okText, okColor) { popup?.dismiss(); onOk() },
            button(ctx, "취소", Theme.GRAY) { popup?.dismiss(); onCancel?.invoke() },
        ), gap(ctx, 16f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 400f))
        return popup
    }

    /** 고개를 젓듯 좌우로 흔들기 (못 고르는 카드 등) */
    fun shake(v: View) {
        val d = Theme.dpf(v.context, 8f)
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -d, d, -d * 0.7f, d * 0.7f, -d * 0.3f, 0f).apply {
            duration = 420
            start()
        }
    }

    /** 왕관 (cx, 아래 가장자리 y 기준, 폭 w). path 는 호출하는 쪽이 재사용 */
    fun drawCrown(c: Canvas, cx: Float, bottom: Float, w: Float, path: Path, paint: Paint) {
        val h = w * 0.72f
        val l = cx - w / 2f
        path.reset()
        path.moveTo(l, bottom)
        path.lineTo(l, bottom - h * 0.75f)
        path.lineTo(l + w * 0.27f, bottom - h * 0.38f)
        path.lineTo(cx, bottom - h)
        path.lineTo(l + w * 0.73f, bottom - h * 0.38f)
        path.lineTo(l + w, bottom - h * 0.75f)
        path.lineTo(l + w, bottom)
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = Theme.GOLD
        c.drawPath(path, paint)
        paint.color = Theme.GOLD_DARK
        c.drawRect(l, bottom - h * 0.18f, l + w, bottom, paint)
        paint.color = Theme.RED
        c.drawCircle(cx, bottom - h * 0.5f, w * 0.08f, paint)
        paint.color = Theme.GOLD_LIGHT
        c.drawCircle(l, bottom - h * 0.75f, w * 0.07f, paint)
        c.drawCircle(cx, bottom - h, w * 0.07f, paint)
        c.drawCircle(l + w, bottom - h * 0.75f, w * 0.07f, paint)
    }
}
