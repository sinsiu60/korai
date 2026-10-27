package com.siwoo.party.games.onecard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Theme

/**
 * 카드 테이블 위 안내 층 (터치는 받지 않음): 뽑을 더미 장수, 지금 낼 무늬 동그라미, 진행 방향 화살표,
 * 쌓인 공격 장수 터짐, 공격받는 사람 이름 위 "+N" 말풍선(잠깐 튀어나왔다 사라짐).
 * 글자는 값이 바뀔 때만 만든다 → onDraw 할당 없음. "+N" 연출 중에만 다시 그린다.
 */
class TableInfoView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    // ── 상태 ──
    private var pileX = 0f
    private var pileY = 0f
    private var pileHalfH = 0f
    private var pileText = ""
    private var suitX = 0f
    private var suitY = 0f
    private var suit: Suit? = null
    private var suitChanged = false
    private var suitLabel = ""
    /** 첫 카드를 펼치기 전에는 무늬 동그라미를 그리지 않는다 */
    private var suitVisible = false
    private var dirX = 0f
    private var dirY = 0f
    private var direction = 1
    private var attackX = 0f
    private var attackY = 0f
    private var attack = 0
    private var attackText = ""

    /** 지금 보이는 공격 장수 (테스트용) */
    val attackShown: Int get() = attack
    /** 지금 보이는 무늬 (null = 아무 무늬) */
    val suitShown: Suit? get() = suit
    val directionShown: Int get() = direction

    // ── "+N" 말풍선 ──
    private var badgeOn = false
    private var badgeX = 0f
    private var badgeY = 0f
    private var badgeText = ""
    private var badgeStart = 0L
    private var badgeDur = 1L
    private var paused = false
    private var pauseAt = 0L
    private val frame = Runnable { onFrame() }
    private var looping = false

    /** "+N" 말풍선이 떠 있는지 (테스트용) */
    val badgeShowing: Boolean get() = badgeOn
    val badgeLabel: String get() = badgeText

    // ── 그리기 도구 ──
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB; typeface = Typeface.DEFAULT_BOLD }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.TEXT; typeface = Typeface.DEFAULT_BOLD }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val rect = RectF()
    private val arrow = Path()
    private val burst = Path()

    init {
        isClickable = false
        small.textSize = 11f * density
        big.textSize = 20f * density
        // 공격 터짐 (반지름 1, 12갈래)
        for (k in 0 until 24) {
            val a = -Math.PI / 2 + k * Math.PI / 12
            val r = if (k % 2 == 0) 1.0 else 0.78
            val x = (r * Math.cos(a)).toFloat()
            val y = (r * Math.sin(a)).toFloat()
            if (k == 0) burst.moveTo(x, y) else burst.lineTo(x, y)
        }
        burst.close()
        // 오른쪽 화살표 (가운데 0,0, 폭 2, 높이 1)
        arrow.moveTo(-1f, -0.16f)
        arrow.lineTo(0.35f, -0.16f)
        arrow.lineTo(0.35f, -0.5f)
        arrow.lineTo(1f, 0f)
        arrow.lineTo(0.35f, 0.5f)
        arrow.lineTo(0.35f, 0.16f)
        arrow.lineTo(-1f, 0.16f)
        arrow.close()
    }

    // ── 값 넣기 (글자는 여기서만 만든다) ──

    fun setPile(x: Float, y: Float, halfH: Float, count: Int) {
        pileX = x; pileY = y; pileHalfH = halfH
        pileText = if (count > 0) "남은 ${count}장" else "더미 없음"
        invalidate()
    }

    fun setSuitSpot(x: Float, y: Float) {
        suitX = x; suitY = y
        invalidate()
    }

    /** 지금 낼 무늬 (null = 아무 무늬, changed = 7 로 바뀐 무늬) */
    fun setSuit(s: Suit?, changed: Boolean) {
        suit = s
        suitVisible = true
        suitChanged = changed
        suitLabel = when {
            s == null -> "아무 무늬"
            changed -> s.koName + "로 바뀜"
            else -> "지금 무늬"
        }
        invalidate()
    }

    fun setDirectionSpot(x: Float, y: Float) {
        dirX = x; dirY = y
        invalidate()
    }

    fun setDirection(d: Int) {
        if (direction == d) return
        direction = d
        invalidate()
    }

    fun setAttackSpot(x: Float, y: Float) {
        attackX = x; attackY = y
        invalidate()
    }

    fun setAttack(n: Int) {
        if (attack == n) return
        attack = n
        attackText = if (n > 0) "+$n" else ""
        invalidate()
    }

    /** (x, y) 위에 "+N" 말풍선을 durMs 동안 */
    fun showBadge(x: Float, y: Float, n: Int, durMs: Long) {
        badgeX = x; badgeY = y
        badgeText = "+$n"
        badgeStart = SystemClock.uptimeMillis()
        badgeDur = durMs.coerceAtLeast(1L)
        badgeOn = true
        startLoop()
        invalidate()
    }

    fun pause() {
        if (paused) return
        paused = true
        pauseAt = SystemClock.uptimeMillis()
        stopLoop()
    }

    fun resume() {
        if (!paused) return
        paused = false
        badgeStart += SystemClock.uptimeMillis() - pauseAt
        if (badgeOn) startLoop()
    }

    private fun startLoop() {
        if (looping || paused) return
        looping = true
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        looping = false
        removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!looping) return
        if (SystemClock.uptimeMillis() - badgeStart >= badgeDur) {
            badgeOn = false
            looping = false
        } else {
            postOnAnimation(frame)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        badgeOn = false
        super.onDetachedFromWindow()
    }

    // ── 그리기 (할당 없음) ──

    override fun onDraw(c: Canvas) {
        val d = density
        // 뽑을 더미 장수
        if (pileText.isNotEmpty()) c.drawText(pileText, pileX, pileY + pileHalfH + 24f * d, small)
        if (suitVisible) drawSuit(c, d)
        drawDirection(c, d)
        if (attack > 0) drawAttack(c, d)
        if (badgeOn) drawBadge(c, d)
    }

    private fun drawSuit(c: Canvas, d: Float) {
        val r = 21f * d
        fill.color = 0x55000000
        c.drawCircle(suitX + d, suitY + 2f * d, r, fill)
        fill.color = Theme.PAPER
        c.drawCircle(suitX, suitY, r, fill)
        stroke.color = if (suitChanged) Theme.GOLD else Theme.GOLD_DARK
        stroke.strokeWidth = (if (suitChanged) 3.5f else 2f) * d
        c.drawCircle(suitX, suitY, r, stroke)
        if (suitChanged) {
            stroke.color = Theme.GOLD
            stroke.alpha = 80
            stroke.strokeWidth = 8f * d
            c.drawCircle(suitX, suitY, r + 4f * d, stroke)
            stroke.alpha = 255
        }
        val s = suit
        if (s != null) {
            fill.color = SuitArt.color(s)
            SuitArt.draw(c, s, suitX, suitY, r * 1.15f, fill)
        } else {
            fill.color = Theme.GOLD_DARK
            SuitArt.drawStar(c, suitX, suitY, r * 1.3f, fill)
        }
        c.drawText(suitLabel, suitX, suitY + r + 14f * d, small)
    }

    private fun drawDirection(c: Canvas, d: Float) {
        val w = 30f * d
        val h = 22f * d
        rect.set(dirX - w, dirY - h / 2f, dirX + w, dirY + h / 2f)
        fill.color = Theme.CARD
        c.drawRoundRect(rect, h / 2f, h / 2f, fill)
        stroke.color = Theme.CARD_BORDER
        stroke.strokeWidth = d
        c.drawRoundRect(rect, h / 2f, h / 2f, stroke)
        fill.color = Theme.GOLD
        c.save()
        c.translate(dirX, dirY)
        c.scale(direction.toFloat() * w * 0.62f, h * 0.62f)
        c.drawPath(arrow, fill)
        c.restore()
        c.drawText(DIR_LABEL, dirX, dirY + h / 2f + 13f * d, small)
    }

    private fun drawAttack(c: Canvas, d: Float) {
        val r = 26f * d
        fill.color = 0x66000000
        c.save()
        c.translate(attackX + d, attackY + 2f * d)
        c.scale(r, r)
        c.drawPath(burst, fill)
        c.restore()
        fill.color = Theme.RED_DARK
        c.save()
        c.translate(attackX, attackY)
        c.scale(r, r)
        c.drawPath(burst, fill)
        c.scale(0.82f, 0.82f)
        fill.color = Theme.RED
        c.drawPath(burst, fill)
        c.restore()
        big.textSize = 19f * d
        c.drawText(attackText, attackX, attackY + 7f * d, big)
        small.color = Theme.RED
        c.drawText(ATTACK_LABEL, attackX, attackY + r + 13f * d, small)
        small.color = Theme.TEXT_SUB
    }

    private fun drawBadge(c: Canvas, d: Float) {
        val now = if (paused) pauseAt else SystemClock.uptimeMillis()
        val e = (now - badgeStart).coerceAtLeast(0L)
        val p = (e.toFloat() / badgeDur).coerceIn(0f, 1f)
        // 0~15% 튀어나옴(1.25배) → 25% 제자리 → 80% 부터 위로 떠오르며 사라짐
        val sc = when {
            p < 0.15f -> 1.25f * (p / 0.15f)
            p < 0.25f -> 1.25f - 0.25f * ((p - 0.15f) / 0.1f)
            else -> 1f
        }
        val fade = if (p > 0.8f) 1f - (p - 0.8f) / 0.2f else 1f
        val rise = if (p > 0.8f) (p - 0.8f) / 0.2f * 14f * d else 0f
        val a = (255 * fade).toInt()
        val cx = badgeX
        val cy = badgeY - rise
        c.save()
        c.translate(cx, cy)
        c.scale(sc, sc)
        val w = 34f * d
        val h = 28f * d
        rect.set(-w, -h, w, h)
        fill.color = Theme.RED_DARK
        fill.alpha = a
        c.drawRoundRect(rect, h, h, fill)
        rect.inset(3f * d, 3f * d)
        fill.color = Theme.RED
        fill.alpha = a
        c.drawRoundRect(rect, h, h, fill)
        stroke.color = Theme.GOLD
        stroke.alpha = a
        stroke.strokeWidth = 2.5f * d
        rect.inset(-3f * d, -3f * d)
        c.drawRoundRect(rect, h, h, stroke)
        stroke.alpha = 255
        fill.alpha = 255
        big.textSize = 26f * d
        outline.textSize = big.textSize
        outline.strokeWidth = 4f * d
        outline.color = Theme.RED_DARK
        outline.alpha = a
        big.alpha = a
        c.drawText(badgeText, 0f, 9f * d, outline)
        c.drawText(badgeText, 0f, 9f * d, big)
        big.alpha = 255
        c.restore()
    }

    companion object {
        private const val DIR_LABEL = "진행 방향"
        private const val ATTACK_LABEL = "공격 중!"
    }
}
