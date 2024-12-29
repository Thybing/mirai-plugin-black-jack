package org.example.mirai.plugin.blackjack

internal abstract class Player(val name : String, val uniqueCode : ULong,  var money : Int) {
    //玩家当前正在操作的手牌
    var curHand : HandCard = HandCard()

    fun changeMoney(delta : Int) : Boolean {
        return if (money + delta >= 0) {
            money += delta
            true
        }else {
            false
        }
    }
}


internal class Banker(name : String, uniqueCode : ULong, money: Int) : Player(name, uniqueCode, money) {

}

internal class Punter(name : String, uniqueCode : ULong, money: Int) : Player(name, uniqueCode, money) {
    val preHand : MutableList<HandCard> = mutableListOf()
    val splitStack : MutableList<HandCard> = mutableListOf()

    var chip: Int = -1

    /**
     * 闲家下注
     */
    fun bet(chip : Int) : String {
        return if(this.chip != -1) {
            "${name}已经下注过"
        }
        else if(changeMoney(chip * -1)) {
            this.chip = chip
            "${name}下注${chip}"
        } else {
            "${name}下注失败，筹码不足"
        }
    }
}
