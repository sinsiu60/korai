package com.siwoo.party.games.archery.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.archery.ArcheryGame
import com.siwoo.party.games.archery.ArcheryModule
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Phase
import com.siwoo.party.games.archery.Shot
import kotlin.random.Random

/**
 * 양궁 화면 (2~4인, 차례대로, 1인당 6발 — 한 발씩 돌아가며).
 * 왼쪽: 사람 패널(합계 점수·X 개수) + [점수표] | 오른쪽: 차례 표시(몇 발째·이름·화살 6칸) + 경기장(FieldView).
 * 흐름: 소개 → "○○ 차례" → 조준점이 8자로 흔들림 → 길게 누르면 숨 참기(흔들림 줄어듦, 2초 넘으면 다시 커짐),
 *       누른 채 끌어 바람만큼 조준 옮기기 → 떼면 발사 → 화살이 날아가 꽂힘 → 확대 화면(점수 배지·"엑스 텐!") →
 *       다음 사람 … → 모두 6발 → 최종 점수표 → 합계(→ X 개수 → 10점 개수) 순위.
 * 흔들림·숨 참기는 게임 시계(timers.now())로 계산하므로 일시정지하면 그대로 멈춘다.
 * 프레임 루프(postOnAnimation)는 조준 중·화살 비행·확대 화면 연출 중에만 돌고, 일시정지·대기 중에는 멈춘다.
 */
class ArcheryActivity : PartyGameActivity(), FieldView.Listener {

    override val module = ArcheryModule

    lateinit var game: ArcheryGame
        private set
    lateinit var field: FieldView
        private set
    lateinit var header: TurnHeaderView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var zoom: ZoomView
        private set
    private lateinit var cardButton: GameButton

    private var started = false
    private var ended = false
    /** 차례 배너가 끝나 터치를 받는 중 */
    private var aiming = false

    /** 마지막 점수표와 [최종 결과] 버튼 (테스트에서 누름) */
    var finalPopup: PopupLayer.Popup? = null
        private set
    var finalButton: GameButton? = null
        private set
    /** [점수표] 버튼으로 연 점수표 */
    private var peek: PopupLayer.Popup? = null

    private var zoomTask: GameTimers.Task? = null
    private var resultShown = false

    // ── 프레임 루프 ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private val pa = PointF()
    private val loc = IntArray(2)
    private val loc2 = IntArray(2)

