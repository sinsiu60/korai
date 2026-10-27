package com.siwoo.party.games.tuho.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.tuho.GameConfig
import com.siwoo.party.games.tuho.Outcome
import com.siwoo.party.games.tuho.Phase
import com.siwoo.party.games.tuho.TuhoField
import com.siwoo.party.games.tuho.TuhoGame
import com.siwoo.party.games.tuho.TuhoModule

/**
 * 투호 화면 (2~4인, 차례대로, 1인당 화살 10개).
 * 위: 차례 표시(TurnHeaderView — 순번·이름·안내·지금 사람의 화살 10칸) / 왼쪽: 플레이어 패널(합계) / 가운데: 마당(CourtView).
 * 흐름: 소개 → [차례 배너 → ① 각도 게이지 왕복, 터치로 멈춤 → ② 힘 게이지 왕복, 터치로 멈춤 → 포물선 비행
 *       → 입구 "탁!" +2 / 귀 "탁!" +3 / 테두리·몸통 튕김·바닥 "아깝다…"] × (인원 × 10) → 합계 순위.
 * 차례는 한 사람씩 화살 한 개씩 돌아간다. 꽂힌 화살은 그 순번(모두의 k 번째 화살)이 끝날 때까지 항아리에 남는다.
 * 게이지·비행은 게임 시계(timers.now(), 일시정지 시간 제외)로 계산 → 일시정지하면 그대로 멈춘다.
 * 프레임 루프(postOnAnimation)는 게이지가 움직이거나 화살이 나는 동안에만 돈다.
 */
class TuhoActivity : PartyGameActivity(), CourtView.Listener {

    override val module = TuhoModule

    lateinit var game: TuhoGame
        private set
    lateinit var court: CourtView
        private set
    lateinit var header: TurnHeaderView
        private set
    lateinit var strip: PlayerStrip
        private set

    private lateinit var colors: IntArray
    private var started = false
    private var ended = false
    /** 비행 결과(land)를 이미 처리했는지 */
    private var landed = false

    // ── 프레임 루프 ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 그린 프레임 수 (테스트용) */
    var frames = 0L
        private set
    private val pa = PointF()

