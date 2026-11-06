# Mechanics — 三维山水全景地图 (Feng Shui compass) rebuild

Extracted from `E:\APPDetector\input\swss.apk` (package `space.xinzhi.globe`, v1.6.1,
app label "三维山水全景地图").

## Decompilation findings (real addresses, from android_method_show + native_function_pseudocode)

The APK is packed with the 爱加密 (ijiamei) shell. The *visible* DEX is only the packer
plus `com.example.helloworld.*` placeholder stubs; the entire app is in an encrypted DEX.

### Java-side packer (readable, classes.dex)
- `space.xinzhi.globe.MyWrapperProxyApplication.initProxyApplication(Context)V`
  (46 instructions): opens the APK as a `ZipFile` (`ApplicationInfo.sourceDir`), calls
  `com.wrapper.proxyapplication.Util.PrepareSecurefiles(Context, ZipFile)`, then loads the
  native shell via `System.load(filesDir + "/prodexdir/" + Util.libname)` (or
  `System.loadLibrary(simplelibname)` on ARM).
- `com.wrapper.proxyapplication.WrapperProxyApplication.<clinit>()V` (32 instructions):
  - `0x0003` `pkgName = "space.xinzhi.globe"`
  - `0x0007` `className = "space.xinzhi.globe.App"`  ← the **real Application class**
  - `0x0011` `protectlib = "libshell-super.space.xinzhi.globe.so"`
  - `0x0015` `protectlibx86 = "libshellx-super.space.xinzhi.globe.so"`
  - `0x0019` `shellsuperversion = "4.6.2.2"`
- `com.wrapper.proxyapplication.Util.<clinit>()V` (86 instructions):
  - `0x0000` `MAX_DEX_NUM = 300` (`0x12c`)
  - `0x0025` `securename5 = "0OO00l111l1l"`  ← the 27 MB **encrypted DEX container** asset
  - `0x0029` `securename6 = "o0oooOO0ooOo.dat"`  ← header `SMZ40` key/IV blob
  - `0x0035` `securename9 = ".updateIV.dat"`  ← decryption **IV** file
  - `0x0049` `dexname = "classes.dex"`  ← the decrypted DEX is written as this
  - `0x007e` `simplelibname = "shell-super.space.xinzhi.globe"` → `libname = "libshell-super.space.xinzhi.globe.so"`

### Native shell `libshell-super.space.xinzhi.globe.so` (360 KB)
- `JNI_OnLoad` @ `0x5370` — packer entry; control-flow-flattened (OLLVM) and
  string-encrypted (obfuscated strings; the only readable literal is `"gggg,"` = DEX magic).
- `sub_1bf8c` @ `0x1bf8c` — the 37 KB decryptor (1235 basic blocks, 9281 instructions).
  Its call list (from the pseudo-C): `mprotect@plt` (marks the decrypted DEX executable),
  `srand@plt` + `time@plt` (key/IV derivation), `memcpy@plt`, `snprintf@plt`/`sprintf@plt`
  (builds the `classes.dex` path), `getenv@plt` + `__system_property_get@plt` (anti-tamper),
  plus internal helpers. zlib `inflate` lives in a separate function; the whole shell imports
  `inflate`/`deflate`/`crc32`/`adler32` for the DEX container.
- 455 functions, symbols stripped.

### `libterrain.so` (256 KB, "3D-terrain")
- No compass strings (`子`/`丑`/`分金`/`罗盘`/`山`/`卦` → 0 matches); strings obfuscated.
  Only `"libterrain.so"` in `.dynstr`.
- 289 functions, but they are C++ runtime thunks + `sub_*`; notable symbols:
  `vmInterpret` @ `0xfa30`, `cacheInitial` @ `0x18eac`, `getCacheClass` @ `0x19d5c`
  — these are a **Dalvik VM interpreter**, i.e. the "terrain" logic is VM-protected
  (interpreted bytecode), so it is not statically readable native code. `fmod`/`fmodf` are
  used (coordinate wrapping). Not reproducible offline.

Dynamic-unpack (Huawei device `L2E0222531014833`): re-signed the APK as `debuggable`
(apktool → `android:debuggable="true"` → zipalign → apksigner), installed it, launched it,
and dumped the **decrypted on-disk DEX** via `run-as` from
`files/prodexdir/00O000ll111l_{0..6}.dex` (7 files, 61 MB, real `dex\n037`).

The real app code is in `00O000ll111l_6.dex` (3411 classes under `space.xinzhi.globe`).
Key compass classes found:
- `space.xinzhi.globe.ui.tools.CompassToolsKt` — 133 methods (the core 罗盘 logic).
- `space.xinzhi.globe.common.CompassReadTypeEnum` — 14 reading types `TYPE_1..TYPE_14`,
  each with `text`, `intervalDegrees:F`, `offsetDegrees:F`, `html`, `isVip`. **This confirms
  the ring model: every layer is an `intervalDegrees` + `offsetDegrees` pair** (i.e. 24山 =
  15° interval/0° offset, 人盘 = 15°/−7.5°, 天盘 = 15°/+7.5°, 分金 = 3°, 72龙 = 5°, 60龙 = 6°).
- `space.xinzhi.globe.common.CommonCompassEnum` — 5 compass types `COMPASS_0..COMPASS_4`
  (each `code`/`text`/`resId`/`isVip`).
- `space.xinzhi.globe.manager.CompassManager` (14 methods), `model.CompassModel` (8 fields),
  `model.CompassCategoryModel`, `viewmodel.CompassListViewModel`,
  `ui.pages.CompassListActivity` / `CompassIntroduceActivity`.

Limitation resolved via **Frida memory dump** (frida-gadget 17.17.0 injected into the
re-signed APK, `frida-dexdump -H 127.0.0.1:27042`): the in-memory DEX (`mem_dump/classes*.dex`)
has the real de-obfuscated method bodies. Reading `CompassReadTypeEnum.<clinit>` (147 insns)
and `CommonCompassEnum.<clinit>` (57 insns) gives the **exact** values:

### Real compass dials — `space.xinzhi.globe.common.CommonCompassEnum` (5 types)
| enum | code | text | isVip |
|------|------|------|-------|
| COMPASS_0 | compass | 二十四山(阴) | false |
| COMPASS_1 | compass1 | 常用罗盘 | false |
| COMPASS_2 | compass2 | 三合罗盘(白) | true |
| COMPASS_3 | compass3 | 三元罗盘(金) | true |
| COMPASS_4 | compass4 | 综合罗盘(白) | true |

