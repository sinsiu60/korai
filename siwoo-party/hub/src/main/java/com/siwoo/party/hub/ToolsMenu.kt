package com.siwoo.party.hub

import android.view.Gravity
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.hub.tools.FingerPickActivity
import com.siwoo.party.hub.tools.LadderActivity
import com.siwoo.party.hub.tools.OrderActivity
import com.siwoo.party.hub.tools.ToolActivity

/**
 * [🛠️ 도구] 메뉴: 점수와 상관없는 모임 도구 (손가락 추첨 · 사다리 타기 · 순서 정하기).
 */
object ToolsMenu {

    class Tool(val emoji: String, val name: String, val desc: String, val open: ((GameHostActivity) -> Unit)?)

    /** 도구 목록 (open 이 null 이면 아직 준비 중). 지금 모임 플레이어 이름·색을 넘긴다 */
    val tools: List<Tool> = listOf(
        Tool("☝️", "손가락 추첨", "화면에 손가락을 올리면 한 명(또는 팀)을 뽑아요") { host ->
            ToolActivity.open(host, FingerPickActivity::class.java, host.session.players)
        },
        Tool("🔀", "사다리 타기", "이름과 결과를 넣고 사다리를 타요") { host ->
            ToolActivity.open(host, LadderActivity::class.java, host.session.players)
        },
        Tool("🔢", "순서 정하기", "이름 카드를 섞어 1번부터 공개해요") { host ->
            ToolActivity.open(host, OrderActivity::class.java, host.session.players)
        },
    )

    fun show(host: GameHostActivity): PopupLayer.Popup {
        val ctx = host
        val card = Ui.darkCard(ctx, padDp = 16f)
        card.addView(Theme.text(ctx, "🛠️ 도구", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "점수와 상관없이 쓰는 모임 도구예요", 13f, Theme.TEXT_SUB), Ui.wrapGap(ctx, 2f))
        var popup: PopupLayer.Popup? = null
        val row = Theme.horizontal(ctx)
        for ((k, t) in tools.withIndex()) {
            val box = Theme.vertical(ctx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = Theme.card(ctx, Theme.CARD_LIGHT, 14f)
                val p = Theme.dp(ctx, 10f)
                setPadding(p, p, p, p)
            }
            box.addView(Theme.text(ctx, t.emoji, 30f).apply { gravity = Gravity.CENTER })
            box.addView(Theme.text(ctx, t.name, 16f, Theme.GOLD_LIGHT, bold = true).apply { gravity = Gravity.CENTER })
            box.addView(Theme.text(ctx, t.desc, 12f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.wrapGap(ctx, 4f))
            val open = t.open
            box.addView(Ui.button(ctx, if (open != null) "열기" else "준비 중", if (open != null) Theme.GREEN_DARK else Theme.GRAY, 14f) {
                if (open != null) { popup?.dismiss(animate = false); open(host) }
            }.apply { isEnabled = open != null }, Theme.lp(Theme.MATCH, Theme.dp(ctx, 40f)).apply { topMargin = Theme.dp(ctx, 8f) })
            row.addView(box, Theme.lp(0, Theme.WRAP, 1f).apply { if (k > 0) leftMargin = Theme.dp(ctx, 10f) })
        }
        card.addView(row, Ui.gap(ctx, 10f))
        card.addView(Ui.button(ctx, "닫기", Theme.GRAY, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 42f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = host.popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 560f))
        return popup
    }
}
