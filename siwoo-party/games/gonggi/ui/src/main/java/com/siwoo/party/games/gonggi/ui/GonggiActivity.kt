package com.siwoo.party.games.gonggi.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.gonggi.GameConfig
import com.siwoo.party.games.gonggi.GonggiGame
import com.siwoo.party.games.gonggi.GonggiModule
import com.siwoo.party.games.gonggi.Miss
import com.siwoo.party.games.gonggi.Phase
import com.siwoo.party.games.gonggi.StoneState
import kotlin.random.Random

/**
 * 공기놀이 화면 (2~4인, 차례대로).
 * 위: 차례 표시(StageHeaderView — 목표·이름·안내·단계 칸) / 왼쪽: 플레이어 패널(점수·단계) / 가운데: 돗자리(MatView).
 * 흐름: 소개 → [차례 배너 → 1단~4단: (손 터치로 던지기 → 공중에 있는 동안 바닥 돌 집기 → 내려올 때 손 터치로 받기) …
 *       → 꺾기: 던지기 → 손등 받기(정확도만큼 얹힘) → 튕기기 → 낚아채기 = 점수] — 실패하거나 꺾기가 끝나면 다음 사람.
 * 목표 점수(게임 설정 10/20/30, 기본 20)에 먼저 닿은 순서대로 순위. 남은 사람이 1명이면(또는 최대 바퀴) 끝.
 * 공중 시간·받기 구간은 게임 시계(timers.now(), 일시정지 시간 제외)로 판정 → 일시정지하면 돌이 공중에서 그대로 멈춘다.
 * 프레임 루프(postOnAnimation)는 돌이 공중에 있거나 떨어지는 동안에만 돈다.
 */
class GonggiActivity : PartyGameActivity(), MatView.Listener {

    override val module = GonggiModule

    lateinit var game: GonggiGame
        private set
    lateinit var mat: MatView
        private set
    lateinit var header: StageHeaderView
        private set
    lateinit var strip: PlayerStrip
        private set

    private lateinit var colors: IntArray
    private var started = false
    private var ended = false
    /** 배너·결과 연출 중 (터치 막음) */
    private var busy = true

    // ── 프레임 루프 ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 그린 프레임 수 (테스트용) */
    var frames = 0L
        private set
    private val pa = PointF()
    private val pb = PointF()

