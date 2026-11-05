# 探针工具（结构取证用）

用于「跳过 UI 收费门、直达真实页面」取证。**只用于摸清功能、交互、字段与逻辑，不提取、不转存付费数据。**

## 前置

1. 设备安装 `swss_gadget_final.apk`（debuggable + frida-gadget，同版本 1.6.1）。
2. `adb forward tcp:27042 tcp:27042`
3. 本机 `frida` 能连上：`frida-ps -H 127.0.0.1:27042` 应列出 `Gadget`。

## 用法

### A. 直启非导出 Activity（一次性）

```powershell
# 1) 把模板里的 __TARGET__ 换成目标类名
$tpl = Get-Content docs/features/tools/probe-launch.template.js -Raw
$js  = $tpl.Replace('__TARGET__', 'space.xinzhi.globe.ui.pages.WeatherActivity')
Set-Content probe_tmp.js $js -Encoding UTF8

# 2) 注入（CLI 会在脚本加载后退出，但 startActivity 已入主线程队列）
frida -H 127.0.0.1:27042 -n Gadget -l probe_tmp.js -q

# 3) 取证三件套
adb -s <serial> shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png shot.png
adb -s <serial> shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml shot.xml
```

### B. 保持 hook 常驻（需要点击流程时）

frida CLI 在 stdin 结束时会退出，用「慢速生产者」把 stdin 撑住：

```powershell
$frida = (Get-Command frida).Source
& { 1..180 | ForEach-Object { Start-Sleep -Seconds 1; '' } } |
  & $frida -H 127.0.0.1:27042 -n Gadget -l docs/features/tools/probe-hold.template.js -q
```

在 180 秒窗口内用 `adb shell input tap x y` 点击，`isVip` hook 保持为 true。

## 已知坑

1. **Python frida 绑定注入后 `Java` 未定义**（`typeof Java === "undefined"`），CLI `frida` 正常；一律走 CLI。
2. 注入后 App 可能重启（Splash → 广告 → Guide → Main），采集前先等主界面稳定。
3. frida 与 uiautomator 同时占用会互相干扰，采集脚本要串行。
4. `am start` 启动非导出 Activity 会被 `not exported` 拒绝，必须走本模板的 `ActivityThread.currentApplication().startActivity()`。

## 文件

| 文件 | 用途 |
|---|---|
| `probe-launch.template.js` | hook `AccountModelKt.isVip`→true，并以 App 自身 UID 起动 `__TARGET__` |
| `probe-hold.template.js` | 只做 `isVip` hook，常驻，供手工点击 |
