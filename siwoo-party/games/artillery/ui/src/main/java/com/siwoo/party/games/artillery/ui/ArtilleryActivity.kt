package com.siwoo.party.games.artillery.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.artillery.ArtilleryGame
import com.siwoo.party.games.artillery.ArtilleryModule
import com.siwoo.party.games.artillery.EndReason
import com.siwoo.party.games.artillery.FireResult
import com.siwoo.party.games.artillery.GameConfig
import com.siwoo.party.games.artillery.ImpactKind
import com.siwoo.party.games.artillery.Phase
import kotlin.random.Random

/**
 * 포탄 대결 화면 (2~4인, 차례대로).
 * 위: 전장(BattleView — 하늘·언덕·대포·안내판), 아래: 플레이어 패널(PlayerStrip, 체력).
 * 흐름: 소개 → 차례(새 바람) → 전장을 눌러 뒤로 끌었다 놓아 쏘기 → 포탄이 날아감 → 떨어지면 폭발·피해
 *       → 탈락 배너 → 다음 살아 있는 사람 → 한 명 남으면(또는 최대 발사 수) 끝.
 * 탄도는 ArtilleryGame(고정 1/60초 단계). 프레임 루프(postOnAnimation)는 포탄이 날아가거나 폭발하는 동안에만 돌고,
 * 폭발이 끝나거나 일시정지하면 멈춘다 (계속하기 → 하던 중이면 다시 돈다).
 */
class ArtilleryActivity : PartyGameActivity(), BattleView.Listener {

    override val module = ArtilleryModule

    lateinit var game: ArtilleryGame
        private set
    lateinit var battle: BattleView
        private set
    lateinit var strip: PlayerStrip
        private set

    private var started = false
    private var ended = false
    /** 판정 연출 중 (터치 막음) */
    private var busy = false
    /** 폭발이 끝나면 다음 진행을 해야 하는지 */
    private var awaitAfterExplosion = false

    /** 프레임 루프 (날아가는 중·폭발 중에만) */
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private val pt = PointF()

    /** 마지막으로 띄운 큰 배너 글자 (테스트 확인용) */
    var lastBanner = ""
        private set
    /** 탈락 순서 (테스트 확인용) */
    val eliminationLog = ArrayList<Int>()

