package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow

// 全服楷模记录数据类
@Serializable
data class TopPlayerRecord(
    val playerId: Long,
    val playerName: String,
    val totalScore: Int,
    val finalATK: Int,
    val finalDEF: Int,
    val finalLUCK: Int,
    val baseATK: Int,
    val baseDEF: Int,
    val baseLUCK: Int,
    val equipmentName: String?,
    val equipmentATK: Int,
    val equipmentDEF: Int,
    val equipmentLUCK: Int,
    val equipmentBaseATK: Int,
    val equipmentBaseDEF: Int,
    val equipmentBaseLUCK: Int,
    val enhanceLevel: Int = 0,
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
    val relicGrade: String?,
    // 新增染色加成字段
    val relicAtkBonus: Int = 0,
    val relicDefBonus: Int = 0,
    val relicLuckBonus: Int = 0,
    // 吞噬属性字段
    val devouredATK: Int = 0,
    val devouredDEF: Int = 0,
    val devouredLUCK: Int = 0,
    val devouredPets: Map<String, Int> = emptyMap()
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
        relicGrade: String?,
        // 新增染色加成参数
        relicAtkBonus: Int = 0,
        relicDefBonus: Int = 0,
        relicLuckBonus: Int = 0,
        // 吞噬属性参数
        devouredATK: Int = 0,
        devouredDEF: Int = 0,
        devouredLUCK: Int = 0,
        devouredPets: Map<String, Int> = emptyMap()
    ) {
        val totalScore = finalATK + finalDEF + (finalLUCK * 5)

        val currentRecord = getRecord()

        // 获取玩家数据以获取装备强化信息
        val playerData = PlayerDataManager.getPlayerData(playerId)
        val enhanceLevel = playerData?.equipment?.enhanceLevel ?: 0

        // 计算装备的基础属性（从强化后的属性反推）
        val baseEquipmentATK = if (enhanceLevel > 0) {
            (equipmentATK / (1.1).pow(enhanceLevel)).toInt()
        } else {
            equipmentATK
        }

        val baseEquipmentDEF = if (enhanceLevel > 0) {
            (equipmentDEF / (1.1).pow(enhanceLevel)).toInt()
        } else {
            equipmentDEF
        }

        val baseEquipmentLUCK = if (enhanceLevel > 0) {
            (equipmentLUCK / (1.1).pow(enhanceLevel)).toInt()
        } else {
            equipmentLUCK
        }

        if (currentRecord == null || totalScore > currentRecord.totalScore) {
            val newRecord = TopPlayerRecord(
                playerId, playerName, totalScore,
                finalATK, finalDEF, finalLUCK,
                baseATK, baseDEF, baseLUCK,
                equipmentName, equipmentATK, equipmentDEF, equipmentLUCK,
                baseEquipmentATK, baseEquipmentDEF, baseEquipmentLUCK,
                enhanceLevel,
                petName, petATK, petDEF, petLUCK, petGrade, petEffect,
                relicName, relicATK, relicDEF, relicLUCK, relicGrade,
                // 传递染色加成属性
                relicAtkBonus, relicDefBonus, relicLuckBonus,
                // 传递吞噬属性
                devouredATK, devouredDEF, devouredLUCK, devouredPets
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

            builder.append("  $equipmentNameWithEnhance (ATK+${record.equipmentATK}, DEF+${record.equipmentDEF}, LUCK+${record.equipmentLUCK})\n")
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

        // 吞噬信息
        if (record.devouredPets.isNotEmpty()) {
            builder.append("\n🍽️ 吞噬:\n")
            val petsList = record.devouredPets.entries.joinToString("") { (name, count) ->
                if (count > 1) "$name($count)" else name
            }
            builder.append("  $petsList (ATK=${record.devouredATK}, DEF=${record.devouredDEF}, LUCK=${record.devouredLUCK})\n")
        }

        // 遗物信息
        builder.append("\n🔮 遗物:\n")
        if (record.relicName != null) {
            val atkWithBonus = record.relicATK + record.relicAtkBonus
            val defWithBonus = record.relicDEF + record.relicDefBonus
            val luckWithBonus = record.relicLUCK + record.relicLuckBonus

            builder.append("  ${record.relicName} (${record.relicGrade}级, ATK+${record.relicATK}(+${record.relicAtkBonus}), DEF+${record.relicDEF}(+${record.relicDefBonus}), LUCK+${record.relicLUCK}(+${record.relicLuckBonus}))\n")
        } else {
            builder.append("  无\n")
        }

        return builder.toString()
    }
}