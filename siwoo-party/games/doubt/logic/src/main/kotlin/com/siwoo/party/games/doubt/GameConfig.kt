package com.siwoo.party.games.doubt

/** 의심 카드 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 조커 없이 52장을 모두 나눈다 */
    const val TOTAL_CARDS = 52

    /** 한 번에 낼 수 있는 장수 */
    const val MIN_PLAY = 1
    const val MAX_PLAY = 4

    /** 첫 선언 숫자 (A) — 이후 A → 2 → … → K → A 순서로 강제 */
    const val FIRST_RANK = 1

    /** [의심!] 버튼이 떠 있는 시간 — 게임 규칙 시간이라 속도 배율을 적용하지 않는다 */
    const val DOUBT_WINDOW_MS = 3000L

    /**
     * 차례 제한: 이 차례 수가 끝나면 판을 끝내고, 남은 사람은 손패가 적은 순서로 순위 (같은 장수는 같은 순위).
     * 의심 카드는 마지막 카드 규칙 때문에 판이 아주 길어질 수 있다 (무작위 모의 실험: 4인 중간값 700차례 이상).
     * 한 차례 ≈ 넘기기 2초 + 고르기 5초 + 의심 3초 + 연출 1초 → 60차례 ≈ 10분. 화면에 "남은 차례" 를 늘 보여 준다.
     */
    const val MAX_TURNS = 60

    // ── 연출 (속도 배율 적용) ──
    /** 나눠 주기: 한 장 날아가는 시간 / 다음 장 출발 간격 */
    const val DEAL_FLY_MS = 300L
    const val DEAL_STAGGER_MS = 20L
    /** 시작 안내 배너 */
    const val START_BANNER_MS = 1700L
    /** 손패가 아래에서 펼쳐지는 시간 / 카드 간격 */
    const val HAND_IN_MS = 320L
    const val HAND_IN_STAGGER_MS = 12L
    /** 고른 카드 떠오르기 */
    const val LIFT_MS = 120L
    /** [내기] 직후 손패를 뒷면으로 덮는 시간 */
    const val HIDE_FLIP_MS = 160L
    /** 낸 카드가 가운데 더미로 날아가는 시간 / 간격 */
    const val PLAY_FLY_MS = 380L
    const val PLAY_STAGGER_MS = 70L
    /** 남은 손패(뒷면)가 내 패널로 돌아가는 시간 */
    const val HAND_AWAY_MS = 300L
    /** 의심 창 나타남·사라짐 */
    const val DOUBT_FADE_MS = 220L
    /** 의심한 사람 표시 후 창을 닫기까지 */
    const val DOUBT_HIT_SHOW_MS = 750L
    /** 공개: 낸 카드가 가운데로 펼쳐지는 시간 / 뒤집기 / 카드 간격 */
    const val REVEAL_SPREAD_MS = 360L
    const val REVEAL_FLIP_MS = 300L
    const val REVEAL_STAGGER_MS = 140L
    /** 판정("거짓말!"/"진짜!") 보여 주는 시간 */
    const val VERDICT_MS = 1500L
    /** 더미 전체가 가져가는 사람에게 날아가는 시간 / 간격 */
    const val TAKE_FLY_MS = 520L
    const val TAKE_STAGGER_MS = 26L
    /** 아무도 의심하지 않으면 낸 카드가 더미에 합쳐지는 시간 */
    const val MERGE_MS = 260L
    /** 다 비운 사람 확정 배너 */
    const val FINISH_BANNER_MS = 1500L
    /** 차례 끝 → 다음 넘기기까지 */
    const val TURN_END_MS = 350L
    /** 끝 배너 → 결과 */
    const val END_BANNER_MS = 2400L

    /** 더미가 날아갈 때 그릴 최대 장수 (나머지는 숫자만 바뀐다) */
    const val TAKE_FLY_MAX = 14
}
