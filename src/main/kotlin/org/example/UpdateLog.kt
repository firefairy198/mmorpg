// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-09-15",
            version = "1.5.1",
            changes = listOf(
                "修改奖励副本汇报时错误的使用了普通难度作为基础成功率（但不影响结果）",
                "调整属性锁为动态（使用'/我的信息'查询）\n\n",
                "增加了'/兑换码 [兑换码内容]'功能",
                "兑换码:dalaodaidaiwo",
                "增加双属性20，每人限使用1次",
                "兑换码:geiwodianmiaobi",
                "增加喵币500，每人限使用1次",
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

        builder.append("更多功能开发中，敬请期待！")
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