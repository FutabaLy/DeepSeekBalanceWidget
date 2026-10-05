package com.deepseek.balancewidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.core.PeakScheduler
import com.deepseek.balancewidget.core.WidgetStateBus
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.HolidayUpdater
import com.deepseek.balancewidget.service.BalanceService
import com.deepseek.balancewidget.service.Notifier
import com.deepseek.balancewidget.service.ServiceController
import com.deepseek.balancewidget.widget.BalanceWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 唯一的界面：配置 API Key、刷新节奏、时段判定，并提供小米机型的保活引导。
 *
 * 同时兼任插件「配置 Activity」：`appwidget-provider` 里声明了 `android:configure`，
 * 从桌面添加插件时会先打开这里，避免出现「加上了但没数据」的空白卡片。
 */
class MainActivity : ComponentActivity() {

    private var configureWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 从桌面添加插件时系统会带上 EXTRA_APPWIDGET_ID
        configureWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        HolidayCalendar.init(this)
        Notifier.ensureChannel(this)
        val settings = AppSettings.get(this)

        // 已经配过密钥了（比如加第二个插件）就直接完成配置，不打扰用户
        if (isConfiguring() && settings.apiKey.value.isNotBlank()) {
            finishConfigure(success = true)
            return
        }

        // 打开界面即拉起常驻刷新（用户在前台，允许启动前台服务）
        if (settings.apiKey.value.isNotBlank() && settings.serviceEnabled.value) {
            ServiceController.start(this)
        } else {
            // 至少让插件显示当前时段，而不是空卡片
            BalanceWidgetProvider.updateAll(this)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = BrandBlue, background = BgDeep)) {
                SettingsScreen(
                    isConfiguring = isConfiguring(),
                    onConfigured = { finishConfigure(success = it) },
                    onStartService = { ServiceController.start(this) },
                    onStopService = { ServiceController.stop(this) },
                    onRefreshNow = { BalanceService.requestRefresh(this, "界面手动刷新") },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                        }
                    },
                    onToast = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    private fun isConfiguring(): Boolean = configureWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    private fun finishConfigure(success: Boolean) {
        if (isConfiguring()) {
            setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, configureWidgetId)
            })
        }
        finish()
    }

    override fun onDestroy() {
        // 配置流程中用户直接返回：系统会把 setResult(CANCELED) 视为放弃，插件不会被添加
        if (isConfiguring() && !isFinishing) {
            setResult(Activity.RESULT_CANCELED)
        }
        super.onDestroy()
    }
}

// ------------------------------------------------------------------ 主题色
private val BrandBlue = Color(0xFF1B6BFF)
private val BgDeep = Color(0xFF0B1420)
private val CardBg = Color(0xFF131F2E)
private val CardBorder = Color(0xFF1F3046)
private val PeakColor = Color(0xFFFFB020)
private val OffPeakColor = Color(0xFF12D6A0)
private val TextDim = Color(0xFF8FA6C0)

// ------------------------------------------------------------------ 界面

