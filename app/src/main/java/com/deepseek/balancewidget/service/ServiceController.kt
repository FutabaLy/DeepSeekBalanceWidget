package com.deepseek.balancewidget.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 拉起前台服务的唯一入口。
 *
 * 说明：Android 12+ 禁止 App 在后台启动前台服务，所以这里的调用点必须满足其一：
 * - 用户在界面上操作（Activity 可见）；
 * - 用户点击了插件 / 通知按钮（属于用户交互，允许）；
 * - 电池优化白名单内的应用（小米用户建议开启）。
 * 开机自动恢复走 WorkManager 兜底任务，不强启前台服务，避免被系统拦截。
 */
object ServiceController {

    /** 尝试启动常驻刷新服务。 */
    fun start(context: Context) {
        val intent = Intent(context, BalanceService::class.java).setAction(BalanceService.ACTION_START)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { android.util.Log.w("ServiceController", "启动前台服务失败：${it.message}") }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, BalanceService::class.java)) }
    }

    /** 用 ActivityManager 交叉校验服务是否真的在运行（isRunning 只是进程内标记）。 */
    fun isServiceAlive(context: Context): Boolean {
        if (BalanceService.isRunning) return true
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == BalanceService::class.java.name
        }
    }
}
