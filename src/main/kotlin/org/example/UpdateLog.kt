// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-10-14",
            version = "4.0.1",
            changes = listOf(
                "将汪币掉落处理的更平滑了一些以符合之前版本的物品掉落价值",
                "难6-35%+3,75%+1 (理论值未更改)",
                "难7-35%+9,75%+3 (理论值降低了一些)",
                "难8-35%+27,75%+9",
                "增加了WB参与的基础奖励喵币：888 → 1288"
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