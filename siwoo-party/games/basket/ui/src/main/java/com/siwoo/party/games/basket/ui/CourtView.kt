package com.siwoo.party.games.basket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.swipe.SwipeConfig
import com.siwoo.party.common.swipe.SwipeReject
import com.siwoo.party.common.swipe.SwipeResult
import com.siwoo.party.common.swipe.SwipeTracker
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.basket.Aim
import com.siwoo.party.games.basket.BasketGame
import com.siwoo.party.games.basket.GameConfig
import com.siwoo.party.games.basket.Phase
import kotlin.math.cos
import kotlin.math.sin

/**
 * 옆에서 본 농구 코트 (왼쪽 슛 자리들 → 오른쪽 골대). 좌표는 Court 의 m 단위를 그대로 그린다 (x = 림 가운데 0, y = 바닥 0).
 * - 체육관 벽·마루·3점 라인·페인트 존·슛 자리 점·골대 기둥·백보드는 크기가 바뀔 때 비트맵으로 한 번 그려 두고(release 에서 recycle) 붙이기만
 * - 림(타원)·그물은 뒤쪽 반 → 공 → 앞쪽 반 순서로 그려 공이 림 "안으로" 들어가 보이게. 골이 들어가면 그물이 출렁인다
 * - 조작: 화면 아무 데나 누르고 위로(골대 쪽으로) 쓱 밀었다 떼면 공통 스와이프(SwipeTracker → 각도·세기)를 listener.onShoot 로
 * - 미는 동안: 예상 궤적 점선(앞쪽 일부) + 세기 막대
 * - 불꽃: 연속 성공 중인 사람의 공에 불꽃 왕관, 날아가는 동안엔 미리 만든 불꽃 입자(최대 FIRE_N 개)가 꼬리처럼
 * 움직임은 화면(Activity)의 프레임 루프가 game.advance + tickFire 후 invalidate 한다 — 이 View 는 스스로 반복해서 그리지 않는다.
 */
class CourtView(context: Context) : View(context) {

    interface Listener {
        /** 지금 슛할 수 있는지 */
        fun canShoot(): Boolean
        /** 스와이프 성공: 스와이프 각도(0° = 오른쪽, 90° = 위) · 세기 0..1 */
        fun onShoot(swipeAngleDeg: Float, power: Float)
        /** 스와이프 실패 (짧음·느림·아래로 등) */
        fun onBadSwipe(reject: SwipeReject)
        /** 화면을 누름 */
        fun onGrab() {}
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: BasketGame? = null

    // ── 배치: 1m = unitPx, 그리는 범위 왼쪽 위 (X0, Y1) = (ox, oy) ──
    var unitPx = 1f
        private set
    var ox = 0f
        private set
    var oy = 0f
        private set
    private var bgBmp: Bitmap? = null

    // ── 표시 상태 (setter 에서만 글자를 만든다) ──
    private var shooterColor = Theme.RED
    private var shooterDark = Theme.RED_DARK
    private var initial = ""
    private var spotText = ""
    private var spotThree = false
    private var hint = false
    /** 지금 슛하는 사람이 불꽃 상태 */
    var fire = false
        private set

    // ── 스와이프 ──
    val tracker = SwipeTracker()
    val swipeConfig = SwipeConfig(
        minLength = GameConfig.SWIPE_MIN_LEN,
        minSpeed = GameConfig.SWIPE_MIN_SPEED,
        fullSpeed = GameConfig.SWIPE_FULL_SPEED,
        maxDurationMs = GameConfig.SWIPE_MAX_MS,
        forwardDeg = GameConfig.SWIPE_FORWARD_DEG,
        maxDeviationDeg = GameConfig.SWIPE_MAX_DEVIATION,
    )
    private val preview = SwipeResult()
    /** 누르고 미는 중 */
    var holding = false
        private set

    // ── 불꽃 입자 (미리 만든 배열) ──
    private val fx = FloatArray(FIRE_N)
    private val fy = FloatArray(FIRE_N)
    private val fvx = FloatArray(FIRE_N)
    private val fvy = FloatArray(FIRE_N)
    private val fAge = FloatArray(FIRE_N)
    private val fLife = FloatArray(FIRE_N)
    private val fSize = FloatArray(FIRE_N)
    private var emitAcc = 0f
    private var seed = 12345
    private var wobble = 0f
    /** 살아 있는 불꽃 입자 수 (테스트 확인용) */
    var fireAlive = 0
        private set

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xDD101530.toInt()
    }
    private val jersey = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val path = Path()
    private val rect = RectF()
    private val netTopX = FloatArray(NET_K)
    private val netTopY = FloatArray(NET_K)
    private val netCos = FloatArray(NET_K) { cos(it * 2.0 * Math.PI / NET_K).toFloat() }
    private val netSin = FloatArray(NET_K) { sin(it * 2.0 * Math.PI / NET_K).toFloat() }

