# M2 Android 客户端审查报告 · 第一批

| 项 | 值 |
|---|---|
| 模块 | M2 Android 客户端，25 文件 1901 行（9 个 .java + Manifest + 5 个 res 文本 + 10 个 drawable） |
| 审查人 | LC审客户端 |
| 基线 | `develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`） |
| 工作区 | `E:\APPDetector\review-base`（只读） |
| 批次 | 第一批：阻断/高 + 全部必查项 + 用户已知缺陷 K-01 专项回答；第二批：中/低（§9）+ 必查缺口（§10）+ 未覆盖文件清单（§11） |
| 条目计数 | 已证实 **20**（阻断 1 / 高 4 / 中 8 / 低 7）；B-01 已撤（误报）· 高度可疑 **1** · 待确认 **2**（第一批 10 + 第二批 10；B-10 按总监裁定由中升高） |
| 文件命名说明 | 已按技术总监裁定（第一批抽样追溯后）改名：**A=M1=`10-M1-desktop.md`**、B=M2=`11-M2-android.md`，与 `98-report-conventions.md` §2 一致。B 编号未变。 |

---

## 1 缺陷清单（已证实）

### B-01 [已撤·误报] 原判「读数行 `String.format` 实参错位」不成立

**撤条原因（保留空号，不得再引用）**：初判时漏数了格式串里「`度数 %.2f°` 之后连续两个 `%s`」中的第二个匿名占位符，误算为 11 个占位符/12 个实参。复核 `MainActivity.java` L640 原文：

```java
                String.format("度数 %.2f°  %s  %s\n地盘 %s山  人盘 %s山  天盘 %s山  坐 %s山\n分金 %s  七十二龙 %s\n六十龙 %s  后天卦 %s  先天卦 %s",
```

其中占位符共 **12** 个（`%.2f` 1 个 + `%s` 11 个：无名 2、地盘/人盘/天盘/坐 4、分金/七十二龙 2、六十龙/后天卦/先天卦 3），与 L641-L647 的 **12** 个实参逐位一一对应，`r.heading → %.2f`、`r.jianText → 第 1 个 %s`、`MOUNTAINS[r.mountain] → 第 2 个 %s`（行首独立的山名）、`MOUNTAINS[r.mountain] → 地盘`、`MOUNTAINS[plate(h+15f)] → 人盘`、`MOUNTAINS[plate(h)] → 天盘`、`MOUNTAINS[r.sitting] → 坐`、`fenjinText → 分金`、`dragon72Text → 七十二龙`、`dragon60Text → 六十龙`、`houtianText → 后天卦`、`xiantianText → 先天卦`。**该行无缺陷。**

教训（供成文时不再复发）：连续匿名 `%s` 必须按出现次序数，不能按「字段个数」数。

---

### B-02 [阻断] `DebugBridge` 用 `RECEIVER_EXPORTED` 注册、无权限无签名无 `debuggable` 门槛：任意第三方 App 可让宿主执行任意 JS / 加载任意 URL

**文件行号**
- `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L234-L250（注册）、L167-L232（命令分发：`url` L184-L191、`js` L192-L206）、L369-L393（WebView 能力）
- `app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java` L22、L34-L41
- `app/android/app/src/main/AndroidManifest.xml` L1-L30（无 `receiver` 声明、无自定义 `permission`）
- `app/android/app/build.gradle` L12（`targetSdk 34`）

**代码片段（逐字）**

`MainActivity.java` L237-L243：

```java
            debugBridge = new DebugBridge(this::handleDebugCommand);
            android.content.IntentFilter f = new android.content.IntentFilter(DebugBridge.ACTION);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(debugBridge, f, android.content.Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(debugBridge, f);
            }
```

`MainActivity.java` L184-L191（`url` 命令，任意 URL 直接灌进两个 WebView）：

```java
            case "url": {
                String url = it.getStringExtra("value");
                if (url != null) {
                    if (mapView != null) mapView.loadUrl(url);
                    if (compassMapView != null) compassMapView.loadUrl(url);
                }
                break;
            }
```

`MainActivity.java` L192-L199（`js` 命令，任意脚本直接求值）：

```java
            case "js": {
                String code = it.getStringExtra("value");
                if (code != null) {
                    // ValueCallback -> logcat, so "did the injection run" is observable.
                    if (mapView != null) {
                        mapView.evaluateJavascript(code,
                                v -> android.util.Log.d("LCJS", "mapView -> " + v));
                    }
```

`MainActivity.java` L373-L383（WebView 同时具备 JS / 本地文件 / JS 桥）：

```java
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(true);
        webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
            if ("pick".equals(type)) {
                mapMarker = marker;
            } else {
                mapMarker = null;
            }
        }), "LCAndroid");
```

`DebugBridge.java` L22：

```java
    public static final String ACTION = "com.luopan.fengshui.DEBUG";