### Real ring layers — `space.xinzhi.globe.common.CompassReadTypeEnum` (14 types)
Constructor `(name, ordinal, text, float, float, html, isVip)`; the two floats are
`intervalDegrees` (degrees per division) and `offsetDegrees` (boundary/center offset).
Decoded from the `const/high16` float literals:

| TYPE | text | intervalDegrees | offsetDegrees | isVip |
|------|------|-----------------|---------------|-------|
| TYPE_1 | 先天八卦_卦位 | 45.0 | 22.5 | false |
| TYPE_2 | 后天八卦_卦位 | 45.0 | 22.5 | false |
| TYPE_3 | 先天八卦洛书数 | 45.0 | 22.5 | false |
| TYPE_4 | 地盘正针二十四山 | 15.0 | 7.5 | true |
| TYPE_5 | 穿山七十二龙 | 5.0 | 2.5 | true |
| TYPE_6 | 地盘正针百二十分金 | 3.0 | 1.5 | true |
| TYPE_7 | 透地平分六十龙 | 6.0 | 1.5 | true |
| TYPE_8 | 人盘中针二十四山 | 15.0 | 0.0 | true |
| TYPE_9 | 天盘缝针二十四山 | 15.0 | 0.0 | true |
| TYPE_10 | 天盘缝针百二十分金 | 3.0 | 0.0 | true |
| TYPE_11 | 先天方图六十四卦内卦 | 5.625 | 0.0 | true |
| TYPE_12 | 先天圆图六十四卦外卦 | 5.625 | 0.0 | true |
| TYPE_13 | 六十甲配六十四卦五行 | 5.625 | 0.0 | true |
| TYPE_14 | 正兼向度数指标 | 3.0 | 0.0 | true |

This **confirms** the rebuild's 24山 15°/7.5°, 分金 3°/1.5°, 72龙 5°/2.5°, 60龙 6°, 八卦 45°
and adds the 64卦 layer at **5.625°** (= 360/64).

### CompassToolsKt (133 methods, Jetpack Compose)
- `CompassToolsKt.B1(D)D` (7 insns) = degree normalization:
  `v %= 360.0; if (v < 0) v += 360.0; return v` (const `0x4076800000000000` = 360.0).
  This is byte-for-byte the same rule as the rebuild's `normalize_deg`.
- **更正（2026-11-06，见 §R5）**：这一段原先记「per-ring radii/colors 未解出」。实际是
  **原版根本没有这些常量**——`CompassToolsKt` 里没有任何 `drawArc`/`drawCircle` 逐圈矢量绘制
  （全 smali 树里 `drawArc` 只出现在第三方库 `ENDownloadView`/`ENPlayView`），盘面是整张位图
  `ImageKt.Image(...)`（`:4281`/`:14282`）+ `GraphicsLayerScope.rotationZ`（`:4584`/`:4800`/`:17593`）。
  全文件 `const-wide` 仅 9 条（180×2、360×2、白×4、`0xcc000000`）。半径/配色改由**位图实测**给出：
  见 **§补证 R5.4 / R5.5**（默认盘面 `compass.png`：外缘 476 px = 半画布 0.8815；三环带
  0–0.786 / 0.786–0.948 / 0.948–1.0 R；配色黑 `#000000` + 白 `#FFFFFF`）。
- `CompassToolsKt.E0(...)` (44 insns) reveals the app architecture: the map is a
  **WebView** (`space.xinzhi.globe.map.MapWebController`) driven by a JS bridge
  (`space.xinzhi.globe.map.MapWebApiKt`), and the compass is a Compose `GraphicsLayer`
  overlay; there is a "截图中…" screenshot path that calls
  `MapWebApiKt.getScreenshot(mapWebController, …, "compass", …)` (plus a CameraX
  `ImageCapture` path). This confirms the rebuild's WebView-map + Canvas-compass structure is
  architecturally correct (the native `libterrain.so` is an additional 3D terrain overlay).

### Map classes (confirms WebView architecture)
`space.xinzhi.globe.map` package (28 classes) — the map is a **WebView**, not native:
- `MapWebView extends android.webkit.WebView` (with `WebViewClientCompat`).
- `MapWebInterface` (33 methods) = the `@JavascriptInterface` JS bridge; `MapWebApiKt` (84
  methods) = the Kotlin JS API; `MapWebController` (26) / `MapWebListener` (23) = control.
- Features revealed by the inner-class names: `on3dChanged` + `MapWebKt$Control3d`
  (**3D mode**), `onMapRotate` (map rotation), `openStreetView` (street view),
  `setSitFace` (set 坐/向 on the map), `onClickMapElement` / `deleteMapElementByType` /
  `setDrawBtnState` (drawing), `showFullScreen` / `closeFullScreen`.

So the original "三维山水全景地图" is a WebView map with a 3D mode + rotation + street view +
a compass/sit-face overlay — exactly the Tianditu-WebView + 3D-tilt + compass rebuild
implemented here. The bridge is a structured JS-action pattern: `MapWebController.javascript
(String, JsonElement, Function3)` sends a `JSAction` to the WebView, `handleJsAction` /
`handleJsCallback` receive callbacks, `screenshotHtml() → Bitmap` captures the map+compass
(the "compass screenshot"), and the 3D control (`Control3d`) is entered by long-press
(`suppressLongPressFor`/`shouldSuppressLongPress`). The JS command is a 3-field
`space.xinzhi.globe.map.action.JSAction` data class: `action:String` (command name),
`callbackName:String`, `linkPar:JsonElement` (params) — i.e. every map call is
`window[action](params, callback)`.

### Real reading formula — `CompassReadTypeEnum.getFaceText(F)` / `getBackText(F)`
Decoded bytecode (18 + 7 insns) gives the exact mapping from a heading to a ring text:

```
index = (int) floor(((heading - offsetDegrees + 360) % 360) / intervalDegrees)
face  = textList[index]  (else "未知")
back  = getFaceText((heading + 180) % 360)     // 坐 = 向 + 180°
```

So each ring is fully described by `(textList, intervalDegrees, offsetDegrees)`. The
rebuild's reading uses this same formula (with a list ordered 子癸…; the original's lists are
ordered from 癸, so the two are equivalent). One correction applied: 透地平分六十龙 uses
`offsetDegrees = 1.5` (not 0) → index = `floor(((h − 1.5 + 360) % 360) / 6)`.

