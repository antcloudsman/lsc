# D1 真机核验证据（Stage 4 / Stage 1 · 核验用，不是对照证据）

- 执行人：LC审核验　｜　执行时间：2026-11-06 17:38–17:46（首轮）+ 19:45–19:55（有效轮，v3/v4/v5）
- 机上 APK sha256：`381d163ea095cbffa4d1c719af6c080daa8fe46376476e195bb10cfae6815788`（= D2 的 `beafeb7` 构建）
- 设备：L2E0222531014833（1228×2700，density 540），单台串行
- 前置：动画三项 0、唤醒解锁、`rm -f /sdcard/a.xml` 后再 dump、heading 冻结 0°、`setView(39.9042,116.4074,12)`
- 代码基准：`develop` `bd0323b`；与 findings 基准 `3d7fcb3` 逐字节同源（`git diff --stat 3d7fcb3 bd0323b -- app/android/app/src` → **0 行**），故本次结果可直接用于两份 findings
- 收尾：前台华为桌面、动画 1/1/1、`adb forward --list` 空、App `force-stop`
- 用途：`docs/fidelity/verify-round2.md` §6 的判据。**本目录不进 `fidelity.py report` 的配对门禁**（门禁只扫 `docs/fidelity/orig/` 与 `docs/fidelity/lc/` 顶层 `*.png`，见 `tools/fidelity/fidelity.py:568`）

## 1. 命令（可复跑）

```powershell
adb -s L2E0222531014833 shell am start -n com.luopan.fengshui/.MainActivity
adb -s L2E0222531014833 shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 0
adb -s L2E0222531014833 shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "setView(39.9042,116.4074,12)"
# 每步：点按 → 等待 → dump → 截图
adb -s L2E0222531014833 shell input tap <x> <y>
adb -s L2E0222531014833 shell rm -f /sdcard/a.xml
adb -s L2E0222531014833 shell uiautomator dump /sdcard/a.xml
adb -s L2E0222531014833 pull /sdcard/a.xml <name>.xml
adb -s L2E0222531014833 shell screencap -p /sdcard/s.png
adb -s L2E0222531014833 pull /sdcard/s.png <name>.png
# 3D 状态取证（确认不是「没进 3D」）
adb -s L2E0222531014833 shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "console.log('D1STATE '+JSON.stringify(getState()))"
adb -s L2E0222531014833 shell logcat -d | Select-String 'D1STATE|LC3D'
```

## 2. 逐项结果（12 项）

| 项 | 步骤（坐标） | 观测 | 判定 | dump | 截图 sha256(前16) |
|---|---|---|---|---|---|
| client 05 | 点图 `614 1000`、`800 1600` | 每次点按新增 `立向点 向 子山 · 坐 午山` 标记（2 次=2 个，可累积），无「导航/复制经纬度/距我/名称」面板 | 成立·修正 R9（「无任何可见反馈」不成立） | `client05-map.xml` / `client05-tap.xml` | map `62B05611…` / tap `D701BD55…` |
| client 09 | 罗盘 `103 1868` → 隐藏 `1127 249` | 隐藏前有读盘/隐藏/罗盘复位；隐藏后 `全地图工具`=True、`罗盘复位`=False、无「显示」；**隐藏后截图 sha = 当次地图基线** | 成立 | `client09-compass.xml` / `client09-hidden.xml` | `63B81E2B…` / `68469C5C…`（首轮）；v4 轮 baseline=hidden=`AC06F0B5…` |
| client 10 | 海拔查询 `103 1674` → 页内点图 `614 1350` | `mResumedActivity=…/.ElevationActivity`；无「打点」；点图后出现「纬度/海拔」行 | 成立（R3 限定被证实） | `client10-elev.xml` / `client10-elevtap.xml` | `4A14B658…` / `7776E111…` |
| client 17 | 更多 `103 2453` → 收藏点 `232 1453` | 面板有「地图工具/收藏点」；点击后 `mResumedActivity=…/.InterestListActivity`、「暂无收藏点」、无「添加点」 | 成立 | `client17-more.xml` / `client17-favorite.xml` | `B77834FA…` / `057C5CDF…` |
| map C-01 | 搜索 `1127 249` | dump 命中 `搜索：盘面 / 圈层 / 知识条目` | 成立 | `mapC01-search.xml` | `F0A246D1…` |
| map C-03 | 图层 `1124 683` | 命中 `底图源` / `中科星图` / `需授权`（竖列表 12 源） | 成立 | `mapC03-layer.xml` | `AC5FDCB0…` |
| map C-05 | 叠加 `1124 878` | `数据叠加`=True、`行政区域（面）`=True、`行政区域筛选`=False；`…/.OverlayActivity` | 成立 | `mapC05-overlay.xml` | `A80CAA17…` |
| map C-06 | 山脉 `1124 1072` | `mResumedActivity=…/.MountainActivity`、有「看山脉」 | 成立 | `mapC06-mountain.xml` | `508CDE93…` |
| map C-07 | 探索 `1124 1462` | `mResumedActivity=…/.DiscoveryActivity`、有「专题地图/探索」 | 成立 | `mapC07-explore.xml` | `BDFA8539…` |
| map C-04 | 3D `1124 2430` | `LC3D mode=3d zoom=12 pitch=45` / `texture ok 36/36 vec z12` / `fps=53…60`；`D1STATE mode=3d,drape=vec,demZoom=11,hasTexture=true`；3D 图 `F037F735…` ≠ 2D `68469C5C…` | **部分驳回**（3D 可用成立；「实为 2D」不成立） | `mapC04-3d.xml`（sha `3D14B3C2…`，与旧 `map-3d-mode.xml` 相同） | 2D `68469C5C…` / 3D `F037F735…` |
| map C-12 | 3D 下等高线开 `103 2258` / 再关 | 三张截图 sha 全等 `F037F735…`；`logcat LC3D contour on=true uniform=1` | NC 成立·修正 R8 | 无 dump（截图为判据） | `F037F735…` ×3 |
| map C-13 | 更多面板全项 | dump 无「相机」 | 成立 | `mapC13-more.xml`（= `client17-more.xml`，`EC23C862…`） | `B77834FA…` |

