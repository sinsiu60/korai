package com.siwoo.party.games.codetile.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretHoldView
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.codetile.CodeTileGame
import com.siwoo.party.games.codetile.CodeTileModule
import com.siwoo.party.games.codetile.GameConfig
import com.siwoo.party.games.codetile.GuessResult
import com.siwoo.party.games.codetile.Phase
import com.siwoo.party.games.codetile.Tiles
import kotlin.random.Random

/**
 * 숨은 숫자 타일 화면 (2~4인, 차례대로, 기기 하나를 넘기며).
 * 위: 플레이어 패널(숨은 타일 수·탈락) / 아래: 왼쪽 공개 탁자(TableView) · 오른쪽 차례 카드 + 숫자 패드 + 버튼
 * 한 차례: 더미에서 1장 뽑기(자동) → 공통 넘기기 화면 → 꾹 눌러 내 타일·뽑은 타일 확인(공통 SecretScreen, 닫으면 숫자 지움)
 *  → 공개 탁자에서 상대의 숨은 타일 누르기 → 0~11 고르기 → [추리!]
 *    정답: 타일 뒤집기 → [계속 추리] 또는 [멈추기](뽑은 타일을 숨긴 채 정렬 자리에)
 *    오답: 뽑은 타일을 공개한 채 정렬 자리에 / 더미가 비었으면 내 숨은 타일 하나를 골라 공개
 *  → 숨은 타일이 없으면 탈락, 최후의 1인 승리.
 * 차례 중 [내 타일 몰래 보기]로 언제든 다시 확인(넘기기 없이 꾹 누르기 카드).
 * 공개 탁자는 game.publicNumber 만 받는다 → 숨은 숫자는 공개 화면 어디에도 없다.
 */
class CodeTileActivity : PartyGameActivity() {

    override val module = CodeTileModule

    lateinit var game: CodeTileGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var table: TableView
        private set
    lateinit var pad: GuessPadView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    lateinit var turnLabel: TextView
        private set
    lateinit var guessButton: GameButton
        private set
    lateinit var continueButton: GameButton
        private set
    lateinit var stopButton: GameButton
        private set
    lateinit var peekButton: GameButton
        private set
    private lateinit var decideRow: LinearLayout

    /** 연출 중 (입력 무시) */
    var busy = true
        private set

    /** 차례 시작 비밀 확인 (넘기기 → 꾹 누르기) */
    private var secret: SecretHandle? = null
    /** 차례 중 다시 보기 */
    private var peek: SecretHandle? = null

    val handoffPopup: PopupLayer.Popup? get() = secret?.handoffPopup
    val secretView: SecretHoldView? get() = secret?.holdView ?: peek?.holdView
    val confirmButton: GameButton? get() = secret?.confirmButton ?: peek?.confirmButton
    val checkingSecret: Boolean get() = secret != null || peek != null

    /** 가장 최근에 띄운 비밀 카드 내용 (테스트: 닫은 뒤 지워졌는지) */
    var lastSecret: MyTilesSecret? = null
        private set

