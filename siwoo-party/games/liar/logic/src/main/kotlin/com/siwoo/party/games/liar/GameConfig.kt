package com.siwoo.party.games.liar

/** 라이어 게임 수치 (시간 단위 ms·초). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 4

    // ── 제시어 ──
    /** 제시어 카테고리 수 (음식·장소·동물·직업·스포츠·물건) */
    const val CATEGORY_COUNT = 6
    /** 정답 맞히기 보기 수 (정답 포함) — 카테고리마다 최소 이만큼 단어가 있어야 한다 */
    const val GUESS_CHOICES = 8
    const val MIN_WORDS = GUESS_CHOICES
    /** 카테고리 하나에 넣을 수 있는 최대 단어 수 */
    const val MAX_WORDS = 40
    /** 단어 하나 최대 글자 수 */
    const val MAX_WORD_LEN = 10

    // ── 규칙 시간 (속도 배율 적용 안 함) ──
    /** 설명 시간: 1인 20초 (LIAR_EXPLAIN_SEC) */
    const val LIAR_EXPLAIN_SEC = 20
    /** 설명 바퀴 수 */
    const val LIAR_EXPLAIN_LAPS = 2
    /** 토론 시간 60초 (LIAR_DISCUSS_SEC) */
    const val LIAR_DISCUSS_SEC = 60
    /** 남은 시간이 이 초 이하면 빨갛게 + 째깍 */
    const val WARN_SEC = 5

    // ── 점수 ──
    /** 라이어가 안 걸림: 라이어 +2 */
    const val POINTS_NOT_CAUGHT = 2
    /** 걸렸지만 정답 맞힘: 라이어 +1 */
    const val POINTS_CAUGHT_CORRECT = 1
    /** 걸리고 정답 틀림: 라이어 제외 전원 +1 */
    const val POINTS_CAUGHT_WRONG = 1

    // ── 연출 (속도 배율 적용) ──
    const val ROUND_INTRO_MS = 1400L
    /** 카테고리 카드: 확대 등장 → 뒤집기 → 잠깐 보여 주기 */
    const val CATEGORY_ZOOM_MS = 600L
    const val CATEGORY_FLIP_MS = 700L
    const val CATEGORY_SHOW_MS = 1500L
    /** 개표: 표 한 장 뒤집기 + 이름 아래로 날아가 쌓이기, 다음 표까지 간격 */
    const val BALLOT_FLIP_MS = 520L
    const val BALLOT_FLY_MS = 420L
    const val BALLOT_GAP_MS = 380L
    /** 개표가 끝난 뒤 결과 발표까지 */
    const val COUNT_END_MS = 900L
    /** 동점·지목 배너 */
    const val VERDICT_SHOW_MS = 1700L
    /** 정체 공개 카드 뒤집기 + 보여 주기 */
    const val ROLE_FLIP_MS = 800L
    const val ROLE_SHOW_MS = 1500L
    /** 정답 맞히기 결과 */
    const val GUESS_RESULT_MS = 1700L
    /** 게임 끝 배너 */
    const val END_SHOW_MS = 2000L
}
