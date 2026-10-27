package com.siwoo.party.games.minigolf.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.minigolf.Course
import com.siwoo.party.games.minigolf.GameConfig
import com.siwoo.party.games.minigolf.MinigolfGame
import com.siwoo.party.games.minigolf.MinigolfModule
import com.siwoo.party.games.minigolf.Phase
import com.siwoo.party.games.minigolf.TurnResult

/**
 * 미니 골프 화면 (2~4인, 차례대로, 9홀).
 * 왼쪽: 사람 패널(합계 타수) + [점수표] | 오른쪽: 홀 머리 줄(홀·파·지금 사람·타수 구슬) + 코스(CourseView).
 * 흐름: 소개 → "1번 홀" → 첫 사람 차례 → 끌었다 놓아 퍼팅 → 공이 멈추면 다시 (넣거나 6타까지) → 다음 사람 … →
 * 모두 끝나면 점수표 → [다음 홀] … → 9홀 뒤 최종 점수표 → 타수 합이 적은 순으로 순위.
 * 물리는 MinigolfGame(고정 1/60초 단계). 여기 프레임 루프(postOnAnimation)는 공이 구르거나 컵에 빠지는 동안에만 돌고,
 * 멈추거나 일시정지하면 멈춘다 (계속하기 → 구르던 중이면 다시 돈다).
 */
class MinigolfActivity : PartyGameActivity(), CourseView.Listener, MinigolfGame.Listener {

    override val module = MinigolfModule

    lateinit var game: MinigolfGame
        private set
    lateinit var course: CourseView
        private set
    lateinit var bar: HoleBarView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var celebrate: CelebrateView
        private set
    private lateinit var cardButton: GameButton

    private var started = false
    private var ended = false
    /** 조준을 받는 중 (차례 배너가 끝난 뒤 ~ 칠 때까지) */
    private var aiming = false
    /** 홀 사이 점수표 */
    var scorecard: PopupLayer.Popup? = null
        private set
    /** [점수표] 버튼으로 연 점수표 (밖을 눌러 닫음) */
    private var peek: PopupLayer.Popup? = null
    /** 홀 사이 점수표의 [다음 홀] 버튼 (테스트용) */
    var nextButton: GameButton? = null
        private set

    // ── 프레임 루프 ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastWallSound = -10_000L
    private val pa = PointF()

    /** 지금 끌어서 칠 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && aiming && game.phase == Phase.AIM

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val limit = holeLimit
        val holes = if (limit in 1 until Course.HOLES.size) Course.HOLES.take(limit) else Course.HOLES
        game = MinigolfGame(players.size, holes)
        game.listener = this

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)

        // 왼쪽: 사람 패널 + 점수표 버튼
        val left = Theme.vertical(this)
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.NONE)
        left.addView(strip, Theme.lp(Theme.MATCH, 0, 1f))
        cardButton = Ui.button(this, CARD_TEXT, Theme.BLUE, 14f) { showCard(final = false) }
        left.addView(cardButton, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)).apply { topMargin = Theme.dp(this@MinigolfActivity, 6f) })
        row.addView(left, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))

        // 오른쪽: 머리 줄 + 코스
        val right = Theme.vertical(this)
        bar = HoleBarView(this)
        right.addView(bar, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)))
        course = CourseView(this)
        course.listener = this
        val frameBox = FrameLayout(this)
        frameBox.background = Theme.card(this, GolfArt.ROUGH, 14f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 3f)
        frameBox.setPadding(inset, inset, inset, inset)
        frameBox.clipToOutline = true
        frameBox.addView(course, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        right.addView(frameBox, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@MinigolfActivity, 6f) })
        row.addView(right, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@MinigolfActivity, 8f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        celebrate = CelebrateView(this)
        stage.addView(celebrate, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        refreshTotals()
        course.showHole(game)
        showHoleInfo()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val n = game.holes.size
        banner(module.name, n.toString() + "홀 · 타수 합이 가장 적은 사람이 우승!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            beginHole()
        }
    }

    private fun showHoleInfo() {
        val h = game.hole
        bar.setHole(h.number, h.name, h.par, game.holes.size)
        bar.setStrokes(game.strokes, game.penalties)
        val p = game.current
        bar.setPlayer(players[p].name, playerColor(p))
        course.ballColor = playerColor(p)
    }

    /** 새 홀: 코스 그림 → "3번 홀" 배너 → 첫 사람 */
    private fun beginHole() {
        course.showHole(game)
        showHoleInfo()
        strip.setCurrent(game.current)
        val h = game.hole
        banner(h.number.toString() + "번 홀", h.name + " · 파 " + h.par, BannerTone.INFO, GameConfig.HOLE_BANNER_MS)
        timers.after(t(GameConfig.HOLE_BANNER_MS)) { beginTurn() }
    }

