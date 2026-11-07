# M3 Stage 3 客户端证据（七子页业务逻辑）

采证方：LC客户端 · 设备：`L2E0222531014833`（Android 12，1228×2700，密度 540）· 时间：2026-11-07 `08:36→08:42`（Asia/Shanghai）

## 构建与指纹

| 项 | 值 |
| --- | --- |
| 代码 | `51793a9`（分支 `feature/client-m3-stage3`） |
| 构建 | `gradle assembleDebug`（JDK 17.0.21+9 / Gradle 8.7），`APP_DIR=app/android`，exit 0 |
| APK sha256 | `f3f789fb64483079e3a4124b6df689b7e6192d7720f6ce0cf5f80521bf85cfbb` |
| 设备侧校验 | `adb pull /data/app/.../base.apk` 的 sha256 与本地一致（`F3F789FB…`） |
| 桌面包 | 同一工作树 `scripts/build-desktop.ps1` → DESKTOP: PASS（docker cargo build，1100×780 截图 266376 B，mechanics 19 值 0 估计） |
| 单源/文案门禁 | `tests/copy/copy_gate.py` → COPY: PASS（钉子 `contact.txt:1` / `agreement.txt:2`）；`tests/numeric/run_all.py` → PASS |

> 说明：`51793a9` 之后另有一笔**只改分享日志文案**的提交（加入 `ACTION_SEND` / `D-026` 字样），UI 与行为不变；那行日志的真机补录见后续更新。本目录截图与日志均采自 `51793a9` 的 APK。

## 采证纪律

- 每步先看 `dumpsys activity activities | grep mResumedActivity`，前台包不是 `com.luopan.fengshui` 即中止（M3 Stage 2 出过「点到原版 App 采到假证据」）；
- 每次 dump 前 `adb shell rm -f /sdcard/a.xml`，用 `uiautomator dump` 拉回，点击取节点 `bounds` 中心；
- 日志 `adb logcat -s LCM3 / LCFAV / LCM3WEB`，不落敏感信息。

## 文件与判据对应

| 文件 | 对应判据 | 断言（日志/截图可见） |
| --- | --- | --- |
| `a-account.png` | M3-01 账户设置 | `您当前未登录` / `立即登录` / D-022 可见标注 / 默认头像 `lc_m3_avatar_img=[20,376][140,496]`；点登录追加「登录需服务端账号体系，当前未接入（D-022）」 |
| `b-complaint-submit.png` | M3-02 投诉反馈（在线态） | 空提交 → `请先填写申请原因、联系方式与问题描述`；三字段填满后 → `提交失败：服务端未接入（D-022），未发送请求` |
| `k-complaint-offline.png` | M3-02 投诉反馈（离线态） | 断网 `svc wifi/data disable` 后提交 → `提交失败：当前网络不可用（原版提交依赖服务端，D-022）` |
| `c-about.png` | M3-03 关于我们 | 两个版本节点 `三维山水全景地图` / `v1.6.1`、`测绘资质`、`APP备案编号：渝ICP备20003923号-12A`、` 《用户协议》 ` ` 《隐私政策》 `（前后空格逐字） |
| `c-agreement-web.png` | M3-03 链接载体 | 点《用户协议》→ `M3WebActivity` 标题 `用户协议`（`lc_web_title`），BACK 回 `M3PageActivity`；《隐私政策》同（标题 `隐私政策`） |
| `f-help.png` | M3-06 帮助中心 | 远端 URL 加载成功（真实渲染语雀帮助中心目录）；标题 `帮助中心`；BACK 一层回 `MainActivity` |
| `g-help-offline.png` | M3-06 失败态（D-023） | 断网 → `加载失败：net::ERR_INTERNET_DISCONNECTED` + `重试`（`lc_web_retry=[0,412][1228,479]`），不白屏 |
| `l-help-loading.png` | M3-06 加载中态 | 点开 200ms 截图可见 `加载中…` |
| `j-contact-toast.png` | M3-04 联系我们 | `邮箱地址 / account@xinzhi.space / 复制邮箱` 三行；点击 toast `复制成功`，日志 `copy mail ok text=account@xinzhi.space`（剪贴板 `ClipData.newPlainText("Label", …)`，出处 `ui/compose/c0$a$b`） |
| `e-share-panel.png` | M3-05 分享面板 | `选择要分享到的平台` / `微信` / `微信朋友圈` / `取消分享`，2 枚原版图标；取消回 `MainActivity` |
| `i-share-wx.png` | M3-05 微信渠道 | 点 `微信` → 微信 `ACTION_SEND` resolver「发送给朋友 / 添加到微信收藏」 |
| `i-share-circle.png` | M3-05 朋友圈渠道（D-026） | 点 `微信朋友圈` → **同一个**微信 resolver（降级）；两个渠道落同一面板，故两张截图逐字节相同（sha 相同，非复制错误） |
| `h-sublist-after-back.png` | P1-21-A | 收藏列表子页 `lc_fav_sublist` 打开后 `input keyevent 4` → dump 为 `GONE`，日志 `LCFAV: sublist close by back-key`，前台 `MainActivity` |
| `verify-final.log` | A–G 逐页 | 账户 / 投诉 / 关于 / 联系 / 分享 / 帮助 / 用户协议 / 离线态全量输出 |
| `verify2-final.log` | H–K | P1-21-A / 分享渠道 / 联系我们 / 投诉离线 |
| `loading-probe.log` | M3-06 加载中 | 打开后 200ms 截图 + 1s 后 dump |

## 分享正文出处（逐字）

原版 `MainViewModel.s3(Activity)`（smali）：`UMWeb("https://sj.qq.com/appdetail/space.xinzhi.globe")`
+ `setTitle(getString(R.string.app_name))` + `setDescription("我正在使用《appName》探索世界，它支持3D、超清影像、罗盘等功能，快来一起探索吧~")`
+ `setThumb(R.mipmap.ic_launcher)`。正文里的 `appName` 是原文占位符，**未替换**。

渠道：原版友盟 `SHARE_MEDIA.WEIXIN` / `WEIXIN_CIRCLE`（`F8/d$a`、`F8/d$b`）。本项目不接 SDK（D-026），
两个渠道都用 `ACTION_SEND` 到 `com.tencent.mm`，渠道标签与正文逐字保留，朋友圈分流能力丢失属**已知差异**，页面不加提示。

## 未覆盖

- 投诉反馈表单字段级 bounds / 字号未按 dump 逐项对齐（Stage 2 遗留，结构与文案已 1:1）；
- `测绘资质` 按 D-025 不跳转（日志 `measure prove page deferred by D-025`）；
- 帮助中心正文为远端第三方内容，只判「加载成功/失败可见」。
