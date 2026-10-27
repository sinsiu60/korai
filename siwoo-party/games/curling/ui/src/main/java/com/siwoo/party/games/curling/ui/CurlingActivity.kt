package com.siwoo.party.games.curling.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.curling.CurlStone
import com.siwoo.party.games.curling.CurlingGame
import com.siwoo.party.games.curling.CurlingModule
import com.siwoo.party.games.curling.CurlingRules
import com.siwoo.party.games.curling.EndResult
import com.siwoo.party.games.curling.GameConfig
import com.siwoo.party.games.curling.Phase
import kotlin.random.Random

/**
 * 컬링 화면 (2~4인, 차례대로, 3엔드).
 * 위: 차례 표시(TurnHeaderView) + 휘는 방향 버튼(CurlToggleView) / 가운데: 시트(SheetView) / 아래: 플레이어 패널(합계 점수).
 * 흐름: 소개 → 차례마다 끌었다 놓아 던짐 → 움직이는 동안 연타로 스위핑 → 모두 멈추면 다음 스톤
 *       → 엔드의 마지막 스톤이 멈추면 득점 표시·"N점!" → 점수판(하우스 확대 + 점수표) → 다음 엔드 … → 3엔드 합계 순위.
 * 물리는 CurlingGame(고정 1/60초 단계). 프레임 루프(postOnAnimation)는 스톤이 움직이는 동안에만 돌고,
 * 모두 멈추거나 일시정지하면 멈춘다 (계속하기 → 움직이던 중이면 다시 돈다).
 */
class CurlingActivity : PartyGameActivity(), SheetView.Listener, CurlingGame.Listener {

    override val module = CurlingModule

    lateinit var game: CurlingGame
        private set
    lateinit var sheet: SheetView
        private set
    lateinit var header: TurnHeaderView
        private set
    lateinit var curl: CurlToggleView
        private set
    lateinit var strip: PlayerStrip
        private set

    /** 점수판 팝업과 [다음 엔드] 버튼 (테스트에서 누름) */
    var boardPopup: PopupLayer.Popup? = null
        private set
    var nextButton: GameButton? = null
        private set

    private lateinit var sideColors: IntArray
    private lateinit var sideNames: List<String>

    private var started = false
    private var ended = false

    // ── 프레임 루프 (스톤이 움직이는 동안에만) ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 그린 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastCollideSound = -10_000L
    private var lastSweepSound = -10_000L
    private val pa = PointF()

