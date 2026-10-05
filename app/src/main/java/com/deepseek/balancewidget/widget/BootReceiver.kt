package com.deepseek.balancewidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.service.ServiceController
import com.deepseek.balancewidget.worker.WidgetWorkScheduler

/**
 * 开机自启。
 *
 * 注意：Android 8.0+ 起，[android.content.Intent.ACTION_BOOT_COMPLETED] 之后不允许直接启动前台服务
 * （Android 12+ 更严格），所以这里不硬启服务，而是：
 * 1. 重新载入节假日日历、重画一次插件；
 * 2. 注册 WorkManager 周期兜底任务，并立刻执行一次刷新；
 * 3. 若用户已把本应用加入电池优化白名单（小米建议开启），再尝试拉起前台服务恢复秒级刷新。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        Log.i(TAG, "收到 $action，恢复插件刷新")
        HolidayCalendar.init(context)
        WidgetWorkScheduler.schedulePeriodic(context)
        WidgetWorkScheduler.refreshNow(context)
        BalanceWidgetProvider.updateAll(context)

        val settings = AppSettings.get(context)
        if (settings.apiKey.value.isNotBlank() && settings.serviceEnabled.value) {
            if (ServiceController.isAlive(context) || isIgnoringBatteryOptimizations(context)) {
                runCatching { ServiceController.start(context) }
                    .onFailure { Log.w(TAG, "开机后拉起服务失败：${it.message}") }
            } else {
                Log.i(TAG, "未加入电池优化白名单，本次不启动前台服务，交由兜底任务刷新")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
