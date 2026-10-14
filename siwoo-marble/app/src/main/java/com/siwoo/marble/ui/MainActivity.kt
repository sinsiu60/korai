package com.siwoo.marble.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.logic.GameEngine
import com.siwoo.marble.logic.GameEvent
import com.siwoo.marble.logic.Money
import com.siwoo.marble.model.GameState
import com.siwoo.marble.model.Pending
import com.siwoo.marble.model.PendingKind
import com.siwoo.marble.model.Player
import com.siwoo.marble.model.Tile
import com.siwoo.marble.model.TileType

/**
 * 게임 화면.
 * 위: 정사각형 보드 / 가운데: 현재 플레이어 정보 + 큰 주사위 버튼 / 아래: 플레이어별 자산 탭
 *
 * 규칙은 GameEngine 이 모두 처리하고, 이 화면은 엔진이 돌려준 GameEvent 를 순서대로 재생한 뒤
 * 남은 선택(pending)이 있으면 다이얼로그로 묻기만 한다.
 */
class MainActivity : Activity(), BoardView.Listener {

    private lateinit var storage: GameStorage
    private lateinit var state: GameState
    private lateinit var engine: GameEngine
    private val beeper = Beeper()

    // ── 뷰 ──
    private lateinit var board: BoardView
    private lateinit var playPanel: LinearLayout
    private lateinit var dot: View
    private lateinit var nameText: TextView
    private lateinit var moneyText: TextView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var rollButton: Button
    private lateinit var escapeButton: Button
    private lateinit var assetScroll: ScrollView
    private lateinit var assetText: TextView
    private val tabViews = ArrayList<TextView>()
    private lateinit var overlay: LinearLayout
    private lateinit var overlayDot: View
    private lateinit var overlayTitle: TextView
    private lateinit var overlayInfo: TextView

    // ── 진행 상태 ──
    private val queue = ArrayDeque<GameEvent>()
    private val logLines = ArrayDeque<String>()
    private var playing = false
    private var paused = false
    private var interrupted = false
    /** "○○님 차례" 화면에서 확인을 누른 플레이어 */
    private var confirmedPlayer = -1
    private var selectedTab = -1
    private var dialog: Dialog? = null

    private val stepMs get() = if (storage.fastMove) GameConfig.MOVE_STEP_FAST_MS else GameConfig.MOVE_STEP_MS
    private val diceMs get() = if (storage.fastMove) GameConfig.DICE_ROLL_FAST_MS else GameConfig.DICE_ROLL_MS
    private val jumpMs get() = if (storage.fastMove) 220L else 450L

