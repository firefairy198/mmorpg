// Picture.kt
package org.example.mmorpg

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.message.data.Message
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.io.File

// Picture.kt
object PictureGenerator {

    // 定义字体变量
    private var customFont: Font? = null

    init {
        // 初始化时尝试加载自定义字体
        loadCustomFont()
    }

    // 加载自定义字体
    private fun loadCustomFont() {
        try {
            val fontFile = File(PluginMain.dataFolder, "HanYiZhongYuanJian.ttf")
            if (fontFile.exists()) {
                val fontStream = fontFile.inputStream()
                customFont = Font.createFont(Font.TRUETYPE_FONT, fontStream)
                PluginMain.logger.info("成功加载自定义字体: HanYiZhongYuanJian.ttf")
            } else {
                PluginMain.logger.warning("自定义字体文件不存在: ${fontFile.absolutePath}")
            }
        } catch (e: Exception) {
            PluginMain.logger.error("加载自定义字体时发生错误", e)
        }
    }

    // 获取字体方法 - 如果自定义字体加载失败，使用备用字体
    private fun getFont(style: Int = Font.PLAIN, size: Float): Font {
        return try {
            customFont?.deriveFont(style, size) ?: Font("Microsoft YaHei", style, size.toInt())
        } catch (e: Exception) {
            PluginMain.logger.error("使用自定义字体时发生错误，使用备用字体", e)
            Font("Microsoft YaHei", style, size.toInt())
        }
    }

    // 卡片样式配置
    data class CardStyle(
        val backgroundColor: Color = Color(216, 242, 255), // #AAE9FF
        val titleColor: Color = Color(0, 0, 0), // 黑色标题，与浅蓝背景对比
        val textColor: Color = Color(0, 0, 0), // 黑色文本
        val sectionColor: Color = Color(200, 230, 255), // 浅蓝色区域
        val progressBarColor: Color = Color(70, 130, 180),
        val highlightColor: Color = Color(255, 105, 180) // 粉色高亮
    )

    // 生成玩家信息卡片
    suspend fun generatePlayerInfoCard(
        playerName: String,
        playerData: PlayerData,
        finalATK: Long,
        finalDEF: Long,
        finalLUCK: Long,
        contact: Contact
    ): Message {
        val image = createPlayerInfoImage(playerName, playerData, finalATK, finalDEF, finalLUCK)
        return convertToMiraiImage(image, contact)
    }

    private fun createPlayerInfoImage(
        playerName: String,
        playerData: PlayerData,
        finalATK: Long,
        finalDEF: Long,
        finalLUCK: Long
    ): BufferedImage {
        val width = 400
        val height = calculateCardHeight(playerData) // 这里会计算包含项链的高度
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.graphics as Graphics2D

        // 设置渲染质量
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val style = getCardStyle(playerData)

        // 绘制背景和内容
        drawBackground(g, width, height, style)
        var currentY = drawHeader(g, playerName, width, style)
        currentY = drawAttributes(g, playerData, finalATK, finalDEF, finalLUCK, width, currentY, style)
        currentY = drawResources(g, playerData, width, currentY, style)
        currentY = drawEquipment(g, playerData, width, currentY, style)
        currentY = drawPet(g, playerData, width, currentY, style)
        currentY = drawRelic(g, playerData, width, currentY, style)
        currentY = drawLuckyNecklace(g, playerData, width, currentY, style) // 确保这行存在

        // 确保内容不会超出图片边界
        if (currentY > height - 10) {
            // 如果内容接近底部，绘制一个简单的底部边框
            g.color = style.titleColor
            g.drawLine(10, height - 5, width - 10, height - 5)
        }

        g.dispose()
        return image
    }

