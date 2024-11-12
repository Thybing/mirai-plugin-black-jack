package org.example.mirai.plugin.blackjack

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content


internal class BlackJackGame(group: Group) {
    val gamePlayer : MutableList<Member> = mutableListOf()
    val curRound : BlackJackRound? = null
    var ready : Boolean = false
    private val filteredEventChannel = Channel<GroupMessageEvent>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            processEvent(filteredEventChannel)
        }
    }

    private suspend fun processEvent(channel: Channel<GroupMessageEvent>) {
        for (event in channel) {
            eventHandler(event)
        }
    }

    private suspend fun eventHandler(event: GroupMessageEvent) {
        event.group.sendMessage("收到了信息" + event.message.contentToString())
    }

    fun receiveFilteredEvent(event: GroupMessageEvent) {
        scope.launch {
            filteredEventChannel.send(event)
        }
    }
}