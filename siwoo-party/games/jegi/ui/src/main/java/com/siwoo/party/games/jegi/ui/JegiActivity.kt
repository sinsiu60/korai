package com.siwoo.party.games.jegi.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jegi.EndReason
import com.siwoo.party.games.jegi.GameConfig
import com.siwoo.party.games.jegi.JegiGame
import com.siwoo.party.games.jegi.JegiModule
import com.siwoo.party.games.jegi.Phase
import com.siwoo.party.games.jegi.Tap
import kotlin.random.Random

/**
 * 제기차기 화면 (2~4인, 차례대로).
 * 왼쪽: 마당(YardView — 터치를 받는 곳), 오른쪽: 차례 판(JegiPanel: 몇 번째 도전 · 이름 · 개수 · 판정 구간 막대 · 기록)
 *       + 플레이어 패널(PlayerStrip, 최고 기록).
 * 흐름: 소개 → "○○ 차례 · 1번째 도전" → 제기를 던져 올림 → 떨어지는 제기 아래를 터치해 차기 … → 놓치면 제기가 땅에 떨어짐
 *       → "N개!" → 다음 사람 (1회차 한 바퀴 → 2회차 한 바퀴) → 최고 기록 순위.
 * 터치: 마당의 가로 자리로 발이 옮겨 가서 찬다. 제기가 차기 시작선(점선) 위에 있을 땐 발만 옮긴다.
 * 제기 움직임·판정은 게임 시계(timers.now()) 로 계산 (규칙 시간이라 속도 배율 없음), 연출 시간만 t() 적용.
 * 프레임 루프(postOnAnimation)는 제기가 날거나 연출 중일 때만 돈다. 일시정지하면 루프·게임 시계가 멈춰 제기가 그 자리에 선다.
 */
class JegiActivity : PartyGameActivity(), YardView.TapListener {

    override val module = JegiModule

    lateinit var game: JegiGame
        private set
    lateinit var yard: YardView
        private set
    lateinit var panel: JegiPanel
        private set
    lateinit var strip: PlayerStrip
        private set

    private var started = false
    private var ended = false
    /** 놓친 뒤 땅에 떨어지는 처리를 했는지 */
    private var landedDone = true
    private var deadlineTask: GameTimers.Task? = null

    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    /** 마지막으로 띄운 큰 배너 글자 (테스트용) */
    var lastBanner = ""
        private set
    /** 기념 배너(10·20·50개) 띄운 수 (테스트용) */
    var milestoneCount = 0
        private set
    /** 마지막 판정 글자 (테스트용) */
    var lastJudge = ""
        private set
    /** 마친 도전 수 (테스트용) */
    var attemptsDone = 0
        private set

    private val pa = PointF()
    private val xy = FloatArray(2)