    init {
        isClickable = true
    }

    /** 게임 연결 */
    fun bind(game: BasketGame) {
        this.game = game
        invalidate()
    }

    /** 지금 슛하는 사람 (유니폼 색·가슴 글자) */
    fun setShooter(color: Int, name: String) {
        shooterColor = color
        shooterDark = Theme.shade(color, 0.55f)
        initial = name.take(1)
        invalidate()
    }

    /** 지금 슛 자리 표시 (거리 글자 · 3점 자리) */
    fun setSpot(distance: Float, three: Boolean) {
        spotText = (Math.round(distance * 10f) / 10f).toString() + "m"
        spotThree = three
        invalidate()
    }

    /** 불꽃 켜기/끄기 (끄면 입자도 지움) */
    fun setFire(on: Boolean) {
        if (fire == on) return
        fire = on
        if (!on) clearFire()
        invalidate()
    }

    /** "위로 쓱!" 안내 표시 */
    fun setHint(on: Boolean) {
        if (hint != on) {
            hint = on
            invalidate()
        }
    }

    /** 미는 중이던 손가락 취소 (일시정지 등) */
    fun cancelAim() {
        if (!holding) return
        holding = false
        tracker.reset()
        invalidate()
    }

    fun sx(x: Float): Float = ox + (x - X0) * unitPx
    fun sy(y: Float): Float = oy + (Y1 - y) * unitPx

    /** 코트 좌표 (x, y) → 이 View 좌표 */
    fun toScreen(x: Float, y: Float, out: PointF) {
        out.set(sx(x), sy(y))
    }