@Composable
private fun SettingsScreen(
    isConfiguring: Boolean,
    onConfigured: (Boolean) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRefreshNow: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = AppSettings.get(context)
    val scope = rememberCoroutineScope()

    val apiKey by settings.apiKey.collectAsState()
    val interval by settings.intervalSeconds.collectAsState()
    val tick by settings.tickEverySecond.collectAsState()
    val override by settings.overrideMode.collectAsState()
    val serviceEnabled by settings.serviceEnabled.collectAsState()
    val state by WidgetStateBus.state.collectAsState()

    var keyInput by remember { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var busyHoliday by remember { mutableStateOf(false) }
    var busyRefresh by remember { mutableStateOf(false) }

    // 界面每秒走一次倒计时，保证看到的就是插件上的数字
    var now by remember { mutableStateOf(DateUtil.nowBeijing()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = DateUtil.nowBeijing()
            kotlinx.coroutines.delay(1000)
        }
    }

    val tier = settings.effectiveTier(now)
    val accent = if (tier.isPeak) PeakColor else OffPeakColor
    val countdown = PeakScheduler.timeUntilBoundary(now).toMillis()
    val nextTier = PeakScheduler.nextTransition(now).second

    Scaffold(containerColor = BgDeep) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0E1A28), BgDeep)),
                )
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (isConfiguring) "添加到桌面" else "DeepSeek 余额插件",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "桌面实时显示余额 + 高峰/空闲时段，默认 5 秒刷新一次",
                    color = TextDim,
                    fontSize = 12.sp,
                )

                // ---- 状态卡
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前余额", color = TextDim, fontSize = 12.sp)
                            Text(
                                text = state.balanceText,
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = tier.label,
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${tier.detail}",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "距${if (nextTier.isPeak) "高峰" else "谷时"} " +
                            PeakScheduler.formatCountdown(countdown) +
                            "（${DateUtil.formatTime(PeakScheduler.nextBoundary(now))} 切换，北京时间）",
                        color = Color(0xFFC6D6E8),
                        fontSize = 12.sp,
                    )
                    state.dayBadge()?.let {
                        Text("今天：$it", color = accent, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                busyRefresh = true
                                onRefreshNow()
                                scope.launch {
                                    kotlinx.coroutines.delay(900)
                                    busyRefresh = false
                                }
                            },
                            enabled = !busyRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        ) {
                            Text(if (busyRefresh) "刷新中…" else "立即刷新")
                        }
                        Text(
                            text = when {
                                state.lastError != null -> state.lastError!!
                                state.serviceRunning -> "常驻服务运行中 · ${interval}s 刷新"
                                else -> "常驻服务未运行（仅兜底刷新）"
                            },
                            color = if (state.lastError != null) Color(0xFFFF8A80) else TextDim,
                            fontSize = 11.sp,
                        )
                    }
                }

                // ---- API Key
                SectionCard {
                    Text("API Key", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-…", color = TextDim) },
                        singleLine = true,
                        visualTransformation = if (keyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                settings.setApiKey(keyInput)
                                settings.setServiceEnabled(true)
                                onStartService()
                                onRefreshNow()
                                onToast(if (keyInput.isBlank()) "已清空密钥" else "已保存，正在刷新余额")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        ) {
                            Text("保存并启动")
                        }
                        OutlinedButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "隐藏" else "显示")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "在 platform.deepseek.com → API keys 创建。密钥仅存于本机私有目录，" +
                            "插件只访问 api.deepseek.com 的 /user/balance 接口。",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                }

                // ---- 刷新节奏
                SectionCard {
                    Text("刷新间隔", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "余额接口不计费，可以高频查询；间隔越短越耗电。",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppSettings.INTERVAL_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = interval == preset,
                                onClick = { settings.setIntervalSeconds(preset) },
                                label = { Text("${preset}s", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandBlue,
                                    selectedLabelColor = Color.White,
                                    labelColor = TextDim,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ToggleRow(
                        title = "倒计时每秒跳动",
                        subtitle = "关闭后每分钟才重画一次，更省电",
                        checked = tick,
                        onCheckedChange = { settings.setTickEverySecond(it); BalanceWidgetProvider.updateAll(context) },
                    )
                    ToggleRow(
                        title = "息屏后也继续刷新余额",
                        subtitle = "关闭则息屏暂停、亮屏立刻补刷（默认，最省电）",
                        checked = settings.keepRefreshingInBackground,
                        onCheckedChange = { settings.setKeepRefreshingInBackground(it) },
                    )
                }

                // ---- 时段判定
                SectionCard {
                    Text("时段判定", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "官方规则：北京时间周一至周五 9:00-12:00、14:00-18:00 为高峰时段，" +
                            "其余时段（含周末和法定节假日全天）为谷时，谷时单价是峰时的 5 折。",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TierChip("自动判定", override == AppSettings.OVERRIDE_AUTO) {
                            settings.setOverrideMode(AppSettings.OVERRIDE_AUTO)
                            BalanceWidgetProvider.updateAll(context)
                        }
                        TierChip("强制高峰", override == AppSettings.OVERRIDE_PEAK) {
                            settings.setOverrideMode(AppSettings.OVERRIDE_PEAK)
                            BalanceWidgetProvider.updateAll(context)
                        }
                        TierChip("强制谷时", override == AppSettings.OVERRIDE_OFF_PEAK) {
                            settings.setOverrideMode(AppSettings.OVERRIDE_OFF_PEAK)
                            BalanceWidgetProvider.updateAll(context)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "节假日日历：已载入 ${HolidayCalendar.size} 条特殊日期" +
                            if (HolidayCalendar.updatedAt > 0) {
                                "（${DateUtil.relativeFrom(HolidayCalendar.updatedAt)}更新）"
                            } else {
                                "（内置数据）"
                            },
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            busyHoliday = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { HolidayUpdater.update(context) }
                                busyHoliday = false
                                result.fold(
                                    onSuccess = {
                                        BalanceWidgetProvider.updateAll(context)
                                        onToast("节假日数据已更新：$it 条")
                                    },
                                    onFailure = { onToast("更新失败：${it.message}") },
                                )
                            }
                        },
                        enabled = !busyHoliday,
                    ) {
                        Text(if (busyHoliday) "更新中…" else "更新节假日数据")
                    }
                }

                // ---- 保活
                SectionCard {
                    Text("保持刷新（小米/红米必做）", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    BulletText("1. 省电策略设为「无限制」")
                    BulletText("2. 允许「自启动」")
                    BulletText("3. 最近任务里给本应用「加锁」，避免被一键清理")
                    BulletText("4. 允许通知（前台服务需要常驻通知）")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        }) { Text("应用设置") }

                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        }) { Text("电池优化") }

                        OutlinedButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                onRequestNotificationPermission()
                            } else {
                                onToast("通知权限已允许")
                            }
                        }) { Text("通知权限") }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        title = "常驻后台刷新（前台服务）",
                        subtitle = if (BalanceService.isRunning) "正在运行" else "未运行",
                        checked = serviceEnabled,
                        onCheckedChange = { enabled ->
                            settings.setServiceEnabled(enabled)
                            if (enabled) {
                                onStartService()
                            } else {
                                onStopService()
                            }
                        },
                    )
                    Text(
                        text = if (BalanceService.isIgnoringBatteryOptimizations(context)) {
                            "✔ 已加入电池优化白名单"
                        } else {
                            "⚠ 尚未加入电池优化白名单，MIUI 可能在后台冻结刷新"
                        },
                        color = if (BalanceService.isIgnoringBatteryOptimizations(context)) {
                            OffPeakColor
                        } else {
                            PeakColor
                        },
                        fontSize = 11.sp,
                    )
                }

                // ---- 添加插件
                SectionCard {
                    Text("添加桌面插件", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "长按桌面空白处 → 添加小部件 / 桌面工具 → 找到「DeepSeek 余额与峰谷」" +
                            " → 拖到桌面（建议 4x2 大小）。",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "插件上的 ↻ 按钮 = 立即刷新余额；点峰/谷徽章 = 在自动/强制峰/强制谷之间切换。",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                }

                if (isConfiguring) {
                    Button(
                        onClick = {
                            settings.setApiKey(keyInput)
                            if (keyInput.isNotBlank()) {
                                settings.setServiceEnabled(true)
                                onStartService()
                            }
                            onConfigured(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    ) {
                        Text("完成，放到桌面")
                    }
                    TextButton(
                        onClick = { onConfigured(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("取消", color = TextDim)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandBlue,
            ),
        )
    }
}

@Composable
private fun TierChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandBlue,
            selectedLabelColor = Color.White,
            labelColor = TextDim,
        ),
    )
}

@Composable
private fun BulletText(text: String) {
    Text(
        text = text,
        color = TextDim,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
