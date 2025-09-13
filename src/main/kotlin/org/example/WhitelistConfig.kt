// WhitelistConfig.kt
package org.example.mmorpg

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.ValueName
import net.mamoe.mirai.console.data.value
import org.example.mmorpg.PluginMain.save

object WhitelistConfig : AutoSavePluginConfig("whitelist") {
    @ValueName("enabled_groups")
    @ValueDescription("启用的群列表")
    val enabledGroups: MutableSet<Long> by value(mutableSetOf())

    init {
        // 添加一些日志来调试配置加载
        PluginMain.logger.info("WhitelistConfig 初始化完成")
    }

    fun addGroup(groupId: Long) {
        enabledGroups.add(groupId)
        save()
        PluginMain.logger.info("已添加群 $groupId 到白名单")
    }

    fun removeGroup(groupId: Long) {
        enabledGroups.remove(groupId)
        save()
        PluginMain.logger.info("已从白名单移除群 $groupId")
    }

    fun isGroupEnabled(groupId: Long): Boolean {
        val enabled = enabledGroups.contains(groupId)
        PluginMain.logger.info("检查群 $groupId 是否启用: $enabled")
        return enabled
    }
}