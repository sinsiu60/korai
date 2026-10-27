package com.siwoo.marble.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.siwoo.marble.GameConfig
import com.siwoo.marble.logic.GameEngine
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.PlayerSetup

/**
 * 타이틀 화면: "시우 마블" / "시우 공방" → [게임 시작]
 * → 아래에서 올라오는 카드형 팝업: 2인/3인/4인 → 같은 팝업에서 이름·말 색상(중복 불가)·게임 방식
 * → [시작] → 팝업이 닫히고 순서 추첨 연출 → 게임 화면
 */
class TitleActivity : Activity() {

    private lateinit var storage: GameStorage
    private lateinit var popups: PopupLayer
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())

    private var count = 2
    private var turnLimit = GameConfig.DEFAULT_TURN_LIMIT
    private val colorOf = IntArray(GameConfig.MAX_PLAYERS) { it }
    private val names = arrayOf("플레이어1", "플레이어2", "플레이어3", "플레이어4")
    private var setupPopup: PopupLayer.Popup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = GameStorage(this)
        Immersive.apply(window)
        root = FrameLayout(this)
        root.background = Theme.background()
        root.addView(TitleArt(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER }
        val title = TitleText(this)
        col.addView(title, LinearLayout.LayoutParams(Theme.dp(this, 420f), Theme.dp(this, 110f)))
        col.addView(Theme.text(this, "시우 공방", 20f, Theme.GOLD_LIGHT, bold = true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
        }, Theme.lp(Theme.WRAP, Theme.WRAP))
        val start = GameButton(this, Theme.GOLD_DARK, 22f).apply { text = "게임 시작" }
        start.setOnClickListener { start.bounce(); openSetup() }
        col.addView(start, LinearLayout.LayoutParams(Theme.dp(this, 240f), Theme.dp(this, 60f)).apply { topMargin = Theme.dp(this@TitleActivity, 28f) })
        val saved = storage.load()
        if (saved != null && !saved.gameOver) {
            val cont = GameButton(this, Theme.BLUE, 18f).apply {
                text = "이어하기  (" + saved.players.filter { !it.bankrupt }.joinToString(", ") { it.name } + ")"
                maxLines = 1
            }
            cont.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            col.addView(cont, LinearLayout.LayoutParams(Theme.dp(this, 320f), Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@TitleActivity, 12f) })
        }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        if (intent.getBooleanExtra(EXTRA_OPEN_SETUP, false)) root.post { openSetup() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (popups.handleBack()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ─────────────────────────────────────────────
    // 시작 팝업 (아래에서 올라옴)
    // ─────────────────────────────────────────────

    private fun sheetCard(): FrameLayout = FrameLayout(this).apply {
        background = Theme.verticalCard(this@TitleActivity, Theme.CARD_LIGHT, Theme.CARD, 22f)
        elevation = Theme.dpf(this@TitleActivity, 14f)
        val p = Theme.dp(this@TitleActivity, 16f)
        setPadding(p, Theme.dp(this@TitleActivity, 12f), p, Theme.dp(this@TitleActivity, 12f))
    }

    fun openSetup() {
        if (setupPopup != null) return
        val card = sheetCard()
        card.addView(countStep(card))
        val w = minOf(root.width - Theme.dp(this, 24f), Theme.dp(this, 640f))
        setupPopup = popups.show(card, cancelable = true, widthPx = w, fromBottom = true, onDismiss = { setupPopup = null })
    }

    /** 1단계: 몇 명? */
    private fun countStep(card: FrameLayout): View {
        val box = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(Theme.text(this, "몇 명이서 할까요?", 24f, Theme.GOLD, bold = true))
        box.addView(Theme.text(this, "한 대의 기기를 돌려가며 함께 합니다", 13f, Theme.TEXT_SUB), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 4f) })
        val row = Theme.horizontal(this)
        for (n in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) {
            val b = GameButton(this, if (n == count) Theme.GOLD_DARK else Theme.BLUE, 28f).apply { text = "${n}인" }
            b.setOnClickListener {
                count = n
                b.bounce() // 눌렸다 튀어오름
                handler.postDelayed({ switchStep(card, playerStep(card)) }, 300)
            }
            row.addView(b, Theme.lp(Theme.dp(this, 150f), Theme.dp(this, 96f)).apply {
                leftMargin = Theme.dp(this@TitleActivity, 8f); rightMargin = Theme.dp(this@TitleActivity, 8f)
            })
        }
        box.addView(row, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 18f); bottomMargin = Theme.dp(this@TitleActivity, 8f) })
        return box
    }

    private fun switchStep(card: FrameLayout, next: View) {
        val old = if (card.childCount > 0) card.getChildAt(0) else null
        next.alpha = 0f
        card.addView(next)
        old?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction { card.removeView(old) }?.start()
        next.animate().alpha(1f).setDuration(220).start()
    }

    /** 2단계: 플레이어별 이름 + 말 색상, 게임 방식, 현실 벌칙 */
    private fun playerStep(card: FrameLayout): View {
        val box = Theme.vertical(this)
        val head = Theme.horizontal(this)
        head.addView(Theme.text(this, "${count}인 플레이어 설정", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Theme.text(this, "말 색은 겹칠 수 없어요 · 순서는 시작할 때 추첨", 11f, Theme.TEXT_SUB))
        box.addView(head)

        // 남은 색이 겹치지 않게 정리
        for (i in 0 until count) for (k in 0 until i) if (colorOf[k] == colorOf[i]) {
            colorOf[i] = Theme.PLAYER_COLORS.indices.first { c -> (0 until i).none { colorOf[it] == c } }
        }

        val grid = Theme.vertical(this)
        val swatchRows = ArrayList<LinearLayout>()
        val edits = ArrayList<EditText>()
        fun refreshSwatches() {
            for (i in 0 until count) {
                val row = swatchRows[i]
                for (c in 0 until row.childCount) {
                    val v = row.getChildAt(c) as TextView
                    val sel = colorOf[i] == c
                    val taken = (0 until count).any { it != i && colorOf[it] == c }
                    v.background = Theme.oval(Theme.PLAYER_COLORS[c], if (sel) Theme.GOLD else Color.WHITE, Theme.dp(this, if (sel) 3f else 1f))
                    v.text = if (sel) "✓" else ""
                    v.setTextColor(Theme.textOn(Theme.PLAYER_COLORS[c]))
                    v.alpha = if (taken) 0.2f else 1f
                }
            }
        }
        var line: LinearLayout? = null
        for (i in 0 until count) {
            if (i % 2 == 0) {
                line = Theme.horizontal(this)
                grid.addView(line, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 6f) })
            }
            val pc = Theme.horizontal(this).apply {
                background = Theme.card(this@TitleActivity, 0x33000000, 14f)
                val p = Theme.dp(this@TitleActivity, 8f)
                setPadding(p, Theme.dp(this@TitleActivity, 4f), p, Theme.dp(this@TitleActivity, 4f))
            }
            val edit = EditText(this).apply {
                setText(names[i])
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.WHITE)
                setHintTextColor(0x88FFFFFF.toInt())
                hint = "이름"
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(8))
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                setSelectAllOnFocus(true)
                background = null
            }
            edits.add(edit)
            pc.addView(edit, Theme.lp(0, Theme.WRAP, 1f))
            val sw = Theme.horizontal(this)
            for (c in Theme.PLAYER_COLORS.indices) {
                val dot = TextView(this).apply {
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    contentDescription = Theme.PLAYER_COLOR_NAMES[c]
                    setOnClickListener {
                        if ((0 until count).none { it != i && colorOf[it] == c }) {
                            colorOf[i] = c
                            refreshSwatches()
                        }
                    }
                }
                sw.addView(dot, LinearLayout.LayoutParams(Theme.dp(this, 32f), Theme.dp(this, 32f)).apply { leftMargin = Theme.dp(this@TitleActivity, 4f) })
            }
            swatchRows.add(sw)
            pc.addView(sw)
            line!!.addView(pc, Theme.lp(0, Theme.WRAP, 1f).apply {
                if (i % 2 == 1) leftMargin = Theme.dp(this@TitleActivity, 8f)
            })
        }
        if (count % 2 == 1) line!!.addView(View(this), Theme.lp(0, 1, 1f).apply { leftMargin = Theme.dp(this@TitleActivity, 8f) })
        refreshSwatches()
        box.addView(grid)

        // 게임 방식
        val modes = Theme.horizontal(this)
        modes.addView(Theme.text(this, "게임 방식", 13f, Theme.TEXT_SUB))
        val modeButtons = ArrayList<GameButton>()
        for (v in GameConfig.TURN_LIMIT_OPTIONS) {
            val b = GameButton(this, Theme.GRAY, 12f).apply { text = if (v == 0) "최후 1인" else "${v}라운드" }
            b.setOnClickListener {
                turnLimit = v
                for ((k, mb) in modeButtons.withIndex()) mb.color = if (GameConfig.TURN_LIMIT_OPTIONS[k] == turnLimit) Theme.GOLD_DARK else Theme.GRAY
            }
            b.color = if (v == turnLimit) Theme.GOLD_DARK else Theme.GRAY
            modeButtons.add(b)
            modes.addView(b, Theme.lp(0, Theme.dp(this, 38f), 1f).apply { leftMargin = Theme.dp(this@TitleActivity, 6f) })
        }
        box.addView(modes, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 8f) })

        // 현실 벌칙 모드 + 문구 편집 (PART 12)
        val real = Theme.horizontal(this)
        val realToggle = GameButton(this, if (storage.realPenaltyMode) Theme.RED_DARK else Theme.GRAY, 12f)
        fun refreshReal() {
            realToggle.text = "현실 벌칙: " + if (storage.realPenaltyMode) "켬" else "끔"
            realToggle.color = if (storage.realPenaltyMode) Theme.RED_DARK else Theme.GRAY
        }
        refreshReal()
        realToggle.setOnClickListener { storage.realPenaltyMode = !storage.realPenaltyMode; refreshReal() }
        real.addView(Theme.text(this, "대형 벌칙 때 현실 벌칙도 함께", 12f, Theme.TEXT_SUB), Theme.lp(0, Theme.WRAP, 1f))
        real.addView(realToggle, Theme.lp(Theme.dp(this, 130f), Theme.dp(this, 36f)))
        val edit = GameButton(this, Theme.BLUE, 12f).apply { text = "문구 편집 (${storage.realPenalties.size})" }
        edit.setOnClickListener { openPhraseEditor { edit.text = "문구 편집 (${storage.realPenalties.size})" } }
        real.addView(edit, Theme.lp(Theme.dp(this, 130f), Theme.dp(this, 36f)).apply { leftMargin = Theme.dp(this@TitleActivity, 6f) })
        box.addView(real, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 6f) })

        // 버튼
        val buttons = Theme.horizontal(this)
        val back = GameButton(this, Theme.GRAY).apply { text = "뒤로" }
        back.setOnClickListener { switchStep(card, countStep(card)) }
        buttons.addView(back, Theme.lp(0, Theme.dp(this, 46f), 1f))
        val go = GameButton(this, Theme.GREEN_DARK, 20f).apply { text = "시작" }
        go.setOnClickListener {
            for (i in 0 until count) names[i] = edits[i].text.toString().trim().ifEmpty { "플레이어${i + 1}" }
            startGame()
        }
        buttons.addView(go, Theme.lp(0, Theme.dp(this, 46f), 2f).apply { leftMargin = Theme.dp(this@TitleActivity, 10f) })
        box.addView(buttons, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 10f) })

        // 작은 화면에서도 넘치지 않게 스크롤
        return ScrollView(this).apply {
            isFillViewport = true
            addView(box)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, minOf(root.height - Theme.dp(this@TitleActivity, 40f), Theme.dp(this@TitleActivity, 330f)))
        }
    }

    /** 현실 벌칙 문구 추가/수정/삭제 (앱 내부 저장) */
    private fun openPhraseEditor(onSaved: () -> Unit) {
        val card = Theme.vertical(this).apply {
            background = Theme.verticalCard(this@TitleActivity, Theme.CARD_LIGHT, Theme.CARD, 18f)
            elevation = Theme.dpf(this@TitleActivity, 16f)
            val p = Theme.dp(this@TitleActivity, 14f)
            setPadding(p, p, p, p)
        }
        card.addView(Theme.text(this, "현실 벌칙 문구", 18f, Theme.GOLD, bold = true))
        val list = Theme.vertical(this)
        val edits = ArrayList<EditText>()
        fun addRow(initial: String) {
            if (edits.size >= GameStorage.MAX_REAL_PENALTIES) return
            val row = Theme.horizontal(this)
            val e = EditText(this).apply {
                setText(initial)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                setHintTextColor(0x88FFFFFF.toInt())
                hint = "예) 노래 한 소절 부르기"
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(GameStorage.MAX_REAL_PENALTY_LEN))
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            }
            edits.add(e)
            row.addView(e, Theme.lp(0, Theme.WRAP, 1f))
            val del = GameButton(this, Theme.RED_DARK, 12f).apply { text = "삭제" }
            del.setOnClickListener { edits.remove(e); list.removeView(row) }
            row.addView(del, Theme.lp(Theme.dp(this, 64f), Theme.dp(this, 34f)))
            list.addView(row)
        }
        for (s in storage.realPenalties) addRow(s)
        val scroll = ScrollView(this).apply { addView(list) }
        card.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@TitleActivity, 6f) })
        val buttons = Theme.horizontal(this)
        var popup: PopupLayer.Popup? = null
        val add = GameButton(this, Theme.BLUE, 14f).apply { text = "+ 추가" }
        add.setOnClickListener { addRow(""); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        val reset = GameButton(this, Theme.GRAY, 14f).apply { text = "기본값" }
        reset.setOnClickListener {
            edits.clear(); list.removeAllViews()
            for (s in GameStorage.DEFAULT_REAL_PENALTIES) addRow(s)
        }
        val save = GameButton(this, Theme.GREEN_DARK, 14f).apply { text = "저장" }
        save.setOnClickListener {
            val v = edits.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
            storage.realPenalties = if (v.isEmpty()) GameStorage.DEFAULT_REAL_PENALTIES else v
            popup?.dismiss()
            onSaved()
        }
        buttons.addView(add, Theme.lp(0, Theme.dp(this, 42f), 1f))
        buttons.addView(reset, Theme.lp(0, Theme.dp(this, 42f), 1f).apply { leftMargin = Theme.dp(this@TitleActivity, 6f) })
        buttons.addView(save, Theme.lp(0, Theme.dp(this, 42f), 1f).apply { leftMargin = Theme.dp(this@TitleActivity, 6f) })
        card.addView(buttons, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 8f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 460f), heightPx = (root.height * 0.9f).toInt())
    }

    // ─────────────────────────────────────────────
    // 시작 → 순서 추첨
    // ─────────────────────────────────────────────

    private fun startGame() {
        val setups = (0 until count).map { PlayerSetup(names[it], colorOf[it]) }
        val state = GameEngine.newGame(setups, turnLimit)
        storage.save(state)
        setupPopup?.dismiss()
        handler.postDelayed({ showLottery(state) }, 250)
    }

    /** 순서 추첨: 각 순번 칸에서 이름이 빠르게 바뀌다가 하나씩 멈춘다 */
    private fun showLottery(state: GameState) {
        val speed = storage.speedFactor
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@TitleActivity, Theme.CARD_LIGHT, Theme.CARD, 22f)
            elevation = Theme.dpf(this@TitleActivity, 14f)
            val p = Theme.dp(this@TitleActivity, 20f)
            setPadding(p * 2, p, p * 2, p)
        }
        val title = Theme.text(this, "순서 추첨!", 26f, Theme.GOLD, bold = true)
        card.addView(title)
        val slots = ArrayList<TextView>()
        for (k in state.players.indices) {
            val row = Theme.horizontal(this)
            row.addView(Theme.text(this, "${k + 1}번", 18f, Theme.TEXT_SUB, bold = true), Theme.lp(Theme.dp(this, 56f), Theme.WRAP))
            val slot = Theme.text(this, "?", 22f, Theme.TEXT, bold = true).apply {
                gravity = Gravity.CENTER
                background = Theme.card(this@TitleActivity, 0x33000000, 12f)
            }
            slots.add(slot)
            row.addView(slot, Theme.lp(Theme.dp(this, 220f), Theme.dp(this, 44f)))
            card.addView(row, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@TitleActivity, 8f) })
        }
        popups.show(card, cancelable = false)
        val all = state.players.map { it.name }
        val stopped = BooleanArray(slots.size)
        var tick = 0
        val spin = object : Runnable {
            override fun run() {
                tick++
                for ((k, s) in slots.withIndex()) if (!stopped[k]) s.text = all[(tick + k) % all.size]
                if (stopped.any { !it }) handler.postDelayed(this, 70)
            }
        }
        handler.post(spin)
        for ((k, p) in state.players.withIndex()) {
            handler.postDelayed({
                stopped[k] = true
                val color = Theme.playerColor(p.colorIndex)
                slots[k].text = "● " + p.name
                slots[k].setTextColor(color)
                slots[k].background = Theme.card(this, 0x55000000, 12f, Theme.GOLD, 2f)
                slots[k].scaleX = 1.3f; slots[k].scaleY = 1.3f
                slots[k].animate().scaleX(1f).scaleY(1f).setDuration(250).start()
            }, ((900 + k * 500) * speed).toLong().coerceAtLeast(300L + k * 150L))
        }
        val end = ((900 + state.players.size * 500 + 700) * speed).toLong().coerceAtLeast(1200L)
        handler.postDelayed({ title.text = "게임 시작!" }, end - 300)
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, end + 400)
    }

    companion object {
        const val EXTRA_OPEN_SETUP = "openSetup"
    }

    /** 제목: 굵은 금색 그라데이션 글씨 + 짙은 외곽선 */
    private class TitleText(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF060A24.toInt()
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0x55FFC93C }
        private var base = 0f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val size = h * 0.72f
            fill.textSize = size; outline.textSize = size; glow.textSize = size
            outline.strokeWidth = size * 0.13f
            glow.strokeWidth = size * 0.3f
            glow.style = Paint.Style.STROKE
            base = h * 0.78f
            fill.shader = LinearGradient(0f, base - size, 0f, base, Theme.GOLD_LIGHT, Theme.GOLD_DARK, Shader.TileMode.CLAMP)
        }

        override fun onDraw(c: Canvas) {
            val x = width / 2f
            c.drawText(TEXT, x, base, glow)
            c.drawText(TEXT, x, base, outline)
            c.drawText(TEXT, x, base, fill)
        }

        companion object { private const val TEXT = "시우 마블" }
    }

    /** 배경 장식: 흩어진 주사위와 금색 별 (한 번만 그림) */
    private class TitleArt(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val s = h * 0.12f
            dice(c, w * 0.12f, h * 0.25f, s, -15f, 5)
            dice(c, w * 0.86f, h * 0.7f, s * 1.1f, 20f, 3)
            dice(c, w * 0.9f, h * 0.18f, s * 0.7f, 8f, 1)
            dice(c, w * 0.1f, h * 0.78f, s * 0.8f, 30f, 6)
            p.color = 0x66FFC93C
            for (k in 0 until 18) {
                val x = ((k * 97) % 100) / 100f * w
                val y = ((k * 53 + 11) % 100) / 100f * h
                c.drawCircle(x, y, h * (0.004f + (k % 3) * 0.002f), p)
            }
        }

        private fun dice(c: Canvas, x: Float, y: Float, s: Float, rot: Float, v: Int) {
            c.save()
            c.translate(x, y)
            c.rotate(rot)
            r.set(-s / 2, -s / 2, s / 2, s / 2)
            p.color = 0x33FFFFFF
            c.drawRoundRect(r, s * 0.2f, s * 0.2f, p)
            p.color = 0x55FFC93C
            val d = s * 0.25f
            val pts = when (v) {
                1 -> floatArrayOf(0f, 0f)
                3 -> floatArrayOf(-d, -d, 0f, 0f, d, d)
                5 -> floatArrayOf(-d, -d, d, -d, 0f, 0f, -d, d, d, d)
                else -> floatArrayOf(-d, -d, d, -d, -d, 0f, d, 0f, -d, d, d, d)
            }
            var k = 0
            while (k < pts.size) { c.drawCircle(pts[k], pts[k + 1], s * 0.08f, p); k += 2 }
            c.restore()
        }
    }
}
