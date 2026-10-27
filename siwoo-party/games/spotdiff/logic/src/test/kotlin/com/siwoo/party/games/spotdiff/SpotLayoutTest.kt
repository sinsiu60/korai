package com.siwoo.party.games.spotdiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 배치: 구역·버저·그림이 겹치지 않고, 좌표 변환이 맞는지 */
class SpotLayoutTest {

    private val dp = 2f
    private val sizes = listOf(1280f to 720f, 1600f to 720f, 1920f to 1080f, 2400f to 1080f)

    @Test
    fun `인원별 구역과 방향`() {
        val l2 = SpotLayout(2, 1280f, 720f, dp)
        assertEquals(listOf(0f, 0f), l2.panels.map { it.rotation })
        assertTrue(l2.panels[0].left == 0f && l2.panels[1].right == 1280f)
        val l3 = SpotLayout(3, 1280f, 720f, dp)
        assertEquals(listOf(180f, 180f, 0f), l3.panels.map { it.rotation })
        assertEquals(720f, l3.panels[2].bottom)
        assertEquals(640f, l3.panels[2].centerX, 0.01f)
        val l4 = SpotLayout(4, 1280f, 720f, dp)
        assertEquals(listOf(180f, 180f, 0f, 0f), l4.panels.map { it.rotation })
        // 위쪽(180°) 구역의 버저는 화면 위쪽(그 사람 쪽), 아래 구역은 아래쪽
        assertTrue(l4.buzzY[0] < l4.panels[0].centerY)
        assertTrue(l4.buzzY[2] > l4.panels[2].centerY)
        assertTrue(l3.buzzY[1] < l3.panels[1].centerY)
        // 3인 아래 구역은 가로로 넓음 → 버저가 오른쪽
        assertTrue(l3.buzzX[2] > l3.panels[2].centerX)
    }

    @Test
    fun `버저는 자기 구역 안, 그림은 구역과 겹치지 않음`() {
        for ((w, h) in sizes) for (n in 2..4) {
            val l = SpotLayout(n, w, h, dp)
            assertEquals(n, l.panels.size)
            for (i in 0 until n) {
                val z = l.panels[i]
                val r = l.buzzR[i]
                assertTrue("$n 인 버저 $i 크기", r >= 30f * dp)
                assertTrue(l.buzzX[i] - r >= z.left && l.buzzX[i] + r <= z.right)
                assertTrue(l.buzzY[i] - r >= z.top && l.buzzY[i] + r <= z.bottom)
                assertEquals(i, l.panelAt(l.buzzX[i], l.buzzY[i]))
                for (k in 0..1) {
                    val picOverlap = l.picLeft[k] < z.right && z.left < l.picLeft[k] + l.picW && l.picTop < z.bottom && z.top < l.picTop + l.picH
                    assertTrue("$n 인 그림 $k 와 구역 $i 겹침", !picOverlap)
                }
                assertTrue("안내줄도 구역과 안 겹침", !(l.headerLeft < z.right && z.left < l.headerRight && l.statusTop < z.bottom && z.top < l.statusBottom))
            }
            for (a in 0 until n) for (b in a + 1 until n) {
                val p = l.panels[a]
                val q = l.panels[b]
                assertTrue(!(p.left < q.right && q.left < p.right && p.top < q.bottom && q.top < p.bottom))
            }
            // 그림 두 장은 같은 크기, 비율 유지, 화면 안, 넉넉한 크기
            assertEquals(l.picW * GameConfig.SCENE_H / GameConfig.SCENE_W, l.picH, 0.01f)
            assertTrue(l.picLeft[0] + l.picW < l.picLeft[1])
            assertTrue(l.headerTop >= 0f && l.statusBottom <= h)
            assertTrue("그림 폭 ${l.picW / dp}dp", l.picW >= 190f * dp)
        }
    }

    @Test
    fun `그림 좌표 변환과 그림 판정`() {
        val l = SpotLayout(4, 1280f, 720f, dp)
        val out = FloatArray(2)
        for (pic in 0..1) {
            l.toScreen(pic, 100f, 250f, out)
            assertEquals(pic, l.pictureAt(out[0], out[1]))
            assertEquals(-1, l.panelAt(out[0], out[1]))
            l.toScene(pic, out[0], out[1], out)
            assertEquals(100f, out[0], 0.01f)
            assertEquals(250f, out[1], 0.01f)
        }
        assertEquals(-1, l.pictureAt(l.picLeft[0] - 1f, l.picTop + 10f))
        assertEquals(-1, l.pictureAt(l.picLeft[1] + 10f, l.picTop + l.picH + 1f))
        assertEquals(-1, l.pictureAt(l.picLeft[0] + l.picW + 2f, l.picTop + 10f))
    }
}
