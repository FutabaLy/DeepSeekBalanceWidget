# DeepSeek 余额与峰谷 · 安卓桌面插件

把 **DeepSeek 账户余额** 和 **当前是高峰还是空闲（谷时）** 钉在桌面上：余额默认 **每 5 秒**刷新，
峰谷倒计时每秒跳动，法定节假日与调休补班自动识别。

两种尺寸任选，也可以同时放：

| 4×2 深色大卡片 | 2×2 浅色紧凑卡片 |
|---|---|
| 大字余额 + 顶部标题带 + 距下次切换倒计时 + 赠金/充值明细 | 封面图 ⇄ 数据面双层：余额 + 今日已用 + 峰谷胶囊 + 累计小时倒计时 |

![插件预览](docs/widget-preview.png)

**兼容性**：Android 8.0 及以上（minSdk 26），**不依赖谷歌服务**。小米 / 红米、华为 / 荣耀、
vivo / iQOO、OPPO / 一加 / realme、三星、魅族、类原生系统都能装。

> ⚠️ 唯一例外：华为 **HarmonyOS NEXT（纯血鸿蒙 5.0+）** 已不再兼容 APK，装不了 —— 那是华为主动切断的，与代码无关。

---

## 1. 下载与安装

打开 [Releases](https://github.com/FutabaLy/DeepSeekBalanceWidget/releases/latest) 下载：

- `app-debug.apk` —— 调试版，**推荐先用这个**；
- `app-release.apk` —— 体积更小的 release 包。

传到手机点击安装即可（小米/华为等会提示「未知来源应用」，允许本次安装），本机不需要装任何开发环境。

**关于升级**：所有版本都用同一把固定密钥签名，所以新版本可以**直接覆盖安装**，API Key、
今日已用、用量记录、封面图都会保留。

---

## 2. 首次配置

1. 安装后打开 App，粘贴 **DeepSeek API Key**（在 [platform.deepseek.com](https://platform.deepseek.com) → API keys 创建），点「保存并启动」；
2. 按提示完成保活设置（见第 4 节，**这步不做的话 5 秒刷新会被系统冻结**）；
3. 回到桌面 → 长按空白处 → 添加小部件 / 桌面工具，两种尺寸任选：
   - 「**DeepSeek 余额与峰谷**」—— 4×2 深色大卡片；
   - 「**DeepSeek 余额（2×2 紧凑）**」—— 2×2 浅色卡片，正面是封面图，点一下翻到数据面；
4. 想换 2×2 的封面图：App → **2×2 封面图** → 选择图片 → 在固定取景框里拖动 / 双指缩放 →（可选）勾「圆角」→ 确定。

> API Key 只保存在 App 私有目录，插件只访问 `api.deepseek.com` 的 `/user/balance` 接口，不做任何上报；
> Android 自动备份已关闭（`data_extraction_rules.xml`）。

---

## 3. 功能一览

**插件本体**

- 两种尺寸：4×2 深色大卡片、2×2 浅色紧凑卡片（共用同一份数据与刷新链路，放几个都行）
- **2×2 是双层的**：正面封面图，点一下翻到数据面；在数据面上点空白处翻回封面，点 ↻ 立即刷新
- **封面图可自定义**：内置一张抠好的透明底图；也可以导入自己的图片，导入后拖动/双指缩放裁剪，
  取景框大小固定、框内所见即所得，圆角可选；输出 512×512 PNG（保留透明通道），
  导入上限 30MB，支持 JPG / PNG / WebP / GIF(首帧) / BMP / HEIC 等常见格式
- **余额大字是循环渐变动画**：左深蓝 → 右天蓝，12 秒一轮（实现方式见第 8 节）
- 4×2 显示大字余额、距下次切换的倒计时（天级用「2天08小时53分」）、节假日/补班标记、赠金与充值明细
- 2×2 显示余额、**今日已用**、峰/谷胶囊、**累计小时倒计时**（`56:53:12`，窄卡片不会被截断）
- 统一使用打包进 APK 的 **Nunito Black** 字体（只含拉丁与数字，中文自动回落系统字体）
- ↻ 按钮立即刷新；点 4×2 的峰/谷徽章可在「自动 / 强制峰 / 强制谷」之间切换
- 常驻通知：收起时显示「谷 · ¥109.29 · 刚刚」，展开有完整时段说明

**数据与设置**

- **今日已用**：靠相邻两次刷新的余额差值累计（余额变大视为充值，只抬高基准不冲抵已用），北京时间跨天归零
- **用量统计页**：近 7 天 / 近 30 天可切换，金额汇总、峰值、可点柱状图、日期搜索、按天展开余额差额明细；
  每日合计持续保存，最近 1000 条观测明细保留；不同 Key / 接口 / 币种分开保存（Key 只存 SHA-256 哈希），切回后恢复原记录
- **法定节假日感知**：节假日全天算谷时，调休补班的周末照常有峰谷之分；内置 2025–2026 日历，可联网更新
- **保活引导**：自动识别机型品牌，显示对应的设置路径（见第 4 节）
- 息屏默认暂停请求、亮屏立刻补一次（可改成息屏也刷新）

---

## 4. 保活设置（各家系统都建议做）

这个插件靠**前台服务**维持秒级刷新。系统一旦把它冻结，就会退回 15 / 30 分钟的兜底刷新
—— 数字不会错，只是不再"实时"。各品牌要做的是同一件事（**允许自启动、允许后台、别省电**），
只是菜单路径不同：

| 品牌 | 要做的事 |
|---|---|
| 小米 / 红米（MIUI / HyperOS） | 省电策略 → 无限制；允许自启动；最近任务里给本应用加锁 |
| 华为 / 荣耀（EMUI / MagicOS） | 应用启动管理 → 本应用改为「手动管理」，自启动 / 关联启动 / 后台活动全部允许；电池 → 不受限制 |
| vivo / iQOO（OriginOS） | 后台高耗电 → 允许；自启动 → 允许；电池 → 后台耗电管理 → 允许后台运行 |
| OPPO / 一加 / realme（ColorOS） | 应用管理 → 允许自启动与关联启动；耗电管理 → 允许完全后台行为 |
| 三星（One UI） | 电池 → 后台使用限制 → 加入「永不自动休眠的应用」 |
| 魅族（Flyme） | 手机管家 → 后台管理 → 允许后台运行；允许自启动 |
| 类原生 / 其他 | 电池 → 不受限制（Unrestricted） |

> App 里的「**保持刷新**」卡片会**自动识别你的机型**并显示上面这套路径（认不出来会给通用说明），
> 旁边还有三个直达按钮：应用设置 / 电池优化 / 通知权限。

---

## 5. 峰谷规则（实现依据）

来自 DeepSeek 官方定价页 [模型 & 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing)：

> 空闲时段价格为高峰时段价格的一半。**北京时间周一至周五（不含中国法定节假日）9:00 - 12:00、14:00 - 18:00 为高峰时段；
> 其余时段，包括周末及中国法定节假日全天均为空闲时段。**

判定顺序（一律用北京时间 `Asia/Shanghai`，与手机时区无关）：

| 日子类型 | 判定 |
|---|---|
| 法定节假日（含春节/国庆等） | 全天 **谷** |
| 周六 / 周日（未调休补班） | 全天 **谷** |
| 调休补班日（周末上班） | 有峰谷之分，按工作日规则 |
| 工作日 09:00–12:00、14:00–18:00 | **峰** |
| 工作日其余时间（含 12:00–14:00 午休） | **谷** |

边界：`09:00`、`12:00`、`14:00`、`18:00` 这四个整点算在**前一个区间**内（与官方表的区间写法一致），
`12:01` 起为午间谷、`18:01` 起为谷。

节假日数据来自公开接口 `timor.tech/api/holiday/year`，内置了 2025–2026 全部特殊日期，
App 内可手动更新，服务启动时每 24 小时最多自动更新一次。

---

## 6. 关于「今日已用」和用量统计（请先读这段）

余额接口只返回**当前余额**，没有流水，所以用量是**估算**，不是官方账单：

- 做法：用**相邻两次成功刷新的余额差值**累计；余额变大视为充值，只抬高基准、不冲抵已用；北京时间跨天归零
- 金额以「微元」（1e-6 元）存 Long，不做浮点累加，避免误差积累

**已知局限**（都是原理决定的，不是 bug）：

| 情况 | 影响 |
|---|---|
| 安装之前的历史消费 | 无从得知，统计从装上之后开始 |
| App 被冻结 / 息屏暂停期间的消费 | 会漏采；下一次刷新只能看到"总差额" |
| 充值、赠金到期 | 充值会被识别为"余额变大"而不计入消耗；赠金到期会让余额下降，被算成消耗 |
| 同一时刻既有充值又有消费 | 无法区分，按余额变化方向归类 |
| 模型 / Token 明细 | 余额接口不提供，做不到 |

所以用量页里的数字请当作**趋势参考**，不要拿去对账。

---

## 7. 刷新节奏与耗电

| 内容 | 频率 | 说明 |
|---|---|---|
| 峰谷倒计时重画 | 1 秒 | **纯内存计算**，不发网络请求（可在设置里关掉省电） |
| `/user/balance` 查询 | 默认 5 秒 | 可调 5/10/15/30/60/120/300 秒；余额接口不计费、不消耗 token |
| 2×2 余额渐变动画 | 50ms（20fps） | 只在**亮屏 + 数据面 + 开启秒级跳动**时跑，用局部更新只推余额位图；**会增加耗电** |
| 息屏期间 | 暂停（默认） | 亮屏立刻补一次；想要息屏也刷可在设置里打开 |
| 失败重试 | 1 秒起步，指数退避至 60 秒 | 避免网络异常时把接口打爆 |
| 兜底刷新 | 15 / 30 分钟 | WorkManager + 系统 `updatePeriodMillis`，服务被杀也能续上 |

嫌费电的话，把「余额刷新间隔」调大、或关掉「倒计时每秒跳动」即可（关闭后渐变动画也会停）。

---

## 8. 工程结构与关键实现

```
DeepSeekBalanceWidget/
├─ .github/workflows/build-apk.yml        # 云端出 APK（跑单测 → 出包 → 打 tag 时发 Release）
├─ app/src/main/
│  ├─ AndroidManifest.xml                 # 权限、两个插件 provider、前台服务、开机广播
│  ├─ assets/holidays.json                # 内置法定节假日日历（2025–2026）
│  ├─ assets/licenses/OFL-Nunito.txt      # 打包字体的许可
│  ├─ java/com/deepseek/balancewidget/
│  │  ├─ MainActivity.kt                  # 唯一界面（Compose）+ 插件配置页
│  │  ├─ ui/CoverCropScreen.kt            # 封面裁剪界面（固定取景框、拖动/缩放、圆角开关）
│  │  ├─ ui/UsageScreen.kt                # 用量统计页（近 7/30 天、柱状图、明细）
│  │  ├─ core/
│  │  │  ├─ PeakScheduler.kt              # ★ 峰谷判定：档位、下一次切换、倒计时格式化
│  │  │  ├─ KeepAliveGuide.kt             # ★ 按机型品牌给保活路径（纯函数，可单测）
│  │  │  ├─ CoverCrop.kt                  # 封面裁剪几何（纯函数，可单测）
│  │  │  ├─ HolidayCalendar.kt            # 节假日日历（内置资产 + 联网更新 + 落盘）
│  │  │  ├─ TariffTier.kt / WidgetState.kt / WidgetStateBus.kt / DateUtil.kt
│  │  ├─ data/
│  │  │  ├─ DeepSeekApi.kt                # /user/balance 响应解析（纯函数，可单测）
│  │  │  ├─ BalanceRepository.kt          # OkHttp 请求 + 错误映射（401/402/429/网络）
│  │  │  ├─ DailyUsageStore.kt            # 今日已用 / 每日账本 / 差额明细（微元 Long、按 Key 隔离）
│  │  │  ├─ CompactCoverStore.kt          # 自定义封面：私有目录 512×512 PNG + 内存缓存
│  │  │  ├─ CoverImageLoader.kt           # 选图解码（ImageDecoder / EXIF 方向 / 降采样 / 30MB 上限）
│  │  │  ├─ CompactFaceStore.kt           # 2×2 翻面状态（按 widgetId 存）
│  │  │  ├─ AppSettings.kt / BalanceStore.kt / HolidayUpdater.kt / BalanceModels.kt
│  │  ├─ service/
│  │  │  ├─ BalanceService.kt             # ★ 前台服务：1s 走倒计时 / 5s 查余额 / 退避 / 动画帧
│  │  │  ├─ ServiceController.kt / Notifier.kt
│  │  ├─ widget/
│  │  │  ├─ BalanceWidgetProvider.kt      # 4×2 provider（updateAll 是刷新全部插件的入口）
│  │  │  ├─ CompactBalanceWidgetProvider.kt # 2×2 provider（含动画局部更新）
│  │  │  ├─ WidgetRenderer.kt             # ★ 4×2 渲染（深色卡片 + 标题带分层）
│  │  │  ├─ CompactWidgetRenderer.kt      # ★ 2×2 渲染（浅色卡片 / 封面层）
│  │  │  ├─ GradientTextRenderer.kt       # ★ 渐变文字画成位图（余额大字 + 动画相位）
│  │  │  ├─ WidgetIntents.kt / WidgetActionReceiver.kt / BootReceiver.kt
│  │  └─ worker/
│  │     ├─ BalanceRefreshWorker.kt       # 兜底刷新任务
│  │     └─ WidgetWorkScheduler.kt        # 周期 + 立即任务调度
│  ├─ res/layout/widget_balance.xml       # 4×2 布局
│  ├─ res/layout/widget_balance_compact.xml        # 2×2 数据面
│  ├─ res/layout/widget_balance_compact_cover.xml  # 2×2 封面层
│  ├─ res/font/nunito_black.ttf           # 打包字体（34KB，拉丁+数字子集）
│  ├─ res/drawable-nodpi/compact_cover.webp # 内置封面图（透明底抠图）
│  └─ res/xml/widget_balance*_info.xml    # 两个插件的元数据
├─ app/src/test/...                       # 53 个 JUnit 用例
└─ docs/
   ├─ widget-preview.png                  # 预览图
   ├─ make_preview.py / make_cover.py / make_font.py / font_candidates.py
   └─ verify_peak_logic.py                # 峰谷逻辑独立验证（全年逐分钟扫描）
```

### 几个实现上的取舍

- **余额渐变为什么不写在布局里**：RemoteViews 给 TextView 只能 `setTextColor` 上纯色，也没有逐帧动画能力。
  所以这张字是 App 里用 `LinearGradient` 画成**位图**再 `setImageViewBitmap` 传过去的
  （位图在 Binder 里走 ashmem，不占 1MB 事务限额）；动画靠独立协程每 50ms 做一次
  **`partiallyUpdateAppWidget` 局部更新**（系统侧 `mergeRemoteViews` 合并，桌面拿到的始终是完整 RemoteViews）。
- **自定义封面为什么不用 `content://` URI**：桌面进程读不到 App 私有目录，给它授权又要先知道具体
  launcher 包名、重启后授权还会失效。改成渲染时直接把位图随 RemoteViews 传过去，任何桌面、重启后都正常。
- **2×2 的倒计时为什么是 `56:53:12`**：带「天」的写法在窄卡片上会被截断；小时数直接累加既短又直观。
- **文字为什么画成位图**：部分 OEM 桌面在 inflate 插件布局时可能替换字体，画成位图可以保证字形一致
  （代价是没有省略号，超长文本会被等比缩小）。

---

## 9. 自己出包（改代码后）

### 方式 A：GitHub Actions 云端编译（无需本地环境）

```bash
git add .
git commit -m "改了什么"
git push
```

推送到 `main` 会自动跑单元测试并出包；想发版就打个 tag：

```bash
git tag v1.9.1 && git push origin v1.9.1
```

到仓库 **Actions** 页看进度，3–6 分钟后在 **Artifacts** 里下载 APK，打 tag 那次还会自动创建 Release。

> **关于签名**：工作流用仓库 secret 里的固定密钥签名（`SIGNING_KEYSTORE_BASE64` /
> `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`），所以新版能直接覆盖安装。
> 没配这 4 个 secret 时会退回 Android 默认 debug 签名 —— 而 GitHub runner 每次构建都会重新生成
> `debug.keystore`，签出来的包一次一个样，新版本装不上去，只能卸载重装。

### 方式 B：Android Studio 本地编译

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK 17 与 Android SDK）；
2. 首次启动让它下载 SDK，本项目 `compileSdk` 用到 **API 35**；
3. `File → Open` 选择项目根目录，等 Gradle 同步（已配阿里云镜像优先，国内 1–3 分钟）；
4. 连手机点 ▶，或 `Build → Build Bundle(s)/APK(s) → Build APK(s)`，
   产物在 `app/build/outputs/apk/debug/app-debug.apk`。

命令行也行（需自备 JDK 17 + Gradle 8.9+）：

```bash
gradle :app:testDebugUnitTest    # 跑单测（53 个用例）
gradle :app:assembleDebug        # 出调试包
```

---

## 10. 常见问题

**Q：插件显示 `--` 或一直「尚未同步」？**
A：① 检查 API Key 是否填对；② 打开 App 点「立即刷新」看具体报错；③ 确认已允许通知权限（前台服务需要）。

**Q：余额不刷新了，数字卡住？**
A：多半被系统冻结了，按第 4 节做保活设置；然后打开 App 一次即可恢复秒级刷新。

**Q：401 鉴权失败？**
A：Key 被删除或复制错了（注意首尾空格）。重新在开放平台创建并粘贴。

**Q：会额外扣费吗？**
A：不会。`/user/balance` 只是查询接口，不计费也不消耗 token；只有调模型才扣费。

**Q：2×2 的渐变动画不动了？**
A：动画只在**亮屏 + 停在数据面 + 开启「倒计时每秒跳动」**时跑；息屏、被冻结、或关掉秒级刷新时它会停在当前相位。

**Q：时段判定和实际扣费差一分钟？**
A：以官方服务器时间为准。手机时间不准时，可在 App 里用「强制高峰 / 强制谷时」手动覆盖显示。

**Q：为什么用量页的数字和账单对不上？**
A：见第 6 节 —— 它是按余额差额估算的，不是官方逐次调用账单。

**Q：我的手机不是小米，能用吗？**
A：能。Android 8.0+ 且不是纯血鸿蒙就行，保活路径 App 会自动识别。

---

## 11. 依赖与许可

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |
| Kotlin | 2.0.21（含 Compose 编译器插件） |
| compileSdk / targetSdk / minSdk | 35 / 35 / **26**（Android 8.0 起） |
| Jetpack Compose BOM | 2024.10.01 |
| OkHttp | 4.12.0 |
| WorkManager | 2.9.1 |

- 单元测试：**53 个 JUnit 用例** —— 峰谷判定 23、封面裁剪 6、今日已用 9、余额解析 9、保活引导 6
- 打包字体：**Nunito**（SIL Open Font License 1.1），许可见 `app/src/main/assets/licenses/OFL-Nunito.txt`
- 节假日数据：公开接口 `timor.tech`
- 预览图与字体脚本：见 `docs/`

---

## 12. 免责声明

本项目为个人自用工具，与 DeepSeek 官方无关。「高峰 / 空闲时段」的判定依据官方公开文档实现，
用量为**余额差额估算**；若官方调整计费规则或节假日安排，请以官方页面为准，并在 App 内点「更新节假日数据」。
