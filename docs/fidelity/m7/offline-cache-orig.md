# 原版「离线瓦片 / 本地瓦片目录」取证（M7 B 轮用）

- 开工 `2026-11-09 11:59`，收工 `2026-11-09 12:10`，返工 0 轮
- 取证对象：原版 1.6.1 `space.xinzhi.globe`（Frida 内存 dump 的 smali + 原版 assets 内 web bundle）
- 目的：B 轮①②③ 的「原版有没有、长什么样」判据出处。**本文只写证据，不改业务代码。**

---

## 0. 结论（可直接抄进台账）

1. **原版没有「浏览即缓存」的离线机制。** 原版 web 层 17 个 bundle 全量计数：
   `CacheStorage = 0`、`caches.open = 0`、`indexedDB = 0`、`serviceWorker = 0`。
   （`caches.` 命中 7 处，全部是撤销栈数组 `this.caches` / `this.caches.pop()`，**不是 Web Cache API**。）
   ⇒ 我方 B 轮「断网后已缓存区域仍可看图」属 **新增（竞品 P0）**，台账不能写「与原版一致」。

2. **原版唯一把瓦片写到磁盘的路径是「超清优化 / 导入地图」任务**（`SuperMap*` 一组类）：
   ```
   <filesDir>/tiles/<task_id>/{z}_{x}_{y}.png
   ```
   元数据落 Tencent WCDB：库 `super_map.db`、表 `SuperMapRecordTable`、Helper `SuperMapDbHelper`。

3. **原版有「把本地目录注册成栅格源」的完整先例**（`addSuperResolution`），字段与 MapLibre
   raster source 一一对应。这证明「本地瓦片 → 地图图层」这条路原版走过，但用途是超清影像，
   **不是**天地图底图的离线缓存。

4. **原版有「当前视野应有瓦片」的现成算法**（`getCoveringTiles`），可直接当我方
   `cacheExpect()` 的判据参照（见 §2.5）。

---

## 1. 检索范围与命令

| 范围 | 命令 |
|---|---|
| web 层 17 个 bundle 的离线 API | `Get-ChildItem orig-compass-assets\web\js -File -Filter *.js` 逐个 `[regex]::Matches(ReadAllText, 'CacheStorage\|caches.open\|indexedDB\|serviceWorker')` |
| smali 全量 | `Get-ChildItem .inspect\unpack\mem_smali -Recurse -Filter *.smali`（7698 个文件）`Select-String '/tiles/\|DiskLruCache\|TileStore\|OfflineManager'` |
| 中文文案 | smali 里中文是 `\uXXXX` 转义（**UTF-8 直接搜中文命中 0，会假阴性**），须用 `'\\u79bb\\u7ebf'` 这种转义串或先解码 |
| 资源 | `decoded\res\values*\strings.xml` 全量搜 `离线` = 0 命中（该功能文案是 Compose 内联，不在 strings.xml） |

> 坑：`mem_smali` 里 0 个非 ASCII 字节，中文全是 `\u` 转义；用 `Select-String '离线'` 得到「零命中」是**假阴性**，不是证据。

---

## 2. 锚点

### 2.1 落盘路径（两处独立命中）

`space/xinzhi/globe/map/MapWebApiKt.smali`
```
:83   .method public static final addSuperResolution(MapWebController, SuperMapRecordModel)V
:170      const-string v0, "/tiles/"
:179      const-string v0, "/{z}_{x}_{y}.png"
:387      const-string v2, "addSuperResolution"
:1572 .method public static final getCoveringTiles(MapWebController, Function1)V
:1619     const-string v2, "getCoveringTiles"
```
拼接顺序（`MapWebApiKt.smali:1xx`）：`getAppContext().getFilesDir().getPath()`
`+ "/tiles/"` `+ model.getTask_id()` `+ "/{z}_{x}_{y}.png"` → 作为 JSON 的 `url` 字段。

同串在下载侧再出现一次：`space/xinzhi/globe/viewmodel/SuperMapViewModel$d.smali:691` `/tiles/`、
`:700` `/{z}_{x}_{y}.png`（此处结果为 `SuperMapRecordModel.setSave_dir(...)`）。

### 2.2 库与表

