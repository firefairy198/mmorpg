//Dungeon.kt
package org.example.mmorpg

data class Dungeon(
    val id: Int,
    val name: String,
    val difficulty: Long,
    val reward: Int
)

object DungeonManager {
    val dungeons = listOf(
        Dungeon(1, "初级洞穴", 4000L, 2400),
        Dungeon(2, "中级地牢", 20000L, 2000),
        Dungeon(3, "高级城堡", 100000L, 1000),
        Dungeon(4, "精英神殿", 500000L, 1600),
        Dungeon(5, "魔王深渊", 2500000L, 800),
        Dungeon(6, "天堂宝库", 12500000L, 400),
        Dungeon(7, "神殿", 100000000L, 200),
        Dungeon(8, "仙境", 2000000000L, 100)
    )

    fun getDungeonById(id: Int): Dungeon? {
        return dungeons.find { it.id == id }
    }
}