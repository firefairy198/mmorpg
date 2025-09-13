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
    var lastFindOpponentTime: Long = 0
)

// 新增宠物数据类
@Serializable
data class Pet(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val grade: String // 宠物等级
)

@Serializable
data class Relic(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val grade: String // 遗物品级
)