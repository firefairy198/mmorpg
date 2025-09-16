// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-09-16",
            version = "1.7.0",
            changes = listOf(
                "增加宠物特殊技能（宠物职业），在获得宠物时会随机获得",
                "宠物特殊技能分为'战士''盗贼''弓手''牧师''宝藏猎手''吟游诗人'",
                "增加宝藏猎手S的效果：普通副本事件+1（不叠加）",
                "老宠物会随机获得一个效果，为防止养成阶段的职业歧视",
                "道具商城添加'S型宠物辅助职业变更券'，使用后会变更宠物职业",
                "S型宠物辅助职业变更券：只会生成'盗贼S''牧师S''宝藏猎手S''吟游诗人S'4种职业",
                "已经2转的人少ROLL1次宠物职业，已补偿666喵币",
                "已经3转的人少ROLL2次宠物职业，已补偿1332喵币\n",

                "添加了隐藏奖励副本的负面事件，正负比值为20%:80%",
                "大幅加强了战士弓手的buff能力",
                "缩短了所有特殊技能和S的差距，具体在群公告的在线文档查看",
                "添加一个兑换码：chongwuchongzhi。增加双属性50，喵币1999",
                "修改了奖励副本失败时的概率提示",
                "节省消息输出，/加入 和 /组队 不再提示冷却时间以防止无脑刷屏，具体时间可以使用副本信息查看\n",

                "赶工版本，有问题及时汇报，谢谢~"
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

        builder.append("更多tips在群公告的在线表格中（有2个分页）\n你所有的问题基本上都可以查到")
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

