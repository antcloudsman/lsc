# B-02 字节码证据：release 变体不注册调试桥

> 用途：B-02（DebugBridge 导出广播可注入任意 JS）的**静态 + 字节码**证据。
> 基线：`fix/p0-review-findings` @ `e2a51a5`（修复基点 `d752efa`）。
> 证据等级口径：**已证实（静态 + 字节码）；运行态未验**（原因见 §6，不得写成「已验证通过」）。
> 记录人：LC审客户端；记录时间：2026-11-05。

---

## 1 结论（一句话）

release APK 的 manifest 无 `application-debuggable`（`aapt` 计数 0）→ `isDebuggable()` 编译为 `flags & 2` 判断并返回 0 → `registerDebugBridge()` 在 `0x000c` 提前 `return-void`，到不了其后 `0x002e`/`0x0034` 的两条 `registerReceiver`。因此 release 包上不存在调试广播接收器，`cmd=js`/`cmd=url` 不可达。

---

## 2 构建与产物

构建（本地 Gradle 8.7 + JDK 17，不落 `review-base`）：

```
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
gradle.bat -p E:\APPDetector\fix-p0\app\android assembleDebug assembleRelease --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 21s / 75 actionable tasks: 75 executed
```

release 产物未签名（`app/build.gradle` 无 `signingConfig`），按总监口径用本机 debug keystore 签一次再验：

```
apksigner.bat sign --ks %USERPROFILE%\.android\debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out app-release-signed.apk app-release-unsigned.apk
sign exit=0
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: f09cca9d499f0aed62797365bb883ebb18ec99b35be1d97ad768816360e9d237
```

被验产物与哈希：

| 项 | 值 |
|---|---|
| APK | `app/android/app/build/outputs/apk/release/app-release-signed.apk` |
| APK SHA256 | `E804C8F5EE4B459D07943CBBFE162EC1B857DE329A0ED0CBB89F1ABDAE714281` |
| 包内 `classes.dex` | 5,753,900 字节 |
| `classes.dex` SHA256 | `E00F98F94AC38D32D00B23B6DD7C50E0A607373728C81238C5F8B58C60A67AD1` |
| DEX 版本（dexdump 首行） | `DEX version '037'` |
| dexdump | `C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\35.0.1\dexdump.exe`（build-tools **35.0.1**；`dexdump` 无 `--version`，故以 build-tools 版本 + 二进制哈希标识） |
| dexdump SHA256 | `6B19A7075C13367A7AD5E30E25143CCC99C3AB525B3F3357209C95CF538B6D4C` |

`aapt dump badging` 的 debuggable 标记（release 0 处、debug 1 处）：

```
release_badging_debuggable_count=0     # aapt.exe dump badging app-release-signed.apk | Select-String 'application-debuggable'
debug_badging_debuggable_count=1       # 同一命令对 app-debug.apk
```

---

## 3 反汇编（`dexdump -d`）

命令（完整原文）：

```
dexdump.exe -d E:\APPDetector\tmp-b02dex\classes.dex > E:\APPDetector\tmp-b02dex\dump.txt
Processing 'E:\APPDetector\tmp-b02dex\classes.dex'...
Opened 'E:\APPDetector\tmp-b02dex\classes.dex', DEX version '037'
# dump.txt 共 1,049,431 行
```

### 3.1 `MainActivity.isDebuggable:()Z`（方法 #36，代码起始 `0x1c89e4`）

```
    #36              : (in Lcom/luopan/fengshui/MainActivity;)
      name          : 'isDebuggable'
      type          : '()Z'
      access        : 0x0002 (PRIVATE)
1c89e4:                                        |[1c89e4] com.luopan.fengshui.MainActivity.isDebuggable:()Z
1c89f4: 6e10 9857 0100                         |0000: invoke-virtual {v1}, Lcom/luopan/fengshui/MainActivity;.getApplicationInfo:()Landroid/content/pm/ApplicationInfo; // method@5798
1c89fa: 0c00                                   |0003: move-result-object v0
1c89fc: 5200 2a00                              |0004: iget v0, v0, Landroid/content/pm/ApplicationInfo;.flags:I // field@002a
1c8a00: dd00 0002                              |0006: and-int/lit8 v0, v0, #int 2 // #02
1c8a04: 3800 0400                              |0008: if-eqz v0, 000c // +0004
1c8a08: 1210                                   |000a: const/4 v0, #int 1 // #1
1c8a0a: 2802                                   |000b: goto 000d // +0002
1c8a0c: 1200                                   |000c: const/4 v0, #int 0 // #0
1c8a0e: 0f00                                   |000d: return v0
      positions     :
        0x0000 line=698
```

