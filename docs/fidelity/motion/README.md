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
