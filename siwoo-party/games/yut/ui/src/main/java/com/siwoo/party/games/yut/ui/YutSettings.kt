package com.siwoo.party.games.yut.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.yut.GameConfig
import com.siwoo.party.games.yut.YutRules

/**
 * 윷놀이 전용 설정 (SharedPreferences "yut").
 * - 말 수: 1인당 2~4개 (기본 4)
 * - 흔들어 던지기: 기기를 흔들어도 던짐 (기본 끔)
 * - 낙: 5% 확률로 던지기 무효 (기본 끔)
 * - 빽도 대기말 올리기: 판에 말이 없을 때 빽도로 대기 말을 출발점 바로 뒤에 (기본 끔)
 */
object YutSettings {
    const val PREFS = "yut"
    const val KEY_PIECES = "pieces"
    const val KEY_SHAKE = "shake"
    const val KEY_NAK = "nak"
    const val KEY_BACKDO_ENTER = "backdoEnter"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pieces(ctx: Context): Int =
        prefs(ctx).getInt(KEY_PIECES, GameConfig.YUT_PIECES).coerceIn(GameConfig.PIECES_MIN, GameConfig.PIECES_MAX)

    fun setPieces(ctx: Context, n: Int) {
        prefs(ctx).edit().putInt(KEY_PIECES, n.coerceIn(GameConfig.PIECES_MIN, GameConfig.PIECES_MAX)).apply()
    }

    fun shake(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHAKE, GameConfig.SHAKE_DEFAULT)
    fun nak(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_NAK, GameConfig.NAK_DEFAULT)
    fun backdoEnter(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BACKDO_ENTER, GameConfig.BACKDO_ENTER_DEFAULT)

    fun setFlag(ctx: Context, key: String, on: Boolean) {
        prefs(ctx).edit().putBoolean(key, on).apply()
    }

    /** 게임 규칙 설정 (말 수·낙·빽도 대기말) */
    fun rules(ctx: Context): YutRules = YutRules(pieces(ctx), nak(ctx), backdoEnter(ctx))

    /** 설정 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, 16f)
        var popup: PopupLayer.Popup? = null

        // 제목 + 닫기
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "윷놀이 설정", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Ui.button(ctx, "닫기", Theme.BLUE, 14f) { popup?.dismiss() }, Theme.lp(Theme.dp(ctx, 76f), Theme.dp(ctx, 36f)))
        card.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP))

        // 말 수
        val pieceRow = Theme.horizontal(ctx)
        pieceRow.addView(label(ctx, "말 수", "1인당 말 개수 (기본 4개)"), Theme.lp(0, Theme.WRAP, 1f))
        val pieceButtons = ArrayList<GameButton>()
        fun refreshPieces() {
            val cur = pieces(ctx)
            for ((k, b) in pieceButtons.withIndex()) b.color = if (GameConfig.PIECES_MIN + k == cur) Theme.GOLD_DARK else Theme.GRAY
        }
        for (n in GameConfig.PIECES_MIN..GameConfig.PIECES_MAX) {
            val b = Ui.button(ctx, "${n}개", Theme.GRAY, 15f) { b ->
                setPieces(ctx, n)
                b.bounce()
                refreshPieces()
            }
            b.contentDescription = "말 ${n}개"
            pieceButtons.add(b)
            pieceRow.addView(b, Theme.lp(Theme.dp(ctx, 64f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 8f) })
        }
        refreshPieces()
        card.addView(pieceRow, Ui.gap(ctx, 10f))

        toggle(ctx, card, "흔들어 던지기", "기기를 흔들어도 윷을 던져요", KEY_SHAKE, shake(ctx))
        toggle(ctx, card, "낙", "5% 확률로 윷이 판 밖으로! 그 던지기는 무효", KEY_NAK, nak(ctx))
        toggle(ctx, card, "빽도 대기말 올리기", "판에 말이 없을 때 빽도면 대기 말을 출발점 바로 뒤에", KEY_BACKDO_ENTER, backdoEnter(ctx))

        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 500f))
        return popup
    }

    private fun label(ctx: Context, title: String, sub: String): LinearLayout {
        val col = Theme.vertical(ctx)
        col.addView(Theme.text(ctx, title, 16f, Theme.TEXT, bold = true))
        col.addView(Theme.text(ctx, sub, 12f, Theme.TEXT_SUB).apply { maxLines = 1 }, Ui.gap(ctx, 3f))
        return col
    }

    /** 켬/끔 한 줄 */
    private fun toggle(ctx: Context, card: LinearLayout, title: String, sub: String, key: String, initial: Boolean) {
        val row = Theme.horizontal(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(label(ctx, title, sub), Theme.lp(0, Theme.WRAP, 1f))
        var on = initial
        val b = Ui.button(ctx, "", Theme.GRAY, 15f) { b ->
            on = !on
            setFlag(ctx, key, on)
            b.bounce()
            b.text = if (on) ON else OFF
            b.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        }
        b.text = if (on) ON else OFF
        b.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        b.contentDescription = title
        row.addView(b, Theme.lp(Theme.dp(ctx, 88f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 10f) })
        card.addView(row, Ui.gap(ctx, 10f))
    }

    private const val ON = "켬"
    private const val OFF = "끔"
}