    // 添加绘制幸运项链的方法
    private fun drawLuckyNecklace(g: Graphics2D, playerData: PlayerData, width: Int, startY: Int, style: CardStyle): Int {
        if (playerData.luckyNecklace == null) return startY

        g.font = getFont(Font.BOLD, 16f)
        g.color = style.highlightColor
        g.drawString("幸运项链", 20, startY)

        g.font = getFont(Font.PLAIN, 12f)
        g.color = style.textColor

        val necklace = playerData.luckyNecklace!!
        var currentY = startY + 20

        // 项链名称和罕见度
        g.drawString(necklace.getName(), 20, currentY)
        currentY += 18

        // 项链属性 - 确保每个属性都显示在一行
        necklace.attributes.forEachIndexed { index, attr ->
            val attrText = when (attr.type) {
                NecklaceAttributeType.ATK -> "ATK+${attr.value}"
                NecklaceAttributeType.DEF -> "DEF+${attr.value}"
                NecklaceAttributeType.LUCK -> "LUCK+${attr.value}"
                NecklaceAttributeType.POW -> "POW+${attr.displayValue}"
            }
            g.drawString("属性${index + 1}: $attrText", 20, currentY)
            currentY += 16
        }

        currentY += 10
        return currentY
    }

    private fun calculateCardHeight(playerData: PlayerData): Int {
        var height = 220 // 减少基础高度，从250减到220

        // 根据内容动态调整高度
        if (playerData.equipment != null) {
            height += 70 // 装备信息高度
        }

        if (playerData.pet != null) {
            height += 70 // 宠物信息高度
            if (playerData.devouredPets.isNotEmpty()) {
                height += 40 // 吞噬信息高度
            }
        }

        if (playerData.relic != null) {
            height += 70 // 遗物信息高度
        }

        //新增：幸运项链高度计算
        if (playerData.luckyNecklace != null) {
            val necklace = playerData.luckyNecklace!!
            // 基础高度：标题 + 名称行 + 每个属性行
            height += 60 // 标题和基础信息高度
            height += necklace.attributes.size * 16 // 每个属性一行
            height += 10 // 底部间距
        }

        // 添加底部边距
        height += 20

        return height
    }

    private fun getCardStyle(playerData: PlayerData): CardStyle {
        // 移除特殊条件，所有玩家使用统一颜色
        return CardStyle()
    }

    private fun drawBackground(g: Graphics2D, width: Int, height: Int, style: CardStyle) {
        // 绘制渐变背景
        val gradient = GradientPaint(0f, 0f, style.backgroundColor, width.toFloat(), height.toFloat(), Color(216, 242, 255))
        g.paint = gradient
        g.fillRect(0, 0, width, height)

        // 绘制边框
        g.color = style.titleColor
        g.stroke = BasicStroke(3f)
        g.drawRoundRect(5, 5, width - 10, height - 10, 20, 20)
    }

    private fun drawHeader(g: Graphics2D, playerName: String, width: Int, style: CardStyle): Int {
        // 设置字体
        val titleFont = getFont(Font.BOLD, 20f) // 减小字体大小
        g.font = titleFont
        g.color = style.titleColor

        // 绘制标题
        val title = "$playerName - 个人信息"
        val titleWidth = g.fontMetrics.stringWidth(title)
        g.drawString(title, (width - titleWidth) / 2, 35) // 调整位置

        return 65 // 调整起始Y位置
    }

