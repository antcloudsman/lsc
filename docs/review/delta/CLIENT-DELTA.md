# M2 客户端增量审查（delta · Java）

| 项 | 值 |
|---|---|
| 审查人 | LC审客户端 |
| 基线 | `review-20261105` / `d752efa`（冻结） |
| 审查对象 | `origin/develop` @ **`bfb8492`**（`git worktree` = `E:\APPDetector\review-delta`，只读） |
| 增量 | `d752efa..bfb8492` 的 `app/android/app/src`：38 个 Java 文件 +8,419 行、主/debug manifest +197 行 |
| 方法 | 逐文件读 + 全仓 grep（权限/导出/存储/线程/解码/门槛词）+ **实际构建 release/debug 两包并用 `aapt dump xmltree` 核 exported** |
| 计数 | 阻断 **0** · 高 **1** · 中 **6** · 低 **4**（共 11 条，均已证实） |
| 未覆盖 | 见 §5（F-15 轨迹未进 develop，按要求下一轮） |

---

## 1 缺陷清单（已证实）

### C-01 [中] 导入盘面：任意大小文件全量复制进内部存储，无上限

**文件:行**：`app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java:298-310`

```java
    private void importFromUri(Uri uri) throws Exception {
        File dir = new File(getFilesDir(), "compass_dials");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("mkdir failed");
        String stamp = String.valueOf(System.currentTimeMillis());

        File raw = new File(dir, "dial_" + stamp + ".img");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(raw)) {
            if (in == null) throw new Exception("open failed");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
```

**为什么是问题**：`ACTION_OPEN_DOCUMENT` 只保证 `image/*` 声明，不限制体积；这里把选中文件**全量**写进 `getFilesDir()`，循环中既无 `Content-Length` 预检也无累计上限。用户选中一个几百 MB 的图片/被改 MIME 的大文件，会直接吃满应用内部存储（不是缓存目录，不会被系统自动清理）；后续 `decodeFile` 还要再读一遍。
**修法建议**：`importFromUri` 开头查 `OpenableColumns.SIZE`，>20 MB 直接拒绝并提示；复制时累计字节数，超限中止并 `raw.delete()`。

---

### C-02 [高] 网络图片全尺寸解码：`decodeStream` / `decodeByteArray` 无 bounds、无采样，OOM 可达

**文件:行**
- `GeographicImageryActivity.java:263-273`
- `DiscoveryActivity.java:260-262`

```java
// GeographicImageryActivity.java:266-273
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", LcHttp.UA);
                InputStream in = c.getInputStream();
                bmp = BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();
```
```java
// DiscoveryActivity.java:260-262
                byte[] bytes = LcHttp.bytes(info.url);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) throw new IllegalStateException("decode");
```

**为什么是问题**：两处都对**外部服务返回的图片**做全尺寸解码。GeographicImagery 的源是 Bing 每日壁纸（常见 3840×2160，ARGB_8888 约 32 MB/张）；Discovery 的源是 Wikimedia 任意尺寸图，且 `LcHttp.bytes` 先整包读进 `byte[]`，解码时内存里同时存在「原字节 + 位图」两份。低端机/连点切换时是现实的 OOM 路径；`decodeStream` 不读 `inSampleSize` 也没有 `inJustDecodeBounds` 预检。
**修法建议**：先 `inJustDecodeBounds` 拿尺寸（网络图可 `mark/reset` 或先落临时文件），按目标显示宽度算 `inSampleSize` 再解码；或统一走 `decodeSampled` 工具（`CompassListActivity.java:353-367` 已有实现，应提取复用）。

---

### C-03 [中] `LcHttp` 无响应体上限：任何 200 大响应直接进内存

**文件:行**：`LcHttp.java:44-52`

```java
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
```

**为什么是问题**：`bytes()` 被 `DiscoveryActivity:260` 直接用来拉外部图库图片，`get()` 被 `MapPoints`/`HistoryImage`/`GeographicImagery` 用来拉 JSON。没有 `Content-Length` 预检、没有最大字节数，服务端或中间人返回超大响应时先 OOM 的是 App。超时只覆盖「连接/单次读」，不覆盖总量。
**修法建议**：`MAX_BYTES`（如 16 MB）+ 预检 `getContentLengthLong()`；循环内累计超限即 `throw`。

