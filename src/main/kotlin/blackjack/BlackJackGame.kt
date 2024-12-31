package org.example.mirai.plugin.blackjack

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*


internal class BlackJackGame(private val group: Group,private val onGameOver: (Group) -> Unit) {
    val gamePlayer : MutableList<Player> = mutableListOf()
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
        try {
            for (event in channel) {
                eventHandler(event)
            }
        }catch(e: Exception) {
            TODO("异常处理")
        }finally {
            onGameOver(group)
//            TODO("资源回收")
        }
    }

    private suspend fun eventHandler(event: GroupMessageEvent) {
        if(!ready) {
            addPlayer(event)
            return
        }
        curRound?.gameProcess(event)?: throw Exception("ready for game but round was null")
        if (curRound?.isRoundEnd()?:throw Exception("ready for game but round was null")) {
            var tmp = "结算："
            for (player in gamePlayer) {
                tmp += ("\n" + player.member.nameCardOrNick + ": $" + player.money)
            }
            group.sendMessage(tmp)
            if(nextRound()) {
                startRound(event)
            } else {
                scope.cancel()
            }
        }
    }

    suspend fun receiveFilteredEvent(event: GroupMessageEvent) {
        filteredGroupMessage.send(event)
    }

    private suspend fun addPlayer(event: GroupMessageEvent) {
        when(event.message.contentToString()) {
            "加入21点" -> {
                if(gamePlayer.any{it.member == event.sender}){
                    event.group.sendMessage(event.sender.at() + "您已加入游戏")
                    return
                }
                gamePlayer.add(Player(event.sender, 1000))
                event.group.sendMessage(event.sender.at() + "加入成功")
            }
            "停止加入" -> {
                if(gamePlayer.count() < 2) {
                    event.group.sendMessage("游戏人数不满两人")
                    return
                }
                event.group.sendMessage("已停止加入游戏")
                ready = true
                nextRound()
                startRound(event)
            }
        }
    }

    private fun nextRound() : Boolean{
        curBankerIndex++
        if(curBankerIndex >= gamePlayer.count()) {
            return false
        }
        curRound = BlackJackRound()
        curRound?.setBanker(gamePlayer[curBankerIndex])?:throw IllegalStateException("BlackJackRound create err")
        for(each in gamePlayer) {
            if(each != gamePlayer[curBankerIndex]) {
                curRound?.addPunter(each)
            }
        }
        return true
    }

    private suspend fun startRound(event: GroupMessageEvent) {
        event.group.sendMessage(PlainText("Round:${curBankerIndex + 1}/${gamePlayer.count()}\n" +
        "庄家为:") + gamePlayer[curBankerIndex].member.at() +
        "\n请闲家开始下注")
    }
}
