package com.siwoo.party.games.charades

/** 몸으로 말해요 수치 (시간 단위 ms). "연출" 시간은 화면에서 공통 속도 배율(t)이 곱해지고, 규칙 시간은 그대로. */
object GameConfig {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 4
    /** 이 인원이면 2 대 2 팀전, 아니면(3인) 개인전 */
    const val TEAM_PLAYERS = 4
    const val TEAM_SIZE = 2

    // ── 제시어 ──
    /** 카테고리 수 (동물·운동·직업·행동·물건) */
    const val CATEGORY_COUNT = 5
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

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 설명자 한 명(=한 팀 차례) 제한 시간 90초 */
    const val TURN_MS = 90_000L
    /** 차례마다 패스 최대 횟수 */
    const val MAX_PASSES = 3
    /** 팀전: 맞힌 단어 하나 = 팀 +1 */
    const val POINTS_TEAM = 1
    /** 개인전: 맞힌 사람 +1 */
    const val POINTS_GUESSER = 1
    /** 개인전: 설명한 사람 +1 */
    const val POINTS_EXPLAINER = 1

    // ── 연출 (속도 배율 적용) ──
    /** 팀 뽑기: 이름표가 양쪽을 오가는 횟수, 첫/마지막 간격(점점 느려짐), 확정 뒤 보여 주기 */
    const val REVEAL_STEPS = 10
    const val REVEAL_STEP_FIRST_MS = 110L
    const val REVEAL_STEP_LAST_MS = 460L
    const val REVEAL_HOLD_MS = 1500L
    /** 팀 소개·개인전 소개 배너 */
    const val INTRO_MS = 1800L
    /** 단어가 튀어나오는 연출 */
    const val POP_MS = 220L
    /** 정답 초록 번쩍 */
    const val FLASH_MS = 600L
    /** 패스 주황 화면 */
    const val PASS_FLASH_MS = 420L
    /** "시간 끝!" 화면 */
    const val TIME_UP_MS = 1500L
    /** 단어 목록 공개 배너 */
    const val REVIEW_BANNER_MS = 1500L
    /** 단어 칩이 하나씩 나타나는 간격 */
    const val REVIEW_CHIP_MS = 70L
    /** 게임 끝 배너 */
    const val END_MS = 2200L
}
