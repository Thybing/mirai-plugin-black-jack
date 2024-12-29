package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.message.data.at

internal class Player(val member : Member, var money : Int) {
    fun changeMoney(delta : Int) : Boolean {
        if (money + delta >= 0) {
            money += delta
            return true
        }else {
            return false
        }
    }
}


internal class Banker(val player: Player) {
    //玩家当前正在操作的手牌
    var curHand : HandCard = HandCard()
}

internal class Punter(val player: Player) {
    //玩家当前正在操作的手牌
    var curHand : HandCard = HandCard()

    val preHand : MutableList<HandCard> = mutableListOf()
    val splitStack : MutableList<HandCard> = mutableListOf()

    var chip: Int = -1

    /**
     * 闲家下注
     */
    suspend fun bet(chip : Int) {
        player.member.group.sendMessage(
            if(this.chip != -1) {
                player.member.at() + "已经下注过"
            }
            else if(player.changeMoney(chip * -1)) {
                this.chip = chip
                player.member.at() + "下注金额:${chip}"
            } else {
                player.member.at() + "下注失败，筹码不足"
            }
        )
    }
}
