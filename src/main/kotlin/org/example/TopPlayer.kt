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
    val equipmentATK: Int,
    val equipmentDEF: Int,
    val equipmentLUCK: Int,
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
        finalATK: Int,  // 最终ATK
        finalDEF: Int,  // 最终DEF
        finalLUCK: Int, // 最终LUCK
        baseATK: Int,   // 基础ATK
        baseDEF: Int,   // 基础DEF
        baseLUCK: Int,  // 基础LUCK
        equipmentName: String?,
        equipmentATK: Int,
        equipmentDEF: Int,
        equipmentLUCK: Int,
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

        if (currentRecord == null || totalScore > currentRecord.totalScore) {
            val newRecord = TopPlayerRecord(
                playerId, playerName, totalScore,
                finalATK, finalDEF, finalLUCK,
                baseATK, baseDEF, baseLUCK,
                equipmentName, equipmentATK, equipmentDEF, equipmentLUCK,
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
            builder.append("  ${record.equipmentName} (ATK+${record.equipmentATK}, DEF+${record.equipmentDEF}, LUCK+${record.equipmentLUCK})\n")
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