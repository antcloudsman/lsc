# 00 · 原版 v1.6.1 导航表（控件 → 页面）

- 产出：LC逆向，2026-11-05
- 原版：`space.xinzhi.globe` v1.6.1（`E:\APPDetector\input\swss.apk`）
- 证据根：`.inspect\unpack\mem_dump\classes06.dex`（Frida 内存 dump，方法体真实，7698 类）
- 真机：`L2E0222531014833`（JAD-AL00，1228×2700，density 540）
- 坐标一律 **物理像素**，原点左上

## 0. 取证方法与限制（必须先读）

| 手段 | 状态 | 结果 |
|---|---|---|
| Frida 注入（hook `Activity.startActivity`） | **不可用** | `frida-ps -U` 能枚举进程，但 `attach`/`frida-instrument-run` 均返回 `ServerNotRunningError: unable to connect to remote frida-server: closed`。设备为 production build（`ro.build.type=user`、`ro.debuggable=0`），`adb root` 被拒（`adbd cannot run as root in production builds`），frida-server 以 shell 权限只能列进程，无法注入非 debuggable 应用。**不是脚本问题，是权限边界。** |
| DEX 静态链路 | 可用 | `android-class-users-find` + `android-method-show`，可把每个点击 lambda → Compose 方法 → 目标 Activity 完整串起来（下表"证据=S"） |
| adb 逐步点击 | 可用（有干扰） | 逐控件 `input tap` + `screencap` + `dumpsys activity activities` 取 `mResumedActivity`（下表"证据=T"）。**取证期间另一进程在反复 `adb install` 本机调试包，华为安装器每次抢焦点**（task t1025302→t1025315 连续出现），部分点击被吞，已在下表标注。 |

结论口径：**S+T 双证** > **T 单证** > **S 单证**。只有 S 的行表示「静态可达，未点中验证」；没有纯猜的行。

## 1. 一句话结论

1. **原版没有"设置页"入口**：`SettingActivity`（系统设置）在 30 个 DEX 里被外部引用 **0 次**，其启动器 `SettingActivity$a.a(Context)` 也无任何调用者 → v1.6.1 里是死代码。真正的设置入口是**左侧抽屉**里的「地图设置」「账户设置」两项。
2. 主页左上「**全地图工具**」、右侧「**叠加**」「**山脉**」「**水系**」→ **`OpenVipActivity` 付费墙**（不是功能页）。
3. 「**探索**」→ `DiscoveryActivity`；「**搜索**」框 → `SearchActivity`；左下「**更多**」→ 应用内「地图工具」底部面板（16 格 + `编辑`）。
4. 抽屉「**编辑/全部功能**」入口在「更多」面板，不在抽屉：`更多 → 地图工具面板 → 编辑 → ToolsManagerActivity`。

## 2. 顶栏

| 控件 | 坐标 | 目标 | 证据 | 说明 |
|---|---|---|---|---|
| 汉堡（左上） | (120,221) | 抽屉 `MainActivity.MainDrawerContent`（Compose `ModalNavigationDrawer`，`MainActivity.e:DrawerState`） | T+S | `MainActivity;->D4` trace `MainActivity.MainDrawerContent (MainActivity.kt:851)`；`<init>` @0x0022 建 `DrawerState(Closed)`。抽屉内条目见 §4 |
| 搜索输入框 | (535,221) | `ui.pages.SearchActivity` | T | 截图 `44-search-field.png`；静态 `MainActivity;->w6` @0x0000 `SearchActivity$b` + `ActivityResultLauncher`（`k:Landroidx/activity/result/ActivityResultLauncher`），调用链 `j2 → w6` |
| 搜索按钮（右侧） | (1063,221) | 同上（推断为同一入口） | — | 单独点击未触发跳转（S 页面停留 MainActivity），**未确认**是否为独立按钮逻辑 |

## 3. 主页左右两列

### 3.1 左列（自上而下）