    /** 지금 누르면 숨 참기를 시작하는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !gamePaused && aiming && game.phase == Phase.AIM && peek == null

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val random = randomOverride ?: Random.Default
        val first = random.nextInt(n)
        game = ArcheryGame(n, random.nextLong(), first, noiseOverride ?: 1f)

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)

        // 왼쪽: 사람 패널 + 점수표 버튼
        val left = Theme.vertical(this)
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.NONE)
        left.addView(strip, Theme.lp(Theme.MATCH, 0, 1f))
        cardButton = Ui.button(this, CARD_TEXT, Theme.BLUE, 14f) { showCard(final = false) }
        left.addView(cardButton, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)).apply { topMargin = Theme.dp(this@ArcheryActivity, 6f) })
        row.addView(left, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))

        // 오른쪽: 차례 표시 + 경기장
        val right = Theme.vertical(this)
        header = TurnHeaderView(this)
        right.addView(header, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)))
        field = FieldView(this)
        field.bind(game, IntArray(n) { playerColor(it) })
        field.listener = this
        val frameBox = FrameLayout(this)
        frameBox.background = Theme.card(this, 0xFF2F7D32.toInt(), 14f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 3f)
        frameBox.setPadding(inset, inset, inset, inset)
        frameBox.clipToOutline = true
        frameBox.addView(field, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        right.addView(frameBox, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@ArcheryActivity, 6f) })
        row.addView(right, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@ArcheryActivity, 8f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        zoom = ZoomView(this)
        zoom.onTap = { skipZoom() }
        stage.addView(zoom, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        refreshTotals()
        val p = game.current
        header.setRound(0)
        header.setTurn(players[p].name + " 먼저", playerColor(p))
        header.setHint(GameConfig.ARROWS.toString() + "발 합계로 순위")
        header.setShots(game.shots[p])
        field.shooter = p
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        field.now = timers.now()
        banner(module.name + " · " + GameConfig.ARROWS + "발", INTRO_SUB, BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            beginTurn()
        }
    }

    /** 지금 사람 차례: 바람 표시 → 배너 → 조준 받기 */
    private fun beginTurn() {
        if (ended) return
        val p = game.current
        strip.setCurrent(p)
        strip.pulse(p)
        header.setRound(game.arrow)
        header.setTurn(players[p].name + " 차례", playerColor(p))
        header.setShots(game.shots[p])
        header.setHint("")
        field.shooter = p
        field.setWind(game.wind)
        field.now = timers.now()
        field.aimVisible = true
        val w = game.wind
        banner(players[p].name + " 차례", (game.arrow + 1).toString() + "번째 화살 · 바람 " + w.speedText + " m/s", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        ensureLoop()
        timers.after(t(GameConfig.TURN_BANNER_MS)) {
            if (ended || game.phase != Phase.AIM) return@after
            aiming = true
            header.setHint(HINT_AIM)
        }
    }

    // ── 터치 (FieldView) ──

    override fun onPress(): Boolean {
        if (!acceptingInput) return false
        if (!game.press(timers.now())) return false
        header.setHint(HINT_HOLD)
        ensureLoop()
        return true
    }

    override fun onDrag(dxPx: Float, dyPx: Float) {
        if (!acceptingInput || !game.holding) return
        val u = field.unitPx
        if (u <= 0f) return
        game.moveAim(dxPx * GameConfig.AIM_DRAG_GAIN / u, dyPx * GameConfig.AIM_DRAG_GAIN / u)
    }

    override fun onRelease() {
        if (!acceptingInput || !game.holding) return
        val s = game.release(timers.now()) ?: return
        shoot(s)
    }

    override fun onCancel() {
        if (game.holding) game.cancelHold(timers.now())
        if (acceptingInput) header.setHint(HINT_AIM)
    }

    /** 발사: 화살 비행 → 꽂힘 */
    private fun shoot(s: Shot) {
        aiming = false
        field.aimVisible = false
        header.setHint("")
        beeper.dice()
        field.now = timers.now()
        field.startFlight(s.x, s.y, s.arrow, t(GameConfig.FLIGHT_MS))
        ensureLoop()
        timers.after(t(GameConfig.FLIGHT_MS)) { landed(s) }
    }

    private fun landed(s: Shot) {
        field.now = timers.now()
        field.busy()
        field.invalidate()
        beeper.step()
        field.toScreen(s.x, s.y, pa)
        toFx(field, pa)
        fx.ripple(pa.x, pa.y, if (s.miss) 0xFFFFFFFF.toInt() else Theme.GOLD_LIGHT, t(RIPPLE_MS))
        refreshTotals()
        header.setShots(game.shots[s.player])
        timers.after(t(GameConfig.HIT_PAUSE_MS)) { openZoom(s) }
    }

    /** 확대 화면: 경기장 과녁 자리에서 커지며 → 결과 배너 → 잠시 뒤 닫힘 */
    private fun openZoom(s: Shot) {
        if (ended) return
        field.getLocationInWindow(loc)
        zoom.getLocationInWindow(loc2)
        val sx = loc[0] - loc2[0] + field.targetX
        val sy = loc[1] - loc2[1] + field.targetY
        val now = timers.now()
        resultShown = false
        zoom.show(game, s, playerColor(s.player), players[s.player].name, sx, sy, field.targetR, now, t(GameConfig.ZOOM_IN_MS))
        ensureLoop()
        timers.after(t(GameConfig.ZOOM_IN_MS)) { resultFx(s) }
        zoomTask = timers.after(t(GameConfig.ZOOM_IN_MS + GameConfig.ZOOM_HOLD_MS)) { closeZoom() }
    }

    private fun resultFx(s: Shot) {
        resultShown = true
        val name = players[s.player].name
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        // 배너·별은 확대 화면의 점수 배지 자리에 (과녁의 화살을 가리지 않게)
        zoom.badgeCenter(pa)
        toFx(zoom, pa)
        val bw = maxOf(zoom.badgeColumnWidth, w * 0.3f)
        when {
            s.isX -> {
                beeper.good()
                if (w > 0f) {
                    fx.banner(pa.x, pa.y, bw, X_TEN, name + " · 정중앙 X 링!", BannerTone.GOOD, t(GameConfig.RESULT_BANNER_MS))
                    fx.stars(pa.x, pa.y, 10, t(GameConfig.RESULT_BANNER_MS))
                    fx.confetti(w, h, 12, t(GameConfig.RESULT_BANNER_MS))
                }
                strip.pulse(s.player)
            }
            s.score == GameConfig.RINGS -> {
                beeper.good()
                if (w > 0f) fx.stars(pa.x, pa.y, 7, t(GameConfig.RESULT_BANNER_MS))
            }
            s.miss -> {
                beeper.bad()
                if (w > 0f) fx.banner(pa.x, pa.y, bw, MISS, name + " · 과녁 밖 0점", BannerTone.BAD, t(GameConfig.RESULT_BANNER_MS))
            }
            else -> beeper.step()
        }
    }

    /** 확대 화면을 누르면 (결과가 나온 뒤) 바로 닫기 */
    private fun skipZoom() {
        if (gamePaused || !resultShown) return
        val task = zoomTask ?: return
        task.cancel()
        closeZoom()
    }

    private fun closeZoom() {
        zoomTask = null
        if (!zoom.isShowing) return
        zoom.now = timers.now()
        zoom.hide(zoom.now, t(GameConfig.ZOOM_OUT_MS))
        ensureLoop()
        timers.after(t(GameConfig.ZOOM_OUT_MS)) { afterShot() }
    }

    private fun afterShot() {
        zoom.dismissNow()
        when (game.next()) {
            Phase.AIM -> beginTurn()
            Phase.OVER -> {
                strip.setCurrent(-1)
                field.aimVisible = false
                header.setTurn(FINISHED, Theme.GOLD)
                header.setHint("")
                timers.after(t(GameConfig.BOARD_DELAY_MS)) { showCard(final = true) }
            }
            else -> Unit
        }
    }

    /** 점수표 팝업. final = 마지막 (닫으면 순위), 아니면 언제든 보는 점수표 (밖을 눌러 닫기) */
    private fun showCard(final: Boolean) {
        if (!final && (peek != null || finalPopup != null || ended)) return
        if (final) {
            peek?.dismiss(false)
            peek = null
        }
        val box = Ui.darkCard(this, 14f)
        box.addView(Theme.text(this, if (final) FINAL_TITLE else CARD_TEXT, 18f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        val table = ScoreTableView(this)
        table.bind(game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        box.addView(table, Theme.lp(Theme.MATCH, Theme.dp(this, table.heightDp(players.size))).apply { topMargin = Theme.dp(this@ArcheryActivity, 8f) })
        box.addView(Theme.text(this, TIE_NOTE, 12f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.gap(this, 6f))
        val w = minOf(resources.displayMetrics.widthPixels - Theme.dp(this, 24f), Theme.dp(this, 600f))
        if (final) {
            val b = Ui.button(this, FINAL_TEXT, Theme.GREEN, 16f) { finishGame() }
            box.addView(Ui.buttonRow(this, b, heightDp = 44f), Ui.gap(this, 8f))
            finalButton = b
            finalPopup = popups.show(box, cancelable = false, widthPx = w)
            beeper.turn()
        } else {
            val b = Ui.button(this, CLOSE_TEXT, Theme.BLUE, 15f) { peek?.dismiss() }
            box.addView(Ui.buttonRow(this, b, heightDp = 40f), Ui.gap(this, 8f))
            var pop: PopupLayer.Popup? = null
            pop = popups.show(box, cancelable = true, widthPx = w, onDismiss = { if (peek === pop) peek = null })
            peek = pop
        }
    }

    private fun finishGame() {
        if (ended || gamePaused) return
        ended = true
        finalPopup?.dismiss()
        finalPopup = null
        finalButton = null
        aiming = false
        strip.setCurrent(-1)
        val winners = game.winners()
        val text = if (winners.size == 1) players[winners[0]].name + " 우승!" else "공동 우승!"
        val wp = winners[0]
        val sub = winners.joinToString(" · ") { players[it].name } + " · " + game.totals[wp] + "점" +
            if (game.xCount[wp] > 0) " (X " + game.xCount[wp] + "개)" else ""
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        val ranks = game.ranks()
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    /** 패널: 합계 점수 (X 개수) */
    private fun refreshTotals() {
        for (p in 0 until game.players) {
            val x = game.xCount[p]
            strip.setValue(p, game.totals[p].toString() + "점" + if (x > 0) " · X" + x else "")
        }
    }

    // ── 프레임 루프 (조준 중·비행·확대 연출 중에만) ──

    private fun needLoop(): Boolean =
        !ended && ((game.phase == Phase.AIM && field.aimVisible) || field.busy() || zoom.busy())

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        if (!needLoop()) return
        loopOn = true
        field.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::field.isInitialized) field.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        val now = timers.now()
        field.now = now
        zoom.now = now
        field.invalidate()
        if (zoom.isShowing) zoom.invalidate()
        if (needLoop() && loopOn) field.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::game.isInitialized) return
        // 일시정지 중에 손을 뗐으므로 숨 참기는 쏘지 않고 끝 (흔들림은 보통으로)
        if (game.holding) {
            game.cancelHold(timers.now())
            field.cancelTouch()
            if (acceptingInput) header.setHint(HINT_AIM)
        }
        field.now = timers.now()
        zoom.now = field.now
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        field.release()
        zoom.release()
    }

    companion object {
        /** 테스트용 난수 (처음 쏘는 사람·바람·흔들림 씨앗, null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 흔들림·흩어짐 배율 (0 = 흔들리지 않음, null 이면 1) */
        var noiseOverride: Float? = null

        private const val SIDE_DP = 128f
        private const val RIPPLE_MS = 420L
        private const val CARD_TEXT = "점수표"
        private const val CLOSE_TEXT = "닫기"
        private const val FINAL_TITLE = "최종 점수표"
        private const val FINAL_TEXT = "최종 결과 ▶"
        private const val FINISHED = "경기 끝!"
        private const val X_TEN = "엑스 텐!"
        private const val MISS = "빗나감!"
        private const val INTRO_SUB = "길게 눌러 숨 참기 · 끌어서 조준 · 떼면 발사!"
        private const val HINT_AIM = "누른 채 끌어 조준 · 떼면 발사"
        private const val HINT_HOLD = "흔들림이 작을 때 손 떼기!"
        private const val TIE_NOTE = "합계가 같으면 X 개수 → 10점 개수로 순위"
    }
}