    private fun drawAttributes(
        g: Graphics2D,
        playerData: PlayerData,
        finalATK: Long,
        finalDEF: Long,
        finalLUCK: Long,
        width: Int,
        startY: Int,
        style: CardStyle
    ): Int {
        val sectionFont = getFont(Font.BOLD, 16f)
        val textFont = getFont(Font.PLAIN, 12f)
        val finalStatsFont = getFont(Font.BOLD, 14f)

        g.font = sectionFont
        g.color = style.highlightColor

        // 绘制章节标题和最终属性值在同一行
        g.drawString("基础属性", 20, startY)

        // 在右侧绘制最终属性值，间隔100像素
        val finalStatsX = 20 + 100 // 基础属性右侧100像素

        // ATK - 加粗红色
        g.color = Color.RED
        g.drawString("ATK:$finalATK", finalStatsX, startY)

        // DEF - 加粗蓝色
        g.color = Color.BLUE
        g.drawString("DEF:$finalDEF", finalStatsX + 80, startY) // 每个属性间隔80像素

        // LUCK - 加粗黄色 (使用RGB 184,172,71)
        g.color = Color(184, 172, 71)
        g.drawString("LUCK:$finalLUCK", finalStatsX + 160, startY)

        g.font = textFont
        g.color = style.textColor

        var currentY = startY + 25

        // 计算属性上限
        val maxAttribute = 225 + 10 * playerData.rebirthCount

        // 绘制ATK进度条
        g.color = style.textColor
        g.drawString("ATK: ${playerData.baseATK}/$maxAttribute", 20, currentY - 5)
        drawProgressBar(g, 20, currentY, width - 60, playerData.baseATK, maxAttribute, "", style)
        currentY += 30

        // 绘制DEF进度条 - 将DEF标签和进度条都向下移动5像素
        g.color = style.textColor
        g.drawString("DEF: ${playerData.baseDEF}/$maxAttribute", 20, currentY) // 从currentY-5改为currentY
        drawProgressBar(g, 20, currentY + 5, width - 60, playerData.baseDEF, maxAttribute, "", style)
        currentY += 35 // 从30改为35，因为DEF部分向下移动了5像素

        currentY += 10 // 增加一些间距

        return currentY
    }

    private fun drawProgressBar(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        current: Int,
        max: Int,
        label: String,
        style: CardStyle
    ) {
        // 注意：这里移除了标签绘制，因为标签现在在进度条上方单独绘制

        // 绘制进度条背景
        g.color = Color(100, 100, 100)
        g.fillRoundRect(x, y, width, 15, 8, 8)

        // 绘制进度 - 确保不会超出
        val progressWidth = (current.toDouble() / max * width).toInt().coerceIn(0, width)
        g.color = when {
            current >= max -> Color(50, 205, 50) // 绿色
            current >= max * 0.7 -> Color(255, 165, 0) // 橙色
            else -> style.progressBarColor // 蓝色
        }
        g.fillRoundRect(x, y, progressWidth, 15, 8, 8)

        // 绘制边框
        g.color = style.textColor
        g.drawRoundRect(x, y, width, 15, 8, 8)

        // 绘制百分比
        val percent = (current.toDouble() / max * 100).toInt()
        val percentText = "$percent%"
        g.drawString(percentText, x + width + 5, y + 12)
    }

    private fun drawResources(g: Graphics2D, playerData: PlayerData, width: Int, startY: Int, style: CardStyle): Int {
        g.font = getFont(Font.BOLD, 16f)
        g.color = style.highlightColor
        g.drawString("资源信息", 20, startY)

        g.font = getFont(Font.PLAIN, 12f)

        var currentY = startY + 20

        // 第一行：喵币和转生次数
        g.color = style.textColor
        g.drawString("喵币: ${playerData.gold}", 20, currentY)
        g.drawString("汪币: ${playerData.wangCoin}", 100, currentY)
        g.drawString("转生: ${playerData.rebirthCount}次", 200, currentY)
        currentY += 20

        // 第二行：彩笔信息 - 用对应颜色显示
        g.drawString("彩笔: ", 20, currentY)

        var penX = 50 // 彩笔起始X坐标
        val penY = currentY

        // 红彩笔 - 红色
        if (playerData.redPenCount > 0) {
            g.color = Color.RED
            g.drawString("红×${playerData.redPenCount}", penX, penY)
            penX += g.fontMetrics.stringWidth("红×${playerData.redPenCount} ") + 5
        }

        // 蓝彩笔 - 蓝色
        if (playerData.bluePenCount > 0) {
            g.color = Color.BLUE
            g.drawString("蓝×${playerData.bluePenCount}", penX, penY)
            penX += g.fontMetrics.stringWidth("蓝×${playerData.bluePenCount} ") + 5
        }

        // 黄彩笔 - 黄色
        if (playerData.yellowPenCount > 0) {
            g.color = Color(184, 172, 71)
            g.drawString("黄×${playerData.yellowPenCount}", penX, penY)
            penX += g.fontMetrics.stringWidth("黄×${playerData.yellowPenCount} ") + 5
        }

        // 黑彩笔 - 黑色
        if (playerData.blackPenCount > 0) {
            g.color = Color.BLACK
            g.drawString("黑×${playerData.blackPenCount}", penX, penY)
        }

        // 如果没有彩笔，显示"无"
        if (playerData.redPenCount == 0 && playerData.bluePenCount == 0 &&
            playerData.yellowPenCount == 0 && playerData.blackPenCount == 0) {
            g.color = style.textColor
            g.drawString("无", penX, penY)
        }

        g.color = style.textColor
        currentY += 20

        // 第三行：其他道具

        if (playerData.miraclePillCount > 0) {
            g.color = style.textColor
        }

        g.drawString("小药丸: ${playerData.miraclePillCount}个", 180, currentY)
        g.drawString("变更券: ${playerData.sPetChangeTickets}张", 20, currentY)
        currentY += 30

        return currentY
    }

