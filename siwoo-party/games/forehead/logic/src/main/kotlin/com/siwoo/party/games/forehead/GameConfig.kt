package com.siwoo.party.games.forehead

/** 이마 퀴즈 수치 (시간 단위 ms). 연출 시간(…_MS 중 "연출" 표시)은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 4

    // ── 판 수 ──
    /** 한 바퀴 = 모두 한 번씩 차례. 설정 1~2바퀴 */
    const val LAPS_MIN = 1
    const val LAPS_MAX = 2
    const val LAPS_DEFAULT = 1

    // ── 제시어 ──
    /** 카테고리 수 (동물·음식·직업·장소·물건·동작) */
    const val CATEGORY_COUNT = 6
    /** 기본 단어 수 (카테고리마다) */
    const val DEFAULT_WORDS = 30
    /** 카테고리 하나 최소 단어 수 (모자라면 기본 단어로 채움) */
    const val MIN_WORDS = 5
    /** 카테고리 하나 최대 단어 수 */
    const val MAX_WORDS = 60
    /** 단어 하나 최대 글자 수 */
    const val MAX_WORD_LEN = 10
    /** 카테고리 고르기에서 "모두 섞기" */
    const val MIXED = -1

    // ── 규칙 시간 (속도 배율 적용 안 함) ──
    /** 1인 제한 시간 60초 */
    const val TURN_MS = 60_000L
    /** 이마에 대면 3초 카운트 (3 · 2 · 1) */
    const val COUNT_STEPS = 3
    const val COUNT_STEP_MS = 1000L
    /** 가로로 세워 이만큼 가만히 들고 있으면 카운트 시작 */
    const val READY_HOLD_MS = 600L
    /** 정답/패스 뒤 다음 동작까지 최소 간격 (센서 흔들림 방지) */
    const val ACTION_GAP_MS = 350L

    // ── 기울기 판정 (가속도 센서) ──
    /**
     * 기울기 각 pitch = atan2(화면 바깥 방향 성분 z, 화면 위쪽 방향 성분 up) (도).
     * 0° = 이마에 똑바로 세움, -90° = 화면이 바닥을 봄(아래로 기울임 → 정답), +90° = 화면이 천장을 봄(위로 → 패스).
     */
    /** 이 각도 이상 기울이면 동작 (정답/패스) */
    const val TILT_TRIGGER_DEG = 50f
    /** 동작 뒤에는 이 각도 안(가운데)으로 돌아와야 다음 동작 가능 (히스테리시스) */
    const val TILT_NEUTRAL_DEG = 25f
    /** 이보다 더 넘어가면(거꾸로 뒤집힘) 판정하지 않음 */
    const val TILT_FLIP_DEG = 155f
    /** 중력 크기가 이 범위(배수) 밖이면 흔드는 중 → 무시 */
    const val G_MIN = 0.55f
    const val G_MAX = 1.6f
    /** 저역 통과 필터 비율 (1 = 필터 없음) */
    const val TILT_SMOOTHING = 0.45f

    // ── 연출 (속도 배율 적용) ──
    /** 정답(초록)·패스(주황) 화면 */
    const val FLASH_MS = 600L
    /** "시간 끝!" 화면 */
    const val TIME_UP_MS = 1500L
    /** 단어 목록 공개 배너 */
    const val REVIEW_BANNER_MS = 1500L
    /** 단어 칩이 하나씩 나타나는 간격 */
    const val REVIEW_CHIP_MS = 70L
    /** 게임 끝 배너 */
    const val END_MS = 2200L
}
