package com.siwoo.party.common.ui

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 플레이어 패널 칸: 긴 이름이 칸 밖으로 잘리지 않게 줄이거나 말줄임 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) // 글자 너비를 실제로 재야 함 (기본 모드는 글자 수를 너비로 돌려줌)
class PlayerStripTest {

    private fun strip(names: List<String>, w: Int, h: Int, vertical: Boolean): PlayerStrip {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val s = PlayerStrip(a, names.mapIndexed { i, n -> PlayerInfo(i, n, i) }, vertical)
        a.setContentView(s, FrameLayout.LayoutParams(w, h))
        s.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        s.layout(0, 0, w, h)
        return s
    }

    /** onDraw 와 같은 배치로 구한 이름 칸 너비 */
    private fun avail(t: PlayerStrip.Tile): Float {
        val sw = Theme.dpf(t.context, 2f)
        val rw = t.width - sw * 4
        val rh = t.height - sw * 4
        val cr = minOf(rh * 0.3f, rw * 0.16f)
        return rw - cr * 2.9f - sw * 3
    }

    private fun nameWidth(t: PlayerStrip.Tile, minScale: Float): Float {
        val base = minOf(t.height.toFloat(), t.width * 0.45f)
        val p = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textSize = base * 0.26f * minScale }
        return p.measureText(t.shownName)
    }

    @Test
    fun shortNamesStayAsTheyAre() {
        val s = strip(listOf("가람", "나래"), 248, 360, vertical = true)
        for (t in s.tiles) assertEquals(t.player.name, t.shownName)
    }

    @Test
    fun longNamesFitInsideTheTile() {
        // 좁은 세로 패널 (스핀탑·투호처럼 약 124dp 폭)
        val s = strip(listOf("가람", "다온이네아빠", "아주아주긴이름의플레이어"), 248, 480, vertical = true)
        assertEquals("짧은 이름은 그대로", "가람", s.tiles[0].shownName)
        for (t in s.tiles) {
            // 글자 크기를 최소(70%)까지 줄인 상태에서도 칸 안에 들어간다
            assertTrue("${t.shownName} 이 칸 너비 안", nameWidth(t, 0.7f) <= avail(t) + 0.5f)
        }
        val longest = s.tiles[2]
        assertTrue("아주 긴 이름은 말줄임", longest.shownName.endsWith("…"))
        assertTrue(longest.player.name.startsWith(longest.shownName.dropLast(1)))
    }
}
