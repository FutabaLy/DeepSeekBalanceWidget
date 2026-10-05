package com.deepseek.balancewidget.data

import android.content.Context
import androidx.core.content.edit

/**
 * 2×2 紧凑插件的「翻面」状态。
 *
 * 正面（默认）= 封面图，点一下翻到数据面（余额 / 今日已用 / 峰谷 / 倒计时）；
 * 在数据面上再点一下翻回封面。
 *
 * 按 widgetId 分开存，桌面上放多个 2×2 时互不影响。
 */
object CompactFaceStore {

    private const val PREFS = "deepseek_compact_face"

    /** true = 显示封面图，false = 显示数据面。 */
    fun isCover(context: Context, widgetId: Int): Boolean =
        prefs(context).getBoolean(key(widgetId), true)

    fun setCover(context: Context, widgetId: Int, cover: Boolean) {
        prefs(context).edit { putBoolean(key(widgetId), cover) }
    }

    /** 翻面，返回翻面后是否显示封面图。 */
    fun toggle(context: Context, widgetId: Int): Boolean {
        val next = !isCover(context, widgetId)
        setCover(context, widgetId, next)
        return next
    }

    /** 删掉插件时清掉它的状态，避免 id 复用后串味。 */
    fun forget(context: Context, widgetId: Int) {
        prefs(context).edit { remove(key(widgetId)) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(widgetId: Int) = "cover_$widgetId"
}