    private fun drawEquipment(g: Graphics2D, playerData: PlayerData, width: Int, startY: Int, style: CardStyle): Int {
        if (playerData.equipment == null) return startY

        g.font = getFont(Font.BOLD, 16f)
        g.color = style.highlightColor
        g.drawString("装备信息", 20, startY)

        g.font = getFont(Font.PLAIN, 12f)
        g.color = style.textColor

        val equipment = playerData.equipment!!

        // 装备名称和强化等级
        val enhanceText = if (equipment.enhanceLevel > 0) "+${equipment.enhanceLevel}" else ""
        val equipmentText = if (enhanceText.isNotEmpty()) {
            "${equipment.name} $enhanceText"
        } else {
            equipment.name
        }

        var currentY = startY + 20
        g.drawString(equipmentText, 20, currentY)
        currentY += 18

        // 装备详细属性 - 显示基础值和强化加成
        val enhancedAtk = equipment.getEnhancedAtk()
        val enhancedDef = equipment.getEnhancedDef()
        val enhancedLuck = equipment.getEnhancedLuck()

        val atkIncrease = enhancedAtk - equipment.atk
        val defIncrease = enhancedDef - equipment.def
        val luckIncrease = enhancedLuck - equipment.luck

        val statsText = "ATK+$enhancedAtk(${equipment.atk}+$atkIncrease), " +
            "DEF+$enhancedDef(${equipment.def}+$defIncrease), " +
            "LUCK+$enhancedLuck(${equipment.luck}+$luckIncrease)"

        // 如果属性文本太长，进行换行处理
        val maxWidth = width - 40
        val statsLines = wrapText(g, statsText, maxWidth)

        for (line in statsLines) {
            g.drawString(line, 20, currentY)
            currentY += 16
        }

        currentY += 10

        return currentY
    }

