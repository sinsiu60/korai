package com.siwoo.party.games.traceshape

/**
 * 도형 따라 그리기 수치 (시간 단위 ms, 거리 단위 = 도형 상자 한 변(0..1) 비율).
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 규칙 시간(제한 시간·속도 보너스)은 배율 적용 안 함.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 라운드 수 (라운드마다 다른 도형) */
    const val ROUNDS = 5
    /** 한 라운드 제한 시간 (규칙 시간 — 배율 적용 안 함) */
    const val ROUND_MS = 12_000L
    /** 남은 시간이 이 이하면 시간 막대가 빨갛게 */
    const val HURRY_MS = 4_000L

    // ── 그린 점 모으기 ──
    /** 한 사람이 한 라운드에 모을 수 있는 점 수 (사이 채운 점 포함, 넘으면 더 안 받음) */
    const val MAX_POINTS = 1200
    /** 앞 점과 이보다 가까운 점은 버린다 (손가락 떨림·같은 자리) */
    const val MIN_STEP = 0.004f
    /** 앞 점과 이보다 멀면 사이를 이 간격으로 채운다 (빠르게 그려도 촘촘하게) */
    const val MAX_STEP = 0.015f
    /** 손가락이 상자 밖으로 이만큼까지만 (그 밖은 가장자리로 잘림) */
    const val OUTSIDE_LIMIT = 0.3f

    // ── 정확도 ──
    /** 목표 경로 위에 고르게 찍는 확인 점 수 (얼마나 다 따라갔는지 = 덮은 비율) */
    const val COVER_SAMPLES = 120
    /** 확인 점에서 이 거리 안에 그린 점이 있으면 "덮음" 1점 */
    const val COVER_FULL = 0.06f
    /** 이 거리부터는 0점 (사이는 직선으로 줄어듦) */
    const val COVER_ZERO = 0.12f
    /** 평균 거리가 이 이하이면 거리 점수 만점 (손가락 굵기 여유) */
    const val DIST_FREE = 0.008f
    /** 평균 거리가 이 이상이면 거리 점수 0 */
    const val DIST_ZERO = 0.15f

    // ── 자동 완료 ──
    /**
     * 덮은 비율이 이 이상 + 끝점(거꾸로면 시작점) 가까이 오면 자동 완료.
     * 끝점과 상관없이 확인 점이 모두 COVER_FULL 안으로 덮여도 완료 (닫힌 도형을 중간부터 그린 경우 등)
     */
    const val AUTO_COVERAGE = 0.9f
    /** 끝점 근처 판정 거리 */
    const val END_TOL = 0.08f
    /** 자동 완료 순간 마지막 점이 끝점(거꾸로면 시작점)에서 이 거리 안이면 끝점까지 선을 이어 준다 */
    const val SNAP_TOL = 0.15f

    // ── 속도 보너스 (완료한 사람만) ──
    /** 최대 배수 */
    const val BONUS_MAX = 1.5f
    /** 이 시간 안에 완료하면 최대 배수 */
    const val BONUS_FAST_MS = 3_000L
    /** 이 시간 이후 완료는 ×1.0 (사이는 직선) */
    const val BONUS_SLOW_MS = 10_000L

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1800L
    /** 라운드 안내 배너 ("2라운드 · 별") */
    const val ROUND_BANNER_MS = 1300L
    /** 배너가 사라진 뒤 그리기 시작 전 잠깐 */
    const val READY_MS = 500L
    /** 모두 완료한 뒤 공개까지 잠깐 ("완료!" 를 볼 시간) */
    const val ALL_DONE_MS = 450L
    /** 라운드 끝: 내 그림 + 정확도·보너스·점수 */
    const val REVEAL_MS = 2600L
    /** 라운드 끝: 모두의 그림 겹쳐 비교 */
    const val COMPARE_MS = 2600L
    /** "완료!" 도장·점수 커지는 연출 */
    const val POP_MS = 260L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
}
