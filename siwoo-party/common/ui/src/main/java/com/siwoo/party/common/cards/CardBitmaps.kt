package com.siwoo.party.common.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 트럼프 카드 그림 캐시: 앞면 54장 + 뒷면 1장을 Canvas 코드로 그려 Bitmap 으로 한 번만 만든다.
 * 이미지 파일 없음 — 무늬(♠♥♦♣)는 Path, 숫자는 글자, J·Q·K 는 도형 인물, 조커는 광대 모자 + 별.
 *
 * - 처음 [face]/[back] 을 부를 때 그 카드만 그린다(지연 생성). 그리기(onDraw) 중 첫 생성을 피하려면
 *   게임 시작 때 [prepareAll] (또는 [prepare]) 을 부를 것. CardTableView 는 카드를 올릴 때 미리 그린다.
 * - 모서리가 둥글어 투명 픽셀이 필요하므로 ARGB_8888 (RGB_565 불가).
 * - 메모리: 장당 cardWidthPx × cardHeightPx × 4 바이트, 최대 55장.
 *   예) 56dp 카드: xhdpi 112×157px = 69KB/장 → 전체 3.8MB, xxhdpi 168×235px = 158KB/장 → 8.7MB,
 *       hdpi(저가형) 84×118px = 40KB/장 → 2.2MB. 게임당 예산 60MB 안에서 넉넉하다.
 * - **게임이 끝나면 [release] 를 부를 것** (onGameDestroy). 소유자는 만든 쪽(게임 Activity)이다.
 *   release 뒤에 다시 face() 를 부르면 그 카드만 새로 그린다.
 *
 * @param cardWidthPx 카드 폭(px). 높이 = 폭 × 1.4. 화면에서 더 크게/작게 보일 때는 CardTableView 의 scale 로.
 */
class CardBitmaps(context: Context, val cardWidthPx: Int) {

    val cardHeightPx: Int = (cardWidthPx * RATIO).roundToInt()

    /** 장당 바이트 */
    val bytesPerCard: Int = cardWidthPx * cardHeightPx * 4

    /** 55장(앞 54 + 뒤 1)을 모두 만들었을 때 바이트 */
    val maxBytes: Int = bytesPerCard * (Card.COUNT + 1)

    /** 카드 모서리 반지름(px) — 그림자·테두리를 같은 모양으로 그릴 때 */
    val cornerRadius: Float = cardWidthPx * CORNER

    private val faces = arrayOfNulls<Bitmap>(Card.COUNT)
    private var backBmp: Bitmap? = null
    private val painter = Painter(cardWidthPx.toFloat(), cardHeightPx.toFloat())

    init {
        // context 는 보관하지 않는다 (Activity 누수 방지) — 지금은 쓰지 않지만 API 를 맞춰 둠
        require(cardWidthPx >= 8) { "카드 폭이 너무 작음: $cardWidthPx" }
    }

    /** 지금 만들어져 있는 그림 수 */
    val cachedCount: Int
        get() {
            var n = if (backBmp != null) 1 else 0
            for (b in faces) if (b != null) n++
            return n
        }

    /** 지금 차지하는 바이트 */
    val cachedBytes: Int get() = cachedCount * bytesPerCard

    /** 앞면 (처음이면 그린다) */
    fun face(card: Card): Bitmap {
        faces[card.id]?.let { return it }
        val b = newBitmap()
        painter.drawFace(Canvas(b), card)
        faces[card.id] = b
        return b
    }

    /** 뒷면 (처음이면 그린다) */
    fun back(): Bitmap {
        backBmp?.let { return it }
        val b = newBitmap()
        painter.drawBack(Canvas(b))
        backBmp = b
        return b
    }

    fun bitmap(card: Card, faceUp: Boolean): Bitmap = if (faceUp) face(card) else back()

    /** 여러 장 미리 그리기 (뒷면 포함) */
    fun prepare(cards: Iterable<Card>) {
        back()
        for (c in cards) face(c)
    }

    /** 55장 전부 미리 그리기 */
    fun prepareAll() {
        prepare(Card.ALL)
    }