    // ── 크기·배경 비트맵 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        unitPx = minOf(w / (X1 - X0), h / (Y1 - Y0))
        ox = (w - (X1 - X0) * unitPx) / 2f
        oy = (h - (Y1 - Y0) * unitPx) / 2f
        label.textSize = 13f * d
        outline.textSize = 13f * d
        outline.strokeWidth = 3.5f * d
        jersey.textSize = unitPx * 0.26f
        buildBackground(w, h)
    }

    /** 벽·마루·코트 선·슛 자리·기둥·보드 (움직이지 않는 것 전부) */
    private fun buildBackground(w: Int, h: Int) {
        release()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = unitPx
        val floorY = sy(0f)
        val floorTop = floorY - FLOOR_BACK * u
        // 벽 (위 어둡게 → 아래 밝게)
        q.color = 0xFF000000.toInt()
        q.shader = LinearGradient(0f, 0f, 0f, floorTop, intArrayOf(0xFF0E1645.toInt(), 0xFF1E2E78.toInt(), 0xFF2A3C8C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), floorTop, q)
        q.shader = null
        // 조명 (천장 근처 둥근 빛)
        for (k in 0 until 4) {
            val lx = w * (0.12f + k * 0.26f)
            val ly = u * 0.25f
            q.shader = RadialGradient(lx, ly, u * 1.6f, 0x55FFF2C0, 0x00FFF2C0, Shader.TileMode.CLAMP)
            c.drawCircle(lx, ly, u * 1.6f, q)
            q.shader = null
            q.color = 0xCCFFF6D8.toInt()
            c.drawRect(lx - u * 0.35f, 0f, lx + u * 0.35f, u * 0.08f, q)
        }
        // 벽 위쪽 응원 깃발 줄 (삼각 깃발)
        val flagY = u * 0.55f
        q.style = Paint.Style.STROKE
        q.strokeWidth = maxOf(1f, u * 0.02f)
        q.color = 0x66FFFFFF
        c.drawLine(0f, flagY, w.toFloat(), flagY, q)
        q.style = Paint.Style.FILL
        val fp = Path()
        var fxp = u * 0.2f
        var k = 0
        while (fxp < w) {
            q.color = Theme.PLAYER_COLORS[k % Theme.PLAYER_COLORS.size]
            q.alpha = 200
            fp.reset()
            fp.moveTo(fxp, flagY)
            fp.lineTo(fxp + u * 0.36f, flagY)
            fp.lineTo(fxp + u * 0.18f, flagY + u * 0.34f)
            fp.close()
            c.drawPath(fp, q)
            fxp += u * 0.55f
            k++
        }
        q.alpha = 255
        // 벽 아래 보호 패드 (남색 + 금색 줄)
        val padTop = floorTop - u * 0.75f
        q.color = 0xFF141C4E.toInt()
        c.drawRect(0f, padTop, w.toFloat(), floorTop, q)
        q.color = Theme.GOLD_DARK
        c.drawRect(0f, padTop, w.toFloat(), padTop + maxOf(2f, u * 0.05f), q)
        q.color = 0x22FFFFFF
        var px = 0f
        while (px < w) { c.drawRect(px, padTop + u * 0.05f, px + maxOf(1f, u * 0.02f), floorTop, q); px += u * 1.4f }
        // 마루 (원근: 위쪽이 먼 쪽)
        q.color = 0xFF000000.toInt()
        q.shader = LinearGradient(0f, floorTop, 0f, h.toFloat(), intArrayOf(0xFFB98552.toInt(), BasketArt.WOOD, 0xFFE2AE74.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, floorTop, w.toFloat(), h.toFloat(), q)
        q.shader = null
        // 마루 판자 (가로 줄 = 원근으로 아래로 갈수록 간격 넓게) + 이음매
        q.color = 0x2A5A3210
        q.strokeWidth = maxOf(1f, u * 0.015f)
        var gy = floorTop
        var gap = u * 0.06f
        var row = 0
        while (gy < h) {
            c.drawLine(0f, gy, w.toFloat(), gy, q)
            var sxp = ((row * 37) % 5) * u * 0.5f
            while (sxp < w) { c.drawLine(sxp, gy, sxp, gy + gap, q); sxp += u * 2.4f }
            gy += gap
            gap *= 1.18f
            row++
        }
        // 페인트 존 (자유투 라인까지) — 마루 위 납작한 사다리꼴
        val ftX = sx(-FREE_THROW)
        val baseX = sx(GameConfig.BOARD_X)
        q.color = 0x88B83A2A.toInt()
        fp.reset()
        fp.moveTo(ftX + u * 0.15f, floorY - KEY_HALF * u)
        fp.lineTo(baseX + u * 0.15f, floorY - KEY_HALF * u)
        fp.lineTo(baseX - u * 0.1f, floorY + KEY_HALF * u * 1.3f)
        fp.lineTo(ftX - u * 0.1f, floorY + KEY_HALF * u * 1.3f)
        fp.close()
        c.drawPath(fp, q)
        q.style = Paint.Style.STROKE
        q.strokeWidth = maxOf(1.5f, u * 0.05f)
        q.color = 0xEEFFFFFF.toInt()
        c.drawPath(fp, q)
        // 3점 라인 (림 아래를 중심으로 한 큰 원 → 납작한 타원의 왼쪽 반)
        val r3 = GameConfig.THREE_PT_LINE * u
        val ry = FLOOR_RY * u
        val rf = RectF(sx(0f) - r3, floorY - ry, sx(0f) + r3, floorY + ry)
        c.drawArc(rf, 90f, 180f, false, q)
        // 바닥 가장자리 (엔드 라인)
        c.drawLine(baseX + u * 0.2f, floorTop + u * 0.04f, baseX - u * 0.15f, h.toFloat(), q)
        q.style = Paint.Style.FILL
        // 슛 자리 점 (3점 자리는 금색 테)
        for (i in GameConfig.DISTANCES.indices) {
            val x = sx(-GameConfig.DISTANCES[i])
            val three = GameConfig.DISTANCES[i] > GameConfig.THREE_PT_LINE
            q.color = 0x99FFFFFF.toInt()
            c.drawOval(x - u * 0.13f, floorY - u * 0.05f, x + u * 0.13f, floorY + u * 0.05f, q)
            if (three) {
                q.style = Paint.Style.STROKE
                q.strokeWidth = maxOf(1f, u * 0.03f)
                q.color = Theme.GOLD
                c.drawOval(x - u * 0.19f, floorY - u * 0.08f, x + u * 0.19f, floorY + u * 0.08f, q)
                q.style = Paint.Style.FILL
            }
        }
        // "3점 라인" 글자 (마루 위, 라인 바로 왼쪽 아래)
        q.textSize = maxOf(9f * d, u * 0.2f)
        q.typeface = Typeface.DEFAULT_BOLD
        q.textAlign = Paint.Align.CENTER
        q.color = 0xCCFFFFFF.toInt()
        c.drawText("3점 라인", sx(-GameConfig.THREE_PT_LINE) + u * 0.05f, floorY + ry + q.textSize * 1.05f, q)
        // 골대 기둥 + 받침 + 팔
        val poleX = sx(POLE_X)
        q.color = 0xFF1A2A6A.toInt()
        c.drawRoundRect(poleX - u * 0.4f, floorY - u * 0.3f, poleX + u * 0.4f, floorY + u * 0.12f, u * 0.08f, u * 0.08f, q)
        q.color = 0xFF000000.toInt()
        q.shader = LinearGradient(poleX - u * 0.09f, 0f, poleX + u * 0.09f, 0f, intArrayOf(0xFF5A6488.toInt(), 0xFFB8C0DC.toInt(), 0xFF5A6488.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(poleX - u * 0.09f, sy(POLE_TOP), poleX + u * 0.09f, floorY - u * 0.2f, q)
        q.shader = null
        q.color = 0xFF6C7699.toInt()
        q.style = Paint.Style.STROKE
        q.strokeWidth = u * 0.08f
        q.strokeCap = Paint.Cap.ROUND
        c.drawLine(poleX, sy(POLE_TOP - 0.05f), sx(GameConfig.BOARD_X + 0.1f), sy(POLE_TOP - 0.05f), q)
        c.drawLine(poleX, sy(POLE_TOP - 0.6f), sx(GameConfig.BOARD_X + 0.1f), sy(GameConfig.BOARD_BOTTOM + 0.25f), q)
        q.strokeCap = Paint.Cap.BUTT
        q.style = Paint.Style.FILL
        // 기둥 보호 패드 (아래쪽)
        q.color = 0xFF2D47B8.toInt()
        c.drawRoundRect(poleX - u * 0.14f, sy(1.6f), poleX + u * 0.14f, floorY - u * 0.25f, u * 0.06f, u * 0.06f, q)
        // 백보드: 옆면 판 + 살짝 보이는 앞면 유리 (원근 느낌)
        val bl = sx(GameConfig.BOARD_X - GameConfig.BOARD_HALF)
        val br = sx(GameConfig.BOARD_X + GameConfig.BOARD_HALF)
        val bt = sy(GameConfig.BOARD_TOP)
        val bb = sy(GameConfig.BOARD_BOTTOM)
        q.color = 0x55CFE4FF
        fp.reset()
        fp.moveTo(bl, bt)
        fp.lineTo(bl - GLASS_W * u, bt + u * 0.06f)
        fp.lineTo(bl - GLASS_W * u, bb + u * 0.06f)
        fp.lineTo(bl, bb)
        fp.close()
        c.drawPath(fp, q)
        q.color = 0xFFFF6A2A.toInt()
        q.strokeWidth = maxOf(1f, u * 0.025f)
        val sqTop = sy(GameConfig.RIM_Y + 0.45f)
        val sqBottom = sy(GameConfig.RIM_Y + 0.02f)
        c.drawLine(bl - GLASS_W * u * 0.5f, sqTop + u * 0.03f, bl - GLASS_W * u * 0.5f, sqBottom + u * 0.03f, q)
        q.color = 0xFFF4F7FF.toInt()
        c.drawRect(bl, bt, br, bb, q)
        q.color = 0xFF8A93B8.toInt()
        c.drawRect(br - u * 0.015f, bt, br, bb, q)
        q.color = 0xFFFF6A2A.toInt()
        c.drawRect(bl, sqTop, br, sqBottom, q)
        // 림 받침 (림 뒤 → 보드)
        q.color = 0xFF9AA2C0.toInt()
        c.drawRect(sx(GameConfig.RIM_R), sy(GameConfig.RIM_Y) - u * 0.03f, bl, sy(GameConfig.RIM_Y) + u * 0.05f, q)
        c.drawRect(bl - u * 0.04f, sy(GameConfig.RIM_Y) - u * 0.03f, bl, sy(GameConfig.RIM_Y - 0.18f), q)
        bgBmp = bmp
    }

    fun release() {
        bgBmp?.recycle()
        bgBmp = null
    }

    // ── 불꽃 입자 ──

    /** dtMs 만큼 불꽃 입자를 움직이고, 공이 날아가는 중이면 새로 뿜는다 (프레임 루프에서) */
    fun tickFire(dtMs: Long) {
        val g = game ?: return
        val dt = dtMs.coerceIn(0L, 50L) / 1000f
        wobble += dt * 9f
        if (wobble > 1000f) wobble -= 1000f
        var alive = 0
        for (i in 0 until FIRE_N) {
            if (fLife[i] <= 0f) continue
            fAge[i] += dt
            if (fAge[i] >= fLife[i]) { fLife[i] = 0f; continue }
            fx[i] += fvx[i] * dt
            fy[i] += fvy[i] * dt
            fvy[i] -= 60f * d * dt // 위로 떠오름
            alive++
        }
        if (fire && g.phase == Phase.FLYING) {
            emitAcc += dt * EMIT_PER_SEC
            val b = g.court.ball
            val bx = sx(b.x)
            val by = sy(b.y)
            val r = GameConfig.BALL_R * unitPx
            val svx = b.vx * unitPx
            val svy = -b.vy * unitPx
            while (emitAcc >= 1f) {
                emitAcc -= 1f
                val i = freeSlot()
                if (i < 0) break
                fx[i] = bx + (rnd() - 0.5f) * r * 1.2f
                fy[i] = by + (rnd() - 0.5f) * r * 1.2f
                // 공 진행 반대로 조금 + 위로
                fvx[i] = -svx * 0.18f + (rnd() - 0.5f) * 30f * d
                fvy[i] = -svy * 0.18f - (30f + rnd() * 40f) * d
                fAge[i] = 0f
                fLife[i] = 0.32f + rnd() * 0.3f
                fSize[i] = r * (0.75f + rnd() * 0.6f)
                alive++
            }
        } else {
            emitAcc = 0f
        }
        fireAlive = alive
    }

    private fun clearFire() {
        for (i in 0 until FIRE_N) fLife[i] = 0f
        fireAlive = 0
        emitAcc = 0f
    }

    private fun freeSlot(): Int {
        for (i in 0 until FIRE_N) if (fLife[i] <= 0f) return i
        return -1
    }

    /** 결정적 간단 난수 0..1 (그림용) */
    private fun rnd(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65535f
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (l == null || !l.canShoot()) return false
                holding = true
                tracker.begin(e.x, e.y, e.eventTime)
                l.onGrab()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!holding) return false
                tracker.add(e.x, e.y, e.eventTime)
                wobble = (e.eventTime % 100_000L) / 1000f * 9f
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!holding) return false
                tracker.add(e.x, e.y, e.eventTime)
                holding = false
                val r = tracker.analyze(height.toFloat(), swipeConfig)
                tracker.reset()
                invalidate()
                if (l == null || !l.canShoot()) return true
                if (r.valid) l.onShoot(r.angleDeg, r.power) else l.onBadSwipe(r.reject)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAim()
                return true
            }
        }
        return false
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val bmp = bgBmp ?: return
        val g = game ?: return
        c.drawBitmap(bmp, 0f, 0f, bmpPaint)
        val u = unitPx
        val court = g.court
        val ball = court.ball
        val aiming = g.phase == Phase.AIM
        // 지금 슛 자리 (빛나는 테 + 거리 글자)
        val spotX = sx(g.releaseX)
        val floorY = sy(0f)
        line.color = if (spotThree) Theme.GOLD else Theme.GOLD_LIGHT
        line.strokeWidth = maxOf(2f, u * 0.05f)
        rect.set(spotX - u * 0.32f, floorY - u * 0.12f, spotX + u * 0.32f, floorY + u * 0.12f)
        c.drawOval(rect, line)
        val ty = floorY + u * 0.12f + label.textSize * 1.15f
        c.drawText(spotText, spotX, ty, outline)
        label.color = if (spotThree) Theme.GOLD else Theme.TEXT
        c.drawText(spotText, spotX, ty, label)
        label.color = Theme.TEXT
        // 슛하는 사람 (공 뒤)
        drawShooter(c, g.releaseX, aiming)
        // 림 뒤쪽 반 + 그물 뒤쪽
        val rimX = sx(0f)
        val rimY = sy(GameConfig.RIM_Y)
        val rx = GameConfig.RIM_R * u
        val rry = RIM_RY * u
        var stretch = 1f
        var squeeze = 1f
        if (court.madeStep >= 0) {
            val f = (court.steps - court.madeStep) / GameConfig.NET_SWISH_STEPS.toFloat()
            if (f in 0f..1f) {
                val s = sin(f * Math.PI.toFloat())
                stretch = 1f + 0.4f * s
                squeeze = 1f - 0.3f * s
            }
        }
        drawNet(c, rimX, rimY, rx, rry, stretch, squeeze, back = true)
        line.color = BasketArt.RIM_DARK
        line.strokeWidth = maxOf(2f, GameConfig.RIM_TUBE * 2.2f * u)
        rect.set(rimX - rx, rimY - rry, rimX + rx, rimY + rry)
        c.drawArc(rect, 180f, 180f, false, line)
        // 공 그림자 (바닥)
        val bx = sx(ball.x)
        val by = sy(ball.y)
        val br = GameConfig.BALL_R * u
        val hgt = (ball.y / 4f).coerceIn(0f, 1f)
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        rect.set(bx - br * (1.1f - 0.5f * hgt), floorY - br * 0.25f, bx + br * (1.1f - 0.5f * hgt), floorY + br * 0.25f)
        c.drawOval(rect, p)
        // 공 (+ 불꽃)
        if (fire) {
            if (g.phase == Phase.FLYING) {
                drawParticles(c)
                p.color = 0x55FF8A1E
                c.drawCircle(bx, by, br * 1.8f, p)
            } else {
                BasketArt.fireCrown(c, p, path, bx, by, br, wobble)
            }
        }
        BasketArt.ball(c, p, bx, by, br, ball.rot)
        if (fire && g.phase == Phase.FLYING) {
            p.color = 0x55FFB020
            c.drawCircle(bx, by, br * 1.1f, p)
        }
        // 그물 앞쪽 + 림 앞쪽 반
        drawNet(c, rimX, rimY, rx, rry, stretch, squeeze, back = false)
        line.color = BasketArt.RIM
        line.strokeWidth = maxOf(2f, GameConfig.RIM_TUBE * 2.2f * u)
        c.drawArc(rect.apply { set(rimX - rx, rimY - rry, rimX + rx, rimY + rry) }, 0f, 180f, false, line)
        // 화면 위로 넘어간 공 표시 (작은 삼각형)
        if (by + br < 0f) {
            p.color = BasketArt.ORANGE
            path.reset()
            path.moveTo(bx, 3f * d)
            path.lineTo(bx - 7f * d, 13f * d)
            path.lineTo(bx + 7f * d, 13f * d)
            path.close()
            c.drawPath(path, p)
        }
        // 조준 중: 예상 궤적·세기 / 안내
        if (aiming) {
            if (holding) drawPreview(c, g) else if (hint) drawHint(c, g)
        }
    }

    /** 옆에서 본 선수 (발 = 바닥, 공을 머리 위로 든 자세 / 던진 뒤엔 손목을 꺾은 팔로우스루) */
    private fun drawShooter(c: Canvas, releaseX: Float, aiming: Boolean) {
        val u = unitPx
        val bodyX = sx(releaseX - 0.2f)
        val floorY = sy(0f)
        // 다리
        line.color = 0xFF2A1E18.toInt()
        line.strokeWidth = u * 0.13f
        c.drawLine(bodyX - u * 0.06f, sy(0.9f), bodyX - u * 0.12f, floorY - u * 0.06f, line)
        c.drawLine(bodyX + u * 0.06f, sy(0.9f), bodyX + u * 0.1f, floorY - u * 0.06f, line)
        // 신발
        p.style = Paint.Style.FILL
        p.color = Theme.TEXT
        c.drawRoundRect(bodyX - u * 0.22f, floorY - u * 0.1f, bodyX - u * 0.0f, floorY, u * 0.04f, u * 0.04f, p)
        c.drawRoundRect(bodyX + u * 0.0f, floorY - u * 0.1f, bodyX + u * 0.22f, floorY, u * 0.04f, u * 0.04f, p)
        // 반바지 + 유니폼
        p.color = shooterDark
        c.drawRoundRect(bodyX - u * 0.17f, sy(1.05f), bodyX + u * 0.17f, sy(0.78f), u * 0.05f, u * 0.05f, p)
        p.color = shooterColor
        c.drawRoundRect(bodyX - u * 0.18f, sy(1.58f), bodyX + u * 0.18f, sy(1.0f), u * 0.08f, u * 0.08f, p)
        p.color = 0x55FFFFFF
        c.drawRect(bodyX - u * 0.18f, sy(1.58f) + u * 0.04f, bodyX + u * 0.18f, sy(1.58f) + u * 0.07f, p)
        if (initial.isNotEmpty()) c.drawText(initial, bodyX, sy(1.22f), jersey)
        // 팔 (어깨 → 손)
        val shY = sy(1.5f)
        val handX: Float
        val handY: Float
        if (aiming) {
            handX = sx(releaseX - 0.05f)
            handY = sy(GameConfig.RELEASE_Y - GameConfig.BALL_R * 0.9f)
        } else {
            handX = sx(releaseX + 0.12f)
            handY = sy(GameConfig.RELEASE_Y + 0.02f)
        }
        line.color = 0xFFE7B08A.toInt()
        line.strokeWidth = u * 0.09f
        c.drawLine(bodyX + u * 0.05f, shY, handX, handY, line)
        c.drawLine(bodyX - u * 0.05f, shY, handX - u * 0.08f, handY + u * 0.02f, line)
        // 머리
        p.color = 0xFFE7B08A.toInt()
        c.drawCircle(bodyX, sy(1.76f), u * 0.15f, p)
        p.color = 0xFF2A1A10.toInt()
        rect.set(bodyX - u * 0.16f, sy(1.93f), bodyX + u * 0.16f, sy(1.7f))
        c.drawArc(rect, 180f, 180f, true, p)
        // 머리띠 (사람 색)
        p.color = shooterColor
        c.drawRect(bodyX - u * 0.15f, sy(1.83f), bodyX + u * 0.15f, sy(1.79f), p)
    }

    /** 그물: 림 타원 둘레 NET_K 개 점 → 아래 작은 타원. back = 뒤쪽 줄만 / 앞쪽 줄만 */
    private fun drawNet(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, stretch: Float, squeeze: Float, back: Boolean) {
        val u = unitPx
        val depth = GameConfig.NET_DEPTH * u * stretch
        val brx = rx * 0.58f * squeeze
        val bry = ry * 0.6f * squeeze
        line.color = if (back) 0x99FFFFFF.toInt() else BasketArt.NET
        line.strokeWidth = maxOf(1f, u * 0.018f)
        for (k in 0 until NET_K) {
            val isBack = netSin[k] < 0f
            if (isBack != back) continue
            val tx = cx + rx * netCos[k]
            val ty = cy + ry * netSin[k]
            val bx = cx + brx * netCos[k]
            val by = cy + depth + bry * netSin[k]
            netTopX[k] = tx
            netTopY[k] = ty
            c.drawLine(tx, ty, bx, by, line)
            // 엇갈린 그물코: 옆 줄의 가운데로
            val k2 = (k + 1) % NET_K
            val mx = cx + (rx + brx) * 0.5f * netCos[k2]
            val my = cy + depth * 0.5f + (ry + bry) * 0.5f * netSin[k2]
            c.drawLine(tx, ty, mx, my, line)
            c.drawLine(mx, my, bx, by, line)
        }
        // 가운데·아래 고리
        val ringStart = if (back) 180f else 0f
        rect.set(cx - (rx + brx) * 0.5f, cy + depth * 0.5f - (ry + bry) * 0.5f, cx + (rx + brx) * 0.5f, cy + depth * 0.5f + (ry + bry) * 0.5f)
        c.drawArc(rect, ringStart, 180f, false, line)
        rect.set(cx - brx, cy + depth - bry, cx + brx, cy + depth + bry)
        c.drawArc(rect, ringStart, 180f, false, line)
    }

    private fun drawParticles(c: Canvas) {
        p.style = Paint.Style.FILL
        for (i in 0 until FIRE_N) {
            if (fLife[i] <= 0f) continue
            val a = (fAge[i] / fLife[i]).coerceIn(0f, 1f)
            p.color = if (a < 0.3f) BasketArt.FLAME_YELLOW else if (a < 0.65f) BasketArt.FLAME_ORANGE else BasketArt.FLAME_RED
            p.alpha = ((1f - a) * 230f).toInt()
            c.drawCircle(fx[i], fy[i], fSize[i] * (1f - 0.6f * a), p)
        }
        p.alpha = 255
    }

    /** 미는 중: 지금까지의 스와이프로 예상 궤적(앞쪽 일부) 점선 + 세기 막대 */
    private fun drawPreview(c: Canvas, g: BasketGame) {
        if (tracker.sampleCount < 2) return
        val r = tracker.analyze(height.toFloat(), swipeConfig, preview)
        if (r.lengthNorm < GameConfig.SWIPE_MIN_LEN * 0.4f) return
        val u = unitPx
        val ok = r.valid || r.reject == SwipeReject.TOO_SHORT
        val dirOk = r.reject != SwipeReject.WRONG_DIRECTION
        if (dirOk) {
            val ang = Aim.launchAngle(r.angleDeg)
            val v = Aim.speed(g.distance, ang, r.power)
            val rad = Math.toRadians(ang.toDouble())
            val vx = (cos(rad) * v).toFloat()
            val vy = (sin(rad) * v).toFloat()
            val total = (g.distance + GameConfig.AIM_BEYOND) / maxOf(0.1f, vx)
            val tEnd = total * GameConfig.PREVIEW_FRACTION
            p.style = Paint.Style.FILL
            for (k in 1..PREVIEW_DOTS) {
                val t = tEnd * k / PREVIEW_DOTS
                val x = g.releaseX + vx * t
                val y = g.releaseY + vy * t - 0.5f * GameConfig.GRAVITY * t * t
                p.color = if (ok) 0xFFFFFFFF.toInt() else 0xFFB8C3F0.toInt()
                p.alpha = 230 - k * 14
                c.drawCircle(sx(x), sy(y), u * (0.075f - k * 0.0035f), p)
            }
            p.alpha = 255
        }
        // 세기 막대 (선수 왼쪽)
        val bx = sx(g.releaseX - 0.62f)
        val b0 = sy(0.55f)
        val bh = 1.3f * u
        p.color = 0x88000000.toInt()
        c.drawRoundRect(bx - 4f * d, b0 - bh, bx + 4f * d, b0, 3f * d, 3f * d, p)
        p.color = if (r.power > 0.85f) Theme.RED else Theme.GOLD
        c.drawRoundRect(bx - 4f * d, b0 - bh * r.power.coerceIn(0.02f, 1f), bx + 4f * d, b0, 3f * d, 3f * d, p)
    }

    /** 대기 중 안내: 공 위로 휘어 오르는 점 화살표 + "위로 쓱!" */
    private fun drawHint(c: Canvas, g: BasketGame) {
        val u = unitPx
        val x0 = g.releaseX
        val y0 = g.releaseY + 0.3f
        p.style = Paint.Style.FILL
        p.color = 0xDDFFFFFF.toInt()
        for (k in 0 until 6) {
            val f = k / 5f
            c.drawCircle(sx(x0 + 0.15f + f * 0.9f), sy(y0 + f * 1.1f - f * f * 0.35f), u * (0.05f + 0.02f * f), p)
        }
        // 화살촉
        val hx = sx(x0 + 1.12f)
        val hy = sy(y0 + 0.82f)
        path.reset()
        path.moveTo(hx + u * 0.2f, hy - u * 0.08f)
        path.lineTo(hx - u * 0.08f, hy - u * 0.12f)
        path.lineTo(hx + u * 0.04f, hy + u * 0.16f)
        path.close()
        c.drawPath(path, p)
        val tx = sx(x0 - 0.2f)
        val ty = sy(y0 + 1.25f)
        c.drawText(HINT, tx, ty, outline)
        c.drawText(HINT, tx, ty, label)
    }

    companion object {
        /** 그리는 범위 (m): 가장 먼 슛 자리 뒤 ~ 골대 기둥 뒤, 바닥 아래(원근 마루) ~ 보드 위 */
        val X0 = -(GameConfig.DISTANCES[GameConfig.DISTANCES.size - 1] + 1.1f)
        const val X1 = GameConfig.BOARD_X + 1.2f
        const val Y0 = -0.75f
        const val Y1 = 4.5f
        /** 마루 먼 쪽 깊이 (바닥선 위로 보이는 마루, m) · 3점 원의 납작한 높이 */
        private const val FLOOR_BACK = 0.45f
        private const val FLOOR_RY = 0.36f
        /** 자유투 라인 (림 가운데에서) · 페인트 존 반폭(원근으로 납작) */
        private const val FREE_THROW = 4.2f
        private const val KEY_HALF = 0.2f
        /** 기둥 x · 기둥 꼭대기 */
        private const val POLE_X = GameConfig.BOARD_X + 0.85f
        private const val POLE_TOP = 3.7f
        /** 백보드 앞면 유리 보이는 폭 (원근) */
        private const val GLASS_W = 0.09f
        /** 림 타원 세로 반지름 (원근) */
        private const val RIM_RY = 0.07f
        private const val NET_K = 10
        private const val FIRE_N = 36
        private const val EMIT_PER_SEC = 90f
        private const val PREVIEW_DOTS = 10
        private const val HINT = "위로 쓱!"
    }
}
