package com.siwoo.party.common.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * 규칙 카드 3장 넘겨 보기 (그림 + 제목 + 설명). 첫 판 전에 자동으로, 또는 [규칙 보기]로 연다.
 * 마지막 장의 버튼은 doneText (예: "시작!" / "닫기").
 */
object RulesPager {

    /** 카드 그림 영역: 둥근 밝은 판 위에 RuleCard.draw 로 그린다 */
    class ArtView(context: Context) : View(context) {
        var card: RuleCard? = null
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(c: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            bg.color = 0xFF101A4D.toInt()
            c.drawRoundRect(rect, height * 0.08f, height * 0.08f, bg)
            val rc = card ?: return
            c.save()
            c.clipRect(rect)
            paint.reset()
            paint.isAntiAlias = true
            rc.draw(c, width.toFloat(), height.toFloat(), paint)
            c.restore()
        }
    }

    fun show(popups: PopupLayer, gameName: String, cards: List<RuleCard>, doneText: String = "닫기", onDone: (() -> Unit)? = null): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "$gameName 규칙", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        val pageText = Theme.text(ctx, "", 13f, Theme.TEXT_SUB, bold = true)
        head.addView(pageText)
        card.addView(head)

        val body = Theme.horizontal(ctx).apply { gravity = Gravity.TOP }
        val art = ArtView(ctx)
        body.addView(art, Theme.lp(Theme.dp(ctx, 230f), Theme.dp(ctx, 160f)))
        val texts = Theme.vertical(ctx)
        val title = Theme.text(ctx, "", 19f, Theme.TEXT, bold = true)
        val text = Theme.text(ctx, "", 15f, Theme.TEXT_SUB).apply { setLineSpacing(0f, 1.25f) }
        texts.addView(title)
        texts.addView(text, Ui.wrapGap(ctx, 8f))
        body.addView(texts, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(ctx, 14f) })
        card.addView(body, Ui.gap(ctx, 10f))

        val dots = Theme.horizontal(ctx).apply { gravity = Gravity.CENTER }
        val dotViews = cards.indices.map { Theme.colorDot(ctx, Theme.GRAY, 10f) }
        for ((k, d) in dotViews.withIndex()) dots.addView(d, Theme.lp(Theme.dp(ctx, 10f), Theme.dp(ctx, 10f)).apply { if (k > 0) leftMargin = Theme.dp(ctx, 8f) })
        card.addView(dots, Ui.gap(ctx, 10f))

        var page = 0
        var popup: PopupLayer.Popup? = null
        val prev = Ui.button(ctx, "이전", Theme.GRAY) {}
        val next = Ui.button(ctx, "다음", Theme.BLUE) {}
        fun render() {
            val rc = cards[page]
            art.card = rc
            title.text = rc.title
            text.text = rc.text
            pageText.text = "${page + 1} / ${cards.size}"
            for ((k, d) in dotViews.withIndex()) d.background = Theme.oval(if (k == page) Theme.GOLD else Theme.GRAY)
            prev.isEnabled = page > 0
            val last = page == cards.size - 1
            next.text = if (last) doneText else "다음"
            next.color = if (last) Theme.GREEN_DARK else Theme.BLUE
            // 넘길 때 살짝 밀려 들어옴
            body.translationX = Theme.dpf(ctx, 24f)
            body.alpha = 0f
            body.animate().translationX(0f).alpha(1f).setDuration(200).start()
        }
        prev.setOnClickListener { if (page > 0) { page--; render() } }
        next.setOnClickListener {
            if (page < cards.size - 1) { page++; render() } else { popup?.dismiss(); onDone?.invoke() }
        }
        card.addView(Ui.buttonRow(ctx, prev, next), Ui.gap(ctx, 12f))
        render()
        popup = popups.show(card, cancelable = onDone == null, widthPx = Theme.dp(ctx, 560f))
        return popup
    }
}

/** 뒤로 가기 → [계속하기] [규칙 보기] [허브로 나가기(확인)] */
object PauseMenu {
    fun show(popups: PopupLayer, gameName: String, rules: List<RuleCard>, exitNote: String, onExit: () -> Unit): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, padDp = 22f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(ctx, "일시정지", 24f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, gameName, 14f, Theme.TEXT_SUB), Ui.wrapGap(ctx, 4f))
        var popup: PopupLayer.Popup? = null
        fun add(b: GameButton, top: Float) = card.addView(b, LinearLayout.LayoutParams(Theme.dp(ctx, 260f), Theme.dp(ctx, 50f)).apply { topMargin = Theme.dp(ctx, top) })
        add(Ui.button(ctx, "계속하기", Theme.GREEN_DARK) { popup?.dismiss() }, 16f)
        add(Ui.button(ctx, "규칙 보기", Theme.BLUE) { RulesPager.show(popups, gameName, rules) }, 10f)
        add(Ui.button(ctx, "허브로 나가기", Theme.RED_DARK) {
            Ui.confirm(popups, "허브로 나갈까요?", exitNote, "나가기", Theme.RED_DARK) {
                popup?.dismiss(animate = false)
                onExit()
            }
        }, 10f)
        popup = popups.show(card, cancelable = true)
        return popup
    }
}
