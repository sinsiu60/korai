package com.siwoo.party.games.spotdiff

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 장면에 쓰는 색 (ARGB). 그림은 화면 모듈이 이 값 그대로 칠한다. */
object Palette {
    const val RED = 0xFFE53935.toInt()
    const val ORANGE = 0xFFFB8C00.toInt()
    const val YELLOW = 0xFFFDD835.toInt()
    const val GREEN = 0xFF43A047.toInt()
    const val DARK_GREEN = 0xFF1B5E20.toInt()
    const val LIGHT_GREEN = 0xFFAED581.toInt()
    const val BLUE = 0xFF1E88E5.toInt()
    const val SKYBLUE = 0xFF81D4FA.toInt()
    const val PURPLE = 0xFF8E24AA.toInt()
    const val PINK = 0xFFF06292.toInt()
    const val BROWN = 0xFF8D6E63.toInt()
    const val WHITE = 0xFFFAFAFA.toInt()
    const val GRAY = 0xFF78909C.toInt()
    const val BLACK = 0xFF37474F.toInt()
    const val NAVY = 0xFF283593.toInt()

    /** 두 색의 RGB 거리 (0..441) */
    fun dist(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}

/**
 * 장면에 놓는 물체 종류. base 크기는 그림 좌표 (배율 1).
 * sky = 하늘에 떠 있는 것(가운데 기준), 아니면 땅에 서 있는 것(바닥 기준).
 * colors = 처음 칠할 수 있는 주 색, changeTo = 색 바꾸기 차이에서 고를 수 있는 색.
 * canDiffer = 차이로 쓸 수 있는지 (새·꽃은 너무 작아서 장식만).
 */
enum class Kind(
    val label: String,
    val baseW: Float,
    val baseH: Float,
    val sky: Boolean,
    val colors: IntArray,
    val changeTo: IntArray,
    val accents: IntArray,
    val canDiffer: Boolean,
) {
    HOUSE("집", 86f, 86f, false,
        intArrayOf(Palette.YELLOW, Palette.PINK, Palette.SKYBLUE, Palette.WHITE),
        intArrayOf(Palette.YELLOW, Palette.PINK, Palette.SKYBLUE, Palette.WHITE, Palette.ORANGE, Palette.LIGHT_GREEN),
        intArrayOf(Palette.RED, Palette.BLUE, Palette.BROWN), true),
    TREE("나무", 52f, 88f, false,
        intArrayOf(Palette.GREEN, Palette.DARK_GREEN),
        intArrayOf(Palette.ORANGE, Palette.RED, Palette.YELLOW),
        intArrayOf(Palette.BROWN), true),
    PINE("전나무", 46f, 88f, false,
        intArrayOf(Palette.DARK_GREEN, Palette.GREEN),
        intArrayOf(Palette.ORANGE, Palette.YELLOW, Palette.WHITE),
        intArrayOf(Palette.BROWN), true),
    SUN("해", 50f, 50f, true,
        intArrayOf(Palette.YELLOW),
        intArrayOf(Palette.RED, Palette.PURPLE, Palette.PINK),
        intArrayOf(Palette.ORANGE), true),
    CLOUD("구름", 80f, 38f, true,
        intArrayOf(Palette.WHITE),
        intArrayOf(Palette.GRAY, Palette.PINK, Palette.PURPLE),
        intArrayOf(Palette.WHITE), true),
    BALLOON("풍선", 28f, 52f, true,
        intArrayOf(Palette.RED, Palette.BLUE, Palette.YELLOW, Palette.PURPLE, Palette.GREEN),
        intArrayOf(Palette.RED, Palette.BLUE, Palette.YELLOW, Palette.PURPLE, Palette.GREEN),
        intArrayOf(Palette.WHITE), true),
    BIRD("새", 28f, 14f, true,
        intArrayOf(Palette.BLACK),
        intArrayOf(Palette.RED),
        intArrayOf(Palette.BLACK), false),
    CAR("자동차", 76f, 38f, false,
        intArrayOf(Palette.RED, Palette.BLUE, Palette.YELLOW, Palette.GREEN, Palette.WHITE),
        intArrayOf(Palette.RED, Palette.BLUE, Palette.YELLOW, Palette.GREEN, Palette.WHITE),
        intArrayOf(Palette.BLACK), true),
    FENCE("울타리", 80f, 30f, false,
        intArrayOf(Palette.BROWN, Palette.WHITE),
        intArrayOf(Palette.BROWN, Palette.WHITE, Palette.RED, Palette.BLUE),
        intArrayOf(Palette.BROWN), true),
    PERSON("사람", 26f, 54f, false,
        intArrayOf(Palette.RED, Palette.BLUE, Palette.GREEN, Palette.YELLOW, Palette.PURPLE),
        intArrayOf(Palette.RED, Palette.BLUE, Palette.GREEN, Palette.YELLOW, Palette.PURPLE),
        intArrayOf(Palette.NAVY, Palette.BROWN, Palette.BLACK), true),
    FLOWER("꽃", 20f, 30f, false,
        intArrayOf(Palette.RED, Palette.YELLOW, Palette.PINK, Palette.PURPLE),
        intArrayOf(Palette.RED, Palette.YELLOW, Palette.PINK, Palette.PURPLE),
        intArrayOf(Palette.GREEN), false),
    ;

    /** from 과 확실히 다른 바꿀 색 후보 */
    fun farColors(from: Int): List<Int> = changeTo.filter { Palette.dist(it, from) >= GameConfig.MIN_COLOR_DIST }
}

/** 장면 속 물체 하나. (cx, cy) = 경계 상자 가운데, w×h = 상자 크기 (그림 좌표) */
class SceneObject(
    val id: Int,
    val kind: Kind,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val color: Int,
    val accent: Int,
    /** 좌우 뒤집어 그리기 (사람·자동차·새 방향) */
    val flip: Boolean,
) {
    val left: Float get() = cx - w / 2f
    val right: Float get() = cx + w / 2f
    val top: Float get() = cy - h / 2f
    val bottom: Float get() = cy + h / 2f

    /** 다른 물체 상자와 gap 이내로 붙거나 겹치는지 */
    fun overlaps(o: SceneObject, gap: Float): Boolean =
        left - gap < o.right && o.left - gap < right && top - gap < o.bottom && o.top - gap < bottom

    fun inside(w0: Float, h0: Float, margin: Float): Boolean =
        left >= margin && top >= margin && right <= w0 - margin && bottom <= h0 - margin

    /** 크기 바꾼 복사본: 땅 물체는 바닥을, 하늘 물체는 가운데를 그대로 둔다 */
    fun resized(f: Float): SceneObject {
        val nw = w * f
        val nh = h * f
        val ncy = if (kind.sky) cy else bottom - nh / 2f
        return SceneObject(id, kind, cx, ncy, nw, nh, color, accent, flip)
    }

    fun recolored(c: Int): SceneObject = SceneObject(id, kind, cx, cy, w, h, c, accent, flip)
}

/** 차이 종류 */
enum class DiffType(val label: String) {
    COLOR("색이 바뀜"),
    REMOVED("사라짐"),
    SIZE("크기가 바뀜"),
}

/**
 * 차이 하나: 왼쪽(원래) 물체 original 이 오른쪽에서 changed 로 바뀜 (사라짐이면 null).
 * 영역 = 두 상자를 합친 상자. 동그라미·판정은 그 가운데 기준.
 */
class Difference(val index: Int, val type: DiffType, val original: SceneObject, val changed: SceneObject?) {
    val left: Float = if (changed == null) original.left else min(original.left, changed.left)
    val top: Float = if (changed == null) original.top else min(original.top, changed.top)
    val right: Float = if (changed == null) original.right else max(original.right, changed.right)
    val bottom: Float = if (changed == null) original.bottom else max(original.bottom, changed.bottom)
    val cx: Float = (left + right) / 2f
    val cy: Float = (top + bottom) / 2f
    /** 그림에 그리는 동그라미 반지름 */
    val radius: Float = max(max(right - left, bottom - top) * 0.5f + 4f, 16f)
    /** 정답으로 인정하는 반경 */
    val hitRadius: Float = max(max(right - left, bottom - top) * 0.5f, GameConfig.MIN_HIT_RADIUS) + GameConfig.HIT_SLOP

