package com.siwoo.party.games.charades

import com.siwoo.party.common.Json
import kotlin.random.Random

/**
 * 몸으로 말해요 제시어.
 * - CATEGORIES: 동물·운동·직업·행동·물건
 * - DEFAULT: 카테고리마다 몸짓으로 표현하기 좋은 기본 단어 30개 (설정의 [기본값])
 * - 사용자가 편집한 목록은 화면 쪽이 SharedPreferences "charades" 에 JSON({"동물": [...], ...})으로 저장 (encode / decode)
 */
object CharadesWords {
    val CATEGORIES: List<String> = listOf("동물", "운동", "직업", "행동", "물건")

    val DEFAULT: List<List<String>> = listOf(
        listOf(
            "사자", "코끼리", "원숭이", "펭귄", "캥거루", "토끼", "뱀", "기린", "고릴라", "닭",
            "개구리", "독수리", "악어", "강아지", "고양이", "곰", "돌고래", "게", "낙타", "거북이",
            "나비", "오리", "말", "문어", "박쥐", "하마", "공작새", "다람쥐", "젖소", "홍학",
        ),
        listOf(
            "축구", "농구", "야구", "수영", "볼링", "골프", "테니스", "탁구", "배드민턴", "복싱",
            "태권도", "스키", "스케이트", "요가", "줄넘기", "역도", "양궁", "유도", "씨름", "펜싱",
            "서핑", "클라이밍", "배구", "마라톤", "스카이다이빙", "체조", "컬링", "스노보드", "아이스하키", "훌라후프",
        ),
        listOf(
            "의사", "경찰관", "소방관", "요리사", "가수", "지휘자", "마술사", "미용사", "사진작가", "택배기사",
            "화가", "발레리나", "우주비행사", "농부", "어부", "치과의사", "비행기 조종사", "군인", "피아니스트", "교통경찰",
            "목수", "광대", "해녀", "버스 기사", "드러머", "심판", "경비원", "바리스타", "제빵사", "환경미화원",
        ),
        listOf(
            "양치질", "세수하기", "하품하기", "기지개 켜기", "박수 치기", "윙크하기", "재채기", "눈싸움", "사진 찍기", "전화하기",
            "운전하기", "낚시하기", "머리 감기", "화장하기", "넥타이 매기", "줄 서기", "우산 쓰기", "라면 먹기", "빨래 널기", "청소기 돌리기",
            "설거지", "셀카 찍기", "모기 잡기", "엿보기", "코 골기", "절하기", "손 씻기", "신발 신기", "풍선 불기", "문 두드리기",
        ),
        listOf(
            "우산", "가위", "망원경", "피아노", "기타", "카메라", "선풍기", "시계", "안경", "칫솔",
            "휴대폰", "자전거", "망치", "빗자루", "다리미", "냉장고", "컴퓨터", "헤어드라이어", "줄자", "마이크",
            "리모컨", "의자", "지퍼", "텐트", "엘리베이터", "비행기", "그네", "거울", "풍선", "열쇠",
        ),
    )

    /** 카테고리 하나 정리: 공백·빈 줄·중복 제거, 개수·길이 제한, MIN_WORDS 보다 적으면 기본 단어로 채움 */
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

/** 제시어 하나 (카테고리 번호와 함께 — 모두 섞기일 때 설명자 화면에 카테고리를 보여 준다) */
class CharadesWord(val text: String, val category: Int)

/**
 * 한 게임 동안 제시어 뽑기: 같은 단어는 다시 나오지 않는다 (카테고리가 달라도 같은 글자면 한 번만).
 * 고른 카테고리(MIXED = 전부)의 단어를 섞어 차례로 내고, 다 쓰면 다른 카테고리 단어를 섞어 이어서 낸다.
 * 모든 단어를 다 쓴 경우에만(사실상 없음) 처음부터 다시 섞는다.
 */
class WordPicker(lists: List<List<String>>, category: Int, private val random: Random) {
    private val primary: List<CharadesWord>
    private val secondary: List<CharadesWord>
    private val queue = ArrayList<CharadesWord>()
    private val used = HashSet<String>()

    /** 지금까지 낸 단어 수 */
    var picked = 0
        private set

    init {
        val seen = HashSet<String>()
        val first = ArrayList<CharadesWord>()
        val rest = ArrayList<CharadesWord>()
        for ((k, l) in lists.withIndex()) {
            val mine = category == GameConfig.MIXED || k == category
            if (mine) for (w in l) if (seen.add(w)) first.add(CharadesWord(w, k))
        }
        for ((k, l) in lists.withIndex()) for (w in l) if (seen.add(w)) rest.add(CharadesWord(w, k))
        if (first.isEmpty()) {
            primary = rest
            secondary = emptyList()
        } else {
            primary = first
            secondary = rest
        }
        queue.addAll(primary.shuffled(random))
    }

    /** 처음 고른 카테고리 단어 수 */
    val primaryCount: Int get() = primary.size

    fun next(): CharadesWord {
        if (queue.isEmpty()) {
            val rest = secondary.filter { it.text !in used }
            if (rest.isNotEmpty()) queue.addAll(rest.shuffled(random))
            else {
                used.clear()
                queue.addAll((primary + secondary).shuffled(random))
            }
        }
        val w = queue.removeAt(queue.size - 1)
        used.add(w.text)
        picked++
        return w
    }
}
