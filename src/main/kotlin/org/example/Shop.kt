//Shop.kt
package org.example.mmorpg

object Shop {
    val equipmentList = listOf(
        Equipment("独伊的白丝", atk = 5, def = 5, luck = 0, price = 100),
        Equipment("讲黑大旗", atk = 20, def = 0, luck = 1, price = 500),
        Equipment("库洛的外卖", atk = 0, def = 20, luck = 1, price = 500),
        Equipment("幸运护符", atk = 5, def = 5, luck = 2, price = 2000),
        Equipment("叶凡的Ball", atk = 6, def = 6, luck = 3, price = 5000),
        Equipment("茯苓大片", atk = 16, def = 16, luck = 4, price = 15000),
        Equipment("屠狗利刃", atk = 50, def = 25, luck = 5, price = 33333),
        Equipment("无Ming大剑", atk = 66, def = 96, luck = 6, price = 66666),
    )

    // 新增特殊装备（不在商店出售）
    val specialEquipmentList = listOf(
        Equipment("[SR]王国圣剑", atk = 98, def = 68, luck = 8, price = 36666),
        Equipment("[SSR]天使权杖", atk = 138, def = 88, luck = 10, price = 66666),
        Equipment("[UR]魔之宝珠", atk = 198, def = 98, luck = 15, price = 96666)
    )

    // 获取特殊装备的方法
    fun getSpecialEquipmentByName(name: String): Equipment? {
        return specialEquipmentList.find { it.name == name }
    }


    // 新增道具列表
    val itemList = listOf(
        Item("隐藏副本进入券", price = 999, maxStack = 1, description = "使用后确保隐藏副本的出现（需每人均持有1枚）")
    )

    // 道具数据类
    data class Item(
        val name: String,
        val price: Int,
        val maxStack: Int, // 最大堆叠数量
        val description: String
    )

    fun getItemByName(name: String): Item? {
        return itemList.find { it.name == name }
    }

    fun getEquipmentByName(name: String): Equipment? {
        return equipmentList.find { it.name == name }
    }

    // 新增方法：生成简洁的装备描述
    fun getEquipmentDescription(equipment: Equipment): String {
        val attributes = mutableListOf<String>()

        if (equipment.atk != 0) attributes.add("ATK+${equipment.atk}")
        if (equipment.def != 0) attributes.add("DEF+${equipment.def}")
        if (equipment.luck != 0) attributes.add("LUCK+${equipment.luck}")

        return if (attributes.isNotEmpty()) {
            "${equipment.name} (${attributes.joinToString(", ")}) - 价格: ${equipment.price}喵币"
        } else {
            "${equipment.name} - 价格: ${equipment.price}喵币"
        }
    }
}