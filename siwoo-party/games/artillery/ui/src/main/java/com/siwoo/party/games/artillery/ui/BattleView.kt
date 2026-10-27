package com.siwoo.party.games.artillery.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.aim.Slingshot
import com.siwoo.party.common.aim.SlingshotDrawer
import com.siwoo.party.common.aim.SlingshotTouch
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.artillery.ArtilleryGame
import com.siwoo.party.games.artillery.GameConfig
import com.siwoo.party.games.artillery.Phase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 전장 (옆에서 본 모습): 하늘·먼 산·언덕 지형(크기가 바뀔 때 비트맵 하나로) + 구름 + 대포·체력 막대 + 포탄·자취 + 폭발 + 위쪽 안내판.
 * - 위쪽 안내판(HUD): 왼쪽 "○○ 차례"·도움말, 가운데 바람(풍향 자루 + 세기 화살표), 오른쪽 각도·힘 (끄는 중이면 지금 값, 아니면 지난 발사).
 * - 터치: 전장 아무 곳이나 누르고 뒤로 끌었다 놓기 (공통 SlingshotTouch — 누른 곳에서 끈 거리·방향을 잼).
 *   각도·힘은 화면에 보이는 정수 값(도·%) 그대로 쏜다.
 * 움직임(포탄·폭발·구름)은 화면(Activity)의 프레임 루프가 [tick] 후 invalidate — 이 View 는 스스로 반복해서 그리지 않는다. onDraw 할당 없음.
 */
class BattleView(context: Context) : View(context) {

    interface Listener {
        /** 지금 조준할 수 있는지 (내 차례·연출 중 아님·일시정지 아님) */
        fun canAim(): Boolean
        /** 쏘기 (각도 도·힘 %) */
        fun onFire(angle: Int, power: Int)
        /** 너무 짧게 끌고 놓음 */
        fun onAimCancel()
    }

    var listener: Listener? = null

    private val d = Theme.dpf(context, 1f)
    private var game: ArtilleryGame? = null
    private var colors = IntArray(GameConfig.MAX_PLAYERS) { Theme.PLAYER_COLORS[it] }
    private var names: Array<String> = Array(GameConfig.MAX_PLAYERS) { "" }

    // ── 배치 (onSizeChanged) ──
    /** 전장 1단위 = unitPx 픽셀, 전장 왼쪽 = ox, 높이 0 = baseY */
    var unitPx = 1f
        private set
    private var ox = 0f
    private var baseY = 0f
    private var bg: Bitmap? = null

    // ── 새총 ──
    val sling = Slingshot(maxDistance = 100f, minDistance = 15f)
    private val slingTouch = SlingshotTouch(sling)
    private val slingDrawer = SlingshotDrawer(context)

    /** 지금 끄는 중인지 */
    val aiming: Boolean get() = slingTouch.dragging

    /** 끄는 중의 각도(도)·힘(%) — 손을 떼면 이 값 그대로 쏜다 */
    var aimAngle = 45
        private set
    var aimPower = 0
        private set

    // ── 안내판 글자 (값이 바뀔 때만 만듦) ──
    private var turnText = ""
    private var turnColor = Theme.GOLD
    private var hintText = ""
    private var windLevel = 0
    private var lastAngle = -1
    private var lastPower = -1
    private var shotText = ""
    private var turnBoxW = 0f
    /** 차례 표시를 할지 (소개·끝에는 숨김) */
    private var showTurn = false

    /** 안내판 도움말 (테스트 확인용) */
    val hint: String get() = hintText

    // ── 자취·폭발 ──
    private var showTrail = false
    /** 폭발 연출 중인지 */
    var exploding = false
        private set
    private var explodeT = 0L
    private var explodeMs = 1L
    private var ex = 0f
    private var ey = 0f
    private val dmg = IntArray(GameConfig.MAX_PLAYERS)
    private val hpFrom = IntArray(GameConfig.MAX_PLAYERS)
    private val hpTo = IntArray(GameConfig.MAX_PLAYERS)

    // ── 구름 ──
    private val cloudX = FloatArray(CLOUDS)
    private val cloudY = FloatArray(CLOUDS)
    private val cloudS = FloatArray(CLOUDS)

