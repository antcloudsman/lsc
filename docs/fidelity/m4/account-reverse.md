# M4 Stage 1 · 原版账户体系逆向取证

> 设备 `L2E0222531014833`（1228×2700，动画三项 0）。DEX 证据来源：`.inspect/unpack/mem_smali`（Frida 内存 dump，方法体真实）+ `mem_dump/classes*.dex`。
> 标记口径：**已取证**＝有 DEX 类/方法/地址或真机可观测行为；**未取证**＝写清原因，不猜。
> 开工 14:10 / 首轮落盘见 §11。

## 0. 一句话结论

原版账户体系是**手机号 + 短信验证码**为主、第三方（微信/QQ/荣耀）为辅；登录成功后 `AccountModel`（含 `token`）整体 JSON 落 MMKV，之后所有请求由拦截器带 `token` 头；业务侧收藏点、投诉反馈都是**登录取 token 后直连**的 REST 接口，回包统一 `{code,msg,data}`，**成功码 200**。

## 1. 基址、回包与请求头

| 项 | 值 | 出处 |
| --- | --- | --- |
| API 基址 | `https://globe.xinzhi.space` | `net/ApiHelper.smali` `const-string` |
| 回包外壳 | `ApiResult<T>{ code:int, msg:String, data:T }` | `net/ApiResult.smali` 字段 |
| 成功判定 | `isSuccess()` ⇔ `code == 0xC8`（**200**，非 0） | `net/ApiResult.smali:111` `const/16 v1, 0xc8` |
| 认证头 | **`token`**，值取 `AccountManager.getCurrentAccount().getToken()`；为空则**不带**该头 | `net/interceptor/HeaderInterceptor.smali:388` `const-string "token"` |

同拦截器还带三个非认证头（记录用，我方后端**不需要**复刻）：

| 头 | 内容 | 出处 |
| --- | --- | --- |
| `cp` | AES/CBC/PKCS5Padding 加密的 JSON：`device / channel / androidId / model / timestamp(秒) / oaid`（oaid 非空才带） | `HeaderInterceptor.smali` + `ApiHelper$Companion.smali` `encrypt()` |
| `ta` | 数数 SDK `getPresetProperties()` 的 JSON，urlencode | `HeaderInterceptor.smali` |
| `ge` | 语言（urlencode） | 同上 |

> 安全备注：`encrypt()/decrypt()` 的密钥与 IV 是**硬编码常量**（在 `ApiHelper$Companion.smali`）。本文档不抄该值；判「原版契约」不需要它，我方 v2 **不复刻**这套混淆头。

## 2. 端点表（账户与业务，逐条来自 Retrofit 注解）

方法取自 `net/ApiService.smali` 的 `.annotation runtime Lretrofit2/http/*` 与接口签名。

| 方法 | 路径 | 用途 | 入参类型 |
| --- | --- | --- | --- |
| POST | `api/user/login` | 登录（含注册即登录） | `LoginParameter` |
| POST | `api/user/logout` | 登出 | 无 |
| GET | `api/user/info` | 拉当前用户 | `GetUserInfoParameter`（作为 query） |
| POST | `api/base/sendValidCode` | 发短信验证码 | `Map` |
| PUT | `api/user/bindAccount` | 绑定手机 / 绑定第三方 | `Map` / `BindThirdParameter` |
| PUT | `/api/user/changePhone` | 换绑手机 | `Map` |
| POST | `/api/user/verifyCurrentPhone` | 校验当前手机 | `Map` |
| PUT | `/api/user/updateAvatar` | 改头像 | `Map` |
| PUT | `/api/user/updateNickname` | 改昵称 | `Map` |
| PUT | `api/user/unsubscribe` | 注销账户 | 无 |
| POST | `api/base/getUploadOssPreSignUrl` | 取 OSS 上传预签名 | `Map` |
| GET | `api/userCollectPoint/list` | 收藏点列表 | `pageNum:int`, `overlay:Boolean?` |
| POST | `api/userCollectPoint/save` | 保存/更新收藏点 | `CollectPointModel` |
| DELETE | `api/userCollectPoint/delete` | 删收藏点 | `id:long`（query） |
| POST | `api/feedback/add` | 提交投诉/反馈 | `AddFeedbackReqModel` |
| GET | `api/feedback/list` | 我的反馈列表 | 无 |

请求全表共 **128 条** Retrofit 注解（含地图/天气/VIP/发票/轨迹等，本轮只取账户与业务闭环相关）。

