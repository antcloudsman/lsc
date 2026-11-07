# M4 E2E 全链路真机结果（LC测试）

- 被测提交：`develop` = `9028cbe`（与 `d53d4bc` 的 `app/` 零差异，`git diff --name-only d53d4bc 9028cbe -- app/` → 0 行）
- 被测 APK：`app\android\app\build\outputs\apk\debug\app-debug.apk`，8,584,457 B，sha256 `7389fa25…`（设备 `pm path` 侧 sha256 逐字节一致，`S0.3` PASS）
- 设备：`L2E0222531014833`（Android 12，1228×2700），`adb reverse tcp:8787 tcp:8787`
- 容器：`lc-backend` healthy，只 `stop`/`start`，未 `down -v`
- 脚本：`tests\e2e\run-m4-e2e.ps1` + `tests\lib\ui-drive.ps1`（坐标全部当次 dump 解析，不写死）
- 证据根目录：`checks\m4-e2e-<时间戳>\`（`step.md`/`step.jsonl`/逐步 dump 与截图/脱敏 `logcat.txt`）

## 1. 有效轮次（`m4-e2e-20261107-194119`，完整 E1–E8）

| 步骤 | 内容 | 结果 | 实测 |
|---|---|---|---|
| S0.1 | adb reverse | PASS | `UsbFfs tcp:8787 tcp:8787` |
| S0.3 | 装机指纹 | PASS | device=local=`7389fa25…` |
| S0.4 | 启动并就绪 | PASS | 主界面 + 冻结 heading 后可 dump |
| E1.1–E1.5 | 抽屉→账户设置→登录页→注册 tab→填账号 | PASS | `M3PageActivity`→`LoginActivity`，`user=e2e47712` |
| E1.6 | 注册并登录 | PASS | `back_to_account=True`（活动回到账户页；`登录成功` 是瞬时行，抓不到不算失证） |
| E2.1 | 登录态 | PASS | dump 出 `昵称：e2enick` |
| E3.1–E3.4 | 更多→收藏点→添加点→保存 | PASS | 进入 `SaveInterestActivity` 并保存成功 |
| E4.1 | 同步收藏点 | PASS | `同步收藏点：同步完成：上传 1，拉取 1` |
| E5.1 | 服务端独立印证 | PASS | DB `{"users":37,"favorites":23,"feedback":8,"sessions":60}`（同步前 favorites 22） |
| E6.1–E6.3 | 抽屉→投诉反馈→问题反馈表单→填三字段 | PASS | 三字段节点齐全 |
| **E6.4** | **提交反馈** | **FAIL（测试侧）** | 点击后仍显示 `请先填写申请原因、联系方式与问题描述`——三段文本全部落进第一个 EditText（键盘弹起后表单重排，dump 只暴露部分输入框），属自动化输入缺陷，非应用缺陷 |
| E7.1 | 退出登录 | PASS | 回到 `您当前未登录` |
| E8.0 | 重新登录 | PASS | 回到账户页登录态 |
| E8.1 | 停容器（不删卷） | PASS | `Container lc-backend Stopped` |
| **E8.2** | **停机降级可见** | **PASS** | dump 出现 `同步失败：连不上本地服务（需 adb reverse tcp:8787）` |
| E8.3–E8.4 | 恢复容器 | PASS | `Started` + 轮询到 `healthy` |
| **E8.5** | **恢复后同步成功** | **FAIL（测试侧）** | 恢复后未重新定位到「同步收藏点」节点（`tap=False`），页面仍显示停机时的失败行；需在恢复后重新进入账户页再点 |
| Z.1 | 无应用侧 FATAL | PASS | `app_fatal=0`（另 1 条是 uiautomator 自身崩溃，不计应用缺陷） |

合计 **34 PASS / 3 测试侧 FAIL / 1 判据 false-negative**（S0.36 权限校验正则误判，权限实际已 `granted=true`）。

## 2. 结论口径

1. **Stage 4 的「注册→登录→收藏→同步→反馈→登出」里，除「反馈提交」外全部真机走通**；反馈的提交链路有客户端独立证据（`POST /v2/feedback/add -> 201`、DB `feedback 7→8`），但**UI 端到端未由我方脚本跑成**，本文件不写成通过。
2. **「服务不可用降级可见」成立**（E8.2 有截图+dump），恢复后再次同步未验证（E8.5 测试侧失败）。
3. 三条 FAIL **全部是测试脚本缺陷**，没有一条是应用行为异常：无应用侧 FATAL、无 ANR、DB 计数与服务端响应一致。

## 3. 已踩并修正的坑（写进脚本，避免下个人重踩）

| 坑 | 现象 | 修法 |
|---|---|---|
| 辅助函数名与外部命令同名 | `function Adb` 让 PowerShell 递归调用自己 → `调用深度溢出` | 改名 `Invoke-Adb`（技术总监已提醒过同一坑） |
| 罗盘动画未冻结 | `uiautomator dump` 报 `could not get idle state`、不产出文件 | dump 前 `--es cmd heading --ef value 0`，失败时自动重试 |
| 残留登录态 | `立即登录` 节点不存在 → E1.3 假 FAIL | 每轮 `pm clear` 起干净会话 |
| `pm clear` 副作用 | 连运行时权限一起清 → 冷启动弹授权框；首启还有「校准罗盘」引导框 | `pm grant` 补回；`Dismiss-SystemDialogs` 点「跳过」 |
| dump 并发 | 多个脚本/监控同时 dump → `UiAutomationService already registered`、uiautomator 自身 FATAL | 单实例运行；dump 节流 250ms；失败时 `pkill -f uiautomator` 再试 |
| `keyevent 4` 想收键盘 | 键盘未弹时等同「返回」，把表单页退掉 | 不按 BACK，改为重新 dump 后按 hint 定位下一字段 |
| `resource-id` 取值 | PowerShell 里写 `$_.resourceid` 取不到（属性名带连字符） | 用 `$_.'resource-id'` |

## 4. 未完成（下一窗口继续）

1. **反馈提交 E6.4**：改为「点击输入框后用 `input text` + 校验 dump 文本」逐字段确认，或改用 `uiautomator` 注入；确认后再判。
2. **恢复后同步 E8.5**：容器 healthy 后强制重新进入「抽屉→账户设置」再点同步。
3. **装机五场景 S1–S5**：本轮设备时间被 E2E 调试占用，**尚未开跑**，脚本 `tests\lib\install-scenario.ps1` 与 checklist 已就绪。
