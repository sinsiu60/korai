package com.siwoo.party.games.colorcode.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.colorcode.ColorCodeGame
import com.siwoo.party.games.colorcode.ColorCodeModule
import com.siwoo.party.games.colorcode.GameConfig
import com.siwoo.party.games.colorcode.GuessResult
import com.siwoo.party.games.colorcode.Phase
import kotlin.random.Random

/**
 * 색 코드 맞히기 화면 (2인 전용).
 * 위: 몇 번째 추리 표시 + 플레이어 패널 / 아래: 왼쪽 추리 기록판 두 개(RecordBoardView) · 오른쪽 차례 이름 + 입력판(CodeInputView) + [지우기][확인]
 * 흐름: 비밀 코드 정하기 — 한 명씩 (공통 넘기기 화면 SecretScreen.handoffOnly → 화면 전체 비밀 화면 SecretEntryScreen: 4칸을 색으로 채움, [무작위])
 *   → 정하면 입력판의 색부터 지우고 화면을 치운 뒤 다음 사람 넘기기 화면 (이전 사람 코드가 뒤에 남지 않음)
 *   → 사람 1 부터 번갈아 상대 코드 추리 (기록판·입력은 모두 공개 정보) → 금색·은색 핀 배너 + 기록판 새 줄
 *   → 선공이 맞히면 후공의 같은 번째 추리가 "마지막 기회" → 누가 맞혔거나 10번째가 끝나면 정답 공개 → 순위를 허브로.
 * 규칙 판정은 ColorCodeGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class ColorCodeActivity : PartyGameActivity() {

    override val module = ColorCodeModule

    lateinit var game: ColorCodeGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var board: RecordBoardView
        private set
    /** 추리 입력판 (공개) */
    lateinit var input: CodeInputView
        private set
    lateinit var clearButton: GameButton
        private set
    lateinit var okButton: GameButton
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var roundText: TextView

    private lateinit var random: Random

    /** 연출 중 (입력 무시) */
    var busy = true
        private set

    /** 떠 있는 비밀 코드 정하기 화면 */
    var entry: SecretEntryScreen? = null
        private set
    /** 떠 있는 넘기기 화면 (공통 SecretScreen.handoffOnly) */
    private var handoff: SecretHandle? = null
    val handoffPopup: PopupLayer.Popup? get() = handoff?.handoffPopup

    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        random = randomOverride ?: Random.Default
        game = ColorCodeGame(ColorCodeSettings.duplicates(this), first = 0)
        val root = Theme.vertical(this)
        val pad10 = Theme.dp(this, 10f)
        root.setPadding(pad10, Theme.dp(this, 8f), pad10, Theme.dp(this, 8f))

        // ── 위: 몇 번째 + 플레이어 패널 ──
        val top = Theme.horizontal(this)
        roundText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.05f)
            background = Theme.card(this@ColorCodeActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@ColorCodeActivity, 4f)
        }
        top.addView(roundText, Theme.lp(Theme.dp(this, 96f), Theme.MATCH))
        strip = PlayerStrip(this, players, false)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@ColorCodeActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)))

        // ── 아래: 기록판 · 입력판 ──
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        board = RecordBoardView(this)
        board.clock = RecordBoardView.Clock { timers.now() }
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        main.addView(board, Theme.lp(0, Theme.MATCH, 1f))
        main.addView(buildPanel(), Theme.lp(Theme.dp(this, 228f), Theme.MATCH).apply { leftMargin = Theme.dp(this@ColorCodeActivity, 8f) })
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@ColorCodeActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        roundText.text = "비밀\n정하기"
        nameText.text = "색 코드 맞히기"
        nameText.setTextColor(Theme.GOLD)
        hintText.text = ruleText()
        for (p in players.indices) strip.setValue(p, "비밀 정하는 중")
        setInput(false)
        stage.post { start() }
    }

    private fun buildPanel(): LinearLayout {
        val col = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@ColorCodeActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@ColorCodeActivity, 4f)
            val p = Theme.dp(this@ColorCodeActivity, 8f)
            setPadding(p, Theme.dp(this@ColorCodeActivity, 5f), p, p)
        }
        nameText = Theme.text(this, "", 19f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 19, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 25f)))
        hintText = Theme.text(this, "", 12f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(hintText, Theme.lp(Theme.MATCH, Theme.dp(this, 17f)))
        input = CodeInputView(this)
        input.clock = CodeInputView.Clock { timers.now() }
        input.allowDuplicates = game.allowDuplicates
        input.popMs = t(260)
        input.onPut = { beeper.step() }
        input.onReject = { beeper.bad() }
        input.onChange = { onInputChanged() }
        col.addView(input, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@ColorCodeActivity, 5f) })
        clearButton = Ui.button(this, "지우기", Theme.GRAY, 15f) { if (!busy && !gamePaused) input.backspace() }
        okButton = Ui.button(this, "확인", Theme.GREEN_DARK, 17f) { onConfirm() }
        col.addView(Ui.buttonRow(this, clearButton, okButton, heightDp = 42f), Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply {
            topMargin = Theme.dp(this@ColorCodeActivity, 6f)
        })
        return col
    }

    private fun name(i: Int) = players[i].name

    private fun ruleText() = if (game.allowDuplicates) "6색 · 4칸 · 같은 색 중복 가능" else "6색 · 4칸 · 같은 색 중복 없음"

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun setInput(on: Boolean) {
        input.inputEnabled = on
        clearButton.isEnabled = on && input.filled > 0
        okButton.isEnabled = on && input.isComplete
    }

    /** 입력판이 바뀌면: 지금 판 다음 줄에 미리 보기 + 단추 상태 */
    private fun onInputChanged() {
        for (i in 0 until GameConfig.SLOTS) board.setPending(i, input.colorAt(i))
        val on = input.inputEnabled
        clearButton.isEnabled = on && input.filled > 0
        val was = okButton.isEnabled
        okButton.isEnabled = on && input.isComplete
        if (!was && okButton.isEnabled) okButton.bounce()
    }

    // ───────────── 비밀 코드 정하기 ─────────────

    private fun start() {
        if (finished) return
        banner("색 코드 맞히기", "각자 비밀 색 코드 4칸을 몰래 정해요 · " + (if (game.allowDuplicates) "같은 색 중복 가능" else "같은 색 중복 없음"),
            ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { askSecret(0) }
    }

    private fun askSecret(p: Int) {
        if (finished) return
        busy = true
        strip.setCurrent(p)
        nameText.text = name(p)
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        hintText.text = "비밀 코드를 정하는 중…"
        handoff = SecretScreen.handoffOnly(
            this, p,
            title = "${name(p)}님 비밀 코드 정하기",
            sub = "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, ${name(p)}님만 화면을 보세요",
            okText = "비밀 코드 정하기",
        ) {
            handoff = null
            openEntry(p)
        }
    }

    private fun openEntry(p: Int) {
        if (finished) return
        val s = SecretEntryScreen(this, p, game.allowDuplicates)
        s.input.clock = CodeInputView.Clock { timers.now() }
        s.input.popMs = t(260)
        s.input.onPut = { beeper.step() }
        s.input.onReject = { beeper.bad() }
        s.onRandom = {
            s.input.setCode(ColorCodeGame.randomCode(random, game.allowDuplicates))
            s.randomButton.bounce()
            beeper.dice()
        }
        s.onDone = { confirmSecret() }
        entry = s.show()
        busy = false
    }

    private fun confirmSecret() {
        val s = entry ?: return
        if (busy || gamePaused || finished) return
        val p = s.player
        val code = s.input.code()
        if (code == null || !game.setSecret(p, code)) {
            Ui.shake(s.doneButton)
            beeper.bad()
            return
        }
        busy = true
        code.fill(-1) // 지역 복사본도 지움
        closeEntry()
        beeper.good()
        strip.setValue(p, "준비 완료")
        strip.pulse(p)
        if (game.phase == Phase.PLAYING) schedule(t(GameConfig.SETUP_NEXT_MS)) { beginPlay() }
        else schedule(t(GameConfig.SETUP_NEXT_MS)) { askSecret(1 - p) }
    }

    /** 비밀 화면 닫기: 색부터 지우고 화면 제거 */
    private fun closeEntry() {
        entry?.close()
        entry = null
    }

    // ───────────── 추리 ─────────────

    private fun beginPlay() {
        if (finished) return
        busy = true
        setInput(false)
        strip.setCurrent(-1)
        for (p in players.indices) strip.setValue(p, "추리 0번")
        val f = game.first
        roundText.text = "추리\n시작!"
        nameText.text = "추리 시작!"
        nameText.setTextColor(Theme.GOLD)
        hintText.text = "${name(f)}님부터 번갈아 추리해요"
        beeper.dice()
        banner("추리 시작!", "${name(f)}님부터 번갈아 · 금색 핀 4개면 정답 · 최대 ${GameConfig.MAX_TRIES}번", ms = GameConfig.START_MS)
        schedule(t(GameConfig.START_MS)) { beginTurn() }
    }

    private fun beginTurn() {
        if (finished || game.phase != Phase.PLAYING) return
        val p = game.current
        val o = 1 - p
        busy = false
        strip.setCurrent(p)
        board.setCurrent(p)
        val last = game.lastChance
        board.setLastChance(if (last) p else -1)
        roundText.text = if (last) "마지막\n기회!" else "${game.round}번째\n/ ${GameConfig.MAX_TRIES}"
        roundText.setTextColor(if (last) Theme.RED else Theme.GOLD)
        nameText.text = "${name(p)} 차례"
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        hintText.text = if (last) "맞히면 무승부! 단 한 번의 기회" else "${name(o)}님의 코드를 맞혀요 · ${game.round}번째"
        input.clear()
        setInput(true)
        beeper.turn()
    }

    private fun onConfirm() {
        if (busy || gamePaused || finished) return
        val code = input.code() ?: return
        val p = game.current
        val r = game.guess(code) ?: run {
            beeper.bad()
            Ui.shake(okButton)
            return
        }
        busy = true
        setInput(false)
        val pg = r.record.pegs
        board.addGuess(p, code, pg.gold, pg.silver, t(GameConfig.ROW_POP_MS))
        board.setCurrent(-1) // 결과 배너 동안엔 다음 줄 화살표를 숨김
        input.clear()
        strip.setValue(p, if (pg.isSolved) "정답! ${r.record.number}번" else "추리 ${game.tries(p)}번")
        strip.pulse(p)
        board.lastRowCenter(p, pa)
        toFx(board, pa)
        when {
            pg.isSolved -> {
                beeper.good()
                fx.stars(pa.x, pa.y, 10, t(700))
                banner("정답!", "${name(p)} · ${r.record.number}번 만에 맞혔어요", BannerTone.GOOD, GameConfig.JUDGE_MS)
            }
            pg.gold == 0 && pg.silver == 0 -> {
                beeper.bad()
                banner("핀 없음!", "${name(p)} · 4색 모두 코드에 없어요", BannerTone.BAD, GameConfig.JUDGE_MS)
            }
            else -> {
                beeper.step()
                fx.ripple(pa.x, pa.y, Theme.GOLD, t(500))
                banner("금색 ${pg.gold} · 은색 ${pg.silver}", "${name(p)} · ${r.record.number}번째 추리", BannerTone.INFO, GameConfig.JUDGE_MS)
            }
        }
        schedule(t(GameConfig.JUDGE_MS)) { afterGuess(r) }
    }

    private fun afterGuess(r: GuessResult) {
        when {
            r.gameOver -> finishGame()
            r.lastChanceStarted -> {
                val s = game.current
                board.setCurrent(s)
                board.setLastChance(s)
                strip.setCurrent(s)
                roundText.text = "마지막\n기회!"
                roundText.setTextColor(Theme.RED)
                shakeScreen(Theme.dpf(this, 5f), t(300))
                banner("마지막 기회!", "${name(s)}님도 ${game.round}번째에 맞히면 무승부", BannerTone.BAD, GameConfig.LAST_CHANCE_MS)
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
        board.clearPending()
        // 판 0 은 사람 1 의 코드를, 판 1 은 사람 0 의 코드를 맞히는 판
        val c1 = game.secretOf(1) ?: IntArray(GameConfig.SLOTS)
        val c0 = game.secretOf(0) ?: IntArray(GameConfig.SLOTS)
        board.reveal(c1, c0, t(GameConfig.REVEAL_MS))
        beeper.dice()
        for (p in players.indices) {
            board.headCenter(p, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 6, t(GameConfig.REVEAL_MS))
        }
        roundText.text = "정답\n공개"
        roundText.setTextColor(Theme.GOLD)
        strip.setCurrent(-1)
        nameText.text = "정답 공개!"
        nameText.setTextColor(Theme.GOLD)
        hintText.text = "두 사람의 비밀 코드를 공개해요"
        val sub = players.indices.joinToString(" · ") { p ->
            val s = game.solvedAt(p)
            name(p) + " " + (if (s > 0) "${s}번" else "실패")
        }
        schedule(t(GameConfig.REVEAL_MS)) {
            val w = game.winner
            val ranks = game.ranks()
            for (p in players.indices) strip.setValue(p, "${ranks[p]}위 · " + (if (game.solvedAt(p) > 0) "${game.solvedAt(p)}번" else "실패"))
            if (w < 0) {
                strip.setCurrent(-1)
                nameText.text = "무승부!"
                nameText.setTextColor(Theme.GOLD)
                hintText.text = if (game.solvedAt(0) > 0) "같은 횟수로 맞혔어요!" else "둘 다 못 맞혔어요"
                banner("무승부!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            } else {
                strip.setCurrent(w)
                strip.pulse(w)
                nameText.text = "${name(w)} 승리!"
                nameText.setTextColor(Theme.shade(playerColor(w), 1.25f))
                hintText.text = "${game.solvedAt(w)}번 만에 맞혔어요"
                banner("${name(w)} 승리!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            }
            beeper.good()
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
            timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(ranks) }
        }
    }

    override fun onGamePause() {
        // 일시정지 메뉴 뒤로 비밀 코드가 비치지 않게 가린다
        entry?.setHidden(true)
    }

    override fun onGameResume() {
        entry?.setHidden(false)
    }

    override fun onGameDestroy() {
        cancelTasks()
        handoff?.close()
        handoff = null
        closeEntry()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