## 3. 关键交叉校验（判词依据）

1. **C-04**：本次 3D 态 dump sha `3D14B3C2…` 与旧 `lc/map-3d-mode.xml` 相同、截图 sha `F037F735…` 与旧 `lc/map-3d-mode.png` 相同 → 旧证据与我今天在 `mode=3d/drape=vec` 下拍的**是同一状态**；「实为 2D」不成立（详见 `verify-round2.md` §3.9）。
2. **C-12**：3D 下开/关等高线三张截图全等，而 `logcat` 确认 `contour on=true uniform=1` → 同图原因是固定态北京地形平坦（`elev=53`）、无可绘等值线，不是「不在 3D」。
3. **client 09**：隐藏后截图 sha 回到 2D 固定态 `68469C5C…` → 「隐藏＝退出罗盘态」在像素级成立。
4. **client 05**：点图不是「无反馈」——v5 实测每次点按新增 `立向点 向 子山 · 坐 午山` 标记（2 次=2 个，会累积），但始终无 POI 面板（详见 `verify-round2.md` R9）。

## 3.1 复现性告警（同态 ≠ 同 sha）

- 同一 UI 状态在 17:45 与 19:49 两次采集：节点树**完全相同**（38 节点、零差异），但 PNG sha 不同（`68469C5C…` → `AC06F0B5…`）——地图像素受瓦片加载/渲染时序影响。
- 会让截图变化的两个已知副作用：① 点图累积「立向点」标记；② D2「加钓点」写本机 DB（`LC客户端` 已登记）。
- 所以「同一状态」只能靠节点树 + 状态断言（`D1STATE`/前台 Activity）保证，不能靠 sha 相等；复跑同一态前先重启 App。
- `fidelity.py report` 的重复图判定按 sha，不会误报，但也**发现不了「同态不同 sha」的重复采集**——建议在工具 README 注明，别把 sha 不同当作「两个不同态」。


## 4. 截图未入库说明

- 12 项截图（共约 30 MB）留在 `E:\APPDetector\reports\stage4-verify\d1\`，不提交二进制；上表已记 sha256，可随时按 §1 重拍比对。
- 两张判定图在仓库里已有等价物：2D 固定态 `68469C5C…`（`lc/map-beijing-z12.png` 同源）、3D 态 `F037F735…`（`lc/map-3d-mode.png`）。若总监要求在仓库内留 PNG，我在下一棒设备窗口补入 `docs/fidelity/verify-d1/*.png`（门禁不扫子目录）。

## 5. 过程坑（会影响任何真机核验）

首轮跑到 C-05 时被**联想应用市场**弹层劫持：dump 依次变成应用商店首页 →「探索」tab → `王者荣耀` 详情页，导致 C-05/C-06/C-07/C-04 首轮全部读错，其中 C-07 因商店页面也有「探索」二字而**假 PASS**。
处置：`am force-stop com.lenovo.leos.appstore` + 每步先断言 `mResumedActivity` 属于 `com.luopan.fengshui`，否则重拉 App 重试；四步复跑全过。**采集脚本与复现清单应内置这条守卫**（`LC地图` 已在 `collect_map_lc.ps1` 加 `Assert-LcForeground`）。
