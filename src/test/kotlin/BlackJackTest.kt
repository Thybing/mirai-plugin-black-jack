package org.example.mirai.plugin.blackjack

import java.awt.Dimension

import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

fun showImage(image: BufferedImage) {
    val frame = JFrame("Image Viewer")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.size = Dimension(image.width,image.height)

    val icon = ImageIcon(image)
    val label = JLabel(icon)
    frame.contentPane.add(label)

    frame.pack()
    frame.isVisible = true
}

fun main() {
    val d = Dealer(4)
    val h = HandCard()
    h.initialCard(d)
    repeat(5){
    h.add(d.dealCard())
    }
//    showImage(HandPicCreater.drawHandPic(h,true))
}