**Conclusion:** the compass (罗盘) ring logic lives in the encrypted DEX
(`assets/0OO00l111l1l`) under `space.xinzhi.globe.App` and its UI classes, which the shell
decrypts into `classes.dex` at runtime using the `.updateIV.dat` IV. It is not statically
readable. The ring *formulas* below come from the app's own bundled knowledge base
(`assets/compassIntroduce/*.html`) — real app content. The per-mountain *tables* are in the
encrypted DEX and are marked **estimated**.

## Compass rings (from the app's knowledge base — real values)
- **二十四山 (24 mountains)** = 十二地支 (子丑寅卯辰巳午未申酉戌亥) + 八干
  (甲乙丙丁庚辛壬癸, 戊己 omitted) + 四维 (乾坤艮巽). 24 × 15° = 360°.
  Order clockwise from north: 子 癸 丑 艮 寅 甲 卯 乙 辰 巽 巳 丙 午 丁 未 坤 申 庚 酉 辛 戌 乾 亥 壬.
  Cardinal mountains: 子=N(0°) 午=S(180°) 卯=E(90°) 酉=W(270°).
- **三盘 (three plates):**
  - 地盘正针 (earth / correct needle): reference, 子 at 0°. offset **0°**.
  - 人盘中针 (human / middle needle, 赖公中针): counterclockwise **−7.5°**.
  - 天盘缝针 (heaven / seam needle): clockwise **+7.5°**.
- **百二十分金 (120 fenjin):** 120 divisions × **3°**, 5 per mountain, named by 六十甲子
  cycled twice. 子山 (from the 壬 side): 甲子 丙子 戊子 庚子 壬子.
- **穿山七十二龙 (72 dragons):** 72 divisions × **5°**, 3 per mountain.
  子山: 甲子 丙子 戊子; 丑山: 乙丑 丁丑 己丑; 寅山: 丙寅 戊寅 庚寅.
- **透地平分六十龙 (60 dragons):** 60 divisions × **6°**, named by 六十甲子, 甲子 → 癸亥.
- **正兼向 (real, decompiled):** each 山 (15°) splits into 5 分金 (3° each); the
  `getTextList()` TYPE_14 branch is a 120-entry list whose per-山 pattern is
  `九度外 / 九度内 / 九度内 / 九度内 / 九度外` → 正向 = the central **9°** (±4.5° from the
  mountain center, "九度内"), 兼向 = the outer **3°** on each side ("九度外").
  (Rebuild uses this ±4.5° boundary.)

## Five elements (五行) of the 24 mountains
Derived from the KB's explicit rules (assets/compassIntroduce/六十甲配六十四卦五行.html):
天干 甲乙木 丙丁火 庚辛金 壬癸水; 地支 子亥水 寅卯木 巳午火 申酉金 辰戌丑未土;
四维 乾金 坤土 艮土 巽木 (from 八卦五行 乾兑金 坤艮土 震巽木 坎水 离火). Therefore:
子水 癸水 丑土 艮土 寅木 甲木 卯木 乙木 辰土 巽木 巳火 丙火
午火 丁火 未土 坤土 申金 庚金 酉金 辛金 戌土 乾金 亥水 壬水.

## Yin / Yang (三元盘, from the KB)
阴: 辰戌丑未, 子午卯酉, 癸丁, 乙辛.
阳: 寅申巳亥, 乾坤艮巽, 甲庚, 丙壬.

## Bagua (八卦)
- 后天八卦 directions: 坎北(0°) 离南(180°) 震东(90°) 兑西(270°) 巽东南(135°)
  乾西北(315°) 坤西南(225°) 艮东北(45°).
- 先天八卦 directions: 乾南 坤北 离东 坎西 兑东南 巽西南 震东北 艮西北.
- 八卦五行: 乾兑金 坤艮土 震巽木 坎水 离火.
- 先天八卦洛书数: 乾1 坤8 震4 巽5 坎6 离3 艮7 兑2.

## 六十甲子 (60 Jiazi) + 纳音五行
甲子乙丑海中金 丙寅丁卯炉中火 戊辰己巳大林木 庚午辛未路旁土 壬申癸酉剑锋金
甲戌乙亥山头火 丙子丁丑涧下水 戊寅己卯城头土 庚辰辛巳白蜡金 壬午癸未杨柳木
甲申乙酉泉中水 丙戌丁亥屋上土 戊子己丑霹雳火 庚寅辛卯松柏木 壬辰癸巳长流水
甲午乙未沙中金 丙申丁酉山下火 戊戌己亥平地木 庚子辛丑壁上土 壬寅癸卯金箔金
甲辰乙巳佛灯火 丙午丁未天河水 戊申己酉大驿土 庚戌辛亥钗钏金 壬子癸丑桑柘木
甲寅乙卯大溪水 丙辰丁巳沙中土 戊午己未天上火 庚申辛酉石榴木 壬戌癸亥大海水.

## What is estimated vs sourced
- **Sourced (real, from the decompiled DEX):** 24-mountain order (internal order is
  癸,丑,艮,寅,甲,卯,乙,辰,巽,巳,丙,午,丁,未,坤,申,庚,酉,辛,戌,乾,亥,壬,子 — i.e. 子 last, from
  `offsetDegrees=7.5`), 15° spacing, 分金 3°, 72龙 5°, 60龙 6° (offset 1.5), 兼向 3°/正向 9°,
  the 八卦/五行 rules, and the 分金/龙 naming.
- **分金/龙 naming (CONFIRMED, was "estimated"):** `getTextList()` TYPE_6 branch holds the
  full 120-分金 list in the form `"{山名} {甲子}"` (e.g. 癸山 甲子/丙子/戊子/庚子/壬子,
  丑山 乙丑/丁丑/己丑/辛丑/癸丑, 艮山 borrows 丑, 甲山 borrows 寅 …). This is exactly the
  旺相 convention used in the rebuild: 阳支山 take 甲丙戊庚壬+支, 阴支山 take 乙丁己辛癸+支,
  八干/四维 borrow the adjacent 支. (Original even has a typo "已山" for 乙山.)
- **Remaining for 1:1:** the 3D terrain home page (`libterrain.so`) and the per-page UX of
  `MainActivity`; the per-dial ring radii/colors are **no longer an open item** — see R1 below.

## 补证 R1（2026-11-05，smali 级 / 内存 DEX）