```

`MainActivity.java` L711-L714（无门槛：只要 Activity 在前台就注册，release 包同样注册）：

```java
    @Override
    protected void onResume() {
        super.onResume();
        registerDebugBridge();
```

**触发条件**
任意安装在同机的第三方 App（无需系统权限）执行
`sendBroadcast(new Intent("com.luopan.fengshui.DEBUG").putExtra("cmd","js").putExtra("value","<任意 JS>"))`，
或 `putExtra("cmd","url").putExtra("value","file:///data/data/com.luopan.fengshui/...")`；前提仅是本 App 处于 `onResume`（`receiverRegistered = true`，L244）。`targetSdk 34` 下 L240 显式要求 `RECEIVER_EXPORTED` 才允许接收外部广播——代码主动选择了「对外导出」；AndroidManifest.xml 全文（L1-L30）未声明任何自定义 `permission`，Receiver 也不在清单里，无法靠 `android:permission` 限制发送方。

**预期 vs 实际**
- 预期：调试通道只对 `adb`/本机调试可用——即受 `ApplicationInfo.FLAG_DEBUGGABLE` 约束（同文件 L693-L695 已有 `isDebuggable()` 实现，L664 的 `LCRING` 日志就用了它），或改用 `RECEIVER_NOT_EXPORTED` + `am broadcast -n <pkg>/.DebugReceiver` 定向投递，或在清单里给该 action 定义 `signature` 级 `permission`。自用场景的最低要求是「非 debuggable 包内不注册」。
- 实际：release 包同样注册且对外导出；`js` 命令把任意字符串送进两个已开启 JS、且 `setAllowFileAccess(true)`（L375）、且挂了 `@JavascriptInterface`（L377-L383）的 WebView。攻击者由此可在宿主 WebView 内执行任意脚本、把页面导航到任意 URL（含 `file://`），并读取 JS 桥回传（`LCAndroid.onEvent` 只做坐标校验，无鉴权，`LCAndroid.java` L29-L65）。

**证据等级**：已证实（注册标志、命令分发、WebView 能力、清单缺省权限四段代码闭合；不需要真机即可判定「任意 App 可触发」）。

**修复方向**
1. `registerDebugBridge()` 首行加 `if (!isDebuggable()) return;`，release 包彻底不注册；
2. SDK≥33 分支改 `RECEIVER_NOT_EXPORTED`（`adb` 侧改用 `am broadcast -n com.luopan.fengshui/.MainActivity` 或增加 `exported` 的 debug-only receiver 由 `debug` 源集提供）；
3. 保留 `heading/accuracy/tilt/declination/reset/capture`，**删除 `url` 与 `js` 两个命令**（AT 需要注入的只有数值与截图）；如必须保留，仅接受 `file:///android_asset/` 前缀的 URL 并拒绝以 `javascript:` 开头的 payload；
4. `settings.setAllowFileAccess(false)`（`map.html` 走 `file:///android_asset/`，`allowFileAccess` 对它不是必需——`loadUrl("file:///android_asset/map.html")` 走 asset loader），并在 `addJavascriptInterface` 上把接口白名单化。

---

### B-03 [高] 三 Tab 造成同一页面两份实例：「地图」Tab 与「罗盘」Tab 的底图是同一个 `map.html` 的两个 WebView，读数每次刷新向两份实例各注 2 条 JS

**文件行号**：`MainActivity.java` L79-L90、L262-L266、L567-L569、L682-L686；`app/android/app/src/main/assets/map.html` L1168-L1182（实例内的渲染循环）

**代码片段（逐字）**

`MainActivity.java` L79-L90：

```java
        compassTab = buildCompassTab();
        mapView = buildMapTab();
        knowledgeTab = buildKnowledgeTab();

        content.addView(compassTab);
        content.addView(mapView);
        content.addView(knowledgeTab);

        tabCompass.setOnClickListener(v -> showTab(0));
        tabMap.setOnClickListener(v -> showTab(1));
        tabKnowledge.setOnClickListener(v -> showTab(2));
        showTab(0);
```

`MainActivity.java` L262-L265：

```java
        // Base map (底图). Keep the reference: 轮 C 截图与坐向推送都指向这个 WebView.
        compassMapView = createMapWebView();
        frame.addView(compassMapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
```

`MainActivity.java` L567-L569：

```java
    private WebView buildMapTab() {
        return createMapWebView();
    }
```

`MainActivity.java` L682-L686：

```java
        for (WebView wv : new WebView[]{compassMapView, mapView}) {
            if (wv == null) continue;
            wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
            wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
```

`map.html` L1168-L1172（每个 WebView 实例各跑一份循环）：

```javascript
function startLoop() {
  if (rafId) return;
  fpsT = 0; fpsFrames = 0;
  rafId = requestAnimationFrame(loop);
}
```

**触发条件**：应用启动即成立（`onCreate` L79-L85 一次性建三页并全部 `addView`）；此后每次传感器回调都会执行 L682-L686。

**预期 vs 实际**
- 预期：同一时刻只存在一个 `map.html` 实例。依据（档案，转述）：原版只有 `MapWebView` 一个地图容器（`gap-matrix.md` L128 F1 的「WebView 地图：`MapWebView`/`MapWebInterface`(33)/`MapWebController`(26)/`MapWebApiKt`(84)」），且罗盘页控件清单里「地图」是页面内按钮（`gap-matrix.md` L121），即原版罗盘与地图同页同一实例。该档案的可回溯性见 §5 B-Q02。
- 实际：两个 WebView 各自 `loadUrl("file:///android_asset/map.html")`（`createMapWebView()` L391），各自持有瓦片缓存、WebGL 上下文、DEM 请求与 rAF 回调；`updateReading` 在每次传感器事件里对两份实例各发 2 条 `evaluateJavascript`。稳态注入量为**推算值，未实测**：假设采样率取 `SensorManager.SENSOR_DELAY_UI` 的官方定义 60,000 µs（约 16.7 Hz），则 16.7 × 2 WebView × 2 调用 ≈ 67 次 JS 求值/秒，另加同频率的 `setText`（L639）。**本数字只在本报告出现这一处**，M3/成文若引用须标注「引自 B-03，推算值，未实测」并复用同一假设，不得另算一套频率。`onSensorChanged` 只判 `injectedHeading != null`（L737），没有任何节流；同文件里 `LCRING` 日志都做了 500 ms 节流（L664），JS 侧没有。

**证据等级**：已证实（两个实例、两处 `loadUrl`、循环体、注入路径均为代码直接给出）；频率数字为**推算值，未实测**（依据 `SensorManager.SENSOR_DELAY_UI` 官方常量 60,000 µs，本机未装 SDK 源码未复核该常量；真机实测由 LC审测试 补，见 §10）。

**修复方向**
删除 `mapView`/`buildMapTab()` 与「地图」Tab，只保留 `compassMapView` 一个实例（K-01 的落地动作，见 §2）；`updateReading` 的 JS 注入改为仅在 heading 变化超过阈值（如 0.5°）或 100 ms 节流时发送，并用单次 `evaluateJavascript` 合并 `setHeading`+`setSitFace`。

---

### B-04 [高] 罗盘浮层不在「地图中心」：贴顶部、占屏宽 78%、整块吞掉触摸，且盖住 `map.html` 自己的控件列——「地图」Tab 正是因为这一层不可用才存在

**文件行号**：`MainActivity.java` L267-L274；`CompassView.java` L126-L154；`map.html` L13（控件列位置）、L26-L27（顶部朝向标签）

**代码片段（逐字）**

`MainActivity.java` L267-L274：

```java
        // Floating compass overlay (罗盘盖在底图上).
        compassView = new CompassView(this);
        compassView.setOnHeadingChangeListener(h -> updateReading());
        int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        frame.addView(compassView, cp);
```

`CompassView.java` L130-L135：

```java
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                followSensor = false;
                lastAngle = (float) Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
                return true;
```

`map.html` L13：

```css
  #controls { position:absolute; left:10px; top:10px; z-index:10; }
```

**触发条件**：进入罗盘页（默认页，`MainActivity.java` L90）；以 1080×2340、`density = 3.0` 的机型为例（数值为推演，非实测）。

**预期 vs 实际**
- 预期（K-01 口径）：罗盘浮层叠在**地图中心**，中心点与地图中心重合，圆外区域的地图手势（拖动/缩放/pick）可用，底图控件可点。
- 实际：`gravity = TOP | CENTER_HORIZONTAL` + `topMargin = 16dp`（L272-L273），`csize = 0.78 × widthPixels`（L270）。推演：`csize = 842 px`，罗盘占据 `x ∈ [119, 961]`、`y ∈ [48, 890]`，圆心 `y ≈ 469`，而屏幕中心 `y = 1170` —— 圆心比地图中心高 701 px（约 30% 屏高）；同时 78% 的屏宽被圆盘覆盖。`map.html` 的控件列在 `left:10px / top:10px`、按钮宽 `92px`（L13-L15），顶部朝向标签在 `top:8px` 居中（L26-L27），全部落进罗盘覆盖区，`FrameLayout` 会把触摸先给上层子 View：`CompassView.onTouchEvent` 在 `ACTION_DOWN` 无条件 `return true`（L131-L135），既不判断是否落在圆内，也不做任何 `requestDisallowInterceptTouchEvent`/穿透处理 → 罗盘方块范围内底图完全不可操作（含 `#controls` 的 18 个按钮、拖动平移、双击缩放、pick）。
- 附带的透明度问题：`CompassView.onDraw` 先用 `Color.argb(210, 24, 20, 16)` 铺满整圆（`CompassView.java` L163、L167-L169），82% 不透明的黑底直接把底图遮死，「罗盘浮在地图上」在视觉上也未成立。

**证据等级**：已证实（布局参数、触摸返回值、控件坐标均为代码事实；像素数值为按 density=3.0 的推演，已在文中标注）。

**修复方向**
`cp.gravity` 改 `Gravity.CENTER`（或按地图视口中心动态定位），`csize` 降到屏宽 0.55–0.65 并允许地图在手势时半透明化；`CompassView.onTouchEvent` 先判 `hypot(x−cx, y−cy) <= r`，圆外返回 `false` 让事件落到 WebView；`onDraw` 的圆底 alpha 从 210 降到 ≤80 或直接不铺底（`map.html` 自带底色 `#181410`）。`#controls` 列改由原生侧复用（见 §2 修复方向第 4 步），不要指望 HTML 浮在罗盘下面还能点。

---

### B-05 [高] 罗盘上任意点按（无需拖动）即 `followSensor = false`，传感器跟随被永久关闭，只有「复位朝北」能恢复

**文件行号**：`CompassView.java` L99-L104（守卫）、L130-L135（`ACTION_DOWN` 置位）、L106-L118（仅 `reset()` 恢复）

**代码片段（逐字）**

`CompassView.java` L99-L104：

```java
    public void setSensorHeading(float azimuth) {
        if (followSensor) {
            heading = azimuth;
            invalidate();
        }
    }
```

`CompassView.java` L106-L118：

```java
    public void reset() {
        followSensor = true;
        heading = 0f;
        invalidate();
        notifyHeadingChanged();
    }

    public void setHeadingManually(float h) {
        followSensor = false;
        heading = normalize(h);
        invalidate();
        notifyHeadingChanged();
    }
```

**触发条件**：手指按下罗盘区域（`ACTION_DOWN`，L131-L133）后抬起、不产生移动（`ACTION_UP` L148-L151 只清 `dragging`，不清 `followSensor`）。罗盘矩形覆盖屏宽 78%、屏高约 36%（B-04 的推演：`y ∈ [48,890]`，屏高 2340），单手持机时拇指落点极易在圆内；更常见的路径是用户想拖动地图（底图在浮层下方不可拖，见 B-04）而第一下点在罗盘上——一按即永久退出跟随。

**预期 vs 实际**
- 预期：只有「拖动」才切到手动模式；单击（无位移）不应影响跟随。「锁定朝向」在原版是**独立控件**（`docs/gap-matrix.md` L121 的 E6 真机控件清单：左列含「罗盘锁定」，右列「坐向朝北」是另一个双标签开关，两者分开）；原版清单里**没有「翻坐向（转 180°）」按钮**，因此基线自创的这个按钮不应当兼任「冻结传感器」——它若保留，必须做成**持久偏移量**（`CompassView` 内 `heading = azimuth + offset`，`followSensor` 保持 `true`），与 `map.html` L342-L346 的 `offDeg` 同构。
- 实际：`ACTION_DOWN` 就把 `followSensor` 置 `false`（L133），此后 `setSensorHeading()` 全部落空（L100-L102），罗盘永久冻结在按下的那一刻朝向；`MainActivity.handleDebugCommand` 的 `heading` 注入（L170-L175）同样失效（因为它也走 `setSensorHeading`），AT 脚本会静默失败。`notifyHeadingChanged()` 在 DOWN 时不触发，读数行不会更新，用户也看不到任何「已切手动」的提示（`statusView` 只显示口径/精度/倾斜，L650-L661）。同一条冻结路径也被「翻坐向」按钮使用（`MainActivity.java` L314-L317 → `CompassView.setHeadingManually` L113-L118），点一次「翻坐向」后传感器跟随同样永久关闭，盘面与地图一起停在旧读数。

**证据等级**：已证实（状态位置位与守卫条件闭合）。

**定性纠偏（按 `90-verify.md` §11.2 单点复核，2026-11-05）**：本条主缺陷（`ACTION_DOWN` 即冻结、只有 `reset()` 能恢复）不变、仍为 **高**。原稿「`setHeadingManually` 才是显式退出跟随的入口」一句已删——该判断不成立：`setHeadingManually` 是「翻坐向」的实现，而原版把「冻结传感器」的职责归给独立的「罗盘锁定」。原版出处：`docs/gap-matrix.md` L132（F5）记 `CompassToolsKt.n0`（`CompassToolsKt.smali:10531/10542`，`controller.g;->x()Z` 真取「坐北朝南」、假取「坐南朝北」），同段明确两态几何**未取证**。**边界说明**：`map.html` L342-L346 的 `offDeg` 只能证明「180° 翻转可以保持跟随」在该实现内自洽，不能据此宣称原版 `g.x()Z` 的极性已 1:1。

**修复方向**
`ACTION_DOWN` 只记 `lastAngle`，不置 `followSensor`；在 `ACTION_MOVE` 且累计位移超过 `ViewConfiguration.getScaledTouchSlop()` 时才 `followSensor = false`。**「翻坐向」改为 `CompassView` 内的持久偏移量**（新增 `setHeadingOffset(float deg)`，`setSensorHeading(az)` 内部用 `heading = normalize(az + offset)`），`followSensor` 保持 `true`，不得再走 `setHeadingManually`；「锁定朝向」另做独立控件（`followSensor=false` 只能在锁定动作里置位），并在 `statusView` 增加「锁定/手动」态文字（`MainActivity.java` L650-L661）以便用户与 AT 判断当前模式。

---

### B-06 [中] 前后台切换不暂停 WebView、不销毁 WebView：`onPause` 只停传感器与广播，两个 `map.html` 实例的后台计时器/rAF 继续跑，且 `onDestroy` 缺失

**文件行号**：`MainActivity.java` L725-L732、全文无 `onDestroy`（文件止于 L767）

**代码片段（逐字）**

`MainActivity.java` L725-L732：

```java
    @Override
    protected void onPause() {
        unregisterDebugBridge();
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
```

**触发条件**：按 Home 键 / 切到其它 App（`onPause` 触发）；Activity 真正结束时（返回键退出、`finish()`）。

**预期 vs 实际**
- 预期：`onPause` 调 `WebView.onPause()` + `WebView.pauseTimers()`（或 `stopLoading()`），`onDestroy` 调 `destroy()` 释放 native WebView；这是 Android WebView 的官方生命周期要求，也是「后台不再跑页面脚本」的唯一受控手段。
- 实际：两处都没有。`map.html` 的渲染循环 `rafId = requestAnimationFrame(loop)`（`map.html` L1168-L1182）在 WebView 存活期间持续注册；两个 WebView 实例（B-03）在 `onCreate` 创建后从未 `destroy()`，Activity 结束时 Context 被 WebView 持有。`onDestroy` 在整个文件里不存在（L1-L767，最后一个方法是 `onBackPressed` L760-L766）。

**证据等级**：已证实（缺失调用可用全文核对；渲染循环由 `map.html` L1168-L1182 给出）。

**修复方向**
`onPause()` 末尾加 `for (WebView wv : new WebView[]{compassMapView, mapView}) { if (wv != null) wv.onPause(); }` 并按需 `webView.pauseTimers()`；新增 `onDestroy()` 调 `removeJavascriptInterface("LCAndroid")` + `destroy()`，并置 `compassMapView = mapView = null`。B-03 落地成单实例后此项只剩一处。

---

### B-07 [中] `configChanges="orientation|screenSize"` 让 Activity 不重建，但罗盘尺寸只在 `onCreate` 按当次 `widthPixels` 算一次、无 `onConfigurationChanged`，横屏后罗盘尺寸与位置错误

**文件行号**：`app/android/app/src/main/AndroidManifest.xml` L19-L22；`MainActivity.java` L270-L274

**代码片段（逐字）**

`AndroidManifest.xml` L19-L22：

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden">
```

`MainActivity.java` L270-L273（只在 `buildCompassTab()` 内执行一次）：

```java
        int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
```

**触发条件**：设备旋转到横屏（`configChanges` 已声明 → 不重建、不重跑 `onCreate`，`buildCompassTab()` 不会再次执行）。

**预期 vs 实际**
- 预期：横屏后罗盘按新的 `widthPixels`（例如 2340）重算为 0.78×新宽，或者显式保持竖屏（`android:screenOrientation="portrait"`）。
- 实际：`csize` 仍为竖屏算出的 842 px，占横屏宽度（2340）的 36%，且顶部/居中参数不变；`MainActivity` 全文无 `onConfigurationChanged` 覆写，无任何重算路径 → 横屏下罗盘偏小、底图可视区与点击区错位。`CompassView` 自身在 `onSizeChanged` 里也没有重算需求（它按 `getWidth()/2` 自适应，故只有外层 `LayoutParams` 是错的）。

**证据等级**：已证实（清单声明 + 唯一计算点 + 无重算入口）。

**修复方向**
二选一：删掉 `configChanges` 里的 `orientation|screenSize`（让系统重建，配合 B-06 的销毁逻辑），或覆写 `onConfigurationChanged()` 重新设置 `compassView.setLayoutParams(new FrameLayout.LayoutParams(newCsize, newCsize))`。

---

### B-08 [中] 没有 rotation vector 传感器时零提示：读数行永远空白、盘心恒显示「0.0° 子山」，与「无传感器」不可区分；倾斜提示也只在注入态才可能出现

**文件行号**：`MainActivity.java` L92-L95、L720-L722、L636-L639、L650-L655、L734-L751

**代码片段（逐字）**

`MainActivity.java` L92-L95：

```java
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
```

`MainActivity.java` L720-L722：

```java
        if (sensorManager != null && rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
```

`MainActivity.java` L652-L655：

```java
            float tilt = 0f;
            if (injectedPitch != null) tilt = SensorMath.tiltDegrees(injectedPitch, injectedRoll);
            String accText = acc >= 3 ? "" : (acc == 2 ? " · 精度中" : " · 精度低");
            String tiltText = tilt > 8f ? String.format(java.util.Locale.US, " · 请端平(%.0f°)", tilt) : "";
```

**触发条件**
1. 设备无 `TYPE_ROTATION_VECTOR`（模拟器、部分平板、部分定制 ROM），`rotationVector == null` → L720 不注册；`readingView` 只在 L639 被赋值，故读数行永远空白（初始无文本，L301-L307 未 `setText`）。
2. 真机持有：`onSensorChanged` 只取 `orientation[0]`（L741），`orientation[1]/[2]` 被丢弃 → `injectedPitch` 恒为 `null` → L653 的 `tilt` 恒为 0 → 「请端平」提示在真实使用中永不出现。

**预期 vs 实际**
- 预期：无传感器时给明确降级提示（例如「本机无方向传感器，请用拖动/读数」）并切到手动模式；倾斜指示从真实 sensor 的 pitch/roll 计算，`SensorMath.tiltDegrees` 已有现成实现（`SensorMath.java` L26-L30）。
- 实际：无提示、无降级，界面显示一个看似有效的「0.0° 子山」读数；倾斜检测仅对 adb 注入生效，真机不可用。

**证据等级**：已证实（两条路径都由代码直接给出）。

**修复方向**
`onResume` 的 `else` 分支把 `statusView` 置为「无方向传感器」并对 `readingView` 写占位文案；`onSensorChanged` 用 `orientation[1]/[2]`（弧度）经 `tiltDegrees(degrees, degrees)` 计算真实倾斜，替换 L653 的注入专用逻辑。

---

### B-09 [中 · 母条] 百二十分金 / 穿山七十二龙 刻度同样忽略 `offset`，刻度落在数据格中心而非边界（桌面侧 A-04 交叉引用）

> 技术总监 2026-11-05 裁定：双端同源缺陷母条归实现模块（B），A-04 转交叉引用。两侧同为「中」，合并后级别不变；两端改法相同（刻度起点用 `RINGS[n].offset`）。

**文件行号**：`CompassView.java` L198-L209（刻度）、L303-L313（格中心高亮）；`CompassData.java` L117-L118（步长常量）、L100（`TYPE_6 offset 1.5`）、L99（`TYPE_5 offset 2.5`）

**代码片段（逐字）**

`CompassView.java` L198-L209：

```java
        for (int d = 0; d < 360; d += (int) CompassData.FENJIN_STEP) {
            float[] p0 = pos(cx, cy, r * 0.845f, d, heading);
            float[] p1 = pos(cx, cy, r * 0.825f, d, heading);
            paint.setColor(Color.rgb(120, 100, 70));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        for (int d = 0; d < 360; d += (int) CompassData.DRAGON72_STEP) {
            float[] p0 = pos(cx, cy, r * 0.805f, d, heading);
            float[] p1 = pos(cx, cy, r * 0.785f, d, heading);
            paint.setColor(Color.rgb(110, 130, 90));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
```

`CompassData.java` L117-L118：

```java
    static final float FENJIN_STEP = 3f;
    static final float DRAGON72_STEP = 5f;
```

**触发条件（范围已核对，勿夸大）**：仅在**矢量绘制分支**成立，即 `artwork == null`（`CompassView.java` L175-L177 的 `else` 分支）且 `dialType > 0`（L197；默认值来自 `MainActivity.java` L716-L718 读 prefs，缺省 1）。`artwork == null` 有两类可达状态：① `onResume` 尚未执行 `setArtwork()` 之前的最初几帧（`MainActivity.java` L715-L719）；② `setArtwork()` 内 `BitmapFactory.decodeResource` 抛异常被 `catch (Throwable)` 吞掉后 `artwork = null`（`CompassView.java` L38-L49）——`res[dial]` 越界（prefs 里 `dial_index` 被写成 0..4 之外）也走此路。artwork 正常加载时走 `drawArtwork()`（L175-L176），本缺陷不显示。

**预期 vs 实际**
- 预期：刻度画在表定义格边界 `ring.offset + k*interval`（分金 `1.5+3k`、72 龙 `2.5+5k`），与 `CompassData.ringIndex()`（L252-L256）的格划分同源。
- 实际：从 0° 起按 3°/5° 均分；而格中心 = `offset+(i+0.5)*interval` 恰为 3 的倍数/5 的倍数（分金 `1.5+1.5+3i = 3+3i`），即**每根刻度都落在格中心**，格边界无刻度；`drawCellMarker` 又画在格中心（L305-L307）→ 高亮线与刻度同角度重叠，读分金/72 龙错半格。

**证据等级**：已证实（角度算术由常量直接给出）。

**修复方向**
同 A-04：两处循环起点改为 `CompassData.RINGS[5].offset` / `CompassData.RINGS[4].offset`（或统一 `for (int k = 0; k < n; k++) { float d = ring.offset + k * ring.interval; }`），禁止再用从 0 起的字面量。

---

### B-10 [高 · 母条] 「正/兼向」双源：盘心用公式 `Reading.jian`，读数行用表 `jianText`，同一朝向同屏两个答案（桌面侧为 A-03，交叉引用）

**文件行号**：`CompassView.java` L298-L299（盘心）、`MainActivity.java` L641（读数行）、`CompassData.java` L229（表）、L233-L238（公式）

**代码片段（逐字）**

`CompassView.java` L298-L299：

```java
        drawText(canvas, rd.jian + " · " + CompassData.YINYANG[rd.mountain],
                cx, cy + 34f * density(), textPaint);
```

`CompassData.java` L229：

```java
            this.jianText = ringText(14, h);
```

`CompassData.java` L233-L238：

```java
            if (Math.abs(offset) * 2f <= ZHENG_XIANG_DEG) {
                this.jian = "正向";
            } else {
                int n = offset < 0 ? (m + 23) % 24 : (m + 1) % 24;
                this.jian = "兼向" + MOUNTAINS[n];
            }
```

**触发条件**：任意朝向。样例 `heading = 0.00°`：
- 盘心：`offset = 0` → `jian = 正向`（L233-L234）→ 显示「正向 · 阴」。
- 读数行第 2 个 `%s`：`jianText = ringText(14, 0)` → `RINGS[13].labels[ringIndex(14,0)]` → `labels[0]` = 「癸山九度外」（`CompassData.java` L108 头 + L252-L256）→ 显示「度数 0.00°  癸山九度外」。
- 同一读数行第 3 位「地盘」显示「子山」（`MainActivity.java` L641）。

**预期 vs 实际**
- 预期：同一朝向只有一个正/兼结论，且与地盘山名同侧。
- 实际：同屏「子山 + 正向」与「癸山九度外」并存；`TYPE_14`（offset 0）整体比地盘（`TYPE_4` offset 7.5，`CompassData.java` L98）前移 7.5°，`h = 3°` 时表给「癸山九度内」而地盘是子山。

**证据等级**：已证实（两条取值路径与数值都在代码中）。哪一侧是原版口径 → 本报告 §5 的 A-Q02（与桌面共用）。

**定级说明（按技术总监裁定 2026-11-05）**：本条为母条，取高——「双盘读数矛盾直接输出错读数，属读数错误」。交叉引用侧 A-03（M1）同为「高」、F-03（M5）按其母条标注，三处定级一致。

**修复方向**
与 A-03 同一裁决：确定唯一来源后，或让 `CompassView` L298-L299 改用 `rd.jianText`、或修正 `TYPE_14` 的 `offset` 后让 `MainActivity` L641 改用 `rd.jian`；`tests/numeric/java/CompassProbe.java` L40-L41 目前只导公式侧（`r.jian`），需同时导出 `jianText` 以便护栏。

---

### B-11 [中] 双 WebView 之间没有任何状态同步：同一「地图」在两页各有独立的中心/缩放/底图源/坐向状态

**文件行号**：`MainActivity.java` L262-L266、L567-L569、L682-L686

**代码片段（逐字）**

`MainActivity.java` L682-L686：

```java
        for (WebView wv : new WebView[]{compassMapView, mapView}) {
            if (wv == null) continue;
            wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
            wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
```

**触发条件**：在「地图」Tab 里拖动/缩放/切底图源/进 3D（`map.html` 的 `pan/zoom/setBase/toggle3D` 全在页面内 JS 状态，`map.html` L424-L429 等），再切回「罗盘」Tab。

**预期 vs 实际**
- 预期：同一个地图页只有一个地图状态（中心、缩放、底图、模式），切换视图不改变内容。
- 实际：两个 `map.html` 实例各自持有 `center/zoom/base/mode/bearing` 等全局变量（`map.html` L92-L100），`updateReading` 只同步 `heading` 与 `sitFace` 两项（L684-L685），其余状态永不同步：用户在地图 Tab 平移到的位置，回罗盘页后底图仍是北京默认视角（`map.html` L99-L100），反之亦然。用户看到的是「同一功能两个不一致的地图」。

**证据等级**：已证实（两个实例 + 仅两键同步）。

**修复方向**：B-03 单实例化后本缺陷自动消失；若保留双实例，必须新增 `getViewState()/setViewState()` 桥并在 `showTab` 时双向同步。

---

## 2 已知缺陷 K-01 专项回答（用户提出 · 必查）

> 原话：底部菜单里「罗盘」与「地图」其实是同一个页面的不同表现：参照原 APK 首页布局，罗盘应是点击「罗盘」按钮后浮动叠加在地图中心，配合地图模拟真实罗盘场景；整个底部菜单栏没有存在意义。要遮挡页面的组件与子页，应依原 APK 的设计逻辑放在合适位置、用合适的呈现方式。

### 2.1 现状（基线代码事实）

| 事实 | 证据 |
|---|---|
| 底部固定三按钮「罗盘 / 地图 / 知识」，常驻布局 | `res/layout/activity_main.xml` L14-L49（`bottom_bar` + `tab_compass` L23-L30 / `tab_map` L32-L39 / `tab_knowledge` L41-L48）；文案 `res/values/strings.xml` L4-L6 |
| 三页在 `onCreate` 一次性建好并全部 `addView`，默认第 0 页 | `MainActivity.java` L79-L90 |
| 切页 = 三选一 `setVisibility`，罗盘页与地图页互斥 | `MainActivity.java` L630-L634 |
| 罗盘页**已经**是「底图 WebView + 罗盘浮层 + 底部面板」三层 | `MainActivity.java` L262-L266（底图）、L267-L274（浮层）、L360-L363（面板 `Gravity.BOTTOM`） |
| 「地图」页就是同一个 `createMapWebView()`，仅少了浮层 | `MainActivity.java` L567-L569 + L391 |
| 罗盘状态推送两份 WebView | `MainActivity.java` L682-L686 |
| 浮层贴顶非居中、圆底 82% 不透明、吞掉全部触摸 | `MainActivity.java` L270-L274；`CompassView.java` L163、L130-L135 |

**结论**：两个 Tab 是同一个页面的两种裁剪；「地图」Tab 的唯一增量价值是「没有被罗盘浮层挡住的地图」——这恰好证明罗盘浮层的层叠方式错了（浮层把底图与底图控件一起挡死，见 B-04），而不是证明需要第二个页面。知识页（`buildKnowledgeTab()` L571-L605，8 条静态条目 + 2 条目录清单）是内容型子页，同样不需要占用底部导航位。

### 2.2 与原版差在哪（档案引用）

- `docs/gap-matrix.md` L121（E6）记录的原版罗盘页控件清点：左列 截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地，右列 **地图**/实景/坐向朝北开关/指针开关，底栏 切换/旋转/缩放/透明度/天支线。即「地图」在原版是**页面内的一个按钮/开关**，不是底部导航项；页面本身是地图 + 罗盘叠加。
- `docs/MECHANICS.md` L466-L468（**我未直接读该文件**，以下为 `98-report-conventions.md` §6.2(b) 的引文转述）给出原版左列按钮的真机坐标（1228×2700，按钮中心 x≈132），说明原版控件是**浮动在整屏页面上的按钮列**，而不是把页面切成若干 Tab。
- 待我确认的部分（不写进定论）：上述两条均为档案转述，原始真机截图位于 `.inspect` 引用链，本审查按纪律未打开 `E:\APPDetector\output\.inspect`；本组 `LC审文档` 正在核 `gap-matrix.md`/`MECHANICS.md` 的档案可回溯性（见 §5 待确认 B-Q02）。**代码侧结论不依赖该档案**：无论原版如何，「两 Tab 同页 + 浮层把底图挡死」在工程上都不成立。

### 2.3 与基线缺陷条目的关系与定级（本审查建议）

| 项 | 内容 |
|---|---|
| 本报告对应母条 | **B-03**（三 Tab = 同页两份实例）与 **B-04**（浮层不在图心、挡死底图与控件、吞触摸）；K-01 引用 B-03/B-04，不重复计数 |
| 定级建议 | **高**（K-01 母条进正式缺陷清单）；拆分后的两项独立定级：B-03 高、B-04 高 |
| 为什么不是阻断 | 罗盘读数主路径仍可出数（读数行、盘心、读盘弹层三处取值均正确），K-01 是信息架构与叠加方式错误，不产生数据损坏 |
| 为什么不是中 | 主路径交互错误：底图手势/控件在默认页完全不可用，功能重复导致内存与注入翻倍（B-03） |

### 2.4 修复方向（动作式，按落地顺序）

1. `res/layout/activity_main.xml` L14-L49：删除整个 `bottom_bar` 与其三个 `Button`；`strings.xml` L4-L6 三个 `tab_*` 字符串改为按钮文案（`compass_toggle`/`knowledge`）。
2. `MainActivity.java` L79-L90：只 `buildCompassTab()` 一次（更名为 `buildMainPage()`），删除 `mapView` 字段（L30）、`buildMapTab()`（L567-L569）、`showTab()`（L630-L634）及其三处点击绑定（L87-L89）。
3. `MainActivity.java` L267-L274：`cp.gravity = Gravity.CENTER`；`csize` 改屏宽 0.60 左右；新增 `compassView.setVisibility(GONE)` 初始态，由「罗盘」按钮切换（对应原版右列「地图/罗盘」的按钮语义）。
4. 底图控件（`map.html` L49-L69 的 `#controls` 18 个按钮）在罗盘浮层显示时用原生按钮列承载（左列截屏/等高线/罗盘复位/贴地、右列 地图/实景/坐向/指针，位置按 `MECHANICS.md` L466-L468 的坐标比例，该坐标为转述引用、原文件我未读），HTML 控件列在原生模式下用 `document.getElementById('controls').style.display='none'` 隐藏，避免被浮层压住。
5. 知识页改为「读数行/圈层文字点击 → 对应条目」的弹层（对齐 `gap-matrix.md` L97 的 C2：原版是点圈层文字直达），不再占导航位。
6. 验收判据（建议写进 AT）：① APK 内 `activity_main.xml` 无 `bottom_bar`；② `MainActivity` 只有 1 个 `WebView` 实例（`grep -c "createMapWebView()" == 1`）；③ 罗盘浮层中心与底图视口中心偏差 ≤ 2 px（`uiautomator dump` 取两者 bounds）；④ 罗盘圆外 50 px 处拖动可平移地图（`input swipe` 后 `center` 变化）。

---

## 3 模块必查项逐项结论

| # | 必查项 | 结论 | 证据 |
|---|---|---|---|
| ① | 三盘读数与圈层自洽（`plate()` vs 圈层角度） | **命中**：双端各自自洽，但两处不自洽——分金/72 龙刻度 vs 数据格（B-09）、正/兼向双源（B-10） | `CompassView.java` L234-L251、`MainActivity.java` L697-L703；`CompassData.java` L209-L238、L252-L262 |
| ② | 屏幕旋转/前后台切换时传感器与 WebView 泄漏 | **命中**：WebView 不 pause/destroy（B-06）、旋转不重算尺寸（B-07）、无传感器静默（B-08）；**传感器本身无泄漏**（`onResume` 注册 / `onPause` 注销成对，`MainActivity.java` L720-L731；广播接收器有 `receiverRegistered` 幂等守卫 L235、L253-L256） | B-06 / B-07 / B-08 |
| ③ | `DebugBridge` 广播是否需要签名/权限保护（任意 App 可触发？） | **命中，且可被任意 App 触发** | B-02 |
| ④ | 双 WebView 状态污染 | **命中**：两份实例 + 仅同步 heading/sitFace | B-03 / B-11 |
| ⑤ | 罗盘叠加在底图上的层级/透明度/触摸穿透是否符合原版 | **命中**：层级正确（底图 L264 → 浮层 L274 → 面板 L363），但位置、透明度、触摸穿透三项均不符合（B-04）；另有点按即停跟随（B-05） | B-04 / B-05 |
| ⑥ | `--screenshot` 验收失真 | **不适用本模块**（Android 截图走 `captureMap()` L400-L415 + `PixelCopy` L434-L452，不经桌面渲染器）；该必查项在 `10-M1-desktop.md` A-01 回答 | A-01 |
| ⑦ | **K-01 三 Tab / 罗盘浮层** | **命中** | §2 |

**① 三盘自洽性核对（结论：Android 侧自洽，无缺陷）**

`CompassView` 三面画字角度：地盘 `m*15 + 0`（L237）、人盘 `m*15 + (−7.5)`（L243）、天盘 `m*15 + (+7.5)`（L248）；`MainActivity` 读数取：地盘 `MOUNTAINS[r.mountain]`、人盘 `MOUNTAINS[plate(h+15)]`（= `floor(h/15)+1`）、天盘 `MOUNTAINS[plate(h)]`（= `floor(h/15)`）（L641-L644）。
推演：人盘第 i 个字在 `i*15−7.5`，其格 `[i*15−15, i*15)` 含 h ⇔ `i = floor(h/15)+1`，与读数同索引；天盘第 i 个字在 `i*15+7.5`，其格 `[i*15, i*15+15)` 含 h ⇔ `i = floor(h/15)`，与读数同索引。`h = 0/3/15/354` 四例逐一成立。**该子项无缺陷**（读数行 L639-L647 的 12 个占位符与 12 个实参亦逐位对齐，见 B-01 撤条说明）。

---

## 4 高度可疑（不进正式缺陷清单）

### B-Q01 [高度可疑 · 中] 截图失败路径在「非罗盘页触发」时必现，且 `looksBlank` 只采 4 个像素

- 线索：`MainActivity.java` L400-L415（`captureMap`）、L425-L452（`doCapture`）、L455-L465（`looksBlank`）、L468-L493（`captureViaJs`）。
- 已掌握证据：`captureMap()` 的 `target` 固定为 `compassTab`（L401），当罗盘页 `GONE`（用户在「地图」或「知识」Tab）时 `target.getWidth()==0` → 直接 toast「罗盘页未就绪」返回（L402-L405）；`looksBlank()` 只取 `width*0.03/0.5` × `height*0.35/0.55` 四个点（L456-L457），阈值 `>24`（L461），一张几乎全黑但含单个高光像素的图会判为非空并保存。
- 缺什么证据：真机上 PixelCopy 对 WebGL 表面的实际返回码与「黑图」样本（需设备）。`captureViaJs` 的兜底产物明确不含罗盘（L467-L492 只取 `canvas.toDataURL`）。
- 由谁补：LC审测试（真机 AT 断言「截图必须含罗盘圆」与「黑图必须被拒」）。
- 补齐后的判据：PASS = 任意 Tab 下 `capture` 广播都能产出含罗盘的图且黑图被拒；FAIL = 任一路径产出无罗盘图或纯黑图仍返回成功。

---

## 5 待确认（供 99 的 Q 编号）

### B-Q02 [待确认] K-01 的「原版首页 = 地图 + 罗盘叠加」档案依据是否可回溯

- 线索：`docs/gap-matrix.md` L121（E6 控件清点）；`docs/MECHANICS.md` L466-L468（原版左列坐标，**我未直接读，转述自 `98-report-conventions.md` §6.2(b) 引文**）。
- 已掌握证据：两处文档均声称来自真机截图 `win-02-compass.png` / `left-column.png`；`docs/qa/baseline-android/manifest.md` L15/L77 显示该目录的截图是 **LC 自己的 APK**（`com.luopan.fengshui` v1.0，HEAD `ee1330a`）采集，不能作原版证据（`98-report-conventions.md` L22-L36 已给出该结论）。
- 缺什么证据：原版 `space.xinzhi.globe` 首页的真机截图或布局 dump（`.inspect` 引用链），以及 E6 的行级出处。
- 由谁补：LC审文档（档案）/ LC审证据（`.inspect` 产物）。
- 补齐后的判据：PASS = 原版首页确为「地图全屏 + 罗盘叠加 + 左/右浮动按钮列」，无底部导航 → K-01 维持本报告 §2.3 的定级与 §2.4 的改法；FAIL = 原版确有独立地图页 → K-01 降级并改为「浮层居中 + 去掉多余 Tab」的最小改法。

### B-Q03 [待确认] 分金/72 龙 表的 `offset` 是否应参与刻度绘制（A-04/B-09 的正确修正方向）

- 线索：`CompassData.java` L100（TYPE_6 offset 1.5）、L99（TYPE_5 offset 2.5）、L117-L118（步长）、`CompassData.ringIndex`（L252-L256）已按 offset 划格。
- 已掌握证据：刻度从 0 起（`CompassView.java` L198、L204），格中心高亮（L305-L307）与刻度重合。
- 缺什么证据：原版盘面在该环上的刻度是否与分金格边界对齐（需原版 `compass4.png` 等 artwork 的圈层像素比对，属 M8/资产）。
- 由谁补：LC审地图/LC审证据（artwork 圈层几何）。
- 补齐后的判据：PASS = 原版刻度与 `offset+ k*interval` 对齐 → 按 A-04/B-09 改；FAIL = 原版刻度从 0 起 → 改为「以 0 起刻度为规格」，在文档登记而非改代码。

---

## 6 与文档一致性（D12）

| # | 文档引用 | 基线事实 | 判定 |
|---|---|---|---|
| 1 | `docs/gap-matrix.md` L105（D1）：「`MainActivity.java:126` `captureMap()`」「`:163-179` 抓地图 tab 的 `mapView`」 | 基线 `MainActivity.java` L126 是 `new android.app.AlertDialog.Builder(this)`（校准对话框），`captureMap()` 在 L400-L415，抓的是 `compassTab`（L401），不是 `mapView` | 行号与行为描述均与基线不符（已证实） |
| 2 | `docs/gap-matrix.md` L131（F4）：「`MainActivity.java:247-248` 已有 `setHeading`/`setSitFace`」 | 基线 L247-L248 是 `registerDebugBridge()` 的 catch 分支；`setHeading/setSitFace` 注入在 L684-L685 | 行号错位（已证实） |
| 3 | `docs/product/PRD-v0.1.md` L100（B-19）自述三 Tab 的依据是「原版主页结构（未逐页比对）」 | 与 §2.2 的档案冲突：`gap-matrix.md` L121 记录的罗盘页有「地图」按钮，未记录底部三 Tab | 文档内部口径不一致（已证实），K-01 需按 §2.2/§5 补齐档案 |

---

## 7 测试覆盖缺口（D10）

- M2 无任何 Android 单测：`glob app/android/app/src/**/*.java` 只返回 9 个 `main/java/com/luopan/fengshui/*.java`，无 `test/`、`androidTest/` 源集；`00-MATRIX.md` 的 M2 25 文件清单里也没有测试文件。
- 现有 JVM 探针只盖数据层，不盖 UI 层：`tests/numeric/java/CompassProbe.java` L39-L41 导出 `r.mountain/r.offset/r.fenjinSlot/r.dragon72Slot/r.dragon60/r.sitting/r.jian/r.fenjin/r.dragon72/r.dragon60Name`——**不含 `jianText/fenjinText/...`，也不含 `updateReading()` 拼出的整行文本**，所以 B-10（表/公式不一致）不会被探针发现；`tests/numeric/java/SensorMathProbe.java` L14-L25 只盖 `SensorMath` 三个纯函数。
- 因此「18 项 AT 全绿」不能证明读数行文本正确：任何 AT 只要没有逐字比对 `readingView` 的整行内容（含人盘/天盘/坐三项与 5 个表字段），就不会失败。本报告 B-01 撤条的过程本身即说明：没有逐字断言的 UI 文本，人工复核也容易数错占位符。

---

## 8 已读文件清单与覆盖声明

| 文件（相对 review-base） | 行数 | 覆盖 |
|---|---|---|
| `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 767 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 358 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` | 280 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java` | 112 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/CompassReadSheet.java` | 95 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java` | 42 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/LCAndroid.java` | 66 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/MapMarker.java` | 28 | 全文 |
| `app/android/app/src/main/java/com/luopan/fengshui/SensorMath.java` | 40 | 全文 |
| `app/android/app/src/main/AndroidManifest.xml` | 30 | 全文 |
| `app/android/app/src/main/res/layout/activity_main.xml` | 50 | 全文 |
| `app/android/app/src/main/res/values/colors.xml` | 8 | 全文 |
| `app/android/app/src/main/res/values/ids.xml` | 5 | 全文 |
| `app/android/app/src/main/res/values/strings.xml` | 11 | 全文 |
| `app/android/app/src/main/res/values/themes.xml` | 9 | 全文 |
| `app/android/app/build.gradle` | 38 | 全文（取 `targetSdk 34` 作 B-02 依据；该文件归 M4） |
| `app/android/app/src/main/assets/map.html` | 1605 | **部分**：L1-L103、L330-L429、L1163-L1184 逐行读；其余仅 grep（归 M3，此处只作本模块调用方/叠加层依据） |
| `app/android/app/src/main/res/drawable-nodpi/*`（10 个二进制） | bin | 只做尺寸/体积/哈希抽查：`dial_full_1..4` 均 2048²（562–841 KB）、`dial_full_0` 1080²（130,543 B）、`dial0.png` 与 `dial_full_0.png` **SHA256 完全相同**（重复资源）、`dial*_min.webp` 各约 10 KB |
| `tests/numeric/java/CompassProbe.java` | 45 | 全文（作 D10 证据；该文件归 M5） |
| `tests/numeric/java/SensorMathProbe.java` | 27 | 全文（同上） |
| `docs/review/00-MATRIX.md` / `98-report-conventions.md` / `docs/gap-matrix.md` | 253 / 432 / 185 | 全读 / 全读 / 抽查 L100-L139 |

**覆盖声明（M2）**：9 个 `.java`、`AndroidManifest.xml`、5 个 `res` 文本文件**全部逐行读完（15/15，M2 文本文件无未读）**；10 个 drawable 按 `00-MATRIX.md` §4 的二进制例外只做资源一致性抽查；`assets/map.html` 属 M3，只读与本模块调用契约和叠加层相关的 3 个区段（225/1605 行）。逐文件「读了几行/为什么没读完」见 §11。

---

## 9 第二批缺陷（中/低，已证实）

### B-12 [中] 首启无条件请求定位权限，与用户动作无关；拒绝后无任何提示，`onRequestPermissionsResult` 未实现

**文件行号**：`MainActivity.java` L98-L102（请求）；全文无 `onRequestPermissionsResult`（文件止于 L767）

**代码片段（逐字）**

```java
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
```

**触发条件**：每次冷启动 `onCreate`，只要定位权限未授予（含用户上次点了「拒绝」但未勾「不再询问」）→ 重复弹系统权限框；勾了「不再询问」则静默不弹。

**预期 vs 实际**
- 预期：按需请求——用户点 `map.html` 的「定位」按钮（`map.html` L68 `onclick="locate()"`）时才请求；无论允许/拒绝都应给可感知反馈（`onRequestPermissionsResult` 里 toast 或禁用定位按钮）。
- 实际：进 App 第一屏就弹权限框，与用户当前意图无关；全文没有 `onRequestPermissionsResult` 覆写，拒绝后既无提示也不降级，`locate()` 之后无任何动静，用户无法判断「没权限」还是「定位失败」。

**证据等级**：已证实（请求位置、缺回调实现均为源码事实）。

**修复方向**：把请求迁到「定位」按钮的点击路径（原生按钮或 `addJavascriptInterface` 回调触发），实现 `onRequestPermissionsResult` 并在拒绝时 toast「已拒绝定位权限」+ `map.html` 侧显示不可用；启动阶段不请求。

---

### B-13 [中] 切到「地图」「知识」Tab 后传感器回调仍全速执行 `updateReading()`：隐藏页也在跑 4 次 `evaluateJavascript` 与 `setText`

**文件行号**：`MainActivity.java` L744-L749（无条件 `updateReading`）、L630-L634（切页只改可见性）

**代码片段（逐字）**

`MainActivity.java` L744-L749：

```java
            // P0-1 S5: every ring uses H; master switch off falls back to raw (RT-010).
            float h = p01Enabled
                    ? SensorMath.applyDeclination(raw, declination, trueNorth)
                    : SensorMath.normalize(raw);
            compassView.setSensorHeading(h);
            updateReading();
```

**触发条件**：在「地图」或「知识」Tab 停留时持有设备（传感器持续回调，`onResume` 期间注册，L720-L722）。

**预期 vs 实际**
- 预期：非罗盘页时不重算读数、不向 WebView 注入（或至少降到 1 Hz）。
- 实际：`onSensorChanged` 无可见性判断，`updateReading()`（L636-L647）每次照样 `setText` 并向两份 WebView 各发 `setHeading`+`setSitFace`（L682-L686）；`showTab` 只做 `setVisibility`（L630-L634），不暂停任何计算。与 B-03 的注入频率叠加后，用户在看知识页时仍在为不可见的罗盘付 CPU/电量。

**证据等级**：已证实（无可见性门槛 + 注入路径）。

**修复方向**：`updateReading()` 首行加 `if (compassTab == null || compassTab.getVisibility() != View.VISIBLE) return;`；或按 Tab 切换注册/注销传感器。

---

### B-14 [中] WebView 对所有来源自动授予地理定位：任意页面（含经 B-02 注入的 URL）请求即放行

**文件行号**：`MainActivity.java` L385-L390

**代码片段（逐字）**

```java
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
```

**触发条件**：WebView 内任意页面调用 `navigator.geolocation`；配合 B-02 的 `url` 命令，第三方 App 可让宿主加载攻击者页面，该页面随即获得定位授权（无需用户点「允许」）。

**预期 vs 实际**
- 预期：只对本应用自有 origin（`file:///android_asset/map.html`）放行，其它来源拒绝或弹确认；这与 B-02 的最低修复（删除 `url`/`js`）应同时生效。
- 实际：`callback.invoke(origin, true, false)` 的第一参是来源、第二参 `true` = 允许、第三参 `false` = 不「记住」但本次直接给坐标——来源字符串完全未校验。

**证据等级**：已证实（无条件放行）。

**修复方向**：改为 `boolean allow = origin.startsWith("file:///android_asset/"); callback.invoke(origin, allow, allow);`，并保留 B-02 的导出广播修复作前置。

---

### B-15 [低] 截图入口固定在罗盘页：在「地图」/「知识」Tab 触发（按钮或 `capture` 广播）必失败

**文件行号**：`MainActivity.java` L400-L405

**代码片段（逐字）**

```java
    private void captureMap() {
        final View target = compassTab;
        if (target == null || target.getWidth() == 0 || target.getHeight() == 0) {
            toast("截图失败：罗盘页未就绪");
            return;
        }
```

**触发条件**：`am broadcast ... --es cmd capture`（`handleDebugCommand` 的 `capture` 分支，L207-L210）在非罗盘 Tab 触发；或将来把「截图」按钮移到其它页。

**预期 vs 实际**
- 预期：截图不依赖当前 Tab（PixelCopy 可直接对 GONE 视图的 window 区域拍不到，应先切页或提示用户回到罗盘页并给出可操作文案）。
- 实际：`compassTab` 为 `GONE` 时 `getWidth()==0` → 直接 toast「罗盘页未就绪」，且不切页、不重试。AT 若在错误 Tab 下跑 `capture`，得到的是「失败」而不是可用图，容易被误判为功能缺陷。

**证据等级**：已证实（固定 target + GONE 尺寸为 0）。

**修复方向**：`captureMap()` 先 `showTab(0)`（或 B-03 单页化后无需切页）再截图；失败文案改为「请回到罗盘页后重试」并附带当前 Tab 名。

---

### B-16 [低] `looksBlank` 只采 4 个像素判黑图：单点高光即可让全黑图通过

**文件行号**：`MainActivity.java` L456-L461

**代码片段（逐字）**

```java
        int[] xs = {(int) (bmp.getWidth() * 0.03f), (int) (bmp.getWidth() * 0.5f)};
        int[] ys = {(int) (bmp.getHeight() * 0.35f), (int) (bmp.getHeight() * 0.55f)};
        for (int x : xs) {
            for (int y : ys) {
                int c = bmp.getPixel(x, y);
                if (((c >> 16) & 0xff) + ((c >> 8) & 0xff) + (c & 0xff) > 24) return false;
```

**触发条件**：PixelCopy 返回黑图（WebGL 未合成）但有任一采样点亮着；或图表恰有 UI 元素落在 4 个采样点上。

**预期 vs 实际**
- 预期：按区域直方图/非零像素比例判定（例如抽样网格 32×32、非黑占比阈值），或直接比较「地图瓦片区域」的方差。
- 实际：4 个点、阈值 24（三通道和）→ 判定极不稳定：真黑图若某点有一处 8,8,9 以上的像素就判「非空」，进而保存失败图；反之正常图若四点是纯底色也可能误判为空并走 `captureViaJs`（丢罗盘）。

**证据等级**：已证实（采样点与阈值均为常量）。

**修复方向**：改为网格采样（≥1024 点）+ 非黑像素占比阈值（如 <1% 判空），并把判定结果与 PixelCopy 返回码一起写进 `LCCAP` 日志。

---

### B-17 [低] `onSensorChanged` 内 `if (injectedHeading != null) raw = injectedHeading;` 是不可达分支（方法入口已提前 return）

**文件行号**：`MainActivity.java` L737-L743

**代码片段（逐字）**

```java
        if (injectedHeading != null) return;
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float raw = normalize((float) Math.toDegrees(orientation[0]));
            lastRaw = raw;
            if (injectedHeading != null) raw = injectedHeading;
```

**触发条件**：静态事实，运行时永不执行到 L743。

**预期 vs 实际**
- 预期：注入态已由 L737 提前返回，L743 无存在必要；若设计意图是「注入值也要走一遍归一化/磁偏角」，则应在返回前处理，而不是写在返回之后。
- 实际：L743 是死分支，读代码的人会误以为传感器路径也会被注入值覆盖（`injectedHeading` 仅在 L114/L170/L172/L223 被读写）。同一文件里 `LCRING` 日志有 500 ms 节流（L664），而这条死分支没有任何测试覆盖。

**证据等级**：已证实（前置 `return` 使后续同条件判断不可达）。

**修复方向**：删除 L743；注入路径统一收敛到 `applyTrustState()`（L109-L119）与 `handleDebugCommand` 的 `heading` 分支（L169-L176）。

---

### B-18 [低] D-005 单点标记未落地：`mapMarker` 只被赋值、全仓无读取点，pick 结果不进任何 UI、截图或日志

**文件行号**：`MainActivity.java` L40（声明）、L377-L383（赋值）

**代码片段（逐字）**

`MainActivity.java` L377-L383：

```java
        webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
            if ("pick".equals(type)) {
                mapMarker = marker;
            } else {
                mapMarker = null;
            }
        }), "LCAndroid");
```

**触发条件**：`map.html` 内任意 pick（`window.LCAndroid.onEvent('{"type":"pick",...}')`，`map.html` L1006-L1007）。

**预期 vs 实际**
- 预期：D-005 要求「单点标记、每次 pick 覆盖上一次」，至少要能在 UI 上看到该点（或进截图角注、或进 logcat 结构化日志以便 AT 校验）。
- 实际：`grep -n "mapMarker"` 全仓只有 L40 声明与 L379/L381 两处赋值，**无任何读取**：坐标既不显示、不写入截图、也不打日志；`LCAndroid.java` L60 的 `Log.d("LCPICK", "pick " + marker)` 是唯一痕迹。B-Q01 的截图与 D-005 的验收因此都没有客户端侧可断言的产物。

**证据等级**：已证实（读点缺失可 grep 复核）。

**修复方向**：pick 时更新一个可见的坐标条（或 `statusView` 第二行）并写 `Log.d("LCPICK", marker.toString())`；截图 `composeAttribution` 增加「选中点 lng/lat/elev」一行。

---

### B-19 [低] `dial0.png` 与 `dial_full_0.png` 字节完全相同（重复资源），且 48dp 缩略图加载 1080² 全图

**文件行号**：`CompassListActivity.java` L25-L28（缩略图表）、L87-L92（48dp 尺寸）；`CompassView.java` L36-L37（全图表）

**代码片段（逐字）**

`CompassListActivity.java` L25-L28：

```java
    private static final int[] THUMBS = {
            R.drawable.dial0, R.drawable.dial1_min, R.drawable.dial2_min,
            R.drawable.dial3_min, R.drawable.dial4_min
    };
```

`CompassListActivity.java` L87-L92：

```java
            ImageView thumb = new ImageView(this);
            int size = (int) (48 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
            thumb.setLayoutParams(lp);
            thumb.setImageResource(THUMBS[i % THUMBS.length]);
```

**触发条件**：打开「盘面」列表页（`CompassListActivity`）。

**预期 vs 实际**
- 预期：缩略图用 `dial*_min` 级资源；`dial0` 与 `dial_full_0` 不应同时存在两份相同字节。
- 实际：`SHA256` 实测 `dial0.png` = `dial_full_0.png`（同前缀 `0721754D5595C065`，各 130,543 B）→ APK 多 130 KB；`THUMBS[0]` 是 1080² 全图，在 48dp 的 `ImageView` 上解码约 1080×1080×4 ≈ 4.6 MB 内存（其余 4 项是 ~10 KB 的 webp）。

**证据等级**：已证实（哈希与尺寸实测，命令见 §11）。

**修复方向**：`dial0.png` 换成 `dial_full_0` 的缩略图副本（webp/`_min`），或直接删 `dial0.png` 让 `THUMBS[0]` 指向一张新生成的 96² webp。

---

### B-20 [低] 同一「角度归一化」实现被复制四份（Android 包内：`SensorMath.normalize` / `MainActivity.normalize` / `CompassData.norm` / `CompassView.normalize`）

**文件行号**：`SensorMath.java` L14-L18、`MainActivity.java` L705-L709、`CompassData.java` L144-L148、`CompassView.java` L120-L124（调用点 `CompassView.java` L115 `heading = normalize(h);`）

**代码片段（逐字）**

`SensorMath.java` L14-L18：

```java
    public static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }
```

`CompassView.java` L120-L124：

```java
    private static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }
```

`MainActivity.java` L705-L709：

```java
    private static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }
```

**触发条件**：静态事实。

**预期 vs 实际**
- 预期：`SensorMath` 已是「纯静态、JVM 可测」的单一入口（该类注释 L6-L8 明示），其余三处应直接调用它。
- 实际：四份实现逐字相同（`SensorMath.normalize` 为 `public static`，`MainActivity.normalize` L705、`CompassData.norm` L144、`CompassView.normalize` L120 均为 `private static`，函数体一致）；`SensorMath.normalize` 的 JVM 探针（`SensorMathProbe.java` L14-L25）只测到第一份，另三份改规则不会让探针失败。跨端另有两处同构实现（`app/src/main.rs` L227 `fn normalize_deg`、`tests/numeric/oracle.py` L122 `def normalize`），属各自语言的独立实现，按模块边界**不计入 M2 计数**，仅在此记录。

**证据等级**：已证实（四份代码逐字比对；跨端两处另行 grep 复核存在：`main.rs` L227、`oracle.py` L122）。

**修复方向**：四处统一委托 `SensorMath.normalize`（`CompassData` 是纯 JVM 类、`CompassView` 可直接静态调用），删掉三份复制体；并在 `tests/numeric/source_parse.py` 增加一条「包内只允许一处 `normalize` 定义」的源码扫描断言（F-02 的扫描机制可复用），防止再分叉。

---

### B-21 [低] 读数行 `String.format` 未指定 Locale，状态条却用 `Locale.US`：小数分隔符随系统语言变化，两处口径不一致

**文件行号**：`MainActivity.java` L640（读数行，无 Locale 参数）、L656-L659（状态条，`Locale.US`）

**代码片段（逐字）**

`MainActivity.java` L656-L659：

```java
            String declText = trueNorth
                    ? String.format(java.util.Locale.US, "真北%+.1f°", declination)
                    : "磁北";
            statusView.setText(declText + accText + tiltText);
```

**触发条件**：系统语言为使用逗号作小数分隔符的 Locale（如 de-DE、fr-FR、ru-RU）。

**预期 vs 实际**
- 预期：同一屏的两个数值文本用同一 Locale（本项目其它数值位置均显式 `Locale.US`，见 L216/L343/L526/L612/L669/L671）。
- 实际：L640 的 `String.format("度数 %.2f° …")`（该行完整原文见 B-01 撤条段）未传 Locale → 在 de-DE 下显示「度数 0,00°」，而状态条显示「真北+0.0°」，同一屏两种小数点；截图/AT 文案断言会随系统语言失效。

**证据等级**：已证实（Locale 参数缺失 + 同文件其余位置均显式指定）。

**修复方向**：L640 改为 `String.format(java.util.Locale.US, "度数 %.2f° …", ...)`；全文件统一走一个 `fmt(String, Object...)` 私有方法。

---

## 10 必查项缺口（第一批未闭环项）

| 必查项 | 状态 | 缺什么 | 由谁补 / 判据 |
|---|---|---|---|
| ② 前后台/旋转下的传感器与 WebView 泄漏 | 代码层闭环（B-06/B-07/B-08） | **真机运行态**：`adb shell dumpsys meminfo` 前后台对比、`webview` 的 `pauseTimers` 是否生效 | LC审测试（真机 AT）；PASS = 后台 30 s 后 WebView 进程 CPU≈0 且无 rAF 唤醒 |
| ⑤ 触摸层级/透明度 | 代码层闭环（B-04/B-05） | **真机手势**：`uiautomator dump` 取罗盘/底图 bounds；圆外 50px `input swipe` 后地图 `center` 是否变化 | LC审测试；PASS = 圆外拖动可平移、圆内拖动只转盘 |
| K-01 原版几何依据 | 档案未取证（B-Q02） | 原版首页真机截图/布局 dump（`.inspect` 引用链） | LC审证据 / LC审文档；见 B-Q02 的 PASS/FAIL |
| ① 三盘自洽 | 闭环（结论：自洽；另两处不自洽为 B-09/B-10） | 分金/72 龙刻度口径需 artwork 像素比对（B-Q03） | LC审地图/LC审证据 |

## 11 未覆盖文件清单（读了几行 / 为什么没读完）

M2 的 25 个文件里，**15 个文本文件全部逐行读完（15/15）**；10 个 drawable 为二进制（§4 例外）。下表列出我引用过或按模块归属**未读完**的文件：

| 文件 | 总行数 | 我读了多少 | 为什么没读完 |
|---|---|---|---|
| `app/android/app/src/main/assets/map.html` | 1605 | **225 行**（L1-L103、L330-L429、L1163-L1184 逐行）+ 17 处 grep 命中 | 属 M3（LC审地图 报告 853 行已覆盖全文件）。我只取三块：①HTML/CSS 的叠加层与控件坐标（L1-L103）；②`setHeading/setSitFace` 契约（L330-L429）；③rAF 渲染循环（L1163-L1184）。业务逻辑、瓦片、3D 管线未读 |
| `app/android/app/README.md` | 41 | 0 行 | 属 M4；不属 M2 代码审查范围 |
| `app/android/app/{Dockerfile,docker-compose.yml,gradle.properties,settings.gradle}`、`gradle/wrapper/gradle-wrapper.properties`、`proguard-rules.pro` | 共 46 行 | 0 行 | 属 M4（构建/基建）；本报告只读 `app/build.gradle` 取 `targetSdk 34` |
| `app/MECHANICS.md` | 505 | **0 行** | 属 M7（LC审文档）；§2.2 与 B-Q02 对它的引用全部标注为「转述自 `98-report-conventions.md` §6.2(b)」 |
| `app/mechanics.json` | 1605 | **0 行** | 属 M7；本报告只核对 `CompassData.java` 内已生成的表 |
| `docs/qa/baseline-android/**`（截图/dump） | — | 0 行 | 二进制与 uiautomator dump；`98-report-conventions.md` §1 已判定其为 LC 自己的 APK，不作原版证据 |
| `docs/MECHANICS.md` 之外的 M6 文档（PRD/AT/FINAL-REPORT） | — | 仅 `PRD-v0.1.md` L100 一行 | 属 M6；§6 的 D12 只抽到与 M2 直接冲突的 3 行 |

**已执行未用满的取证手段**：
- **未构建 APK**、**未上真机**（本机无连接设备）：B-04 的像素数值按 `density=3.0` 推演（文中已标注），B-06/B-07/B-08 与 B-Q01 的判据保留在 §10。
- **未运行 `gradlew` / `lint`**：`app/android` 无 Gradle wrapper 可执行环境（`gradlew` 未入库，`00-MATRIX.md` 亦无该文件）。
- 已实测的哈希命令（可复现）：`Get-ChildItem <res\drawable-nodpi> | Get-FileHash -Algorithm SHA256`，输出 `dial0.png` 与 `dial_full_0.png` 前缀均为 `0721754D5595C065`。

## 12 本批计数（两批合计）

- 已证实：**20**
  - 阻断 **1**：B-02
  - 高 **4**：B-03、B-04、B-05、B-10（B-10 按裁定「同因冲突取高、母条归实现模块」由中升为高）
  - 中 **8**：B-06、B-07、B-08、B-09、B-11、B-12、B-13、B-14
  - 低 **7**：B-15、B-16、B-17、B-18、B-19、B-20、B-21
- 已撤：**B-01**（复核为误报，保留空号，不得引用）
- 高度可疑：**1**（B-Q01）
- 待确认：**2**（B-Q02、B-Q03）
- K-01：本报告 §2 已给出代码事实、档案引用、定级建议（高）与修复方向，引 B-03/B-04 为母条，不重复计数（按技术总监裁定：双母条 = B-03+B-04，C-07/G-01 交叉引用；双盘读数口径矛盾归 B 母条 B-10）。
- 本报告母条吸收的交叉引用：**A-03 → B-10**、**A-04 → B-09**（两条均在 `10-M1-desktop.md`，不重复计数）、**F-03 → B-10**（M5）。

---

## 13 订正记录（按技术总监 2026-11-05 裁定 + `91-audit.md` §3/§8.3 订正清单）

只列被改动的条目；未列条目与片段一字未动。格式：证据原文 → 行号 → 改了什么。

| 条目 | 证据原文（基线逐字） | 行号（原 → 订正后） | 改了什么 |
|---|---|---|---|
| **B-02** | `            case "url": {` … `                break;` / `            }`（`MainActivity.java` L184 起，至 L191 的块结束 `}`） | L184-L190 → **L184-L191** | 只补片段末行（`url` 块的闭合 `}` 在 L191），片段与结论未变 |
| **B-10** | `        drawText(canvas, rd.jian + " · " + CompassData.YINYANG[rd.mountain],` / `                cx, cy + 34f * density(), textPaint);` | `CompassView.java` L298 → **L298-L299**；级别 中 → **高** | 补片段末行（第二行是同一调用）；按裁定「同因冲突取高、母条归实现模块」升为高，并加「定级说明」；A-03（M1）、F-03（M5）转交叉引用 |
| **B-20** | `    private static float normalize(float d) {` / `        float x = d % 360f;` / `        if (x < 0f) x += 360f;` / `        return x;` / `    }`（`CompassView.java` L120-L124，调用点 L115） | 三份 → **四份**，补 `CompassView.java` L120-L124 | 标题/文件行号/片段/预期vs实际/证据等级/修复方向全部改为「Android 包内四份」；跨端 `app/src/main.rs` L227、`tests/numeric/oracle.py` L122 仅记录、不计入 M2 |
| **B-03** | `            updateReading();`（`MainActivity.java` L749，注入路径原文不变） | 无行号变化 | 频率数字改为「推算值，未实测」并写明唯一假设（`SENSOR_DELAY_UI` 60,000 µs）；声明本数字全报告只此一处，M3/成文引用须标来源、不得另算 |
| **B-09** | `        for (int d = 0; d < 360; d += (int) CompassData.FENJIN_STEP) {`（`CompassView.java` L198；72 龙 L204 同构） | 无行号变化 | 转为母条（A-04/M1 交叉引用）：按裁定「双端同源归实现模块」，两侧同为中，级别不变 |
| **B-01** | `                String.format("度数 %.2f°  %s  %s\n地盘 %s山  人盘 %s山  天盘 %s山  坐 %s山\n分金 %s  七十二龙 %s\n六十龙 %s  后天卦 %s  先天卦 %s",`（`MainActivity.java` L640） | 无行号变化 | 已撤（误报）：格式串实为 12 个占位符（`%.2f` + 11 个 `%s`，含连续两个匿名 `%s`），与 12 个实参逐位对应；保留空号，不得引用 |
| **B-05** | `    public void setHeadingManually(float h) {` / `        followSensor = false;`（`CompassView.java` L113-L114）；`                compassView.setHeadingManually(normalize(compassView.getHeading() + 180f)));`（`MainActivity.java` L316-L317） | 无行号变化；级别「高」不变 | **删除**「`setHeadingManually` 才是显式退出跟随的入口」一句（该判断不成立）；改为「锁定走独立控件、翻坐向走持久偏移量且保持 `followSensor=true`」；补原版出处（`gap-matrix.md` L121 E6 清单无「转 180°」按钮、L132 F5 双标签两态几何未取证）；修复方向同步改为 `setHeadingOffset` 方案。主缺陷（`ACTION_DOWN` 即冻结）与定级未动 |
