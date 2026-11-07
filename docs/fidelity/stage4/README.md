# M2 收口 Stage 4 · 客户端 P2 证据（R2-001 / R2-008 / R2-034）

采证：LC客户端 · 设备 `L2E0222531014833`（Android 12，1228×2700，密度 540）· `2026-11-07 09:33–09:35`

| 项 | 值 |
| --- | --- |
| 分支 | `feature/client-r2013-rework`（基线 `92d5f4b`；P2 主体已先合入 `da4b606`） |
| APK | `gradle assembleDebug`（JDK 17.0.21+9 / Gradle 8.7），sha256 `d7a8315691a27e3c82d225eb37ef794799f759437e1fc2e1c50ac9ae58e65da1`（9,117,212 B），`lastUpdateTime=2026-11-07 09:57` |
| 证据日志 | `verify-stage4.txt`（每步前台校验 `mResumedActivity`，dump 走 `/data/local/tmp`） |

## R2-001 启动页

- 新增 `SplashActivity` 作为 LAUNCHER（原版 `SplashActivity` 同构），1.5 s 后进 `MainActivity`；`MainActivity` 保留 `exported` 以便测试直达。
- 素材只用 APK 导出件：`assets/brand/ic_splash_bg.webp`（1500×3248）+ `ic_splash_logo.webp`（780×476，已含「3D实景」图标 + 标题 + 副标题），**页面不另拼文字**。
- 时长 **1.5 s 属 DI 决策**：原版 Splash 探针 60 s 停住、`am start -W` ANR（`lc-captures.md` §4），真实时长不可得，不冒充「±100ms」。
- 位置按原版像素对齐（阈值：RGB 和 <200 的暗像素带）：

| 元素 | 原版 `orig/_launch-probe.png` | 本实现 `r2-001-splash.png` |
| --- | --- | --- |
| 图标暗部 | y 517–597，x 510–716 | y 517–597，x 510–718 |
| 标题带 | y 731–779，x 402–824（宽 422） | y 731–779，x 402–824（宽 422） |

偏差 ≤2 px（x 右缘 +2 px 属图标抗锯齿边缘）。**顶部 `y0..121` = `(255,255,255)`**（原版同值）：上一版是主题黑底 `(0,0,0)`，改法为 `Theme.Luopan.Splash`（Light.Fullscreen + 白 window/status/nav + cutout shortEdges）+ `FLAG_LAYOUT_IN_SCREEN`，窗口真正铺到 y=0，LOGO_TOP 由 313 调到 435 保持内容带位置。

## R2-008 超清页文案

- 删除实现口径：`LC 会按…（原生最高级）…没有云端超分…` → 新文案「把地图拖到想看的位置、放大到街道级，再点「生成超清图」，即可生成当前视野的高清大图。」
- 禁用词扫描（`LC 会按|原生最高级|云端超分`）在 `strings_supermap.xml` 命中 **0**（`verify-stage4.txt` `banned-words: 0`）。
- **原版正文不可比（NC）**：该页是 VIP 墙，只留 `orig/vip-paywall-01.xml` 文本「真实3D视角的山脉走势、水系分布 待你解锁哦！ / 忍痛放弃」，PNG 受限未采；因此本条判「不再出现实现口径 + 文案自洽」，不判「与原版逐字」。

## R2-034 轨迹页空态

- 原版入口是**手机号/验证码登录墙**（`orig/tool-track.xml`：`请输入手机号`/`请输入验证码`/`获取验证码`/`登录/注册`），按 **D-006 去登录墙**登记为 DI，不做登录。
- 本实现（`TrackListActivity`）：`历史轨迹` + `轨迹设置` + `开始记录` + 空态 `暂无历史轨迹记录`（含蓝星球插画 `lc_track_empty_planet`）；设置面板：`权限设置` / `后台定位` / `忽略省电优化` / `去设置` / `后台运行权限` / `通知栏权限` / `记录参数` / `采样间隔：1s` / `最小位移：3m` / `高精度定位` / `轨迹只保存在本机，不会上传。` / `关闭`。
- 双图：`orig/tool-track.png`（原版登录墙）↔ `r2-034-track.png`、`r2-034-track-settings.png`（本实现空态与设置）。

## 文件与 sha256

见 `SHA256SUMS.txt`（5 张截图 + `verify-stage4.txt`）。


## R2-013 智能读盘 sheet 返工（A/B/C）

- **C（顶边/滚动）**：sheet 顶边 `[0,1011]`，与原版像素扫描值 **1011 一致（偏差 0，上一版 380 差 −631）**；首屏可见 10 行（第 11 行被裁），滚动后到第 14 行 `正兼向度数指标`，14 行齐。
- **B（前 3 行数值）**：`先天八卦_卦位 → 向：乾　坐：坤`、`后天八卦_卦位 → 向：离　坐：坎`、`先天八卦洛书数 → 向：乾 1　坐：坤 8`（值随 heading）；第 4–14 行保持「点击查看 ›」。
- **A（VIP 徽标）已完成**：`◆` 字符已换成原版素材 `ic_compass_vip.webp`（sha256 `5e2811b6…`，132×112，取自 APK `res/Dn.webp`），节点 `lc_read_vip` 渲染 `77×65`（FIT_CENTER，等比 76.6×65），位置在圈层名之后、`点击查看 ›` 之前、垂直居中；dump 中已无「◆」文本节点。
- 证据：`r2-013-sheet-top.png`、`r2-013-sheet-scrolled.png`、`verify-r2-013.txt`。