| 控件 | 坐标 | 目标 | 证据 | 说明 |
|---|---|---|---|---|
| 全地图工具 | (124,474) | **OpenVipActivity** | T | 付费墙；`mResumedActivity=...OpenVipActivity` |
| 截屏 | (124,673) | 应用内截图 → 相册，toast「已保存至相册」 | T+S | 静态 `MainActivity;->y4`：`"截屏中..."` + `MapWebApiKt.getScreenshotToPhoto(MapWebController, BaseActivity)` |
| 超清优化 | (124,1338) | 应用内（ViewModel） | S | `ToolsEnum.SUPER_MAP_OLD` → `MainActivity.V8` case15 → `MainViewModel.L3(Context)` |
| 海拔查询 | (124,1509) | 应用内（ViewModel） | S | `ALTITUDE` → case1 → `MainViewModel.A1()` |
| 罗盘 | (124,1673) | 应用内（ViewModel） | S | `COMPASS` → case7 → `MainViewModel.D1()` |
| 路网 | (124,1837) | 应用内（ViewModel，图层开关） | S | `ROAD` → case17 → `MainViewModel.I3()` |
| 等高线 | (124,2082) | 应用内（ViewModel） | S | `ISOLINE` → case5 → `MainViewModel.G3(Context)` |
| 更多 | (124,2250) | 「地图工具」底部面板（16 格 + `编辑`） | T | 截图 `51-relaunch.png`（面板：罗盘/超清地图/看山脉/看水系/海拔查询/天气/历史影像/等高线/收藏点/测量/画笔/钓点/轨迹记录/路网/地理影像/超清优化） |

### 3.2 右列（面板收起态）

| 控件 | 坐标 | 目标 | 证据 | 说明 |
|---|---|---|---|---|
| 图层 | ≈(1097,490) | 应用内「图层选择」面板：中科星图 / 吉林一号-全球 / 吉林一号-全国 / 世纪空间 / 天地图卫星 / 天地图地图 / 天地图地形 / 高德卫星 / 高德地图 / 腾讯卫星 / 腾讯地图 / 腾讯地形 / ＋新建 | T | 截图 `46-side-mianad.png`。**该次点击的 y 为 676（落在图层与叠加之间）仍打开了面板，精确热区未复核** |
| 叠加 | (1097,795) | **OpenVipActivity** | T | 付费墙 |
| 山脉 | (1097,957) | **OpenVipActivity** | T | 付费墙（看山脉功能） |
| 水系 | (1097,1126) | **OpenVipActivity** | T | 付费墙（看水系功能） |
| 探索 | (1097,1291) | `discovery.DiscoveryActivity` | T+S | 静态 `P6 → DiscoveryActivity$a.a`，`P6` 由 `m3` 调用 |
| 指北针 / ＋ / － / 定位 / 3D | 约 y=1605 / 1781 / 1946 / 2071 / 2250 | 地图交互（无页面跳转） | — | 未逐一点击；从 `MainCover`（`MainActivity.kt:1118`）结构看为地图手势/缩放入口，**未确认** |

> 面板展开态（点「图层」后）右列首项变为「关闭」，并出现「免广告」浮标，坐标整体下移，不能沿用上表 y。

## 4. 抽屉条目（`MainDrawerContent`，12 项）

抽屉行高实测 189 px，首行中心 y=677（用像素行检测得到，见 §6 脚本）。

