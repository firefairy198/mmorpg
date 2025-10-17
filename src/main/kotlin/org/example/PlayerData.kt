// PlayerData.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable

@Serializable
data class PlayerData(
    val qqId: Long,
    var baseATK: Int = 10,
    var baseDEF: Int = 10,
    var baseLUCK: Int = 10,
    var gold: Int = 0,
    var wangCoin: Int = 0,
    var equipment: Equipment? = null,
    var lastPkTime: Long = 0,
    var lastSignDate: String = "",
    var pet: Pet? = null,
    var rebirthCount: Int = 0,
    var lastDungeonDate: String? = null,
    var lastDungeonTime: Long = 0,
    var dailyDungeonCount: Int = 0,
    var lastDungeonResetDate: String = "",
    var relic: Relic? = null,
    var lastFindOpponentTime: Long = 0,
    var usedCodes: MutableSet<String> = mutableSetOf(),
    var hiddenDungeonTickets: Int = 0,
    var sPetChangeTickets: Int = 0,
    var enhanceFailCount: Int = 0,
    var devouredATK: Int = 0,
    var devouredDEF: Int = 0,
    var devouredLUCK: Int = 0,
    var devouredPets: MutableMap<String, Int> = mutableMapOf(),
    var relicAtkBonus: Int = 0,
    var relicDefBonus: Int = 0,
    var relicLuckBonus: Int = 0,
    var luckyNecklace: LuckyNecklace? = null,
    // 新增：消息返回样式设置 (null或0=文字，1=图片)
    var messageBack: Int? = null,
    // 鱼塘加入时间
    var pondJoinTime: String? = null,
    // 精英鱼攻击冷却时间
    var lastEliteFishAttackTime: Long = 0,
    // 保留鱼饵相关字段（在彩笔数量之前）
    var fishBaitCount: Int = 0,
    var dailyFishBaitUsed: Int = 0,
    var lastFishResetDate: String = "",
    var fishBombCount: Int = 0,
    // 新增：高级炸鱼器字段
    var advancedFishBombCount: Int = 0,
    // 彩笔数量，保证彩笔数量在最下边
    var redPenCount: Int = 0,
    var bluePenCount: Int = 0,
    var yellowPenCount: Int = 0,
    var blackPenCount: Int = 0,
    // 新增：神奇小药丸数量
    var miraclePillCount: Int = 0
)

// 新增宠物数据类
@Serializable
data class Pet(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val grade: String,
    val specialEffect: PetEffect? = null
)

// 添加宠物特殊效果枚举
enum class PetEffect {
    WARRIOR,
    WARRIOR_S,
    THIEF,
    THIEF_S,
    ARCHER,
    ARCHER_S,
    PRIEST,
    PRIEST_S,
    TREASURE_HUNTER,
    TREASURE_HUNTER_S,
    BARD,
    BARD_S
}

@Serializable
data class Relic(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val grade: String
)
