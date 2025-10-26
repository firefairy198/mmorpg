//PluginMain
package org.example.mmorpg

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.message.data.content
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import net.mamoe.mirai.message.data.buildMessageChain
import kotlinx.coroutines.*
import net.mamoe.mirai.message.data.At
import java.time.LocalDateTime
import java.time.temporal.TemporalQueries.zoneId
import kotlin.math.min
import kotlin.math.max
import kotlin.time.Duration
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User



// 数字格式化函数
fun formatDifficulty(difficulty: Long): String {
    val thousand = 1_000L
    val million = 1_000_000L
    val billion = 1_000_000_000L
    val trillion = 1_000_000_000_000L
    val quadrillion = 1_000_000_000_000_000L
    val quintillion = 1_000_000_000_000_000_000L

    return when {
        difficulty < thousand -> "$difficulty"
        difficulty < 10 * thousand -> "${"%.1f".format(difficulty / thousand.toDouble())}K"
        difficulty < million -> "${difficulty / thousand}K"
        difficulty < 10 * million -> "${"%.1f".format(difficulty / million.toDouble())}M"
        difficulty < billion -> "${difficulty / million}M"
        difficulty < 10 * billion -> "${"%.1f".format(difficulty / billion.toDouble())}B"
        difficulty < trillion -> "${difficulty / billion}B"
        difficulty < 10 * trillion -> "${"%.1f".format(difficulty / trillion.toDouble())}T"
        difficulty < quadrillion -> "${difficulty / trillion}T"
        difficulty < 10 * quadrillion -> "${"%.1f".format(difficulty / quadrillion.toDouble())}Q"
        difficulty < quintillion -> "${difficulty / quadrillion}Q"
        difficulty < 10 * quintillion -> "${"%.1f".format(difficulty / quintillion.toDouble())}Qt"
        else -> "${difficulty / quintillion}Qt"
    }
}

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.example.mmorpg",
        name = "卧槽真来副本吗",
        version = "0.1.0"
    ) {
        author("firefairy198")
        info(
            """
            别搞笑了！
        """.trimIndent()
        )
    }
)
{
    object RebirthConfirmation {
        private val pendingRebirth: MutableMap<Long, Pair<PlayerData, Pet>> = mutableMapOf()
        private val pendingTimes: MutableMap<Long, Long> = mutableMapOf() // 记录请求时间
        private const val TIMEOUT_MS = 2 * 60 * 1000 // 5分钟超时

        fun addPendingRebirth(userId: Long, playerData: PlayerData, newPet: Pet) {
            pendingRebirth[userId] = Pair(playerData, newPet)
            pendingTimes[userId] = System.currentTimeMillis()
        }

        fun removePendingRebirth(userId: Long) {
            pendingRebirth.remove(userId)
            pendingTimes.remove(userId)
        }

        fun getPendingRebirth(userId: Long): Pair<PlayerData, Pet>? {
            val requestTime = pendingTimes[userId] ?: return null

            // 检查是否超时
            if (System.currentTimeMillis() - requestTime > TIMEOUT_MS) {
                removePendingRebirth(userId)
                return null
            }

            return pendingRebirth[userId]
        }

        // 添加定时清理超时请求的方法
        fun cleanupExpiredRequests() {
            val currentTime = System.currentTimeMillis()
            val expiredIds = pendingTimes.filter { currentTime - it.value > TIMEOUT_MS }.keys

            expiredIds.forEach { userId ->
                removePendingRebirth(userId)
            }
        }
    }

    // 管理员ID
    private val adminId = 335693890L

    // 周末狂欢检测函数
    private fun isWeekendBonus(): Boolean {
        val today = LocalDate.now()
        return today.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            today.dayOfWeek == java.time.DayOfWeek.SUNDAY
    }

    // 获取周末狂欢消息的函数
    private fun getWeekendBonusMessage(): String {
        return if (isWeekendBonus()) {
            "🎉周末狂欢：所有奖励翻倍🎉\n"
        } else {
            ""
        }
    }

    // 检查玩家是否可以获得特定装备
    private fun canPlayerGetEquipment(playerData: PlayerData, equipment: Equipment): Boolean {
        val currentEquipment = playerData.equipment

        // 如果没有装备，可以获取任何装备
        if (currentEquipment == null) {
            return true
        }

        // 获取装备等级
        fun getEquipmentLevel(name: String): Int {
            return when {
                name.contains("[LR]") -> 5
                name.contains("[MR]") -> 4
                name.contains("[UR]") -> 3
                name.contains("[SSR]") -> 2
                name.contains("[SR]") -> 1
                else -> 0
            }
        }

        // 检查装备等级限制
        val currentLevel = getEquipmentLevel(currentEquipment.name)
        val newLevel = getEquipmentLevel(equipment.name)

        return newLevel > currentLevel
    }

    // 获取装备等级
    private fun getEquipmentLevel(equipmentName: String): Int {
        return when {
            equipmentName.contains("[LR]") -> 5
            equipmentName.contains("[MR]") -> 4
            equipmentName.contains("[UR]") -> 3
            equipmentName.contains("[SSR]") -> 2
            equipmentName.contains("[SR]") -> 1
            else -> 0
        }
    }

    private fun generateBCDPetName(): String {
        return when (Random.nextInt(0, 3)) {
            0 -> listOf("豹", "狼", "熊").random()
            1 -> listOf("猫", "狗", "兔").random()
            else -> listOf("猪", "鼠", "鸭").random()
        }
    }

    private fun containsCaptchaDigitsInOrder(digits: String, captcha: String): Boolean {
        // 如果验证码本身就是数字序列的子串，直接返回true
        if (digits.contains(captcha)) {
            return true
        }

        // 检查验证码中的数字是否按顺序出现在数字序列中（不要求连续）
        var captchaIndex = 0
        for (digit in digits) {
            if (captchaIndex < captcha.length && digit == captcha[captchaIndex]) {
                captchaIndex++
            }
            if (captchaIndex == captcha.length) {
                return true
            }
        }
        return false
    }

    private fun calculateNecklaceBonuses(playerData: PlayerData): Triple<Int, Int, Int> {
        val necklace = playerData.luckyNecklace ?: return Triple(0, 0, 0)

        val atkBonus = necklace.attributes
            .filter { it.type == NecklaceAttributeType.ATK }
            .sumOf { it.value }

        val defBonus = necklace.attributes
            .filter { it.type == NecklaceAttributeType.DEF }
            .sumOf { it.value }

        val luckBonus = necklace.attributes
            .filter { it.type == NecklaceAttributeType.LUCK }
            .sumOf { it.value }

        return Triple(atkBonus, defBonus, luckBonus)
    }

    private fun formatTeamPower(power: Double): String {
        return when {
            power >= 1_000_000_000 -> "${"%.2f".format(power / 1_000_000_000)}B" // 十亿
            power >= 1_000_000 -> "${"%.2f".format(power / 1_000_000)}M" // 百万
            power >= 1_000 -> "${"%.2f".format(power / 1_000)}K" // 千
            else -> "%.0f".format(power)
        }
    }

    // 优化副本显示
    private fun getFilteredDungeonRecommendations(teamPower: Double): String {
        val dungeons = DungeonManager.dungeons
        val recommendations = mutableListOf<String>()
        // 计算每个副本的成功率
        val successRates = dungeons.map { dungeon ->
            val successRate = (teamPower / dungeon.difficulty.toDouble()).coerceAtMost(1.0)
            Pair(dungeon, successRate)
        }
        // 找到第一个成功率不是100%的副本
        val firstNonPerfectIndex = successRates.indexOfFirst { it.second < 1.0 }
        // 确定显示的起始索引：从第一个非100%副本的前一个开始（如果存在），至少显示两个
        val startIndex = if (firstNonPerfectIndex != -1) {
            max(0, firstNonPerfectIndex - 1)
        } else {
            // 如果所有副本都是100%，则只显示最后两个
            max(0, dungeons.size - 2)
        }
        // 构建推荐信息
        for (i in startIndex until dungeons.size) {
            val (dungeon, successRate) = successRates[i]
            val formattedRate = "%.2f".format(successRate * 100)
            recommendations.add("副本${dungeon.id}: ${dungeon.name} - 难度${formatDifficulty(dungeon.difficulty)} - 成功率${formattedRate}%")
        }

        return recommendations.joinToString("\n")
    }

    private suspend fun sendTextPlayerInfo(
        group: Group,
        playerName: String,
        playerData: PlayerData,
        finalATK: Long,
        finalDEF: Long,
        finalLUCK: Long
    ) {
        val maxAttribute = 225 + 10 * playerData.rebirthCount

        val equipmentInfo = playerData.equipment?.let {
            val enhancedAtk = it.getEnhancedAtk()
            val enhancedDef = it.getEnhancedDef()
            val enhancedLuck = it.getEnhancedLuck()

            "装备: ${it.getDisplayName()} (ATK+${enhancedAtk}${if (it.enhanceLevel > 0) "(${it.atk}+${enhancedAtk - it.atk})" else ""}, " +
                "DEF+${enhancedDef}${if (it.enhanceLevel > 0) "(${it.def}+${enhancedDef - it.def})" else ""}, " +
                "LUCK+${enhancedLuck}${if (it.enhanceLevel > 0) "(${it.luck}+${enhancedLuck - it.luck})" else ""})"
        } ?: "装备: 无"

        val petInfo = playerData.pet?.let { pet ->
            val devouredPetsInfo = if (playerData.devouredPets.isNotEmpty()) {
                val petsList = playerData.devouredPets.entries.joinToString("") { (name, count) ->
                    if (count > 1) "$name($count)" else name
                }
                "\n吞噬: $petsList（ATK=${playerData.devouredATK}，DEF=${playerData.devouredDEF}，LUCK=${playerData.devouredLUCK}）"
            } else {
                ""
            }

            "宠物: ${formatPetInfo(pet)}" + devouredPetsInfo
        } ?: "宠物: 无"

        val relicInfo = playerData.relic?.let {
            val atkWithBonus = it.atk + playerData.relicAtkBonus
            val defWithBonus = it.def + playerData.relicDefBonus
            val luckWithBonus = it.luck + playerData.relicLuckBonus

            "遗物: ${it.name} (${it.grade}级, ATK+${it.atk}(+${playerData.relicAtkBonus}), DEF+${it.def}(+${playerData.relicDefBonus}), LUCK+${it.luck}(+${playerData.relicLuckBonus}))"
        } ?: "遗物: 无"

        val miraclePillInfo = if (playerData.miraclePillCount > 0) {
            "神奇小药丸: ${playerData.miraclePillCount}个\n"
        } else {
            "神奇小药丸: 无"
        }

        val rebirthInfo = if (playerData.rebirthCount > 0) {
            "转生次数: ${playerData.rebirthCount}"
        } else {
            ""
        }

        val sPetChangeTicketInfo = if (playerData.sPetChangeTickets > 0) {
            "S型宠物辅助职业变更券: ${playerData.sPetChangeTickets}个"
        } else {
            "S型宠物辅助职业变更券: 无"
        }

        val necklaceInfo = playerData.luckyNecklace?.let { necklace ->
            val attributesText = necklace.attributes.joinToString("，") { attr ->
                when (attr.type) {
                    NecklaceAttributeType.ATK -> "ATK+${attr.value}"
                    NecklaceAttributeType.DEF -> "DEF+${attr.value}"
                    NecklaceAttributeType.LUCK -> "LUCK+${attr.value}"
                    NecklaceAttributeType.POW -> "POW+${attr.displayValue}"
                }
            }
            "项链: ${necklace.getName()} ($attributesText)"
        } ?: "项链: 未开启"

        val message = buildString {
            append("${playerName} 的信息:\n")
            append("ATK: $finalATK (基础: ${playerData.baseATK} / $maxAttribute)\n")
            append("DEF: $finalDEF (基础: ${playerData.baseDEF} / $maxAttribute)\n")
            append("LUCK: $finalLUCK\n\n")
            append("喵币: ${playerData.gold}\n")
            append("汪币: ${playerData.wangCoin}\n")
            append("$equipmentInfo\n")
            append("$petInfo\n")
            append("$relicInfo\n")
            append("$necklaceInfo\n")
            if (rebirthInfo.isNotEmpty()) {
                append("$rebirthInfo\n\n")
            } else {
                append("\n")
            }

            append("$miraclePillInfo\n")
            append("$sPetChangeTicketInfo\n")
            append("彩笔: 红×${playerData.redPenCount} 蓝×${playerData.bluePenCount} 黄×${playerData.yellowPenCount} 黑×${playerData.blackPenCount}")
        }

        group.sendMessage(message)
    }

    object PetEffectCalculator {
        // 计算队伍中所有宠物的效果加成，包括虚拟队员的职业效果
        fun calculateTeamEffects(team: Team): TeamPetEffects {
            val effects = TeamPetEffects()
            var hasNoPetMember = false

            team.members.forEach { member ->
                if (member.isVirtual) {
                    // 处理虚拟队员的职业效果
                    when (member.virtualClass) {
                        "牧师S" -> effects.positiveEventChance += 0.25
                        "诗人S" -> effects.bonusDungeonChance = 0.06  // 注意：诗人S是固定0.06，不是累加
                        "盗贼S" -> {
                            effects.rewardMultiplier += 0.1
                            effects.equipmentDropChance += 0.05
                        }
                        "猎手S" -> {
                            effects.equipmentDropChance += 0.14
                            effects.additionalEvents += 1
                        }
                    }
                } else {
                    // 处理真实玩家的宠物效果
                    val playerData = PlayerDataManager.getPlayerData(member.playerId)

                    // 检查是否有队员没有宠物
                    if (playerData?.pet == null) {
                        hasNoPetMember = true
                    }

                    playerData?.pet?.specialEffect?.let { effect ->
                        when (effect) {
                            PetEffect.WARRIOR -> effects.atkMultiplier += 0.34
                            PetEffect.WARRIOR_S -> effects.atkMultiplier += 0.5
                            PetEffect.ARCHER -> effects.luckMultiplier += 0.34
                            PetEffect.ARCHER_S -> effects.luckMultiplier += 0.5
                            PetEffect.THIEF -> {
                                effects.rewardMultiplier += 0.05
                                effects.equipmentDropChance += 0.05
                            }
                            PetEffect.THIEF_S -> {
                                effects.rewardMultiplier += 0.1
                                effects.equipmentDropChance += 0.05
                            }
                            PetEffect.PRIEST -> effects.positiveEventChance += 0.15
                            PetEffect.PRIEST_S -> effects.positiveEventChance += 0.25
                            PetEffect.TREASURE_HUNTER -> effects.equipmentDropChance += 0.14
                            PetEffect.TREASURE_HUNTER_S -> {
                                effects.equipmentDropChance += 0.14
                                effects.additionalEvents += 1
                            }
                            PetEffect.BARD -> effects.bonusDungeonChance += 0.01
                            PetEffect.BARD_S -> {
                                effects.bonusDungeonChance = 0.06
                            }
                        }
                    }
                }
            }

            if (hasNoPetMember) {
                effects.bonusDungeonChance += 0.05
            }

            return effects
        }
    }

    // 队伍宠物效果数据类
    data class TeamPetEffects(
        var atkMultiplier: Double = 0.0,
        var luckMultiplier: Double = 0.0,
        var rewardMultiplier: Double = 0.0,
        var positiveEventChance: Double = 0.0,
        var equipmentDropChance: Double = 0.0,
        var bonusDungeonChance: Double = 0.0,
        var additionalEvents: Int = 0
    )

    override fun onEnable() {
        logger.info("PK插件加载成功！")

        // 确保数据文件夹存在
        dataFolder.mkdirs()

        // 启动定时任务检查队伍超时
        this.launch {
            while (isActive) {
                delay(30 * 1000)
                TeamManager.checkExpiredTeams()
                // 清理超时的转生和遗物重置确认请求
                RebirthConfirmation.cleanupExpiredRequests()
                RelicConfirmation.cleanupExpiredRequests()
                NecklaceReforgeConfirmation.cleanupExpiredRequests()
                WorldBossManager.checkDailyReset()
            }
        }

        // 初始化世界BOSS
        WorldBossManager.getBossInfo()

        WhitelistConfig.reload()
        logger.info("已启用的群: ${WhitelistConfig.enabledGroups}")

        globalEventChannel().subscribeAlways<GroupMessageEvent> { event ->
            val message = event.message.content.trim()
            val sender = event.sender
            val group = event.group
            val senderId = sender.id

            // 白名单检查
            if (!isGroupEnabled(group.id)) {
                // 只响应管理员命令，即使群不在白名单中
                if (message.startsWith("/启用群 ") && senderId == adminId) {
                    val groupId = message.substringAfter("/启用群 ").trim().toLongOrNull()
                    if (groupId == null) {
                        group.sendMessage("请输入有效的群号")
                    } else {
                        WhitelistConfig.enabledGroups.add(groupId)
                        WhitelistConfig.save()
                        group.sendMessage("已启用群 $groupId")
                    }
                }
                return@subscribeAlways
            }

            // 获取玩家数据，如果不存在则为null
            var playerData = PlayerDataManager.getPlayerData(senderId)

            when {

                message.startsWith("/兑换码 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val code = message.substringAfter("/兑换码 ").trim().lowercase() // 转换为小写

                    // 检查兑换码有效性
                    when (code) {
                        "dalaodaidaiwo" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("dalaodaidaiwo")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("dalaodaidaiwo")

                                // 增加属性
                                playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, 20, playerData.rebirthCount)
                                playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, 20, playerData.rebirthCount)

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得20双属！")
                            }
                        }
                        "tuntuntun" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("tuntuntun")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("tuntuntun")

                                // 增加吞噬属性
                                playerData.devouredATK += 15
                                playerData.devouredDEF += 15
                                playerData.devouredLUCK += 1

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！吞噬属性增加：ATK+15, DEF+15, LUCK+1")
                            }
                        }
                        "geiwodianmiaobi" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("geiwodianmiaobi")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("geiwodianmiaobi")

                                // 增加喵币
                                playerData.gold += 500

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得500喵币！")
                            }
                        }
                        "100people7days" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("100people7days")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("100people7days")

                                // 增加属性
                                playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, 100, playerData.rebirthCount)
                                playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, 100, playerData.rebirthCount)

                                // 增加喵币
                                playerData.gold += 7777

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得7777喵币和100全属！")
                            }
                        }
                        "chongwuchongzhi" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("chongwuchongzhi")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("chongwuchongzhi")

                                // 增加属性
                                playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, 50, playerData.rebirthCount)
                                playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, 50, playerData.rebirthCount)

                                // 增加喵币
                                playerData.gold += 1999

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得1999喵币和50全属！")
                            }
                        }
                        "huanduguoqing" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("huanduguoqing")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("huanduguoqing")

                                // 增加基础LUCK属性
                                playerData.baseLUCK += 10

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得10点基础LUCK！")
                            }
                        }
                        "zhouyueqing" -> {
                            // 检查是否已使用过此兑换码
                            if (playerData.usedCodes.contains("zhouyueqing")) {
                                group.sendMessage("您已经使用过这个兑换码了。")
                            } else {
                                // 添加兑换码到已使用列表
                                playerData.usedCodes.add("zhouyueqing")

                                // 增加30个神奇小药丸
                                playerData.miraclePillCount += 13

                                // 保存数据
                                PlayerDataManager.savePlayerData(playerData)

                                group.sendMessage("兑换成功！获得13个神奇小药丸！")
                            }
                        }
                        // 在这里添加更多兑换码
                        else -> {
                            group.sendMessage("无效的兑换码。")
                        }
                    }
                }

                message == "/神人堂" -> {
                    val hallOfGods = HallOfGodsManager.getFormattedHallOfGods()
                    group.sendMessage(hallOfGods)
                }

                message == "/榜一大哥" -> {
                    val topPlayer = TopPlayerManager.getFormattedTopPlayer()
                    group.sendMessage(topPlayer)
                }

                message == "/我的信息" || message == "/wdxx" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 创建不可变引用
                    val currentPlayerData = playerData
                    val playerName = sender.nameCardOrNick

                    val necklaceATK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0

                    val necklaceDEF = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.DEF }
                        ?.sumOf { it.value } ?: 0

                    val necklaceLUCK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    // 计算最终属性
                    val finalATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 添加项链ATK加成

                    val finalDEF = (currentPlayerData.baseDEF +
                        (currentPlayerData.equipment?.getEnhancedDef() ?: 0) +
                        ((currentPlayerData.pet?.def ?: 0) + currentPlayerData.devouredDEF) +
                        (currentPlayerData.relic?.def ?: 0) +
                        currentPlayerData.relicDefBonus +
                        necklaceDEF).toLong()  // 添加项链DEF加成

                    val finalLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()  // 添加项链LUCK加成

                    // 检查属性上限警告
                    val maxAttribute = 225 + 10 * currentPlayerData.rebirthCount
                    val showWarning = currentPlayerData.baseATK + 20 >= maxAttribute ||
                        currentPlayerData.baseDEF + 20 >= maxAttribute

                    // 根据 messageBack 设置决定使用图片还是文字
                    when (currentPlayerData.messageBack) {
                        1 -> {
                            // 使用图片消息
                            launch {
                                try {
                                    val imageMessage = PictureGenerator.generatePlayerInfoCard(
                                        playerName,
                                        currentPlayerData,
                                        finalATK,
                                        finalDEF,
                                        finalLUCK,
                                        group
                                    )
                                    group.sendMessage(imageMessage)

                                    if (showWarning) {
                                        group.sendMessage("Warn：你的基础属性即将达到上限，记得转生")
                                    }

                                } catch (e: Exception) {
                                    PluginMain.logger.error("生成玩家信息图片失败", e)
                                    // 图片生成失败时回退到文字消息
                                    sendTextPlayerInfo(group, playerName, currentPlayerData, finalATK, finalDEF, finalLUCK)
                                }
                            }
                        }
                        else -> {
                            // 使用文字消息 (包括 null 和 0)
                            sendTextPlayerInfo(group, playerName, currentPlayerData, finalATK, finalDEF, finalLUCK)
                        }
                    }
                }

                message == "/签到" -> {
                    // 如果玩家未注册，则创建新玩家数据
                    if (playerData == null) {
                        playerData = PlayerDataManager.createPlayerData(senderId)
                        group.sendMessage("注册成功！欢迎加入！其他指令请使用 /帮助 查看！")
                    }

                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

                    if (playerData.lastSignDate == today) {
                        group.sendMessage("你今天已经签到过了！试试 /组队 下副本吧。")
                    } else {
                        val goldGained = Random.nextInt(20, 51)
                        playerData.gold += goldGained
                        playerData.lastSignDate = today
                        PlayerDataManager.savePlayerData(playerData)

                        group.sendMessage("签到成功！获得${goldGained}喵币，当前喵币：${playerData.gold}")
                    }
                }

                message == "/商店" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val shopList = Shop.equipmentList.joinToString("\n") {
                        Shop.getEquipmentDescription(it)
                    }

                    group.sendMessage("装备商店:\n$shopList\n使用\"/购买 装备名\"来购买装备")
                }

                message == "/道具商店" || message == "/djsd" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val itemList = Shop.itemList.joinToString("\n") { item ->
                        val itemNumber = when (item.name) {
                            "S型宠物辅助职业变更券" -> "1"
                            "鱼饵" -> "2"
                            "炸鱼器" -> "3"
                            "高级炸鱼器" -> "4"
                            else -> ""
                        }
                        "${itemNumber}. ${item.name} - 价格: ${item.price}喵币 (${item.description})"
                    }

                    group.sendMessage("道具商店:\n$itemList\n使用\"/购买道具 数字\"或\"/购买道具 道具名\"来购买道具\n例如: /购买道具 1 或 /购买道具 S型宠物辅助职业变更券")
                }

                // 在 PluginMain.kt 中的转生命令处理部分，修改为：
                message == "/转生" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    if (RebirthConfirmation.getPendingRebirth(senderId) != null) {
                        group.sendMessage("您已经有一个转生请求等待确认，请先回复\"是\"或\"否\"。")
                        return@subscribeAlways
                    }

                    // 检查是否达到转生条件
                    if (playerData.baseATK < 200 || playerData.baseDEF < 200) {
                        group.sendMessage("转生需要基础ATK和DEF都达到200以上！")
                        return@subscribeAlways
                    }

                    // 检查是否使用神奇小药丸
                    val useMiraclePills = playerData.miraclePillCount >= 1
                    var miraclePillMessage = ""

                    if (useMiraclePills) {
                        // 消耗1个神奇小药丸
                        playerData.miraclePillCount -= 1
                        miraclePillMessage = " (消耗1个神奇小药丸，宠物LUCK固定为10)"
                    }

                    // 执行转生减属性（无论是否确认更换宠物，属性都会扣除）
                    playerData.baseATK = (playerData.baseATK - 150).coerceAtLeast(10)
                    playerData.baseDEF = (playerData.baseDEF - 150).coerceAtLeast(10)
                    playerData.rebirthCount++
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成随机宠物，如果使用了神奇小药丸则固定LUCK为10
                    val newPet = if (useMiraclePills) {
                        val atk = Random.nextInt(20, 51) // 10-50
                        val def = Random.nextInt(20, 51) // 10-50
                        val luck = 10 // 固定为10

                        // 计算宠物等级
                        val grade = when {
                            atk + def + 5*luck >= 145 -> "SS"
                            atk + def + 5*luck >= 135 -> "S"
                            atk + def + 5*luck >= 120 -> "A"
                            atk + def + 5*luck >= 100 -> "B"
                            atk + def + 5*luck >= 75 -> "C"
                            else -> "D"
                        }

                        // 生成宠物名称
                        val name = when (grade) {
                            "SS" -> listOf("玄武", "朱雀", "白虎", "青龙").random()
                            "S" -> listOf("腾蛇", "麒麟", "朱鹤", "椒图").random()
                            "A" -> listOf("雪狸", "赢鱼", "霸下").random()
                            "B" -> listOf("豹", "狼", "熊").random()
                            "C" -> listOf("猫", "狗", "兔").random()
                            else -> listOf("猪", "鼠", "鸭").random()
                        }

                        // 随机分配特殊效果
                        val allEffects = PetEffect.values()
                        val specialEffect = allEffects.random()

                        Pet(name, atk, def, luck, grade, specialEffect)
                    } else {
                        generateRandomPet() // 使用原有的随机生成逻辑
                    }

                    // 如果有旧宠物，询问是否更换
                    if (playerData.pet != null) {
                        RebirthConfirmation.addPendingRebirth(senderId, playerData, newPet)
                        group.sendMessage("${sender.nameCardOrNick}，你已满足转生条件！$miraclePillMessage\n" +
                            "转生后已减少150点ATK和DEF，并获得一只新宠物：\n" +
                            "${formatPetInfo(newPet)}\n" +
                            "你当前已拥有宠物：\n" +
                            "${formatPetInfo(playerData.pet!!)}\n" +
                            "是否更换宠物？回复\"是\"更换，回复\"否\"保留原宠物（2分钟内有效）")
                    } else {
                        // 没有宠物，直接设置
                        playerData.pet = newPet
                        PlayerDataManager.savePlayerData(playerData)

                        group.sendMessage("转生成功！当前转生次数：${playerData.rebirthCount}$miraclePillMessage")
                    }
                }

                message.startsWith("/超级转生") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 解析次数参数
                    val parts = message.substringAfter("/超级转生").trim().split(" ")
                    val times = when {
                        parts.isEmpty() || parts[0].isEmpty() -> 1  // 默认1次
                        else -> parts[0].toIntOrNull() ?: 1  // 尝试转换为数字，失败则默认1次
                    }

                    // 检查次数是否在合理范围内
                    if (times <= 0) {
                        group.sendMessage("转生次数必须大于0！")
                        return@subscribeAlways
                    }

                    if (times > 100) {
                        group.sendMessage("一次最多只能进行100次超级转生！")
                        return@subscribeAlways
                    }

                    // 计算总属性需求
                    val totalATKNeeded = 750 * times
                    val totalDEFNeeded = 750 * times

                    // 检查是否达到超级转生条件
                    if (playerData.baseATK < totalATKNeeded || playerData.baseDEF < totalATKNeeded) {
                        group.sendMessage("属性不足！${times}次超级转生需要基础ATK和DEF都达到${totalATKNeeded}以上！")
                        return@subscribeAlways
                    }

                    // 检查属性是否足够扣除
                    if (playerData.baseATK - totalATKNeeded < 10 || playerData.baseDEF - totalDEFNeeded < 10) {
                        group.sendMessage("属性不足！执行超级转生后属性至少为10点")
                        return@subscribeAlways
                    }

                    // 执行超级转生
                    playerData.baseATK = (playerData.baseATK - totalATKNeeded).coerceAtLeast(10)
                    playerData.baseDEF = (playerData.baseDEF - totalDEFNeeded).coerceAtLeast(10)
                    playerData.rebirthCount += 5 * times  // 转生次数+5*次数

                    // 增加吞噬属性
                    playerData.devouredATK += 15 * times
                    playerData.devouredDEF += 15 * times
                    playerData.devouredLUCK += 1 * times

                    // 新增：随机生成5*times个BCD等级宠物名称并添加到吞噬记录
                    val devouredPetsList = mutableListOf<String>()
                    repeat(5 * times) {
                        val petName = generateBCDPetName()
                        devouredPetsList.add(petName)
                        playerData.devouredPets[petName] = playerData.devouredPets.getOrDefault(petName, 0) + 1
                    }

                    // 保存玩家数据
                    PlayerDataManager.savePlayerData(playerData)

                    // 构建吞噬宠物列表的显示字符串（如果太多就显示部分）
                    val devouredPetsDisplay = if (devouredPetsList.size <= 10) {
                        devouredPetsList.joinToString("、")
                    } else {
                        val displayed = devouredPetsList.take(10)
                        "${displayed.joinToString("、")}...等${devouredPetsList.size}个宠物"
                    }

                    group.sendMessage("🌟 ${times}次超级转生成功！\n" +
                        "• 消耗${totalATKNeeded}点ATK和${totalDEFNeeded}点DEF\n" +
                        "• 转生次数增加${5 * times}次（当前：${playerData.rebirthCount}次）\n" +
                        "• 吞噬属性增加：ATK+${15 * times}, DEF+${15 * times}, LUCK+${1 * times}\n" +
                        "• 吞噬宠物增加：$devouredPetsDisplay")
                }

                // 添加对确认消息的处理
                message == "是" || message == "否" -> {
                    // 先检查是否有待处理的项链重铸确认
                    val pendingNecklaceReforge = NecklaceReforgeConfirmation.getPendingReforge(senderId)
                    if (pendingNecklaceReforge != null) {
                        val (playerData, oldNecklace, newNecklace) = pendingNecklaceReforge

                        if (message == "是") {
                            // 应用新项链
                            LuckyNecklaceManager.applyReforge(playerData, newNecklace)
                            group.sendMessage("幸运项链重铸成功！新项链信息：\n${newNecklace.getFormattedInfo()}")
                        } else {
                            // 保留原项链，汪币已扣除且不返还
                            group.sendMessage("已取消项链重铸，对应汪币已扣除")
                        }

                        PlayerDataManager.savePlayerData(playerData)
                        NecklaceReforgeConfirmation.removePendingReforge(senderId)
                        return@subscribeAlways
                    }
                    // 先检查是否有待处理的遗物重置确认
                    val pendingRelicReset = RelicConfirmation.getPendingReset(senderId)
                    if (pendingRelicReset != null) {
                        val (playerData, newRelic, resetType) = pendingRelicReset

                        if (message == "是") {
                            // 替换遗物
                            playerData.relic = newRelic
                            group.sendMessage("遗物属性已更新：\n${RelicGenerator.formatRelicInfo(newRelic)}")
                        } else {
                            // 保留原遗物，资源已扣除且不返还
                            group.sendMessage("已取消遗物重置，${if (resetType == "gold") "2500喵币" else "50点ATK和DEF"}已扣除")
                        }

                        PlayerDataManager.savePlayerData(playerData)
                        RelicConfirmation.removePendingReset(senderId)
                        return@subscribeAlways
                    }

                    // 检查是否有待处理的转生确认
                    val pendingRebirth = RebirthConfirmation.getPendingRebirth(senderId)
                    RebirthConfirmation.removePendingRebirth(senderId) // 立即移除
                    if (pendingRebirth != null) {
                        val (playerData, newPet) = pendingRebirth
                        val currentPlayerData = playerData // 创建不可变引用

                        if (message == "是") {

                            // 更换宠物，吞噬原宠物
                            val oldPet = currentPlayerData.pet!!
                            val devouredATK = (oldPet.atk * 0.1).toInt()
                            val devouredDEF = (oldPet.def * 0.1).toInt()
                            val devouredLUCK = (oldPet.luck * 0.1).toInt()

                            // 更新吞噬属性
                            currentPlayerData.devouredATK += devouredATK
                            currentPlayerData.devouredDEF += devouredDEF
                            currentPlayerData.devouredLUCK += devouredLUCK

                            // 更新吞噬宠物记录
                            val petName = oldPet.name
                            currentPlayerData.devouredPets[petName] = currentPlayerData.devouredPets.getOrDefault(petName, 0) + 1

                            // 更换为新宠物
                            currentPlayerData.pet = newPet
                            group.sendMessage("已更换为新宠物，并吞噬了原宠物${petName}（ATK+$devouredATK, DEF+$devouredDEF, LUCK+$devouredLUCK）")
                        } else {

                            // 保留原宠物，吞噬新宠物
                            val devouredATK = (newPet.atk * 0.1).toInt()
                            val devouredDEF = (newPet.def * 0.1).toInt()
                            val devouredLUCK = (newPet.luck * 0.1).toInt()

                            // 更新吞噬属性
                            currentPlayerData.devouredATK += devouredATK
                            currentPlayerData.devouredDEF += devouredDEF
                            currentPlayerData.devouredLUCK += devouredLUCK

                            // 更新吞噬宠物记录
                            val petName = newPet.name
                            currentPlayerData.devouredPets[petName] = currentPlayerData.devouredPets.getOrDefault(petName, 0) + 1

                            group.sendMessage("已保留原宠物，并吞噬了新宠物${petName}（ATK+$devouredATK, DEF+$devouredDEF, LUCK+$devouredLUCK）")
                        }

                        PlayerDataManager.savePlayerData(currentPlayerData)
                        RebirthConfirmation.removePendingRebirth(senderId)

                        group.sendMessage("转生成功！当前转生次数：${currentPlayerData.rebirthCount}")
                    }
                }

                message == "/帮助" -> {

                    val isAdmin = sender.id == 335693890L // 替换为你的QQ号

                    val helpMessage = buildMessageChain {
                        if (isAdmin) {
                            append("👑 管理员命令 👑\n")
                            append("• /启用群 [群号] - 启用指定群的PK功能\n")
                            append("• /禁用群 [群号] - 禁用指定群的PK功能\n")
                            append("• /查看启用群 - 查看所有启用的群\n\n")
                        }
                        +"⚔️ 帮助菜单 ⚔️\n\n"
                        +"• /我的信息(/wdxx) - 查看自己的属性、上传个人信息至榜一大哥\n"
                        +"• /商店 - 查看商店中出售的装备\n"
                        +"• /道具商店(/djsd) - 查看道具商店中出售的道具\n"
                        +"• /使用 道具名称 - 使用道具\n"
                        +"• /世界BOSS(/wb) - 查看世界BOSS信息\n"
                        +"• /转生 - 基础属性≥200可转生，获得宠物，基本属性-150\n"
                        +"• /获取遗物 - 消耗5次转生次数获取遗物\n"
                        +"• /喵币(属性)重置遗物 - 花费2500喵币(50双属性)重置遗物属性\n"
                        +"• /使用(赠送)红(蓝黄黑)彩笔 - 使用(赠送)一根对应颜色的彩笔为遗物染色\n"
                        +"• /合成黑彩笔 - 合成一根对应颜色的彩笔\n"
                        +"• /组队(/zd;/加入(/jr);/离开队伍(/lkdw)) - 创建/加入副本队伍（15min冷却）\n\n"
                        +"• /更新日志 查看最新版本的更新内容\n"
                        +"• /兑换码 [兑换码内容] - 领取新手奖励，[兑换码内容]见在线文档"
                        +"• 全部指令详见群公告 → 在线文档 → 第三分页"
                    }
                    group.sendMessage(helpMessage)
                }

                message.startsWith("/启用群 ") -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val groupId = message.substringAfter("/启用群 ").trim().toLongOrNull()
                    if (groupId == null) {
                        group.sendMessage("请输入有效的群号")
                    } else {
                        WhitelistConfig.enabledGroups.add(groupId)
                        WhitelistConfig.save()
                        group.sendMessage("已启用群 $groupId")
                    }
                }

                message.startsWith("/禁用群 ") -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val groupId = message.substringAfter("/禁用群 ").trim().toLongOrNull()
                    if (groupId == null) {
                        group.sendMessage("请输入有效的群号")
                    } else {
                        WhitelistConfig.enabledGroups.remove(groupId)
                        WhitelistConfig.save()
                        group.sendMessage("已禁用群 $groupId")
                    }
                }

                message == "/查看启用群" -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val groups = WhitelistConfig.enabledGroups.joinToString(", ")
                    group.sendMessage("已启用的群: $groups")
                }

                // 在命令处理中添加调试命令
                message == "/调试信息" -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val debugInfo = """
                        当前群ID: ${group.id}
                        白名单状态: ${if (isGroupEnabled(group.id)) "已启用" else "未启用"}
                        已启用的群: ${WhitelistConfig.enabledGroups.joinToString(", ")}
                        配置文件路径: ${WhitelistConfig.save()}
                    """.trimIndent()

                    group.sendMessage(debugInfo)
                }


                message == "/组队" || message == "/zd" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }
                    // 新增：检查玩家是否已经通关
                    if (playerData.hasClearedGame) {
                        group.sendMessage("🎊 恭喜你已通关游戏！作为肝帝，你已不屑再创建队伍挑战副本。")
                        return@subscribeAlways
                    }

                    if (playerData.dailyDungeonCount >= 40) {
                        group.sendMessage("今日副本次数已达上限，无法创建队伍！")
                        return@subscribeAlways
                    }

                    // 修改：动态CD检查（根据今日副本次数）
                    val currentTime = System.currentTimeMillis()
                    val cdMinutes = min(playerData.dailyDungeonCount + 5, 20)
                    val cdTime = cdMinutes * 60 * 1000  // 转换为毫秒
                    val remainingTime = cdTime - (currentTime - playerData.lastDungeonTime)

                    if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒（今日已进入${playerData.dailyDungeonCount}次副本）")
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性（包含项链加成）
                    val currentPlayerData = playerData

                    // 计算项链加成
                    val necklaceATK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0
                    val necklaceLUCK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    val finalATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 包含项链ATK加成

                    val finalLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()  // 包含项链LUCK加成

                    // 获取宠物信息
                    val petInfo = if (playerData.pet != null) {
                        val effectName = when (playerData.pet!!.specialEffect) {
                            PetEffect.WARRIOR -> "战士"
                            PetEffect.WARRIOR_S -> "战士S"
                            PetEffect.ARCHER -> "弓手"
                            PetEffect.ARCHER_S -> "弓手S"
                            PetEffect.THIEF -> "盗贼"
                            PetEffect.THIEF_S -> "盗贼S"
                            PetEffect.PRIEST -> "牧师"
                            PetEffect.PRIEST_S -> "牧师S"
                            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
                            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                            PetEffect.BARD -> "吟游诗人"
                            PetEffect.BARD_S -> "吟游诗人S"
                            else -> ""
                        }
                        if (effectName.isNotEmpty()) {
                            " [$effectName]"
                        } else {
                            " "
                        }
                    } else {
                        "[新人]"
                    }

                    // 创建队伍
                    val (createSuccess, captcha) = TeamManager.createTeam(senderId, group.id, sender.nameCardOrNick, finalATK, finalLUCK)
                    if (createSuccess) {
                        // 显示玩家今日副本次数信息
                        val countInfo = if (playerData.dailyDungeonCount >= 41) {
                            " (奖励已达上限)"
                        } else {
                            " (${playerData.dailyDungeonCount}/40)"
                        }

                        // 添加周末狂欢提示
                        val weekendBonusMessage = getWeekendBonusMessage()


                        group.sendMessage("${petInfo}${sender.nameCardOrNick}${countInfo}创建了队伍，等待队员加入（5分钟有效）。\n" +
                            "验证码：$captcha\n" +
                            "使用'/加入(jr) [验证码]'加入队伍。使用'/自动补全'命令加入人机。")
                    } else {
                        group.sendMessage("创建队伍失败，可能该群已经有一个队伍了。")
                    }
                }

                // PluginMain.kt - 在 "/自动补全" 命令处理部分添加时间限制

                message == "/自动补全" || message == "/zdbq" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 获取当前群的队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        group.sendMessage("当前群没有队伍，请先使用\"/组队\"创建队伍。")
                        return@subscribeAlways
                    }

                    // 检查是否是队长
                    if (team.captainId != senderId) {
                        group.sendMessage("只有队长可以使用自动补全功能！")
                        return@subscribeAlways
                    }

                    // 新增：检查队长今日副本次数是否超过20次
                    if (playerData.dailyDungeonCount > 20) {
                        group.sendMessage("今日副本次数已超过20次，无法使用自动补全功能！")
                        return@subscribeAlways
                    }

                    // 检查队伍是否已满
                    if (team.members.size >= 4) {
                        group.sendMessage("队伍已经满员，无需补全！")
                        return@subscribeAlways
                    }

                    // 获取榜一大哥的属性
                    val topPlayerRecord = TopPlayerManager.getRecord()
                    if (topPlayerRecord == null) {
                        group.sendMessage("暂无榜一大哥记录，无法使用自动补全功能！")
                        return@subscribeAlways
                    }

                    // 计算队长当前最终属性
                    val currentPlayerData = playerData

                    // 计算项链加成
                    val necklaceATK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0
                    val necklaceLUCK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    val captainATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 包含项链ATK加成

                    val captainLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()  // 包含项链LUCK加成

                    // 使用榜一大哥的最终属性
                    val topPlayerATK = topPlayerRecord.finalATK.toLong()
                    val topPlayerLUCK = topPlayerRecord.finalLUCK.toLong()

                    // 执行自动填补
                    val success = TeamManager.autoFillTeam(team.groupId, senderId, topPlayerATK, topPlayerLUCK, captainATK, captainLUCK)

                    if (success) {
                        val virtualCount = TeamManager.getVirtualMembersCount(team)
                        val memberNames = team.members.joinToString("，") { it.playerName }

                        // 计算虚拟队员的实际属性
                        val virtualATK = min((topPlayerATK * 0.75).toLong(), captainATK)
                        val virtualLUCK = min((topPlayerLUCK * 0.75).toLong(), captainLUCK)

                        group.sendMessage("🎮 自动补全成功！\n" +
                            "当前队伍成员：$memberNames\n" +
                            "虚拟队员职业为随机的[牧师S, 诗人S, 盗贼S, 猎手S]")

                        // 如果队伍已满，显示队伍战斗力信息（类似满员时的提示）
                        if (team.members.size == 4) {
                            // 计算队伍总战力和成功率
                            val totalATK = team.members.sumOf { it.atk }
                            val totalLUCK = team.members.sumOf { it.luck }
                            val totalPowBonus = LuckyNecklaceManager.calculateTeamPowBonus(team)
                            val teamPower = totalATK * (0.5 + totalPowBonus) * totalLUCK
                            // 构建副本推荐信息
                            val dungeonRecommendations = getFilteredDungeonRecommendations(teamPower)

                            // 获取队伍宠物效果列表（包括虚拟队员的职业）
                            val petEffects = mutableListOf<String>()
                            team.members.forEach { member ->
                                if (member.isVirtual) {
                                    // 虚拟队员使用其职业作为效果，但显示时隐藏为"??
                                    petEffects.add("??")
                                } else {
                                    val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                    memberData?.pet?.specialEffect?.let { effect ->
                                        val effectName = when (effect) {
                                            PetEffect.WARRIOR -> "战士"
                                            PetEffect.WARRIOR_S -> "战士S"
                                            PetEffect.ARCHER -> "弓手"
                                            PetEffect.ARCHER_S -> "弓手S"
                                            PetEffect.THIEF -> "盗贼"
                                            PetEffect.THIEF_S -> "盗贼S"
                                            PetEffect.PRIEST -> "牧师"
                                            PetEffect.PRIEST_S -> "牧师S"
                                            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
                                            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                                            PetEffect.BARD -> "吟游诗人"
                                            PetEffect.BARD_S -> "吟游诗人S"
                                            else -> "未知"
                                        }
                                        petEffects.add(effectName)
                                    }
                                }
                            }

                            val petEffectsStr = if (petEffects.isNotEmpty()) {
                                "队伍效果: ${petEffects.joinToString(",")}"
                            } else {
                                "队伍效果: 无"
                            }

                            group.sendMessage("队伍已满！队伍总ATK: ${formatDifficulty(totalATK)}, 总LUCK: ${formatDifficulty(totalLUCK)}, 综合战力: ${formatTeamPower(teamPower)}\n" +
                                "请使用\"/选择副本(/xzfb) [1-9]\"命令选择副本。\n" +
                                "$petEffectsStr\n" +
                                "(概率未计算宠物效果)\n$dungeonRecommendations")
                        }
                    } else {
                        group.sendMessage("自动补全失败，请稍后再试！")
                    }
                }

                // PluginMain.kt - 加入命令处理部分
                message.startsWith("/加入 ") || message.startsWith("/jr ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    if (playerData.dailyDungeonCount >= 40) {
                        group.sendMessage("今日副本次数已达上限（40次），无法加入队伍！")
                        return@subscribeAlways
                    }

                    // 解析验证码 - 同时处理两种命令格式
                    val inputCaptcha = if (message.startsWith("/加入 ")) {
                        message.substringAfter("/加入 ").trim()
                    } else {
                        message.substringAfter("/jr ").trim()
                    }

                    // 检查当前群是否有队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        return@subscribeAlways
                    }

                    // 验证验证码
                    if (!TeamManager.verifyCaptcha(group.id, inputCaptcha)) {
                        // 验证码错误，不予响应（不发送任何消息）
                        return@subscribeAlways
                    }

                    // 修改：动态CD检查（根据今日副本次数）
                    val currentTime = System.currentTimeMillis()
                    val cdMinutes = min(playerData.dailyDungeonCount + 4, 20)
                    val cdTime = cdMinutes * 60 * 1000
                    val remainingTime = cdTime - (currentTime - playerData.lastDungeonTime)

                    if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒（今日已进入${playerData.dailyDungeonCount}次副本）")
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    val randomDelay = (100L..2000L).random() // 100-2000毫秒的随机延迟
                    delay(randomDelay)

                    // 计算玩家最终属性
                    val currentPlayerData = playerData
                    val necklaceATK = playerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0

                    val necklaceLUCK = playerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    val finalATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 添加项链ATK加成

                    val finalLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()  // 添加项链LUCK加成

                    // 加入队伍
                    if (TeamManager.addMember(group.id, senderId, sender.nameCardOrNick, finalATK, finalLUCK)) {
                        val updatedTeam = TeamManager.getTeamByGroup(group.id)!!

                        // 显示玩家今日副本次数信息
                        val countInfo = if (playerData.dailyDungeonCount >= 41) {
                            " (奖励次数已达上限)"
                        } else {
                            " (${playerData.dailyDungeonCount}/40)"
                        }

                        // 获取宠物信息
                        val petInfo = if (playerData.pet != null) {
                            val effectName = when (playerData.pet!!.specialEffect) {
                                PetEffect.WARRIOR -> "战士"
                                PetEffect.WARRIOR_S -> "战士S"
                                PetEffect.ARCHER -> "弓手"
                                PetEffect.ARCHER_S -> "弓手S"
                                PetEffect.THIEF -> "盗贼"
                                PetEffect.THIEF_S -> "盗贼S"
                                PetEffect.PRIEST -> "牧师"
                                PetEffect.PRIEST_S -> "牧师S"
                                PetEffect.TREASURE_HUNTER -> "宝藏猎手"
                                PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                                PetEffect.BARD -> "吟游诗人"
                                PetEffect.BARD_S -> "吟游诗人S"
                                else -> ""
                            }
                            if (effectName.isNotEmpty()) {
                                " [${effectName}]"
                            } else {
                                ""
                            }
                        } else {
                            " [萌新]"
                        }

                        group.sendMessage("${petInfo}${sender.nameCardOrNick}${countInfo} 加入了队伍。当前队伍人数：${updatedTeam.members.size}/4")

                        // 如果队伍已满，计算并显示队伍战斗力
                        if (updatedTeam.members.size == 4) {
                            // 计算队伍总战力和成功率
                            val totalATK = updatedTeam.members.sumOf { it.atk }
                            val totalLUCK = updatedTeam.members.sumOf { it.luck }
                            val totalPowBonus = LuckyNecklaceManager.calculateTeamPowBonus(team)
                            val teamPower = totalATK * (0.5 + totalPowBonus) * totalLUCK

                            // 构建副本推荐信息
                            val dungeonRecommendations = getFilteredDungeonRecommendations(teamPower)
                            // 获取队伍宠物效果列表（包括虚拟队员的职业）
                            val petEffects = mutableListOf<String>()
                            updatedTeam.members.forEach { member ->
                                if (member.isVirtual) {
                                    // 虚拟队员使用其职业作为效果
                                    petEffects.add("??")
                                } else {
                                    val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                    memberData?.pet?.specialEffect?.let { effect ->
                                        val effectName = when (effect) {
                                            PetEffect.WARRIOR -> "战士"
                                            PetEffect.WARRIOR_S -> "战士S"
                                            PetEffect.ARCHER -> "弓手"
                                            PetEffect.ARCHER_S -> "弓手S"
                                            PetEffect.THIEF -> "盗贼"
                                            PetEffect.THIEF_S -> "盗贼S"
                                            PetEffect.PRIEST -> "牧师"
                                            PetEffect.PRIEST_S -> "牧师S"
                                            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
                                            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                                            PetEffect.BARD -> "吟游诗人"
                                            PetEffect.BARD_S -> "吟游诗人S"
                                            else -> "未知"
                                        }
                                        petEffects.add(effectName)
                                    }
                                }
                            }

                            val petEffectsStr = if (petEffects.isNotEmpty()) {
                                "宠物: ${petEffects.joinToString(",")}"
                            } else {
                                "宠物: 无"
                            }
                            // 在队伍已满时@队长并发送消息
                            val captainId = team.captainId
                            val captainAt = At(captainId) // 创建@队长的消息组件

                            val message = captainAt +
                                " 队伍已满！队伍总ATK: ${formatDifficulty(totalATK)}, 总LUCK: ${formatDifficulty(totalLUCK)}, 综合战力: ${formatTeamPower(teamPower)}\n" +
                                "请使用\"/选择副本(/xzfb) [1-9]\"命令选择副本。\n" +
                                "$petEffectsStr\n" +
                                "(概率未计算宠物效果)\n$dungeonRecommendations"

                            group.sendMessage(message)
                        }
                    } else {
                        group.sendMessage("加入队伍失败，可能队伍已满或你已在其他队伍中。")
                    }
                }

                message == "/开启幸运项链" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val result = LuckyNecklaceManager.activateNecklace(playerData)
                    if (result.first) {
                        PlayerDataManager.savePlayerData(playerData)
                        group.sendMessage(result.second)
                    } else {
                        group.sendMessage(result.second)
                    }
                }

                message.startsWith("/选择副本 ") || message.startsWith("/xzfb ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 获取当前群的队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        group.sendMessage("当前群没有队伍，请先使用\"/组队\"创建队伍。")
                        return@subscribeAlways
                    }

                    // 检查是否是队长
                    if (team.captainId != senderId) {
                        group.sendMessage("只有队长可以选择副本。")
                        return@subscribeAlways
                    }

                    // 检查队伍是否已满
                    if (team.members.size < 4) {
                        group.sendMessage("队伍还未满，请等待4人满员后再选择副本。")
                        return@subscribeAlways
                    }

                    // 获取当前时间 - 在这里定义 currentTime
                    val currentTime = System.currentTimeMillis()

                    // 检查队长副本CD（动态CD）
                    val captainCdMinutes = min(playerData.dailyDungeonCount + 5, 20)
                    val captainCdTime = captainCdMinutes * 60 * 1000  // 转换为毫秒
                    val captainRemainingTime = captainCdTime - (currentTime - playerData.lastDungeonTime)

                    if (captainRemainingTime > 0) {
                        val minutes = captainRemainingTime / 60000
                        val seconds = (captainRemainingTime % 60000) / 1000
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒（今日已进入${playerData.dailyDungeonCount}次副本）")
                        return@subscribeAlways
                    }

                    // 检查所有队员的副本CD - 现在 currentTime 在这个作用域内可用
                    val membersWithCooldown = mutableListOf<String>()
                    team.members.forEach { member ->
                        // 跳过虚拟队员的CD检查
                        if (member.isVirtual) {
                            return@forEach
                        }

                        val memberData = PlayerDataManager.getPlayerData(member.playerId)
                        if (memberData != null) {
                            if (memberData.dailyDungeonCount >= 40) {
                                membersWithCooldown.add("${member.playerName}(已达40次上限)")
                                return@forEach
                            }
                            val memberCdMinutes = min(memberData.dailyDungeonCount + 5, 20)
                            val memberCdTime = memberCdMinutes * 60 * 1000
                            val memberRemainingTime = memberCdTime - (currentTime - memberData.lastDungeonTime)
                            if (memberRemainingTime > 0) {
                                membersWithCooldown.add("${member.playerName}(${memberData.dailyDungeonCount}次，CD中)")
                            }
                        }
                    }

                    if (membersWithCooldown.isNotEmpty()) {
                        group.sendMessage("以下队员副本冷却中: ${membersWithCooldown.joinToString("、")}")
                        return@subscribeAlways
                    }

                    val dungeonNum = if (message.startsWith("/选择副本 ")) {
                        message.substringAfter("/选择副本 ").trim().toIntOrNull()
                    } else {
                        message.substringAfter("/xzfb ").trim().toIntOrNull()
                    }
                    val dungeon = if (dungeonNum != null) DungeonManager.getDungeonById(dungeonNum) else null

                    if (dungeon == null) {
                        group.sendMessage("请输入有效的副本编号（1-9）。")
                        return@subscribeAlways
                    }

                    // 检查副本的进入门槛
                    if (dungeon.id == 7) {
                        val captainData = PlayerDataManager.getPlayerData(team.captainId)
                        // 修改条件：检查是否持有MR或LR装备
                        val hasMREquipment = captainData?.equipment?.name?.contains("[MR]") == true
                        val hasLREquipment = captainData?.equipment?.name?.contains("[LR]") == true
                        val hasXREquipment = captainData?.equipment?.name?.contains("[XR]") == true
                        val hasRequiredEquipment = hasMREquipment || hasLREquipment

                        if (!hasRequiredEquipment) {
                            group.sendMessage("难度7副本需要队长持有[MR]及以上等级装备才能进入！")
                            return@subscribeAlways
                        }
                    }
                    if (dungeon.id == 8) {
                        val captainData = PlayerDataManager.getPlayerData(team.captainId)
                        // 检查是否持有LR装备
                        val hasLREquipment = captainData?.equipment?.name?.contains("[LR]") == true
                        val hasXREquipment = captainData?.equipment?.name?.contains("[XR]") == true
                        if (!hasLREquipment) {
                            group.sendMessage("难度8副本需要队长持有[LR]及以上等级装备才能进入！")
                            return@subscribeAlways
                        }
                    }
                    if (dungeon.id == 9) {
                        val captainData = PlayerDataManager.getPlayerData(team.captainId)
                        // 检查是否持有XR装备
                        val hasXREquipment = captainData?.equipment?.name?.contains("[XR]") == true

                        if (!hasXREquipment) {
                            group.sendMessage("难度9副本需要队长持有[XR]等级装备才能进入！")
                            return@subscribeAlways
                        }
                    }

                    // 设置队伍选择的副本
                    team.dungeonId = dungeon.id

                    // 更新所有队员的副本冷却时间戳
                    team.members.forEach { member ->
                        val memberData = PlayerDataManager.getPlayerData(member.playerId)
                        if (memberData != null) {
                            memberData.lastDungeonTime = System.currentTimeMillis()
                            // 确保清空旧字段（如果存在）
                            memberData.lastDungeonDate = null

                            // 增加每日副本计数（前移到这里）
                            memberData.dailyDungeonCount += 1


                            PlayerDataManager.savePlayerData(memberData)
                        }
                    }

                    // 使用 GlobalScope 启动协程处理副本攻略
                    GlobalScope.launch {
                        // 计算队伍宠物效果
                        val teamEffects = PetEffectCalculator.calculateTeamEffects(team)

                        // 计算队伍总战力和基础成功率（应用宠物效果）
                        val totalATK = (team.members.sumOf { it.atk } * (1 + teamEffects.atkMultiplier)).toLong()
                        val totalLUCK = (team.members.sumOf { it.luck } * (1 + teamEffects.luckMultiplier)).toLong()
                        val totalPowBonus = LuckyNecklaceManager.calculateTeamPowBonus(team)
                        val teamPower = totalATK * (0.5 + totalPowBonus) * totalLUCK
                        val baseSuccessRate = (teamPower / dungeon.difficulty.toDouble()).coerceAtMost(1.0)

                        // 发送开始消息
                        val memberNames = team.members.joinToString("，") { it.playerName }
                        group.sendMessage("$memberNames 开始攻略 ${dungeon.name}。")

                        delay(3000)

                        // 生成剧情事件（应用宠物效果：增加正向事件概率和额外事件数量）
                        val events = DungeonStoryGenerator.generateEvents(
                            team,
                            dungeon,
                            teamEffects.positiveEventChance,
                            teamEffects.additionalEvents
                        )

                        // 构建事件消息
                        val eventMessages = StringBuilder()
                        eventMessages.append("副本剧情事件：\n")

                        // 发送所有普通事件（不包括BOSS事件）
                        val regularEventsCount = min(events.size - 1, 5)
                        for (i in 0 until regularEventsCount) {
                            eventMessages.append("${events[i].description}\n")
                        }

                        // 如果事件太多，添加提示
                        if (events.size - 1 > 5) {
                            eventMessages.append("...还有${events.size - 1 - 5}个事件未显示，但效果已生效\n")
                        }

                        // 发送普通事件消息
                        group.sendMessage(eventMessages.toString())
                        delay(3000)
                        // 发送BOSS事件
                        group.sendMessage(events[events.size - 1].description)

                        // 计算总奖励和成功率调整
                        var totalSuccessRateChange = 0.0
                        var totalExtraGold = 0
                        var totalExtraATK = 0
                        var totalExtraDEF = 0
                        var totalExtraWangCoin = 0        // 新增：汪币奖励
                        var totalExtraRebirthCount = 0    // 新增：转生次数奖励
                        var totalExtraBaseLUCK = 0        // 新增：基础LUCK奖励

                        // 排除BOSS事件，只计算前5个事件
                        for (i in 0 until events.size - 1) {
                            totalSuccessRateChange += events[i].successRateChange
                            totalExtraGold += events[i].extraGold
                            totalExtraATK += events[i].extraATK
                            totalExtraDEF += events[i].extraDEF
                            totalExtraWangCoin += events[i].extraWangCoin        // 新增
                            totalExtraRebirthCount += events[i].extraRebirthCount // 新增
                            totalExtraBaseLUCK += events[i].extraBaseLUCK        // 新增
                        }

                        // 检查是否为周末狂欢
                        val isWeekendBonus = isWeekendBonus()
                        val bonusMultiplier = if (isWeekendBonus) 2 else 1

                        // 计算最终成功率
                        val finalSuccessRate = (baseSuccessRate + totalSuccessRateChange).coerceIn(0.0, 1.0)
                        val random = Random.nextDouble(0.0, 1.0)
                        val success = random <= finalSuccessRate

                        // 计算基础奖励（应用宠物效果：奖励加成）
                        val thiefBonus = teamEffects.rewardMultiplier * dungeon.id
                        val finalRewardMultiplier = 1 + thiefBonus
                        val baseReward = (dungeon.reward * bonusMultiplier * finalRewardMultiplier).toInt()
                        val actualReward = if (success) {
                            baseReward
                        } else {
                            (baseReward * 0.1).toInt().coerceAtLeast(1)
                        }

                        // 额外奖励也需要应用盗贼效果
                        val bonusExtraGold = (totalExtraGold * bonusMultiplier * finalRewardMultiplier).toInt()
                        val bonusExtraATK = (totalExtraATK * bonusMultiplier * finalRewardMultiplier).toInt()
                        val bonusExtraDEF = (totalExtraDEF * bonusMultiplier * finalRewardMultiplier).toInt()

                        // 平分奖励
                        val rewardPerPerson = (actualReward + bonusExtraGold) / 4

                        // 发送结果
                        delay(3000)

                        // 添加周末狂欢提示
                        val weekendBonusMessage = getWeekendBonusMessage()

                        if (success) {
                            group.sendMessage("经过一番苦战，队伍终于击败了BOSS！$weekendBonusMessage")

                            // 构建奖励信息
                            val rewardInfo = StringBuilder()

                            // 记录奖励消息
                            val noRewardPlayers = mutableListOf<String>()
                            var totalRewardGiven = 0 // 声明并初始化变量

                            // 处理每个队员的奖励
                            team.members.forEach { member ->
                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                if (memberData != null) {
                                    // 检查玩家今日副本次数是否已达上限
                                    if (memberData.dailyDungeonCount <= 40) {
                                        // 未达上限，正常获得喵币和属性奖励
                                        memberData.gold += rewardPerPerson
                                        if (success) {
                                            memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, bonusExtraATK, memberData.rebirthCount)
                                            memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, bonusExtraDEF, memberData.rebirthCount)
                                        }
                                        totalRewardGiven++
                                    } else {
                                        // 已达上限，不获得喵币和属性奖励
                                        noRewardPlayers.add(member.playerName)
                                    }

                                    // 难度5副本汪币奖励
                                    if (dungeon.id == 5) {
                                        val wangCoinReward = if (Random.nextDouble() < 0.67) 0 else 1
                                        memberData.wangCoin += wangCoinReward
                                    }
                                    if (dungeon.id == 6) {
                                        val wangCoinReward = if (Random.nextDouble() < 0.67) 1 else 3
                                        memberData.wangCoin += wangCoinReward
                                    }
                                    if (dungeon.id == 7) {
                                        val wangCoinReward = if (Random.nextDouble() < 0.67) 3 else 9
                                        memberData.wangCoin += wangCoinReward
                                    }
                                    if (dungeon.id == 8) {
                                        val wangCoinReward = if (Random.nextDouble() < 0.67) 9 else 27
                                        memberData.wangCoin += wangCoinReward
                                    }
                                    if (dungeon.id == 9) {
                                        val wangCoinReward = if (Random.nextDouble() < 0.67) 27 else 81
                                        memberData.wangCoin += wangCoinReward
                                    }

                                    // 保存玩家数据
                                    PlayerDataManager.savePlayerData(memberData)
                                }
                            }

                            if (totalRewardGiven > 0) {
                                rewardInfo.append("恭喜！攻略${dungeon.name}成功！")

                                if (dungeon.id != 6) {
                                    // 普通副本：显示每人获得多少喵币
                                    rewardInfo.append("每人获得${rewardPerPerson}喵币。")
                                } else {
                                    // 难度6副本：显示总结信息
                                    rewardInfo.append("每人获得${rewardPerPerson}喵币。")
                                    if (bonusExtraATK > 0 || bonusExtraDEF > 0) {
                                        rewardInfo.append("和属性奖励")
                                    }
                                    rewardInfo.append("。")
                                }

                                if (bonusExtraGold > 0) {
                                    rewardInfo.append("\n额外喵币奖励: +${bonusExtraGold}喵币(平分)")
                                }
                                if (bonusExtraATK > 0) {
                                    rewardInfo.append("\n额外ATK奖励: +${bonusExtraATK}点基础ATK")
                                }
                                if (bonusExtraDEF > 0) {
                                    rewardInfo.append("\n额外DEF奖励: +${bonusExtraDEF}点基础DEF")
                                }

                                // 添加新奖励信息（只对难7-9显示）
                                if (totalExtraWangCoin > 0) {
                                    rewardInfo.append("\n额外汪币奖励: +${totalExtraWangCoin}汪币")
                                }
                                if (totalExtraRebirthCount > 0) {
                                    rewardInfo.append("\n额外转生次数奖励: +${totalExtraRebirthCount}次")
                                }
                                if (totalExtraBaseLUCK > 0) {
                                    rewardInfo.append("\n额外基础LUCK奖励: +${totalExtraBaseLUCK}点")
                                }
                            }
                            //这里可以添加到达次数的提示
                            if (noRewardPlayers.isNotEmpty()) {
                                if (rewardInfo.isNotEmpty()) {
                                    rewardInfo.append("")
                                }
                                rewardInfo.append("")
                            }

                            // 添加成功率信息
                            if (rewardInfo.isNotEmpty()) {
                                rewardInfo.append("\n")
                            }
                            rewardInfo.append("基础成功率: ${"%.1f".format(baseSuccessRate * 100)}%")
                            rewardInfo.append("\n事件调整: ${if (totalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(totalSuccessRateChange * 100)}%")
                            rewardInfo.append("\n最终成功率: ${"%.1f".format(finalSuccessRate * 100)}%")

                            group.sendMessage(rewardInfo.toString())

                            // 发送彩笔获得提示（仅限难度6副本）
                            if (dungeon.id >= 6){
                                delay(3000)
                                group.sendMessage("🐶 每位队员获得一定数量的汪币奖励 🐶")
                            }
                            // 检查队伍中每个玩家的隐藏副本进入券数量
                            val teamTickets = mutableListOf<Int>()
                            var allHaveTicket = true
                            var anyHasTicket = false

                            // 获取每个队员的隐藏副本进入券数量
                            team.members.forEach { member ->
                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                val ticketCount = memberData?.hiddenDungeonTickets ?: 0
                                teamTickets.add(ticketCount)

                                if (ticketCount < 1) {
                                    allHaveTicket = false
                                } else {
                                    anyHasTicket = true
                                }
                            }

                            val bardSBonus = if (teamEffects.bonusDungeonChance > 0.0) {
                                val dungeonLevel = dungeon.id
                                (dungeonLevel + 1) * (dungeonLevel + 1) / 100.0
                            } else {
                                0.0
                            }

                            // 计算隐藏副本触发概率
                            val baseRate = 0.05
                            val totalBonusRate = teamEffects.bonusDungeonChance + bardSBonus
                            val totalRate = baseRate + totalBonusRate

                            // 生成随机数
                            val randomValue = Random.nextDouble()

                            val triggerBonusDungeon = if (allHaveTicket) {
                                true
                            } else {
                                randomValue < totalRate
                            }

                            if (triggerBonusDungeon) {
                                delay(3000)
                                if (allHaveTicket) {
                                    group.sendMessage("🎉 队伍使用了隐藏副本进入券，确保了隐藏副本的出现！")

                                    // 消耗所有队员的券
                                    team.members.forEach { member ->
                                        val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                        if (memberData != null && memberData.hiddenDungeonTickets >= 1) {
                                            memberData.hiddenDungeonTickets -= 1
                                            PlayerDataManager.savePlayerData(memberData)
                                        }
                                    }
                                } else {
                                    group.sendMessage("🎉 One more thing！")
                                }
                                delay(3000)

                                val originalDungeonId = dungeon.id
                                // 创建奖励副本 (难度x2，奖励x2)
                                val bonusDungeon = Dungeon(
                                    originalDungeonId * 10,
                                    "${dungeon.name}(奖励)",
                                    dungeon.difficulty * 2,
                                    dungeon.reward * 2
                                )

                                // 在奖励副本事件生成处传递牧师效果
                                val bonusEvents = DungeonStoryGenerator.generateBonusDungeonEvents(
                                    team,
                                    bonusDungeon,
                                    teamEffects.positiveEventChance
                                )

                                // 构建奖励副本事件消息
                                val bonusEventMessages = StringBuilder()
                                bonusEventMessages.append("奖励副本剧情事件：\n")

                                // 发送前3个事件
                                for (i in 0 until 3) {
                                    bonusEventMessages.append("${bonusEvents[i].description}\n")
                                }

                                // 发送奖励副本普通事件消息
                                group.sendMessage(bonusEventMessages.toString())

                                // 发送BOSS事件
                                group.sendMessage(bonusEvents[3].description)

                                // 在奖励副本计算前重新计算团队战力（考虑宠物效果）
                                val bonusTotalATK = (team.members.sumOf { it.atk } * (1 + teamEffects.atkMultiplier)).toLong()
                                val bonusTotalLUCK = (team.members.sumOf { it.luck } * (1 + teamEffects.luckMultiplier)).toLong()
                                val bonusTotalPowBonus = LuckyNecklaceManager.calculateTeamPowBonus(team)
                                val bonusTeamPower = bonusTotalATK * (0.5 + bonusTotalPowBonus) * bonusTotalLUCK

                                // 计算奖励副本的成功率
                                val bonusTotalSuccessRateChange = bonusEvents.take(3).sumOf { it.successRateChange }
                                val bonusBaseSuccessRate = (bonusTeamPower / bonusDungeon.difficulty.toDouble()).coerceAtMost(1.0)
                                val bonusFinalSuccessRate = (bonusBaseSuccessRate + bonusTotalSuccessRateChange).coerceIn(0.0, 1.0)
                                val bonusRandom = Random.nextDouble(0.0, 1.0)
                                val bonusSuccess = bonusRandom <= bonusFinalSuccessRate

                                // 计算奖励副本的奖励
                                val bonusThiefBonus = teamEffects.rewardMultiplier * dungeon.id
                                val bonusFinalRewardMultiplier = 1 + bonusThiefBonus
                                val bonusBaseReward = (bonusDungeon.reward * bonusMultiplier * bonusFinalRewardMultiplier).toInt()

                                val bonusActualReward = if (bonusSuccess) {
                                    bonusBaseReward
                                } else {
                                    (bonusBaseReward * 0.1).toInt().coerceAtLeast(1)
                                }

                                // 计算额外奖励（应用盗贼效果）
                                val bonusTotalExtraGold = (bonusEvents.take(3).sumOf { it.extraGold } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()
                                val bonusTotalExtraATK = (bonusEvents.take(3).sumOf { it.extraATK } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()
                                val bonusTotalExtraDEF = (bonusEvents.take(3).sumOf { it.extraDEF } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()

                                // 新增：计算奖励副本的新奖励字段（应用盗贼效果）
                                val bonusTotalExtraWangCoin = (bonusEvents.take(3).sumOf { it.extraWangCoin } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()
                                val bonusTotalExtraRebirthCount = (bonusEvents.take(3).sumOf { it.extraRebirthCount } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()
                                val bonusTotalExtraBaseLUCK = (bonusEvents.take(3).sumOf { it.extraBaseLUCK } * bonusMultiplier * bonusFinalRewardMultiplier).toInt()
                                // 平分奖励
                                val bonusRewardPerPerson = (bonusActualReward + bonusTotalExtraGold) / 4
                                delay(4000)
                                // 发送结果
                                if (bonusSuccess) {
                                    group.sendMessage("🌟 队伍成功通过了奖励副本！")

                                    val bonusRewardInfo = StringBuilder()
                                    bonusRewardInfo.append("奖励副本攻略成功！每人获得${bonusRewardPerPerson}喵币。")

                                    if (bonusTotalExtraATK > 0) {
                                        bonusRewardInfo.append("\n额外ATK奖励: 每人+${bonusTotalExtraATK}点基础ATK")
                                    }
                                    if (bonusTotalExtraDEF > 0) {
                                        bonusRewardInfo.append("\n额外DEF奖励: 每人+${bonusTotalExtraDEF}点基础DEF")
                                    }
                                    // 添加新奖励信息（只对难7-9显示）
                                    if (bonusTotalExtraWangCoin > 0) {
                                        bonusRewardInfo.append("\n额外汪币奖励: +${bonusTotalExtraWangCoin}汪币")
                                    }
                                    if (bonusTotalExtraRebirthCount > 0) {
                                        bonusRewardInfo.append("\n额外转生次数奖励: +${bonusTotalExtraRebirthCount}次")
                                    }
                                    if (bonusTotalExtraBaseLUCK > 0) {
                                        bonusRewardInfo.append("\n额外基础LUCK奖励: +${bonusTotalExtraBaseLUCK}点")
                                    }

                                    bonusRewardInfo.append("\n基础成功率: ${"%.1f".format(bonusBaseSuccessRate * 100)}%")
                                    bonusRewardInfo.append("\n事件调整: ${if (bonusTotalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(bonusTotalSuccessRateChange * 100)}%")
                                    bonusRewardInfo.append("\n最终成功率: ${"%.1f".format(bonusFinalSuccessRate * 100)}%")

                                    group.sendMessage(bonusRewardInfo.toString())

                                    // 添加装备掉落逻辑
                                    // 获取原始副本难度
                                    val originalDungeonId = bonusDungeon.id / 10

                                    // 检查是否掉落装备 (1%基础概率 + 宝藏猎手效果)
                                    val dropChance = (0.01 + teamEffects.equipmentDropChance).coerceAtMost(1.0)
                                    val shouldDropEquipment = Random.nextDouble() < dropChance

                                    if (shouldDropEquipment) {
                                        // 根据原始副本难度确定掉落装备
                                        val dropEquipment = when (originalDungeonId) {
                                            3 -> Shop.getSpecialEquipmentByName("[SR]王国圣剑")
                                            4 -> Shop.getSpecialEquipmentByName("[SSR]天使权杖")
                                            5 -> Shop.getSpecialEquipmentByName("[UR]魔之宝珠")
                                            6 -> Shop.getSpecialEquipmentByName("[MR]诸神之怒")
                                            7 -> Shop.getSpecialEquipmentByName("[LR]创世神杖")
                                            8 -> Shop.getSpecialEquipmentByName("[XR]诛仙剑")
                                            else -> null
                                        }

                                        if (dropEquipment != null) {
                                            // 重新获取所有队员的最新数据
                                            val updatedTeamMembers = team.members.map { member ->
                                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                                Pair(member, memberData)
                                            }.filter { it.second != null }.map { it.first to it.second!! }

                                            // 筛选可以接受该装备的玩家（这里会自动应用等级检查）
                                            val eligiblePlayers = updatedTeamMembers.filter { (member, playerData) ->
                                                canPlayerGetEquipment(playerData, dropEquipment)
                                            }

                                            if (eligiblePlayers.isNotEmpty()) {
                                                // 随机选择一个符合条件的玩家
                                                val (luckyMember, luckyPlayerData) = eligiblePlayers.random()

                                                // 记录旧装备信息（用于继承强化等级）
                                                val oldEquipment = luckyPlayerData.equipment
                                                val oldEnhanceLevel = oldEquipment?.enhanceLevel ?: 0

                                                // 创建新装备并继承强化等级
                                                val newEquipment = dropEquipment.copy(enhanceLevel = oldEnhanceLevel)

                                                // 卖掉现有装备（如果有）
                                                if (oldEquipment != null) {
                                                    luckyPlayerData.gold += oldEquipment.price
                                                }

                                                // 装备新装备
                                                luckyPlayerData.equipment = newEquipment

                                                // 保存玩家数据
                                                PlayerDataManager.savePlayerData(luckyPlayerData)

                                                // 发送获得装备的消息
                                                group.sendMessage("🎁 隐藏副本掉落！${luckyMember.playerName} 获得了 ${newEquipment.getDisplayName()}！")
                                            } else {
                                                group.sendMessage("💔 隐藏副本掉落了 ${dropEquipment.name}，但队伍中没有人符合条件。")
                                            }
                                        }
                                    }
                                    // 检查是否是难度9的隐藏副本 (9 * 10 = 90)
                                    val isFinalBoss = bonusDungeon.id == 90

                                    if (isFinalBoss) {
                                        // 标记所有真实队员为通关状态
                                        val clearedPlayers = mutableListOf<String>()

                                        team.members.forEach { member ->
                                            if (!member.isVirtual) {
                                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                                if (memberData != null && !memberData.hasClearedGame) {
                                                    memberData.hasClearedGame = true
                                                    PlayerDataManager.savePlayerData(memberData)
                                                    clearedPlayers.add(member.playerName)
                                                }
                                            }
                                        }

                                        // 如果有玩家首次通关，发送通关剧情
                                        if (clearedPlayers.isNotEmpty()) {
                                            // 使用协程来按顺序发送剧情消息
                                            launch {
                                                delay(2000) // 等待2秒让奖励信息显示完

                                                group.sendMessage("🎊 ${clearedPlayers.joinToString("、")} 达成了游戏通关成就！")
                                                delay(3000)

                                                // 开始通关剧情演绎
                                                group.sendMessage("🌟 虚空深处，一道光芒逐渐显现...")
                                                delay(3000)

                                                group.sendMessage("💫 '终于...有人到达了这里。'")
                                                delay(3000)

                                                group.sendMessage("🌌 一个古老的声音在虚空中回荡：'你们证明了勇气与智慧...当然最重要的是肝。'")
                                                delay(3000)

                                                group.sendMessage("✨ '但记住，真正的冒险永远不会结束...它只是以新的形式开始。'")
                                                delay(3000)

                                                group.sendMessage("🎊 '恭喜你们，你们已经完成了这段传奇旅程！'")
                                                delay(3000)

                                                group.sendMessage("💝 '请继续期待新作:再刷亿把(OneMoreRun)...'")
                                            }
                                        }
                                    }
                                } else {
                                    group.sendMessage("😢 队伍未能在奖励副本中获胜，但仍获得了一些安慰奖励...")

                                    val bonusFailInfo = StringBuilder()
                                    bonusFailInfo.append("奖励副本攻略失败。每人获得${bonusRewardPerPerson}喵币。")
                                    bonusFailInfo.append("\n基础成功率: ${"%.1f".format(bonusBaseSuccessRate * 100)}%")
                                    bonusFailInfo.append("\n事件调整: ${if (bonusTotalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(bonusTotalSuccessRateChange * 100)}%")
                                    bonusFailInfo.append("\n最终成功率: ${"%.1f".format(bonusFinalSuccessRate * 100)}%")

                                    group.sendMessage(bonusFailInfo.toString())
                                }

                                // 发放奖励副本的奖励（不占用每日次数）
                                val bonusRewardMessages = mutableListOf<String>()

                                team.members.forEach { member ->
                                    val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                    if (memberData != null) {
                                        // 奖励副本的奖励不占用每日次数，所有玩家都能获得
                                        memberData.gold += bonusRewardPerPerson
                                        if (bonusSuccess) {
                                            memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, bonusTotalExtraATK, memberData.rebirthCount)
                                            memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, bonusTotalExtraDEF, memberData.rebirthCount)
                                            memberData.baseLUCK = increaseAttributeWithLimit(memberData.baseLUCK, bonusTotalExtraBaseLUCK, memberData.rebirthCount)
                                            memberData.rebirthCount += bonusTotalExtraRebirthCount
                                            memberData.wangCoin += bonusTotalExtraWangCoin
                                        }

                                        PlayerDataManager.savePlayerData(memberData)

                                        // 更新全服楷模记录（每个真实队员）
                                        team.members.forEach { member ->
                                            if (!member.isVirtual) {
                                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                                if (memberData != null) {
                                                    // 计算该队员的最终属性（与"/我的信息"中相同）
                                                    val necklaceATK = memberData.luckyNecklace?.attributes
                                                        ?.filter { it.type == NecklaceAttributeType.ATK }
                                                        ?.sumOf { it.value } ?: 0
                                                    val necklaceDEF = memberData.luckyNecklace?.attributes
                                                        ?.filter { it.type == NecklaceAttributeType.DEF }
                                                        ?.sumOf { it.value } ?: 0
                                                    val necklaceLUCK = memberData.luckyNecklace?.attributes
                                                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                                                        ?.sumOf { it.value } ?: 0

                                                    val finalATK = (memberData.baseATK +
                                                        (memberData.equipment?.getEnhancedAtk() ?: 0) +
                                                        ((memberData.pet?.atk ?: 0) + memberData.devouredATK) +
                                                        (memberData.relic?.atk ?: 0) +
                                                        memberData.relicAtkBonus +
                                                        necklaceATK).toLong()

                                                    val finalDEF = (memberData.baseDEF +
                                                        (memberData.equipment?.getEnhancedDef() ?: 0) +
                                                        ((memberData.pet?.def ?: 0) + memberData.devouredDEF) +
                                                        (memberData.relic?.def ?: 0) +
                                                        memberData.relicDefBonus +
                                                        necklaceDEF).toLong()

                                                    val finalLUCK = (memberData.baseLUCK +
                                                        (memberData.equipment?.getEnhancedLuck() ?: 0) +
                                                        ((memberData.pet?.luck ?: 0) + memberData.devouredLUCK) +
                                                        (memberData.relic?.luck ?: 0) +
                                                        memberData.relicLuckBonus +
                                                        necklaceLUCK).toLong()

                                                    // 获取玩家在群中的昵称
                                                    val playerName = try {
                                                        group.get(member.playerId)?.nameCardOrNick ?: member.playerName
                                                    } catch (e: Exception) {
                                                        member.playerName
                                                    }

                                                    // 获取装备信息
                                                    val equipmentName = memberData.equipment?.name
                                                    val equipmentATK = memberData.equipment?.getEnhancedAtk() ?: 0
                                                    val equipmentDEF = memberData.equipment?.getEnhancedDef() ?: 0
                                                    val equipmentLUCK = memberData.equipment?.getEnhancedLuck() ?: 0
                                                    val equipmentBaseATK = memberData.equipment?.atk ?: 0
                                                    val equipmentBaseDEF = memberData.equipment?.def ?: 0
                                                    val equipmentBaseLUCK = memberData.equipment?.luck ?: 0

                                                    // 获取宠物信息
                                                    val petName = memberData.pet?.name
                                                    val petATK = memberData.pet?.atk ?: 0
                                                    val petDEF = memberData.pet?.def ?: 0
                                                    val petLUCK = memberData.pet?.luck ?: 0
                                                    val petGrade = memberData.pet?.grade
                                                    val petEffect = memberData.pet?.specialEffect?.let { effect ->
                                                        when (effect) {
                                                            PetEffect.WARRIOR -> "战士"
                                                            PetEffect.WARRIOR_S -> "战士S"
                                                            PetEffect.ARCHER -> "弓手"
                                                            PetEffect.ARCHER_S -> "弓手S"
                                                            PetEffect.THIEF -> "盗贼"
                                                            PetEffect.THIEF_S -> "盗贼S"
                                                            PetEffect.PRIEST -> "牧师"
                                                            PetEffect.PRIEST_S -> "牧师S"
                                                            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
                                                            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                                                            PetEffect.BARD -> "吟游诗人"
                                                            PetEffect.BARD_S -> "吟游诗人S"
                                                            else -> "未知"
                                                        }
                                                    }

                                                    // 获取遗物信息
                                                    val relicName = memberData.relic?.name
                                                    val relicATK = memberData.relic?.atk ?: 0
                                                    val relicDEF = memberData.relic?.def ?: 0
                                                    val relicLUCK = memberData.relic?.luck ?: 0
                                                    val relicGrade = memberData.relic?.grade

                                                    val necklacePOW = memberData.luckyNecklace?.attributes
                                                        ?.filter { it.type == NecklaceAttributeType.POW }
                                                        ?.sumOf { it.value } ?: 0

                                                    // 调用TopPlayerManager.updateRecord
                                                    TopPlayerManager.updateRecord(
                                                        member.playerId,
                                                        playerName,
                                                        finalATK,
                                                        finalDEF,
                                                        finalLUCK,
                                                        memberData.baseATK,
                                                        memberData.baseDEF,
                                                        memberData.baseLUCK,
                                                        equipmentName,
                                                        equipmentATK,
                                                        equipmentDEF,
                                                        equipmentLUCK,
                                                        equipmentBaseATK,
                                                        equipmentBaseDEF,
                                                        equipmentBaseLUCK,
                                                        memberData.equipment?.enhanceLevel ?: 0,
                                                        petName,
                                                        petATK,
                                                        petDEF,
                                                        petLUCK,
                                                        petGrade,
                                                        petEffect,
                                                        relicName,
                                                        relicATK,
                                                        relicDEF,
                                                        relicLUCK,
                                                        relicGrade,
                                                        memberData.relicAtkBonus,
                                                        memberData.relicDefBonus,
                                                        memberData.relicLuckBonus,
                                                        memberData.devouredATK,
                                                        memberData.devouredDEF,
                                                        memberData.devouredLUCK,
                                                        memberData.devouredPets,
                                                        necklaceName = memberData.luckyNecklace?.getName(),
                                                        necklaceRarity = memberData.luckyNecklace?.rarity ?: 0,
                                                        necklaceATK = necklaceATK,
                                                        necklaceDEF = necklaceDEF,
                                                        necklaceLUCK = necklaceLUCK,
                                                        necklacePOW = necklacePOW
                                                    )
                                                }
                                            }
                                        }
                                        // 更新奖励消息
                                        val bonusInfo = if (isWeekendBonus) " (周末狂欢双倍奖励)" else ""
                                        val rewardDetails = buildString {
                                            append("${bonusRewardPerPerson}喵币")
                                        }
                                        bonusRewardMessages.add("${member.playerName} 获得${rewardDetails}$bonusInfo")
                                    }
                                }
                            }

                        } else {
                            group.sendMessage("经过一番苦战，菜鸡们最终还是不敌BOSS…")

                            // 添加失败信息
                            val failInfo = StringBuilder()
                            failInfo.append("很遗憾，攻略${dungeon.name}失败。每人获得${rewardPerPerson}喵币。")
                            failInfo.append("\n基础成功率: ${"%.1f".format(baseSuccessRate * 100)}%")
                            failInfo.append("\n事件调整: ${if (totalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(totalSuccessRateChange * 100)}%")
                            failInfo.append("\n最终成功率: ${"%.1f".format(finalSuccessRate * 100)}%")

                            group.sendMessage(failInfo.toString())

                            // 失败情况下也要处理奖励（但不受每日次数限制，因为失败奖励较少）
                            team.members.forEach { member ->
                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                if (memberData != null) {
                                    // 失败情况下获得较少的喵币奖励（不受每日次数限制）
                                    memberData.gold += rewardPerPerson
                                    PlayerDataManager.savePlayerData(memberData)
                                }
                            }
                        }

                        // 解散队伍
                        TeamManager.disbandTeam(group.id)
                    }
                }

                message == "/离开队伍" || message == "/lkdw" -> {
                    val team = TeamManager.getTeamByPlayer(senderId)
                    if (team == null) {
                        group.sendMessage("你不在任何队伍中。")
                        return@subscribeAlways
                    }

                    if (team.captainId == senderId) {
                        TeamManager.disbandTeam(team.groupId)
                        group.sendMessage("队长解散了队伍。")
                    } else {
                        // 使用新的 removeMember 方法
                        if (TeamManager.removeMember(senderId)) {
                            group.sendMessage("${sender.nameCardOrNick} 离开了队伍。")
                        } else {
                            group.sendMessage("离开队伍失败。")
                        }
                    }
                }

                message.startsWith("/更改宠物职业 ") || message.startsWith("/ggcwzy ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有宠物
                    if (playerData.pet == null) {
                        group.sendMessage("你还没有宠物，无法更改职业")
                        return@subscribeAlways
                    }

                    // 检查宠物等级是否为SS
                    if (playerData.pet!!.grade != "SS") {
                        group.sendMessage("你的宠物还太菜了~")
                        return@subscribeAlways
                    }

                    val targetClass = message.substringAfter("/更改宠物职业 ").trim()

                    // 定义可变更的职业映射
                    val classMapping = mapOf(
                        "战士S" to PetEffect.WARRIOR_S,
                        "弓手S" to PetEffect.ARCHER_S,
                        "盗贼S" to PetEffect.THIEF_S,
                        "牧师S" to PetEffect.PRIEST_S,
                        "宝藏猎手S" to PetEffect.TREASURE_HUNTER_S,
                        "猎手S" to PetEffect.TREASURE_HUNTER_S,
                        "吟游诗人S" to PetEffect.BARD_S,
                        "诗人S" to PetEffect.BARD_S
                    )

                    val targetEffect = classMapping[targetClass]

                    if (targetEffect == null) {
                        val validClasses = classMapping.keys.joinToString("、")
                        group.sendMessage("无效的职业名称！可选的职业有：$validClasses")
                        return@subscribeAlways
                    }

                    // 获取当前宠物信息
                    val currentPet = playerData.pet!!

                    // 创建新宠物（只变更职业，其他属性不变）
                    val newPet = Pet(
                        currentPet.name,
                        currentPet.atk,
                        currentPet.def,
                        currentPet.luck,
                        currentPet.grade,
                        targetEffect
                    )

                    // 更新宠物
                    playerData.pet = newPet

                    // 保存数据
                    PlayerDataManager.savePlayerData(playerData)

                    group.sendMessage("更改成功！${currentPet.name} 的职业已变更为: $targetClass")
                }

                // 在 /副本信息 命令中，更新CD信息显示：
                message == "/副本信息" || message == "/fbxx" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 修改：动态CD计算
                    val currentTime = System.currentTimeMillis()
                    val cdMinutes = min(playerData.dailyDungeonCount + 5, 20)
                    val cdTime = cdMinutes * 60 * 1000
                    val remainingTime = cdTime - (currentTime - playerData.lastDungeonTime)

                    val cdMessage = if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        "副本冷却中，还需${minutes}分${seconds}秒"
                    } else {
                        "可以进入副本"
                    }

                    val countMessage = if (playerData.dailyDungeonCount >= 40) {
                        "今日参与次数: ${playerData.dailyDungeonCount} (副本次数达上限)"
                    } else {
                        "今日参与次数: ${playerData.dailyDungeonCount}"
                    }

                    // 添加动态CD说明
                    val nextCdMessage = if (playerData.dailyDungeonCount <= 40) {
                        val nextCdMinutes = min(playerData.dailyDungeonCount + 5, 20)
                        "\n下次副本CD: ${nextCdMinutes}分钟"
                    } else {
                        "\n今日已达上限，明日重置"
                    }

                    group.sendMessage("${sender.nameCardOrNick} 的副本信息:\n" +
                        countMessage + "\n" +
                        "状态: $cdMessage" + nextCdMessage)
                }

                message == "/获取遗物" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查转生次数是否足够
                    if (playerData.rebirthCount < 5) {
                        group.sendMessage("获取遗物需要5次转生次数，你当前只有${playerData.rebirthCount}次")
                        return@subscribeAlways
                    }

                    // 检查是否已有遗物
                    if (playerData.relic != null) {
                        group.sendMessage("你已经拥有遗物，无法再次获取")
                        return@subscribeAlways
                    }

                    // 生成遗物
                    val newRelic = RelicGenerator.generateRandomRelic()
                    playerData.relic = newRelic
                    playerData.rebirthCount -= 5
                    PlayerDataManager.savePlayerData(playerData)

                    group.sendMessage("${sender.nameCardOrNick} 消耗5次转生次数获取了遗物：\n${RelicGenerator.formatRelicInfo(newRelic)}")
                }

                message == "/遗物信息" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    if (playerData.relic == null) {
                        group.sendMessage("你还没有遗物，使用\"/获取遗物\"命令可以消耗5次转生次数获取遗物")
                    } else {
                        group.sendMessage("${sender.nameCardOrNick} 的遗物信息:\n${RelicGenerator.formatRelicInfo(playerData.relic!!)}")
                    }
                }

                message == "/喵币重置遗物" || message == "/mbczyw" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有遗物
                    if (playerData.relic == null) {
                        group.sendMessage("你还没有遗物，无法重置")
                        return@subscribeAlways
                    }

                    // 检查喵币是否足够
                    if (playerData.gold < 2500) {
                        group.sendMessage("喵币重置需要2500喵币，你当前只有${playerData.gold}喵币")
                        return@subscribeAlways
                    }

                    // 立即扣除喵币
                    playerData.gold -= 2500
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成新遗物并进入确认流程
                    val newRelic = RelicGenerator.generateRandomRelic()
                    RelicConfirmation.addPendingReset(senderId, playerData, newRelic, "gold")

                    group.sendMessage("${sender.nameCardOrNick}，已扣除2500喵币进行遗物重置：\n" +
                        "当前遗物：\n${RelicGenerator.formatRelicInfo(playerData.relic!!)}\n" +
                        "新遗物：\n${RelicGenerator.formatRelicInfo(newRelic)}\n" +
                        "是否替换？回复\"是\"替换，回复其他内容保留原遗物（2分钟内有效）")
                }

                // 重铸幸运项链命令
                message == "/汪币重置项链" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有待处理的重铸请求
                    if (NecklaceReforgeConfirmation.getPendingReforge(senderId) != null) {
                        group.sendMessage("您已经有一个项链重铸请求等待确认，请先回复\"是\"或\"否\"。")
                        return@subscribeAlways
                    }

                    // 检查是否可以重铸
                    val (canReforge, message) = LuckyNecklaceManager.canReforgeNecklace(playerData)
                    if (!canReforge) {
                        group.sendMessage(message)
                        return@subscribeAlways
                    }

                    // 立即扣除汪币
                    playerData.wangCoin -= 20
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成新项链
                    val oldNecklace = playerData.luckyNecklace!!
                    val newNecklace = LuckyNecklaceManager.reforgeNecklace(playerData)

                    // 进入确认状态
                    NecklaceReforgeConfirmation.addPendingReforge(senderId, playerData, oldNecklace, newNecklace)

                    group.sendMessage("${sender.nameCardOrNick}，已扣除20汪币进行幸运项链重铸：\n" +
                        "当前项链：\n${oldNecklace.getFormattedInfo()}\n" +
                        "新项链：\n${newNecklace.getFormattedInfo()}\n" +
                        "是否替换？回复\"是\"替换，回复\"否\"保留原项链（2分钟内有效）")
                }

                message == "/高级重置项链" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有待处理的重铸请求
                    if (NecklaceReforgeConfirmation.getPendingReforge(senderId) != null) {
                        group.sendMessage("您已经有一个项链重铸请求等待确认，请先回复\"是\"或\"否\"。")
                        return@subscribeAlways
                    }

                    // 检查是否可以高级重铸
                    val (canAdvancedReforge, message) = LuckyNecklaceManager.canAdvancedReforgeNecklace(playerData)
                    if (!canAdvancedReforge) {
                        group.sendMessage(message)
                        return@subscribeAlways
                    }

                    // 立即扣除汪币
                    playerData.wangCoin -= 200
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成新项链（高级重置）
                    val oldNecklace = playerData.luckyNecklace!!
                    val newNecklace = LuckyNecklaceManager.advancedReforgeNecklace(playerData)

                    // 进入确认状态
                    NecklaceReforgeConfirmation.addPendingReforge(senderId, playerData, oldNecklace, newNecklace)

                    group.sendMessage("${sender.nameCardOrNick}，已扣除200汪币进行幸运项链高级重铸：\n" +
                        "当前项链：\n${oldNecklace.getFormattedInfo()}\n" +
                        "新项链：\n${newNecklace.getFormattedInfo()}\n" +
                        "是否替换？回复\"是\"替换，回复\"否\"保留原项链（2分钟内有效）")
                }

                message == "/名人堂" -> {
                    val hallOfFame = HallOfFameManager.getFormattedHallOfFame()
                    group.sendMessage(hallOfFame)
                }

                message == "/更新日志" -> {
                    group.sendMessage(UpdateLog.getFormattedLog())
                }

                // 购买道具的处理逻辑
                message.startsWith("/购买道具 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }
                    val itemParam = message.substringAfter("/购买道具 ").trim()
                    val itemName = when (itemParam) {
                        "1" -> "S型宠物辅助职业变更券"
                        "2" -> "鱼饵"
                        "3" -> "炸鱼器"
                        "4" -> "高级炸鱼器"
                        else -> itemParam
                    }
                    val item = Shop.getItemByName(itemName)

                    if (item == null) {
                        group.sendMessage("没有找到名为\"$itemName\"的道具")
                    } else if (playerData.gold < item.price) {
                        group.sendMessage("喵币不足！需要${item.price}喵币，你只有${playerData.gold}喵币")
                    } else if (itemName == "S型宠物辅助职业变更券" && playerData.sPetChangeTickets >= item.maxStack) {
                        group.sendMessage("超过最大持有数量，请先使用一些")
                    } else if (itemName == "鱼饵" && playerData.fishBaitCount >= item.maxStack) {
                        group.sendMessage("超过最大持有数量，请先使用一些")
                    } else if (itemName == "炸鱼器" && playerData.fishBombCount >= item.maxStack) {
                        group.sendMessage("超过最大持有数量，请先使用")
                    } else if (itemName == "高级炸鱼器" && playerData.advancedFishBombCount >= item.maxStack) {
                        group.sendMessage("超过最大持有数量，请先使用")
                    } else {
                        playerData.gold -= item.price

                        // 特殊处理不同道具
                        when (itemName) {
                            "S型宠物辅助职业变更券" -> playerData.sPetChangeTickets += 5
                            "鱼饵" -> playerData.fishBaitCount += 5
                            "炸鱼器" -> playerData.fishBombCount += 1
                            "高级炸鱼器" -> playerData.advancedFishBombCount += 1
                        }

                        PlayerDataManager.savePlayerData(playerData)

                        // 根据购买的道具显示不同的成功消息
                        val successMessage = when (itemName) {
                            "S型宠物辅助职业变更券" -> "购买成功！获得5张S型宠物辅助职业变更券"
                            "鱼饵" -> "购买成功！获得5个鱼饵"
                            "炸鱼器" -> "购买成功！获得1个炸鱼器"
                            "高级炸鱼器" -> "购买成功！获得1个高级炸鱼器"
                            else -> "购买成功！"
                        }

                        group.sendMessage("$successMessage\n花费${item.price}喵币，剩余${playerData.gold}喵币")
                    }
                }

                // 装备强化处理
                message == "/强化" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有装备
                    if (playerData.equipment == null) {
                        group.sendMessage("你没有装备，无法强化")
                        return@subscribeAlways
                    }

                    val equipment = playerData.equipment!!

                    // 检查是否已达到最大强化等级
                    if (equipment.enhanceLevel >= 12) {
                        group.sendMessage("装备已达到最大强化等级(+12)")
                        return@subscribeAlways
                    }

                    // 检查金币是否足够
                    val enhanceCost = equipment.getNextEnhanceCost()
                    if (playerData.gold < enhanceCost) {
                        group.sendMessage("金币不足！${equipment.getDisplayName()} 强化至+${equipment.enhanceLevel + 1}需要${enhanceCost}喵币，你只有${playerData.gold}喵币")
                        return@subscribeAlways
                    }

                    // 扣除金币
                    playerData.gold -= enhanceCost

                    // 检查保底机制：如果失败次数达到25次，本次必定成功
                    val isGuaranteedSuccess = playerData.enhanceFailCount >= 25
                    val successRate = if (isGuaranteedSuccess) 1.0 else equipment.getNextEnhanceSuccessRate()
                    val successRatePercent = (successRate * 100).toInt()
                    val random = Random.nextDouble()

                    if (random <= successRate || isGuaranteedSuccess) {
                        // 强化成功
                        equipment.enhanceLevel++

                        // 重置失败计数
                        playerData.enhanceFailCount = 0

                        group.sendMessage("强化成功！${equipment.name} 强化等级提升至+${equipment.enhanceLevel}")

                        // 如果是保底成功，添加提示
                        if (isGuaranteedSuccess) {
                            group.sendMessage("🎉 保底机制触发，本次强化必定成功！")
                        }
                    } else {
                        // 强化失败 - 等级不变，增加失败计数
                        playerData.enhanceFailCount++

                        group.sendMessage("强化失败！${equipment.getDisplayName()} 的强化等级没有变化")

                        // 检查是否达到30次失败，给予提示
                        if (playerData.enhanceFailCount == 30) {
                            group.sendMessage("💔 你已经连续失败30次了！再失败${25 - playerData.enhanceFailCount}次后将触发保底机制，下次强化必定成功！")
                        } else if (playerData.enhanceFailCount >= 20) {
                            // 从20次开始，每次失败都提示剩余保底次数
                            val remaining = 25 - playerData.enhanceFailCount
                            group.sendMessage("💔 你已经连续失败${playerData.enhanceFailCount}次了！再失败${remaining}次后将触发保底机制，下次强化必定成功！")
                        }
                    }

                    // 保存数据
                    PlayerDataManager.savePlayerData(playerData)
                }

                message == "/世界BOSS" || message == "/wb" -> {
                    val bossInfo = WorldBossManager.getBossInfo()
                    group.sendMessage(bossInfo)
                }

                message == "/查刀神" || message == "/cds" -> {
                    val ranking = WorldBossManager.getDamageRanking()
                    group.sendMessage(ranking)
                }

                message == "/出刀" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val currentPlayerData = playerData

                    // 计算项链加成
                    val necklaceATK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0
                    val necklaceDEF = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.DEF }
                        ?.sumOf { it.value } ?: 0
                    val necklaceLUCK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    val finalATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 包含项链ATK加成

                    val finalDEF = (currentPlayerData.baseDEF +
                        (currentPlayerData.equipment?.getEnhancedDef() ?: 0) +
                        ((currentPlayerData.pet?.def ?: 0) + currentPlayerData.devouredDEF) +
                        (currentPlayerData.relic?.def ?: 0) +
                        currentPlayerData.relicDefBonus +
                        necklaceDEF).toLong()  // 包含项链DEF加成

                    val finalLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()  // 包含项链LUCK加成

                    // 处理攻击
                    val result = WorldBossManager.handleAttack(senderId, sender.nameCardOrNick, finalATK, finalDEF, finalLUCK)
                    group.sendMessage(result)
                }

                    // 添加管理员重置命令
                message == "/重置世界BOSS" -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val result = WorldBossManager.resetBoss()
                    group.sendMessage(result)
                }

                // 新增：增加喵币命令
                message.startsWith("/增加喵币 ") -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val parts = message.substringAfter("/增加喵币 ").trim().split(" ")
                    if (parts.size < 2) {
                        group.sendMessage("使用方法: /增加喵币 [QQ号] [金额]")
                        return@subscribeAlways
                    }

                    val targetQQId = parts[0].toLongOrNull()
                    val amount = parts[1].toIntOrNull()

                    if (targetQQId == null || amount == null) {
                        group.sendMessage("请输入有效的QQ号和金额")
                        return@subscribeAlways
                    }

                    if (amount <= 0) {
                        group.sendMessage("金额必须大于0")
                        return@subscribeAlways
                    }

                    // 获取目标玩家的数据
                    val targetPlayerData = PlayerDataManager.getPlayerData(targetQQId)
                    if (targetPlayerData == null) {
                        group.sendMessage("${targetQQId} 不存在，请检查QQ号是否正确")
                        return@subscribeAlways
                    }

                    // 增加喵币
                    targetPlayerData.gold += amount
                    PlayerDataManager.savePlayerData(targetPlayerData)

                    group.sendMessage("已为 ${targetQQId} 增加 ${amount} 喵币，当前喵币: ${targetPlayerData.gold}")
                }

                message.startsWith("/增加汪币 ") -> {
                    if (senderId != adminId) {
                        group.sendMessage("只有管理员可以执行此命令")
                        return@subscribeAlways
                    }

                    val parts = message.substringAfter("/增加汪币 ").trim().split(" ")
                    if (parts.size < 2) {
                        group.sendMessage("使用方法: /增加汪币 [QQ号] [数量]")
                        return@subscribeAlways
                    }

                    val targetQQId = parts[0].toLongOrNull()
                    val amount = parts[1].toIntOrNull()

                    if (targetQQId == null || amount == null) {
                        group.sendMessage("请输入有效的QQ号和数量")
                        return@subscribeAlways
                    }

                    if (amount <= 0) {
                        group.sendMessage("数量必须大于0")
                        return@subscribeAlways
                    }

                    // 获取目标玩家的数据
                    val targetPlayerData = PlayerDataManager.getPlayerData(targetQQId)
                    if (targetPlayerData == null) {
                        group.sendMessage("${targetQQId} 不存在，请检查QQ号是否正确")
                        return@subscribeAlways
                    }

                    // 增加汪币
                    targetPlayerData.wangCoin += amount
                    PlayerDataManager.savePlayerData(targetPlayerData)

                    // 获取目标玩家在群中的昵称
                    val targetMember = group.get(targetQQId)
                    val targetName = targetMember?.nameCardOrNick ?: "玩家$targetQQId"

                    group.sendMessage("已为 $targetName($targetQQId) 增加 ${amount} 汪币，当前汪币: ${targetPlayerData.wangCoin}")
                }

                message.startsWith("/使用红彩笔") -> {
                    useColorPen(playerData, group, sender, "红")
                }
                message.startsWith("/使用蓝彩笔") -> {
                    useColorPen(playerData, group, sender, "蓝")
                }
                message.startsWith("/使用黄彩笔") -> {
                    useColorPen(playerData, group, sender, "黄")
                }

                message == ("/合成黑彩笔") -> {
                    val playerData = PlayerDataManager.getPlayerData(sender.id) ?: PlayerDataManager.createPlayerData(sender.id)
                    if (playerData.redPenCount >= 1 && playerData.bluePenCount >= 1 && playerData.yellowPenCount >= 1) {
                        playerData.redPenCount -= 1
                        playerData.bluePenCount -= 1
                        playerData.yellowPenCount -= 1
                        playerData.blackPenCount += 1
                        PlayerDataManager.savePlayerData(playerData)
                        subject.sendMessage("合成成功！消耗红、蓝、黄彩笔各1根，获得黑彩笔1根")
                    } else {
                        subject.sendMessage("合成失败！需要红、蓝、黄彩笔各1根")
                    }
                }

                // 使用黑彩笔指令
                message == ("/使用黑彩笔") -> {
                    val playerData = PlayerDataManager.getPlayerData(sender.id) ?: PlayerDataManager.createPlayerData(sender.id)
                    if (playerData.blackPenCount < 1) {
                        subject.sendMessage("你没有黑彩笔！")
                        return@subscribeAlways
                    }

                    if (playerData.relic == null) {
                        subject.sendMessage("你还没有遗物，无法使用黑彩笔！")
                        return@subscribeAlways
                    }

                    // 检查属性是否超过遗物基础值
                    val currentRelic = playerData.relic!!
                    val canIncreaseATK = playerData.relicAtkBonus < currentRelic.atk
                    val canIncreaseDEF = playerData.relicDefBonus < currentRelic.def
                    val canIncreaseLUCK = playerData.relicLuckBonus < currentRelic.luck

                    if (!canIncreaseATK && !canIncreaseDEF && !canIncreaseLUCK) {
                        subject.sendMessage("遗物的所有染色属性都已达到基础值上限，无法使用黑彩笔！")
                        return@subscribeAlways
                    }

                    // 计算实际增加的属性
                    val atkIncrease = if (canIncreaseATK) min(15, currentRelic.atk - playerData.relicAtkBonus) else 0
                    val defIncrease = if (canIncreaseDEF) min(15, currentRelic.def - playerData.relicDefBonus) else 0
                    val luckIncrease = if (canIncreaseLUCK) min(1, currentRelic.luck - playerData.relicLuckBonus) else 0

                    // 应用属性增加
                    playerData.relicAtkBonus += atkIncrease
                    playerData.relicDefBonus += defIncrease
                    playerData.relicLuckBonus += luckIncrease
                    playerData.blackPenCount -= 1

                    PlayerDataManager.savePlayerData(playerData)

                    val increaseMessage = buildString {
                        append("使用黑彩笔成功！固定增加遗物染色属性：")
                        if (atkIncrease > 0) append(" ATK+$atkIncrease")
                        if (defIncrease > 0) append(" DEF+$defIncrease")
                        if (luckIncrease > 0) append(" LUCK+$luckIncrease")
                        append("\n当前遗物染色属性：ATK+${playerData.relicAtkBonus}, DEF+${playerData.relicDefBonus}, LUCK+${playerData.relicLuckBonus}")
                    }
                    subject.sendMessage(increaseMessage)
                }

                message.startsWith("/使用 ") -> {
                    val itemParam = message.substringAfter("/使用 ").trim()
                    val itemName = when (itemParam) {
                        "1" -> "S型宠物辅助职业变更券"
                        "2" -> "鱼饵"
                        "3" -> "炸鱼器"
                        "4" -> "高级炸鱼器"
                        else -> itemParam
                    }

                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    when (itemName) {
                        "S型宠物辅助职业变更券" -> {
                            // 检查玩家是否有宠物
                            if (playerData.pet == null) {
                                group.sendMessage("你没有宠物，无法使用宠物变更券")
                                return@subscribeAlways
                            }

                            // 检查玩家是否有S型宠物变更券
                            if (playerData.sPetChangeTickets < 1) {
                                group.sendMessage("你没有S型宠物辅助职业变更券")
                                return@subscribeAlways
                            }

                            // 定义可变更的S型职业列表
                            val sClassEffects = listOf(
                                PetEffect.THIEF_S,          // 盗贼S
                                PetEffect.PRIEST_S,         // 牧师S
                                PetEffect.TREASURE_HUNTER_S, // 宝藏猎手S
                                PetEffect.BARD_S            // 吟游诗人S
                            )

                            // 随机选择一个S型职业
                            val newEffect = sClassEffects.random()

                            // 获取当前宠物信息
                            val currentPet = playerData.pet!!

                            // 创建新宠物（只变更职业，其他属性不变）
                            val newPet = Pet(
                                currentPet.name,
                                currentPet.atk,
                                currentPet.def,
                                currentPet.luck,
                                currentPet.grade,
                                newEffect
                            )

                            // 更新宠物
                            playerData.pet = newPet
                            // 减少变更券数量
                            playerData.sPetChangeTickets -= 1

                            // 保存数据
                            PlayerDataManager.savePlayerData(playerData)

                            // 获取新职业的中文名称
                            val newEffectName = when (newEffect) {
                                PetEffect.THIEF_S -> "盗贼S"
                                PetEffect.PRIEST_S -> "牧师S"
                                PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
                                PetEffect.BARD_S -> "吟游诗人S"
                                else -> "未知职业"
                            }

                            group.sendMessage("使用成功！宠物职业已变更为: $newEffectName\n" +
                                "剩余S型宠物辅助职业变更券: ${playerData.sPetChangeTickets}")
                        }
                        "鱼饵" -> {
                            // 检查玩家是否有鱼饵
                            if (playerData.fishBaitCount < 1) {
                                group.sendMessage("你没有鱼饵，无法钓鱼！")
                                return@subscribeAlways
                            }

                            // 检查每日使用次数
                            if (playerData.dailyFishBaitUsed >= 10) {
                                group.sendMessage("今天已经使用了10次鱼饵，请明天再来吧！")
                                return@subscribeAlways
                            }

                            // 检查玩家是否在鱼塘中
                            val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                            val pondName = playerRelation.currentPondName

                            if (pondName == null) {
                                group.sendMessage("您还没有加入任何鱼塘！请先创建或加入一个鱼塘才能钓鱼。")
                                return@subscribeAlways
                            }

                            // 使用鱼塘名称加载鱼塘数据（而不是群ID）
                            val pond = FishingManager.loadFishPond(pondName)
                            if (pond == null) {
                                group.sendMessage("鱼塘不存在！")
                                return@subscribeAlways
                            }

                            // 执行钓鱼（使用鱼塘等级）
                            val result = FishingManager.goFishing(playerData, pond.level)

                            // 消耗鱼饵并增加使用次数
                            playerData.fishBaitCount -= 1
                            playerData.dailyFishBaitUsed += 1

                            // 根据钓鱼结果增加属性
                            var attributeMessage = ""
                            if (result.fish.stars >= 6) {
                                // EX鱼：三项属性各增加
                                val increase = result.fish.value
                                playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, increase, playerData.rebirthCount)
                                playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, increase, playerData.rebirthCount)
                                playerData.baseLUCK = increaseAttributeWithLimit(playerData.baseLUCK, increase, playerData.rebirthCount)
                                attributeMessage = "三项基础属性各+$increase"
                            } else {
                                // 普通鱼：根据烹饪方法增加对应属性
                                val increase = result.fish.value
                                when (result.cookingMethod) {
                                    "清蒸" -> {
                                        playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, increase, playerData.rebirthCount)
                                        attributeMessage = "基础ATK+$increase"
                                    }
                                    "红烧" -> {
                                        playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, increase, playerData.rebirthCount)
                                        attributeMessage = "基础DEF+$increase"
                                    }
                                }
                            }

                            // 保存玩家数据
                            PlayerDataManager.savePlayerData(playerData)

                            // 构建完整的消息（包含钓鱼结果、属性奖励和使用次数）
                            val completeMessage = buildString {
                                append(result.message)
                                append("\n")
                                append(attributeMessage)
                                append("\n今日已钓鱼次数: ${playerData.dailyFishBaitUsed}/10")
                            }

                            group.sendMessage(completeMessage)
                        }

                        "炸鱼器" -> {
                            // 检查玩家是否有炸鱼器
                            if (playerData.fishBombCount < 1) {
                                group.sendMessage("你没有炸鱼器，无法使用！")
                                return@subscribeAlways
                            }

                            // 检查今日钓鱼次数是否为0
                            if (playerData.dailyFishBaitUsed > 0) {
                                group.sendMessage("今日已经使用过鱼饵，无法使用炸鱼器！")
                                return@subscribeAlways
                            }

                            // 检查玩家是否在鱼塘中
                            val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                            val pondName = playerRelation.currentPondName

                            if (pondName == null) {
                                group.sendMessage("您还没有加入任何鱼塘！请先创建或加入一个鱼塘才能使用炸鱼器。")
                                return@subscribeAlways
                            }

                            val pond = FishingManager.loadFishPond(pondName)
                            if (pond == null) {
                                group.sendMessage("鱼塘不存在！")
                                return@subscribeAlways
                            }

                            // 新增：检查玩家加入鱼塘时间是否超过1天
                            val joinTime = playerData.pondJoinTime
                            if (joinTime == null) {
                                group.sendMessage("无法确定您加入鱼塘的时间，无法使用炸鱼器！")
                                return@subscribeAlways
                            }

                            try {
                                val joinDateTime = LocalDateTime.parse(joinTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val currentDateTime = LocalDateTime.now()

                                // 使用时间戳计算小时差
                                val joinMillis = joinDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val currentMillis = currentDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val hoursDifference = (currentMillis - joinMillis) / (1000 * 60 * 60)

                                if (hoursDifference < 24) {
                                    val remainingHours = 24 - hoursDifference
                                    group.sendMessage("您加入鱼塘的时间不足24小时，还需${remainingHours}小时才能使用炸鱼器！")
                                    return@subscribeAlways
                                }
                            } catch (e: Exception) {
                                PluginMain.logger.error("解析加入鱼塘时间时出现错误", e)
                                group.sendMessage("解析加入鱼塘时间时出现错误，无法使用炸鱼器！")
                                return@subscribeAlways
                            }

                            // 消耗炸鱼器
                            playerData.fishBombCount -= 1

                            // 执行10次钓鱼
                            val fishingResults = mutableListOf<FishingResult>()
                            var totalATKIncrease = 0
                            var totalDEFIncrease = 0
                            var totalLUCKIncrease = 0

                            // 统计每种鱼的获得情况
                            val fishCounts = mutableMapOf<String, Int>()

                            repeat(10) {
                                val result = FishingManager.goFishing(playerData, pond.level)
                                fishingResults.add(result)

                                // 统计鱼的数量
                                val fishName = result.fish.name
                                fishCounts[fishName] = fishCounts.getOrDefault(fishName, 0) + 1

                                // 计算属性增加
                                if (result.fish.stars >= 6) {
                                    // EX鱼：三项属性各增加
                                    val increase = result.fish.value
                                    totalATKIncrease += increase
                                    totalDEFIncrease += increase
                                    totalLUCKIncrease += increase
                                } else {
                                    // 普通鱼：根据烹饪方法增加对应属性
                                    val increase = result.fish.value
                                    when (result.cookingMethod) {
                                        "清蒸" -> totalATKIncrease += increase
                                        "红烧" -> totalDEFIncrease += increase
                                    }
                                }
                            }

                            // 增加属性
                            playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, totalATKIncrease, playerData.rebirthCount)
                            playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, totalDEFIncrease, playerData.rebirthCount)
                            playerData.baseLUCK = increaseAttributeWithLimit(playerData.baseLUCK, totalLUCKIncrease, playerData.rebirthCount)

                            // 增加使用次数
                            playerData.dailyFishBaitUsed += 10

                            // 保存玩家数据
                            PlayerDataManager.savePlayerData(playerData)

                            // 构建炸鱼器使用结果消息
                            val bombMessage = StringBuilder()
                            bombMessage.append("💣 你使用了炸鱼器！\n")
                            bombMessage.append("一次性获得了10条鱼：\n\n")

                            // 显示每条鱼的详细信息（包括星级和烹饪方法）
                            fishingResults.forEachIndexed { index, result ->
                                val starSymbols = "※".repeat(result.fish.stars)
                                val fishDisplayName = if (result.fish.stars >= 6) {
                                    "[$starSymbols]${result.cookingMethod}${result.fish.name}"
                                } else {
                                    "[$starSymbols]${result.cookingMethod}${result.fish.name}"
                                }

                                // 计算单条鱼的属性增加
                                val singleIncrease = if (result.fish.stars >= 6) {
                                    "三项属性各+${result.fish.value}"
                                } else {
                                    when (result.cookingMethod) {
                                        "清蒸" -> "ATK+${result.fish.value}"
                                        "红烧" -> "DEF+${result.fish.value}"
                                        else -> ""
                                    }
                                }

                                bombMessage.append("${index + 1}. $fishDisplayName ($singleIncrease)\n")
                            }

                            bombMessage.append("\n属性总增加：")
                            if (totalATKIncrease > 0) bombMessage.append(" ATK+$totalATKIncrease")
                            if (totalDEFIncrease > 0) bombMessage.append(" DEF+$totalDEFIncrease")
                            if (totalLUCKIncrease > 0) bombMessage.append(" LUCK+$totalLUCKIncrease")

                            bombMessage.append("\n\n今日已钓鱼次数: ${playerData.dailyFishBaitUsed}/10")

                            // 检查精英鱼生成（在发送消息之前）
                            val eliteFishSpawned = FishingManager.checkEliteFishSpawn(pondName)
                            if (eliteFishSpawned) {
                                // 获取鱼塘等级信息
                                val pondLevel = pond.level
                                val baseProbability = 10
                                val levelBonus = pondLevel * 2
                                val totalProbability = baseProbability + levelBonus

                                bombMessage.append("\n\n🎣 炸鱼过程中惊动了精英鱼！(鱼塘等级${pondLevel}，生成概率${totalProbability}%)\n使用'/鱼塘出刀'指令参与讨伐！")
                            }

                            // 发送完整的消息（包含炸鱼结果和可能的精英鱼生成信息）
                            group.sendMessage(bombMessage.toString())
                        }
                        "高级炸鱼器" -> {
                            if (playerData.advancedFishBombCount < 1) {
                                group.sendMessage("你没有高级炸鱼器，无法使用！")
                                return@subscribeAlways
                            }

                            // 检查今日钓鱼次数是否为0
                            if (playerData.dailyFishBaitUsed > 0) {
                                group.sendMessage("今日已经使用过鱼饵，无法使用高级炸鱼器！")
                                return@subscribeAlways
                            }

                            // 检查玩家是否在鱼塘中
                            val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                            val pondName = playerRelation.currentPondName

                            if (pondName == null) {
                                group.sendMessage("您还没有加入任何鱼塘！请先创建或加入一个鱼塘才能使用高级炸鱼器。")
                                return@subscribeAlways
                            }

                            val pond = FishingManager.loadFishPond(pondName)
                            if (pond == null) {
                                group.sendMessage("鱼塘不存在！")
                                return@subscribeAlways
                            }

                            // 新增：检查玩家加入鱼塘时间是否超过1天
                            val joinTime = playerData.pondJoinTime
                            if (joinTime == null) {
                                group.sendMessage("无法确定您加入鱼塘的时间，无法使用高级炸鱼器！")
                                return@subscribeAlways
                            }

                            try {
                                val joinDateTime = LocalDateTime.parse(joinTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val currentDateTime = LocalDateTime.now()

                                // 使用时间戳计算小时差
                                val joinMillis = joinDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val currentMillis = currentDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val hoursDifference = (currentMillis - joinMillis) / (1000 * 60 * 60)

                                if (hoursDifference < 24) {
                                    val remainingHours = 24 - hoursDifference
                                    group.sendMessage("您加入鱼塘的时间不足24小时，还需${remainingHours}小时才能使用高级炸鱼器！")
                                    return@subscribeAlways
                                }
                            } catch (e: Exception) {
                                PluginMain.logger.error("解析加入鱼塘时间时出现错误", e)
                                group.sendMessage("解析加入鱼塘时间时出现错误，无法使用高级炸鱼器！")
                                return@subscribeAlways
                            }

                            // 消耗高级炸鱼器
                            playerData.advancedFishBombCount -= 1

                            // 执行15次钓鱼
                            val fishingResults = mutableListOf<FishingResult>()
                            var totalATKIncrease = 0
                            var totalDEFIncrease = 0
                            var totalLUCKIncrease = 0

                            // 统计每种鱼的获得情况
                            val fishCounts = mutableMapOf<String, Int>()

                            repeat(15) {  // 改为15次
                                val result = FishingManager.goFishing(playerData, pond.level)
                                fishingResults.add(result)

                                // 统计鱼的数量
                                val fishName = result.fish.name
                                fishCounts[fishName] = fishCounts.getOrDefault(fishName, 0) + 1

                                // 计算属性增加
                                if (result.fish.stars >= 6) {
                                    // EX鱼：三项属性各增加
                                    val increase = result.fish.value
                                    totalATKIncrease += increase
                                    totalDEFIncrease += increase
                                    totalLUCKIncrease += increase
                                } else {
                                    // 普通鱼：根据烹饪方法增加对应属性
                                    val increase = result.fish.value
                                    when (result.cookingMethod) {
                                        "清蒸" -> totalATKIncrease += increase
                                        "红烧" -> totalDEFIncrease += increase
                                    }
                                }
                            }

                            // 增加属性
                            playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, totalATKIncrease, playerData.rebirthCount)
                            playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, totalDEFIncrease, playerData.rebirthCount)
                            playerData.baseLUCK = increaseAttributeWithLimit(playerData.baseLUCK, totalLUCKIncrease, playerData.rebirthCount)

                            // 增加使用次数（注意：这里还是+10，不是+15）
                            playerData.dailyFishBaitUsed += 10

                            // 保存玩家数据
                            PlayerDataManager.savePlayerData(playerData)

                            // 构建高级炸鱼器使用结果消息
                            val bombMessage = StringBuilder()
                            bombMessage.append("💣💥 你使用了高级炸鱼器！\n")
                            bombMessage.append("一次性获得了15条鱼：\n\n")

                            // 显示每条鱼的详细信息（包括星级和烹饪方法）
                            fishingResults.forEachIndexed { index, result ->
                                val starSymbols = "※".repeat(result.fish.stars)
                                val fishDisplayName = if (result.fish.stars >= 6) {
                                    "[$starSymbols]${result.cookingMethod}${result.fish.name}"
                                } else {
                                    "[$starSymbols]${result.cookingMethod}${result.fish.name}"
                                }

                                // 计算单条鱼的属性增加
                                val singleIncrease = if (result.fish.stars >= 6) {
                                    "三项属性各+${result.fish.value}"
                                } else {
                                    when (result.cookingMethod) {
                                        "清蒸" -> "ATK+${result.fish.value}"
                                        "红烧" -> "DEF+${result.fish.value}"
                                        else -> ""
                                    }
                                }

                                bombMessage.append("${index + 1}. $fishDisplayName ($singleIncrease)\n")
                            }

                            bombMessage.append("\n属性总增加：")
                            if (totalATKIncrease > 0) bombMessage.append(" ATK+$totalATKIncrease")
                            if (totalDEFIncrease > 0) bombMessage.append(" DEF+$totalDEFIncrease")
                            if (totalLUCKIncrease > 0) bombMessage.append(" LUCK+$totalLUCKIncrease")

                            bombMessage.append("\n\n今日已钓鱼次数: ${playerData.dailyFishBaitUsed}/10")

                            // 检查精英鱼生成 - 高级炸鱼器100%生成精英鱼
                            val eliteFishSpawned = true  // 强制为true，100%生成
                            if (eliteFishSpawned) {
                                // 生成精英鱼
                                FishingManager.checkEliteFishSpawn(pondName, true)

                                // 获取鱼塘等级信息
                                val pondLevel = pond.level
                                val baseProbability = 100  // 高级炸鱼器100%概率
                                val levelBonus = pondLevel * 2
                                val totalProbability = baseProbability + levelBonus

                                bombMessage.append("\n\n🎣💥 高级炸鱼器威力巨大！必定惊动了精英鱼！(鱼塘等级${pondLevel})\n使用'/鱼塘出刀'指令参与讨伐！")
                            }

                            // 发送完整的消息（包含炸鱼结果和精英鱼生成信息）
                            group.sendMessage(bombMessage.toString())
                        }

                        else -> {
                            group.sendMessage("无法使用该道具或道具不存在")
                        }
                    }
                }

                message.startsWith("/购买 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val equipmentName = message.substringAfter("/购买 ").trim()
                    val equipment = Shop.getEquipmentByName(equipmentName)

                    // 检查是否是特殊装备
                    val isSpecialEquipment = Shop.getSpecialEquipmentByName(equipmentName) != null
                    if (isSpecialEquipment) {
                        group.sendMessage("特殊装备无法在商店购买，只能通过隐藏副本获得。")
                    } else if (equipment == null) {
                        group.sendMessage("没有找到名为\"$equipmentName\"的装备")
                    } else if (playerData.gold < equipment.price) {
                        group.sendMessage("喵币不足！需要${equipment.price}喵币，你只有${playerData.gold}喵币")
                    } else {
                        // 计算返还喵币（如果有旧装备）
                        val refund = playerData.equipment?.let {
                            // 只返还基础价格，不考虑强化价值
                            (it.price * 1).toInt()
                        } ?: 0
                        val totalCost = equipment.price - refund

                        playerData.gold -= totalCost
                        val oldEnhanceLevel = playerData.equipment?.enhanceLevel ?: 0
                        playerData.equipment = equipment.copy(enhanceLevel = oldEnhanceLevel)
                        PlayerDataManager.savePlayerData(playerData)

                        val refundMsg = if (refund > 0) "出售旧装备返还 $refund 喵币，" else ""
                        group.sendMessage("购买成功！${refundMsg}实际花费${totalCost}喵币，剩余${playerData.gold}喵币")
                    }
                }

                // 在 PluginMain.kt 的命令处理部分添加：
                message == "/汪币商店" || message == "/wbsd" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val itemList = WangCoinShop.itemList.joinToString("\n") { item ->
                        "${item.name} - 价格: ${item.price}汪币/个 (${item.description})"
                    }

                    group.sendMessage("💰 汪币商店:\n$itemList\n\n使用说明:\n• \"/汪币购买 道具名\" - 购买1个道具\n• \"/汪币购买 道具名 数量\" - 批量购买道具\n• 一次最多购买99个道具\n• 喵币兑换不支持批量购买")
                }

                message.startsWith("/汪币购买 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val params = message.substringAfter("/汪币购买 ").trim().split(" ")
                    if (params.isEmpty()) {
                        group.sendMessage("使用方法: /汪币购买 [道具名] [数量]")
                        return@subscribeAlways
                    }

                    val itemName = params[0]
                    val quantity = if (params.size > 1) {
                        params[1].toIntOrNull() ?: 1
                    } else {
                        1
                    }

                    // 验证数量
                    if (quantity <= 0) {
                        group.sendMessage("购买数量必须大于0")
                        return@subscribeAlways
                    }

                    if (quantity > 99) {
                        group.sendMessage("一次最多购买99个道具")
                        return@subscribeAlways
                    }

                    val item = WangCoinShop.getItemByName(itemName)

                    if (item == null) {
                        group.sendMessage("没有找到名为\"$itemName\"的道具")
                    } else {
                        val totalPrice = item.price * quantity

                        if (playerData.wangCoin < totalPrice) {
                            group.sendMessage("汪币不足！需要${totalPrice}汪币，你只有${playerData.wangCoin}汪币")
                        } else {
                            playerData.wangCoin -= totalPrice

                            // 根据购买的道具增加对应物品
                            when (itemName) {
                                "神奇小药丸" -> playerData.miraclePillCount += quantity
                                "红彩笔" -> playerData.redPenCount += quantity
                                "蓝彩笔" -> playerData.bluePenCount += quantity
                                "黄彩笔" -> playerData.yellowPenCount += quantity
                                "黑彩笔" -> playerData.blackPenCount += quantity
                                "5000喵币" -> {
                                    // 特殊处理喵币兑换，数量参数对喵币兑换无效，固定兑换5000喵币
                                    if (quantity > 1) {
                                        group.sendMessage("喵币兑换每次只能兑换1份，数量参数无效")
                                        playerData.wangCoin += totalPrice - item.price
                                        playerData.gold += 5000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    } else {
                                        playerData.gold += 5000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    }
                                    PlayerDataManager.savePlayerData(playerData)
                                    return@subscribeAlways
                                }
                                "50000喵币" -> {
                                    // 特殊处理喵币兑换，数量参数对喵币兑换无效，固定兑换5000喵币
                                    if (quantity > 1) {
                                        group.sendMessage("喵币兑换每次只能兑换1份，数量参数无效")
                                        playerData.wangCoin += totalPrice - item.price
                                        playerData.gold += 50000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    } else {
                                        playerData.gold += 50000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    }
                                    PlayerDataManager.savePlayerData(playerData)
                                    return@subscribeAlways
                                }
                                "500000喵币" -> {
                                    // 特殊处理喵币兑换，数量参数对喵币兑换无效，固定兑换5000喵币
                                    if (quantity > 1) {
                                        group.sendMessage("喵币兑换每次只能兑换1份，数量参数无效")
                                        playerData.wangCoin += totalPrice - item.price
                                        playerData.gold += 500000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    } else {
                                        playerData.gold += 500000
                                        group.sendMessage("兑换成功！获得5000喵币\n花费${item.price}汪币，剩余${playerData.wangCoin}汪币\n当前喵币：${playerData.gold}")
                                    }
                                    PlayerDataManager.savePlayerData(playerData)
                                    return@subscribeAlways
                                }
                            }

                            PlayerDataManager.savePlayerData(playerData)

                            val quantityText = if (quantity > 1) "${quantity}个" else ""
                            group.sendMessage("购买成功！获得${quantityText}${itemName}\n花费${totalPrice}汪币，剩余${playerData.wangCoin}汪币")
                        }
                    }
                }

                // 添加鱼塘相关命令
                message.startsWith("/创建鱼塘 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有遗物
                    if (playerData.relic == null) {
                        group.sendMessage("你还没有遗物，无法创建鱼塘")
                        return@subscribeAlways
                    }

                    // 检查转生次数
                    if (playerData.rebirthCount < 3) {
                        group.sendMessage("创建鱼塘需要3次以上转生次数！")
                        return@subscribeAlways
                    }

                    // 检查喵币是否足够
                    if (playerData.gold < 5000) {
                        group.sendMessage("创建鱼塘需要5000喵币，你只有${playerData.gold}喵币！")
                        return@subscribeAlways
                    }

                    val pondName = message.substringAfter("/创建鱼塘 ").trim()

                    if (pondName.isEmpty()) {
                        group.sendMessage("请输入鱼塘名称！")
                        return@subscribeAlways
                    }

                    // 检查鱼塘名称长度（8个汉字以内）
                    if (pondName.length > 8) {
                        group.sendMessage("鱼塘名称不能超过8个汉字！")
                        return@subscribeAlways
                    }

                    // 检查鱼塘名称是否只包含中文、英文、数字和下划线
                    val validNameRegex = Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9_]+$")
                    if (!validNameRegex.matches(pondName)) {
                        group.sendMessage("鱼塘名称只能包含中文、英文、数字和下划线！")
                        return@subscribeAlways
                    }

                    // 检查玩家是否已有鱼塘
                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    if (playerRelation.currentPondName != null) {
                        group.sendMessage("您已经加入了鱼塘，请先离开当前鱼塘再创建新鱼塘！")
                        return@subscribeAlways
                    }

                    // 创建鱼塘
                    val success = FishingManager.createFishPond(pondName, senderId)

                    if (success) {
                        // 扣除喵币
                        playerData.gold -= 5000
                        // 保存玩家数据
                        PlayerDataManager.savePlayerData(playerData)

                        group.sendMessage("鱼塘 \"$pondName\" 创建成功！消耗5000喵币，您已成为该鱼塘的管理者。")
                    } else {
                        group.sendMessage("鱼塘名称 \"$pondName\" 已存在，请换一个名称！")
                    }
                }

                // 销毁鱼塘命令
                message == "/销毁鱼塘" || message == "/摧毁鱼塘" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    val pondName = playerRelation.currentPondName

                    if (pondName == null) {
                        group.sendMessage("您还没有加入任何鱼塘！")
                        return@subscribeAlways
                    }

                    // 检查是否是管理者
                    val pond = FishingManager.loadFishPond(pondName)
                    if (pond?.managerId != senderId) {
                        group.sendMessage("只有鱼塘管理者才能销毁鱼塘！")
                        return@subscribeAlways
                    }

                    // 销毁鱼塘
                    val success = FishingManager.destroyFishPond(pondName, senderId)

                    if (success) {
                        group.sendMessage("鱼塘 \"$pondName\" 已成功销毁！")
                    } else {
                        group.sendMessage("销毁鱼塘失败，请稍后再试！")
                    }
                }

                // 加入鱼塘命令
                message.startsWith("/加入鱼塘 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有遗物
                    if (playerData.relic == null) {
                        group.sendMessage("你还没有遗物，无法加入鱼塘")
                        return@subscribeAlways
                    }

                    // 检查转生次数
                    if (playerData.rebirthCount < 3) {
                        group.sendMessage("加入鱼塘需要3次以上转生次数！")
                        return@subscribeAlways
                    }

                    val pondName = message.substringAfter("/加入鱼塘 ").trim()

                    if (pondName.isEmpty()) {
                        group.sendMessage("请输入鱼塘名称！")
                        return@subscribeAlways
                    }

                    // 检查玩家是否已有鱼塘
                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    if (playerRelation.currentPondName != null) {
                        group.sendMessage("您已经加入了鱼塘，请先离开当前鱼塘再加入新鱼塘！")
                        return@subscribeAlways
                    }

                    // 加入鱼塘
                    val success = FishingManager.joinFishPond(pondName, senderId)

                    if (success) {
                        group.sendMessage("成功加入鱼塘 \"$pondName\"！")
                    } else {
                        group.sendMessage("加入鱼塘失败！可能鱼塘不存在、已满员或您已在鱼塘中。")
                    }
                }

                // 离开鱼塘命令
                message == "/离开鱼塘" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val success = FishingManager.leaveFishPond(senderId)

                    if (success) {
                        group.sendMessage("已成功离开当前鱼塘！")
                    } else {
                        group.sendMessage("离开鱼塘失败！您可能没有加入任何鱼塘或是鱼塘管理者。")
                    }
                }

                // 踢出玩家命令
                message.startsWith("/踢出鱼塘 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val targetPlayerIdStr = message.substringAfter("/踢出鱼塘 ").trim()
                    val targetPlayerId = targetPlayerIdStr.toLongOrNull()

                    if (targetPlayerId == null) {
                        group.sendMessage("请输入有效的玩家ID(QQ号)！")
                        return@subscribeAlways
                    }

                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    val pondName = playerRelation.currentPondName

                    if (pondName == null) {
                        group.sendMessage("您还没有加入任何鱼塘！")
                        return@subscribeAlways
                    }

                    // 检查是否是管理者
                    val pond = FishingManager.loadFishPond(pondName)
                    if (pond?.managerId != senderId) {
                        group.sendMessage("只有鱼塘管理者才能踢出玩家！")
                        return@subscribeAlways
                    }

                    // 踢出玩家
                    val success = FishingManager.kickPlayerFromPond(pondName, senderId, targetPlayerId)

                    if (success) {
                        val pondmemberssize = pond.members.size - 1
                        group.sendMessage("已成功将玩家 $targetPlayerId 踢出鱼塘！当前成员：$pondmemberssize/15人")
                    } else {
                        group.sendMessage("踢出玩家失败！可能玩家不在鱼塘中或是您不能踢出自己。")
                    }
                }

                // 查看鱼塘命令
                message == "/查看鱼塘" || message == "/ckyt" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val pondInfo = FishingManager.getFishPondInfo(senderId)
                    group.sendMessage(pondInfo)
                }

                // 修改修建鱼塘命令
                message.startsWith("/修建鱼塘 ") || message.startsWith("/xjyt ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否有遗物
                    if (playerData.relic == null) {
                        group.sendMessage("你还没有遗物，无法修建")
                        return@subscribeAlways
                    }

                    // 检查转生次数
                    if (playerData.rebirthCount < 3) {
                        group.sendMessage("修建鱼塘需要3次以上转生次数！")
                        return@subscribeAlways
                    }

                    val amountStr = if (message.startsWith("/修建鱼塘 ")) {
                        message.substringAfter("/修建鱼塘 ").trim()
                    } else {
                        message.substringAfter("/xjyt ").trim()
                    }

                    val amount = amountStr.toIntOrNull()
                    if (amount == null || amount <= 0) {
                        group.sendMessage("请输入有效的金额！")
                        return@subscribeAlways
                    }

                    // 检查玩家是否在鱼塘中
                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    val pondName = playerRelation.currentPondName

                    if (pondName == null) {
                        group.sendMessage("您还没有加入任何鱼塘！请先创建或加入一个鱼塘。")
                        return@subscribeAlways
                    }

                    // 使用新的升级逻辑
                    val upgradeResult = FishingManager.upgradeFishPond(playerData, pondName, amount)

                    if (upgradeResult.success) {
                        group.sendMessage(upgradeResult.message)
                    } else {
                        group.sendMessage(upgradeResult.message)
                    }

                    // 保存玩家数据
                    PlayerDataManager.savePlayerData(playerData)
                }

                message.startsWith("/鱼塘出刀 ") || message.startsWith("/ytcd ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 解析验证码
                    val inputCaptcha = message.substringAfter("/鱼塘出刀 ").trim()

                    // 检查玩家是否在鱼塘中
                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    val pondName = playerRelation.currentPondName

                    if (pondName == null) {
                        group.sendMessage("您还没有加入任何鱼塘！")
                        return@subscribeAlways
                    }

                    // 获取精英鱼信息
                    val eliteFish = EliteFishManager.getEliteFish(pondName)
                    if (eliteFish == null) {
                        group.sendMessage("您的鱼塘目前没有精英鱼！")
                        return@subscribeAlways
                    }

                    // 生成验证码：精英鱼血量的最后两位
                    val expectedCaptcha = String.format("%02d", eliteFish.currentHp % 100)

                    // 验证验证码
                    if (inputCaptcha != expectedCaptcha) {
                        // 验证码错误，不予响应（不发送任何消息）
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性（包含项链加成）
                    val currentPlayerData = playerData

                    // 计算项链加成
                    val necklaceATK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.ATK }
                        ?.sumOf { it.value } ?: 0
                    val necklaceDEF = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.DEF }
                        ?.sumOf { it.value } ?: 0
                    val necklaceLUCK = currentPlayerData.luckyNecklace?.attributes
                        ?.filter { it.type == NecklaceAttributeType.LUCK }
                        ?.sumOf { it.value } ?: 0

                    val finalATK = (currentPlayerData.baseATK +
                        (currentPlayerData.equipment?.getEnhancedAtk() ?: 0) +
                        ((currentPlayerData.pet?.atk ?: 0) + currentPlayerData.devouredATK) +
                        (currentPlayerData.relic?.atk ?: 0) +
                        currentPlayerData.relicAtkBonus +
                        necklaceATK).toLong()  // 包含项链ATK加成

                    val finalDEF = (currentPlayerData.baseDEF +
                        (currentPlayerData.equipment?.getEnhancedDef() ?: 0) +
                        ((currentPlayerData.pet?.def ?: 0) + currentPlayerData.devouredDEF) +
                        (currentPlayerData.relic?.def ?: 0) +
                        currentPlayerData.relicDefBonus +
                        necklaceDEF).toLong()  // 包含项链DEF加成

                    val finalLUCK = (currentPlayerData.baseLUCK +
                        (currentPlayerData.equipment?.getEnhancedLuck() ?: 0) +
                        ((currentPlayerData.pet?.luck ?: 0) + currentPlayerData.devouredLUCK) +
                        (currentPlayerData.relic?.luck ?: 0) +
                        currentPlayerData.relicLuckBonus +
                        necklaceLUCK).toLong()

                    // 处理精英鱼攻击
                    val result = EliteFishManager.handleAttack(senderId, sender.nameCardOrNick, pondName, finalATK, finalDEF, finalLUCK)
                    group.sendMessage(result)
                }

                // PluginMain.kt - 查看精英鱼命令处理部分
                message == "/查看精英鱼" || message == "/ckjyy" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查玩家是否在鱼塘中
                    val playerRelation = FishingManager.loadPlayerPondRelation(senderId)
                    val pondName = playerRelation.currentPondName

                    if (pondName == null) {
                        group.sendMessage("您还没有加入任何鱼塘！")
                        return@subscribeAlways
                    }

                    val eliteFishInfo = EliteFishManager.getEliteFishInfo(pondName)

                    // 获取鱼塘等级和生成概率信息
                    val pond = FishingManager.loadFishPond(pondName)
                    val pondLevel = pond?.level ?: 0
                    val baseProbability = 10
                    val levelBonus = pondLevel * 2
                    val totalProbability = baseProbability + levelBonus

                    // 获取当前验证码（精英鱼血量最后两位）
                    val eliteFish = EliteFishManager.getEliteFish(pondName)
                    val currentCaptcha = if (eliteFish != null) {
                        String.format("%02d", eliteFish.currentHp % 100)
                    } else {
                        "无精英鱼"
                    }

                    val probabilityInfo = "\n\n炸鱼器炸出精英鱼概率: ${totalProbability}%\n" +
                        "当前出刀验证码: $currentCaptcha\n"

                    group.sendMessage(eliteFishInfo + probabilityInfo)
                }

                message.startsWith("/赠送红彩笔 ") -> {
                    handleColorPenTrade(message, senderId, group, "红")
                }
                message.startsWith("/赠送蓝彩笔 ") -> {
                    handleColorPenTrade(message, senderId, group, "蓝")
                }
                message.startsWith("/赠送黄彩笔 ") -> {
                    handleColorPenTrade(message, senderId, group, "黄")
                }
                message.startsWith("/赠送黑彩笔 ") -> {
                    handleColorPenTrade(message, senderId, group, "黑")
                }

                message == "/切换返回消息" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 切换消息返回样式
                    val currentSetting = playerData.messageBack
                    val newSetting = if (currentSetting == 1) 0 else 1
                    playerData.messageBack = newSetting

                    PlayerDataManager.savePlayerData(playerData)

                    val messageType = if (newSetting == 1) "图片" else "文字"
                    group.sendMessage("已切换消息返回样式为: $messageType")
                }
            }
        }
    }

    private fun isGroupEnabled(groupId: Long): Boolean {
        return WhitelistConfig.enabledGroups.contains(groupId)
    }

    // 属性增加时检查上限
    private fun increaseAttributeWithLimit(currentValue: Int, increase: Int, rebirthCount: Int): Int {
        val maxValue = 225 + 10 * rebirthCount

        // 如果当前值已经超过上限，先将其设置为上限值
        val normalizedCurrent = if (currentValue > maxValue) {
            maxValue
        } else {
            currentValue
        }

        val newValue = normalizedCurrent + increase
        return if (newValue > maxValue) {
            maxValue
        } else {
            newValue
        }
    }

    // 在 PluginMain.kt 中添加彩笔使用函数
    private suspend fun useColorPen(playerData: PlayerData?, group: Group, sender: User, color: String) {
        if (playerData == null) {
            group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
            return
        }

        // 检查是否有遗物
        if (playerData.relic == null) {
            group.sendMessage("你还没有遗物，无法使用彩笔！")
            return
        }

        when (color) {
            "红" -> {
                if (playerData.redPenCount < 1) {
                    group.sendMessage("你没有红彩笔！")
                    return
                }
                playerData.redPenCount -= 1
                val multiplier = Random.nextDouble(1.0, 2.0)
                val newBonus = (playerData.relic!!.atk * multiplier).toInt() - playerData.relic!!.atk
                playerData.relicAtkBonus = newBonus
                group.sendMessage("使用红彩笔成功！遗物攻击属性加成变为：+$newBonus")
            }
            "蓝" -> {
                if (playerData.bluePenCount < 1) {
                    group.sendMessage("你没有蓝彩笔！")
                    return
                }
                playerData.bluePenCount -= 1
                val multiplier = Random.nextDouble(1.0, 2.0)
                val newBonus = (playerData.relic!!.def * multiplier).toInt() - playerData.relic!!.def
                playerData.relicDefBonus = newBonus
                group.sendMessage("使用蓝彩笔成功！遗物防御属性加成变为：+$newBonus")
            }
            "黄" -> {
                if (playerData.yellowPenCount < 1) {
                    group.sendMessage("你没有黄彩笔！")
                    return
                }
                playerData.yellowPenCount -= 1
                val multiplier = Random.nextDouble(1.0, 2.0)
                val newBonus = (playerData.relic!!.luck * multiplier).toInt() - playerData.relic!!.luck
                playerData.relicLuckBonus = newBonus
                group.sendMessage("使用黄彩笔成功！遗物幸运属性加成变为：+$newBonus")
            }
            else -> {
                group.sendMessage("未知的彩笔颜色！")
                return
            }
        }

        PlayerDataManager.savePlayerData(playerData)
    }
    // 处理彩笔交易
    private suspend fun handleColorPenTrade(message: String, senderId: Long, group: Group, color: String) {
        val parts = message.substringAfter("/赠送${color}彩笔 ").trim().split(" ")
        if (parts.isEmpty()) {
            group.sendMessage("使用方法: /赠送${color}彩笔 [QQ号]")
            return
        }

        val targetQQId = parts[0].toLongOrNull()
        if (targetQQId == null) {
            group.sendMessage("请输入有效的QQ号")
            return
        }

        // 检查是否赠送给自己
        if (targetQQId == senderId) {
            group.sendMessage("不能赠送彩笔给自己")
            return
        }

        // 获取发送者数据
        val senderData = PlayerDataManager.getPlayerData(senderId)
        if (senderData == null) {
            group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
            return
        }

        // 检查发送者是否有足够的彩笔
        val senderPenCount = when (color) {
            "红" -> senderData.redPenCount
            "蓝" -> senderData.bluePenCount
            "黄" -> senderData.yellowPenCount
            "黑" -> senderData.blackPenCount
            else -> 0
        }

        if (senderPenCount < 1) {
            group.sendMessage("你没有${color}彩笔可以赠送")
            return
        }

        // 获取目标玩家数据
        val targetData = PlayerDataManager.getPlayerData(targetQQId)
        if (targetData == null) {
            group.sendMessage("目标玩家不存在或未注册")
            return
        }

        // 执行交易
        when (color) {
            "红" -> {
                senderData.redPenCount -= 1
                targetData.redPenCount += 1
            }
            "蓝" -> {
                senderData.bluePenCount -= 1
                targetData.bluePenCount += 1
            }
            "黄" -> {
                senderData.yellowPenCount -= 1
                targetData.yellowPenCount += 1
            }
            "黑" -> {
                senderData.blackPenCount -= 1
                targetData.blackPenCount += 1
            }
        }

        // 保存数据
        PlayerDataManager.savePlayerData(senderData)
        PlayerDataManager.savePlayerData(targetData)

        // 获取目标玩家在群中的昵称
        val targetMember = group.get(targetQQId)
        val targetName = targetMember?.nameCardOrNick ?: "玩家$targetQQId"

        group.sendMessage("成功赠送1支${color}彩笔给 $targetName")
    }

    // 在 PluginMain 中添加遗物生成器
    object RelicGenerator {
        // 生成随机遗物
        fun generateRandomRelic(): Relic {
            val atk = Random.nextInt(20, 151) // 20-150
            val def = Random.nextInt(20, 151) // 20-150
            val luck = Random.nextInt(10, 21) // 10-20

            // 计算遗物品级
            val grade = when {
                atk + def + luck >= 300 -> "SS"
                atk + def + luck >= 270 -> "S"
                atk + def + luck >= 230 -> "A"
                atk + def + luck >= 180 -> "B"
                atk + def + luck >= 120 -> "C"
                else -> "D"
            }

            // 生成遗物名称
            val name = when (grade) {
                "SS" -> listOf("创世神器", "灭世魔器", "永恒圣物").random()
                "S" -> listOf("神之右手", "魔之左手", "天命之器").random()
                "A" -> listOf("龙魂之心", "凤血之羽", "麒麟之角").random()
                "B" -> listOf("星辰碎片", "月光精华", "日光结晶").random()
                "C" -> listOf("觉之瞳", "万宝槌", "彩虹碎片").random()
                else -> listOf("三攻烛台", "三防圣杯", "三血古书", "柔情猫娘").random()
            }

            return Relic(name, atk, def, luck, grade)
        }

        // 格式化遗物信息
        fun formatRelicInfo(relic: Relic): String {
            return "${relic.name}(${relic.grade}级)\n" +
                "ATK+${relic.atk}\n" +
                "DEF+${relic.def}\n" +
                "LUCK+${relic.luck}"
        }
    }
    // 在 PluginMain 中添加遗物确认管理器
    object RelicConfirmation {
        private val pendingRelicReset: MutableMap<Long, Triple<PlayerData, Relic, String>> = mutableMapOf()
        private val pendingTimes: MutableMap<Long, Long> = mutableMapOf()
        private const val TIMEOUT_MS = 2 * 60 * 1000

        fun addPendingReset(userId: Long, playerData: PlayerData, newRelic: Relic, resetType: String) {
            pendingRelicReset[userId] = Triple(playerData, newRelic, resetType)
            pendingTimes[userId] = System.currentTimeMillis()
        }

        fun removePendingReset(userId: Long) {
            pendingRelicReset.remove(userId)
            pendingTimes.remove(userId)
        }

        fun getPendingReset(userId: Long): Triple<PlayerData, Relic, String>? {
            val requestTime = pendingTimes[userId] ?: return null

            // 检查是否超时
            if (System.currentTimeMillis() - requestTime > TIMEOUT_MS) {
                removePendingReset(userId)
                return null
            }

            return pendingRelicReset[userId]
        }

        // 添加定时清理超时请求的方法
        fun cleanupExpiredRequests() {
            val currentTime = System.currentTimeMillis()
            val expiredIds = pendingTimes.filter { currentTime - it.value > TIMEOUT_MS }.keys

            expiredIds.forEach { userId ->
                removePendingReset(userId)
            }
        }
    }
    // 在 PluginMain 中添加重铸幸运项链确认管理器
    object NecklaceReforgeConfirmation {
        private val pendingReforge: MutableMap<Long, Triple<PlayerData, LuckyNecklace, LuckyNecklace>> = mutableMapOf()
        private val pendingTimes: MutableMap<Long, Long> = mutableMapOf()
        private const val TIMEOUT_MS = 2 * 60 * 1000 // 2分钟超时

        fun addPendingReforge(userId: Long, playerData: PlayerData, oldNecklace: LuckyNecklace, newNecklace: LuckyNecklace) {
            pendingReforge[userId] = Triple(playerData, oldNecklace, newNecklace)
            pendingTimes[userId] = System.currentTimeMillis()
        }

        fun removePendingReforge(userId: Long) {
            pendingReforge.remove(userId)
            pendingTimes.remove(userId)
        }

        fun getPendingReforge(userId: Long): Triple<PlayerData, LuckyNecklace, LuckyNecklace>? {
            val requestTime = pendingTimes[userId] ?: return null

            // 检查是否超时
            if (System.currentTimeMillis() - requestTime > TIMEOUT_MS) {
                removePendingReforge(userId)
                return null
            }

            return pendingReforge[userId]
        }

        // 添加定时清理超时请求的方法
        fun cleanupExpiredRequests() {
            val currentTime = System.currentTimeMillis()
            val expiredIds = pendingTimes.filter { currentTime - it.value > TIMEOUT_MS }.keys

            expiredIds.forEach { userId ->
                removePendingReforge(userId)
            }
        }
    }

    // 生成随机宠物
    private fun generateRandomPet(): Pet {
        val atk = Random.nextInt(10, 45) // 10-44
        val def = Random.nextInt(10, 45) // 10-44
        val luck = Random.nextInt(5, 11) // 5-10

        // 计算宠物等级
        val grade = when {
            atk + def + 5*luck >= 145 -> "SS"
            atk + def + 5*luck >= 135 -> "S"
            atk + def + 5*luck >= 120 -> "A"
            atk + def + 5*luck >= 100 -> "B"
            atk + def + 5*luck >= 75 -> "C"
            else -> "D"
        }

        // 生成宠物名称
        val name = when (grade) {
            "SS" -> listOf("玄武", "朱雀", "白虎", "青龙").random()
            "S" -> listOf("腾蛇", "麒麟", "朱鹤", "椒图").random()
            "A" -> listOf("雪狸", "赢鱼", "霸下").random()
            "B" -> listOf("豹", "狼", "熊").random()
            "C" -> listOf("猫", "狗", "兔").random()
            else -> listOf("猪", "鼠", "鸭").random()
        }


        // 随机分配特殊效果
        val allEffects = PetEffect.values()
        val specialEffect = allEffects.random()


        return Pet(name, atk, def, luck, grade, specialEffect)
    }

    // 格式化宠物信息
    private fun formatPetInfo(pet: Pet): String {
        // 获取特殊效果的中文名称
        val effectName = when (pet.specialEffect) {
            PetEffect.WARRIOR -> "战士"
            PetEffect.WARRIOR_S -> "战士S"
            PetEffect.ARCHER -> "弓手"
            PetEffect.ARCHER_S -> "弓手S"
            PetEffect.THIEF -> "盗贼"
            PetEffect.THIEF_S -> "盗贼S"
            PetEffect.PRIEST -> "牧师"
            PetEffect.PRIEST_S -> "牧师S"
            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
            PetEffect.BARD -> "吟游诗人"
            PetEffect.BARD_S -> "吟游诗人S"
            null -> ""
        }

        // 如果有特殊效果，在宠物名前显示
        val petNameDisplay = if (effectName.isNotEmpty()) {
            "[$effectName]${pet.name}"
        } else {
            pet.name
        }

        return "$petNameDisplay (${pet.grade}级, ATK+${pet.atk}, DEF+${pet.def}, LUCK+${pet.luck})"
    }
}