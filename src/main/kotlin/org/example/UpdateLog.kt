// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-09-20",
            version = "2.0.6",
            changes = listOf(
                "修复了一处会导致存储不上掉落数据的BUG",
                "赏金猎手S的普通事件+1调整为可叠加",
                "修改了世界BOSS血量，但后期会继续调整，以保证每个号的参加",
                "添加了更多的指令缩写：/jr,/fbxx等",
                "添加了强化保底机制，25次失败必定成功，成功后清空保底次数\n",
                "更多的本日更新详见群公告的在线文档",
                "出SS遗物可以把防御加到攻击和幸运上去,可以命名"
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
        // 注意：由于 logEntries 是 val 且使用 listOf，这里需要修改为可变列表
        // 实际使用中，您可以直接修改上面的 logEntries 定义
    }
}

