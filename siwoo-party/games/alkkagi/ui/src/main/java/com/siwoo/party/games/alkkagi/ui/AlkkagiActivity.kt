package com.siwoo.party.games.alkkagi.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.alkkagi.AlkkagiGame
import com.siwoo.party.games.alkkagi.AlkkagiModule
import com.siwoo.party.games.alkkagi.Flick
import com.siwoo.party.games.alkkagi.GameConfig
import com.siwoo.party.games.alkkagi.Phase
import com.siwoo.party.games.alkkagi.Stone
import kotlin.random.Random

/**
 * 알까기 화면 (2인, 차례대로).
 * 왼쪽 패널(1번) | 나무 바둑판(BoardView) | 오른쪽 패널(2번). 1번 알은 왼쪽, 2번 알은 오른쪽에 놓인다.
 * 흐름: 소개 → (무작위로 먼저 할 사람) 차례 → 내 알을 끌었다 놓아 튕김 → 모두 멈추면 다음 차례 → 한쪽 알이 다 떨어지면 끝.
 * 물리는 AlkkagiGame(고정 1/60초 단계). 여기 프레임 루프(postOnAnimation)는 알이 움직이는 동안에만 돌고,
 * 모두 멈추거나 일시정지하면 멈춘다 (계속하기 → 움직이던 중이면 다시 돈다).
 */
class AlkkagiActivity : PartyGameActivity(), BoardView.Listener, AlkkagiGame.Listener {

    override val module = AlkkagiModule

    lateinit var game: AlkkagiGame
        private set
    lateinit var board: BoardView
        private set
    lateinit var sides: Array<SideView>
        private set

    /** 소개가 끝나 첫 차례가 시작됐는지 */
    private var started = false
    private var ended = false

    /** 프레임 루프 (알이 움직이는 동안에만) */
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 그린 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastCollideSound = -10_000L
    private val pa = PointF()

    /** 지금 알을 튕길 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && game.phase == Phase.AIM

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = AlkkagiGame(random.nextInt(GameConfig.PLAYERS), t(GameConfig.FALL_MS))
        game.listener = this

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        sides = Array(GameConfig.PLAYERS) { SideView(this) }
        board = BoardView(this)
        board.bind(game, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        board.listener = this
        // 판 밖으로 떨어지는 알은 패널 위로 지나가며 작아지게 (판을 맨 위에, 잘리지 않게)
        row.clipChildren = false
        row.clipToPadding = false
        board.z = 1f
        row.addView(sides[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(sides[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) {
            sides[i].setPlayer(players[i].name, playerColor(i))
            sides[i].setCount(game.remaining(i))
        }
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner(players[0].name + " vs " + players[1].name, "내 알을 뒤로 끌었다 놓아 상대 알을 판 밖으로!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn()
        }
    }

    /** 차례 표시: 패널 금빛 + 판 테두리 색 + 짧은 배너 */
    private fun showTurn() {
        val p = game.current
        for (i in sides.indices) sides[i].setCurrent(i == p)
        sides[p].setHint(HINT_AIM)
        board.invalidate()
        banner(players[p].name + " 차례", "", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
    }

    override fun canAim(): Boolean = acceptingInput

    override fun onFlick(id: Int, dragX: Float, dragY: Float) {
        if (!acceptingInput) return
        when (game.flick(id, dragX, dragY)) {
            Flick.SHOT -> {
                beeper.dice()
                sides[game.current].setHint(HINT_WAIT)
                ensureLoop()
            }
            Flick.TOO_SHORT -> {
                sides[game.current].setHint(HINT_SHORT)
                beeper.step()
            }
            Flick.NOT_YOURS, Flick.NOT_NOW -> Unit
        }
    }

    // ── 게임 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onCollide(x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.COLLIDE_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCollideSound < GameConfig.COLLIDE_SOUND_GAP_MS) return
        lastCollideSound = now
        beeper.step()
        board.toScreen(x, y, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
    }

    override fun onFall(stone: Stone, byShooter: Boolean) {
        val owner = stone.owner
        sides[owner].setCount(game.remaining(owner))
        Ui.shake(sides[owner])
        board.toScreen(stone.x, stone.y, pa)
        toFx(board, pa)
        if (byShooter) {
            outBanner("아이고!", players[owner].name + "의 알이 떨어졌어요", BannerTone.BAD, stone.y)
        } else {
            fx.stars(pa.x, pa.y, 8, t(GameConfig.OUT_BANNER_MS))
            outBanner("퇴장!", players[owner].name + "의 알이 판 밖으로", BannerTone.GOOD, stone.y)
        }
    }

    override fun onTurnEnd(shooter: Int, knocked: Int, lost: Int, next: Int) {
        sides[shooter].setHint("")
        showTurn()
    }

    override fun onGameOver(winner: Int) {
        ended = true
        val loser = 1 - winner
        for (i in sides.indices) sides[i].setCurrent(false)
        sides[winner].setWinner(true)
        board.invalidate()
        // 두 마지막 알이 한꺼번에 떨어진 경우 = 튕긴(진) 사람의 마지막 알도 떨어짐
        val sub = if (game.remaining(winner) == 0) "둘 다 떨어졌지만 튕긴 " + players[loser].name + " 패배"
        else players[loser].name + "의 알이 모두 떨어졌어요"
        timers.after(t(GameConfig.END_DELAY_MS)) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
            banner(players[winner].name + " 승리!", sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    /** 떨어진 자리 반대쪽(위/아래)에 띄우는 작은 배너 — 떨어지는 알·움직이는 알이 가려지지 않게 */
    private fun outBanner(text: String, sub: String, tone: BannerTone, fallY: Float) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (w <= 0f) return
        val y = if (fallY < GameConfig.BOARD_H / 2f) h * 0.74f else h * 0.26f
        fx.banner(w / 2f, y, w * 0.5f, text, sub, tone, t(GameConfig.OUT_BANNER_MS))
        if (tone == BannerTone.GOOD) beeper.good() else beeper.bad()
    }

    // ── 프레임 루프 (알이 움직일 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.MOVING) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        board.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::board.isInitialized) board.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val moving = game.advance(dt)
        board.invalidate()
        if (moving && loopOn) board.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        if (::board.isInitialized) board.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        board.release()
    }

    companion object {
        /** 테스트용 난수 (먼저 할 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 100f
        /** 한 프레임 최대 시간 (그 이상 멈칫하면 잘라서 물리가 튀지 않게) */
        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 320L
        private const val HINT_AIM = "내 알을 끌었다 놓기"
        private const val HINT_SHORT = "더 길게 끌어요!"
        private const val HINT_WAIT = "굴러가는 중…"
    }
}
