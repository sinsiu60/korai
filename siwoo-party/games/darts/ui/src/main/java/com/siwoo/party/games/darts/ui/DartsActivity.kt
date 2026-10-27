package com.siwoo.party.games.darts.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.darts.AimPath
import com.siwoo.party.games.darts.DartsGame
import com.siwoo.party.games.darts.DartsMode
import com.siwoo.party.games.darts.DartsModule
import com.siwoo.party.games.darts.GameConfig
import com.siwoo.party.games.darts.Hit
import com.siwoo.party.games.darts.Phase
import com.siwoo.party.games.darts.Ring
import com.siwoo.party.games.darts.ThrowResult
import kotlin.random.Random

/**
 * 다트 화면 (2~4인, 차례대로).
 * 왼쪽: 다트판(BoardView), 오른쪽: 차례 판(TurnPanel: 라운드 · 이름 · 이번 차례 다트 3칸) + 플레이어 패널(PlayerStrip, 점수).
 * 흐름: 소개 → "○○ 차례" → 조준점이 8자로 흔들림 → 화면 아무 곳이나 터치 → 다트가 날아가 꽂힘 + 점수 글자
 *       → 3발(301 은 버스트·완료면 그 자리에서) → 꽂힌 다트 감상 → 회수 연출 → 다음 사람 … → 순위.
 * 조준점·연출은 게임 시계(timers.now())로 계산하고, 프레임 루프(postOnAnimation)는 조준점이 흔들리거나 연출 중일 때만 돈다.
 * 일시정지하면 루프가 멈추고 게임 시계도 멈춰 조준점이 그 자리에 선다.
 */
class DartsActivity : PartyGameActivity() {

    override val module = DartsModule

    /** 테스트용: 던질 때 조준점 대신 이 자리(mm)에 정확히 꽂음 (빗나감 없음) */
    fun interface AimHook {
        fun aim(player: Int, dart: Int, out: FloatArray)
    }

    lateinit var game: DartsGame
        private set
    lateinit var board: BoardView
        private set
    lateinit var panel: TurnPanel
        private set
    lateinit var strip: PlayerStrip
        private set
    /** 터치를 받는 영역 (화면 전체) */
    lateinit var touchArea: FrameLayout
        private set
    private lateinit var aimPath: AimPath

    var aimHook: AimHook? = null

    private var started = false
    private var ended = false
    /** 조준점이 흔들리며 터치를 기다리는 중 */
    private var aiming = false
    private var dartStart = 0L

    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    /** 회수 연출을 마친 횟수 (테스트용) */
    var retrieveCount = 0
        private set
    /** 마지막으로 띄운 큰 배너 글자 (테스트용) */
    var lastBanner = ""
        private set
    /** 지금까지 띄운 180 · 불 배너 수 (테스트용) */
    var bigCount = 0
        private set

    private val aimPt = FloatArray(2)
    private val trailX = FloatArray(GameConfig.TRAIL_POINTS)
    private val trailY = FloatArray(GameConfig.TRAIL_POINTS)
    private val pa = PointF()

    /** 지금 터치하면 던지는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && aiming && game.phase == Phase.THROW

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val random = randomOverride ?: Random.Default
        val mode = modeOverride ?: DartsSettings.mode(this)
        game = DartsGame(n, mode, random.nextInt(n))
        aimPath = AimPath(random.nextLong())

        touchArea = FrameLayout(this)
        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        board = BoardView(this)
        val frameBox = FrameLayout(this)
        frameBox.background = Theme.card(this, 0xFF0E1534.toInt(), 16f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 2f)
        frameBox.setPadding(inset, inset, inset, inset)
        frameBox.clipToOutline = true
        frameBox.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        row.addView(frameBox, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        panel = TurnPanel(this)
        side.addView(panel, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true)
        val tileH = when (n) { 2 -> 50f; 3 -> 46f; else -> 40f }
        val stripH = Theme.dp(this, tileH * n + 6f * (n - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@DartsActivity, 6f) })
        row.addView(side, LinearLayout.LayoutParams(Theme.dp(this, SIDE_DP), Theme.MATCH).apply { leftMargin = Theme.dp(this@DartsActivity, 8f) })
        touchArea.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        touchArea.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> onTap()
                MotionEvent.ACTION_UP -> v.performClick()
            }
            true
        }
        stage.addView(touchArea, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        panel.setMode(if (mode == DartsMode.ROUNDS) MODE_ROUNDS else MODE_X01)
        for (i in players.indices) strip.setValue(i, scoreText(i))
        val p = game.current
        strip.setCurrent(p)
        panel.setPlayer(players[p].name, playerColor(p), animate = false)
        panel.setScore(scoreText(p))
        panel.setRound(game.round, game.totalRounds)
        panel.setHint(HINT_WAIT)
        board.setAimColor(playerColor(p))
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        board.now = timers.now()
        val title = if (game.mode == DartsMode.ROUNDS) "다트 · " + game.rounds + "라운드" else "다트 · " + game.start
        val sub = if (game.mode == DartsMode.ROUNDS) "3발씩, 합계가 높은 사람이 우승!" else game.start.toString() + "점을 정확히 0 으로 먼저 만들면 우승!"
        say(title, sub, BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn()
        }
    }

    /** 차례 시작: 패널 금빛 · 차례 판 · 배너 → 첫 다트 */
    private fun startTurn() {
        val p = game.current
        strip.setCurrent(p)
        panel.setPlayer(players[p].name, playerColor(p), animate = true)
        panel.setScore(scoreText(p))
        panel.setRound(game.round, game.totalRounds)
        panel.clearDarts()
        panel.setTotal("", Theme.TEXT)
        panel.setHint(HINT_WAIT)
        board.setAimColor(playerColor(p))
        val sub = if (game.mode == DartsMode.ROUNDS) game.round.toString() + "라운드 · 다트 3발"
        else "남은 점수 " + game.remaining[p] + " · " + game.round + "라운드"
        say(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) { startDart() }
    }

