package com.siwoo.party.games.forehead.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Gravity
import android.widget.LinearLayout
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.forehead.ForeheadWords
import com.siwoo.party.games.forehead.GameConfig

/**
 * 이마 퀴즈 저장값 (SharedPreferences "forehead")
 * - words: 카테고리별 제시어 JSON (공통 ListEditor 로 편집)
 * - touch: 터치로 조작 (센서가 불안정한 기기용, 기본 끔. 가속도 센서가 없으면 언제나 터치)
 * - laps: 판 수 1~2바퀴 / category: 마지막으로 고른 카테고리
 */
object ForeheadStore {
    const val PREFS = "forehead"
    const val KEY_WORDS = "words"
    const val KEY_TOUCH = "touch"
    const val KEY_LAPS = "laps"
    const val KEY_CATEGORY = "category"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun words(ctx: Context): List<List<String>> = ForeheadWords.decode(prefs(ctx).getString(KEY_WORDS, null))

    fun saveWords(ctx: Context, lists: List<List<String>>) {
        prefs(ctx).edit().putString(KEY_WORDS, ForeheadWords.encode(ForeheadWords.sanitizeAll(lists))).apply()
    }

    fun touch(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TOUCH, false)
    fun setTouch(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_TOUCH, on).apply() }

    fun laps(ctx: Context): Int = prefs(ctx).getInt(KEY_LAPS, GameConfig.LAPS_DEFAULT).coerceIn(GameConfig.LAPS_MIN, GameConfig.LAPS_MAX)
    fun setLaps(ctx: Context, n: Int) { prefs(ctx).edit().putInt(KEY_LAPS, n.coerceIn(GameConfig.LAPS_MIN, GameConfig.LAPS_MAX)).apply() }

    fun category(ctx: Context): Int = prefs(ctx).getInt(KEY_CATEGORY, GameConfig.MIXED).let {
        if (it in 0 until GameConfig.CATEGORY_COUNT) it else GameConfig.MIXED
    }
    fun setCategory(ctx: Context, k: Int) { prefs(ctx).edit().putInt(KEY_CATEGORY, k).apply() }

    /** 가속도 센서가 있는지 */
    fun hasAccelerometer(ctx: Context): Boolean =
        (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /** 제시어 편집 (카테고리 6탭) */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "이마 퀴즈 제시어",
            tabs = ForeheadWords.CATEGORIES,
            tabColors = ForeheadArt.CATEGORY_COLORS.toList(),
            lists = words(ctx),
            defaults = ForeheadWords.DEFAULT,
            maxItems = GameConfig.MAX_WORDS,
            maxLen = GameConfig.MAX_WORD_LEN,
            hint = "예) 카피바라",
        ) { result -> saveWords(ctx, result) }
    }

    /** 허브의 [게임 설정]: 제시어 편집 · 판 수 · 터치 조작 (누르는 즉시 저장) */
    fun showSettings(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, 16f)
        var popup: PopupLayer.Popup? = null
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "이마 퀴즈 설정", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Ui.button(ctx, "닫기", Theme.BLUE, 14f) { popup?.dismiss() }, Theme.lp(Theme.dp(ctx, 76f), Theme.dp(ctx, 36f)))
        card.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP))

        // 제시어
        val wordRow = row(ctx, "제시어", "카테고리 6개 × 기본 ${GameConfig.DEFAULT_WORDS}단어 · 추가·수정")
        wordRow.addView(Ui.button(ctx, "제시어 편집", Theme.Party.NEON_PURPLE, 15f) { showEditor(popups) },
            Theme.lp(Theme.dp(ctx, 140f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 10f) })
        card.addView(wordRow, Ui.gap(ctx, 10f))

        // 판 수
        val lapRow = row(ctx, "판 수", "모두 한 번씩 = 1바퀴")
        val lapButtons = ArrayList<GameButton>()
        fun refreshLaps() {
            val cur = laps(ctx)
            for ((k, b) in lapButtons.withIndex()) b.color = if (GameConfig.LAPS_MIN + k == cur) Theme.GOLD_DARK else Theme.GRAY
        }
        for (n in GameConfig.LAPS_MIN..GameConfig.LAPS_MAX) {
            val b = Ui.button(ctx, "${n}바퀴", Theme.GRAY, 15f) { b -> setLaps(ctx, n); b.bounce(); refreshLaps() }
            b.contentDescription = "${n}바퀴"
            lapButtons.add(b)
            lapRow.addView(b, Theme.lp(Theme.dp(ctx, 76f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 8f) })
        }
        refreshLaps()
        card.addView(lapRow, Ui.gap(ctx, 10f))

        // 터치 조작
        val hasSensor = hasAccelerometer(ctx)
        val touchRow = row(ctx, "터치로 조작", if (hasSensor) "센서가 불안정하면: 왼쪽 터치 = 정답, 오른쪽 = 패스" else "이 기기는 기울기 센서가 없어 항상 터치로 해요")
        var on = touch(ctx) || !hasSensor
        val tb = Ui.button(ctx, "", Theme.GRAY, 15f) { b ->
            if (!hasSensor) return@button
            on = !on
            setTouch(ctx, on)
            b.bounce()
            b.text = if (on) ON else OFF
            b.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        }
        tb.text = if (on) ON else OFF
        tb.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        tb.isEnabled = hasSensor
        tb.contentDescription = "터치로 조작"
        touchRow.addView(tb, Theme.lp(Theme.dp(ctx, 88f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 10f) })
        card.addView(touchRow, Ui.gap(ctx, 10f))

        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 520f))
        return popup
    }

    private fun row(ctx: Context, title: String, sub: String): LinearLayout {
        val row = Theme.horizontal(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        val col = Theme.vertical(ctx)
        col.addView(Theme.text(ctx, title, 16f, Theme.TEXT, bold = true))
        col.addView(Theme.text(ctx, sub, 12f, Theme.TEXT_SUB).apply { maxLines = 2 }, Ui.gap(ctx, 3f))
        row.addView(col, Theme.lp(0, Theme.WRAP, 1f))
        return row
    }

    private const val ON = "켬"
    private const val OFF = "끔"
}