    private fun drawPet(g: Graphics2D, playerData: PlayerData, width: Int, startY: Int, style: CardStyle): Int {
        if (playerData.pet == null) return startY

        g.font = getFont(Font.BOLD, 16f)
        g.color = style.highlightColor
        g.drawString("宠物信息", 20, startY)

        g.font = getFont(Font.PLAIN, 12f)
        g.color = style.textColor

        val pet = playerData.pet!!
        val effectName = when (pet.specialEffect) {
            PetEffect.WARRIOR -> "战士"
            PetEffect.WARRIOR_S -> "战士S"
            PetEffect.ARCHER -> "弓手"
            PetEffect.ARCHER_S -> "弓手S"
            PetEffect.THIEF -> "盗贼"
            PetEffect.THIEF_S -> "盗贼S"
            PetEffect.PRIEST -> "牧师"
            PetEffect.PRIEST_S -> "牧师S"
            PetEffect.TREASURE_HUNTER -> "宝藏猎手"
            PetEffect.TREASURE_HUNTER_S -> "宝藏猎手S"
            PetEffect.BARD -> "吟游诗人"
            PetEffect.BARD_S -> "吟游诗人S"
            else -> ""
        }

        val petName = if (effectName.isNotEmpty()) {
            "[$effectName]${pet.name}"
        } else {
            pet.name
        }

        var currentY = startY + 20

        // 第一行：宠物名称和等级
        g.drawString("$petName(${pet.grade}级)", 20, currentY)
        currentY += 18

        // 第二行：宠物基础属性
        val petStats = "基础属性: ATK+${pet.atk} DEF+${pet.def} LUCK+${pet.luck}"
        g.drawString(petStats, 20, currentY)
        currentY += 18

        // 第三行：吞噬属性（如果有）
        if (playerData.devouredPets.isNotEmpty()) {
            val devouredInfo = "吞噬属性: ATK+${playerData.devouredATK} DEF+${playerData.devouredDEF} LUCK+${playerData.devouredLUCK}"
            g.drawString(devouredInfo, 20, currentY)
            currentY += 18

            // 显示具体吞噬的宠物 - 全部在一行内显示
            val devouredPetsList = playerData.devouredPets.entries.toList()
            val petsString = StringBuilder("吞噬宠物: ")

            for ((petName, petCount) in devouredPetsList) {
                val petDisplayName = if (petCount > 1) "$petName$petCount" else petName
                petsString.append(petDisplayName)
            }

            g.drawString(petsString.toString(), 20, currentY)
            currentY += 20
        } else {
            currentY += 5
        }

        return currentY
    }

    private fun drawRelic(g: Graphics2D, playerData: PlayerData, width: Int, startY: Int, style: CardStyle): Int {
        if (playerData.relic == null) return startY

        g.font = getFont(Font.BOLD, 16f)
        g.color = style.highlightColor
        g.drawString("遗物信息", 20, startY + 5) // 向下移动5像素

        g.font = getFont(Font.PLAIN, 12f)
        g.color = style.textColor

        val relic = playerData.relic!!
        var currentY = startY + 25 // 调整起始位置

        // 遗物名称和等级
        g.drawString("${relic.name} (${relic.grade}级)", 20, currentY)
        currentY += 18

        // 遗物详细属性 - 显示基础值和染色加成
        val atkWithBonus = relic.atk + playerData.relicAtkBonus
        val defWithBonus = relic.def + playerData.relicDefBonus
        val luckWithBonus = relic.luck + playerData.relicLuckBonus

        val statsText = "ATK+$atkWithBonus(${relic.atk}+${playerData.relicAtkBonus}), " +
            "DEF+$defWithBonus(${relic.def}+${playerData.relicDefBonus}), " +
            "LUCK+$luckWithBonus(${relic.luck}+${playerData.relicLuckBonus})"

        // 如果属性文本太长，进行换行处理
        val maxWidth = width - 40
        val statsLines = wrapText(g, statsText, maxWidth)

        for (line in statsLines) {
            g.drawString(line, 20, currentY)
            currentY += 16
        }

        currentY += 5 // 减少底部间距

        return currentY
    }

    private fun wrapText(g: Graphics2D, text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        // 按逗号分割，保持属性对的完整性
        val segments = text.split(", ")
        var currentLine = StringBuilder()

        for (segment in segments) {
            val testLine = if (currentLine.isEmpty()) segment else "$currentLine, $segment"
            val testWidth = g.fontMetrics.stringWidth(testLine)

            if (testWidth <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(segment)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    private suspend fun convertToMiraiImage(image: BufferedImage, contact: Contact): Message {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", outputStream)
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())

        // 使用正确的方式上传图片
        return contact.uploadImage(inputStream)
    }
}