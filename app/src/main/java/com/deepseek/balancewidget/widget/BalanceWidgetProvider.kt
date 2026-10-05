package com.deepseek.balancewidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.BalanceStore
import com.deepseek.balancewidget.data.DailyUsageStore
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.service.BalanceService
import com.deepseek.balancewidget.worker.WidgetWorkScheduler

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
            // 「用户主动重配置」由系统依据 widget_balance_info.xml 里的
            // android:widgetFeatures="reconfigurable" + android:configure 直接拉起 MainActivity，
            // 这里既不需要也无法再自己弹一次配置页。
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
        Log.i(TAG, "4×2 实例已全部移除")
        // 两类插件都清空了才停服务，省电同时不误伤另一种尺寸
        stopServiceIfNoWidgetLeft(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        stopServiceIfNoWidgetLeft(context)
    }

    /** 只有两类插件（4×2 / 2×2）都清空时才停服务。 */
    private fun stopServiceIfNoWidgetLeft(context: Context) {
        if (hasAnyWidgetInstances(context)) return
        BalanceService.stop(context)
        WidgetWorkScheduler.cancelPeriodic(context)
    }

    companion object {
        private const val TAG = "BalanceWidgetProvider"

        /**
         * 立即刷新所有插件实例。
         *
         * 这里是「刷新全部插件」的唯一入口：4×2 画完后顺手把 2×2 也刷新，
         * 所以服务、兜底任务、设置页改动都只用调这一个方法。
         */
        fun updateAll(context: Context, state: WidgetState? = null) {
            val s = state ?: currentState(context)
            updateStandard(context, s)
            CompactBalanceWidgetProvider.updateAll(context, s)
        }

        private fun updateStandard(context: Context, state: WidgetState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            val settings = AppSettings.get(context)
            val views = WidgetRenderer.build(
                context,
                state,
                settings.tickEverySecond.value,
                settings.intervalSeconds.value,
            )
            manager.updateAppWidget(ids, views)
        }

        /** 桌面上是否还有任意一种插件实例（4×2 或 2×2）。 */
        fun hasAnyWidgetInstances(context: Context): Boolean {
            if (WidgetRenderer.hasWidgetInstances(context)) return true
            val manager = AppWidgetManager.getInstance(context) ?: return false
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CompactBalanceWidgetProvider::class.java),
            )
            return ids != null && ids.isNotEmpty()
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
                usedToday = DailyUsageStore.usedTodayYuan(context),
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
