package org.example.mirai.plugin.blackjack

internal class HandCard() {
    constructor(pokerCard: PokerCard) :this() {
        splitFlag = true
    }

    /**
     * 发初始底牌
     */
    fun initialCard(dealer: Dealer) {
        while(handCard.size < 2) {
            add(dealer.dealCard())
        }
        //如果是A分牌，不能成为黑杰克，并且立刻停牌
        if (splitFlag && handCard.first().rank == PokerCard.Rank.Ace) {
            blackJackFlag = false
            stand()
        } else {
            //其它情况下黑杰克判断，并且如果是黑杰克，立刻停牌
            blackJackFlag = (handCard.first().rank.num >= 10 && handCard.last().rank == PokerCard.Rank.Ace)
                || (handCard.first().rank == PokerCard.Rank.Ace && handCard.last().rank.num >= 10)
            if(blackJackFlag) stand()
        }
    }

    /**
     * 要一张牌。如果要牌后未爆牌，返回 true；如果爆牌，返回 false。
     */
    fun hit(dealer: Dealer) : Boolean {
        check(!bustFlag)
        add(dealer.dealCard())
        bustFlag = isBust()
        return !bustFlag
    }

    /**
     * 分牌，返回分牌后的两套手牌
     */
    fun split(dealer: Dealer) : List<HandCard> {
        check(splitCheck())

        val firstHandCard = HandCard(handCard.removeFirst())
        firstHandCard.splitFlag = true
        val secondHandCard = HandCard(handCard.removeFirst())
        secondHandCard.splitFlag = true

        return listOf<HandCard>(firstHandCard,secondHandCard)
    }

    /**
     * 加倍
     */
    fun double(dealer: Dealer) : Boolean {
        doubleFlag = true
        return hit(dealer)
    }

    /**
     * 停牌
     */
    fun stand() { standFlag = true }


    /**
     * 向手牌内加一张牌
     */
    private fun add(pokerCard: PokerCard) = handCard.add(pokerCard)

    /**
     * 检查是否构成黑杰克
     */
    fun isBlackJack() : Boolean {
        return blackJackFlag
    }

    /**
     * 可以进行分牌
     */
    fun splitCheck() : Boolean {
        if(handCard.size != 2) return false
        if(handCard[0] != handCard[1]) return false
        return true
    }

    /**
     * 检查是否爆牌
     */
    fun isBust() : Boolean {
        return handCard.sumOf { if (it.rank.num > 10) 10 else it.rank.num } > 21
    }

    /**
     * 获得最大价值
     */
    fun getValue() : Int{
        val cardValue = handCard.map { if (it.rank.num > 10) 10 else it.rank.num }
        val aceNum = handCard.count { it.rank == PokerCard.Rank.Ace }
        var res = cardValue.sum()
        repeat(aceNum) {
            if(res <= 11) {
                res += 10
            }
        }
        return res
    }

    var standFlag = false
    var bustFlag = false
    var doubleFlag = false
    var splitFlag = false
    var blackJackFlag = false

    /**
     * 手牌
     */
    private val handCard = mutableListOf<PokerCard>()
}
