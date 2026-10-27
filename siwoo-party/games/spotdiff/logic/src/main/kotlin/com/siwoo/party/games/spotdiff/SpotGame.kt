package com.siwoo.party.games.spotdiff

import com.siwoo.party.common.Ranking
import kotlin.random.Random

/** 진행 단계 */
enum class Phase {
    /** 장면 시작 전 */
    READY,
    /** 모두 버저를 누를 수 있음 */
    SEARCH,
    /** 누군가 버저를 눌러 그림을 터치하는 중 (다른 버저는 막힘) */
    ANSWER,
    /** 장면 끝 (5곳 다 찾음 또는 시간 끝) */
    SCENE_CLEAR,
    /** 3장면 끝 */
    GAME_OVER,
}

/** 버저 결과 */
sealed class Buzz {
    /** 지금은 누를 때가 아님 */
    object Ignored : Buzz()
    /** 잠금 중 */
    class Locked(val player: Int, val untilMs: Long) : Buzz()
    /** 다른 사람이 답하는 중 */
    class Busy(val answerer: Int) : Buzz()
    /** 버저 성공: deadlineMs 까지 그림을 터치해야 함 */
    class Accepted(val player: Int, val deadlineMs: Long) : Buzz()
}

/** 그림 터치(또는 시간 초과) 결과 */
sealed class Answer {
    object Ignored : Answer()
    /** 정답: diff 번째 차이를 찾음 (+1). sceneDone = 이 장면 5곳을 다 찾음 */
    class Correct(val player: Int, val diff: Int, val sceneDone: Boolean) : Answer()
    /** 오답 또는 시간 초과 (-1), untilMs 까지 버저 잠금 */
    class Wrong(val player: Int, val untilMs: Long, val timeout: Boolean) : Answer()
}

/**
 * 틀린 그림 찾기 대결 규칙.
 * - 장면마다 두 그림(왼쪽 원래, 오른쪽 차이 5곳). 각자 [찾았다!] 버저를 먼저 누른 사람이 3초 안에 차이 위치를 터치.
 * - 정답 +1 (동그라미), 오답·시간 초과 -1 + 2초 버저 잠금. 누군가 답하는 동안 다른 버저는 막힌다.
 * - 5곳을 다 찾으면 다음 장면. 90초 안에 못 찾으면 남은 차이 공개 후 다음 장면 (점수 없음).
 * - 3장면 합산 점수 순 (음수 가능, 같은 점수 같은 순위).
 * 시간(ms, 게임 시간)과 장면은 밖에서 넣는다 → 테스트에서 그대로 재현 가능.
 */
