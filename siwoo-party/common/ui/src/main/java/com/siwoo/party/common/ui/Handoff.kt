package com.siwoo.party.common.ui

import android.view.Gravity
import android.widget.LinearLayout
import com.siwoo.party.common.PlayerInfo

/**
 * 기기 넘기기 화면: 화면 전체를 불투명하게 덮어 이전 사람의 정보(제시어·투표 등)가 보이지 않게 한다.
 * 플레이어 색 동그라미 + "○○님 차례" + 안내 + [확인]
 */
object Handoff {
    fun show(
        popups: PopupLayer,
        player: PlayerInfo,
        title: String,
        sub: String,
        okText: String,
        beeper: Beeper?,
        onOk: () -> Unit,
    ): PopupLayer.Popup {
        val ctx = popups.context
        val box = Theme.vertical(ctx).apply {
            gravity = Gravity.CENTER
            background = Theme.background()
            tag = TAG
        }
        val color = Theme.playerColor(player.colorIndex)
        box.addView(Theme.text(ctx, player.name.take(1), 30f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(ctx, 4f))
        }, LinearLayout.LayoutParams(Theme.dp(ctx, 76f), Theme.dp(ctx, 76f)))
        box.addView(Theme.text(ctx, title, 26f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 16f) })
        box.addView(Theme.text(ctx, sub, 16f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) },
            Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 8f) })
        var popup: PopupLayer.Popup? = null
        box.addView(Ui.button(ctx, okText, Theme.GREEN_DARK, 19f) {
            popup?.dismiss(animate = false)
            onOk()
        }, LinearLayout.LayoutParams(Theme.dp(ctx, 220f), Theme.dp(ctx, 54f)).apply { topMargin = Theme.dp(ctx, 24f) })
        val w = if (popups.width > 0) popups.width else ctx.resources.displayMetrics.widthPixels
        val h = if (popups.height > 0) popups.height else ctx.resources.displayMetrics.heightPixels
        popup = popups.show(box, cancelable = false, widthPx = w, heightPx = h, dim = false)
        beeper?.turn()
        return popup
    }

    const val TAG = "handoff"
}
