package com.siwoo.party.games.reaction.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.reaction.GameConfig
import com.siwoo.party.games.reaction.ReactionGame
import com.siwoo.party.games.reaction.ReactionModule
import com.siwoo.party.games.reaction.Touch
import kotlin.random.Random

/**
 * 반응속도 결투 화면 (동시 터치).
 * 각자 구역(공통 MultiTouchZoneView) + 가운데 신호 원. 라운드 흐름은 ReactionGame 이 판정하고
 * 여기서는 대기·속임수·"지금!" 시각을 timers 로 예약하고 결과를 연출한다.
 */
class ReactionActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = ReactionModule

    lateinit var game: ReactionGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    private lateinit var signal: SignalView
    /** 이번 라운드에 예약한 일 (무효·일시정지 때 한꺼번에 취소) */
    private val roundTasks = ArrayList<GameTimers.Task>()
    private var goUptime = 0L
    /** 라운드 진행 중 일시정지되면 돌아와서 그 라운드를 다시 한다 (공정하게) */
    private var restartOnResume = false
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = ReactionGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players, "대기")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        signal = SignalView(this)
        val size = Theme.dp(this, 150f)
        stage.addView(signal, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        signal.setRound(roundLabel())
        stage.post { introRound() }
    }

    private fun roundLabel() = "${game.roundNumber} / ${GameConfig.REACTION_ROUNDS}"

    private fun schedule(ms: Long, block: () -> Unit) {
        roundTasks.add(timers.after(ms, block))
    }

    private fun cancelRound() {
        for (t in roundTasks) t.cancel()
        roundTasks.clear()
    }

    /** "라운드 N" 배너 → 라운드 시작 */
    private fun introRound() {
        cancelRound()
        zones.clearMarks()
        zones.setHint("대기")
        signal.setRound(roundLabel())
        signal.show(SignalView.State.WAIT, "준비")
        banner("라운드 ${game.roundNumber}", "자기 구역에 손가락을 올려 두세요", ms = GameConfig.ROUND_INTRO_MS)
        schedule(t(GameConfig.ROUND_INTRO_MS)) { startRound() }
    }

    private fun startRound() {
        cancelRound()
        zones.clearMarks()
        val plan = game.startRound()
        signal.show(SignalView.State.WAIT, "기다리세요...")
        zones.touchEnabled = true
        // 대기·속임수 시간은 게임 규칙이라 속도 배율을 곱하지 않는다
        if (plan.hasFake) {
            schedule(plan.fakeAtMs) {
                game.showFake()
                signal.show(SignalView.State.FAKE, "속임수!")
                beeper.bad()
            }
            schedule(plan.fakeAtMs + GameConfig.FAKE_SHOW_MS) {
                game.endFake()
                signal.show(SignalView.State.WAIT, "기다리세요...")
            }
        }
        schedule(plan.goAtMs) { go() }
    }

    private fun go() {
        game.go()
        goUptime = SystemClock.uptimeMillis()
        signal.show(SignalView.State.GO, "지금!")
        beeper.good()
        schedule(GameConfig.ROUND_TIMEOUT_MS) { finishRound() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        when (val r = game.touch(zone, timeMs - goUptime)) {
            is Touch.Disqualified -> {
                zones.setCrossed(zone, true)
                zones.setSub(zone, "실격")
                beeper.bad()
                if (r.allOut) voidRound("전원 실격!")
            }
            is Touch.Won -> {
                zones.setWinner(zone, true)
                zones.setSub(zone, "${r.ms}ms")
                zones.setStars(zone, game.wins[zone])
                signal.show(SignalView.State.WIN, players[zone].name)
                zone(zone)
                fx.stars(pa.x, pa.y, 10, t(900))
                beeper.good()
                cancelRound()
                if (game.allResponded()) schedule(t(400)) { finishRound() } else schedule(GameConfig.LATE_WINDOW_MS) { finishRound() }
            }
            is Touch.Late -> {
                zones.setSub(zone, "${r.ms}ms")
                if (game.allResponded()) { cancelRound(); schedule(t(400)) { finishRound() } }
            }
            Touch.Ignored -> Unit
        }
    }

    /** zone 가운데 → pa (연출 층 좌표) */
    private fun zone(i: Int) {
        val z = zones.zone(i) ?: return
        pa.set(z.centerX, z.centerY)
        toFx(zones, pa)
    }

    private fun voidRound(title: String) {
        cancelRound()
        zones.touchEnabled = false
        signal.show(SignalView.State.VOID, "무효")
        banner(title, "이번 라운드는 다시 합니다", BannerTone.BAD, GameConfig.VOID_SHOW_MS)
        schedule(t(GameConfig.VOID_SHOW_MS)) { startRound() }
    }

    private fun finishRound() {
        cancelRound()
        if (!game.endRound()) {
            voidRound("아무도 안 눌렀어요")
            return
        }
        zones.touchEnabled = false
        if (game.gameOver) {
            banner("결투 끝!", "결과를 발표합니다", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            schedule(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
        } else {
            schedule(t(GameConfig.RESULT_SHOW_MS)) { introRound() }
        }
    }

    override fun onGamePause() {
        // 대기·"지금!" 중에 멈추면 돌아와서 그 라운드를 다시 (손가락 위치·시간이 불공정해지므로)
        if (game.phase == com.siwoo.party.games.reaction.Phase.WAITING ||
            game.phase == com.siwoo.party.games.reaction.Phase.FAKE ||
            game.phase == com.siwoo.party.games.reaction.Phase.GO && game.roundWinner < 0
        ) {
            restartOnResume = true
            zones.touchEnabled = false
        }
    }

    override fun onGameResume() {
        if (restartOnResume) {
            restartOnResume = false
            cancelRound()
            introRound()
        }
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
