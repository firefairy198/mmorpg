// Fish.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlin.math.min

// 鱼类数据类
@Serializable
data class Fish(
    val name: String,
    val stars: Int,
    val value: Int
)

// 钓鱼结果数据类
data class FishingResult(
    val fish: Fish,
    val cookingMethod: String,
    val message: String
)

// 鱼塘数据类（按鱼塘名存储）
@Serializable
data class FishPond(
    val name: String,
    val managerId: Long, // 管理者ID
    val createdTime: String, // 创建时间
    var level: Int = 0,
    var totalInvestment: Long = 0,
    val contributors: MutableMap<Long, Int> = mutableMapOf(), // 玩家ID -> 投资金额
    val members: MutableSet<Long> = mutableSetOf() // 成员ID集合（包括管理者）
)

// 玩家鱼塘关系数据类
@Serializable
data class PlayerPondRelation(
    var currentPondName: String? = null, // 当前加入的鱼塘名
    val managedPonds: MutableSet<String> = mutableSetOf() // 管理的鱼塘名集合
)

// 钓鱼管理器
object FishingManager {
    private val fishPondsFile by lazy {
        File(PluginMain.dataFolder, "fish_ponds.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                // 初始化文件为空映射
                writeText(json.encodeToString(emptyMap<String, FishPond>()))
            }
        }
    }

    private val playerPondRelationsFile by lazy {
        File(PluginMain.dataFolder, "player_pond_relations.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                // 初始化文件为空映射
                writeText(json.encodeToString(emptyMap<Long, PlayerPondRelation>()))
            }
        }
    }

    private val json = Json { prettyPrint = true }

    // 鱼类定义
    private val oneStarFishes = listOf(
        Fish("小鱼", 1, 1),
        Fish("小虾", 1, 1)
    )

    private val twoStarFishes = listOf(
        Fish("鲈鱼", 2, 4),
        Fish("鲤鱼", 2, 4)
    )

    private val threeStarFishes = listOf(
        Fish("甜虾", 3, 9),
        Fish("三眼蟹", 3, 9),
        Fish("梭子蟹", 3, 9)
    )

    private val fourStarFishes = listOf(
        Fish("鳌虾", 4, 16),
        Fish("牡丹虾", 4, 16)
    )

    private val fiveStarFishes = listOf(
        Fish("龙虾", 5, 25),
        Fish("帝王蟹", 5, 25)
    )

    private val exFishes = listOf(
        Fish("三文鱼", 6, 1), // EX鱼，三项属性各+2
        Fish("蓝鳍金枪鱼", 7, 3) // EX鱼2，三项属性各+5
    )

    // 烹饪方法
    private val cookingMethods = listOf("清蒸", "红烧")

    // 加载所有鱼塘数据
    fun loadAllFishPonds(): Map<String, FishPond> {
        return try {
            json.decodeFromString<Map<String, FishPond>>(fishPondsFile.readText())
        } catch (e: Exception) {
            PluginMain.logger.error("读取鱼塘数据时发生错误", e)
            emptyMap()
        }
    }

    // 加载指定鱼塘数据
    fun loadFishPond(pondName: String): FishPond? {
        val allPonds = loadAllFishPonds()
        return allPonds[pondName]
    }

    // 保存鱼塘数据
    fun saveFishPond(pond: FishPond) {
        val allPonds = loadAllFishPonds().toMutableMap()
        allPonds[pond.name] = pond
        try {
            fishPondsFile.writeText(json.encodeToString(allPonds))
        } catch (e: Exception) {
            PluginMain.logger.error("保存鱼塘数据时发生错误", e)
        }
    }

    // 删除鱼塘
    fun deleteFishPond(pondName: String) {
        val allPonds = loadAllFishPonds().toMutableMap()
        allPonds.remove(pondName)
        try {
            fishPondsFile.writeText(json.encodeToString(allPonds))
        } catch (e: Exception) {
            PluginMain.logger.error("删除鱼塘数据时发生错误", e)
        }
    }

    // 加载玩家鱼塘关系数据
    fun loadPlayerPondRelation(playerId: Long): PlayerPondRelation {
        return try {
            val allRelations = json.decodeFromString<Map<Long, PlayerPondRelation>>(playerPondRelationsFile.readText())
            allRelations[playerId] ?: PlayerPondRelation()
        } catch (e: Exception) {
            PluginMain.logger.error("读取玩家鱼塘关系数据时发生错误", e)
            PlayerPondRelation()
        }
    }

    // 保存玩家鱼塘关系数据
    fun savePlayerPondRelation(playerId: Long, relation: PlayerPondRelation) {
        val allRelations = try {
            json.decodeFromString<MutableMap<Long, PlayerPondRelation>>(playerPondRelationsFile.readText()).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
        allRelations[playerId] = relation
        try {
            playerPondRelationsFile.writeText(json.encodeToString(allRelations))
        } catch (e: Exception) {
            PluginMain.logger.error("保存玩家鱼塘关系数据时发生错误", e)
        }
    }

    // 创建鱼塘
    fun createFishPond(pondName: String, managerId: Long): Boolean {
        val allPonds = loadAllFishPonds()

        // 检查鱼塘名是否已存在
        if (allPonds.containsKey(pondName)) {
            return false
        }

        // 创建新鱼塘
        val newPond = FishPond(
            name = pondName,
            managerId = managerId,
            createdTime = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        )

        // 添加管理者为成员
        newPond.members.add(managerId)

        // 保存鱼塘
        saveFishPond(newPond)

        // 更新玩家关系
        val playerRelation = loadPlayerPondRelation(managerId)
        playerRelation.currentPondName = pondName
        playerRelation.managedPonds.add(pondName)
        savePlayerPondRelation(managerId, playerRelation)

        // 记录管理者加入鱼塘的时间
        val playerData = PlayerDataManager.getPlayerData(managerId)
        if (playerData != null) {
            playerData.pondJoinTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            PlayerDataManager.savePlayerData(playerData)
        }

        return true
    }

    // 加入鱼塘
    fun joinFishPond(pondName: String, playerId: Long): Boolean {
        val pond = loadFishPond(pondName) ?: return false

        // 检查鱼塘是否已满
        if (pond.members.size >= 15) {
            return false
        }

        // 检查玩家是否已在鱼塘中
        if (pond.members.contains(playerId)) {
            return false
        }

        // 添加玩家到鱼塘
        pond.members.add(playerId)
        saveFishPond(pond)

        // 更新玩家关系
        val playerRelation = loadPlayerPondRelation(playerId)
        playerRelation.currentPondName = pondName
        savePlayerPondRelation(playerId, playerRelation)

        // 记录玩家加入鱼塘的时间
        val playerData = PlayerDataManager.getPlayerData(playerId)
        if (playerData != null) {
            playerData.pondJoinTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            PlayerDataManager.savePlayerData(playerData)
        }

        return true
    }

    // 离开鱼塘
    fun leaveFishPond(playerId: Long): Boolean {
        val playerRelation = loadPlayerPondRelation(playerId)
        val pondName = playerRelation.currentPondName ?: return false

        val pond = loadFishPond(pondName) ?: return false

        // 检查是否是管理者
        if (pond.managerId == playerId) {
            return false // 管理者不能直接离开，需要销毁鱼塘
        }

        // 从鱼塘中移除玩家
        pond.members.remove(playerId)
        saveFishPond(pond)

        // 更新玩家关系
        playerRelation.currentPondName = null
        savePlayerPondRelation(playerId, playerRelation)

        return true
    }

    // 销毁鱼塘
    fun destroyFishPond(pondName: String, managerId: Long): Boolean {
        val pond = loadFishPond(pondName) ?: return false

        // 检查是否是管理者
        if (pond.managerId != managerId) {
            return false
        }

        // 更新所有成员的关系
        pond.members.forEach { memberId ->
            val memberRelation = loadPlayerPondRelation(memberId)
            if (memberRelation.currentPondName == pondName) {
                memberRelation.currentPondName = null
            }
            memberRelation.managedPonds.remove(pondName)
            savePlayerPondRelation(memberId, memberRelation)
        }

        // 删除鱼塘
        deleteFishPond(pondName)

        return true
    }

    // 踢出玩家
    fun kickPlayerFromPond(pondName: String, managerId: Long, targetPlayerId: Long): Boolean {
        val pond = loadFishPond(pondName) ?: return false

        // 检查是否是管理者
        if (pond.managerId != managerId) {
            return false
        }

        // 检查目标玩家是否在鱼塘中
        if (!pond.members.contains(targetPlayerId)) {
            return false
        }

        // 不能踢出自己
        if (targetPlayerId == managerId) {
            return false
        }

        // 从鱼塘中移除玩家
        pond.members.remove(targetPlayerId)
        saveFishPond(pond)

        // 更新目标玩家的关系
        val targetRelation = loadPlayerPondRelation(targetPlayerId)
        if (targetRelation.currentPondName == pondName) {
            targetRelation.currentPondName = null
        }
        savePlayerPondRelation(targetPlayerId, targetRelation)

        return true
    }

    // 获取鱼塘等级调整后的概率
    private fun getAdjustedProbabilities(pondLevel: Int): Map<Int, Double> {
        val baseProbabilities = mapOf(
            1 to 0.26,  // 1星鱼
            2 to 0.25,  // 2星鱼
            3 to 0.30,  // 3星鱼
            4 to 0.15,  // 4星鱼
            5 to 0.037, // 5星鱼
            6 to 0.002, // EX鱼
            7 to 0.001  // EX鱼2
        )

        if (pondLevel == 0) return baseProbabilities

        return mapOf(
            1 to (baseProbabilities[1]!! - 0.02 * pondLevel - 0.004 * pondLevel).coerceAtLeast(0.0),
            2 to (baseProbabilities[2]!! - 0.015 * pondLevel - 0.002 * pondLevel).coerceAtLeast(0.0),
            3 to (baseProbabilities[3]!! + 0.02 * pondLevel),
            4 to (baseProbabilities[4]!! + 0.015 * pondLevel),
            5 to (baseProbabilities[5]!! + 0.003 * pondLevel),
            6 to (baseProbabilities[6]!! + 0.002 * pondLevel),
            7 to (baseProbabilities[7]!! + 0.001 * pondLevel)
        )
    }

    // 钓鱼功能
    fun goFishing(playerData: PlayerData, pondLevel: Int): FishingResult {
        val probabilities = getAdjustedProbabilities(pondLevel)

        // 根据概率随机选择鱼类星级
        val randomValue = Random.nextDouble()
        var cumulative = 0.0

        val selectedStar = probabilities.entries.find { (_, prob) ->
            cumulative += prob
            randomValue <= cumulative
        }?.key ?: 1 // 默认1星鱼

        // 根据星级选择具体的鱼
        val fish = when (selectedStar) {
            1 -> oneStarFishes.random()
            2 -> twoStarFishes.random()
            3 -> threeStarFishes.random()
            4 -> fourStarFishes.random()
            5 -> fiveStarFishes.random()
            6 -> exFishes[0] // 三文鱼
            7 -> exFishes[1] // 蓝鳍金枪鱼
            else -> oneStarFishes.random()
        }

        // 随机烹饪方法（EX鱼特殊处理）
        val cookingMethod = if (fish.stars >= 6) {
            "刺身" // EX鱼固定为刺身
        } else {
            cookingMethods.random()
        }

        // 生成完整的钓鱼过程消息
        val starSymbols = "※".repeat(fish.stars)
        val fishDisplayName = if (fish.stars >= 6) {
            "[$starSymbols]${cookingMethod}${fish.name}"
        } else {
            "[$starSymbols]${cookingMethod}${fish.name}"
        }

        // 构建完整的消息
        val message = buildString {
            append("你使用了鱼饵…\n")
            append("经过一顿操作…\n")
            append("获得了\n")
            append("$fishDisplayName")
        }

        return FishingResult(fish, cookingMethod, message)
    }

    fun checkEliteFishSpawn(pondName: String, forceSpawn: Boolean = false): Boolean {
        val pond = loadFishPond(pondName) ?: return false

        // 如果是强制生成，直接生成精英鱼
        if (forceSpawn) {
            PluginMain.logger.info("强制生成精英鱼在鱼塘 $pondName")
            return EliteFishManager.spawnEliteFish(pondName)
        }

        // 原有的概率计算逻辑保持不变
        val baseProbability = 0.1
        val levelBonus = pond.level * 0.02
        val totalProbability = baseProbability + levelBonus

        val randomValue = Random.nextDouble()
        if (randomValue < totalProbability) {
            PluginMain.logger.info("鱼塘 $pondName 等级 ${pond.level}，精英鱼生成概率: ${"%.1f".format(totalProbability * 100)}%，随机值: ${"%.1f".format(randomValue * 100)}%，生成精英鱼")
            return EliteFishManager.spawnEliteFish(pondName)
        } else {
            PluginMain.logger.info("鱼塘 $pondName 等级 ${pond.level}，精英鱼生成概率: ${"%.1f".format(totalProbability * 100)}%，随机值: ${"%.1f".format(randomValue * 100)}%，未生成")
        }
        return false
    }

    // 计算升级所需喵币
    fun getUpgradeCost(currentLevel: Int): Int {
        return (currentLevel + 1) * 50000
    }

    // 获取鱼塘信息
    fun getFishPondInfo(playerId: Long): String {
        val playerRelation = loadPlayerPondRelation(playerId)
        val pondName = playerRelation.currentPondName ?: return "您还没有加入任何鱼塘！"

        val pond = loadFishPond(pondName) ?: return "鱼塘不存在！"

        val nextLevelCost = if (pond.level < 10) getUpgradeCost(pond.level) else 0
        val probabilities = getAdjustedProbabilities(pond.level)
        val remainingForNextLevel = if (pond.level < 10) nextLevelCost - pond.totalInvestment else 0
        val progressPercent = if (pond.level < 10 && nextLevelCost > 0) {
            (pond.totalInvestment.toDouble() / nextLevelCost.toDouble() * 100).coerceAtMost(100.0)
        } else {
            100.0
        }

        val infoBuilder = StringBuilder()
        infoBuilder.append("""🐟 鱼塘信息: ${pond.name} 🐟
创建者: 玩家${pond.managerId}
创建时间: ${pond.createdTime}
等级: ${pond.level}级  |  $remainingForNextLevel 喵币
累计投资: ${pond.totalInvestment}喵币
成员数量: ${pond.members.size}/15人""")

        if (pond.level < 10) {
            infoBuilder.append("")
        } else {
            infoBuilder.append("\n已达成最高等级")
        }

        // 如果是管理者，显示成员列表
        if (pond.managerId == playerId) {
            infoBuilder.append("\n\n👥 成员列表:")
            pond.members.forEachIndexed { index, memberId ->
                val role = if (memberId == pond.managerId) " (管理者)" else ""
                infoBuilder.append("\n${index + 1}. 玩家$memberId$role")
            }
        }

        // 添加玩家个人投资信息
        val playerContribution = pond.contributors[playerId] ?: 0
        infoBuilder.append("\n\n个人投资信息:")
        infoBuilder.append("\n在本鱼塘投资: ${playerContribution}喵币")

        infoBuilder.append("\n\n当前概率调整:")
        infoBuilder.append("\n1星鱼: ${"%.1f".format(probabilities[1]!! * 100)}%")
        infoBuilder.append("\n2星鱼: ${"%.1f".format(probabilities[2]!! * 100)}%")
        infoBuilder.append("\n3星鱼: ${"%.1f".format(probabilities[3]!! * 100)}%")
        infoBuilder.append("\n4星鱼: ${"%.1f".format(probabilities[4]!! * 100)}%")
        infoBuilder.append("\n5星鱼: ${"%.1f".format(probabilities[5]!! * 100)}%")
        infoBuilder.append("\nEX鱼: ${"%.1f".format(probabilities[6]!! * 100)}%")
        infoBuilder.append("\nEX鱼2: ${"%.1f".format(probabilities[7]!! * 100)}%")

        infoBuilder.append("\n\n精英鱼讨伐:")
        val hasEliteFish = EliteFishManager.getEliteFish(pondName) != null
        if (hasEliteFish) {
            infoBuilder.append("\n当前有精英鱼出现！使用'/查看精英鱼'和'/鱼塘出刀'参与讨伐")
        } else {
            infoBuilder.append("\n当前无精英鱼")
        }

        // 添加生成概率信息
        val baseProbability = 10
        val levelBonus = pond.level * 2
        val totalProbability = baseProbability + levelBonus
        infoBuilder.append("\n炸鱼器生成概率: ${totalProbability}% (基础${baseProbability}% + 等级${pond.level}级加成${levelBonus}%)")

        return infoBuilder.toString()
    }

    // 修改修建鱼塘的方法
    fun upgradeFishPond(playerData: PlayerData, pondName: String, amount: Int): UpgradeResult {
        val pond = loadFishPond(pondName) ?: return UpgradeResult(false, 0, 0, "鱼塘不存在！")

        // 检查玩家是否在鱼塘中
        if (!pond.members.contains(playerData.qqId)) {
            return UpgradeResult(false, 0, 0, "您不是该鱼塘的成员！")
        }

        // 检查鱼塘等级上限
        if (pond.level >= 10) {
            return UpgradeResult(false, 0, 0, "鱼塘已经达到最高等级（10级）！")
        }

        // 检查玩家喵币是否足够
        if (playerData.gold < amount) {
            return UpgradeResult(false, 0, 0, "喵币不足！需要${amount}喵币，你只有${playerData.gold}喵币")
        }

        // 更新玩家数据
        playerData.gold -= amount

        // 更新鱼塘数据
        pond.totalInvestment += amount
        pond.contributors[playerData.qqId] = (pond.contributors[playerData.qqId] ?: 0) + amount

        // 检查是否可以升级
        val upgradeCost = getUpgradeCost(pond.level)
        val canUpgrade = pond.totalInvestment >= upgradeCost

        // 如果达到升级条件，升级鱼塘
        var levelUpMessage = ""
        if (canUpgrade) {
            val oldLevel = pond.level
            pond.level += 1
            levelUpMessage = "\n🎉 鱼塘等级提升！从${oldLevel}级升至${pond.level}级！"

            // 检查是否可以连续升级（处理超额投资）
            while (pond.level < 10 && pond.totalInvestment >= getUpgradeCost(pond.level)) {
                pond.level += 1
            }

            if (pond.level > oldLevel + 1) {
                levelUpMessage = "\n🎉 鱼塘连续升级！从${oldLevel}级直接升至${pond.level}级！"
            }
        }

        // 保存鱼塘数据
        saveFishPond(pond)

        // 计算下一级所需金额
        val nextUpgradeCost = if (pond.level < 10) {
            getUpgradeCost(pond.level)
        } else {
            0
        }

        // 计算距离下一级还差多少
        val remainingForNextLevel = if (pond.level < 10) {
            nextUpgradeCost - pond.totalInvestment
        } else {
            0
        }

        val message = if (pond.level < 10) {
            "投资成功！本次投资${amount}喵币。$levelUpMessage\n" +
                "当前等级：${pond.level}级，累计投资：${pond.totalInvestment}喵币\n" +
                "下一级(${pond.level + 1}级)需要：$nextUpgradeCost 喵币，还需：$remainingForNextLevel 喵币"
        } else {
            "投资成功！本次投资${amount}喵币。$levelUpMessage\n" +
                "鱼塘已达到最高等级(10级)，累计投资：${pond.totalInvestment}喵币"
        }

        return UpgradeResult(true, amount, 0, message)
    }
}

// 升级结果数据类
data class UpgradeResult(
    val success: Boolean,
    val invested: Int,
    val refund: Int,
    val message: String
)