    /** 다트 한 발 조준 시작: 새 8자(시작 위치·기울기) + 궤적 미리 보기 + 조준점 */
    private fun startDart() {
        if (ended) return
        aimPath.newDart(game.round)
        aimPath.trail(trailX, trailY)
        board.setTrail(trailX, trailY)
        dartStart = timers.now()
        updateAim()
        board.trailShown = true
        board.aimShown = true
        aiming = true
        panel.setHint(HINT_AIM)
        ensureLoop()
    }

    private fun updateAim() {
        val now = timers.now()
        board.now = now
        aimPath.point(now - dartStart, aimPt)
        board.setAim(aimPt[0], aimPt[1])
    }

    /** 화면 아무 곳 터치 → 지금 조준점(+작은 빗나감)으로 던짐 */
    private fun onTap() {
        if (!acceptingInput) return
        updateAim()
        val p = game.current
        val hook = aimHook
        if (hook != null) hook.aim(p, game.dartInTurn, aimPt) else aimPath.scatter(aimPt)
        aiming = false
        board.aimShown = false
        board.trailShown = false
        val r = game.throwDart(aimPt[0], aimPt[1]) ?: return
        board.throwDart(r.dart, aimPt[0], aimPt[1], playerColor(p), t(GameConfig.FLIGHT_MS))
        beeper.step()
        panel.setHint("")
        ensureLoop()
        timers.after(t(GameConfig.FLIGHT_MS)) { landed(r) }
    }

    /** 꽂힘: 점수 글자 · 효과 · 패널 갱신 → 다음 다트 / 차례 끝 */
    private fun landed(r: ThrowResult) {
        board.now = timers.now()
        val h = r.hit
        val p = r.player
        board.showText(r.dart, h.label, hitColor(h, r.bust), t(GameConfig.HIT_TEXT_MS))
        board.toScreen(game.turnX[r.dart], game.turnY[r.dart], pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, if (h.special) Theme.GOLD else Theme.GOLD_LIGHT, t(RIPPLE_MS))
        shakeScreen(Theme.dpf(this, if (h.special) 3f else 1.5f), t(SHAKE_MS))
        var bigWait = false
        when {
            r.bust -> beeper.bad()
            h.ring == Ring.OUT -> beeper.bad()
            h.ring == Ring.BULL -> {
                beeper.good()
                fx.stars(pa.x, pa.y, 10, t(GameConfig.BIG_BANNER_MS))
                bigCount++
                say(BULL_BANNER, players[p].name + " · 한가운데 50점!", BannerTone.GOOD, GameConfig.BIG_BANNER_MS)
                bigWait = true
            }
            h.special -> beeper.good()
            else -> beeper.dice()
        }
        val pts = when {
            r.bust -> BUST_SHORT
            game.mode == DartsMode.ROUNDS -> PLUS[h.points]
            else -> MINUS[h.points]
        }
        panel.setDart(r.dart, h.label, pts, hitColor(h, r.bust))
        strip.setValue(p, scoreText(p))
        panel.setScore(scoreText(p))
        if (r.counted > 0) strip.pulse(p)
        panel.setTotal(totalText(r), if (r.bust) Theme.RED else Theme.TEXT)
        ensureLoop()
        val wait = if (bigWait) GameConfig.BIG_BANNER_MS else GameConfig.AFTER_DART_MS
        if (r.turnOver) timers.after(t(wait)) { turnOver(r) }
        else timers.after(t(wait)) { startDart() }
    }