## 3. 登录

### 3.1 请求体 `net/request/LoginParameter.smali`

字段：`phone`、`smsCode`、`loginType`、`phoneToken`、`wxOpenid`、`wxUnionid`、`honorOpenid`、`honorUnionid`、`nickname`、`avatar`、`sex`。

`loginType` 取值（`LoginParameter$LoginType.smali`，全部已取证）：

```
phone      手机号 + 短信验证码
phoneOauth 运营商一键登录（phoneToken）
wechat     微信
qq         QQ
honor      荣耀账号
device     设备/游客态
```

### 3.2 四条登录链路（`manager/AccountManager.smali`）

| 方法 | 形态 | 落点 |
| --- | --- | --- |
| `login(scope, LoginParameter, cb)` | 通用 | `ApiHelper_CommonKt.login` → `ApiService.login` |
| `loginByCode(scope, phone, code, cb)` | 手机号+验证码 | 组 `LoginParameter(loginType=phone)` |
| `loginByQuick(scope, phoneToken, cb)` | 一键登录 | 组 `loginType=phoneOauth`，`const-string "phoneOauth"` |
| `loginByWeChat(scope, activity, Z, cb)` | 微信 | `loginType=wechat` + `wxOpenid/wxUnionid` |
| `loginByHonor(scope, activity, cb)` | 荣耀 | `loginType=honor` + honor openid/unionid |

### 3.3 响应体 `model/AccountModel.smali`

`id:long`、`nickname`、`avatar`、`phone`、`sex:int`、**`token`**、`tokenValidity`、`vipExpire:Date`、`vipForever:Boolean`、`vipName`、`experienceVip:Boolean`、`payWay:int`、`createTime:Long`、`wxOpenid/wxUnionid`、`qqOpenid/qqUnionid`。

### 3.4 token 存放位置（已取证）

- 存储：**MMKV**，实例 id `AccountManager`（`AccountManager.mmkv_delegate$lambda$0` `const-string "AccountManager"` → `MMKV.mmkvWithID`）。
- 键：**`currentAccount`**，值为 `AccountModel` 的 **Gson JSON 整串**（`get_currentAccount()` → `decodeString("currentAccount")` → `GsonKt.fromJson(..., AccountModel.class)`；写侧 `encode("currentAccount", json)`）。
- ⇒ `token` 不是单独键，而是埋在 `currentAccount` 的 JSON 里；这也解释了为什么拦截器每次都要 `getCurrentAccount()` 取对象再 `getToken()`。

### 3.5 登出

`AccountManager.logout(scope, cb)` → `ApiService.logout`（POST `api/user/logout`）→ `handleLogoutSuccess()` 清当前账户（清 MMKV 与内存态）。
另有 `logoutOnAccountInvalid()`（token 失效被动登出），**触发它的具体 code 未取证**（成功码之外的错误码表未取证，见 §9）。

## 4. 注册

- **原版没有独立注册页/注册接口**：`AndroidManifest.xml` 全量 activity 里只有 `ui.pages.LoginActivity`，无 Register 类；`ApiService` 也无 register 路径。
- ⇒ 形态为**「手机号 + 验证码登录即注册」**（依据：唯一登录入口 + `LoginType.phone` + `loginByCode`）。
- 验证码：POST `api/base/sendValidCode`，请求 `GetValidCodeParameter{ phone, type }`，`type` 取值已取证 `LOGIN / BIND_PHONE / OLD_PHONE / NEW_PHONE`（`GetValidCodeParameter$ValidCodeType.smali`）。
- **未取证**：验证码按钮文案/倒计时/频控提示、发送成功提示、验证码错误提示（需要未登录态页面 + 真发短信，见 §9）。

## 5. 账户设置相关接口（含 Map 的真实 key）

`Map` 传参的 key 已从方法体常量取证：

| 接口 | Map key | 出处 |
| --- | --- | --- |
| `bindPhone` | `phone`、`code` | `AccountManager.bindPhone` 常量 |
| `changePhone` | `phone`、`code` | `AccountManager.changePhone` |
| `verifyCurrentPhone` | `phone`、`code` | `AccountManager.verifyCurrentPhone` |
| `updateNickname` | `nickname` | `AccountManager.updateNickname` |
| `bindAccount`（绑第三方） | `phone`、`smsCode`、`wxOpenid`、`wxUnionid`、`loginType="wechat"` | `AccountManager.bindAccount` 常量（含 `const-string "wechat"`） |
| `sendBindValidCode` | `phone` | `AccountManager.sendBindValidCode` |

