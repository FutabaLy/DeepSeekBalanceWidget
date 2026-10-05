package com.deepseek.balancewidget.worker

import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.BalanceRepository
import com.deepseek.balancewidget.data.BalanceStore
import com.deepseek.balancewidget.data.DailyUsageStore
import com.deepseek.balancewidget.widget.BalanceWidgetProvider

/**
 * 兜底 / 立即刷新任务：查一次余额 → 落盘 → 重画插件。
 * 不依赖前台服务是否存活，因此开机、被系统清理后依然能更新桌面数字。
 */
class BalanceRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = DailyUsageStore.refreshMutex.withLock {
        val context = applicationContext
        val settings = AppSettings.get(context)
        val apiKey = settings.apiKey.value
        val baseUrl = settings.baseUrl.value
        if (apiKey.isBlank()) {
            Log.i(TAG, "未配置 API Key，跳过兜底刷新")
            return@withLock Result.success()
        }

        val repository = BalanceRepository { settings.baseUrl.value }
        repository.fetchBalance(apiKey).fold(
            onSuccess = { snapshot ->
                if (apiKey != settings.apiKey.value || baseUrl != settings.baseUrl.value) return@fold Result.success()
                BalanceStore.save(context, snapshot)
                DailyUsageStore.onBalance(context, snapshot.primary?.totalBalance,
                        DailyUsageStore.sourceId(apiKey, baseUrl), snapshot.primary?.currency ?: "CNY")
                BalanceWidgetProvider.updateAll(context)
                Log.i(TAG, "兜底刷新成功：${snapshot.totalText}")
                Result.success()
            },
            onFailure = { error ->
                Log.w(TAG, "兜底刷新失败：${error.message}")
                // 失败也重画一次，让插件至少显示最新时间与时段
                BalanceWidgetProvider.updateAll(context)
                Result.retry()
            },
        )
    }

    companion object {
        private const val TAG = "BalanceRefreshWorker"
    }
}
