//Dungeon.kt
package org.example.mmorpg

data class Dungeon(
    val id: Int,
    val name: String,
    val difficulty: Int,
    val reward: Int
)

object DungeonManager {
    val dungeons = listOf(
        Dungeon(1, "初级洞穴", 4000, 2400),
        Dungeon(2, "中级地牢", 20000, 2000),
        Dungeon(3, "高级城堡", 100000, 1000),
        Dungeon(4, "精英神殿", 500000, 1600),
        Dungeon(5, "魔王深渊", 2500000, 800),
        Dungeon(6, "天堂宝库", 12500000, 400),
        Dungeon(7, "神殿", 100000000, 200)
    )

    fun getDungeonById(id: Int): Dungeon? {
        return dungeons.find { it.id == id }
    }
}