原版被爱加密加壳，磁盘版方法体被 nop。本节全部结论来自 **Frida 内存 dump**
(`.inspect\unpack\mem_dump\classes06.dex`, 7.4 MB, 7698 classes) 的 baksmali 输出
`.inspect\unpack\mem_smali\`（apktool.jar 内 shaded dexlib2/baksmali 无 main，
用 `.inspect\tools\BaksmaliLauncher.java` 调 API 反汇编；脚本与命令见 `docs\gap-matrix.md` §0）。

### R1.1 构造器字段顺序（把「offset/interval」钉死）
`CompassReadTypeEnum.<init>(Ljava/lang/String;ILjava/lang/String;FFLjava/lang/String;Z)V`：

```smali
iput-object v3, v0, ...->text:Ljava/lang/String;
iput v4, v0, ...->offsetDegrees:F      # 第 4 参 = offsetDegrees
iput v5, v0, ...->intervalDegrees:F    # 第 5 参 = intervalDegrees
iput-object v6, v0, ...->html:Ljava/lang/String;
iput-boolean v7, v0, ...->isVip:Z
```

### R1.2 14 张文字表全部还原（780 条，`getTextList()`）
`getTextList()`（smali 630-7094）用 `packed-switch` key 1..14 分派到 14 个
`new-array` + `const-string` + `aput-object` 块。逐条寄存器仿真还原（脚本
`.inspect\tools\extract_rings.py`），结果写入 `app\mechanics.json` 的 `rings[].labels`
（`labels_status: confirmed_dex`），全量可读版 `.inspect\derived\ring_tables.txt`。

| TYPE | text | 条数 | 首条 | 末条 | smali 行 |
|---|---|---|---|---|---|
| TYPE_1 | 先天八卦_卦位 | 8 | ‌震 | ‌坤 | 7022-7078 |
| TYPE_2 | 后天八卦_卦位 | 8 | 坤 | ‌坎 | 6967-7021 |
| TYPE_3 | 先天八卦洛书数 | 8 | ‌震 4 | ‌坤 8 | 6912-6966 |
| TYPE_4 | 地盘正针二十四山 | 24 | 癸山 | 子山 | 6752-6911 |
| TYPE_5 | 穿山七十二龙 | 72 | 庚子 | 戊子 | 6160-6751 |
| TYPE_6 | 地盘正针百二十分金 | 120 | 甲山 庚子 | 甲山 戊子 | 5136-6159 |
| TYPE_7 | 透地平分六十龙 | 60 | 壬子 壬子 | 壬子 庚子 | 4652-5135 |
| TYPE_8 | 人盘中针二十四山 | 24 | 癸山 | 子山 | 4492-4651 |
| TYPE_9 | 天盘缝针二十四山 | 24 | 子山 | 壬山 | 4332-4491 |
| TYPE_10 | 天盘缝针百二十分金 | 120 | 甲山甲子 | 壬山癸亥 | 3308-4331 |
| TYPE_11 | 先天方图六十四卦内卦 | 64 | 乾小畜 | 乾乾 | 2788-3307 |
| TYPE_12 | 先天圆图六十四卦外卦 | 64 | 坤复一 | 坤坤一 | 2268-2787 |
| TYPE_13 | 六十甲配六十四卦五行 | 64 | 复甲子金 | 坤甲子金 | 1748-2267 |
| TYPE_14 | 正兼向度数指标 | 120 | 癸山九度外 | 子山九度外 | 724-1747 |

**原版数据自带瑕疵（1:1 必须保留）**：TYPE_2 只有 兑七/乾六/艮八/震三/巽四 五个带数字，
坤/离/坎 无数字；TYPE_1 有 7/8 条以 U+200C（零宽非连接符）开头；TYPE_5 有错字
戌寅(=戊寅)/戌午(=戊午)/巳未(=己未)/壬坤 等；TYPE_6 有「已山」(=乙山)；
TYPE_10 的「山名+干支」无空格（TYPE_6 有空格）；TYPE_7 是「双山名 + 干支」两段式
（12 双山 × 5 = 60）；TYPE_5 是 12 组 × (5 龙 + 1「正X」) = 72，**不是**「每山 3 龙」。

**逐条地址**：每条 label 对应自己那条 `const-string` 的指令地址，已导成
`.inspect\derived\ring_label_addrs.json`（`{i, text, code_off, smali_line}`，780 条；
`code_off` 为 dex **code-unit** 偏移，与 `packed-switch` 载荷同一坐标系）。
段级首末地址：TYPE_1 `@108f→@10ab`、TYPE_2 `@106c→@1088`、TYPE_3 `@1049→@1065`、
TYPE_4 `@fe2→@1042`、TYPE_5 `@e59→@fd9`、TYPE_6 `@bb0→@e50`、TYPE_7 `@a6f→@ba7`、
TYPE_8 `@a06→@a66`、TYPE_9 `@99d→@9fd`、TYPE_10 `@6f4→@994`、TYPE_11 `@59b→@6eb`、
TYPE_12 `@442→@592`、TYPE_13 `@2e9→@439`、TYPE_14 `@40→@2e0`。

### R1.3 人盘/天盘的真值：不是 offset，是表旋转（轮 B 关键）
| 盘 | TYPE | offsetDegrees | interval | labels 首位→末位 | 子山中心 | 等价值（MOUNTAINS 子=0） |
|---|---|---|---|---|---|---|
| 地盘正针 | TYPE_4 | 7.5f (`0x40f00000`) | 15.0f | 癸山→子山 | 0° | `floor((h+7.5)/15) % 24` |
| 人盘中针 | TYPE_8 | **0.0f** (`const/4 v11,0x0`) | 15.0f | 癸山→子山 | **−7.5°** | `(floor(h/15)+1) % 24` |
| 天盘缝针 | TYPE_9 | **0.0f** (`const/4 v12,0x0`) | 15.0f | 子山→壬山 | **+7.5°** | `floor(h/15) % 24` |

即：三盘的 24 山名相同，差异**全部**编码在各自 labels 的起点/顺序里；人盘=逆时针 7.5°、
天盘=顺时针 7.5° 与教材一致。**原版不存在第四/第五套 ±7.5 的 offset 常量。**

复算证据（逐指令移植 `getFaceText`，float32）：0.1° 扫频 3600 点，人盘与地盘
**1800/3600 (50.0%) 不同**、天盘与地盘同样 50.0%（`.inspect\derived\readings_check.txt`）。
10°/170°/250° 三方位（同一文件 §2）：
- h=10：地=癸山 人=癸山 天=子山
- h=170：地=丙山 人=午山 天=丙山
- h=250：地=庚山 人=庚山 天=申山

边界行为：h=0 时 地=子山、人=癸山、天=子山；h=15 时 地=癸山、人=丑山、天=癸山。

### R1.4 盘面是位图，不是「圈层集合」
`CommonCompassEnum.<clinit>` 构造 `(name, ordinal, code, text, resId, isVip)`：
COMPASS_0 compass/二十四山(阴)/`R$mipmap.compass`/false；COMPASS_1 compass1/常用罗盘/
`compass1_min`/false；COMPASS_2 compass2/三合罗盘(白)/`compass2_min`/true；
COMPASS_3 compass3/三元罗盘(金)/`compass3_min`/true；COMPASS_4 compass4/综合罗盘(白)/
`compass4_min`/true。全 dex 内 `getResId()` 只被 `CompassToolsKt.smali:3716` 取用作背景图；
`getFaceText/getBackText` 只被 `ui\compose\W$a$b/c/i`（读数面板）调用。

整盘 artwork：`assets/webapp/assets/imgs/compass/` 下 `compass.png` + `compass1..13.png`
共 14 幅（620–2251 px，`compass.png`=1080² 单圈 24 山+360°刻度）+ `pointer.png`(46×188)。
**结论：盘面不「开」圈层；读数面板独立列 14 类读数（向/坐）。**

五幅盘面各自的圈层（12 点钟条带逐圈清点，条带图 `.inspect\derived\dial_strips\*_top.png`）：

| 盘面 | 圈数 | 由外向内（要点） |
|---|---|---|
| COMPASS_0 `compass.png` | 2 | 360°刻度 → 24山(阴)单圈 → 中心圆 |
| COMPASS_1 `compass1.png` | ≈9 | 360°刻度 → 24山 → 72/60龙干支 → 分金(五子/三子/七子) → 后天八卦+洛书数 → 内层24山 → 指北针 |
| COMPASS_2 `compass2.png` | ≈13 | 360° → 28宿 → 纳音五行 → 壬子紫带(72龙) → 分金五子+五行色 → 24山 → 纳音 → 60龙 → 壬子癸紫带(三盘24山) → 申巳艮/乾巳巽(八卦) → 后天卦+卦符 → 指北针 |
| COMPASS_3 `compass3.png` | ≈17 | 360° → 江东/江西·天元/地元/人元 → 卦符 → 九星(贪巨禄文廉武破) → 洛书数 → 玄空数 → 64卦名 → 数字带 → 神煞(破劫刑賓客輔庫正天) → 节气+五行 → 三盘24山(天地人) → 神煞(玉印/劫煞/華蓋/直符/地煞) → 后天卦+卦符 → 指北针 |
| COMPASS_4 `compass4.png` | ≈20 | 360° → 28宿 → 八煞 → 64卦+九星 → 先天八卦 → 后天八卦 → 三盘24山并排 → 120分金 → 72龙 → 60龙纳音 → 中心指向罗盘 |

### R1.5 读数公式逐指令
`getFaceText(F)`：`sub-float offset` → `add 360.0f` → `rem 360.0f` → `div-float interval` →
`float-to-int`（截断，非 floor）→ `0<=i<len` 取 `list[i]`，否则 `"未知"`。
`getBackText(F)`：`add 180.0f` → `rem 360.0f` → `getFaceText`。
在 `h∈[0,360)`、`offset<=22.5` 下 `x+360 ∈ (337.5,720)`，`rem` 后恒 ≥0，故截断 ≡ floor。

### R1.6 知识库与设置页（本轮新增，供产品/客户端）
- KB：`assets/compassIntroduce/` 14 个 HTML（52–60 KB/个），由
  `CompassToolsKt.a1(BaseActivity, boolean, CompassReadTypeEnum)`（smali 1539）→
  `CompassIntroduceActivity$a.a(Context, CompassReadTypeEnum)`（smali 1568）打开枚举的
  `html` 字段。
- 设置：**原版有独立设置页**。`MainActivity.MainDrawerContent (MainActivity.kt:851)` 是入口，
  菜单项字符串：地图设置/账户设置/投诉反馈/关于我们/联系我们/推荐给好友/开具发票/帮助中心/
  用户协议/隐私政策/第三方sdk清单/权限清单；`SettingActivity.kt:64 Item`、`:83 Divider`；
  `MapSettingActivity.kt:269/292/313/325/412/461/545` = 地图滤镜（常规/自然/高亮/自定义）+
  亮度最小/最大/饱和度/对比度/色相旋转 + 恢复默认，预览走
  `https://appassets.androidplatform.net/assets/webapp/index.html`。

