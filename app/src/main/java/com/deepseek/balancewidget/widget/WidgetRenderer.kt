package com.deepseek.balancewidget.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.deepseek.balancewidget.MainActivity
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.WidgetState

/**
 * 把 [WidgetState] 渲染成桌面插件 / 通知的 RemoteViews。
 *
 * 配色约定：
 * - 空闲（谷）：青绿 #12D6A0，卡片偏冷蓝黑
 * - 高峰（峰）：琥珀 #FFB020，卡片偏暖黑
 *
 * 大字余额是主视觉，峰/谷徽章与倒计时一眼可辨。
 */
object WidgetRenderer {

    // 配色
    private const val COLOR_PEAK = 0xFFFFB020.toInt()
    private const val COLOR_OFF_PEAK = 0xFF12D6A0.toInt()
    private const val COLOR_TEXT_TITLE = 0xFF8FA6C0.toInt()
    private const val COLOR_TEXT_SYNC = 0xFF7C90A8.toInt()
    private const val COLOR_TEXT_MAIN = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SUB = 0xFFC6D6E8.toInt()
    private const val COLOR_TEXT_ERROR = 0xFFFF8A80.toInt()
    private const val COLOR_BG_PEAK = 0xE81C1206.toInt()
    private const val COLOR_BG_OFF_PEAK = 0xE80A1A24.toInt()

    const val ACTION_REFRESH = "com.deepseek.balancewidget.action.REFRESH"
    const val ACTION_TOGGLE_TIER = "com.deepseek.balancewidget.action.TOGGLE_TIER"

    fun build(
        context: Context,
        state: WidgetState,
        showSeconds: Boolean = true,
        intervalSeconds: Int = 5,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        val accent = if (state.tier.isPeak) COLOR_PEAK else COLOR_OFF_PEAK
        val bgColor = if (state.tier.isPeak) COLOR_BG_PEAK else COLOR_BG_OFF_PEAK

        // 卡片背景与徽章
        // 注意：这里用 setBackgroundTintList 而不是 setBackgroundColor —— 后者会丢掉
        // bg_badge / bg_button 的圆角形状；tint 才能在保留胶囊圆角的同时换色。
        views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
        views.setColorStateList(
            R.id.tv_tier,
            "setBackgroundTintList",
            ColorStateList.valueOf(withAlpha(accent, 0x38)),
        )
        views.setTextColor(R.id.tv_tier, accent)
        views.setTextViewText(
            R.id.tv_tier,
            state.tier.shortLabel + if (state.autoTier) "" else "*",
        )

        // 标题
        views.setTextViewText(R.id.tv_title, context.getString(R.string.widget_title_default))
        views.setTextColor(R.id.tv_title, COLOR_TEXT_TITLE)

        // 同步状态：把状态行与「x 秒刷新」拼在一起，间隔数值来自用户设置
        val statusText = if (state.lastError != null) {
            state.lastError
        } else {
            state.statusText() + " · ${intervalSeconds}秒"
        }
        views.setTextViewText(R.id.tv_sync, statusText)
        views.setTextColor(
            R.id.tv_sync,
            if (state.lastError != null) COLOR_TEXT_ERROR else COLOR_TEXT_SYNC,
        )

        // 余额大字
        views.setTextViewText(R.id.tv_balance, state.balanceText)
        views.setTextColor(R.id.tv_balance, COLOR_TEXT_MAIN)

        // 倒计时
        views.setTextViewText(
            R.id.tv_countdown,
            "${state.countdownLabel} ${state.countdownText(showSeconds)}",
        )
        views.setTextColor(R.id.tv_countdown, COLOR_TEXT_SUB)

        // 节假日 / 补班标记
        val badge = state.dayBadge()
        views.setTextViewText(R.id.tv_day, badge.orEmpty())
        views.setViewVisibility(R.id.tv_day, if (badge == null) View.GONE else View.VISIBLE)

        // 余额明细
        val detail = buildDetail(state)
        views.setTextViewText(R.id.tv_detail, detail.orEmpty())
        views.setViewVisibility(R.id.tv_detail, if (detail == null) View.GONE else View.VISIBLE)

        // 刷新按钮：请求中变半透明，给出即时反馈（背景保持圆角形状，仅调透明度）
        views.setImageViewResource(R.id.btn_refresh, R.drawable.ic_refresh)
        if (state.loading) {
            views.setFloat(R.id.btn_refresh, "setAlpha", 0.35f)
        } else {
            views.setFloat(R.id.btn_refresh, "setAlpha", 1.0f)
        }

        // 点击事件：点刷新按钮 → 立即刷新；点卡片其它区域 → 打开设置页
        views.setOnClickPendingIntent(R.id.btn_refresh, broadcast(context, ACTION_REFRESH, 1))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 2))
        views.setOnClickPendingIntent(R.id.tv_tier, broadcast(context, ACTION_TOGGLE_TIER, 3))

        return views
    }

    /** 「赠金 ¥9.29 · 充值 ¥100.00」，官方扣费规则是先用赠金。 */
    private fun buildDetail(state: WidgetState): String? {
        val b = state.balance ?: return null
        if (state.lastError != null) return null
        return "赠金 ${b.currencySymbol}${twoDecimals(b.grantedBalance)}" +
            " · 充值 ${b.currencySymbol}${twoDecimals(b.toppedUpBalance)}"
    }

    /** "9.290000" → "9.29"；无法解析时原样返回。 */
    private fun twoDecimals(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        return String.format(java.util.Locale.CHINA, "%.2f", d)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 判断某个 provider 当前是否还有存活的插件实例。 */
    fun hasWidgetInstances(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
        return ids != null && ids.isNotEmpty()
    }
}
