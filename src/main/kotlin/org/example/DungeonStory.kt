//DungeonStory.kt
package org.example.mmorpg

import kotlin.random.Random

// 添加副本剧情事件类
data class DungeonEvent(
    val playerName: String,
    val action: String,
    val effect: String,
    val description: String,
    val successRateChange: Double = 0.0,
    val extraGold: Int = 0,
    val extraATK: Int = 0,
    val extraDEF: Int = 0
)

// 添加副本剧情生成器
object DungeonStoryGenerator {
    // 正面事件及其效果
    private val positiveEvents = mapOf(
        "找到了一双丝袜" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "找到了一双丝袜", "↑",
                "[❤] $playerName 在角落中找到了一双丝袜，士气大振！",
                successRateChange = 0.015)
        },
        "发现了隐藏的宝箱" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "发现了隐藏的宝箱", "↑",
                "[❤] $playerName 发现了隐藏的宝箱，获得了额外的喵币！",
                extraGold = (dungeon.reward * 0.1).toInt())
        },
        "施展了治疗法术" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "施展了治疗法术", "↑",
                "[✨] $playerName 施展了强大的治疗法术，恢复了队伍状态",
                successRateChange = 0.003)
        },
        "找到了捷径" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "找到了捷径", "↑",
                "[✨] $playerName 找到了一条捷径，减少了遭遇的敌人",
                successRateChange = 0.004)
        },
        "激活了神秘BUFF" to { playerName: String, dungeon: Dungeon ->
            val dungeonLevel = dungeon.id // 副本编号 1-5
            val squareBonus = dungeonLevel * dungeonLevel // 平方奖励
            DungeonEvent(playerName, "激活了神秘BUFF", "↑",
                "[❤] $playerName 激活了神秘BUFF，所有成员BDBC！",
                extraATK = squareBonus,
                extraDEF = squareBonus)
        },
        "驱散了周围的迷雾" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "驱散了周围的迷雾", "↑",
                "[✨] $playerName 驱散了迷雾，视野变得更加清晰",
                successRateChange = 0.003)
        },
        "解锁了古老符文" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "解锁了古老符文", "↑",
                "[✨] $playerName 解锁了古老符文，感觉副本难度下降了",
                successRateChange = 0.005)
        },
        "变成了猪" to { playerName: String, dungeon: Dungeon ->
            val dungeonLevel = dungeon.id // 副本编号-难度1-5
            DungeonEvent(playerName, "变成了猪", "↑",
                "[❤] $playerName 遇到了白丝独伊，并把他变成了🐷！",
                extraATK = dungeonLevel,
                extraDEF = dungeonLevel)
        },
        "遇到了魅魔" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "遇到了魅魔", "↑",
                "[✨] $playerName 遇到了魅魔，和队友一起超市了ta",
                successRateChange = 0.004)
        },
        "解读了古代文字" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "解读了古代文字", "↑",
                "[✨] $playerName 解读了古代文字，获得了战斗技巧",
                successRateChange = 0.005)
        }
    )

    // 负面事件及其效果
    private val negativeEvents = mapOf(
        "看到了一个宝箱" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "看到了一个宝箱", "↓",
                "[☠] $playerName 看到了一个宝箱，但被它吞了进去",
                successRateChange = -0.007)
        },
        "被史莱姆缠住了" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "被史莱姆缠住了", "↓",
                "[☠☠] $playerName 被史莱姆缠住了，行动受限",
                successRateChange = -0.008)
        },
        "发现了古老典籍" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "发现了古老典籍", "↓",
                "[☠☠] $playerName 被名为《金**每》的古书吸引住了，浪费了时间",
                successRateChange = -0.009)
        },
        "试图拆卸华丽陷阱" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "试图拆卸华丽陷阱", "↓",
                "[☠] $playerName 试图拆卸一个陷阱，随着一声巨响，希望人没事",
                successRateChange = -0.007)
        },
        "向奇怪的神像祈祷" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "向奇怪的神像祈祷", "↓",
                "[☠☠☠] $playerName 向一个长满触手的神像祈祷，感觉有什么东西回应了…",
                successRateChange = -0.01)
        },
        "跟魅魔跑了" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "跟魅魔跑了", "↓",
                "[☠☠☠] $playerName 被魅魔诱惑，暂时离开了队伍",
                successRateChange = -0.011)
        },
        "开始吟唱爆裂魔法" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "开始吟唱爆裂魔法", "↓",
                "[☠☠] ‘比黑色更黑……Explosion!’$playerName 用魔法炸到了空气，然后瘫倒在地。",
                successRateChange = -0.008)
        },
        "开始玩坎公骑冠剑" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "开始玩坎公骑冠剑", "↓",
                "[☠] $playerName 忽然想起刀还没出，连忙掏出手机打开了坎公骑",
                successRateChange = -0.007)
        },
        "惊动了守卫" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "惊动了守卫", "↓",
                "[☠☠] $playerName 不小心惊动了守卫，增加了战斗难度",
                successRateChange = -0.009)
        },
        "被黑暗气息侵蚀" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "被黑暗气息侵蚀", "↓",
                "[☠☠☠] $playerName 被黑暗气息侵蚀，状态下降",
                successRateChange = -0.01)
        }
    )

    // BOSS战事件
    private val bossEvents = listOf(
        "召唤了小弟支援",
        "进入了狂暴状态",
        "施展了全屏毒雾",
        "开启了绝对防御",
        "发动了地震术",
        "释放了黑暗领域",
        "使用了精神控制",
        "召唤了陨石雨",
        "开启了嗜血"
    )

    fun generateEvents(team: Team, dungeon: Dungeon, positiveEventBonus: Double = 0.0, additionalEvents: Int = 0): List<DungeonEvent> {
        val events = mutableListOf<DungeonEvent>()
        val members = team.members.map { it.playerName }

        // 基础5个事件 + 宝藏猎手提供的额外事件
        val totalEvents = 5 + additionalEvents

        // 检查是否为难度7副本
        val isHighDifficulty = dungeon.id == 7 || dungeon.id == 8

        // 生成事件
        repeat(totalEvents) {
            val player = members.random()

            // 难度7副本：基础正面事件概率0%，但可以受到牧师加成
            val basePositiveChance = if (isHighDifficulty) {
                0.2 // 难度7和8副本基础正面事件概率为20%
            } else {
                0.5
            }

            // 应用牧师效果加成
            val positiveChance = (basePositiveChance + positiveEventBonus).coerceIn(0.0, 1.0)

            val isPositive = Random.nextDouble() < positiveChance

            if (isPositive) {
                val (action, eventGenerator) = positiveEvents.entries.random()
                val event = eventGenerator(player, dungeon)
                events.add(event)
            } else {
                val (action, eventGenerator) = negativeEvents.entries.random()
                val event = eventGenerator(player, dungeon)
                events.add(event)
            }
        }

        // BOSS事件
        events.add(DungeonEvent(
            "BOSS",
            bossEvents.random(),
            "⚡",
            "队伍遇到了BOSS，BOSS${bossEvents.random()}……"
        ))

        return events
    }

    // 在 DungeonStoryGenerator 中添加奖励副本事件
    private val bonusDungeonEvents = mapOf(
        "发现了隐藏的传送门" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "发现了隐藏的传送门", "↑",
                "[✨] $playerName 发现了一个发光的传送门，似乎是一条近道！",
                successRateChange = 0.023)
        },
        "找到了古代宝库" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "找到了古代宝库", "↑",
                "[❤] $playerName 发现了一个装满喵币的古代宝库！",
                extraGold = (dungeon.reward * 0.15).toInt())
        },
        "沐浴在神圣之光中" to { playerName: String, dungeon: Dungeon ->
            // 根据副本难度计算属性奖励
            // 基础奖励为2点，每增加40000难度增加1点奖励
            val baseBonus = 2
            val difficultyBonus = (dungeon.difficulty / 40000).toInt()
            val totalBonus = baseBonus + difficultyBonus

            DungeonEvent(playerName, "沐浴在神圣之光中", "↑",
                "[❤] $playerName 沐浴在神圣之光中，感觉力量增强了！",
                extraATK = totalBonus,
                extraDEF = totalBonus)
        }
    )
    private val bonusDungeonNegativeEvents = mapOf(
        "触发了古老陷阱" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "触发了古老陷阱", "↓",
                "[☠☠] $playerName 不小心触发了一个古老的陷阱，队伍受到了惊吓！",
                successRateChange = -0.05)
        },
        "遭遇了时空乱流" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "遭遇了时空乱流", "↓",
                "[☠☠☠] $playerName 遭遇了时空乱流，队伍被分散了！",
                successRateChange = -0.1)
        },
        "被幻象迷惑" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "被幻象迷惑", "↓",
                "[☠] $playerName 被奖励副本中的幻象迷惑，浪费了宝贵时间！",
                successRateChange = -0.025)
        },
        "惊动了守护者" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "惊动了守护者", "↓",
                "[☠☠] $playerName 不小心惊动了奖励副本的守护者，使其更加警惕！",
                successRateChange = -0.05)
        },
        "陷入了魔法沼泽" to { playerName: String, dungeon: Dungeon ->
            DungeonEvent(playerName, "陷入了魔法沼泽", "↓",
                "[☠] $playerName 陷入了魔法沼泽，行动变得迟缓！",
                successRateChange = -0.025)
        }
    )

    // 修改 generateBonusDungeonEvents 函数，添加难度7隐藏副本的特殊事件概率
    fun generateBonusDungeonEvents(team: Team, dungeon: Dungeon, positiveEventBonus: Double = 0.0): List<DungeonEvent> {
        val events = mutableListOf<DungeonEvent>()
        val members = team.members.map { it.playerName }

        // 检查是否为难度7隐藏副本（原始副本难度为7）
        val isHighDifficultyBonus = (dungeon.id / 10) == 7 || (dungeon.id / 10) == 8

        // 添加3个特殊事件
        repeat(3) {
            val player = members.random()

            // 难度7隐藏副本：基础正面事件概率20%，但可以受到牧师加成
            val basePositiveChance = if (isHighDifficultyBonus) {
                0.2 // 难度7和8隐藏副本基础正面事件概率为20%
            } else {
                0.2
            }

            // 应用牧师效果加成
            val positiveChance = (basePositiveChance + positiveEventBonus).coerceIn(0.0, 1.0)

            val isPositive = Random.nextDouble() < positiveChance

            if (isPositive) {
                val (action, eventGenerator) = bonusDungeonEvents.entries.random()
                val event = eventGenerator(player, dungeon)
                events.add(event)
            } else {
                val (action, eventGenerator) = bonusDungeonNegativeEvents.entries.random()
                val event = eventGenerator(player, dungeon)
                events.add(event)
            }
        }

        // BOSS事件
        events.add(DungeonEvent(
            "BOSS",
            bossEvents.random(),
            "⚡",
            "队伍遇到了奖励副本的守护者，守护者${bossEvents.random()}……"
        ))

        return events
    }
}