## 补证 R2（轮 B2 换表手册：期望值 + 落位）

数据源：`CompassReadTypeEnum.getTextList()` 的 14 张表（逐条地址见 R1.2）+
`<clinit>` 的 `offsetDegrees/intervalDegrees`。**读数与圈层必须共用同一对 `(interval, offset)`**：
读数索引 `i = (int)(((h − offset + 360) % 360) / interval)`，第 i 格文字画在该格中心
`offset + (i + 0.5) × interval`（不是 `i × interval`，也不是格边界）。

### R2.1 期望值（向；坐 = `face((h+180)%360)`，`␣` = U+200C）

| TYPE | 文字 | 0° | 10° | 90° | 170° | 250° | 359.99° |
|---|---|---|---|---|---|---|---|
| 1 | 先天八卦_卦位 | ␣坤[7] | ␣坤[7] | ␣离[1] | 乾[3] | ␣坎[5] | ␣坤[7] |
| 2 | 后天八卦_卦位 | ␣坎[7] | ␣坎[7] | 兑七[1] | 离[3] | 震三[5] | ␣坎[7] |
| 3 | 先天八卦洛书数 | ␣坤 8[7] | ␣坤 8[7] | ␣离 3[1] | 乾 1[3] | ␣坎 6[5] | ␣坤 8[7] |
| 4 | 地盘正针二十四山 | 子山[23] | 癸山[0] | 卯山[5] | 丙山[10] | 庚山[16] | 子山[23] |
| 5 | 穿山七十二龙 | 戊子[71] | 壬子[1] | 辛卯[17] | 庚午[33] | 庚坤[49] | 戊子[71] |
| 6 | 地盘正针百二十分金 | 甲山 戊子[119] | 癸山 甲子[2] | 卯山 己卯[29] | 丙山 癸巳[56] | 庚山 甲申[82] | 甲山 戊子[119] |
| 7 | 透地平分六十龙 | 壬子 庚子[59] | 癸丑 乙丑[1] | 甲卯 癸卯[14] | 丙午 甲午[28] | 庚酉 癸酉[41] | 壬子 庚子[59] |
| 8 | 人盘中针二十四山 | 癸山[0] | 癸山[0] | 乙山[6] | 午山[11] | 庚山[16] | 子山[23] |
| 9 | 天盘缝针二十四山 | 子山[0] | 子山[0] | 卯山[6] | 丙山[11] | 申山[16] | 壬山[23] |
| 10 | 天盘缝针百二十分金 | 甲山甲子[0] | 甲山庚子[3] | 卯山乙卯[30] | 丙山丁巳[56] | 申山庚申[83] | 壬山癸亥[119] |
| 11 | 先天方图六十四卦内卦 | 乾小畜[0] | 兑中孚[1] | 乾大畜[16] | 艮谦[30] | 震噬嗑[44] | 乾乾[63] |
| 12 | 先天圆图六十四卦外卦 | 坤复一[0] | 艮颐六[1] | 坤临一[16] | 兑夬四[30] | 巽涣二[44] | 坤坤一[63] |
| 13 | 六十甲配六十四卦五行 | 复甲子金[0] | 颐丙子水[1] | 临乙卯水[16] | 夬癸巳水[30] | 涣戊申土[44] | 坤甲子金[63] |
| 14 | 正兼向度数指标 | 癸山九度外[0] | 癸山九度内[3] | 乙山九度外[30] | 午山九度内[56] | 庚山九度内[83] | 子山九度外[119] |

