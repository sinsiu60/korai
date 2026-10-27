package com.siwoo.party.games.crown.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.Zone
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.crown.CrownGame
import com.siwoo.party.games.crown.GameConfig
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 왕관 쟁탈 판 (MultiTouchZoneView 위에 겹치는 그림 층, 터치는 아래 구역으로 그대로 전달).
 * 구역마다 같은 배치를 구역 방향으로 돌려 그린다 (위쪽 구역 180°): 큰 둥근 버튼 + 이름·점수.
 * 화면 가운데: 남은 시간 고리 + 숫자, 왕관이 가운데 있으면 그 위에.
 * 왕관은 맨 위 층에 그리고, 주인이 바뀌면 포물선을 그리며 날아간다 (flightMs, 속도 배율 적용 값).
 * 움직임은 step(dtMs) 로 진행 (화면이 게임 중·연출 중에만 프레임을 돌린다). onDraw 는 할당 없음.
 */
class CrownView(context: Context) : View(context) {

    private val dp = Theme.dpf(context, 1f)
    private var zones: List<Zone> = emptyList()
    private var n = 0

    // ── 배치 (구역 가운데 원점, 그 사람 기준 좌표) ──
    private var cellW = 0f
    private var cellH = 0f
    /** 버튼 가운데·반지름 */
    var btnX = 0f
        private set
    var btnY = 0f
        private set
    var btnR = 0f
        private set
    private var nameX = 0f
    private var nameY = 0f
    private var scoreY = 0f
    private var ringR = 0f
    private var depth = 0f

    // ── 플레이어 ──
    private var names: Array<String> = emptyArray()
    private var colors = IntArray(0)
    private var baseColors = IntArray(0)
    private var scoreText: Array<String> = emptyArray()
    private var scores = IntArray(0)
    private var pressed = BooleanArray(0)
    /** 쿨다운 남은 비율 0..1 */
    private var cooldown = FloatArray(0)
    /** 쿨다운 중 누름 → 떨림 진행 0..1 (−1 = 없음) */
    private var shakeT = FloatArray(0)
    private var faces: Array<RadialGradient> = emptyArray()

    // ── 상태 ──
    /** 게임 중 (버튼 활성·금빛 숨쉬기) */
    var playing = false
        set(v) { field = v; invalidate() }
    /** 왕관이 있는(또는 날아가는 목적지) 곳: 플레이어 번호 또는 CrownGame.CENTER */
    var crownAt = CrownGame.CENTER
        private set
    private var timeFrac = 1f
    private var hurry = false
    private var secText = SEC[GameConfig.GAME_TIME_SEC]

    // ── 왕관 비행 ──
    private var flightT = 1f
    private var fromX = 0f
    private var fromY = 0f
    private var fromRot = 0f
    private var fromW = 0f
    /** 지금 그릴 왕관 (계산 결과) */
    private var curX = 0f
    private var curY = 0f
    private var curRot = 0f
    private var curW = 0f

    // ── 연출 시간 (속도 배율 적용된 값을 화면이 넣는다) ──
    var flightMs = GameConfig.CROWN_FLIGHT_MS.toFloat()
    var shakeMs = GameConfig.BLOCKED_SHAKE_MS.toFloat()
    var glowMs = GameConfig.GLOW_PERIOD_MS.toFloat()