    fun hit(x: Float, y: Float): Boolean = dist2(x, y) <= hitRadius * hitRadius

    fun dist2(x: Float, y: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy
    }

    /** 다른 차이 영역과 겹치는지 */
    fun overlaps(o: Difference): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * 장면 하나: 왼쪽 그림 = left, 오른쪽 그림 = right (차이 5곳 반영), 배경 테마 번호.
 * 물체 목록은 그리는 순서 (하늘 → 먼 땅 → 가까운 땅).
 */
class Scene(
    val seed: Long,
    val theme: Int,
    val left: List<SceneObject>,
    val right: List<SceneObject>,
    val differences: List<Difference>,
) {
    /**
     * (x, y)(그림 좌표)에 있는 아직 못 찾은 차이 번호, 없으면 -1.
     * 판정 반경이 겹치면 가운데가 가장 가까운 차이.
     */
    fun findAt(x: Float, y: Float, found: BooleanArray): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (d in differences) {
            if (found[d.index] || !d.hit(x, y)) continue
            val dd = d.dist2(x, y)
            if (dd < bestD) {
                bestD = dd
                best = d.index
            }
        }
        return best
    }

    companion object {
        /** 배경 테마 수 (0 맑은 낮, 1 노을, 2 봄 들판) */
        const val THEMES = 3
    }
}

/**
 * 장면 자동 생성 (같은 seed → 같은 장면).
 * 1) 집·나무·해·구름·사람·꽃·자동차·울타리·새·풍선을 겹치지 않게 12~20개 놓는다.
 * 2) 충분히 큰 물체 5개를 골라 오른쪽 그림에서 색 바꿈 / 사라짐 / 크기 변경(±40%).
 *    키운 물체도 다른 물체·차이와 겹치지 않고 그림 밖으로 나가지 않는다.
 */
object SceneGenerator {

