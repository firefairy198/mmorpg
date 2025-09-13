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
        Equipment("赞助广告位", atk = 114, def = 51, luck = 4, price = 1919810),
    )

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