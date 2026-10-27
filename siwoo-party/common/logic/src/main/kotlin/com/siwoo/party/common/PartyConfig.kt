package com.siwoo.party.common

/**
 * 시우 파티 공통 설정값. 숫자는 모두 여기서 고친다.
 * (게임별 규칙 숫자는 각 게임의 GameConfig)
 */
object PartyConfig {
    // ── 플레이어 ─────────────────────────────────
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4
    /** 이름 최대 글자 수 */
    const val MAX_NAME_LEN = 8

    // ── 순위 점수 ─────────────────────────────────
    /** 인원별 순위 점수 (1위부터). 동점이면 같은 순위 점수를 각각 받는다 */
    val RANK_POINTS: Map<Int, IntArray> = mapOf(
        2 to intArrayOf(3, 0),
        3 to intArrayOf(4, 2, 0),
        4 to intArrayOf(4, 2, 1, 0),
    )

    // ── 연출 속도 (모든 게임 공통) ────────────────
    /** 연출 시간에 곱하는 배율: 느리게 / 보통 / 빠르게 */
    val SPEED_FACTORS = floatArrayOf(1.0f, 0.7f, 0.4f)
    val SPEED_NAMES = arrayOf("느리게", "보통", "빠르게")
    const val DEFAULT_SPEED_INDEX = 0

    // ── 현실 벌칙 룰렛 ───────────────────────────
    val GRADE_NAMES = arrayOf("소형", "중형", "대형")
    /** 등급 확률(%): 소형 / 중형 / 대형 */
    val GRADE_PERCENT = intArrayOf(60, 30, 10)
    /** 룰렛 10칸 배치 (0 소형 ×6, 1 중형 ×3, 2 대형 ×1 → 정확히 60/30/10) */
    val REAL_ROULETTE = intArrayOf(0, 1, 0, 0, 1, 0, 2, 0, 1, 0)
    const val MAX_PHRASES_PER_GRADE = 10
    const val MAX_PHRASE_LEN = 30
    /** 등급별 기본 문구 (설정에서 편집) */
    val DEFAULT_PHRASES: List<List<String>> = listOf(
        listOf("옆 사람에게 칭찬 한마디 하기", "10초 동안 가장 웃긴 표정 짓기", "애교 한 번 부리기", "다음 게임까지 존댓말 쓰기"),
        listOf("노래 한 소절 부르기", "엉덩이로 이름 쓰기", "모두에게 물 한 잔씩 따라 주기", "아는 성대모사 하나 하기"),
        listOf("30초 동안 막춤 추기", "오른쪽 사람 어깨 30초 주물러 주기", "다음 게임 정리 담당", "다음 게임까지 모두가 지어 준 별명으로 불리기"),
    )

    // ── 연출 시간 (ms, 속도 배율이 곱해진다) ───────
    /** 화면 전환 (페이드 + 살짝 확대) */
    const val TRANSITION_MS = 260L
    /** 로고 "시우 공방" 표시 */
    const val LOGO_MS = 1600L
    /** 시상대 블록이 하나씩 올라오는 시간 */
    const val PODIUM_RISE_MS = 700L
    /** 1위 왕관 떨어짐 */
    const val CROWN_DROP_MS = 650L
    /** 얻은 점수가 플레이어 칩으로 날아감 */
    const val POINTS_FLY_MS = 900L
    /** 누적 점수 숫자 올라감 */
    const val COUNT_UP_MS = 800L
    /** 랜덤 게임 룰렛 회전 */
    const val RANDOM_SPIN_MS = 2600L
    /** 벌칙 룰렛 회전 */
    const val PENALTY_SPIN_MS = 3000L
    /** 카드 뒤집기 */
    const val CARD_FLIP_MS = 900L

    /** 동시 터치 게임에 필요한 손가락 수 */
    const val REQUIRED_TOUCHES = 4
}
