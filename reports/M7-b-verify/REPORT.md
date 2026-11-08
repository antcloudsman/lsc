# B 轮真机验收（LC 审核验，2026-11-08 21:00–21:09）

- 包：本人合并 `origin/develop` + `a70fb3d`（地图缓存次序）后自建，`apk_sha256 = 51c495cb1a1f0efa…`；
  装机时已 `adb pull base.apk` 回核一致；设备 `L2E0222531014833`，租约 `-Owner 审核验`，测完 `-Release`。
- 方法：全部走调试钩子，不靠肉眼；文件级判据（KML）用本地脚本离线比对。

## ① KML 往返（同坐标系）

清空（`pm clear`）后导入 `a.kml` → 再导出 `b.kml`：

```
placemarks: A=12  B=12
lon/lat 比较值 24   max_abs_diff = 0.000e+00   => PASS
alt     比较值 0    presence 不一致 = 0        => PASS（收藏点无海拔，两边都不写 alt）
名称不一致 0
```

**逐字节不相同**（hash 不同）是预期的：`lc:exported` 是导出时间、`lc:created` 是导入时刻。

逐点业务字段：

```
sit / heading：按坐标排序后集合与逐点值完全一致  (0.0 / 180.0)
lc:id：导入后仍为 12,11,10,…,1（保留）
lc:created：1794142044671 → 1794143053519（变化，符合「导入不入库、取导入时刻」的已知边界）
```

## ② B-1 逐点字段（grep 快筛）

```
12 点：lc:id=12  lc:type=12  lc:sit=12  lc:heading=12  lc:created=12   （= 点数 ✓）
lc:elev = 0（本轮收藏点无海拔列，按值有无，期望 0 ✓）
coordinates：2 项=12 / 3 项=0；以 ",0" 结尾 = 0 ✓
```

## ③ 导入后点上图

```
getState().overlay = 12，overlayKind = "kml"
截图可见 KML 标记标签（P3）    => PASS
```

## ④ 缓存四步（判据②）

| 步 | 实测 | 判 |
|---|---|---|
| STEP0 默认态 | `enabled:false, sources:[], hitRate:null, items:0, put:0`；`storeDiag:{called:160, disabled:160}` | **PASS**（默认全关，实测不是读代码） |
| STEP1 启用源 | `sources:["img"], enabled:true` | **PASS** |
| STEP2 在线积累 | `items:20, cacheBytes:428059 (0.4MB), put:680, net/netOk:1520` | **PASS** |
| STEP3 离线回看 | `online:false`；`hit:660, shown:660, hitRate:1.0, miss:0`；缓存仍在（items 20 / 428,059 B） | **缓存命中 PASS**，但见下方缺陷 |
| STEP4 未缓存区 | `miss:40`；banner 文案「当前区域未缓存，联网后可查看（已命中 660 块 / 缺 40 块）」存在 | **提示存在但被遮挡 → FAIL**（见下） |

### 缺陷 1：离线时 `netOk` 仍在增长，净计数器不可信

```
离线前 net/netOk = 1520 / 1520
离线回看后      = 2180 / 2180    ← netOk 增加 660，正好等于 shown 的增量
```

`netOk` 的定义是「真的从网络拿到的图」，离线时不该增加。代码位置 `map.html:610-611`：

```js
if (img && img.getAttribute && img.getAttribute('data-fromcache') === '1') TILE_OFF.stats.shown++;
else tileDiag.netOk++;
```

同一张图走了两次 `__tileOfflineLoad`（占位图 onload 时 `data-fromcache` 还没置位 → 计进 `netOk`；回填后再来一次 → 计进 `shown`），于是 `netOk` 被占位图污染。

**影响**：判据「`hit` 增 + `shown` 增 + `netOk` 不增」在当前实现上必然 FAIL（实测 +660），不是判据写错，是计数器不可靠。
**修法**：`netOk` 只在「`img.src` 是 http(s) 且 `navigator.onLine === true` 的成功加载」这条路径 +1；占位图/回填走独立分支，不参与。

### 缺陷 2：降级提示被原生底部栏遮挡

```
banner rect（CSS）：top=763.20  bottom=792.00   viewport.innerHeight=800
原生底部栏（磁北/偏角）约占 CSS 773..800
⇒ banner 下沿 792 落在原生栏内，约 19/29 px 被盖住，屏幕上看不到整句话
```

截图 `offline-banner.png`：只露出提示的上半行，其余被「磁北 / 偏角」栏压住。
**修法**：banner 的 `bottom` 要避开原生栏（如 `bottom: calc(8px + <栏高>px)`），或原生把栏高通过桥告诉页面（`--lc-bottom-inset`）。

## ⑤ 判据③ 合规

产品五条检索记录（4×HTTP 200 但 JS 渲染抓空 + 1×fetch failed）已归档为「**未查证 → 不实施**」，缓存默认关闭、白名单为空（STEP0 实测 `sources:[]` 佐证）。**本项按「有意未实现」关账，不判 FAIL。**

## 待办（未覆盖）

- 判据④ 四道门禁需在**最终 develop**（合并 `a70fb3d` 后）重跑。
- 缺陷 1/2 修复后需复验：离线 `netOk` 不增、banner 完整可见。
