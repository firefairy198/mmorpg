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
        Equipment("[SR]王国圣剑", atk = 98, def = 68, luck = 8, price = 6666),
        Equipment("[SSR]天使权杖", atk = 138, def = 88, luck = 10, price = 16666),
        Equipment("[UR]魔之宝珠", atk = 198, def = 98, luck = 15, price = 26666),
        // 新增MR装备
        Equipment("[MR]诸神之怒", atk = 278, def = 118, luck = 20, price = 36666)
    )

    // 获取特殊装备的方法
    fun getSpecialEquipmentByName(name: String): Equipment? {
        return specialEquipmentList.find { it.name == name }
    }


    // 道具类商城
    val itemList = listOf(
        Item("S型宠物辅助职业变更券", price = 500, maxStack = 10, description = "(一次购买获得5张)可将宠物职业变更为盗贼S、牧师S、宝藏猎手S、吟游诗人S中的随机一种"),
        Item("鱼饵", price = 5000, maxStack = 50, description = "(一次购买获得5个)钓鱼用的鱼饵，需要持有鱼饵才可以钓鱼~转生次数小于5次不要购买！"),
        Item("炸鱼器", price = 10000, maxStack = 1, description = "一次性获得10条鱼的效果，省时省力！(今日钓鱼次数为0时才能使用)")
    )

    // 道具数据类
    data class Item(
        val name: String,
        val price: Int,
        val maxStack: Int,
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