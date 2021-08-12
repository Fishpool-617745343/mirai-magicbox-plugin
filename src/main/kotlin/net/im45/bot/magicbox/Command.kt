package net.im45.bot.magicbox

import io.ktor.util.*
import net.im45.bot.magicbox.MBX.mbx
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.flash
import net.mamoe.mirai.message.data.sendTo
import net.mamoe.mirai.utils.ExternalResource.Companion.sendAsImageTo
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.isDirectory

private const val errMsg = "Error: No such directory"
internal val errLog
    get() = "$errMsg: ${Config.imageDir}"

private fun checkDirectory(path: String = Config.imageDir): Boolean =
    runCatching { Paths.get(path).isDirectory() }.getOrElse { false }

private suspend fun CommandSender.error() {
    MagicBox.logger.error(errLog)
    sendMessage(errMsg)
}

object MBX : SimpleCommand(
    MagicBox, "mbx"
) {
    private val IMAGE_EXT = arrayOf("jpg", "jpeg", "png", "gif")
    private val magic: MutableList<File> = mutableListOf()

    internal fun reload(
        fromPath: String = Config.imageDir,
        recurseSubDirectories: Boolean = Config.recurseSubDirectories
    ): Boolean {
        Config.recurseSubDirectories = recurseSubDirectories
        if (!checkDirectory(fromPath)) return false
        val path = Paths.get(fromPath)

        magic.clear()
        magic += (if (recurseSubDirectories) Files.walk(path) else Files.list(path))
            .filter { it.extension.lowercase(Locale.getDefault()) in IMAGE_EXT }
            .map(Path::toFile)
            .collect(Collectors.toList())

        return true
    }

    @Handler
    suspend fun UserCommandSender.mbx() {
        if (!Config.enable) return
        if (!checkDirectory()) {
            error()
            return
        }
        if (magic.isEmpty()) {
            sendMessage("Directory ${Config.imageDir} does not contain image.")
            return
        }

        MagicBox.logger.debug("Sending an image to $subject")

        magic.random().toExternalResource().use {
            if (subject is Group)
                when (Config.groupTrusts.getValue(subject.id)) {
                    Trust.NOT -> return
                    Trust.UNKNOWN -> it.sendAsImageTo(user)
                    Trust.MARGINAL -> it.sendAsImageTo(subject).recallIn(Config.marginallyRecallIn)
                    Trust.FULL -> it.uploadAsImage(subject).flash().sendTo(subject)
                    Trust.ULTIMATE -> it.sendAsImageTo(subject)
                }
            else it.sendAsImageTo(subject)

            Data.served++
        }
    }

    @Handler
    suspend fun ConsoleCommandSender.mbx() {
        sendMessage("There are ${magic.size} images currently.")
        sendMessage("Image directory: ${Config.imageDir} (Recurse subdirectories: ${Config.recurseSubDirectories})")
    }
}

object Control : CompositeCommand(
    MagicBox, "magicbox"
) {
    @SubCommand
    suspend fun ConsoleCommandSender.dir(path: String, recurseSubDirectories: Boolean = false) {
        if (checkDirectory(path)) {
            Config.imageDir = path
            MBX.reload(recurseSubDirectories = recurseSubDirectories)
        } else error()
    }

    @SubCommand
    suspend fun CommandSender.reload() {
        if (MBX.reload()) {
            sendMessage("MagicBox 已重载")
            if (this is ConsoleCommandSender)
                mbx()
        } else error()
    }

    @SubCommand
    suspend fun CommandSender.defaultTrust(trust: Trust) {
        Config.defaultTrust = trust
        sendMessage("设置默认信任等级为$trust")
    }

    @SubCommand
    suspend fun UserCommandSender.trust(trust: Trust) {
        (subject as? Group)?.let { trust(it, trust) }
    }

    @SubCommand
    suspend fun CommandSender.trust(group: Group, trust: Trust) {
        Config.groupTrusts[group.id] = trust
        sendMessage("${trust}信任 ${group.name}")
    }

    @SubCommand
    suspend fun UserCommandSender.distrust() {
        (subject as? Group)?.let { distrust(it) }
    }

    @SubCommand
    suspend fun CommandSender.distrust(group: Group) {
        Config.groupTrusts.remove(group.id)
        sendMessage("移除 ${group.name} 的信任等级")
    }

    @SubCommand
    suspend fun CommandSender.trusts() {
        sendMessage(buildString {
            append("默认信任等级：${Config.defaultTrust}\n")
            Config.groupTrusts
                .map { "${it.key}\t${it.value}" }
                .run {
                    append(if (isEmpty()) "无信任设置" else joinToString("\n"))
                }
        })
    }

    @SubCommand
    suspend fun CommandSender.enable() {
        if (checkDirectory()) {
            Config.enable = true
            sendMessage("MagicBox 已启用")
        } else error()
    }

    @SubCommand
    suspend fun CommandSender.disable() {
        Config.enable = false
        sendMessage("MagicBox 已禁用")
    }

    @SubCommand
    suspend fun CommandSender.stat() {
        sendMessage("MagicBox ${if (Config.enable) "启" else "禁"}用，目前已发送图片 ${Data.served} 张")
    }
}
