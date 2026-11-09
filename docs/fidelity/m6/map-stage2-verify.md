# M6 Stage 2 · 地图侧修复与真机验证（第 2 / 6 / 8 条）

> 归属：LC地图。代码 `feature/地图-m6-fix` = `e605df3`（base develop `0879355`）。
> 基线说明：此处原记 `0f1bd56`，该对象在仓库不可解析（`git cat-file -t 0f1bd56` → `Not a valid object name`）；
> 已按 `e605df3` 的父提交回填为可解析的 `0879355`（`Merge 地图-m6-anchors`，develop 祖先）。
> 本文件其余提交号 `e605df3` / `2cb77ff` 均可解析；`7b5aa6e9` 是 **APK 字节指纹前缀、非提交号**（见 §1.1）。
> 纪律：判据写内容锚点；本文件的「文件:行」只用于定位。

## 1. 构建与装机指纹

```
commit          e605df3
构建            clean assembleDebug（JAVA_HOME=jdk-17.0.2 / gradle 8.11.1）
APK             8,593,165 B
apk.sha256      46b2ad1037b68e9e1984f5c0fb7083df2e359af215e49352d1264f8946f848c0
zip 条目        518
装机            tools/device/install-debug.ps1 → INSTALL_OK taps=2 / MARKET_RESTORED=ok
lastUpdateTime  02:10:59 → 02:13:19
device base.apk 8,593,165 B / 46b2ad10…（与构建产物逐字节一致）
```

### 1.1 上一轮「typeof resetCompass === undefined」的根因（已查清，非缓存、非加载路径）
- 我 01:55:57 装的是自己的包；**客户端 01:57:29 又装了一次**，其 `base.apk` sha 前缀 `7b5aa6e9…`（**APK 字节指纹前缀，非提交号**，`git cat-file -t` 不可解析）、`assets/map.html` 内 `resetCompass` 次数 = 0。
- 因此我 01:58–02:03 的测试全部跑在**别人的构建**上；`typeof resetCompass` 必然是 undefined，`input swipe` 四方向也全部「零位移」。
- 现流程固定为：**装机 → 核 `pm path` 的 size/sha 与本地 APK 一致 → 再测**。本轮已按此复核（见 §1）。

## 2. 第 2 条 2a（方向符号）真机实测

`debugSetView(26.6068,106.7221,<z>,0,<bearing>)` 后 `input swipe`，用 `getState()` 读中心。

| 用例 | 前 lng | 后 lng | Δlng | 手指 dx | 判据(Δlng·dx<0) |
|---|---|---|---|---|---|
| z18-right | 106.7221 | 106.72146846 | **−0.00063154** | +400 | PASS |
| z18-left | 106.7221 | 106.72272731 | **+0.00062731** | −400 | PASS |
| z12-right | 106.7221 | 106.68208807 | **−0.04001193** | +400 | PASS |
| z12-left | 106.7221 | 106.76156940 | **+0.03946940** | −400 | PASS |

| 用例 | 前 lat | 后 lat | Δlat | 手指 dy | 判据(Δlat·dy>0) |
|---|---|---|---|---|---|
| z18-up | 26.6068 | 26.60622965 | **−0.00057035** | −400 | PASS |
| z18-down | 26.6068 | 26.60735519 | **+0.00055519** | +400 | PASS |
| z12-up | 26.6068 | 26.57053447 | **−0.03626553** | −400 | PASS |
| z12-down | 26.6068 | 26.64329653 | **+0.03649653** | +400 | PASS |

**语义**：手指上滑 → 中心南移 → 内容上移（同向）；右滑 → 中心西移 → 内容右移（同向）。四方向全部与原版一致。

### 2.1 一条判据符号要订正（否则会把正确实现判成 FAIL）
纬度判据**不能**写成 `Δlat·dy < 0`：
- 手指上滑 `dy = −400`（屏幕 y 向下为正），中心南移 `Δlat = −0.00057`（纬度向北为正），二者**符号相同**；
- 所以纬度用 `Δlat·dy > 0`；经度因为「东为正、屏幕 x 右为正」同向，才是 `Δlng·dx < 0`。
- 两者不对称来自**纬度轴向与屏幕 y 轴相反**，不是实现差异。

## 3. 第 2 条 2b（bearing 逆旋转）真机实测

内容位移换算：`dmx = −Δlng·(111320·cosφ)`、`dmy = +Δlat·111320`（φ=26.6068 ⇒ cosφ=0.8939）。