`#int 2` 即 `ApplicationInfo.FLAG_DEBUGGABLE`：manifest 无该 flag → `flags & 2 == 0` → 返回 0。

### 3.2 `MainActivity.registerDebugBridge:()V`（方法 #63，代码起始 `0x1c9c58`）

```
    #63              : (in Lcom/luopan/fengshui/MainActivity;)
      name          : 'registerDebugBridge'
      type          : '()V'
      access        : 0x0002 (PRIVATE)
1c9c58:                                        |[1c9c58] com.luopan.fengshui.MainActivity.registerDebugBridge:()V
1c9c68: 1a00 e124                              |0000: const-string v0, "LCDEBUG" // string@24e1
1c9c6c: 1a01 fd05                              |0002: const-string v1, " action=com.luopan.fengshui.DEBUG" // string@05fd
1c9c70: 1a02 5856                              |0004: const-string v2, "bridge registered sdk=" // string@5658
1c9c74: 7010 a057 0600                         |0006: invoke-direct {v6}, Lcom/luopan/fengshui/MainActivity;.isDebuggable:()Z // method@57a0
1c9c7a: 0a03                                   |0009: move-result v3
1c9c7c: 3903 0300                              |000a: if-nez v3, 000d // +0003
1c9c80: 0e00                                   |000c: return-void
1c9c82: 5563 fb26                              |000d: iget-boolean v3, v6, Lcom/luopan/fengshui/MainActivity;.receiverRegistered:Z // field@26fb
1c9c86: 3803 0300                              |000f: if-eqz v3, 0012 // +0003
1c9c8a: 0e00                                   |0011: return-void
1c9c8c: 2203 480c                              |0012: new-instance v3, Lcom/luopan/fengshui/DebugBridge; // type@0c48
1c9c90: 2204 4e0c                              |0014: new-instance v4, Lcom/luopan/fengshui/MainActivity$$ExternalSyntheticLambda12; // type@0c4e
1c9c94: 7020 4357 6400                         |0016: invoke-direct {v4, v6}, Lcom/luopan/fengshui/MainActivity$$ExternalSyntheticLambda12;.<init>:(Lcom/luopan/fengshui/MainActivity;)V // method@5743
1c9c9a: 7020 3957 4300                         |0019: invoke-direct {v3, v4}, Lcom/luopan/fengshui/DebugBridge;.<init>:(Lcom/luopan/fengshui/DebugBridge$Sink;)V // method@5739
1c9ca0: 5b63 e826                              |001c: iput-object v3, v6, Lcom/luopan/fengshui/MainActivity;.debugBridge:Lcom/luopan/fengshui/DebugBridge; // field@26e8
1c9ca4: 2203 6c00                              |001e: new-instance v3, Landroid/content/IntentFilter; // type@006c
1c9ca8: 1a04 f358                              |0020: const-string v4, "com.luopan.fengshui.DEBUG" // string@58f3
1c9cac: 7020 b802 4300                         |0022: invoke-direct {v3, v4}, Landroid/content/IntentFilter;.<init>:(Ljava/lang/String;)V // method@02b8
1c9cb2: 6004 c200                              |0025: sget v4, Landroid/os/Build$VERSION;.SDK_INT:I // field@00c2
1c9cb6: 1305 2100                              |0027: const/16 v5, #int 33 // #21
1c9cba: 3454 0900                              |0029: if-lt v4, v5, 0032 // +0009
1c9cbe: 5464 e826                              |002b: iget-object v4, v6, Lcom/luopan/fengshui/MainActivity;.debugBridge:Lcom/luopan/fengshui/DebugBridge; // field@26e8
1c9cc2: 1225                                   |002d: const/4 v5, #int 2 // #2
1c9cc4: 6e40 c357 4653                         |002e: invoke-virtual {v6, v4, v3, v5}, Lcom/luopan/fengshui/MainActivity;.registerReceiver:(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)Landroid/content/Intent; // method@57c3
1c9cca: 2806                                   |0031: goto 0037 // +0006
1c9ccc: 5464 e826                              |0032: iget-object v4, v6, Lcom/luopan/fengshui/MainActivity;.debugBridge:Lcom/luopan/fengshui/DebugBridge; // field@26e8
1c9cd0: 6e30 c257 4603                         |0034: invoke-virtual {v6, v4, v3}, Lcom/luopan/fengshui/MainActivity;.registerReceiver:(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent; // method@57c2
1c9cd6: 1213                                   |0037: const/4 v3, #int 1 // #1
1c9cd8: 5c63 fb26                              |0038: iput-boolean v3, v6, Lcom/luopan/fengshui/MainActivity;.receiverRegistered:Z // field@26fb
1c9cdc: 2203 d60c                              |003a: new-instance v3, Ljava/lang/StringBuilder; // type@0cd6
1c9ce0: 7020 d359 2300                         |003c: invoke-direct {v3, v2}, Ljava/lang/StringBuilder;.<init>:(Ljava/lang/String;)V // method@59d3
1c9ce6: 6002 c200                              |003f: sget v2, Landroid/os/Build$VERSION;.SDK_INT:I // field@00c2
1c9cea: 6e20 d759 2300                         |0041: invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;.append:(I)Ljava/lang/StringBuilder; // method@59d7
1c9cf0: 0c02                                   |0044: move-result-object v2
1c9cf2: 6e20 dc59 1200                         |0045: invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder; // method@59dc
1c9cf8: 0c01                                   |0048: move-result-object v1
1c9cfa: 6e10 ec59 0100                         |0049: invoke-virtual {v1}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String; // method@59ec
1c9d00: 0c01                                   |004c: move-result-object v1
1c9d02: 7120 6308 1000                         |004d: invoke-static {v0, v1}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@0863
1c9d08: 2807                                   |0050: goto 0057 // +0007
1c9d0a: 0d01                                   |0051: move-exception v1
1c9d0c: 1a02 5756                              |0052: const-string v2, "bridge register FAILED" // string@5657
1c9d10: 7130 6608 2001                         |0054: invoke-static {v0, v2, v1}, Landroid/util/Log;.e:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I // method@0866
1c9d16: 0e00                                   |0057: return-void
      positions     :
        0x0000 line=236
        0x0002 line=0
        0x0006 line=236
        0x000d line=237
        0x0012 line=239
        0x001e line=240
        0x0025 line=241
        0x002b line=244
        0x0032 line=246
        0x0038 line=248
        0x003a line=249
        0x0052 line=252
```

