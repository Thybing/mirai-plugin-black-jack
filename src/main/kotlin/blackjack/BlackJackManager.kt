package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.event.events.GroupMessageEvent

object BlackJackManager {
    /**
     * 所有正在进行的游戏的目录
     */
    private val gameDirectory: MutableMap<Group, BlackJackGame> = mutableMapOf()

    /**
     * 检查群组是否正在游戏中
     */
    fun isGroupGaming(group: Group): Boolean {
        return group in gameDirectory.keys
    }

    /**
     * 检查群成员是否在该群游戏中
     */
    fun isMemberGaming(member: Member): Boolean =
        gameDirectory[member.group]?.run {
            gamePlayer.any{it.member == member}
        }?:false

    /**
     * 检查群是否处于加入游戏状态
     * 如果此群不在游戏中，会抛出异常
     */
    fun isAddGame(group: Group): Boolean {
        return !(gameDirectory[group]?:throw IllegalStateException("群不在游戏中，但是调用了检查群是否正在加入游戏的函数")).ready
    }

    /**
     * 添加群游戏，传入事件，使用游戏创建工厂
     */
    fun addGame(messageEvent: GroupMessageEvent) {
        gameDirectory[messageEvent.group] = BlackJackFactory.create(messageEvent, this::removeGame)
    }

    /**
     * 移除群游戏，作为游戏结束后的回调
     */
    fun removeGame(group: Group) {
        gameDirectory.remove(group)
    }

    /**
     * 转发通过过滤后的消息
     */
    suspend fun forwardMessage(messageEvent: GroupMessageEvent) {
        gameDirectory[messageEvent.group]?.receiveFilteredEvent(messageEvent)?:throw IllegalStateException("群不在游戏中，但是消息通过了过滤进入了转发")
    }
}