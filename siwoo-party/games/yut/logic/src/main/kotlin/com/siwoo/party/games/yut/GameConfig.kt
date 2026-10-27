package com.siwoo.party.games.yut

/**
 * 윷놀이 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다.
 *
 * 규칙 선택 (판·골인·빽도) — 자세한 길은 YutBoard 참고
 * - 출발·골인 자리는 오른쪽 아래 모서리 참먹이. 말은 판 밖(대기)에서 들어와 도(1번 칸)부터 시계 반대 방향으로 돈다.
 * - **골인**: 길을 따라가다 참먹이에 도착하거나 지나가면 바로 골인 (참먹이에 "정확히 멈춰도" 골인).
 *   그래서 바깥 한 바퀴는 20걸음, 모 지름길(모→방→찌모) 16걸음, 모→방→참먹이 11걸음, 뒷모→방→참먹이 16걸음.
 * - **빽도**: 말이 지나온 길을 따라 한 칸 뒤로.
 *   도(1번 칸)에서 빽도가 나오면 참먹이(0번 칸)에 "선 채로" 판에 남는다 → 다음에 어떤 앞 결과든 쓰면 골인, 또 빽도면 날윷(19번)으로.
 *   판에 말이 없으면 빽도는 쓸 수 없다 (쓸 수 없는 결과만 남으면 자동으로 버림).
 *   설정 "빽도 대기말 올리기"를 켜면 대기 말을 출발점 바로 뒤(19번 칸, 날윷)에 올릴 수 있다 → 다음에 도만 나와도 골인.
 * - 윷·모가 나오면 이동하기 전에 한 번 더 던진다 (윷·모가 아닐 때까지). 그다음 대기열의 결과를 원하는 순서로 쓴다.
 * - 상대 말을 잡으면 그 이동이 끝난 뒤 한 번 더 던진다 (남은 대기열 결과는 그대로 두었다가 던진 뒤 함께 쓴다).
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 1인당 말 수 (기본) — 설정에서 2~4 */
    const val YUT_PIECES = 4
    const val PIECES_MIN = 2
    const val PIECES_MAX = 4

    /** 윷가락 수 */
    const val STICKS = 4
    /** 윷가락 하나가 배(평평한 면)가 나올 확률 */
    const val YUT_FLAT_RATE = 0.5
    /** 빽도 표시가 있는 윷가락 번호 (이 가락만 배가 나오면 빽도) */
    const val BACKDO_STICK = 0
    /** 낙(던지기 무효) 확률 — 설정에서 켰을 때만 */
    const val YUT_NAK_RATE = 0.05

    // ── 설정 기본값 ──
    const val SHAKE_DEFAULT = false
    const val NAK_DEFAULT = false
    const val BACKDO_ENTER_DEFAULT = false

    /** 흔들어 던지기: 이 세기(중력 배수) 이상 흔들면 던짐 */
    const val SHAKE_G = 2.2f
    /** 흔들어 던지기: 한 번 던진 뒤 다시 감지하지 않는 시간 (규칙 시간, 배율 없음) */
    const val SHAKE_COOLDOWN_MS = 800L

    // ── 연출 (속도 배율 적용) ──
    /** 첫 차례 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** 윷가락이 떠올라 돌고 떨어져 튕긴 뒤 멈출 때까지 */
    const val THROW_MS = 1150L
    /** 결과 글자를 보여 준 뒤 다음 단계까지 */
    const val RESULT_SHOW_MS = 650L
    /** "모!" 금색 대형 배너 · "윷!" 배너 */
    const val MO_BANNER_MS = 1300L
    /** "낙!" 배너 */
    const val NAK_BANNER_MS = 1300L
    /** 대기열 칩이 톡 나타나는 시간 */
    const val CHIP_POP_MS = 320L
    /** 말이 한 칸 뛰어가는 시간 */
    const val STEP_MS = 190L
    /** 업기: 말이 겹쳐지며 통 하는 시간 */
    const val STACK_POP_MS = 380L
    /** 잡기: 상대 말이 튕겨나가 패널로 날아가는 시간 */
    const val CAPTURE_FLY_MS = 850L
    /** "잡았다!" 배너 */
    const val CAPTURE_BANNER_MS = 1300L
    /** 골인: 말이 패널로 날아가는 시간 */
    const val GOAL_FLY_MS = 700L
    /** "골인!" 배너 */
    const val GOAL_BANNER_MS = 1200L
    /** 쓸 수 없는 결과를 버릴 때 안내 배너 */
    const val DISCARD_BANNER_MS = 1400L
    /** 차례가 바뀔 때 잠깐 쉼 */
    const val TURN_GAP_MS = 350L
    /** 도착 예정 칸 발광이 한 번 숨 쉬는 시간 (미리보기 중에만 돈다) */
    const val GLOW_PULSE_MS = 900L
    /** 1위 확정 배너 */
    const val FIRST_BANNER_MS = 1600L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2200L
}