`0x0006` 调 `isDebuggable`，`0x000a` 判零，`0x000c` 直接 `return-void`；两条 `registerReceiver` 在 `0x002e`/`0x0034`，都在这条 `return-void` **之后**。`0x002d` 的 `const/4 v5, #int 2` 即 `RECEIVER_EXPORTED`（保留导出是刻意，安全边界是 `0x000a` 那条判零）。

---

## 4 调用点计数（全 DEX）

命令与输出原文：

```
> findstr /C:"Lcom/luopan/fengshui/MainActivity;.registerReceiver:(" dump.txt
1c9cc4: ... |002e: invoke-virtual {v6, v4, v3, v5}, Lcom/luopan/fengshui/MainActivity;.registerReceiver:(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)Landroid/content/Intent; // method@57c3
1c9cd0: ... |0034: invoke-virtual {v6, v4, v3}, Lcom/luopan/fengshui/MainActivity;.registerReceiver:(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent; // method@57c2
```

- `Lcom/luopan/fengshui/MainActivity;.registerReceiver:` 调用点 = **2**，全部位于 `registerDebugBridge` 的 `0x000c` 之后。
- `Lcom/luopan/fengshui/MainActivity;.unregisterReceiver:` 调用点 = **1**（`0x1ca2a6`，在 `unregisterDebugBridge` 内）。
- 全 DEX 含 `registerReceiver:(` 的行共 **17** 行，其中 `invoke-` 指令 **13** 条；除上面 2 条外，其余分布在 `Landroidx/core/content/ContextCompat` 及其 `Api26Impl`/`Api33Impl` 内部与系统库类，不是本应用的注册点（不改变结论）。
- `isDebuggable:()Z` 的调用点 = **2**：`0x1c9c74`（`registerDebugBridge`）与 `0x1ca4cc`（`updateReading` 的 `LCRING` 日志分支），没有第三个入口。

`registerDebugBridge` 的唯一调用者是 `onResume`（源码 `MainActivity.java` L714），因此 release 包上不存在任何注册路径。

---

## 5 与源码行号的对应（便于抽样追溯）

