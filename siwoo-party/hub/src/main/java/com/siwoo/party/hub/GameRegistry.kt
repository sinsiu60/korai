package com.siwoo.party.hub

import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.pirate.PirateModule
import com.siwoo.party.games.reaction.ReactionModule

/**
 * 모음집에 들어 있는 게임 목록 (구현된 게임만 등록).
 * 새 게임 추가: games/<게임>/logic·ui 모듈을 만들고 GameModule 을 구현한 뒤 여기에 한 줄.
 */
object GameRegistry {
    val games: List<GameModule> = listOf(
        MarbleModule,
        ReactionModule,
        PirateModule,
    )

    fun byId(id: String?): GameModule? = games.firstOrNull { it.id == id }
}
