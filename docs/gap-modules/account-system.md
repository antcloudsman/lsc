# 账户体系逆向（M4 Stage 1）— 登录/账户设置/收藏点云同步/投诉反馈

> 状态：**DEX 静态取证完成**（锚点齐全）；**真机截图未取证**（设备被其他线安装占用的时间窗内无法稳定操作，见 §9）。
> 证据基线：`mem_dump`（方法体真实）smali。设备现状为原版 **1.6.3**（应用市场自动升级）；本文 DEX 取自 1.6.1 冻结包，若与 1.6.3 有差异按「版本漂移」标注（§9.2）。

## 1 页面与入口

| 页面 | 类 | 说明 |
|---|---|---|
| 登录页 | `ui.pages.LoginActivity`（`LoginActivity.kt`，Compose） | 标题区 `登录/注册`；两种形态：`一键登录` / `常规登录`（`其他手机号登录`） |
| 账户设置页 | `ui.pages.setting.AccountSettingActivity`（`AccountSettingActivity.kt`） | 行：`头像` / `昵称` / `绑定手机` / `绑定微信` / `注销账户` / `账户ID`；底部 `退出登录`(Kt:143) |
| 投诉反馈 hub | `ui.pages.setting.feedback.ComplaintFeedbackActivity` | 三入口：`问题反馈` / `订单投诉` / `微信客服` |
| 问题反馈 | `ui.pages.setting.feedback.BugFeedbackActivity` | 字段 `问题描述`、`问题截图` |
| 我的反馈 | `ui.pages.setting.feedback.MyFeedbackActivity` | 列表（`api/feedback/list`） |
| 订单投诉/发票类 | `OrderFeedbackActivity` | 与订单相关，含 `请选择要反馈的订单` |
| 微信绑定失败提示 | `AccountSettingActivity` | 文案 `微信绑定失败` |

## 2 登录方式（4 种）与 `loginType` 取值

`net.request.LoginParameter` 构造器第一个参数即 `loginType`（`AccountManager` 各登录入口逐条取证）：

| 方式 | `loginType` | 传入字段 | 取证位置 |
|---|---|---|---|
| 手机号 + 短信验证码 | `phone` | `phone`, `smsCode` | `AccountManager.smali`（短信登录方法 @#@14–#@1f） |
| 一键登录（运营商取号） | `phoneOauth` | `phoneToken` | `AccountManager.smali:1800` |
| 微信 | `wechat` | `wxOpenid`, `wxUnionid` | `AccountManager$loginByWeChat$2.smali:185` |
| 荣耀账号 | `honor` | `honorOpenid`, `honorUnionid` | `AccountManager.smali:902` |

**没有账号密码登录**：`LoginParameter` 无任何 password/pwd 字段，全 DEX 也没有密码请求模型 → 原版是「验证码/第三方」，不是「账号密码」。**注册与登录同页**（`登录/注册`），验证码首次登录即建号（无独立注册页，`ui` 包内 `注册` 仅出现在 `LoginActivity` 及 `LoginActivity$b`）。

## 3 登录请求字段（`LoginParameter`，11 个私有字段）

```
avatar, honorOpenid, honorUnionid, loginType, nickname, phone,
phoneToken, sex, smsCode, wxOpenid, wxUnionid
```

登录成功后返回 `ApiResult<AccountModel>`。

## 4 `AccountModel`（16 个字段，即登录态与 VIP 判定依据）

```
id(long), avatar, nickname, phone, sex(int), token, tokenValidity,
vipExpire(Date), vipForever(bool), vipName, experienceVip(bool),
payWay(int), createTime(long), qqOpenid, qqUnionid, wxOpenid, wxUnionid
```

> `AccountModelKt.isVip(...)` 读的就是这里的 VIP 字段（早前已作为付费门判定点记录）。

## 5 接口清单（`net.ApiService`，Retrofit 注解逐条取值）

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `api/user/login` | 登录（body = `LoginParameter`）→ `AccountModel` |
| POST | `api/user/logout` | 登出 → `BlankModel` |
| POST | `api/base/sendValidCode` | 发送短信验证码 |
| GET | `api/user/info` | 取当前账户信息 |
| PUT | `api/user/updateAvatar` | 改头像 |
| PUT | `api/user/updateNickname` | 改昵称 |
| PUT | `api/user/bindAccount` | 绑定第三方账号（另有 `/api/user/bindAccount` 变体） |
| PUT | `api/user/changePhone` | 换绑手机 |
| POST | `api/user/verifyCurrentPhone` | 校验当前手机 |
| PUT | `api/user/unsubscribe` | **注销账户** |
| GET | `api/userCollectPoint/list` | 收藏点列表（云同步读） |
| POST | `api/userCollectPoint/save` | 收藏点保存 |
| DELETE | `api/userCollectPoint/delete` | 收藏点删除 |
| POST | `api/feedback/add` | 提交反馈/投诉 |
| GET | `api/feedback/list` | 我的反馈列表 |
| POST | `api/base/getUploadOssPreSignUrl` | 图片上传预签名（反馈截图/头像） |

