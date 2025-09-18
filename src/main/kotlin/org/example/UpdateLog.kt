// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-09-19",
            version = "1.9.0",
            changes = listOf(
                "增强诗人地位！将诗人效果由1/4/9/16/25增强至4/9/16/25/36",
                "修改了变猪事件，主角改为茯苓\n",
                "答谢百人&存活7天，添加1个超强力兑换码，具体看在线文档",
                "给已赞助和想氪金的hxd一些喵币反馈(1R=500)",
                "个人不建议赞助，且成长属性相关的内容[永远]不会被影响",
                "暂不打算添加洗战弓券，战弓依然是稀有职业，会持续一段时间\n",
                "得到SS宠物时可以改名&改职业(仅支持1次)",
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

