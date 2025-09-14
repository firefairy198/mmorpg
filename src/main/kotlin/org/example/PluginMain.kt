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

        fun addPendingRebirth(userId: Long, playerData: PlayerData, newPet: Pet) {
            pendingRebirth[userId] = Pair(playerData, newPet)
        }

        fun removePendingRebirth(userId: Long) {
            pendingRebirth.remove(userId)
        }

        fun getPendingRebirth(userId: Long): Pair<PlayerData, Pet>? {
            return pendingRebirth[userId]
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
            "🎉周末狂欢：所有奖励翻倍🎉"
        } else {
            ""
        }
    }

    override fun onEnable() {
        logger.info("PK插件加载成功！")

        // 确保数据文件夹存在
        dataFolder.mkdirs()

        // 启动定时任务检查队伍超时
        this.launch {
            while (isActive) {
                delay(30 * 1000) // 每30秒检查一次
                TeamManager.checkExpiredTeams()
            }
        }

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

                                group.sendMessage("兑换成功！获得20点基础ATK和20点基础DEF！")
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
                        // 可以在这里添加更多兑换码
                        else -> {
                            group.sendMessage("无效的兑换码。")
                        }
                    }
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

                message == "/我的信息" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 计算动态属性上限
                    val maxAttribute = 225 + 10 * playerData.rebirthCount

                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.atk ?: 0) +
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)

                    val finalDEF = playerData.baseDEF +
                        (playerData.equipment?.def ?: 0) +
                        (playerData.pet?.def ?: 0) +
                        (playerData.relic?.def ?: 0)

                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.luck ?: 0) +
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

                    val equipmentInfo = playerData.equipment?.let {
                        "装备: ${it.name} (ATK+${it.atk}, DEF+${it.def}, LUCK+${it.luck})"
                    } ?: "装备: 无"

                    val petInfo = playerData.pet?.let {
                        "宠物: ${it.name} (${it.grade}级, ATK+${it.atk}, DEF+${it.def}, LUCK+${it.luck})"
                    } ?: "宠物: 无"

                    val relicInfo = playerData.relic?.let {
                        "遗物: ${it.name} (${it.grade}级, ATK+${it.atk}, DEF+${it.def}, LUCK+${it.luck})"
                    } ?: "遗物: 无"

                    val rebirthInfo = if (playerData.rebirthCount > 0) {
                        "转生次数: ${playerData.rebirthCount}"
                    } else {
                        ""
                    }

                    // 添加属性上限信息
                    val attributeLimitInfo = "属性上限: ATK/DEF ${maxAttribute} (基础225 + 转生${playerData.rebirthCount}次×10)"

                    group.sendMessage("""
                        ${sender.nameCardOrNick} 的信息:
                        ${attributeLimitInfo}
                        ATK: $finalATK (基础: ${playerData.baseATK})
                        DEF: $finalDEF (基础: ${playerData.baseDEF})
                        LUCK: $finalLUCK (基础: ${playerData.baseLUCK})
                        喵币: ${playerData.gold}
                        $equipmentInfo
                        $petInfo
                        $relicInfo
                        $rebirthInfo
                        """.trimIndent())

                    // 检查是否达到上限
                    if (playerData.baseATK >= maxAttribute || playerData.baseDEF >= maxAttribute) {
                        group.sendMessage("警告：你的基础属性已达到上限(${maxAttribute})！")
                    }

                    // 检查是否达到转生条件
                    if (playerData.baseATK >= 200 && playerData.baseDEF >= 200) {
                        group.sendMessage("你的基础属性已满足转生条件(ATK和DEF≥200)，使用\"/转生\"命令可以转生！")
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

                message == "/转生" -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    // 检查是否达到转生条件
                    if (playerData.baseATK < 200 || playerData.baseDEF < 200) {
                        group.sendMessage("转生需要基础ATK和DEF都达到200以上！")
                        return@subscribeAlways
                    }

                    // 生成随机宠物
                    val newPet = generateRandomPet()

                    // 如果有旧宠物，询问是否更换
                    if (playerData.pet != null) {
                        RebirthConfirmation.addPendingRebirth(senderId, playerData, newPet)
                        group.sendMessage("${sender.nameCardOrNick}，你已满足转生条件！\n" +
                                "转生后将减少150点ATK和DEF，并获得一只新宠物：\n" +
                                "${formatPetInfo(newPet)}\n" +
                                "你当前已拥有宠物：\n" +
                                "${formatPetInfo(playerData.pet!!)}\n" +
                                "是否更换宠物？回复\"是\"更换，回复\"否\"保留原宠物")
                    } else {
                        // 没有宠物，直接设置
                        playerData.pet = newPet
                        playerData.baseATK = (playerData.baseATK - 150).coerceAtLeast(10)
                        playerData.baseDEF = (playerData.baseDEF - 150).coerceAtLeast(10)
                        playerData.rebirthCount++

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
                            // 保留原遗物，但资源已扣除
                            group.sendMessage("已保留原遗物属性（${if (resetType == "gold") "2000喵币" else "50点ATK和DEF"}已扣除）")
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
                            group.sendMessage("已保留原宠物")
                        }

                        // 执行转生
                        playerData.baseATK = (playerData.baseATK - 150).coerceAtLeast(10)
                        playerData.baseDEF = (playerData.baseDEF - 150).coerceAtLeast(10)
                        playerData.rebirthCount++

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
                        +"• /我的信息 - 查看自己的属性、装备宠物遗物等信息\n"
                        +"• /商店 - 查看商店中出售的装备\n"
                        +"• /转生 - 基础属性≥200可转生，获得宠物，基本属性-150\n"
                        +"• /获取遗物 - 消耗5次转生次数获取遗物\n"
                        +"• /喵币重置遗物 - 花费2000喵币重置遗物属性\n"
                        +"• /属性重置遗物 - 消耗50点ATK和DEF重置遗物属性\n"
                        +"• /组队(/加入;/离开队伍) - 创建副本队伍（15min冷却，每日10次）\n\n"
                        +"• /更新日志 查看最新版本的更新内容\n"
                        +"• /兑换码 [兑换码内容] - 领取新手奖励，[兑换码内容]可以询问其他成员\n"
                        +"• 祝您愉快！如有BUG请不要联系管理员~"
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


                message == "/组队" -> {
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
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒")
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.atk ?: 0) +
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)
                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.luck ?: 0) +
                        (playerData.pet?.luck ?: 0) +
                        (playerData.relic?.luck ?: 0)

                    // 创建队伍
                    if (TeamManager.createTeam(senderId, group.id, sender.nameCardOrNick, finalATK, finalLUCK)) {
                        // 显示玩家今日副本次数信息
                        val countInfo = if (playerData.dailyDungeonCount >= 10) {
                            " (奖励次数已达上限)"
                        } else {
                            " (${playerData.dailyDungeonCount}/10)"
                        }

                        // 添加周末狂欢提示
                        val weekendBonusMessage = getWeekendBonusMessage()


                        group.sendMessage("${weekendBonusMessage}\n${sender.nameCardOrNick} 创建了队伍${countInfo}，等待队员加入（5分钟有效）。使用\"/加入\"命令加入队伍。")
                    } else {
                        group.sendMessage("创建队伍失败，可能该群已经有一个队伍了。")
                    }
                }

                message == "/加入" -> {
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
                        group.sendMessage("副本冷却中，还需${minutes}分${seconds}秒")
                        return@subscribeAlways
                    }

                    // 检查当前群是否有队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        group.sendMessage("当前群没有队伍，请先由队长使用\"/组队\"创建队伍。")
                        return@subscribeAlways
                    }

                    // 检查是否已经在队伍中
                    if (TeamManager.isPlayerInTeam(senderId)) {
                        group.sendMessage("你已经在一个队伍中了！")
                        return@subscribeAlways
                    }

                    // 计算玩家最终属性
                    val finalATK = playerData.baseATK +
                        (playerData.equipment?.atk ?: 0) +
                        (playerData.pet?.atk ?: 0) +
                        (playerData.relic?.atk ?: 0)
                    val finalLUCK = playerData.baseLUCK +
                        (playerData.equipment?.luck ?: 0) +
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

                        group.sendMessage("${sender.nameCardOrNick}${countInfo} 加入了队伍。当前队伍人数：${updatedTeam.members.size}/4")

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

                            group.sendMessage("队伍已满！队伍总ATK: $totalATK, 总LUCK: $totalLUCK, 综合战力: $teamPower\n" +
                                "请队长使用\"/选择副本 [1-5]\"命令选择副本。\n副本推荐:\n$dungeonRecommendations")
                        }
                    } else {
                        group.sendMessage("加入队伍失败，可能队伍已满或你已在其他队伍中。")
                    }
                }

                message.startsWith("/选择副本 ") -> {
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

                    val dungeonNum = message.substringAfter("/选择副本 ").trim().toIntOrNull()
                    val dungeon = if (dungeonNum != null) DungeonManager.getDungeonById(dungeonNum) else null

                    if (dungeon == null) {
                        group.sendMessage("请输入有效的副本编号（1-5）。")
                        return@subscribeAlways
                    }

                    // 设置队伍选择的副本
                    team.dungeonId = dungeon.id

                    // 使用 GlobalScope 启动协程处理副本攻略
                    GlobalScope.launch {
                        // 计算队伍总战力和基础成功率
                        val totalATK = team.members.sumOf { it.atk }
                        val totalLUCK = team.members.sumOf { it.luck }
                        val teamPower = totalATK * 0.5 * totalLUCK
                        val baseSuccessRate = (teamPower / dungeon.difficulty).coerceAtMost(1.0)

                        // 发送开始消息
                        val memberNames = team.members.joinToString("，") { it.playerName }
                        group.sendMessage("$memberNames 开始攻略 ${dungeon.name}。")

                        // 生成剧情事件
                        val events = DungeonStoryGenerator.generateEvents(team, dungeon)

                        // 发送前5个事件，每个间隔5秒
                        for (i in 0 until 5) {
                            delay(5000)
                            group.sendMessage(events[i].description)
                        }

                        // 发送BOSS事件
                        delay(5000)
                        group.sendMessage(events[5].description)

                        // 计算总奖励和成功率调整
                        var totalSuccessRateChange = 0.0
                        var totalExtraGold = 0
                        var totalExtraATK = 0
                        var totalExtraDEF = 0

                        // 排除BOSS事件，只计算前5个事件
                        for (i in 0 until 5) {
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

                        // 计算基础奖励
                        val baseReward = dungeon.reward * bonusMultiplier
                        val actualReward = if (success) {
                            baseReward
                        } else {
                            (baseReward * 0.1).toInt().coerceAtLeast(1)
                        }

                        // 计算额外奖励
                        val bonusExtraGold = totalExtraGold * bonusMultiplier
                        val bonusExtraATK = totalExtraATK * bonusMultiplier
                        val bonusExtraDEF = totalExtraDEF * bonusMultiplier

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
                                HallOfFameManager.addRecord(playerNames)
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

                            // 检查是否触发奖励副本 (10%概率)
                            val triggerBonusDungeon = Random.nextDouble() < 0.05
                            if (triggerBonusDungeon) {
                                delay(3000)
                                group.sendMessage("🎉 奇迹发生了！队伍成员发现了一个隐藏的奖励副本！")

                                // 创建奖励副本 (难度x2，奖励x2)
                                val bonusDungeon = Dungeon(
                                    dungeon.id * 10, // 使用特殊ID标识奖励副本
                                    "${dungeon.name}(奖励)",
                                    dungeon.difficulty * 2,
                                    dungeon.reward * 2
                                )

                                // 生成奖励副本事件
                                val bonusEvents = DungeonStoryGenerator.generateBonusDungeonEvents(team, bonusDungeon)

                                // 发送前3个事件，每个间隔4秒
                                for (i in 0 until 3) {
                                    delay(4000)
                                    group.sendMessage(bonusEvents[i].description)
                                }

                                // 发送BOSS事件
                                delay(4000)
                                group.sendMessage(bonusEvents[3].description)

                                // 计算奖励副本的成功率
                                val bonusTotalSuccessRateChange = bonusEvents.take(3).sumOf { it.successRateChange }
                                val bonusBaseSuccessRate = (teamPower / bonusDungeon.difficulty).coerceAtMost(1.0)
                                val bonusFinalSuccessRate = (bonusBaseSuccessRate + bonusTotalSuccessRateChange).coerceIn(0.0, 1.0)
                                val bonusRandom = Random.nextDouble(0.0, 1.0)
                                val bonusSuccess = bonusRandom <= bonusFinalSuccessRate

                                // 计算奖励副本的奖励
                                val bonusBaseReward = bonusDungeon.reward * bonusMultiplier
                                val bonusActualReward = if (bonusSuccess) {
                                    bonusBaseReward
                                } else {
                                    (bonusBaseReward * 0.1).toInt().coerceAtLeast(1)
                                }

                                // 计算额外奖励
                                val bonusTotalExtraGold = bonusEvents.take(3).sumOf { it.extraGold } * bonusMultiplier
                                val bonusTotalExtraATK = bonusEvents.take(3).sumOf { it.extraATK } * bonusMultiplier
                                val bonusTotalExtraDEF = bonusEvents.take(3).sumOf { it.extraDEF } * bonusMultiplier

                                // 平分奖励
                                val bonusRewardPerPerson = (bonusActualReward + bonusTotalExtraGold) / 4

                                // 发送结果
                                delay(5000)

                                if (bonusSuccess) {
                                    group.sendMessage("🌟 队伍成功通过了奖励副本！获得了丰厚的额外奖励！")

                                    val bonusRewardInfo = StringBuilder()
                                    bonusRewardInfo.append("奖励副本攻略成功！每人获得${bonusRewardPerPerson}喵币。")

                                    if (bonusTotalExtraGold > 0) {
                                        bonusRewardInfo.append("\n额外喵币奖励: +${bonusTotalExtraGold}喵币")
                                    }
                                    if (bonusTotalExtraATK > 0) {
                                        bonusRewardInfo.append("\n额外ATK奖励: +${bonusTotalExtraATK}点基础ATK")
                                    }
                                    if (bonusTotalExtraDEF > 0) {
                                        bonusRewardInfo.append("\n额外DEF奖励: +${bonusTotalExtraDEF}点基础DEF")
                                    }

                                    bonusRewardInfo.append("\n基础成功率: ${"%.1f".format(bonusBaseSuccessRate * 100)}%")
                                    bonusRewardInfo.append("\n事件调整: ${if (bonusTotalSuccessRateChange >= 0) "+" else ""}${"%.1f".format(bonusTotalSuccessRateChange * 100)}%")
                                    bonusRewardInfo.append("\n最终成功率: ${"%.1f".format(bonusFinalSuccessRate * 100)}%")

                                    group.sendMessage(bonusRewardInfo.toString())
                                } else {
                                    group.sendMessage("😢 队伍未能在奖励副本中获胜，但仍获得了一些安慰奖励...")

                                    val bonusFailInfo = StringBuilder()
                                    bonusFailInfo.append("奖励副本攻略失败。每人获得${bonusRewardPerPerson}喵币。")
                                    bonusFailInfo.append("\n基础成功率: ${"%.1f".format(baseSuccessRate * 100)}%")
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
                                            // 改为全额奖励，每个队员获得全部额外属性
                                            memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, bonusTotalExtraATK, memberData.rebirthCount)
                                            memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, bonusTotalExtraDEF, memberData.rebirthCount)
                                        }

                                        PlayerDataManager.savePlayerData(memberData)

                                        // 更新奖励消息
                                        val bonusInfo = if (isWeekendBonus) " (周末狂欢双倍奖励)" else ""
                                        bonusRewardMessages.add("${member.playerName} 获得${bonusRewardPerPerson}喵币${if (bonusSuccess) "和属性奖励" else ""}$bonusInfo")
                                    }
                                }

                                //if (bonusRewardMessages.isNotEmpty()) {
                                    //group.sendMessage("奖励分配：\n" + bonusRewardMessages.joinToString("\n"))
                                //}
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
                                        memberData.baseATK = increaseAttributeWithLimit(memberData.baseATK, bonusExtraATK, memberData.rebirthCount)
                                        memberData.baseDEF = increaseAttributeWithLimit(memberData.baseDEF, bonusExtraDEF, memberData.rebirthCount)
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

                                // 无论是否获得奖励，都更新副本CD
                                memberData.lastDungeonTime = System.currentTimeMillis()
                                // 确保清空旧字段（如果存在）
                                memberData.lastDungeonDate = null
                                PlayerDataManager.savePlayerData(memberData)
                            }
                        }

                        // 发送奖励消息
                        //if (rewardMessages.isNotEmpty()) {
                            //group.sendMessage("奖励分配：\n" + rewardMessages.joinToString("\n"))
                        //}

                        // 发送已达上限玩家消息
                        //if (noRewardPlayers.isNotEmpty()) {
                            //group.sendMessage("${noRewardPlayers.joinToString("、")} 今日副本次数已达上限(10/10)，无法获得奖励\n感谢 ${noRewardPlayers.joinToString("、")} 的无私奉献~")
                        //}

                        // 解散队伍
                        TeamManager.disbandTeam(group.id)
                    }
                }

                message == "/离开队伍" -> {
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

                message == "/队伍信息" -> {
                    // 检查当前群是否有队伍
                    val team = TeamManager.getTeamByGroup(group.id)
                    if (team == null) {
                        group.sendMessage("当前群没有队伍。")
                        return@subscribeAlways
                    }

                    val memberList = team.members.joinToString("\n") { member ->
                        val memberData = PlayerDataManager.getPlayerData(member.playerId)
                        val countInfo = if (memberData != null && memberData.dailyDungeonCount >= 10) {
                            " (已达上限)"
                        } else if (memberData != null) {
                            " (${memberData.dailyDungeonCount}/10)"
                        } else {
                            ""
                        }

                        "${member.playerName} (ATK:${member.atk}, LUCK:${member.luck})$countInfo" +
                            if (member.playerId == team.captainId) " [队长]" else ""
                    }

                    val timeLeft = (5 * 60 * 1000 - (System.currentTimeMillis() - team.createTime)) / 1000
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60

                    group.sendMessage("队伍信息（剩余时间: ${minutes}分${seconds}秒）:\n$memberList")
                }


                message == "/副本信息" -> {
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

                message == "/喵币重置遗物" -> {
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
                    if (playerData.gold < 2000) {
                        group.sendMessage("喵币重置需要2000喵币，你当前只有${playerData.gold}喵币")
                        return@subscribeAlways
                    }

                    // 立即扣除喵币
                    playerData.gold -= 2000
                    PlayerDataManager.savePlayerData(playerData)

                    // 生成新遗物并进入确认流程
                    val newRelic = RelicGenerator.generateRandomRelic()
                    RelicConfirmation.addPendingReset(senderId, playerData, newRelic, "gold")

                    group.sendMessage("${sender.nameCardOrNick}，已扣除2000喵币进行遗物重置：\n" +
                        "当前遗物：\n${RelicGenerator.formatRelicInfo(playerData.relic!!)}\n" +
                        "新遗物：\n${RelicGenerator.formatRelicInfo(newRelic)}\n" +
                        "是否替换？回复\"是\"替换，回复\"否\"保留原遗物")
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

                    // 检查属性是否足够扣除
                    if (playerData.baseATK < 50 || playerData.baseDEF < 50) {
                        group.sendMessage("属性重置需要至少50点ATK和DEF！")
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
                        "是否替换？回复\"是\"替换，回复\"否\"保留原遗物")
                }

                message == "/名人堂" -> {
                    val hallOfFame = HallOfFameManager.getFormattedHallOfFame()
                    group.sendMessage(hallOfFame)
                }

                message == "/更新日志" -> {
                    group.sendMessage(UpdateLog.getFormattedLog())
                }

                message.startsWith("/购买 ") -> {
                    // 检查玩家是否已注册
                    if (playerData == null) {
                        group.sendMessage("你还没有注册，请先使用\"/签到\"命令注册")
                        return@subscribeAlways
                    }

                    val equipmentName = message.substringAfter("/购买 ").trim()
                    val equipment = Shop.getEquipmentByName(equipmentName)

                    if (equipment == null) {
                        group.sendMessage("没有找到名为\"$equipmentName\"的装备")
                    } else if (playerData.gold < equipment.price) {
                        group.sendMessage("喵币不足！需要${equipment.price}喵币，你只有${playerData.gold}喵币")
                    } else {
                        // 计算返还喵币（如果有旧装备）
                        val refund = playerData.equipment?.let { (it.price * 1).toInt() } ?: 0
                        val totalCost = equipment.price - refund

                        playerData.gold -= totalCost
                        playerData.equipment = equipment.copy()
                        PlayerDataManager.savePlayerData(playerData)

                        val refundMsg = if (refund > 0) "出售旧装备返还 $refund 喵币，" else ""
                        group.sendMessage("购买成功！${refundMsg}花费${totalCost}喵币，剩余${playerData.gold}喵币")
                    }
                }
            }
        }
    }

    // 创建一个数据类来存储PK结果和额外信息
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
                "C" -> listOf("古老护符", "先祖遗物", "遗迹碎片").random()
                else -> listOf("粗糙遗物", "普通遗物", "常见遗物").random()
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

        fun addPendingReset(userId: Long, playerData: PlayerData, newRelic: Relic, resetType: String) {
            pendingRelicReset[userId] = Triple(playerData, newRelic, resetType)
        }

        fun removePendingReset(userId: Long) {
            pendingRelicReset.remove(userId)
        }

        fun getPendingReset(userId: Long): Triple<PlayerData, Relic, String>? {
            return pendingRelicReset[userId]
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
            "SS" -> listOf("神兽麒麟", "神兽玄武", "神兽朱雀", "神兽白虎").random()
            "S" -> listOf("圣兽腾蛇", "圣兽吞水", "圣兽朱鹤", "圣兽椒图").random()
            "A" -> listOf("仙兽雪狸", "仙兽赢鱼", "仙兽霸下").random()
            "B" -> listOf("猎犬", "野狼", "黑熊").random()
            "C" -> listOf("猫猫", "狗狗", "兔兔").random()
            else -> listOf("小强", "老鼠", "蛆").random()
        }

        return Pet(name, atk, def, luck, grade)
    }

    // 格式化宠物信息
    private fun formatPetInfo(pet: Pet): String {
        return "${pet.name}(${pet.grade}级)\n" +
                "基础ATK+${pet.atk}\n" +
                "基础DEF+${pet.def}\n" +
                "基础LUCK+${pet.luck}"
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

            // 喵币转移（只对真实玩家）
            if (defender.qqId != 0L && defender.gold > 0) {
                // 计算喵币变化，最多不超过50个喵币
                val goldChange = ((defender.gold * 0.1).toInt().coerceAtLeast(1)).coerceAtMost(50)
                attacker.gold += goldChange
                defender.gold -= goldChange
            }

            PkResult(attacker, defender, false, criticalHit, criticalPlayerId, criticalEquipment)
        } else if (defenderPower > attackerPower) {
            // 防御方胜利（反击）
            defender.baseATK = increaseAttributeWithLimit(defender.baseATK, 6, attacker.rebirthCount)
            defender.baseDEF = increaseAttributeWithLimit(defender.baseDEF, 6, attacker.rebirthCount)
            attacker.baseATK = increaseAttributeWithLimit(attacker.baseATK, 3, attacker.rebirthCount)
            attacker.baseDEF = increaseAttributeWithLimit(attacker.baseDEF, 3, attacker.rebirthCount)

            // 喵币转移（只对真实玩家）
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