    /** 터치를 받는 중 (던지기·집기·받기) */
    val accepting: Boolean
        get() = started && !ended && !gamePaused && !busy && TAPPABLE[game.phase.ordinal]

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val target = targetOverride ?: GonggiSettings.target(this)
        game = GonggiGame(n, target, randomOverride ?: Random.Default)
        colors = IntArray(n) { playerColor(it) }

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, Theme.dp(this, 8f))
        header = StageHeaderView(this)
        col.addView(header, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)))
        val row = Theme.horizontal(this)
        strip = PlayerStrip(this, players, true)
        mat = MatView(this)
        mat.bind(game, colors)
        mat.listener = this
        row.addView(strip, Theme.lp(Theme.dp(this, STRIP_DP), Theme.MATCH))
        row.addView(mat, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@GonggiActivity, 6f) })
        col.addView(row, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@GonggiActivity, 6f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        header.setGoal(target)
        header.setTurn(players[0].name + " 먼저", colors[0])
        header.setHint("던지고 → 바닥 돌 집고 → 받기!")
        header.setStage(0, 0)
        updateStrip()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner("공기놀이 · 목표 " + game.target + "점", "던지고 → 집고 → 받기! 꺾기로 점수", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn()
        }
    }

    /** 차례 시작: 패널 금빛 + 이름 + 배너 → 단계 준비 */
    private fun startTurn() {
        if (ended) return
        val p = game.current
        busy = true
        mat.clearDrop()
        strip.setCurrent(p)
        header.setTurn(players[p].name + " 차례", colors[p])
        header.setStage(game.stage[p], 0)
        header.setHint(GameConfig.STAGE_NAMES[game.stage[p]] + "부터 시작해요")
        mat.now = timers.now()
        mat.invalidate()
        val sub = if (game.turns[p] == 0) STAGE_DESC[0] else GameConfig.STAGE_NAMES[game.stage[p]] + "부터 · " + STAGE_DESC[game.stage[p]]
        banner(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) {
            if (game.startTurn()) {
                busy = false
                refreshHint()
                mat.now = timers.now()
                mat.invalidate()
            }
        }
    }

    override fun onMatTap(stone: Int) {
        if (!accepting) return
        val now = timers.now()
        mat.now = now
        val before = game.phase
        val mask = heldMask()
        val h = mat.airHeight(now)
        val pickedBefore = game.picked
        if (!game.tap(now, stone)) return
        val after = game.phase
        when {
            after == Phase.TURN_END && game.miss != Miss.NONE -> onFail(mask, h)
            after == Phase.TURN_END -> onKkScored()
            after == Phase.CAUGHT -> onCaught()
            after == Phase.KK_BACK -> onBack()
            before == Phase.AIR && game.picked > pickedBefore -> {
                beeper.step()
                mat.stonePoint(stone, pa)
                toFx(mat, pa)
                fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
                refreshHint()
            }
            else -> {
                // 던지기 (READY → AIR, KK_READY → KK_AIR, KK_BACK → KK_FLIP)
                beeper.step()
                refreshHint()
                ensureLoop()
            }
        }
        mat.invalidate()
    }

    /** 손에 있는(떨어질 수 있는) 돌 비트 */
    private fun heldMask(): Int {
        var m = 0
        for (i in game.stones.indices) {
            val s = game.stones[i].state
            if (s == StoneState.HAND || s == StoneState.PICKED || s == StoneState.BACK) m = m or (1 shl i)
        }
        return m
    }

    private fun onCaught() {
        beeper.good()
        mat.handPoint(pa)
        toFx(mat, pa)
        fx.stars(pa.x, pa.y - mat.handR, 6, t(STAR_MS))
        header.setHint(if (game.subStep + 1 < game.stepsInStage) HINT_CAUGHT else GameConfig.STAGE_NAMES[game.curStage] + " 통과!")
        busy = true
        timers.after(t(GameConfig.CAUGHT_MS)) { afterCaught() }
    }

    private fun afterCaught() {
        if (ended) return
        val newStage = game.advance()
        header.setStage(game.curStage, game.subStep)
        mat.now = timers.now()
        mat.invalidate()
        if (newStage) {
            val s = game.curStage
            val p = game.current
            strip.setValue(p, valueText(p))
            strip.pulse(p)
            banner(STAGE_TITLE[s], STAGE_DESC[s], BannerTone.GOOD, GameConfig.STAGE_BANNER_MS)
            header.setHint(STAGE_DESC[s])
            timers.after(t(GameConfig.STAGE_BANNER_MS)) {
                busy = false
                refreshHint()
            }
        } else {
            busy = false
            refreshHint()
        }
    }

    private fun onBack() {
        beeper.good()
        mat.handPoint(pa)
        toFx(mat, pa)
        fx.stars(pa.x, pa.y - mat.handR, game.backCount + 1, t(STAR_MS))
        refreshHint()
    }

    private fun onKkScored() {
        busy = true
        val p = game.current
        val pts = game.lastPoints
        beeper.good()
        updateStrip()
        strip.pulse(p)
        mat.handPoint(pa)
        toFx(mat, pa)
        strip.anchor(p, pb)
        toFx(strip.tiles[p], pb)
        fx.coins(pa.x, pa.y, pb.x, pb.y, pts, t(GameConfig.KK_RESULT_MS) * 2 / 3)
        val sub = players[p].name + " 합계 " + game.scores[p] + "점"
        banner("꺾기 " + pts + "점!", sub, BannerTone.GOOD, GameConfig.KK_RESULT_MS, sound = false)
        header.setHint("꺾기 " + pts + "점! 다음 차례는 1단부터")
        header.setStage(game.stage[p], 0)
        timers.after(t(GameConfig.KK_RESULT_MS)) {
            if (game.justFinished) {
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.FINISH_BANNER_MS))
                banner(players[p].name + " 완주! " + game.place[p] + "등", "목표 " + game.target + "점 달성", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
                timers.after(t(GameConfig.FINISH_BANNER_MS)) { nextTurn() }
            } else {
                nextTurn()
            }
        }
    }

    /** 실패: 돌이 떨어지고 차례 넘김 (다음엔 그 단계 처음부터) */
    private fun onFail(mask: Int, h: Float) {
        busy = true
        val p = game.current
        val now = timers.now()
        mat.now = now
        mat.startDrop(now, mask, h, t(GameConfig.DROP_MS))
        ensureLoop()
        shakeScreen(Theme.dpf(this, 5f), t(SHAKE_MS))
        updateStrip()
        val stageName = GameConfig.STAGE_NAMES[game.stage[p]]
        banner(MISS_TITLE[game.miss.ordinal], MISS_SUB[game.miss.ordinal] + " · 다음엔 " + stageName + "부터", BannerTone.BAD, GameConfig.FAIL_MS)
        header.setHint(MISS_SUB[game.miss.ordinal] + " — 다음 차례에 " + stageName + "부터")
        header.setStage(game.stage[p], 0)
        timers.after(t(GameConfig.FAIL_MS)) { nextTurn() }
    }

    private fun nextTurn() {
        if (ended) return
        if (game.nextTurn()) startTurn() else finishGame()
    }

    private fun finishGame() {
        ended = true
        busy = true
        strip.setCurrent(-1)
        updateStrip()
        val ranks = game.ranks()
        var winners = 0
        var who = -1
        for (i in ranks.indices) if (ranks[i] == 1) { winners++; who = i }
        val text = if (winners > 1) "공동 우승!" else players[who].name + " 우승!"
        val sub = if (game.finishedCount > 0) "목표 " + game.target + "점 먼저 달성" else GameConfig.MAX_ROUNDS.toString() + "바퀴 끝 · 점수 순"
        header.setTurn(text, if (winners > 1) Theme.GOLD else colors[who])
        header.setHint(sub)
        header.setStage(-1, 0)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun valueText(i: Int): String =
        if (game.place[i] > 0) "완주 " + game.place[i] + "등 · " + game.scores[i] + "점"
        else game.scores[i].toString() + "점 · " + GameConfig.STAGE_NAMES[game.stage[i]]

    private fun updateStrip() {
        for (i in players.indices) strip.setValue(i, valueText(i))
    }

    private fun refreshHint() {
        val g = game
        header.setStage(g.curStage, g.subStep)
        val text = when (g.phase) {
            Phase.READY -> "손을 톡! 던지고 바닥 돌 " + g.required + "개 집기"
            Phase.AIR -> if (g.picked < g.required) "돌 " + (g.required - g.picked) + "개 더 집고 → 내려올 때 손 터치!" else "이제 내려오는 돌을 손으로 받아요!"
            Phase.KK_READY -> "꺾기! 손을 톡 — 5개를 던져 손등으로 받아요"
            Phase.KK_AIR -> "떨어질 때 터치! 정확할수록 많이 얹혀요"
            Phase.KK_BACK -> "손등에 " + g.backCount + "개! 톡 튕겨 올려요"
            Phase.KK_FLIP -> "내려올 때 터치해 낚아채요!"
            else -> return
        }
        header.setHint(text)
    }

    // ── 프레임 루프 ──

    private fun needsLoop(now: Long): Boolean = game.airborne || mat.animating(now)

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        mat.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::mat.isInitialized) mat.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        val now = timers.now()
        mat.now = now
        if (game.airborne) {
            val mask = heldMask()
            val h = mat.airHeight(now)
            if (game.update(now)) onFail(mask, h)
        }
        mat.invalidate()
        if (loopOn && needsLoop(now)) mat.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::mat.isInitialized) return
        val now = timers.now()
        mat.now = now
        if (needsLoop(now)) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    companion object {
        /** 테스트용: 돌 흩기 난수 / 목표 점수 */
        var randomOverride: Random? = null
        var targetOverride: Int? = null

        private const val STRIP_DP = 124f
        private const val RIPPLE_MS = 380L
        private const val STAR_MS = 700L
        private const val SHAKE_MS = 260L
        private const val HINT_CAUGHT = "잘 받았어요! 다음 돌"
        private val TAPPABLE = BooleanArray(Phase.values().size).also {
            for (ph in arrayOf(Phase.READY, Phase.AIR, Phase.KK_READY, Phase.KK_AIR, Phase.KK_BACK, Phase.KK_FLIP)) it[ph.ordinal] = true
        }
        private val STAGE_TITLE = arrayOf("1단!", "2단!", "3단!", "4단!", "꺾기!")
        private val STAGE_DESC = arrayOf(
            "하나씩 집기 × 4번",
            "둘씩 집기 × 2번",
            "셋 집고 → 하나 집기",
            "넷을 한꺼번에!",
            "던져서 손등으로 받고 낚아채기",
        )
        /** Miss 순서대로 */
        private val MISS_TITLE = arrayOf("", "너무 많이!", "덜 집었어요", "너무 일찍!", "놓쳤다!", "와르르…", "너무 일찍!", "놓쳤다!")
        private val MISS_SUB = arrayOf(
            "",
            "필요한 것보다 더 집었어요",
            "돌을 다 못 집고 받았어요",
            "돌이 내려오기 전에 손을 쥐었어요",
            "돌이 바닥에 떨어졌어요",
            "손등에 하나도 못 얹었어요",
            "낚아채기가 너무 빨랐어요",
            "튕긴 돌을 못 잡았어요",
        )
    }
}