| # | 条目 | 图标 | 坐标 | 目标 | 证据 | 说明 |
|---|---|---|---|---|---|---|
| 1 | 地图设置 | `R.drawable.svg_setting` | (460,677) | `setting.MapSettingActivity` | S | `MainActivity$d` → lambda `M1` → `MainActivity.n1` → `D7` → `MapSettingActivity$a.a(Context)`；**点击验证被安装器打断，未完成** |
| 2 | 账户设置 | `svg_account_setting` | (460,866) | 已登录→`AccountSettingActivity`；未登录→`LoginActivity`（带 source `"账户设置"`） | S | `T1 → Z → E7`：判 `AccountManager.getCurrentAccount()==null` 走 Login |
| 3 | 投诉反馈 | `svg_tousu` | (460,1055) | `setting.feedback.ComplaintFeedbackActivity` | S+T(近似) | `U1 → D2 → H7` → `ComplaintFeedbackActivity$a.b(Context)`。早期点击 y=1360 命中「关于我们」行，坐标偏差已修正为像素实测 |
| 4 | 关于我们 | `svg_about` | (460,1244) | `ui.pages.AboutActivity` | T+S | 截图 `33-drawer-complaint.png`（实际打开的是关于我们）；`V1 → S0 → I7 → AboutActivity$a.a`。页内含「测绘资质」链接与版本 v1.6.1、备案号 渝ICP备20003923号-12A |
| 5 | 联系我们 | `svg_contact_us` | (460,1433) | 应用内（`MainViewModel.R2(true)`） | S | `W1 → Y2 → J7`，无 Activity 跳转 |
| 6 | 推荐给好友 | `svg_menu_share` | (460,1622) | 应用内分享（`MainViewModel.s3(Activity)`，埋点 `recommend_app`） | S | `X1 → X0 → K7` |
| 7 | 开具发票 | `svg_bill_apply` | (460,1810) | `bill.BillOrderListActivity` | S | `Y1 → A0 → L7`；**商业模块，有意省略** |
| 8 | 帮助中心 | `svg_helper` | (460,2000) | `WebActivity` → `https://xinzhispace.yuque.com/org-wiki-xinzhispace-zrccm1/mh7qvx?#` | T+S | 截图 `23-more.png`（WebView「帮助中心」）；URL 见 `MainActivity.M7` @0x0005 |
| 9 | 用户协议 | `svg_user_agreement` | (460,2189) | `WebActivity` → `https://agreement.yuan9.cn/agreement/swssqjdt/yhxyb.html` | S | `l0 → N7` @0x0005 |
| 10 | 隐私政策 | `svg_secrecy_policy` | (460,2378) | `WebActivity` → `https://agreement.yuan9.cn/agreement/swssqjdt/privacy.html` | S | `A2 → O7` @0x0005 |
| 11 | 第三方sdk清单 | `svg_secrecy_policy` | 需滚动 | `WebActivity` → `https://cdn.xinzhi.space/doc/policyCompliance/globe/Android/第三方sdk清单.html` | S | `o1 → F7` @0x0005 |
| 12 | 权限清单 | `svg_secrecy_policy` | 需滚动 | `WebActivity` → `https://cdn.xinzhi.space/doc/policyCompliance/globe/Android/权限清单.html` | S | `a2 → G7` @0x0005 |

抽屉顶部：未登录态「您当前未登录 / 立即登录」、VIP 卡片「三维山水全景地图VIP / 立即开通VIP / 去开通」、底部版本「三维山水全景地图V 1.6.1」+「返回」（关闭抽屉，`SettingActivity$b.c` 同款 `finish()` 语义）。

## 5. 工具派发表（18 个 `ToolsEnum` → 动作）

`ToolsEnum.<clinit>`（code_units=319）+ `MainActivity$N.<clinit>`（switch 映射，code_units=224）+ `MainActivity.V8(ToolsEnum,boolean)`（code_units=290，`packed-switch` @0x0008）三件套还原。

