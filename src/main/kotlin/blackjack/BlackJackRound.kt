package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.at

internal class BlackJackRound {
    private val dealer : Dealer = Dealer(4)
    private lateinit var banker : Banker
    private val punters : MutableList<Punter> = mutableListOf()

    private var roundState : Int = 0

    suspend fun gameProcess(event: GroupMessageEvent) {
        val active = event.message.contentToString()
        val curPunter = punters.find { it.player.member == event.sender }
        if(curPunter == null && event.sender != banker.player.member) {
            throw IllegalStateException("member into game gameProcess but isn't banker or punter")
        }
        //闲家下注
        if(roundState == 0) {
            if(curPunter == null) return
            if (active.startsWith('$')) {
                try {
                    val chip = active.substringAfter('$').toInt()
                    if(chip <= 0) {
                        event.group.sendMessage(curPunter.player.member.at() + "下注金额不合法")
                        return
                    }
                    curPunter.bet(chip)
                } catch (_: NumberFormatException) {
                    event.group.sendMessage(curPunter.player.member.at() + "下注格式错误")
                    return
                }
            }

            if (isBetEnd()) {
                roundState++
                event.group.sendMessage("下注结束，开始发牌")
            } else {
                return
            }
        }
        //发牌
        if(roundState == 1) {
            initHand()
            showInitHand()
            roundState++
        }

        //发牌后事件
        if(roundState == 2) {
            if(banker.curHand.isBlackJack()) {
                event.group.sendMessage("庄家底牌为BlackJack，进入结算")
                roundState = 5 //直接进入结算阶段
            } else {
                event.group.sendMessage("请闲家开始说话")
                roundState++
                return
            }
        }

        //闲家说话
        if(roundState == 3) {
            if(curPunter == null) return
            val op = strToOperate(active) ?: return
            punterOperate(curPunter, op)
            showPunterHand(curPunter)

            if(isPuntersEnd()) {
                event.group.sendMessage(PlainText("请庄家开始说话") + banker.player.member.at())
                roundState++
            } else {
                return
            }
        }

        //庄家说话
        if(roundState == 4) {
            if(banker.player.member != event.sender) return
            val op = strToOperate(active) ?: return
            bankerOperate(op)

            if(isBankerEnd()) {
                event.group.sendMessage("本局结束，开始结算")
                roundState++
            }
        }

        //结算
        if(roundState == 5) {
            settlement()
            TODO("show pic")
        }
    }

    fun isRoundEnd() : Boolean{
        return roundState == 5
    }

    /**
     * 设置庄家
     */
    fun setBanker (player: Player) {
        banker = Banker(player)
    }

    /**
     * 添加闲家
     */
    fun addPunter (player: Player) {
        punters.add(Punter(player))
    }

    private fun isBetEnd() : Boolean = punters.all{it.chip != -1}

    /**
     * 发底牌
     */
    fun initHand() {
        banker.curHand.initialCard(dealer)
        punters.forEach{
            it.curHand.initialCard(dealer)
        }
    }

    private suspend fun showInitHand() {
        TODO("show init hand")
    }

    private enum class Operate {Hit,Double,Split,Stand,Next,Surrender}

    private fun strToOperate(str : String) : Operate? {
        return when(str) {
            "Hit" -> Operate.Hit
            "Double" -> Operate.Double
            "Split" -> Operate.Split
            "Stand" -> Operate.Stand
            "Next" -> Operate.Next
            "Surrender" -> null
            else -> null
        }
    }

    private suspend fun punterOperate(punter: Punter, operate : Operate) = punter.player.member.group.sendMessage(
        punter.player.member.at() +
        when(operate) {
            Operate.Hit -> when(punter.curHand.hit(dealer)) {
                HitResult.Success -> "Hit成功"
                HitResult.SuccessButBust -> {
                    "Hit后爆牌"
                }
                HitResult.HadBust -> "已经爆牌"
                HitResult.HadStand -> "已经停牌"
            }
            Operate.Double -> {
                if (punter.player.money < punter.chip) {
                    "Double失败，筹码不够"
                } else {
                    when(punter.curHand.double(dealer)) {
                        DoubleResult.Success -> {
                            punter.player.changeMoney(punter.chip * -1)
                            "加倍成功"
                        }
                        DoubleResult.SuccessButBust -> {
                            punter.player.changeMoney(punter.chip * -1)
                            "加倍后爆牌"
                        }
                        DoubleResult.HadBust -> "已经爆牌"
                        DoubleResult.HadStand -> "已经停牌"
                        DoubleResult.HadSplit -> "分牌后不能加倍"
                        DoubleResult.HadHit -> "拿牌后不能加倍"
                    }
                }
            }

            Operate.Stand -> when(punter.curHand.stand()) {
                StandResult.Success -> {
                    "停牌成功"
                }
                StandResult.HadStand -> "已经停牌"
                StandResult.HadBust -> "已经爆牌"
            }
            Operate.Split -> {
                if (punter.player.money < punter.chip) {
                    "分牌失败，筹码不够"
                } else if(!punter.curHand.splitCheck()) {
                    "不满足分牌条件"
                } else {
                    punter.curHand.split().reversed().forEach{
                        punter.splitStack.add(it)
                    }
                    nextHand(punter)
                    "分牌成功"
                }
            }
            Operate.Next -> {
                if (!punter.curHand.splitFlag) {
                    "您没有分过牌"
                } else {
                    if (!(punter.curHand.isBust() || punter.curHand.standFlag)) {
                        "当前的手牌还未结束"
                    }
                    else if (nextHand(punter,true)) {
                        "切换至下一套牌"
                    } else {
                        "没有下一套牌了"
                    }
                }
            }
            Operate.Surrender -> TODO()
        }
        )

