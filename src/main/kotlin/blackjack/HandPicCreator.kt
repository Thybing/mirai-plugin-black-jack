package org.thybing.mirai.plugin.blackjack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

internal object HandPicCreator {
    private val backGroundPic: BufferedImage = loadImage("/pic/background.png")
    private val pokerCardPic: MutableMap<PokerCard, BufferedImage> = mutableMapOf()
    private val cardBackPic = loadImage("/pic/pokerCard/Back.png")
    private val defaultAvatar = loadImage("/pic/pokerCard/Back.png")

    init {
        //加载图片
        for (suit in PokerCard.Suit.values()) {
            for (rank in PokerCard.Rank.values()) {
                val path = "/pic/pokerCard/${suit.name}_${rank.name}.png"
                loadImage(path)?.let { image ->
                    pokerCardPic[PokerCard(suit, rank)] = image
                }
            }
        }
    }

    fun urlToBufferedImage(url: String): BufferedImage {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000 // 设置超时时间
            connection.readTimeout = 5000
            connection.getInputStream().use { inputStream ->
                ImageIO.read(inputStream) // 使用 ImageIO 解析输入流为 BufferedImage
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defaultAvatar
        }
    }

    private fun loadImage(resourcePath: String) = ImageIO.read(javaClass.getResourceAsStream(resourcePath))


    fun createBankerPic(banker : Banker, shadowFirst: Boolean) : BufferedImage{
        val copiedBackground = BufferedImage(
            backGroundPic.width * 2 / 5,
            backGroundPic.height,
            backGroundPic.type
        )
        val graph = copiedBackground.createGraphics()

        drawHandPic(banker.curHand,graph,shadowFirst)
        graph.drawImage(banker.player.avatar,30,30,null)

        graph.translate(0,backGroundPic.height)

        graph.dispose() // 释放资源
        return copiedBackground
    }

    fun createPunterPic(punter: Punter) : BufferedImage{
        val copiedBackground = BufferedImage(
            backGroundPic.width * 2 / 5,
            backGroundPic.height * (punter.preHand.count() + 1) ,
            backGroundPic.type
        )
        val graph = copiedBackground.createGraphics()

        drawHandPic(punter.curHand,graph)
        graph.drawImage(punter.player.avatar,30,30,null)

        graph.translate(0,backGroundPic.height)

        for (handCard in punter.preHand.reversed()) {
            drawHandPic(handCard,graph)
        }
        graph.dispose() // 释放资源
        return copiedBackground
    }

    private fun drawHandPic(handCard: HandCard, graph : Graphics, shadowFirst : Boolean = false){
        graph.drawImage(backGroundPic, 0, 0, null)

        val x = 180
        val diffX = 15
        val y = 30
        handCard.handCard.forEachIndexed { index, it ->
            if(shadowFirst && index == 0) {
                graph.drawImage(cardBackPic, x, y, null)
                return@forEachIndexed
            }
            drawPoker(graph, it, x + index * diffX,y)
        }
    }

    private fun drawPoker(graph: Graphics, card: PokerCard, x: Int, y: Int) {

        // 在指定位置绘制扑克牌图片
        val cardPic = pokerCardPic[card]?:throw IllegalStateException("no exist card")
        graph.drawImage(cardPic, x, y, null)
    }
}

suspend fun bufferedImageToFile(image: BufferedImage) : File {
    val tempFile = withContext(Dispatchers.IO) {
        File.createTempFile("image", ".png")
    }
    withContext(Dispatchers.IO) {
        ImageIO.write(image, "png", tempFile)
    }
    return tempFile
}
