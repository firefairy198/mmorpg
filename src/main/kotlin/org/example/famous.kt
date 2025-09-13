// famous.kt
package org.example.mmorpg

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 名人堂记录数据类
@Serializable
data class HallOfFameRecord(
    val date: String,
    val players: List<String>
)

// 名人堂管理器
object HallOfFameManager {
    private val dataFile by lazy {
        File(PluginMain.dataFolder, "hall_of_fame.json").apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
                // 初始化文件为空列表
                writeText("[]")
            }
        }
    }

    private val json = Json { prettyPrint = true }
    private val maxRecords = 10 // 最多保留10条记录

    // 添加新的名人堂记录
    fun addRecord(players: List<String>) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val records = getAllRecords().toMutableList()

        // 只保留前10条记录，超过10条时不添加新记录
        if (records.size >= maxRecords) {
            PluginMain.logger.info("名人堂记录已满（${maxRecords}条），不再添加新记录")
            return
        }

        // 添加新记录（允许同一天多个队伍记录）
        records.add(HallOfFameRecord(today, players))

        // 保存到文件
        try {
            dataFile.writeText(json.encodeToString(records))
            PluginMain.logger.info("成功添加名人堂记录：${today} - ${players.joinToString()}")
        } catch (e: Exception) {
            PluginMain.logger.error("保存名人堂记录时发生错误", e)
        }
    }

    // 获取所有名人堂记录
    fun getAllRecords(): List<HallOfFameRecord> {
        return try {
            json.decodeFromString<List<HallOfFameRecord>>(dataFile.readText())
        } catch (e: Exception) {
            PluginMain.logger.error("读取名人堂记录时发生错误", e)
            emptyList()
        }
    }

    // 获取格式化后的名人堂信息
    fun getFormattedHallOfFame(): String {
        val records = getAllRecords()
        if (records.isEmpty()) {
            return "暂无名人堂记录，还不快冲？"
        }

        val builder = StringBuilder()
        builder.append("成功讨伐难度5副本的勇者昵称为：\n")

        records.forEach { record ->
            builder.append("[${record.date}]-${record.players.joinToString("，")}\n")
        }

        return builder.toString()
    }
}