---

### C-04 [中] 用户可见文案仍含「会员/付费」：strings 零命中门禁不过

**文件:行（用户可见 strings，共 5 处）**
- `res/values/strings_overlay.xml:34`：`<string name="lc_overlay_no_volcano">无公开等价数据源：火山专题为会员数据</string>`
- `res/values/strings_overlay.xml:35`：`<string name="lc_overlay_no_vegetation">无公开等价数据源：植被专题为会员数据</string>`
- `res/values/strings_fish.xml:8`：`... + 本机自建 · 非原版付费底库 · 审图号：未确认</string>`
- `res/values/strings_supermap.xml:5`：`... 没有云端超分、不做会员限制。</string>`
- `res/values/strings_discover.xml:8`：`... · 页面本身不判会员</string>`

**为什么是问题**：按总监 2026-11-05 口径，`res/values/strings*.xml` 是用户可见文案，必须对 `VIP|会员|开通|升级|订阅|付费|解锁` **零命中**（注释不在此列，见下）。上述 5 处会被即将上线的 strings 门禁判红；其中「火山专题为会员数据」这种解释句还会被读成功能门槛暗示，与 D-001 去商业冲突。
**修法建议**：改成中性事实表述，例如「原版该图层未公开提供数据」「数据：天地图公开水域 + 本机自建」「无云端超分」。

> **注释口径（总监裁决，不构成违规）**：代码/文档注释允许保留「原版此处为付费/会员」的事实记录，不必为过门禁删事实；本报告初版把注释计入「命中」的口径已作废。注释示例：`FishingActivity.java:33`、`DiscoveryActivity.java:34`、`ToolsManagerActivity.java:97`。

---

### C-05 [中·继承] `MainActivity` 无 `onDestroy`：单 WebView 仍不销毁

**文件:行**：`MainActivity.java`（全文件无 `onDestroy`，grep 0 命中）；WebView 创建于 `MainActivity.java:1342-1378`

```java
    private WebView createMapWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(24, 20, 16));
```

**为什么是问题**：K-01 已合成单 WebView，但 Activity 结束时没有 `destroy()`，WebView 引擎/JS 上下文随 Activity 常驻；基线 B-06 同源，属**继承未修**而非本轮新增。新增的 10 个页面里 9 个都补了 `onDestroy`（见 §4），主页面反而漏着。
**修法建议**：`onDestroy()` 里 `compassMapView.removeJavascriptInterface("LCAndroid"); compassMapView.destroy();`，与 `FishingActivity.java:360-368` 同构。

---

### C-06 [中] 位置数据进入自动备份：`allowBackup=true` + 坐标落库/落 prefs

**文件:行**
- `app/android/app/src/main/AndroidManifest.xml:9`：`android:allowBackup="true"`
- `GapDb.java:52-56`：`CREATE TABLE interest(... lat REAL NOT NULL, lng REAL NOT NULL, ...)`
- `FishingActivity.java:46-47`：`PREFS = "lc_fish"` / `KEY_POINTS = "points"`（L248-261 把含 `lat/lng` 的 JSON 写进 SharedPreferences）

**为什么是问题**：收藏点/自建钓点的经纬度属位置数据，随 `allowBackup` 进入 Auto Backup（云端）与 `adb backup` 可导出范围；`getFilesDir()` 与 DB 都在备份集合内。总监优先级②「定位与轨迹的权限与落盘位置」要求可审计，这里没有备份排除规则。
**修法建议**：`android:allowBackup="false"`，或补 `android:fullBackupContent` / `dataExtractionRules` 明确排除 `GapDb` 与 `lc_fish`。

---

### C-07 [低] `handleMapJump` 坐标只判 NaN、不判范围，且来自导出 Activity

**文件:行**：`MainActivity.java:168-177`、`:183-187`

```java
    private void handleMapJump(android.content.Intent intent) {
        if (intent == null) return;
        double lat = MapJump.doubleExtra(intent, MapJump.EXTRA_LAT);
        double lng = MapJump.doubleExtra(intent, MapJump.EXTRA_LNG);
        if (Double.isNaN(lat) || Double.isNaN(lng)) return;
        jumpLat = lat;
        jumpLng = lng;
```
```java
        String code = String.format(java.util.Locale.US, "setView(%.6f,%.6f,%d)",
                jumpLat, jumpLng, MapJump.ZOOM);
```

