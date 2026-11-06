# R2-025 终验证据包（develop `2c9a203`，2026-11-07 00:27–00:32）

判词见 `docs/fidelity/verify-p0-judge.md` §4.6 / §5；本目录只放可复核的原始件。

## 被测包

- 源码：`E:\APPDetector\develop-int`，`git rev-parse HEAD` = `2c9a203e837642eec590f6a49b554899c5b4aa69`（develop）
- 构建：`gradle assembleDebug --no-daemon` → `BUILD SUCCESSFUL in 40s`
- APK：`app/build/outputs/apk/debug/app-debug.apk`，7,165,085 B，
  sha256 `760926FC8FA29B5965F80EE04A7E1FDBABC3CC2C28B1AEC7F1A150A6B97905E7`
- 安装：`adb push … /data/local/tmp/lc.apk` + `pm install -r -t` → `Success`；`lastUpdateTime=2026-11-07 00:27:41`
- 设备：`L2E0222531014833`（JAD-AL00）；全程前台 `com.luopan.fengshui/.MainActivity`

## 文件清单

| 文件 | 内容 |
|---|---|
| `lcsearch-stream.txt` | 判据 ①/② 的整段流式日志（`adb logcat -v time -s LCSEARCH` 重定向，PID 18762） |
| `lcsearch-cold.txt` | 冷启动复跑整段日志（PID 19569） |
| `v2-before-tap.xml` | 广播「故宫」后、点按前的 dump：`lc_search_tab1` bounds `[369,803][613,916]`，输入框 `text=故宫` |
| `v2-after-tap.xml` | 点按后的 dump |

未入库的图片（留 sha256 备查，`E:\APPDetector\reports\stage4-verify\s6\`）：

- `v2-after-tap.png`：`9f8977011d40e942ec84d71cd875b2e1eb99530a8a06ae5651b4a4a031881d5f`（弹层内「境外」高亮，列表 `故宫博物院 116.390865,39.914896`）

## 关键日志（逐字）

判据 ① `--ei tab 1`：

```
11-07 00:29:36.196 D/LCSEARCH(18762): debug search=故宫 tab=1
11-07 00:29:36.232 D/LCSEARCH(18762): search start kw=[故宫] tab=1 center=39.9042,116.4074
11-07 00:29:36.911 D/LCSEARCH(18762): tab=1 code=ok total=1283 first=故宫博物院
11-07 00:29:36.913 D/LCSEARCH(18762): ui rows=20 first=故宫博物院  116.390865,39.914896
```

判据 ② 真实点按（`input tap 491 860`）：

```
11-07 00:30:06.972 D/LCSEARCH(18762): tab-click idx=1 active=1 ref=true
11-07 00:30:06.975 D/LCSEARCH(18762): search start kw=[故宫] tab=1 center=39.9042,116.4074
11-07 00:30:07.125 D/LCSEARCH(18762): tab=1 code=ok total=1283 first=故宫博物院
```

冷启动复跑：`00:31:01.871 tab-click` → `00:31:02.109 tab=1 code=ok total=1283`（238ms）。

## 复跑命令

```powershell
# 构建 + 装包
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
$env:PATH="$env:JAVA_HOME\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;$env:PATH"
gradle assembleDebug --no-daemon     # 工作目录 app/android
adb -s L2E0222531014833 push app-debug.apk /data/local/tmp/lc.apk
adb -s L2E0222531014833 shell pm install -r -t /data/local/tmp/lc.apk
```

```powershell
# 全流程脚本（含设备前置、判据 ①②、冷启动复跑、收尾复位）
pwsh -NoProfile -File E:\APPDetector\reports\stage4-verify\stage6b.ps1
pwsh -NoProfile -File E:\APPDetector\reports\stage4-verify\stage6c.ps1
```

## 纪律备注

- 日志断言不要写成 `-match 'LCSEARCH tab='`：实际行是 `D/LCSEARCH(pid): tab=…`，tag 与消息之间隔着 `(pid): `，该模式永远不命中（本轮抓到的自方测量缺陷，见判据表 §4.4 C）。
- 抓 logcat 用流式重定向到文件；`logcat -d` 在本机 12 秒内就可能被刷掉。
