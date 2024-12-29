package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.event.events.GroupMessageEvent

internal class BlackJackRound {
    private val dealer : Dealer = Dealer(4)
    private lateinit var banker : Banker
    private val punters : MutableMap<ULong,Punter> = mutableMapOf()

    private var roundState : Int = 0

    suspend fun gameProcess(event: GroupMessageEvent) {
        val active = event.message.contentToString()
        val uniqueCode = event.sender.id.toULong()

        //下注
        if(roundState == 0) {
            //庄家会通过前置的过滤来到下注
            if(uniqueCode == banker.uniqueCode) {
                return
            }
            if (active.startsWith('$')) {
                try {
                    val chip = active.substringAfter('$').toInt()
                    if(chip <= 0) {
                        event.group.sendMessage("下注金额不合法")
                        return
                    }
                    event.group.sendMessage(bet(uniqueCode, chip))
                } catch (_: NumberFormatException) {
                    event.group.sendMessage("下注格式错误")
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
            val op = strToOperate(active) ?: return
            event.group.sendMessage(punterOperate(uniqueCode, op))
            showPunterHand(uniqueCode)

            if(isPuntersEnd()) {
                event.group.sendMessage("请庄家开始说话")
                roundState++
            } else {
                return
            }
        }

        //庄家说话
        if(roundState == 4) {
            val op = strToOperate(active) ?: return
            event.group.sendMessage(bankerOperate(op))

            if(isBankerEnd()) {
                event.group.sendMessage("游戏结束，开始结算")
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
    fun setBanker (name : String, uniqueCode : ULong, money : Int) {
        banker = Banker(name, uniqueCode, money)
    }

    /**
     * 添加闲家
     */
    fun addPunter (name : String, uniqueCode : ULong, money : Int) {
        punters[uniqueCode] = Punter(name, uniqueCode, money)
    }

    /**
     * 闲家下注
     */
    fun bet(uniqueCode: ULong, chip : Int) : String{
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return punter.bet(chip)
    }

    private fun isBetEnd() : Boolean {
        for (punter in punters.values) {
            if(punter.chip == -1) return false
        }
        return true
    }

    /**
     * 发底牌
     */
    fun initHand() {
        banker.curHand.initialCard(dealer)
        punters.values.forEach {
            it.curHand.initialCard(dealer)
        }
    }

    private suspend fun showInitHand() {
        //
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

    private fun punterOperate(uniqueCode: ULong, operate : Operate) : String {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return when(operate) {
            Operate.Hit -> when(punter.curHand.hit(dealer)) {
                HitResult.Success -> "Hit成功"
                HitResult.SuccessButBust -> {
                    "Hit后爆牌"
                }
                HitResult.HadBust -> "已经爆牌"
                HitResult.HadStand -> "已经停牌"
            }
            Operate.Double -> {
                if (punter.money < punter.chip) {
                    "Double失败，筹码不够"
                } else {
                    when(punter.curHand.double(dealer)) {
                        DoubleResult.Success -> {
                            punter.changeMoney(punter.chip * -1)
                            "加倍成功"
                        }
                        DoubleResult.SuccessButBust -> {
                            punter.changeMoney(punter.chip * -1)
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
                if (punter.money < punter.chip) {
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
    }

    private fun bankerOperate(operate : Operate) : String {
        when(operate) {
            Operate.Hit -> {
                return if(banker.curHand.isBust()) {
                    "已经爆牌"
                } else if(banker.curHand.getValue() >= 17) {
                    "超过17点,庄家不可要牌"
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
                return if(banker.curHand.getValue() < 17) {
                    "未到17点,庄家不允许停牌"
                } else {
                    when(banker.curHand.stand()) {
                        StandResult.Success -> "停牌成功"
                        StandResult.HadStand -> "已经停牌"
                        StandResult.HadBust -> "已经爆牌"
                    }
                }
            else -> return "庄家只能要牌或停牌"
        }
    }

    fun isPuntersEnd() : Boolean {
        punters.values.forEach {
            if (!it.curHand.standFlag && !it.curHand.isBust()) return false
            if (it.splitStack.isNotEmpty()) return false
        }
        return true
    }

    fun isBankerEnd() : Boolean {
        return banker.curHand.isBust() || banker.curHand.standFlag
    }

    fun getPunterHand(uniqueCode: ULong) : HandCard {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return punter.curHand
    }

    /**
     * 游戏结束结算
     */
    fun settlement() {
        punters.values.forEach { punter ->
            punter.preHand.add(punter.curHand)
            punter.preHand.forEach {
                when(compare(it)){
                    CompareRes.Banker -> banker.money += if(it.doubleFlag) punter.chip * 2 else punter.chip
                    CompareRes.Punter -> {
                        banker.money -= if (it.doubleFlag) punter.chip * 2 else punter.chip
                        punter.money += (if (it.doubleFlag) punter.chip * 2 else punter.chip) * 2
                    }
                    CompareRes.Tie -> punter.money += if(it.doubleFlag) punter.chip * 2 else punter.chip
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

     private fun showPunterHand(uniqueCode: ULong) {
         val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
         punter.name + ":" + "\n" + punter.curHand.toString()
    }
}