| ordinal | 枚举 | 中文（`getText`） | 网格图标 / 抽屉图标 | case | 动作 |
|---|---|---|---|---|---|
| 0 | COMPASS | 罗盘 | `ic_tool_compass` / `svg_compass` | 7 | `MainViewModel.D1()`（应用内） |
| 1 | SUPER_MAP | 超清地图 | `ic_tool_super_map` / `svg_super_map` | 14 | `MainViewModel.y1(Context)`（应用内） |
| 2 | MOUNTAIN | 看山脉 | `ic_tool_mountain` / `svg_mountain2` | 11 | `discovery.MountainActivity$a.b(Context, 0.0, 0.0)` |
| 3 | WATER | 看水系 | `ic_tool_water` / `svg_water2` | 10 | `discovery.WaterActivity$a.b(Context, 0.0, 0.0)` |
| 4 | VR | VR全景 | `ic_tool_vr` / `svg_vr2` | 13 | `ui.pages.VrActivity$a.a(Context)` |
| 5 | ALTITUDE | 海拔查询 | `ic_tool_altitude` / `svg_altitude` | 1 | `MainViewModel.A1()` |
| 6 | WEATHER | 天气 | `ic_tool_weather` / `svg_weather` | 2 | `ui.pages.WeatherActivity$a.a(Context)` |
| 7 | HISTORY | 历史影像 | `ic_tool_history` / `svg_history` | 3 | `MainViewModel.F3(Context)` |
| 8 | ISOLINE | 等高线 | `ic_tool_isoline` / `svg_isoline` | 5 | `MainViewModel.G3(Context)` |
| 9 | COLLECT | 收藏点 | `ic_tool_collect` / `svg_collect` | 6 | `MainViewModel.C1()` |
| 10 | STREET | 景点 | `ic_tools_street` / `svg_street` | 16 | `MainActivity.K3(Boolean)`（应用内；该枚举在 `getTools()` 中被移除，默认不显示） |
| 11 | MEASURE | 测量 | `ic_tool_measure` / `svg_measure` | 8 | `MainViewModel.I1()` |
| 12 | DRAW | 画笔 | `ic_tool_paint` / `svg_paint` | 9 | `MainViewModel.E1()` |
| 13 | FISH | 钓点 | `ic_tool_fish` / `svg_fish_point` | 12 | `MainViewModel.F1(Context)` |
| 14 | TRACK_RECORD | 轨迹记录 | `ic_tools_track` / `svg_track` | 4 | `MainViewModel.M3()` |
| 15 | ROAD | 路网 | `ic_tools_road` / `svg_road` | 17 | `MainViewModel.I3()` |
| 16 | GEOGRAPHIC_IMAGERY | 地理影像 | `ic_tools_geographic_imagery` / `svg_geo_imagery` | 18 | `GeographicImageryActivity$b.a(Context, "更多"/"首页按钮")`（按调用方传参） |
| 17 | SUPER_MAP_OLD | 超清优化 | `ic_tool_super_map_old` / `svg_super_map_old` | 15 | `MainViewModel.L3(Context)` |

`ToolsEnum$Companion.getTools()`（code_units=49）会把 **STREET、VR** 从列表移除；`AppConfig.hidePassAuditFunction()` 为真时再移除 **COMPASS**；`ChannelName.isRestrictedFeatureChannel()` 为真时再移除 **GEOGRAPHIC_IMAGERY**。默认渠道 → 16 项，与真机「地图工具」面板 16 格一致。

## 6. "更多 → 地图工具面板 → 编辑" 链

| 步骤 | 控件 | 坐标 | 目标 | 证据 |
|---|---|---|---|---|
| 1 | 左下「更多」 | (124,2250) | 应用内底部面板 `MainActivity.MainOther`（`MainActivity.kt:1754`） | T（截图 `51-relaunch.png`）+ S（`F4` trace） |
| 2 | 面板右上「编辑」 | (1087,1039) | `ui.pages.ToolsManagerActivity` | S：`K0.invoke() → MainActivity.T1 → H4 → new Intent(ToolsManagerActivity)`；**点击验证被安装器打断** |

## 7. 未确认 / 待补

1. 「搜索」按钮（右侧蓝钮）是否有独立逻辑；点击未跳转。
2. 右列「图层」精确热区（本次以 y=676 命中，实际按钮中心约 490）。
3. 抽屉第 11/12 项（第三方sdk清单、权限清单）需滚动后才可见，坐标未实测。
4. 地图右下角指北针/＋/－/定位/3D 均未单独点击。
5. 地图设置页真机截图未取（安装器抢占焦点）；页面结构已由 DEX 还原（见 `MapSettingActivity.md`）。

## 8. 截图清单（绝对路径 + sha256）

