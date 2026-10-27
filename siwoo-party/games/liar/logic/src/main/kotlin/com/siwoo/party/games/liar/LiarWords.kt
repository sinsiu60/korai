package com.siwoo.party.games.liar

import com.siwoo.party.common.Json

/**
 * 라이어 게임 제시어.
 * - CATEGORIES: 카테고리 6개 (음식·장소·동물·직업·스포츠·물건)
 * - DEFAULT: 카테고리마다 기본 단어 20개 (설정의 [기본값])
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences 에 JSON({"음식": [...], ...})으로 저장한다 (encode / decode)
 * - 정답 맞히기 보기가 8개라서 카테고리마다 단어가 8개 이상 있어야 한다 → 모자라면 기본 단어로 채운다
 */
object LiarWords {
    val CATEGORIES: List<String> = listOf("음식", "장소", "동물", "직업", "스포츠", "물건")

    val DEFAULT: List<List<String>> = listOf(
        listOf(
            "김치찌개", "떡볶이", "피자", "치킨", "라면", "비빔밥", "짜장면", "햄버거", "초밥", "삼겹살",
            "김밥", "돈가스", "냉면", "불고기", "샌드위치", "스파게티", "순대", "만두", "팥빙수", "핫도그",
        ),
        listOf(
            "학교", "병원", "놀이공원", "도서관", "영화관", "수영장", "공항", "편의점", "동물원", "바다",
            "캠핑장", "찜질방", "경찰서", "백화점", "지하철역", "박물관", "놀이터", "미용실", "빵집", "산",
        ),
        listOf(
            "사자", "호랑이", "코끼리", "기린", "펭귄", "토끼", "강아지", "고양이", "원숭이", "판다",
            "돌고래", "상어", "거북이", "하마", "캥거루", "다람쥐", "악어", "독수리", "부엉이", "고슴도치",
        ),
        listOf(
            "의사", "선생님", "경찰관", "소방관", "요리사", "가수", "축구선수", "과학자", "비행기 조종사", "간호사",
            "우주비행사", "미용사", "농부", "화가", "유튜버", "택배기사", "군인", "변호사", "제빵사", "마술사",
        ),
        listOf(
            "축구", "야구", "농구", "배구", "탁구", "테니스", "배드민턴", "수영", "태권도", "스키",
            "골프", "볼링", "피겨스케이팅", "마라톤", "유도", "양궁", "씨름", "줄넘기", "스케이트보드", "펜싱",
        ),
        listOf(
            "우산", "연필", "휴대폰", "안경", "시계", "가위", "칫솔", "냉장고", "텔레비전", "자전거",
            "베개", "거울", "지우개", "가방", "숟가락", "리모컨", "선풍기", "열쇠", "장갑", "컵",
        ),
    )

    /**
     * 카테고리 하나 정리: 앞뒤 공백 제거·빈 줄 제거·중복 제거·개수/길이 제한.
     * 남은 단어가 8개(MIN_WORDS)보다 적으면 그 카테고리의 기본 단어를 뒤에 붙여 8개를 채운다 (사용자 단어는 그대로 앞에).
     */
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

    /** 6개 카테고리 모두 정리 (모자란 카테고리는 기본 단어) */
    fun sanitizeAll(lists: List<List<String>>): List<List<String>> =
        CATEGORIES.indices.map { sanitize(it, lists.getOrNull(it) ?: DEFAULT[it]) }

    /** 정리할 때 기본 단어로 채워야 하는(단어가 8개보다 적은) 카테고리 번호 */
    fun shortCategories(lists: List<List<String>>): List<Int> =
        CATEGORIES.indices.filter { k ->
            val l = lists.getOrNull(k) ?: return@filter false
            l.map { it.trim().take(GameConfig.MAX_WORD_LEN).trim() }.filter { it.isNotEmpty() }.toSet().size < GameConfig.MIN_WORDS
        }

    /** {"음식": [...], "장소": [...], ...} */
    fun encode(lists: List<List<String>>): String {
        val m = LinkedHashMap<String, Any?>()
        for ((k, name) in CATEGORIES.withIndex()) m[name] = lists.getOrNull(k) ?: DEFAULT[k]
        return Json.write(m)
    }

    /** 저장된 JSON → 카테고리별 단어 (없거나 깨진 카테고리는 기본 단어) */
    fun decode(text: String?): List<List<String>> {
        if (text.isNullOrBlank()) return DEFAULT
        val m = try {
            Json.parse(text) as? Map<*, *>
        } catch (e: RuntimeException) {
            null // 깨진 저장값
        } ?: return DEFAULT
        return CATEGORIES.mapIndexed { k, name ->
            val v = m[name] as? List<*>
            if (v == null) DEFAULT[k] else sanitize(k, v.filterIsInstance<String>())
        }
    }
}