（复算脚本：逐指令移植 `getFaceText`，float32；0.1° 扫频与边界见
`.inspect\derived\readings_check.txt`。）

### R2.2 轮 B2 换表清单（当前仍是教材推导的圈）

| 圈 | 现在 | B2 应改为 | 陷阱 |
|---|---|---|---|
| TYPE_5 72龙 | `wangxiang(borrow_branch(m), dragon72_slot)`，slot 上限 2 | 表 72 条，`i=(int)(((h−2.5+360)%360)/5)` | 每山 5 龙 + 每干维 1「正X」，**不是每山 3 龙**；错字 戌寅/戌午/巳未/壬坤 照抄 |
| TYPE_6 120分金 | 同上，slot 上限 4 | 表 120 条，`i=(int)(((h−1.5+360)%360)/3)` | `"{山名} {甲子}"` 有空格；「已山」=乙山照抄；八干四维借支是原版数据 |
| TYPE_7 60龙 | `JIAZI[dragon60]` 单干支 | 表 60 条，`i=(int)(((h−1.5+360)%360)/6)` | 两段式 `"{双山} {干支}"`；表旋转过，0° = `壬子 庚子`（**不是癸亥**） |
| TYPE_10 天盘分金 | 未实现 | 表 120 条，`i=(int)(((h+360)%360)/3)` | `"甲山甲子"` **无空格**，与 TYPE_6 只差表内容与空格 |
| TYPE_11/12/13 64卦 | 未实现 | 各自 64 条，`i=(int)(((h+360)%360)/5.625)` | 三张表文字不同（卦名 / 卦名+数 / 卦名+干支+五行） |
| TYPE_1/2/3 八卦 | 教材 8 卦序 | 各自 8 条，`i=(int)(((h−22.5+360)%360)/45)` | TYPE_2 只有 5 个卦带数字；TYPE_1 有 7/8 条带 U+200C，照抄 |
| TYPE_14 正兼向 | ±4.5° 判正向 + 「正向/兼向X山」 | 表 120 条，`i=(int)(((h+360)%360)/3)` | 9° 阈值可留，但**文字**必须是 `"X山九度外/内"` |

不动：TYPE_4（已同语义）、TYPE_8/9（offset=0 + 表旋转已等价）。

**排字提醒（客户端 backlog 的量化依据）**：换表后 72龙/120分金/60龙 的文字从 2–3 字变成
3–6 字（`癸山 甲子`/`壬子 庚子`），120 格时分金每格仅 3°、同一半径文字必然叠；
原版靠整幅位图排版规避，矢量重建需「只画当前选中圈 + 相邻圈半透明」或按缩放分级显隐。

## 补证 R3（盘面目录的数据源：服务端，不是常量）

**结论：原版「罗盘切换」页的盘面目录来自服务端接口，APK 内不存在完整列表；5 个
`CommonCompassEnum` 盘面是唯一可静态证实的部分。**

证据链：

1. 接口：`space.xinzhi.globe.net.ApiService.getCompassList()`
   `@GET "api/compass/list"` → `Call<ApiResult<List<CompassModel>>>`（retrofit2，
   `.inspect\unpack\mem_smali\space\xinzhi\globe\net\ApiService.smali`）。
   `CompassModel` 字段：`id/name/label/categoryName/img/previewImg/vip`。
2. 分组在客户端按 `categoryName` 做：`CompassListActivity$b$a$a.smali:311`
   `CompassModel.getCategoryName()`（同 `$e$a.smali:78`）。
3. **否定性证据**：把「简易罗盘 / 极简白底 / 极简红底 / 入门 / 罗门八局 / 十二长生水法 /
   九元罗盘 / 廿十四山 / 传统罗盘」共 9 个词，在 **30 个 Frida 内存 dump dex** 里按 UTF-8
   逐字节搜索 → **全部 0 命中**；`decoded/res/values/strings.xml` 与 `decoded/assets/`
   同样 0 命中。只有「常用罗盘」「综合罗盘」（= CommonCompassEnum 名称）与「上传罗盘」
   各 1 处。即：组名与条目名**运行时从网络取得**。
4. `CompassCategoryModel`（`categoryName` + `List<CompassModel>`）**全 dex 无引用**，是历史死类。
5. `CompassManager` 只负责「选中盘面 + 自定义上传」：MMKV key `lastSelected`、
   自定义目录 `files/compass/custom`、事件 `event_last_compass_changed`、
   `CommonCompassEnum.getCode()/getDefault()`。它不持有目录。

**规模**：真机实测「罗盘切换」页滚动中可见 ≥27 个盘面（`.inspect\derived\orig-shots\dial-switch.png`、
`dial-switch3.png`），但**精确总数不能从 APK 静态确定**；要定死需读设备 MMKV 缓存
（key `compassModel`）或抓 `api/compass/list` 响应（需网络 + 登录态）。

**对 1:1 的含义（供产品/总监）**：可 1:1 的是「四组结构 + 分组 UI + 常用组 5 项」；
简易组/综合组的**条目内容**是服务端数据，离线复刻不可能穷举 → 建议登记为
「等价替代（内置若干常见盘面）」或「有意省略（仅保留常用 5 项）」，二者都要在差距矩阵里写死，
不能按「27 个」排期。

### R3b MMKV 实读（`run-as`，2026-11-05，包 `space.xinzhi.globe`）

