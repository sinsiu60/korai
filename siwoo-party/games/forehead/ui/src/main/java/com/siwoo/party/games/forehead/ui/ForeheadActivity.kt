package com.siwoo.party.games.forehead.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.forehead.ForeheadGame
import com.siwoo.party.games.forehead.ForeheadModule
import com.siwoo.party.games.forehead.ForeheadWords
import com.siwoo.party.games.forehead.GameConfig
import com.siwoo.party.games.forehead.TiltAction
import com.siwoo.party.games.forehead.TiltDetector
import kotlin.random.Random

/**
 * 이마 퀴즈 화면 (3~4인, 차례대로).
 * 흐름: 카테고리·바퀴 수 고르기 → [차례마다] 안내 화면(이름·조작법) → [준비 완료] → 폰을 이마에 대고 가로로 들면(센서)
 *   또는 바로(터치) 3초 카운트 → 60초 동안 큰 제시어 (아래로 기울임/왼쪽 터치 = 정답 초록, 위로/오른쪽 = 패스 주황)
 *   → 시간 끝 → 맞힌/패스한 단어 공개 → 다음 사람 … → 합계 순위로 허브에.
 * 가속도 센서는 준비·카운트·플레이 중에만 등록 (일시정지·차례 사이·결과·종료 때 해제).
 * 60초는 규칙 시간 (배율 없음), 연출 시간은 t().
 */
class ForeheadActivity : PartyGameActivity() {

    /** 화면 단계 */
    enum class Step { SETUP, INTRO, READY, COUNT, PLAY, TIME_UP, REVIEW, END }

    override val module = ForeheadModule

    lateinit var game: ForeheadGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var playView: PlayView
        private set
    lateinit var timerBar: TimerBarView
        private set
    /** 플레이 화면 층 (이마에 댄 동안 화면 전체) */
    lateinit var playLayer: FrameLayout
        private set
    private lateinit var main: FrameLayout
    private lateinit var headText: TextView

    var step = Step.SETUP
        private set
    /** 터치 조작 (설정 또는 센서 없음) */
    var touchMode = false
        private set
    /** 가속도 센서가 있는지 */
    var hasSensor = false
        private set

    // ── 테스트에서 누르는 버튼 ──
    var categoryButtons: List<GameButton> = emptyList()
        private set
    var lapButtons: List<GameButton> = emptyList()
        private set
    var controlButton: GameButton? = null
        private set
    var startButton: GameButton? = null
        private set
    var readyButton: GameButton? = null
        private set
    var nextButton: GameButton? = null
        private set
    var reviewView: ReviewView? = null
        private set

    private var setupPopup: PopupLayer.Popup? = null
    private var chosenCategory = GameConfig.MIXED
    private var chosenLaps = GameConfig.LAPS_DEFAULT
    /** 정답/패스 화면 중 (다음 단어 대기) */
    private var flashing = false
    private val tasks = ArrayList<GameTimers.Task>()

    // ── 가속도 센서 ──
    private var sensorManager: SensorManager? = null
    private var accel: Sensor? = null
    private var screenDisplay: Display? = null
    /** 지금 센서 리스너가 등록되어 있는지 */
    var sensorRegistered = false
        private set
    val detector = TiltDetector()

