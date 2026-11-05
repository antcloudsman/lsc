# 地图侧免费数据源与做法（F-02 / F-04 / F-06 / F-07 / F-09 / F-10 / F-16）

> 归属：LC地图。依据 `00-PLAN.md` §4.0「无门槛」与 §4.1 数据替代矩阵；页面结构/入口以逆向的单功能文档与 `00-UI-ROUTES.md` 为准，本文只给**免费数据源、map.html 接口、免费边界、人日**。
> 实测时间 2026-11-05（本机直连 HTTP，非推测）；凡「未核实」处不排期。
> 共同前提：天地图需 key（现 `map.html` 内置的是原版 key，归属第三方；应换 LC 自有 key，个人配额 1 万次/日/服务）。DEM 沿用 Terrarium（AWS 公开数据，境外源，现已在用）。

## F-02 超清优化 / 超清地图（降级超清，无锁）

- **免费方案**：天地图 `img` **z18**（公开最高级）+ 本地瓦片缓存（IndexedDB，键 `z/x/y + 版本`）+ 2D 双三次放大 / 3D 贴面上采样；文案写明「z18 原生 + 本地放大，非云端超分」，入口开放、无会员门。
- **原版事实**：云端任务（后端 `/api/superResolution/getResult` + `SuperMap*` 下载瓦片、`createCombinedBitmapFromTiles` 拼接），算力与影像源都不可复刻，只做降级。
- **实测**：`https://t0.tianditu.gov.cn/img_w/wmts?...TILEMATRIX=18&TILEROW=99027&TILECOL=215537&tk=…` → **200** `image/jpg` 12,033 B（北京）；`ter_w` 上限 z14（z15 → 空白 859 B）。
- **map.html 接口**：`setMaxZoom(18)`（已有上限）、`enableTileCache(on)`、`setUpscale('bicubic'|'nearest')`；事件 `{"type":"cache","hit":n,"bytes":b}`。
- **人日 3.5**：缓存 1.5 + 2D 放大 1.0 + 3D 贴面 0.5 + 文案/AT 0.5。风险：缓存脏读、z18 无覆盖处回落 z17。

## F-04 路网

- **免费方案**：天地图 `vec` 底图本身含路网图面 + `cva` 注记（路名）；「路网视图」= 切 `vec`，或影像上叠 `cva` 做「影像+路网」混合；关掉恢复原底图。
- **原版事实**：`MapWebInterface` 33 个方法里**没有路网专用方法**（只有 `onMapScroll/onMapRotate/onClickMapElement/openStreetView…`），路网是底图/叠加切换而不是独立图层；`setTrafficEnabled/showRoad` 只见于打包的 AMap SDK 类与 `MapManager` 持久化字符串，**未证实在用**。
- **实测**：`https://t0.tianditu.gov.cn/vec_w/wmts?request=GetCapabilities…` → **200**，仅 1 个图层 `vec`、z1–18；无「路网/实时路况」图层，天地图公开服务清单也无交通服务。
- **map.html 接口**：`setRoadNet(on)`（`on` → `setBase('vec')` 或叠 `cva`；`off` → 恢复原 base）；事件 `{"type":"roadnet","on":bool,"mode":"vec|hybrid"}`。
- **人日 1.5**：切换+注记叠层 1.0，真机/AT 0.5。**边界：没有实时路况**——无免费源，不做假红绿线。

## F-06 山脉（看山脉，无锁）

- **免费方案**：页面本身是地图（逆向探针已证：山体晕渲 + 地名标注）；我们用地图 + 天地图分类搜索取「山 260203」点 + 点选详情（名称/坐标/DEM 海拔/坡向）+ DEM 派生山脊线（3D）。
- **实测**：`https://api.tianditu.gov.cn/v2/search?postStr={"queryType":13,"dataTypes":"260203","specify":"156370000",…}&type=query&tk=…` → **200**，山东 117 条（虞山/匡山/雪山…），字段 `name/address/lonlat`；`dataTypes":"山"` 等价。
- **map.html 接口**：`setOverlay('mountain', on, {dataTypes:'260203', specify, max:200, minZoom:8})`、`setRidge(on)`（3D）；点选走 `pointInfo` 事件；请求复用 `searchCategory`。
- **人日 3.0**：标注/聚合/点选 2.0 + 山脊线 1.0。**边界**：点位是 POI 坐标，山脉面/脊线为 DEM 派生，不是官方边界（角注标明）。

## F-07 水系（看水系，无锁）

- **免费方案**：地图 + 分类搜索「河流 260204 / 湖泊 260205」点 + 名称/坐标/到当前位置距离/DEM 海拔；底图用 `vec`/`ter` 图面 + `cta` 注记。
- **实测**：`queryType=13&dataTypes=260204&specify=156110000` → **200**，北京 933 条（凤河/潮河/转河…）；`260205` 全国 54,229 条。**天地图没有水系矢量 WMTS 图层**（GetCapabilities 每端点单图层）。
- **线/面缺口**：免费候选只有全国 1:100 万基础地理（`webmap.cn`），当日 **502**，授权/格式/体积**未核实**，不排期；DEM 派生河网可做但平坦区断线（+3.0）。
- **map.html 接口**：`setOverlay('water', on, {dataTypes:'260204,260205', …})`；`pointInfo` 带距离/海拔；预留 `loadWaterVector(url)`（数据核实后再实现）。
- **人日 3.0**：点位+详情 2.0，`vec` 图面对比度 0.5，AT 0.5；线面另 +3.0（未核实前不排）。