**为什么是问题**：`MainActivity` 是 `exported=true` 的 launcher，任意 App 可 `am start` 并带 `lc_lat=1e308`；只有 NaN 拦截，越界值会格式化成超长数字串送进 `map.html` 的 `setView`，地图状态被写成非法坐标。不会崩溃，但状态会被污染，且日志/截图里会出现异常坐标。
**修法建议**：与 `FishingActivity.java:68-72` 对齐，`lat∈[-85,85]`、`lng∈[-180,180]`，越界直接丢弃。

---

### C-08 [低] 超清图保存失败仍显示「完成」

**文件:行**：`SuperMapActivity.java:348-366`

```java
    private void saveAndShow(Bitmap bmp, int cached, int total) {
        progressPanel.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        String path = "";
        try {
            File dir = new File(getExternalFilesDir(null), "supermap");
            ...
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_supermap_save_fail, Toast.LENGTH_SHORT).show();
        }
        final String p = path;
        progressText.setText(getString(R.string.lc_supermap_done,
                bmp.getWidth(), bmp.getHeight(), total, cached));
        showResult(bmp, p);
```

**为什么是问题**：保存抛异常时只弹 toast，随后仍写「完成」文案并弹结果框（`path` 为空时标题退回普通标题）。「失败」与「成功」同屏，属总监优先级④「失败态是否误报成功」。
**修法建议**：catch 后 `progressText.setText(R.string.lc_supermap_save_fail)` 并只显示内存图、不声明已保存。

---

### C-09 [低] 网络图片异常路径不关流/不断连

**文件:行**：`GeographicImageryActivity.java:266-273`

```java
                InputStream in = c.getInputStream();
                bmp = BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();
```

**为什么是问题**：`decodeStream` 抛异常时 `in.close()`/`c.disconnect()` 都被跳过，连接与流泄漏；连续切换多张失败图会累积 socket/句柄。
**修法建议**：`try (InputStream in = ...) { ... } finally { c.disconnect(); }`。

---

### C-10 [低] 天地图 key 硬编码随包分发

**文件:行**：`MapKeys.java:12`

```java
    static final String TDT = "dd18858b33229f5837ad1d97b8d3105e";
```

**为什么是问题**：key 随 APK 明文分发、与 `map.html` 共用；注释自认「沿用原版内置 key（归属第三方）」。配额被他用/被回收时，F-06/F-07/F-03 会静默空结果。
**修法建议**：换 LC 自有 key，构建期注入（`BuildConfig`/资源），至少登记配额与归属风险。

---

### C-11 [低] 探索页位图缓存会话内无上限

**文件:行**：`DiscoveryActivity.java:109`、`:264`

```java
    private final Map<String, Bitmap> bmpCache = new HashMap<>();
```
```java
                bmpCache.put(t.title, bmp);
```

**为什么是问题**：只在 `onDestroy` 清理（L331-334）；三个页签逐卡浏览时，位图会一直累积到离开页面，配合 C-02 的全尺寸解码更容易撞 OOM。
**修法建议**：改 LRU（上限 20 张左右）或 `WeakReference`。

---

## 2 优先级逐项结论

