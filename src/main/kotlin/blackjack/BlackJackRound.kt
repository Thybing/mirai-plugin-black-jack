package org.example.mirai.plugin.blackjack

internal class BlackJackRound {
    private val dealer : Dealer = Dealer(4)
    private lateinit var banker : Banker
    private val punters : MutableMap<ULong,Punter> = mutableMapOf()

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
    fun bet(uniqueCode: ULong, chip : Int) {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        punter.chip = chip
    }

    /**
     * 发底牌
     */
    fun initHand() {
        punters.values.forEach {
            it.curHand.initialCard(dealer)
        }
        banker.curHand.initialCard(dealer)
    }

    /**
     * 检查庄家是否构成黑杰克
     */
    fun checkBankerBlackJack() : Boolean = banker.curHand.isBlackJack()

    /**
     * 向上提供扣费接口，用于下注
     */
    fun cost(uniqueCode : ULong, chip: Int) : Boolean {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return if(punter.money >= chip) {
            punter.money -= chip
            true
        } else {
            false
        }
    }

    /**
     * 向上提供扣费接口，用于加倍，分牌等
     */
    fun cost(uniqueCode : ULong) : Boolean {
        val punter = punters[uniqueCode] ?: throw NoSuchElementException("No punter found with code: $uniqueCode")
        return if(punter.money >= punter.chip) {
            punter.money -= punter.chip
            true
        } else {
            false
        }
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
}