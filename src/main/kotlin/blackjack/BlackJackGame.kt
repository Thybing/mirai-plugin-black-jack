package org.example.mirai.plugin.blackjack

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.event.events.GroupMessageEvent


internal class BlackJackGame(private val group: Group,private val onGameOver: (Group) -> Unit) {
    val gamePlayer : MutableList<Member> = mutableListOf()
    var curBankerIndex : Int = -1
    var curRound : BlackJackRound? = null
    var ready : Boolean = false
    private val filteredGroupMessage = Channel<GroupMessageEvent>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            processEvent(filteredGroupMessage)
        }
    }

    private suspend fun processEvent(channel: Channel<GroupMessageEvent>) {
        for (event in channel) {
            eventHandler(event)
        }
    }

    private suspend fun eventHandler(event: GroupMessageEvent) {
        if(!ready) {
            addPlayer(event)
            return
        }

        curRound?.gameProcess(event)?: throw Exception("ready for game but round was null")
        if (curRound?.isRoundEnd()?:throw Exception("ready for game but round was null")) {
            if(nextRound()) {
                TODO("start round")
            } else {
                onGameOver(group)
            }
        }
    }

    fun receiveFilteredEvent(event: GroupMessageEvent) {
        scope.launch {
            try {
                filteredGroupMessage.send(event)
            }catch(e: Exception) {

            }finally {

            }
        }
    }

    private suspend fun addPlayer(event: GroupMessageEvent) {
        when(event.message.contentToString()) {
            "加入" -> {
                if(event.sender in gamePlayer)
                    return
                gamePlayer.add(event.sender)
            }
            "停止加入" -> {
                if(gamePlayer.count() < 2) {
                    event.group.sendMessage("游戏人数不满两人")
                    return
                }
                event.group.sendMessage("停止加入游戏")
                ready = true
                nextRound()
                TODO("start round")
            }
        }
    }

    private fun nextRound() : Boolean{
        curBankerIndex++
        if(curBankerIndex >= gamePlayer.count()) {
            return false
        }
        curRound = BlackJackRound()
        curRound?.setBanker(gamePlayer[curBankerIndex].nameCard,gamePlayer[curBankerIndex].id.toULong(),100)
        for(each in gamePlayer.subList(curBankerIndex + 1,gamePlayer.count())) {
            curRound?.addPunter(each.nameCard,each.id.toULong(),100)
        }
        for(each in gamePlayer.subList(0, curBankerIndex)) {
            curRound?.addPunter(each.nameCard,each.id.toULong(),100)
        }
        return true
    }
}