目录：`E:\APPDetector\lc-gap-evidence\nav\`

| 文件 | 内容 | sha256 |
|---|---|---|
| `01-main.png` | 启动后主页（含开屏广告退出后的 VIP 弹窗） | `f37cd762475f43975bb7fce5a269456a04700dd85be1c3f1f566873c0277d960` |
| `12-relaunch.png` | 干净主页（图层按钮态） | `fdc6c38c6a5854b0bbdcd2c8150c15c954c0c0f05454e1f2aa057f7426d2bff7` |
| `30-hamburger.png` | 左侧抽屉全貌（12 条 + VIP 卡 + 版本号） | `d11f388e1e717cf95f7cd91b87fd2042a240d9b73939f4c9ddbaf5108e45607a` |
| `33-drawer-complaint.png` | 关于我们（AboutActivity） | `f1a00fdd842c1ab020be3166d2f5ac21409f40cefe5d08162a90c2d5c390c81b` |
| `40-search.png` | 主页（搜索框旋转占位词） | `eed1154ed56b15992d3a533917f4b9a3aaa09290aa832425c4a29a98a6ec7aad` |
| `44-search-field.png` | SearchActivity（推荐地点/地图探索/键盘） | `6bb25d247a184a015d76a5eb831aa6d0fe2965adba9cfd6f4677ae05fb50049b` |
| `43-side-tansuo.png` | 探索 → DiscoveryActivity | `98812c2653a6ce0b3087c6e903c3d111cba960386f13e08badc8967d5fc70ae6` |
| `46-side-mianad.png` | 图层选择面板（12 图源 + ＋） | `d945b249ab14f5394e18cc85ea6a202c9a2a3651d44c3fe2dbf4c9a30ce8274a` |
| `47-screenshot-btn.png` | 截屏 → toast「已保存至相册」 | `3a903c87d883724271e12d82aafd2c5d9e1a7e7224fd19ad260b74037b3e5144` |
| `51-relaunch.png` | 地图工具面板（16 格 + 编辑） | `e8b9c5630a0827f93822901d07d4461493fb31a0699baf7255f420e2f47ba091` |
| `55-mapsetting.png` | 抽屉（像素行实测基准图） | `a31302c8dd00be32dde25994cdba75b22f072e770dd0b5e7320293e84ddebc88` |

另：开屏广告截图 `00-launch.png`（`1764d2002daefa27a8596ce94b8c1837a82258b3dea8ef0489a0b02a8965ee4f`），付费墙 `03-vip.png`（内容为 OpenVipActivity：年卡¥98/终身¥168/月卡¥68 + 支付宝/微信支付）。

## 9. 复现脚本

像素行检测（抽屉行高/首行中心，避免坐标猜错）：

```powershell
python -c "
from PIL import Image
im=Image.open(r'E:\APPDetector\lc-gap-evidence\nav\55-mapsetting.png').convert('L')
px=im.load(); w,h=im.size; rows=[]
for y in range(500,h-100):
    c=sum(1 for x in range(300,900,3) if px[x,y]>180)
    if c>10: rows.append(y)
groups=[]
for y in rows:
    if groups and y-groups[-1][-1]<=8: groups[-1].append(y)
    else: groups.append([y])
print([(g[0],g[-1],(g[0]+g[-1])//2) for g in groups])
"
```

点击并判定目标页（每步一条）：

```powershell
adb -s L2E0222531014833 shell input tap <x> <y>
adb -s L2E0222531014833 shell dumpsys activity activities | Select-String 'mResumedActivity'
```

静态链路（单方法定位 + 调用者）：

```powershell
$cli='D:\Program Files\Craft Anything\craft-anything-cli.exe'
$dex='E:\APPDetector\output\.inspect\unpack\mem_dump\classes06.dex'
& $cli --project E:\APPDetector\lc-gap.cany --format json android-method-show `
  --class-name space.xinzhi.globe.ui.pages.MainActivity --method-name V8 --path $dex
& $cli --project E:\APPDetector\lc-gap.cany --format json android-class-users-find `
  --class-name space.xinzhi.globe.ui.pages.MainActivity --method-name H4 --path $dex
```