    /** 지금 사람 차례: 공은 티에 */
    private fun beginTurn() {
        val p = game.current
        strip.setCurrent(p)
        strip.pulse(p)
        course.newTurn()
        showHoleInfo()
        val first = game.holeIndex == 0 && game.turnPos == 0
        banner(players[p].name + " 차례", if (first) HINT_FIRST else "", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) { readyToAim() }
    }

    private fun readyToAim() {
        if (ended || game.phase != Phase.AIM) return
        aiming = true
        course.aimEnabled = !gamePaused
    }

    override fun onShoot(dirX: Float, dirY: Float, power: Float) {
        if (!acceptingInput) return
        if (!game.shoot(dirX, dirY, power)) return
        aiming = false
        course.aimEnabled = false
        course.clearTrail()
        beeper.dice()
        bar.setStrokes(game.strokes, game.penalties)
        ensureLoop()
    }

    // ── 게임 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onWall(x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.WALL_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastWallSound < GameConfig.WALL_SOUND_GAP_MS) return
        lastWallSound = now
        beeper.step()
    }

    override fun onLip(x: Float, y: Float) {
        beeper.step()
        fxAt(x, y)
        fx.ripple(pa.x, pa.y, 0xFFFFFFFF.toInt(), t(RIPPLE_MS))
    }

    override fun onHoled(result: TurnResult) {
        course.now = timers.now()
        course.startSink(t(GameConfig.SINK_MS))
        bar.setStrokes(game.strokes, game.penalties)
        refreshTotals()
        fxAt(game.hole.cupX, game.hole.cupY)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
        timers.after(t(GameConfig.SINK_MS)) { celebrateHole(result) }
    }

    override fun onWater(x: Float, y: Float, turnOver: Boolean) {
        bar.setStrokes(game.strokes, game.penalties)
        course.clearTrail()
        fxAt(x, y)
        fx.ripple(pa.x, pa.y, GolfArt.WATER_LIGHT, t(SPLASH_MS))
        val px = pa.x
        val py = pa.y
        timers.after(t(SPLASH_GAP_MS)) { fx.ripple(px, py, 0xFFFFFFFF.toInt(), t(SPLASH_MS)) }
        banner(WATER_TEXT, WATER_SUB, BannerTone.BAD, GameConfig.WATER_MS)
        Ui.shake(bar)
        timers.after(t(GameConfig.WATER_MS)) {
            if (turnOver) maxedOut() else readyToAim()
        }
    }

    override fun onStopped(turnOver: Boolean) {
        bar.setStrokes(game.strokes, game.penalties)
        if (turnOver) maxedOut() else readyToAim()
    }

    /** "홀인!" 배너 (1타면 "홀인원!" 대형 글자 + 폭죽) → 다음 사람 */
    private fun celebrateHole(r: TurnResult) {
        val name = players[r.player].name
        val par = game.holes[r.hole].par
        if (r.holeInOne) {
            celebrate.show(HOLE_IN_ONE, name + " · 한 번에 쏙!", t(GameConfig.HOLE_IN_ONE_MS))
            beeper.good()
            fireworks()
            timers.after(t(GameConfig.HOLE_IN_ONE_MS)) { nextTurn() }
        } else {
            val sub = r.score.toString() + "타 · " + MinigolfGame.scoreName(r.score, par, true)
            banner(HOLED, sub, BannerTone.GOOD, GameConfig.HOLED_MS)
            fxAt(game.hole.cupX, game.hole.cupY)
            fx.stars(pa.x, pa.y, 8, t(GameConfig.HOLED_MS))
            timers.after(t(GameConfig.HOLED_MS)) { nextTurn() }
        }
    }

    /** 홀인원 폭죽: 여러 자리에서 별이 차례로 터지고 색종이 */
    private fun fireworks() {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (w <= 0f) return
        for (k in 0 until FIREWORKS) {
            val x = w * FW_X[k]
            val y = h * FW_Y[k]
            timers.after(t(FW_GAP_MS * k)) {
                fx.stars(x, y, 6, t(FW_MS))
                beeper.step()
            }
        }
        timers.after(t(FW_GAP_MS * FIREWORKS)) { fx.confetti(w, h, 14, t(GameConfig.HOLE_IN_ONE_MS / 2)) }
    }

    /** 최대 타수로 끝 */
    private fun maxedOut() {
        val r = game.lastResult ?: return
        refreshTotals()
        banner(GameConfig.MAX_STROKES.toString() + "타 끝", players[r.player].name + " · 이번 홀은 " + r.score + "타로 기록", BannerTone.BAD, GameConfig.MAXED_MS)
        timers.after(t(GameConfig.MAXED_MS)) { nextTurn() }
    }

    private fun nextTurn() {
        if (ended) return
        when (game.nextTurn()) {
            Phase.AIM -> beginTurn()
            Phase.HOLE_END -> showCard(final = true)
            else -> Unit
        }
    }

    /** 점수표 팝업. final = 홀 사이(닫으면 다음 홀), 아니면 언제든 보는 점수표(밖을 눌러 닫기) */
    private fun showCard(final: Boolean) {
        if (!final && (peek != null || scorecard != null || ended)) return
        if (final) {
            peek?.dismiss(false)
            peek = null
        }
        val box = Ui.darkCard(this, 14f)
        val title = if (final) {
            if (game.lastHole) "최종 점수표" else game.hole.number.toString() + "번 홀 끝 · 점수표"
        } else "점수표"
        box.addView(Theme.text(this, title, 18f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        val sv = ScorecardView(this)
        sv.bind(game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        sv.refresh(game.holeIndex)
        box.addView(sv, Theme.lp(Theme.MATCH, Theme.dp(this, sv.heightDp(players.size))).apply { topMargin = Theme.dp(this@MinigolfActivity, 8f) })
        val w = minOf(resources.displayMetrics.widthPixels - Theme.dp(this, 24f), Theme.dp(this, 600f))
        if (final) {
            val label = if (game.lastHole) FINAL_TEXT else NEXT_TEXT
            val b = Ui.button(this, label, Theme.GREEN, 16f) { onNextHole() }
            box.addView(Ui.buttonRow(this, b, heightDp = 44f), Ui.gap(this, 10f))
            nextButton = b
            scorecard = popups.show(box, cancelable = false, widthPx = w)
            beeper.turn()
        } else {
            val b = Ui.button(this, CLOSE_TEXT, Theme.BLUE, 15f) { peek?.dismiss() }
            box.addView(Ui.buttonRow(this, b, heightDp = 40f), Ui.gap(this, 10f))
            var pop: PopupLayer.Popup? = null
            pop = popups.show(box, cancelable = true, widthPx = w, onDismiss = { if (peek === pop) peek = null })
            peek = pop
        }
    }

    private fun onNextHole() {
        if (gamePaused || game.phase != Phase.HOLE_END) return
        scorecard?.dismiss()
        scorecard = null
        nextButton = null
        when (game.nextHole()) {
            Phase.OVER -> finishGame()
            else -> beginHole()
        }
    }

    private fun finishGame() {
        ended = true
        aiming = false
        course.aimEnabled = false
        strip.setCurrent(-1)
        val winners = game.winners()
        val text = if (winners.size == 1) players[winners[0]].name + " 우승!" else "공동 우승!"
        val sub = winners.joinToString(" · ") { players[it].name } + " · " + game.total(winners[0]) + "타"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
    }

    /** 패널: 합계 타수 (파와 차이) */
    private fun refreshTotals() {
        for (p in 0 until game.playerCount) {
            val played = game.parSoFar(p)
            val total = game.total(p)
            val diff = total - played
            val d = if (played == 0) "" else if (diff == 0) " (±0)" else if (diff > 0) " (+$diff)" else " ($diff)"
            strip.setValue(p, total.toString() + "타" + d)
        }
    }

    /** 코스 좌표 → 연출 층 좌표 (pa) */
    private fun fxAt(x: Float, y: Float) {
        course.toScreen(x, y, pa)
        toFx(course, pa)
    }

    // ── 프레임 루프 (공이 구르거나 컵에 빠지는 동안만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        if (game.phase != Phase.ROLLING && !course.busy()) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        course.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::course.isInitialized) course.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val rolling = game.advance(dt)
        course.now = timers.now()
        if (rolling) course.recordTrail()
        course.invalidate()
        if ((rolling || game.phase == Phase.ROLLING || course.busy()) && loopOn) course.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        if (::course.isInitialized) course.aimEnabled = false
    }

    override fun onGameResume() {
        if (aiming && game.phase == Phase.AIM && !ended) course.aimEnabled = true
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        course.release()
    }

    companion object {
        /** 테스트용: 앞 몇 홀만 (0 이면 9홀 전체) */
        var holeLimit = 0

        private const val SIDE_DP = 128f
        /** 한 프레임 최대 시간 (그 이상 멈칫하면 잘라서 물리가 튀지 않게) */
        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 420L
        private const val SPLASH_MS = 700L
        private const val SPLASH_GAP_MS = 180L
        private const val FIREWORKS = 3
        private const val FW_GAP_MS = 320L
        private const val FW_MS = 900L
        private val FW_X = floatArrayOf(0.25f, 0.75f, 0.5f)
        private val FW_Y = floatArrayOf(0.3f, 0.28f, 0.2f)

        private const val CARD_TEXT = "점수표"
        private const val CLOSE_TEXT = "닫기"
        private const val NEXT_TEXT = "다음 홀 ▶"
        private const val FINAL_TEXT = "최종 결과 ▶"
        private const val HOLED = "홀인!"
        private const val HOLE_IN_ONE = "홀인원!"
        private const val WATER_TEXT = "퐁당!"
        private const val WATER_SUB = "벌타 +1 · 친 자리에서 다시"
        private const val HINT_FIRST = "코스를 눌러 뒤로 끌었다 놓으면 퍼팅!"
    }
}
