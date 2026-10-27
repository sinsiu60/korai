package com.siwoo.party.games.baseball.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.numpad.NumPadView
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.baseball.BaseballGame
import com.siwoo.party.games.baseball.BaseballModule
import com.siwoo.party.games.baseball.GameConfig
import com.siwoo.party.games.baseball.GuessResult
import com.siwoo.party.games.baseball.Phase
import kotlin.random.Random

/**
 * 숫자 야구 화면 (2인 전용).
 * 위: 회 표시("3회초") + 플레이어 패널 / 아래: 왼쪽 기록표(두 사람 추측 나란히, RecordBoardView) · 오른쪽 차례 이름 + 숫자 패드(NumPadView)
 * 흐름: 비밀 숫자 정하기 — 한 명씩 (넘기기 화면 → 화면 전체 비밀 입력: 패드로 누르면 칸엔 ●만, 칸을 꾹 누르는 동안만 숫자 보임 → ✓)
 *   → 확인하면 입력 칸·패드를 비우고 화면을 치운 뒤 다음 사람 넘기기 화면 (이전 사람 숫자가 뒤에 남지 않음)
 *   → 선공 뽑기 → 번갈아 추측 → S/B 판정 배너 + 기록표 새 줄 → 3S 면 승리 (선공이 맞히면 후공 마지막 1회, 맞히면 무승부)
 *   → 비밀 숫자 공개 → 순위를 허브로.
 * 규칙 판정은 BaseballGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class BaseballActivity : PartyGameActivity() {

    override val module = BaseballModule

    lateinit var game: BaseballGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var board: RecordBoardView
        private set
    /** 추측 입력 패드 (공개) */
    lateinit var pad: NumPadView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var inningText: TextView

    /** 연출 중 (입력 무시) */
    var busy = true
        private set

    /** 비밀 입력 중인 사람 (-1 = 아님) */
    var entryPlayer = -1
        private set
    /** 비밀 입력 화면의 패드·가려진 칸 (입력 중일 때만) */
    var entryPad: NumPadView? = null
        private set
    var entryDigits: SecretDigitsView? = null
        private set
    private var entryPopup: PopupLayer.Popup? = null
    private var entryMessage: TextView? = null
    /** 떠 있는 기기 넘기기 화면 */
    var handoffPopup: PopupLayer.Popup? = null
        private set

    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = BaseballGame(rnd.nextInt(GameConfig.PLAYERS))
        val root = Theme.vertical(this)
        val pad10 = Theme.dp(this, 10f)
        root.setPadding(pad10, Theme.dp(this, 8f), pad10, Theme.dp(this, 8f))

        // ── 위: 회 + 플레이어 패널 ──
        val top = Theme.horizontal(this)
        inningText = Theme.text(this, "", 15f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            background = Theme.card(this@BaseballActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@BaseballActivity, 4f)
        }
        top.addView(inningText, Theme.lp(Theme.dp(this, 96f), Theme.MATCH))
        strip = PlayerStrip(this, players, false)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@BaseballActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)))

        // ── 아래: 기록표 · 입력판 ──
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        board = RecordBoardView(this)
        board.clock = RecordBoardView.Clock { timers.now() }
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        main.addView(board, Theme.lp(0, Theme.MATCH, 1f))
        main.addView(buildPanel(), Theme.lp(Theme.dp(this, 232f), Theme.MATCH).apply { leftMargin = Theme.dp(this@BaseballActivity, 8f) })
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BaseballActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        inningText.text = "플레이\n볼!"
        nameText.text = "숫자 야구"
        nameText.setTextColor(Theme.GOLD)
        hintText.text = "먼저 비밀 숫자를 정해요"
        for (p in players.indices) strip.setValue(p, "비밀 정하는 중")
        setInput(false)
        stage.post { start() }
    }

    private fun buildPanel(): LinearLayout {
        val col = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@BaseballActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@BaseballActivity, 4f)
            val p = Theme.dp(this@BaseballActivity, 8f)
            setPadding(p, Theme.dp(this@BaseballActivity, 6f), p, p)
        }
        nameText = Theme.text(this, "", 20f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 20, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 26f)))
        hintText = Theme.text(this, "", 12f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(hintText, Theme.lp(Theme.MATCH, Theme.dp(this, 18f)))
        pad = NumPadView(this)
        pad.configure(GameConfig.DIGITS, distinctDigits = true, exactDigits = true)
        pad.onKey = { k -> if (k == NumPadView.KEY_OK) beeper.turn() else beeper.step() }
        pad.onConfirm = { s -> onGuess(s) }
        col.addView(pad, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BaseballActivity, 4f) })
        return col
    }

    private fun name(i: Int) = players[i].name

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun setInput(on: Boolean) {
        pad.isEnabled = on
    }

    // ───────────── 비밀 숫자 정하기 ─────────────

    private fun start() {
        if (finished) return
        banner("숫자 야구", "각자 비밀 숫자 3자리를 몰래 정해요", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { askSecret(0) }
    }

    private fun askSecret(p: Int) {
        if (finished) return
        strip.setCurrent(p)
        nameText.text = name(p)
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        hintText.text = "비밀 숫자를 정하는 중…"
        handoffPopup = handoff(p, SecretScreen.handoffTitle(name(p)), "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, 비밀 숫자 3자리를 몰래 정해요",
            "비밀 숫자 정하기") {
            handoffPopup = null
            openEntry(p)
        }
    }

    /** 화면 전체를 덮는 비밀 입력 화면: 왼쪽 이름·안내·가려진 칸 / 오른쪽 숫자 패드 */
    private fun openEntry(p: Int) {
        entryPlayer = p
        val color = playerColor(p)
        val box = FrameLayout(this).apply {
            background = Theme.background()
            val pd = Theme.dp(this@BaseballActivity, 16f)
            setPadding(pd, pd, pd, pd)
        }
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        left.addView(Theme.text(this, name(p).take(1), 20f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(this@BaseballActivity, 3f))
        }, LinearLayout.LayoutParams(Theme.dp(this, 44f), Theme.dp(this, 44f)))
        left.addView(Theme.text(this, "${name(p)}님의 비밀 숫자", 20f, Theme.shade(color, 1.3f), bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 20, 1, TypedValue.COMPLEX_UNIT_SP)
        }, Theme.lp(Theme.MATCH, Theme.dp(this, 30f)).apply { topMargin = Theme.dp(this@BaseballActivity, 6f) })
        left.addView(Theme.text(this, "서로 다른 숫자 3개 · 0도 쓸 수 있어요", 13f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER },
            Ui.gap(this, 2f))
        val digits = SecretDigitsView(this)
        left.addView(digits, Theme.lp(Theme.MATCH, Theme.dp(this, 70f)).apply { topMargin = Theme.dp(this@BaseballActivity, 12f) })
        left.addView(Theme.text(this, "누른 숫자는 ● 로 가려져요.\n칸을 꾹 누르고 있는 동안만 보여요.", 12f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }, Ui.gap(this, 10f))
        val msg = Theme.text(this, "", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        left.addView(msg, Ui.gap(this, 8f))
        row.addView(left, Theme.lp(0, Theme.WRAP, 1f))
        val ep = NumPadView(this)
        ep.showDisplay = false
        ep.configure(GameConfig.DIGITS, distinctDigits = true, exactDigits = true)
        ep.onChange = { s ->
            digits.setDigits(s)
            updateEntryMessage()
        }
        ep.onKey = { k -> if (k != NumPadView.KEY_OK) beeper.step() }
        ep.onConfirm = { s -> confirmSecret(s) }
        row.addView(ep, Theme.lp(0, Theme.MATCH, 1.05f).apply { leftMargin = Theme.dp(this@BaseballActivity, 18f) })
        box.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        entryPad = ep
        entryDigits = digits
        entryMessage = msg
        updateEntryMessage()
        val w = if (popups.width > 0) popups.width else resources.displayMetrics.widthPixels
        val h = if (popups.height > 0) popups.height else resources.displayMetrics.heightPixels
        entryPopup = popups.show(box, cancelable = false, widthPx = w, heightPx = h, dim = false)
    }

    private fun updateEntryMessage() {
        val ep = entryPad ?: return
        val m = entryMessage ?: return
        val err = ep.input.error
        if (err == null) {
            m.text = "좋아요! ✓ 를 눌러 정하세요"
            m.setTextColor(Theme.GREEN)
        } else {
            m.text = err
            m.setTextColor(Theme.TEXT_SUB)
        }
    }

    private fun confirmSecret(s: String) {
        val p = entryPlayer
        if (p < 0 || gamePaused || !game.setSecret(p, s)) return
        beeper.good()
        closeEntry()
        strip.setValue(p, "비밀 완료!")
        strip.pulse(p)
        if (!game.hasSecret(1 - p)) askSecret(1 - p) else beginPlay()
    }

    /** 비밀 입력 화면 닫기: 숫자부터 지우고 화면 제거 */
    private fun closeEntry() {
        entryDigits?.clear()
        entryPad?.let { it.onChange = null; it.onConfirm = null; it.clear() }
        entryMessage?.text = ""
        entryPopup?.dismiss(animate = false)
        entryPopup = null
        entryPad = null
        entryDigits = null
        entryMessage = null
        entryPlayer = -1
    }

    // ───────────── 추측 ─────────────

    private fun beginPlay() {
        busy = true
        setInput(false)
        strip.setCurrent(-1)
        for (p in players.indices) strip.setValue(p, "추측 0번")
        val f = game.first
        nameText.text = "선공 ${name(f)}"
        nameText.setTextColor(Theme.shade(playerColor(f), 1.25f))
        hintText.text = "${name(f)}님부터 추측해요"
        beeper.dice()
        banner("${name(f)}님 선공!", "번갈아 상대의 비밀 숫자를 맞혀요 · 3S면 승리", ms = GameConfig.STARTER_MS)
        schedule(t(GameConfig.STARTER_MS)) { beginTurn() }
    }

    private fun beginTurn() {
        if (finished || game.phase == Phase.OVER) return
        val p = game.current
        val o = 1 - p
        busy = false
        strip.setCurrent(p)
        board.setCurrent(p)
        val last = game.phase == Phase.LAST_CHANCE
        board.setLastChance(if (last) p else -1)
        inningText.text = if (last) "마지막\n기회!" else "${game.inning}회" + (if (p == game.first) "초" else "말")
        inningText.setTextColor(if (last) Theme.RED else Theme.GOLD)
        nameText.text = "${name(p)} 차례"
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        hintText.text = if (last) "맞히면 무승부! 단 한 번의 기회" else "${name(o)}님의 숫자를 맞혀요"
        pad.clear()
        setInput(true)
        beeper.turn()
    }

    private fun onGuess(s: String) {
        if (busy || gamePaused || finished) return
        val p = game.current
        val r = game.guess(s) ?: return
        busy = true
        setInput(false)
        val j = r.guess.judge
        board.addGuess(p, s, j.strikes, j.balls, t(GameConfig.ROW_POP_MS))
        strip.setValue(p, "추측 ${game.history[p].size}번")
        strip.pulse(p)
        board.lastRowCenter(p, pa)
        toFx(board, pa)
        when {
            j.isHit -> {
                beeper.good()
                fx.stars(pa.x, pa.y, 10, t(700))
                banner("3 스트라이크!", "${name(p)} · $s 정답!", BannerTone.GOOD, GameConfig.JUDGE_MS)
            }
            j.isOut -> {
                beeper.bad()
                banner("아웃!", "${name(p)} · $s — 하나도 없어요", BannerTone.BAD, GameConfig.JUDGE_MS)
            }
            else -> {
                beeper.step()
                fx.ripple(pa.x, pa.y, Theme.GOLD, t(500))
                banner("${j.strikes} 스트라이크 ${j.balls} 볼", "${name(p)} · $s", BannerTone.INFO, GameConfig.JUDGE_MS)
            }
        }
        schedule(t(GameConfig.JUDGE_MS)) { afterGuess(r) }
    }

    private fun afterGuess(r: GuessResult) {
        when {
            r.gameOver -> finishGame()
            r.lastChanceStarted -> {
                val s = game.second
                board.setCurrent(s)
                board.setLastChance(s)
                strip.setCurrent(s)
                inningText.text = "마지막\n기회!"
                inningText.setTextColor(Theme.RED)
                shakeScreen(Theme.dpf(this, 5f), t(300))
                banner("마지막 기회!", "${name(s)}님도 이번에 맞히면 무승부", BannerTone.BAD, GameConfig.LAST_CHANCE_MS)
                schedule(t(GameConfig.LAST_CHANCE_MS)) { beginTurn() }
            }
            else -> beginTurn()
        }
    }

    // ───────────── 끝 ─────────────

    private fun finishGame() {
        if (finished) return
        finished = true
        busy = true
        setInput(false)
        board.setCurrent(-1)
        board.setLastChance(-1)
        val s0 = game.secretOf(0) ?: ""
        val s1 = game.secretOf(1) ?: ""
        board.reveal(s0, s1, t(GameConfig.REVEAL_MS))
        beeper.dice()
        for (p in players.indices) {
            board.headCenter(p, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 6, t(GameConfig.REVEAL_MS))
        }
        inningText.text = "경기\n종료"
        inningText.setTextColor(Theme.GOLD)
        val sub = "비밀 숫자 · ${name(0)} $s0 · ${name(1)} $s1"
        schedule(t(GameConfig.REVEAL_MS)) {
            val w = game.winner
            if (w < 0) {
                strip.setCurrent(-1)
                for (p in players.indices) strip.setValue(p, "무승부")
                nameText.text = "무승부!"
                nameText.setTextColor(Theme.GOLD)
                hintText.text = "둘 다 3스트라이크!"
                banner("무승부!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            } else {
                strip.setCurrent(w)
                strip.setValue(w, "1위")
                strip.setValue(1 - w, "2위")
                strip.pulse(w)
                nameText.text = "${name(w)} 승리!"
                nameText.setTextColor(Theme.shade(playerColor(w), 1.25f))
                hintText.text = "추측 ${game.history[w].size}번 만에 맞혔어요"
                banner("${name(w)} 승리!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            }
            beeper.good()
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
            timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    override fun onGamePause() {
        entryDigits?.hide()
    }

    override fun onGameDestroy() {
        cancelTasks()
        closeEntry()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