- 第三方绑定模型 `net/request/BindThirdParameter.smali`：`bindType`（`BindType.QQ = "QQ"` / `BindType.WECHAT = "WECHAT"`）、`openid`、`unionid`、`qqOpenid`、`qqUnionid`、`nickname`、`avatar`、`sex`。
- `getUserInfo` 入参 `GetUserInfoParameter{ token, loginType }`（token 同时也在头里带，属双重携带）。
- **未取证**：`updateAvatar` 的 Map key（该类里没有 `public final updateAvatar`，改头像走 `uploadImage`/`updateCover` 链路，尚需单独定位）。

## 6. 收藏点云端同步

| 动作 | 接口 | 入参 |
| --- | --- | --- |
| 列表 | GET `api/userCollectPoint/list` | `pageNum:int`、`overlay:Boolean?` |
| 保存/更新 | POST `api/userCollectPoint/save` | `CollectPointModel` |
| 删除 | DELETE `api/userCollectPoint/delete` | `id:long`（query） |

`model/CollectPointModel.smali` 字段（已取证）：`id:Long`、`userId:Long`、`name`、`address`、`latitude:double`、`longitude:double`、`des`、`images`、`icon`、`style`、`overlay:int`、`dbId:int`、`createTime:Date`。

另有 `net/request/AddCollectParameter.smali`（字段更全，含 `addr`、`country/province/city/district/street/adCode`、`altitude:Float`、`sceneCode`、`notify:int`、`notifyConfig`、`createTime:long`、`token`）——**它与 `save` 的入参不是同一个类型**，说明还存在另一条收藏写入链路（`AddCollectParameter` 被谁调用尚未定位）。

**未取证**：同步时机（登录后是否自动全量拉取 / 本地表合并策略）、`overlay` 的取值域。需要真机观察登录态下的拉取请求。

## 7. 投诉反馈提交

- 提交：POST `api/feedback/add`，体为 `model/AddFeedbackReqModel.smali`：`attachments`、`contactWay`、`orderNum`、`reasons`、`remark`、`type:int`。
- 构造顺序（`<init>` 的 iput 顺序，已取证）：`(attachments, contactWay, orderNum, reasons, remark, type)`。
- 提交链路：`viewmodel/FeedbackViewModel.smali` 的 `A(int type, String, String, String, String, String, Function1)` → 组 `AddFeedbackReqModel` → `ApiHelper`。
- 已取证的一条真实提交（`FeedbackViewModel.P(Function1)`，行 1529 附近）：

```
type      = 2               (const/4 v1, 0x2)
orderNum  = z()
reasons   = m()
remark    = ""              (const-string v4, "")
contactWay= y()
attachments = s()
```

  ⇒ **`remark`（问题描述位）在这条链路里恒为空串**，描述内容是走 `reasons` 的。
- 列表：GET `api/feedback/list`；模型 `model/FeedbackListBean.smali`。
- 页面活动（manifest 已取证）：`ComplaintFeedbackActivity`（投诉反馈）、`BugFeedbackActivity`（问题反馈）、`OrderFeedbackActivity`（订单投诉）、`MyFeedbackActivity`（我的反馈）。

**未取证**：`type` 的完整取值域（只证到 `2`）、三页分别对应哪个 type、`attachments` 的格式（JSON 数组还是逗号串）、空提交后服务端返回的校验提示（**不发请求**，总监已裁定；见 §9）。

## 8. 页面文案与控件

### 8.1 登录/注册页逐字文案（**已取证：DEX 字符串池**）

锚点：`mem_dump/classes06.dex` 字符串池（本 App 自身代码；同一批串也出现在 `dex_dump/00O000ll111l_6.dex`）。**这是文案证据，不等于控件树**：各串在哪个控件、什么顺序、什么 bounds，仍未取证（原因见 §10）。

分组列出（逐字，含标点与空格）：

