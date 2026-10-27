package com.siwoo.party.games.simon.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.simon.GameConfig
import com.siwoo.party.games.simon.OutReason
import com.siwoo.party.games.simon.Phase
import com.siwoo.party.games.simon.Press
import com.siwoo.party.games.simon.SimonGame
import com.siwoo.party.games.simon.SimonModule
import kotlin.random.Random

/**
 * 패턴 기억 릴레이 화면 (차례대로).
 * 왼쪽: 차례 카드(지금 차례 이름 크게 + 안내) + 5초 시간 막대 + 플레이어 패널, 오른쪽: 4색 사이먼 판.
 * 흐름: 시작 배너 → 차례인 사람이 지금까지의 순서를 그대로 누름 (누를 때마다 색이 켜지고 그 색의 음)
 *  → 다 맞히면 "+1 새 색을 누르세요!" → 새 색 → 길이 숫자가 톡 → 다음 사람
 *  → 틀리거나 5초 동안 멈추면 탈락 (정답 색이 깜빡임) → 같은 순서를 다음 사람이 이어감
 *  → 한 명 남으면 1위, 나머지는 늦게 탈락한 순으로 허브에 돌려준다.
 * 규칙 판정은 SimonGame (시간은 timers.now() = 일시정지를 뺀 게임 시간), 여기서는 연출만 timers 로 예약한다.
 */
class SimonActivity : PartyGameActivity(), SimonBoardView.Listener {

    override val module = SimonModule

    lateinit var game: SimonGame
        private set
    lateinit var board: SimonBoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turnCard: SimonTurnView
        private set
    lateinit var timerBar: TimerBarView
        private set

