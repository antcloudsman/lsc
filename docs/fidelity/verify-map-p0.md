# Stage 5 独立复核：R2-030 与 R2-025 数据侧

复核人：LC审地图　｜　基线：`origin/develop` = `f3b0bb5cb10d9304cbce5f5ad28c24d3bffcc78a`
方法：判据原文（`docs/fidelity/verify-p0-judge.md` §1/§2）+ 已提交证据（`docs/fidelity/lc/right-mountains-entry|off.*`）+ 代码路径（`MainActivity.java` / `MapPoints.java`）。设备在 LC审核验 手里，本复核未上机、未改代码。

## 1. R2-030（山脉 → 地图内半屏图层）

判据（`verify-p0-judge.md:31`）：点山脉后 `mResumedActivity` 仍 `MainActivity`；dump 出现地图内面板且**非整页**（面板顶之上仍露地图）；关闭后 `lc_mtn_*` 节点数 = 0；再点可重开。

| 判据项 | 实测证据 | 结论 |
|---|---|---|
| 非整页、面板在地图内容之上 | `lc_mtn_layer [0,1341][1228,2578]` = 1237 px = 0.48×2578，顶边 1341 之上仍是地图；`lc_mtn_title`「看山脉」`[47,1409][884,1477]`、`lc_mtn_close`「关闭」`[884,1368][1181,1530]`、`lc_mtn_status`「视野内山名 113 个（天地图地名搜索 · 分类 260203 山）」、`lc_mtn_list [47,1581][1181,2551]`（`docs/fidelity/lc/right-mountains-entry.xml`） | 通过 |
| 前台不变（无 startActivity） | 代码 `MainActivity.java:1060-1067`（`case "山脉"`）→ `1065` `toggleMountainLayer()` → `1582` 起；`1590-1616` 只对 View 做 `addView`/`setVisibility`；`1611` `hideMountainLayer()` 仅 `GONE`；全路径无 `startActivity`/`Intent` | 通过 |
| 关闭后节点 0 | `docs/fidelity/lc/right-mountains-off.xml`：`lc_mtn_*` 节点 **0**（解析结果 mtn_nodes=0） | 通过 |
| 再点可重开（节点回来） | 代码对偶成立：`1583-1586` 以 `mountainLayerOn` 开关；`1592` 仅在未挂载时 `addView`（已挂载则跳过）；`1601` `setVisibility(VISIBLE)` 并重新 `fetchMountainNamesForLayer()`；但**仓库里没有「关→再点开」的 dump**，现只有 entry/off 两态 | 代码成立，缺实证 |

**结论：R2-030 通过（可关账）**；附一条补证要求：审核验在窗口补一张「关闭 → 再点山脉」的 dump（证明 `lc_mtn_*` 节点回≥1），30 秒内可完成，不影响当前判词。若设备回来时审核验已释放，我可代跑一次。

## 2. R2-025 数据侧（DEP-3：降级路径）

判据（`verify-p0-judge.md:30`）：分支 A（探测=可用）要求 **4 tab 各自有数据源** + 超配额返回**可区分降级态**（非空白、非假数据）；红线：任何假 POI 一律不通过。

### 2.1 可区分降级态：通过

- 数据层五态：`MapPoints.java:61-65` `OK / EMPTY / RATE_LIMITED(HTTP 429) / QUOTA(业务错误码 3xxxxx) / UNAVAILABLE`；判定在 `96-120`（`infocode!=1000` → 3 开头为 QUOTA、其余 UNAVAILABLE；异常含 `HTTP 429` → RATE_LIMITED）。
- UI 层逐态有明确文案、不留白：`MainActivity.java:2505`「未找到相关地点（可试试城市名或坐标）」、`2512`「请求过快（HTTP 429），稍后再试」、`2514`「配额不足（detail）」、`2516`「检索服务不可用（detail）」、`2522`「检索服务不可用（异常类名）」。
- 实测证据：审核验 22:52 断网复判「检索服务不可用（Unable to resolve host \"api.tianditu.gov.cn\"…）」`[126,1217][1101,1403]`（`verify-p0-judge.md:42`）。`quota`（301xxx）仍未观测到 → **未确认**，不计通过项（与总监口径一致）。
- 观察项（不影响本条结论）：`MapPoints.java:108` 把**所有** 3 开头 infocode 都映射成「配额不足」，因此非法 key（301013）会显示成「配额不足（infocode=301013…）」，文案误导；建议把 3010xx（key/权限）与配额码分开，或统一改称「检索服务受限（infocode=…）」。