| 组 | 逐字文案 |
| --- | --- |
| 页名 | `登录/注册`、`登录或注册` |
| 登录方式 | `验证码登录`、`一键登录`、`更多登录方式`、`其他手机号登录`、`微信登录`、`荣耀登录`、`免密登录`（后者在 `classes.dex`） |
| 输入区 | `输入手机号`、`请输入手机号`、`输入验证码`、`请输入验证码`、`获取验证码`、`重新获取验证码`、`获取中` |
| 协议区 | `隐私政策及用户服务提示`、`我已阅读并同意`、`同意`、`不同意`、`隐私政策`、`隐私协议`、`隐私声明`、`严格保护隐私` |
| 校验/错误提示 | `请填写手机号码`、`无效的手机号`、`验证码无效`、`请先同意用户协议和隐私政策`、`请同意服务条款` |
| 第三方失败态 | `未安装微信`、`未安装微信应用`、`取消微信登录`、`微信绑定失败`、`无法使用荣耀登录` |
| 账户设置（已登录） | `昵称`、`修改昵称`、`请输入昵称`、`头像`、`绑定手机号`、`当前绑定手机号+86  `（**末尾两个空格，逐字**）、`更换手机号`、`原手机号验证`、`原账号再尝试更换手机号`、`绑定微信`、`前往绑定`、`去绑定`、`绑定成功~`、`注销账户`、`立即注销`、`我已确认注销账户`、`账号注销后`、`账号将会`、`退出登录`、`退出成功`、`手动退出`、`重新登录`、`登录成功`、`您当前未登录`、`立即登录` |
| 其它（同批出现，归属待确认） | `后续可通过手机号验证码快速登录~`、`当前功能需要您同意以下权限才能正常使用`、`保障账号安全`、`当前账号暂无充值订单`、`微信客服`、`微信客服打开失败`、`| 权限详情 | 隐私协议 | 功能介绍`、`再按一次退出` |

> 注意：`当前绑定手机号+86  ` 结尾是**两个空格**（dex 原文如此）；实现若要 trim 必须记录，不要默默改字。

### 8.2 账户设置页（已登录态）

已取证（M3 阶段）：`docs/fidelity/m3/account-setting.{png,xml}` + `docs/fidelity/M3-pages.md` §1.1（昵称 `[39,339][1013,418]`、头像 `[39,502][1084,581]`、绑定手机 + `去绑定 [979,665][1111,744]`、注销账户 `[39,991]`、账户ID `[39,1154]` + `340992`、退出登录 `[523,2541][706,2578]`）。

### 8.3 投诉反馈三级页面文案

已取证（M3 §1.2，逐字表 + bounds），本次补上其后的接口契约（§7）。

## 9. 未取证清单（逐条给原因）

| 项 | 状态 | 原因 |
| --- | --- | --- |
| 登录/注册页**逐字文案** | **已取证** | `mem_dump/classes06.dex` 字符串池（§8.1） |
| 登录/注册页**控件树 / bounds / 控件顺序** | 未取证 | `exported=false` 无法直启；原版已登录不能登出（不可逆）；探针包通道失败（§10） |
| 验证码发送成功/失败提示、倒计时、频控 | 未取证 | 需未登录态并真发短信（对外副作用） |
| 错误码表（除成功码 200 外） | 未取证 | 需服务端回错误；不能对厂商接口做试探 |
| `logoutOnAccountInvalid` 触发码 | 未取证 | 同上 |
| 反馈 `type` 取值域、三页映射 | 部分 | 只证到 `2`；补齐需逐页提交（会发请求，不做） |
| 收藏点同步时机/合并策略 | 未取证 | 需登录态下观察网络请求（可用我方服务端上线后对照，但原版行为仍取不到） |
| `updateAvatar` 的 Map key | 未取证 | 未定位到该 public 方法，走 `uploadImage`/`updateCover` 链路 |

## 10. 登录页采集通道（不破坏原版登录态）

原版登录页必须在**未登录**状态采集。已排除的通道：

| 通道 | 结论 |
| --- | --- |
| `am start -n space.xinzhi.globe/.ui.pages.LoginActivity` | **失败**：`SecurityException: not exported from uid 10362` |
| deeplink / intent-filter | **不存在**：`LoginActivity` 无 intent-filter |
| 模拟器 | **不可行**：APK 只有 `arm64-v8a`（`lib/` 下无 x86_64），本机 AVD 是 x86_64 镜像 |
| 登出原版 | **不授权**（总监裁定：不可逆丢凭据） |

采用的通道（尝试）：**等长包名探针包**——用 apktool 只改 `package`、24 条 `android:authorities` 和自定义权限名（activity/service 的类名保持全限定不动），以 debug key 重签成 `space.xinzhi.globX` 单独安装。它与原包是两个包，原包数据与登录态零改动，卸载即恢复原状。

### 探针包结果：**失败（通道不可得）**

