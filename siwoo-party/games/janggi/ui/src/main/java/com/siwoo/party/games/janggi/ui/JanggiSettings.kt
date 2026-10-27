package com.siwoo.party.games.janggi.ui

import android.content.Context
import android.widget.LinearLayout
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.janggi.Formation
import com.siwoo.party.games.janggi.GameConfig

/**
 * 장기 전용 설정 (SharedPreferences "janggi"). 허브 [장기 설정] 과 게임 시작 화면에서 같은 버튼으로 바꾼다.
 * - 무르기 1회 (기본 켬) · 판 회전 (기본 끔) · 빅장 무승부 (기본 끔) · 수 제한 (없음/100/150/200)
 * - 마지막으로 고른 상차림 (진영별) 도 기억한다.
 */
object JanggiSettings {
    const val PREFS = "janggi"
    const val KEY_UNDO = "undo"
    const val KEY_ROTATE = "rotate"
    const val KEY_BIKJANG = "bikjang"
    const val KEY_LIMIT = "limit"
    private const val KEY_FORM = "form"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun undo(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNDO, GameConfig.UNDO_DEFAULT)
    fun rotate(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ROTATE, GameConfig.ROTATE_DEFAULT)
    fun bikjang(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BIKJANG, GameConfig.BIKJANG_DEFAULT)
    fun limit(ctx: Context): Int = prefs(ctx).getInt(KEY_LIMIT, GameConfig.MOVE_LIMIT_DEFAULT).let { if (it in GameConfig.MOVE_LIMITS) it else 0 }

    fun setUndo(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_UNDO, v).apply()
    fun setRotate(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_ROTATE, v).apply()
    fun setBikjang(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_BIKJANG, v).apply()
    fun setLimit(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_LIMIT, v).apply()

    /** 진영별 마지막 상차림 */
    fun formation(ctx: Context, side: Int): Formation =
        Formation.ALL[prefs(ctx).getInt(KEY_FORM + side, 0).coerceIn(0, Formation.ALL.size - 1)]

    fun setFormation(ctx: Context, side: Int, f: Formation) = prefs(ctx).edit().putInt(KEY_FORM + side, f.ordinal).apply()

    /** 모두 기본값으로 (테스트용) */
    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()

    /** 다음 수 제한 (없음 → 100 → 150 → 200 → 없음) */
    fun nextLimit(v: Int): Int {
        val a = GameConfig.MOVE_LIMITS
        val i = a.indexOf(v)
        return a[(i + 1) % a.size]
    }

    /**
     * 옵션 버튼 네 개 [무르기][판 회전][빅장][수 제한]. 누르면 바로 저장하고 글자·색이 바뀐다 (켬 = 금색, 끔 = 회색).
     * onChange 는 바꾼 뒤 불린다.
     */
    fun optionButtons(ctx: Context, sp: Float, onChange: (() -> Unit)? = null): Array<GameButton> {
        val undoB = GameButton(ctx, Theme.GRAY, sp)
        val rotB = GameButton(ctx, Theme.GRAY, sp)
        val bikB = GameButton(ctx, Theme.GRAY, sp)
        val limB = GameButton(ctx, Theme.GRAY, sp)
        fun refresh() {
            val u = undo(ctx)
            undoB.text = if (u) "무르기 1회 켬" else "무르기 끔"
            undoB.color = if (u) Theme.GOLD_DARK else Theme.GRAY
            val r = rotate(ctx)
            rotB.text = if (r) "판 회전 켬" else "판 회전 끔"
            rotB.color = if (r) Theme.GOLD_DARK else Theme.GRAY
            val b = bikjang(ctx)
            bikB.text = if (b) "빅장 무승부 켬" else "빅장 무승부 끔"
            bikB.color = if (b) Theme.GOLD_DARK else Theme.GRAY
            val l = limit(ctx)
            limB.text = if (l == 0) "수 제한 없음" else "수 제한 " + l + "수"
            limB.color = if (l != 0) Theme.GOLD_DARK else Theme.GRAY
            undoB.contentDescription = undoB.text
            rotB.contentDescription = rotB.text
            bikB.contentDescription = bikB.text
            limB.contentDescription = limB.text
        }
        fun tapped(b: GameButton) {
            b.bounce()
            refresh()
            onChange?.invoke()
        }
        undoB.setOnClickListener { setUndo(ctx, !undo(ctx)); tapped(undoB) }
        rotB.setOnClickListener { setRotate(ctx, !rotate(ctx)); tapped(rotB) }
        bikB.setOnClickListener { setBikjang(ctx, !bikjang(ctx)); tapped(bikB) }
        limB.setOnClickListener { setLimit(ctx, nextLimit(limit(ctx))); tapped(limB) }
        refresh()
        return arrayOf(undoB, rotB, bikB, limB)
    }

    /** 옵션 설명 (설정 팝업·시작 화면 공통) */
    const val HELP = "무르기 — 방금 둔 수를 상대가 두기 전에 한 판에 한 번 물리기\n" +
        "판 회전 — 차례가 바뀌면 판을 180° 돌려 마주 앉아 두기\n" +
        "빅장 — 궁끼리 가로막는 것 없이 마주 보면 다음 수에 풀어야 해요. 못 풀면 무승부\n" +
        "수 제한 — 정한 수를 다 두면 점수로 승패 (차13 포7 마5 상3 사3 졸2, 한 덤 1.5)"

    /** 허브 [장기 설정] 팝업 */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "장기 설정", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, HELP, 12.5f, Theme.TEXT_SUB).apply { setLineSpacing(0f, 1.15f) }, Ui.wrapGap(ctx, 6f))
        val b = optionButtons(ctx, 15f)
        card.addView(Ui.buttonRow(ctx, b[0], b[1], heightDp = 44f), Ui.gap(ctx, 10f))
        card.addView(Ui.buttonRow(ctx, b[2], b[3], heightDp = 44f), Ui.gap(ctx, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            LinearLayout.LayoutParams(Theme.MATCH, Theme.dp(ctx, 42f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 500f))
        return popup
    }
}