    // ── 그리기 도구 ──
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fire = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT_SUB }
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pop = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val tri = Path()
    private val fireMatrix = Matrix()
    private var fireShader: RadialGradient? = null

    init {
        isClickable = true
        slingTouch.listener = object : SlingshotTouch.Listener {
            override fun onAimStart(s: Slingshot) {
                updateAim()
                invalidate()
            }

            override fun onAimMove(s: Slingshot) {
                updateAim()
                invalidate()
            }

            override fun onShoot(s: Slingshot) {
                updateAim()
                invalidate()
                listener?.onFire(aimAngle, aimPower)
            }

            override fun onAimCancel(s: Slingshot) {
                invalidate()
                listener?.onAimCancel()
            }
        }
    }

    /** 게임·플레이어 색·이름 연결 */
    fun bind(game: ArtilleryGame, colors: IntArray, names: Array<String>) {
        this.game = game
        for (i in colors.indices) this.colors[i] = colors[i]
        for (i in names.indices) this.names[i] = names[i]
        val rnd = Random(game.terrain.heights.size * 31 + (game.terrain.heights[7] * 100).toInt())
        for (k in 0 until CLOUDS) {
            cloudX[k] = GameConfig.WORLD_W * (k + rnd.nextFloat() * 0.8f) / CLOUDS
            cloudY[k] = 3f + rnd.nextFloat() * 7f
            cloudS[k] = 1.8f + rnd.nextFloat() * 1.6f
        }
        if (width > 0) buildBackground()
        invalidate()
    }

    /** 차례 표시: 이름 글자·색·바람·지난 발사·발사 수 */
    fun setTurn(label: String, color: Int, wind: Int, lastAngle: Int, lastPower: Int, shots: Int, maxShots: Int) {
        turnText = label
        turnColor = color
        windLevel = wind
        this.lastAngle = lastAngle
        this.lastPower = lastPower
        shotText = (shots + 1).toString() + " / " + maxShots + "발"
        showTurn = true
        measureTurnBox()
        invalidate()
    }

    /** 차례 표시 숨김 (소개·끝) */
    fun hideTurn() {
        showTurn = false
        invalidate()
    }

    fun setHint(s: String) {
        if (hintText == s) return
        hintText = s
        measureTurnBox()
        invalidate()
    }

    private fun measureTurnBox() {
        if (text.textSize <= 0f) return
        turnBoxW = maxOf(text.measureText(turnText) + 34f * d, small.measureText(hintText) + 16f * d, 150f * d)
    }

    /** 자취 보이기 (쏠 때 켜고, 다음 차례에 끔) */
    fun setTrail(on: Boolean) {
        showTrail = on
        invalidate()
    }

    /** 폭발 시작: 전장 (x, y), 맞은 사람들의 이전·이후 체력과 피해 */
    fun explode(x: Float, y: Float, before: IntArray, damage: IntArray, ms: Long) {
        ex = x
        ey = y
        for (i in damage.indices) {
            dmg[i] = damage[i]
            hpFrom[i] = before[i]
            hpTo[i] = before[i] - damage[i]
        }
        explodeT = 0L
        explodeMs = ms.coerceAtLeast(1L)
        exploding = true
        invalidate()
    }

    /** 프레임 진행 (구름 흐름·폭발). 일시정지 중엔 불리지 않는다 */
    fun tick(dtMs: Long) {
        val dt = dtMs / 1000f
        val v = windLevel * GameConfig.CLOUD_DRIFT
        for (k in 0 until CLOUDS) {
            var x = cloudX[k] + v * dt
            if (x > GameConfig.WORLD_W + CLOUD_WRAP) x -= GameConfig.WORLD_W + 2f * CLOUD_WRAP
            if (x < -CLOUD_WRAP) x += GameConfig.WORLD_W + 2f * CLOUD_WRAP
            cloudX[k] = x
        }
        if (exploding) {
            explodeT += dtMs
            if (explodeT >= explodeMs) {
                explodeT = explodeMs
                exploding = false
            }
        }
        invalidate()
    }

    /** 전장 좌표 → View 좌표 */
    fun screenX(x: Float): Float = ox + x * unitPx
    fun screenY(y: Float): Float = baseY - y * unitPx

    /** 끌기 취소 (일시정지 등) */
    fun cancelAim() {
        if (slingTouch.dragging) slingTouch.abort()
        invalidate()
    }

    /** 새총 값 → 정수 각도·힘 */
    private fun updateAim() {
        aimAngle = angleOf(sling.dirX, sling.dirY)
        aimPower = (sling.power * 100f).roundToInt().coerceIn(0, 100)
    }

    // ── 크기·배경 ──

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        unitPx = minOf(w / GameConfig.WORLD_W, h / GameConfig.VIEW_MIN_H)
        ox = (w - GameConfig.WORLD_W * unitPx) / 2f
        baseY = h.toFloat()
        sling.maxDistance = GameConfig.MAX_DRAG_DP * d
        sling.minDistance = sling.maxDistance * GameConfig.MIN_POWER_PCT / 100f
        slingDrawer.guideLength = 15f * unitPx
        slingDrawer.guideStartGap = GameConfig.BARREL * unitPx
        slingDrawer.ringRadius = 3.2f * unitPx
        slingDrawer.showBand = false
        text.textSize = 17f * d
        small.textSize = 11.5f * d
        name.textSize = 11f * d
        pop.textSize = 16f * d
        fireShader = RadialGradient(
            0f, 0f, FIRE_R,
            intArrayOf(0xFFFFFFE0.toInt(), ArtilleryArt.FIRE_LIGHT, ArtilleryArt.FIRE, 0xCCE0381C.toInt(), 0x00801010),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
        fire.shader = fireShader
        measureTurnBox()
        buildBackground()
    }

    /** 하늘·해·먼 산·지형·풀·흙 알갱이를 비트맵 하나에 */
    private fun buildBackground() {
        val g = game ?: return
        if (width <= 0 || height <= 0) return
        release()
        val w = width
        val h = height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = unitPx
        // 하늘
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), ArtilleryArt.SKY_TOP, ArtilleryArt.SKY_BOTTOM, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // 해 (은은한 빛 + 원)
        val sx = ox + GameConfig.WORLD_W * 0.6f * u
        val sy = h * 0.3f
        p.shader = RadialGradient(sx, sy, 9f * u, intArrayOf(0x66FFF6C8, 0x00FFF6C8), null, Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, 9f * u, p)
        p.shader = null
        p.color = 0xFFFFF3B8.toInt()
        c.drawCircle(sx, sy, 2.4f * u, p)
        // 먼 산 두 겹 (지형 높이로 정해지는 물결)
        val t = g.terrain
        val path = Path()
        for (layer in 0..1) {
            path.reset()
            path.moveTo(0f, h.toFloat())
            val base = if (layer == 0) 17f else 12f
            val amp = if (layer == 0) 5f else 4f
            val ph = t.heights[20 + layer * 30] * 0.37f
            var x = 0f
            while (x <= w + 4f) {
                val wx = (x - ox) / u
                val y = base + amp * sin(wx * 0.07f + ph) + amp * 0.5f * sin(wx * 0.19f + ph * 2f)
                path.lineTo(x, baseY - y * u)
                x += 4f
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            p.color = if (layer == 0) ArtilleryArt.FAR_HILL else 0xFF77A6C4.toInt()
            c.drawPath(path, p)
        }
        // 지형
        val terrain = Path()
        terrain.moveTo(ox, h.toFloat() + 2f)
        for (i in t.heights.indices) terrain.lineTo(ox + i * t.dx * u, baseY - t.heights[i] * u)
        terrain.lineTo(ox + GameConfig.WORLD_W * u, h.toFloat() + 2f)
        terrain.close()
        p.shader = LinearGradient(
            0f, baseY - GameConfig.MAX_H * u, 0f, h.toFloat(),
            intArrayOf(0xFF8A6034.toInt(), ArtilleryArt.DIRT, ArtilleryArt.DIRT_DARK),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(terrain, p)
        p.shader = null
        // 흙 알갱이·돌 (땅 안쪽만)
        c.save()
        c.clipPath(terrain)
        val rnd = Random(91)
        repeat((w * h / (60f * d * d)).toInt().coerceAtMost(3000)) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            p.color = if (rnd.nextInt(3) == 0) 0x30FFE8C0 else 0x30402010
            c.drawCircle(x, y, (0.6f + rnd.nextFloat() * 1.3f) * d, p)
        }
        repeat(30) {
            val wx = rnd.nextFloat() * GameConfig.WORLD_W
            val top = t.heightAt(wx)
            val wy = top - 1.5f - rnd.nextFloat() * (top - 2f).coerceAtLeast(0.5f)
            val r = (0.3f + rnd.nextFloat() * 0.45f) * u
            val x = ox + wx * u
            val y = baseY - wy * u
            p.color = 0xFF7D6A58.toInt()
            c.drawOval(x - r, y - r * 0.7f, x + r, y + r * 0.7f, p)
            p.color = 0x40FFFFFF
            c.drawOval(x - r * 0.6f, y - r * 0.55f, x, y - r * 0.1f, p)
        }
        c.restore()
        // 풀 가장자리 (두 겹 선)
        val edge = Path()
        for (i in t.heights.indices) {
            val x = ox + i * t.dx * u
            val y = baseY - t.heights[i] * u
            if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
        }
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 1.1f * u
        p.color = ArtilleryArt.GRASS_DARK
        c.save()
        c.translate(0f, 0.35f * u)
        c.drawPath(edge, p)
        c.restore()
        p.strokeWidth = 0.75f * u
        p.color = ArtilleryArt.GRASS
        c.drawPath(edge, p)
        // 풀 포기
        p.strokeWidth = 1.4f * d
        p.strokeCap = Paint.Cap.ROUND
        repeat(70) {
            val wx = rnd.nextFloat() * GameConfig.WORLD_W
            val x = ox + wx * u
            val y = baseY - t.heightAt(wx) * u
            for (b in -1..1) {
                p.color = if (b == 0) 0xFF79D152.toInt() else ArtilleryArt.GRASS
                c.drawLine(x, y, x + b * 0.25f * u, y - (0.5f + rnd.nextFloat() * 0.4f) * u, p)
            }
        }
        p.style = Paint.Style.FILL
        // 전장 밖 (화면이 더 넓을 때)
        if (ox > 0.5f) {
            p.color = Theme.BG_DEEP
            c.drawRect(0f, 0f, ox, h.toFloat(), p)
            c.drawRect(ox + GameConfig.WORLD_W * u, 0f, w.toFloat(), h.toFloat(), p)
        }
        bg = bmp
    }

    /** 배경 비트맵 해제 */
    fun release() {
        bg?.recycle()
        bg = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bg == null && width > 0) buildBackground()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── 터치 ──

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (slingTouch.dragging) {
            slingTouch.onTouchEvent(e)
            return true
        }
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return false
        if (listener?.canAim() != true) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        slingTouch.clearAnchor()
        return slingTouch.onTouchEvent(e)
    }

    // ── 그리기 ──

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val u = unitPx
        val b = bg
        if (b != null && !b.isRecycled) c.drawBitmap(b, 0f, 0f, bmpPaint)
        // 구름
        for (k in 0 until CLOUDS) ArtilleryArt.cloud(c, art, screenX(cloudX[k]), cloudY[k] * u, cloudS[k] * u, 215)

        val f = if (exploding) explodeT.toFloat() / explodeMs else 1f
        // 대포 + 체력 막대 + 이름
        for (ci in 0 until g.playerCount) {
            val cn = g.cannons[ci]
            val x = screenX(cn.x)
            val y = screenY(cn.y)
            val color = colors[cn.index]
            val shownHp: Float
            if (exploding && dmg[cn.index] > 0) {
                val e = (f / 0.6f).coerceAtMost(1f)
                shownHp = hpFrom[cn.index] + (hpTo[cn.index] - hpFrom[cn.index]) * e
            } else shownHp = cn.hp.toFloat()
            val dead = !cn.alive && !(exploding && shownHp > 0f)
            if (dead) {
                ArtilleryArt.wreck(c, art, x, y, u, color)
            } else {
                val ang = if (aiming && cn.index == g.current) aimAngle.toFloat() else cn.angle.toFloat()
                ArtilleryArt.cannon(c, art, x, y, u, color, ang, GameConfig.BARREL)
            }
            val barW = 46f * d
            val barH = 7f * d
            val barTop = y - 4.3f * u - barH
            if (!dead) {
                val ghost = if (exploding && dmg[cn.index] > 0) hpFrom[cn.index] / 100f else 0f
                ArtilleryArt.hpBar(c, art, x, barTop, barW, barH, shownHp / 100f, ghost)
            }
            // 이름 (외곽선)
            val ny = barTop - 5f * d
            name.style = Paint.Style.STROKE
            name.strokeWidth = 3f * d
            name.color = 0xCC000000.toInt()
            c.drawText(names[cn.index], x, ny, name)
            name.style = Paint.Style.FILL
            name.color = if (dead) 0xFFB0B0B0.toInt() else Theme.TEXT
            c.drawText(names[cn.index], x, ny, name)
            // 지금 차례 표시 (금색 삼각형)
            if (showTurn && cn.index == g.current && cn.alive && g.phase != Phase.OVER) {
                val ty = ny - name.textSize - 4f * d
                tri.reset()
                tri.moveTo(x - 7f * d, ty - 9f * d)
                tri.lineTo(x + 7f * d, ty - 9f * d)
                tri.lineTo(x, ty)
                tri.close()
                fill.color = Theme.GOLD
                c.drawPath(tri, fill)
                stroke.color = 0xAA000000.toInt()
                stroke.strokeWidth = 1.5f * d
                c.drawPath(tri, stroke)
            }
            // 피해 숫자 (떠오름)
            if (exploding && dmg[cn.index] > 0) {
                val py = barTop - 14f * d - f * 22f * d
                val a = if (f < 0.7f) 255 else (255 * (1f - (f - 0.7f) / 0.3f)).toInt()
                val s = DMG_TEXT[dmg[cn.index].coerceIn(0, 100)]
                pop.style = Paint.Style.STROKE
                pop.strokeWidth = 4f * d
                pop.color = 0xFF2A0606.toInt()
                pop.alpha = a
                c.drawText(s, x, py, pop)
                pop.style = Paint.Style.FILL
                pop.color = 0xFFFF5A4A.toInt()
                pop.alpha = a
                c.drawText(s, x, py, pop)
            }
        }

        // 안내판
        drawHud(c, g)

        // 자취
        val sh = g.shell
        if (showTrail) {
            val n = sh.trailCount
            for (k in 0 until n) {
                val i = sh.trailIndex(k)
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = 70 + 150 * (k + 1) / n
                c.drawCircle(screenX(sh.trailX[i]), screenY(sh.trailY[i]), 2.2f * d, fill)
            }
            fill.alpha = 255
        }
        // 날아가는 포탄 (화면 위로 나가면 위쪽 가장자리에 화살표)
        if (sh.flying) {
            val x = screenX(sh.x)
            val y = screenY(sh.y)
            if (y < -GameConfig.SHELL_R * u) {
                tri.reset()
                tri.moveTo(x, 4f * d)
                tri.lineTo(x - 7f * d, 16f * d)
                tri.lineTo(x + 7f * d, 16f * d)
                tri.close()
                fill.color = 0xFF15171C.toInt()
                c.drawPath(tri, fill)
                stroke.color = Theme.TEXT
                stroke.strokeWidth = 1.5f * d
                c.drawPath(tri, stroke)
            } else {
                ArtilleryArt.shell(c, art, x, y, maxOf(GameConfig.SHELL_R * u * 1.3f, 4f * d))
            }
        }
        // 폭발
        if (exploding) drawExplosion(c, f)

        // 조준
        if (aiming) {
            val cn = g.cannons[g.current]
            slingDrawer.draw(c, sling, screenX(cn.x), screenY(cn.y))
        }

        // 차례 색 테두리
        stroke.color = if (showTurn) turnColor else Theme.GOLD
        stroke.alpha = if (listener?.canAim() == true) 255 else 150
        stroke.strokeWidth = 3f * d
        rect.set(1.5f * d, 1.5f * d, width - 1.5f * d, height - 1.5f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, stroke)
        stroke.alpha = 255
    }

    private fun drawExplosion(c: Canvas, f: Float) {
        val u = unitPx
        val x = screenX(ex)
        val y = screenY(ey)
        val e = 1f - (1f - f) * (1f - f) * (1f - f)
        val big = GameConfig.BLAST_R * u
        // 연기
        for (k in 0 until 3) {
            val r = big * (0.25f + 0.35f * e) * (1f + k * 0.2f)
            fill.color = 0xFF505050.toInt()
            fill.alpha = (110 * (1f - f)).toInt()
            c.drawCircle(x + (k - 1) * big * 0.3f * e, y - big * 0.5f * e - k * big * 0.12f, r * 0.6f, fill)
        }
        fill.alpha = 255
        // 불덩이 (방사 그라데이션을 이동·확대해서 재사용)
        val r = big * (0.35f + 0.6f * e) * (1f - 0.25f * f)
        fireMatrix.setScale(r / FIRE_R, r / FIRE_R)
        fireMatrix.postTranslate(x, y)
        fireShader?.setLocalMatrix(fireMatrix)
        fire.alpha = (255 * (1f - f * f)).toInt()
        c.drawCircle(x, y, r, fire)
        // 충격파 고리 (폭발 반경까지)
        stroke.color = 0xFFFFF4C0.toInt()
        stroke.alpha = (230 * (1f - f)).toInt()
        stroke.strokeWidth = (4f - 3f * f) * d
        c.drawCircle(x, y, big * e, stroke)
        stroke.alpha = 255
        // 파편
        for (k in 0 until SPARKS) {
            val a = SPARK_ANGLE[k]
            val dist = big * (0.4f + 0.8f * e) * SPARK_LEN[k]
            val sx = x + cos(a) * dist
            val sy = y - sin(a) * dist + f * f * 3f * u
            fill.color = if (k % 2 == 0) ArtilleryArt.FIRE_LIGHT else ArtilleryArt.FIRE
            fill.alpha = (255 * (1f - f)).toInt()
            c.drawCircle(sx, sy, (2.6f - 1.6f * f) * d, fill)
        }
        fill.alpha = 255
    }

    private fun drawHud(c: Canvas, g: ArtilleryGame) {
        val pad = 8f * d
        val boxH = 48f * d
        val r = 10f * d
        // 왼쪽: 차례
        if (showTurn) {
            rect.set(pad, pad, pad + turnBoxW, pad + boxH)
            fill.color = 0xCC0B1340.toInt()
            c.drawRoundRect(rect, r, r, fill)
            stroke.color = turnColor
            stroke.strokeWidth = 2f * d
            c.drawRoundRect(rect, r, r, stroke)
            fill.color = turnColor
            c.drawCircle(pad + 15f * d, pad + 17f * d, 6f * d, fill)
            c.drawText(turnText, pad + 27f * d, pad + 23f * d, text)
            small.color = Theme.GOLD_LIGHT
            c.drawText(hintText, pad + 9f * d, pad + 40f * d, small)
        }
        // 가운데: 바람
        val ww = 156f * d
        val wx = (width - ww) / 2f
        rect.set(wx, pad, wx + ww, pad + boxH)
        fill.color = 0xCC0B1340.toInt()
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 1.5f * d
        c.drawRoundRect(rect, r, r, stroke)
        ArtilleryArt.windsock(c, art, wx + 20f * d, pad + 12f * d, 24f * d, windLevel, GameConfig.WIND_LEVELS)
        small.color = Theme.TEXT_SUB
        c.drawText(WIND_CAPTION, wx + 60f * d, pad + 17f * d, small)
        c.drawText(WIND_TEXT[windLevel + GameConfig.WIND_LEVELS], wx + 60f * d + small.measureText(WIND_CAPTION) + 6f * d, pad + 17f * d, text)
        // 세기 칸 (바람 방향 화살표가 세기만큼 켜짐)
        val n = abs(windLevel)
        val dir = if (windLevel < 0) -1f else 1f
        val cw = 15f * d
        val cy = pad + 34f * d
        for (k in 0 until GameConfig.WIND_LEVELS) {
            val slot = if (dir > 0) k else GameConfig.WIND_LEVELS - 1 - k
            val cx = wx + 66f * d + slot * cw
            tri.reset()
            tri.moveTo(cx - 5f * d * dir, cy - 6f * d)
            tri.lineTo(cx + 5f * d * dir, cy)
            tri.lineTo(cx - 5f * d * dir, cy + 6f * d)
            tri.close()
            fill.color = if (k < n) (if (n >= 4) 0xFFFF7A45.toInt() else Theme.GOLD) else 0x33FFFFFF
            c.drawPath(tri, fill)
        }
        // 오른쪽: 각도·힘
        val rw = 156f * d
        val rx = width - pad - rw
        rect.set(rx, pad, rx + rw, pad + boxH)
        fill.color = 0xCC0B1340.toInt()
        c.drawRoundRect(rect, r, r, fill)
        stroke.color = if (aiming) Theme.GOLD else 0x55FFFFFF
        stroke.strokeWidth = if (aiming) 2f * d else 1.5f * d
        c.drawRoundRect(rect, r, r, stroke)
        small.color = Theme.TEXT_SUB
        c.drawText(if (aiming) AIM_CAPTION else LAST_CAPTION, rx + 10f * d, pad + 16f * d, small)
        small.textAlign = Paint.Align.RIGHT
        if (showTurn) c.drawText(shotText, rx + rw - 10f * d, pad + 16f * d, small)
        small.textAlign = Paint.Align.LEFT
        val a: Int
        val p: Int
        if (aiming) {
            a = aimAngle
            p = aimPower
        } else {
            a = lastAngle
            p = lastPower
        }
        text.color = if (aiming && sling.cancelled) Theme.RED else Theme.TEXT
        if (a >= 0) {
            val elev = if (a > 90) 180 - a else a
            c.drawText(ANGLE_TEXT[elev], rx + 10f * d, pad + 39f * d, text)
            c.drawText(POWER_TEXT[p.coerceIn(0, 100)], rx + 84f * d, pad + 39f * d, text)
            // 방향 작은 화살표
            val ax = rx + 64f * d
            val ay = pad + 33f * d
            val dir2 = if (a > 90) -1f else 1f
            tri.reset()
            tri.moveTo(ax + 6f * d * dir2, ay)
            tri.lineTo(ax - 4f * d * dir2, ay - 5f * d)
            tri.lineTo(ax - 4f * d * dir2, ay + 5f * d)
            tri.close()
            fill.color = Theme.GOLD
            c.drawPath(tri, fill)
        } else {
            c.drawText(NONE_TEXT, rx + 10f * d, pad + 39f * d, text)
        }
        text.color = Theme.TEXT
    }

    companion object {
        private const val CLOUDS = 5
        private const val CLOUD_WRAP = 8f
        private const val FIRE_R = 100f
        private const val SPARKS = 12
        private val SPARK_ANGLE = FloatArray(SPARKS) { (PI / 2 + (it - (SPARKS - 1) / 2f) * 0.27).toFloat() }
        private val SPARK_LEN = FloatArray(SPARKS) { 0.75f + 0.3f * ((it * 7) % 5) / 4f }
        private val ANGLE_TEXT = Array(91) { "$it°" }
        private val POWER_TEXT = Array(101) { "$it%" }
        private val DMG_TEXT = Array(101) { "-$it" }
        private val WIND_TEXT = Array(GameConfig.WIND_LEVELS * 2 + 1) {
            val v = it - GameConfig.WIND_LEVELS
            when {
                v < 0 -> "← " + (-v)
                v > 0 -> "" + v + " →"
                else -> "잔잔"
            }
        }
        private const val WIND_CAPTION = "바람"
        private const val AIM_CAPTION = "각도 · 힘"
        private const val LAST_CAPTION = "지난 발사"
        private const val NONE_TEXT = "—"

        /**
         * 새총 방향(화면 좌표, y 아래 +) → 발사 각도 (도, 0 = 오른쪽, 90 = 위, 180 = 왼쪽).
         * 아래쪽을 향하면 가까운 수평(0 또는 180)으로.
         */
        fun angleOf(dirX: Float, dirY: Float): Int {
            if (dirX == 0f && dirY == 0f) return 90
            var a = Math.toDegrees(atan2(-dirY, dirX).toDouble())
            if (a < 0) a = if (a > -90) 0.0 else 180.0
            return a.roundToInt().coerceIn(GameConfig.ANGLE_MIN, GameConfig.ANGLE_MAX)
        }
    }
}
