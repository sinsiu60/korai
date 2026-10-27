package com.siwoo.party.games.tensec

/** 딱 10초 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 */
    const val ROUNDS = 3
    /** 목표 시간 범위 (초, 정수) */
    const val TARGET_MIN_SEC = 5
    const val TARGET_MAX_SEC = 15

    // ── 게임 규칙 시간 (속도 배율 적용 안 함) ──
    /** 타이머가 보이는 시간 — 그 뒤로는 마음속으로 센다 */
    const val TIMER_VISIBLE_MS = 3000L
    /** 안전장치: 목표 + 이 시간까지 안 누르면 그 시각(목표+10초)으로 자동 기록 */
    const val CAP_EXTRA_MS = 10_000L

    // ── 연출 (속도 배율 적용) ──
    /** 목표 시간 공개 배너 */
    const val TARGET_SHOW_MS = 2000L
    /** 전원 입력 뒤 "기록 공개" 배너까지 */
    const val ALL_IN_PAUSE_MS = 700L
    /** 기록 공개 배너 */
    const val REVEAL_INTRO_MS = 1300L
    /** 한 사람 기록이 굴러가다 멈추는 시간 */
    const val REVEAL_ROLL_MS = 1200L
    /** 한 사람 공개가 멈춘 뒤 다음 사람까지 */
    const val REVEAL_GAP_MS = 700L
    /** 가장 가까운 사람 강조 배너 */
    const val CLOSEST_SHOW_MS = 2200L
    /** 마지막 결과 배너 */
    const val FINAL_SHOW_MS = 2200L
    /** 터치 물결 */
    const val RIPPLE_MS = 500L
}
