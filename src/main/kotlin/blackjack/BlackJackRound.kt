package org.example.mirai.plugin.blackjack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource


internal class BlackJackRound {
    private val dealer : Dealer = Dealer(4)
    private lateinit var banker : Banker
    private val punters : MutableList<Punter> = mutableListOf()

    private var roundState : Int = 0

    suspend fun roundProcess(event: GroupMessageEvent) {
        val active = event.message.contentToString()
        val curPunter = punters.find { it.player.member == event.sender }
        if(curPunter == null && event.sender != banker.player.member) {
            //如果不是庄家也不是闲家，那么说明该玩家已经破产
            return
        }
        //闲家下注
        if(roundState == 0) {
            if(curPunter == null) return
            if (active.startsWith('$')) {
                try {
                    val chip = active.substringAfter('$').toInt()
                    if(chip < 0) {
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


            if(isPuntersEnd()) {
                event.group.sendMessage(PlainText("请庄家开始说话") + banker.player.member.at())
                showBankerHand(banker,false)
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
                event.group.sendMessage("开始结算本轮:")
                roundState++
            }
        }

        //结算
        if(roundState == 5) {
            settlement()
            showSettlementPic()
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
                HitResult.Success -> {
                    showPunterHand(punter)
                    "Hit成功"
                }
                HitResult.SuccessButBust -> {
                    showPunterHand(punter)
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
                            showPunterHand(punter)
                            "加倍成功"
                        }
                        DoubleResult.SuccessButBust -> {
                            punter.player.changeMoney(punter.chip * -1)
                            showPunterHand(punter)
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
                    showPunterHand(punter)
                    "分牌成功"
                }
            }
            Operate.Next -> {
                if (!punter.curHand.splitFlag) {
                    "您没有分过牌"
                } else {
                    if (!(punter.curHand.bustFlag || punter.curHand.standFlag)) {
                        "当前的手牌还未结束"
                    }
                    else if (nextHand(punter,true)) {
                        showPunterHand(punter)
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
                if(banker.curHand.bustFlag) {
                    "已经爆牌"
                } else if(banker.curHand.getValue() >= 17) {
                    "大于等于17点,庄家不可要牌"
                } else {
                    when(banker.curHand.hit(dealer)) {
                        HitResult.Success -> {
                            showBankerHand(banker,false)
                            "Hit成功"
                        }
                        HitResult.SuccessButBust -> {
                            showBankerHand(banker,false)
                            "Hit后爆牌"
                        }
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

    private fun isPuntersEnd() : Boolean = punters.all{
        (it.curHand.standFlag || it.curHand.bustFlag) && it.splitStack.isEmpty()
    }

    private fun isBankerEnd() : Boolean = banker.curHand.bustFlag || banker.curHand.standFlag

    /**
     * 游戏结束结算
     */
    private suspend fun settlement() {
        //统计庄家和闲家的输赢
        banker.gains = 0
        punters.forEach { punter ->
            punter.gains = 0

            // 将curHand添加到pre中，方便统一处理
            punter.preHand.add(punter.curHand)

            punter.preHand.forEach {
                when(compare(it)){
                    CompareRes.Banker -> {
                        banker.gains += if(it.doubleFlag) punter.chip * 2 else punter.chip
                    }
                    CompareRes.Punter -> {
                        banker.gains -= if (it.doubleFlag) punter.chip * 2 else punter.chip
                        punter.gains += (if (it.doubleFlag) punter.chip * 2 else punter.chip) * 2
                    }
                    CompareRes.Tie -> {
                        punter.gains += if(it.doubleFlag) punter.chip * 2 else punter.chip
                    }
                }
            }
            // 将添加的curHand移除
            punter.preHand.removeLast()
        }

        //如果庄家可以成功结算
        if(banker.player.changeMoney(banker.gains)) {
            punters.forEach {
                //由于闲家的每一次操作都会进行资产检查，所以这里一定不会出现资产不足的情况，否则就是程序逻辑错误
                if(!it.player.changeMoney(it.gains)) throw IllegalStateException("punter money change error")
            }
        } else {
            //庄家破产的情况，先将庄家的钱取出
            var leftMoney : Int = banker.player.money
            banker.player.money = 0

            // 如果闲家输钱了，那么就对庄家进行赔付
            var debt : Int = 0 // 庄家需要赔付的金额
            punters.forEach {
                if (it.gains < 0) {
                    if(!it.player.changeMoney(it.gains)) throw IllegalStateException("punter money change error")
                    leftMoney += (-it.gains) // 闲家赔付的钱直接加到庄家的剩余钱中
                }
                else {
                    debt += it.gains // 闲家赢的钱加到庄家需要赔付的金额中
                }
            }

            // 结算庄家的赔付，由于庄家不够赔付，所以按照比例进行赔付
            punters.forEach {
                if (it.gains > 0) {
                    it.player.changeMoney((it.gains * leftMoney / debt))
                }
            }
            banker.player.member.group.sendMessage("由于庄家的破产保护，所以对于赢的钱只能按照比例进行赔付")
        }

        //检查是否有人破产
        if(banker.player.money == 0) {
            banker.player.member.group.sendMessage(banker.player.member.at() + "资产归零")
            banker.player.isBankruptcy = true
        }
        punters.forEach {
            if(it.player.money == 0) {
                it.player.isBankruptcy = true
                it.player.member.group.sendMessage(it.player.member.at() + "资产归零")
            }
        }
    }

    /**
     * 比较手牌大小返回的结果
     */
    private enum class CompareRes {Banker, Punter, Tie}

    /**
     * 用于和庄家比较手牌大小
     */
    private fun compare(handCard: HandCard) : CompareRes = when{
        //手牌炸掉的情况：爆牌算对方赢，双方爆牌算庄赢，所以先结算闲家的爆牌
        handCard.bustFlag -> CompareRes.Banker
        banker.curHand.bustFlag -> CompareRes.Punter
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

    private suspend fun showBankerHand(banker: Banker,shadowFirst : Boolean) {
        val imgFile = bufferedImageToFile(HandPicCreator.createBankerPic(banker,shadowFirst))
        val fileResource = imgFile.toExternalResource()
        banker.player.member.group.sendMessage(
            banker.player.member.group.uploadImage(fileResource)
        )
        withContext(Dispatchers.IO) {
            fileResource.close()
        }
        imgFile.delete()
    }

    // 展示闲家手牌
    private suspend fun showPunterHand(punter: Punter) {
        val imgFile = bufferedImageToFile(HandPicCreator.createPunterPic(punter))
        val fileResource = imgFile.toExternalResource()
        punter.player.member.group.sendMessage(
            punter.player.member.group.uploadImage(fileResource)
        )
        withContext(Dispatchers.IO) {
            fileResource.close()
        }
        imgFile.delete()
    }

    private suspend fun showInitHand() {
        val bankerHandPicFile = bufferedImageToFile(HandPicCreator.createBankerPic(banker,false))
        val fileResource = bankerHandPicFile.toExternalResource()
        banker.player.member.sendMessage(PlainText("您的底牌为") +
            banker.player.member.uploadImage(fileResource)
        )
        withContext(Dispatchers.IO) {
            fileResource.close()
        }
        bankerHandPicFile.delete()

        showBankerHand(banker,true)
        punters.forEach {
            showPunterHand(it)
        }
    }

    private suspend fun showSettlementPic() {
        showBankerHand(banker,false)
        punters.forEach {
            showPunterHand(it)
        }
    }
}
