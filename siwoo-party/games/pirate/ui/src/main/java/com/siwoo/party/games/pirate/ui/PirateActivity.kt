package com.siwoo.party.games.pirate.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.pirate.GameConfig
import com.siwoo.party.games.pirate.Pick
import com.siwoo.party.games.pirate.PirateGame
import com.siwoo.party.games.pirate.PirateModule
import kotlin.random.Random

/**
 * 해적 룰렛 화면 (차례대로).
 * 왼쪽: 차례 카드(지금 차례 이름 크게) + 플레이어 패널, 오른쪽: 나무통.
 * 흐름: "N판" 배너 → 차례인 사람이 빈 구멍 터치 → 칼이 천천히 꽂힘(0.8초) → 정적(0.5초)
 *  → 안전: 칼은 그대로, 다음 사람 / 함정: 해적이 날아가고 그 사람 탈락 → 통 비우고 다음 판(탈락자 다음 사람부터)
 *  → 한 명 남으면 1위, 나머지는 늦게 탈락한 순으로 허브에 돌려준다.
 * 규칙 판정은 PirateGame, 여기서는 연출 순서만 timers 로 예약한다.
 */
class PirateActivity : PartyGameActivity(), BarrelView.Listener {

    override val module = PirateModule

    lateinit var game: PirateGame
        private set
    lateinit var barrel: BarrelView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turn: TurnView
        private set

    /** 칼 연출·정적·판 바뀜 중 (구멍 터치 무시) */
    var busy = true
        private set
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = PirateGame(players.size, PirateSettings.traps(this), randomOverride ?: Random.Default)
        val pad = Theme.dp(this, 10f)
        val row = Theme.horizontal(this).apply {
            gravity = android.view.Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
            clipChildren = false // 날아가는 해적이 화면 위 가장자리까지 보이도록
            clipToPadding = false
        }
        val left = Theme.vertical(this)
        turn = TurnView(this)
        left.addView(turn, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true)
        // 칸 높이를 일정하게 (2인이면 패널이 짧고 차례 카드가 커진다)
        val tile = Theme.dp(this, 52f)
        val gap = Theme.dp(this, 6f)
        left.addView(strip, Theme.lp(Theme.MATCH, tile * players.size + gap * (players.size - 1)).apply { topMargin = Theme.dp(this@PirateActivity, 8f) })
        row.addView(left, LinearLayout.LayoutParams(Theme.dp(this, 168f), LinearLayout.LayoutParams.MATCH_PARENT))
        barrel = BarrelView(this)
        barrel.listener = this
        row.addView(barrel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = pad })
        stage.clipChildren = false
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        turn.set("해적 룰렛", "준비", Theme.GOLD, "")
        stage.post { startRound() }
    }

    private fun roundHead(): String = "${game.round}판 · 함정 ${game.roundTraps}개"

    /** 새 판: 통 비우기 → "N판" 배너 → 첫 차례 */
    private fun startRound() {
        busy = true
        val s = game.startRound()
        barrel.clearSwords()
        strip.setCurrent(s)
        turn.set(roundHead(), players[s].name, playerColor(s), "${players[s].name}부터 시작!")
        val alive = game.aliveCount
        banner("${game.round}판", "해적 ${alive}명 · 함정 ${game.roundTraps}개 · ${players[s].name}부터", ms = GameConfig.ROUND_INTRO_MS)
        timers.after(t(GameConfig.ROUND_INTRO_MS)) { beginTurn() }
    }

    private fun beginTurn() {
        val p = game.current
        strip.setCurrent(p)
        turn.set(roundHead(), players[p].name, playerColor(p), "빈 구멍을 눌러\n칼을 꽂으세요")
        busy = false
    }

    override fun onHolePicked(hole: Int) {
        if (busy || gamePaused || !game.canPick(hole)) return
        val p = game.current
        val r = game.pick(hole)
        if (r === Pick.Ignored) return
        busy = true
        barrel.stab(hole, playerColor(p), t(GameConfig.SWORD_INSERT_MS))
        turn.setHint("두근두근...")
        beeper.step()
        timers.after(t(GameConfig.SWORD_INSERT_MS)) { barrel.suspense(t(GameConfig.SUSPENSE_MS)) }
        timers.after(t(GameConfig.SWORD_INSERT_MS) + t(GameConfig.SUSPENSE_MS)) { reveal(r) }
    }

    private fun reveal(r: Pick) {
        when (r) {
            is Pick.Safe -> {
                barrel.relax()
                beeper.good()
                barrel.holeCenter(r.hole, pa)
                toFx(barrel, pa)
                fx.ripple(pa.x, pa.y, playerColor(r.player), t(500))
                turn.setHint("휴~ 안전!")
                timers.after(t(GameConfig.SAFE_NEXT_MS)) { beginTurn() }
            }
            is Pick.Trap -> eliminate(r)
            Pick.Ignored -> busy = false
        }
    }

    private fun eliminate(r: Pick.Trap) {
        val p = r.player
        val traps = BooleanArray(GameConfig.HOLES) { game.isTrap(it) }
        barrel.headCenter(pa)
        toFx(barrel, pa)
        fx.stars(pa.x, pa.y, 12, t(900))
        barrel.fly(r.hole, traps, t(GameConfig.PIRATE_FLY_MS))
        // 하늘 멀리 사라지는 순간 반짝
        timers.after(t(GameConfig.PIRATE_FLY_MS)) {
            barrel.flyEnd(pa)
            toFx(barrel, pa)
            fx.stars(pa.x, pa.y, 8, t(700))
        }
        shakeScreen(Theme.dpf(this, 14f), t(GameConfig.SHAKE_MS))
        strip.setOut(p, true)
        strip.setValue(p, "${game.finalRanks()[p]}위")
        turn.set(roundHead(), players[p].name, playerColor(p), "함정! 탈락")
        val sub = if (r.gameOver) "" else "남은 해적 ${game.aliveCount}명 · 통을 비우고 다음 판"
        banner("${players[p].name} 탈락!", sub, BannerTone.BAD, GameConfig.PIRATE_FLY_MS + GameConfig.ELIMINATED_SHOW_MS)
        val wait = t(GameConfig.PIRATE_FLY_MS) + t(GameConfig.ELIMINATED_SHOW_MS)
        if (r.gameOver) {
            timers.after(wait) { finishGame() }
        } else {
            timers.after(wait) {
                barrel.clearSwords()
                barrel.drop(t(GameConfig.PIRATE_DROP_MS))
                timers.after(t(GameConfig.PIRATE_DROP_MS)) { startRound() }
            }
        }
    }

    private fun finishGame() {
        val w = game.winner
        strip.setCurrent(w)
        strip.setValue(w, "1위")
        strip.pulse(w)
        turn.set("최후의 생존자", players[w].name, playerColor(w), "1위!")
        barrel.clearSwords()
        barrel.drop(t(GameConfig.PIRATE_DROP_MS))
        banner("${players[w].name} 생존!", "최후의 1인 · 1위", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        barrel.pauseAnims()
    }

    override fun onGameResume() {
        barrel.resumeAnims()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
