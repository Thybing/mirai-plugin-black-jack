package org.thybing.mirai.plugin.blackjack

import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent

/**
 * 游戏的工厂类，用于创建游戏
 */
internal object BlackJackFactory {
    fun create(messageEvent: GroupMessageEvent, onGameOver: (Group) -> Unit) : BlackJackGame {
        return BlackJackGame(messageEvent.group, onGameOver)
    }
}