class SpotGame(
    val playerCount: Int,
    private val random: Random = Random.Default,
    val totalScenes: Int = GameConfig.SCENES,
) {
    init {
        require(playerCount in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS)
    }

    val scores = IntArray(playerCount)
    /** 플레이어별 버저 잠금 끝 시각 (게임 시간) */
    val lockUntil = LongArray(playerCount) { Long.MIN_VALUE }
    var phase = Phase.READY
        private set
    /** 지금 장면 번호 (0부터, 시작 전 -1) */
    var sceneIndex = -1
        private set
    var scene: Scene? = null
        private set
    /** 이번 장면에서 찾은 차이 */
    val found = BooleanArray(GameConfig.DIFFS_PER_SCENE)
    /** 차이를 찾은 사람 (-1 아직, REVEALED 시간 끝 공개) */
    val foundBy = IntArray(GameConfig.DIFFS_PER_SCENE) { -1 }
    /** 지금 답하는 사람 (-1 없음) */
    var answerer = -1
        private set
    var deadline = 0L
        private set

    val foundCount: Int get() = found.count { it }
    val gameOver: Boolean get() = phase == Phase.GAME_OVER
    /** 다음 장면이 남았는지 */
    val hasNextScene: Boolean get() = sceneIndex + 1 < totalScenes

    fun isLocked(player: Int, nowMs: Long): Boolean = nowMs < lockUntil[player]

    /** 다음 장면 시작 (given 이 없으면 무작위 생성) */
    fun startNextScene(given: Scene? = null): Scene {
        check(phase == Phase.READY || phase == Phase.SCENE_CLEAR) { "장면을 바꿀 수 없는 단계: $phase" }
        check(hasNextScene) { "장면이 더 없습니다" }
        val s = given ?: SceneGenerator.generate(random.nextLong())
        require(s.differences.size == GameConfig.DIFFS_PER_SCENE)
        sceneIndex++
        scene = s
        found.fill(false)
        foundBy.fill(-1)
        answerer = -1
        phase = Phase.SEARCH
        return s
    }

    /** player 가 nowMs 에 버저를 눌렀다 */
    fun buzz(player: Int, nowMs: Long): Buzz {
        if (player !in 0 until playerCount) return Buzz.Ignored
        if (phase == Phase.ANSWER) return Buzz.Busy(answerer)
        if (phase != Phase.SEARCH) return Buzz.Ignored
        if (isLocked(player, nowMs)) return Buzz.Locked(player, lockUntil[player])
        answerer = player
        deadline = nowMs + GameConfig.ANSWER_MS
        phase = Phase.ANSWER
        return Buzz.Accepted(player, deadline)
    }

    /** 답하는 사람이 그림 (x, y)(그림 좌표)를 nowMs 에 터치 */
    fun answer(x: Float, y: Float, nowMs: Long): Answer {
        if (phase != Phase.ANSWER) return Answer.Ignored
        if (nowMs > deadline) return wrong(nowMs, true)
        val s = scene ?: return Answer.Ignored
        val d = s.findAt(x, y, found)
        if (d < 0) return wrong(nowMs, false)
        val p = answerer
        found[d] = true
        foundBy[d] = p
        scores[p] += GameConfig.CORRECT_POINTS
        answerer = -1
        val done = foundCount == GameConfig.DIFFS_PER_SCENE
        phase = if (done) endScenePhase() else Phase.SEARCH
        return Answer.Correct(p, d, done)
    }

    /** 답 시간이 지났는지 확인 (지났으면 시간 초과 오답) */
    fun answerTimeout(nowMs: Long): Answer {
        if (phase != Phase.ANSWER || nowMs < deadline) return Answer.Ignored
        return wrong(nowMs, true)
    }

    private fun wrong(nowMs: Long, timeout: Boolean): Answer {
        val p = answerer
        scores[p] -= GameConfig.WRONG_PENALTY
        lockUntil[p] = nowMs + GameConfig.LOCK_MS
        answerer = -1
        phase = Phase.SEARCH
        return Answer.Wrong(p, lockUntil[p], timeout)
    }

    /**
     * 장면 제한 시간 끝: 찾는 중이면 남은 차이를 공개하고 장면을 끝낸다 → 공개한 차이 번호들.
     * 누가 답하는 중이면 아무것도 안 하고 null (답이 끝난 뒤 다시 부른다).
     */
    fun sceneTimeUp(): List<Int>? {
        if (phase != Phase.SEARCH) return null
        val revealed = ArrayList<Int>()
        for (i in found.indices) if (!found[i]) {
            found[i] = true
            foundBy[i] = REVEALED
            revealed.add(i)
        }
        phase = endScenePhase()
        return revealed
    }

    private fun endScenePhase(): Phase = if (hasNextScene) Phase.SCENE_CLEAR else Phase.GAME_OVER

    /** 최종 순위 (1부터): 합산 점수 높은 순, 같은 점수 같은 순위 */
    fun finalRanks(): IntArray = Ranking.competition(scores.map { it.toLong() })

    companion object {
        /** foundBy: 시간이 끝나 공개된 차이 */
        const val REVEALED = -2
    }
}
