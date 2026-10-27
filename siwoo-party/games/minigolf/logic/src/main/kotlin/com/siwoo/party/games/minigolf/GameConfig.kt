package com.siwoo.party.games.minigolf

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 미니 골프 수치. 길이는 "코스 단위"(코스 상자 COURSE_W × COURSE_H, 공 반지름 BALL_R), 속도는 단위/초, 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 홀 수 (코스 전체) */
    const val HOLES = 9
    /** 한 홀 최대 타수 (MINIGOLF_MAX_STROKES). 이 타수 안에 못 넣어도 이 타수로 기록하고 다음 사람 */
    const val MAX_STROKES = 6
    /** 물에 빠지면 더하는 벌타 */
    const val WATER_PENALTY = 1

    // ── 코스 (모든 홀이 같은 상자 안에 그려진다) ──
    const val COURSE_W = 32f
    const val COURSE_H = 18f
    /** 공 반지름 */
    const val BALL_R = 0.42f
    /** 컵: 공 중심이 이 반지름 안에 들어오고 느리면 홀인 */
    const val CUP_R = 0.6f
    /** 컵 그림 반지름 */
    const val CUP_DRAW_R = 0.72f
    /** 이보다 빠르면 컵 위를 굴러 넘어간다 (단위/초) */
    const val CUP_CAPTURE_SPEED = 8f
    /** 빠르게 컵을 지나갈 때 가장자리에 걸려 꺾이는 최대 각도 (라디안) */
    const val CUP_LIP_ANGLE = 0.35f

    // ── 퍼팅 (새총) ──
    /** 최대 힘으로 친 공의 처음 속도 (단위/초) */
    const val MAX_SPEED = 30f
    /** 이 거리(dp) 이상 끌어도 힘은 더 세지지 않음 */
    const val MAX_DRAG_DP = 120f
    /** 이보다 짧게(dp) 끌고 놓으면 취소 */
    const val MIN_DRAG_DP = 14f

    // ── 물리 (공통 물리 World 에 넘기는 값) ──
    /** 잔디 마찰 감속 (단위/초²) */
    const val FRICTION = 9f
    /** 공기 저항 (1/초) */
    const val DRAG = 0.45f
    /** 이보다 느려지면 멈춤 */
    const val STOP_SPEED = 0.15f
    /** 모래 마찰 배수 (잔디의 몇 배) */
    const val SAND_FRICTION = 4f
    /** 경사 세기 기본값 (단위/초²) — 잔디 마찰보다 작아야 멈춘 공이 저절로 굴러가지 않는다 */
    const val SLOPE = 5f
    /** 경사 세기 상한 (마찰의 이 비율 미만) — 공이 반드시 멈추게 */
    const val SLOPE_MAX_RATIO = 0.8f
    /** 벽 반발 계수 */
    const val WALL_E = 0.78f
    /** 공 반발 계수 */
    const val BALL_E = 0.85f
    /** 한 단계를 몇 번으로 나눠 이동 (빠른 공의 벽 뚫기 방지: 30/60/4 = 0.125 < 공 반지름) */
    const val SUBSTEPS = 4
    /** 고정 시간 단계 */
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    /** 안전장치: 한 번 친 공이 이보다 오래 구르면 그 자리에 멈춤 */
    const val MAX_ROLL_MS = 20_000L

    // ── 연출 (속도 배율 적용) ──
    /** 처음 소개 배너 */
    const val INTRO_MS = 1800L
    /** "3번 홀" 배너 */
    const val HOLE_BANNER_MS = 1500L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 900L
    /** "홀인!" 배너 */
    const val HOLED_MS = 1700L
    /** "홀인원!" 대형 글자 + 폭죽 */
    const val HOLE_IN_ONE_MS = 3000L
    /** "퐁당!" (물) 배너 */
    const val WATER_MS = 1100L
    /** 최대 타수로 끝남 배너 */
    const val MAXED_MS = 1500L
    /** 공이 컵에 빠져 작아지는 시간 */
    const val SINK_MS = 380L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2600L

    // ── 소리 ──
    /** 벽 소리 최소 간격 (ms) */
    const val WALL_SOUND_GAP_MS = 90L
    /** 이보다 약하게 부딪히면 소리 없음 (단위/초) */
    const val WALL_SOUND_MIN = 2f
}
