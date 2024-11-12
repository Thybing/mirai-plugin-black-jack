package org.example.mirai.plugin.blackjack

internal class BlackJackRound {
    private val dealer : Dealer = Dealer(4)
    lateinit var banker : Banker
    val punters : MutableMap<ULong,Punter> = mutableMapOf()

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
    fun bet(uniqueCode: ULong, chip : Int) : Boolean {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return if(punter.changeMoney(chip * -1)) {
            punter.chip = chip
            true
        } else {
            false
        }
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

    /**
     * 检查庄家是否构成黑杰克
     */
    fun checkBankerBlackJack() : Boolean = banker.curHand.isBlackJack()

    enum class  Operate {Hit,Double,Split,Stand,Next,Surrender}

    fun punterOperate(uniqueCode: ULong, operate : Operate) : String {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return when(operate) {
            Operate.Hit -> when(punter.curHand.hit(dealer)) {
                HitResult.Success -> "Hit成功"
                HitResult.SuccessButBust -> {
                    //nextHand(punter,true)
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
                    //nextHand(punter, true)
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

    fun bankerOperate(operate : Operate) : String {
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

    fun puntersEnd() : Boolean {
        punters.values.forEach {
            if (!it.curHand.standFlag && !it.curHand.isBust()) return false
            if (it.splitStack.isNotEmpty()) return false
        }
        return true
    }

    fun bankerEnd() : Boolean {
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
     * 切换下一套手牌
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
}