    /** 지금 쏠 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !busy && !gamePaused && game.canFire

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        val n = players.size
        game = ArtilleryGame.create(n, random.nextInt(n), random.nextInt(), random)

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 6f)
        col.setPadding(pad, pad, pad, pad)
        battle = BattleView(this)
        battle.bind(game, IntArray(n) { playerColor(it) }, Array(n) { players[it].name })
        battle.listener = this
        col.addView(battle, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = false, icon = StripIcon.NONE)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@ArtilleryActivity, 6f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until n) strip.setValue(i, HP_TEXT[GameConfig.START_HP])
        strip.setCurrent(game.current)
        battle.setHint(HINT_WAIT)
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        say("포탄 대결!", "바람을 읽고 상대 대포를 맞혀요", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            startTurn()
        }
    }

    /** 차례 시작: 패널 금빛·안내판·배너 */
    private fun startTurn() {
        val p = game.current
        val c = game.cannons[p]
        strip.setCurrent(p)
        battle.setTrail(false)
        battle.setTurn(players[p].name + " 차례", playerColor(p), game.wind, c.lastAngle, c.lastPower, game.shots, game.maxShots)
        battle.setHint(HINT_AIM)
        busy = false
        say(players[p].name + " 차례", windSub(game.wind), BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        beeper.turn()
    }

    private fun windSub(w: Int): String = when {
        w == 0 -> "바람 잔잔"
        w > 0 -> "바람 → 오른쪽 " + w
        else -> "바람 ← 왼쪽 " + (-w)
    }

    override fun canAim(): Boolean = acceptingInput

    override fun onFire(angle: Int, power: Int) {
        if (!acceptingInput) return
        when (game.fire(angle, power)) {
            FireResult.FIRED -> {
                busy = true
                beeper.dice()
                battle.setTrail(true)
                battle.setHint(HINT_FLY)
                ensureLoop()
            }
            FireResult.TOO_WEAK -> battle.setHint(HINT_SHORT)
            FireResult.NOT_NOW -> Unit
        }
    }

    override fun onAimCancel() {
        if (acceptingInput) battle.setHint(HINT_SHORT)
    }

    /** 포탄이 멈춘 순간 (프레임 루프 안) */
    private fun onLanded() {
        val r = game.report
        val imp = r.impact
        for (i in players.indices) strip.setValue(i, HP_TEXT[game.cannons[i].hp.coerceIn(0, GameConfig.START_HP)])
        if (imp.kind.explodes) {
            battle.explode(imp.x, imp.y, r.hpBefore, r.damage, t(GameConfig.EXPLODE_MS))
            awaitAfterExplosion = true
            shakeScreen(Theme.dpf(this, if (imp.kind == ImpactKind.CANNON) 9f else 5f), t(SHAKE_MS))
            val hitOther = players.indices.any { it != r.shooter && r.damage[it] > 0 }
            if (hitOther) beeper.good() else beeper.bad()
            battle.setHint(
                when {
                    imp.kind == ImpactKind.CANNON && imp.cannon != r.shooter -> HINT_DIRECT
                    hitOther -> HINT_HIT
                    r.damage[r.shooter] > 0 -> HINT_SELF
                    else -> HINT_MISS_GROUND
                },
            )
            for (i in players.indices) if (r.damage[i] > 0) strip.pulse(i)
        } else {
            beeper.bad()
            battle.setHint(HINT_OUT)
            timers.after(t(GameConfig.MISS_DELAY_MS)) { proceed() }
        }
    }

    /** 폭발이 끝난 뒤 (루프가 멈출 때) */
    private fun onExplosionDone() {
        if (!awaitAfterExplosion) return
        awaitAfterExplosion = false
        timers.after(t(GameConfig.AFTER_EXPLODE_MS)) { proceed() }
    }

    /** 판정 뒤: 탈락 배너 → 끝 또는 다음 차례 */
    private fun proceed() {
        val r = game.report
        var delay = 0L
        if (r.eliminatedCount > 0) {
            val out = players.indices.filter { r.eliminated[it] }
            for (i in out) {
                strip.setOut(i, true)
                strip.setValue(i, HP_TEXT[0])
                eliminationLog.add(i)
                cannonFx(i)
                fx.ripple(pt.x, pt.y, Theme.RED, t(RIPPLE_MS))
            }
            if (!r.gameOver) {
                val title = if (out.size == 1) players[out[0]].name + " 탈락!" else out.joinToString(" · ") { players[it].name } + " 탈락!"
                say(title, "남은 대포 " + game.aliveCount + "대", BannerTone.BAD, GameConfig.ELIM_BANNER_MS)
                delay = t(GameConfig.ELIM_BANNER_MS)
            }
        }
        if (r.gameOver) {
            finishGame()
            return
        }
        timers.after(delay) {
            if (game.nextTurn()) startTurn()
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = when (game.endReason) {
            EndReason.MAX_TURNS -> GameConfig.MAX_TURNS_PER_PLAYER.toString() + "발씩 끝 · 체력이 많은 사람이 이겨요"
            EndReason.ALL_DOWN -> winners.joinToString(" · ") { players[it].name } + " 함께 쓰러짐"
            else -> "마지막까지 남은 대포!"
        }
        strip.setCurrent(-1)
        battle.hideTurn()
        battle.setHint(HINT_END)
        if (winners.size == 1) {
            cannonFx(winners[0])
            fx.stars(pt.x, pt.y, 8, t(STARS_MS))
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        say(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    /** i 번 대포 자리 → 연출 층 좌표 (pt) */
    private fun cannonFx(i: Int) {
        val c = game.cannons[i]
        pt.set(battle.screenX(c.x), battle.screenY(c.y))
        toFx(battle, pt)
    }

    /** 큰 배너 (마지막 글자를 기억 — 테스트 확인용) */
    private fun say(text: String, sub: String, tone: BannerTone, ms: Long) {
        lastBanner = text
        banner(text, sub, tone, ms)
    }

    // ── 프레임 루프 (날아가는 중·폭발 중에만) ──

    private val needsLoop: Boolean get() = game.phase == Phase.FLYING || battle.exploding

    private fun ensureLoop() {
        if (gamePaused || loopOn || !needsLoop) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        battle.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::battle.isInitialized) battle.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, GameConfig.MAX_FRAME_MS)
        lastFrame = now
        frames++
        if (game.phase == Phase.FLYING) {
            game.advance(dt)
            if (game.phase != Phase.FLYING) onLanded()
        }
        battle.tick(dt)
        if (needsLoop && loopOn) {
            battle.postOnAnimation(frame)
        } else {
            loopOn = false
            onExplosionDone()
        }
    }

    override fun onGamePause() {
        stopLoop()
        if (::battle.isInitialized) battle.cancelAim()
    }

    override fun onGameResume() {
        ensureLoop()
        if (::battle.isInitialized) battle.invalidate()
    }

    override fun onGameDestroy() {
        stopLoop()
        if (::battle.isInitialized) battle.release()
    }

    companion object {
        /** 테스트용 난수 (먼저 할 사람·지형 시드·바람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SHAKE_MS = 350L
        private const val RIPPLE_MS = 500L
        private const val STARS_MS = 900L
        private val HP_TEXT = Array(GameConfig.START_HP + 1) { "체력 $it" }
        private const val HINT_WAIT = "곧 시작해요"
        const val HINT_AIM = "아무 곳이나 눌러 뒤로 끌었다 놓기"
        private const val HINT_SHORT = "더 길게 끌어요!"
        private const val HINT_FLY = "슝~ 날아가는 중…"
        private const val HINT_DIRECT = "직격! 최대 피해"
        private const val HINT_HIT = "명중!"
        private const val HINT_SELF = "앗, 내 대포가 다쳤어요"
        private const val HINT_MISS_GROUND = "빗나감! 땅에 떨어졌어요"
        const val HINT_OUT = "빗나감! 화면 밖으로"
        private const val HINT_END = "끝!"
    }
}
