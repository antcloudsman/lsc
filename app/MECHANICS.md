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
- The ring drawing lives in `DrawScope` lambdas (obfuscated `A..Z`/`a..b1` methods taking
  `androidx.compose.ui.graphics.drawscope.DrawScope`, plus `A1`/`D`/`b0` Compose
  composables). Extracting exact per-ring radii/colors needs reading those Compose lambdas;
  the rebuild reproduces the same rings with Canvas but not the exact Compose radii.
  (Concretely, `A1(Z,F,F,F,String,…,Composer,I)` is only a recompose wrapper that forwards to
  `z1(...)`; the actual arc/text drawing and its `const` radii live deeper in that obfuscated
  Compose chain + the `CompassToolsKt$…` lambdas, so the exact per-ring pixel radii/colors are
  UI-only and not recovered.)
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
（`compass4.png` 的逐圈清单本轮未目视清点 → 未确认。）

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
