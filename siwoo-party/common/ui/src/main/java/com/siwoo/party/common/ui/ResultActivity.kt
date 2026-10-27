package com.siwoo.party.common.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.ScoreRules
import com.siwoo.party.common.Session

/**
 * 공통 결과 화면.
 * 시상대(3위→2위→1위 블록이 솟고 이름, 1위에 왕관) → 얻은 점수가 플레이어 칩으로 날아가고 누적 점수가 올라간다.
 * [다시 하기] [꼴찌 벌칙 룰렛](설정 "게임 후 벌칙" 켬일 때) [허브로]
 * 점수는 허브가 이미 모임에 반영해 저장한 상태로 들어온다 (여기서는 보여 주기만).
 */
class ResultActivity : Activity() {

    private lateinit var settings: PartySettings
    private val beeper = Beeper()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    lateinit var popups: PopupLayer
        private set
    private lateinit var fx: FxView
    private lateinit var chipsBar: ChipsBar
    private lateinit var podium: PodiumView
    private var session: Session? = null
    private lateinit var ids: IntArray
    private lateinit var ranks: IntArray
    private lateinit var points: IntArray
    private var restIndex: List<Int> = emptyList()
    private var restView: View? = null
    private var groupOf = IntArray(0)
    private val pa = PointF()
    private val pb = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)
    /** 시상대·점수 연출이 끝났는지 (흐름 테스트용) */
    var finished = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = PartySettings(this)
        beeper.enabled = settings.sound
        Immersive.apply(window)
        session = SessionStore(this).load()
        ids = intent.getIntArrayExtra(Party.EXTRA_IDS) ?: IntArray(0)
        ranks = intent.getIntArrayExtra(Party.EXTRA_RANKS) ?: IntArray(0)
        val s = session
        if (s == null || ids.isEmpty() || ids.size != ranks.size || ids.any { s.player(it) == null }) {
            finish() // 모임이 없거나 결과가 이상하면 허브로
            return
        }
        points = ScoreRules.points(ranks)
        setContentView(build(s))
        Transitions.enter(this, root)
        if (savedInstanceState == null) root.post { play() } else showFinal()
    }

    private fun build(s: Session): View {
        root = FrameLayout(this)
        root.background = Theme.background()
        val col = Theme.vertical(this).apply {
            val p = Theme.dp(this@ResultActivity, 14f)
            setPadding(p, Theme.dp(this@ResultActivity, 8f), p, Theme.dp(this@ResultActivity, 10f))
        }
        val head = Theme.horizontal(this)
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: "게임"
        head.addView(Theme.text(this, "$gameName 결과", 22f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        chipsBar = ChipsBar(this)
        // 시작은 이번 판 점수를 빼기 전 점수
        val before = HashMap<Int, Int>()
        for (p in s.players) before[p.id] = s.score(p.id)
        for (k in ids.indices) before[ids[k]] = s.score(ids[k]) - points[k]
        val best = before.values.maxOrNull() ?: 0
        chipsBar.bind(s.players, { before[it] ?: 0 }, if (best > 0) before.filterValues { it == best }.keys else emptySet())
        head.addView(chipsBar)
        col.addView(head)

        val names = ids.map { s.player(it)!!.name }
        val colors = ids.map { Theme.playerColor(s.player(it)!!.colorIndex) }
        val subs = points.map { "+${it}점" }
        val (places, rest) = PodiumView.build(names, colors, ranks, subs)
        restIndex = rest
        val distinct = ranks.distinct().sorted()
        groupOf = IntArray(ids.size) { distinct.indexOf(ranks[it]) }
        podium = PodiumView(this)
        podium.setPlaces(places)
        col.addView(podium, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@ResultActivity, 6f) })
        if (rest.isNotEmpty()) {
            val t = Theme.text(this, rest.joinToString("   ") { "${ranks[it]}위 ${names[it]} ${subs[it]}" }, 15f, Theme.TEXT_SUB, bold = true)
            t.gravity = Gravity.CENTER
            restView = t
            col.addView(t, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@ResultActivity, 4f) })
        }

        val buttons = ArrayList<View>()
        buttons.add(Ui.button(this, "다시 하기", Theme.BLUE) { done(RESULT_REPLAY) })
        val losers = ScoreRules.lastPlace(ranks)
        if (settings.penaltyAfterGame && losers.isNotEmpty()) {
            val who = losers.joinToString("·") { names[it] } + "님"
            buttons.add(Ui.button(this, "꼴찌 벌칙 룰렛", Theme.RED_DARK) {
                if (!popups.isShowing) PenaltyRoulette.showReal(popups, settings, beeper, who, ::shake)
            })
        }
        buttons.add(Ui.button(this, "허브로", Theme.GREEN_DARK) { done(RESULT_OK) })
        val row = Ui.buttonRow(this, *buttons.toTypedArray(), heightDp = 50f)
        col.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@ResultActivity, 8f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun shake(amp: Float) {
        root.translationX = if (amp > 0f) (Math.random().toFloat() - 0.5f) * 2f * amp else 0f
        root.translationY = if (amp > 0f) (Math.random().toFloat() - 0.5f) * 2f * amp else 0f
    }

    private fun play() {
        podium.play(settings.t(PartyConfig.PODIUM_RISE_MS), settings.t(PartyConfig.CROWN_DROP_MS),
            onCrown = {
                podium.placeAnchor(0, pa)
                toFx(podium, pa)
                fx.stars(pa.x, pa.y - podium.height * 0.12f, 8, settings.t(900))
                beeper.good()
            },
            onDone = { flyPoints() },
        )
    }

    /** 얻은 점수가 시상대에서 각자의 칩으로 날아가고, 도착하면 누적 점수가 올라간다 */
    private fun flyPoints() {
        val s = session ?: return
        val fly = settings.t(PartyConfig.POINTS_FLY_MS)
        var delay = 0L
        for (k in ids.indices) {
            if (points[k] <= 0) continue
            val chip = chipsBar.chip(ids[k]) ?: continue
            val g = groupOf[k]
            if (g < 3) {
                podium.placeAnchor(g, pa); toFx(podium, pa)
            } else {
                val rv = restView ?: continue
                pa.set(rv.width / 2f, rv.height / 2f); toFx(rv, pa)
            }
            chip.scoreAnchor(pb); toFx(chip, pb)
            fx.card(pa.x, pa.y, pb.x, pb.y, "+${points[k]}", Theme.GOLD, delay, fly)
            val target = s.score(ids[k])
            ui.postDelayed({
                chip.animateScore(target, settings.t(PartyConfig.COUNT_UP_MS))
                beeper.step()
            }, delay + fly)
            delay += 150
        }
        ui.postDelayed({
            chipsBar.setLeaders(s.leaders().map { it.id }.toSet())
            finished = true
        }, delay + fly + settings.t(PartyConfig.COUNT_UP_MS))
    }

    private fun showFinal() {
        val s = session ?: return
        podium.showFinal()
        for (c in chipsBar.chips) c.setScoreNow(s.score(c.playerId))
        chipsBar.setLeaders(s.leaders().map { it.id }.toSet())
        finished = true
    }

    private fun toFx(v: View, p: PointF) {
        v.getLocationInWindow(locA)
        fx.getLocationInWindow(locB)
        p.x += (locA[0] - locB[0]).toFloat()
        p.y += (locA[1] - locB[1]).toFloat()
    }

    private fun done(code: Int) {
        setResult(code, Intent().putExtra(Party.EXTRA_GAME_ID, intent.getStringExtra(Party.EXTRA_GAME_ID)))
        Transitions.finish(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (::popups.isInitialized && popups.handleBack()) return
        done(RESULT_OK)
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        beeper.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME_NAME = "party.gameName"
        /** [다시 하기] */
        const val RESULT_REPLAY = RESULT_FIRST_USER + 1

        fun intent(context: Context, gameId: String, gameName: String, ids: IntArray, ranks: IntArray): Intent =
            Intent(context, ResultActivity::class.java)
                .putExtra(Party.EXTRA_GAME_ID, gameId)
                .putExtra(EXTRA_GAME_NAME, gameName)
                .putExtra(Party.EXTRA_IDS, ids)
                .putExtra(Party.EXTRA_RANKS, ranks)
    }
}