    /** 연출 중 (버튼 무시) */
    var busy = true
        private set
    private var timeoutTask: GameTimers.Task? = null
    val tones = SimonTones()
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = SimonGame(players.size, randomOverride ?: Random.Default)
        val pad = Theme.dp(this, 10f)
        val row = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val left = Theme.vertical(this)
        turnCard = SimonTurnView(this)
        left.addView(turnCard, Theme.lp(Theme.MATCH, 0, 1f))
        timerBar = TimerBarView(this)
        left.addView(timerBar, Theme.lp(Theme.MATCH, Theme.dp(this, 30f)).apply { topMargin = Theme.dp(this@SimonActivity, 6f) })
        strip = PlayerStrip(this, players, vertical = true)
        val tile = Theme.dp(this, 44f)
        val gap = Theme.dp(this, 6f)
        left.addView(strip, Theme.lp(Theme.MATCH, tile * players.size + gap * (players.size - 1)).apply { topMargin = Theme.dp(this@SimonActivity, 6f) })
        row.addView(left, LinearLayout.LayoutParams(Theme.dp(this, 184f), LinearLayout.LayoutParams.MATCH_PARENT))
        board = SimonBoardView(this)
        board.listener = this
        row.addView(board, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = pad })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        turnCard.set(module.name, "준비", Theme.GOLD, "")
        board.setLength(0, false, 0)
        stage.post { startGame() }
    }

    private fun lightMs(): Long = t(GameConfig.PRESS_LIGHT_MS).coerceAtLeast(GameConfig.MIN_LIGHT_MS)
    private fun fadeMs(): Long = t(GameConfig.PRESS_FADE_MS).coerceAtLeast(GameConfig.MIN_LIGHT_MS)

    private fun startGame() {
        val f = game.start()
        strip.setCurrent(f)
        turnCard.set("${players.size}명 · 릴레이", players[f].name, playerColor(f), "${players[f].name}부터 시작!")
        banner(module.name, "${players[f].name}부터 · 색 하나를 눌러 시작", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { beginTurn() }
    }

    private fun head(): String = "${game.turns}번째 차례 · ${game.aliveCount}명 남음"

    /** 지금 차례 열기: 시간 막대 5초 시작 */
    private fun beginTurn() {
        val p = game.current
        game.beginTurn(timers.now())
        strip.setCurrent(p)
        board.clearLights()
        board.setError(-1)
        board.setLength(game.length, false, 0)
        board.setProgress(0, game.length)
        if (game.phase == Phase.ADD) {
            board.setAdding(true)
            turnCard.set(head(), players[p].name, playerColor(p), "+1 첫 색을 누르세요!", strong = true)
        } else {
            board.setAdding(false)
            turnCard.set(head(), players[p].name, playerColor(p), "처음부터 순서대로 ${game.length}개\n다 누르면 +1 새 색")
        }
        beeper.turn()
        busy = false
        startClock()
    }

    /** 제한 시간 다시 시작 (누를 때마다) */
    private fun startClock() {
        timerBar.start(game.limitMs)
        armTimeout()
    }

    private fun armTimeout() {
        timeoutTask?.cancel()
        timeoutTask = timers.after(game.remainingMs(timers.now()).coerceAtLeast(1)) { onTimeCheck() }
    }

    private fun stopClock() {
        timeoutTask?.cancel()
        timeoutTask = null
        timerBar.stop()
    }

    private fun onTimeCheck() {
        timeoutTask = null
        if (busy || !game.accepting) return
        val r = game.checkTimeout(timers.now())
        if (r is Press.Out) out(r) else armTimeout()
    }

    override fun onPad(index: Int) {
        if (busy || gamePaused || !game.accepting) return
        when (val r = game.press(index, timers.now())) {
            is Press.Correct -> {
                board.light(index, lightMs(), fadeMs())
                tones.play(index, beeper.enabled)
                board.setProgress(r.progress, r.length)
                if (r.needAdd) {
                    board.setAdding(true)
                    turnCard.setHint("+1 새 색을 누르세요!", strong = true)
                }
                startClock()
            }
            is Press.Added -> added(r)
            is Press.Out -> out(r)
            Press.Ignored -> Unit
        }
    }

    private fun added(r: Press.Added) {
        stopClock()
        busy = true
        val p = r.player
        board.light(r.color, lightMs(), fadeMs())
        tones.play(r.color, beeper.enabled)
        board.setAdding(false)
        board.setProgress(r.length, r.length)
        board.setLength(r.length, true, t(GameConfig.POP_MS))
        board.hubCenter(pa)
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 8, t(700))
        strip.setValue(p, "최고 ${game.best[p]}")
        strip.pulse(p)
        turnCard.set("순서 길이 ${r.length}", players[p].name, playerColor(p), "${GameConfig.COLOR_NAMES[r.color]} 추가!\n다음: ${players[r.next].name}")
        timers.after(t(GameConfig.ADD_NEXT_MS)) { beginTurn() }
    }

    private fun out(r: Press.Out) {
        stopClock()
        busy = true
        val p = r.player
        if (r.pressed >= 0) {
            board.light(r.pressed, lightMs(), fadeMs())
            board.setError(r.pressed)
        }
        if (r.expected >= 0) board.flash(r.expected, t(GameConfig.FLASH_MS), GameConfig.FLASH_TIMES)
        board.setAdding(false)
        shakeScreen(Theme.dpf(this, 10f), t(GameConfig.SHAKE_MS))
        strip.setOut(p, true)
        strip.setValue(p, "${game.finalRanks()[p]}위")
        val sub = when {
            r.reason == OutReason.WRONG -> "정답은 ${GameConfig.COLOR_NAMES[r.expected]}이었어요"
            r.expected >= 0 -> "5초 동안 멈춤 · 다음은 ${GameConfig.COLOR_NAMES[r.expected]}이었어요"
            else -> "5초 안에 새 색을 누르지 못했어요"
        }
        val why = if (r.reason == OutReason.WRONG) "틀렸어요!" else "시간 초과!"
        val next = if (r.gameOver) "" else "\n다음: ${players[r.next].name} · 같은 순서로"
        turnCard.set("탈락", players[p].name, playerColor(p), why + next)
        banner("${players[p].name} 탈락!", sub, BannerTone.BAD, GameConfig.OUT_SHOW_MS)
        timers.after(t(GameConfig.OUT_SHOW_MS)) { if (r.gameOver) finishGame() else beginTurn() }
    }

    private fun finishGame() {
        val w = game.winner
        board.setError(-1)
        timerBar.visibility = android.view.View.INVISIBLE
        strip.setCurrent(w)
        strip.setValue(w, "1위")
        strip.pulse(w)
        turnCard.set("최후의 1인", players[w].name, playerColor(w), "1위! 순서 길이 ${game.length}", strong = true)
        banner("${players[w].name} 승리!", "최후의 1인 · 순서 길이 ${game.length}", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        board.pauseAnims()
        tones.stopAll()
    }

    override fun onGameResume() {
        board.resumeAnims()
    }

    override fun onGameDestroy() {
        tones.release()
    }

    companion object {
        /** 테스트용 난수 (첫 차례, null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
