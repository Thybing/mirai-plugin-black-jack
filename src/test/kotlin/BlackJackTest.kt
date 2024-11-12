package org.example.mirai.plugin.blackjack

import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.messageChainOf
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.info

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities

private fun mergeImage(background: BufferedImage, card: BufferedImage, x: Int, y: Int): BufferedImage {
    val wOfCard = card.width
    val hOfCard = card.height

    // 创建一个数组来存储扑克牌图像的 RGB 数据
    val cardArray = IntArray(wOfCard * hOfCard)

    // 逐行扫描扑克牌图像的像素到数组中
    card.getRGB(0, 0, wOfCard, hOfCard, cardArray, 0, wOfCard)

    // 将扑克牌图像的 RGB 数据设置到背景图像的指定位置
    background.setRGB(x, y, wOfCard, hOfCard, cardArray, 0, wOfCard)

    return background
}

fun HandPicCreater() {
    val background: BufferedImage = ImageIO.read(File("./data/org.thybing.mirai.blackjack/background.png"))
    val card: BufferedImage = ImageIO.read(File("./data/org.thybing.mirai.blackjack/pokerCard/Heart_Ace.png"))
    mergeImage(background, card,20,30)
    mergeImage(background, card,35,30)
    SwingUtilities.invokeLater {
        val frame = JFrame("Card Merged Image Viewer")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1200, 600)

        // 创建 JLabel 用于显示合并后的图像
        val label = JLabel(ImageIcon(background))
        frame.add(label)

        // 设置可见
        frame.isVisible = true
    }
}

fun main(args: Array<String>) {
    HandPicCreater()
}