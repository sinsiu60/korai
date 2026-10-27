package com.siwoo.party.games.drawing

import com.siwoo.party.common.Json

/**
 * 그림 맞히기 제시어 (그리기 쉬운 단어).
 * - CATEGORIES: 동물·음식·물건·자연·탈것·장소, DEFAULT: 카테고리마다 30개
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences "drawing" 에 JSON 으로 저장 (encode / decode)
 */
object DrawingWords {
    val CATEGORIES: List<String> = listOf("동물", "음식", "물건", "자연", "탈것", "장소")

    val DEFAULT: List<List<String>> = listOf(
        listOf(
            "고양이", "강아지", "토끼", "물고기", "새", "거북이", "뱀", "코끼리", "기린", "돼지",
            "오리", "닭", "문어", "달팽이", "나비", "거미", "개구리", "사자", "펭귄", "고래",
            "상어", "게", "꿀벌", "쥐", "말", "소", "양", "원숭이", "곰", "부엉이",
        ),
        listOf(
            "사과", "바나나", "수박", "포도", "딸기", "당근", "피자", "햄버거", "아이스크림", "케이크",
            "도넛", "김밥", "라면", "계란후라이", "핫도그", "사탕", "빵", "우유", "치킨", "떡볶이",
            "버섯", "옥수수", "파인애플", "체리", "레몬", "감자튀김", "초밥", "팝콘", "쿠키", "컵라면",
        ),
        listOf(
            "우산", "안경", "시계", "가위", "칫솔", "연필", "의자", "책", "컵", "열쇠",
            "전구", "모자", "신발", "양말", "가방", "숟가락", "포크", "거울", "텔레비전", "휴대폰",
            "카메라", "풍선", "선풍기", "침대", "냉장고", "망치", "왕관", "리본", "편지", "선물상자",
        ),
        listOf(
            "해", "달", "별", "구름", "비", "눈사람", "무지개", "번개", "나무", "꽃",
            "산", "바다", "파도", "화산", "섬", "선인장", "나뭇잎", "불", "폭포", "사막",
            "해바라기", "장미", "눈송이", "바람", "돌", "강", "동굴", "지구", "토네이도", "별똥별",
        ),
        listOf(
            "자동차", "버스", "기차", "비행기", "배", "자전거", "오토바이", "헬리콥터", "로켓", "잠수함",
            "트럭", "택시", "소방차", "경찰차", "구급차", "열기구", "킥보드", "스케이트보드", "썰매", "유모차",
            "지하철", "요트", "트랙터", "포클레인", "우주선", "낙하산", "케이블카", "마차", "카누", "롤러코스터",
        ),
        listOf(
            "집", "학교", "병원", "성", "교회", "놀이터", "수영장", "공원", "다리", "등대",
            "텐트", "피라미드", "에펠탑", "빌딩", "공항", "동물원", "놀이공원", "도서관", "운동장", "화장실",
            "감옥", "우체국", "은행", "시장", "편의점", "캠핑장", "빵집", "영화관", "경찰서", "소방서",
        ),
    )

    /** 카테고리 하나 정리: 공백·빈 줄·중복 제거, 개수·길이 제한, 5개보다 적으면 기본 단어로 채움 */
    fun sanitize(category: Int, list: List<String>): List<String> {
        val out = ArrayList<String>()
        for (s in list) {
            val t = s.trim().take(GameConfig.MAX_WORD_LEN).trim()
            if (t.isEmpty() || t in out) continue
            out.add(t)
            if (out.size >= GameConfig.MAX_WORDS) break
        }
        if (out.size < GameConfig.MIN_WORDS) {
            for (d in DEFAULT[category]) {
                if (out.size >= GameConfig.MIN_WORDS) break
                if (d !in out) out.add(d)
            }
        }
        return out
    }

    fun sanitizeAll(lists: List<List<String>>): List<List<String>> =
        CATEGORIES.indices.map { sanitize(it, lists.getOrNull(it) ?: DEFAULT[it]) }

    fun encode(lists: List<List<String>>): String {
        val m = LinkedHashMap<String, Any?>()
        for ((k, name) in CATEGORIES.withIndex()) m[name] = lists.getOrNull(k) ?: DEFAULT[k]
        return Json.write(m)
    }

    fun decode(text: String?): List<List<String>> {
        if (text.isNullOrBlank()) return DEFAULT
        val m = try {
            Json.parse(text) as? Map<*, *>
        } catch (e: RuntimeException) {
            null
        } ?: return DEFAULT
        return CATEGORIES.mapIndexed { k, name ->
            val v = m[name] as? List<*>
            if (v == null) DEFAULT[k] else sanitize(k, v.filterIsInstance<String>())
        }
    }
}
