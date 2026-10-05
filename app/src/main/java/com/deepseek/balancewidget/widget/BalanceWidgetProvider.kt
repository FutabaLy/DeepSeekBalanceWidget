package com.deepseek.balancewidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.deepseek.balancewidget.MainActivity
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.BalanceStore
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.service.BalanceService

/**
 * 桌面插件入口。
 *
 * 刷新链路（三重保险，任一层被 MIUI 干掉都不会让插件变成死数字）：
 * 1. 前台服务 [BalanceService] —— 主链路，默认每 1 秒走倒计时、每 5 秒查余额；
 * 2. `updatePeriodMillis`（30 分钟）—— 系统级兜底，进程被杀也会被拉起；
 * 3. [BalanceRefreshWorker] —— 开机 / 手动点击时的一次性立即刷新。
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HolidayCalendar.init(context)
        val settings = AppSettings.get(context)
        val state = currentState(context)
        val interval = settings.intervalSeconds.value

        for (id in appWidgetIds) {
            val views = WidgetRenderer.build(context, state, settings.tickEverySecond.value, interval)
            appWidgetManager.updateAppWidget(id, views)
            maybeStartConfigure(context, appWidgetManager, id)
        }

        Log.i(TAG, "onUpdate：${appWidgetIds.size} 个插件实例")
        WidgetWorkScheduler.schedulePeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val settings = AppSettings.get(context)
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetRenderer.build(
                context,
                currentState(context),
                settings.tickEverySecond.value,
                settings.intervalSeconds.value,
            ),
        )
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "首个插件实例已添加")
        WidgetWorkScheduler.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "插件实例已全部移除")
        // 插件都删了就没必要继续跑前台服务，省电
        BalanceService.stop(context)
        WidgetWorkScheduler.cancelPeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        if (!WidgetRenderer.hasWidgetInstances(context)) {
            BalanceService.stop(context)
            WidgetWorkScheduler.cancelPeriodic(context)
        }
    }

    /** 已配置过密钥却仍走到这里（例如系统重配置），自动打开设置页让用户可改。 */
    private fun maybeStartConfigure(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ) {
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull() ?: return
        val needReconfigure = options.getBoolean(AppWidgetManager.OPTION_APPWIDGET_RECONFIGURE, false)
        // 正常添加流程由 appwidget-provider 的 android:configure 接管，这里只处理「用户主动重配置」，
        // 避免和系统配置流程重复弹窗。
        if (!needReconfigure) return
        if (AppSettings.get(context).apiKey.value.isBlank()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val pending = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { pending.send() }
            .onFailure { Log.w(TAG, "无法自动打开配置页（部分系统限制后台启动 Activity）", it) }
    }

    companion object {
        private const val TAG = "BalanceWidgetProvider"

        /** 立即刷新所有插件实例。 */
        fun updateAll(context: Context, state: WidgetState? = null) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            val s = state ?: currentState(context)
            val settings = AppSettings.get(context)
            val views = WidgetRenderer.build(
                context,
                s,
                settings.tickEverySecond.value,
                settings.intervalSeconds.value,
            )
            manager.updateAppWidget(ids, views)
        }

        /**
         * 用「缓存 + 当前时段」拼出一个可渲染的状态。
         * 服务没在跑时（进程刚被杀、刚开机）也能显示上次的余额。
         */
        fun currentState(context: Context): WidgetState {
            val cached = BalanceStore.load(context)
            val now = DateUtil.nowBeijing()
            val tier = AppSettings.get(context).effectiveTier(now)
            val (_, nextTier) = com.deepseek.balancewidget.core.PeakScheduler.nextTransition(now)
            return WidgetState(
                tier = tier,
                autoTier = AppSettings.get(context).isAutoTier,
                nextTier = nextTier,
                countdownMillis = com.deepseek.balancewidget.core.PeakScheduler.timeUntilBoundary(now).toMillis(),
                dayContext = HolidayCalendar.dayContext(now),
                balance = cached?.toBalanceInfo(),
                isAvailable = cached?.isAvailable ?: true,
                hasData = cached != null,
                lastSuccessAt = cached?.updatedAt ?: 0L,
                lastError = null,
                loading = false,
                serviceRunning = BalanceService.isRunning,
            )
        }
    }
}