    /** 모든 Bitmap 을 recycle 하고 비운다 (게임 끝날 때). 다시 쓰면 필요한 것만 새로 그림 */
    fun release() {
        for (i in faces.indices) {
            faces[i]?.recycle()
            faces[i] = null
        }
        backBmp?.recycle()
        backBmp = null
    }

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)

    companion object {
        /** 높이 / 폭 */
        const val RATIO = 1.4f
        /** 모서리 반지름 / 폭 */
        const val CORNER = 0.08f

        const val RED = 0xFFD0202E.toInt()
        const val BLACK = 0xFF161A2C.toInt()

        /** dp 폭 → px (기본 56dp: 640×360dp 가로 화면에서 손패 7~10장이 편한 크기) */
        fun widthPx(context: Context, dp: Float = 56f): Int =
            (dp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(8)
    }

    /**
     * 실제 그리기 (Bitmap 만들 때 한 번씩만 불림 → 여기서는 객체를 만들어도 된다).
     * 좌표는 px, W = 카드 폭, H = 카드 높이.
     */
    private class Painter(val W: Float, val H: Float) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val rect = RectF()
        private val suitPaths = arrayOf(spadePath(), heartPath(), diamondPath(), clubPath())

        private fun suitColor(c: Card): Int = if (c.isRed) RED else BLACK

        // ── 앞면 ──

        fun drawFace(c: Canvas, card: Card) {
            paper(c)
            when {
                card.isJoker -> joker(c, card.isRedJoker)
                card.isCourt -> court(c, card)
                card.rank == 1 -> ace(c, card)
                else -> pips(c, card)
            }
            if (!card.isJoker) {
                corner(c, card)
                c.save(); c.rotate(180f, W / 2f, H / 2f); corner(c, card); c.restore()
            }
        }

        private fun paper(c: Canvas) {
            val r = W * CORNER
            val sw = (W * 0.014f).coerceAtLeast(1f)
            rect.set(sw / 2f, sw / 2f, W - sw / 2f, H - sw / 2f)
            fill.shader = null
            fill.color = 0xFFFFFDF6.toInt()
            c.drawRoundRect(rect, r, r, fill)
            stroke.color = 0xFFB9B2A0.toInt()
            stroke.strokeWidth = sw
            c.drawRoundRect(rect, r, r, stroke)
        }

        /** 왼쪽 위 숫자 + 작은 무늬 (손패에서 겹쳐도 보이는 부분 → 크고 굵게) */
        private fun corner(c: Canvas, card: Card) {
            val cx = W * 0.135f
            val label = card.label
            text.color = suitColor(card)
            text.textSize = W * 0.25f
            text.textScaleX = if (label.length > 1) 0.72f else 1f
            text.typeface = SERIF_BOLD
            c.drawText(label, cx, W * 0.255f, text)
            text.textScaleX = 1f
            suit(c, card.suit!!, cx, W * 0.37f, W * 0.15f, suitColor(card))
        }

        /** 무늬 하나: (cx, cy) 가운데, size = 높이 */
        private fun suit(c: Canvas, s: Suit, cx: Float, cy: Float, size: Float, color: Int, upsideDown: Boolean = false) {
            c.save()
            c.translate(cx, cy)
            if (upsideDown) c.rotate(180f)
            c.scale(size, size)
            fill.shader = null
            fill.color = color
            c.drawPath(suitPaths[s.ordinal], fill)
            c.restore()
        }

        private fun pips(c: Canvas, card: Card) {
            val s = card.suit!!
            val color = suitColor(card)
            val spec = PIPS[card.rank - 2]
            val top = H * 0.19f
            val bottom = H * 0.81f
            val size = W * 0.175f
            var k = 0
            while (k < spec.size) {
                val x = W * spec[k]
                val y = top + (bottom - top) * spec[k + 1]
                suit(c, s, x, y, size, color, upsideDown = spec[k + 1] > 0.55f)
                k += 2
            }
        }

        private fun ace(c: Canvas, card: Card) {
            val s = card.suit!!
            val color = suitColor(card)
            if (s == Suit.SPADE) {
                // 스페이드 A: 금색 원 장식 + 큰 무늬
                fill.shader = null
                fill.color = 0x33FFC93C
                c.drawCircle(W / 2f, H / 2f, W * 0.34f, fill)
                stroke.color = Theme.GOLD_DARK
                stroke.strokeWidth = W * 0.018f
                c.drawCircle(W / 2f, H / 2f, W * 0.34f, stroke)
                suit(c, s, W / 2f, H / 2f, W * 0.48f, color)
            } else {
                suit(c, s, W / 2f, H / 2f, W * 0.42f, color)
            }
        }

        // ── J Q K: 틀 안에 위아래 대칭 인물 ──

        private fun court(c: Canvas, card: Card) {
            val color = suitColor(card)
            val l = W * 0.25f
            val r = W * 0.75f
            val t = H * 0.09f
            val b = H * 0.91f
            val tint = when (card.rank) {
                Card.KING -> 0xFFFFEDB8.toInt()
                Card.QUEEN -> 0xFFFFE0E6.toInt()
                else -> 0xFFDDE8FF.toInt()
            }
            rect.set(l, t, r, b)
            fill.shader = null
            fill.color = tint
            c.drawRect(rect, fill)
            // 위 절반 인물 → 180° 돌려 아래 절반
            c.save(); c.clipRect(l, t, r, H / 2f); figure(c, card, l, t, r, H / 2f); c.restore()
            c.save(); c.rotate(180f, W / 2f, H / 2f); c.clipRect(l, t, r, H / 2f); figure(c, card, l, t, r, H / 2f); c.restore()
            stroke.color = color
            stroke.strokeWidth = (W * 0.016f).coerceAtLeast(1f)
            rect.set(l, t, r, b) // figure() 가 rect 를 썼으므로 다시
            c.drawRect(rect, stroke)
            // 가운데 사선 (위아래 구분)
            stroke.strokeWidth = (W * 0.01f).coerceAtLeast(1f)
            c.drawLine(l, H / 2f, r, H / 2f, stroke)
        }

        /** 틀 위 절반(l,t)-(r,b) 에 인물 반신 */
        private fun figure(c: Canvas, card: Card, l: Float, t: Float, r: Float, b: Float) {
            val fw = r - l
            val hh = b - t
            val cx = (l + r) / 2f
            val red = card.isRed
            val robe = if (red) 0xFFC62834.toInt() else 0xFF26336E.toInt()
            val robeDark = if (red) 0xFF8E1622.toInt() else 0xFF141C45.toInt()
            val hair = when (card.rank) {
                Card.KING -> 0xFFF2F2F2.toInt()
                Card.QUEEN -> 0xFFB86B1F.toInt()
                else -> 0xFF5A3A1E.toInt()
            }
            val headR = fw * 0.16f
            val headY = t + hh * 0.5f
            // 몸(망토): 어깨 → 아래로 넓어지는 사다리꼴
            val path = Path()
            val sy = headY + headR * 1.35f
            path.moveTo(cx - fw * 0.2f, sy)
            path.quadTo(cx, sy - headR * 0.5f, cx + fw * 0.2f, sy)
            path.lineTo(cx + fw * 0.48f, b + 2f)
            path.lineTo(cx - fw * 0.48f, b + 2f)
            path.close()
            fill.shader = null
            fill.color = robe
            c.drawPath(path, fill)
            // 금색 V 깃 + 어깨 장식
            stroke.color = Theme.GOLD
            stroke.strokeWidth = fw * 0.05f
            val vy = sy + (b - sy) * 0.55f
            c.drawLine(cx - fw * 0.17f, sy + headR * 0.05f, cx, vy, stroke)
            c.drawLine(cx + fw * 0.17f, sy + headR * 0.05f, cx, vy, stroke)
            c.drawLine(cx - fw * 0.22f, sy + headR * 0.15f, cx - fw * 0.42f, b, stroke)
            c.drawLine(cx + fw * 0.22f, sy + headR * 0.15f, cx + fw * 0.42f, b, stroke)
            // 가슴의 무늬 메달
            val my = sy + (b - sy) * 0.52f
            fill.color = 0xFFFFFDF6.toInt()
            c.drawCircle(cx + fw * 0.28f, my, fw * 0.105f, fill)
            stroke.strokeWidth = fw * 0.02f
            c.drawCircle(cx + fw * 0.28f, my, fw * 0.105f, stroke)
            suit(c, card.suit!!, cx + fw * 0.28f, my, fw * 0.13f, suitColor(card))
            // 머리카락(퀸은 긴 머리) — 얼굴 뒤
            fill.color = hair
            if (card.rank == Card.QUEEN) {
                rect.set(cx - headR * 1.35f, headY - headR * 1.1f, cx + headR * 1.35f, headY + headR * 1.9f)
                c.drawRoundRect(rect, headR, headR, fill)
            } else {
                c.drawCircle(cx, headY - headR * 0.15f, headR * 1.12f, fill)
            }
            // 목
            fill.color = 0xFFF1C9A0.toInt()
            rect.set(cx - headR * 0.38f, headY + headR * 0.6f, cx + headR * 0.38f, sy + headR * 0.1f)
            c.drawRect(rect, fill)
            // 얼굴
            fill.color = 0xFFF8D9B8.toInt()
            c.drawCircle(cx, headY + headR * 0.1f, headR, fill)
            // 눈·입
            fill.color = 0xFF2A1F1A.toInt()
            c.drawCircle(cx - headR * 0.38f, headY, headR * 0.12f, fill)
            c.drawCircle(cx + headR * 0.38f, headY, headR * 0.12f, fill)
            stroke.color = 0xFFB0413E.toInt()
            stroke.strokeWidth = headR * 0.12f
            c.drawLine(cx - headR * 0.25f, headY + headR * 0.5f, cx + headR * 0.25f, headY + headR * 0.5f, stroke)
            if (card.rank == Card.KING) {
                // 흰 수염
                fill.color = 0xFFF2F2F2.toInt()
                path.reset()
                path.moveTo(cx - headR * 0.85f, headY + headR * 0.45f)
                path.quadTo(cx, headY + headR * 0.85f, cx + headR * 0.85f, headY + headR * 0.45f)
                path.quadTo(cx + headR * 0.6f, headY + headR * 1.7f, cx, headY + headR * 1.85f)
                path.quadTo(cx - headR * 0.6f, headY + headR * 1.7f, cx - headR * 0.85f, headY + headR * 0.45f)
                c.drawPath(path, fill)
            }
            // 모자: K 왕관(뾰족 3개), Q 작은 왕관(둥근), J 베레모 + 깃털
            val hatY = headY - headR * 0.75f
            path.reset()
            when (card.rank) {
                Card.KING -> {
                    val hw = headR * 1.15f
                    val ht = headR * 1.25f
                    path.moveTo(cx - hw, hatY)
                    path.lineTo(cx - hw, hatY - ht)
                    path.lineTo(cx - hw * 0.5f, hatY - ht * 0.5f)
                    path.lineTo(cx, hatY - ht * 1.15f)
                    path.lineTo(cx + hw * 0.5f, hatY - ht * 0.5f)
                    path.lineTo(cx + hw, hatY - ht)
                    path.lineTo(cx + hw, hatY)
                    path.close()
                    fill.color = Theme.GOLD
                    c.drawPath(path, fill)
                    stroke.color = Theme.GOLD_DARK
                    stroke.strokeWidth = headR * 0.12f
                    c.drawPath(path, stroke)
                    fill.color = RED
                    c.drawCircle(cx, hatY - ht * 0.35f, headR * 0.18f, fill)
                }
                Card.QUEEN -> {
                    val hw = headR * 1.0f
                    val ht = headR * 0.8f
                    path.moveTo(cx - hw, hatY)
                    path.quadTo(cx - hw, hatY - ht, cx - hw * 0.5f, hatY - ht * 0.7f)
                    path.quadTo(cx, hatY - ht * 1.6f, cx + hw * 0.5f, hatY - ht * 0.7f)
                    path.quadTo(cx + hw, hatY - ht, cx + hw, hatY)
                    path.close()
                    fill.color = Theme.GOLD
                    c.drawPath(path, fill)
                    stroke.color = Theme.GOLD_DARK
                    stroke.strokeWidth = headR * 0.12f
                    c.drawPath(path, stroke)
                    fill.color = 0xFF3D7BFF.toInt()
                    c.drawCircle(cx, hatY - ht * 0.55f, headR * 0.2f, fill)
                }
                else -> {
                    rect.set(cx - headR * 1.25f, hatY - headR * 0.9f, cx + headR * 1.25f, hatY + headR * 0.35f)
                    fill.color = robeDark
                    c.drawArc(rect, 180f, 180f, true, fill)
                    fill.color = Theme.GOLD
                    rect.set(cx - headR * 1.25f, hatY - headR * 0.05f, cx + headR * 1.25f, hatY + headR * 0.22f)
                    c.drawRect(rect, fill)
                    // 깃털
                    stroke.color = Theme.GOLD_LIGHT
                    stroke.strokeWidth = headR * 0.22f
                    c.drawLine(cx + headR * 0.6f, hatY - headR * 0.4f, cx + headR * 1.5f, hatY - headR * 1.35f, stroke)
                }
            }
        }

        // ── 조커: 세로 JOKER 글자 + 큰 별 + 광대 ──

        private fun joker(c: Canvas, colorful: Boolean) {
            val main = if (colorful) RED else BLACK
            val ink = main
            // 구석 세로 글자 (위 왼쪽, 아래 오른쪽 180°)
            for (pass in 0..1) {
                c.save()
                if (pass == 1) c.rotate(180f, W / 2f, H / 2f)
                text.color = ink
                text.typeface = SANS_BOLD
                text.textSize = W * 0.13f
                text.textScaleX = 1f
                for (i in 0 until 5) c.drawText(JOKER[i], W * 0.12f, W * 0.16f + i * W * 0.125f, text)
                star(c, W * 0.12f, W * 0.16f + 5 * W * 0.125f - W * 0.03f, W * 0.07f, if (colorful) Theme.GOLD else 0xFF8A8FA3.toInt())
                c.restore()
            }
            val cx = W / 2f
            // 뒤의 큰 별 (빛)
            val glow = if (colorful) 0x55FFC93C else 0x33161A2C
            fill.shader = RadialGradient(cx, H * 0.52f, W * 0.42f, glow, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, H * 0.52f, W * 0.42f, fill)
            fill.shader = null
            star(c, cx, H * 0.6f, W * 0.3f, if (colorful) Theme.GOLD else 0xFFC9CCD6.toInt())
            stroke.color = if (colorful) Theme.GOLD_DARK else 0xFF6B7080.toInt()
            stroke.strokeWidth = W * 0.012f
            starPath(cx, H * 0.6f, W * 0.3f)
            c.drawPath(tmpPath, stroke)
            // 광대 얼굴
            val headY = H * 0.43f
            val headR = W * 0.11f
            // 모자: 세 갈래 뿔 + 방울
            val lobe = if (colorful) intArrayOf(RED, 0xFF3D7BFF.toInt(), 0xFF2EA85A.toInt()) else intArrayOf(BLACK, 0xFF7A7F92.toInt(), 0xFF3A3F55.toInt())
            val tips = floatArrayOf(-0.25f, -0.04f, 0f, -0.26f, 0.25f, -0.04f) // W 배수, 머리 위 기준
            val path = Path()
            for (i in 0 until 3) {
                val tx = cx + W * tips[i * 2]
                val ty = headY - headR * 0.6f + W * tips[i * 2 + 1] - W * 0.06f
                path.reset()
                path.moveTo(cx - headR * 0.95f, headY - headR * 0.35f)
                path.quadTo((cx + tx) / 2f - W * 0.04f, ty + W * 0.02f, tx, ty)
                path.quadTo((cx + tx) / 2f + W * 0.04f, ty + W * 0.1f, cx + headR * 0.95f, headY - headR * 0.35f)
                path.close()
                fill.color = lobe[i]
                c.drawPath(path, fill)
                fill.color = Theme.GOLD
                c.drawCircle(tx, ty, W * 0.035f, fill)
            }
            // 모자 띠
            fill.color = Theme.GOLD
            rect.set(cx - headR * 1.05f, headY - headR * 0.55f, cx + headR * 1.05f, headY - headR * 0.25f)
            c.drawRoundRect(rect, headR * 0.15f, headR * 0.15f, fill)
            // 얼굴
            fill.color = 0xFFFFF1E0.toInt()
            c.drawCircle(cx, headY + headR * 0.15f, headR * 0.85f, fill)
            fill.color = main
            c.drawCircle(cx - headR * 0.32f, headY + headR * 0.02f, headR * 0.11f, fill)
            c.drawCircle(cx + headR * 0.32f, headY + headR * 0.02f, headR * 0.11f, fill)
            fill.color = RED
            c.drawCircle(cx, headY + headR * 0.25f, headR * 0.14f, fill)
            stroke.color = main
            stroke.strokeWidth = headR * 0.1f
            rect.set(cx - headR * 0.45f, headY + headR * 0.1f, cx + headR * 0.45f, headY + headR * 0.65f)
            c.drawArc(rect, 20f, 140f, false, stroke)
            // 지그재그 옷깃
            path.reset()
            val cy = headY + headR * 0.95f
            val n = 6
            val cw = headR * 2.6f
            path.moveTo(cx - cw / 2f, cy)
            for (i in 0..n) {
                val x = cx - cw / 2f + cw * i / n
                path.lineTo(x, cy)
                if (i < n) path.lineTo(x + cw / n / 2f, cy + headR * 0.55f)
            }
            path.lineTo(cx + cw / 2f, cy - headR * 0.1f)
            path.lineTo(cx - cw / 2f, cy - headR * 0.1f)
            path.close()
            fill.color = if (colorful) 0xFF3D7BFF.toInt() else 0xFF3A3F55.toInt()
            c.drawPath(path, fill)
            // 아래 JOKER 글자
            text.color = main
            text.typeface = SANS_BOLD
            text.textSize = W * 0.12f
            text.textScaleX = 1f
            c.drawText("JOKER", cx, H * 0.86f, text)
        }

        private val tmpPath = Path()

        private fun starPath(cx: Float, cy: Float, r: Float) {
            tmpPath.reset()
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) r else r * 0.45f
                val a = -Math.PI / 2 + i * Math.PI / 5
                val x = cx + (rr * cos(a)).toFloat()
                val y = cy + (rr * sin(a)).toFloat()
                if (i == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
            }
            tmpPath.close()
        }

        private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            starPath(cx, cy, r)
            fill.shader = null
            fill.color = color
            c.drawPath(tmpPath, fill)
        }

        // ── 뒷면: 남색 + 금색 격자 + 가운데 메달 ──

        fun drawBack(c: Canvas) {
            val r = W * CORNER
            rect.set(0f, 0f, W, H)
            fill.shader = LinearGradient(0f, 0f, W, H, 0xFF22337A.toInt(), Theme.BG_DEEP, Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, fill)
            fill.shader = null
            // 바깥 흰 테 (밝은 바탕에서도 카드 경계가 보이게)
            val edge = (W * 0.045f)
            stroke.color = 0xFFF4EEDC.toInt()
            stroke.strokeWidth = edge
            rect.set(edge / 2f, edge / 2f, W - edge / 2f, H - edge / 2f)
            c.drawRoundRect(rect, r - edge / 2f, r - edge / 2f, stroke)
            // 금색 안쪽 테
            val inset = W * 0.085f
            rect.set(inset, inset, W - inset, H - inset)
            val ir = W * 0.04f
            // 격자 무늬 (안쪽 틀로 자름)
            c.save()
            c.clipRect(rect)
            stroke.color = 0x80FFC93C.toInt()
            stroke.strokeWidth = (W * 0.012f).coerceAtLeast(1f)
            val step = W * 0.115f
            var d = -H
            while (d < W + H) {
                c.drawLine(d, 0f, d + H, H, stroke)
                c.drawLine(d + H, 0f, d, H, stroke)
                d += step
            }
            // 격자 칸마다 작은 점
            fill.color = 0xB3FFC93C.toInt()
            var gy = H / 2f - step * 8
            var row = 0
            while (gy < H) {
                var gx = W / 2f + (if (row % 2 == 0) 0f else step / 2f) - step * 6
                while (gx < W) {
                    c.drawCircle(gx, gy, W * 0.012f, fill)
                    gx += step
                }
                gy += step / 2f
                row++
            }
            c.restore()
            stroke.color = Theme.GOLD
            stroke.strokeWidth = (W * 0.022f).coerceAtLeast(1f)
            c.drawRoundRect(rect, ir, ir, stroke)
            // 가운데 메달: 남색 원 + 금테 + 8각 별
            val cx = W / 2f
            val cy = H / 2f
            val mr = W * 0.21f
            fill.color = Theme.BG_DEEP
            c.drawCircle(cx, cy, mr, fill)
            stroke.strokeWidth = W * 0.02f
            c.drawCircle(cx, cy, mr, stroke)
            stroke.strokeWidth = (W * 0.008f).coerceAtLeast(1f)
            c.drawCircle(cx, cy, mr * 0.82f, stroke)
            fill.color = Theme.GOLD
            val sq = mr * 0.5f
            c.save(); c.translate(cx, cy)
            rect.set(-sq, -sq, sq, sq)
            c.drawRect(rect, fill)
            c.rotate(45f)
            c.drawRect(rect, fill)
            c.restore()
            fill.color = Theme.BG_DEEP
            c.drawCircle(cx, cy, mr * 0.28f, fill)
            fill.color = Theme.GOLD_LIGHT
            c.drawCircle(cx, cy, mr * 0.14f, fill)
            // 위아래 작은 다이아 장식
            for (sgn in intArrayOf(-1, 1)) {
                val dy = cy + sgn * (mr + W * 0.12f)
                tmpPath.reset()
                tmpPath.moveTo(cx, dy - W * 0.06f)
                tmpPath.lineTo(cx + W * 0.04f, dy)
                tmpPath.lineTo(cx, dy + W * 0.06f)
                tmpPath.lineTo(cx - W * 0.04f, dy)
                tmpPath.close()
                fill.color = Theme.GOLD
                c.drawPath(tmpPath, fill)
            }
        }

        companion object {
            private val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            private val SANS_BOLD: Typeface = Typeface.DEFAULT_BOLD
            private val JOKER = arrayOf("J", "O", "K", "E", "R")

            /** 숫자 2..10 의 무늬 위치: (x / W, 세로 비율 0..1) */
            private val PIPS: Array<FloatArray> = run {
                val l = 0.345f
                val m = 0.5f
                val r = 0.655f
                arrayOf(
                    floatArrayOf(m, 0f, m, 1f),
                    floatArrayOf(m, 0f, m, 0.5f, m, 1f),
                    floatArrayOf(l, 0f, r, 0f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, m, 0.5f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, l, 0.5f, r, 0.5f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, m, 0.25f, l, 0.5f, r, 0.5f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, m, 0.25f, l, 0.5f, r, 0.5f, m, 0.75f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, l, 1f / 3f, r, 1f / 3f, m, 0.5f, l, 2f / 3f, r, 2f / 3f, l, 1f, r, 1f),
                    floatArrayOf(l, 0f, r, 0f, m, 1f / 6f, l, 1f / 3f, r, 1f / 3f, l, 2f / 3f, r, 2f / 3f, m, 5f / 6f, l, 1f, r, 1f),
                )
            }

            // 무늬 모양: 가운데 (0,0), 높이 1 (위 -0.5 ~ 아래 0.5)

            private fun spadePath(): Path = Path().apply {
                moveTo(0f, -0.5f)
                cubicTo(0.12f, -0.33f, 0.48f, -0.16f, 0.48f, 0.07f)
                cubicTo(0.48f, 0.27f, 0.25f, 0.34f, 0.06f, 0.2f)
                quadTo(0.08f, 0.4f, 0.2f, 0.5f)
                lineTo(-0.2f, 0.5f)
                quadTo(-0.08f, 0.4f, -0.06f, 0.2f)
                cubicTo(-0.25f, 0.34f, -0.48f, 0.27f, -0.48f, 0.07f)
                cubicTo(-0.48f, -0.16f, -0.12f, -0.33f, 0f, -0.5f)
                close()
            }

            private fun heartPath(): Path = Path().apply {
                moveTo(0f, 0.47f)
                cubicTo(-0.16f, 0.3f, -0.5f, 0.08f, -0.5f, -0.17f)
                cubicTo(-0.5f, -0.4f, -0.2f, -0.52f, 0f, -0.28f)
                cubicTo(0.2f, -0.52f, 0.5f, -0.4f, 0.5f, -0.17f)
                cubicTo(0.5f, 0.08f, 0.16f, 0.3f, 0f, 0.47f)
                close()
            }

            private fun diamondPath(): Path = Path().apply {
                moveTo(0f, -0.5f)
                quadTo(0.16f, -0.18f, 0.38f, 0f)
                quadTo(0.16f, 0.18f, 0f, 0.5f)
                quadTo(-0.16f, 0.18f, -0.38f, 0f)
                quadTo(-0.16f, -0.18f, 0f, -0.5f)
                close()
            }

            private fun clubPath(): Path = Path().apply {
                val r = 0.205f
                addCircle(0f, -0.27f, r, Path.Direction.CW)
                addCircle(-0.25f, 0.05f, r, Path.Direction.CW)
                addCircle(0.25f, 0.05f, r, Path.Direction.CW)
                addCircle(0f, -0.02f, 0.12f, Path.Direction.CW)
                moveTo(-0.05f, 0.05f)
                quadTo(-0.06f, 0.38f, -0.2f, 0.5f)
                lineTo(0.2f, 0.5f)
                quadTo(0.06f, 0.38f, 0.05f, 0.05f)
                close()
            }
        }
    }
}