| # | 关注点 | 结论 |
|---|---|---|
| ① | **debug-only 导出是否真在 release 关闭** | **通过（实测）**。构建 `assembleRelease`（6,263,460 B）与 `assembleDebug` 两包，`aapt dump xmltree AndroidManifest.xml`：release 下 16 个新页 `exported=0x0`，唯一 `true` 是 `MainActivity`（launcher）；debug 下同为这 16 个页 `exported=0xffffffff`。证据：`E:\APPDetector\evidence\delta-manifest.txt`、`delta-manifest-debug.txt`、`parse_manifest.py` |
| ① | `isDebuggable` 门 | **通过**。`MainActivity.java:334-336` `if (!isDebuggable()) return;` 仍在，`RECEIVER_EXPORTED` 只作 AT 通道（L342-344 注释明确安全边界是门槛） |
| ① | Intent extras 校验 | **部分**。5 个带坐标的新页有 `NaN + ±85/±180` 回退（`FishingActivity:68-72` 等）；`MainActivity.handleMapJump` 只有 NaN（C-07） |
| ② | 隐私：定位/轨迹落盘 | **无外部共享存储写坐标**。截图走 MediaStore 相册（`MainActivity:1514-1526`，D-009 决定）；收藏/钓点在应用私有 DB/prefs（C-06 备份问题另计）；新页未申请新权限，`ACCESS_FINE_LOCATION` 沿用主 manifest |
| ③ | 无门槛关键词 | **不通过（仅用户可见文案）**：strings 5 处命中，见 C-04；代码/注释按总监裁决允许保留事实记录，不计违规 |
| ④ | 正确性：flag 默认/失败态 | flag 默认 `true`（`FeatureFlags.java:36-38`），关闭回退旧行为、无「未开通」文案（通过）；失败态误报见 C-08；空态/Loading 文案各页齐全 |
| ⑤ | 线程与资源 | **网络全在后台**：`LcHttp`/`HttpURLConnection` 调用点均在 `POOL.execute` 或 `new Thread`（`HistoryImageActivity:233`）；WebView 9/10 新页有 `onDestroy`+`destroy`，主页面缺（C-05）；流/连接关闭见 C-09 |

## 3 正向确认（非缺陷，供复核）

- 导入盘面的删除路径会清文件：`CompassListActivity.java:260-261` `new File(item.path).delete();` / `thumb` 同删。
- 收藏点写库用 `ContentValues` 参数化：`GapDb.java:100-110`，无字符串拼 SQL。
- 所有新页 HTTP 都有 8–12 s 超时（`LcHttp:40-41`、`GeographicImagery:267-268`、`SuperMap:416-417`、`WeatherClient:135-136`）。
- `CompassView` F-17：`setDialAlpha` 钳位 0..1（L31-34），只作用位图（L144 `bmp.setAlpha(...)`），读数/指针不受影响；`setArtworkPath` 有 ≤1200 长边采样（L109+），导入盘面不会整图解码。

## 4 覆盖声明

**逐行读完**（10 个新页 + 6 个存储/工具 + 接线）：
`FishingActivity`、`ElevationActivity`、`OverlayActivity`、`DiscoveryActivity`、`MountainActivity`、`WaterActivity`、`HistoryImageActivity`、`GeographicImageryActivity`、`SuperMapActivity`、`WeatherActivity`、`CompassDialStore`、`CompassDisplayStore`、`LcHttp`、`LcExtras`、`MapPoints`、`MapKeys`、`FeatureFlags`、`LcFeatureActivity`、`MapJump`、`CompassView`（F-17 段）、`MainActivity`（新增入口/flag/跳转/截图落盘段）、主/debug `AndroidManifest.xml`。

**部分读**：`GapDb`（表定义 + insert/查询）、`SaveInterestActivity`/`InterestListActivity`（存储路径与表单）、`WeatherClient`（HTTP/降级/缓存段）、`CompassListActivity`（导入/删除段）。

**未覆盖（明确声明）**：`SettingActivity`、`FeatureFlagsActivity`、`ToolsManagerActivity`、`ToolsManagerStore`、`LcPillSwitch`、`LcUi`、`MapSettingsStore`、`WeatherCityTable`、`DebugBridge`、`CompassReadSheet` 的全文；`res/layout*` 与 drawable 二进制；`assets/map.html`（属地图侧）。**F-15 轨迹代码未进 `develop`，按要求下一轮单独补审。**

## 5 复现/证据

```
# 工作区
git worktree add E:/APPDetector/review-delta -b review/delta-client origin/develop   # bfb8492

# 两包 exported 核对
gradle assembleRelease assembleDebug
aapt dump xmltree app-release-unsigned.apk AndroidManifest.xml > delta-manifest.txt
aapt dump xmltree app-debug.apk            AndroidManifest.xml > delta-manifest-debug.txt
python parse_manifest.py
```
产物：release 6,263,460 B；debug 见构建目录（未签名，仅用于 manifest 核对）。
