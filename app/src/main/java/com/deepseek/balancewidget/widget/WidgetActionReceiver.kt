package com.deepseek.balancewidget.widget

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.CompactFaceStore
import com.deepseek.balancewidget.service.BalanceService
import com.deepseek.balancewidget.service.Notifier

/**
 * 插件按钮 / 通知按钮的入口。
 *
 * 刻意做成「不依赖服务存活」：点击后直接改设置、重画插件，
 * 再尽力通知服务去拉新数据——服务被 MIUI 清掉时按钮依然有反馈。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "收到动作：$action")

        when (action) {
            WidgetRenderer.ACTION_REFRESH,
            Notifier.ACTION_REFRESH,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                // 先立刻重画（保证按钮有即时反馈），再请求服务刷新余额
                BalanceWidgetProvider.updateAll(context)
                BalanceService.requestRefresh(context, action)
            }

            WidgetRenderer.ACTION_TOGGLE_TIER,
            Notifier.ACTION_TOGGLE_TIER,
            -> toggleTierMode(context)

            CompactWidgetRenderer.ACTION_FLIP -> {
                val widgetId = intent?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                flipCompactWidget(context, widgetId)
            }

            else -> Unit
        }
    }

    /** 2×2 插件翻面：封面图 ⇄ 数据面（只重画被点的那个实例）。 */
    private fun flipCompactWidget(context: Context, widgetId: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val nowCover = CompactFaceStore.toggle(context, widgetId)
        CompactBalanceWidgetProvider.updateOne(context, widgetId)
        Log.i(TAG, "2×2 插件 $widgetId 翻面 → ${if (nowCover) "封面图" else "数据面"}")
    }

    /** 在「自动 → 强制高峰 → 强制谷时」之间循环，用于系统时间不准或想提前摸底。 */
    private fun toggleTierMode(context: Context) {
        val settings = AppSettings.get(context)
        val next = when (settings.overrideMode.value) {
            AppSettings.OVERRIDE_AUTO -> AppSettings.OVERRIDE_PEAK
            AppSettings.OVERRIDE_PEAK -> AppSettings.OVERRIDE_OFF_PEAK
            else -> AppSettings.OVERRIDE_AUTO
        }
        settings.setOverrideMode(next)

        val state = BalanceWidgetProvider.currentState(context)
        BalanceWidgetProvider.updateAll(context, state)
        Notifier.update(context, state)

        // 通知栏被关闭时不弹提示，避免报错
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(TOAST_NOTIFICATION_ID)
        }
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"
        private const val TOAST_NOTIFICATION_ID = 1002
    }
}
