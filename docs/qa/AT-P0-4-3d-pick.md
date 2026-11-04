# AT-P0-4 — 3D 点读（地图）

> 归属：LC地图。被测对象 `app\android\app\src\main\assets\map.html`。
> 无头执行器：`tools\map\run_at_p04.ps1`（TD-001…TD-014）；TD-015 必须真机。
> 契约出处：`docs/map-bridge.md` §5.1（D-005/D-006）。

## 运行

```powershell
pwsh -File tools\map\run_at_p04.ps1
# 期望末行：LCAT-RESULT PASS total=14 fails=0
```

原理：本地 http 服务 + Chrome headless（SwiftShader WebGL）打开 `map.html#at&lat=40.02&lng=115.45&z=12`，页面在 DEM/贴面就绪后跑 14 条断言，输出 `LCAT …` 与 `LCAT-RESULT`。

## 无头用例（脚本自动跑）

| ID | 目的 | 期望 |
|---|---|---|
| TD-001 | WebGL 可用 | `webgl ok`，无 shader/link 错误 |
| TD-002 | DEM 地形块构建 | `patch` 有 9409 顶点 / 55296 索引 |
| TD-003 | 天地图贴面加载 | 36/36 瓦片，`patch.tex` 非空 |
| TD-004 | 点选事件类型 | `{type:"pick", src:"3d"}` |
| TD-005 | 经纬度范围 | `lng∈[-180,180]`、`lat∈[-90,90]` 且有限 |
| TD-006 | 海拔字段 | 数字或 `null`（缺失不得用 0 当哨兵；本视点实测 1645.9 m） |
| TD-007 | `heading` 字段 | 有限且 `[0,360)` |
| TD-008 | 航向透传 | `setHeading(90)` 后点选 → `heading=90`（±0.11 容差） |
| TD-009 | 单点覆盖 | 连点两次后 DOM 中 `.pick3d` 恰为 1 个 |
| TD-010 | 契约整体 | `__LCBridgeTest.assertContract()` 全过 |
| TD-011 | `pick-clear` | `clearMarkers()` 发 `{"type":"pick-clear"}` |
| TD-012 | 天空不误触 | 屏幕顶部点选 → 无 `pick` 事件（射线未击中地形） |
| TD-013 | 2D 清空也发事件 | 切 2D 后 `clearMarkers()` 仍发 `pick-clear` |
| TD-014 | 无桥不崩 | `LCAndroid` 不存在时点选不抛异常且不残留标记 |

## 真机用例（必须手动）

| ID | 目的 | 步骤 | 期望 |
|---|---|---|---|
| TD-015 | 帧率与手感 | 装 develop 的 APK → `#in3d` → 连续点选 5 次，`logcat -s hwschromium` 看 `LC3D fps` | FPS ≥ 30；每次点选只 1 条 `LC3D pick`；标记落点贴合地形、无偏移；`FATAL EXCEPTION` 0 |

## 边界

- 无头环境无触摸事件，TD-015 的触控与真实 GPU 帧率不在无头覆盖范围。
- 点选依赖 DEM 数据；断网时 TD-002/003 会红，属环境失败不是回归。
- 本 AT 只覆盖 3D 点选，不含罗盘读数（归 P0-1/B2）与桌面端。
