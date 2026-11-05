# 构建期密钥：天地图 key（BUILD-SECRETS）

> 适用对象：任何要出 APK 的人 / 构建机 / CI。
> 结论先给：**key 只放仓库外的 `E:\APPDetector\secrets\tdt.properties`（或 CI 环境变量），永远不进 git、不进群、不进文档。**
> 仓库是 **PUBLIC**，推上去就等于公开。

## 1. key 放哪

| 位置 | 用途 | 进 git 吗 |
|---|---|---|
| `E:\APPDetector\secrets\tdt.properties` | 本机 / 构建机默认来源 | **不进**（在仓库外，`.gitignore` 也覆盖不到它，靠位置隔离） |
| `DSH_SECRET_TDT_SERVER_KEY` / `DSH_SECRET_TDT_WEB_KEY` 环境变量 | CI、临时覆盖、换机 | 不进 |
| 任意路径的 tdt.properties | `-Ptdt.keysFile=<路径>` 或 `TDT_KEYS_FILE` 指定 | 不进 |

文件格式（两行，`#` 开头为注释）：

```properties
tdt.server.key=<服务端 key>
tdt.web.key=<网页端 key>
```

- `tdt.server.key`：原生侧用（分类搜索、逆地理编码、超清拼接 → `MapKeys.TDT` → `MapPoints` / `SuperMapActivity`）。
- `tdt.web.key`：网页端用（`map.html` 里的瓦片 URL）。
- 两个 key 分工不同，别互相顶替；**哪个缺了都构建不过**。

### 两个 key 的权限类型不同，不能混用（2026-11-05 实测）

| key | 天地图侧类型 | 正确调用方式 | 用错的后果 |
|---|---|---|---|
| `tdt.server.key` | 服务器端 | Java 原生 HTTP，**默认 UA**（如 okhttp），参数按接口文档 | 浏览器 UA → `301013 权限类型错误`（"Key权限类型为:服务器端"） |
| `tdt.web.key` | 浏览器端 | WebView / 浏览器 UA 请求 WMTS 瓦片 | 被当服务器端用法 → 同样 301013 |

实测结论（总监 curl 验证）：服务端 key 用原生 UA 能过 WAF 进接口（400 是参数写错，不是 403）；网页端 key 用浏览器 UA 取 `img` z18 瓦片 → 200 / image/jpg。**不要用 UA 伪装去"修"401/403**：服务端 key 用默认 UA 就通，伪装反而偏离权限类型。原版内置的第三方 key `dd1885…` 现在是 `301001 非法key`（已死）——所有还在用它的构建，天地图请求会全空；这也是 F-14 `count=0` 的原因（不是代码回归）。

取值优先级（`app/android/app/build.gradle` 里实现，逐个 key 独立回落）：

1. 环境变量 `DSH_SECRET_TDT_SERVER_KEY` / `DSH_SECRET_TDT_WEB_KEY`
2. 文件里的 `tdt.server.key` / `tdt.web.key`

文件路径优先级：`-Ptdt.keysFile=<路径>` > `TDT_KEYS_FILE` 环境变量 > 默认 `E:/APPDetector/secrets/tdt.properties`。
相对路径按 `app/android/app/` 解析（Gradle 规则）。

## 2. 注入成什么

构建期在 `BuildConfig` 生成三个字段：

| 字段 | 值 | 谁用 |
|---|---|---|
| `BuildConfig.TDT_SERVER_KEY` | 服务端 key | 原生侧（地图侧把 `MapKeys.TDT` 指到这） |
| `BuildConfig.TDT_WEB_KEY` | 网页端 key | 地图侧传给 WebView（替换 `map.html` 里原来的明文 `var KEY`） |
| `BuildConfig.TDT_KEY` | 同 `TDT_SERVER_KEY` | **过渡别名**，`MapKeys.TDT` 还引用旧名字；地图改完就删这行 |

读不到 key 的行为（两条，都是**硬失败**，不静默打空包）：

- 文件不存在 → `TDT_KEYS_FILE_NOT_FOUND: missing Tianditu key file: <路径>` + 三种补救做法
- 文件在但字段空 → `TDT_KEYS_INCOMPLETE: missing Tianditu key(s): ...`

