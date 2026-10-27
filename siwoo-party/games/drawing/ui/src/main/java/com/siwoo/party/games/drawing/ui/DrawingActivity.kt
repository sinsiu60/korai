package com.siwoo.party.games.drawing.ui

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.secret.TextSecret
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.drawing.DrawingGame
import com.siwoo.party.games.drawing.DrawingModule
import com.siwoo.party.games.drawing.GameConfig
import com.siwoo.party.games.drawing.Phase
import kotlin.random.Random

/**
 * 그림 맞히기 화면 (3~4인, 차례대로).
 * 위: 플레이어 패널(점수, 그리는 사람 금색) + 80초 시간 막대 / 가운데: 도구(왼쪽) + 그림판 / 아래: 차례 정보 + 맞힌 사람 칩 + [제시어 꾹]
 * 흐름: [차례마다] 공통 SecretScreen(넘기기 → 꾹 눌러 제시어 → 확인 완료) → 80초 그리기 (나머지는 말로 외침)
 *   → 그린 사람이 맞힌 사람 칩 터치(맞힌 사람 +2, 그린 사람 +1) 또는 시간 끝(정답 공개) → 결과 카드 [다음 차례]
 *   → 모두 2번씩 그리면 합계 순위로 허브에. 80초는 규칙 시간 (배율 없음), 연출은 t().
 */
class DrawingActivity : PartyGameActivity() {

    override val module = DrawingModule

    lateinit var game: DrawingGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var canvasView: DrawCanvasView
        private set
    lateinit var tools: ToolBarView
        private set
    lateinit var timerBar: TimerBarView
        private set
    private lateinit var infoText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var peekText: TextView
    lateinit var peekButton: GameButton
        private set

    /** 맞힌 사람 칩 (플레이어 번호 → 버튼, 그리는 중에만) */
    var guessChips: Map<Int, GameButton> = emptyMap()
        private set
    /** 결과 카드의 [다음 차례]/[최종 결과] */
    var nextButton: GameButton? = null
        private set
    /** 제시어 확인 (넘기기 → 비밀 화면) */
    var secret: SecretHandle? = null
        private set
    private var clearPopup: PopupLayer.Popup? = null
    private var ended = false
    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private val pb = PointF()