| 用例 | Δlng | Δlat | dmx(m) | dmy(m) | 内容方向角 | bearing | 结论 |
|---|---|---|---|---|---|---|---|
| bearing=0, up 300px | 0 | −0.00042509 | 0 | −47.3 | 0.0° | 0 | PASS |
| bearing=30, up 300px | +0.00023921 | −0.00037045 | −23.8 | −41.2 | **30.0°** | 30 | PASS |
| bearing=90, up 300px | +0.00046412 | 0 | −46.2 | 0 | **90.0°** | 90 | PASS |

**判法**：内容位移方向角 = `atan2(|dmx|,|dmy|)`，三档分别等于 `bearing`（0/30/90°），误差 ≤0.1° ⇒ 屏幕内容与手指**平行且同向**。

### 3.1 2b 判据已改为角度判据（旧「`|dmx| ≤ 0.25·|dmy|`」描述过期）
`m6-drag-verify.ps1` 早期版本 2b 写死 `|dmx| ≤ 0.25·|dmy|`：该阈值只在 `bearing=0` 成立——`bearing=30` 时正确形态比值为 `tan30° = 0.577`（已 2.3 倍于阈值），`bearing=90` 时 `|dmy| = 0`、比值 → ∞，正确实现会被判 **FAIL**。
现脚本（`tests/e2e/m6-drag-verify.ps1` L106-L111）已改为**角度判据**：`dot > 0` 且 `|atan2(|cross|, dot)| ≤ 3°`；`bearing=0` 时自然退化为「基本竖直」。这不是放水，是把"平行"按旋转几何写对。上文「现写 0.25」的描述已过期（2026-11-09 同步）。

## 4. 第 6 条 resetCompass 真机实测

现状：3D + `bearing=45` + `center=(106.7221,26.6068)` + `zoom=12`。

```
调用前  getState → "3d|45|106.7221|12"
        js("resetCompass()")  → 返回 true
调用后  getState → "2d|0|106.7221|12|0"     （mode|bearing|lng|zoom|sitMode）
```

结论（逐项对判据）：
- **3D → 2D**：`3d → 2d`（倾斜随之消失，因为已退出 3D）；
- **bearing 归 0**：`45 → 0`（等价原版 `map.resetNorth({})`）；
- **center 不变**：`106.7221` 前后一致（原版 `I()` 无 `setCenter`，裁定不复位）；
- **zoom 不变**：`12` 前后一致（原版 `I()` 无 `setZoom`）；
- `sitMode` 清 0（否则下一帧 `setHeading` 会把 bearing 写回 heading）。

## 5. 第 8 条 3D 按钮：链路通 + 失败可读

```
setMode('3d') → LC3D patch 11/1630/865 mpp=68.3 verts=9409
              → LC3D texture ok 36/36 vec z12
              → LC3D fps=56 elev=1409 pitch=0 bearing=0 zoom=12
getState().mode = "3d"；getState().last3dError = null（成功时清空）
```

- 新增 `last3dError`：`webgl-unavailable` / `dem-missing` / `texture-fail <ok>/36` 三态，成功即置 `null`，写入 `getState()`。
- 三种失败原本只有 `console.log("LC3D ERROR …")`，而 HUD 在 `uiMode==="map"` 下不可见 ⇒ 用户只见「没反应」。现在**失败原因可由 `getState().last3dError` 读出**，原生侧亦可由 console 前缀转提示（`onConsoleMessage` 归客户端）。
- 说明：本项只做「可读 + 可判」，**未做**原生 toast（跨模块，需客户端接）。

## 6. 未做项（按纪律挂起，不写「已修复」）

| 项 | 状态 | 原因 |
|---|---|---|
| 第 3 条 当前位置符号 | **未实现** | 逆向 web 层 `userLocation/locationMarker/geolocate` 全 0 命中 ⇒ drawable 未取证，**不许自画** |
| 第 3 条 屏幕中心符号 | **未实现** | 同上；且原版「中心符号」实为十字线的 8px 红三角（属第 1 条，客户端已做） |
| 第 7 条 贴地效果 | **未实现**（非不可取证） | 原版是 `canvas` source + 四角 `coordinates` 的 `compass_ground` raster 图层；我方 `toggleGround()` 只是「pitch 60° + 机位距离压 35%」。等 A 路线 canvas 迁移（`2cb77ff` 方案）落地后再做 |

## 7. 时间戳与轮次
- 开工 `2026-11-08 01:43` / 收工 `02:23` / **返工 1 轮**（首轮测到客户端 01:57:29 覆盖后的包，`typeof resetCompass=undefined`；已改走 `install-debug.ps1` 并核 sha 后重测）。
- 代码：`app/android/app/src/main/assets/map.html`（+54/−6）。文档：本文件。