报错正文是 ASCII 打头：这台 Windows 的 Gradle 控制台会把中文打成乱码，ASCII 标记（`TDT_KEYS_FILE_NOT_FOUND` / `TDT_KEYS_INCOMPLETE`）保证你搜得到、看得懂。

## 3. 为什么不能入库

1. 仓库 **PUBLIC**：push 即公开，fork / 镜像 / 爬虫都会抄走。
2. **git 历史删不掉**：删一行字面量不等于安全——历史里还在，必须 rewrite（`filter-repo`）+ force push，还会连带别人已拉的分支。
3. 泄露后果：key 被拿去刷额度 / 冒用配额，**只能靠控制台轮换**止损；天地图按 key 计量。
4. **APK 也算泄露面**：key 会被内联进 `BuildConfig`，解包即得。所以打了包含真 key 的包，等于把 key 交出去了——包只发给可信的人。
5. 连带：原版 APK 反编译出来的第三方 key（`map.html:107` 那个 `dd1885…`）**本来就不是我们的**，更不能留。

## 4. 怎么轮换

1. 天地图控制台新建/重置 key，拿到新的服务端 + 网页端两个。
2. 更新 `E:\APPDetector\secrets\tdt.properties`（或 CI secrets）**两行都覆盖**。
3. `python tests\secrets\secret_gate.py` → 必须 `SECRET: PASS`（确认旧 key 没留在仓库里）。
4. 重打 APK 验证：`powershell -File scripts\build-android.ps1 -SkipDevice`。
5. 若是**泄露后轮换**：先在控制台**停用旧 key**，再走 2–4；并检查已发出的包/截图里有没有旧 key（`map.html` 打包进 APK 也一样）。
6. 通知持有旧包的人重新拉包；旧包里的 key 已停用，不重打会半瘫（瓦片/搜索 401）。

## 5. 构建机怎么准备

Windows（本机 / 这台共享构建机）：

```powershell
# 仓库外，一次就好
New-Item -ItemType Directory -Force E:\APPDetector\secrets | Out-Null
# 建 tdt.properties，写两行（见上），别贴进聊天/文档
powershell -File scripts\build-android.ps1 -SkipDevice   # 能出包就说明注入成功
```

临时换 key / 换机器：

```powershell
$env:DSH_SECRET_TDT_SERVER_KEY = '<服务端 key>'
$env:DSH_SECRET_TDT_WEB_KEY   = '<网页端 key>'
# 或：gradle -Ptdt.keysFile=D:\ci\tdt.properties ...
```

## 6. CI 怎么用

GitHub Actions（仓库 public）：

```yaml
- name: Build APK
  env:
    DSH_SECRET_TDT_SERVER_KEY: ${{ secrets.DSH_SECRET_TDT_SERVER_KEY }}
    DSH_SECRET_TDT_WEB_KEY: ${{ secrets.DSH_SECRET_TDT_WEB_KEY }}
  run: |
    ./gradlew :app:assembleDebug   # 或 powershell -File scripts/build-android.ps1 -SkipDevice
```

规则：

- **只在可信 workflow 里读 secrets**：push 到 `develop` / tag / release、或 self-hosted runner。GitHub 不会把 secrets 给 fork PR，所以 fork PR 上**根本没有 key 可泄**——那种场景不需要 key 门禁，见下条。
- **fork PR 的 CI**：`python tests\secrets\secret_gate.py --allow-missing`（只跑 `hex` 启发式，精确匹配跳过，日志里有 `[WARN]` 明示）。别为了让它变绿而把 key 塞进 PR 环境。
- **产物**：CI 打出的 APK 是含真 key 的制品，只上传到私有存储 / release asset，别贴 public artifact 或 issue。
- **日志**：门禁和 gradle 都不打印 key 值；如果你在脚本里 `echo $DSH_SECRET_...` 做调试，等于公开——不要。

## 7. 自检清单（换 key 后照做）

```powershell
python tests\secrets\secret_gate.py                 # 绿；hex 告警要能解释
powershell -File scripts\build-android.ps1 -SkipDevice
gradle -Ptdt.keysFile=<不存在的路径> :app:assembleDebug   # 必须失败并报 TDT_KEYS_FILE_NOT_FOUND
```

`tests\secrets\README.md` 有门禁判定细节与白名单规则。