    /** 기울기 감지: 준비 화면에선 똑바로 든 시간 → 카운트, 플레이 중엔 정답/패스 */
    val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!sensorRegistered || e.values.size < 3 || gamePaused) return
            val now = timers.now()
            val action = detector.update(e.values[0], e.values[1], e.values[2], displayRotation, now)
            when (step) {
                Step.READY -> {
                    val held = detector.uprightMs(now)
                    playView.setReadyProgress(held.toFloat() / GameConfig.READY_HOLD_MS)
                    if (held >= GameConfig.READY_HOLD_MS) startCount()
                }
                Step.PLAY -> if (action != null) onAnswer(action == TiltAction.CORRECT)
                else -> Unit
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** 지금 화면 회전 (Surface.ROTATION_*) — 가로 90°/270° 에 따라 기울기 부호가 다르다 */
    val displayRotation: Int get() = screenDisplay?.rotation ?: 0

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = ForeheadGame(players.size, ForeheadStore.words(this), randomOverride ?: Random.Default)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        hasSensor = accel != null
        @Suppress("DEPRECATION")
        screenDisplay = windowManager.defaultDisplay
        touchMode = ForeheadStore.touch(this) || !hasSensor
        chosenCategory = ForeheadStore.category(this)
        chosenLaps = ForeheadStore.laps(this)
        root.background = Theme.Party.background()

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 10f)
        col.setPadding(pad, Theme.dp(this, 8f), pad, Theme.dp(this, 8f))
        val top = Theme.horizontal(this)
        headText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.Party.card(this@ForeheadActivity, Theme.Party.NEON_PINK, 14f)
        }
        top.addView(headText, Theme.lp(Theme.dp(this, 112f), Theme.MATCH))
        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        for (i in players.indices) strip.setValue(i, "0점")
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@ForeheadActivity, 8f) })
        col.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))
        main = FrameLayout(this)
        col.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@ForeheadActivity, 8f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 플레이 층: 위 시간 막대 + 화면 가득 단어
        playLayer = FrameLayout(this).apply {
            setBackgroundColor(Theme.Party.BG_DEEP)
            visibility = View.GONE
            isClickable = true
        }
        val playCol = Theme.vertical(this)
        timerBar = TimerBarView(this)
        playCol.addView(timerBar, Theme.lp(Theme.MATCH, Theme.dp(this, 34f)).apply {
            val m = Theme.dp(this@ForeheadActivity, 6f)
            setMargins(m * 2, m, m * 2, 0)
        })
        playView = PlayView(this)
        playView.setPopMs(t(POP_MS))
        playView.onTap = PlayView.TapListener { left -> onPlayTap(left) }
        playCol.addView(playView, Theme.lp(Theme.MATCH, 0, 1f))
        playLayer.addView(playCol, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.addView(playLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        updateHeader()
        stage.post { showSetup() }
    }

    private fun name(i: Int) = players[i].name

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun updateHeader() {
        headText.text = if (game.turnIndex < 0) "이마\n퀴즈"
        else "${game.lap}바퀴 / ${game.laps}\n${game.turnIndex + 1} / ${game.totalTurns}번째"
    }

    private fun clearMain() {
        readyButton = null
        nextButton = null
        reviewView = null
        main.removeAllViews()
    }

    // ───────────── 카테고리·판 수 고르기 ─────────────

    private fun showSetup() {
        val card = Ui.darkCard(this, padDp = 16f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@ForeheadActivity)
        }
        card.addView(Theme.text(this, "이마 퀴즈 · 카테고리 선택", 21f, Theme.GOLD, bold = true))
        val cats = ArrayList<GameButton>()
        val grid = Theme.vertical(this)
        val names = ForeheadWords.CATEGORIES + "모두 섞기"
        var row: LinearLayout? = null
        for ((k, n) in names.withIndex()) {
            if (k % 4 == 0) {
                row = Theme.horizontal(this)
                grid.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)).apply { if (k > 0) topMargin = Theme.dp(this@ForeheadActivity, 6f) })
            }
            val cat = if (k < GameConfig.CATEGORY_COUNT) k else GameConfig.MIXED
            val b = Ui.button(this, n, Theme.GRAY, 16f) { b -> chosenCategory = cat; b.bounce(); refreshSetup() }
            cats.add(b)
            row!!.addView(b, Theme.lp(0, Theme.MATCH, 1f).apply { if (k % 4 > 0) leftMargin = Theme.dp(this@ForeheadActivity, 6f) })
        }
        // 마지막 줄 빈칸 채우기 (버튼 폭 맞춤)
        repeat((4 - names.size % 4) % 4) { row!!.addView(View(this), Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@ForeheadActivity, 6f) }) }
        categoryButtons = cats
        card.addView(grid, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@ForeheadActivity, 10f) })

        val opts = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        opts.addView(Theme.text(this, "판 수", 15f, Theme.TEXT, bold = true))
        val laps = ArrayList<GameButton>()
        for (n in GameConfig.LAPS_MIN..GameConfig.LAPS_MAX) {
            val b = Ui.button(this, "${n}바퀴", Theme.GRAY, 15f) { b -> chosenLaps = n; b.bounce(); refreshSetup() }
            laps.add(b)
            opts.addView(b, Theme.lp(Theme.dp(this, 76f), Theme.dp(this, 40f)).apply { leftMargin = Theme.dp(this@ForeheadActivity, 8f) })
        }
        lapButtons = laps
        opts.addView(Theme.text(this, "조작", 15f, Theme.TEXT, bold = true), Theme.lp(Theme.WRAP, Theme.WRAP).apply { leftMargin = Theme.dp(this@ForeheadActivity, 20f) })
        val ctl = Ui.button(this, "", Theme.GRAY, 15f) { b ->
            if (!hasSensor) { Ui.shake(b); return@button }
            touchMode = !touchMode
            ForeheadStore.setTouch(this, touchMode)
            b.bounce()
            refreshSetup()
        }
        controlButton = ctl
        opts.addView(ctl, Theme.lp(0, Theme.dp(this, 40f), 1f).apply { leftMargin = Theme.dp(this@ForeheadActivity, 8f) })
        card.addView(opts, Ui.gap(this, 14f))

        val go = Ui.button(this, "시작!", Theme.Party.NEON_PINK, 20f) { onStartGame() }
        startButton = go
        card.addView(go, LinearLayout.LayoutParams(Theme.dp(this, 220f), Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@ForeheadActivity, 14f) })
        refreshSetup()
        setupPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 580f))
        beeper.turn()
    }

    private fun refreshSetup() {
        for ((k, b) in categoryButtons.withIndex()) {
            val cat = if (k < GameConfig.CATEGORY_COUNT) k else GameConfig.MIXED
            b.color = if (cat == chosenCategory) ForeheadArt.categoryColor(cat) else Theme.GRAY
        }
        for ((k, b) in lapButtons.withIndex()) b.color = if (GameConfig.LAPS_MIN + k == chosenLaps) Theme.GOLD_DARK else Theme.GRAY
        controlButton?.let {
            it.text = when {
                !hasSensor -> "터치 (센서 없음)"
                touchMode -> "터치: 왼쪽 정답 · 오른쪽 패스"
                else -> "기울이기: 아래 정답 · 위 패스"
            }
            it.color = if (touchMode) ForeheadArt.PASS_DARK else Theme.BLUE
        }
    }

    private fun onStartGame() {
        if (step != Step.SETUP || gamePaused) return
        ForeheadStore.setCategory(this, chosenCategory)
        ForeheadStore.setLaps(this, chosenLaps)
        game.setup(chosenCategory, chosenLaps)
        setupPopup?.dismiss()
        setupPopup = null
        categoryButtons = emptyList()
        lapButtons = emptyList()
        controlButton = null
        startButton = null
        playView.setCategory(game.categoryName, ForeheadArt.categoryColor(game.category))
        playView.touchHints = touchMode
        showIntro()
    }

    // ───────────── 차례 안내 ─────────────

    private fun showIntro() {
        cancelTasks()
        step = Step.INTRO
        updateSensor()
        clearMain()
        updateHeader()
        playLayer.visibility = View.GONE
        playView.clearWord()
        val p = game.current
        strip.setCurrent(p)
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val art = IntroArtView(this)
        art.shirt = playerColor(p)
        art.touchMode = touchMode
        row.addView(art, Theme.lp(0, Theme.MATCH, 1f))
        val right = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@ForeheadActivity, playerColor(p))
            val pd = Theme.dp(this@ForeheadActivity, 14f)
            setPadding(pd, pd, pd, pd)
        }
        right.addView(Theme.text(this, "${name(p)}님 차례", 28f, Theme.shade(playerColor(p), 1.3f), bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
        right.addView(Theme.text(this, "카테고리: ${game.categoryName}", 15f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }, Ui.wrapGap(this, 4f))
        val how = if (touchMode)
            "폰을 이마에 대고 화면이 바깥을 보게 가로로 드세요.\n맞히면 화면 왼쪽, 모르면 오른쪽을 터치!"
        else
            "폰을 이마에 대고 화면이 바깥을 보게 가로로 드세요.\n맞히면 아래로, 모르면 위로 기울여요!"
        right.addView(Theme.text(this, how + "\n다른 사람들은 말로 설명 (제시어는 말하면 안 돼요)", 13f, Theme.Party.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, Ui.gap(this, 10f))
        val go = Ui.button(this, "준비 완료!", Theme.GREEN_DARK, 20f) { onReady() }
        right.addView(go, Theme.lp(Theme.MATCH, Theme.dp(this, 54f)).apply { topMargin = Theme.dp(this@ForeheadActivity, 12f) })
        readyButton = go
        row.addView(right, Theme.lp(0, Theme.WRAP, 1.25f).apply { leftMargin = Theme.dp(this@ForeheadActivity, 12f) })
        main.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        strip.pulse(p)
        beeper.turn()
    }

    // ───────────── 이마에 대기 · 카운트 ─────────────

    private fun onReady() {
        if (step != Step.INTRO || gamePaused) return
        step = Step.READY
        playView.setCounts(0, 0)
        playLayer.visibility = View.VISIBLE
        timerBar.stop()
        timerBar.setFraction(1f, GameConfig.TURN_MS)
        if (touchMode) {
            playView.showReady("이마에 대세요!", "3초 뒤에 시작해요")
            startCount()
        } else {
            playView.showReady("이마에 대세요!", "화면이 바깥을 보게 가로로 똑바로 들면 시작 (화면을 눌러도 시작)")
            detector.reset()
            updateSensor()
        }
    }

    private fun startCount() {
        if (step != Step.READY) return
        step = Step.COUNT
        updateSensor()
        for (k in 0 until GameConfig.COUNT_STEPS) {
            val n = GameConfig.COUNT_STEPS - k
            schedule(GameConfig.COUNT_STEP_MS * k) { playView.showCount(n); beeper.step() }
        }
        schedule(GameConfig.COUNT_STEP_MS * GameConfig.COUNT_STEPS) { beginPlay() }
    }

    // ───────────── 60초 플레이 ─────────────

    private fun beginPlay() {
        if (step != Step.COUNT) return
        step = Step.PLAY
        flashing = false
        val w = game.beginPlay()
        playView.showWord(w)
        playView.setCounts(0, 0)
        beeper.turn()
        timerBar.start(GameConfig.TURN_MS)
        // 시간 끝은 게임 타이머로 (일시정지 때 같이 멈춤, 규칙 시간이라 배율 없음)
        schedule(GameConfig.TURN_MS) { onTimeUp() }
        updateSensor()
    }

    private fun onPlayTap(left: Boolean) {
        if (gamePaused) return
        when (step) {
            Step.READY -> startCount()
            Step.PLAY -> if (touchMode) onAnswer(left)
            else -> Unit
        }
    }

    /** 정답(true)/패스(false) → 초록/주황 화면 → 다음 단어 */
    fun onAnswer(correct: Boolean) {
        if (step != Step.PLAY || flashing || gamePaused) return
        val answered = game.word
        val next = game.answer(correct)
        flashing = true
        playView.flash(correct, answered)
        playView.setCounts(game.correctCount, game.passCount)
        if (correct) beeper.good() else beeper.dice()
        schedule(t(GameConfig.FLASH_MS)) {
            if (step != Step.PLAY) return@schedule
            flashing = false
            playView.showWord(next)
        }
    }

    private fun onTimeUp() {
        if (step != Step.PLAY) return
        cancelTasks()
        step = Step.TIME_UP
        updateSensor()
        timerBar.stop()
        val n = game.timeUp(currentShown = !flashing)
        flashing = false
        playView.showTimeUp()
        beeper.bad()
        shakeScreen(Theme.dpf(this, 6f), t(SHAKE_MS))
        strip.setValue(game.current, "${game.scores[game.current]}점")
        schedule(t(GameConfig.TIME_UP_MS)) { showReview(n) }
    }

    // ───────────── 단어 공개 ─────────────

    private fun showReview(n: Int) {
        step = Step.REVIEW
        updateSensor()
        playLayer.visibility = View.GONE
        playView.clearWord()
        clearMain()
        val p = game.current
        val row = Theme.horizontal(this)
        val left = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@ForeheadActivity, playerColor(p))
            val pd = Theme.dp(this@ForeheadActivity, 12f)
            setPadding(pd, pd, pd, pd)
        }
        left.addView(Theme.text(this, name(p), 20f, Theme.shade(playerColor(p), 1.3f), bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        left.addView(Theme.text(this, "맞힌 개수", 13f, Theme.Party.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.wrapGap(this, 6f))
        left.addView(Theme.text(this, "$n", 54f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        left.addView(Theme.text(this, "패스 ${game.passCount}개\n합계 ${game.scores[p]}점", 14f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        })
        val last = game.isLastTurn
        val nextName = if (last) "" else name((game.turnIndex + 1) % players.size)
        val nb = Ui.button(this, if (last) "최종 결과" else "다음: $nextName", if (last) Theme.Party.NEON_PINK else Theme.GREEN_DARK, 16f) { onNext() }
        left.addView(nb, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@ForeheadActivity, 12f) })
        nextButton = nb
        row.addView(left, Theme.lp(Theme.dp(this, 168f), Theme.MATCH))
        val rv = ReviewView(this)
        rv.setResults(game.turnResults, t(GameConfig.REVIEW_CHIP_MS))
        row.addView(rv, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@ForeheadActivity, 12f) })
        reviewView = rv
        main.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        strip.pulse(p)
        banner(if (n > 0) "${n}개 정답!" else "아쉬워요!", "맞힌 단어(초록) · 패스(주황)", if (n > 0) BannerTone.GOOD else BannerTone.INFO, GameConfig.REVIEW_BANNER_MS)
    }

    private fun onNext() {
        if (step != Step.REVIEW || gamePaused) return
        if (game.nextTurn()) showIntro() else finishGame()
    }

    private fun finishGame() {
        cancelTasks()
        step = Step.END
        updateSensor()
        strip.setCurrent(-1)
        nextButton?.isEnabled = false
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }.joinToString(", ") { name(it) }
        val best = game.scores.maxOrNull() ?: 0
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_MS))
        banner("$winners 1등!", "${best}개 정답", BannerTone.GOOD, GameConfig.END_MS)
        schedule(t(GameConfig.END_MS)) { finishWithRanks(ranks) }
    }

    // ───────────── 센서 ─────────────

    /** 준비·카운트·플레이 중(센서 조작, 일시정지 아님)에만 등록, 나머지는 해제 */
    private fun updateSensor() {
        val sm = sensorManager ?: return
        val want = !touchMode && !gamePaused && (step == Step.READY || step == Step.COUNT || step == Step.PLAY)
        if (want && !sensorRegistered) {
            val s = accel ?: return
            sm.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_GAME)
            sensorRegistered = true
        } else if (!want && sensorRegistered) {
            sm.unregisterListener(sensorListener)
            sensorRegistered = false
        }
    }

    private fun releaseSensor() {
        if (sensorRegistered) sensorManager?.unregisterListener(sensorListener)
        sensorRegistered = false
    }

    override fun onGamePause() {
        releaseSensor()
        playView.pauseAnim()
        // 준비 화면에서 들고 있던 시간은 처음부터
        if (step == Step.READY) { detector.reset(); playView.setReadyProgress(0f) }
    }

    override fun onGameResume() {
        playView.resumeAnim()
        updateSensor()
    }

    override fun onGameDestroy() {
        releaseSensor()
        cancelTasks()
    }

    companion object {
        /** 테스트용 난수 주입 */
        var randomOverride: Random? = null
        /** 글자 튀어나오기 (연출) */
        const val POP_MS = 220L
        const val SHAKE_MS = 350L
    }
}
