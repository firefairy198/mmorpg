// PlayerDataManager.kt
package org.example.mmorpg

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.example.mmorpg.PluginMain.logger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId

object PlayerDataManager {


    private val dataDir by lazy {
        File(PluginMain.dataFolder, "players").apply { mkdirs() }
    }

    private val json = Json { prettyPrint = true }

    fun getPlayerData(qqId: Long): PlayerData? {
        val file = File(dataDir, "$qqId.json")
        return if (file.exists()) {
            try {
                val playerData = json.decodeFromString<PlayerData>(file.readText())

                // 兼容性处理：为旧宠物添加随机特殊效果
                if (playerData.pet != null && playerData.pet!!.specialEffect == null) {
                    val oldPet = playerData.pet!!
                    val allEffects = PetEffect.values()
                    val specialEffect = allEffects.random()

                    playerData.pet = Pet(
                        oldPet.name,
                        oldPet.atk,
                        oldPet.def,
                        oldPet.luck,
                        oldPet.grade,
                        specialEffect
                    )
                }

                // 兼容性处理：新增消息返回样式字段
                if (playerData.messageBack == null) {
                    playerData.messageBack = null // 保持null，表示使用文字消息
                }

                if (playerData.pondJoinTime == null) {
                    playerData.pondJoinTime = "2025-09-01T00:00:00" // 默认时间
                }
                if (playerData.lastEliteFishAttackTime == 0L) {
                    playerData.lastEliteFishAttackTime = System.currentTimeMillis() - 2 * 60 * 60 * 1000 // 设置为2小时前，允许立即攻击
                }

                // 兼容性处理：新增彩笔字段
                if (playerData.relicAtkBonus == 0 && playerData.relicDefBonus == 0 && playerData.relicLuckBonus == 0) {
                    // 检查是否是旧数据，需要初始化
                    playerData.relicAtkBonus = 0
                    playerData.relicDefBonus = 0
                    playerData.relicLuckBonus = 0
                }

                // 再处理彩笔字段
                if (playerData.redPenCount == 0 && playerData.bluePenCount == 0 &&
                    playerData.yellowPenCount == 0 && playerData.blackPenCount == 0) {
                    playerData.redPenCount = 0
                    playerData.bluePenCount = 0
                    playerData.yellowPenCount = 0
                    playerData.blackPenCount = 0  // 新增黑彩笔初始化
                }

                if (playerData.devouredPets.isEmpty()) {
                    playerData.devouredPets = mutableMapOf()
                    playerData.devouredATK = 0
                    playerData.devouredDEF = 0
                    playerData.devouredLUCK = 0
                }
                // 兼容性处理：幸运项链字段
                if (playerData.luckyNecklace == null) {
                    playerData.luckyNecklace = null
                }

                // 兼容性处理：初始化S型宠物变更券字段
                if (playerData.sPetChangeTickets == 0) {
                    // 对于旧数据，初始化为0
                    playerData.sPetChangeTickets = 0
                }
                if (playerData.wangCoin == 0) {
                    playerData.wangCoin = 0
                }

                // 兼容性处理：新增鱼饵字段
                if (playerData.fishBaitCount == 0 && playerData.dailyFishBaitUsed == 0) {
                    playerData.fishBaitCount = 0
                    playerData.dailyFishBaitUsed = 0
                    playerData.lastFishResetDate = ""
                }
                // 兼容性处理：新增炸鱼器字段
                if (playerData.fishBombCount == 0) {
                    playerData.fishBombCount = 0
                }
                if (playerData.advancedFishBombCount == 0) {
                    playerData.advancedFishBombCount = 0
                }
                // 兼容性处理：新增神奇小药丸字段
                if (playerData.miraclePillCount == 0) {
                    playerData.miraclePillCount = 0
                }

                // 保存更新后的数据
                savePlayerData(playerData)

                // 处理 usedCodes 字段的兼容性
                if (playerData.usedCodes.isEmpty()) {
                    // 对于旧数据，初始化为空集合
                    playerData.usedCodes = mutableSetOf()
                }

                // 验证属性是否超过上限，如果超过则重置为上限值
                val maxAttribute = 225 + 10 * playerData.rebirthCount
                if (playerData.baseATK > maxAttribute) {
                    PluginMain.logger.warning("玩家 ${playerData.qqId} 的ATK属性异常 (${playerData.baseATK} > $maxAttribute)，已重置为上限值")
                    playerData.baseATK = maxAttribute
                }
                if (playerData.baseDEF > maxAttribute) {
                    PluginMain.logger.warning("玩家 ${playerData.qqId} 的DEF属性异常 (${playerData.baseDEF} > $maxAttribute)，已重置为上限值")
                    playerData.baseDEF = maxAttribute
                }

                // 处理lastFindOpponentTime字段的兼容性
                if (playerData.lastFindOpponentTime == 0L) {
                    // 对于旧数据，设置为当前时间减去7小时（确保第一次使用能获得奖励）
                    playerData.lastFindOpponentTime = System.currentTimeMillis() - 7 * 60 * 60 * 1000
                }

                // 处理副本CD字段的兼容性
                if (playerData.lastDungeonTime == 0L && playerData.lastDungeonDate != null) {
                    // 将旧格式的日期字符串转换为时间戳
                    try {
                        val date = LocalDate.parse(playerData.lastDungeonDate, DateTimeFormatter.ISO_DATE)
                        val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        playerData.lastDungeonTime = timestamp
                        playerData.lastDungeonDate = null // 清空旧字段
                    } catch (e: Exception) {
                        logger.error("转换副本日期时发生错误", e)
                        // 如果转换失败，设置为当前时间减去4小时（允许立即使用）
                        playerData.lastDungeonTime = System.currentTimeMillis() - 4 * 60 * 60 * 1000
                    }
                } else if (playerData.lastDungeonTime == 0L) {
                    // 如果两个字段都没有值，设置为当前时间减去4小时（允许立即使用）
                    playerData.lastDungeonTime = System.currentTimeMillis() - 4 * 60 * 60 * 1000
                }

                if (playerData.enhanceFailCount == 0) {
                    // 对于旧数据，初始化为0
                    playerData.enhanceFailCount = 0
                }

                // 处理每日副本次数重置（兼容老版本）
                val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                // 处理鱼饵每日重置
                if (playerData.lastFishResetDate != today) {
                    playerData.lastFishResetDate = today
                    playerData.dailyFishBaitUsed = 0
                }

                // 如果lastDungeonResetDate为空（老版本数据），设置为今天并重置计数
                if (playerData.lastDungeonResetDate.isNullOrEmpty()) {
                    playerData.lastDungeonResetDate = today
                    playerData.dailyDungeonCount = 0
                }
                // 如果上次重置日期不是今天，重置计数
                else if (playerData.lastDungeonResetDate != today) {
                    playerData.lastDungeonResetDate = today
                    playerData.dailyDungeonCount = 0
                }

                // 保存更新后的数据
                savePlayerData(playerData)

                playerData
            } catch (e: Exception) {
                logger.error("解析玩家数据时发生错误", e)
                null
            }
        } else {
            null
        }
    }

    fun createPlayerData(qqId: Long): PlayerData {
        val playerData = PlayerData(qqId)
        savePlayerData(playerData)
        return playerData
    }

    fun savePlayerData(playerData: PlayerData) {
        try {
            val file = File(dataDir, "${playerData.qqId}.json")
            file.writeText(json.encodeToString(playerData))
        } catch (e: Exception) {
            PluginMain.logger.error("保存玩家数据时发生错误", e)
        }
    }

    fun getAllPlayerIds(): List<Long> {
        return dataDir.listFiles { _, name -> name.endsWith(".json") }
            ?.map { it.nameWithoutExtension.toLong() }
            ?: emptyList()
    }
}