package com.siwoo.party.games.blackjack

/**
 * 블랙잭 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다.
 *
 * 규칙 정리 (이 게임에서 정한 것)
 * - 딜러(컴퓨터) 한 명 대 모든 플레이어. 카드는 모두 공개 (기기 넘기기 없음).
 * - 슈(카드 통): 조커 없는 52장 × [DECKS] 벌 섞음. 라운드를 시작할 때 남은 카드가 [RESHUFFLE_BELOW] 장 미만이면
 *   버린 카드를 모두 되돌려 다시 섞는다. (라운드 도중 슈가 비면 버린 카드로 바로 채움 — 사실상 일어나지 않음)
 * - 베팅: [BET_OPTIONS] 중 하나, 가진 칩보다 많이 걸 수 없다. 칩이 [MIN_BET] 미만이면 남은 칩 전부를 건다.
 *   건 칩은 바로 테이블로 나간다(보유 칩에서 빠짐) → 이기면 건 칩 + 이익, 비기면 건 칩만 돌아온다.
 * - A 는 1 또는 11 (21 을 넘지 않는 가장 큰 합 자동 계산, 11 로 셀 수 있으면 "soft" — "7/17" 처럼 표시).
 * - 블랙잭 = 처음 두 장이 21. 배당은 베팅의 1.5배(내림), 딜러도 블랙잭이면 무승부.
 * - 딜러는 처음 두 장 중 한 장(구멍 카드)을 엎어 둔다. [DEALER_PEEKS]: 딜러 앞 두 장이 블랙잭이면 바로 공개하고
 *   라운드 정산 (플레이어 블랙잭은 무승부, 나머지는 패배 — 더블 기회 없음).
 * - 차례: [히트] 한 장 더, [스탠드] 멈춤, [더블] 처음 두 장일 때만 — 베팅을 두 배로(보유 칩이 베팅 이상 있어야 함),
 *   정확히 한 장 더 받고 자동 스탠드. 21 을 넘으면 버스트(즉시 패배). 21 이 되면 자동 스탠드([AUTO_STAND_ON_21]).
 * - 스플릿·보험·서렌더는 없음 (파티용으로 단순하게).
 * - 딜러: 합이 [DEALER_STAND] 이상이 될 때까지 뽑는다. soft 17 에서도 멈춘다 ([DEALER_HITS_SOFT_17] = false, "S17").
 *   모든 플레이어가 버스트(또는 블랙잭)면 딜러는 더 뽑지 않고 구멍 카드만 공개.
 * - 정산: 이기면 +베팅, 블랙잭 +1.5×베팅(내림), 비기면 0, 지거나 버스트면 −베팅.
 * - [ROUNDS] 라운드 뒤 보유 칩 많은 순으로 순위 (같으면 같은 순위). 칩이 0 이 되면 남은 라운드는 관전.
 *   모두 칩이 0 이면 그 자리에서 끝(모두 공동 1등).
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 */
    const val ROUNDS = 5
    /** 시작 칩 */
    const val START_CHIPS = 100

    // ── 베팅 ──
    const val BET_SMALL = 10
    const val BET_MID = 20
    const val BET_BIG = 50
    /** 고를 수 있는 베팅 (버튼 순서) */
    val BET_OPTIONS: List<Int> = listOf(BET_SMALL, BET_MID, BET_BIG)
    /** 이보다 칩이 적으면 남은 칩 전부를 건다 */
    const val MIN_BET = BET_SMALL

    // ── 슈 ──
    /** 섞어 쓰는 카드 벌 수 (조커 없음) */
    const val DECKS = 2
    /** 라운드 시작 때 남은 카드가 이보다 적으면 다시 섞기 (4인 한 라운드 최대 사용량보다 넉넉히) */
    const val RESHUFFLE_BELOW = 30

    // ── 규칙 ──
    const val BUST_OVER = 21
    /** 딜러는 이 합 이상이면 멈춘다 */
    const val DEALER_STAND = 17
    /** soft 17(A 를 11 로 센 17)에서 딜러가 더 뽑는지 — false: 모든 17 에서 멈춤 */
    const val DEALER_HITS_SOFT_17 = false
    /** 딜러가 처음 두 장 블랙잭을 바로 확인(공개)하는지 */
    const val DEALER_PEEKS = true
    /** 21 이 되면 자동 스탠드 */
    const val AUTO_STAND_ON_21 = true
    /** 블랙잭 배당 = 베팅 × NUM / DEN (내림) */
    const val BLACKJACK_PAY_NUM = 3
    const val BLACKJACK_PAY_DEN = 2

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_BANNER_MS = 1500L
    /** "라운드 n" 배너 */
    const val ROUND_BANNER_MS = 1100L
    /** 베팅 칩이 하나씩 떨어져 쌓이는 간격 / 한 개 낙하 시간 */
    const val CHIP_STAGGER_MS = 70L
    const val CHIP_DROP_MS = 260L
    /** 베팅 후 다음 사람까지 */
    const val AFTER_BET_MS = 450L
    /** 나눠 주기: 카드 사이 간격 / 날아가는 시간 / 뒤집기 */
    const val DEAL_STEP_MS = 230L
    const val CARD_FLY_MS = 380L
    const val CARD_FLIP_MS = 300L
    /** 차례 배너 */
    const val TURN_BANNER_MS = 900L
    /** 차례가 바뀐 직후 잘못 누름 방지 */
    const val TURN_LOCK_MS = 350L
    /** 버스트·블랙잭 배너 */
    const val EVENT_BANNER_MS = 1300L
    /** 결과 도장이 톡 찍히는 시간 */
    const val STAMP_POP_MS = 320L
    /** 행동 후 다음 차례까지 */
    const val AFTER_ACTION_MS = 600L
    /** 딜러 한 장 뽑고 다음 장까지 */
    const val DEALER_STEP_MS = 650L
    /** 정산: 칩(코인) 날아가는 시간 */
    const val PAYOUT_MS = 900L
    /** 카드 걷기 */
    const val COLLECT_MS = 420L
    const val COLLECT_STAGGER_MS = 30L
    /** 다시 섞기 연출 */
    const val SHUFFLE_MS = 1200L
    /** 승자 발표 → 결과로 */
    const val END_BANNER_MS = 2600L
}
