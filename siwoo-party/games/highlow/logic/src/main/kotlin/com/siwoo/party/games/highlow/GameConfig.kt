package com.siwoo.party.games.highlow

/** 하이로우 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 1인당 차례 수 (차례는 사람마다 돌아가며 → 전체 차례 = 5 × 인원) */
    const val TURNS_PER_PLAYER = 5

    /** 카드 숫자: A=1(가장 낮음) … K=13(가장 높음). 조커는 쓰지 않는다 */
    const val LOWEST_RANK = 1
    const val HIGHEST_RANK = 13
    const val JOKERS = 0

    /** 성공 k번째의 이번 차례 점수 = 2^(k-1). 넘침 방지용 최대 지수 */
    const val MAX_POT_SHIFT = 62

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1600L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 1100L
    /** 덱에서 자리로 카드가 날아가는 시간 */
    const val DEAL_MS = 380L
    /** 카드 뒤집기 (가로로 접혔다 펴짐) */
    const val FLIP_MS = 560L
    /** 결과 도장이 뜬 뒤 다음 단계까지 */
    const val RESULT_PAUSE_MS = 650L
    /** 성공 뒤 새 카드가 기준 자리로 미끄러지는 시간 */
    const val SLIDE_MS = 420L
    /** 실패("펑!") 연출을 보여 주는 시간 */
    const val FAIL_SHOW_MS = 1500L
    /** 화면 흔들기 */
    const val SHAKE_MS = 520L
    /** 저장: 코인이 플레이어 칸으로 날아가는 시간 */
    const val COINS_MS = 900L
    /** 카드를 버린 더미로 걷는 시간 */
    const val COLLECT_MS = 360L
    /** 다시 섞기 연출 */
    const val SHUFFLE_MS = 1300L
    /** 점수 숫자가 통통 튀는 시간 */
    const val POT_POP_MS = 320L
    /** 결과 도장 튀어나오기 */
    const val STAMP_POP_MS = 300L
    /** 마지막 결과 배너 뒤 허브로 */
    const val END_MS = 2800L
}
