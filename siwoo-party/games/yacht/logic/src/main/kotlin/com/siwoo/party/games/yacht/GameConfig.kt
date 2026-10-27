package com.siwoo.party.games.yacht

/** 야추 주사위 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 주사위 수 */
    const val DICE = 5
    /** 한 차례에 굴릴 수 있는 최대 횟수 (YACHT_MAX_ROLLS) */
    const val MAX_ROLLS = 3
    /** 점수표 칸 수 = 라운드 수 (상단 6 + 하단 6) */
    const val CATEGORIES = 12
    const val ROUNDS = CATEGORIES

    // ── 점수 ──
    /** 상단(에이스~헥사) 합이 이 이상이면 보너스 */
    const val UPPER_BONUS_THRESHOLD = 63
    const val UPPER_BONUS = 35
    /** 스몰 스트레이트 (4개 연속) */
    const val SMALL_STRAIGHT_POINTS = 15
    /** 라지 스트레이트 (5개 연속) */
    const val LARGE_STRAIGHT_POINTS = 30
    /** 야추 (5개 모두 같음) */
    const val YACHT_POINTS = 50
    /** 포카드: 같은 눈이 이 개수 이상이면 (5개 같음도 포함) 5개 합 */
    const val FOUR_KIND_MIN = 4

    /**
     * 5개가 모두 같은 눈(야추)을 풀하우스로 인정할지.
     * 한국에서 흔히 하는 "야추 다이스" 규칙대로 풀하우스는 **서로 다른 두 눈이 정확히 3개 + 2개** 일 때만 인정한다
     * → false: [3,3,3,3,3] 을 풀하우스 칸에 넣으면 0점. (포카드·초이스에는 그대로 합이 들어간다)
     */
    const val YACHT_COUNTS_AS_FULL_HOUSE = false

    // ── 연출 (속도 배율 적용) ──
    /** 주사위 굴리는 시간 (흔들리며 눈이 바뀌다 멈춤) */
    const val ROLL_MS = 900L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 1100L
    /** 차례가 바뀐 직후 잘못 누름 방지 (이 동안 굴리기·칸 터치 무시) */
    const val TURN_LOCK_MS = 400L
    /** 칸을 기록한 뒤 다음 차례까지 (점수 반짝임을 보여 주는 시간) */
    const val AFTER_RECORD_MS = 700L
    /** 기록한 칸 숫자가 톡 커졌다 돌아오는 시간 */
    const val CELL_POP_MS = 450L
    /** "야추!" 배너 + 꽃가루 */
    const val YACHT_BANNER_MS = 1900L
    /** "보너스 +35!" 배너 */
    const val BONUS_BANNER_MS = 1300L
    /** 마지막 "점수 공개!" 배너 */
    const val REVEAL_INTRO_MS = 1300L
    /** 점수 공개: 한 줄씩 밝혀지는 간격 */
    const val REVEAL_ROW_MS = 160L
    /** 승자 발표 배너 → 결과로 */
    const val END_BANNER_MS = 2600L
}
