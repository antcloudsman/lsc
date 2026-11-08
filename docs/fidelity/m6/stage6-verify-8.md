# M6 Stage 6：八条缺陷独立复验（LC审核验）

- 复验对象：develop `a09e076`（Stage 5 五个表现面的修复并入后）
- 构建指纹：`scripts\build-android.ps1 -SkipDevice` → `apk_sha256=78d56885397694b85b1749ce7951ecd7993485909db0353be997d494050382f7`，`bytes=35028824`，装机 `lastUpdateTime=2026-11-08 10:53:01`，设备 `L2E0222531014833`
- 证据目录：`E:\APPDetector\reports\M6-final-verify\`、`M6-drag-symbol\`、`M6-help-figs\`、`M6-help-014e296\`
- 口径：只有「已验 / 未实现 / 未取证 / 待验」四档；不含“应该没问题”

## 判词表

| # | 缺陷 | 判词 | 证据 | 复现命令 |
|---|---|---|---|---|
| 1 | 罗盘红色十字经线 | **已验（含量化）** | 竖线 x 612..616（中心 614=W/2）、y 0..2414；横线 y 1347..1351（中心 1350=H/2）、x 0..1226；交点 (614,1350)；线色 RGB(255,0,0)；红三角 y 739..742 = 屏高 27.4% | 罗盘态截图后：红像素判据 `R>150 && G<90 && B<90`；逐列计数找竖线（峰值列 612/614/616 各 1208 采样点）、逐行计数找横线（峰值行 1347..1351 各 271 采样点）；`GetPixel(614,1350)` 与中段取样 (`#FF0000`) |
| 2 | 地图拖动方向 | **已验（同向）** | 手指 `swipe 400 1500 → 700 1500`（dx=+300）；地标「北京★」设备 x 614 → 903（dmx=+289）；同号=正确 | `adb shell input swipe 400 1500 700 1500 400`；前后各 `exec-out screencap -p`；按渲染宽 918→设备 1228 换算地标 x |
| 3 | 当前位置/屏幕中心两个符号 | **未实现/未取证** | 原版 `userLocation`/`locationMarker`/`geolocate` 全 0 命中（产品关账摘要）；按总监口径不在设备上验 | — |
| 4 | 四页 1:1 排版 | **已验** | §47：三页标题 `[506,154][722,233]`、返回 `[41,115]`、投诉 352/532、账户 339/502/665/828/991、行高 79、步进 163、我的墨迹中心 1133 vs 1132.5（Δ0.5） | 见 §47：`uiautomator dump` + 像素墨迹扫描（阈值 luma<200） |
| 5 | 帮助中心带图文档 | **已验** | 可达（`M3WebActivity` 渲染 13 节目录+正文）；飞行模式冷启动仍可读；29 图 lint `fail=0`；sha 门禁 `exit=0` | `adb shell cmd connectivity airplane-mode enable` → 冷启 → 重进；`python tools/fidelity/evidence-lint.py --root app/android/app/src/main/assets/lc-help --expect-size 1228x2700` |
| 6 | 指南针按钮=回正北 | **待验（等 `state` 命令）** | 需 non-zero bearing → 点按钮 → `bearing=0`、`mode=2d`、`center`/`zoom` 不变 | `am broadcast -a com.luopan.fengshui.DEBUG --es cmd state`（未合入） |
| 7 | 罗盘贴地 | **未实现**（规则认定，不写已修复） | 无 GPU/渲染通道证据；§13 已登记 | — |
| 8 | 右侧 3D 按钮 | **部分验**（T1 间接覆盖；T2/T3 待验） | 开等高线/贴地后 reload 截图 sha 相同（3D 保持）；冷启回 2D、无过渡动画未测 | `am broadcast … --es cmd reload` + `am broadcast … --es cmd state`（未合入） |

## 五个表现面的重载保持（Stage 5 修复复验）

| 表现面 | 结果 | logcat 时间戳 | 证据 |
|---|---|---|---|
| 实景 | 保持（开关仍开、底图仍影像、角注「天地图卫星」） | `10:54:13.734 cmd=reload` / `.735 reload requested` | `s2-real.png` / `s3-real-after-reload.png` |
| 指针（关） | 保持（仍关、指针不画） | `10:54:53.731 reload requested` | `p-off.png` / `p-off-after-reload.png` |
| 等高线（开） | 保持（重载前后截图 sha 相同） | `10:57:51.122 reload requested` | `c3-on.png` / `c4-on-after-reload.png` |
| 贴地（开） | 保持（重载前后截图 sha 相同） | `10:58:47.363 reload requested` | `g-on.png` / `g-on-after-reload.png` |
| 坐南朝北（两次点击） | 跨 reload 截图 sha 相同；**三态语义待 `state` 判 `sitMode=2`** | `10:59:23.554 reload requested` | `s-sit2.png` / `s-sit2-after-reload.png` |

## 方法更正（本轮自记）

自动「横向亮度剖面互相关」给出的位移符号与视觉矛盾（左右拖都得 `best_dx≈−290`），判为脚本索引方向写反，**不采信**；改用截图地标位移后与视觉一致。**脚本与可复核视觉冲突时信视觉坐标**，该脚本不作为证据引用。

## 未覆盖

- 第 6、8 条与坐南朝北三态：`state` 调试命令尚未合入 develop（实测 `"state"` 命中 0），未跑。
- 设备本轮中途掉连两次（`adb: device not found`），每次均为单条中断、其余照跑，未整轮作废。
