package com.siwoo.party.common.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.SessionCodec

/**
 * 허브 ↔ 게임 실행 규약.
 * - 허브: Party.gameIntent(...) 로 startActivityForResult
 * - 게임: Party.players(intent) 로 플레이어를 받고, 끝나면 Party.finishWithRanks(...)
 *   (중간에 나가면 RESULT_CANCELED 로 그냥 finish)
 * - 순위는 1부터, 동점 허용. ids[k] 의 순위가 ranks[k].
 */
object Party {
    const val EXTRA_PLAYERS = "party.players"
    const val EXTRA_GAME_ID = "party.gameId"
    const val EXTRA_IDS = "party.ids"
    const val EXTRA_RANKS = "party.ranks"

    fun gameIntent(context: Context, module: GameModule, players: List<PlayerInfo>): Intent =
        Intent(context, module.activityClass)
            .putExtra(EXTRA_GAME_ID, module.id)
            .putExtra(EXTRA_PLAYERS, SessionCodec.encodePlayers(players))

    /** 허브에서 왔으면 플레이어 목록, 아니면 null */
    fun players(intent: Intent?): List<PlayerInfo>? {
        val raw = intent?.getStringExtra(EXTRA_PLAYERS) ?: return null
        return try { SessionCodec.decodePlayers(raw) } catch (e: Exception) { null }
    }

    fun resultIntent(gameId: String, ids: IntArray, ranks: IntArray): Intent =
        Intent().putExtra(EXTRA_GAME_ID, gameId).putExtra(EXTRA_IDS, ids).putExtra(EXTRA_RANKS, ranks)

    fun finishWithRanks(activity: Activity, gameId: String, ids: IntArray, ranks: IntArray) {
        activity.setResult(Activity.RESULT_OK, resultIntent(gameId, ids, ranks))
        Transitions.finish(activity)
    }
}
