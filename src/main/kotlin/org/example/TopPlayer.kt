package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

// 全服楷模记录数据类
@Serializable
data class TopPlayerRecord(
    val playerId: Long,
    val playerName: String,
    val totalScore: Int,
    val finalATK: Int,  // 改为最终ATK
    val finalDEF: Int,  // 改为最终DEF
    val finalLUCK: Int, // 改为最终LUCK
    val baseATK: Int,   // 保留基础ATK用于显示
    val baseDEF: Int,   // 保留基础DEF用于显示
    val baseLUCK: Int,  // 保留基础LUCK用于显示
    val equipmentName: String?,
    val equipmentATK: Int,      // 强化后的ATK
    val equipmentDEF: Int,      // 强化后的DEF
    val equipmentLUCK: Int,     // 强化后的LUCK
    val equipmentBaseATK: Int,  // 新增：装备基础ATK
    val equipmentBaseDEF: Int,  // 新增：装备基础DEF
    val equipmentBaseLUCK: Int, // 新增：装备基础LUCK
    val enhanceLevel: Int = 0,  // 新增：强化等级
    val petName: String?,
    val petATK: Int,
    val petDEF: Int,
    val petLUCK: Int,
    val petGrade: String?,
    val petEffect: String?,
    val relicName: String?,
    val relicATK: Int,
    val relicDEF: Int,
    val relicLUCK: Int,
    val relicGrade: String?
)

