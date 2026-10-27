package com.siwoo.party.games.clap369.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.clap369.Clap369
import com.siwoo.party.games.clap369.Clap369Game
import com.siwoo.party.games.clap369.Clap369Module
import com.siwoo.party.games.clap369.Fail
import com.siwoo.party.games.clap369.GameConfig
import com.siwoo.party.games.clap369.Key
import com.siwoo.party.games.clap369.Phase
import com.siwoo.party.games.clap369.Press
import com.siwoo.party.games.clap369.Tick

/**
 * 369 박수 화면 (차례대로, 2~4인, 한 기기를 가운데 두고).
 * 위: PlayerStrip (지금 차례 빛·탈락 도장), 가운데: TurnView ("○○ 차례" · 말하면서 누르세요! · 방금 숫자 · 친 짝 수),
 * TimerBarView (제한 시간 — 규칙 시간이라 배율 없음, 게임 시계라 일시정지 때 멈춤), 아래: ClapPadView [숫자 N] [짝!].
 * 판정·시간은 Clap369Game. 마감·확인 시간은 timers 로 예약 (일시정지 자동).
 * 일시정지 뒤 돌아오면 입력 중이던 차례는 처음부터 다시 (리듬이 끊겼으므로 공정하게).
 */
class Clap369Activity : PartyGameActivity(), ClapPadView.Listener {

    override val module = Clap369Module

    lateinit var game: Clap369Game
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turn: TurnView
        private set
    lateinit var bar: TimerBarView
        private set
    lateinit var pad: ClapPadView
        private set
    private var tickTask: GameTimers.Task? = null
    /** 화면에 보이는 차례 (번호·사람) — 바뀌면 화면 갱신 */
    private var shownNumber = -1
    private var shownPlayer = -1
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        root.background = Theme.Party.background()
        game = Clap369Game(players.size)
        val col = Theme.vertical(this)
        val pad8 = Theme.dp(this, 8f)
        col.setPadding(pad8 + Theme.dp(this, 4f), Theme.dp(this, 6f), pad8 + Theme.dp(this, 4f), pad8)
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        strip = PlayerStrip(this, players, vertical = false)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 56f)))

        turn = TurnView(this)
        turn.clock = TurnView.Clock { timers.now() }
        turn.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        col.addView(turn, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@Clap369Activity, 6f) })

        bar = TimerBarView(this)
        col.addView(bar, Theme.lp(Theme.MATCH, Theme.dp(this, 30f)).apply { topMargin = Theme.dp(this@Clap369Activity, 4f) })

        pad = ClapPadView(this)
        pad.clock = ClapPadView.Clock { timers.now() }
        pad.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        pad.listener = this
        col.addView(pad, Theme.lp(Theme.MATCH, Theme.dp(this, 138f)).apply { topMargin = Theme.dp(this@Clap369Activity, 6f) })

        turn.setReady()
        stage.post { intro() }
    }

    private fun intro() {
        banner(module.name, "3·6·9가 들어간 개수만큼 짝! 아니면 숫자!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            countdown {
                game.startTurn(timers.now())
                showTurn()
            }
        }
    }

    /** 새 차례 화면: 이름·방금 숫자·[숫자 N]·시간 막대 + 마감 예약 */
    private fun showTurn() {
        shownNumber = game.number
        shownPlayer = game.current
        strip.setCurrent(game.current)
        turn.setTurn(players[game.current].name, playerColor(game.current), game.number - 1)
        pad.setNumber(game.number)
        pad.active = true
        bar.start((game.deadline - timers.now()).coerceAtLeast(1))
        beeper.step()
        scheduleTick()
    }

    /** 다음 시계 일(마감·확인 끝) 예약 — 규칙 시간이라 배율 없음 */
    private fun scheduleTick() {
        tickTask?.cancel()
        tickTask = null
        val ms = game.msUntilNextEvent(timers.now())
        if (ms >= 0) tickTask = timers.after(ms) { onTick() }
    }

    private fun onTick() {
        tickTask = null
        when (val r = game.update(timers.now())) {
            is Tick.Next -> showTurn()
            is Tick.Out -> onOut(r.out)
            Tick.None -> scheduleTick()
        }
    }

    override fun onKey(key: Key) {
        if (gamePaused) return
        val now = timers.now()
        // 확인 시간이 막 끝났는데 예약이 아직이면 먼저 다음 차례로 (그 사람 입력으로 처리)
        if (game.phase == Phase.SETTLE && now >= game.settleUntil) onTick()
        when (val r = game.press(key, now)) {
            is Press.Clapped -> {
                turn.setClaps(r.count)
                beeper.dice()
                padFx(Key.CLAP, 4)
            }
            is Press.Done -> {
                if (key == Key.CLAP) {
                    turn.setClaps(r.claps)
                    beeper.dice()
                } else {
                    beeper.good()
                }
                bar.stop()
                padFx(key, 7)
                scheduleTick()
            }
            is Press.Out -> onOut(r)
            Press.Ignored -> Unit
        }
    }

    private fun padFx(key: Key, n: Int) {
        pad.buttonCenter(key, pa)
        toFx(pad, pa)
        fx.stars(pa.x, pa.y, n, t(600))
    }

    /** 탈락: 이유 + 정답 안내 → 다음 사람 같은 숫자부터 (또는 끝) */
    private fun onOut(o: Press.Out) {
        tickTask?.cancel()
        tickTask = null
        bar.stop()
        pad.active = false
        strip.setOut(o.player, true)
        strip.setValue(o.player, "${players.size - game.outOrder.size + 1}위")
        val need = Clap369.claps(o.number)
        val rule = if (need == 0) "${o.number} → 숫자!" else "${o.number} → 짝 ${need}번!"
        val line = o.reason.label + " · " + rule
        turn.showOut(players[o.player].name, line)
        beeper.bad()
        shakeScreen(Theme.dpf(this, 8f), t(400))
        if (o.gameOver) {
            timers.after(t(GameConfig.ELIM_SHOW_MS)) { finishGame() }
        } else {
            banner(players[o.player].name + " 탈락!", if (o.reason == Fail.TIMEOUT) "시간 초과 · $rule" else line, BannerTone.BAD, GameConfig.ELIM_SHOW_MS)
            timers.after(t(GameConfig.ELIM_SHOW_MS)) {
                game.resumeAfterOut(timers.now())
                showTurn()
            }
        }
    }

    private fun finishGame() {
        val w = game.winner()
        strip.setCurrent(w)
        strip.setValue(w, "1위")
        strip.pulse(w)
        banner(players[w].name + " 우승!", "${game.number}까지 버텼어요", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        timers.after(t(GameConfig.END_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        turn.frozen = true
        pad.frozen = true
    }

    override fun onGameResume() {
        turn.frozen = false
        pad.frozen = false
        // 입력 중이던 차례는 처음부터 다시 (짝 수·제한 시간 새로)
        if (game.phase == Phase.INPUT) {
            game.restartTurn(timers.now())
            showTurn()
        }
    }

    /** 화면에 보이는 차례 숫자 (테스트용) */
    val shownTurnNumber: Int get() = shownNumber

    /** 화면에 보이는 차례 사람 (테스트용) */
    val shownTurnPlayer: Int get() = shownPlayer
}
