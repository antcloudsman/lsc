# swss.apk 复刻工程（三维山水全景地图 → 罗盘/地图）

对 `E:\APPDetector\input\swss.apk`（`space.xinzhi.globe`，一个风水罗盘 + 天地图 App）的反编译 + 复刻工程。已去掉全部广告/收费逻辑，保留被收费的完整功能模块。

## 目录结构
- `app/`（本目录）：Rust + eframe/egui **桌面版**罗盘（用于 Craft Anything 的自动检查）。
- `app/android/`：**Android APK 工程**（Gradle 8.7 + Java 17 + compileSdk 34，可编译、可改）。
- `app/MECHANICS.md`：反编译得到的**所有真实数值/公式/规则 + 函数地址**（交接必读）。
- `app/mechanics.json`：机器可读的数值表（19 项，全部有来源、0 项估计）。

## 已实现（与原版功能一致）
- **罗盘**：24 山、地盘/人盘/天盘三盘（0/−7.5/+7.5°）、百二十分金（3°）、穿山七十二龙（5°）、
  透地平分六十龙（6°，偏移 1.5°）、六十四卦（5.625°）、先天/后天八卦、五行/阴阳、正兼向
  （正向中央 9°）。5 种盘面切换、传感器自动定向 + 手指拖动、完整读数面板（分金/龙用
  「山名+甲子」格式，与反编译一致）。
- **地图**：天地图（WMTS 瓦片，APIKEY 内置）底图 + 矢量/影像/地形切换 + 3D 俯仰 +
  旋转 + 罗盘坐向指针 + 全屏 + 截图 + 立向标记 + 定位。
- **知识库**：列表 → 详情导航（5 盘面 + 14 圈层 + 8 知识条目）。

## 还需逆向/完善的部分（交接给下一个 agent 时重点）
1. **Compose 逐像素圈层半径/配色**（`CompassToolsKt` 的 `A1→z1→…` DrawScope lambda，纯 UI、
   深度混淆）：价值低，当前 Canvas 实现已等价。可继续逐个 `const` 常量抽取。
2. **3D 地形引擎**：`libterrain.so` 内是 `vmInterpret`（Dalvik VM 解释器，即 VM 保护字节码），
   无法静态反编译；已用天地图地形底图 + CSS 3D 透视等价替换。要更真 3D 可接天地图 DEM + WebGL。
3. **原版 148 个 Activity / 481 方法的 `MainActivity`** 里的社交/发现/直播等次要页面：按
   「去商业」要求已省略，如需 1:1 页面/链接可继续从 `ui/pages/*` 反编译逐个补。

## 构建
### 桌面版（Rust）
```bash
cd app && cargo build
```

### Android APK（本机已装好工具链）
```bash
set JAVA_HOME=C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9
set PATH=%JAVA_HOME%\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;%PATH%
cd app/android && gradle assembleDebug --no-daemon
# 产物 app/android/app/build/outputs/apk/debug/app-debug.apk
```
也可用 Docker：`cd app/android && docker compose up --build`（镜像含 JDK17 + Gradle8.7 + Android SDK）。

真机安装：`adb -s <设备> install -r app-debug.apk`（已连接的华为设备 ID 为 `L2E0222531014833`）。

## 反编译工具链（如需继续逆向）
- 脱壳后的真实 DEX 在 `.inspect/unpack/dex_dump/`（磁盘版方法体被 nop）和
  `.inspect/unpack/mem_dump/`（Frida 内存 dump，方法体真实）。用 craft-anything 的
  `android_class_show`/`android_method_show` 指向这些 .dex 即可读真实字节码。
- Frida-gadget 注入流程：apktool 解码 → `android:debuggable="true"` + `System.loadLibrary("fridagadget")`
  → 重打包（resources.arsc 需 STORED）→ zipalign → apksigner → 装真机 → `frida-dexdump -H 127.0.0.1:27042 -n Gadget`。
