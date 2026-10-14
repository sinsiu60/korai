package com.siwoo.marble.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.siwoo.marble.GameConfig
import com.siwoo.marble.logic.GameEngine
import com.siwoo.marble.model.PlayerSetup

/**
 * 시작 화면: 이어하기 / 새 게임, 플레이어 수·이름·말 색상, 게임 방식(턴 제한) 선택.
 */
class SetupActivity : Activity() {

    private lateinit var storage: GameStorage
    private var count = 2
    private var turnLimit = GameConfig.DEFAULT_TURN_LIMIT
    private val colorOf = IntArray(GameConfig.MAX_PLAYERS) { it }

    private val rows = ArrayList<LinearLayout>()
    private val nameEdits = ArrayList<EditText>()
    private val swatchRows = ArrayList<LinearLayout>()
    private val countButtons = ArrayList<Button>()
    private val limitButtons = ArrayList<Button>()
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = GameStorage(this)
        setContentView(buildUi())
        refresh()
        val saved = storage.load()
        if (saved != null && !saved.gameOver) {
            val names = saved.players.filter { !it.bankrupt }.joinToString(", ") { it.name }
            val round = if (saved.turnLimit > 0) "${saved.round}/${saved.turnLimit}라운드" else "${saved.round}라운드"
            dialog = GameDialog.show(
                this, "진행 중인 게임이 있어요",
                "$names\n$round 진행 중",
                listOf(
                    DialogButton("이어하기", Ui.GREEN) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    DialogButton("새 게임", Ui.BLUE) { },
                ),
            )
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val pad = Ui.dp(this, 18f)
        val col = Ui.vertical(this).apply {
            setPadding(pad, pad, pad, pad)
            // 시작하자마자 이름 칸에 포커스가 가서 키보드가 뜨지 않도록
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }

        col.addView(Ui.text(this, "시우 마블", 40f, 0xFF1B5E20.toInt(), bold = true).apply { gravity = Gravity.CENTER }, Ui.matchWrap())
        col.addView(
            Ui.text(this, "한 대로 돌려 하는 보드게임 · 2~4인", 16f, Ui.SUB_TEXT).apply { gravity = Gravity.CENTER },
            Ui.matchWrap(Ui.dp(this, 2f)),
        )

        // 인원
        col.addView(section("플레이어 수"), Ui.matchWrap(Ui.dp(this, 22f)))
        val countRow = Ui.horizontal(this)
        for (n in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) {
            val b = Ui.bigButton(this, "${n}명", Ui.GRAY)
            b.setOnClickListener { count = n; refresh() }
            countButtons.add(b)
            countRow.addView(b, Ui.weighted(1f, Ui.dp(this, 4f)))
        }
        col.addView(countRow, Ui.matchWrap(Ui.dp(this, 8f)))

        // 플레이어별 이름·색
        for (i in 0 until GameConfig.MAX_PLAYERS) {
            val row = Ui.vertical(this).apply {
                background = Ui.rounded(Color.WHITE, Ui.dp(this@SetupActivity, 14f).toFloat())
                val p = Ui.dp(this@SetupActivity, 12f)
                setPadding(p, p, p, p)
            }
            row.addView(Ui.text(this, "플레이어 ${i + 1}", 15f, Ui.SUB_TEXT, bold = true))
            val edit = EditText(this).apply {
                setText(DEFAULT_NAMES[i])
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                filters = arrayOf(InputFilter.LengthFilter(8))
                hint = "이름"
                setTextColor(Ui.TEXT)
                setHintTextColor(0xFF9E9E9E.toInt())
                setSelectAllOnFocus(true)
            }
            row.addView(edit, Ui.matchWrap())
            val swatches = Ui.horizontal(this)
            for (c in GameConfig.PLAYER_COLORS.indices) {
                val sw = TextView(this).apply {
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    contentDescription = GameConfig.PLAYER_COLOR_NAMES[c]
                    setOnClickListener { pickColor(i, c) }
                }
                val size = Ui.dp(this, 40f)
                swatches.addView(sw, LinearLayout.LayoutParams(size, size).apply { rightMargin = Ui.dp(this@SetupActivity, 6f) })
            }
            row.addView(swatches, Ui.matchWrap(Ui.dp(this, 6f)))
            rows.add(row)
            nameEdits.add(edit)
            swatchRows.add(swatches)
            col.addView(row, Ui.matchWrap(Ui.dp(this, 10f)))
        }

        // 게임 방식
        col.addView(section("게임 방식"), Ui.matchWrap(Ui.dp(this, 22f)))
        val opts = GameConfig.TURN_LIMIT_OPTIONS
        var line: LinearLayout? = null
        for ((k, v) in opts.withIndex()) {
            if (k % 2 == 0) {
                line = Ui.horizontal(this)
                col.addView(line, Ui.matchWrap(Ui.dp(this, 8f)))
            }
            val b = Ui.bigButton(this, if (v == 0) "최후 1인까지" else "${v}라운드 제한", Ui.GRAY, textSp = 18f)
            b.setOnClickListener { turnLimit = v; refresh() }
            limitButtons.add(b)
            line!!.addView(b, Ui.weighted(1f, Ui.dp(this, 4f)))
        }
        col.addView(
            Ui.text(
                this,
                "라운드 제한: 모두가 정해진 횟수만큼 차례를 마치면 총자산(현금+땅+건물) 1위가 승리합니다.\n" +
                    "차례 순서는 시작할 때 무작위로 정해집니다.",
                14f, Ui.SUB_TEXT,
            ),
            Ui.matchWrap(Ui.dp(this, 10f)),
        )

        val start = Ui.bigButton(this, "게임 시작", Ui.GREEN, heightDp = 72f, textSp = 24f)
        start.setOnClickListener { startGame() }
        col.addView(start, Ui.matchWrap(Ui.dp(this, 22f)))

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(col)
        }
    }

    private fun section(title: String) = Ui.text(this, title, 18f, Ui.DARK, bold = true)

    private fun pickColor(player: Int, c: Int) {
        for (k in 0 until count) if (k != player && colorOf[k] == c) return // 다른 사람이 쓰는 색
        colorOf[player] = c
        refresh()
    }

    private fun refresh() {
        // 숨겨진 플레이어가 쓰던 색은 해제되므로, 보이는 플레이어끼리 겹치지 않게 정리
        for (i in 0 until count) {
            for (k in 0 until i) {
                if (colorOf[k] == colorOf[i]) {
                    colorOf[i] = GameConfig.PLAYER_COLORS.indices.first { c -> (0 until i).none { colorOf[it] == c } }
                }
            }
        }
        for ((k, b) in countButtons.withIndex()) {
            Ui.setButtonColor(b, if (k + GameConfig.MIN_PLAYERS == count) Ui.BLUE else Ui.GRAY)
        }
        for ((k, b) in limitButtons.withIndex()) {
            Ui.setButtonColor(b, if (GameConfig.TURN_LIMIT_OPTIONS[k] == turnLimit) Ui.BLUE else Ui.GRAY)
        }
        for (i in 0 until GameConfig.MAX_PLAYERS) {
            rows[i].visibility = if (i < count) View.VISIBLE else View.GONE
            val sw = swatchRows[i]
            for (c in 0 until sw.childCount) {
                val v = sw.getChildAt(c) as TextView
                val selected = colorOf[i] == c
                val taken = (0 until count).any { it != i && colorOf[it] == c }
                v.background = Ui.oval(
                    GameConfig.PLAYER_COLORS[c],
                    if (selected) Ui.DARK else Color.WHITE,
                    Ui.dp(this, if (selected) 4f else 2f),
                )
                v.text = if (selected) "✓" else ""
                v.setTextColor(Ui.textOn(GameConfig.PLAYER_COLORS[c]))
                v.alpha = if (taken) 0.2f else 1f
            }
        }
    }

    private fun startGame() {
        val setups = (0 until count).map { i ->
            val name = nameEdits[i].text.toString().trim().ifEmpty { DEFAULT_NAMES[i] }
            PlayerSetup(name, colorOf[i])
        }
        val state = GameEngine.newGame(setups, turnLimit)
        storage.save(state)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private val DEFAULT_NAMES = arrayOf("플레이어1", "플레이어2", "플레이어3", "플레이어4")
    }
}
