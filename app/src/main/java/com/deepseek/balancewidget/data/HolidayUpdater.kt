package com.deepseek.balancewidget.data

import android.content.Context
import android.util.Log
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.HolidayCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 联网更新法定节假日日历（timor.tech 免费接口）。
 *
 * 目的：法定节假日每年由国务院办公厅在年底公布，内置资产不可能永远最新。
 * 联网更新并落盘后，「法定节假日全天算空闲时段」的判定即可长期保持准确。
 *
 * 拉取「去年 + 今年 + 明年」三年，合并成内置格式后一次性覆盖：
 * `{ "2026-10-01": { "h": true, "n": "国庆节" }, ... }`
 */
object HolidayUpdater {

    private const val TAG = "HolidayUpdater"
    private const val ENDPOINT = "https://timor.tech/api/holiday/year"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return 成功时返回合并后的日历条数
     */
    suspend fun update(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        val year = DateUtil.nowBeijing().year
        val merged = JSONObject()
        merged.put("_comment", "联网更新于 ${DateUtil.nowBeijing().toLocalDate()}")

        var successYears = 0
        var lastError: String? = null
        var count = 0

        for (y in listOf(year - 1, year, year + 1)) {
            val raw = runCatching { fetchYear(y) }
                .onFailure { lastError = it.message }
                .getOrNull() ?: continue
            successYears++
            count += appendYear(merged, raw)
        }

        if (successYears == 0) {
            return@withContext Result.failure(
                IllegalStateException(lastError ?: "节假日接口无响应，请检查网络后重试"),
            )
        }
        if (count == 0) {
            return@withContext Result.failure(IllegalStateException("接口返回了数据，但没有解析出有效日期"))
        }

        val json = merged.toString()
        if (!HolidayCalendar.apply(json)) {
            return@withContext Result.failure(IllegalStateException("节假日数据解析失败"))
        }
        HolidayCalendar.persist(context, json)
        Log.i(TAG, "节假日更新成功：$successYears 个年份 / ${HolidayCalendar.size} 条特殊日期")
        Result.success(HolidayCalendar.size)
    }

    private fun fetchYear(year: Int): String {
        val request = Request.Builder()
            .url("$ENDPOINT/$year")
            .header("Accept", "application/json")
            .header("User-Agent", "DeepSeekBalanceWidget/1.0 (Android)")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            if (!body.contains("\"holiday\"")) throw IllegalStateException("$year 年暂无节假日数据")
            body
        }
    }

    /** 把某年的响应并入 [target]，返回并入条数。 */
    private fun appendYear(target: JSONObject, raw: String): Int {
        val holiday = runCatching { JSONObject(raw).optJSONObject("holiday") }.getOrNull() ?: return 0
        var added = 0
        val keys = holiday.keys()
        while (keys.hasNext()) {
            val entry = holiday.optJSONObject(keys.next()) ?: continue
            val date = entry.optString("date", "").trim()
            if (date.length < 10) continue
            val item = JSONObject()
            item.put("h", entry.optBoolean("holiday", true))
            item.put("n", entry.optString("name", ""))
            target.put(date.substring(0, 10), item)
            added++
        }
        return added
    }
}
