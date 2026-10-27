package com.siwoo.party.hub

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSensor
import java.util.concurrent.TimeUnit

/**
 * 단원 11 점검: 등록된 게임 목록 (보드 5 · 카드·두뇌 8 · 순발력 10 · 손맛·타이밍 4 · 파티 존 7 = 34종 + 도구 3종),
 * 모든 게임의 규칙 카드 3장 · 아이콘 · 인원·방식·예상 시간 · 랜덤 스위치 항목,
 * 인원을 바꾸면 파티 존 카드와 파티 랜덤 후보도 바로 바뀌는지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CatalogTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun findAll(v: View, pred: (View) -> Boolean, out: MutableList<View> = ArrayList()): MutableList<View> {
        if (v.visibility != View.VISIBLE) return out
        if (pred(v)) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) findAll(v.getChildAt(i), pred, out)
        return out
    }

    @Test
    fun `카테고리별 게임 수 34종과 도구 3종`() {
        val count = GameCategory.values().associateWith { c -> GameRegistry.inCategory(c).size }
        assertEquals(5, count[GameCategory.BOARD])
        assertEquals(8, count[GameCategory.CARD_BRAIN])
        assertEquals(10, count[GameCategory.REFLEX])
        assertEquals(4, count[GameCategory.TIMING])
        assertEquals(7, count[GameCategory.PARTY])
        assertEquals(34, GameRegistry.games.size)
        assertEquals("id 중복 없음", 34, GameRegistry.games.map { it.id }.toSet().size)
        assertEquals("이름 중복 없음", 34, GameRegistry.games.map { it.name }.toSet().size)
        assertEquals(3, ToolsMenu.tools.size)
        assertTrue("도구 3종 모두 열 수 있음", ToolsMenu.tools.all { it.open != null })
    }

    @Test
    fun `모든 게임 - 규칙 3장 아이콘 인원 방식 시간`() {
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (m in GameRegistry.games) {
            assertEquals("${m.name} 규칙 카드", 3, m.rules.size)
            for (r in m.rules) {
                assertTrue("${m.name} 규칙 제목", r.title.isNotBlank())
                assertTrue("${m.name} 규칙 설명", r.text.isNotBlank())
                p.reset(); r.draw(c, 230f, 160f, p)
            }
            p.reset()
            m.drawIcon(c, 60f, 60f, 100f, p) // 그리다 예외가 나지 않아야 함
            assertTrue("${m.name} 소개", m.tagline.isNotBlank())
            assertTrue("${m.name} 인원", m.playersLabel.isNotBlank() && (2..4).any { m.supports(it) })
            assertTrue("${m.name} 예상 시간", m.minutes.isNotBlank())
            if (m.category == GameCategory.PARTY) assertTrue("${m.name} 파티 태그", m.partyTags.isNotEmpty())
        }
        bmp.recycle()
    }

    @Test
    fun `모든 게임이 랜덤 설정에 스위치로 나온다`() {
        PartySettings(app).touchPoints = 5
        SessionStore(app).save(Session(listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))))
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(600)
        RandomSettingsPopup.show(hub); idle(600)
        assertEquals(GameRegistry.games.map { it.id }.toSet(), hub.randomScreen!!.ids.toSet())
        hub.popups.dismissAll(); idle(300)
        val pz = Robolectric.buildActivity(PartyZoneActivity::class.java).setup().get()
        idle(600)
        RandomSettingsPopup.show(pz, party = true); idle(600)
        assertEquals(GameRegistry.partyGames.map { it.id }.toSet(), pz.randomScreen!!.ids.toSet())
    }

    @Test
    fun `인원을 바꾸면 파티 존 카드와 파티 랜덤 후보가 바로 바뀐다`() {
        PartySettings(app).touchPoints = 5
        // 기울기 센서가 있는 기기 (이마 퀴즈 후보 포함)
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(sm).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
        SessionStore(app).save(Session(four))
        val pz = Robolectric.buildActivity(PartyZoneActivity::class.java).setup().get()
        idle(600)
        fun state() = findAll(pz.window.decorView, { it is GameCardView }).associate { (it as GameCardView).module.id to ((it as GameCardView).unavailable == null) }
        assertEquals(GameRegistry.partyGames.associate { it.id to it.supports(4) }, state())
        assertEquals(GameRegistry.partyGames.filter { it.supports(4) }.map { it.id }, pz.partyCandidates().map { it.id })
        // 허브에서 2인으로 바꾸고 파티 존으로 돌아옴 (onResume → 다시 읽기)
        val s = SessionStore(app).load()!!
        s.replacePlayers(listOf("가람" to 0, "나래" to 1))
        SessionStore(app).save(s)
        pz.reloadSession(); idle(300)
        assertEquals(GameRegistry.partyGames.associate { it.id to it.supports(2) }, state())
        assertEquals(GameRegistry.partyGames.filter { it.supports(2) }.map { it.id }, pz.partyCandidates().map { it.id })
    }
}
