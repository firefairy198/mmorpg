// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-09-14",
            version = "1.3.6",
            changes = listOf(
                "开启周末的副本狂欢-奖励变为2倍",
                "鼓励带新-无副本次数时可以继续参加副本，但无奖励",
                "添加了'/名人堂'指令以查询通过难5的无聊玩家",
                "大幅提升丝袜事件的成功率补正",
                "夜间助睡补丁-与上次寻敌时间差距6h以上时补偿3属性",
                "修改了一些无用装备的属性和名字，修改了货币名称",
                "修改替换装备后将全额返喵币以用来更换装备",
                "（懒得改购买逻辑了，逻辑还是钱够才能买/换，可以先购买白丝先得到返还的喵币再购买别的）\n",
                "提示：副本不论成功与否都能获得喵币奖励，但失败只有10%",
                "!!!只有成功通过副本才能获得事件的属性奖励!!!",
                "刷属性请尝试打的过的难度，难1难2属性奖励不差多少"
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