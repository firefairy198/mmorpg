// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-10-27",
            version = "4.2.3",
            changes = listOf(
                "增加难9隐藏成功后的结局和对应处理"
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
}