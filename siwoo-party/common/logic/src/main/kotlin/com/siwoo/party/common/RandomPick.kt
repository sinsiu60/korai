package com.siwoo.party.common

/**
 * 랜덤 게임 후보 고르기 (허브 랜덤 · 파티 랜덤 공통, Android 의존성 없음).
 * 룰렛에 올라가는 조건: 스위치 켬 + 현재 인원 지원 + 기기 조건(동시 터치 수, 센서) 충족.
 * 같은 게임 연속 방지(기본 켬): 직전 게임은 빼되, 그러면 후보가 없어질 때(후보 1개뿐)는 예외.
 */
class RandomEntry(
    val id: String,
    /** 랜덤 스위치 */
    val enabled: Boolean,
    /** 지금 인원으로 할 수 있는지 */
    val supportsPlayers: Boolean,
    /** 지금 인원으로 동시에 필요한 손가락 수 (0 = 필요 없음) */
    val requiredTouches: Int = 0,
    val needsSensor: Boolean = false,
)

/** 기기 조건. maxTouches < 0 이면 아직 모름 → 된다고 본다 */
class DeviceCaps(val maxTouches: Int, val hasAccelerometer: Boolean)

/** 후보에서 빠진 이유 (설정 화면 안내용) */
enum class RandomBlock { NONE, SWITCH_OFF, PLAYERS, TOUCHES, SENSOR }

object RandomPick {

    /** 인원·기기 조건만 (스위치와 상관없이) — 실제로 할 수 있는 게임인지 */
    fun playable(e: RandomEntry, device: DeviceCaps): Boolean = blockOf(e, device, ignoreSwitch = true) == RandomBlock.NONE

    /** 이 게임이 후보에서 빠지는 이유 (빠지지 않으면 NONE) */
    fun blockOf(e: RandomEntry, device: DeviceCaps, ignoreSwitch: Boolean = false): RandomBlock = when {
        !ignoreSwitch && !e.enabled -> RandomBlock.SWITCH_OFF
        !e.supportsPlayers -> RandomBlock.PLAYERS
        e.requiredTouches > 0 && device.maxTouches >= 0 && device.maxTouches < e.requiredTouches -> RandomBlock.TOUCHES
        e.needsSensor && !device.hasAccelerometer -> RandomBlock.SENSOR
        else -> RandomBlock.NONE
    }

    /** 스위치·인원·기기 조건을 모두 통과한 게임 (연속 방지 전) */
    fun eligible(entries: List<RandomEntry>, device: DeviceCaps): List<String> =
        entries.filter { blockOf(it, device) == RandomBlock.NONE }.map { it.id }

    /**
     * 룰렛에 올릴 후보.
     * @param lastId 직전에 한 게임 (없으면 null)
     * @param avoidRepeat 같은 게임 연속 방지
     */
    fun candidates(entries: List<RandomEntry>, device: DeviceCaps, lastId: String?, avoidRepeat: Boolean): List<String> {
        val ok = eligible(entries, device)
        if (!avoidRepeat || lastId == null || ok.size <= 1) return ok
        val without = ok.filter { it != lastId }
        return if (without.isEmpty()) ok else without
    }
}

/** 카테고리 스위치 상태: 전부 켬 / 전부 끔 / 일부 */
enum class GroupState { ON, OFF, PARTIAL }

/** 카테고리 스위치 ↔ 게임 스위치 연동 */
object SwitchGroup {
    fun state(values: List<Boolean>): GroupState = when {
        values.isEmpty() -> GroupState.OFF
        values.all { it } -> GroupState.ON
        values.none { it } -> GroupState.OFF
        else -> GroupState.PARTIAL
    }

    /** 카테고리 스위치를 눌렀을 때 게임 스위치들의 새 값: 켬 → 전부 끔, 끔·일부 → 전부 켬 */
    fun toggle(values: List<Boolean>): Boolean = state(values) != GroupState.ON
}