`space/xinzhi/globe/db/r.smali`：`"super_map.db"`、`"SuperMapRecordTable"`、`"SuperMapDbHelper"`、
`"ALTER TABLE " + " ADD COLUMN "`（带 `"duplicate column name"` 容错）、`"addRecord failed. taskId="`、
`"清空历史列表失败，原因"`。
模型 `space/xinzhi/globe/model/DBSuperMapRecordModel.smali` 实现 `com.tencent.wcdb.orm.TableBinding<SuperMapRecordModel>`，
每个字段一个 `com.tencent.wcdb.orm.Field`。

### 2.3 WebView 命令 `addSuperResolution`（原生 → JS 载荷）

`MapWebApiKt.addSuperResolution`（:83 起）按序塞入：
`linkLayer`(=model.getLayerId)、`id`(=model.getTask_id)、`url`(§2.1 的本地模板)、
`maxzoom`、`minzoom`、`projection`、`scheme`、`bounds`（把 `model.getBounds()` 用 `","` split 后逐项
`toDoubleOrNull() ?: 0.0`，再 `GsonKt.toJsonTree` 成 double 数组）。
最后 `MapWebController.javascript("addSuperResolution", payload)`。

### 2.4 Web 端 raster source 参数（`index.946c1624e0.bundle.js`，逐字）

```js
tI(this,"addSuperResolution",e=>{let t={linkLayer:e.linkLayer,id:e.id,sources:{[e.id]:{tileSize:256,
priority:!0,type:"raster",projection:e.projection||(0,tv.l)(e.linkLayer),scheme:e.scheme||(0,tv.ie)(e.linkLayer),
tiles:[(0,tv.Ad)(e.linkLayer,e.url)],maxzoom:e.maxzoom,minzoom:e.minzoom}},
layers:[{zIndex:1,id:e.id,type:"raster",source:e.id,maxzoom:Math.min(e.maxzoom+2,20),
paint:{"raster-opacity":1,"raster-resampling":"nearest"}}]};
this.superResolutionStyleMap.set(e.id,t),this.showCurrentsSuper()})
```
要点：`tileSize:256`、`type:"raster"`、`raster-opacity:1`、`raster-resampling:"nearest"`、
图层 `maxzoom = min(source.maxzoom+2, 20)`。

### 2.5 「应有瓦片」算法 `getCoveringTiles`（逐字）

```js
tI(this,"getCoveringTiles",(e,t)=>{
  let r=this.map.transform.coveringTiles({tileSize:256,minzoom:0,maxzoom:18,roundZoom:!1}),
      i=r.reduce((e,t)=>Math.min(e,t.overscaledZ),1/0), o=r[0].overscaledZ,
      a=this.map.getBounds(), n=a.getWest(), s=a.getEast(), l=[n,a.getNorth(),s,a.getSouth()];
  tm.ju.get(tf.Z.baseMapCode)().then(e=>{
    let a=e.sources[e.id],
        n={maxZoom:o,minZoom:i,projection:a.projection,scheme:a.scheme||"xyz",
           bounds:(0,ty.e)(l).join(","),
           tiles:a.tiles.map(t=>(e.token&&(t=t.replace(e.token,"")),t)),
           coveringTiles:this.calcCoveringTiles(r,a.projection)
             .map(e=>({z:e.z,x:e.x,y:"tms"===a.scheme?Math.pow(2,e.z)-e.y-1:e.y}))};
    t&&t(n)})})
```
⇒ 判据可直接照抄：`coveringTiles({tileSize:256,minzoom:0,maxzoom:18,roundZoom:false})`、
取 `min/max overscaledZ`、`bounds = [west,north,east,south]`、`scheme==="tms"` 时 `y = 2^z-1-y`、
瓦片 URL 里 `token` 要去掉再比对。

### 2.6 用户可见文案（Compose 内联，`\u` 解码后）

