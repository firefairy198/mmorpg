//Equipment.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable

@Serializable
data class Equipment(
    val name: String,
    val atk: Int,
    val def: Int,
    val luck: Int,
    val price: Int
)