    private suspend fun bankerOperate(operate : Operate)  = banker.player.member.group.sendMessage(
        banker.player.member.at() +
        when(operate) {
            Operate.Hit -> {
                if(banker.curHand.isBust()) {
                    "已经爆牌"
                } else if(banker.curHand.getValue() >= 17) {
                    "大于等于17点,庄家不可要牌"
                } else {
                    when(banker.curHand.hit(dealer)) {
                        HitResult.Success -> "Hit成功"
                        HitResult.SuccessButBust -> "Hit后爆牌"
                        HitResult.HadBust -> "已经爆牌"
                        HitResult.HadStand -> "已经停牌"
                    }
                }
            }
            Operate.Stand ->
                if(banker.curHand.getValue() < 17) {
                    "未到17点,庄家不允许停牌"
                } else {
                    when(banker.curHand.stand()) {
                        StandResult.Success -> "停牌成功"
                        StandResult.HadStand -> "已经停牌"
                        StandResult.HadBust -> "已经爆牌"
                    }
                }
            else -> "庄家只能要牌或停牌"
        })

    fun isPuntersEnd() : Boolean = punters.all{
        (it.curHand.standFlag || it.curHand.isBust()) && it.splitStack.isEmpty()
    }

    fun isBankerEnd() : Boolean = banker.curHand.isBust() || banker.curHand.standFlag

    /**
     * 游戏结束结算
     */
    fun settlement() {
        punters.forEach { punter ->
            punter.preHand.add(punter.curHand)
            punter.preHand.forEach {
                when(compare(it)){
                    CompareRes.Banker -> banker.player.money += if(it.doubleFlag) punter.chip * 2 else punter.chip
                    CompareRes.Punter -> {
                        banker.player.money -= if (it.doubleFlag) punter.chip * 2 else punter.chip
                        punter.player.money += (if (it.doubleFlag) punter.chip * 2 else punter.chip) * 2
                    }
                    CompareRes.Tie -> punter.player.money += if(it.doubleFlag) punter.chip * 2 else punter.chip
                }
            }
        }
    }

    /**
     * 比较手牌大小返回的结果
     */
    private enum class CompareRes {Banker, Punter, Tie}

    /**
     * 用于比较手牌大小
     */
    private fun compare(handCard: HandCard) : CompareRes = when{
        //有手牌炸掉的情况：爆牌算对方赢，双方爆牌算庄赢，所以先结算闲家的爆牌
        handCard.isBust() -> CompareRes.Banker
        banker.curHand.isBust() -> CompareRes.Punter
        //比较是否出现黑杰克
        banker.curHand.isBlackJack() && handCard.isBlackJack() -> CompareRes.Tie
        banker.curHand.isBlackJack() -> CompareRes.Banker
        handCard.isBlackJack() -> CompareRes.Punter
        //双方都没有黑杰克时比大小
        banker.curHand.getValue() == handCard.getValue() -> CompareRes.Tie
        banker.curHand.getValue() > handCard.getValue() -> CompareRes.Banker
        banker.curHand.getValue() < handCard.getValue() -> CompareRes.Punter
        else -> throw IllegalStateException("compare but no result")
    }

    /**
     * 切换下一套手牌，saveHand为是否保存当前手牌(如果是分牌之后的手牌那么就不保存，如果是主动切换手牌就保存)
     */
    private fun nextHand(punter : Punter, saveHand : Boolean = false) : Boolean {
        return if(punter.splitStack.isNotEmpty()) {
            if(saveHand) punter.preHand.add(punter.curHand)
            punter.curHand = punter.splitStack.removeLast().also { it.initialCard(dealer) }
            true
        } else {
            false
        }
    }

     private suspend fun showPunterHand(punter: Punter) {
         punter.player.member.group.sendMessage(punter.player.member.at() + ":" + "\n" + punter.curHand.toString())
    }
}
