package com.deepseek.balancewidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.service.BalanceService
import com.deepseek.balancewidget.worker.WidgetWorkScheduler

/**
 * 2×2 紧凑插件入口（浅色主题）。
 *
 * 与 4×2 版共用同一份数据与同一条刷新链路：前台服务、系统 updatePeriodMillis、
 * WorkManager 兜底任务都会同时刷新两类插件，桌面上放哪个/放几个都行。
 */
class CompactBalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HolidayCalendar.init(context)
        val settings = AppSettings.get(context)
        val views = CompactWidgetRenderer.build(
            context,
            BalanceWidgetProvider.currentState(context),
            settings.tickEverySecond.value,
        )
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
        Log.i(TAG, "onUpdate：${appWidgetIds.size} 个 2×2 实例")
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
            CompactWidgetRenderer.build(
                context,
                BalanceWidgetProvider.currentState(context),
                settings.tickEverySecond.value,
            ),
        )
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "首个 2×2 实例已添加")
        WidgetWorkScheduler.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "2×2 实例已全部移除")
        stopServiceIfNoWidgetLeft(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        stopServiceIfNoWidgetLeft(context)
    }

    /** 只有两类插件都清空时才停服务，避免桌面上还留着 4×2 却被误停。 */
    private fun stopServiceIfNoWidgetLeft(context: Context) {
        if (BalanceWidgetProvider.hasAnyWidgetInstances(context)) return
        BalanceService.stop(context)
        WidgetWorkScheduler.cancelPeriodic(context)
    }

    companion object {
        private const val TAG = "CompactWidgetProvider"

        /** 立即刷新所有 2×2 插件实例。 */
        fun updateAll(context: Context, state: WidgetState? = null) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CompactBalanceWidgetProvider::class.java),
            )
            if (ids == null || ids.isEmpty()) return
            val settings = AppSettings.get(context)
            val views = CompactWidgetRenderer.build(
                context,
                state ?: BalanceWidgetProvider.currentState(context),
                settings.tickEverySecond.value,
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
