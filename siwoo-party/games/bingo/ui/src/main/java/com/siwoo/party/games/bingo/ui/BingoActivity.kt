package com.siwoo.party.games.bingo.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.bingo.BingoGame
import com.siwoo.party.games.bingo.BingoModule
import com.siwoo.party.games.bingo.CallResult
import com.siwoo.party.games.bingo.GameConfig
import com.siwoo.party.games.bingo.Phase
import kotlin.random.Random

/**
 * 빙고 화면 (2~4인, 기기를 넘기며).
 * 위: 부른 수 상자 + 플레이어 패널(완성 줄 수 = 별 + "N줄", 모두 공개) /
 * 아래: 왼쪽 차례 카드(이름 크게 + 안내) · 오른쪽 CallView(방금 부른 숫자 공 "17!" + 1~25 부른 숫자표)
 * 흐름: 시작 배너 → 한 명씩 (공통 넘기기 화면 → 내 판 화면에서 1~25 배치 → [완료], 판 내용 지움)
 *  → starter 부터 차례로 (넘기기 → 내 판 화면에서 안 부른 숫자 고르기 → [부르기], 판 내용 지움)
 *  → 모두에게 "17!" + 줄 수 갱신 → 누군가 3줄이면 그 즉시 "빙고!" → 모두의 판 공개 → 순위를 허브로.
 * 비밀 판(BoardScreen)은 닫을 때 BoardSecret.clear + 화면 제거 → 다음 사람 넘기기 화면 뒤에 숫자가 남지 않는다.
 * 규칙 판정은 BingoGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class BingoActivity : PartyGameActivity() {

    override val module = BingoModule

    lateinit var game: BingoGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var callView: CallView
        private set
    lateinit var revealView: RevealView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    lateinit var countText: TextView
        private set
    private lateinit var turnLabel: TextView
    private lateinit var goalText: TextView

    /** 연출 중 (입력 무시) */
    var busy = true
        private set

    /** 떠 있는 넘기기 화면 (공통 SecretScreen.handoffOnly) */
    private var handoff: SecretHandle? = null
    val handoffPopup: PopupLayer.Popup? get() = handoff?.handoffPopup

    /** 떠 있는 내 판 화면 */
    var screen: BoardScreen? = null
        private set

    /** 가장 최근에 띄운 판 내용 (테스트: 닫은 뒤 지워졌는지) */
    var lastContent: BoardSecret? = null
        private set

    /** 부르기 화면에서 고른 숫자 (0 = 없음) */
    var selectedNumber = 0
        private set

    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = BingoGame(players.size, randomOverride ?: Random.Default)
        val root = Theme.vertical(this)
        val pad10 = Theme.dp(this, 10f)
        root.setPadding(pad10, Theme.dp(this, 8f), pad10, Theme.dp(this, 8f))

        // ── 위: 부른 수 + 플레이어 패널 ──
        val top = Theme.horizontal(this)
        countText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.card(this@BingoActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@BingoActivity, 4f)
        }
        top.addView(countText, Theme.lp(Theme.dp(this, 96f), Theme.MATCH))
        strip = PlayerStrip(this, players, false, StripIcon.STAR)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@BingoActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))

        // ── 아래: 차례 카드 · 부른 숫자 (끝나면 그 위에 모두의 판) ──
        val area = FrameLayout(this)
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        main.addView(buildTurnCard(), Theme.lp(Theme.dp(this, 156f), Theme.MATCH))
        callView = CallView(this).apply {
            background = Theme.card(this@BingoActivity, Theme.CARD, 16f)
            elevation = Theme.dpf(this@BingoActivity, 4f)
        }
        main.addView(callView, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@BingoActivity, 8f) })
        area.addView(main, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        revealView = RevealView(this).apply { visibility = View.GONE; elevation = Theme.dpf(this@BingoActivity, 8f) }
        area.addView(revealView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(area, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BingoActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateCount()
        for (p in players.indices) strip.setValue(p, "준비 중")
        turnLabel.text = "빙고"
        nameText.text = "준비"
        hintText.text = "각자 몰래\n1~25를\n내 판에 채워요"
        callView.setIdle("판 채우는 중", "${GameConfig.WIN_LINES}줄 먼저 완성하면 빙고!")
        stage.post { start() }
    }

    private fun buildTurnCard(): LinearLayout {
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@BingoActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@BingoActivity, 4f)
            val p = Theme.dp(this@BingoActivity, 10f)
            setPadding(p, Theme.dp(this@BingoActivity, 12f), p, p)
        }
        turnLabel = Theme.text(this, "지금 차례", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER }
        card.addView(turnLabel, Theme.lp(Theme.MATCH, Theme.WRAP))
        nameText = Theme.text(this, "", 26f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        card.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 38f)).apply { topMargin = Theme.dp(this@BingoActivity, 4f) })
        hintText = Theme.text(this, "", 14f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        card.addView(hintText, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BingoActivity, 6f) })
        goalText = Theme.text(this, "${GameConfig.WIN_LINES}줄 먼저\n빙고!", 15f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.card(this@BingoActivity, Theme.BG_DEEP, 12f, Theme.GOLD_DARK, 1.5f)
            val p = Theme.dp(this@BingoActivity, 6f)
            setPadding(p, p, p, p)
        }
        card.addView(goalText, Theme.lp(Theme.MATCH, Theme.WRAP))
        return card
    }

    private fun name(i: Int) = players[i].name

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun setName(p: Int) {
        nameText.text = name(p)
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
    }

    private fun updateCount() {
        countText.text = "부른 수\n${game.callCount} / ${GameConfig.CELLS}"
    }

    /** 패널: 완성 줄 수 (모두 공개) */
    private fun updateLines() {
        for (p in players.indices) {
            val n = game.lines(p)
            strip.setCount(p, n)
            strip.setValue(p, "${n}줄")
        }
    }

    // ───────────── 시작 · 판 채우기 ─────────────

    private fun start() {
        if (finished) return
        banner("빙고!", "한 명씩 몰래 내 판에 1~${GameConfig.CELLS}를 채워요", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { nextSetup() }
    }

    private fun nextSetup() {
        val p = game.setupPlayer
        busy = true
        strip.setCurrent(p)
        turnLabel.text = "판 채우기 ${p + 1} / ${players.size}"
        setName(p)
        hintText.text = "${name(p)}님이\n몰래 판을\n채우는 중"
        handoff = SecretScreen.handoffOnly(
            this, p,
            title = "${name(p)}님 판 채우기",
            sub = "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, ${name(p)}님만 화면을 보세요",
            okText = "내 판 채우기",
        ) {
            handoff = null
            openSetup(p)
        }
    }

    private fun openSetup(p: Int) {
        val content = BoardSecret(playerColor(p))
        lastContent = content
        val s = BoardScreen(this, p, content, setup = true)
        s.guide.text = "빈칸을 누르면 1부터\n차례로 들어가요.\n마지막 숫자를 누르면 빼요."
        s.board.onCellTap = { cell -> onSetupTap(cell) }
        s.onRandom = { onRandom() }
        s.onReset = { onReset() }
        s.onDone = { onSetupDone() }
        screen = s.show()
        refreshSetup()
        busy = false
    }

    private fun refreshSetup() {
        val s = screen ?: return
        val b = game.boards[s.player]
        s.content.set(b, 0)
        if (b.isComplete) s.setInfo("다 채웠어요!", "${GameConfig.CELLS} / ${GameConfig.CELLS}", "[완료]를 누르세요")
        else s.setInfo("다음에 넣을 숫자", b.nextNumber.toString(), "${b.placedCount} / ${GameConfig.CELLS}칸")
        s.randomButton?.text = if (b.isEmpty || b.isComplete) "랜덤 배치" else "나머지 랜덤"
        s.resetButton?.isEnabled = !b.isEmpty
        s.doneButton?.isEnabled = b.isComplete
        s.board.refresh()
    }

    private fun onSetupTap(cell: Int) {
        val s = screen ?: return
        if (busy || gamePaused || game.phase != Phase.SETUP) return
        val b = game.boards[s.player]
        when {
            b.numberAt(cell) == 0 && b.place(cell) -> {
                beeper.step()
                refreshSetup()
                s.board.pop(cell, t(GameConfig.PLACE_POP_MS))
            }
            b.numberAt(cell) != 0 && b.numberAt(cell) == b.placedCount -> {
                b.undoLast()
                beeper.step()
                refreshSetup()
            }
            else -> beeper.bad()
        }
    }

    private fun onRandom() {
        val s = screen ?: return
        if (busy || game.phase != Phase.SETUP) return
        game.randomFill(s.player)
        beeper.dice()
        refreshSetup()
        s.randomButton?.bounce()
    }

    private fun onReset() {
        val s = screen ?: return
        if (busy || game.phase != Phase.SETUP) return
        game.boards[s.player].clear()
        beeper.step()
        refreshSetup()
    }

    private fun onSetupDone() {
        val s = screen ?: return
        if (busy || game.phase != Phase.SETUP) return
        val p = s.player
        if (!game.finishSetup()) {
            s.doneButton?.let { Ui.shake(it) }
            beeper.bad()
            return
        }
        busy = true
        closeScreen()
        beeper.good()
        strip.setValue(p, "준비 완료")
        strip.pulse(p)
        if (game.phase == Phase.PLAY) schedule(t(GameConfig.SETUP_NEXT_MS)) { startPlay() }
        else schedule(t(GameConfig.SETUP_NEXT_MS)) { nextSetup() }
    }

    private fun closeScreen() {
        screen?.close()
        screen = null
        selectedNumber = 0
    }

    // ───────────── 부르기 ─────────────

    private fun startPlay() {
        updateLines()
        val s = game.starter
        strip.setCurrent(s)
        turnLabel.text = "부르기 시작"
        setName(s)
        hintText.text = "${name(s)}님부터\n차례로 숫자를\n불러요"
        callView.setIdle("${name(s)}님부터 불러요", "${GameConfig.WIN_LINES}줄 먼저 완성하면 빙고!")
        banner("부르기 시작!", "${name(s)}님부터 · ${GameConfig.WIN_LINES}줄 먼저 빙고!", ms = GameConfig.PLAY_INTRO_MS)
        schedule(t(GameConfig.PLAY_INTRO_MS)) { nextTurn() }
    }

    private fun nextTurn() {
        if (game.phase != Phase.PLAY) return
        val p = game.current
        busy = true
        strip.setCurrent(p)
        turnLabel.text = "지금 차례"
        setName(p)
        hintText.text = "${name(p)}님이\n숫자를\n부를 차례"
        val last = game.lastCall
        val sub = (if (last > 0) "방금 부른 숫자: $last\n" else "") +
            "기기를 ${name(p)}님에게 건네고, ${name(p)}님만 화면을 보세요"
        handoff = SecretScreen.handoffOnly(this, p, title = "${name(p)}님 차례입니다", sub = sub, okText = "내 판 보기") {
            handoff = null
            openCall(p)
        }
    }

    private fun openCall(p: Int) {
        val content = BoardSecret(playerColor(p))
        lastContent = content
        val s = BoardScreen(this, p, content, setup = false)
        s.board.onCellTap = { cell -> onCallTap(cell) }
        s.onCall = { onCall() }
        screen = s.show()
        selectedNumber = 0
        content.set(game.boards[p], game.lastCall)
        val lines = content.lines
        s.setInfo("내 빙고", "${lines}줄", "${GameConfig.WIN_LINES}줄이면 빙고! · 부른 수 ${game.callCount}개")
        s.guide.text = (if (game.lastCall > 0) "방금 부른 숫자 ${game.lastCall}\n" else "") +
            "부를 숫자를 누르고\n[부르기]를 누르세요.\n도장 칸은 이미 부른 수예요."
        s.board.refresh()
        busy = false
    }

    private fun onCallTap(cell: Int) {
        val s = screen ?: return
        if (busy || gamePaused || game.phase != Phase.PLAY) return
        val n = game.boards[s.player].numberAt(cell)
        if (!game.canCall(n)) {
            beeper.bad()
            s.callButton?.let { Ui.shake(it) }
            return
        }
        selectedNumber = n
        s.content.selected = cell
        s.callButton?.text = "$n 부르기!"
        s.callButton?.let { b ->
            if (!b.isEnabled) {
                b.isEnabled = true
                b.bounce()
            }
        }
        beeper.step()
        s.board.pop(cell, t(GameConfig.PLACE_POP_MS))
    }

    private fun onCall() {
        if (busy || gamePaused || selectedNumber == 0 || screen == null) return
        val r = game.call(selectedNumber) ?: return
        busy = true
        closeScreen()
        showCall(r)
    }

    private fun showCall(r: CallResult) {
        updateCount()
        updateLines()
        val gainers = StringBuilder()
        for (p in players.indices) {
            val g = r.gained(p)
            if (g <= 0) continue
            if (gainers.isNotEmpty()) gainers.append(" · ")
            gainers.append(name(p)).append(" +").append(g).append("줄")
            strip.pulse(p)
            strip.anchor(p, pa)
            toFx(strip.tiles[p], pa)
            fx.stars(pa.x, pa.y, 5, t(600))
        }
        val sub = if (gainers.isEmpty()) "새로 완성된 줄 없음" else "줄 완성! $gainers"
        callView.showCall(r.number, "${name(r.caller)}님이 불렀어요", sub, t(GameConfig.CALL_POP_MS))
        turnLabel.text = "방금 부른 숫자"
        setName(r.caller)
        hintText.text = "${r.number}!\n모두의 판에\n도장 쾅"
        beeper.good()
        if (r.gameOver) schedule(t(GameConfig.CALL_POP_MS)) { bingo(r) }
        else schedule(t(GameConfig.CALL_SHOW_MS)) { nextTurn() }
    }

    // ───────────── 빙고 · 공개 · 끝 ─────────────

    private fun bingo(r: CallResult) {
        val w = r.winners
        for (p in w) {
            strip.setValue(p, "빙고!")
            strip.pulse(p)
        }
        strip.setCurrent(if (w.size == 1) w[0] else -1)
        turnLabel.text = "빙고!"
        if (w.size == 1) setName(w[0]) else {
            nameText.text = "동시 빙고"
            nameText.setTextColor(Theme.GOLD)
        }
        hintText.text = w.joinToString("\n") { "${name(it)}님" } + "\n1위!"
        shakeScreen(Theme.dpf(this, 8f), t(GameConfig.SHAKE_MS))
        val sub = if (w.size == 1) "${name(w[0])}님 ${game.lines(w[0])}줄 완성! · ${r.number}에서 끝"
        else w.joinToString(" · ") { name(it) } + " 동시에 ${GameConfig.WIN_LINES}줄 · 함께 1위"
        banner("빙고!", sub, BannerTone.GOOD, GameConfig.BINGO_BANNER_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.BINGO_BANNER_MS + GameConfig.REVEAL_MS))
        schedule(t(GameConfig.BINGO_BANNER_MS)) { reveal() }
    }

    /** 끝난 뒤 모두의 판 공개 → 순위를 허브로 */
    private fun reveal() {
        val ranks = game.ranks()
        val contents = players.indices.map { p ->
            BoardSecret(playerColor(p)).also { it.set(game.boards[p], game.lastCall) }
        }
        val labels = players.indices.map { p -> "${ranks[p]}위 · ${game.lines(p)}줄" }
        for (p in players.indices) strip.setValue(p, "${ranks[p]}위")
        revealView.show(contents, players.map { it.name }, IntArray(players.size) { playerColor(it) }, labels, game.winners)
        revealView.visibility = View.VISIBLE
        revealView.alpha = 0f
        revealView.animate().alpha(1f).setDuration(t(300)).start()
        beeper.turn()
        schedule(t(GameConfig.REVEAL_MS)) { finishGame() }
    }

    private fun finishGame() {
        if (finished) return
        finished = true
        finishWithRanks(game.ranks())
    }

    override fun onGamePause() {
        // 일시정지 메뉴 뒤로 판이 비치지 않게 가린다
        screen?.setHidden(true)
    }

    override fun onGameResume() {
        screen?.setHidden(false)
    }

    override fun onGameDestroy() {
        cancelTasks()
        handoff?.close()
        handoff = null
        screen?.close()
        screen = null
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
