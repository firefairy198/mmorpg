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
    // 新增兑换码使用记录字段
    var usedCodes: MutableSet<String> = mutableSetOf(), // 存储已使用的兑换码
    var hiddenDungeonTickets: Int = 0, // 隐藏副本进入券数量
    var sPetChangeTickets: Int = 0 // 新增：S型宠物辅助职业变更券数量
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
    WARRIOR,       // 战士：提升团队副本总ATK 33%
    WARRIOR_S,     // 战士S：提升团队副本总ATK 50%
    THIEF,         // 盗贼：提升团队副本总收益 25%
    THIEF_S,       // 盗贼S：提升团队副本总收益 25%
    ARCHER,        // 弓手：提升团队副本总LUCK 10%
    ARCHER_S,      // 弓手S：提升团队副本总LUCK 20%
    PRIEST,        // 牧师：提升正向事件概率 5%
    PRIEST_S,      // 牧师S：提升正向事件概率 10%
    TREASURE_HUNTER, // 宝藏猎手：增加装备掉落概率 5%
    TREASURE_HUNTER_S, // 宝藏猎手S：增加装备掉落概率 10%
    BARD,          // 吟游诗人：增加隐藏副本出现概率 5%
    BARD_S         // 吟游诗人S：增加隐藏副本出现概率 10%
}

@Serializable
data class Relic(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val grade: String // 遗物品级
)