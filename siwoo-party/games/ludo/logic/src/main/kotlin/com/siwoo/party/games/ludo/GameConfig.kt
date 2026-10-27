package com.siwoo.party.games.ludo

/**
 * 말판 레이스 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다.
 *
 * 규칙 선택 (명세에서 정하지 않은 부분)
 * - **판**: 십자형 15×15 판, 바깥 길 52칸 + 색마다 집 통로 6칸(통로 5칸 + 가운데 집).
 *   말은 자기 출발 칸에서 시계 방향으로 51칸(출발 칸 포함)을 돈 뒤 자기 색 통로로 들어가 집까지 간다 (진행 0~56, 56 = 집).
 *   2~3인도 4색 판을 그대로 쓰고 빈 자리는 비워 둔다 (2인은 마주 보는 자리 0·2, 3인은 0·1·2).
 * - **출발**: 6이 나와야 대기 말을 출발 칸(진행 0)에 올릴 수 있다 (6을 다 써서 출발 칸에 멈춤).
 * - **6이면 한 번 더**: 이동한 뒤 한 번 더 굴린다. 6이 나왔는데 움직일 말이 없어도 한 번 더 굴린다.
 *   **3연속 6**: 세 번째 6은 이동하지 못하고(무효) 바로 차례가 끝난다. 6이 아닌 눈이 나오면 연속 수는 0으로.
 * - **움직일 말이 없으면** (6이 아닌 눈) 차례가 자동으로 넘어간다.
 * - **잡기**: 바깥 길의 안전 칸이 아닌 칸에서 상대 말 위에 정확히 멈추면 그 칸의 상대 말을 모두 대기로 돌려보낸다.
 *   잡으면 이동이 끝난 뒤 **한 번 더 굴린다** (6 보너스와 겹쳐도 한 번만). 집 통로는 자기 색만 들어가므로 잡기가 없다.
 * - **안전 칸**: 네 출발 칸 + 별 칸 4개(각 출발 칸에서 8칸 앞). 여기서는 여러 색이 함께 설 수 있고 잡을 수 없다.
 * - **블록 없음**: 같은 색 말이 겹쳐도(어느 칸이든) 길을 막지 않는다. 지나가는 말은 아무도 잡지 않는다.
 * - **집 정확히**: 집(진행 56)까지 남은 칸보다 큰 눈으로는 그 말을 움직일 수 없다 (넘치면 이동 불가).
 *   집에 들어가도 보너스 굴림은 없다.
 * - **순위**: 말 4개를 모두 집에 넣은 순서. 한 명만 남으면 그 사람이 꼴찌. 3~4인에서 1위가 나오면
 *   [끝까지 진행 / 여기서 종료] — 여기서 끝내면 남은 사람은 집에 넣은 수 → 말 진행 합 순 (같으면 공동).
 * - 움직일 수 있는 말이 하나뿐이면(또는 모두 같은 이동이면 — 대기 말 여러 개 출발 등) 잠깐 발광한 뒤 자동으로 움직인다.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 1인당 말 수 */
    const val PIECES = 4
    /** 자리 수 (판은 항상 4색) */
    const val SEATS = 4

    /** 바깥 길 칸 수 */
    const val TRACK = 52
    /** 자리 하나가 차지하는 바깥 길 칸 수 (다음 자리 출발 칸까지) */
    const val SEAT_SPAN = 13
    /** 출발 칸에서 별(안전) 칸까지 */
    const val STAR_OFFSET = 8
    /** 바깥 길에서 마지막 진행 값 (출발 칸 0 → 50, 다음 걸음부터 집 통로) */
    const val LAST_TRACK = 50
    /** 집 통로 칸 수 (집 제외) */
    const val HOME_LANE = 5
    /** 집 (진행 끝) */
    const val HOME = LAST_TRACK + HOME_LANE + 1

    /** 출발에 필요한 눈 · 한 번 더 굴리는 눈 */
    const val RELEASE_ROLL = 6
    /** 이 횟수만큼 연속 6이면 그 6은 무효, 차례 끝 */
    const val MAX_SIXES = 3
    /** 잡으면 한 번 더 */
    const val CAPTURE_BONUS = true
    /** 움직일 수 있는 이동이 한 가지뿐이면 자동 이동 */
    const val AUTO_SINGLE_MOVE = true

    // ── 연출 (속도 배율 적용) ──
    /** 첫 차례 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 900L
    /** 주사위가 구르는 시간 */
    const val ROLL_MS = 700L
    /** 굴린 눈을 보여 준 뒤 다음 단계까지 */
    const val RESULT_SHOW_MS = 350L
    /** 말이 한 칸 통 뛰는 시간 */
    const val STEP_MS = 170L
    /** 대기 말이 출발 칸으로 뛰어오르는 시간 */
    const val RELEASE_MS = 420L
    /** 잡힌 말이 대기 자리로 날아가는 시간 */
    const val CAPTURE_FLY_MS = 650L
    /** "잡았다!" 배너 */
    const val CAPTURE_BANNER_MS = 1200L
    /** "집 도착!" 배너 */
    const val HOME_BANNER_MS = 1100L
    /** 6 / 한 번 더 배너 */
    const val SIX_BANNER_MS = 900L
    /** 3연속 6 배너 */
    const val TRIPLE_SIX_BANNER_MS = 1400L
    /** 움직일 말이 없을 때 안내 배너 */
    const val NO_MOVE_BANNER_MS = 1200L
    /** 이동 가능한 말 발광이 한 번 숨 쉬는 시간 (입력을 기다리는 동안만 돈다) */
    const val GLOW_PULSE_MS = 900L
    /** 이동이 한 가지뿐일 때 발광을 보여 준 뒤 자동 이동까지 */
    const val AUTO_MOVE_MS = 550L
    /** 차례가 바뀔 때 잠깐 쉼 */
    const val TURN_GAP_MS = 300L
    /** 순위 확정 배너 */
    const val FINISH_BANNER_MS = 1600L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2200L
}
