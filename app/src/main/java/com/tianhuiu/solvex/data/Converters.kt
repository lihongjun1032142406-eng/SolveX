package com.tianhuiu.solvex.data

import androidx.room.TypeConverter
import com.tianhuiu.solvex.data.models.ToolCallRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room 数据库类型转换器。
 *
 * 将 [ToolCallRecord] 列表序列化为 JSON 字符串存储，
 * 读取时反序列化回 Kotlin 对象。
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromToolCallList(value: List<ToolCallRecord>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toToolCallList(value: String): List<ToolCallRecord> {
        return try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
