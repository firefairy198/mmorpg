// WorldBoss.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// 世界BOSS数据类
@Serializable
data class WorldBoss(
    var currentHp: Long = 600000, // 修改：初始血量从1000000改为600000
    var maxHp: Long = 600000,     // 修改：初始血量从1000000改为600000
    var level: Int = 1,
    var lastResetTime: String = getCurrentDateTime(), // 最后重置时间（改为记录完整时间）
    var attackers: MutableMap<String, Long> = mutableMapOf(), // 攻击者记录 (玩家ID-等级 -> 造成的伤害)
    var rewards: MutableMap<Long, Int> = mutableMapOf() // 奖励记录 (玩家ID -> 获得的奖励)
) {
    companion object {
        // 获取当前日期时间（使用明确时区）
        fun getCurrentDateTime(): String {
            return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }

        // 生成攻击记录键
        fun getAttackKey(playerId: Long, level: Int): String {
            return "$playerId-$level"
        }
    }
}

// 世界BOSS管理器
object WorldBossManager {
    private val dataFile by lazy {
        File(PluginMain.dataFolder, "world_boss.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                // 初始化文件
                val initialBoss = WorldBoss()
                writeText(json.encodeToString(initialBoss))
            }
        }
    }

    private val json = Json { prettyPrint = true }
    private var worldBoss: WorldBoss = WorldBoss() // 先初始化为默认值
    private val zoneId = ZoneId.of("Asia/Shanghai") // 明确指定时区

    init {
        // 在初始化块中加载BOSS数据
        worldBoss = loadWorldBoss()
    }

    // 加载世界BOSS数据
    private fun loadWorldBoss(): WorldBoss {
        return try {
            val boss = json.decodeFromString<WorldBoss>(dataFile.readText())
            // 检查是否需要重置
            checkAndResetBoss(boss)
            boss
        } catch (e: Exception) {
            PluginMain.logger.error("读取世界BOSS数据时发生错误", e)
            WorldBoss().also { checkAndResetBoss(it) }
        }
    }

    // 保存世界BOSS数据
    private fun saveWorldBoss() {
        try {
            dataFile.writeText(json.encodeToString(worldBoss))
        } catch (e: Exception) {
            PluginMain.logger.error("保存世界BOSS数据时发生错误", e)
        }
    }

    // 检查并重置BOSS - 修改为中午12点重置
    private fun checkAndResetBoss(boss: WorldBoss) {
        val currentDateTime = LocalDateTime.now(zoneId)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        // 解析最后重置时间
        val lastResetTime = try {
            LocalDateTime.parse(boss.lastResetTime, formatter)
        } catch (e: Exception) {
            // 如果解析失败，使用当前时间减去一天作为最后重置时间
            currentDateTime.minusDays(1)
        }

        // 获取今天和昨天的12点
        val todayNoon = LocalDate.now(zoneId).atTime(12, 0)
        val yesterdayNoon = LocalDate.now(zoneId).minusDays(1).atTime(12, 0)

        // 确定应该使用的重置时间点
        val resetTime = if (currentDateTime.isBefore(todayNoon)) {
            // 如果当前时间在今天12点之前，使用昨天12点作为重置时间点
            yesterdayNoon
        } else {
            // 如果当前时间在今天12点之后，使用今天12点作为重置时间点
            todayNoon
        }

        PluginMain.logger.info("检查BOSS重置: 当前时间=$currentDateTime, 最后重置时间=$lastResetTime, 重置时间点=$resetTime")

        // 如果最后重置时间早于重置时间点，则需要重置
        if (lastResetTime.isBefore(resetTime)) {
            boss.currentHp = 600000 // 修改：重置血量从1000000改为600000
            boss.maxHp = 600000     // 修改：重置血量从1000000改为600000
            boss.level = 1
            boss.lastResetTime = resetTime.format(formatter)
            boss.attackers.clear()
            boss.rewards.clear()
            saveWorldBoss()
            PluginMain.logger.info("世界BOSS已重置（中午12点重置）")
        }
    }

    // 获取当前日期时间（使用明确时区）
    private fun getCurrentDateTime(): String {
        return LocalDateTime.now(zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    // 获取BOSS信息
    fun getBossInfo(): String {
        // 每次获取信息时都检查重置
        checkAndResetBoss(worldBoss)

        val progressBar = getProgressBar(worldBoss.currentHp.toDouble() / worldBoss.maxHp.toDouble())
        return "世界BOSS LV.${worldBoss.level}\n" +
            "HP: ${worldBoss.currentHp}/${worldBoss.maxHp}\n" +
            "进度: $progressBar\n" +
            "参与人数: ${worldBoss.attackers.size}"
    }

    // 生成进度条
    private fun getProgressBar(progress: Double, length: Int = 10): String {
        val filled = (progress * length).toInt().coerceIn(0, length)
        val empty = length - filled
        return "[" + "■".repeat(filled) + "□".repeat(empty) + "] ${(progress * 100).toInt()}%"
    }

    // 处理玩家攻击
    fun handleAttack(playerId: Long, playerName: String, atk: Int, def: Int, luck: Int): String {
        // 检查重置
        checkAndResetBoss(worldBoss)

        // 检查玩家是否已经攻击过当前等级的BOSS
        val attackKey = WorldBoss.getAttackKey(playerId, worldBoss.level)
        if (worldBoss.attackers.containsKey(attackKey)) {
            return "你已经对当前等级的世界BOSS出过刀了！"
        }

        // 检查BOSS是否已被击败
        if (worldBoss.currentHp <= 0) {
            return "世界BOSS已被击败，请等待下次刷新！"
        }

        // 计算伤害
        val randomFactor = Random.nextDouble(0.66, 1.33)
        val damage = ((atk + def) * luck * randomFactor).toLong()

        // 记录伤害
        worldBoss.attackers[attackKey] = damage
        worldBoss.currentHp -= damage

        // 生成伤害消息
        val damageMessage = StringBuilder()
        damageMessage.append("${playerName} 对世界BOSS(LV.${worldBoss.level})造成了 $damage 点伤害！")

        // 添加彩蛋消息
        when {
            randomFactor <= 0.71 -> damageMessage.append("\n究极炸刀！")
            randomFactor <= 0.76 -> damageMessage.append("\n超级炸刀！")
            randomFactor <= 0.81 -> damageMessage.append("\n炸刀！")
            randomFactor >= 1.28 -> damageMessage.append("\n究极刀神！")
            randomFactor >= 1.23 -> damageMessage.append("\n超级刀神！")
            randomFactor >= 1.18 -> damageMessage.append("\n刀神！")
            randomFactor <= 0.86 -> damageMessage.append("\n有点炸...")
            randomFactor >= 1.13 -> damageMessage.append("\n有点神！")
        }

        // 检查BOSS是否被击败
        if (worldBoss.currentHp <= 0) {
            damageMessage.append("\n\n世界BOSS已被击败！奖励将在稍后发放...")
            // 启动异步任务发放奖励
            GlobalScope.launch {
                distributeRewards()
            }
        }

        // 保存BOSS状态
        saveWorldBoss()

        return damageMessage.toString()
    }

    // 分发奖励
    private suspend fun distributeRewards() {
        delay(1000) // 延迟1秒确保所有攻击记录已保存

        val totalDamage = worldBoss.attackers.values.sum()
        val rewardBase = worldBoss.level * 1000

        // 计算总奖励池
        val totalRewardPool = rewardBase * 6

        // 分批发放奖励，避免一次性处理过多数据
        val batchSize = 50

        // 构建奖励详情消息
        val rewardDetails = StringBuilder()
        rewardDetails.append("世界BOSS击败奖励详情:\n")
        rewardDetails.append("BOSS等级: ${worldBoss.level}\n")
        rewardDetails.append("总伤害: $totalDamage\n")
        rewardDetails.append("奖励池: $totalRewardPool 喵币\n")
        rewardDetails.append("参与玩家: ${worldBoss.attackers.size}人\n\n")

        // 处理所有攻击者
        worldBoss.attackers.forEach { (attackKey, damage) ->
            // 从attackKey中提取playerId
            val playerId = attackKey.substringBefore("-").toLong()

            val playerData = PlayerDataManager.getPlayerData(playerId)
            if (playerData != null) {
                // 计算奖励：基于伤害比例 + 参与奖励
                val damageRatio = damage.toDouble() / totalDamage
                val damageReward = (totalRewardPool * damageRatio).toInt()
                val participationReward = 388 // 参与奖励
                val totalReward = damageReward + participationReward

                playerData.gold += totalReward
                PlayerDataManager.savePlayerData(playerData)

                // 记录奖励
                worldBoss.rewards[playerId] = totalReward

                PluginMain.logger.info("玩家 $playerId 获得世界BOSS奖励: $totalReward 喵币")
            }
        }

        // 保存奖励记录
        saveWorldBoss()

        // 发送奖励详情到日志
        PluginMain.logger.info(rewardDetails.toString())

        // 提升BOSS等级和血量 - 修改：从1.2倍改为1.3倍
        worldBoss.level++
        worldBoss.maxHp = (worldBoss.maxHp * 1.3).toLong()
        worldBoss.currentHp = worldBoss.maxHp
        worldBoss.attackers.clear()
        worldBoss.rewards.clear()

        saveWorldBoss()
        PluginMain.logger.info("世界BOSS已升级至 LV.${worldBoss.level}, 新血量: ${worldBoss.maxHp}")
    }

    // 重置BOSS（管理员命令）
    fun resetBoss(): String {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        // 获取当前时间并确定重置时间点
        val currentDateTime = LocalDateTime.now(zoneId)
        val todayNoon = LocalDate.now(zoneId).atTime(12, 0)
        val resetTime = if (currentDateTime.isBefore(todayNoon)) {
            // 如果当前时间在今天12点之前，使用昨天12点作为重置时间点
            LocalDate.now(zoneId).minusDays(1).atTime(12, 0)
        } else {
            // 如果当前时间在今天12点之后，使用今天12点作为重置时间点
            todayNoon
        }

        worldBoss.currentHp = 600000 // 修改：重置血量从1000000改为600000
        worldBoss.maxHp = 600000     // 修改：重置血量从1000000改为600000
        worldBoss.level = 1
        worldBoss.lastResetTime = resetTime.format(formatter)
        worldBoss.attackers.clear()
        worldBoss.rewards.clear()
        saveWorldBoss()
        return "世界BOSS已重置！下次重置时间：中午12点"
    }

    // 添加获取玩家奖励信息的方法
    fun getPlayerRewardInfo(playerId: Long): String {
        val damage = worldBoss.attackers.values.firstOrNull() // 简化处理，只获取第一个伤害值
        val reward = worldBoss.rewards[playerId]

        return if (damage != null && reward != null) {
            "你对世界BOSS造成了 $damage 点伤害，获得 $reward 喵币奖励"
        } else if (damage != null) {
            "你对世界BOSS造成了 $damage 点伤害，奖励尚未发放"
        } else {
            "你今天还没有攻击世界BOSS"
        }
    }

    // 强制检查重置（用于调试）
    fun forceCheckReset(): String {
        checkAndResetBoss(worldBoss)
        return "已强制检查重置"
    }

    // 检查每日重置（用于定时任务）
    fun checkDailyReset() {
        checkAndResetBoss(worldBoss)
    }
}