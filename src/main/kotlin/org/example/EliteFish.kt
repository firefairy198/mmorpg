//EliteFish.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

// 精英鱼数据类
@Serializable
data class EliteFish(
    val pondName: String,
    var currentHp: Long = 15000000,
    val maxHp: Long = 15000000,
    val spawnTime: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    var attackers: MutableMap<String, Long> = mutableMapOf() // 玩家ID-时间 -> 造成的伤害
)

// 精英鱼管理器
object EliteFishManager {
    private val dataFile by lazy {
        File(PluginMain.dataFolder, "elite_fish.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                writeText("{}")
            }
        }
    }

    private val json = Json { prettyPrint = true }
    private val eliteFishes = mutableMapOf<String, EliteFish>()

    init {
        loadEliteFishes()
    }

    // 属性增加时检查上限（复制版本）
    private fun increaseAttributeWithLimit(currentValue: Int, increase: Int, rebirthCount: Int): Int {
        val maxValue = 225 + 10 * rebirthCount

        // 如果当前值已经超过上限，先将其设置为上限值
        val normalizedCurrent = if (currentValue > maxValue) {
            PluginMain.logger.warning("属性异常: 当前值($currentValue)超过上限($maxValue)，已重置为上限值")
            maxValue
        } else {
            currentValue
        }

        val newValue = normalizedCurrent + increase
        return if (newValue > maxValue) {
            PluginMain.logger.debug("属性增加后超过上限，设置为上限值: $maxValue")
            maxValue
        } else {
            PluginMain.logger.debug("属性增加后未超过上限，新值: $newValue")
            newValue
        }
    }

    // 加载精英鱼数据
    private fun loadEliteFishes() {
        try {
            val loadedFishes = json.decodeFromString<Map<String, EliteFish>>(dataFile.readText())
            eliteFishes.clear()
            eliteFishes.putAll(loadedFishes)
        } catch (e: Exception) {
            PluginMain.logger.error("读取精英鱼数据时发生错误", e)
        }
    }

    // 保存精英鱼数据
    private fun saveEliteFishes() {
        try {
            dataFile.writeText(json.encodeToString(eliteFishes))
        } catch (e: Exception) {
            PluginMain.logger.error("保存精英鱼数据时发生错误", e)
        }
    }

    // 生成精英鱼
    fun spawnEliteFish(pondName: String): Boolean {
        // 检查鱼塘是否已有精英鱼
        if (eliteFishes.containsKey(pondName)) {
            return false
        }

        val eliteFish = EliteFish(pondName)
        eliteFishes[pondName] = eliteFish
        saveEliteFishes()

        PluginMain.logger.info("在鱼塘 $pondName 炸出了精英鱼")
        return true
    }

    // 获取鱼塘的精英鱼
    fun getEliteFish(pondName: String): EliteFish? {
        return eliteFishes[pondName]
    }

    // 检查玩家是否可以参与精英鱼讨伐
    fun canPlayerParticipate(playerId: Long, pondName: String): Boolean {
        val playerData = PlayerDataManager.getPlayerData(playerId) ?: return false

        // 检查玩家是否在指定鱼塘中
        val playerRelation = FishingManager.loadPlayerPondRelation(playerId)
        if (playerRelation.currentPondName != pondName) {
            return false
        }

        // 检查加入鱼塘时间 - 从3天改为1天
        val joinTime = playerData.pondJoinTime ?: "2025-09-01T00:00:00" // 默认时间

        val joinDate = try {
            LocalDateTime.parse(joinTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            LocalDateTime.of(2025, 9, 1, 0, 0) // 解析失败使用默认时间
        }

        val currentDate = LocalDateTime.now()
        val daysBetween = ChronoUnit.DAYS.between(joinDate, currentDate)

        return daysBetween >= 1  // 改为1天
    }

    // 处理玩家攻击精英鱼
    fun handleAttack(playerId: Long, playerName: String, pondName: String, finalATK: Long, finalDEF: Long, finalLUCK: Long): String {
        val eliteFish = getEliteFish(pondName) ?: return "您的鱼塘目前没有精英鱼！"

        // 检查玩家是否可以参与
        if (!canPlayerParticipate(playerId, pondName)) {
            return "您加入鱼塘未满1天，无法参与精英鱼讨伐！"
        }

        // 检查攻击冷却
        val playerData = PlayerDataManager.getPlayerData(playerId)!!
        val currentTime = System.currentTimeMillis()
        val remainingTime = 60 * 60 * 1000 - (currentTime - playerData.lastEliteFishAttackTime)

        if (remainingTime > 0) {
            val minutes = remainingTime / 60000
            val seconds = (remainingTime % 60000) / 1000
            return "攻击冷却中，还需${minutes}分${seconds}秒"
        }

        // 计算伤害 - 添加0.67-1.33的RP值
        val randomFactor = Random.nextDouble(0.67, 1.33)
        val baseDamage = (finalATK + finalDEF) * finalLUCK  // 现在都是Long类型
        val damage = (baseDamage * randomFactor).toLong()

        // 记录攻击
        val attackKey = "$playerId-${System.currentTimeMillis()}"
        eliteFish.attackers[attackKey] = damage
        eliteFish.currentHp -= damage

        // 更新玩家攻击时间
        playerData.lastEliteFishAttackTime = currentTime
        PlayerDataManager.savePlayerData(playerData)

        // 保存精英鱼数据
        saveEliteFishes()

        val result = StringBuilder()
        result.append("${playerName} 对精英鱼造成了 $damage 点伤害！")

        // 添加人品信息
        result.append(" (RP: ${"%.2f".format(randomFactor)})")

        // 添加彩蛋消息（类似世界BOSS）
        when {
            randomFactor <= 0.71 -> result.append("\n究极炸刀！")
            randomFactor <= 0.76 -> result.append("\n超级炸刀！")
            randomFactor <= 0.81 -> result.append("\n炸刀！")
            randomFactor >= 1.28 -> result.append("\n究极刀神！")
            randomFactor >= 1.23 -> result.append("\n超级刀神！")
            randomFactor >= 1.18 -> result.append("\n刀神！")
            randomFactor <= 0.86 -> result.append("\n有点炸...")
            randomFactor >= 1.13 -> result.append("\n有点神！")
        }

        result.append("\n精英鱼剩余HP: ${eliteFish.currentHp}/${eliteFish.maxHp}")

        // 检查是否击败精英鱼
        if (eliteFish.currentHp <= 0) {
            // 获取攻击者数量
            val attackerIds = mutableSetOf<Long>()
            eliteFish.attackers.keys.forEach { attackKey ->
                val playerIdStr = attackKey.substringBefore("-")
                val playerId = playerIdStr.toLongOrNull()
                if (playerId != null) {
                    attackerIds.add(playerId)
                }
            }

            result.append("\n\n🎉 精英鱼已被击败！${attackerIds.size}名参与讨伐的玩家获得奖励：基础ATK+50, DEF+50, LUCK+5")
            distributeRewards(pondName)
            eliteFishes.remove(pondName)
            saveEliteFishes()
        }

        return result.toString()
    }

    // 分发奖励 - 只给攻击过精英鱼的玩家
    private fun distributeRewards(pondName: String) {
        val eliteFish = getEliteFish(pondName) ?: return

        // 从攻击记录中提取所有攻击过的玩家ID
        val attackerIds = mutableSetOf<Long>()

        eliteFish.attackers.keys.forEach { attackKey ->
            // 攻击键格式: "玩家ID-时间戳"
            val playerIdStr = attackKey.substringBefore("-")
            val playerId = playerIdStr.toLongOrNull()
            if (playerId != null) {
                attackerIds.add(playerId)
            }
        }

        PluginMain.logger.info("精英鱼被击败，参与者数量: ${attackerIds.size}")

        // 只给攻击过精英鱼的玩家发放奖励
        attackerIds.forEach { playerId ->
            val playerData = PlayerDataManager.getPlayerData(playerId)
            if (playerData != null && canPlayerParticipate(playerId, pondName)) {
                // 增加基础属性
                playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, 50, playerData.rebirthCount)
                playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, 50, playerData.rebirthCount)
                playerData.baseLUCK += 5

                PlayerDataManager.savePlayerData(playerData)
                PluginMain.logger.info("玩家 $playerId 获得精英鱼奖励（攻击者）")
            }
        }

        // 记录奖励发放情况
        val pond = FishingManager.loadFishPond(pondName)
        val totalMembers = pond?.members?.size ?: 0
        PluginMain.logger.info("精英鱼奖励发放完成：参与者${attackerIds.size}人获得奖励")
    }

    // 获取精英鱼信息
    fun getEliteFishInfo(pondName: String): String {
        val eliteFish = getEliteFish(pondName) ?: return "您的鱼塘目前没有精英鱼！还不快炸！"

        val progress = eliteFish.currentHp.toDouble() / eliteFish.maxHp.toDouble()
        val progressBar = getProgressBar(progress)

        // 计算攻击者数量
        val attackerIds = mutableSetOf<Long>()
        eliteFish.attackers.keys.forEach { attackKey ->
            val playerIdStr = attackKey.substringBefore("-")
            val playerId = playerIdStr.toLongOrNull()
            if (playerId != null) {
                attackerIds.add(playerId)
            }
        }

        return "🐟 精英鱼 🐟\n" +
            "HP: ${eliteFish.currentHp}/${eliteFish.maxHp}\n" +
            "进度: $progressBar\n" +
            "攻击次数: ${eliteFish.attackers.size}\n" +
            "参与玩家: ${attackerIds.size}人"
    }

    // 生成进度条
    private fun getProgressBar(progress: Double, length: Int = 10): String {
        val filled = (progress * length).toInt().coerceIn(0, length)
        val empty = length - filled
        return "[" + "■".repeat(filled) + "□".repeat(empty) + "] ${(progress * 100).toInt()}%"
    }
}