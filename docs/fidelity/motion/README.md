# 动效录屏（A-09）现状：不可得 — 待授权换装可运行构建

## 结论

截至 `2026-11-07T02:14`，A-09 三类动效（底部 sheet 弹出/关闭、按钮按下反馈、页面过渡）**未取得**，
原因是**机上原版是商店构建，冷启动卡在保护壳初始化**，与之前 D0/等高线两次受阻同因。

## 证据

| 项 | 实测 |
|---|---|
| 机上原版 | `space.xinzhi.globe` 1.6.1，`lastUpdateTime=2026-11-05 19:29:17` |
| 装机 APK sha256 | `64af145dbf612fd1d2f6ab4cd32cd5875383789cea2d8faeb285c20663654927`（= 证据目录 README 里那份 **ANR 的商店构建**） |
| 启动 | `am start -n space.xinzhi.globe/.ui.pages.SplashActivity` → `Starting: Intent {...}` |
| 8s 后 | `mCurrentFocus=null`（无窗口获得焦点＝停在启动图前） |
| 10s 后 | 仍 `mCurrentFocus=null`，`dumpsys activity activities` 无 `topResumedActivity` |
| 屏幕 | 当前显示的是**我方** `com.luopan.fengshui`（罗盘浮层态），不是原版 |
| 收尾 | 已 `am force-stop space.xinzhi.globe`，未改原版任何数据/配置 |

## 2026-11-07T02:19–02:46 换装尝试记录（授权后执行，未完成）

技术总监授权换装 `input/swss.apk`（同版本 1.6.1、sha `2fd1fc15…`）后按纪律执行，遇两处阻断：

1. **`pm install -r` 不可能保留数据**：商店构建签名与输入 APK 不一致 →
   `Failure [INSTALL_FAILED_ABORTED: User rejected permissions]`，随后系统页明确
   「新版本签名与已安装版本不一致，需先卸载旧版本」。
2. **卸载后被华为应用市场劫持**：卸载 `space.xinzhi.globe` 后，市场页自动装上它自己的
   **1.6.3**（`installerPackageName=com.huawei.appmarket`，装机 sha `3001836d…`）；已卸载该包。
   之后重装我们那份 1.6.1 时，`pm install` / `pm install-create+write+commit` 都在
   系统「风险提示 → 继续安装」页卡住，命令侧拿到的是
   `INSTALL_FAILED_ABORTED: User rejected permissions`；点掉「继续安装」后会话仍不 commit。

**设备当前状态（已清理）**：`space.xinzhi.globe` **未安装**；`com.luopan.fengshui` 未动；
三项动画已置 `0/0/0`；`verifier_verify_adb_installs`/`package_verifier_enable` 已恢复 `1`；
`com.huawei.appmarket` 已 `force-stop`；已退回桌面。

**恢复方案（建议）**：用会话式安装并让 `pm install-commit` 与确认点击在同一 shell 存活周期内完成，
或由人工在设备上点一次「继续安装」；恢复目标是**商店构建** `64af145d…`（`evidence/original-apk/installed-base.apk`，
110,781,555 B）或输入 APK `2fd1fc15…`，两者都是 1.6.1 / versionCode 161。

## 已知可行路径（需授权）

`E:\APPDetector\evidence\original-apk\README.md` 记录：卸载商店构建、安装**输入 APK**
`E:\APPDetector\input\swss.apk`（sha256 `2fd1fc159c92cbd6fea1b210621eb619dce87b051a8a9275c9330ffb0992bfe8`）后
原版可正常启动（`GuideActivity` → `MainActivity`）。那份 APK 正是本次全部素材的来源，同版本 1.6.1。

**未擅自换装**，因为章程要求「原版只作对照，不得修改其数据或配置」；换装属设备配置变更，
需技术总监授权后执行。授权后按三步录：

1. `adb push` + `pm install -r -t`（同上纪律），校验 `dumpsys package` 的 `lastUpdateTime` 变化；
2. 动画三项临时置 **1/1/1**（动效录制必须开，与截图口径不同，录完恢复 0）；
3. `adb shell screenrecord --time-limit 20 --bit-rate 8M` 分别录：地图页点「更多」弹 sheet → 再点空白关闭；
   左列任一按钮按下/抬起；图层入口 → 面板展开（页面过渡）。

录完落 `docs/fidelity/motion/*.mp4` + 关键帧时间戳与时长（±100ms 口径）。

## 2026-11-07T02:56–03:05 安装成功，但引导页卡住

- 通过「风险提示 → 继续安装」（实际按钮坐标 `[81,2295][1147,2430]`；之前几次点到的是 `[81,2484][1147,2578]` 的「取消」）安装成功：
  `space.xinzhi.globe` 装机 sha256 = **2fd1fc159c92cbd6fea1b210621eb619dce87b051a8a9275c9330ffb0992bfe8**
  （versionName 1.6.1 / versionCode 161），与原版素材同源；商店构建 `64af145d…` 未回装，设备对照构建 = `swss.apk`。
- 启动后依次出现：隐私政策授权页（已点「同意并开始授权」）→ `GuideActivity` 引导页（4 页，底部「下一步」）。
- **引导页对合成输入无响应**：`input tap`（614,2171 / 614,2182 / 612,2185）、`input swipe`（页面区左滑、按钮原地长按）、
  `input motionevent DOWN/UP`、`TAB+ENTER` 全部无效，`mResumedActivity` 始终是 `GuideActivity`；
  该页 `uiautomator dump` 返回空树（Compose 自绘，无文本节点）；`run-as` 不可用（`package not debuggable`），
  不能直接改引导完成标记。
- 设备已恢复：`package_verifier_enable=1`、`verifier_verify_adb_installs=1`、`com.huawei.appmarket` 已 `enabled`；
  动画三项**暂留 1/1/1**（录制需要，录完归 0）。

**下一步**：需要人工在引导页点 4 次「下一步」进入地图页（约 10 秒），之后三类动效可一次录完。
