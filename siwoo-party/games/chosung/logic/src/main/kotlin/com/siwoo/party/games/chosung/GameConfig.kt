package com.siwoo.party.games.chosung

/** 초성 퀴즈 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 문제 수 (CHOSUNG_QUESTIONS) — 단어가 모자라면 있는 만큼 */
    const val CHOSUNG_QUESTIONS = 15
    /** 맞히면 + */
    const val CORRECT_POINTS = 1
    /** 틀리면 - (그리고 그 문제는 버저 잠금) */
    const val WRONG_PENALTY = 1

    // ── 규칙 시간 (속도 배율 적용 안 함, 게임 시계 = 일시정지 제외) ──
    /** 버저를 누른 사람이 말로 답하고 판정까지 받는 시간. 이 안에 판정이 없으면 오답 */
    const val ANSWER_MS = 5000L
    /** 아무도 못 맞히면 문제가 열린 뒤 이 시간에 힌트(글자 하나 공개) */
    const val HINT_AT_MS = 10_000L
    /** 아무도 못 맞히면 이 시간에 정답 공개 (문제 끝, 점수 없음) */
    const val REVEAL_AT_MS = 20_000L

    // ── 단어 목록 ──
    /** 카테고리당 최대 단어 수 (설정) */
    const val MAX_WORDS = 60
    /** 카테고리당 최소 단어 수 (모자라면 기본 단어로 채움) */
    const val MIN_WORDS = 5
    /** 단어 최대 글자 수 */
    const val MAX_WORD_LEN = 12
    /** 단어에 한글 음절이 이만큼은 있어야 (초성 퀴즈가 되려면) */
    const val MIN_SYLLABLES = 2

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 (뒤에 3·2·1) */
    const val INTRO_MS = 1500L
    /** 정답! 배너 + 정답 보여 주기 */
    const val CORRECT_SHOW_MS = 1800L
    /** 아무도 못 맞혀 정답 공개 */
    const val REVEAL_SHOW_MS = 2200L
    /** 다음 문제 전 빈 화면 */
    const val GAP_MS = 450L
    /** 오답·힌트 반짝 */
    const val FLASH_MS = 700L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 2000L
}
