package com.siwoo.party.games.bomb

import com.siwoo.party.common.Json
import kotlin.random.Random

/**
 * 폭탄 돌리기 주제 목록.
 * - DEFAULT: 기본 주제 30개 (설정의 [기본값])
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences 에 JSON 으로 저장한다 (encode / decode)
 */
object BombTopics {
    val DEFAULT: List<String> = listOf(
        "과일 이름",
        "동물 이름",
        "나라 이름",
        "ㄱ으로 시작하는 단어",
        "채소 이름",
        "직업 이름",
        "운동 종목",
        "악기 이름",
        "색깔 이름",
        "탈것 이름",
        "우리나라 도시 이름",
        "과자 이름",
        "음료 이름",
        "학교에 있는 물건",
        "부엌에 있는 물건",
        "바다에 사는 생물",
        "곤충 이름",
        "꽃 이름",
        "분식 메뉴",
        "한식 메뉴",
        "몸의 부위",
        "옷과 신발",
        "만화 캐릭터",
        "세 글자 단어",
        "ㅅ으로 시작하는 단어",
        "빨간색인 것",
        "동그란 것",
        "놀이공원에 있는 것",
        "겨울에 생각나는 것",
        "\"기\"로 끝나는 단어",
    )

    /** 앞뒤 공백 제거·빈 줄 제거·중복 제거·개수/길이 제한. 남는 게 없으면 기본 주제 */
    fun sanitize(list: List<String>): List<String> {
        val out = ArrayList<String>()
        for (s in list) {
            val t = s.trim().take(GameConfig.MAX_TOPIC_LEN).trim()
            if (t.isEmpty() || t in out) continue
            out.add(t)
            if (out.size >= GameConfig.MAX_TOPICS) break
        }
        return if (out.isEmpty()) DEFAULT else out
    }

    fun encode(list: List<String>): String = Json.write(list)

    /** 저장된 JSON → 주제 목록 (없거나 깨졌으면 기본 주제) */
    fun decode(text: String?): List<String> {
        if (text.isNullOrBlank()) return DEFAULT
        return try {
            val v = Json.parse(text) as? List<*> ?: return DEFAULT
            sanitize(v.filterIsInstance<String>())
        } catch (e: RuntimeException) {
            DEFAULT // 깨진 저장값
        }
    }
}

/**
 * 주제 뽑기: 섞은 순서대로 하나씩 꺼내 한 바퀴 돌 때까지 겹치지 않는다.
 * 주제가 라운드 수(5)보다 적으면 다 쓴 뒤 다시 섞는다 (바로 앞 주제가 연달아 나오지 않게).
 */
class TopicDeck(topics: List<String>, private val random: Random) {
    private val all: List<String> = BombTopics.sanitize(topics)
    private val order = ArrayList<String>()
    private var last: String? = null

    val size: Int get() = all.size

    fun next(): String {
        if (order.isEmpty()) {
            order.addAll(all.shuffled(random))
            // 다시 섞었을 때 직전 주제가 맨 앞이면 뒤로 보낸다
            if (order.size > 1 && order[0] == last) order.add(order.removeAt(0))
        }
        val t = order.removeAt(0)
        last = t
        return t
    }
}
