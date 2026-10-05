package com.deepseek.balancewidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 余额查询客户端：OkHttp + 协程，返回 [Result]。
 *
 * OkHttp 会自动协商 TLS 1.2/1.3，兼容国内网络环境。
 */
class BalanceRepository(
    private val baseUrlProvider: () -> String = { DeepSeekApi.DEFAULT_BASE_URL },
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetchBalance(apiKey: String): Result<BalanceSnapshot> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("请先在 App 里填写 API Key"))
        }
        if (!DeepSeekApi.looksLikeApiKey(apiKey)) {
            return@withContext Result.failure(IllegalStateException("API Key 格式看起来不对"))
        }

        val base = baseUrlProvider().trimEnd('/').ifBlank { DeepSeekApi.DEFAULT_BASE_URL }
        val request = Request.Builder()
            .url("$base/user/balance")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "application/json")
            .header("User-Agent", "DeepSeekBalanceWidget/1.0 (Android)")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        runCatching { DeepSeekApi.parseBalance(body) }
                            .fold(
                                onSuccess = { Result.success(it) },
                                onFailure = { Result.failure(it) },
                            )
                    }

                    response.code == 401 -> Result.failure(
                        IllegalStateException("401 鉴权失败：API Key 无效或已被删除"),
                    )

                    response.code == 402 -> Result.failure(
                        IllegalStateException("402 余额不足：账户已欠费"),
                    )

                    response.code == 429 -> Result.failure(
                        IllegalStateException("429 请求过于频繁，请调大刷新间隔"),
                    )

                    else -> Result.failure(
                        IllegalStateException(
                            "HTTP ${response.code}：${body.take(160).ifBlank { response.message }}",
                        ),
                    )
                }
            }
        } catch (e: IOException) {
            Result.failure(IllegalStateException(networkHint(e), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: e.javaClass.simpleName, e))
        }
    }

    private fun networkHint(e: IOException): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("Unable to resolve host", true) -> "无法解析域名，检查网络或 DNS 设置"
            raw.contains("timeout", true) -> "网络超时，稍后自动重试"
            raw.contains("SSL", true) || raw.contains("Certificate", true) ->
                "TLS 握手失败（网络可能被劫持或代理拦截）"
            raw.isBlank() -> "网络异常：${e.javaClass.simpleName}"
            else -> "网络异常：$raw"
        }
    }
}