    // ─────────────────────────────────────────────
    // 생명주기
    // ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = GameStorage(this)
        val loaded = storage.load()
        if (loaded == null) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        state = loaded
        engine = GameEngine(state)
        beeper.enabled = storage.sound
        setContentView(buildUi())
        board.bind(state)
        if (state.round == 1 && state.lastDie1 == 0) {
            addLog("순서: " + state.players.joinToString(" → ") { it.name })
        } else {
            addLog("저장된 게임을 이어서 합니다.")
        }
        presentState()
    }

    override fun onPause() {
        super.onPause()
        paused = true
        if (!::state.isInitialized) return
        // 진행 중인 애니메이션은 즉시 정리하고 상태만 보존
        if (playing && board.isAnimating) {
            board.cancelAnimations()
            interrupted = true
        }
        storage.save(state)
    }

    override fun onResume() {
        super.onResume()
        paused = false
        if (!::state.isInitialized) return
        beeper.enabled = storage.sound
        if (interrupted) {
            interrupted = false
            flushQueue()
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        if (::board.isInitialized) board.cancelAnimations()
        beeper.release()
        super.onDestroy()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        showDialog(
            "게임 나가기", "지금까지의 진행은 자동 저장됩니다.\n다음에 \"이어하기\"로 계속할 수 있어요.",
            listOf(
                DialogButton("저장하고 나가기", Ui.BLUE) { storage.save(state); finish() },
                DialogButton("계속하기", Ui.GRAY) { },
            ),
            cancelable = true,
        )
    }

    // ─────────────────────────────────────────────
    // 화면 구성 (코드로 생성)
    // ─────────────────────────────────────────────

    private fun buildUi(): View {
        val dm = resources.displayMetrics
        val boardSide = minOf(dm.widthPixels, dm.heightPixels - Ui.dp(this, 300f))

        val root = FrameLayout(this)
        val main = Ui.vertical(this)

        // 보드: 폭에 맞춘 정사각형. 남는 좌우 여백만 배경색 (보드 영역은 비트맵이 덮으므로 배경 없음)
        board = BoardView(this)
        board.listener = this
        val boardHolder = FrameLayout(this)
        if (boardSide < dm.widthPixels) boardHolder.setBackgroundColor(0xFF33691E.toInt())
        boardHolder.addView(board, FrameLayout.LayoutParams(boardSide, boardSide, Gravity.CENTER_HORIZONTAL))
        main.addView(boardHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, boardSide))

        // 가운데 영역 (진행 패널 / 자산 패널)
        val content = FrameLayout(this)
        content.setBackgroundColor(Ui.PANEL)
        content.addView(buildPlayPanel())
        assetText = Ui.text(this, "", 17f).apply {
            val p = Ui.dp(this@MainActivity, 16f)
            setPadding(p, p, p, p)
            setLineSpacing(0f, 1.2f)
        }
        assetScroll = ScrollView(this).apply {
            addView(assetText)
            visibility = View.GONE
        }
        content.addView(assetScroll)
        main.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // 아래 탭
        val tabs = Ui.horizontal(this).apply { setBackgroundColor(Ui.DARK) }
        addTab(tabs, -1)
        for (p in state.players) addTab(tabs, p.id)
        main.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 58f)))

        root.addView(main)
        root.addView(buildOverlay())
        return root
    }

    private fun buildPlayPanel(): View {
        val pad = Ui.dp(this, 12f)
        playPanel = Ui.vertical(this).apply { setPadding(pad, Ui.dp(this@MainActivity, 8f), pad, pad) }

        val row = Ui.horizontal(this)
        dot = Ui.colorDot(this, Color.GRAY, 26f)
        row.addView(dot)
        nameText = Ui.text(this, "", 22f, Ui.TEXT, bold = true).apply {
            setPadding(Ui.dp(this@MainActivity, 10f), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(nameText, Ui.weighted())
        val settings = Ui.bigButton(this, "설정", Ui.GRAY, heightDp = 44f, textSp = 16f)
        settings.setOnClickListener { if (!playing) showSettings() }
        row.addView(settings, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44f)))
        playPanel.addView(row, Ui.matchWrap())

        moneyText = Ui.text(this, "", 18f, Ui.TEXT, bold = true)
        playPanel.addView(moneyText, Ui.matchWrap(Ui.dp(this, 4f)))
        statusText = Ui.text(this, "", 15f, Ui.SUB_TEXT)
        playPanel.addView(statusText, Ui.matchWrap(Ui.dp(this, 2f)))

        logText = Ui.text(this, "", 15f, 0xFF37474F.toInt()).apply {
            background = Ui.rounded(0xFFF1F3F4.toInt(), Ui.dp(this@MainActivity, 10f).toFloat())
            val p = Ui.dp(this@MainActivity, 8f)
            setPadding(p, p / 2, p, p / 2)
            // 화면이 작아 기록이 넘치면 최신 줄이 보이도록 아래로 스크롤
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = false
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scrollLogToBottom() }
        }
        playPanel.addView(logText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = Ui.dp(this@MainActivity, 6f)
        })

        val buttons = Ui.horizontal(this)
        rollButton = Ui.bigButton(this, "주사위 굴리기", Ui.BLUE, heightDp = 68f, textSp = 22f)
        rollButton.setOnClickListener { onRollClicked() }
        buttons.addView(rollButton, LinearLayout.LayoutParams(0, Ui.dp(this, 68f), 2f))
        escapeButton = Ui.bigButton(this, "탈출권\n사용", Ui.GOLD, heightDp = 68f, textSp = 17f)
        escapeButton.setOnClickListener { if (!playing && engine.canUseEscapeCard()) perform { engine.useEscapeCard() } }
        buttons.addView(escapeButton, LinearLayout.LayoutParams(0, Ui.dp(this, 68f), 1f).apply {
            leftMargin = Ui.dp(this@MainActivity, 8f)
        })
        playPanel.addView(buttons, Ui.matchWrap(Ui.dp(this, 8f)))
        return playPanel
    }

    private fun addTab(tabs: LinearLayout, playerId: Int) {
        val tv = Ui.text(this, "", 14f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setOnClickListener { selectTab(playerId) }
        }
        tabViews.add(tv)
        tabs.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun buildOverlay(): View {
        overlay = Ui.vertical(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Ui.DARK)
            isClickable = true // 뒤의 보드 터치 차단
            visibility = View.GONE
            val p = Ui.dp(this@MainActivity, 32f)
            setPadding(p, p, p, p)
        }
        overlayDot = Ui.colorDot(this, Color.GRAY, 96f)
        overlay.addView(overlayDot)
        overlayTitle = Ui.text(this, "", 34f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER }
        overlay.addView(overlayTitle, Ui.matchWrap(Ui.dp(this, 24f)))
        overlayInfo = Ui.text(this, "", 18f, 0xFFCFD8DC.toInt()).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        overlay.addView(overlayInfo, Ui.matchWrap(Ui.dp(this, 12f)))
        val go = Ui.bigButton(this, "확인, 시작합니다", Ui.GREEN, heightDp = 72f, textSp = 22f)
        go.setOnClickListener { onHandoffConfirmed() }
        overlay.addView(go, Ui.matchWrap(Ui.dp(this, 36f)))
        return overlay
    }

    // ─────────────────────────────────────────────
    // 상태 표시
    // ─────────────────────────────────────────────

    /** 이벤트 재생이 끝난 뒤 현재 상태에 맞는 화면/선택지를 띄운다 */
    private fun presentState() {
        updatePanel()
        board.refresh()
        if (state.gameOver) {
            board.selectMode = false
            updateControls()
            showGameOver()
            return
        }
        val p = engine.currentPlayer
        if (confirmedPlayer != p.id) {
            showHandoff(p)
            return
        }
        val pd = state.pending
        if (pd != null) {
            showPending(pd)
            updateControls()
            return
        }
        board.selectMode = p.spaceTravelPending
        updateControls()
    }

    private fun updatePanel() {
        val s = state
        val p = engine.currentPlayer
        dot.background = Ui.oval(color(p), Color.WHITE, Ui.dp(this, 2f))
        nameText.text = if (s.gameOver) "게임 종료" else "${p.name}님 차례"
        moneyText.text = "현금 ${Money.won(p.money)}  ·  총자산 ${Money.won(engine.totalAssets(p.id))}"
        statusText.text = statusLine(p)
        logText.text = logLines.joinToString("\n")
        scrollLogToBottom()

        for ((k, tv) in tabViews.withIndex()) {
            val id = k - 1
            if (id < 0) {
                tv.text = "진행"
            } else {
                val q = s.players[id]
                tv.text = if (q.bankrupt) "${q.name}\n파산" else "${q.name}\n${Money.short(q.money)}"
                tv.setTextColor(if (q.bankrupt) 0xFF90A4AE.toInt() else Color.WHITE)
            }
            tv.background = if (id == selectedTab) {
                Ui.rounded(if (id < 0) 0xFF546E7A.toInt() else color(s.players[id]), 0f)
            } else null
            if (id >= 0 && id == selectedTab && !s.players[id].bankrupt) tv.setTextColor(Ui.textOn(color(s.players[id])))
        }
        if (selectedTab >= 0) assetText.text = assetReport(s.players[selectedTab])
    }

    private fun statusLine(p: Player): String {
        val parts = ArrayList<String>()
        if (p.islandTurns > 0) parts.add("무인도 ${p.islandTurns}턴 남음 (더블이면 탈출)")
        if (p.spaceTravelPending) parts.add("우주여행: 보드에서 이동할 칸을 터치")
        if (state.extraRoll && !playing) parts.add("더블! 한 번 더")
        if (p.escapeCards > 0) parts.add("탈출권 ${p.escapeCards}")
        if (p.tollFreeCards > 0) parts.add("면제권 ${p.tollFreeCards}")
        if (p.halfBuildCards > 0) parts.add("반값건설 ${p.halfBuildCards}")
        val round = if (state.turnLimit > 0) "${state.round}/${state.turnLimit}라운드" else "${state.round}라운드"
        parts.add(0, round)
        return parts.joinToString("  ·  ")
    }

    private fun updateControls() {
        val p = engine.currentPlayer
        val ready = !playing && !state.gameOver && confirmedPlayer == p.id && state.pending == null
        when {
            state.gameOver -> { rollButton.text = "결과 보기"; rollButton.isEnabled = true }
            p.spaceTravelPending -> { rollButton.text = "보드에서 칸 선택"; rollButton.isEnabled = false }
            p.islandTurns > 0 -> { rollButton.text = "탈출 주사위 굴리기"; rollButton.isEnabled = ready }
            state.extraRoll -> { rollButton.text = "한 번 더 굴리기!"; rollButton.isEnabled = ready }
            else -> { rollButton.text = "주사위 굴리기"; rollButton.isEnabled = ready }
        }
        rollButton.alpha = if (rollButton.isEnabled) 1f else 0.45f
        escapeButton.visibility = if (ready && engine.canUseEscapeCard()) View.VISIBLE else View.GONE
    }

    private fun selectTab(id: Int) {
        selectedTab = id
        playPanel.visibility = if (id < 0) View.VISIBLE else View.GONE
        assetScroll.visibility = if (id < 0) View.GONE else View.VISIBLE
        updatePanel()
    }

    private fun assetReport(p: Player): String {
        val sb = StringBuilder()
        sb.append(p.name).append(if (p.bankrupt) "  (파산)" else "").append('\n')
        sb.append("현금 ").append(Money.won(p.money)).append('\n')
        sb.append("총자산 ").append(Money.won(engine.totalAssets(p.id))).append('\n')
        sb.append("출발 통과 ").append(p.laps).append("회\n")
        if (p.escapeCards + p.tollFreeCards + p.halfBuildCards > 0) {
            sb.append("보관 카드: ")
            if (p.escapeCards > 0) sb.append("무인도 탈출권×").append(p.escapeCards).append(' ')
            if (p.tollFreeCards > 0) sb.append("통행료 면제권×").append(p.tollFreeCards).append(' ')
            if (p.halfBuildCards > 0) sb.append("반값 건설권×").append(p.halfBuildCards)
            sb.append('\n')
        }
        val owned = engine.ownedTiles(p.id)
        sb.append("\n보유 땅 ").append(owned.size).append("곳\n")
        if (owned.isEmpty()) sb.append("  (없음)\n")
        for (t in owned) {
            val lv = state.level[t.index]
            sb.append("• ").append(t.name)
            if (t.canBuild) sb.append(" [").append(Tile.LEVEL_NAMES[lv]).append(']')
            sb.append("  통행료 ").append(Money.short(t.toll(lv))).append('\n')
        }
        return sb.toString()
    }

    private fun addLog(text: String) {
        logLines.addLast(text)
        while (logLines.size > 5) logLines.removeFirst()
        if (::logText.isInitialized) {
            logText.text = logLines.joinToString("\n")
            scrollLogToBottom()
        }
    }

    private fun scrollLogToBottom() {
        val layout = logText.layout ?: return
        val bottom = layout.height + logText.totalPaddingTop + logText.totalPaddingBottom - logText.height
        logText.scrollTo(0, if (bottom > 0) bottom else 0)
    }

    private fun color(p: Player) = GameConfig.PLAYER_COLORS[p.colorIndex]

    // ─────────────────────────────────────────────
    // 턴 넘김 화면
    // ─────────────────────────────────────────────

    private fun showHandoff(p: Player) {
        board.selectMode = false
        overlayDot.background = Ui.oval(color(p), Color.WHITE, Ui.dp(this, 4f))
        overlayTitle.text = "${p.name}님 차례"
        val info = StringBuilder()
        info.append("기기를 ").append(p.name).append("님에게 넘겨주세요.\n\n")
        info.append(if (state.turnLimit > 0) "${state.round} / ${state.turnLimit} 라운드" else "${state.round} 라운드")
        info.append("\n현금 ").append(Money.won(p.money))
        if (p.islandTurns > 0) info.append("\n무인도에 갇혀 있습니다 (${p.islandTurns}턴)")
        if (p.spaceTravelPending) info.append("\n우주여행! 원하는 칸으로 날아갈 수 있어요")
        overlayInfo.text = info
        overlay.visibility = View.VISIBLE
        updateControls()
    }

    private fun onHandoffConfirmed() {
        val p = engine.currentPlayer
        confirmedPlayer = p.id
        overlay.visibility = View.GONE
        beeper.turn()
        addLog("── ${p.name}님 차례 ──")
        if (selectedTab >= 0) selectTab(-1)
        presentState()
    }

    // ─────────────────────────────────────────────
    // 행동 → 이벤트 재생
    // ─────────────────────────────────────────────

    private fun onRollClicked() {
        if (state.gameOver) { showGameOver(); return }
        if (playing || !engine.canRoll()) return
        perform { engine.roll() }
    }

    private fun perform(action: () -> List<GameEvent>) {
        if (playing) return
        val events = try {
            action()
        } catch (e: IllegalStateException) {
            presentState(); return
        } catch (e: IllegalArgumentException) {
            presentState(); return
        }
        playing = true
        updateControls()
        queue.addAll(events)
        playNext()
    }

    private fun playNext() {
        if (isFinishing || isDestroyed) return
        if (paused) { interrupted = true; return }
        val e = queue.removeFirstOrNull()
        if (e == null) {
            finishPlayback()
            return
        }
        when (e) {
            is GameEvent.DiceRolled -> board.animateDice(e.die1, e.die2, diceMs, { beeper.dice() }) { playNext() }
            is GameEvent.Moved -> board.animateMove(e.playerId, e.from, e.steps, stepMs, { beeper.step() }) { playNext() }
            is GameEvent.Jumped -> board.animateJump(e.playerId, e.from, e.to, jumpMs) { playNext() }
            is GameEvent.CardDrawn -> {
                beeper.good()
                addLog("황금열쇠: ${e.card.title}")
                showDialog(
                    "황금열쇠", "【${e.card.title}】\n\n${e.card.description}",
                    listOf(DialogButton("확인", Ui.ORANGE) { playNext() }),
                    accent = 0xFFB28704.toInt(),
                )
            }
            is GameEvent.Message -> {
                addLog(e.text)
                when (e.tone) {
                    GameEvent.Tone.GOOD -> beeper.good()
                    GameEvent.Tone.BAD -> beeper.bad()
                    GameEvent.Tone.INFO -> Unit
                }
                updatePanel()
                board.refresh()
                playNext()
            }
            is GameEvent.Bankrupt -> {
                val p = state.players[e.playerId]
                updatePanel()
                board.refresh()
                beeper.bad()
                showDialog(
                    "파산", "${p.name}님이 파산했습니다.\n가진 땅은 모두 은행으로 돌아갑니다.",
                    listOf(DialogButton("확인", Ui.RED) { playNext() }),
                    accent = Ui.RED,
                )
            }
            is GameEvent.TurnChanged, is GameEvent.GameOver -> playNext()
        }
    }

    private fun finishPlayback() {
        playing = false
        board.syncPieces()
        board.refresh()
        storage.save(state) // 매 행동(턴 종료 포함) 후 자동 저장
        presentState()
    }

    /** 백그라운드에서 돌아왔을 때: 남은 연출은 건너뛰고 기록만 남긴다 */
    private fun flushQueue() {
        while (true) {
            val e = queue.removeFirstOrNull() ?: break
            when (e) {
                is GameEvent.Message -> addLog(e.text)
                is GameEvent.CardDrawn -> addLog("황금열쇠: ${e.card.title} - ${e.card.description}")
                is GameEvent.Bankrupt -> addLog("${state.players[e.playerId].name}님 파산")
                else -> Unit
            }
        }
        board.cancelAnimations()
        finishPlayback()
    }

    // ─────────────────────────────────────────────
    // 선택 다이얼로그
    // ─────────────────────────────────────────────

    private fun showPending(pd: Pending) {
        val p = engine.currentPlayer
        when (pd.kind) {
            PendingKind.BUY -> {
                val t = BoardData.tiles[pd.tile]
                val msg = StringBuilder()
                msg.append("땅값  ").append(Money.won(t.price)).append('\n')
                if (t.canBuild) msg.append("통행료  ").append(Money.won(t.toll(0))).append(" (건물 지으면 더 비싸져요)\n")
                else msg.append("통행료  ").append(Money.won(t.toll(0))).append(" (관광지: 고정)\n")
                msg.append("\n현금 ").append(Money.won(p.money)).append(" → ").append(Money.won(p.money - t.price))
                showDialog(
                    "${t.name} 구매할까요?", msg,
                    listOf(
                        DialogButton("구매하기 (${Money.short(t.price)})", Ui.GREEN) { perform { engine.answer(true) } },
                        DialogButton("사지 않기", Ui.GRAY) { perform { engine.answer(false) } },
                    ),
                    accent = groupColor(t),
                )
            }
            PendingKind.BUILD -> {
                val t = BoardData.tiles[pd.tile]
                val next = state.level[t.index] + 1
                val name = Tile.LEVEL_NAMES[next]
                val msg = StringBuilder()
                msg.append("건설비  ").append(Money.won(pd.amount))
                if (pd.halfBuild) msg.append("  (반값 건설권 적용!)")
                msg.append("\n통행료  ").append(Money.won(t.toll(next - 1))).append(" → ").append(Money.won(t.toll(next)))
                msg.append("\n\n현금 ").append(Money.won(p.money)).append(" → ").append(Money.won(p.money - pd.amount))
                showDialog(
                    "${t.name}에 $name 지을까요?", msg,
                    listOf(
                        DialogButton("$name 짓기 (${Money.short(pd.amount)})", Ui.GREEN) { perform { engine.answer(true) } },
                        DialogButton("짓지 않기", Ui.GRAY) { perform { engine.answer(false) } },
                    ),
                    accent = groupColor(t),
                )
            }
            PendingKind.USE_TOLL_FREE -> {
                val t = BoardData.tiles[pd.tile]
                val owner = state.players[pd.creditor]
                showDialog(
                    "통행료 면제권 사용?",
                    "${owner.name}님의 ${t.name}\n통행료 ${Money.won(pd.amount)}\n\n면제권을 쓰면 내지 않아도 됩니다.\n(남은 면제권 ${p.tollFreeCards}장)",
                    listOf(
                        DialogButton("면제권 사용", Ui.GREEN) { perform { engine.answer(true) } },
                        DialogButton("그냥 낸다", Ui.GRAY) { perform { engine.answer(false) } },
                    ),
                )
            }
            PendingKind.RAISE_FUNDS -> showSellDialog(pd)
        }
    }

    private fun showSellDialog(pd: Pending) {
        val p = engine.currentPlayer
        val buttons = ArrayList<DialogButton>()
        buttons.add(DialogButton("자동 매각 (싼 것부터)", Ui.ORANGE) { perform { engine.autoSell() } })
        for (t in engine.ownedTiles(p.id)) {
            val lv = state.level[t.index]
            if (lv > 0) {
                val v = engine.saleValueOfTopBuilding(t.index)
                buttons.add(DialogButton("${t.name} ${Tile.LEVEL_NAMES[lv]} 팔기  +${Money.short(v)}", Ui.BLUE) {
                    perform { engine.sellBuilding(t.index) }
                })
            } else {
                val v = engine.saleValueOfLand(t.index)
                buttons.add(DialogButton("${t.name} 땅 팔기  +${Money.short(v)}", Ui.BLUE) {
                    perform { engine.sellLand(t.index) }
                })
            }
        }
        val lack = pd.amount - p.money
        showDialog(
            "돈이 부족합니다!",
            "${pd.reason}  ${Money.won(pd.amount)}\n현금  ${Money.won(p.money)}  (부족 ${Money.won(lack)})\n\n" +
                "땅·건물을 은행에 구매가의 절반에 팝니다.\n건물이 있는 땅은 건물부터 팔아야 해요.",
            buttons,
            accent = Ui.RED,
        )
    }

    private fun groupColor(t: Tile): Int =
        if (t.type == TileType.CITY) darker(BoardData.GROUP_COLORS[t.group]) else BoardData.SPECIAL_COLOR

    private fun darker(c: Int): Int = Color.rgb(Color.red(c) * 7 / 10, Color.green(c) * 7 / 10, Color.blue(c) * 7 / 10)

    // ─────────────────────────────────────────────
    // 보드 터치
    // ─────────────────────────────────────────────

    override fun onTileTapped(index: Int) {
        if (playing || dialog?.isShowing == true || overlay.visibility == View.VISIBLE) return
        val p = engine.currentPlayer
        val t = BoardData.tiles[index]
        if (board.selectMode && p.spaceTravelPending && state.pending == null && !state.gameOver) {
            if (index == GameConfig.TILE_SPACE) {
                addLog("우주여행 칸은 고를 수 없습니다.")
                return
            }
            val passStart = index < GameConfig.TILE_SPACE
            showDialog(
                "${t.name}(으)로 갈까요?",
                tileInfo(index) + if (passStart) "\n\n출발 칸을 지나므로 월급을 받습니다." else "",
                listOf(
                    DialogButton("여기로 이동", Ui.GREEN) {
                        board.selectMode = false
                        perform { engine.spaceTravel(index) }
                    },
                    DialogButton("다시 고르기", Ui.GRAY) { },
                ),
                cancelable = true,
                accent = 0xFF1A237E.toInt(),
            )
            return
        }
        showDialog(t.name, tileInfo(index), listOf(DialogButton("닫기", Ui.GRAY) { }), cancelable = true)
    }

    private fun tileInfo(i: Int): String {
        val t = BoardData.tiles[i]
        val sb = StringBuilder()
        when (t.type) {
            TileType.CITY -> {
                sb.append(BoardData.GROUP_NAMES[t.group]).append(" 도시\n\n")
                sb.append("땅값  ").append(Money.won(t.price)).append('\n')
                sb.append("건설비  별장 ").append(Money.short(t.buildCosts[0]))
                    .append(" / 빌딩 ").append(Money.short(t.buildCosts[1]))
                    .append(" / 호텔 ").append(Money.short(t.buildCosts[2])).append('\n')
                sb.append("통행료\n")
                for (lv in 0..Tile.MAX_LEVEL) {
                    sb.append("  ").append(Tile.LEVEL_NAMES[lv]).append("  ").append(Money.won(t.toll(lv))).append('\n')
                }
            }
            TileType.SPECIAL -> {
                sb.append("특수 지역 (관광지)\n\n")
                sb.append("땅값  ").append(Money.won(t.price)).append('\n')
                sb.append("통행료  ").append(Money.won(t.toll(0))).append(" (고정)\n")
                sb.append("건물을 지을 수 없습니다.\n")
            }
            TileType.GOLDEN_KEY -> sb.append("황금열쇠 카드를 한 장 뽑습니다.")
            TileType.START -> sb.append("지나가거나 도착하면 월급 ${Money.won(GameConfig.SALARY)}을 받습니다.")
            TileType.ISLAND -> sb.append("${GameConfig.ISLAND_TURNS}턴 동안 갇힙니다.\n더블이 나오거나 탈출권을 쓰면 바로 나갈 수 있어요.")
            TileType.WELFARE_RECEIVE -> sb.append("도착하면 쌓인 사회복지기금을 모두 받습니다.\n현재 기금: ${Money.won(state.welfareFund)}")
            TileType.WELFARE_PAY -> sb.append("사회복지기금으로 ${Money.won(GameConfig.WELFARE_DONATION)}을 기부합니다.")
            TileType.SPACE -> sb.append("다음 턴에 원하는 칸을 골라 바로 이동합니다.\n(출발 칸을 지나면 월급)")
        }
        if (t.isProperty) {
            val owner = state.owner[i]
            sb.append("\n소유자  ")
            if (owner < 0) sb.append("없음 (은행)")
            else {
                sb.append(state.players[owner].name)
                if (t.canBuild) sb.append(" · ").append(Tile.LEVEL_NAMES[state.level[i]])
                sb.append("\n현재 통행료  ").append(Money.won(t.toll(state.level[i])))
            }
        }
        return sb.toString().trimEnd()
    }

    // ─────────────────────────────────────────────
    // 설정 / 게임 종료
    // ─────────────────────────────────────────────

    private fun showSettings() {
        showDialog(
            "설정", null,
            listOf(
                DialogButton("말 이동: " + if (storage.fastMove) "빠르게" else "보통", Ui.BLUE) {
                    storage.fastMove = !storage.fastMove
                    showSettings()
                },
                DialogButton("효과음: " + if (storage.sound) "켜짐" else "꺼짐", Ui.BLUE) {
                    storage.sound = !storage.sound
                    beeper.enabled = storage.sound
                    showSettings()
                },
                DialogButton("새 게임 시작", Ui.RED) { confirmNewGame() },
                DialogButton("닫기", Ui.GRAY) { presentStateIfIdle() },
            ),
            cancelable = true,
        )
    }

    private fun confirmNewGame() {
        showDialog(
            "새 게임을 시작할까요?", "지금 게임은 삭제됩니다.",
            listOf(
                DialogButton("새 게임", Ui.RED) { startNewGame() },
                DialogButton("취소", Ui.GRAY) { presentStateIfIdle() },
            ),
            cancelable = true,
        )
    }

    private fun presentStateIfIdle() {
        if (!playing && state.pending != null) presentState()
    }

    private fun startNewGame() {
        storage.clear()
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    private fun showGameOver() {
        val rank = engine.ranking()
        val winner = state.players.getOrNull(state.winnerId) ?: rank.first()
        val sb = StringBuilder()
        if (state.turnLimit > 0 && state.aliveCount() > 1) sb.append("턴 제한 종료! 총자산 1위가 승리합니다.\n\n")
        else sb.append("최후의 1인이 남았습니다!\n\n")
        for ((k, p) in rank.withIndex()) {
            sb.append(k + 1).append("위  ").append(p.name)
            if (p.bankrupt) sb.append("  (파산)")
            else sb.append("  총자산 ").append(Money.won(engine.totalAssets(p.id)))
            sb.append('\n')
        }
        showDialog(
            "${winner.name}님 승리!", sb.toString().trimEnd(),
            listOf(
                DialogButton("새 게임", Ui.GREEN) { startNewGame() },
                DialogButton("보드 보기", Ui.GRAY) { },
            ),
            accent = Ui.GREEN,
        )
    }

    private fun showDialog(
        title: String,
        message: CharSequence?,
        buttons: List<DialogButton>,
        cancelable: Boolean = false,
        accent: Int = Ui.DARK,
    ) {
        dialog?.dismiss()
        dialog = GameDialog.show(this, title, message, buttons, cancelable, accent)
    }
}
