# AT-P0-4 · 3D 点读（海拔 / 坡度 / 坡向 / 垂直夸张 / 立向标记）验收用例

- 作者：LC产品｜执行：LC测试｜日期：2026-11-05
- 关联：`docs/product/PRD-v0.1.md`（v0.3）§3 P0-4、§0.1 T-2（3D 路径 A：在线 DEM + 天地图贴图，不自建瓦片服务）
- 状态：**未实现 → 当前 BLOCKED**；实现落地后按本文件直接执行
- **两段交付（PRD T-8）**：TD-001…TD-015 只要 JS 侧（`probe/pick/M2`）到位就能跑；**TD-016…TD-020 依赖 `LCAndroid` 桥与 `MapMarker`（轮 C 收口后由 LC客户端 接）**，桥未接前判 BLOCKED，不要拿「JS 自己打标记」当通过。
- 判定：`PASS` / `FAIL` / `BLOCKED` / `N/A`
- 证据目录：`docs/qa/runs/<yyyyMMdd-HHmm>-P0-4/`（logcat.txt、shots/、notes.md）

---

## 0. 实现侧前置（缺任何一条 → BLOCKED）

| # | 要求 | 提供方 | 说明 |
|---|---|---|---|
| M1 | `window.__LC3D` 增：`probe(lat,lng)` → `{elev, slope, aspect, demZoom}`（纯 DEM 采样，不依赖渲染；**必须回报 demZoom**，否则数值不可复现）；`pick(px,py)` → `{lat,lng,elev,slope,aspect,demZoom}`；缺块返回 `null`；`state()` 增 `vexag`、`demZoom` | LC地图 | 现有 `window.__LC3D`/`debugSetView` 已有，扩展即可。**允许降级实现**：射线沿 `demAt` 网格步进 + 二分，而非精确三角面求交；TD-007 仍硬断言，TD-008 放宽到 2 网格 |
| M2 | **统一 DebugBridge**（与 P0-0 K5、P0-1 S2 同一个 receiver）：`--es cmd js --es value <js>`。**B-02 定案：契约只认 `value`**——基线从未读取 `b64`/`target`（`MainActivity.java` 的 `js` 分支只 `getStringExtra("value")`），旧写法 `--es b64 ... --es target ...` 是 no-op，已从本文件删除；注入同时求值到两个 WebView（`compassMapView` 与 `mapView`），不区分 target。`onConsoleMessage` 两个 WebView 都要挂 → logcat tag **`LCJS`**（基线未覆写 `onConsoleMessage`，因此示例改用 `evaluateJavascript` 的 ValueCallback 输出，见 §1）；结构化行：`LCPROBE ...`、`LCPICK ...`、`LCVEXAG x=..`、`LC3D marker n=..`、`LC3D NODATA lat=.. lng=..` | LC客户端 + LC地图 | **限制（非设计如此）**：`js` 同时打到两个 WebView 实例，无法按实例定向；TD-010/014 只能以**当前可见 Tab 的截图**判定，另一实例上残留的 marker 无法从日志区分。要定向注入需另开条目给 bridge 增加 `target` 支持 |
| M3 | 同上 M2（合并，不再单列） | — | — |
| M4 | **坡度/坡向用真实高程计算**，`VEXAG`（垂直放大）只影响渲染；HUD 文案含「垂直放大 2.5×」 | LC地图 | 否则 2.5× 会把坡度算成 2.5 倍虚高 |
| M5 | 缺块（DEM 404/断网）显示「无数据」，**不得**返回 0 m；连带 `mesh/demoElev/HUD` 容错（约 30–50 行） | LC地图 | 现 `demAt()` 缺块返回 0，会造「假海平面」 |
| M6 | **T-8 点选契约**（PRD §0.1）：JS 通过 `LCAndroid.onEvent(String json)` 发射 `{"type":"pick","lng":…,"lat":…,"elev":…,"heading":…,"src":"3d"}`，**缺高程传 `null`**；`{"type":"pick-clear"}` 清标记；客户端 `MapMarker` **单点覆盖**；**点选不联动罗盘读数**；3D 默认贴面为**影像**（`state().base === 'img'`），`ter` 留开关 | LC地图（JS 侧）+ LC客户端（桥，轮 C 后） | 桥未接前 TD-016…TD-019 判 BLOCKED；JS 侧用例可先跑 |

> 基线对照物：`docs/qa/baseline-android/`（含 `probe.csv` 与当前结构的截图；K-01 落地后须按「地图态 / 罗盘浮层态 / 面板态」重采）。TD-015/回归要用它。

---

## 1. 环境与命令

