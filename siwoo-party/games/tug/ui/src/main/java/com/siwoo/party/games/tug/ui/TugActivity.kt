package com.siwoo.party.games.tug.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.tug.GameConfig
import com.siwoo.party.games.tug.Phase
import com.siwoo.party.games.tug.Side
import com.siwoo.party.games.tug.Tap
import com.siwoo.party.games.tug.Teams
import com.siwoo.party.games.tug.TugGame
import com.siwoo.party.games.tug.TugModule
import kotlin.random.Random

/**
 * 연타 줄다리기 화면 (동시 터치, 2인 1 대 1 / 4인 2 대 2).
 * 아래: 공통 MultiTouchZoneView (같은 팀은 같은 쪽 구역), 위: TugView (밧줄·깃발·사람·시간).
 * 흐름: (4인) 팀 뽑기 연출 → 3·2·1 → 30초 연타 → 승리선/시간 종료 판정 → 끌려가며 넘어짐 → 순위.
 * 판정은 TugGame, 시간은 timers.now() (일시정지 시간 제외). 프레임 루프는 경기·연출 중에만 돈다.
 */
class TugActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = TugModule

    lateinit var game: TugGame
        private set
    lateinit var teams: Teams
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var tug: TugView
        private set
    var reveal: TeamRevealView? = null
        private set
    private lateinit var random: Random
    /** 프레임 루프 (postOnAnimation, 애니메이션 배율 설정과 무관하게 돈다) */
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private var shownSec = -1
    private var overtimeTask: GameTimers.Task? = null
    private var timeTask: GameTimers.Task? = null
    /** 팀 이름 (배너용): "가람" / "가람·라온" */
    private val teamName = arrayOf("", "")
    private val pa = PointF()
    private val pb = PointF()

    private val solo: Boolean get() = players.size == GameConfig.SOLO_PLAYERS

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        random = randomOverride ?: Random.Default
        teams = Teams.assign(players.size, random)
        game = TugGame(teams)
        for (s in 0..1) teamName[s] = teams.members(if (s == 0) Side.LEFT else Side.RIGHT).joinToString("·") { players[it].name }

        // 구역: 구역 번호 순서대로 플레이어를 넘긴다 (4인: 0·2 왼쪽 팀, 1·3 오른쪽 팀).
        // 2인은 구역 가운데로 밧줄이 지나가므로 이름은 TugView 가 아래쪽에 쓴다.
        val zoneList = teams.zonePlayer.map { p ->
            val info = players[p]
            if (solo) PlayerInfo(info.id, "", info.colorIndex) else info
        }
        zones = MultiTouchZoneView(this)
        zones.setPlayers(zoneList, if (solo) "" else HINT)
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        tug = TugView(this)
        tug.followMs = t(GameConfig.FLAG_FOLLOW_MS).coerceAtLeast(16).toFloat()
        tug.setup(
            IntArray(teams.left.size) { playerColor(teams.left[it]) },
            IntArray(teams.right.size) { playerColor(teams.right[it]) },
            if (solo) players[teams.left[0]].name else null,
            if (solo) players[teams.right[0]].name else null,
        )
        tug.setTime(GameConfig.TUG_TIME_SEC.toString(), false)
        if (solo) {
            tug.setSoloSub(0, HINT)
            tug.setSoloSub(1, HINT)
        }
        stage.addView(tug, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        if (solo) {
            stage.post { introSolo() }
        } else {
            // 팀이 공개되기 전에는 구역·판을 가린다
            zones.visibility = View.INVISIBLE
            tug.visibility = View.INVISIBLE
            val rv = TeamRevealView(this)
            rv.setPlayers(players, IntArray(players.size) { playerColor(it) })
            stage.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            reveal = rv
            stage.post { startReveal() }
        }
    }

    // ── 시작 연출 ──

    private fun introSolo() {
        banner("${teamName[0]} vs ${teamName[1]}", "자기 구역을 마구 연타하세요!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startMatch() } }
    }

    /** 4인 팀 뽑기: 이름표가 양쪽을 오가다 점점 느려지며 최종 팀에 멈춤 */
    private fun startReveal() {
        val rv = reveal ?: return
        val final = IntArray(players.size) { if (teams.sideOf(it) == Side.LEFT) 0 else 1 }
        val n = GameConfig.REVEAL_STEPS
        var at = 0L
        var prev = IntArray(players.size) { it % 2 }
        for (k in 0 until n) {
            val f = k.toFloat() / (n - 1)
            at += t((GameConfig.REVEAL_STEP_FIRST_MS + (GameConfig.REVEAL_STEP_LAST_MS - GameConfig.REVEAL_STEP_FIRST_MS) * f * f).toLong())
            val last = k == n - 1
            val arr = if (last) final else randomArrangement(prev)
            prev = arr
            timers.after(at) {
                rv.show(arr, last)
                if (last) {
                    beeper.good()
                    shakeScreen(3f * resources.displayMetrics.density, t(260))
                } else {
                    beeper.step()
                }
            }
        }
        timers.after(at + t(GameConfig.REVEAL_HOLD_MS)) { revealToZones() }
    }

    /** 두 명씩 나눈 무작위 배치 (바로 전과는 다르게) */
    private fun randomArrangement(prev: IntArray): IntArray {
        while (true) {
            val order = (0 until players.size).shuffled(random)
            val arr = IntArray(players.size)
            for (k in order.indices) arr[order[k]] = if (k < players.size / 2) 0 else 1
            if (!arr.contentEquals(prev)) return arr
        }
    }

    /** 이름표가 자기 구역으로 날아가고 판이 나타남 → 카운트다운 */
    private fun revealToZones() {
        val rv = reveal ?: return
        for (p in players.indices) {
            rv.chipCenter(p, pa)
            toFx(rv, pa)
            val z = zones.zone(teams.playerZone[p]) ?: continue
            pb.set(z.centerX, z.centerY)
            toFx(zones, pb)
            fx.card(pa.x, pa.y, pb.x, pb.y, players[p].name, playerColor(p), t(70L * p), t(GameConfig.CARD_FLIGHT_MS))
        }
        rv.visibility = View.GONE
        zones.visibility = View.VISIBLE
        tug.visibility = View.VISIBLE
        banner("${teamName[0]}  VS  ${teamName[1]}", "같은 팀은 같은 쪽 구역을 연타!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startMatch() } }
    }

    // ── 경기 ──

    private fun startMatch() {
        game.start(timers.now())
        zones.touchEnabled = true
        tug.playing = true
        shownSec = -1
        ensureLoop()
        timeTask = timers.after(GameConfig.TUG_TIME_MS) { onTimeUp() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        val p = teams.zonePlayer[zone]
        val r = game.tap(p, timers.now())
        if (r != Tap.COUNTED && r != Tap.WON) return
        val side = if (teams.sideOf(p) == Side.LEFT) 0 else 1
        pa.set(x, y)
        toFx(zones, pa)
        fx.ripple(pa.x, pa.y, playerColor(p), t(GameConfig.RIPPLE_MS))
        tug.kick(side)
        val count = "${game.taps[p]}회"
        if (solo) tug.setSoloSub(side, count) else zones.setSub(zone, count)
        if (r == Tap.WON) endMatch(true)
    }

    private fun onTimeUp() {
        if (game.phase != Phase.PLAYING) return
        if (game.timeUp(timers.now()) == Phase.OVERTIME) {
            banner("동점! 연장전", "${GameConfig.TUG_OVERTIME_MS / 1000}초 안에 앞서는 팀이 승리", BannerTone.BAD, 1400)
            shownSec = -1
            overtimeTask = timers.after(GameConfig.TUG_OVERTIME_MS) {
                game.overtimeUp()
                endMatch(false)
            }
        } else {
            endMatch(false)
        }
    }

    /** 승패 결정 → 끌려가며 넘어짐 + 배너 → 순위 */
    private fun endMatch(byLine: Boolean) {
        timeTask?.cancel()
        overtimeTask?.cancel()
        zones.touchEnabled = false
        val w = game.winner
        val side = when (w) { Side.LEFT -> 0; Side.RIGHT -> 1; null -> 2 }
        tug.setTime(if (byLine) "끝!" else "0", false)
        tug.finish(side, t(GameConfig.END_ANIM_MS))
        ensureLoop()
        beeper.good()
        if (w != null) {
            for (z in 0 until players.size) {
                if (teams.sideOf(teams.zonePlayer[z]) == w) zones.setWinner(z, true)
            }
        }
        // 밧줄이 끌려가고 진 쪽이 넘어지는 걸 먼저 보여 준 뒤 배너
        val bannerAt = t(GameConfig.END_ANIM_MS * 3 / 4)
        timers.after(bannerAt) {
            if (w == null) {
                resultBanner("무승부!", "연장전까지 똑같이 당겼어요 — 모두 1위")
            } else {
                val sub = if (byLine) "깃발이 승리선에 닿았어요!" else "시간 종료 — 깃발이 더 가까웠어요"
                resultBanner("${teamName[side]} 승리!", sub)
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
            }
        }
        timers.after(bannerAt + t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(game.ranks()) }
    }

    /** 결과 배너: 밧줄 위쪽에 띄워 넘어진 사람이 가려지지 않게 */
    private fun resultBanner(text: String, sub: String) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        fx.banner(w / 2f, h / 2f - 62f * resources.displayMetrics.density, w * 0.8f, text, sub, BannerTone.GOOD, t(GameConfig.RESULT_SHOW_MS))
        beeper.good()
    }

    // ── 프레임 (경기 중·연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        tug.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::tug.isInitialized) tug.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        val live = game.phase == Phase.PLAYING || game.phase == Phase.OVERTIME
        if (live) {
            tug.target = game.flagTarget
            val ms = game.remainingMs(timers.now())
            val sec = ((ms + 999) / 1000).toInt()
            if (sec != shownSec) {
                shownSec = sec
                val ot = game.phase == Phase.OVERTIME
                val hurry = ot || sec <= GameConfig.HURRY_SEC
                tug.setTime(if (ot) "연장 $sec" else sec.toString(), hurry)
                if (hurry && sec > 0) {
                    tug.pulseTime()
                    beeper.step()
                }
            }
        }
        val busy = tug.step(dt)
        if (!live && !busy) stopLoop() else tug.postOnAnimation(frame)
    }

    override fun onGamePause() {
        // 일시정지 동안 프레임을 멈춘다 (탭은 gamePaused 로 무시)
        stopLoop()
    }

    override fun onGameResume() {
        val live = game.phase == Phase.PLAYING || game.phase == Phase.OVERTIME
        if (live || (game.phase == Phase.DONE && !tug.ended)) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        private const val HINT = "연타!"

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