    /** 지금 그리는 중 (시간 흐름) */
    val drawingNow: Boolean get() = game.phase == Phase.DRAWING && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = DrawingGame(players.size, DrawingStore.load(this), randomOverride ?: Random.Default)
        root.background = Theme.Party.background()
        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 6f))

        // 위: 플레이어 + 시간 막대
        val top = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        for (i in players.indices) strip.setValue(i, "0점")
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1.25f))
        timerBar = TimerBarView(this)
        timerBar.setFraction(1f, GameConfig.DRAW_MS)
        top.addView(timerBar, Theme.lp(0, Theme.dp(this, 34f), 1f).apply { leftMargin = Theme.dp(this@DrawingActivity, 8f) })
        col.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)))

        // 가운데: 도구 + 그림판
        val mid = Theme.horizontal(this)
        tools = ToolBarView(this)
        tools.listener = ToolBarView.Listener { onTool(it) }
        mid.addView(tools, Theme.lp(Theme.dp(this, 96f), Theme.MATCH))
        val board = FrameLayout(this).apply {
            background = Theme.Party.card(this@DrawingActivity, Theme.Party.NEON_SKY, 12f)
            val bp = Theme.dp(this@DrawingActivity, 4f)
            setPadding(bp, bp, bp, bp)
        }
        canvasView = DrawCanvasView(this)
        canvasView.onStrokeEnd = Runnable { refreshTools() }
        board.addView(canvasView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        peekText = Theme.text(this, "", 22f, Theme.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.card(this@DrawingActivity, Theme.Party.NEON_PURPLE, 12f, Theme.GOLD, 2f)
            val p = Theme.dp(this@DrawingActivity, 10f)
            setPadding(p * 2, p / 2, p * 2, p / 2)
            visibility = View.GONE
        }
        board.addView(peekText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Theme.dp(this@DrawingActivity, 8f) })
        overlay = FrameLayout(this).apply { visibility = View.GONE }
        board.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        mid.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@DrawingActivity, 8f) })
        col.addView(mid, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@DrawingActivity, 6f) })

        // 아래: 정보 + 맞힌 사람 칩 + 제시어 꾹
        val bottom = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        infoText = Theme.text(this, "", 13f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            maxLines = 2
        }
        bottom.addView(infoText, Theme.lp(Theme.dp(this, 96f), Theme.MATCH))
        chipRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(chipRow, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@DrawingActivity, 8f) })
        peekButton = Ui.button(this, "제시어 꾹", Theme.Party.NEON_PURPLE, 14f) { }
        setupPeek(peekButton)
        bottom.addView(peekButton, Theme.lp(Theme.dp(this, 104f), Theme.MATCH).apply { leftMargin = Theme.dp(this@DrawingActivity, 8f) })
        col.addView(bottom, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@DrawingActivity, 6f) })

        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setDrawingUi(false)
        stage.post { startTurn() }
    }

    private fun name(i: Int) = players[i].name

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    /** 그리는 동안만 도구·그림판·칩·제시어 버튼 켜기 */
    private fun setDrawingUi(on: Boolean) {
        canvasView.touchEnabled = on
        tools.toolsEnabled = on
        peekButton.isEnabled = on
        if (!on) hidePeek()
        for (b in guessChips.values) b.isEnabled = on
        refreshTools()
    }

    private fun refreshTools() {
        tools.canUndo = canvasView.model.canUndo
        tools.canClear = !canvasView.model.isEmpty
    }

    private fun updateInfo() {
        val d = game.drawer
        infoText.text = "${game.turnIndex + 1} / ${game.totalTurns}번째\n${name(d)} 그림"
    }

    // ───────────── 차례 시작: 제시어 확인 ─────────────

    private fun startTurn() {
        cancelTasks()
        overlay.removeAllViews()
        overlay.visibility = View.GONE
        nextButton = null
        canvasView.clearAll()
        tools.selectedColor = 0
        tools.selectedWidth = 0
        canvasView.color = 0
        canvasView.widthIndex = 0
        timerBar.stop()
        timerBar.setFraction(1f, GameConfig.DRAW_MS)
        val d = game.drawer
        strip.setCurrent(d)
        updateInfo()
        buildChips()
        setDrawingUi(false)
        secret = SecretScreen.show(
            this, d,
            TextSecret("${game.categoryName} · ${game.word.length}글자", game.word, "80초 동안 그려요 · 글자·숫자는 안 돼요", DrawingArt.categoryColor(game.category)),
            title = "${name(d)}님이 그릴 차례",
            info = "${game.round}바퀴 · ${game.turnIndex + 1} / ${game.totalTurns}번째",
            handoffTitle = "${name(d)}님이 그릴 차례",
            handoffSub = "다른 사람은 보지 마세요!\n기기를 ${name(d)}님에게 건네고, ${name(d)}님만 제시어를 보세요",
            handoffOk = "제시어 보기",
            guide = "오른쪽 카드를 꾹 누르고 있는 동안만 보여요.\n다 봤으면 [그리기 시작!]을 누르세요.",
            confirmText = "그리기 시작!",
        ) { onSecretDone() }
    }

    private fun onSecretDone() {
        secret = null
        if (game.phase != Phase.SECRET) return
        game.startDrawing()
        setDrawingUi(true)
        beeper.turn()
        banner("그리기 시작!", "힌트: ${game.categoryName} · ${game.word.length}글자 — 다른 사람은 말로 외쳐요", ms = GameConfig.TURN_INTRO_MS)
        timerBar.start(GameConfig.DRAW_MS)
        // 시간 끝은 게임 타이머로 (일시정지 때 같이 멈춤, 규칙 시간이라 배율 없음)
        schedule(GameConfig.DRAW_MS) { onTimeout() }
    }

    /** 그린 사람을 뺀 사람들의 이름 칩 */
    private fun buildChips() {
        chipRow.removeAllViews()
        val map = LinkedHashMap<Int, GameButton>()
        chipRow.addView(Theme.text(this, "맞힌 사람", 12f, Theme.Party.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER })
        for (i in players.indices) {
            if (i == game.drawer) continue
            val label = if (players.size > 3 && name(i).length > 3) name(i) else "${name(i)} 정답!"
            val b = Ui.button(this, label, playerColor(i), if (players.size > 3) 13f else 15f) { onGuess(i) }
            b.maxLines = 1
            map[i] = b
            chipRow.addView(b, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@DrawingActivity, 6f) })
        }
        guessChips = map
    }

    // ───────────── 그리기 ─────────────

    private fun onTool(tool: Int) {
        if (!drawingNow) return
        when (tool) {
            in 0 until GameConfig.COLOR_COUNT -> { canvasView.color = tool; tools.selectedColor = tool }
            ToolBarView.THIN -> { canvasView.widthIndex = 0; tools.selectedWidth = 0 }
            ToolBarView.THICK -> { canvasView.widthIndex = 1; tools.selectedWidth = 1 }
            ToolBarView.ERASER -> { canvasView.color = GameConfig.ERASER; tools.selectedColor = GameConfig.ERASER }
            ToolBarView.UNDO -> if (canvasView.undo()) beeper.step()
            ToolBarView.CLEAR -> askClear()
        }
        beeper.step()
        refreshTools()
    }

    /** 전체 지우기 확인 팝업이 떠 있는지 */
    private val clearOpen: Boolean get() = clearPopup?.closing == false

    private fun askClear() {
        if (canvasView.model.isEmpty || clearOpen) return
        clearPopup = Ui.confirm(popups, "전체 지울까요?", "그린 그림이 모두 지워져요 (시간은 계속 흘러요)", "지우기", Theme.RED_DARK,
            onCancel = { clearPopup = null }) {
            clearPopup = null
            if (drawingNow) {
                canvasView.clearAll()
                refreshTools()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPeek(b: GameButton) {
        b.setOnTouchListener { v, e ->
            if (!v.isEnabled) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { showPeek(); v.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { hidePeek(); v.isPressed = false }
            }
            true
        }
    }

    /** 그리는 사람이 제시어를 다시 볼 때 (누르고 있는 동안만) */
    fun showPeek() {
        if (!drawingNow) return
        peekText.text = game.word
        peekText.visibility = View.VISIBLE
    }

    fun hidePeek() {
        peekText.text = ""
        peekText.visibility = View.GONE
    }

    val peekShowing: Boolean get() = peekText.visibility == View.VISIBLE

    // ───────────── 맞힘 · 시간 끝 ─────────────

    /** 그린 사람이 맞힌 사람 칩을 누름 */
    fun onGuess(player: Int) {
        if (!drawingNow) return
        if (!game.guess(player)) return
        // 전체 지우기 확인이 떠 있으면 닫음 (뒤로 가기·바깥 터치로 닫히면 onCancel 이 안 불리므로 closing 으로 판단)
        if (clearOpen) clearPopup?.dismiss(false)
        clearPopup = null
        cancelTasks()
        timerBar.stop()
        setDrawingUi(false)
        val d = game.drawer
        strip.setValue(player, "${game.scores[player]}점")
        strip.setValue(d, "${game.scores[d]}점")
        strip.pulse(player)
        guessChips[player]?.let { chip ->
            pa.set(chip.width / 2f, chip.height / 2f)
            toFx(chip, pa)
            pb.set(0f, 0f)
            strip.anchor(player, pb)
            toFx(strip.tiles[player], pb)
            fx.coins(pa.x, pa.y, pb.x, pb.y, 6, t(GameConfig.STAR_FLY_MS))
            fx.stars(pa.x, pa.y, 10, t(GameConfig.STAR_FLY_MS))
        }
        banner("${name(player)} 정답!", "정답: ${game.word} · ${name(player)} +${GameConfig.POINTS_GUESSER} / ${name(d)} +${GameConfig.POINTS_DRAWER}",
            BannerTone.GOOD, GameConfig.RESULT_BANNER_MS)
        schedule(t(GameConfig.RESULT_BANNER_MS)) { showResultCard() }
    }

    private fun onTimeout() {
        if (game.phase != Phase.DRAWING) return
        cancelTasks()
        clearPopup?.dismiss(false)
        clearPopup = null
        game.timeout()
        timerBar.stop()
        setDrawingUi(false)
        shakeScreen(Theme.dpf(this, 6f), t(SHAKE_MS))
        banner("시간 끝!", "정답은 \"${game.word}\"", BannerTone.BAD, GameConfig.RESULT_BANNER_MS)
        schedule(t(GameConfig.RESULT_BANNER_MS)) { showResultCard() }
    }

    /** 그림 위 결과 카드: 정답 + 누가 맞혔는지 + [다음 차례] */
    private fun showResultCard() {
        overlay.removeAllViews()
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@DrawingActivity, if (game.guesser >= 0) Theme.GREEN else Theme.RED)
            val p = Theme.dp(this@DrawingActivity, 14f)
            setPadding(p, p, p, p)
        }
        card.addView(Theme.text(this, "정답", 13f, Theme.Party.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER })
        card.addView(Theme.text(this, game.word, 34f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        val g = game.guesser
        val d = game.drawer
        val msg = if (g >= 0) "${name(g)} 정답! +${GameConfig.POINTS_GUESSER}  ·  ${name(d)} +${GameConfig.POINTS_DRAWER}" else "아무도 못 맞혔어요 (점수 없음)"
        card.addView(Theme.text(this, msg, 15f, if (g >= 0) Theme.TEXT else Theme.Party.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER }, Ui.wrapGap(this, 4f))
        val last = game.isLastTurn
        val nb = Ui.button(this, if (last) "최종 결과" else "다음: ${name((game.turnIndex + 1) % players.size)} 그리기",
            if (last) Theme.Party.NEON_PINK else Theme.GREEN_DARK, 17f) { onNext() }
        card.addView(nb, LinearLayout.LayoutParams(Theme.dp(this, 230f), Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@DrawingActivity, 12f) })
        nextButton = nb
        overlay.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        overlay.setBackgroundColor(0x66000000)
        overlay.visibility = View.VISIBLE
        overlay.isClickable = true
        beeper.turn()
    }

    private fun onNext() {
        if (game.phase != Phase.RESULT || gamePaused || ended) return
        if (game.nextTurn()) startTurn() else finishGame()
    }

    private fun finishGame() {
        cancelTasks()
        ended = true
        nextButton?.isEnabled = false
        strip.setCurrent(-1)
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }.joinToString(", ") { name(it) }
        val best = game.scores.maxOrNull() ?: 0
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_MS))
        banner("$winners 1등!", "${best}점", BannerTone.GOOD, GameConfig.END_MS)
        schedule(t(GameConfig.END_MS)) { finishWithRanks(ranks) }
    }

    // ───────────── 일시정지 · 정리 ─────────────

    override fun onGamePause() {
        canvasView.endStroke()
        refreshTools()
        hidePeek()
        secret?.hide()
    }

    override fun onGameDestroy() {
        cancelTasks()
        secret?.close()
        secret = null
        canvasView.release()
    }

    companion object {
        /** 테스트용 난수 주입 */
        var randomOverride: Random? = null
        const val SHAKE_MS = 350L
    }
}