    /** 지금 던질 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !gamePaused && game.phase == Phase.AIM && boardPopup == null

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val random = randomOverride ?: Random.Default
        val sides = CurlingRules.sideCount(n)
        game = CurlingGame(n, random.nextInt(sides), t(GameConfig.FADE_MS))
        game.listener = this
        sideColors = IntArray(sides) { playerColor(CurlingRules.members(it, n)[0]) }
        sideNames = List(sides) { s -> CurlingRules.members(s, n).joinToString("·") { players[it].name } }

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 6f))

        val top = Theme.horizontal(this)
        header = TurnHeaderView(this)
        header.setup(sides, sideColors, game.stonesPerEnd / sides)
        curl = CurlToggleView(this)
        curl.listener = CurlToggleView.Listener { onCurlPick(it) }
        top.addView(header, Theme.lp(0, Theme.MATCH, 1f))
        top.addView(curl, Theme.lp(Theme.dp(this, 214f), Theme.MATCH).apply { leftMargin = Theme.dp(this@CurlingActivity, 8f) })
        col.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)))

        sheet = SheetView(this)
        sheet.bind(game, sideColors)
        sheet.listener = this
        col.addView(sheet, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@CurlingActivity, 4f) })

        strip = PlayerStrip(this, players, false)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@CurlingActivity, 4f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        header.setEnd(0)
        header.setTurn(players[game.current].name + " 먼저", sideColors[game.currentSide])
        header.setHint(if (game.teamMode) "2 대 2 팀전 · 1인당 스톤 2개" else "1인당 스톤 " + CurlingRules.stonesPerPlayer(n) + "개")
        curl.active = false
        updateScores()
        updateRemaining()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val sub = if (game.teamMode) sideNames[0] + " vs " + sideNames[1] else "끌어서 던지고, 연타로 스위핑!"
        banner("컬링 · " + GameConfig.ENDS + "엔드", sub, BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn("")
        }
    }

    /** 차례 표시: 패널 금빛 + 이름 + 휘는 방향 버튼 켜기 + 짧은 배너 */
    private fun showTurn(prefix: String) {
        val p = game.current
        strip.setCurrent(p)
        header.setTurn(players[p].name + " 차례", sideColors[game.currentSide])
        header.setHint(HINT_AIM)
        curl.select(game.curlDir)
        curl.active = true
        updateRemaining()
        sheet.invalidate()
        val sub = if (game.teamMode) sideNames[game.currentSide] + " 팀" else "남은 스톤 " + game.remaining(p) + "개"
        banner(prefix + players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
    }

    private fun onCurlPick(dir: Int) {
        if (!acceptingInput) {
            curl.select(game.curlDir)
            return
        }
        game.setCurl(dir)
        beeper.step()
        sheet.invalidate()
    }

    override fun canAim(): Boolean = acceptingInput

    override fun onShoot(dirX: Float, dirY: Float, power: Float) {
        if (!acceptingInput) return
        if (!game.throwStone(dirX, dirY, power)) return
        beeper.dice()
        curl.active = false
        header.setHint(if (game.teamMode) HINT_SWEEP_TEAM else HINT_SWEEP)
        updateRemaining()
        ensureLoop()
    }

    override fun onSweepTap(x: Float, y: Float) {
        if (gamePaused || ended || !started) return
        if (!game.sweep()) return
        pa.set(x, y)
        toFx(sheet, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
        val now = SystemClock.uptimeMillis()
        if (now - lastSweepSound >= GameConfig.SWEEP_SOUND_GAP_MS) {
            lastSweepSound = now
            beeper.step()
        }
        if (!loopOn) sheet.invalidate()
    }

    // ── 게임 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onCollide(x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.COLLIDE_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCollideSound < GameConfig.COLLIDE_SOUND_GAP_MS) return
        lastCollideSound = now
        beeper.step()
        sheet.toScreen(x, y, pa)
        toFx(sheet, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
    }

    override fun onOut(stone: CurlStone, hog: Boolean) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (w <= 0f) return
        val y = if (stone.y < GameConfig.SHEET_W / 2f) h * 0.72f else h * 0.32f
        val text = if (hog) "호그 라인!" else "아웃!"
        val sub = if (hog) players[stone.owner].name + "의 스톤이 호그 라인을 못 넘었어요" else players[stone.owner].name + "의 스톤이 밖으로"
        fx.banner(w / 2f, y, w * 0.55f, text, sub, BannerTone.BAD, t(GameConfig.OUT_BANNER_MS))
        beeper.bad()
    }

    override fun onNextThrow(player: Int) {
        showTurn("")
    }

    override fun onEndScored(end: Int, result: EndResult) {
        curl.active = false
        strip.setCurrent(-1)
        updateScores()
        updateRemaining()
        sheet.setShowScore(true)
        header.setTurn((end + 1).toString() + "엔드 끝!", if (result.side >= 0) sideColors[result.side] else Theme.GRAY)
        header.setHint("득점을 재는 중…")
        timers.after(t(GameConfig.SCORE_DELAY_MS)) {
            if (result.blank) {
                banner("블랭크 엔드", if (result.tie) "가장 가까운 스톤이 같은 거리 → 0점" else "하우스에 스톤이 없어 0점", BannerTone.INFO, GameConfig.SCORE_BANNER_MS)
            } else {
                banner(sideNames[result.side] + " " + result.points + "점!", (end + 1).toString() + "엔드 득점", BannerTone.GOOD, GameConfig.SCORE_BANNER_MS)
                val list = result.counting
                for (i in list.indices) {
                    val s = game.stones[list[i]]
                    sheet.toScreen(s.x, s.y, pa)
                    toFx(sheet, pa)
                    fx.stars(pa.x, pa.y, 5, t(GameConfig.SCORE_BANNER_MS))
                }
                strip.members(result.side)
            }
            timers.after(t(GameConfig.SCORE_BANNER_MS + GameConfig.BOARD_DELAY_MS)) { showBoard() }
        }
    }

    private fun PlayerStrip.members(side: Int) {
        for (m in CurlingRules.members(side, players.size)) pulse(m)
    }

    /** 점수판: 제목(득점) + 하우스 확대 + 점수표 + [다음 엔드] */
    private fun showBoard() {
        if (boardPopup != null || ended) return
        val r = game.lastResult
        val card = Ui.darkCard(this, 16f)
        val title = (game.end + 1).toString() + "엔드 결과 · " +
            if (r.blank) "블랭크 (0점)" else sideNames[r.side] + " " + r.points + "점!"
        card.addView(Theme.text(this, title, 19f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }, Theme.lp(Theme.MATCH, Theme.WRAP))
        val row = Theme.horizontal(this)
        row.gravity = Gravity.TOP
        val house = HouseZoomView(this)
        house.bind(game, sideColors)
        val hs = Theme.dp(this, 176f)
        row.addView(house, Theme.lp(hs, hs))
        val right = Theme.vertical(this)
        val table = ScoreTableView(this)
        table.set(game, sideNames, sideColors)
        right.addView(table, Theme.lp(Theme.MATCH, Theme.WRAP))
        val note = Theme.text(this, NOTE, 12f, Theme.TEXT_SUB)
        note.setLineSpacing(0f, 1.15f)
        right.addView(note, Ui.gap(this, 6f))
        val btn = Ui.button(this, if (game.lastEnd) "최종 결과 보기" else (game.end + 2).toString() + "엔드 시작 ▶", Theme.GREEN, 16f) { onNextEnd() }
        right.addView(btn, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@CurlingActivity, 8f) })
        row.addView(right, LinearLayout.LayoutParams(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@CurlingActivity, 14f) })
        card.addView(row, Ui.gap(this, 10f))
        nextButton = btn
        boardPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 580f))
    }

    private fun onNextEnd() {
        if (gamePaused || boardPopup == null) return
        boardPopup?.dismiss()
        boardPopup = null
        nextButton = null
        sheet.setShowScore(false)
        if (game.nextEnd()) {
            header.setEnd(game.end)
            updateRemaining()
            showTurn((game.end + 1).toString() + "엔드 · ")
        } else {
            finishGame()
        }
    }

    private fun finishGame() {
        ended = true
        curl.active = false
        strip.setCurrent(-1)
        val ranks = game.ranks()
        var best = 0
        for (v in game.sideTotals) best = maxOf(best, v)
        var winners = 0
        var who = -1
        for (s in 0 until game.sides) if (game.sideTotals[s] == best) { winners++; who = s }
        val text = if (winners > 1) "공동 우승!" else sideNames[who] + " 우승!"
        val sub = "합계 " + game.sideTotals.joinToString(" : ")
        header.setTurn(text, if (winners > 1) Theme.GOLD else sideColors[who])
        header.setHint(sub)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun updateScores() {
        for (i in players.indices) strip.setValue(i, game.total(i).toString() + "점")
    }

    private fun updateRemaining() {
        for (s in 0 until game.sides) header.setRemaining(s, game.remainingForSide(s))
    }

    // ── 프레임 루프 ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.MOVING) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        sheet.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::sheet.isInitialized) sheet.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val moving = game.advance(dt)
        sheet.invalidate()
        if (moving && loopOn) sheet.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        if (::sheet.isInitialized) sheet.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        sheet.release()
    }

    companion object {
        /** 테스트용 난수 (먼저 던질 편, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 320L
        private const val HINT_AIM = "뒤로 끌었다 놓아 던지기"
        private const val HINT_SWEEP = "연타해서 스위핑! (더 멀리, 덜 휘게)"
        private const val HINT_SWEEP_TEAM = "팀원도 함께 연타해서 스위핑!"
        private const val NOTE = "하우스 중심에 가장 가까운 스톤의 편이,\n상대 가장 가까운 스톤보다 안쪽 스톤 수만큼 득점"
    }
}
