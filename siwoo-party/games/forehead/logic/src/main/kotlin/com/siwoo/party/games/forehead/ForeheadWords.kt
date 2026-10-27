package com.siwoo.party.games.forehead

import com.siwoo.party.common.Json
import kotlin.random.Random

/**
 * 이마 퀴즈 제시어.
 * - CATEGORIES: 동물·음식·직업·장소·물건·동작
 * - DEFAULT: 카테고리마다 기본 단어 30개 (설정의 [기본값])
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences "forehead" 에 JSON({"동물": [...], ...})으로 저장 (encode / decode)
 */
object ForeheadWords {
    val CATEGORIES: List<String> = listOf("동물", "음식", "직업", "장소", "물건", "동작")

    val DEFAULT: List<List<String>> = listOf(
        listOf(
            "사자", "호랑이", "코끼리", "기린", "펭귄", "토끼", "강아지", "고양이", "원숭이", "판다",
            "돌고래", "상어", "거북이", "하마", "캥거루", "다람쥐", "악어", "독수리", "부엉이", "고슴도치",
            "얼룩말", "낙타", "코알라", "문어", "오징어", "개구리", "뱀", "닭", "돼지", "젖소",
        ),
        listOf(
            "김치찌개", "떡볶이", "피자", "치킨", "라면", "비빔밥", "짜장면", "햄버거", "초밥", "삼겹살",
            "김밥", "돈가스", "냉면", "불고기", "샌드위치", "스파게티", "순대", "만두", "팥빙수", "핫도그",
            "아이스크림", "케이크", "수박", "바나나", "떡국", "카레", "붕어빵", "호떡", "감자튀김", "계란말이",
        ),
        listOf(
            "의사", "선생님", "경찰관", "소방관", "요리사", "가수", "축구선수", "과학자", "비행기 조종사", "간호사",
            "우주비행사", "미용사", "농부", "화가", "유튜버", "택배기사", "군인", "변호사", "제빵사", "마술사",
            "배우", "기자", "사진작가", "어부", "버스 기사", "수의사", "치과의사", "프로게이머", "발레리나", "목수",
        ),
        listOf(
            "학교", "병원", "놀이공원", "도서관", "영화관", "수영장", "공항", "편의점", "동물원", "바다",
            "캠핑장", "찜질방", "경찰서", "백화점", "지하철역", "박물관", "놀이터", "미용실", "빵집", "산",
            "노래방", "우체국", "은행", "화장실", "교회", "시장", "운동장", "사막", "남극", "우주",
        ),
        listOf(
            "우산", "연필", "휴대폰", "안경", "시계", "가위", "칫솔", "냉장고", "텔레비전", "자전거",
            "베개", "거울", "지우개", "가방", "숟가락", "리모컨", "선풍기", "열쇠", "장갑", "컵",
            "모자", "신발", "양말", "책상", "의자", "침대", "컴퓨터", "카메라", "풍선", "빗자루",
        ),
        listOf(
            "줄넘기", "양치질", "세수하기", "박수치기", "윙크하기", "하품하기", "기지개", "팔굽혀펴기", "수영하기", "노래하기",
            "춤추기", "달리기", "낚시하기", "운전하기", "요리하기", "그림 그리기", "사진 찍기", "전화하기", "잠자기", "재채기",
            "웃기", "울기", "박치기", "손 흔들기", "절하기", "스키 타기", "피아노 치기", "기타 치기", "공 던지기", "축구하기",
        ),
    )

    /** 카테고리 하나 정리: 공백·빈 줄·중복 제거, 개수·길이 제한, 5개(MIN_WORDS)보다 적으면 기본 단어로 채움 */
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

    /** 저장된 JSON → 카테고리별 단어 (없거나 깨진 카테고리는 기본 단어) */
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

/**
 * 한 게임 동안 제시어 뽑기: 같은 단어는 다시 나오지 않는다.
 * 고른 카테고리(MIXED = 전부)의 단어를 섞어 차례로 내고, 다 쓰면 다른 카테고리 단어를 섞어 이어서 낸다.
 * 모든 단어를 다 쓴 경우에만(사실상 없음) 처음부터 다시 섞는다.
 */
class WordPicker(lists: List<List<String>>, category: Int, private val random: Random) {
    private val primary: List<String>
    private val secondary: List<String>
    private val queue = ArrayList<String>()
    private val used = HashSet<String>()

    /** 지금까지 낸 단어 수 */
    var picked = 0
        private set

    init {
        val all = LinkedHashSet<String>()
        val first = LinkedHashSet<String>()
        for ((k, l) in lists.withIndex()) {
            if (category == GameConfig.MIXED || k == category) first.addAll(l)
            all.addAll(l)
        }
        if (first.isEmpty()) first.addAll(all)
        primary = first.toList()
        secondary = all.filter { it !in first }
        queue.addAll(primary.shuffled(random))
    }

    /** 처음 고른 카테고리 단어 수 */
    val primaryCount: Int get() = primary.size

    fun next(): String {
        if (queue.isEmpty()) {
            val rest = secondary.filter { it !in used }
            if (rest.isNotEmpty()) queue.addAll(rest.shuffled(random))
            else {
                used.clear()
                queue.addAll((primary + secondary).shuffled(random))
            }
        }
        val w = queue.removeAt(queue.size - 1)
        used.add(w)
        picked++
        return w
    }
}
