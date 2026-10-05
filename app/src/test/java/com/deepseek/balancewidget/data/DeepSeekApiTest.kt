package com.deepseek.balancewidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 余额接口响应解析测试。
 *
 * 覆盖官方文档给出的正常响应，以及鉴权失败、字段缺失等异常情况。
 */
class DeepSeekApiTest {

    @Test
    fun `解析官方示例响应`() {
        val json = """
            {
              "is_available": true,
              "balance_infos": [
                {
                  "currency": "CNY",
                  "total_balance": "110.00",
                  "granted_balance": "10.00",
                  "topped_up_balance": "100.00"
                }
              ]
            }
        """.trimIndent()

        val snapshot = DeepSeekApi.parseBalance(json)

        assertTrue(snapshot.isAvailable)
        assertEquals(1, snapshot.infos.size)

        val info = snapshot.primary!!
        assertEquals("CNY", info.currency)
        assertEquals("110.00", info.totalBalance)
        assertEquals("10.00", info.grantedBalance)
        assertEquals("100.00", info.toppedUpBalance)
        assertEquals("¥", info.currencySymbol)
        assertEquals("¥110.00", snapshot.totalText)
    }

    @Test
    fun `余额耗尽时 is_available 为 false`() {
        val json = """
            {"is_available": false, "balance_infos": [
              {"currency":"CNY","total_balance":"0.00","granted_balance":"0.00","topped_up_balance":"0.00"}
            ]}
        """.trimIndent()

        val snapshot = DeepSeekApi.parseBalance(json)
        assertFalse(snapshot.isAvailable)
        assertEquals("¥0.00", snapshot.totalText)
    }

    @Test
    fun `多币种优先取人民币`() {
        val json = """
            {"is_available": true, "balance_infos": [
              {"currency":"USD","total_balance":"5.00","granted_balance":"0","topped_up_balance":"5.00"},
              {"currency":"CNY","total_balance":"36.50","granted_balance":"6.50","topped_up_balance":"30.00"}
            ]}
        """.trimIndent()

        val snapshot = DeepSeekApi.parseBalance(json)
        assertEquals("CNY", snapshot.primary!!.currency)
        assertEquals("¥36.50", snapshot.totalText)
    }

    @Test
    fun `空余额列表不崩溃`() {
        val snapshot = DeepSeekApi.parseBalance("""{"is_available":false,"balance_infos":[]}""")
        assertNull(snapshot.primary)
        assertEquals("--", snapshot.totalText)
    }

    @Test(expected = DeepSeekApi.ApiException::class)
    fun `error 字段会抛出可展示的异常`() {
        DeepSeekApi.parseBalance("""{"error":{"message":"Authentication Fails","type":"authentication_error"}}""")
    }

    @Test(expected = DeepSeekApi.ApiException::class)
    fun `非法 JSON 抛出异常`() {
        DeepSeekApi.parseBalance("<html>502 Bad Gateway</html>")
    }

    @Test(expected = DeepSeekApi.ApiException::class)
    fun `缺少关键字段抛出异常`() {
        DeepSeekApi.parseBalance("""{"foo":"bar"}""")
    }

    @Test
    fun `API Key 粗校验`() {
        // 样例用拼接构造：避免把形如真实 Key 的字面量写进仓库（会触发 GitHub 推送保护）
        val sample = "sk-" + "0123456789abcdef".repeat(2)
        assertTrue(DeepSeekApi.looksLikeApiKey(sample))
        assertFalse(DeepSeekApi.looksLikeApiKey(""))
        assertFalse(DeepSeekApi.looksLikeApiKey("short"))
        assertFalse(DeepSeekApi.looksLikeApiKey("sk-" + "0123 456789"))
        assertFalse(DeepSeekApi.looksLikeApiKey("sk-" + "0123456789abcdef" + "\n" + "0123456789"))
    }

    @Test
    fun `金额转数字失败返回 null 而不是抛异常`() {
        val info = BalanceInfo("CNY", "abc", "0", "0")
        assertNull(info.totalAsDouble)
    }
}