    // ── 그리기 도구 ──
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = 0xFF0A0F2E.toInt()
    }
    private val rect = RectF()
    private val path = Path()
    private val m = Matrix()
    private val tmp = FloatArray(2)

    init {
        isClickable = false
        isFocusable = false
    }

    fun setup(playerNames: List<String>, playerColors: IntArray) {
        n = playerNames.size
        names = playerNames.toTypedArray()
        colors = playerColors
        baseColors = IntArray(n) { Theme.shade(colors[it], 0.45f) }
        scores = IntArray(n)
        scoreText = Array(n) { "0점" }
        pressed = BooleanArray(n)
        cooldown = FloatArray(n)
        shakeT = FloatArray(n) { -1f }
        // 반지름 1 짜리 입체 그라데이션 (그릴 때 행렬로 크기만 맞춘다)
        faces = Array(n) {
            val c = colors[it]
            RadialGradient(-0.3f, -0.4f, 1.5f, intArrayOf(Theme.shade(c, 1.5f), c, Theme.shade(c, 0.6f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        relayout()
    }

    fun setScore(i: Int, score: Int) {
        if (scores[i] == score) return
        scores[i] = score
        scoreText[i] = "${score}점"
        invalidate()
    }

    fun scoreShown(i: Int): Int = scores[i]

    fun setPressed(i: Int, v: Boolean) {
        if (pressed[i] == v) return
        pressed[i] = v
        invalidate()
    }

    fun setCooldown(i: Int, frac: Float) {
        if (cooldown[i] == frac) return
        cooldown[i] = frac
        invalidate()
    }

    fun cooldownShown(i: Int): Float = cooldown[i]

    /** 쿨다운 중 눌러서 막힘 → 버튼 부르르 */
    fun blocked(i: Int) {
        shakeT[i] = 0f
        invalidate()
    }

    /** 남은 시간 (frac 0..1, 남은 초, 서두름) */
    fun setTime(frac: Float, secLeft: Int, hurrying: Boolean) {
        val s = SEC[secLeft.coerceIn(0, GameConfig.GAME_TIME_SEC)]
        if (frac == timeFrac && hurrying == hurry && s === secText) return
        timeFrac = frac
        hurry = hurrying
        secText = s
        invalidate()
    }

    /** 왕관 주인 바뀜: 지금 그려진 자리에서 새 자리로 날아감 (animate=false 면 바로) */
    fun moveCrown(to: Int, animate: Boolean = true) {
        if (to == crownAt && flightT >= 1f) return
        computeCrown()
        fromX = curX
        fromY = curY
        fromRot = curRot
        fromW = curW
        crownAt = to
        flightT = if (animate && width > 0) 0f else 1f
        invalidate()
    }

    /** 왕관이 날아가는 중 */
    val flying: Boolean get() = flightT < 1f

    /** 구역 i 버튼 가운데 (이 View 좌표) — 연출 위치 */
    fun buttonCenter(i: Int, out: PointF) {
        val z = zones.getOrNull(i) ?: return
        toScreen(z, btnX, btnY, tmp)
        out.set(tmp[0], tmp[1])
    }

    /** 연출 진행. 아직 움직이는 게 있으면 true */
    fun step(dtMs: Float): Boolean {
        var busy = false
        if (flightT < 1f) {
            flightT = (flightT + dtMs / flightMs.coerceAtLeast(1f)).coerceAtMost(1f)
            busy = true
        }
        for (i in 0 until n) {
            if (shakeT[i] in 0f..0.999f) {
                shakeT[i] = (shakeT[i] + dtMs / shakeMs.coerceAtLeast(1f)).coerceAtMost(1f)
                busy = true
            } else shakeT[i] = -1f
        }
        invalidate()
        return busy
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        if (width == 0 || n == 0) return
        // 공통 MultiTouchZoneView 와 같은 구역 (간격 10dp)
        zones = TouchZones.layout(n, width.toFloat(), height.toFloat(), Theme.dpf(context, 10f))
        var minW = Float.MAX_VALUE
        var minH = Float.MAX_VALUE
        for (z in zones) { minW = minOf(minW, z.width); minH = minOf(minH, z.height) }
        // 구역 테두리 안쪽으로 여유
        cellW = minW - 16f * dp
        cellH = minH - 16f * dp
        val w = cellW
        val h = cellH
        ringR = minOf(minOf(width, height) * 0.115f, 42f * dp)
        if (h >= w * 0.8f) {
            // 세로로 긴 구역 (2인): 위 이름·점수, 아래 큰 버튼
            btnR = minOf(h * 0.27f, w * 0.36f)
            btnX = 0f
            btnY = h * 0.5f - btnR - h * 0.1f
            nameX = 0f
            namePaint.textSize = minOf(h * 0.065f, 22f * dp)
            scorePaint.textSize = minOf(h * 0.12f, 44f * dp)
            nameY = -h * 0.36f
            scoreY = nameY + namePaint.textSize * 0.4f + scorePaint.textSize * 1.05f
        } else {
            // 가로로 긴 구역 (3·4인): 왼쪽 버튼, 오른쪽 이름·점수
            btnR = minOf(h * 0.38f, w * 0.2f)
            btnX = -w * 0.2f
            btnY = h * 0.03f
            nameX = w * 0.2f
            namePaint.textSize = minOf(h * 0.13f, 20f * dp)
            scorePaint.textSize = minOf(h * 0.25f, 40f * dp)
            nameY = -h * 0.12f
            scoreY = nameY + namePaint.textSize * 0.35f + scorePaint.textSize * 1.05f
        }
        depth = btnR * 0.13f
        btnText.textSize = btnR * 0.42f
        secPaint.textSize = ringR * 0.62f
        outline.strokeWidth = scorePaint.textSize * 0.12f
        computeCrown()
        invalidate()
    }

    /** 왕관 자리 (to: 플레이어 또는 가운데) → tmp[0], tmp[1] / spotRot / spotW */
    private fun spot(to: Int) {
        val z = if (to >= 0) zones.getOrNull(to) else null
        if (z == null) {
            tmp[0] = width / 2f
            tmp[1] = height / 2f - ringR * 0.26f
            spotRot = 0f
            spotW = ringR * 1.05f
        } else {
            toScreen(z, btnX, btnY - depth * 0.5f, tmp)
            spotRot = z.rotation
            spotW = btnR * 1.15f
        }
    }

    private var spotRot = 0f
    private var spotW = 0f

    /** 비행 진행에 따른 지금 왕관 자리 (포물선 + 회전 + 커졌다 작아짐) */
    private fun computeCrown() {
        if (width == 0) return
        spot(crownAt)
        val tx = tmp[0]
        val ty = tmp[1]
        if (flightT >= 1f) {
            curX = tx; curY = ty; curRot = spotRot; curW = spotW
            return
        }
        val t = flightT
        val e = t * t * (3f - 2f * t)
        // 출발·도착 사이를 옆으로 부풀린 2차 베지어 (포물선)
        val mx = (fromX + tx) / 2f
        val my = (fromY + ty) / 2f
        val dx = tx - fromX
        val dy = ty - fromY
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val bulge = d * 0.35f
        // 화면 위쪽으로 휘도록 (dy 방향 반대)
        var nx = dy / d
        var ny = -dx / d
        if (ny > 0f) { nx = -nx; ny = -ny }
        val qx = mx + nx * bulge
        val qy = my + ny * bulge
        val u = 1f - e
        curX = u * u * fromX + 2f * u * e * qx + e * e * tx
        curY = u * u * fromY + 2f * u * e * qy + e * e * ty
        curRot = fromRot + (spotRot - fromRot) * e
        curW = (fromW + (spotW - fromW) * e) * (1f + 0.35f * sin((PI * t).toFloat()))
    }

    override fun onDraw(c: Canvas) {
        if (zones.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        for (z in zones) {
            val i = z.index
            c.save()
            c.translate(z.centerX, z.centerY)
            c.rotate(z.rotation)
            drawButton(c, i, now)
            drawText(c, i)
            c.restore()
        }
        drawClock(c)
        computeCrown()
        c.save()
        c.rotate(curRot, curX, curY)
        CrownArt.crown(c, curX, curY, curW, p, path)
        c.restore()
    }

    private fun drawButton(c: Canvas, i: Int, now: Long) {
        val holder = crownAt == i && flightT >= 1f
        var x = btnX
        val st = shakeT[i]
        if (st >= 0f) x += sin(st * 6f * PI.toFloat()) * btnR * 0.12f * (1f - st)
        val y = btnY
        val r = btnR
        // 왕관 주인: 금빛 후광 (숨쉬기)
        if (holder) {
            val wave = if (playing) 0.5f + 0.5f * sin((now % 100_000L).toFloat() / glowMs * 2f * PI.toFloat()) else 1f
            p.style = Paint.Style.FILL
            p.color = Theme.GOLD
            for (k in 3 downTo 1) {
                p.alpha = ((40 + 40 * wave) * (4 - k) / 3f).toInt()
                c.drawCircle(x, y + depth * 0.5f, r + k * 5f * dp + wave * 3f * dp, p)
            }
            p.alpha = 255
        }
        // 그림자 + 두께
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(x + r * 0.04f, y + depth + r * 0.07f, r, p)
        p.color = baseColors[i]
        c.drawCircle(x, y + depth, r, p)
        // 윗면 (누르면 내려감)
        val fy = if (pressed[i]) y + depth * 0.8f else y
        val sh = faces[i]
        m.setScale(r, r)
        m.postTranslate(x, fy)
        sh.setLocalMatrix(m)
        face.shader = sh
        face.alpha = if (playing) 255 else 200
        c.drawCircle(x, fy, r, face)
        line.color = 0x55FFFFFF
        line.strokeWidth = r * 0.05f
        c.drawCircle(x, fy, r * 0.84f, line)
        if (holder) {
            line.color = Theme.GOLD
            line.strokeWidth = 3f * dp
            c.drawCircle(x, fy, r - 1.5f * dp, line)
        } else {
            val tx = x
            val ty = fy + btnText.textSize * 0.36f
            outline.textSize = btnText.textSize
            outline.strokeWidth = btnText.textSize * 0.14f
            c.drawText(PRESS, tx, ty, outline)
            c.drawText(PRESS, tx, ty, btnText)
            outline.textSize = scorePaint.textSize
            outline.strokeWidth = scorePaint.textSize * 0.12f
        }
        // 빼앗기 쿨다운 고리 (남은 만큼 빨간 호)
        val cd = cooldown[i]
        if (cd > 0f) {
            val rr = r + 6f * dp
            rect.set(x - rr, y + depth * 0.5f - rr, x + rr, y + depth * 0.5f + rr)
            line.strokeWidth = 5f * dp
            line.color = 0x66000000
            c.drawCircle(rect.centerX(), rect.centerY(), rr, line)
            line.color = Theme.RED
            c.drawArc(rect, -90f, 360f * cd, false, line)
        }
    }

    private fun drawText(c: Canvas, i: Int) {
        namePaint.color = colors[i]
        c.drawText(names[i], nameX, nameY, namePaint)
        val holder = crownAt == i && flightT >= 1f
        scorePaint.color = if (holder) Theme.GOLD else Theme.TEXT
        c.drawText(scoreText[i], nameX, scoreY, outline)
        c.drawText(scoreText[i], nameX, scoreY, scorePaint)
    }

    /** 가운데 시계: 남은 시간 호 + 초 */
    private fun drawClock(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = ringR
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(cx + 2f * dp, cy + 3f * dp, r, p)
        p.color = CLOCK_BG
        c.drawCircle(cx, cy, r, p)
        val ar = r - 4f * dp
        line.strokeWidth = 5f * dp
        line.color = 0x33FFFFFF
        c.drawCircle(cx, cy, ar, line)
        if (timeFrac > 0f) {
            line.color = if (hurry) Theme.RED else Theme.GOLD
            rect.set(cx - ar, cy - ar, cx + ar, cy + ar)
            c.drawArc(rect, -90f, 360f * timeFrac, false, line)
        }
        secPaint.color = if (hurry) Theme.RED else Theme.TEXT
        // 왕관이 가운데 있으면 숫자는 아래쪽, 없으면 가운데
        val center = crownAt == CrownGame.CENTER && flightT >= 1f
        val y = if (center) cy + r * 0.58f else cy + secPaint.textSize * 0.36f
        val ts = secPaint.textSize
        if (center) secPaint.textSize = ts * 0.62f
        c.drawText(secText, cx, y, secPaint)
        secPaint.textSize = ts
    }

    /** 사람 기준 좌표 (lx, ly) → 화면 좌표 */
    private fun toScreen(z: Zone, lx: Float, ly: Float, out: FloatArray) {
        if (z.rotation == 180f) {
            out[0] = z.centerX - lx
            out[1] = z.centerY - ly
        } else {
            out[0] = z.centerX + lx
            out[1] = z.centerY + ly
        }
    }

    companion object {
        private const val PRESS = "꾹!"
        private const val CLOCK_BG = 0xEE0A0F2E.toInt()
        /** 남은 초 글자 (미리 만듦) */
        private val SEC = Array(GameConfig.GAME_TIME_SEC + 1) { it.toString() }
    }
}
