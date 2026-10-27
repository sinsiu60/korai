package com.siwoo.party.games.basket.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.swipe.SwipeReject
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.basket.BasketGame
import com.siwoo.party.games.basket.BasketModule
import com.siwoo.party.games.basket.Court
import com.siwoo.party.games.basket.GameConfig
import com.siwoo.party.games.basket.Phase
import com.siwoo.party.games.basket.Scoring

/**
 * 농구 슛 화면 (2~4인, 차례대로, 10슛).
 * 위: 차례 표시줄(TurnHeaderView) / 가운데: 옆에서 본 코트(CourtView) / 아래: 플레이어 패널(합계 점수).
 * 흐름: 소개 → 바퀴마다 "N번째 자리" 배너 → 1번 사람부터 한 번씩 위로 쓱 밀어 슛 → 공이 바닥에 닿으면 결과 배너
 *       (골 점수·클린 슛·불꽃 보너스 / 아깝다·에어볼) → 다음 사람 … → 마지막 바퀴(가장 먼 자리) 뒤 합계 순위.
 * 물리는 BasketGame/Court(고정 1/60초 단계). 프레임 루프(postOnAnimation)는 공이 나는 동안에만 돌고,
 * 바닥에 닿거나 일시정지하면 멈춘다 (계속하기 → 날던 중이면 다시 돈다).
 */
class BasketActivity : PartyGameActivity(), CourtView.Listener, Court.Listener {

    override val module = BasketModule

    lateinit var game: BasketGame
        private set
    lateinit var court: CourtView
        private set
    lateinit var header: TurnHeaderView
        private set
    lateinit var strip: PlayerStrip
        private set

    /** 마지막으로 띄운 결과 배너 글자 (테스트 확인용) */
    var lastBanner = ""
        private set
    /** 지금까지 띄운 "불꽃 모드!" 배너 수 */
    var fireBanners = 0
        private set

    private var started = false
    private var ended = false
    /** 차례 배너가 끝나 슛을 받는 중 */
    private var aiming = false
    private lateinit var colors: IntArray

    // ── 프레임 루프 (공이 나는 동안에만) ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastHitSound = -10_000L
    private val pa = PointF()

    /** 지금 슛할 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !gamePaused && aiming && game.phase == Phase.AIM

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        game = BasketGame(n, shotsOverride ?: GameConfig.SHOTS)
        game.court.listener = this
        colors = IntArray(n) { playerColor(it) }

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 6f))

        header = TurnHeaderView(this)
        col.addView(header, Theme.lp(Theme.MATCH, Theme.dp(this, 42f)))

        court = CourtView(this)
        court.bind(game)
        court.listener = this
        col.addView(court, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BasketActivity, 4f) })

        strip = PlayerStrip(this, players, false)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@BasketActivity, 4f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateScores()
        showSeat()
        header.setHint("")
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner("농구 슛 · " + game.shots + "슛", "위로 쓱 밀어 슛! 갈수록 멀어져요", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn(true)
        }
    }

    /** 지금 사람·자리를 머리 줄·코트·패널에 표시 */
    private fun showSeat() {
        val p = game.current
        val dist = game.distance
        val three = Scoring.isThree(dist)
        strip.setCurrent(p)
        header.setRound(game.round, game.shots)
        header.setTurn(players[p].name + " 차례", colors[p])
        header.setSpot(dist, three)
        header.setFire(game.onFire(p))
        court.setShooter(colors[p], players[p].name)
        court.setSpot(dist, three)
        court.setFire(game.onFire(p))
    }

    /** 차례 시작 (새 바퀴면 자리 배너 먼저) */
    private fun startTurn(newRound: Boolean) {
        showSeat()
        val p = game.current
        val ms: Long
        if (newRound) {
            ms = GameConfig.ROUND_BANNER_MS
            val dist = game.distance
            val three = Scoring.isThree(dist)
            val title = (game.round + 1).toString() + "번째 자리 · " + (Math.round(dist * 10f) / 10f) + "m"
            val sub = if (game.round == game.shots - 1) "마지막 슛!" else if (three) "3점 라인 밖 — 넣으면 3점!" else "넣으면 2점"
            banner(title, sub, if (three) BannerTone.GOOD else BannerTone.INFO, ms)
        } else {
            ms = GameConfig.TURN_BANNER_MS
            smallBanner(players[p].name + " 차례", if (game.onFire(p)) "불꽃! 넣으면 +1" else "", BannerTone.INFO, ms)
        }
        timers.after(t(ms) / 2) {
            aiming = true
            court.setHint(true)
            header.setHint(HINT_AIM)
        }
    }

    override fun canShoot(): Boolean = acceptingInput

    override fun onGrab() {
        court.setHint(false)
    }

    override fun onShoot(swipeAngleDeg: Float, power: Float) {
        if (!acceptingInput) return
        if (!game.shootSwipe(swipeAngleDeg, power)) return
        aiming = false
        beeper.dice()
        court.setHint(false)
        header.setHint(HINT_FLY)
        ensureLoop()
    }

    override fun onBadSwipe(reject: SwipeReject) {
        header.setHint(
            when (reject) {
                SwipeReject.TOO_SHORT -> "더 길게 밀어요!"
                SwipeReject.TOO_SLOW -> "더 빠르게 쓱!"
                SwipeReject.WRONG_DIRECTION -> "골대 쪽 위로 밀어요!"
                else -> "다시 밀어요!"
            },
        )
        court.setHint(true)
        beeper.step()
    }

