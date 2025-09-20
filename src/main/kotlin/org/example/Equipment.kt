//Equipment.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable

@Serializable
data class Equipment(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val price: Int,
    var enhanceLevel: Int = 0 // 新增：强化等级，默认为0
) {
    // 计算强化后的ATK
    fun getEnhancedAtk(): Int {
        return (atk * (1 + enhanceLevel * 0.1)).toInt()
    }

    // 计算强化后的DEF
    fun getEnhancedDef(): Int {
        return (def * (1 + enhanceLevel * 0.1)).toInt()
    }

    // 计算强化后的LUCK
    fun getEnhancedLuck(): Int {
        return (luck * (1 + enhanceLevel * 0.1)).toInt()
    }

    // 获取带强化等级的装备名称
    fun getDisplayName(): String {
        return if (enhanceLevel > 0) "$name+$enhanceLevel" else name
    }

    // 获取下一级强化所需金币
    fun getNextEnhanceCost(): Int {
        return (enhanceLevel + 1) * (enhanceLevel + 1) * 100
    }

    // 获取下一级强化成功率
    fun getNextEnhanceSuccessRate(): Double {
        return when (enhanceLevel) {
            0 -> 1.0      // +0 → +1: 100% (1.0^2)
            1 -> 0.81     // +1 → +2: 81% (0.9^2)
            2 -> 0.64     // +2 → +3: 64% (0.8^2)
            3 -> 0.49     // +3 → +4: 49% (0.7^2)
            4 -> 0.36     // +4 → +5: 36% (0.6^2)
            5 -> 0.25     // +5 → +6: 25% (0.5^2)
            6 -> 0.16     // +6 → +7: 16% (0.4^2)
            7 -> 0.09     // +7 → +8: 9% (0.3^2)
            8 -> 0.04     // +8 → +9: 4% (0.2^2)
            9 -> 0.01     // +9 → +10: 1% (0.1^2)
            else -> 0.0
        }
    }
}