package com.deepseek.balancewidget.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * 余额快照的轻量持久化。
 *
 * 作用：进程被系统回收（MIUI 常见）后，桌面插件刷新时仍能立刻画出「上次的余额」，
 * 而不是退回到 `--` 占位；同时让通知与服务之间共享同一份数据。
 */
object BalanceStore {

    private const val PREFS = "deepseek_balance_cache"
    private const val KEY_JSON = "snapshot"

    data class Cached(
        val currency: String,
        val total: String,
        val granted: String,
        val toppedUp: String,
        val isAvailable: Boolean,
        val updatedAt: Long,
    ) {
        fun toBalanceInfo(): BalanceInfo = BalanceInfo(
            currency = currency,
            totalBalance = total,
            grantedBalance = granted,
            toppedUpBalance = toppedUp,
        )
    }

    fun save(context: Context, snapshot: BalanceSnapshot) {
        val info = snapshot.primary ?: return
        val json = JSONObject().apply {
            put("currency", info.currency)
            put("total", info.totalBalance)
            put("granted", info.grantedBalance)
            put("toppedUp", info.toppedUpBalance)
            put("available", snapshot.isAvailable)
            put("updatedAt", System.currentTimeMillis())
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_JSON, json.toString()) }
    }

    fun load(context: Context): Cached? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Cached(
                currency = o.optString("currency", "CNY"),
                total = o.optString("total", "0"),
                granted = o.optString("granted", "0"),
                toppedUp = o.optString("toppedUp", "0"),
                isAvailable = o.optBoolean("available", true),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}
