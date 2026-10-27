package com.siwoo.party.games.boxes

import kotlin.math.sqrt

/**
 * 점 격자의 번호 매기기와 기하 계산 (순수 Kotlin).
 *
 * 좌표: 점 (열 c, 줄 r) 은 (x = c, y = r) — 칸 한 변이 1.
 * - 가로선: 줄 r(0..n-1), 열 c(0..n-2) → 번호 r × (n-1) + c, (c, r)–(c+1, r)
 * - 세로선: 줄 r(0..n-2), 열 c(0..n-1) → 번호 가로선 수 + r × n + c, (c, r)–(c, r+1)
 * - 칸: 줄 r, 열 c (0..n-2) → 번호 r × (n-1) + c, 네 변 = 위·아래 가로선 + 왼쪽·오른쪽 세로선
 */
class BoxesGrid(val dots: Int) {

    init {
        require(dots >= 2)
    }

    /** 한 줄 칸 수 */
    val cells = dots - 1
    val horizontalCount = dots * cells
    val lineCount = horizontalCount * 2
    val boxCount = cells * cells

    fun isHorizontal(line: Int): Boolean = line < horizontalCount

    fun hLine(r: Int, c: Int): Int = r * cells + c
    fun vLine(r: Int, c: Int): Int = horizontalCount + r * dots + c
    fun box(r: Int, c: Int): Int = r * cells + c
    fun boxRow(box: Int): Int = box / cells
    fun boxCol(box: Int): Int = box % cells

    /** 선의 시작 점 x (열) */
    fun x0(line: Int): Float = if (isHorizontal(line)) (line % cells).toFloat() else ((line - horizontalCount) % dots).toFloat()
    /** 선의 시작 점 y (줄) */
    fun y0(line: Int): Float = if (isHorizontal(line)) (line / cells).toFloat() else ((line - horizontalCount) / dots).toFloat()
    fun x1(line: Int): Float = if (isHorizontal(line)) x0(line) + 1f else x0(line)
    fun y1(line: Int): Float = if (isHorizontal(line)) y0(line) else y0(line) + 1f
    fun midX(line: Int): Float = if (isHorizontal(line)) x0(line) + 0.5f else x0(line)
    fun midY(line: Int): Float = if (isHorizontal(line)) y0(line) else y0(line) + 0.5f

    /** 칸의 네 변 (위, 아래, 왼쪽, 오른쪽) */
    fun top(box: Int): Int = hLine(boxRow(box), boxCol(box))
    fun bottom(box: Int): Int = hLine(boxRow(box) + 1, boxCol(box))
    fun left(box: Int): Int = vLine(boxRow(box), boxCol(box))
    fun right(box: Int): Int = vLine(boxRow(box), boxCol(box) + 1)

    /** 선 양쪽의 칸 (판 가장자리면 -1). first = 위/왼쪽, second = 아래/오른쪽 */
    fun boxBefore(line: Int): Int =
        if (isHorizontal(line)) { val r = line / cells; if (r == 0) -1 else box(r - 1, line % cells) }
        else { val k = line - horizontalCount; val c = k % dots; if (c == 0) -1 else box(k / dots, c - 1) }

    fun boxAfter(line: Int): Int =
        if (isHorizontal(line)) { val r = line / cells; if (r == cells) -1 else box(r, line % cells) }
        else { val k = line - horizontalCount; val c = k % dots; if (c == cells) -1 else box(k / dots, c) }

    /** 점 (x, y) 에서 선분 line 까지 거리 */
    fun distanceToLine(line: Int, x: Float, y: Float): Float {
        val ax = x0(line)
        val ay = y0(line)
        // 선분은 축에 평행하고 길이 1
        val dx: Float
        val dy: Float
        if (isHorizontal(line)) {
            dx = if (x < ax) ax - x else if (x > ax + 1f) x - ax - 1f else 0f
            dy = y - ay
        } else {
            dx = x - ax
            dy = if (y < ay) ay - y else if (y > ay + 1f) y - ay - 1f else 0f
        }
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 터치한 곳 (x, y) 에서 가장 가까운 "아직 안 그은" 선. 없으면 -1.
     * - 선분까지 거리가 maxDist 이하인 선만 후보
     * - 가장 가까운 선분, 거의 같으면(점 근처 등) 선 가운데가 더 가까운 쪽
     * drawn[line] 이 true 인 선은 건너뛴다. 할당 없음 (터치 중 계속 불러도 됨).
     */
    fun nearestFreeLine(x: Float, y: Float, drawn: BooleanArray, maxDist: Float = GameConfig.PICK_RADIUS): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        var bestM = Float.MAX_VALUE
        // 터치 근처 줄·열만 보면 되지만 선이 최대 84개라 전부 봐도 충분히 빠르다
        for (line in 0 until lineCount) {
            if (drawn[line]) continue
            val d = distanceToLine(line, x, y)
            if (d > maxDist) continue
            val mx = midX(line) - x
            val my = midY(line) - y
            val m = mx * mx + my * my
            if (d < bestD - TIE || (d <= bestD + TIE && m < bestM)) {
                best = line
                bestD = d
                bestM = m
            }
        }
        return best
    }

    companion object {
        /** 거리가 이만큼 안쪽이면 같은 거리로 보고 선 가운데로 가린다 */
        private const val TIE = 0.02f

        /** 인원별 한 줄 점 개수: 2인 6, 3~4인 7 */
        fun dotsFor(playerCount: Int): Int =
            if (playerCount <= 2) GameConfig.BOXES_GRID_2P else GameConfig.BOXES_GRID_34P
    }
}