```powershell
$DEV = 'L2E0222531014833'
$PKG = 'com.luopan.fengshui'

pwsh -File scripts\build-android.ps1 -DeviceId $DEV -OutDir checks\at-p0-4
adb -s $DEV logcat -c

# 执行任意 JS（B-02 定案：契约只认 value；同一段 JS 会同时求值到两个 WebView）
# 引号：整条 am 命令用双引号包住，JS 内的单引号先转成 '\''（POSIX 单引号转义）再交给设备 shell
function Invoke-LCJs([string]$Js) {
  $payload = $Js.Replace("'", "'\''")
  adb -s $DEV shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$payload'" | Out-Null
  Start-Sleep -Milliseconds 400
}

# 例：进 3D、固定视角（无返回值）；读状态用「表达式返回值」走 ValueCallback → logcat tag LCJS
Invoke-LCJs "__LC3D.setMode('3d'); __LC3D.setView(39.9042,116.4074,12,45,0);"
Invoke-LCJs "JSON.stringify(__LC3D.state())"

# 读日志（只取本功能相关行）
adb -s $DEV logcat -d -s LCJS | Select-String 'LCPROBE|LCPICK|LCVEXAG|LC3D marker|LC3D NODATA|LCSTATE'
```

> 截图用设备内截 + pull（`exec-out` 重定向会把 PNG 写成文本）：
> `adb -s $DEV shell screencap -p /sdcard/shot.png; adb -s $DEV pull /sdcard/shot.png .\shot.png`
> 执行器（LC测试 已落）：`pwsh -File tests\at\run-at.ps1 -Suite P0-4`，缺前置 exit 2。

**取画布中心像素**（用于 `pick`）：先 `Invoke-LCJs "__LC3D.setView(...)"`，再读 `window.innerWidth/innerHeight`：

```powershell
Invoke-LCJs "window.innerWidth+'x'+window.innerHeight"
```

---

## 2. 用例

| ID | 层级 | 步骤 | 预期 | 证据 |
|---|---|---|---|---|
| TD-001 | 数值·华北平原 | `probe(39.9042,116.4074)` | 30–90 m（北京一带平原，±50 m 内属正常） | LCPROBE 日志 |
| TD-002 | 数值·高原 | `probe(29.6520,91.1721)`（拉萨） | 3500–3800 m | 日志 |
| TD-003 | **数值·不得乘放大** | `probe(27.9881,86.9250)`（珠峰） | **8700–9000 m**；若出现 ~21000–23000 m 判 **FAIL**（说明坡度/海拔乘了 VEXAG） | 日志 |
| TD-004 | 坡度 | `probe(36.50,116.00)`（平原）；`probe(27.98,86.93)`（高山） | 平原 slope <2°；高山 slope >15° | 日志 |
| TD-005 | 坡向（oracle 对拍） | 1) 用同一批 Terrarium 瓦片在 Python 里做 Sobel，独立算 aspect；2) 选 slope ≥8° 的点；3) `probe` 同点 | 当 slope ≥8° 时 `abs(probe.aspect − oracle.aspect) ≤15°` → 再按八向扇区判；slope <8° 或两法差 >45° → **N/A + finding**（数据分辨率不足）。珠峰两点可能压在山脊/沟谷，坐标由 LC测试 用 oracle 挑清楚坡面后回填 | 双份输出 + oracle 脚本 |
| TD-006 | 缺块 | `probe(89.9,0.0)`（超出瓦片范围） | 返回 `null` + `LC3D NODATA`；**不得**返回 `elev=0` | 日志 |
| TD-007 | 命中·默认视角 | `setView(39.9042,116.4074,12,45,0)` → `pick(中心)` | 返回经纬度与设定中心差 ≤1 个网格步长（z12 时 ≈234 m）；`pick.elev == probe.elev`（严格相等）；`pick.demZoom == probe.demZoom` | LCPICK + LCPROBE |
| TD-008 | 命中·俯仰边界 | 同上，pitch 分别 0° 与 60°，各 pick 中心 | 两次都 ≤**2 个网格**（放宽项，超 1 网格记 finding）；不出现「点天空返回地面点」的明显错位 | 两组日志 + finding |
| TD-009 | 连续点选 | 垂直方向连点 10 个不同位置 | 10 条 `LCPICK` 且经纬度互不相同；`LC3D marker n=10` | 日志 |
| TD-010 | 清除标记 | `target=both` 下 `clearMarkers()`（或点「清除标记」） | 两个 WebView 的 `LC3D marker n=0`；画面标记消失 | 日志（两条）+ 截图 |
| TD-011 | 倍率透明 | 读 `state().vexag` 与 HUD 文案；再切 1.0×，重读同点 | 默认 `2.5` 且 HUD 含「垂直放大 2.5×」；切 1.0 后 `LCVEXAG x=1`，**同点 elev 不变**（差 ≤0.01 m） | 日志 + 截图 |
| TD-012 | 断网降级 | 飞行模式 → 进 3D | ≤5 s 内回落 2D 并提示；`LC3D ERROR dem-load` 出现；不无限「加载中」 | 日志 + 截图 |
| TD-013 | 性能 | 默认视角连续 10 s 采样 `state().fps`；再操作 5 min | ≥30 fps（允许 1 次瞬时低于 30）；无 FATAL EXCEPTION | 采样表 + logcat |
| TD-014 | 全关 | 关闭 P0-4 开关后重复 TD-007，`target=both` 各查一次 | 两个 WebView 都无 `LCPICK`、无 marker、HUD 无倍率；3D 基本渲染与当前版本一致 | 日志 + 截图 |
| TD-015 | 不碰基线 | 关掉 P0-4，回罗盘态（**LC 现状为罗盘 Tab；K-01 后为同页浮层**）dump `reading_text`；`git diff app/mechanics.json`；`python tests/numeric/run_all.py` | 与 `docs/qa/baseline-android/` 基线逐字一致；19 项 0 改动；全绿 | ui.xml + diff + 测试输出 |
| TD-016 | **onEvent 契约** | 点选一个正常点，抓 `LCAndroid.onEvent` 的 JSON（JS 侧 `console.log` 同份 payload） | 字段齐全 `type=pick`、`lng/lat/elev/heading/src=3d`；`elev` 与同点 `probe.elev` 一致；类型为数字不是字符串 | 日志 JSON |
| TD-017 | 缺高程传 null | 点一个 DEM 缺块位置（或断网后点选） | JSON 中 `elev: null`（**不是 0**）；界面显示「无数据」 | 日志 JSON + 截图 |
| TD-018 | 单点覆盖 | 连点 3 个不同位置 | 标记始终只有 1 个（`n=1`），payload 为最后一点；无重叠堆积 | 日志 + 截图 |
| TD-019 | 不联动罗盘 | 记录点选前 `reading_text`；点选 3 次后再 dump | `reading_text` 完全不变（点选不改罗盘航向）；罗盘仍跟传感器 | 两份 ui.xml |
| TD-020 | 默认贴面 | 首次进 3D，读 `state().base`；再切 `ter` | 默认 `img`（影像）；`ter` 可切回地形；HUD 同步 | 日志 + 截图 |