`files/mmkv/` 下 10+ 个库：`AccountManager` / `AppConfig`(16 KB) / `CompassManager`(4 KB) /
`ComplaintGuidanceManager` / `GuideHelper` 等。逐库导出后按可打印串/UTF-8 串扫描：

1. **盘面目录没有本地缓存**：`CompassManager` 库 4 KB，**0 条可打印字符串**（只有
   `lastSelected` 之类短键）；`AppConfig` 里也没有任何盘面名/`categoryName`。
   → 「读缓存数条目」这条路走不通，条目数只能抓 `api/compass/list` 响应或 UI 滚到底。
   （B 方案下这不影响实现，只影响“将来想加盘面”时的依据。）
2. **地图配置全在 `AppConfig`（服务端下发 JSON 明文缓存）**，对地图模块是硬证据：
   - `appMapSourceList`：**12 个底图源**，`中科星图 / 吉林一号-全球 / 吉林一号-全国 / 世纪空间 /
     天地图卫星 / 天地图地图 / 天地图地形 / 高德卫星 / 高德地图 / 腾讯卫星 / 腾讯地图 / 腾讯地形`，
     `defaultMapCode = siJi_satellite`（**默认不是天地图**）。
   - `mapReviewNum`：每个源对应审图号（形如 `GS(2026)xxxx号` + 「数据来自…」），
     → 地图页必须显示当前源审图号，这是合规项。
   - `mapToken`：`tianMapTokens`（**16 个天地图 token 明文**）、`mapboxTokens`、`baiduStreetToken`、
     `xintuTokens{SecretID,SecretKey}` —— 敏感凭据以明文落 MMKV；**值不入库、不进群**，
     仅记录「存在且字段名如此」，LC 侧一律用自己的 key。
   - `competeAppPkg`（竞品包名表）、`adCloseConfig`、`payWayConfig`、`functionHideForPassAudit`、
     图层枚举（水体=water/river/canal/lake/reservoir/basin；植被=forest/meadow/scrub/wetland）。
3. `AccountManager` 里是登录态明文（id/nickname/phone/token）——去商业原则下 LC 不实现账号体系，
   仅记录在案，**不复制值**。

## 补证 R4（14 类文字渲染在哪：`CompassReadSheet`，已定）

**结论：14 类读数渲染在 `space.xinzhi.globe.ui.compose.CompassReadSheet` 的底部弹层里，
不是罗盘页面上的常驻文本。**

证据：

1. `ui\compose\W.smali` 的 `.source` 字符串即类名与文件行号：
   `space.xinzhi.globe.ui.compose.CompassReadSheet (CompassReadSheet.kt:54)`、
   `space.xinzhi.globe.ui.compose.ReadTypeItem (CompassReadSheet.kt:125)`。
2. `W.smali` 里的界面文案（4 条）：`点击查看`、`向：`、` 坐：`（坐前面有空格，照抄）。
   → 每行 `ReadTypeItem` = 「类型名 + 向：X + 坐：Y + 点击查看」。
3. `getFaceText/getBackText` 的调用点只有 `W$a$b` / `W$a$c` / `W$a$i`（这三个是 `ReadTypeItem`
   的 Compose lambda），即**弹层里的行**在取文字；罗盘页的 ring 绘制**不调用** `getFaceText`。
4. 打开入口：`CompassToolsKt.n0(BaseActivity, MainViewModel, Function0, …)`
   （`CompassToolsKt.smali` 第 14591 行）→ `Lspace/xinzhi/globe/ui/compose/W;->f(FLSheetState;…)`，
   传入当前 heading 与 `SheetState`。按钮文案：罗盘页左列第 5 个 **「读盘」**
   （`CompassToolsKt.smali:9763`）；工具浮层里另有 **「解读」**（`:776`），二者是否同一弹层**未确认**。
5. 「点击查看」→ `CompassToolsKt.a1(BaseActivity, boolean, CompassReadTypeEnum)`（`.smali:1539`）
   → `CompassIntroduceActivity$a.a(Context, CompassReadTypeEnum)`（`:1568`），带该类的 `html`。

**行位置（真机截图坐标，1228×2700，`win-02-compass.png` 左列）**：截屏 y≈1340 / 等高线 1500 /
罗盘复位 1690 / 罗盘锁定 1880 / **读盘 2070** / 贴地 2250，按钮中心 x≈132。
（上一轮 30 分钟窗口两次点 2070 之外（1957/2196）→ 弹层没打开，**不是功能缺失**，记录纠正。）

**对 P0-2 的含义**：读数面板不必自创布局，按原版做「14 行 × (向：/坐：) + 点击查看」即可；
数据就是 `rings[].labels` + `getFaceText/getBackText` 公式；「解读/读盘」两个入口的差异
留待真机确认后定。

### R4b 弹层实测（真机，2026-11-05，`orig-shots/w3-sheet.png`）

点 (132,2070) 打开的左列第 5 个按钮，标题是 **「智能读盘」**，内容结构（h=0）：

```
向北 0.0°                                  离宅
坐南 180.0°                                坐午山 向子山
先天八卦_卦位            向：坤  坐：乾  ›
后天八卦_卦位            向：坎  坐：离  ›
先天八卦洛书数           向：坤 8 坐：乾 1 ›
地盘正针二十四山     🔒                         点击查看 ›
穿山七十二龙         🔒                         点击查看 ›
地盘正针百二十分金    🔒                         点击查看 ›
透地平分六十龙       🔒                         点击查看 ›
人盘中针二十四山      🔒                         点击查看 ›   （以下同类，共 11 行）
```

**运行期与 DEX 表逐字一致（h=0）**：先天八卦_卦位 `向坤/坐乾` ↔ TYPE_1 `labels[7]=␣坤`、
`face(180)=␣乾`；后天 `向坎/坐离` ↔ TYPE_2；洛书 `坤 8 / 乾 1` ↔ TYPE_3。三行全对，
**这是 14 张表的第一份非静态验证**（其余 11 行因 VIP 不显示文字）。

**关键产品事实**：`isVip=true` 的 11 行**只显示 🔒 + 点击查看，不显示向/坐**。所以
「读数面板列出 14 类」只有在免费层成立；P0-2 若照抄付费墙，就与「去商业化」冲突，必须
明写取舍（建议：全显示，但在文档标注「原版此处为付费墙」）。