    /** 고른 상대 타일·숫자 */
    var selPlayer = -1
        private set
    var selIndex = -1
        private set
    var selNumber = -1
        private set

    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private val crossed = BooleanArray(GameConfig.NUMBERS)
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = CodeTileGame.create(players.size, randomOverride ?: Random.Default)
        val root = Theme.vertical(this)
        val pad8 = Theme.dp(this, 8f)
        root.setPadding(Theme.dp(this, 10f), pad8, Theme.dp(this, 10f), pad8)

        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        root.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)))

        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        table = TableView(this)
        table.setPlayers(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        table.onTileTap = { p, i -> onTileTap(p, i) }
        main.addView(table, Theme.lp(0, Theme.MATCH, 1f))
        main.addView(buildPanel(), Theme.lp(Theme.dp(this, 212f), Theme.MATCH).apply { leftMargin = pad8 })
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = pad8 })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        table.sync(game)
        updateStrip()
        turnLabel.text = "숨은 숫자 타일"
        nameText.text = "준비"
        hintText.text = "0~11 타일 ${GameConfig.TILE_COUNT}장\n상대 숫자를 맞혀요"
        pad.close("곧 시작해요")
        showActions(guess = false, decide = false)
        stage.post { start() }
    }

    private fun buildPanel(): LinearLayout {
        val panel = Theme.vertical(this)
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@CodeTileActivity, Theme.CARD_LIGHT, Theme.CARD, 14f)
            elevation = Theme.dpf(this@CodeTileActivity, 4f)
            val p = Theme.dp(this@CodeTileActivity, 8f)
            setPadding(p, Theme.dp(this@CodeTileActivity, 6f), p, Theme.dp(this@CodeTileActivity, 6f))
        }
        turnLabel = Theme.text(this, "", 12f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        card.addView(turnLabel, Theme.lp(Theme.MATCH, Theme.WRAP))
        nameText = Theme.text(this, "", 22f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 22, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        card.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 28f)).apply { topMargin = Theme.dp(this@CodeTileActivity, 2f) })
        hintText = Theme.text(this, "", 12f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            maxLines = 2
            setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        card.addView(hintText, Theme.lp(Theme.MATCH, Theme.dp(this, 32f)).apply { topMargin = Theme.dp(this@CodeTileActivity, 2f) })
        panel.addView(card, Theme.lp(Theme.MATCH, Theme.WRAP))

        pad = GuessPadView(this).apply {
            background = Theme.card(this@CodeTileActivity, Theme.BG_DEEP, 14f, Theme.CARD_BORDER, 1f)
            onKey = { k -> onKey(k) }
        }
        panel.addView(pad, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@CodeTileActivity, 6f) })

        // 버튼 자리: [추리!] 또는 [계속 추리] [멈추기]
        val actions = FrameLayout(this)
        guessButton = Ui.button(this, "추리!", Theme.GREEN_DARK, 17f) { onGuess() }
        actions.addView(guessButton, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        continueButton = Ui.button(this, "계속 추리", Theme.BLUE, 15f) { onContinue() }
        stopButton = Ui.button(this, "멈추기", Theme.RED_DARK, 15f) { onStopGuessing() }
        decideRow = Theme.horizontal(this)
        decideRow.addView(continueButton, Theme.lp(0, Theme.MATCH, 1.15f))
        decideRow.addView(stopButton, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@CodeTileActivity, 6f) })
        actions.addView(decideRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        panel.addView(actions, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@CodeTileActivity, 6f) })

        peekButton = Ui.button(this, "내 타일 몰래 보기", Theme.GRAY, 13f) { onPeek() }
        panel.addView(peekButton, Theme.lp(Theme.MATCH, Theme.dp(this, 34f)).apply { topMargin = Theme.dp(this@CodeTileActivity, 6f) })
        return panel
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

    private fun updateStrip() {
        for (p in players.indices) {
            if (game.isOut(p)) {
                strip.setOut(p, true)
                strip.setValue(p, "모두 공개")
            } else strip.setValue(p, "숨김 ${game.hiddenCount(p)}장")
        }
    }

    private fun showActions(guess: Boolean, decide: Boolean) {
        guessButton.visibility = if (guess) View.VISIBLE else View.GONE
        decideRow.visibility = if (decide) View.VISIBLE else View.GONE
        if (guess) {
            guessButton.text = "추리!"
            guessButton.isEnabled = false
        }
    }

    private fun setPeek(on: Boolean) {
        peekButton.isEnabled = on
    }

    private fun turnLine() {
        turnLabel.text = "지금 차례 · 더미 ${game.pileSize}장"
    }

    // ───────────── 시작 · 차례 ─────────────

    private fun start() {
        if (finished) return
        val s = game.starter
        val each = GameConfig.startTiles(players.size)
        banner("숨은 숫자 타일", "한 사람에 ${each}장씩 · ${name(s)}님부터!", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { beginTurn() }
    }

    private fun beginTurn() {
        if (game.phase == Phase.OVER || finished) return
        val p = game.current
        busy = true
        clearSelection()
        table.pick = TableView.Pick.NONE
        table.sync(game)
        table.setTurn(p, if (game.drawn >= 0) Tiles.isBlack(game.drawn) else null)
        strip.setCurrent(p)
        updateStrip()
        turnLine()
        setName(p)
        hintText.text = "${name(p)}님이\n몰래 타일 확인 중"
        pad.close("타일 확인 중")
        showActions(guess = true, decide = false)
        setPeek(false)
        val content = MyTilesSecret(game, p, name(p), playerColor(p))
        lastSecret = content
        val info = if (game.drawn >= 0) "더미에서 1장 뽑았어요 (${game.turn}번째 차례)" else "더미가 비어 뽑지 않아요"
        secret = SecretScreen.show(
            this, p, content,
            title = "${name(p)}님 차례",
            info = info,
            guide = "카드를 꾹 누르는 동안만 보여요.\n다 봤으면 [확인 완료]를 누르세요.",
            handoffTitle = SecretScreen.handoffTitle(name(p)),
            handoffSub = "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, ${name(p)}님만 화면을 보세요",
            handoffOk = "내 타일 보기",
        ) {
            secret = null
            startGuess()
        }
    }

    private fun startGuess() {
        if (game.phase != Phase.GUESS) return
        busy = false
        clearSelection()
        table.pick = TableView.Pick.OPPONENTS
        pad.close("상대 타일을 먼저 고르세요")
        hintText.text = if (game.correctThisTurn == 0) "상대의 숨은 타일을\n하나 누르세요" else "계속 추리!\n상대 타일을 누르세요"
        showActions(guess = true, decide = false)
        setPeek(true)
    }

    private fun clearSelection() {
        selPlayer = -1
        selIndex = -1
        selNumber = -1
        table.select(-1, -1)
    }

    // ───────────── 고르기 · 추리 ─────────────

    private fun onTileTap(p: Int, i: Int) {
        if (busy || gamePaused || checkingSecret) return
        when (game.phase) {
            Phase.GUESS -> {
                if (!game.canTarget(p, i)) {
                    beeper.bad()
                    return
                }
                selPlayer = p
                selIndex = i
                selNumber = -1
                table.select(p, i)
                val black = game.isBlackAt(p, i)
                for (k in 0 until GameConfig.NUMBERS) crossed[k] = game.isPubliclyRevealed(Tiles.code(k, black))
                pad.open(black, crossed)
                guessButton.text = "추리!"
                guessButton.isEnabled = false
                hintText.text = "${name(p)}님의 ${i + 1}번째 ${if (black) "검정" else "흰"} 타일\n숫자는 무엇일까요?"
                beeper.step()
            }
            Phase.PENALTY -> {
                if (p != game.current || !game.canReveal(i)) {
                    beeper.bad()
                    return
                }
                doReveal(i)
            }
            else -> Unit
        }
    }

    private fun onKey(k: Int) {
        if (busy || gamePaused || checkingSecret || game.phase != Phase.GUESS || selPlayer < 0) return
        if (pad.isCrossed(k)) {
            beeper.bad()
            hintText.text = "${k}${josa(k)} 이미 공개됐어요\n다른 숫자를 고르세요"
            return
        }
        selNumber = k
        pad.select(k)
        guessButton.text = "${k}! 추리"
        if (!guessButton.isEnabled) {
            guessButton.isEnabled = true
            guessButton.bounce()
        }
        beeper.step()
    }

    private fun onGuess() {
        if (busy || gamePaused || checkingSecret || selPlayer < 0 || selNumber < 0) return
        val r = game.guess(selPlayer, selIndex, selNumber) ?: return
        busy = true
        table.pick = TableView.Pick.NONE
        pad.close(if (r.correct) "정답!" else "땡!")
        showActions(guess = false, decide = false)
        setPeek(false)
        guessButton.bounce()
        if (r.correct) correct(r) else wrong(r)
    }

    private fun tileFx(p: Int, i: Int) {
        if (table.tileCenter(p, i, pa)) {
            toFx(table, pa)
            fx.stars(pa.x, pa.y, 6, t(650))
        }
    }

    private fun correct(r: GuessResult) {
        clearSelection()
        table.sync(game)
        table.flip(r.target, r.index, t(GameConfig.FLIP_MS))
        tileFx(r.target, r.index)
        beeper.good()
        updateStrip()
        strip.pulse(r.guesser)
        hintText.text = "정답! ${name(r.target)}님의\n${r.index + 1}번째 타일은 ${r.number}"
        val after = {
            if (r.gameOver) gameOver() else schedule(t(GameConfig.CORRECT_PAUSE_MS)) { showDecide() }
        }
        if (r.eliminated >= 0 && !r.gameOver) schedule(t(GameConfig.FLIP_MS)) { eliminated(r.eliminated, after) }
        else schedule(t(GameConfig.FLIP_MS)) { after() }
    }

    private fun showDecide() {
        if (game.phase != Phase.DECIDE) return
        busy = false
        hintText.text = if (game.drawn >= 0) "정답! 더 추리할까요?\n멈추면 뽑은 타일은 숨긴 채" else "정답! 더 추리할까요?\n멈추면 차례가 넘어가요"
        showActions(guess = false, decide = true)
        continueButton.bounce()
        setPeek(true)
    }

    private fun onContinue() {
        if (busy || gamePaused || checkingSecret || !game.continueGuess()) return
        beeper.step()
        startGuess()
    }

    private fun onStopGuessing() {
        if (busy || gamePaused || checkingSecret || game.phase != Phase.DECIDE) return
        val me = game.current
        val at = game.stop() ?: return
        busy = true
        showActions(guess = false, decide = false)
        setPeek(false)
        table.sync(game)
        table.setTurn(me, null)
        beeper.turn()
        updateStrip()
        if (at >= 0) {
            table.insert(me, at, t(GameConfig.INSERT_MS))
            hintText.text = "멈춤! 뽑은 타일을\n숨긴 채 ${at + 1}번째 자리에"
        } else hintText.text = "멈춤!\n다음 사람 차례"
        schedule(t(GameConfig.INSERT_MS + GameConfig.AFTER_INSERT_MS)) { beginTurn() }
    }

    private fun wrong(r: GuessResult) {
        table.shake(r.target, r.index, t(GameConfig.WRONG_SHAKE_MS))
        beeper.bad()
        hintText.text = "땡! ${r.number}${josa(r.number)} 아니에요"
        banner("땡!", "${name(r.target)}님의 타일은 ${r.number}${josa(r.number)} 아니에요", BannerTone.BAD, GameConfig.WRONG_BANNER_MS)
        schedule(t(GameConfig.WRONG_BANNER_MS)) {
            clearSelection()
            if (r.penalty) startPenalty()
            else {
                table.sync(game)
                table.setTurn(r.guesser, null)
                table.insert(r.guesser, r.insertedIndex, t(GameConfig.INSERT_MS))
                val n = Tiles.number(r.insertedCode)
                hintText.text = "뽑은 타일 ${n}${josa(n)}\n모두에게 공개!"
                updateStrip()
                schedule(t(GameConfig.INSERT_MS + GameConfig.AFTER_INSERT_MS)) { beginTurn() }
            }
        }
    }

    // ───────────── 더미가 빈 오답: 내 타일 공개 ─────────────

    private fun startPenalty() {
        busy = false
        table.pick = TableView.Pick.OWN
        pad.close("내 타일 하나 공개")
        hintText.text = "더미가 비었어요! 내 숨은\n타일 하나를 눌러 공개"
        setPeek(true)
    }

    private fun doReveal(i: Int) {
        val rr = game.revealOwn(i) ?: return
        busy = true
        setPeek(false)
        table.pick = TableView.Pick.NONE
        table.sync(game)
        table.flip(rr.player, rr.index, t(GameConfig.FLIP_MS))
        beeper.bad()
        updateStrip()
        val n = Tiles.number(rr.code)
        hintText.text = "${name(rr.player)}님의\n${n}${josa(n)} 공개됐어요"
        val after = { if (rr.gameOver) gameOver() else schedule(t(GameConfig.AFTER_INSERT_MS)) { beginTurn() } }
        if (rr.eliminated && !rr.gameOver) schedule(t(GameConfig.FLIP_MS)) { eliminated(rr.player, after) }
        else schedule(t(GameConfig.FLIP_MS)) { after() }
    }

    // ───────────── 탈락 · 끝 ─────────────

    private fun eliminated(p: Int, then: () -> Unit) {
        table.sync(game)
        strip.setOut(p, true)
        updateStrip()
        shakeScreen(Theme.dpf(this, 7f), t(GameConfig.SHAKE_MS))
        banner("${name(p)} 탈락!", "숨은 타일이 모두 공개됐어요", BannerTone.BAD, GameConfig.ELIMINATED_MS)
        schedule(t(GameConfig.ELIMINATED_MS)) { then() }
    }

    private fun gameOver() {
        val w = game.winner
        busy = true
        table.pick = TableView.Pick.NONE
        table.setTurn(w, null)
        table.showSlot = false
        table.sync(game)
        table.revealAll(game)
        updateStrip()
        val last = game.eliminationOrder.lastOrNull() ?: -1
        if (last >= 0) shakeScreen(Theme.dpf(this, 7f), t(GameConfig.SHAKE_MS))
        strip.setCurrent(w)
        turnLabel.text = "최후의 1인"
        setName(w)
        hintText.text = "마지막까지 숫자를\n지켜 냈어요! 1위"
        pad.close("게임 끝")
        showActions(guess = false, decide = false)
        setPeek(false)
        val sub = if (last >= 0) "${name(last)} 탈락 · 최후의 1인! 1위" else "마지막까지 숨은 타일을 지켰어요 · 1위"
        banner("${name(w)} 승리!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
        schedule(t(GameConfig.RESULT_SHOW_MS)) { finishGame() }
    }

    private fun finishGame() {
        if (finished) return
        finished = true
        finishWithRanks(game.ranks())
    }

    // ───────────── 내 타일 다시 보기 ─────────────

    private fun onPeek() {
        if (busy || gamePaused || checkingSecret) return
        if (game.phase != Phase.GUESS && game.phase != Phase.DECIDE && game.phase != Phase.PENALTY) return
        val p = game.current
        val content = MyTilesSecret(game, p, name(p), playerColor(p))
        lastSecret = content
        peek = SecretScreen.show(
            this, p, content,
            title = "${name(p)}님의 타일",
            info = "${name(p)}님만 보세요",
            handoff = false,
            confirmText = "다 봤어요",
        ) { peek = null }
    }

    override fun onGamePause() {
        secret?.hide()
        peek?.hide()
        table.pause()
    }

    override fun onGameResume() {
        table.resume()
    }

    override fun onGameDestroy() {
        cancelTasks()
        secret?.close()
        secret = null
        peek?.close()
        peek = null
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        /** 숫자 뒤 조사 이/가 (영·일·이·삼·사·오·육·칠·팔·구·십·십일) */
        private val JOSA = arrayOf("이", "이", "가", "이", "가", "가", "이", "이", "이", "가", "이", "이")

        fun josa(n: Int): String = JOSA[n.coerceIn(0, 11)]
    }
}
