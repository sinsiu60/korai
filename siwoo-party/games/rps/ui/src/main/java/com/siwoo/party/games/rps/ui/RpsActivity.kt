package com.siwoo.party.games.rps.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.rps.Foul
import com.siwoo.party.games.rps.GameConfig
import com.siwoo.party.games.rps.Phase
import com.siwoo.party.games.rps.Press
import com.siwoo.party.games.rps.Replay
import com.siwoo.party.games.rps.RpsGame
import com.siwoo.party.games.rps.RpsModule
import com.siwoo.party.games.rps.ThrowResult

/**
 * 가위바위보 토너먼트 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView(구역·터치), 위: RpsView(구역마다 같은 버튼 배치를 그 사람 쪽으로 회전).
 * 한 판: 준비 → "가위…" → "바위…" (이때 누르면 이른 반칙) → "보!" 부터 0.6초 동안만 버튼 인정
 *   → 모든 구역 카드가 동시에 뒤집히며 손 공개 → 판정(탈락 도장 / 무승부 다시) → 최후의 1인까지.
 * 구호 간격·0.6초 창은 게임 규칙이라 속도 배율을 곱하지 않는다. 시간은 timers.now() (일시정지 제외).
 * 판 도중 일시정지되면 돌아와서 그 판을 처음부터 다시 한다 (리듬이 깨져 불공정하므로).
 */
class RpsActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = RpsModule

    lateinit var game: RpsGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: RpsView
        private set
    /** 이번 판에 예약한 일 (일시정지·판정 때 한꺼번에 취소) */
    private val throwTasks = ArrayList<GameTimers.Task>()
    private var restartOnResume = false
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = RpsGame(players.size)
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        board = RpsView(this, zones)
        board.setPlayers(Array(players.size) { players[it].name }, IntArray(players.size) { playerColor(it) })
        board.stampMs = t(GameConfig.STAMP_MS).coerceAtLeast(1)
        board.ready(game.alive)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun schedule(ms: Long, block: () -> Unit) {
        throwTasks.add(timers.after(ms, block))
    }

    private fun cancelThrow() {
        for (t in throwTasks) t.cancel()
        throwTasks.clear()
    }

    private fun intro() {
        say("가위바위보 토너먼트", "\"보!\" 하는 순간부터 ${GameConfig.WINDOW_MS / 100 / 10.0}초 안에 버튼을!", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { nextThrow() }
    }

    // ── 한 판 ──

    private fun nextThrow() {
        cancelThrow()
        zones.touchEnabled = false
        board.ready(game.alive)
        schedule(t(GameConfig.READY_MS)) { chantStart() }
    }

    /** "가위…" (여기부터 누르면 이른 반칙) → "바위…" → "보!" */
    private fun chantStart() {
        game.startThrow()
        zones.touchEnabled = true
        board.chant(WORD_1)
        beeper.step()
        schedule(GameConfig.CHANT_STEP_MS) {
            board.chant(WORD_2)
            beeper.step()
            schedule(GameConfig.CHANT_STEP_MS) { go() }
        }
    }

    private fun go() {
        val now = timers.now()
        game.open(now)
        board.open(WORD_3, now, GameConfig.WINDOW_MS)
        beeper.good()
        ensureLoop()
        schedule(GameConfig.WINDOW_MS) { judge() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        val k = board.hit(zone, x, y)
        if (k < 0) return
        when (game.press(zone, RpsView.HANDS[k], timers.now())) {
            Press.ACCEPTED -> {
                board.picked(zone)
                beeper.step()
                pa.set(x, y)
                toFx(zones, pa)
                fx.ripple(pa.x, pa.y, playerColor(zone), t(RIPPLE_MS))
            }
            Press.FOUL_EARLY -> {
                board.earlyFoul(zone)
                beeper.bad()
            }
            Press.IGNORED -> return
        }
        if (game.allDecided) judge()
    }

    /** 창이 닫힘(또는 모두 결정) → 동시에 뒤집어 공개 → 판정 */
    private fun judge() {
        if (game.phase != Phase.CHANT && game.phase != Phase.OPEN) return
        cancelThrow()
        zones.touchEnabled = false
        val res = game.resolve()
        board.reveal(game.hand, game.foul, timers.now(), t(GameConfig.FLIP_MS))
        beeper.dice()
        ensureLoop()
        schedule(t(GameConfig.FLIP_MS) + t(GameConfig.JUDGE_HOLD_MS)) { showResult(res) }
    }

    private fun showResult(res: ThrowResult) {
        val replay = res.replay
        if (replay != null) {
            for (p in players.indices) if (game.alive[p]) board.setStatus(p, "무승부", Theme.TEXT_SUB)
            val title = when (replay) {
                Replay.ALL_SAME -> "모두 같은 손!"
                Replay.ALL_THREE -> "세 가지가 다 나왔어요!"
                Replay.ALL_FOUL -> "모두 반칙!"
            }
            say(title, "비겼어요 — 다시 한 판!", BannerTone.INFO, GameConfig.RESULT_SHOW_MS)
            schedule(t(GameConfig.RESULT_SHOW_MS)) { nextThrow() }
            return
        }
        val now = timers.now()
        for (p in res.eliminated) {
            board.eliminate(p, "${res.rank}위", now)
            board.setStatus(p, if (game.foul[p] != Foul.NONE) "반칙 패배" else "패배", Theme.RED)
        }
        for (p in players.indices) if (game.alive[p]) board.setStatus(p, "통과!", Theme.GREEN)
        ensureLoop()
        shakeScreen(3f * resources.displayMetrics.density, t(240))
        val out = res.eliminated.joinToString("·") { players[it].name }
        if (game.isOver) {
            val w = game.winner
            board.setWinner(w)
            board.setStatus(w, "우승!", Theme.GOLD)
            zones.setWinner(w, true)
            zoneCenter(w)
            fx.stars(pa.x, pa.y, 12, t(1000))
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
            say("${players[w].name} 우승!", "최후의 1인! ($out 탈락)", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
            schedule(t(GameConfig.FINAL_SHOW_MS)) { finishWithRanks(game.ranks()) }
        } else {
            say("$out 탈락!", "남은 사람 ${game.aliveCount}명 — 다음 판!", BannerTone.BAD, GameConfig.RESULT_SHOW_MS)
            schedule(t(GameConfig.RESULT_SHOW_MS)) { nextThrow() }
        }
    }

    /**
     * 큰 문구. 2인은 카드가 화면 가운데에 있으므로 위쪽 빈 곳에 띄운다 (3·4인은 가운데 = 구역 사이).
     */
    private fun say(text: String, sub: String, tone: BannerTone = BannerTone.INFO, ms: Long) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (players.size != 2 || w <= 0f) {
            banner(text, sub, tone, ms)
            return
        }
        fx.banner(w / 2f, h * 0.13f, w * 0.8f, text, sub, tone, t(ms))
        when (tone) {
            BannerTone.GOOD -> beeper.good()
            BannerTone.BAD -> beeper.bad()
            BannerTone.INFO -> beeper.turn()
        }
    }

    private fun zoneCenter(i: Int) {
        val z = zones.zone(i) ?: return
        pa.set(z.centerX, z.centerY)
        toFx(zones, pa)
    }

    // ── 프레임 (막대·뒤집기·도장 중에만) ──

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
        if (board.tick(timers.now())) board.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        // 구호·"보!" 도중이면 돌아와서 그 판을 다시 (리듬·0.6초 창이 깨지므로)
        if (game.phase == Phase.CHANT || game.phase == Phase.OPEN) {
            restartOnResume = true
            zones.touchEnabled = false
        }
    }

    override fun onGameResume() {
        if (restartOnResume) {
            restartOnResume = false
            cancelThrow()
            game.abortThrow()
            say("다시 갑니다!", "멈춘 판은 처음부터 다시 해요", BannerTone.INFO, GameConfig.RESULT_SHOW_MS)
            nextThrow()
            return
        }
        if (board.busy) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 도는지 */
    val looping: Boolean get() = loopOn

    companion object {
        const val WORD_1 = "가위…"
        const val WORD_2 = "바위…"
        const val WORD_3 = "보!"
        private const val RIPPLE_MS = 450L
    }
}
