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
import kotlin.time.Duration

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "org.example.mmorpg",
        name = "卧槽真来PK吗",
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

    // PK冷却时间（3小时）
    private val pkCooldown = 3 * 60 * 60 * 1000

    // 管理员ID
    private val adminId = 335693890L

    // 添加周末狂欢检测函数
    private fun isWeekendBonus(): Boolean {
        val today = LocalDate.now()
        return today.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            today.dayOfWeek == java.time.DayOfWeek.SUNDAY
    }

    // 添加获取周末狂欢消息的函数
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
                name.contains("[UR]") -> 3
                name.contains("[SSR]") -> 2
                name.contains("[SR]") -> 1
                else -> 0
            }
        }

        // 检查装备等级限制
        val currentLevel = getEquipmentLevel(currentEquipment.name)
        val newLevel = getEquipmentLevel(equipment.name)

        return newLevel > currentLevel // 只有新装备等级更高才可以获取
    }

    // 获取装备等级
    private fun getEquipmentLevel(equipmentName: String): Int {
        return when {
            equipmentName.contains("[UR]") -> 3
            equipmentName.contains("[SSR]") -> 2
            equipmentName.contains("[SR]") -> 1
            else -> 0
        }
    }

    object PetEffectCalculator {
        // 计算队伍中所有宠物的效果加成
        fun calculateTeamEffects(team: Team): TeamPetEffects {
            val effects = TeamPetEffects()
            var hasNoPetMember = false // 检查是否有队员没有宠物

            team.members.forEach { member ->
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
                            // 修改：盗贼效果改为基础5%加成
                            effects.rewardMultiplier += 0.05
                            effects.equipmentDropChance += 0.05
                        }
                        PetEffect.THIEF_S -> {
                            // 修改：盗贼S效果改为基础10%加成
                            effects.rewardMultiplier += 0.1
                            effects.equipmentDropChance += 0.05
                        }
                        PetEffect.PRIEST -> effects.positiveEventChance += 0.15
                        PetEffect.PRIEST_S -> effects.positiveEventChance += 0.2
                        PetEffect.TREASURE_HUNTER -> effects.equipmentDropChance += 0.14
                        PetEffect.TREASURE_HUNTER_S -> {
                            effects.equipmentDropChance += 0.14
                            effects.additionalEvents += 1
                        }
                        PetEffect.BARD -> effects.bonusDungeonChance += 0.05
                        PetEffect.BARD_S -> {
                            // 诗人S效果：基础1%加成，实际计算在触发隐藏副本时进行
                            effects.bonusDungeonChance = 0.05
                        }
                    }
                }
            }

            // 添加萌新队伍BUFF
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
                delay(30 * 1000) // 每30秒检查一次
                TeamManager.checkExpiredTeams()
                // 清理超时的转生和遗物重置确认请求
                RebirthConfirmation.cleanupExpiredRequests()
                RelicConfirmation.cleanupExpiredRequests()

                // 检查世界BOSS是否需要每日重置 - 使用线程安全的方式
                WorldBossManager.checkDailyReset()
            }
        }

        // 初始化世界BOSS
        WorldBossManager.getBossInfo() // 这会触发重置检查

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

                    val code = message.substringAfter("/兑换码 ").trim().lowercase() // 转换为小写以忽略大小写

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
                        // 可以在这里添加更多兑换码
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

                message == "/找个对手" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查CD
                    val currentTime = System.currentTimeMillis()
                    val remainingTime = pkCooldown - (currentTime - playerData.lastPkTime)

                    if (remainingTime > 0) {
                        val hours = remainingTime / 3600000
                        val minutes = (remainingTime % 3600000) / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        group.sendMessage("你刚Battle过，萎了，再休息${hours}小时${minutes}分${seconds}秒吧~\n或者使用 /帮助 查看其他操作！")
                        return@subscribeAlways
                    }

                    // 检查是否超过6小时，给予睡眠补丁奖励
                    val sleepBonusTime = 6 * 60 * 60 * 1000 // 6小时
                    val timeSinceLastFind = currentTime - playerData.lastFindOpponentTime
                    var sleepBonusMessage = ""

                    if (timeSinceLastFind >= sleepBonusTime) {
                        // 给予睡眠补丁奖励
                        playerData.baseATK = increaseAttributeWithLimit(playerData.baseATK, 3, playerData.rebirthCount)
                        playerData.baseDEF = increaseAttributeWithLimit(playerData.baseDEF, 3, playerData.rebirthCount)
                        sleepBonusMessage = "\n(已超过6小时未对战，获得睡眠补丁奖励：ATK+3, DEF+3)"
                    }

                    // 更新上次寻找对手的时间
                    playerData.lastFindOpponentTime = currentTime
                    PlayerDataManager.savePlayerData(playerData)

                    // 获取所有已注册玩家ID（排除自己）
                    val allPlayerIds = PlayerDataManager.getAllPlayerIds().filter { it != senderId }

                    // 选择对手（如果有其他玩家则随机选择，否则提示没有对手）
                    val opponentId = if (allPlayerIds.isNotEmpty()) {
                        allPlayerIds.random()
                    } else {
                        group.sendMessage("当前没有其他玩家可以挑战，快去邀请朋友加入吧！")
                        return@subscribeAlways
                    }

                    val opponentData = PlayerDataManager.getPlayerData(opponentId) ?: run {
                        group.sendMessage("对手数据异常，请稍后再试")
                        return@subscribeAlways
                    }

                    // 修改PK处理部分
                    val pkResult = performPk(playerData, opponentData)
                    val winner = pkResult.winner
                    val loser = pkResult.loser

                    // 更新PK时间
                    playerData.lastPkTime = currentTime
                    PlayerDataManager.savePlayerData(playerData)

                    // 发送结果
                    if (pkResult.isDraw) {
                        group.sendMessage("${sender.nameCardOrNick} 和 ${if (opponentId == 0L) "沙包NPC" else group.get(opponentId)?.nameCardOrNick} 打平了！双方各增加3点ATK和DEF$sleepBonusMessage")
                    } else {
                        val winnerName = if (winner.qqId == senderId) sender.nameCardOrNick else group.get(winner.qqId)?.nameCardOrNick ?: "隔壁某人(非本群用户)"
                        val loserName = if (loser.qqId == senderId) sender.nameCardOrNick else group.get(loser.qqId)?.nameCardOrNick ?: "隔壁某人(非本群用户)"

                        // 只有真实玩家才保存数据
                        if (winner.qqId != 0L) {
                            PlayerDataManager.savePlayerData(winner)
                        }
                        if (loser.qqId != 0L) {
                            PlayerDataManager.savePlayerData(loser)
                        }

                        val goldChange = if (winner.qqId == senderId) {
                            winner.gold - playerData.gold
                        } else if (loser.qqId == senderId) {
                            playerData.gold - loser.gold
                        } else {
                            0
                        }

                        val goldMessage = if (goldChange != 0) {
                            if (winner.qqId == senderId) {
                                "并拿走了对手10%的喵币"
                            } else {
                                "并被拿走了10%的喵币"
                            }
                        } else {
                            ""
                        }

                        // 修改暴击消息构建部分
                        val criticalMessage = if (pkResult.criticalHit && pkResult.criticalPlayerId != null) {
                            val criticalPlayerName = if (pkResult.criticalPlayerId == senderId) {
                                sender.nameCardOrNick
                            } else {
                                // 确保 criticalPlayerId 不为 null
                                pkResult.criticalPlayerId.let {
                                    group.get(it)?.nameCardOrNick ?: "隔壁某人(非本群用户)"
                                }
                            }

                            // 根据是否有装备或宠物提供不同的暴击提示
                            val criticalSource = if (pkResult.criticalEquipment != null) {
                                "装备的${pkResult.criticalEquipment}"
                            } else {
                                // 检查是否有宠物提供LUCK加成
                                val criticalPlayerData = if (pkResult.criticalPlayerId == senderId) {
                                    playerData
                                } else {
                                    PlayerDataManager.getPlayerData(pkResult.criticalPlayerId)
                                }

                                if (criticalPlayerData?.pet != null) {
                                    "宠物${criticalPlayerData.pet?.name}"
                                } else if (criticalPlayerData?.relic != null) {
                                    "遗物${criticalPlayerData.relic?.name}"
                                } else {
                                    "的幸运值"
                                }
                            }

                            "⚡️${criticalPlayerName}的${criticalSource}生效了！暴击！⚡️\n"
                        } else {
                            ""
                        }

                        // 构建最终消息
                        val resultMessage = StringBuilder()
                        resultMessage.append(criticalMessage)
                        resultMessage.append("$winnerName GANK了 $loserName！\n")
                        resultMessage.append("胜利者增加6点ATK和DEF，并拿走了对手10%的喵币\n")
                        resultMessage.append("失败者增加3点ATK和DEF")
                        resultMessage.append(sleepBonusMessage)

                        group.sendMessage(resultMessage.toString())
                    }
                }

                message == "/我的信息" || message == "/wdxx" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 计算动态属性上限
                    val maxAttribute = 225 + 10 * playerData.rebirthCount

                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.getEnhancedAtk() ?: 0) +  // 修改：使用强化后的ATK
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)

                    val finalDEF = playerData.baseDEF +
                        (playerData.equipment?.getEnhancedDef() ?: 0) +  // 修改：使用强化后的DEF
                        (playerData.pet?.def ?: 0) +
                        (playerData.relic?.def ?: 0)

                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.getEnhancedLuck() ?: 0) +  // 修改：使用强化后的LUCK
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

                    val equipmentInfo = playerData.equipment?.let {
                        val enhancedAtk = it.getEnhancedAtk()
                        val enhancedDef = it.getEnhancedDef()
                        val enhancedLuck = it.getEnhancedLuck()

                        "装备: ${it.getDisplayName()} (ATK+${enhancedAtk}${if (it.enhanceLevel > 0) "(${it.atk}+${enhancedAtk - it.atk})" else ""}, " +
                            "DEF+${enhancedDef}${if (it.enhanceLevel > 0) "(${it.def}+${enhancedDef - it.def})" else ""}, " +
                            "LUCK+${enhancedLuck}${if (it.enhanceLevel > 0) "(${it.luck}+${enhancedLuck - it.luck})" else ""})"
                    } ?: "装备: 无"

                    // 使用修改后的formatPetInfo函数
                    val petInfo = playerData.pet?.let {
                        "宠物: ${formatPetInfo(it)}"
                    } ?: "宠物: 无"

                    val relicInfo = playerData.relic?.let {
                        "遗物: ${it.name} (${it.grade}级, ATK+${it.atk}, DEF+${it.def}, LUCK+${it.luck})"
                    } ?: "遗物: 无"

                    // 添加道具信息
                    val ticketInfo = if (playerData.hiddenDungeonTickets > 0) {
                        "隐藏副本进入券: ${playerData.hiddenDungeonTickets}个"
                    } else {
                        "隐藏副本进入券: 无"
                    }

                    val rebirthInfo = if (playerData.rebirthCount > 0) {
                        "转生次数: ${playerData.rebirthCount}"
                    } else {
                        ""
                    }

                    // 添加属性上限信息
                    val attributeLimitInfo = "${maxAttribute}"
                    // 添加装备等级信息
                    val equipmentLevelInfo = if (playerData.equipment != null) {
                        val level = getEquipmentLevel(playerData.equipment!!.name)
                        val levelName = when (level) {
                            3 -> "UR"
                            2 -> "SSR"
                            1 -> "SR"
                            else -> "普通"
                        }
                        "装备等级: $levelName"
                    } else {
                        "装备等级: 无"
                    }

                    // 添加S型宠物变更券信息
                    val sPetChangeTicketInfo = if (playerData.sPetChangeTickets > 0) {
                        "S型宠物辅助职业变更券: ${playerData.sPetChangeTickets}个"
                    } else {
                        "S型宠物辅助职业变更券: 无"
                    }

                    group.sendMessage("""
                        ${sender.nameCardOrNick} 的信息:
                        ATK: $finalATK (基础: ${playerData.baseATK} / ${attributeLimitInfo} )
                        DEF: $finalDEF (基础: ${playerData.baseDEF} / ${attributeLimitInfo} )
                        LUCK: $finalLUCK
                        
                        喵币: ${playerData.gold}
                        $equipmentInfo
                        $petInfo
                        $relicInfo
                        $rebirthInfo
                        
                        ${ticketInfo}
                        ${sPetChangeTicketInfo}
                        """.trimIndent())

                    // 检查是否达到上限
                    if (playerData.baseATK + 20 >= maxAttribute || playerData.baseDEF + 20 >= maxAttribute) {
                        group.sendMessage("警告：你的基础属性即将达到上限，请记得转生！")
                    }

                    // 计算属性分数并更新全服楷模（使用最终属性）
                    val totalScore = finalATK + finalDEF + (finalLUCK * 5)
                    val enhanceLevel = playerData.equipment?.enhanceLevel ?: 0

                    // 获取装备信息
                    val equipmentName = playerData.equipment?.name
                    val equipmentATK = playerData.equipment?.getEnhancedAtk() ?: 0  // 使用强化后的属性
                    val equipmentDEF = playerData.equipment?.getEnhancedDef() ?: 0  // 使用强化后的属性
                    val equipmentLUCK = playerData.equipment?.getEnhancedLuck() ?: 0  // 使用强化后的属性
                    val equipmentBaseATK = if (enhanceLevel > 0) {
                        (equipmentATK / (1 + enhanceLevel * 0.1)).toInt()
                    } else {
                        equipmentATK
                    }
                    val equipmentBaseDEF = if (enhanceLevel > 0) {
                        (equipmentDEF / (1 + enhanceLevel * 0.1)).toInt()
                    } else {
                        equipmentDEF
                    }
                    val equipmentBaseLUCK = if (enhanceLevel > 0) {
                        (equipmentLUCK / (1 + enhanceLevel * 0.1)).toInt()
                    } else {
                        equipmentLUCK
                    }

                    // 获取宠物信息
                    val petName = playerData.pet?.name
                    val petATK = playerData.pet?.atk ?: 0
                    val petDEF = playerData.pet?.def ?: 0
                    val petLUCK = playerData.pet?.luck ?: 0
                    val petGrade = playerData.pet?.grade
                    val petEffect = playerData.pet?.specialEffect?.let { effect ->
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
                    val relicName = playerData.relic?.name
                    val relicATK = playerData.relic?.atk ?: 0
                    val relicDEF = playerData.relic?.def ?: 0
                    val relicLUCK = playerData.relic?.luck ?: 0
                    val relicGrade = playerData.relic?.grade

                    TopPlayerManager.updateRecord(
                        senderId,
                        sender.nameCardOrNick,
                        finalATK,
                        finalDEF,
                        finalLUCK,
                        playerData.baseATK,
                        playerData.baseDEF,
                        playerData.baseLUCK,
                        equipmentName,
                        equipmentATK,
                        equipmentDEF,
                        equipmentLUCK,
                        equipmentBaseATK,
                        equipmentBaseDEF,
                        equipmentBaseLUCK,
                        enhanceLevel,
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
                        relicGrade
                    )
                }

                message == "/签到" -> {
                    // 如果玩家未注册，则创建新玩家数据
                    if (playerData == null) {
                        playerData = PlayerDataManager.createPlayerData(senderId)
                        group.sendMessage("注册成功！欢迎加入！其他指令请使用 /帮助 查看！")
                    }

                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

                    if (playerData.lastSignDate == today) {
                        group.sendMessage("你今天已经签到过了！试试 /找个对手 吧。")
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
                            "隐藏副本进入券" -> "1"
                            "S型宠物辅助职业变更券" -> "2"
                            else -> ""
                        }
                        "${itemNumber}. ${item.name} - 价格: ${item.price}喵币 (${item.description})"
                    }

                    group.sendMessage("道具商店:\n$itemList\n使用\"/购买道具 数字\"或\"/购买道具 道具名\"来购买道具\n例如: /购买道具 1 或 /购买道具 隐藏副本进入券")
                }

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

                    // 执行转生减属性（无论是否确认更换宠物，属性都会扣除）
                    playerData.baseATK = (playerData.baseATK - 150).coerceAtLeast(10)
                    playerData.baseDEF = (playerData.baseDEF - 150).coerceAtLeast(10)
                    playerData.rebirthCount++
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成随机宠物
                    val newPet = generateRandomPet()

                    // 如果有旧宠物，询问是否更换
                    if (playerData.pet != null) {
                        RebirthConfirmation.addPendingRebirth(senderId, playerData, newPet)
                        group.sendMessage("${sender.nameCardOrNick}，你已满足转生条件！\n" +
                            "转生后已减少150点ATK和DEF，并获得一只新宠物：\n" +
                            "${formatPetInfo(newPet)}\n" +
                            "你当前已拥有宠物：\n" +
                            "${formatPetInfo(playerData.pet!!)}\n" +
                            "是否更换宠物？回复\"是\"更换，回复\"否\"保留原宠物（2分钟内有效）")
                    } else {
                        // 没有宠物，直接设置
                        playerData.pet = newPet

                        PlayerDataManager.savePlayerData(playerData)

                        group.sendMessage("转生成功！当前转生次数：${playerData.rebirthCount}")
                    }
                }


                // 添加对确认消息的处理
                message == "是" || message == "否" -> {
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
                    if (pendingRebirth != null) {
                        val (playerData, newPet) = pendingRebirth

                        if (message == "是") {
                            // 更换宠物
                            playerData.pet = newPet
                            group.sendMessage("已更换为新宠物")
                        } else {
                            // 保留原宠物，属性已扣除且不返还
                            group.sendMessage("已取消宠物更换，属性已扣除")
                        }

                        PlayerDataManager.savePlayerData(playerData)
                        RebirthConfirmation.removePendingRebirth(senderId)

                        group.sendMessage("转生成功！当前转生次数：${playerData.rebirthCount}")
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
                        +"• /签到 - 注册并签到获得20-50喵币\n"
                        +"• /找个对手 - 随机找一个对手进行PK（3小时冷却）\n"
                        +"• /我的信息(/wdxx) - 查看自己的属性、上传个人信息至服务器\n"
                        +"• /商店 - 查看商店中出售的装备\n"
                        +"• /道具商店(/djsd) - 查看道具商店中出售的道具\n"
                        +"• /使用 道具名称 - 使用道具\n"
                        +"• /世界BOSS - 查看世界BOSS信息\n"
                        +"• /出刀 - 对世界BOSS进行一次攻击\n"
                        +"• /转生 - 基础属性≥200可转生，获得宠物，基本属性-150\n"
                        +"• /获取遗物 - 消耗5次转生次数获取遗物\n"
                        +"• /喵币(属性)重置遗物 - 花费2500喵币(50双属性)重置遗物属性\n"
                        +"• /组队(/zd;/加入;/离开队伍(/lkdw)) - 创建副本队伍（15min冷却，每日10次）\n\n"
                        +"• /名人堂(/神人堂) - 查看通关难5/难5隐藏的玩家\n"
                        +"• /榜一大哥 - 查看后台战力值最高的玩家\n"
                        +"• /更新日志 查看最新版本的更新内容\n"
                        +"• /兑换码 [兑换码内容] - 领取新手奖励，[兑换码内容]见在线文档"

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

                    // 检查副本CD（15分钟）
                    val currentTime = System.currentTimeMillis()
                    val remainingTime = 15 * 60 * 1000 - (currentTime - playerData.lastDungeonTime)

                    if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.getEnhancedAtk() ?: 0) +  // 修改：使用强化后的ATK
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)

                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.getEnhancedLuck() ?: 0) +  // 修改：使用强化后的LUCK
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

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
                    if (TeamManager.createTeam(senderId, group.id, sender.nameCardOrNick, finalATK, finalLUCK)) {
                        // 显示玩家今日副本次数信息
                        val countInfo = if (playerData.dailyDungeonCount >= 10) {
                            " (奖励已达上限)"
                        } else {
                            " (${playerData.dailyDungeonCount}/10)"
                        }

                        // 添加周末狂欢提示
                        val weekendBonusMessage = getWeekendBonusMessage()


                        group.sendMessage("${weekendBonusMessage}${petInfo}${sender.nameCardOrNick}${countInfo}创建了队伍，等待队员加入（5分钟有效）。使用\"/加入\"命令加入队伍。")
                    } else {
                        group.sendMessage("创建队伍失败，可能该群已经有一个队伍了。")
                    }
                }

                message == "/加入" || message == "/jr" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查副本CD（15分钟）
                    val currentTime = System.currentTimeMillis()
                    val remainingTime = 15 * 60 * 1000 - (currentTime - playerData.lastDungeonTime)

                    if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        return@subscribeAlways
                    }

                    // 检查当前群是否有队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        group.sendMessage("当前群没有队伍或已超时解散，请先使用'/组队'创建队伍。")
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.getEnhancedAtk() ?: 0) +  // 修改：使用强化后的ATK
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)

                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.getEnhancedLuck() ?: 0) +  // 修改：使用强化后的LUCK
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

                    // 加入队伍
                    if (TeamManager.addMember(group.id, senderId, sender.nameCardOrNick, finalATK, finalLUCK)) {
                        val updatedTeam = TeamManager.getTeamByGroup(group.id)!!

                        // 显示玩家今日副本次数信息
                        val countInfo = if (playerData.dailyDungeonCount >= 10) {
                            " (奖励次数已达上限)"
                        } else {
                            " (${playerData.dailyDungeonCount}/10)"
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
                            val teamPower = totalATK * 0.5 * totalLUCK

                            // 构建副本推荐信息
                            val dungeonRecommendations = DungeonManager.dungeons.joinToString("\n") { dungeon ->
                                val successRate = (teamPower / dungeon.difficulty).coerceAtMost(1.0)
                                "副本${dungeon.id}: ${dungeon.name} - 难度${dungeon.difficulty / 1000}K - 成功率约${"%.2f".format(successRate * 100)}%"
                            }
                            // 获取队伍宠物效果列表
                            val petEffects = mutableListOf<String>()
                            updatedTeam.members.forEach { member ->
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

                            val petEffectsStr = if (petEffects.isNotEmpty()) {
                                "宠物: ${petEffects.joinToString(",")}"
                            } else {
                                "宠物: 无"
                            }
                            // 在队伍已满时@队长并发送消息
                            val captainId = team.captainId
                            val captainAt = At(captainId) // 创建@队长的消息组件

                            val message = captainAt +
                                " 队伍已满！队伍总ATK: $totalATK, 总LUCK: $totalLUCK, 综合战力: $teamPower\n" +
                                "请使用\"/选择副本(/xzfb) [1-5]\"命令选择副本。\n" +
                                "$petEffectsStr\n" +
                                "(概率未计算宠物效果)\n$dungeonRecommendations"

                            group.sendMessage(message)
                        }
                    } else {
                        group.sendMessage("加入队伍失败，可能队伍已满或你已在其他队伍中。")
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

                    // 检查队长副本CD（15分钟）
                    val currentTime = System.currentTimeMillis()
                    val remainingTime = 15 * 60 * 1000 - (currentTime - playerData.lastDungeonTime)

                    if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒")
                        return@subscribeAlways
                    }

                    // 检查所有队员的副本CD
                    val membersWithCooldown = mutableListOf<String>()
                    team.members.forEach { member ->
                        val memberData = PlayerDataManager.getPlayerData(member.playerId)
                        if (memberData != null) {
                            val memberRemainingTime = 15 * 60 * 1000 - (currentTime - memberData.lastDungeonTime)
                            if (memberRemainingTime > 0) {
                                membersWithCooldown.add(member.playerName)
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
                        group.sendMessage("请输入有效的副本编号（1-5）。")
                        return@subscribeAlways
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
                            PlayerDataManager.savePlayerData(memberData)
                        }
                    }

                    // 使用 GlobalScope 启动协程处理副本攻略
                    GlobalScope.launch {

                        // 计算队伍宠物效果
                        val teamEffects = PetEffectCalculator.calculateTeamEffects(team)

                        // 计算队伍总战力和基础成功率（应用宠物效果）
                        val totalATK = (team.members.sumOf { it.atk } * (1 + teamEffects.atkMultiplier)).toInt()
                        val totalLUCK = (team.members.sumOf { it.luck } * (1 + teamEffects.luckMultiplier)).toInt()
                        val teamPower = totalATK * 0.5 * totalLUCK
                        val baseSuccessRate = (teamPower / dungeon.difficulty).coerceAtMost(1.0)

                        // 发送开始消息
                        val memberNames = team.members.joinToString("，") { it.playerName }
                        group.sendMessage("$memberNames 开始攻略 ${dungeon.name}。")

                        // 生成剧情事件（应用宠物效果：增加正向事件概率和额外事件数量）
                        val events = DungeonStoryGenerator.generateEvents(
                            team,
                            dungeon,
                            teamEffects.positiveEventChance,
                            teamEffects.additionalEvents // 传递额外事件数量
                        )
                        // 发送所有普通事件（不包括BOSS事件），但最多显示8个
                        val regularEventsCount = min(events.size - 1, 5) // 最多显示8个普通事件
                        for (i in 0 until regularEventsCount) {
                            delay(5000)
                            group.sendMessage(events[i].description)
                        }

                        // 如果事件太多，添加提示
                        if (events.size - 1 > 5) {
                            delay(5000)
                            group.sendMessage("...还有${events.size - 1 - 5}个事件未显示，但效果已生效")
                        }

                        // 发送BOSS事件
                        delay(5000)
                        group.sendMessage(events[events.size - 1].description) // BOSS事件是最后一个

                        // 计算总奖励和成功率调整
                        var totalSuccessRateChange = 0.0
                        var totalExtraGold = 0
                        var totalExtraATK = 0
                        var totalExtraDEF = 0

                        // 排除BOSS事件，只计算前5个事件
                        for (i in 0 until events.size - 1) { // events.size - 1 是为了排除BOSS事件
                            totalSuccessRateChange += events[i].successRateChange
                            totalExtraGold += events[i].extraGold
                            totalExtraATK += events[i].extraATK
                            totalExtraDEF += events[i].extraDEF
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
                        delay(5000)

                        // 添加周末狂欢提示
                        val weekendBonusMessage = getWeekendBonusMessage()


                        if (success) {
                            group.sendMessage("经过一番苦战，队伍终于击败了BOSS！$weekendBonusMessage")

                            // 如果是难度5副本且成功，记录到名人堂
                            if (dungeon.id == 5) {
                                val playerNames = team.members.map { it.playerName }
                                HallOfFameManager.addRecord(playerNames, finalSuccessRate)
                            }

                            // 构建奖励信息
                            val rewardInfo = StringBuilder()
                            rewardInfo.append("恭喜！攻略${dungeon.name}成功！每人获得${rewardPerPerson}喵币。")

                            if (bonusExtraGold > 0) {
                                rewardInfo.append("\n额外喵币奖励: +${bonusExtraGold}喵币")
                            }
                            if (bonusExtraATK > 0) {
                                rewardInfo.append("\n额外ATK奖励: +${bonusExtraATK}点基础ATK")
                            }
                            if (bonusExtraDEF > 0) {
                                rewardInfo.append("\n额外DEF奖励: +${bonusExtraDEF}点基础DEF")
                            }

                            // 添加成功率信息
                            rewardInfo.append("\n基础成功率: ${"%.1f".format(baseSuccessRate * 100)}%")
                            rewardInfo.append("\n事件调整: ${if (totalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(totalSuccessRateChange * 100)}%")
                            rewardInfo.append("\n最终成功率: ${"%.1f".format(finalSuccessRate * 100)}%")

                            group.sendMessage(rewardInfo.toString())


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
                                // 只要队伍中有任意诗人或诗人S宠物，就给予固定加成
                                // 对于难度3副本：(3 + 1)^2 / 100 = 0.16
                                val dungeonLevel = dungeon.id
                                (dungeonLevel + 1) * (dungeonLevel + 1) / 100.0
                            } else {
                                0.0
                            }

                            val triggerBonusDungeon = if (allHaveTicket) {
                                true // 全队有券，100%触发
                            } else {
                                Random.nextDouble() < (0.05 + teamEffects.bonusDungeonChance + bardSBonus)
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

                                // 创建奖励副本 (难度x2，奖励x2)
                                val bonusDungeon = Dungeon(
                                    dungeon.id * 10, // 使用特殊ID标识奖励副本
                                    "${dungeon.name}(奖励)",
                                    dungeon.difficulty * 2,
                                    dungeon.reward * 2
                                )

                                // 在奖励副本事件生成处传递牧师效果
                                val bonusEvents = DungeonStoryGenerator.generateBonusDungeonEvents(
                                    team,
                                    bonusDungeon,
                                    teamEffects.positiveEventChance // 传递牧师效果加成
                                )

                                // 发送前3个事件，每个间隔4秒
                                for (i in 0 until 3) {
                                    delay(4000)
                                    group.sendMessage(bonusEvents[i].description)
                                }

                                // 发送BOSS事件
                                delay(4000)
                                group.sendMessage(bonusEvents[3].description)

                                // 在奖励副本计算前重新计算团队战力（考虑宠物效果）
                                val bonusTotalATK = (team.members.sumOf { it.atk } * (1 + teamEffects.atkMultiplier)).toInt()
                                val bonusTotalLUCK = (team.members.sumOf { it.luck } * (1 + teamEffects.luckMultiplier)).toInt()
                                val bonusTeamPower = bonusTotalATK * 0.5 * bonusTotalLUCK

                                // 计算奖励副本的成功率
                                val bonusTotalSuccessRateChange = bonusEvents.take(3).sumOf { it.successRateChange }
                                val bonusBaseSuccessRate = (bonusTeamPower / bonusDungeon.difficulty).coerceAtMost(1.0)
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

                                // 平分奖励
                                val bonusRewardPerPerson = (bonusActualReward + bonusTotalExtraGold) / 4

                                // 发送结果
                                delay(5000)

                                if (bonusSuccess) {
                                    group.sendMessage("🌟 队伍成功通过了奖励副本！获得了丰厚的额外奖励！")

                                    val bonusRewardInfo = StringBuilder()
                                    bonusRewardInfo.append("奖励副本攻略成功！每人获得${bonusRewardPerPerson}喵币。")

                                    if (bonusTotalExtraGold > 0) {
                                        val extraGoldPerPerson = bonusTotalExtraGold / 4
                                        bonusRewardInfo.append("\n额外喵币奖励: 每人+${extraGoldPerPerson}喵币")
                                    }
                                    if (bonusTotalExtraATK > 0) {
                                        bonusRewardInfo.append("\n额外ATK奖励: 每人+${bonusTotalExtraATK}点基础ATK")
                                    }
                                    if (bonusTotalExtraDEF > 0) {
                                        bonusRewardInfo.append("\n额外DEF奖励: 每人+${bonusTotalExtraDEF}点基础DEF")
                                    }

                                    bonusRewardInfo.append("\n基础成功率: ${"%.1f".format(bonusBaseSuccessRate * 100)}%")
                                    bonusRewardInfo.append("\n事件调整: ${if (bonusTotalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(bonusTotalSuccessRateChange * 100)}%")
                                    bonusRewardInfo.append("\n最终成功率: ${"%.1f".format(bonusFinalSuccessRate * 100)}%")

                                    group.sendMessage(bonusRewardInfo.toString())

                                    // 添加装备掉落逻辑
                                    delay(3000)

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
                                            else -> null
                                        }

                                        if (dropEquipment != null) {
                                            // 重新获取所有队员的最新数据
                                            val updatedTeamMembers = team.members.map { member ->
                                                val memberData = PlayerDataManager.getPlayerData(member.playerId)
                                                Pair(member, memberData)
                                            }.filter { it.second != null }.map { it.first to it.second!! }

                                            // 筛选可以接受该装备的玩家
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

                                                // 记录日志以便调试
                                                PluginMain.logger.info("玩家 ${luckyMember.playerName} (ID: ${luckyMember.playerId}) 获得了装备: ${newEquipment.getDisplayName()}")

                                                // 再次验证装备是否成功保存
                                                val verifiedData = PlayerDataManager.getPlayerData(luckyMember.playerId)
                                                if (verifiedData?.equipment?.name != newEquipment.name) {
                                                    PluginMain.logger.error("装备保存验证失败！玩家 ${luckyMember.playerName} 应获得装备 ${newEquipment.name}，但实际为 ${verifiedData?.equipment?.name}")
                                                }
                                            } else {
                                                group.sendMessage("💔 隐藏副本掉落了 ${dropEquipment.name}，但队伍中没有人符合条件。")
                                            }
                                        }
                                    }

                                    if (bonusSuccess && originalDungeonId == 5) {
                                        val playerNames = team.members.map { it.playerName }
                                        HallOfGodsManager.addRecord(playerNames, bonusFinalSuccessRate)
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
                                            // 奖励副本的属性奖励也应用盗贼效果
                                            val bonusExtraATKWithBonus = (bonusTotalExtraATK * bonusFinalRewardMultiplier).toInt()
                                            val bonusExtraDEFWithBonus = (bonusTotalExtraDEF * bonusFinalRewardMultiplier).toInt()
                                            memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, bonusExtraATKWithBonus, memberData.rebirthCount)
                                            memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, bonusExtraDEFWithBonus, memberData.rebirthCount)
                                        }

                                        PlayerDataManager.savePlayerData(memberData)

                                        // 更新奖励消息
                                        val bonusInfo = if (isWeekendBonus) " (周末狂欢双倍奖励)" else ""
                                        bonusRewardMessages.add("${member.playerName} 获得${bonusRewardPerPerson}喵币${if (bonusSuccess) "和属性奖励" else ""}$bonusInfo")
                                    }
                                }
                            }

                        } else {

                            group.sendMessage("经过一番苦战，菜鸡们最终还是不敌BOSS……$weekendBonusMessage")

                            // 添加失败信息
                            val failInfo = StringBuilder()
                            failInfo.append("很遗憾，攻略${dungeon.name}失败。每人获得${rewardPerPerson}喵币。")
                            failInfo.append("\n基础成功率: ${"%.1f".format(baseSuccessRate * 100)}%")
                            failInfo.append("\n事件调整: ${if (totalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(totalSuccessRateChange * 100)}%")
                            failInfo.append("\n最终成功率: ${"%.1f".format(finalSuccessRate * 100)}%")

                            group.sendMessage(failInfo.toString())
                        }

                        // 更新每个队员的喵币、属性和副本CD
                        val noRewardPlayers = mutableListOf<String>() // 记录没有获得奖励的玩家
                        val rewardMessages = mutableListOf<String>() // 记录奖励消息

                        team.members.forEach { member ->
                            val memberData = PlayerDataManager.getPlayerData(member.playerId)
                            if (memberData != null) {
                                // 检查玩家今日副本次数是否已达上限
                                if (memberData.dailyDungeonCount < 10) {
                                    // 未达上限，正常获得奖励
                                    memberData.gold += rewardPerPerson
                                    if (success) {
                                        // 属性奖励也应用盗贼效果
                                        val extraATKWithBonus = (bonusExtraATK * finalRewardMultiplier).toInt()
                                        val extraDEFWithBonus = (bonusExtraDEF * finalRewardMultiplier).toInt()
                                        memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, extraATKWithBonus, memberData.rebirthCount)
                                        memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, extraDEFWithBonus, memberData.rebirthCount)
                                    }
                                    // 增加每日副本计数
                                    memberData.dailyDungeonCount += 1

                                    // 添加周末狂欢提示
                                    val bonusInfo = if (isWeekendBonus) " (周末狂欢双倍奖励)" else ""
                                    rewardMessages.add("${member.playerName} 获得${rewardPerPerson}喵币${if (success) "和属性奖励" else ""}$bonusInfo，今日副本次数: ${memberData.dailyDungeonCount}/10")
                                } else {
                                    // 已达上限，不获得普通副本奖励，但仍计入冷却时间
                                    noRewardPlayers.add(member.playerName)
                                }

                                // 注意：这里不再更新副本CD，因为已经在选择副本时更新过了
                                // 确保清空旧字段（如果存在）
                                memberData.lastDungeonDate = null
                                PlayerDataManager.savePlayerData(memberData)
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


                message == "/副本信息" || message == "/fbxx" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查副本CD
                    val currentTime = System.currentTimeMillis()
                    val remainingTime = 15 * 60 * 1000 - (currentTime - playerData.lastDungeonTime)
                    val cdMessage = if (remainingTime > 0) {
                        val minutes = remainingTime / 60000
                        val seconds = (remainingTime % 60000) / 1000
                        "副本冷却中，还需${minutes}分${seconds}秒"
                    } else {
                        "可以进入副本"
                    }

                    val countMessage = if (playerData.dailyDungeonCount >= 10) {
                        "今日参与次数: ${playerData.dailyDungeonCount}/10 (已达上限，可参与但无奖励)"
                    } else {
                        "今日参与次数: ${playerData.dailyDungeonCount}/10"
                    }

                    group.sendMessage("${sender.nameCardOrNick} 的副本信息:\n" +
                        countMessage + "\n" +
                        "状态: $cdMessage")
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

                message == "/属性重置遗物" -> {
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

                    // 检查属性是否足够
                    if (playerData.baseATK < 150 || playerData.baseDEF < 150) {
                        group.sendMessage("属性重置需要基础ATK和DEF都达到150以上！")
                        return@subscribeAlways
                    }

                    // 立即扣除属性
                    playerData.baseATK = (playerData.baseATK - 50).coerceAtLeast(10)
                    playerData.baseDEF = (playerData.baseDEF - 50).coerceAtLeast(10)
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成新遗物并进入确认流程
                    val newRelic = RelicGenerator.generateRandomRelic()
                    RelicConfirmation.addPendingReset(senderId, playerData, newRelic, "attribute")

                    group.sendMessage("${sender.nameCardOrNick}，已扣除50点ATK和DEF进行遗物重置：\n" +
                        "当前遗物：\n${RelicGenerator.formatRelicInfo(playerData.relic!!)}\n" +
                        "新遗物：\n${RelicGenerator.formatRelicInfo(newRelic)}\n" +
                        "是否替换？回复\"是\"替换，回复其他内容保留原遗物（2分钟内有效）")
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
                        "1" -> "隐藏副本进入券"
                        "2" -> "S型宠物辅助职业变更券"
                        else -> itemParam
                    }
                    val item = Shop.getItemByName(itemName)

                    if (item == null) {
                        group.sendMessage("没有找到名为\"$itemName\"的道具")
                    } else if (playerData.gold < item.price) {
                        group.sendMessage("喵币不足！需要${item.price}喵币，你只有${playerData.gold}喵币")
                    } else if (itemName == "隐藏副本进入券" && playerData.hiddenDungeonTickets >= item.maxStack) {
                        group.sendMessage("每个玩家最多只能持有${item.maxStack}个${itemName}")
                    } else if (itemName == "S型宠物辅助职业变更券" && playerData.sPetChangeTickets >= item.maxStack) {
                        group.sendMessage("每个玩家最多只能持有${item.maxStack}个${itemName}")
                    } else {
                        playerData.gold -= item.price

                        // 特殊处理不同道具
                        when (itemName) {
                            "隐藏副本进入券" -> playerData.hiddenDungeonTickets += 1
                            "S型宠物辅助职业变更券" -> playerData.sPetChangeTickets += 1
                        }

                        PlayerDataManager.savePlayerData(playerData)
                        group.sendMessage("购买成功！花费${item.price}喵币，获得1个${itemName}，剩余${playerData.gold}喵币")
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
                    if (equipment.enhanceLevel >= 10) {
                        group.sendMessage("装备已达到最大强化等级(+10)")
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

                        group.sendMessage("强化成功！${equipment.getDisplayName()} 强化等级提升至+${equipment.enhanceLevel}")

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

                message == "/世界BOSS" -> {
                    val bossInfo = WorldBossManager.getBossInfo()
                    group.sendMessage(bossInfo)
                }

                message == "/出刀" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.getEnhancedAtk() ?: 0) +
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)

                    val finalDEF = playerData.baseDEF +
                        (playerData.equipment?.getEnhancedDef() ?: 0) +
                        (playerData.pet?.def ?: 0) +
                        (playerData.relic?.def ?: 0)

                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.getEnhancedLuck() ?: 0) +
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

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

                message.startsWith("/使用 ") -> {
                    val itemParam = message.substringAfter("/使用 ").trim()
                    val itemName = when (itemParam) {
                        "2" -> "S型宠物辅助职业变更券"
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
            }
        }
    }

    // 、存储PK结果和额外信息
    data class PkResult(
        val winner: PlayerData,
        val loser: PlayerData,
        val isDraw: Boolean,
        val criticalHit: Boolean = false,
        val criticalPlayerId: Long? = null,
        val criticalEquipment: String? = null
    )

    private fun isGroupEnabled(groupId: Long): Boolean {
        return WhitelistConfig.enabledGroups.contains(groupId)
    }

    // 属性增加时检查上限
    private fun increaseAttributeWithLimit(currentValue: Int, increase: Int, rebirthCount: Int): Int {
        // 计算动态上限：225 + 10 * 转生次数
        val maxValue = 225 + 10 * rebirthCount
        val newValue = currentValue + increase
        return if (newValue > maxValue) {
            maxValue
        } else {
            newValue
        }
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
        private val pendingTimes: MutableMap<Long, Long> = mutableMapOf() // 记录请求时间
        private const val TIMEOUT_MS = 2 * 60 * 1000 // 5分钟超时

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


    // 生成随机宠物
    private fun generateRandomPet(): Pet {
        val atk = Random.nextInt(10, 51) // 10-50
        val def = Random.nextInt(10, 51) // 10-50
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

    private fun performPk(attacker: PlayerData, defender: PlayerData): PkResult {
        // 计算最终属性 - 只保留实际使用的属性
        val attackerFinalATK = attacker.baseATK +
            (attacker.equipment?.atk ?: 0) +
            (attacker.pet?.atk ?: 0) +
            (attacker.relic?.atk ?: 0)
        val attackerFinalLUCK = attacker.baseLUCK +
            (attacker.equipment?.luck ?: 0) +
            (attacker.pet?.luck ?: 0) +
            (attacker.relic?.luck ?: 0)

        val defenderFinalDEF = defender.baseDEF +
            (defender.equipment?.def ?: 0) +
            (defender.pet?.def ?: 0) +
            (defender.relic?.def ?: 0)
        val defenderFinalLUCK = defender.baseLUCK +
            (defender.equipment?.luck ?: 0) +
            (defender.pet?.luck ?: 0) +
            (defender.relic?.luck ?: 0)

        // 计算攻击力和防御力（考虑幸运值影响）
        // 修改随机数范围：[LUCK-9, 2*LUCK-15]
        val attackerRandom = Random.nextInt(
            (attackerFinalLUCK - 9).coerceAtLeast(1),
            (2 * attackerFinalLUCK - 15 + 1).coerceAtLeast(2) // +1 因为 Random.nextInt 不包含上限
        )
        val defenderRandom = Random.nextInt(
            (defenderFinalLUCK - 9).coerceAtLeast(1),
            (2 * defenderFinalLUCK - 15 + 1).coerceAtLeast(2) // +1 因为 Random.nextInt 不包含上限
        )

        val attackerPower = attackerFinalATK * attackerRandom
        val defenderPower = defenderFinalDEF * defenderRandom

        // 检查是否触发暴击 - 修改暴击判定条件为 ≥11
        var criticalHit = false
        var criticalPlayerId: Long? = null
        var criticalEquipment: String? = null

        // 修改暴击检测条件
        if (attackerRandom >= 13) {
            criticalHit = true
            criticalPlayerId = attacker.qqId
            // 优先显示装备，其次显示宠物，再次是遗物，最后是基础LUCK
            criticalEquipment = attacker.equipment?.name ?: attacker.pet?.name ?: attacker.relic?.name
        } else if (defenderRandom >= 13) {
            criticalHit = true
            criticalPlayerId = defender.qqId
            // 优先显示装备，其次显示宠物，再次是遗物，最后是基础LUCK
            criticalEquipment = defender.equipment?.name ?: defender.pet?.name ?: defender.relic?.name
        }

        return if (attackerPower > defenderPower) {
            // 攻击方胜利
            attacker.baseATK = increaseAttributeWithLimit(attacker.baseATK, 6, attacker.rebirthCount)
            attacker.baseDEF = increaseAttributeWithLimit(attacker.baseDEF, 6, attacker.rebirthCount)
            defender.baseATK = increaseAttributeWithLimit(defender.baseATK, 3, defender.rebirthCount)
            defender.baseDEF = increaseAttributeWithLimit(defender.baseDEF, 3, defender.rebirthCount)

            // 喵币转移
            if (defender.qqId != 0L && defender.gold > 0) {
                // 计算喵币变化，最多不超过50个喵币
                val goldChange = ((defender.gold * 0.1).toInt().coerceAtLeast(1)).coerceAtMost(50)
                attacker.gold += goldChange
                defender.gold -= goldChange
            }

            PkResult(attacker, defender, false, criticalHit, criticalPlayerId, criticalEquipment)
        } else if (defenderPower > attackerPower) {
            // 防御方胜利
            defender.baseATK = increaseAttributeWithLimit(defender.baseATK, 6, attacker.rebirthCount)
            defender.baseDEF = increaseAttributeWithLimit(defender.baseDEF, 6, attacker.rebirthCount)
            attacker.baseATK = increaseAttributeWithLimit(attacker.baseATK, 3, attacker.rebirthCount)
            attacker.baseDEF = increaseAttributeWithLimit(attacker.baseDEF, 3, attacker.rebirthCount)

            // 喵币转移
            if (attacker.qqId != 0L && attacker.gold > 0) {
                // 计算喵币变化，最多不超过50喵币
                val goldChange = ((attacker.gold * 0.1).toInt().coerceAtLeast(1)).coerceAtMost(50)
                defender.gold += goldChange
                attacker.gold -= goldChange
            }

            PkResult(defender, attacker, false, criticalHit, criticalPlayerId, criticalEquipment)
        } else {
            // 平局
            attacker.baseATK = increaseAttributeWithLimit(attacker.baseATK, 3, attacker.rebirthCount)
            attacker.baseDEF = increaseAttributeWithLimit(attacker.baseDEF, 3, attacker.rebirthCount)
            defender.baseATK = increaseAttributeWithLimit(defender.baseATK, 3, attacker.rebirthCount)
            defender.baseDEF = increaseAttributeWithLimit(defender.baseDEF, 3, attacker.rebirthCount)

            PkResult(attacker, defender, true, criticalHit, criticalPlayerId, criticalEquipment)
        }
    }
}