### 2.2 无假数据：通过

- `MapPoints.java:126-146` `parsePois` 只读接口返回的 `pois[].lonlat`/`name`/`address`，`lonlat` 缺逗号的单条直接跳过，无合成、无占位、无兜底 POI。
- 山脉面板的 113 条山名来自 `api.tianditu.gov.cn/v2/search` 分类 `260203`（`right-mountains-entry.xml` 的 `lc_mtn_status` 文案 + `MainActivity.java:1664-1694` 查询链路，`1694` 为 `MapPoints.category(MapKeys.TYPE_MOUNTAIN, province, 120)`），非编造。
- 坐标输入的 GCJ02/WGS84/BD09/CGCS2000 四行是本地换算（`MainActivity.java:2453` `CoordConv.wgs84ToGcj02`，`2454-2456` 三行），不是 POI，也不消耗配额；CGCS2000 行明确标「与 WGS84 等价」。

### 2.3 四 tab 数据源：不通过

| tab | 数据源 | 判定 |
|---|---|---|
| 收藏点 | 本地 `GapDb.listInterest()`（`MainActivity.java:2461-2463`），不依赖关键词与网络，空态有「暂无收藏点…」 | 满足 |
| 境内 / 境外 / 行政区域 | **同一个请求**：`2496` 取 `final int tab = active[0];` 之后 `tab` **未参与任何查询**；`2501` 三 tab 统一 `MapPoints.searchNear(kw, flat, flng, 12, 20)`；`MapPoints.java:85-93` 固定 `mapBound=中心±0.6°`、`queryType=2`、`level=12` | 不满足「各自有数据源」 |

后果是可观测的：同一关键词在「境内」「境外」「行政区域」下返回**完全相同**的 20 条（同样的视野框、同样的 level），「境外」永远搜不到境外（窗口仍是中国境内 ±0.6°），「行政区域」也不做行政区解析（`MapPoints.provinceCode()` 已存在却未被搜索链路调用）。

复现（设备 30 秒，或纯判码）：地图中心 39.9042/116.4074 → 搜索「故宫」→ 记 `LCSEARCH code= total=` 与结果首行 → 切「境外」再搜同一词 → 两行文本与 `code/total` 完全一致，仅 tab 高亮色不同。

修法（二选一，需总监拍）：
- **补语义（推荐，约 0.5 人日）**：境外 → 用全球范围（去掉/放大 `mapBound` 或改 `queryType`）；行政区域 → 先 `MapPoints.provinceCode(lat,lng)` 取省码再限定检索；境内 → 保持现实现。
- **登记 DI**：承认「4 tab 仅视觉分组、语义未实现」，写进报告 DI 表，不计入 R2-025 数据半通过项。

## 3. 结论（给总监）

| 项 | 结论 |
|---|---|
| R2-030 | **通过（关账）**；补一张「重开」dump 归档 |
| R2-025 数据侧 · 降级路径（可区分、非空白） | **通过** |
| R2-025 数据侧 · 无假数据 | **通过**（红线未触碰） |
| R2-025 数据侧 · 四 tab 各有数据源 | **不通过**（境内/境外/行政区域同一查询） |
| R2-025 quota（301xxx） | **未确认**（未观测，不写成通过） |

即：R2-025 数据半不能按「整条通过」关账——要么补 tab 语义后关账，要么按 DI 登记后关账。UI 半（四 tab 存在、坐标四行、186 间距）属审核验/客户端口径，不在本复核重复判定。

## 4. 未覆盖

- 未上机复测（设备归审核验）：HTTP 429、quota 301xxx 无真实响应样本，只有代码分支与文案证据。
- 未复核搜索 UI 的视觉细节（输入框/历史 chip/行高 186）——属 R2-025 UI 半。
- 未复测「境外」在原版的实际检索范围（原版证据只有 `orig/search-result-01/02.xml`，未见境外结果页），因此本条按「tab 语义应有差异」的常理与代码事实判定，不引用原版实测。
