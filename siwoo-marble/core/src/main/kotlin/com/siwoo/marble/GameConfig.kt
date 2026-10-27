package com.siwoo.marble

/**
 * 게임 밸런스 수치를 한 곳에 모은 설정 파일.
 * 금액 단위는 모두 "원"이다. (화면에는 "30만" 처럼 만원 단위로 표시)
 *
 * 칸별 땅값·건설비·통행료는 data/BoardData.kt,
 * 황금열쇠 카드 금액은 data/CardData.kt 에 있다.
 */
object GameConfig {
    // ── 인원 ──────────────────────────────────────
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 돈 ────────────────────────────────────────
    /** 시작 자금 */
    const val START_MONEY = 3_000_000
    /** 출발 칸을 지나가거나 도착할 때 받는 월급 */
    const val SALARY = 200_000

    // ── 보드 ──────────────────────────────────────
    const val BOARD_SIZE = 40
    const val TILE_START = 0
    const val TILE_ISLAND = 10
    const val TILE_WELFARE_RECEIVE = 20
    const val TILE_SPACE = 30

    // ── 주사위 / 무인도 ───────────────────────────
    /** 이 횟수만큼 연속 더블이 나오면 즉시 무인도행 */
    const val MAX_DOUBLES = 3
    /** 무인도에 갇히는 턴 수 (더블이 나오면 즉시 탈출) */
    const val ISLAND_TURNS = 3

    // ── 사회복지기금 ─────────────────────────────
    /** 사회복지기금 접수처 칸에 도착하면 내는 기부금 */
    const val WELFARE_DONATION = 150_000

    // ── 판매 / 거래 ──────────────────────────────
    /** 은행에 팔 때 받는 비율: 판매가 = 자산 가치(땅값 + 지은 건물 건설비) × SELL_RATE */
    const val SELL_RATE = 0.5
    /** 한 턴에 할 수 있는 거래 제안 횟수 (수락·거절 모두 1회) */
    const val MAX_TRADES_PER_TURN = 3
    /** 거래 화면 돈 입력 버튼 단위 */
    const val TRADE_STEP_SMALL = 10_000
    const val TRADE_STEP_BIG = 100_000

    // ── 벌칙 룰렛 ────────────────────────────────
    /** 등급 확률(%): 소형 / 중형 / 대형 */
    const val PENALTY_SMALL_PCT = 60
    const val PENALTY_MEDIUM_PCT = 30
    const val PENALTY_LARGE_PCT = 10
    /** 총자산 꼴찌의 대형 확률 (차이는 소형에 더함) */
    const val PENALTY_LARGE_PCT_LAST = 3
    /** 총자산 1위의 대형 확률 (차이는 소형에서 뺌) */
    const val PENALTY_LARGE_PCT_FIRST = 15
    /** 한 번의 벌칙으로 잃는 금액 상한 = 총자산 × 이 비율 */
    const val PENALTY_MAX_RATE = 0.25

    // ── 턴 제한 모드 ─────────────────────────────
    /** 시작 팝업에서 고를 수 있는 라운드 수 (0 = 턴 제한 없음, 최후 1인 승리) */
    val TURN_LIMIT_OPTIONS = intArrayOf(0, 15, 20, 30)
    const val DEFAULT_TURN_LIMIT = 0

    // ── 연출 시간 (ms) ── 설정의 "연출 속도" 배율이 모두 곱해진다
    /** 주사위 굴림 (흔들리며 눈이 바뀌다 점점 느려짐) */
    const val DICE_ROLL_MS = 1200L
    /** 주사위 결과 숫자를 크게 보여주는 시간 */
    const val DICE_RESULT_HOLD_MS = 700L
    /** 결과 후 말 이동 시작까지 */
    const val MOVE_START_DELAY_MS = 500L
    /** 말 한 칸 이동 */
    const val STEP_MS = 300L
    /** 도착 후 칸 효과 전 멈춤 */
    const val ARRIVE_PAUSE_MS = 500L
    /** 돈 이동(코인 비행)과 숫자 변화 */
    const val MONEY_ANIM_MS = 1200L
    /** 보드 중앙 문구 */
    const val CENTER_BANNER_MS = 1800L
    /** 차례 전환 화면 */
    const val TURN_CHANGE_MS = 1500L
    /** 황금열쇠 카드 뒤집기 */
    const val CARD_FLIP_MS = 900L
    /** 순간이동(우주여행·황금열쇠 이동) */
    const val JUMP_MS = 700L
    /** 칸 번쩍임(통행료) */
    const val TILE_FLASH_MS = 600L
    /** 벌칙 칸 빨간 깜빡임 (3번) */
    const val PENALTY_FLASH_MS = 1200L
    /** 벌칙 룰렛 회전 */
    const val ROULETTE_SPIN_MS = 3000L

    /** 연출 속도 배율: 느리게 / 보통 / 빠르게 */
    val SPEED_FACTORS = floatArrayOf(1.0f, 0.7f, 0.4f)
    val SPEED_NAMES = arrayOf("느리게", "보통", "빠르게")
    const val DEFAULT_SPEED_INDEX = 0
}
