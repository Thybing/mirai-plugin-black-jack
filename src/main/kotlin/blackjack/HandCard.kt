package org.example.mirai.plugin.blackjack


internal enum class HitResult {
    Success,SuccessButBust,HadBust,HadStand,
}

internal enum class DoubleResult {
    Success,SuccessButBust,HadBust,HadStand,HadHit,HadSplit,
}

internal enum class StandResult {
    Success,HadStand,HadBust,
}

//internal enum class

internal class HandCard() {

    //仅在分牌时调用的初始化HandCard，会自动添加分开的牌，并且将已分牌的标志位置位
    constructor(pokerCard: PokerCard) :this() {
        handCard.add(pokerCard)
        splitFlag = true
    }

    override fun toString(): String {
        var string = ""
        handCard.forEach{
            string = "$string $it \n"
        }
        return string
    }

    /**
     * 发初始底牌
     */
    fun initialCard(dealer: Dealer) {
        check(handCard.size <= 2)

        while(handCard.size < 2) {
            add(dealer.dealCard())
        }
        //如果是对A分牌之后的手牌，不能成为黑杰克，并且立刻停牌
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
     * 要一张牌。如果要牌后未爆牌，返回hit后的结果
     */
    fun hit(dealer: Dealer) : HitResult = when{
        bustFlag -> HitResult.HadBust
        standFlag -> HitResult.HadStand
        else -> {
            add(dealer.dealCard())
            if(isBust()) {
                bustFlag = true
                HitResult.SuccessButBust
            }else{
                HitResult.Success
            }
        }
    }

    /**
     * 加倍
     */
    fun double(dealer: Dealer) : DoubleResult = when{
        bustFlag -> DoubleResult.HadBust
        standFlag -> DoubleResult.HadStand
        handCard.size > 2 -> DoubleResult.HadHit
        splitFlag -> DoubleResult.HadSplit
        else -> when(hit(dealer)) {
            HitResult.Success -> DoubleResult.Success.also { stand() }
            HitResult.SuccessButBust -> DoubleResult.SuccessButBust.also { stand() }
            else -> throw IllegalStateException("double had some error!")
        }.also { doubleFlag = true }
    }

    /**
     * 停牌
     */
    fun stand() : StandResult = when {
        bustFlag -> StandResult.HadBust
        standFlag -> StandResult.HadStand
        else -> {
            standFlag = true
            StandResult.Success
        }
    }

    /**
     * 分牌，返回分牌后的两套手牌
     */
    fun split() : List<HandCard> {
        check(splitCheck())

        val firstHandCard = HandCard(handCard.removeFirst())
        firstHandCard.splitFlag = true
        val secondHandCard = HandCard(handCard.removeFirst())
        secondHandCard.splitFlag = true

        return listOf(firstHandCard,secondHandCard)
    }

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
     * 返回是否可以进行分牌
     */
    fun splitCheck() : Boolean {
        if(standFlag) return false
        if(handCard.size != 2) return false
        if(handCard[0].rank != handCard[1].rank) return false
        return true
    }

    /**
     * 检查是否爆牌
     */
    fun isBust() : Boolean {
        return handCard.sumOf { if (it.rank.num > 10) 10 else it.rank.num } > 21
    }

    /**
     * 计算最大价值
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