    /** 차례 끝: 180 · 버스트 · 완료 배너 → 꽂힌 다트 감상 → 회수 */
    private fun turnOver(r: ThrowResult) {
        val p = r.player
        var wait = GameConfig.TURN_END_PAUSE_MS
        when {
            r.bust -> {
                say(BUST_BANNER, "0 밑으로 내려가 이번 차례 점수 취소 · 남은 " + game.remaining[p], BannerTone.BAD, GameConfig.BIG_BANNER_MS)
                wait = GameConfig.BIG_BANNER_MS
            }
            r.finished -> {
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.BIG_BANNER_MS))
                say(players[p].name + " 완료!", (game.finishOrder[p] + 1).toString() + "등으로 " + game.start + " 끝!", BannerTone.GOOD, GameConfig.BIG_BANNER_MS)
                strip.setValue(p, scoreText(p))
                wait = GameConfig.BIG_BANNER_MS
            }
            r.turnTotal == MAX_TURN -> {
                bigCount++
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.BIG_BANNER_MS))
                say(TON80_BANNER, players[p].name + " · 트리플 20 세 발!", BannerTone.GOOD, GameConfig.BIG_BANNER_MS)
                wait = GameConfig.BIG_BANNER_MS
            }
        }
        panel.setHint(HINT_RETRIEVE)
        timers.after(t(wait)) { retrieve() }
    }

    /** 회수 연출: 꽂힌 다트가 하나씩 빠져 날아감 → 다음 사람 */
    private fun retrieve() {
        board.now = timers.now()
        val stagger = t(GameConfig.RETRIEVE_STAGGER_MS)
        val ms = t(GameConfig.RETRIEVE_MS)
        board.retrieveAll(ms, stagger)
        beeper.step()
        ensureLoop()
        timers.after(ms + stagger * (GameConfig.DARTS_PER_TURN - 1)) {
            board.clearDarts()
            retrieveCount++
            if (game.endTurn()) finishGame() else startTurn()
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        aiming = false
        board.aimShown = false
        board.trailShown = false
        strip.setCurrent(-1)
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val w = winners[0]
        val title = if (winners.size == 1) players[w].name + " 우승!" else "공동 우승!"
        val sub = when {
            game.mode == DartsMode.ROUNDS -> "합계 " + game.scores[w] + "점" + if (winners.size > 1) " · " + winners.joinToString(" · ") { players[it].name } else ""
            game.endedByRoundLimit && game.finishedCount == 0 -> game.maxRounds.toString() + "라운드 끝 · 남은 점수가 가장 적어요 (" + game.remaining[w] + ")"
            game.endedByRoundLimit -> game.maxRounds.toString() + "라운드 끝 · 가장 먼저 " + game.start + " 을 끝냈어요"
            else -> "가장 먼저 " + game.start + " 을 끝냈어요"
        }
        panel.setPlayer(if (winners.size == 1) players[w].name else title, if (winners.size == 1) playerColor(w) else Theme.GOLD, animate = true)
        panel.setScore(if (winners.size == 1) scoreText(w) else "")
        panel.clearDarts()
        panel.setTotal(title, Theme.GOLD)
        panel.setHint(HINT_END)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        say(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 글자 ──

    /** 패널 점수 글자: 라운드제 "123점", 301 "남은 187" / "완료 · 1등" */
    private fun scoreText(p: Int): String = when {
        game.mode == DartsMode.ROUNDS -> game.scores[p].toString() + "점"
        game.finished(p) -> "완료 · " + (game.finishOrder[p] + 1) + "등"
        else -> "남은 " + game.remaining[p]
    }

    private fun totalText(r: ThrowResult): String = when {
        r.bust -> "버스트! 점수 그대로 (" + game.remaining[r.player] + ")"
        game.mode == DartsMode.ROUNDS -> "이번 차례 " + r.turnTotal + "점"
        r.finished -> "0 완료!"
        else -> "이번 차례 −" + r.turnTotal + " → 남은 " + game.remaining[r.player]
    }

    private fun hitColor(h: Hit, bust: Boolean): Int = when {
        bust -> Theme.RED
        h.ring == Ring.OUT -> 0xFFFF8A8A.toInt()
        h.special -> Theme.GOLD
        else -> Theme.TEXT
    }

    /** 큰 배너 (마지막 글자를 기억 — 테스트 확인용) */
    private fun say(text: String, sub: String, tone: BannerTone, ms: Long) {
        lastBanner = text
        banner(text, sub, tone, ms)
    }

    // ── 프레임 루프 (조준점이 흔들리거나 연출 중일 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        board.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::board.isInitialized) board.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        board.now = timers.now()
        if (aiming) updateAim()
        val busy = board.tick()
        board.invalidate()
        if ((aiming || busy) && loopOn) board.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::board.isInitialized) return
        board.now = timers.now()
        if (aiming || board.tick()) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        if (::board.isInitialized) board.release()
    }

    companion object {
        /** 테스트용 난수 (첫 차례 · 조준 seed, null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 경기 방식 (null 이면 저장된 설정) */
        var modeOverride: DartsMode? = null

        private const val SIDE_DP = 256f
        private const val RIPPLE_MS = 380L
        private const val SHAKE_MS = 160L
        private const val MAX_TURN = 180
        const val BULL_BANNER = "불!"
        const val TON80_BANNER = "180!!"
        const val BUST_BANNER = "버스트!"
        private const val BUST_SHORT = "버스트"
        private const val MODE_ROUNDS = "라운드 점수제"
        private const val MODE_X01 = "301 (더블 아웃 없음)"
        private const val HINT_WAIT = "곧 시작해요"
        const val HINT_AIM = "조준점이 지날 때 아무 곳이나 터치!"
        private const val HINT_RETRIEVE = "다트 회수 중…"
        private const val HINT_END = "경기 끝!"
        private val PLUS = Array(61) { "+$it" }
        private val MINUS = Array(61) { "−$it" }
    }
}
