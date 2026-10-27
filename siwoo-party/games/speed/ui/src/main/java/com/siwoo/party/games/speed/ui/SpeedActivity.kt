package com.siwoo.party.games.speed.ui

import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.speed.AgainResult
import com.siwoo.party.games.speed.FlipSource
import com.siwoo.party.games.speed.GameConfig
import com.siwoo.party.games.speed.PlayResult
import com.siwoo.party.games.speed.SpeedGame
import com.siwoo.party.games.speed.SpeedModule
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 스피드 카드 화면 (2인 동시 진행, 폰을 가운데 두고 긴 변 양쪽에 마주 앉음).
 * 흐름: 안내 배너 + 칸 나눠 주기 → 3·2·1 → 가운데 2장 뒤집기 → 두 사람이 동시에 끌어 놓기
 *      → (둘 다 막히면 [다시] 둘 다 눌러 새 카드) → 먼저 다 낸 사람 승리 → 순위.
 * 판정은 SpeedGame, 그림·터치는 SpeedTableView (구역마다 손가락 하나씩 따로 추적).
 */
class SpeedActivity : PartyGameActivity(), SpeedTableView.Listener {

    override val module = SpeedModule

    lateinit var game: SpeedGame
        private set
    lateinit var table: SpeedTableView
        private set
    private lateinit var bitmaps: CardBitmaps

    /** 진행 중 (카드를 끌 수 있음) */
    var playing = false
        private set
    private var stuckTask: GameTimers.Task? = null
    /** 막힘 알림을 이미 띄웠는지 (소리 한 번만) */
    private var stuckShown = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = gameFactory?.invoke(random) ?: SpeedGame.deal(random)

        // 카드 그림은 가운데 더미 크기(가장 큰 크기)로 한 번 그려 두고 줄어들여 쓴다
        val dm = resources.displayMetrics
        val shortSide = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val px = (shortSide * GameConfig.CARD_H_FRAC * GameConfig.CENTER_SCALE / CardBitmaps.RATIO).roundToInt().coerceAtLeast(16)
        bitmaps = CardBitmaps(this, px)
        bitmaps.prepareAll()

        table = SpeedTableView(this, bitmaps)
        table.clock = SpeedTableView.Clock { timers.now() }
        table.speed = t(1000) / 1000f
        table.setup(game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        table.listener = this
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    // ── 시작 ──

    private fun intro() {
        banner("스피드!", "가운데보다 1 크거나 작은 카드를 끌어다 놓기", ms = GameConfig.INTRO_MS)
        table.dealAnimation()
        timers.after(t(GameConfig.INTRO_MS)) { countdown { start() } }
    }

    private fun start() {
        table.revealed = true
        for (p in 0 until GameConfig.PLAYERS) table.flipToCenter(p, p, true, 0L)
        playing = true
        scheduleStuckCheck(GameConfig.FLIP_MS)
    }

    // ── 끌어 놓기 ──

    override fun canDrag(p: Int): Boolean = playing && !gamePaused

    override fun onDrop(p: Int, i: Int, k: Int): Boolean {
        if (!playing || gamePaused) return false
        return when (game.play(p, i, k)) {
            PlayResult.PLAYED -> {
                beeper.step()
                scheduleStuckCheck(GameConfig.REFILL_MS)
                true
            }
            PlayResult.WON -> {
                beeper.good()
                win(p)
                true
            }
            PlayResult.NOT_ADJACENT -> {
                beeper.bad()
                false
            }
            else -> false
        }
    }

    // ── 막힘 · [다시] ──

    /** 카드가 내려앉은 뒤 둘 다 막혔는지 확인 (막힘은 [다시] 전엔 풀리지 않으므로 늦게 봐도 된다) */
    private fun scheduleStuckCheck(animMs: Long) {
        stuckTask?.cancel()
        stuckTask = timers.after(t(animMs + GameConfig.STUCK_NOTICE_MS)) { updateStuck() }
    }

    private fun updateStuck() {
        if (!playing) return
        if (game.stuck) {
            for (p in 0 until GameConfig.PLAYERS) {
                val ready = game.isReady(p)
                table.setAgain(p, if (ready) SpeedTableView.AGAIN_WAIT else SpeedTableView.AGAIN_LIT)
                table.setMessage(p, if (ready) MSG_WAIT else MSG_STUCK)
            }
            if (!stuckShown) beeper.turn()
            stuckShown = true
        } else {
            clearStuck()
        }
    }

    private fun clearStuck() {
        stuckShown = false
        for (p in 0 until GameConfig.PLAYERS) {
            table.setAgain(p, SpeedTableView.AGAIN_OFF)
            table.setMessage(p, "")
        }
    }

    override fun onAgain(p: Int) {
        if (!playing || gamePaused) return
        when (game.pressAgain(p)) {
            AgainResult.WAITING -> {
                beeper.step()
                table.setAgain(p, SpeedTableView.AGAIN_WAIT)
                table.setMessage(p, MSG_WAIT)
            }
            AgainResult.FLIPPED -> {
                beeper.good()
                clearStuck()
                for (q in 0 until GameConfig.PLAYERS) {
                    if (game.lastFlip[q] == FlipSource.NONE) continue
                    table.flipToCenter(q, q, game.lastFlip[q] != FlipSource.RESERVE, t(60L * q))
                }
                scheduleStuckCheck(GameConfig.FLIP_MS)
            }
            else -> {}
        }
    }

    /** 테스트·도움용: [다시] 를 직접 누름 */
    fun pressAgain(p: Int) = onAgain(p)

    // ── 끝 ──

    private fun win(p: Int) {
        playing = false
        stuckTask?.cancel()
        clearStuck()
        table.input.enabled = false
        table.cancelDrags()
        table.winner = p
        val loser = 1 - p
        banner("${players[p].name} 승리!", "${players[loser].name} ${game.cardsLeft(loser)}장 남음", BannerTone.GOOD, GameConfig.END_BANNER_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.END_BANNER_MS))
        timers.after(t(GameConfig.END_BANNER_MS + 200)) { finishWithRanks(game.ranks()) }
    }

    // ── 일시정지 ──

    override fun onGamePause() {
        if (!::table.isInitialized) return
        table.cancelDrags()
        table.stopLoop()
    }

    override fun onGameResume() {
        if (::table.isInitialized && table.isAnimating) table.ensureLoop()
    }

    override fun onGameDestroy() {
        if (::table.isInitialized) table.stopLoop()
        if (::bitmaps.isInitialized) bitmaps.release()
    }

    companion object {
        private const val MSG_STUCK = "둘 다 낼 카드가 없어요! [다시]를 누르세요"
        private const val MSG_WAIT = "상대가 [다시]를 누르길 기다리는 중…"

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트·스크린샷용 판 (null 이면 섞어서 나눔) */
        var gameFactory: ((Random) -> SpeedGame)? = null
    }
}