## 6 请求头与 token

**`net.interceptor.HeaderInterceptor`** 统一加头，键名逐条取证：

```
clientType, version, device, channel, androidId, model, timestamp,
oaid, cp, ta, ge, token
```

- 鉴权头名就是 **`token`**（`HeaderInterceptor.smali:388`），不是 `Authorization`；`Authorization` 只出现在第三方库（`G7/k`、`H7/a`、`M7/l`、`O7/e`）与日志上报。
- 另有部分请求把 `token` 放进 **body 参数**：`AddCollectParameter`、`BindPhoneParameter`、`GetUserInfoParameter`、`ModifyUserInfoParameter`、`TokenParameter` 都有 `private token` 字段 —— 即原版是「头 + body 双份」。
- 其它头含义（值来源）：`timestamp`（毫秒）、`model`=`Build.MODEL`、`androidId`、`oaid`（设备标识）、`cp`/`ta`/`ge`（URL 编码后的追踪参数，由 `ApiHelper` 拼）。

**存放位置**：MMKV 库 ID **`AccountManager`**（`AccountManager.mmkv_delegate$lambda$0`），键 **`currentAccount`** 存整个 `AccountModel` 的 JSON（`decodeString/encode` 成对出现）。
即：**token 不单独存**，随账户对象一起序列化在 `currentAccount` 里；清登录态 = 覆盖该键。

## 7 错误码与提示（`net.ApiHelper.handleApiResultError(code, msg)`）

| code | 十进制 | 行为 |
|---|---|---|
| `0x1f5` | 501 | `ToastUtils.S(msg)` —— 直接把服务端 `msg` 弹出来 |
| `0x1fa` | 506 | 只打日志：`handleApiResultError: code=506, msg=` |
| `0x1f7` | 503 | 发 `Event("event_token_invalid")`（token 失效） |
| `0x1f8` | 504 | 同上 |
| 其它 | — | 直接 return，不提示 |

- `event_token_invalid` 的订阅方：`AccountManager.smali:2070`、`ApiHelper.smali:62`；即 token 失效由账户管理器统一处理（清本地账户 → 引导重新登录）。
- 通用兜底：响应体为空时 `ApiResult{code = -2, msg = "服务端响应错误"}`；异常分支文案 `UNKNOWN EXCEPTION`，日志 tag `[HTTP]`。
- 登录页本地错误码：**`700002` / `700003`**（`LoginActivity.R0(...)` 判定），命中时按「一键登录」失败处理并埋点；页面兜底文案 `登录失败，请重试`。

## 8 账户设置、登出、收藏点同步、投诉反馈

### 8.1 登出 `AccountManager.logout(...)`（逐指令）

1. 若当前账户存在：`CollectPointManager.clearCollectPointByUserId(uid)` + `TrackManager.clearTrackByUserId(uid)` —— **本地收藏点与轨迹按用户清空**；
2. `TAHelper.logout()`（停止埋点会话）；
3. 埋点 `account_logout`；
4. 重置 `AppConfig.setVipRechargeDialogTime(0)` / `setVipRechargeDialogCount(0)`、`clearMarketingActivityVipPurchase()`、`clearHomeMarketingActivityEntry()`；
5. 发事件（账户态变更）→ 覆盖 MMKV `currentAccount`。

### 8.2 收藏点云同步

- 请求模型 `net.request.AddCollectParameter`（17 字段）：
```
id(Integer), name, addr, lng(double), lat(double), altitude(Float),
country, province, city, district, street, adCode, createTime(long),
notify(int), notifyConfig, sceneCode, token
```
- 接口：`GET api/userCollectPoint/list`、`POST api/userCollectPoint/save`、`DELETE api/userCollectPoint/delete`。
- 与地图联动：`MapWebApiKt` 侧 `collect_point` 图层（早前 F-11 文档已记）。
- 断网/未登录行为**未取证**（需真机）。

### 8.3 投诉反馈

