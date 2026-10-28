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

    /** 최종 분류 (단원 19): 보드 14 · 카드·두뇌 18 · 순발력 23 · 손맛·타이밍 16 · 파티 7 = 78종 */
    private val finalCatalog: Map<GameCategory, List<String>> = mapOf(
        GameCategory.BOARD to listOf("marble", "yut", "yacht", "liardice", "boxes",
            "snakeladder", "ludo", "horserace", "janggi", "umul", "reversi", "polyblock", "bingo", "flood"),
        GameCategory.CARD_BRAIN to listOf("onecard", "oldmaid", "blackjack", "memory", "gomoku", "connect4", "baseball", "updown",
            "highlow", "sevens", "doubt", "codetile", "colorcode", "minefield", "matches", "avoidbid", "simon", "shutbox"),
        GameCategory.REFLEX to listOf("reaction", "tug", "redlight", "mole", "stroop", "sequence", "tensec", "rps", "balloon", "math",
            "speed", "airhockey", "spotdiff", "oddone", "rhythm", "jumprope", "bobber", "count", "digitmemo", "crown", "eggcatch", "sprint", "traceshape"),
        GameCategory.TIMING to listOf("alkkagi", "pirate", "stack", "ddakji",
            "curling", "marbles", "minigolf", "bowling", "spintop", "artillery", "darts", "archery", "tuho", "basket", "jegi", "gonggi"),
        GameCategory.PARTY to listOf("liar", "bomb", "forehead", "drawing", "chosung", "clap369", "charades"),
    )

    /** 단원 19 사양의 게임 이름 그대로 */
    private val finalNames: Map<GameCategory, List<String>> = mapOf(
        GameCategory.BOARD to listOf("시우 마블", "윷놀이", "야추 주사위", "거짓말 주사위", "땅따먹기", "뱀과 사다리", "말판 레이스",
            "주사위 경마", "장기", "우물고누", "뒤집기 돌", "조각 배치 대결", "빙고", "색 번지기 점령"),
        GameCategory.CARD_BRAIN to listOf("원카드", "도둑잡기", "블랙잭", "짝 맞추기", "오목", "사목 떨어뜨리기", "숫자 야구", "폭탄 숫자 업다운",
            "하이로우", "7 이어붙이기", "의심 카드", "숨은 숫자 타일", "색 코드 맞히기", "지뢰 피하기", "성냥개비 가져가기",
            "피하기 경매", "패턴 기억 릴레이", "숫자 닫기"),
        GameCategory.REFLEX to listOf("반응속도 결투", "연타 줄다리기", "무궁화 꽃이 피었습니다", "두더지 잡기 대결", "색깔 함정", "숫자 순서 터치",
            "딱 10초", "가위바위보 토너먼트", "풍선 치킨 게임", "암산 대결", "스피드 카드", "에어하키", "틀린 그림 찾기 대결",
            "하나만 다른 모양", "리듬 노트", "줄넘기 생존", "낚시 찌 대결", "몇 개였지?", "숫자 순간 기억", "왕관 쟁탈",
            "계란 받기", "100m 달리기", "도형 따라 그리기"),
        GameCategory.TIMING to listOf("알까기", "해적 룰렛", "탑 쌓기", "딱지치기", "컬링", "구슬치기", "미니 골프", "볼링", "팽이 대결", "포탄 대결",
            "다트", "양궁", "투호", "농구 슛", "제기차기", "공기놀이"),
        GameCategory.PARTY to listOf("라이어 게임", "폭탄 돌리기", "이마 퀴즈", "그림 맞히기", "초성 퀴즈", "369 박수", "몸으로 말해요"),
    )

    @Test
    fun `카테고리 분류 - 등록된 게임은 최종 분류와 같은 카테고리, 도구 3종`() {
        assertEquals(14, finalCatalog[GameCategory.BOARD]!!.size)
        assertEquals(18, finalCatalog[GameCategory.CARD_BRAIN]!!.size)
        assertEquals(23, finalCatalog[GameCategory.REFLEX]!!.size)
        assertEquals(16, finalCatalog[GameCategory.TIMING]!!.size)
        assertEquals(7, finalCatalog[GameCategory.PARTY]!!.size)
        assertEquals(78, finalCatalog.values.flatten().toSet().size)
        val categoryOf = finalCatalog.flatMap { (c, ids) -> ids.map { it to c } }.toMap()
        for (m in GameRegistry.games) assertEquals(m.id, categoryOf[m.id], m.category)
        // 1차 34종은 모두 등록
        for (id in listOf("marble", "yut", "yacht", "liardice", "boxes", "onecard", "oldmaid", "blackjack", "memory", "gomoku", "connect4",
            "baseball", "updown", "reaction", "tug", "redlight", "mole", "stroop", "sequence", "tensec", "rps", "balloon", "math",
            "alkkagi", "pirate", "stack", "ddakji", "liar", "bomb", "forehead", "drawing", "chosung", "clap369", "charades")) {
            assertTrue(id, GameRegistry.byId(id) != null)
        }
        // 단원 19: 등록된 게임이 최종 78종과 정확히 같음
        assertEquals("등록 = 최종 78종", categoryOf.keys, GameRegistry.games.map { it.id }.toSet())
        for ((c, ids) in finalCatalog) assertEquals(c.label, ids.size, GameRegistry.inCategory(c).size)
        for ((c, names) in finalNames) assertEquals(c.label, names.toSet(), GameRegistry.inCategory(c).map { it.name }.toSet())
        // 허브 탭 수 = 카테고리별 등록 수
        for (c in GameCategory.values()) assertEquals(c.label, GameRegistry.games.count { it.category == c }, GameRegistry.inCategory(c).size)
        assertEquals("id 중복 없음", GameRegistry.games.size, GameRegistry.games.map { it.id }.toSet().size)
        assertEquals("이름 중복 없음", GameRegistry.games.size, GameRegistry.games.map { it.name }.toSet().size)
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