| 类 | 文案 |
|---|---|
| `SuperMapListActivity` | 导入地图 / 取消导入 / 预览 / 重新生成 / 删除 / 关闭 / 取消 / 已导入 / 优化中… / 生成失败 / 100%（`…kt:101/242/257/279/310`） |
| `SuperMapViewModel` | 进行中 / 生成失败 / 已导入 / 生成成功 / 重新生成 / 删除 / 导入地图 / 预览；错误串「下载图片失败，响应码: 」「删除任务目录失败: 」「解析coveringTiles2失败: 」 |
| `SuperMapToolsKt`（`ui/tools/J2.smali`，SMAP 注明 `SuperMapTools.kt`，源行 167 行） | 任务列表 / 开始超清优化 / 等高线 / 图层 / 搜索 / 返回 / `hd_ck` / `hd_task_ck` |

### 2.7 数据结构（`SuperMapRecordModel` 字段全集）

`id`、`task_id`、`userId`(J)、`create_time`(J)、`name`、`bounds`、`coveringTiles2`、`images`、
`minZoom`/`maxZoom`(I)、`projection`、`scheme`、`save_dir`、`status`、`progress`(Float)、
`imported`(I)、`isLoad`(Z)、`layerId`、`linkLayer`。
`coveringTiles2` 被 `GsonKt.parseString` 成 `MutableList<Tile>`（见 §2.6 的「解析coveringTiles2失败: 」）。

---

## 3. 对 B 轮三条判据的建议（供总监/地图取用）

- **② 断网可看图**：定性为**新增能力**。原版没有浏览缓存，因此「断网降级提示」没有原版对照，
  只能按我方自洽写判据（已缓存命中 > 0、`shown/hit == 1`），**不许写「与原版一致」**。
- **`cacheExpect()` 的算法**可以直接抄 §2.5（tileSize/zoom 范围/roundZoom/tms 翻转/token 剥离），
  这样「应有 key」的定义与原版「下载哪些瓦片」的定义同源，避免我方自造一套。
- **key 命名**：原版是纯 `{z}_{x}_{y}.png` 平铺在 `tiles/<task_id>/` 下，**没有来源/版本前缀**
  （因为它一个任务一个目录、绑死一个图层）。我方若要加 `source/version` 前缀，属新增设计的自由，
  不与原版冲突；但**不能声称"仿原版 key"**。
- **总监判词（2026-11-09，原文照记）**：原版 key 无前缀，**不能反推我方 key 形状**；我方加前缀属自加。
- **③ 天地图离线缓存条款**：不在本文范围，未查证。若要结论，需另起一条（附条款 URL 原文）。

---

## 4. 未确认（不许当结论用）

| 项 | 状态 |
|---|---|
| `hd_ck` / `hd_task_ck` 的业务语义 | 未确认（只有字符串与 `const-string` 位置） |
| 「超清优化」是本地处理还是服务端处理 | 未确认（见到 `https://mapcube.21atcloud.com.cn/...` 影像模板与 `ll.php`、`token=` 拼接，但未定位调用链） |
| 该功能在真机 UI 的确切入口点位 | 未确认（`SuperMapToolsKt` 属工具面板 Compose，未做真机 bounds） |
| 原版是否另有 okhttp 磁盘缓存覆盖底图瓦片 | 无书证；`DiskLruCache` 只命中 okhttp3 自带类（`okhttp3/internal/cache/DiskLruCache`），未见业务侧引用 |
| 原版是否有「离线地图」字样 | 无：`strings.xml` 全语言 0 命中「离线」，web 层 0 命中「离线」 |

---

## 5. 复现命令（贴给复验方）

```powershell
$w="E:\APPDetector\orig-compass-assets\web\js"
Get-ChildItem $w -File -Filter *.js | ForEach-Object {
  $t=[System.IO.File]::ReadAllText($_.FullName)
  foreach($p in @('CacheStorage','caches.open','indexedDB','serviceWorker')){
    "$($_.Name) $p = " + ([regex]::Matches($t,[regex]::Escape($p))).Count }
}   # 全 0

$m="E:\APPDetector\output\.inspect\unpack\mem_smali"
Select-String -Path "$m\space\xinzhi\globe\map\MapWebApiKt.smali" -Pattern '/tiles/|\{z\}_\{x\}_\{y\}|addSuperResolution|getCoveringTiles'
Select-String -Path "$m\space\xinzhi\globe\db\r.smali" -Pattern 'super_map|SuperMapRecordTable|SuperMapDbHelper'
```
