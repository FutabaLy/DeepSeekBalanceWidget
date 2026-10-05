package com.deepseek.balancewidget.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 中国法定节假日日历（单例，进程内共享）。
 *
 * 数据来源两级：
 * 1. 内置 assets/holidays.json —— 离线可用，保证冷启动立刻有数据；
 * 2. 用户点击「更新节假日」或服务启动时，从 timor.tech 拉取并写入 SharedPreferences 覆盖内置数据。
 *
 * 只有**放假日**和**调休补班日**需要记录：
 * - 放假日（holiday = true）→ 全天算空闲时段
 * - 调休补班日（holiday = false）→ 虽然是周末，但按工作日算，有峰谷之分
 * - 其余日子按普通周一~周五/周末规则推导
 */
object HolidayCalendar {

    private const val TAG = "HolidayCalendar"
    private const val PREFS = "holiday_calendar"
    private const val KEY_JSON = "holiday_json"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val ASSET_NAME = "holidays.json"

    /** key = "yyyy-MM-dd"，value = true 放假 / false 补班。 */
    @Volatile
    private var holidays: Map<String, Boolean> = emptyMap()

    @Volatile
    private var names: Map<String, String> = emptyMap()

    @Volatile
    var updatedAt: Long = 0L
        private set

    @Volatile
    private var loaded = false

    /** 进程启动时调用一次；重复调用只生效一次。 */
    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val app = context.applicationContext

        // 1) 先读上次联网更新的缓存
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_JSON, null)
        var applied = false
        if (!cached.isNullOrBlank()) {
            applied = apply(cached)
            if (applied) updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        }

        // 2) 缓存不可用则回落到内置资产
        if (!applied) {
            runCatching {
                app.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }.onSuccess { apply(it) }
                .onFailure { Log.w(TAG, "读取内置节假日数据失败", it) }
        }

        loaded = true
        Log.i(TAG, "节假日日历已加载：${holidays.size} 条特殊日期")
    }

    /**
     * 解析并应用一份节假日 JSON。支持两种结构：
     * - 内置格式：`{ "2026-02-17": { "h": true, "n": "初一" } }`
     * - timor.tech 格式：`{ "code":0, "holiday": { "02-17": { "holiday": true, "name": "初一", "date": "2026-02-17" } } }`
     */
    @Synchronized
    fun apply(json: String): Boolean {
        return runCatching {
            val root = JSONObject(json)
            val container = if (root.has("holiday") && root.opt("holiday") is JSONObject) {
                root.getJSONObject("holiday")
            } else {
                root
            }
            val h = HashMap<String, Boolean>()
            val n = HashMap<String, String>()
            val keys = container.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                if (rawKey.startsWith("_")) continue
                val value = container.opt(rawKey)
                if (value !is JSONObject) continue

                val isHoliday = when {
                    value.has("h") -> value.optBoolean("h", true)
                    value.has("holiday") -> value.optBoolean("holiday", true)
                    else -> true
                }
                val dateKey = normalizeDateKey(rawKey, value.optString("date", "")) ?: continue
                h[dateKey] = isHoliday
                val name = value.optString("name", value.optString("n", ""))
                if (name.isNotBlank()) n[dateKey] = name
            }
            if (h.isEmpty()) return false
            holidays = h
            names = n
            true
        }.getOrElse {
            Log.w(TAG, "节假日 JSON 解析失败", it)
            false
        }
    }

    /** 把更新后的 JSON 落盘，下次冷启动直接可用。 */
    fun persist(context: Context, json: String) {
        val now = System.currentTimeMillis()
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_UPDATED_AT, now)
            .apply()
        updatedAt = now
    }

    /** 日历条数，用于界面展示。 */
    val size: Int get() = holidays.size

    /** null = 普通日子，true = 放假，false = 调休补班。 */
    fun holidayOf(date: LocalDate): Boolean? = holidays[date.toString()]

    /** 节假日名称，例如「国庆节」「春节后补班」。 */
    fun nameOf(date: LocalDate): String? = names[date.toString()]

    /** 该日是否是法定放假日。 */
    fun isStatutoryHoliday(date: LocalDate): Boolean = holidays[date.toString()] == true

    /** 该日是否是周末调休补班日。 */
    fun isMakeupWorkday(date: LocalDate): Boolean = holidays[date.toString()] == false

    /** 当前（北京时间）日期上下文，供界面展示说明文字。 */
    fun dayContext(now: LocalDateTime = DateUtil.nowBeijing()): DayContext {
        val date = now.toLocalDate()
        return DayContext(
            date = date,
            isWorkday = PeakScheduler.isWorkday(date),
            holidayName = nameOf(date),
        )
    }

    private fun normalizeDateKey(rawKey: String, dateField: String): String? {
        val fromField = dateField.trim()
        if (fromField.length >= 10 && fromField[4] == '-') return fromField.substring(0, 10)

        val raw = rawKey.trim()
        if (raw.length >= 10 && raw[4] == '-') return raw.substring(0, 10)

        // "MM-dd"：按当年推断（跨年 1 月数据时当前年份-1 也合并一次，避免边界丢失）
        val md = if (raw.length == 5 && raw[2] == '-') raw else return null
        val year = DateUtil.nowBeijing().year
        return "$year-$md"
    }
}
