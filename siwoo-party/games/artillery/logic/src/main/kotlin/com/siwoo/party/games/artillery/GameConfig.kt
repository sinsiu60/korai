package com.siwoo.party.games.artillery

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 포탄 대결 수치. 길이는 "전장 단위"(전장 가로 = WORLD_W), y 는 위쪽이 + (0 = 화면 맨 아래), 시간은 ms.
 * 속도는 단위/초, 가속도는 단위/초². 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 전장 ──
    /** 전장 가로 (이 밖으로 나간 포탄은 빗나감) */
    const val WORLD_W = 100f
    /** 화면에 꼭 보여야 하는 높이 (화면이 더 길쭉하면 하늘이 더 보임) */
    const val VIEW_MIN_H = 42f

    // ── 지형 (시드로 정해지는 언덕, 파괴 없음) ──
    /** 높이 표본 수 (간격 = WORLD_W / (SAMPLES − 1)) */
    const val SAMPLES = 201
    /** 기본 땅 높이 */
    const val BASE_H = 11f
    /** 지형 높이 범위 */
    const val MIN_H = 4f
    const val MAX_H = 27f
    /** 겹쳐 더하는 사인 물결 수와 첫 물결 진폭 (뒤로 갈수록 작고 촘촘하게) */
    const val WAVES = 4
    const val WAVE_AMP = 5.5f
    /** 가운데 근처 큰 언덕 (포탄이 넘어가야 하는 산): 최대 높이·폭 */
    const val HILL_MAX = 11f
    const val HILL_WIDTH = 15f
    /** 높이를 고르게 다듬는 횟수 (이웃 평균) */
    const val SMOOTH_PASSES = 3

    // ── 대포 ──
    /** 양 끝 대포가 전장 가장자리에서 떨어진 거리 → 그 사이에 고르게 */
    const val CANNON_MARGIN = 10f
    /** 대포 자리는 이 반폭만큼 평평하게 다지고, 그 밖으로 BLEND 만큼 원래 땅과 이어 붙인다 */
    const val PAD_HALF = 2.6f
    const val PAD_BLEND = 3.5f
    /** 땅에서 대포 몸통(포신 축) 중심까지 높이 */
    const val CANNON_LIFT = 1.3f
    /** 대포 몸통 맞음 판정 반지름 (포탄이 여기 닿으면 직격) */
    const val CANNON_R = 1.9f
    /** 포신 길이 (포탄이 나오는 자리 = 몸통 중심 + 방향 × 길이) */
    const val BARREL = 3.1f
    /** 처음 체력 */
    const val START_HP = 100

    // ── 발사 ──
    /** 힘 100% 일 때 처음 속도 (평지 45° 사거리 ≈ MAX_SPEED² / GRAVITY = 120) */
    const val MAX_SPEED = 60f
    /** 이보다 약한 힘(%)은 쏘지 않음 — 새총 최소 거리와 같은 비율 */
    const val MIN_POWER_PCT = 15
    /** 각도 범위 (도, 0 = 오른쪽 수평, 90 = 바로 위, 180 = 왼쪽 수평) */
    const val ANGLE_MIN = 0
    const val ANGLE_MAX = 180
    /** 새총: 이만큼(dp) 끌면 힘 100% */
    const val MAX_DRAG_DP = 130f

    // ── 탄도 ──
    /** 중력 (아래로) */
    const val GRAVITY = 30f
    /** 바람: 차례마다 −WIND_LEVELS..WIND_LEVELS 단계 중 하나 (0 = 바람 없음), 1단계 = 이만큼의 가로 가속도 */
    const val WIND_LEVELS = 5
    const val WIND_PER_LEVEL = 0.8f
    /** 1/60초 한 단계를 이만큼 나눠 이동·충돌 */
    const val SUBSTEPS = 4
    /** 포탄 반지름 (대포와 닿음 판정·그림) */
    const val SHELL_R = 0.45f
    /** 땅 충돌 위치를 찾는 반으로 나누기 횟수 */
    const val BISECT = 14
    /** 안전장치: 이 단계 수(20초)가 지나도 안 떨어지면 빗나감 */
    const val MAX_FLIGHT_STEPS = 20 * PhysicsConfig.STEPS_PER_SEC
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 50L
    /** 날아간 자취 점: 최대 수, 몇 단계마다 하나 */
    const val TRAIL_MAX = 90
    const val TRAIL_EVERY = 3

    // ── 피해 ──
    /** 폭발 반경: 대포 몸통(원)까지 거리 d 가 이보다 짧으면 피해 = MAX_DAMAGE × (1 − d / BLAST_R) (반올림) */
    const val BLAST_R = 8f
    /** 직격(몸통에 바로 맞음, d = 0) 피해 */
    const val MAX_DAMAGE = 50

    // ── 끝 ──
    /** 안전장치: 발사가 (인원 × 이 수)번이면 끝 — 남은 사람은 체력 순 */
    const val MAX_TURNS_PER_PLAYER = 10

    // ── 연출 (속도 배율 적용) ──
    /** 처음 소개 배너 */
    const val INTRO_MS = 1800L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 1100L
    /** 폭발 애니메이션 (체력 막대가 줄어드는 시간 포함) */
    const val EXPLODE_MS = 900L
    /** 폭발이 끝난 뒤 다음 진행까지 */
    const val AFTER_EXPLODE_MS = 350L
    /** 빗나감(화면 밖) 뒤 다음 차례까지 */
    const val MISS_DELAY_MS = 700L
    /** "○○ 탈락!" 배너 */
    const val ELIM_BANNER_MS = 1400L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2400L
    /** 구름이 바람 1단계에 흘러가는 속도 (단위/초) */
    const val CLOUD_DRIFT = 1.6f
}
