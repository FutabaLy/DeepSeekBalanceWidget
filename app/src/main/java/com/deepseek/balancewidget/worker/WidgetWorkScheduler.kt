package com.deepseek.balancewidget.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 兜底刷新调度。
 *
 * 主链路是前台服务（秒级刷新）；这里提供两条与进程存活无关的保底路径：
 * - 周期任务：每 15 分钟（WorkManager 允许的最短周期）刷一次并重画插件，
 *   保证即使服务被 MIUI 杀掉、插件也不会长期停在旧数字上；
 * - 一次性任务：开机、手动点击刷新按钮时立刻执行，不受「后台不能启动前台服务」限制。
 */
object WidgetWorkScheduler {

    private const val PERIODIC_NAME = "deepseek_balance_periodic"
    private const val ONE_SHOT_NAME = "deepseek_balance_oneshot"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 开机 / 首个插件被添加时调用；幂等，重复调用不会叠加任务。 */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_NAME)
    }

    /** 立刻刷一次。expedited 让请求尽量马上执行，配额不足时自动降级为普通任务。 */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BalanceRefreshWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
