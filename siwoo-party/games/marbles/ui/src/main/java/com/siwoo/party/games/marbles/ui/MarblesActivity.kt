package com.siwoo.party.games.marbles.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.marbles.Fire
import com.siwoo.party.games.marbles.GameConfig
import com.siwoo.party.games.marbles.Marble
import com.siwoo.party.games.marbles.MarblesGame
import com.siwoo.party.games.marbles.MarblesModule
import com.siwoo.party.games.marbles.Phase
import com.siwoo.party.games.marbles.ShotEnd
import kotlin.random.Random

/**
 * 구슬치기 화면 (2~4인, 차례대로).
 * 왼쪽: 흙 마당(FieldView), 오른쪽: "지금 차례" 판(TurnPanel) + 플레이어 패널(PlayerStrip, 모은 구슬 수).
 * 흐름: 소개 → 차례 → 링 밖에 내 구슬 놓기 → 내 구슬을 뒤로 끌었다 놓아 쏘기 → 모두 멈추면 판정
 *       → 획득한 구슬이 내 패널로 날아감 → 하나라도 얻으면 "한 번 더!", 아니면 다음 사람 → 구슬이 다 없어지면(또는 최대 바퀴) 끝.
 * 물리는 MarblesGame(고정 1/60초 단계). 프레임 루프(postOnAnimation)는 구슬이 구르는 동안에만 돌고,
 * 모두 멈추거나 일시정지하면 멈춘다 (계속하기 → 구르던 중이면 다시 돈다).
 */
class MarblesActivity : PartyGameActivity(), FieldView.Listener, MarblesGame.Listener {

    override val module = MarblesModule

    lateinit var game: MarblesGame
        private set
    lateinit var field: FieldView
        private set
    lateinit var panel: TurnPanel
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var fly: FlyView
        private set
    private lateinit var skins: MarbleSkins

    /** 소개가 끝나 첫 차례가 시작됐는지 */
    private var started = false
    private var ended = false
    /** 판정 연출 중 (터치 막음) */
    private var busy = false

    /** 프레임 루프 (구슬이 구르는 동안에만) */
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastCollideSound = -10_000L
    private val pa = PointF()
    private val pb = PointF()
    private val loc = IntArray(2)
    private val loc2 = IntArray(2)

    /** 마지막으로 띄운 큰 배너 글자 (테스트 확인용) */
    var lastBanner = ""
        private set
    /** "한 번 더!" 배너를 띄운 횟수 (테스트 확인용) */
    var againCount = 0
        private set