    fun generate(seed: Long): Scene {
        val rnd = kotlin.random.Random(seed)
        repeat(100) {
            tryGenerate(rnd, seed)?.let { return it }
        }
        error("장면을 만들지 못함: $seed")
    }

    private fun tryGenerate(rnd: kotlin.random.Random, seed: Long): Scene? {
        val plan = ArrayList<Kind>()
        repeat(1 + rnd.nextInt(2)) { plan.add(Kind.HOUSE) }
        if (rnd.nextFloat() < 0.85f) plan.add(Kind.SUN)
        if (rnd.nextFloat() < 0.7f) plan.add(Kind.CAR)
        plan.add(Kind.FENCE)
        repeat(2 + rnd.nextInt(3)) { plan.add(if (rnd.nextBoolean()) Kind.TREE else Kind.PINE) }
        repeat(2 + rnd.nextInt(2)) { plan.add(Kind.CLOUD) }
        if (rnd.nextFloat() < 0.5f) plan.add(Kind.BALLOON)
        repeat(1 + rnd.nextInt(3)) { plan.add(Kind.PERSON) }
        repeat(2 + rnd.nextInt(3)) { plan.add(Kind.FLOWER) }
        repeat(1 + rnd.nextInt(3)) { plan.add(Kind.BIRD) }

        val placed = ArrayList<SceneObject>()
        for (k in plan) {
            if (placed.size >= GameConfig.MAX_OBJECTS) break
            place(rnd, k, placed)?.let { placed.add(it) }
        }
        // 모자라면 작은 것들로 채운다
        var extra = 0
        while (placed.size < GameConfig.MIN_OBJECTS && extra < 40) {
            extra++
            val k = FILLERS[rnd.nextInt(FILLERS.size)]
            place(rnd, k, placed)?.let { placed.add(it) }
        }
        if (placed.size < GameConfig.MIN_OBJECTS) return null

        // 그리는 순서: 하늘 → 땅(먼 것부터)
        val ordered = placed.sortedWith(compareBy<SceneObject>({ if (it.kind.sky) 0 else 1 }, { if (it.kind.sky) 0f else it.bottom }))
        val left = ArrayList<SceneObject>(ordered.size)
        for ((i, o) in ordered.withIndex()) left.add(SceneObject(i, o.kind, o.cx, o.cy, o.w, o.h, o.color, o.accent, o.flip))

        val diffs = makeDifferences(rnd, left) ?: return null
        val right = ArrayList<SceneObject>(left.size)
        for (o in left) {
            val d = diffs.firstOrNull { it.original.id == o.id }
            if (d == null) right.add(o) else d.changed?.let { right.add(it) }
        }
        return Scene(seed, rnd.nextInt(Scene.THEMES), left, right, diffs)
    }

