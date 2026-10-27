package com.siwoo.party.games.bowling.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.swipe.SwipeReject
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.bowling.BowlingGame
import com.siwoo.party.games.bowling.BowlingModule
import com.siwoo.party.games.bowling.GameConfig
import com.siwoo.party.games.bowling.Lane
import com.siwoo.party.games.bowling.Phase
import com.siwoo.party.games.bowling.Pin
import com.siwoo.party.games.bowling.RollMark

/**
 * 볼링 화면 (2~4인, 차례대로, 10 또는 5프레임).
 * 위: 차례 표시줄(HeaderView) + [점수판] / 가운데: 레인(LaneView) / 아래: 플레이어 패널(확정 점수).
 * 흐름: 소개 → 한 사람이 한 프레임을 다 굴리면 다음 사람 → 굴릴 때마다: 멈춤 → 쓰러진 핀 회색으로 잠깐 →
 *       배너(스트라이크!·스페어!·거터…·N핀) → 점수판 잠깐 → 쓰러진 핀 치움 → 다음 투구 … → 최종 점수판 → 점수 순 순위.
 * 물리는 BowlingGame/Lane(고정 1/60초 단계). 프레임 루프(postOnAnimation)는 공이 굴러가는 동안에만 돌고,
 * 모두 멈추거나 일시정지하면 멈춘다 (계속하기 → 굴러가던 중이면 다시 돈다).
 */
class BowlingActivity : PartyGameActivity(), LaneView.Listener, Lane.Listener {

    override val module = BowlingModule

    lateinit var game: BowlingGame
        private set
    lateinit var lane: LaneView
        private set
    lateinit var header: HeaderView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var boardButton: GameButton
        private set

    /** 떠 있는 점수판 (굴린 뒤 잠깐 / [점수판] 버튼 / 최종) */
    var boardPopup: PopupLayer.Popup? = null
        private set
    /** 최종 점수판의 [결과 보기] (테스트에서 누름) */
    var finalButton: GameButton? = null
        private set
    /** 마지막으로 띄운 결과 배너 글자 (테스트 확인용) */
    var lastBanner = ""
        private set
    /** 지금까지 띄운 스트라이크 배너 수 */
    var strikeBanners = 0
        private set

    private var started = false
    private var ended = false
    private var briefTask: GameTimers.Task? = null
    private lateinit var colors: IntArray
    private val pinsUp = BooleanArray(GameConfig.PINS)

    // ── 프레임 루프 (공이 굴러가는 동안에만) ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastHitSound = -10_000L
    private val pa = PointF()

    /** 지금 공을 굴릴 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !gamePaused && game.phase == Phase.AIM && boardPopup == null

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val frameCount = framesOverride ?: BowlingSettings.frames(this, n)
        game = BowlingGame(n, frameCount)
        game.lane.listener = this
        colors = IntArray(n) { playerColor(it) }

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 6f))

        val top = Theme.horizontal(this)
        header = HeaderView(this)
        boardButton = Ui.button(this, "점수판", Theme.BLUE, 15f) { openBoard() }
        top.addView(header, Theme.lp(0, Theme.MATCH, 1f))
        top.addView(boardButton, Theme.lp(Theme.dp(this, 96f), Theme.MATCH).apply { leftMargin = Theme.dp(this@BowlingActivity, 8f) })
        col.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 42f)))

        lane = LaneView(this)
        lane.bind(game)
        lane.listener = this
        col.addView(lane, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BowlingActivity, 4f) })

        strip = PlayerStrip(this, players, false)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@BowlingActivity, 4f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        header.setTurn(players[0].name + " 먼저", colors[0])
        header.setSub(game.frames.toString() + "프레임 경기")
        lane.setBallColor(colors[0])
        updatePins()
        updateScores()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner("볼링 · " + game.frames + "프레임", "어프로치에서 공을 잡고 오른쪽으로 쓱!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn(true)
        }
    }

    /** 차례 표시: 패널 금빛 + 이름 + 프레임·투구 + 공 색 (사람이 바뀌면 배너) */
    private fun showTurn(newPlayer: Boolean) {
        val p = game.current
        val c = game.card
        strip.setCurrent(p)
        lane.setBallColor(colors[p])
        lane.resetBall()
        lane.setHint(true)
        header.setTurn(players[p].name + " 차례", colors[p])
        val bonus = c.isLastFrame && c.rollInFrame == 2
        header.setSub((c.frame + 1).toString() + " / " + game.frames + " 프레임 · " + (c.rollInFrame + 1) + "구" + if (bonus) " (보너스)" else "")
        header.setHint(HINT_AIM)
        updatePins()
        if (newPlayer) {
            val sub = if (c.isLastFrame) "마지막 프레임!" else (c.frame + 1).toString() + "프레임"
            banner(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        }
    }

    override fun canAim(): Boolean = acceptingInput

    override fun onGrab() {
        lane.setHint(false)
    }

    override fun onSwipe(startY: Float, deviationDeg: Float, power: Float, spin: Float) {
        if (!acceptingInput) return
        if (!game.throwBall(startY, deviationDeg, power, spin)) return
        beeper.dice()
        lane.setHint(false)
        header.setHint(HINT_ROLL)
        ensureLoop()
    }

    override fun onBadSwipe(reject: SwipeReject) {
        header.setHint(
            when (reject) {
                SwipeReject.TOO_SHORT -> "더 길게 밀어요!"
                SwipeReject.TOO_SLOW -> "더 빠르게 밀어요!"
                SwipeReject.WRONG_DIRECTION -> "핀 쪽(오른쪽)으로 밀어요!"
                else -> "다시 밀어요!"
            },
        )
        lane.setHint(true)
        beeper.step()
    }

    // ── 레인 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onHit(x: Float, y: Float, speed: Float, ball: Boolean) {
        if (speed < GameConfig.HIT_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHitSound < GameConfig.HIT_SOUND_GAP_MS) return
        lastHitSound = now
        beeper.step()
        if (ball) {
            lane.toScreen(x, y, pa)
            toFx(lane, pa)
            fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
        }
    }