// 全服楷模管理器
object TopPlayerManager {
    private val dataFile by lazy {
        File(PluginMain.dataFolder, "top_player.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                writeText("{}")
            }
        }
    }

    private val json = Json { prettyPrint = true }

    // 更新全服楷模记录 - 使用最终属性计算分数
    fun updateRecord(
        playerId: Long,
        playerName: String,
        finalATK: Int,
        finalDEF: Int,
        finalLUCK: Int,
        baseATK: Int,
        baseDEF: Int,
        baseLUCK: Int,
        equipmentName: String?,
        equipmentATK: Int,
        equipmentDEF: Int,
        equipmentLUCK: Int,
        equipmentBaseATK: Int,
        equipmentBaseDEF: Int,
        equipmentBaseLUCK: Int,
        enhanceLevel: Int,
        petName: String?,
        petATK: Int,
        petDEF: Int,
        petLUCK: Int,
        petGrade: String?,
        petEffect: String?,
        relicName: String?,
        relicATK: Int,
        relicDEF: Int,
        relicLUCK: Int,
        relicGrade: String?
    ) {
        val totalScore = finalATK + finalDEF + (finalLUCK * 5) // 使用最终属性计算分数

        val currentRecord = getRecord()

        // 获取玩家数据以获取装备强化信息
        val playerData = PlayerDataManager.getPlayerData(playerId)
        val enhanceLevel = playerData?.equipment?.enhanceLevel ?: 0

        // 计算装备的基础属性（从强化后的属性反推）
        val baseEquipmentATK = if (enhanceLevel > 0) {
            (equipmentATK / (1 + enhanceLevel * 0.1)).toInt()
        } else {
            equipmentATK
        }

        val baseEquipmentDEF = if (enhanceLevel > 0) {
            (equipmentDEF / (1 + enhanceLevel * 0.1)).toInt()
        } else {
            equipmentDEF
        }

        val baseEquipmentLUCK = if (enhanceLevel > 0) {
            (equipmentLUCK / (1 + enhanceLevel * 0.1)).toInt()
        } else {
            equipmentLUCK
        }

        if (currentRecord == null || totalScore > currentRecord.totalScore) {
            val newRecord = TopPlayerRecord(
                playerId, playerName, totalScore,
                finalATK, finalDEF, finalLUCK,
                baseATK, baseDEF, baseLUCK,
                equipmentName, equipmentATK, equipmentDEF, equipmentLUCK, // 使用强化后的属性
                baseEquipmentATK, baseEquipmentDEF, baseEquipmentLUCK, // 存储基础属性
                enhanceLevel, // 存储强化等级
                petName, petATK, petDEF, petLUCK, petGrade, petEffect,
                relicName, relicATK, relicDEF, relicLUCK, relicGrade
            )

            try {
                dataFile.writeText(json.encodeToString(newRecord))
                PluginMain.logger.info("更新全服楷模记录：${playerName} - 总分: ${totalScore}")
            } catch (e: Exception) {
                PluginMain.logger.error("保存全服楷模记录时发生错误", e)
            }
        }
    }

    // 获取当前全服楷模记录
    fun getRecord(): TopPlayerRecord? {
        return try {
            json.decodeFromString<TopPlayerRecord>(dataFile.readText())
        } catch (e: Exception) {
            PluginMain.logger.error("读取全服楷模记录时发生错误", e)
            null
        }
    }

    // 获取格式化后的全服楷模信息 - 使用最终属性
    fun getFormattedTopPlayer(): String {
        val record = getRecord()
        if (record == null) {
            return "暂无全服楷模记录"
        }

        val builder = StringBuilder()
        builder.append("🏆 全村儿最帅 🏆\n\n")
        builder.append("玩家: ${record.playerName}\n")
        builder.append("战力: ${record.totalScore}\n\n")

        // 属性汇总
        builder.append("📊 属性汇总:\n")
        builder.append("  最终属性: ATK:${record.finalATK}, DEF:${record.finalDEF}, LUCK:${record.finalLUCK}\n")
        builder.append("  基础属性: ATK:${record.baseATK}, DEF:${record.baseDEF}, LUCK:${record.baseLUCK}\n\n")

        // 装备信息
        builder.append("🗡️ 装备:\n")
        if (record.equipmentName != null) {
            // 显示装备强化等级
            val equipmentNameWithEnhance = if (record.enhanceLevel > 0) {
                "${record.equipmentName}+${record.enhanceLevel}"
            } else {
                record.equipmentName
            }

            // 计算强化加成
            val enhanceBonusATK = record.equipmentATK - record.equipmentBaseATK
            val enhanceBonusDEF = record.equipmentDEF - record.equipmentBaseDEF
            val enhanceBonusLUCK = record.equipmentLUCK - record.equipmentBaseLUCK

            builder.append("  $equipmentNameWithEnhance (ATK+${record.equipmentATK}")
            if (record.enhanceLevel > 0) {
                builder.append("(${record.equipmentBaseATK}+$enhanceBonusATK)")
            }

            builder.append(", DEF+${record.equipmentDEF}")
            if (record.enhanceLevel > 0) {
                builder.append("(${record.equipmentBaseDEF}+$enhanceBonusDEF)")
            }

            builder.append(", LUCK+${record.equipmentLUCK}")
            if (record.enhanceLevel > 0) {
                builder.append("(${record.equipmentBaseLUCK}+$enhanceBonusLUCK)")
            }
            builder.append(")\n")
        } else {
            builder.append("  无\n")
        }

        // 宠物信息
        builder.append("\n🐾 宠物:\n")
        if (record.petName != null) {
            builder.append("  ${record.petName} (${record.petGrade}级, ATK+${record.petATK}, DEF+${record.petDEF}, LUCK+${record.petLUCK}")
            if (record.petEffect != null) {
                builder.append(", ${record.petEffect})\n")
            } else {
                builder.append(")\n")
            }
        } else {
            builder.append("  无\n")
        }

        // 遗物信息
        builder.append("\n🔮 遗物:\n")
        if (record.relicName != null) {
            builder.append("  ${record.relicName} (${record.relicGrade}级, ATK+${record.relicATK}, DEF+${record.relicDEF}, LUCK+${record.relicLUCK})\n")
        } else {
            builder.append("  无\n")
        }

        return builder.toString()
    }
}