- hub 三入口：`问题反馈`（BugFeedbackActivity）/ `订单投诉`（OrderFeedbackActivity）/ `微信客服`。
- 提交模型 `net.request.AddFeedbackReqModel`（6 字段）：
```
type(int), reasons, remark, contactWay, orderNum, attachments
```
  - `attachments` 是上传后的文件地址串（先 `api/base/getUploadOssPreSignUrl` 拿预签名再上传，日志 `文件上传成功 path = `）。
- 类型选项（`FeedbackViewModel` 常量）：`使用需求` / `使用Bug` / `使用其他`
- 投诉原因选项：`无法正常使用` / `会员充值未到账` / `本人误操作充值` / `打算购买其他会员` / `其他`
- 校验与提示文案：`请输入问题描述`、`请选择申请原因`、`请选择要反馈的订单`、`未选择任何订单`、`请确认提交的信息准确无误`
- 路径：`POST api/feedback/add`、`GET api/feedback/list`；日志 tag `FEEDBACK`。

## 9 未取证 / 版本漂移

### 9.1 未取证（需真机窗口）

1. 登录页首屏与常态（`一键登录` 卡与 `其他手机号登录` 切换）、协议勾选弹窗（`同意` 按钮、三份协议链接）——**截图未取**；
2. 协议页 URL（已知，可直接引用）：
   - 用户协议 `https://agreement.yuan9.cn/agreement/swssqjdt/yhxyb.html`
   - 隐私政策 `https://agreement.yuan9.cn/agreement/swssqjdt/privacy.html`
   - 儿童信息保护声明 `https://agreement.yuan9.cn/agreement/swssqjdt/etysxy.html`
3. 账户设置页（头像/昵称/账户ID/绑定手机/绑定微信/注销账户/退出登录）**实机版面未取**；
4. 反馈三页（hub / 问题反馈 / 我的反馈）**实机版面未取**；
5. 验证码倒计时、错误提示的实际弹出形态（500ms 级时序）**未取**；
6. 注销账户的二次确认文案（`AccountSettingActivity` 内未命中中文常量，可能在 `$c`/`$d` lambda 的字符串资源里）**未确认**。

### 9.2 版本漂移（1.6.1 → 1.6.3，实机观测）

设备已被应用市场升级到 **1.6.3**（`installerPackageName=com.huawei.appmarket`，2026-11-07 13:31）。在 1.6.3 上 dump 主页（1228×2700）得到的按钮坐标已与 1.6.1 冻结记录不同：

| 控件 | 1.6.1 记录 | 1.6.3 实测 | 偏移 |
|---|---|---|---|
| 截屏 | y≈673 | y≈742 | +69 |
| 罗盘 | y≈1673 | y≈1764 | +91 |
| 更多 | y≈2294 | y≈2322 | +28 |
| 搜索 | — | 文本区 `[1002,194][1097,273]` | — |

→ 结论：**1.6.3 的 Compose 版面整体下移**，坐标类证据必须分版本存放，不能混用。

### 9.3 采集受阻（如实记）

2026-11-07 14:38 起设备上出现**他人发起的安装流程**：`/data/local/tmp/probe.apk`（100,963,282 B，14:38 推入）、前台 `com.android.packageinstaller/.InstallStaging`（task 1026197→1026199 反复出现）、并弹出「风险提示 / 取消 / 继续安装」对话框。未点击任何按钮，避免打断他人安装流程，因此本轮未继续在设备上操作。
→ 上述 1–6 项需在**无人安装的干净窗口**内补。

## 10 对 LC 的落点（不含实现，只给约束）

1. **登录方式按原版 4 种**，不做账号密码；短信验证码登录即注册（无独立注册页），UI 文案沿用 `登录/注册`。
2. **鉴权头名用 `token`**；如沿用原版后端，body 也要带 `token`（否则与 `AddCollectParameter` 等模型不一致）。
3. **登录态整体存一个 JSON**（对应 MMKV `AccountManager/currentAccount`），不要拆散；token 失效统一按「清账户 → 重新登录」处理（对应 503/504）。
4. **错误码语义**：501 直接弹服务端 msg；506 只记日志；503/504 视为登录失效——不要把所有非 200 都弹 toast。
5. **登出必须清本地收藏点与轨迹**（按 userId），否则会串号。
6. 收藏点同步字段按 §8.2 的 17 字段对齐（`sceneCode`、`notifyConfig`、`adCode` 等别漏）。
7. 反馈表单 6 字段 + 三类型 + 五原因；截图先走预签名上传。
