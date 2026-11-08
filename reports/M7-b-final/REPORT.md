# B 轮最后一轮复验（2cb5334，2026-11-08 21:17–21:22）

包：本人 `git checkout 2cb5334`（总监跑门禁那棵树）+ `Remove-Item app/build/outputs` 后重建，
`apk_sha256 = 0a62cda346cd2eef…`，装机后 `adb pull base.apk` 回核一致；租约 `-Owner 审核验` → 测完 `-Release`。

## 远端状态（必须先说）

`git ls-remote origin refs/heads/develop` → **4e34f39**；该树 `map.html` 里 `setSafeArea` / `offlineAttempts` **命中都是 0**
⇒ **地图的缓存两笔修复（`0f80854` / `2cb5334`）尚未进远端 develop**，只在 `feature/地图-b1-tilecache` 与本机 `develop` 引用上。
本报告测的是 `2cb5334`（含修复），但**别人 `git fetch` 拿到的主线不含它**。

## 四组数

| # | 判据 | 实测 | 判 |
|---|---|---|---|
| 4 | 默认态 | `enabled:false, sources:[], hitRate:null, items:0, put:0`；`storeDiag.called=120/disabled=120` | **PASS** |
| 2 | 离线 `netOk` 冻结 | 全程 **160 → 160**（多次离线重绘、两次平移、两次换缩放） | **PASS** |
| 2 | `offlineAttempts == hit + miss` | 30=0+30 → 70=0+70 → **1110=0+1110** | **PASS**（等式恒成立） |
| 1 | `shown / hit` 比值 | **无法判定**：`hit=0`，分母为 0（见缺陷 A） | — |
| 3 | 未缓存区 `miss` 增 + 文案 | `miss` 0→30→70→1110；文案「当前区域未缓存，联网后可查看（已命中 0 块 / 缺 70 块）」 | 文案 **PASS** |
| 3 | `banner.bottom ≤ 原生栏顶 − 8` | `safeArea bottom=32css (108px win=2700)`；`banner rect top=731.20 bottom=760.00`，`innerHeight=800`；期望 `800−32−8=760` —— **逐位吻合** | **PASS**（截图可见完整一句，不再被底栏遮） |

banner 修法验证：页面确实只消费原生下发值（`safeAreaBottom` 出现在 `getState().tileOffline` 里为 32），没有写死偏移。

## 缺陷 A（阻断）：离线时缓存 0 命中，且 miss 异常膨胀

```
在线积累：sources=["img"]，items=40，cacheBytes=528,659 B，put=20
断网后：
  · 首次回看（视野未变）        hit=0 miss=0    （瓦片已在 DOM，不重新请求，正常）
  · 缩放 12→13                  hit=0 miss=30
  · 缩放 13→12（已缓存层级）     hit=0 miss=70   ← 40 项在库，仍 0 命中
  · 两次小幅平移（重叠区）       hit=0 miss=1110 ← 0 命中 + 尝试量暴涨
```

两个独立问题：

1. **键不匹配（0 命中）**：库里 40 项、当前视角在同一层级与区域内，却一次也没命中。写入用 `tileOfflinePut(key)`、读取用 `tileOfflineGet(key)`，两处 key 都取自 `data-tkey`（`map.html:577/583` 由 `tileKeyOf(layer, zoom, xx, yy)` 生成），**理论上一致**；需要把库里实际 key 与请求 key 各打印几条才能定位（我这轮没有取到，见下）。
2. **重试风暴**：两次平移产生 **1110 次**离线尝试，远超一屏瓦片数（~70）。疑为失败后重复重建 `<img>` 或每次 pan 触发整屏重建，缺去重/退避。

**取不到 key 列表的原因（方法记录）**：我注入 `tileOfflineOpen()/TILE_OFF` 想读 IndexedDB 的 `getAllKeys()`，但那些是页面闭包内的符号、未挂 `window`，注入没有结果；现有 API 只有 `getTileCacheInfo()`，它返回的是统计对象、不含键。**建议加一个只读钩子** `__LC3D.cacheKeys(n)` 返回前 n 个键（或按当前视野返回“应有 key”与“库里是否命中”），否则这类问题只能靠猜。

## 建议

- 缺陷 A 定为 B 轮阻断项：**离线不可用（0 命中）**，不是“命中率低”。
- 加 `cacheKeys(n)` 只读钩子后我 10 分钟内可定位是“key 不一致”还是“写入从未成功”（`put=20` 与 `items=40` 本身也不自洽，值得一并查）。
- 远端 develop 需要把地图两笔修复推上去，否则别人复验拿到的仍是旧实现。
