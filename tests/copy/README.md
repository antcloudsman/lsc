# tests/copy — 无门槛文案 / 静态红线门禁

一个入口三条检查，全绿才 `COPY: PASS`。**退出码 0 = 绿，1 = 有命中**。

```powershell
# 单跑
python tests\copy\copy_gate.py

# 走一键门禁（copy 是第一个门禁，最快红）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1 -SkipDevice
```

输出格式：每个检查一行 `[PASS]` / `[FAIL]`，命中逐条打印 `文件:行:词`，
末行 `COPY: PASS|FAIL`。smoke 里日志落在 `checks\smoke-<时间戳>\copy.log`。

## 三条检查

| 规则 | 判据 | 现状 |
|---|---|---|
| `copy` | `app\android\app\src\main\res\values\*.xml` 的用户可见值零命中 `会员\|付费\|VIP\|开通\|升级\|订阅\|解锁\|充值\|去水印` | 2026-11-05 有 5 处，红（LC地图 在改） |
| `audi` | 发行面不得出现原版审图号 | 绿 |
| `tiles` | 发行面不得出现原版私有瓦片域 `21atcloud` / `xinzhi.space` | 绿 |

### copy

- 只扫 `res\values\*.xml`（目前仓库没有 `values-xx` 变体目录；以后加了要同步扩）。
- **注释不算**：`<!-- ... -->` 先被剔除再匹配。注释里写「原版此处为付费」「不判 VIP」是事实记录，**不许删**。
- 每个 xml 先过一遍 XML 解析，解析失败直接 `[FAIL] copy: ... XML 解析失败`（坏资源不该靠门禁才发现，但顺手兜住）。
- 命中的 `[string=...]` 是资源名，方便定位。

### audi / tiles

- 扫描范围 = `git ls-files` 的**已跟踪**文件 − 豁免 − 二进制，再按扩展名剔除注释。
- 已跟踪：`checks\` 里的产物、未 `git add` 的新文件不参与（门禁只对要入库的东西负责）。
- 注释剔除按语言分三类：`<!-- -->`（xml）、`/* */` + 行首/行尾 `//`（java/kotlin/js/rs/…）、行首/行尾 `#`（ps1/py/sh/yml/…）。
  URL 里的 `://` 和 `#frag` 不会被当注释吃掉——**私有瓦片地址写在字符串里一定抓得到**。
- 例外：`/* */` 块注释只从**行首**起算，行中间的 `int x = 1; /* ... */` 不处理（宁可多报）。

## 豁免（怎么加）

两类，**默认零豁免**：

1. **路径豁免**（写死在脚本 `EXEMPT_PREFIXES`，改它要过评审）：这些不是发行面，且必须保留原版事实：

   | 路径 | 理由 |
   |---|---|
   | `docs\`、`*.md` | PRD / 逆向 / 取证说明要记录原版审图号、私有瓦片域、「原版此处付费」 |
   | `investigation.cany\`、`reference\`、`attachments\`、`capture\`、`pinned\` | 逆向取证日志、原版反编译参考、素材 |
   | `tests\` | 测试要写**负向断言**（`run-final.ps1` 里那行「不得出现第三方审图号」必须带字面量）；门禁自身也是 |
   | `checks\`、`target\`、`.git` | 本地产物 |

2. **逐条豁免**（`tests\copy\allowlist.txt`，一行一条，**理由必填**）：

   ```
   <rule> <glob> <理由>
   # rule : copy | audi | tiles | all
   # glob : 相对仓库根的 posix 路径，fnmatch 语义（`*` 会跨目录）
   # 例：
   copy  app/android/app/src/main/res/values/strings_legal.xml  协议原文由签约方逐字指定，不得改写
   ```

   - 用了哪条就在 `allowlist.txt` 加行；**没有理由的豁免等于放宽红线**，PR 里要单独说明。
   - 豁免项没命中任何文件会打 `[WARN] allowlist 未命中：...`（不判红）——留着的死豁免要定期清。
   - 能改文案就别加豁免：`copy` 的 5 处应该**改文案**，不是加白名单。

3. **计数钉子**（文件级豁免必须钉住条数，`copy_gate.py` 的 `ALLOWLIST_PINS`）：

   ```python
   ALLOWLIST_PINS = {
       "app/android/app/src/main/assets/m3/contact.txt":  ("tiles", "xinzhi" + "." + "space", 1),
       "app/android/app/src/main/assets/m3/agreement.txt": ("tiles", "xinzhi" + "." + "space", 2),
   }
   ```

   - 钉子写在**代码里**而不是 allowlist 第四列：文件级豁免最容易被「后来顺手加的 URL」钻空子，钉住出现次数才防得住；改条数必须改代码、过评审。
   - 文件尚未落库 → `[PASS] 计数钉子：… 尚未落库`（提示，不判红）；已落库但次数不符 → `[FAIL] 计数钉子` 直接判红。
   - 当前钉子（总监 2026-11-07 裁定 (a)）：`contact.txt` **1** 处、`agreement.txt` **2** 处 `xinzhi.space`；新增白名单条目一律要总监批。

## 自查（改门禁后跑）

```powershell
python tests\copy\selftest.py
```

覆盖四件事：① 审图号/瓦片域正例命中、全角变体命中、相邻号不误报；② 注释剔除后「注释里的词不报、字符串里的 URL 照报」；
③ 豁免边界（`docs\`、`*.md`、`tests\` 豁免，`app\...\*.java` 不豁免）；④ `copy_gate.py` 自身不含完整红线字面量
（红线词在运行时拼接，否则「全仓 grep」会把自己判红）。
