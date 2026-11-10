# release 签名评估（M9 第一阶段，只评估不实施）

- 评估人：LC客户端
- 基准树：`0c420e1`（`app` 树 `b203750`）
- 时间：2026-11-10
- 结论：**建议不配 release 签名**，自用场景 debug 签名够用；等出现「给他人安装 / 上架 / 分发」需求时再一次性做（理由与代价见 §4、§5）

---

## 1 现状（实测，非推测）

### 1.1 构建配置

`app/android/app/build.gradle`：

```groovy
buildTypes {
    release {
        minifyEnabled false
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

- **没有 `signingConfigs` 块**，`release` 也没指定 `signingConfig`
- `minifyEnabled false`：debug 与 release **都没开混淆**

### 1.2 两个产物的实测差异

| 维度 | debug | release |
|---|---|---|
| 产物 | `app-debug.apk` | `app-release-unsigned.apk` |
| 字节 | **12,360,802 B** | **11,574,969 B**（−785,833 B，−6.36%） |
| 签名 | `CN=Android Debug`（debug.keystore） | **无签名** |
| 可安装 | 是 | **否** |
| `debuggable` | `application-debuggable` 命中 | 无 |

签名证书指纹（`apksigner verify --print-certs app-debug.apk`）：

```
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256: f09cca9d499f0aed62797365bb883ebb18ec99b35be1d97ad768816360e9d237
Signer #1 certificate SHA-1:   d304800381d228a896851c2c9fcb63b440b1fba4
```

release 未签名证据（`apksigner verify app-release-unsigned.apk`）：

```
DOES NOT VERIFY
ERROR: Missing META-INF/MANIFEST.MF
```

⇒ **现在跑 `assembleRelease` 得到的包装不上手机**，必须配签名才能用。

### 1.3 debug keystore

`%USERPROFILE%\.android\debug.keystore` 存在（2026-09-12 生成），ANDROID SDK 标准调试证书。

---

## 2 配签名要动什么

最小改动（约 3 处，均在不影响 debug 侧的前提下）：

```groovy
// app/build.gradle
android {
    signingConfigs {
        release {
            storeFile     file(System.getenv("LC_KEYSTORE") ?: "keystore/lc-release.jks")
            storePassword System.getenv("LC_STORE_PASS")
            keyAlias      System.getenv("LC_KEY_ALIAS") ?: "lc"
            keyPassword   System.getenv("LC_KEY_PASS")
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
        }
    }
}
```

口令与 keystore 必须**在仓库外**（沿用现规矩：`E:\APPDetector\secrets\`），仓库内零明文。

---

## 3 配签名的代价（逐条可验）

### 3.1 包 sha 全变 —— 所有台账里的包指纹作废

`debug` 证书换成自签证书后，同一个 APK 内容的签名块不同 ⇒ **`sha256` 全部改变**。

受影响：终态表、关账摘要、证据目录头、`reports/**` 里所有「包 sha256」记录。至少 M8 那批（`12,349,xxx`、`12,360,xxx` 等几十处）都要标时点。

### 3.2 装机链路必须重验一次，而且**首次切换必须先卸载**

`adb install -r`（装机脚本用的）要求**新包与已装包同签名**才能覆盖。

debug → release 签名是**不同证书**，`-r` 必然失败：

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.luopan.fengshui
signatures do not match the previously installed version]
```

必须 `adb uninstall com.luopan.fengshui` 再装。**卸载会清掉应用数据**：

- 收藏点（`favorites`）
- 登录态（M8 好不容易用自注册账号登进去的）
- 离线瓦片缓存、轨迹、画笔/钓点、全部 SharedPreferences

⇒ 切换成本 = 重新登录 + 重新导入收藏点 + 重跑一遍所有真机判据（因为「装完的包」变了）。

### 3.3 之后每包都要签

- keystore 与口令成为新的**长期依赖**：丢了 keystore 就再也无法给同一个 `applicationId` 签出可覆盖安装的包
- 每台参与验收的机器都要能拿到口令（仍走 secrets，但多了一层「谁能读」的管理）

### 3.4 装机脚本要改（小）

`tools/device/install-debug.ps1` 默认路径是 `app\android\app\build\outputs\apk\debug\app-debug.apk`。若主用 release，则：

- `-Apk` 默认值要改，或每条命令都显式传路径
- `-r` 在「同签名」前提下仍可用；但**首次**从 debug 切过去必须手动卸载一次（脚本不替用户决定清数据）
- 脚本里 `pm install -r -t` 的 `-t`（允许 testOnly）在 release 已签名、非 testOnly 后不再需要，但不影响

### 3.5 不会带来什么（澄清常见误解）

- **不会让包变小**：`minifyEnabled` 在两种 buildType 下都是 `false`，那 785,833 B 差异来自 `debuggable` 与 dex 优化，跟签名无关
- **不会提升性能**：同上，没开混淆/资源压缩

---

## 4 自用场景是否必须

**不必须。** 逐条理由：

| 判断项 | 现状 | 是否需要 release 签名 |
|---|---|---|
| 上架应用商店（Google Play / 华为 / 小米） | 不做 | 否（这些渠道强制 release 签名） |
| 分发给他人安装 | 不做，自用 | 否 |
| 通过 `adb install` 装到自己的手机 | 是 | 否（debug 签名即可） |
| 需要 `debuggable`（chrome://inspect / JS 注入验收） | **是，本轮大量使用** | **debug 反而更好** |
| debug 证书有效期 | `debug.keystore` 默认 30 年 | 够用 |

**关键点：debug 签名对自用不只「够用」，还更有利。**

本项目的验收手段高度依赖 `debuggable`：

- `m8-g2-inpage.js` 的页内取画布、`__LC3D.__forceGroundFail(...)` 注入
- `adb shell am start --es cmd js --es value '…'` 这类 debug 命令通道
- `chrome://inspect` 查 WebView 状态

release 包 `debuggable=false`，**这些钩子有一部分会用不了或需要额外开关**，等于把已经建好的一整套验收手段削弱。

---

## 5 建议

**不配。** 明确理由三条：

1. **没有收益**：不上架、不分发，release 签名解决的是「身份可信」问题，自用没有这个需求；包体与性能也不会因为签名变好（§3.5）。
2. **有明确成本**：包 sha 全变 + 必须卸载重装（丢收藏点与登录态）+ 全量真机判据重跑 + keystore/口令成为长期依赖（§3.1–3.3）。
3. **会削弱现有验收能力**：`debuggable` 是这一轮贴地验收的主要入口，release 包把它关掉（§4）。

**触发条件**（满足任一再做，届时一次做干净）：

- 要把 APK 给用户以外的人安装
- 要上架任何应用市场
- 要通过华为/小米的应用内安装器（它们对非市场签名的包有额外警告）

**真要做时的最小清单**（避免临时手忙脚乱）：

```
1. Android Studio 生成 keystore（用户本机已装：
   C:\Program Files\Android\Android Studio\bin\studio64.exe）
   → 存到 E:\APPDetector\secrets\，不进仓库
2. 口令写进同一个 secrets 文件；仓库内只用环境变量引用
3. build.gradle 加 signingConfigs + release.signingConfig（§2）
4. 装机前手动 adb uninstall（数据会丢，先导出收藏点）
5. 全台账包 sha256 加「签名切换前/后」时点
6. 重跑四道门禁 + 七门 smoke + 关键真机判据（因为「已装的包」变了）
7. install-debug.ps1 的 -Apk 默认路径改为 release 产物
```

---

## 6 本轮做了什么 / 没做什么

- **做了**：`assembleRelease` 跑了一次（验证「未配签名 ⇒ 产物 unsigned ⇒ 装不上」），列出两产物字节差与 debug 证书指纹；本文件成文。
- **没做**：没有改 `build.gradle`、没有生成 keystore、没有配任何口令、没有改装机脚本。
- 构建产物未提交（`build/` 在 .gitignore 内）。
