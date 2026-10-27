package com.siwoo.party.games.oddone

/** 하나만 다른 모양 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 */
    const val ROUNDS = 15
    /** 격자 한 변 칸 수 (4×4) */
    const val GRID = 4
    const val CELLS = GRID * GRID
    /** 먼저 찾으면 + */
    const val CORRECT_POINTS = 1
    /** 틀린 뒤 잠금 시간 (게임 규칙이라 속도 배율 적용 안 함) */
    const val WRONG_LOCK_MS = 1000L
    /** 이 시간 동안 아무도 못 찾으면 공개하고 넘어감 (게임 규칙) */
    const val ROUND_TIMEOUT_MS = 10_000L

    // ── 모양 기본값 ──
    /** 기본 채도·밝기 (HSL, 0..1) */
    const val BASE_SAT = 0.80f
    const val BASE_LIGHT = 0.56f
    /** 기본 색상(도) 후보 — 남색 바탕에서 잘 보이는 색 (짙은 파랑은 뺌) */
    val BASE_HUES = floatArrayOf(0f, 24f, 46f, 120f, 168f, 198f, 282f, 305f, 330f)

    // ── 난이도: 라운드(1..15)별 차이 — 갈수록 작아진다 (단조 감소) ──
    /** 색: 밝기(HSL L) 차이 */
    val COLOR_DELTA = floatArrayOf(0.26f, 0.23f, 0.20f, 0.18f, 0.16f, 0.145f, 0.13f, 0.12f, 0.11f, 0.10f, 0.09f, 0.08f, 0.07f, 0.065f, 0.06f)
    /** 회전: 각도 차이(도) */
    val ROTATION_DELTA = floatArrayOf(45f, 40f, 35f, 31f, 28f, 25f, 22f, 20f, 18f, 16f, 14f, 13f, 12f, 11f, 10f)
    /** 크기: 배율 차이 (1 ± 이 값) */
    val SIZE_DELTA = floatArrayOf(0.30f, 0.27f, 0.24f, 0.22f, 0.20f, 0.18f, 0.16f, 0.145f, 0.13f, 0.12f, 0.11f, 0.10f, 0.09f, 0.085f, 0.08f)

    /** round(1부터) 번째 라운드의 kind 차이 */
    fun delta(kind: Diff, round: Int): Float {
        val i = (round - 1).coerceIn(0, ROUNDS - 1)
        return when (kind) {
            Diff.COLOR -> COLOR_DELTA[i]
            Diff.ROTATION -> ROTATION_DELTA[i]
            Diff.SIZE -> SIZE_DELTA[i]
        }
    }

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 (뒤에 3·2·1) */
    const val INTRO_MS = 1400L
    /** 정답 공개 (다른 하나 금색 테두리·"○○ +1") */
    const val REVEAL_MS = 1700L
    /** 시간 초과 공개 */
    const val TIMEOUT_SHOW_MS = 1700L
    /** 다음 라운드 전 빈 격자 */
    const val GAP_MS = 450L
    /** 오답 X·+1 떠오름 */
    const val FLASH_MS = 650L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 1900L
}
