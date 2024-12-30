package org.example.mirai.plugin

import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.utils.info
import org.example.mirai.plugin.blackjack.BlackJackRound
import org.example.mirai.plugin.blackjack.BlackJackManager
import org.example.mirai.plugin.blackjack.BlackJackManager.addGame
import org.example.mirai.plugin.blackjack.HandPicCreater

/**
 * 使用 kotlin 版请把
 * `src/main/resources/META-INF.services/net.mamoe.mirai.console.plugin.jvm.JvmPlugin`
 * 文件内容改成 `org.example.mirai.plugin.PluginMain` 也就是当前主类全类名
 *
 * 使用 kotlin 可以把 java 源集删除不会对项目有影响
 *
 * 在 `settings.gradle.kts` 里改构建的插件名称、依赖库和插件版本
 *
 * 在该示例下的 [JvmPluginDescription] 修改插件名称，id和版本，etc
 *
 * 可以使用 `src/test/kotlin/RunMirai.kt` 在 ide 里直接调试，
 * 不用复制到 mirai-console-loader 或其他启动器中调试
 */

private var blackJackRound: BlackJackRound = BlackJackRound()
private var status = 0

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.example.mirai-example",
        name = "插件示例",
        version = BuildConstants.VERSION
    ) {
        author("作者名称或联系方式")
        info(
            """
            这是一个测试插件, 
            在这里描述插件的功能和用法等.
        """.trimIndent()
        )
        // author 和 info 可以删除.
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }
        //配置文件目录 "${dataFolder.absolutePath}/"

        try {
            //加载图片资源文件
            HandPicCreater.toString()
        } catch (e: Exception) {
            // 捕获初始化过程中可能发生的异常
            println("An error occurred during HandPicCreater initialization: ${e.message}")
            e.printStackTrace()
            return
        }

        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent> {

            /**
             * 用于处理接收消息
            //每收到一条消息，先判断是否是开始游戏
            //如果是开始游戏，检查发送方的群聊是否已经在游戏中。然后进行创建游戏/返回已经在游戏中
            //如果是其它，检查发送所在群聊是否在游戏中，如果在游戏中，进行下一步
            //判断发送者是否是该群聊中游戏参与者
            //如果是，转发消息事件，由游戏部分处理
            """
             */
            val text = message.contentToString()
            if(text == "玩21点") {
                if(BlackJackManager.isGroupGaming(group)) {
                    group.sendMessage("21点正在游戏中")
                } else {
                    addGame(it)
                    group.sendMessage("21点开始，请玩家加入")
                }
                return@subscribeAlways
            }

            /**
             * 发送者来自正在游戏的群
             */
            if(BlackJackManager.isGroupGaming(group)) {
                when {
                    /**
                     * 正在加入游戏中，转发消息
                     */
                    BlackJackManager.isAddGame(group) -> {
                        BlackJackManager.forwardMessage(it)
                    }
                    /**
                     * 如果是加入完成，检查发送者是否在群游戏中，如果满足则转发消息
                     */
                    BlackJackManager.isMemberGaming(sender) -> {
                        BlackJackManager.forwardMessage(it)
                    }
                }
            } else {
                /**
                 * 其它情况下不进行转发，提高效率
                 */
                return@subscribeAlways
            }

        }
        myCustomPermission // 注册权限
    }

    // region console 权限系统示例
    private val myCustomPermission by lazy { // Lazy: Lazy 是必须的, console 不允许提前访问权限系统
        // 注册一条权限节点 org.example.mirai-example:my-permission
        // 并以 org.example.mirai-example:* 为父节点

        // @param: parent: 父权限
        //                 在 Console 内置权限系统中, 如果某人拥有父权限
        //                 那么意味着此人也拥有该权限 (org.example.mirai-example:my-permission)
        // @func: PermissionIdNamespace.permissionId: 根据插件 id 确定一条权限 id
        PermissionService.INSTANCE.register(permissionId("my-permission"), "一条自定义权限", parentPermission)
    }

    public fun hasCustomPermission(sender: User): Boolean {
        return when (sender) {
            is Member -> AbstractPermitteeId.ExactMember(sender.group.id, sender.id)
            else -> AbstractPermitteeId.ExactUser(sender.id)
        }.hasPermission(myCustomPermission)
    }
    // endregion
}