| 字节码 | 源码（`fix/p0-review-findings` 工作树） |
|---|---|
| `0x0006` 调 `isDebuggable` → `0x000a` 判零 | `MainActivity.java` L236 `if (!isDebuggable()) return;` |
| `0x002e` `registerReceiver(...;I)` + `const/4 v5,#int 2` | L240-L244（`RECEIVER_EXPORTED` 分支） |
| `0x0034` `registerReceiver(...,...)` | L246（`SDK_INT < 33` 分支） |
| `0x0025` `sget SDK_INT` / `0x0027` `const/16 v5,#int 33` | L241 `if (android.os.Build.VERSION.SDK_INT >= 33)` |
| `isDebuggable` 内 `and-int/lit8 v0,v0,#int 2` | L698 `(getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0` |

---

## 6 运行态未验（**BLOCKED-环境**，不标 PASS）

**结论没有运行态证据。** 原因：本机唯一在册设备 `L2E0222531014833`（Android 12，华为）对 ADB 安装强制走三道设备侧 UI，最后一道要求华为统一身份认证：

1. `com.android.packageinstaller/.InstallStaging` →「风险提示」对话框（`继续安装` @ `[629,2471][1133,2578]`）；
2. `com.huawei.appmarket/...riskcheck.ui.activity.InstallDistActivity`（安装来源：PC 工具 / 应用备案状态未知，含广告位；勾选「已了解」@ `[81,2141][203,2258]` 后再点 `继续安装` @ `[81,2484][1147,2578]`）；
3. `com.huawei.coauthservice/...UnifiedAuthenticationDialogActivity` —— 需要华为账号/生物认证，**未代做**。

已试无效的安装方式：`adb install`（streamed，>2 min 无返回）、`adb install --no-streaming`（>3 min）、`adb push` + `pm install`（>3 min）；期间 `pm list packages com.luopan.fengshui` 始终为空。**debug 包同样被拦在同一处**（对照组），说明是设备安装策略，不是本 APK 的问题。

→ 因此运行态三条命令保留为**待执行项**（标记 BLOCKED-环境），设备策略解除后按下面原文执行：

```
adb -s L2E0222531014833 logcat -c
adb -s L2E0222531014833 shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 94.0
adb -s L2E0222531014833 shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "1+1"
adb -s L2E0222531014833 logcat -d -s LCDEBUG LCJS
```

判定（PASS / FAIL）：

| 组 | 期望 | 判定 |
|---|---|---|
| release（本文件的 APK） | 上述 logcat **无** `bridge registered`、**无** `LCJS`；读数不被 94.0 冻结 | PASS |
| debug（阳性对照） | 必须出现 `bridge registered sdk=... action=com.luopan.fengshui.DEBUG`，且 `js "1+1"` 出两条 `LCJS`（`mapView -> 2` / `compass -> 2`） | PASS |

解除办法（二选一，均需设备侧操作，非本仓库改动）：设备「开发者选项 → 关闭『通过 USB 安装应用需要验证』」；或在该设备手动完成一次统一身份认证后再装。

---

## 7 复现步骤（本条证据链完整命令）

```powershell
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
gradle.bat -p E:\APPDetector\fix-p0\app\android assembleDebug assembleRelease --no-daemon --console=plain
$bt='C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\35.0.1'
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android `
  --key-pass pass:android --ks-key-alias androiddebugkey `
  --out app-release-signed.apk app-release-unsigned.apk
& "$bt\aapt.exe" dump badging app-release-signed.apk | Select-String 'application-debuggable'   # 期望 0 行
& "$bt\aapt.exe" dump badging app-debug.apk            | Select-String 'application-debuggable'   # 期望 1 行
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory('app-release-signed.apk','E:\APPDetector\tmp-b02dex')
& "$bt\dexdump.exe" -d E:\APPDetector\tmp-b02dex\classes.dex > E:\APPDetector\tmp-b02dex\dump.txt
findstr /C:"isDebuggable:()Z" E:\APPDetector\tmp-b02dex\dump.txt
findstr /C:"Lcom/luopan/fengshui/MainActivity;.registerReceiver:(" E:\APPDetector\tmp-b02dex\dump.txt
```

> 注：解包时 `ZipFile::ExtractToDirectory` 会在 `res/*.png` 处抛「already exists」（APK 内有重名资源条目），但 `classes.dex` 在其之前已完整落盘，哈希与 5,753,900 字节可复现；如需解全部资源改用支持覆盖的重载或 `unzip -o`。