    /** 각도 게이지를 멈출 수 있음 */
    val acceptingAngle: Boolean get() = started && !ended && !gamePaused && game.angleLive
    /** 힘 게이지를 멈출 수 있음 */
    val acceptingPower: Boolean get() = started && !ended && !gamePaused && game.powerLive

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        game = TuhoGame(n)
        colors = IntArray(n) { playerColor(it) }

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 8f))
        header = TurnHeaderView(this)
        col.addView(header, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)))
        val row = Theme.horizontal(this)
        strip = PlayerStrip(this, players, true)
        court = CourtView(this)
        court.bind(game, colors)
        court.listener = this
        row.addView(strip, Theme.lp(Theme.dp(this, STRIP_DP), Theme.MATCH))
        row.addView(court, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@TuhoActivity, 6f) })
        col.addView(row, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@TuhoActivity, 6f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        header.setRound(0)
        header.setTurn(players[0].name + " 먼저", colors[0])
        header.setHint("1인당 화살 " + GameConfig.ARROWS + "개 · 한 개씩 돌아가며")
        updateTotals()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner("투호 · 화살 " + GameConfig.ARROWS + "개", "각도 → 힘을 멈춰 항아리에 쏙!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn()
        }
    }

    /** 차례 시작: 패널 금빛 + 이름 + 짧은 배너 → 각도 게이지 */
    private fun startTurn() {
        if (ended) return
        val p = game.current
        landed = false
        court.clearFlight()
        strip.setCurrent(p)
        header.setRound(game.arrow)
        header.setTurn(players[p].name + " 차례", colors[p])
        header.setHint(HINT_WAIT)
        header.setSlots(game.scores[p], game.arrow)
        court.now = timers.now()
        court.invalidate()
        val last = game.arrow == GameConfig.ARROWS - 1
        val sub = when {
            game.newRound && last -> "마지막 화살!"
            game.newRound -> (game.arrow + 1).toString() + "번째 화살"
            else -> "화살 " + (game.arrow + 1) + "/" + GameConfig.ARROWS
        }
        banner(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) {
            if (game.beginAim(timers.now())) {
                header.setHint(HINT_ANGLE)
                ensureLoop()
            }
        }
    }

    override fun onCourtTap() {
        if (acceptingAngle) stopAngle() else if (acceptingPower) stopPower()
    }

    private fun stopAngle() {
        val now = timers.now()
        val deg = game.stopAngle(now)
        if (deg.isNaN()) return
        court.now = now
        beeper.step()
        header.setHint(HINT_POWER)
        court.invalidate()
    }

    private fun stopPower() {
        val now = timers.now()
        val shot = game.stopPower(now) ?: return
        court.now = now
        court.startFlight(shot, game.current, now, t(1000L) * GameConfig.FLIGHT_SLOWMO)
        beeper.step()
        header.setHint(HINT_FLIGHT)
        ensureLoop()
    }

    /** 화살이 결과 자리에 닿음 (프레임 루프에서 한 번) */
    private fun onLand() {
        if (landed) return
        landed = true
        val shot = game.land() ?: return
        val p = game.current
        updateTotals()
        header.setSlots(game.scores[p], -1)
        val o = shot.outcome
        val idx = TuhoField.openingIndex(o)
        if (idx >= 0) {
            // "탁!" + 축하음
            beeper.step()
            timers.after(t(GameConfig.CHEER_DELAY_MS)) { beeper.good() }
            court.openingPoint(idx, pa)
            toFx(court, pa)
            fx.stars(pa.x, pa.y, if (o == Outcome.MOUTH) 7 else 11, t(GameConfig.RESULT_BANNER_MS))
            strip.pulse(p)
            val text = if (o == Outcome.MOUTH) TEXT_MOUTH else TEXT_EAR
            val sub = players[p].name + " " + game.totals[p] + "점"
            banner(text, sub, BannerTone.GOOD, GameConfig.RESULT_BANNER_MS, sound = false)
            header.setHint(if (o == Outcome.MOUTH) HINT_MOUTH else HINT_EAR)
        } else {
            val sub = when (o) {
                Outcome.RIM -> "테두리에 맞고 튕겼어요"
                Outcome.BODY -> "항아리에 맞고 튕겼어요"
                else -> if (shot.x < TuhoField.mouth.cx) "조금 짧았어요" else "너무 멀리 갔어요"
            }
            if (o.bounces) {
                court.toScreen(shot.x, shot.y, pa)
                toFx(court, pa)
                fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
            }
            banner(TEXT_MISS, sub, BannerTone.BAD, GameConfig.RESULT_BANNER_MS)
            header.setHint(sub)
        }
        timers.after(t(GameConfig.RESULT_MS)) { afterResult() }
    }

    private fun afterResult() {
        if (ended) return
        if (game.next()) startTurn() else finishGame()
    }

    private fun finishGame() {
        ended = true
        strip.setCurrent(-1)
        header.setSlots(null, -1)
        val ranks = game.ranks()
        var winners = 0
        var who = -1
        for (i in ranks.indices) if (ranks[i] == 1) { winners++; who = i }
        val text = if (winners > 1) "공동 우승!" else players[who].name + " 우승!"
        val sub = "합계 " + game.totals.joinToString(" : ") + "점"
        header.setTurn(text, if (winners > 1) Theme.GOLD else colors[who])
        header.setHint(sub)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun updateTotals() {
        for (i in players.indices) strip.setValue(i, POINT_TEXT[game.totals[i].coerceIn(0, POINT_TEXT.size - 1)])
    }

    // ── 프레임 루프 ──

    private fun needsLoop(now: Long): Boolean =
        game.angleLive || game.powerLive || (game.phase == Phase.FLIGHT) || court.animating(now)

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        court.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::court.isInitialized) court.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        val now = timers.now()
        court.now = now
        if (game.phase == Phase.FLIGHT && court.reachedEvent(now)) onLand()
        court.invalidate()
        if (loopOn && needsLoop(now)) court.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::court.isInitialized) return
        val now = timers.now()
        court.now = now
        if (needsLoop(now)) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    companion object {
        private const val STRIP_DP = 124f
        private const val RIPPLE_MS = 360L
        private const val HINT_WAIT = "곧 각도 게이지가 움직여요"
        private const val HINT_ANGLE = "① 각도 바늘이 원하는 곳에 오면 터치!"
        private const val HINT_POWER = "② 힘 게이지를 멈추세요! (화면 아무 곳)"
        private const val HINT_FLIGHT = "슝—"
        private const val HINT_MOUTH = "탁! 입구에 쏙 (+2)"
        private const val HINT_EAR = "탁! 귀에 쏙 (+3)"
        private const val TEXT_MOUTH = "입구! +2"
        private const val TEXT_EAR = "귀! +3"
        private const val TEXT_MISS = "아깝다…"
        private val POINT_TEXT = Array(GameConfig.ARROWS * GameConfig.EAR_POINTS + 1) { it.toString() + "점" }
    }
}