    private fun place(rnd: kotlin.random.Random, k: Kind, placed: List<SceneObject>): SceneObject? {
        val scale = if (k == Kind.HOUSE) 0.95f + rnd.nextFloat() * 0.25f else 0.85f + rnd.nextFloat() * 0.3f
        val w = k.baseW * scale
        val h = k.baseH * scale
        val color = k.colors[rnd.nextInt(k.colors.size)]
        val accent = k.accents[rnd.nextInt(k.accents.size)]
        val flip = rnd.nextBoolean()
        val margin = 6f
        repeat(80) {
            val cx = margin + w / 2f + rnd.nextFloat() * (GameConfig.SCENE_W - 2 * margin - w)
            val cy = if (k.sky) {
                val lo = margin + h / 2f
                val hi = GameConfig.HORIZON_Y - 14f - h / 2f
                if (k == Kind.SUN) lo + rnd.nextFloat() * (hi - lo) * 0.45f else lo + rnd.nextFloat() * (hi - lo)
            } else {
                val bottom = GameConfig.HORIZON_Y + 14f + rnd.nextFloat() * (GameConfig.SCENE_H - margin - GameConfig.HORIZON_Y - 14f)
                bottom - h / 2f
            }
            val o = SceneObject(-1, k, cx, cy, w, h, color, accent, flip)
            if (o.inside(GameConfig.SCENE_W, GameConfig.SCENE_H, 2f) && placed.none { it.overlaps(o, GameConfig.OBJECT_GAP) }) return o
        }
        return null
    }

    /** 차이가 될 수 있을 만큼 큰 물체인지 */
    fun eligible(o: SceneObject): Boolean =
        o.kind.canDiffer && min(o.w, o.h) >= GameConfig.DIFF_MIN_SIDE && o.w * o.h >= GameConfig.DIFF_MIN_AREA

    private fun makeDifferences(rnd: kotlin.random.Random, objs: List<SceneObject>): List<Difference>? {
        val n = GameConfig.DIFFS_PER_SCENE
        val candidates = objs.filter { eligible(it) }.shuffled(rnd).toMutableList()
        if (candidates.size < n) return null
        // 종류가 고루 나오게: 색·사라짐·크기 하나씩 + 나머지 무작위
        val types = arrayListOf(DiffType.COLOR, DiffType.REMOVED, DiffType.SIZE)
        while (types.size < n) types.add(DiffType.values()[rnd.nextInt(3)])
        types.shuffle(rnd)
        // 지금 오른쪽 상태 (바뀐 물체 반영) — 키운 물체가 다른 것과 겹치지 않는지 확인용
        val current = HashMap<Int, SceneObject?>()
        val out = ArrayList<Difference>(n)
        for (type in types) {
            val obj = candidates.removeAt(0)
            val d = when (type) {
                DiffType.REMOVED -> Difference(out.size, type, obj, null)
                DiffType.COLOR -> {
                    val far = obj.kind.farColors(obj.color)
                    if (far.isEmpty()) Difference(out.size, DiffType.REMOVED, obj, null)
                    else Difference(out.size, type, obj, obj.recolored(far[rnd.nextInt(far.size)]))
                }
                DiffType.SIZE -> {
                    val grown = obj.resized(GameConfig.GROW)
                    val fits = grown.inside(GameConfig.SCENE_W, GameConfig.SCENE_H, 2f) && objs.none { o ->
                        o.id != obj.id && (o.overlaps(grown, GameConfig.OBJECT_GAP) || current[o.id]?.overlaps(grown, GameConfig.OBJECT_GAP) == true)
                    }
                    val tryGrow = rnd.nextBoolean()
                    Difference(out.size, type, obj, if (fits && tryGrow) grown else obj.resized(GameConfig.SHRINK))
                }
            }
            current[obj.id] = d.changed
            out.add(d)
        }
        return out
    }

    private val FILLERS = arrayOf(Kind.FLOWER, Kind.BIRD, Kind.PERSON, Kind.TREE, Kind.CLOUD)
}