## F-10 历史影像（无历年免费源，入口保留）

- **免费结论**：**没有全国历年多时相免费源**。原版是 Google Earth 历年影像（`GoogleHistoryHelper/DBRootV5/QTree`，dex 类名证据），不可合规接入。
- **免费方案**：天地图 `img` 单期 + 「时间快照」——浏览时把命中瓦片按日期落本地（IndexedDB，200 MB LRU），以后可对比「今天 vs 上次看这片时」；不做假时间轴、不设门。
- **实测**：天地图 `img` z18 → **200**（同 F-02）；省级试点只有陕西富平 25 km²（2009–2019），不采用。
- **map.html 接口**：`historyCompare('single'|'snapshot')`、`snapshotList()`；事件 `{"type":"history","mode":"single|snapshot","dates":[…]}`。
- **人日 3.0**：单期对比 1.5 + 快照缓存/列表 1.5。**边界**：装后才有快照，补不回 2026 之前。

## F-09 天气（免费源已实测，无锁）

- **免费方案**：中国天气网 d1 接口（实况 / 7 日 / AQI）+ 定位城市；备源 open-meteo（无 key、文档化、按经纬度取）。
- **实测**：`http://www.weather.com.cn/data/sk/101010100.html` → **200**（temp/WD/WS/SD/time）；`http://d1.weather.com.cn/weather_index/101010100.html` + `Referer: http://www.weather.com.cn/` → **200**（7 日 `cityDZ`）；`http://d1.weather.com.cn/aqi/101010100.html` 同 Referer → **200**；**去掉 Referer 一律 403**；`https://api.open-meteo.com/v1/forecast?latitude=39.9042&longitude=116.4074&current=temperature_2m,relative_humidity_2m,weather_code&timezone=Asia/Shanghai` → **200**；高德 `https://restapi.amap.com/v3/weather/weatherInfo?city=110000`（无 key）→ **200 但 `INVALID_USER_KEY`**。
- **限额/授权**：中国天气网接口**未文档化、限额未公布**，可能随版本变更；open-meteo 官方免费（非商业 ≤1 万次/日，需署名）。
- **map.html / 客户端**：天气是独立页（`WeatherActivity`），不占地图接口；客户端 OkHttp 带 `Referer` 请求，失败回落 open-meteo。
- **人日 3.0**：三接口 + 定位 + 缓存 2.5，AT 0.5。风险：d1 反爬（Referer/UA）、城市码需内置映射表。

## F-16 地理影像（无免费目录，做单期+快照）

- **原版事实**：`GeographicImageryActivity` + `DateList/GeoImageryDateItem/GeoDetails` + 后端 `/api/geoImagery/getGeoImagery` → 服务端采购影像的**日期目录**；无公开免费等价目录。
- **免费方案**：入口开放，展示天地图 `img` 单期影像 + 复用 F-10 快照日期列表（本地自建）；文案「无历年多时相免费源，仅当前影像 + 本地快照」。
- **实测**：天地图 `img` → **200**（同 F-02）；`/api/geoImagery/getGeoImagery` 是原版私有后端，无账号/key 不可调用（未尝试绕过）。
- **map.html 接口**：复用 `historyCompare('single')` / `snapshotList()`，不新增接口。
- **人日 2.5**：页面壳 + 复用组件 + 文案/AT。

## 汇总

| 项 | 免费源 | 结论 | 人日 |
|---|---|---|---|
| F-02 超清 | 天地图 img z18 + 本地缓存/放大 | 降级可做，无锁 | 3.5 |
| F-04 路网 | 天地图 vec + cva | 可做；无实时路况 | 1.5 |
| F-06 山脉 | 天地图分类点 + DEM | 可做（点+派生脊线） | 3.0 |
| F-07 水系 | 天地图分类点 + vec 图面 | 可做（点；线面缺口） | 3.0（+3.0 待核） |
| F-10 历史影像 | 天地图单期 + 本地快照 | 无历年源；对比/快照可做 | 3.0 |
| F-09 天气 | 中国天气网 d1 + open-meteo 备源 | 可做，无锁 | 3.0 |
| F-16 地理影像 | 复用单期+快照 | 无免费目录；入口保留 | 2.5 |
| **合计** | | | **19.5** |

**map.html 新增接口汇总**（落地时并入 `docs/map-bridge.md`）：`setMaxZoom`、`enableTileCache`、`setUpscale`、`setRoadNet`、`setOverlay('mountain'|'water')`、`setRidge`、`historyCompare`、`snapshotList`、`searchCategory`、`pointInfo` 事件；事件统一 `LCAndroid.onEvent(JSON)`，失败必回 `error`，不用 0/空串当哨兵。