**注入进展（未完成，如实记）**：`MainActivity.onSensorChanged` 钩子**命中 194 次**
（`hits()=194` 证明注入进到了传感器回调），但同一实现里调用原方法报
`TypeError: not a function`（frida-java-bridge 的字段/方法调用惯例问题），原回调未执行、
读数不动；`getFaceText` 调用 0 次（弹层未重算）。下个窗口改走
`SensorManager.getOrientation` 出参改写（v1 不报错但改写无效，需查 UI 侧 heading 状态）
或直接改 `CompassToolsKt` 的 heading 状态变量。**三方位截图仍未完成（第 2 次尝试）**，
但「第二证据」的目的已由 h=0 的弹层实测达成。

## 补证 R5（盘面几何与配色：代码里没有这些常量；实测值，2026-11-06）

### R5.1 结论先行：原版没有「圈层半径 / 盘面配色」常量可抄

- `ui/tools/CompassToolsKt.smali`（451 KB、133 方法）全文件 `const-wide` **只有 9 条**：
  `180.0`×2、`360.0`×2、`0xffffffff`（白）×4、`0xcc000000`（黑 80%）。**没有任何半径/角度步进/调色板常量**。
- 盘面是**整张位图**：`ImageKt.Image(painter, …)`（`:4281`、`:14282`）绘制，
  外层 `GraphicsLayerScope{ setScaleX/setScaleY/setRotationZ }`（`:4522`/`:4540`/`:4584`/`:4800`/`:17593`）旋转。
  即「半径 = 容器尺寸、角度 = rotationZ」，**没有逐圈矢量绘制**。
- 因此要 1:1 只能按位图实测（R5.4/R5.5 即实测值）。
  > 更正本文件 §CompassToolsKt 里「the exact per-ring pixel radii/colors are … not recovered」：
  > 不是没解出来，而是**本来就不存在**。

### R5.2 5 个内置盘面（`common.CommonCompassEnum`，构造签名 `(String name, int ordinal, String code, String text, int resId, boolean isVip)`）

| 枚举 | code | 文案 | mipmap | isVip |
|---|---|---|---|---|
| COMPASS_0 | `compass` | 二十四山(阴) | `R.mipmap.compass`（xxxhdpi，1080×1080） | false |
| COMPASS_1 | `compass1` | 常用罗盘 | `R.mipmap.compass1_min`（xhdpi，160×160） | false |
| COMPASS_2 | `compass2` | 三合罗盘(白) | `R.mipmap.compass2_min`（160×160） | **true** |
| COMPASS_3 | `compass3` | 三元罗盘(金) | `R.mipmap.compass3_min`（160×160） | **true** |
| COMPASS_4 | `compass4` | 综合罗盘(白) | `R.mipmap.compass4_min`（160×160） | **true** |

- `default` = **COMPASS_0**（`<clinit>` @#@55–#@69，v0 仍指向 COMPASS_0）。
- 注意：**只有默认盘面有全分辨率资源**，其余 4 个原生绘制用的是 160×160 缩略图（`getResId()` 取自枚举，`CompassToolsKt:3716`）——这是原版自身的精度限制，不是我们取不到。

### R5.3 盘面资产实测（`assets/webapp/assets/imgs/compass/`，14 张）

| 文件 | 分辨率 | 真实格式 |
|---|---|---|
| compass.png | 1080×1080 | PNG |
| compass1.png | 2048×2048 | PNG |
| compass2.png | 2048×2048 | PNG |
| compass3.png | 2045×2045 | PNG |
| compass4.png | 2048×2048 | PNG |
| compass5.png | 1080×1080 | **WebP** |
| compass6.png | 620×620 | **WebP** |
| compass7.png | 2251×2251 | **WebP** |
| compass8.png | 1684×1684 | PNG |
| compass9.png | 1500×1500 | **WebP** |
| compass10.png | 2040×2040 | **WebP** |
| compass11.png | 2040×2040 | **WebP** |
| compass12.png | 2042×2040 | **WebP** |
| compass13.png | 2042×2040 | **WebP** |
| pointer.png | 46×188 | PNG(RGBA) |

> 8 个 `.png` 后缀文件实际是 WebP（`RIFF…WEBP` 头）——按后缀当 PNG 解码会失败，客户端按真实格式处理。

### R5.4 默认盘面 `compass.png`（二十四山(阴)）实测几何

- 画布 1080×1080；圆盘外缘 **r = 476 px**（= 0.8815 × 半画布 540），有效直径 952 px = 画布 **88.15%**。
- 三道整圆（白底合成后按「该半径上黑像素占比 >55%」径向扫描 1px 步进）：**374 / 451 / 476 px**，
  折算到圆盘外缘 = **0.7857 / 0.9479 / 1.0000**。

| 环带 | 半径 px | 占外缘 R | 内容 |
|---|---|---|---|
| 空白芯 | 0–374 | 0–0.786 | 空（透明/白） |
| 二十四山环 | 374–451 | 0.786–0.948 | 24 等分 × 15° |
| 刻度带 | 451–476 | 0.948–1.000 | 1° 细刻度；每 10° 度数数字画在**外缘之外** |
| 度数数字 | 476–≈530 | 1.0–≈1.11 | 0/10/…/350，位于圆盘外 |

### R5.5 配色实测

- `compass.png` 可见像素 top：`#FFFFFF` **41.5%**、`#000000` **28.95%**，其余为抗锯齿灰（`#101010`/`#EFEFEF`/`#BFBFBF` 各 ≈1%）
  → 默认盘面就是**黑白两色**（「阴」= 黑字白底）。
- 其余 4 个内置盘面只有 160×160 缩略图，取色仅作参考、**不作正式配色表**：
  compass1 主金 `#F4C330`；compass3 主金 `#BEA149`；compass2 / compass4 近白（白盘）。
- 指针 `pointer.png`：`#656565`(灰) 20%、`#E71818`/`#A81414`(红) 29%、`#052750`/`#073470`(深蓝) 22%。

### R5.6 对 LC 的落点

- 客户端 Canvas 画圈按 R5.4 的归一化值即可（空白芯 0.786R / 24山环 0.786–0.948R / 刻度带 0.948–1.0R / 度数数字在 1.0–1.11R），**不要再去猜「原版半径常量」——它不存在**。
- 配色：免费层用「黑白（阴）」，白/金盘按 R5.5 给的主色做近似；4 个盘面的正式配色记为**未确认**，需真机放大截图后取色。

**取证脚本**：`E:\APPDetector\lc-gap-evidence\probe\ring-profile.py`（径向暗线检测）、`dial-analyze.py`（尺寸/主色/圆盘外缘）。
**证据来源**：`mem_smali`（方法体真实）＋ `decoded/res`、`decoded/assets/webapp/assets/imgs/compass`（APK 内原始资源）。