    /** 지금 놓거나 쏠 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !busy && !gamePaused && game.canAct

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = MarblesGame(players.size, random.nextInt(players.size), random.nextInt())
        game.listener = this
        skins = MarbleSkins(IntArray(players.size) { playerColor(it) })

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        field = FieldView(this, skins)
        field.bind(game)
        field.listener = this
        row.addView(field, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        panel = TurnPanel(this)
        side.addView(panel, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.NONE)
        val tileH = if (players.size >= 4) 54f else 60f
        val stripH = Theme.dp(this, tileH * players.size + 6f * (players.size - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@MarblesActivity, 8f) })
        row.addView(side, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH).apply { leftMargin = Theme.dp(this@MarblesActivity, 6f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 날아가는 구슬 층 (맨 위, 터치 안 받음)
        fly = FlyView(this, skins)
        stage.addView(fly, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, COUNTS[0])
        val p = game.current
        strip.setCurrent(p)
        field.setTurnColor(playerColor(p))
        panel.setPlayer(players[p].name, playerColor(p), animate = false)
        panel.setRemaining(game.remaining)
        panel.setRound(game.round)
        panel.setHint(HINT_WAIT_START)
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        say("구슬치기!", "링 밖으로 쳐낸 구슬은 내 것!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn(again = false)
        }
    }

    /** 차례 시작: 패널 금빛·마당 테두리 색·차례 판. again = 한 번 더 (배너는 이미 띄움) */
    private fun startTurn(again: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        field.setTurnColor(playerColor(p))
        panel.setPlayer(players[p].name, playerColor(p), animate = !again)
        panel.setRemaining(game.remaining)
        panel.setRound(game.round)
        panel.setHint(HINT_PLACE)
        busy = false
        field.invalidate()
        if (!again) {
            say(players[p].name + " 차례", "링 밖에 내 구슬을 놓고 쏘세요", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
            beeper.turn()
        }
    }

    override fun canTouch(): Boolean = acceptingInput

    override fun onPlace(ux: Float, uy: Float, first: Boolean) {
        if (!acceptingInput) return
        val had = game.shooterPlaced
        if (!game.place(ux, uy)) return
        if (first && !had) beeper.step()
        panel.setHint(HINT_AIM)
        field.invalidate()
    }

    override fun onShoot(dirX: Float, dirY: Float, power: Float) {
        if (!acceptingInput) return
        when (game.shoot(dirX, dirY, power)) {
            Fire.SHOT -> {
                beeper.dice()
                panel.setHint(HINT_ROLL)
                ensureLoop()
            }
            Fire.TOO_WEAK -> panel.setHint(HINT_SHORT)
            Fire.NOT_PLACED, Fire.NOT_NOW -> Unit
        }
    }

    override fun onAimCancel() {
        if (acceptingInput && game.shooterPlaced) panel.setHint(HINT_SHORT)
    }

    // ── 게임 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onCollide(x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.COLLIDE_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCollideSound < GameConfig.COLLIDE_SOUND_GAP_MS) return
        lastCollideSound = now
        beeper.step()
        fieldToStage(x, y, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
    }

    override fun onLeaveField(m: Marble) {
        if (m.isShooter) return
        fieldToStage(m.x, m.y, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD, t(RIPPLE_MS))
    }

    override fun onShotEnd(r: ShotEnd) {
        busy = true
        field.invalidate()
        timers.after(t(GameConfig.SETTLE_PAUSE_MS)) { showResult(r) }
    }

    /** 판정 연출: 획득한 구슬 → 내 패널로 날아감, 내 구슬 → 주인에게 돌아감 → 한 번 더 / 다음 사람 / 끝 */
    private fun showResult(r: ShotEnd) {
        val p = r.shooter
        val flight = t(GameConfig.FLY_MS)
        val stagger = t(GameConfig.FLY_STAGGER_MS)
        tileCenter(p, pb)
        val rt = GameConfig.TARGET_R * field.unitPx
        // 내 구슬 (작게, 먼저 돌아감)
        fieldToStage(r.shooterX, r.shooterY, pa)
        fly.fly(pa.x, pa.y, pb.x, pb.y, rt * MarbleArt.SHOOTER_SCALE * 0.8f, -1, p, 0L, flight)
        val n = r.captured.size
        for ((k, id) in r.captured.withIndex()) {
            val m = game.targets[id]
            fieldToStage(m.x, m.y, pa)
            fly.fly(pa.x, pa.y, pb.x, pb.y, rt, m.color, -1, stagger * (k + 1), flight)
        }
        field.invalidate()
        if (n > 0) {
            beeper.good()
            timers.after(flight + stagger * n) {
                strip.setValue(p, COUNTS[game.captured[p].coerceAtMost(GameConfig.TARGETS)])
                strip.pulse(p)
                panel.setRemaining(game.remaining)
                tileCenter(p, pb)
                fx.stars(pb.x, pb.y, 6, t(STARS_MS))
                if (r.gameOver) {
                    finishGame()
                } else {
                    againCount++
                    say(AGAIN, players[p].name + " 구슬 " + n + "개 획득", BannerTone.GOOD, GameConfig.AGAIN_BANNER_MS)
                    startTurn(again = true)
                }
            }
        } else {
            beeper.bad()
            panel.setHint(if (r.shooterInRing) HINT_MISS_IN else HINT_MISS)
            timers.after(t(GameConfig.MISS_DELAY_MS)) {
                if (r.gameOver) finishGame() else startTurn(again = false)
            }
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val best = game.captured[winners[0]]
        val title = when {
            best == 0 -> "무승부!"
            winners.size == 1 -> players[winners[0]].name + " 승리!"
            else -> "공동 1등!"
        }
        val sub = when {
            game.remaining > 0 -> GameConfig.MAX_ROUNDS.toString() + "바퀴 끝 · 가장 많이 모은 사람이 이겨요"
            winners.size == 1 -> "구슬 " + best + "개를 모았어요"
            else -> winners.joinToString(" · ") { players[it].name } + " (" + best + "개)"
        }
        strip.setCurrent(-1)
        if (best > 0) {
            val w = winners[0]
            panel.setPlayer(players[w].name, playerColor(w), animate = true)
            panel.setHint(if (winners.size == 1) HINT_WIN else HINT_TIE)
        } else {
            panel.setHint(HINT_END)
        }
        field.invalidate()
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        say(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    /** 큰 배너 (마지막 글자를 기억 — 테스트 확인용) */
    private fun say(text: String, sub: String, tone: BannerTone, ms: Long) {
        lastBanner = text
        banner(text, sub, tone, ms)
    }

    // ── 좌표 ──

    /** 마당 단위 (x, y) → 게임 화면(stage) 좌표. 마당 밖이면 마당 가장자리로 */
    private fun fieldToStage(x: Float, y: Float, out: PointF) {
        field.toScreen(x.coerceIn(0f, GameConfig.FIELD_W), y.coerceIn(0f, GameConfig.FIELD_H), out)
        viewToStage(field, out)
    }

    /** i 번 플레이어 패널 가운데 → stage 좌표 */
    private fun tileCenter(i: Int, out: PointF) {
        val tile = strip.tiles[i]
        out.set(tile.width * 0.22f, tile.height / 2f)
        viewToStage(tile, out)
    }

    private fun viewToStage(v: View, p: PointF) {
        v.getLocationInWindow(loc)
        fly.getLocationInWindow(loc2)
        p.offset((loc[0] - loc2[0]).toFloat(), (loc[1] - loc2[1]).toFloat())
    }

    // ── 프레임 루프 (구슬이 구를 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.MOVING) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        field.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::field.isInitialized) field.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, GameConfig.MAX_FRAME_MS)
        lastFrame = now
        frames++
        val moving = game.advance(dt)
        field.invalidate()
        if (moving && loopOn) field.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        if (::field.isInitialized) field.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
        if (::field.isInitialized) field.invalidate()
    }

    override fun onGameDestroy() {
        stopLoop()
        if (::field.isInitialized) field.release()
        if (::fly.isInitialized) fly.clear()
    }

    companion object {
        /** 테스트용 난수 (먼저 할 사람·배치 시드, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 172f
        private const val RIPPLE_MS = 320L
        private const val STARS_MS = 700L
        const val AGAIN = "한 번 더!"
        private val COUNTS = Array(GameConfig.TARGETS + 1) { it.toString() + "개" }
        private const val HINT_WAIT_START = "곧 시작해요"
        const val HINT_PLACE = "링 밖 아무 곳에 내 구슬 놓기"
        private const val HINT_AIM = "내 구슬을 뒤로 끌었다 놓기"
        private const val HINT_SHORT = "더 길게 끌어요!"
        private const val HINT_ROLL = "굴러가는 중…"
        private const val HINT_MISS = "아깝다! 다음 사람"
        private const val HINT_MISS_IN = "링 안에 멈춤 · 다음 사람"
        private const val HINT_END = "끝!"
        private const val HINT_WIN = "우승! 구슬을 가장 많이 모았어요"
        private const val HINT_TIE = "공동 1등!"
    }
}
