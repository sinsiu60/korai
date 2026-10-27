package com.siwoo.party.hub.tools

import android.graphics.Color
import android.graphics.PointF
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import kotlin.random.Random

/**
 * 🔀 사다리 타기: 위에 이름(지금 플레이어 자동, 수정 가능), 아래에 결과(벌칙·순서 등) → 가려진 사다리.
 * ▼(세로줄 칸)을 누르면 그 줄을 따라 색 선이 내려가고 도착한 결과가 빛난다.
 * 위쪽: [−] n줄 [+] [다시 섞기] [전체 공개]
 */
class LadderActivity : ToolActivity(), LadderView.Listener {

    override val toolTitle = "🔀 사다리 타기"

    lateinit var ladderView: LadderView
        private set
    private lateinit var loop: FrameLoop
    private lateinit var namesRow: LinearLayout
    private lateinit var resultsRow: LinearLayout
    private lateinit var countText: TextView
    private lateinit var minus: GameButton
    private lateinit var plus: GameButton
    lateinit var revealButton: GameButton
        private set
    val nameEdits = ArrayList<EditText>()
    val resultEdits = ArrayList<EditText>()
    private val names = Array(Ladder.MAX_COLUMNS) { "" }
    private val results = Array(Ladder.MAX_COLUMNS) { "" }
    private val colors = IntArray(Ladder.MAX_COLUMNS)
    var columns = 0
        private set
    private lateinit var random: Random
    private val pa = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)

    override fun buildBody(): View {
        random = randomOverride ?: Random.Default
        columns = players.size.coerceIn(Ladder.MIN_COLUMNS, Ladder.MAX_COLUMNS)
        for (k in 0 until Ladder.MAX_COLUMNS) {
            names[k] = players.getOrNull(k)?.name ?: "참가자${k + 1}"
            results[k] = DEFAULT_RESULTS[k]
        }
        // 세로줄 색: 플레이어 색 먼저, 나머지는 안 쓰인 색
        val used = players.map { it.colorIndex % Theme.PLAYER_COLORS.size }.distinct().take(Ladder.MAX_COLUMNS)
        val order = used + Theme.PLAYER_COLORS.indices.filter { it !in used }
        for (k in 0 until Ladder.MAX_COLUMNS) colors[k] = Theme.PLAYER_COLORS[order[k % order.size]]

        topSpacer()
        minus = Ui.button(this, "−", Theme.BLUE, 20f) { changeColumns(-1) }
        plus = Ui.button(this, "+", Theme.BLUE, 20f) { changeColumns(+1) }
        countText = Theme.text(this, "", 16f, Theme.GOLD_LIGHT, bold = true).apply { gravity = Gravity.CENTER }
        addTop(minus, 44f)
        topBar.addView(countText, Theme.lp(Theme.dp(this, 46f), Theme.WRAP).apply { leftMargin = Theme.dp(this@LadderActivity, 4f) })
        addTop(plus, 44f, 4f)
        addTop(Ui.button(this, "🎲 다시 섞기", Theme.GOLD_DARK, 15f) { shuffle() }, 124f, 14f)
        revealButton = Ui.button(this, "전체 공개", Theme.GREEN_DARK, 15f) { revealAll() }
        addTop(revealButton, 104f)

        val col = Theme.vertical(this).apply {
            val p = Theme.dp(this@LadderActivity, 12f)
            setPadding(p, Theme.dp(this@LadderActivity, 6f), p, Theme.dp(this@LadderActivity, 8f))
            // 처음에 이름 칸이 포커스를 받아 키보드가 뜨지 않도록 바탕이 포커스를 가진다
            isFocusable = true
            isFocusableInTouchMode = true
        }
        namesRow = Theme.horizontal(this)
        col.addView(namesRow, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)))
        ladderView = LadderView(this)
        ladderView.speedFactor = settings.speedFactor
        ladderView.listener = this
        col.addView(ladderView, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LadderActivity, 2f); bottomMargin = Theme.dp(this@LadderActivity, 2f) })
        resultsRow = Theme.horizontal(this)
        col.addView(resultsRow, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)))
        loop = FrameLoop(ladderView) { dt -> ladderView.tick(dt) }
        rebuild()
        col.requestFocus()
        return col
    }

    private fun collect() {
        for ((k, e) in nameEdits.withIndex()) names[k] = e.text.toString()
        for ((k, e) in resultEdits.withIndex()) results[k] = e.text.toString()
    }

    fun changeColumns(d: Int) {
        val n = (columns + d).coerceIn(Ladder.MIN_COLUMNS, Ladder.MAX_COLUMNS)
        if (n == columns) { beeper.bad(); return }
        beeper.step()
        collect()
        columns = n
        rebuild()
    }

    /** 칸 수가 바뀜: 입력 줄 다시 만들고 새 사다리 */
    private fun rebuild() {
        nameEdits.clear()
        resultEdits.clear()
        namesRow.removeAllViews()
        resultsRow.removeAllViews()
        for (k in 0 until columns) {
            val ne = field(names[k], PartyConfig.MAX_NAME_LEN, "이름", colors[k], name = true)
            nameEdits.add(ne)
            namesRow.addView(ne, cell())
            val re = field(results[k], MAX_RESULT_LEN, "결과", Theme.GRAY, name = false)
            resultEdits.add(re)
            resultsRow.addView(re, cell())
        }
        countText.text = "${columns}줄"
        minus.isEnabled = columns > Ladder.MIN_COLUMNS
        plus.isEnabled = columns < Ladder.MAX_COLUMNS
        newLadder()
    }

    private fun cell() = Theme.lp(0, Theme.MATCH, 1f).apply {
        leftMargin = Theme.dp(this@LadderActivity, 4f); rightMargin = Theme.dp(this@LadderActivity, 4f)
    }

    private fun field(text: String, maxLen: Int, hint: String, color: Int, name: Boolean): EditText = EditText(this).apply {
        setText(text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (name) 16f else 15f)
        setTextColor(if (name) Color.WHITE else Theme.GOLD_LIGHT)
        setHintTextColor(0x88FFFFFF.toInt())
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        this.hint = hint
        gravity = Gravity.CENTER
        isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(maxLen))
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setSelectAllOnFocus(true)
        val p = Theme.dp(this@LadderActivity, 4f)
        setPadding(p, 0, p, 0)
        includeFontPadding = false
        background = plainBg(color, name)
    }

    private fun plainBg(color: Int, name: Boolean) =
        Theme.card(this, if (name) Theme.shade(color, 0.42f) else 0x33000000, 12f, color, 2f)

    private fun newLadder() {
        val l = Ladder.generate(columns, random)
        ladderView.setLadder(l, colors.copyOf(columns))
        for (k in 0 until columns) {
            nameEdits[k].background = plainBg(colors[k], true)
            resultEdits[k].background = plainBg(Theme.GRAY, false)
            resultEdits[k].setTextColor(Theme.GOLD_LIGHT)
        }
        revealButton.isEnabled = true
        loop.stop()
    }

    fun shuffle() {
        beeper.dice()
        collect()
        newLadder()
        ladderView.alpha = 0.3f
        ladderView.animate().alpha(1f).setDuration(t(350)).start()
    }

    override fun onStartTapped(column: Int) {
        if (column >= columns || popups.isShowing) return
        if (ladderView.trace(column)) {
            beeper.step()
            loop.start()
        }
    }

    fun revealAll() {
        beeper.turn()
        ladderView.reveal()
        var k = 0
        for (c in 0 until columns) if (ladderView.trace(c, t(COVER_WAIT_MS) + k * t(STAGGER_MS))) k++
        revealButton.isEnabled = false
        loop.start()
    }

    override fun onArrive(start: Int, end: Int) {
        val col = colors[start]
        resultEdits[end].background = Theme.card(this, Theme.shade(col, 0.55f), 12f, Theme.GOLD, 3f)
        resultEdits[end].setTextColor(Color.WHITE)
        nameEdits[start].background = Theme.card(this, Theme.shade(col, 0.6f), 12f, Theme.GOLD, 3f)
        center(resultEdits[end], pa)
        fx.stars(pa.x, pa.y, 6, t(700))
        val name = nameEdits[start].text.toString().ifBlank { "참가자${start + 1}" }
        val res = resultEdits[end].text.toString().ifBlank { "?" }
        if (ladderView.revealed) {
            beeper.step()
            if (ladderView.allDone()) beeper.good()
        } else {
            val good = res.contains("당첨")
            if (good) beeper.good() else beeper.turn()
            fx.banner(root.width / 2f, root.height * 0.45f, root.width * 0.8f, "$name → $res", "", if (good) BannerTone.GOOD else BannerTone.INFO, t(1500))
        }
        if (ladderView.allDone()) revealButton.isEnabled = false
    }

    private fun center(v: View, out: PointF) {
        v.getLocationInWindow(locA)
        fx.getLocationInWindow(locB)
        out.set((locA[0] - locB[0]) + v.width / 2f, (locA[1] - locB[1]) + v.height / 2f)
    }

    override fun onToolPause() {
        if (::loop.isInitialized) loop.pause()
    }

    override fun onToolResume() {
        if (::ladderView.isInitialized) ladderView.speedFactor = settings.speedFactor
        if (::loop.isInitialized) loop.resume()
    }

    companion object {
        var randomOverride: Random? = null
        const val MAX_RESULT_LEN = 10
        /** 기본 결과 (칸 순서) */
        val DEFAULT_RESULTS = listOf("당첨", "꽝", "꽝", "꽝", "꽝", "꽝")
        /** 전체 공개: 가림막이 걷힌 뒤 시작, 줄마다 간격 (× 속도 배율) */
        const val COVER_WAIT_MS = 350L
        const val STAGGER_MS = 280L
    }
}