---

## 3. 判定规则

- **PASS**：TD-001…TD-004、TD-006…TD-015 全过；TD-005 按 oracle 门限判，超出记 finding。
- **FAIL**（必须判，不许放过）：
  1. TD-003 出现 VEXAG 污染（21000 m 级）——这是「看山势被放大误导」的根因；
  2. TD-006 缺块返回 0 m——会造出假海平面，比报错更危险；
  3. TD-011 切倍率后海拔跟着变——说明读数乘了渲染倍率；
  4. TD-014 关闭后仍有点选/标记——开关没隔离。
- **BLOCKED**：M1（probe/pick）、M2（DebugBridge + LCJS）、M5（缺块容错）任一缺失。此时不要用截屏 OCR 代替。
- **N/A**：① TD-005 在 slope <8° 或两法差 >45° 时（数据分辨率不足）；② Terrarium 在本机网络不可达（先测，记「数据源不可达」）时，TD-001…TD-005 全部 N/A，**不算实现错**，但 TD-007…TD-015 仍须用其它可达 DEM 或缓存的瓦片跑通。

---

## 4. 回归

1. `scripts/build-android.ps1` 通过，无 FATAL。
2. 2D：底图三选一、旋转、缩放、立向标记、定位、截图（截图依赖 B-09 修复状态，另见 PRD §2.1）。
3. `python tests/numeric/run_all.py` 全绿。
4. 开关全关：**当前结构**三 Tab 截图 diff = 0；K-01 落地后按「地图态 / 罗盘浮层态 / 面板态」重采基线。

---

## 5. 风险与备注

- DEM 源是 Terrarium（`s3.amazonaws.com`），国内网络可能慢或超时：先单独测可达性；TD-012 必须在真实网络条件下跑一次，不能只在局域网。
- 网格步长随 zoom 变化（z12 时约 234 m/格），所以 TD-007 的「1 网格」容差是**当前数据分辨率**决定的，不是实现误差；若 LC地图 后续提高 GRID 或 DEM 缩放级别，本容差要同步收紧。
- `pick` 允许降级为「沿 `demAt` 网格步进 + 二分」，但必须在**同一 VEXAG 变换空间**里求交；否则 TD-008 必然错位。曲面按 2.5× 抬高后，屏幕射线与抬高后的地形求交，不是与平地求交。
- **双 WebView**：罗盘态（LC 现状 `MainActivity.buildCompassTab()`）与地图态（`buildMapTab()`）各加载一份 `map.html`，`__LC3D` 是两个独立实例；任何 marker/倍率断言必须 `target=both` 两边都查。**K-01 落地后两 Tab 合并为单页（地图 + 罗盘浮层），WebView 可能只剩一个——届时应改为 `target=map`，先查 `__LC3D` 实例数再定**。
