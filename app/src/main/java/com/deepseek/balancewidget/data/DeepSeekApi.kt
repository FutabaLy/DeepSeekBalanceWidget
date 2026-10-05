package com.deepseek.balancewidget.data

import org.json.JSONException
import org.json.JSONObject

/**
 * DeepSeek 开放平台接口调用。
 *
 * 余额接口不发消息、不计费，可以安全地高频轮询（默认 5 秒一次）。
 * 文档：GET https://api.deepseek.com/user/balance
 *       Header: Authorization: Bearer <API Key>
 */
object DeepSeekApi {

    const val DEFAULT_BASE_URL = "https://api.deepseek.com"

    /** 判断 API Key 是否明显无效（本地粗校验，避免无意义的网络请求）。 */
    fun looksLikeApiKey(key: String): Boolean {
        val k = key.trim()
        return k.length in 16..200 && !k.contains(' ') && !k.contains('\n')
    }

    /**
     * 解析余额响应体。
     * @throws ApiException 当返回体不符合预期时抛出，message 可直接展示给用户。
     */
    @Throws(ApiException::class)
    fun parseBalance(json: String): BalanceSnapshot {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw ApiException("返回内容不是合法 JSON：${json.take(120)}")
        }

        // 有些情况下（如鉴权失败）会返回 {"error": {...}}
        if (root.has("error")) {
            val err = root.optJSONObject("error")
            val msg = err?.optString("message").orEmpty().ifBlank { "接口返回错误" }
            throw ApiException(msg)
        }

        val rawInfos = root.optJSONArray("balance_infos")
        val infos = ArrayList<BalanceInfo>()
        if (rawInfos != null) {
            for (i in 0 until rawInfos.length()) {
                val o = rawInfos.optJSONObject(i) ?: continue
                infos += BalanceInfo(
                    currency = o.optString("currency", "CNY"),
                    totalBalance = o.optString("total_balance", "0"),
                    grantedBalance = o.optString("granted_balance", "0"),
                    toppedUpBalance = o.optString("topped_up_balance", "0"),
                )
            }
        }

        if (infos.isEmpty() && !root.has("is_available")) {
            throw ApiException("返回体缺少 balance_infos 字段：${json.take(120)}")
        }

        return BalanceSnapshot(
            isAvailable = root.optBoolean("is_available", infos.isNotEmpty()),
            infos = infos,
        )
    }

    class ApiException(message: String) : Exception(message)
}