    /** 지금 터치하면 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && game.phase == Phase.FLYING

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val random = randomOverride ?: Random.Default
        game = JegiGame(n, random.nextLong(), random.nextInt(n))

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        yard = YardView(this)
        yard.game = game
        yard.listener = this
        val frameBox = FrameLayout(this)
        frameBox.background = Theme.card(this, 0xFF0E1534.toInt(), 16f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 2f)
        frameBox.setPadding(inset, inset, inset, inset)
        frameBox.clipToOutline = true
        frameBox.addView(yard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        row.addView(frameBox, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        panel = JegiPanel(this)
        side.addView(panel, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true)
        val tileH = when (n) { 2 -> 48f; 3 -> 42f; else -> 36f }
        val stripH = Theme.dp(this, tileH * n + 5f * (n - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@JegiActivity, 6f) })
        row.addView(side, LinearLayout.LayoutParams(Theme.dp(this, SIDE_DP), Theme.MATCH).apply { leftMargin = Theme.dp(this@JegiActivity, 8f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, stripText(i))
        val p = game.current
        strip.setCurrent(p)
        showTurnInfo(p, animate = false)
        panel.setHint(HINT_WAIT)
        yard.now = timers.now()
        yard.setKidColor(playerColor(p))
        yard.resetKid(GameConfig.FOOT_START)
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        yard.now = timers.now()
        say("제기차기", "떨어지는 제기 아래를 톡! 2번 도전 중 최고 기록으로", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn()
        }
    }

    /** 차례 시작: 패널 금빛 · 차례 판 · 아이 색 → 배너 → 제기 던지기 */
    private fun startTurn() {
        if (ended) return
        val p = game.current
        yard.now = timers.now()
        strip.setCurrent(p)
        showTurnInfo(p, animate = true)
        panel.setHint(HINT_WAIT)
        yard.setKidColor(playerColor(p))
        yard.resetKid(GameConfig.FOOT_START)
        yard.setCount(0, 0)
        yard.jegiInHand = true
        yard.jegiShown = true
        yard.bandShown = false
        yard.invalidate()
        val best = game.best(p)
        val sub = (game.attempt + 1).toString() + "번째 도전" + if (best >= 0) " · 지금 최고 " + best + "개" else ""
        say(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS + GameConfig.TOSS_DELAY_MS)) { toss() }
    }

    private fun showTurnInfo(p: Int, animate: Boolean) {
        panel.setAttempt(game.attempt + 1, game.attempts)
        panel.setPlayer(players[p].name, playerColor(p), animate)
        panel.setCount("0")
        panel.setWindow(JegiGame.windowFor(0))
        panel.setRecord(recordText(p))
    }

    /** 제기를 던져 올림 → 차기 시작 */
    private fun toss() {
        if (ended) return
        val now = timers.now()
        if (!game.startAttempt(now)) return
        landedDone = false
        yard.now = now
        yard.jegiInHand = false
        yard.bandShown = true
        yard.kick(t(GameConfig.KICK_ANIM_MS))
        panel.setHint(HINT_PLAY)
        panel.setWindow(game.window())
        beeper.step()
        scheduleDeadline()
        ensureLoop()
    }

    /** 이번 비행의 판정 구간이 끝나는 순간에 늦음 확인 (프레임 루프가 없어도 확실히) */
    private fun scheduleDeadline() {
        deadlineTask?.cancel()
        val wait = game.flight.idealTime + game.window() + 1 - timers.now()
        deadlineTask = timers.after(wait.coerceAtLeast(0L)) { checkLate() }
    }

    private fun checkLate() {
        if (game.update(timers.now())) attemptEnded()
    }

    /** 마당 터치 → 발이 그 자리로 가서 참 */
    override fun onYardTap(worldX: Float) {
        if (!acceptingInput) return
        val now = timers.now()
        yard.now = now
        val r = game.tap(worldX, now)
        if (r == Tap.IGNORED) return
        if (r != Tap.LATE) yard.slideTo(game.footX, t(GameConfig.FOOT_SLIDE_MS))
        when (r) {
            Tap.MOVED -> Unit
            Tap.KICK -> kicked()
            Tap.EARLY, Tap.OUT_OF_REACH -> {
                yard.kick(t(GameConfig.KICK_ANIM_MS))
                attemptEnded()
            }
            Tap.LATE -> attemptEnded()
            Tap.IGNORED -> Unit
        }
        ensureLoop()
    }

    /** 성공: 개수 · 효과 · 기념 배너 */
    private fun kicked() {
        val n = game.count
        val p = game.current
        yard.kick(t(GameConfig.KICK_ANIM_MS))
        yard.setCount(n, t(GameConfig.COUNT_POP_MS))
        panel.setCount(if (n in NUMS.indices) NUMS[n] else n.toString())
        panel.setWindow(game.window())
        beeper.dice()
        yard.jegiScreen(xy)
        pa.set(xy[0], xy[1])
        toFx(yard, pa)
        fx.stars(pa.x, pa.y, 5, t(STAR_MS))
        if (n in GameConfig.MILESTONES) {
            milestoneCount++
            beeper.good()
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 12, t(GameConfig.MILESTONE_BANNER_MS))
            // 마당을 가리지 않게 오른쪽 판 위에 띄움
            pa.set(panel.width / 2f, panel.height * 0.42f)
            toFx(panel, pa)
            lastBanner = MILESTONE[n] ?: ""
            fx.banner(pa.x, pa.y, panel.width * 1.05f, lastBanner, players[p].name + " 대단해요!", BannerTone.GOOD, t(GameConfig.MILESTONE_BANNER_MS))
        }
        if (game.phase == Phase.ENDED) attemptEnded() else scheduleDeadline()
    }

    /** 도전 끝 (놓침·완벽): 판정 글자 → 제기가 땅에 떨어질 때까지 기다림 */
    private fun attemptEnded() {
        if (game.phase != Phase.ENDED) return
        deadlineTask?.cancel()
        deadlineTask = null
        val now = timers.now()
        yard.now = now
        yard.bandShown = false
        val reason = game.endReason
        val text = when (reason) {
            EndReason.EARLY -> JUDGE_EARLY
            EndReason.LATE -> JUDGE_LATE
            EndReason.REACH -> JUDGE_REACH
            EndReason.PERFECT -> JUDGE_PERFECT
            EndReason.NONE -> ""
        }
        lastJudge = text
        yard.judge(text, if (reason == EndReason.PERFECT) Theme.GOLD else Theme.RED, t(GameConfig.JUDGE_TEXT_MS))
        if (reason == EndReason.PERFECT) {
            yard.setMood(happy = true, unhappy = false)
            beeper.good()
        } else {
            yard.setMood(happy = false, unhappy = true)
            beeper.bad()
        }
        panel.setHint(if (reason == EndReason.PERFECT) HINT_PERFECT else HINT_MISS)
        ensureLoop()
        timers.after((game.landTime - now).coerceAtLeast(0L)) { landed() }
    }

    /** 제기가 땅에 닿음 → 기록 → 배너 → 다음 차례 */
    private fun landed() {
        if (landedDone) return
        landedDone = true
        val now = timers.now()
        yard.now = now
        yard.invalidate()
        yard.jegiScreen(xy)
        pa.set(xy[0], xy[1])
        toFx(yard, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
        beeper.step()
        val p = game.current
        val a = game.attempt
        val n = game.count
        val prevBest = game.best(p)
        game.endAttempt()
        attemptsDone++
        strip.setValue(p, stripText(p))
        panel.setRecord(recordText(p))
        if (n > prevBest && n > 0) strip.pulse(p)
        timers.after(t(GameConfig.LANDED_PAUSE_MS)) {
            val sub = players[p].name + " · " + (a + 1) + "번째 도전" + if (prevBest in 0 until n) " · 최고 기록!" else ""
            say(n.toString() + "개!", sub, if (n > 0) BannerTone.GOOD else BannerTone.BAD, GameConfig.RESULT_BANNER_MS)
            timers.after(t(GameConfig.RESULT_BANNER_MS)) {
                if (game.over) finishGame() else startTurn()
            }
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        yard.bandShown = false
        yard.jegiShown = false
        strip.setCurrent(-1)
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val w = winners[0]
        val title = if (winners.size == 1) players[w].name + " 우승!" else "공동 우승!"
        val sub = "최고 " + game.best(w) + "개" + if (winners.size > 1) " · " + winners.joinToString(" · ") { players[it].name } else ""
        panel.setPlayer(if (winners.size == 1) players[w].name else title, if (winners.size == 1) playerColor(w) else Theme.GOLD, animate = true)
        panel.setCount(game.best(w).toString())
        panel.setRecord(recordText(w))
        panel.setHint(HINT_END)
        yard.now = timers.now()
        yard.setKidColor(if (winners.size == 1) playerColor(w) else Theme.GOLD)
        yard.resetKid(GameConfig.FOOT_START)
        yard.setMood(happy = true, unhappy = false)
        yard.setCount(game.best(w), t(GameConfig.COUNT_POP_MS))
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        say(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        ensureLoop()
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 글자 ──

    /** 패널 값: "최고 7개" / "도전 전" */
    private fun stripText(p: Int): String {
        val b = game.best(p)
        return if (b < 0) "도전 전" else "최고 " + b + "개"
    }

    /** "1차 7개 · 2차 –" */
    private fun recordText(p: Int): String {
        val sb = StringBuilder()
        for (a in 0 until game.attempts) {
            if (a > 0) sb.append(" · ")
            sb.append(a + 1).append("차 ")
            val v = game.records[p][a]
            if (v < 0) sb.append("–") else sb.append(v).append("개")
        }
        return sb.toString()
    }

    /** 큰 배너 (마지막 글자를 기억 — 테스트 확인용) */
    private fun say(text: String, sub: String, tone: BannerTone, ms: Long) {
        lastBanner = text
        banner(text, sub, tone, ms)
    }

    // ── 프레임 루프 (제기가 날거나 연출 중일 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        yard.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::yard.isInitialized) yard.removeCallbacks(frame)
    }

    /** 제기가 아직 화면에서 움직이는지 (날거나 땅에 떨어지는 중) */
    private fun jegiMoving(): Boolean =
        game.phase == Phase.FLYING || (game.phase == Phase.ENDED && !landedDone)

    private fun onFrame() {
        if (!loopOn) return
        frames++
        val now = timers.now()
        yard.now = now
        if (game.phase == Phase.FLYING && game.update(now)) attemptEnded()
        yard.invalidate()
        if ((jegiMoving() || yard.busy()) && loopOn) yard.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::yard.isInitialized) return
        yard.now = timers.now()
        if (jegiMoving() || yard.busy()) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        deadlineTask?.cancel()
        if (::yard.isInitialized) yard.release()
    }

    companion object {
        /** 테스트용 난수 (첫 차례 · 제기 seed, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 214f
        private const val RIPPLE_MS = 420L
        private const val STAR_MS = 520L
        const val JUDGE_EARLY = "너무 빨라요!"
        const val JUDGE_LATE = "놓쳤어요!"
        const val JUDGE_REACH = "발이 안 닿아요!"
        const val JUDGE_PERFECT = "완벽!"
        private const val HINT_WAIT = "곧 제기를 던져요"
        const val HINT_PLAY = "제기 아래를 톡! 띠에 올 때 차기"
        private const val HINT_MISS = "앗, 떨어뜨렸어요"
        private val HINT_PERFECT = GameConfig.MAX_KICKS.toString() + "개 완벽!"
        private const val HINT_END = "경기 끝!"
        private val NUMS = Array(GameConfig.MAX_KICKS + 1) { it.toString() }
        private val MILESTONE: Map<Int, String> = GameConfig.MILESTONES.associateWith { it.toString() + "개 돌파!" }
    }
}