    // ── 코트 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onRim(x: Float, y: Float, speed: Float) {
        hitSound(speed)
        court.toScreen(x, y, pa)
        toFx(court, pa)
        fx.ripple(pa.x, pa.y, BasketArt.RIM, t(RIPPLE_MS))
    }

    override fun onBoard(x: Float, y: Float, speed: Float) {
        hitSound(speed)
    }

    override fun onMade(clean: Boolean) {
        beeper.good()
        court.toScreen(0f, GameConfig.RIM_Y - 0.2f, pa)
        toFx(court, pa)
        fx.stars(pa.x, pa.y, if (clean) 9 else 6, t(GameConfig.RESULT_MS))
    }

    private fun hitSound(speed: Float) {
        if (speed < GameConfig.HIT_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHitSound < HIT_SOUND_GAP_MS) return
        lastHitSound = now
        beeper.step()
    }

    // ── 공이 떨어진 뒤 ──

    private fun onSettled() {
        updateScores()
        header.setHint("")
        court.invalidate()
        val o = game.outcome
        val name = players[o.player].name
        if (o.made) {
            lastBanner = o.points.toString() + "점!"
            val kind = if (o.clean) "클린 슛!" else if (o.bank) "뱅크슛!" else "림 맞고 쏙!"
            val sub = if (o.bonus > 0) "$name · $kind · 불꽃 +${o.bonus}" else "$name · $kind"
            strip.pulse(o.player)
            if (Scoring.isThree(o.distance)) fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.RESULT_MS))
            banner(lastBanner, sub, BannerTone.GOOD, GameConfig.RESULT_MS)
        } else {
            lastBanner = if (o.airball) AIRBALL else MISS
            val sub = if (o.fireLost) "불꽃이 꺼졌어요" else if (o.airball) "아무 데도 안 닿았어요" else "림에 튕겼어요"
            smallBanner(lastBanner, sub, BannerTone.BAD, GameConfig.RESULT_MS)
        }
        if (o.fireLost) {
            header.setFire(false)
            court.setFire(false)
        }
        timers.after(t(GameConfig.RESULT_MS)) {
            if (o.fireStarted) showFireStart() else proceed()
        }
    }

    /** 연속 성공으로 불꽃이 막 붙음 */
    private fun showFireStart() {
        val p = game.outcome.player
        fireBanners++
        court.setFire(true)
        header.setFire(true)
        lastBanner = FIRE_ON
        banner(FIRE_ON, players[p].name + " · 다음 골부터 +1점", BannerTone.GOOD, GameConfig.FIRE_BANNER_MS)
        timers.after(t(GameConfig.FIRE_BANNER_MS)) { proceed() }
    }

    /** 다음 사람 (또는 끝) */
    private fun proceed() {
        if (ended || game.phase != Phase.SETTLED) return
        val newRound = game.next()
        court.invalidate()
        if (game.phase == Phase.OVER) finishGame() else startTurn(newRound)
    }

    /** 코트 가운데 작은 배너 (차례·놓침) */
    private fun smallBanner(text: String, sub: String, tone: BannerTone, ms: Long) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (w <= 0f) return
        fx.banner(w / 2f, h * 0.36f, Theme.dpf(this, 150f), text, sub, tone, t(ms))
        when (tone) {
            BannerTone.BAD -> beeper.bad()
            BannerTone.GOOD -> beeper.good()
            BannerTone.INFO -> beeper.turn()
        }
    }

    private fun finishGame() {
        ended = true
        aiming = false
        strip.setCurrent(-1)
        court.setHint(false)
        court.setFire(false)
        header.setFire(false)
        header.setTurn("경기 끝!", Theme.GOLD)
        header.setHint("")
        val ranks = game.ranks()
        var winners = 0
        var who = 0
        for (i in ranks.indices) if (ranks[i] == 1) { winners++; who = i }
        val text = if (winners > 1) "공동 우승!" else players[who].name + " 우승!"
        val sub = players.indices.joinToString(" : ") { game.total(it).toString() } + "점"
        lastBanner = text
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun updateScores() {
        for (i in players.indices) strip.setValue(i, game.total(i).toString() + "점")
    }

    // ── 프레임 루프 ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.FLYING) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        court.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::court.isInitialized) court.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val moving = game.advance(dt)
        court.tickFire(dt)
        court.invalidate()
        if (moving && loopOn) {
            court.postOnAnimation(frame)
        } else {
            loopOn = false
            if (game.phase == Phase.SETTLED) onSettled()
        }
    }

    override fun onGamePause() {
        stopLoop()
        if (::court.isInitialized) court.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        if (::court.isInitialized) court.release()
    }

    companion object {
        /** 테스트용 슛 수 (null 이면 GameConfig.SHOTS) */
        var shotsOverride: Int? = null

        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 300L
        private const val HIT_SOUND_GAP_MS = 80L
        private const val HINT_AIM = "아무 데나 누르고 골대 쪽 위로 쓱! · 빠를수록 세게"
        private const val HINT_FLY = "날아가는 중…"
        const val MISS = "아깝다!"
        const val AIRBALL = "에어볼…"
        const val FIRE_ON = "불꽃 모드!"
    }
}
