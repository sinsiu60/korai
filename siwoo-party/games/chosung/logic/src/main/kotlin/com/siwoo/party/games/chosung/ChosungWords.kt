package com.siwoo.party.games.chosung

import com.siwoo.party.common.Chosung
import com.siwoo.party.common.Json

/** 문제 후보 하나: 카테고리 번호 + 단어 */
class Entry(val category: Int, val word: String) {
    val categoryName: String get() = ChosungWords.CATEGORIES[category]
    override fun toString(): String = "${categoryName}:$word"
}

/**
 * 초성 퀴즈 단어.
 * - CATEGORIES: 카테고리 6개 (음식·동물·나라·직업·물건·스포츠), DEFAULT: 카테고리마다 기본 단어 30개
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences("chosung")에 JSON({"음식": [...], ...})으로 저장 (encode / decode)
 * - 쓸 수 있는 단어: 앞뒤 공백 제거, 12글자 이하, 한글 음절 2개 이상 (초성이 나와야 하므로). 중복 제거.
 * - 카테고리마다 5개보다 적으면 기본 단어로 채운다 (15문제를 낼 수 있게)
 */
object ChosungWords {
    val CATEGORIES: List<String> = listOf("음식", "동물", "나라", "직업", "물건", "스포츠")

    val DEFAULT: List<List<String>> = listOf(
        listOf(
            "김치찌개", "떡볶이", "비빔밥", "불고기", "삼겹살", "짜장면", "짬뽕", "탕수육", "김밥", "라면",
            "냉면", "된장찌개", "순두부찌개", "갈비탕", "잡채", "떡국", "만두", "치킨", "피자", "햄버거",
            "돈가스", "초밥", "스파게티", "샌드위치", "팥빙수", "호떡", "붕어빵", "닭갈비", "칼국수", "콩나물국밥",
        ),
        listOf(
            "호랑이", "사자", "코끼리", "기린", "원숭이", "펭귄", "토끼", "강아지", "고양이", "다람쥐",
            "거북이", "돌고래", "고래", "상어", "악어", "하마", "코뿔소", "캥거루", "판다", "너구리",
            "여우", "늑대", "고슴도치", "부엉이", "독수리", "앵무새", "낙타", "얼룩말", "오징어", "문어",
        ),
        listOf(
            "대한민국", "일본", "중국", "미국", "캐나다", "멕시코", "브라질", "아르헨티나", "영국", "프랑스",
            "독일", "이탈리아", "스페인", "포르투갈", "네덜란드", "스위스", "러시아", "인도", "태국", "베트남",
            "필리핀", "인도네시아", "호주", "뉴질랜드", "이집트", "튀르키예", "그리스", "스웨덴", "노르웨이", "몽골",
        ),
        listOf(
            "의사", "간호사", "선생님", "경찰관", "소방관", "요리사", "미용사", "가수", "배우", "화가",
            "작가", "기자", "변호사", "판사", "과학자", "우주비행사", "비행기 조종사", "농부", "어부", "군인",
            "운동선수", "은행원", "택배기사", "제빵사", "마술사", "프로그래머", "사진작가", "약사", "수의사", "유튜버",
        ),
        listOf(
            "우산", "연필", "지우개", "가위", "휴대폰", "안경", "시계", "냉장고", "세탁기", "텔레비전",
            "자전거", "베개", "거울", "칫솔", "치약", "숟가락", "젓가락", "리모컨", "선풍기", "에어컨",
            "열쇠", "장갑", "목도리", "운동화", "책가방", "손전등", "컴퓨터", "노트북", "전자레인지", "청소기",
        ),
        listOf(
            "축구", "야구", "농구", "배구", "탁구", "테니스", "배드민턴", "수영", "태권도", "유도",
            "검도", "씨름", "스키", "스노보드", "피겨스케이팅", "쇼트트랙", "골프", "볼링", "당구", "마라톤",
            "양궁", "펜싱", "복싱", "레슬링", "핸드볼", "하키", "럭비", "체조", "줄넘기", "사이클",
        ),
    )

    /** 단어 하나 정리 (못 쓰면 null): 앞뒤 공백 제거, 12글자 이하, 한글 음절 2개 이상 */
    fun clean(s: String): String? {
        val t = s.trim()
        if (t.isEmpty() || t.length > GameConfig.MAX_WORD_LEN) return null
        if (Chosung.syllableCount(t) < GameConfig.MIN_SYLLABLES) return null
        return t
    }

    /** 카테고리 하나 정리: 못 쓰는 단어·중복 빼고 최대 60개, 5개보다 적으면 기본 단어로 채움 */
    fun sanitize(category: Int, list: List<String>): List<String> {
        val out = ArrayList<String>()
        for (s in list) {
            val t = clean(s) ?: continue
            if (t in out) continue
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

    /** 쓸 수 있는 단어가 5개보다 적어 기본 단어로 채워야 하는 카테고리 번호 */
    fun shortCategories(lists: List<List<String>>): List<Int> =
        CATEGORIES.indices.filter { k ->
            val l = lists.getOrNull(k) ?: return@filter false
            l.mapNotNull { clean(it) }.toSet().size < GameConfig.MIN_WORDS
        }

    /** 문제 후보 전체 (카테고리 순서대로, 같은 단어가 여러 카테고리에 있으면 처음 것만) */
    fun pool(lists: List<List<String>>): List<Entry> {
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for ((k, l) in lists.withIndex()) {
            if (k >= CATEGORIES.size) break
            for (w in l) if (seen.add(w)) out.add(Entry(k, w))
        }
        return out
    }

    /** {"음식": [...], "동물": [...], ...} */
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
