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
        Dungeon(1, "初级洞穴", 4000, 400),  //1-1
        Dungeon(2, "中级地牢", 20000, 600),  //1-2
        Dungeon(3, "高级城堡", 100000, 1000),  //5-10
        Dungeon(4, "精英神殿", 500000, 1600), //25-50
        Dungeon(5, "魔王深渊", 2500000, 2400)  //125-250
    )

    fun getDungeonById(id: Int): Dungeon? {
        return dungeons.find { it.id == id }
    }
}