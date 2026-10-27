package com.siwoo.party.games.updown.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.numpad.NumPadView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.updown.GameConfig
import com.siwoo.party.games.updown.Phase
import com.siwoo.party.games.updown.Step
import com.siwoo.party.games.updown.UpDownGame
import com.siwoo.party.games.updown.UpDownModule
import com.siwoo.party.games.updown.Verdict
import kotlin.random.Random

/**
 * 폭탄 숫자 업다운 화면 (2~4인, 차례대로).
 * 위: 라운드 표시 + 플레이어 패널(해골 개수) / 아래: 왼쪽 범위 막대(RangeBarView) · 오른쪽 차례 이름 + 숫자 패드(NumPadView)
 * 흐름: "N라운드" 배너 (1~100 중 폭탄 숫자) → 차례대로 열린 범위 안 숫자를 패드로 입력 (패드 범위 = 열린 범위, 잘린 값 제외)
 *   → 폭탄보다 크면 위쪽, 작으면 아래쪽이 잘려 날아감 → 폭탄을 누르면 폭발(화면 흔들기 + 번쩍) + 해골 +1 → 다음 라운드 (시작 사람 돌아감)
 *   → 라운드 수 = 인원수 → 해골 적은 순 순위를 허브로. 폭탄만 남으면 다음 사람 패드에 그 숫자가 미리 들어가 있다.
 * 규칙 판정은 UpDownGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class UpDownActivity : PartyGameActivity() {

    override val module = UpDownModule

    lateinit var game: UpDownGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var bar: RangeBarView
        private set
    lateinit var pad: NumPadView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var roundText: TextView

    /** 연출 중 (입력 무시) */
    var busy = true
        private set

    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = UpDownGame(players.size, rnd, rnd.nextInt(players.size))
        val root = Theme.vertical(this)
        val pad10 = Theme.dp(this, 10f)
        root.setPadding(pad10, Theme.dp(this, 8f), pad10, Theme.dp(this, 8f))

        // ── 위: 라운드 + 플레이어 패널 ──
        val top = Theme.horizontal(this)
        roundText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
            background = Theme.card(this@UpDownActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@UpDownActivity, 4f)
        }
        top.addView(roundText, Theme.lp(Theme.dp(this, 92f), Theme.MATCH))
        strip = PlayerStrip(this, players, false, StripIcon.SKULL)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@UpDownActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))

        // ── 아래: 범위 막대 · 입력판 ──
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        bar = RangeBarView(this).apply {
            background = Theme.verticalCard(this@UpDownActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@UpDownActivity, 4f)
        }
        bar.clock = RangeBarView.Clock { timers.now() }
        main.addView(bar, Theme.lp(0, Theme.MATCH, 1f))
        main.addView(buildPanel(), Theme.lp(Theme.dp(this, 232f), Theme.MATCH).apply { leftMargin = Theme.dp(this@UpDownActivity, 8f) })
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@UpDownActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        roundText.text = "폭탄\n업다운"
        nameText.text = "준비"
        hintText.text = ""
        bar.reset("1~100 중 폭탄 숫자가 숨어 있어요")
        updateSkulls()
        pad.isEnabled = false
        stage.post { start() }
    }

    private fun buildPanel(): LinearLayout {
        val col = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@UpDownActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@UpDownActivity, 4f)
            val p = Theme.dp(this@UpDownActivity, 8f)
            setPadding(p, Theme.dp(this@UpDownActivity, 6f), p, p)
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
        pad.configure(3, allowLeadingZero = false, min = GameConfig.MIN_NUMBER, max = GameConfig.MAX_NUMBER)
        pad.onKey = { k -> if (k != NumPadView.KEY_OK) beeper.step() }
        pad.onConfirm = { s -> onGuess(s) }
        col.addView(pad, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@UpDownActivity, 4f) })
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

    private fun updateSkulls() {
        for (p in players.indices) {
            strip.setCount(p, game.skulls[p])
            strip.setValue(p, if (game.skulls[p] == 0) "해골 없음" else "해골 ${game.skulls[p]}개")
        }
    }

    // ───────────── 라운드 ─────────────

    private fun start() {
        if (finished) return
        banner("폭탄 숫자 업다운", "1~100 중 폭탄 숫자를 피하세요! · ${game.totalRounds}라운드", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { startRound() }
    }

    private fun startRound() {
        if (finished) return
        val s = game.startRound()
        busy = true
        pad.isEnabled = false
        pad.setRange(game.minAllowed, game.maxAllowed)
        pad.clear()
        roundText.text = "${game.round} / ${game.totalRounds}\n라운드"
        bar.reset("${game.round}라운드 · 폭탄은 이 안에 숨어 있어요")
        strip.setCurrent(s)
        nameText.text = name(s)
        nameText.setTextColor(Theme.shade(playerColor(s), 1.25f))
        hintText.text = "새 폭탄을 숨겼어요!"
        beeper.dice()
        banner("${game.round}라운드", "${name(s)}님부터 · 1~100 중 폭탄 숫자를 피하세요", ms = GameConfig.ROUND_INTRO_MS)
        schedule(t(GameConfig.ROUND_INTRO_MS)) { beginTurn() }
    }

    private fun beginTurn() {
        if (finished || game.phase != Phase.PLAYING) return
        val p = game.current
        busy = false
        strip.setCurrent(p)
        nameText.text = "${name(p)} 차례"
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        pad.setRange(game.minAllowed, game.maxAllowed)
        pad.clear()
        if (game.isForced) {
            hintText.text = "남은 숫자는 폭탄뿐… 눌러야 해요!"
            hintText.setTextColor(Theme.RED)
            pad.value = game.minAllowed.toString()
        } else {
            hintText.text = "폭탄을 피해 숫자를 골라요 · 남은 ${game.remaining}개"
            hintText.setTextColor(Theme.TEXT_SUB)
        }
        pad.isEnabled = true
        beeper.turn()
    }

    private fun onGuess(s: String) {
        if (busy || gamePaused || finished) return
        val v = s.toIntOrNull() ?: return
        val p = game.current
        val step = game.guess(v)
        if (step == null) {
            beeper.bad()
            Ui.shake(pad)
            return
        }
        busy = true
        pad.isEnabled = false
        strip.pulse(p)
        when (step.verdict) {
            Verdict.DOWN, Verdict.UP -> onCut(step)
            Verdict.BOOM -> onBoom(step)
        }
    }

    private fun onCut(step: Step) {
        val down = step.verdict == Verdict.DOWN
        bar.cut(step.value, down, game.low, game.high, t(GameConfig.CUT_MS))
        bar.setCaption("${name(step.player)} → ${step.value} · " + if (down) "더 작아요" else "더 커요")
        beeper.good()
        schedule(t(GameConfig.TURN_GAP_MS)) { beginTurn() }
    }

    private fun onBoom(step: Step) {
        val p = step.player
        bar.boom(game.bomb, t(GameConfig.BOOM_MS))
        bar.setCaption("${name(p)} → ${step.value} · 폭탄!")
        pad.setRange(GameConfig.MIN_NUMBER, GameConfig.MAX_NUMBER)
        pad.clear()
        nameText.text = "펑!!"
        nameText.setTextColor(Theme.RED)
        hintText.text = "${name(p)}님 해골 +1"
        hintText.setTextColor(Theme.TEXT_SUB)
        beeper.bad()
        shakeScreen(Theme.dpf(this, 16f), t(GameConfig.SHAKE_MS))
        bar.bombAnchor(pa)
        toFx(bar, pa)
        fx.stars(pa.x, pa.y, 12, t(GameConfig.BOOM_MS))
        fx.ripple(pa.x, pa.y, Theme.RED, t(GameConfig.BOOM_MS))
        updateSkulls()
        strip.pulse(p)
        val sub = if (step.gameOver) "${name(p)} 해골 +1 · 마지막 라운드 끝!"
        else "${name(p)} 해골 +1 · 남은 라운드 ${game.roundsLeft}"
        banner("펑!! ${game.bomb}", sub, BannerTone.BAD, GameConfig.BOOM_SHOW_MS)
        schedule(t(GameConfig.BOOM_SHOW_MS)) { if (step.gameOver) finishGame() else startRound() }
    }

    private fun finishGame() {
        if (finished) return
        finished = true
        busy = true
        pad.isEnabled = false
        val ranks = game.ranks()
        for (p in players.indices) strip.setValue(p, "${ranks[p]}위 · 해골 ${game.skulls[p]}")
        val winners = players.indices.filter { ranks[it] == 1 }
        strip.setCurrent(if (winners.size == 1) winners[0] else -1)
        for (w in winners) strip.pulse(w)
        val names = winners.joinToString(", ") { name(it) }
        roundText.text = "경기\n종료"
        nameText.text = if (winners.size == players.size) "모두 1위!" else "$names 1위!"
        nameText.setTextColor(Theme.GOLD)
        hintText.text = "해골이 가장 적어요"
        hintText.setTextColor(Theme.TEXT_SUB)
        val title = if (winners.size == players.size) "모두 1위!" else "$names 승리!"
        banner(title, "해골 적은 순으로 순위", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
        beeper.good()
        timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGameDestroy() {
        cancelTasks()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
