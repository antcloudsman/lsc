# 罗盘 · 风水（Android APK 工程）

这是对 `swss.apk`（三维山水全景地图）的 Android 复刻工程，去掉了所有商业收费 / 支付逻辑。

## 已实现
- **罗盘**：可旋转的风水罗盘（二十四山、地盘/人盘/天盘三盘、百二十分金、穿山七十二龙、
  透地平分六十龙、八卦），每座山按五行配色；支持手机传感器自动定向，也支持手指拖动旋转。
- **地图**：底图采用**天地图**资源（WMTS 瓦片），支持矢量 / 影像 / 地形三种底图和注记层，
  拖动平移、按钮与滚轮缩放。APIKEY 已内置在 `app/src/main/assets/map.html`。
- **知识库**：二十四山、三盘、分金、龙、正兼向、八卦、五行等解释。
- 无任何内购、订阅、广告或支付 SDK。

## 构建（本机已验证通过）
本机已安装 Temurin JDK 17（`C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9`）和
Gradle 8.7（`C:\Users\Administrator\tools\gradle_tmp\gradle-8.7`），Android SDK 在
`%ANDROID_HOME%`（compileSdk 34）。

```bash
set JAVA_HOME=C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9
set PATH=%JAVA_HOME%\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;%PATH%
cd app/android
gradle assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（已安装到华为真机验证，界面正常）。

## 用 Docker 构建（可选）
```bash
cd app/android
docker compose up --build
```

Docker 镜像内含 JDK 17 + Gradle 8.7 + Android SDK（Linux），源码留在宿主机挂载。
（国内拉取 Gradle/Android SDK 镜像可能较慢。）

## 说明
- 原 App 用爱加密壳（`libshell-super.space.xinzhi.globe.so`，v4.6.2.2）加密 DEX；真实
  Application 为 `space.xinzhi.globe.App`，加密 DEX 在 `assets/0OO00l111l1l`。本工程是按
  可见功能重新实现的等价版本，不包含原壳。
- 天地图 APIKEY 在 `map.html` 顶部 `KEY` 常量处，可替换。
- 应用名与包名（`com.luopan.fengshui`）为中性占位，可按需修改。