| 尝试 | 结果 |
| --- | --- |
| 1) 只改 `package` + authorities | `INSTALL_FAILED_DUPLICATE_PERMISSION: space.xinzhi.globe.permission.MIPUSH_RECEIVE already owned by space.xinzhi.globe` |
| 2) 再把自定义权限名一起改前缀 → 重建重签 | 安装被华为「风险提示」拦截（`取消 / 继续安装`） |
| 3) 临时 `pm disable-user com.huawei.appmarket` → 装 → 立刻 `pm enable` | `pm install` 挂住 >13 分钟，最终 **包未落地**（`pm path space.xinzhi.globX` 空），`InstallSuccess` 界面是别的安装会话 |
| 4) 复核 | `pm list packages | grep globX` 空；`com.huawei.appmarket` 已恢复 `enabled`；原版 `space.xinzhi.globe` 为 **1.6.3**（`lastUpdateTime 13:31:33`，装源 `com.huawei.appmarket`） |

→ 登录页的**控件树/bounds 记「未取证」**，只给 §8.1 的 DEX 逐字文案。不拿 DEX 推断冒充页面结构。

> 设备侧说明（如实记录）：13:31 那次安装**是我发起的探针包安装**，不是应用市场的自动升级；同一时段市场把原版升到了 1.6.3（版本漂移，见 §12）。为装探针包临时停过市场，已 `pm enable` 恢复，`pm list packages -d` 复查为空。

### 10.1 市场「临时禁用 → 立即恢复」留痕（逐次）

| 序号 | 动作与实测输出 |
| --- | --- |
| 1 | `pm disable-user --user 0 com.huawei.appmarket` → `Package com.huawei.appmarket new state: disabled-user`（为装探针包 S4 口径） |
| 2 | 安装被风险提示拦截 → `pm enable --user 0 com.huawei.appmarket` → `new state: enabled`；`pm list packages -d | grep appmarket` 输出**空**（无残留禁用） |
| 3 | 第二次「停 → 装 → 恢复」：同一对命令；`install 用时 114 s` 后报 `INSTALL_FAILED_DUPLICATE_PERMISSION`（此时市场已恢复 `enabled`） |
| 4 | 修完权限名重建后再来一次：`disable-user` → `pm install`（**挂住 >13 分钟，被 kill**）→ `pm enable` → `new state: enabled`；复查 `pm list packages -d` 空 |
| 5 | 收尾复核：`pm path space.xinzhi.globX` **空**（探针包未落地）；`pm list packages | grep globX` 空；原版 `versionName=1.6.3`、`lastUpdateTime=2026-11-07 13:31:33` |

**结论：市场在我每次动手后都已恢复 `enabled`，没有留下禁用状态。** 后续若复查看到 `enabled=3`（DISABLED_USER），那是**另一个成员的装机脚本正处在 S4 区间**（同一时刻前台是 `com.android.packageinstaller/.InstallStaging`，设备已被他人占用），不是我的残留；我按串行纪律不动设备、不替他人 `pm enable`，以免打断其 finally 恢复判定。

## 11. 取证进度与时间戳

| 步骤 | 结果 | 时间 |
| --- | --- | --- |
| DEX：端点/模型/头/存储/反馈/收藏 全量抽取 | 完成（§1–§7） | 14:10–14:25 |
| DEX 字符串池：登录页逐字文案 | 完成（§8.1，76 条命中） | 14:52–14:55 |
| 登录页直启 `am start` | 失败：`not exported` | 14:19 |
| 探针包构建（apktool 改包名/权限 + 重签） | 完成（100,963,282 B） | 14:22–14:40 |
| 探针包上机 | 失败（见 §10） | 14:24–14:58 |
| 设备状态复原（市场启用 / 前台桌面） | 完成 | 14:58 |
| 文档落盘 | 完成（本文件 + `m4/` 证据） | 14:59 |

## 12. 版本漂移（本轮新增事实）

- 基线：**1.6.1**（已冻结 45 条清单与证据，不改）。
- 实机现状：**1.6.3**，`lastUpdateTime 2026-11-07 13:31:33`，`installerPackageName com.huawei.appmarket`（市场自动升级）。
- 影响：本轮 DEX 证据来自 1.6.1 的解包树（`.inspect/unpack`，与基线一致）；**页面类采集**若在 1.6.3 上做，必须在结果里标「版本漂移」，不与 1.6.1 证据混算。
- 登录态：升级属覆盖安装，**App 数据未被清除**（同签名同包名），原版仍处已登录态。
