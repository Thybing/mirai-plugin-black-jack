package org.thybing.mirai.plugin.blackjack

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*


internal class BlackJackGame(private val group: Group,private val onGameOver: (Group) -> Unit) {
    val gamePlayer : MutableList<Player> = mutableListOf()
    private var curBankerIndex : Int = -1
    private var curRound : BlackJackRound? = null

    var ready : Boolean = false
        private set

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
            channel.close()
            if(e is CancellationException) {
//            TODO("资金结算")
                throw e
            }
            group.sendMessage("游戏异常结束，退回资金")
            println(e.message)
        }finally {
            onGameOver(group)
        }
    }

    //处理游戏消息
    private suspend fun eventHandler(event: GroupMessageEvent) {
        //如果游戏未开始，处理加入游戏消息
        if(!ready) {
            addPlayer(event)
            return
        }

        curRound?.roundProcess(event)?: throw Exception("ready for game but round was null")
        //如果游戏结束，结算并开始下一轮
        if (curRound?.isRoundEnd()?:throw Exception("ready for game but round was null")) {
            //结算消息
            var chipShowStr = "筹码结算："
            for (player in gamePlayer) {
                chipShowStr += ("\n" + player.member.nameCardOrNick + ": $" + player.money)
            }
            group.sendMessage(chipShowStr)
            delay(1000)

            //如果还有下一轮，开始下一轮
            if(nextRound()) {
                startRound(event)
            } else {
                group.sendMessage("游戏结束!!!")
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

    //开始下一轮游戏
    private fun nextRound() : Boolean{
        curBankerIndex++
        //如果庄家轮完一圈，游戏结束
        if(curBankerIndex >= gamePlayer.count()) {
            return false
        }

        //如果庄家破产，跳过
        if(gamePlayer[curBankerIndex].isBankruptcy) {
            return nextRound()
        }

        curRound = BlackJackRound()
        curRound?.setBanker(gamePlayer[curBankerIndex])?:throw IllegalStateException("BlackJackRound create err")

        //如果全部的闲家都破产，游戏结束
        var ret = false
        for(each in gamePlayer) {
            if(each != gamePlayer[curBankerIndex]) {
                if(each.isBankruptcy) {
                    continue
                }
                curRound?.addPunter(each)?:throw IllegalStateException("BlackJackRound create err")
                ret = true
            }
        }
        return ret
    }

    private suspend fun startRound(event: GroupMessageEvent) {
        event.group.sendMessage(PlainText("Round:${curBankerIndex + 1}/${gamePlayer.count()}\n" +
        "庄家为:") + gamePlayer[curBankerIndex].member.at() +
        "\n请闲家开始下注")
    }
}
