package com.siwoo.marble.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.marble.GameConfig
import com.siwoo.marble.logic.Money
import com.siwoo.marble.model.Player

/**
 * 화면 바깥 모서리에 붙는 플레이어 자산 카드 (모두의 마블 스타일).
 * 오른쪽 모서리 카드는 좌우를 뒤집어(mirrored) 말 아이콘이 바깥쪽에 오게 한다.
 */
class PlayerCardView(context: Context, private val mirrored: Boolean) : LinearLayout(context) {

    private val avatar: TextView
    private val nameText: TextView
    private val moneyText: TextView
    private val subText: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(context, 6f)
        setPadding(p, Ui.dp(context, 3f), p, Ui.dp(context, 3f))

        avatar = Ui.text(context, "", 15f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER }
        val col = Ui.vertical(context).apply { gravity = if (mirrored) Gravity.END else Gravity.START }
        nameText = line(12f, Ui.TEXT, true)
        moneyText = line(15f, Ui.TEXT, true)
        subText = line(10f, Ui.SUB_TEXT, false)
        col.addView(nameText)
        col.addView(moneyText)
        col.addView(subText)

        val size = Ui.dp(context, 30f)
        val avatarLp = LayoutParams(size, size)
        val colLp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        val gap = Ui.dp(context, 6f)
        if (mirrored) {
            colLp.rightMargin = gap
            addView(col, colLp)
            addView(avatar, avatarLp)
        } else {
            colLp.leftMargin = gap
            addView(avatar, avatarLp)
            addView(col, colLp)
        }
    }

    private fun line(sp: Float, color: Int, bold: Boolean) = Ui.text(context, "", sp, color, bold).apply {
        maxLines = 1
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        gravity = if (mirrored) Gravity.END else Gravity.START
    }

    /**
     * @param total 총자산 (현금+땅+건물)
     * @param leader 총자산 단독 1위인지
     */
    fun bind(p: Player, total: Int, leader: Boolean, isCurrent: Boolean) {
        val color = GameConfig.PLAYER_COLORS[p.colorIndex]
        avatar.background = Ui.oval(color, Color.WHITE, Ui.dp(context, 2f))
        avatar.setTextColor(Ui.textOn(color))
        avatar.text = if (p.name.isEmpty()) "?" else p.name.substring(0, 1)

        nameText.text = if (leader && !p.bankrupt) "${p.name} · 1위" else p.name
        if (p.bankrupt) {
            moneyText.text = "파산"
            subText.text = "땅은 은행으로"
        } else {
            moneyText.text = Money.won(p.money)
            val sb = StringBuilder("총 ").append(Money.short(total))
            if (p.islandTurns > 0) sb.append(" · 무인도").append(p.islandTurns)
            if (p.spaceTravelPending) sb.append(" · 우주여행")
            val keys = p.escapeCards + p.tollFreeCards + p.halfBuildCards
            if (keys > 0) sb.append(" · 열쇠").append(keys)
            subText.text = sb
        }
        val r = Ui.dp(context, 10f).toFloat()
        background = if (isCurrent && !p.bankrupt) {
            Ui.rounded(0xFFFFFDE7.toInt(), r, color, Ui.dp(context, 3f))
        } else {
            Ui.rounded(Color.WHITE, r, 0x33000000, Ui.dp(context, 1f))
        }
        alpha = if (p.bankrupt) 0.5f else 1f
        contentDescription = "${p.name} 자산 ${moneyText.text}"
    }
}
