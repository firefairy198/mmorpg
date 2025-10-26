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
import kotlin.math.min


// 伤害记录数据类
@Serializable
data class DamageRecord(
    val playerId: Long,
    val playerName: String,
    val damage: Long,
    val timestamp: String,
    val bossLevel: Int
)


// 世界BOSS数据类
@Serializable
data class WorldBoss(
    var currentHp: Long = 10000000,
    var maxHp: Long = 10000000,
    var level: Int = 1,
    var lastResetTime: String = getCurrentDateTime(), // 最后重置时间（改为记录完整时间）
    var attackers: MutableMap<String, Long> = mutableMapOf(), // 攻击者记录 (玩家ID-等级 -> 造成的伤害)
    var rewards: MutableMap<Long, Int> = mutableMapOf(), // 奖励记录 (玩家ID -> 获得的奖励)
    var damageRecords: MutableList<DamageRecord> = mutableListOf() // 新增：伤害记录
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
            // 获取TopPlayer记录来计算血量
            val topPlayer = TopPlayerManager.getRecord()
            val baseHp = if (topPlayer != null) {
                (topPlayer.finalATK + topPlayer.finalDEF) * topPlayer.finalLUCK * 13L
            } else {
                30000000L // 默认血量
            }

            boss.currentHp = baseHp
            boss.maxHp = baseHp
            boss.level = 1
            boss.lastResetTime = resetTime.format(formatter)
            boss.attackers.clear()
            boss.rewards.clear()
            saveWorldBoss()
            PluginMain.logger.info("世界BOSS已重置（中午12点重置），血量设置为: $baseHp")
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
        return "猪咪王 LV.${worldBoss.level}\n" +
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
    fun handleAttack(playerId: Long, playerName: String, atk: Long, def: Long, luck: Long): String {
        // 检查重置
        checkAndResetBoss(worldBoss)

        // 检查玩家是否已经攻击过当前等级的BOSS
        val attackKey = WorldBoss.getAttackKey(playerId, worldBoss.level)
        if (worldBoss.attackers.containsKey(attackKey)) {
            return "你已经对当前等级的猪咪王出过刀了！"
        }

        // 检查BOSS是否已被击败
        if (worldBoss.currentHp <= 0) {
            return "猪咪王已被击败，新的猪咪王已刷新！"
        }

        // 获取玩家数据以读取转生次数
        val playerData = PlayerDataManager.getPlayerData(playerId)
        val rebirthCount = playerData?.rebirthCount ?: 0
        val rebirthBonus = min(1 + 0.01 * rebirthCount,2.0)

        // 计算伤害 - 注意这里参数已经是 Long 类型
        val randomFactor = Random.nextDouble(0.66, 1.33)
        val damage = ((atk + def) * luck * randomFactor * rebirthBonus).toLong()

        // 记录伤害
        worldBoss.attackers[attackKey] = damage
        worldBoss.currentHp -= damage

        // 记录伤害记录
        val damageRecord = DamageRecord(
            playerId = playerId,
            playerName = playerName,
            damage = damage,
            timestamp = getCurrentDateTime(),
            bossLevel = worldBoss.level
        )
        worldBoss.damageRecords.add(damageRecord)

        // 只保留最新的100条记录，避免数据过大
        if (worldBoss.damageRecords.size > 100) {
            worldBoss.damageRecords = worldBoss.damageRecords.takeLast(100).toMutableList()
        }

        // 生成伤害消息
        val damageMessage = StringBuilder()
        damageMessage.append("${playerName} 对猪咪王(LV.${worldBoss.level})造成了 $damage 点伤害！")
        // 添加转生次数信息
        if (rebirthCount < 100) {
            damageMessage.append(" (转生${rebirthCount}次加成: +${rebirthCount}%)")
        } else {
            damageMessage.append(" (转生${rebirthCount}次加成: +100%)")
        }

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
            damageMessage.append("\n\n猪咪王已被击败！奖励将在稍后发放...")
            // 启动异步任务发放奖励
            GlobalScope.launch {
                distributeRewards()
            }
        }

        // 保存BOSS状态
        saveWorldBoss()

        return damageMessage.toString()
    }

    // 获取伤害排行榜
    fun getDamageRanking(limit: Int = 3): String {
        if (worldBoss.damageRecords.isEmpty()) {
            return "暂无伤害记录"
        }

        // 按伤害值排序，取前limit名
        val topRecords = worldBoss.damageRecords
            .sortedByDescending { it.damage }
            .take(limit)

        val rankingMessage = StringBuilder()
        rankingMessage.append("🔪刀神排行榜：\n")

        topRecords.forEachIndexed { index, record ->
            val rank = index + 1
            val dateTime = LocalDateTime.parse(record.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val formattedDate = dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

            rankingMessage.append("${rank}. ${record.playerName} - ${record.damage}点伤害 (LV.${record.bossLevel}, ${formattedDate})\n")
        }

        return rankingMessage.toString()
    }

    // 分发奖励
    private suspend fun distributeRewards() {
        delay(100) // 延迟0.1秒确保所有攻击记录已保存

        val totalDamage = worldBoss.attackers.values.sum()
        val rewardBase = worldBoss.level * 1000

        // 计算总奖励池
        val totalRewardPool = rewardBase * 5

        // 分批发放奖励，避免一次性处理过多数据
        val batchSize = 50

        // 构建奖励详情消息
        val rewardDetails = StringBuilder()
        rewardDetails.append("猪咪王击败奖励详情:\n")
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
                val participationReward = 1288 // 参与奖励
                val totalReward = damageReward + participationReward

                playerData.gold += totalReward
                PlayerDataManager.savePlayerData(playerData)

                // 记录奖励
                worldBoss.rewards[playerId] = totalReward

                PluginMain.logger.info("玩家 $playerId 获得猪咪王奖励: $totalReward 喵币")
            }
        }

        // 保存奖励记录
        saveWorldBoss()

        // 发送奖励详情到日志
        PluginMain.logger.info(rewardDetails.toString())

        // 提升BOSS等级和血量
        worldBoss.level++
        worldBoss.maxHp = (worldBoss.maxHp * 1.2).toLong()
        worldBoss.currentHp = worldBoss.maxHp
        worldBoss.attackers.clear()
        worldBoss.rewards.clear()

        saveWorldBoss()
        PluginMain.logger.info("猪咪王已升级至 LV.${worldBoss.level}, 新血量: ${worldBoss.maxHp}")
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

        // 获取TopPlayer记录来计算血量
        val topPlayer = TopPlayerManager.getRecord()
        val baseHp = if (topPlayer != null) {
            (topPlayer.finalATK + topPlayer.finalDEF) * topPlayer.finalLUCK * 10L
        } else {
            10000000L // 默认血量
        }

        worldBoss.currentHp = baseHp
        worldBoss.maxHp = baseHp
        worldBoss.level = 1
        worldBoss.lastResetTime = resetTime.format(formatter)
        worldBoss.attackers.clear()
        worldBoss.rewards.clear()
        saveWorldBoss()
        return "猪咪王已重置！血量: $baseHp，下次重置时间：中午12点"
    }

    // 添加获取玩家奖励信息的方法
    fun getPlayerRewardInfo(playerId: Long): String {
        val damage = worldBoss.attackers.values.firstOrNull() // 简化处理，只获取第一个伤害值
        val reward = worldBoss.rewards[playerId]

        return if (damage != null && reward != null) {
            "你对猪咪王造成了 $damage 点伤害，获得 $reward 喵币奖励"
        } else if (damage != null) {
            "你对猪咪王造成了 $damage 点伤害，奖励尚未发放"
        } else {
            "你今天还没有攻击猪咪王"
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