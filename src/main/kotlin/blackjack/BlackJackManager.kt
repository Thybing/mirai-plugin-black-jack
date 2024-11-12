package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.isOwner
import net.mamoe.mirai.event.events.GroupMessageEvent
import org.example.mirai.plugin.blackjack.BlackJackFactory

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
    fun isMemberGaming(member: Member): Boolean {
        /**
         * 所在群不在游戏中
         */
        if (member.group !in gameDirectory.keys) {
            return false
        }
        /**
         * 群游戏名单中没有此成员
         */
        if (member !in (gameDirectory[member.group]?.gamePlayer ?: return false)) {
            return false
        }
        return true
    }

    /**
     * 检查群是否处于加入游戏状态
     * 如果此群不在游戏中，会抛出异常
     */
    fun isAddGame(group: Group): Boolean {
        return !(gameDirectory[group]?:throw IllegalStateException("群不在游戏中，但是调用了检查群是否正在加入游戏的函数")).ready
    }

    /**
     * 添加群游戏，传入事件并且使用工厂是为了可能的扩展
     */
    fun addGame(messageEvent: GroupMessageEvent) {
        gameDirectory[messageEvent.group] = BlackJackFactory.create(messageEvent)
    }

    /**
     * 转发通过过滤后的消息
     */
    fun forwardMessage(messageEvent: GroupMessageEvent) {
        gameDirectory[messageEvent.group]?.receiveFilteredEvent(messageEvent)?:throw IllegalStateException("群不在游戏中，但是消息通过了过滤进入了转发")
    }
}