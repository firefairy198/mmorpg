// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-10-14",
            version = "4.0.0",
            changes = listOf(
                "新增功能：幸运项链 - 使用'/开启幸运项链'指令开启",
                "开启幸运项链需要消耗200基础LUCK(请谨慎开启)",
                "幸运项链有自己的罕见度,罕见度越高,属性词条数量越多(最高罕见6)",
                "项链除了增加基础三维，还可以增加POW属性，为副本战力的独立乘区",
                "使用'/汪币重置项链'消耗20汪币重新替换项链属性(会进入2次确认)",
                "调整掉落汪币数量：难6-20%+5,80%+1;难7-20%+15,80%+5",
                "去掉了虾仁猪心的隐藏副本概率提示",
                "为已经赞助的hxd返还0.33倍的汪币(不超过648)",
                "副本CD现在最多20分钟，每日副本次数最多40次"
            )
        )
    )

    // 获取格式化后的更新日志
    fun getFormattedLog(): String {
        val builder = StringBuilder()
        builder.append("⚔️ 本来是PK的你们却要下副本 更新日志 ⚔️\n\n")

        logEntries.forEach { entry ->
            builder.append("版本 ${entry.version} (${entry.date})\n")
            entry.changes.forEach { change ->
                builder.append("• $change\n")
            }
            builder.append("\n")
        }

        builder.append("群公告有在线表格（2个分页）\n你所有的问题基本上都可以查到")
        return builder.toString()
    }

    // 更新日志条目数据类
    data class LogEntry(
        val date: String,
        val version: String,
        val changes: List<String>
    )

    // 添加新日志条目的辅助方法（可选）
    fun addEntry(date: String, version: String, changes: List<String>) {
    }
}