// LuckyNecklace.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlin.random.Random

// 项链属性类型枚举
enum class NecklaceAttributeType {
    ATK, DEF, LUCK, POW
}

// 项链属性数据类
@Serializable
data class NecklaceAttribute(
    val type: NecklaceAttributeType,
    val value: Int, // 对于POW类型，这个值表示百分比（如5表示5%）
    val displayValue: String // 显示用的字符串
)

// 幸运项链数据类
@Serializable
data class LuckyNecklace(
    val rarity: Int, // 罕见度 1-6
    val attributes: List<NecklaceAttribute>,
    val isActivated: Boolean = true
) {
    // 获取项链名称
    fun getName(): String {
        return when (rarity) {
            1 -> "[罕见度1]"
            2 -> "[罕见度2]"
            3 -> "[罕见度3]"
            4 -> "[罕见度4]"
            5 -> "[罕见度5]"
            6 -> "[罕见度6]"
            else -> "[未知]"
        }
    }

    // 获取总POW加成（百分比）
    fun getTotalPowBonus(): Double {
        return attributes
            .filter { it.type == NecklaceAttributeType.POW }
            .sumOf { it.value.toDouble() } / 100.0
    }

    // 格式化显示项链信息
    fun getFormattedInfo(): String {
        val builder = StringBuilder()
        builder.append("${getName()}\n")
        builder.append("属性:\n")

        attributes.forEach { attr ->
            when (attr.type) {
                NecklaceAttributeType.ATK -> builder.append("  ATK: +${attr.value}\n")
                NecklaceAttributeType.DEF -> builder.append("  DEF: +${attr.value}\n")
                NecklaceAttributeType.LUCK -> builder.append("  LUCK: +${attr.value}\n")
                NecklaceAttributeType.POW -> builder.append("  POW: +${attr.displayValue}\n")
            }
        }

        return builder.toString()
    }
}

// 幸运项链管理器
object LuckyNecklaceManager {

    // 生成随机项链属性
    private fun generateRandomAttribute(): NecklaceAttribute {
        val randomType = when (Random.nextDouble(0.0, 1.0)) {
            in 0.0..0.35 -> NecklaceAttributeType.ATK      // 35% 概率
            in 0.35..0.7 -> NecklaceAttributeType.DEF      // 35% 概率
            in 0.7..0.9 -> NecklaceAttributeType.LUCK     // 20% 概率
            else -> NecklaceAttributeType.POW             // 10% 概率
        }

        return when (randomType) {
            NecklaceAttributeType.ATK -> {
                val value = Random.nextInt(100, 501) // 50-500
                NecklaceAttribute(NecklaceAttributeType.ATK, value, value.toString())
            }
            NecklaceAttributeType.DEF -> {
                val value = Random.nextInt(100, 501) // 50-500
                NecklaceAttribute(NecklaceAttributeType.DEF, value, value.toString())
            }
            NecklaceAttributeType.LUCK -> {
                val value = Random.nextInt(10, 51) // 5-50
                NecklaceAttribute(NecklaceAttributeType.LUCK, value, value.toString())
            }
            NecklaceAttributeType.POW -> {
                val value = Random.nextInt(1, 6) // 1-5
                NecklaceAttribute(NecklaceAttributeType.POW, value, "${value}%")
            }
        }
    }

    // 生成随机幸运项链
    fun generateRandomNecklace(): LuckyNecklace {
        // 确定罕见度
        val rarityRoll = Random.nextDouble(0.0, 1.0)
        val rarity = when {
            rarityRoll <= 0.10 -> 1  // 10%
            rarityRoll <= 0.34 -> 2  // 24%
            rarityRoll <= 0.84 -> 3  // 50%
            rarityRoll <= 0.94 -> 4  // 10%
            rarityRoll <= 0.99 -> 5  // 5%
            else -> 6                // 1%
        }

        // 根据罕见度生成对应数量的属性
        val attributeCount = rarity
        val attributes = mutableListOf<NecklaceAttribute>()

        repeat(attributeCount) {
            attributes.add(generateRandomAttribute())
        }

        return LuckyNecklace(rarity, attributes)
    }

    // 检查玩家是否可以开启幸运项链
    fun canActivateNecklace(playerData: PlayerData): Pair<Boolean, String> {
        if (playerData.luckyNecklace != null) {
            return Pair(false, "您已经开启了幸运项链功能！")
        }

        if (playerData.baseLUCK < 200) {
            return Pair(false, "开启幸运项链需要200点基础LUCK，您当前只有${playerData.baseLUCK}点")
        }

        return Pair(true, "")
    }

    // 激活幸运项链
    fun activateNecklace(playerData: PlayerData): Pair<Boolean, String> {
        val (canActivate, message) = canActivateNecklace(playerData)
        if (!canActivate) {
            return Pair(false, message)
        }

        // 扣除200 LUCK
        playerData.baseLUCK -= 200

        // 生成随机项链
        val necklace = generateRandomNecklace()
        playerData.luckyNecklace = necklace

        return Pair(true, "成功开启幸运项链！消耗200点基础LUCK\n${necklace.getFormattedInfo()}")
    }

    // 计算队伍总POW加成
    fun calculateTeamPowBonus(team: Team): Double {
        var totalPowBonus = 0.0

        team.members.forEach { member ->
            if (!member.isVirtual) {
                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                memberData?.luckyNecklace?.let { necklace ->
                    totalPowBonus += necklace.getTotalPowBonus()
                    // 添加调试日志
                    PluginMain.logger.info("玩家 ${member.playerName} 项链POW加成: ${necklace.getTotalPowBonus()}")
                }
            }
        }

        // 添加调试日志
        PluginMain.logger.info("队伍总POW加成: $totalPowBonus")
        return totalPowBonus
    }

    // 检查玩家是否可以重铸幸运项链
    fun canReforgeNecklace(playerData: PlayerData): Pair<Boolean, String> {
        if (playerData.luckyNecklace == null) {
            return Pair(false, "您尚未开启幸运项链功能，无法重铸！")
        }

        if (playerData.wangCoin < 20) {
            return Pair(false, "重铸幸运项链需要20汪币，您当前只有${playerData.wangCoin}汪币")
        }

        return Pair(true, "")
    }

    // 执行重铸（不立即应用，返回新项链用于确认）
    fun reforgeNecklace(playerData: PlayerData): LuckyNecklace {
        // 生成新的随机项链
        return generateRandomNecklace()
    }

    // 应用重铸（确认后调用）
    fun applyReforge(playerData: PlayerData, newNecklace: LuckyNecklace) {
        playerData.luckyNecklace = newNecklace
    }
}