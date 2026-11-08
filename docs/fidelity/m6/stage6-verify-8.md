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
| 6 | 指南针按钮=回正北 | **已验（PASS）** | 罗盘态「罗盘复位」：`bearing 172→0`、`mode 3d→2d`、`center` 与 `zoom` **一位不差**（见 §终值） | `--es cmd state`；进入 3D→坐南朝北两次造 bearing 172→点罗盘复位→再 `state` |
| 7 | 罗盘贴地 | **未实现**（规则认定，不写已修复） | 无 GPU/渲染通道证据；§13 已登记 | — |
| 8 | 右侧 3D 按钮 | **T1 已验 / T2 已验 / T3 证据不可得** | T1 重载后 `mode` 仍 `3d`、`is3D:true`；T2 `am force-stop` 冷启后 `mode:"2d"`、`is3D:false`、`bearing:0`、`zoom:5`；T3「无过渡动画」需逐帧录屏，与 A-09 动效录屏同口径记「证据不可得」 | `--es cmd reload` / `am force-stop` + `monkey … LAUNCHER 1` + `--es cmd state` |

## 终值（`8911c22`，apk_sha `1c340cfc…`，装机 11:11:14）

```
① 基线(2d)      state {"mode":"2d","bearing":0,"center":[116.4074,39.9042],"zoom":5,"sitMode":0,...}
② 点 3D         state {"mode":"3d",...,"zoom":9,...,"is3D":true}
③ 坐南朝北×2    state {"mode":"3d","bearing":172,...,"sitMode":2,...,"is3D":true}      ← 三态确认
④ 点罗盘复位    state {"mode":"2d","bearing":0,"center":[116.12029198163425,39.9042],"zoom":9,"sitMode":0,"is3D":false}
   ← 第 6 条：bearing 归 0 ✓、mode 退 2d ✓、center/zoom 与 ③ 完全一致 ✓
⑤ T1 reload 前  state {"mode":"3d","bearing":0,"center":[116.12029...,39.9042],"zoom":9,"sitMode":0,"needleOn":false}
⑥ T1 reload 后  state {"mode":"3d","bearing":180,"center":[116.4074,39.9042],"zoom":9,"sitMode":2,"needleOn":true}
   ← 3D 保持 ✓（T1 PASS），但状态被"复活"，见下节新线索
⑦ T2 冷启       state {"mode":"2d","bearing":0,"center":[116.4074,39.9042],"zoom":5,"is3D":false}   ← T2 PASS
```

## 复验中发现的新线索（不进八条，交总监裁定）

**重载会把「已被重置/已关闭」的状态重新打开——Java 镜像没有随页面动作同步。**

| 观测 | 重载前 | 重载后 | 该字段的本轮操作 |
|---|---|---|---|
| `sitMode` | 0（罗盘复位后） | **2**（`bearing` 0→**180**） | 罗盘复位只改了页面，未回写 Java `sitMode`（仍为 2），重放 `setSitNorth(2)` 把它复活 |
| `needleOn` | **false**（点「指北针」关掉） | **true** | 同一类：页面侧关闭，Java `pointerOn` 未跟着变，重放 `setNeedle(true)` 又打开 |

- 影响：用户「关掉指针 / 复位到正北」后，任何一次页面重载都会把它们变回打开/偏转，且**重载越频繁越明显**；这与 Stage 5 修的「重载丢状态」是同一根因的另一面（镜像与页面不同源）。
- 证据：上面 ⑤→⑥ 两行 `state` 输出（同一次 `reload`，`10:14:30` → `10:14:42`）。
- 判据建议：重放前先以 `getState()` 回读覆盖 Java 镜像，或让这些控件在改页面后同步写镜像；**不能只做单向重放**。

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