    override fun onKnock(pin: Pin) {}

    // ── 굴림이 끝난 뒤 ──

    private fun onSettled() {
        updateScores()
        updatePins()
        header.setHint("")
        lane.invalidate()
        timers.after(t(GameConfig.FALLEN_SHOW_MS)) { showOutcome() }
    }

    private fun showOutcome() {
        val o = game.outcome
        val name = players[o.player].name
        val ms: Long
        when (o.mark) {
            RollMark.STRIKE -> {
                ms = GameConfig.STRIKE_BANNER_MS
                strikeBanners++
                lastBanner = STRIKE
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(ms))
                deckStars(10)
                banner(STRIKE, name + " · 10핀 모두!", BannerTone.GOOD, ms)
            }
            RollMark.SPARE -> {
                ms = GameConfig.SPARE_BANNER_MS
                lastBanner = SPARE
                deckStars(7)
                banner(SPARE, name + " · 남은 핀을 모두!", BannerTone.GOOD, ms)
            }
            RollMark.GUTTER -> {
                ms = GameConfig.SMALL_BANNER_MS
                lastBanner = GUTTER
                smallBanner(GUTTER, "공이 홈에 빠졌어요", BannerTone.BAD)
            }
            RollMark.MISS -> {
                ms = GameConfig.SMALL_BANNER_MS
                lastBanner = MISS
                smallBanner(MISS, "하나도 못 맞혔어요", BannerTone.BAD)
            }
            RollMark.PINS -> {
                ms = GameConfig.SMALL_BANNER_MS
                lastBanner = o.knocked.toString() + "핀"
                val left = o.standingBefore - o.knocked
                smallBanner(lastBanner, if (o.frameEnded) "프레임 끝 · " + left + "개 남김" else "남은 핀 " + left + "개", BannerTone.INFO)
            }
        }
        timers.after(t(ms)) { showBrief() }
    }

    /** 핀 덱 위에서 별 터뜨리기 */
    private fun deckStars(n: Int) {
        lane.toScreen(GameConfig.HEAD_X + GameConfig.ROW_DX * 1.5f, GameConfig.LANE_W / 2f, pa)
        toFx(lane, pa)
        fx.stars(pa.x, pa.y, n, t(GameConfig.STRIKE_BANNER_MS))
    }

    /** 레인 위쪽 작은 배너 (거터·핀 수) */
    private fun smallBanner(text: String, sub: String, tone: BannerTone) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (w <= 0f) return
        fx.banner(w / 2f, h * 0.5f, Theme.dpf(this, 150f), text, sub, tone, t(GameConfig.SMALL_BANNER_MS))
        when (tone) {
            BannerTone.BAD -> beeper.bad()
            BannerTone.GOOD -> beeper.good()
            BannerTone.INFO -> beeper.turn()
        }
    }

    /** 점수판 팝업 내용 (제목 + 표 + 아래 버튼 하나는 호출 쪽에서) */
    private fun boardCard(title: String, hiRow: Int, hiFrame: Int): android.widget.LinearLayout {
        val card = Ui.darkCard(this, 14f)
        card.addView(Theme.text(this, title, 18f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }, Theme.lp(Theme.MATCH, Theme.WRAP))
        val sheet = ScoreSheetView(this)
        sheet.bind(game, players.map { it.name }, colors)
        sheet.refresh(hiRow, hiFrame)
        card.addView(sheet, Ui.gap(this, 6f))
        return card
    }

    private fun boardWidth(): Int = Theme.dp(this, ScoreSheetView.widthDp(game.frames) + 28f)

    /** 굴린 뒤 잠깐 보여 주는 점수판 (터치하면 바로 닫힘) → 다음 투구 */
    private fun showBrief() {
        if (ended) return
        val o = game.outcome
        val card = boardCard("점수판 · " + players[o.player].name + " " + (o.frame + 1) + "프레임", o.player, o.frame)
        card.addView(Theme.text(this, "터치하면 계속", 12f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@BowlingActivity, 4f) })
        card.setOnClickListener { boardPopup?.dismiss() }
        boardPopup = popups.show(card, cancelable = true, widthPx = boardWidth(), onDismiss = {
            briefTask?.cancel()
            briefTask = null
            boardPopup = null
            proceed()
        })
        briefTask = timers.after(t(GameConfig.BOARD_SHOW_MS)) { boardPopup?.dismiss() }
    }

    /** 쓰러진 핀 치우고 다음 투구(또는 끝) */
    private fun proceed() {
        if (game.phase != Phase.SETTLED) return
        val changed = game.nextTurn()
        lane.invalidate()
        updatePins()
        if (game.phase == Phase.OVER) showFinal() else showTurn(changed)
    }

    /** [점수판] 버튼: 조준 중에만 */
    private fun openBoard() {
        if (!acceptingInput) return
        val c = game.card
        val card = boardCard("점수판", game.current, c.frame)
        card.addView(Ui.button(this, "닫기", Theme.BLUE, 15f) { boardPopup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@BowlingActivity, 8f) })
        boardPopup = popups.show(card, cancelable = true, widthPx = boardWidth(), onDismiss = { boardPopup = null })
    }

    /** 최종 점수판 + [결과 보기] */
    private fun showFinal() {
        ended = true
        strip.setCurrent(-1)
        lane.setHint(false)
        header.setTurn("경기 끝!", Theme.GOLD)
        header.setSub("최종 점수")
        header.setHint("")
        val card = boardCard("최종 점수 · " + game.frames + "프레임", -1, -1)
        val btn = Ui.button(this, "결과 보기 ▶", Theme.GREEN, 16f) { finishGame() }
        card.addView(btn, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@BowlingActivity, 8f) })
        finalButton = btn
        boardPopup = popups.show(card, cancelable = false, widthPx = boardWidth())
    }

    private fun finishGame() {
        if (gamePaused || finalButton == null) return
        finalButton = null
        boardPopup?.dismiss()
        boardPopup = null
        val ranks = game.ranks()
        var winners = 0
        var who = 0
        for (i in ranks.indices) if (ranks[i] == 1) { winners++; who = i }
        val text = if (winners > 1) "공동 우승!" else players[who].name + " 우승!"
        val sub = players.indices.joinToString(" : ") { game.total(it).toString() } + "점"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun updateScores() {
        for (i in players.indices) strip.setValue(i, game.total(i).toString() + "점")
    }

    private fun updatePins() {
        val pins = game.lane.pins
        for (i in pins.indices) pinsUp[i] = pins[i].standing
        header.setPins(pinsUp)
    }

    // ── 프레임 루프 ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.ROLLING) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        lane.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::lane.isInitialized) lane.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val moving = game.advance(dt)
        lane.invalidate()
        if (moving && loopOn) {
            lane.postOnAnimation(frame)
        } else {
            loopOn = false
            if (game.phase == Phase.SETTLED) onSettled()
        }
    }

    override fun onGamePause() {
        stopLoop()
        if (::lane.isInitialized) lane.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        if (::lane.isInitialized) lane.release()
    }

    companion object {
        /** 테스트용 프레임 수 (null 이면 설정·인원에 따라) */
        var framesOverride: Int? = null

        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 300L
        private const val HINT_AIM = "공을 잡고 오른쪽으로 밀기 · 휘게 밀면 훅"
        private const val HINT_ROLL = "굴러가는 중…"
        const val STRIKE = "스트라이크!"
        const val SPARE = "스페어!"
        const val GUTTER = "거터…"
        const val MISS = "아깝다!"
    }
}
