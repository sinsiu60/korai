package com.siwoo.party.games.janggi

/**
 * 장기 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    // ── 판 ──
    /** 세로줄 수 (가로 방향 교차점 수) */
    const val COLS = 9
    /** 가로줄 수 (세로 방향 교차점 수) */
    const val ROWS = 10
    const val CELLS = COLS * ROWS

    // ── 규칙 옵션 기본값 (허브 [장기 설정] · 시작 화면에서 바꿈) ──
    /** 한 사람이 한 판에 쓸 수 있는 무르기 횟수 */
    const val UNDO_PER_PLAYER = 1
    /** 무르기 기본 켬 */
    const val UNDO_DEFAULT = true
    /** 판 회전(차례가 바뀌면 판을 180° 돌림) 기본 끔 */
    const val ROTATE_DEFAULT = false
    /** 빅장 무승부 기본 끔 */
    const val BIKJANG_DEFAULT = false
    /** 수 제한 선택지 (0 = 없음). 한 사람이 한 번 두면 1수 */
    val MOVE_LIMITS = intArrayOf(0, 100, 150, 200)
    const val MOVE_LIMIT_DEFAULT = 0

    // ── 점수 판정 (수 제한에 걸렸을 때, 0.1점 단위) ──
    const val SCORE_CHARIOT = 130
    const val SCORE_CANNON = 70
    const val SCORE_HORSE = 50
    const val SCORE_ELEPHANT = 30
    const val SCORE_GUARD = 30
    const val SCORE_SOLDIER = 20
    /** 나중에 두는 한(漢)의 덤 1.5점 → 점수가 같아질 수 없다 */
    const val HAN_BONUS = 15

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "초 가람 vs 한 나래" 소개 */
    const val INTRO_MS = 1900L
    /** 기물이 미끄러져 가는 시간 */
    const val MOVE_MS = 260L
    /** 잡힌 기물이 작아지며 사라지는 시간 */
    const val CAPTURE_MS = 320L
    /** "장군!" 배너 */
    const val CHECK_BANNER_MS = 1200L
    /** "빅장!" 배너 */
    const val BIKJANG_BANNER_MS = 1500L
    /** 이동 뒤 판 회전 시작까지 */
    const val ROTATE_DELAY_MS = 220L
    /** 판이 180° 도는 시간 */
    const val ROTATE_MS = 650L
    /** 승부가 난 뒤 끝 배너까지 */
    const val END_DELAY_MS = 700L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2800L
    /** "무르기!" 배너 */
    const val UNDO_BANNER_MS = 1000L
}
