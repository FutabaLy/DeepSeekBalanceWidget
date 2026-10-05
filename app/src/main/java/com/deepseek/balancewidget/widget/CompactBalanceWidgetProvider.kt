package com.deepseek.balancewidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.CompactFaceStore
import com.deepseek.balancewidget.service.BalanceService
import com.deepseek.balancewidget.worker.WidgetWorkScheduler
import java.util.Collections

/**
 * 2×2 紧凑插件入口（浅色主题，双层的：封面图 ⇄ 数据面）。
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
        val state = BalanceWidgetProvider.currentState(context)
        val tick = AppSettings.get(context).tickEverySecond.value
        for (id in appWidgetIds) {
            remember(id, CompactFaceStore.isCover(context, id))
            appWidgetManager.updateAppWidget(id, buildFor(context, id, state, tick))
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
        val tick = AppSettings.get(context).tickEverySecond.value
        remember(appWidgetId, CompactFaceStore.isCover(context, appWidgetId))
        appWidgetManager.updateAppWidget(
            appWidgetId,
            buildFor(context, appWidgetId, BalanceWidgetProvider.currentState(context), tick),
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
        for (id in appWidgetIds) {
            // 清掉翻面状态，避免 widgetId 复用后新插件直接是数据面
            CompactFaceStore.forget(context, id)
            pushedCovers.remove(id)
        }
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

        /**
         * 已经推送过封面图的实例。
         *
         * 封面是静态图片，而服务每秒都会带着新的倒计时来刷新；如果不记这一笔，
         * 桌面每秒都要重新贴一次同一张图。翻面或系统回调时会重新推送。
         * 广播在主线程、服务在协程线程，所以用同步集合。
         */
        private val pushedCovers: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())

        private fun remember(widgetId: Int, cover: Boolean) {
            if (cover) pushedCovers.add(widgetId) else pushedCovers.remove(widgetId)
        }

        /** 按该实例当前的翻面状态渲染：封面图 or 数据面。 */
        fun buildFor(
            context: Context,
            widgetId: Int,
            state: WidgetState,
            tickEverySecond: Boolean,
        ): RemoteViews = if (CompactFaceStore.isCover(context, widgetId)) {
            CompactWidgetRenderer.buildCover(context, widgetId)
        } else {
            CompactWidgetRenderer.build(context, state, tickEverySecond, widgetId)
        }

        /** 立即刷新所有 2×2 插件实例（各自按自己的翻面状态渲染）。 */
        fun updateAll(context: Context, state: WidgetState? = null) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CompactBalanceWidgetProvider::class.java),
            )
            if (ids == null || ids.isEmpty()) return
            val s = state ?: BalanceWidgetProvider.currentState(context)
            val tick = AppSettings.get(context).tickEverySecond.value
            pushedCovers.retainAll(ids.toSet())
            for (id in ids) {
                val cover = CompactFaceStore.isCover(context, id)
                if (cover && pushedCovers.contains(id)) continue // 封面没变化，不必每秒重推
                remember(id, cover)
                manager.updateAppWidget(id, buildFor(context, id, s, tick))
            }
        }

        /** 只刷新一个实例（翻面时用，避免动到别的 2×2）。 */
        fun updateOne(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val tick = AppSettings.get(context).tickEverySecond.value
            remember(widgetId, CompactFaceStore.isCover(context, widgetId))
            manager.updateAppWidget(
                widgetId,
                buildFor(context, widgetId, BalanceWidgetProvider.currentState(context), tick),
            )
        }

        /** 换了封面图：清掉「已推过封面」的记账，让下一次刷新把新图推上去。 */
        fun onCoverChanged(context: Context) {
            pushedCovers.clear()
            updateAll(context)
        }
    }
}
