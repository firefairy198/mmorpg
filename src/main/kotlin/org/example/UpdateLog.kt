// UpdateLog.kt
package org.example.mmorpg

object UpdateLog {
    // 更新日志内容，按日期组织
    private val logEntries = listOf(
        LogEntry(
            date = "2025-9-30",
            version = "3.3.3",
            changes = listOf(
                "增加道具：鱼饵(5000喵币5个,每日限制使用10个)，炸鱼器(10000喵币1个)",
                "使用'/购买道具 2'以购买鱼饵，使用'/购买道具 3'以购买炸鱼器",
                "增加鱼塘功能(该功能需持有遗物且转生次数≥3次使用)",
                "每个人都可以创建鱼塘，鱼塘可以容纳10个人，创建者为鱼塘管理者(创建鱼塘需消耗5000喵币)",
                "鱼塘可以升到10级，每50000喵币提升一级，使用'/查看鱼塘'可以查看鱼塘状态",
                "使用'/修建鱼塘 [喵币金额]'可以修建当前群的鱼塘(修建时尽量不要超过当前等级所需的喵币)",
                "增加钓鱼功能(该功能需持有遗物且转生次数≥3次使用)",
                "使用'/使用 2'来消耗1个鱼饵，并获取到一条鱼，出货概率和当前群的鱼塘等级相关",
                "使用'/使用 3'来消耗1个炸鱼器，并获取到10条鱼，只能在每日钓鱼次数为0时使用",
                "钓到鱼后会自动生成烹饪方式，增加对应的攻防属性。EX鱼会增加基础幸运。",
                "'/转生兑换属性'指令将在周一更新时移除，请自己权衡属性上限和转生次数",
                "调整WB血量，Lv1 → 150W ，BOOST → x1.1，以方便更多人参加低等级\n",
                "鱼塘和钓鱼的相关指令：(鱼塘管理者)",
                "/创建鱼塘 [鱼塘名] - 创建自己的鱼塘",
                "/踢出鱼塘 [玩家ID(QQ号)] - 鱼塘管理者踢出玩家",
                "/销毁鱼塘 - 鱼塘管理者销毁鱼塘",
                "鱼塘和钓鱼的相关指令：(其他)",
                "/加入鱼塘 [鱼塘名] - 加入其他玩家的鱼塘",
                "/查看鱼塘 - 查看自己加入的鱼塘状态",
                "/离开鱼塘 - 离开当前鱼塘",
                "/修建鱼塘 [金额] - 为当前鱼塘投资升级"
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