# 原版组件清单与 LC 缺口矩阵（Craft Anything 取证）

> 来源：`craft-anything-cli android-components-list --path E:\APPDetector\input\swss.apk`（2026-11-05）
> 结果：total=225，activity=148（其中可被外部拉起 13）、service=33、receiver=20、provider=24

## 1. 分类统计

| 分类 | 数量 | 处理策略 |
|---|---|---|
| 其他/第三方 | 50 | 按需 |
| 广告SDK | 42 | 有意省略（去商业） |
| 发现/内容 | 21 | P1/P2 逆向 + 实现 |
| 商业/登录/支付 | 14 | 有意省略（去商业） |
| 设置/工具 | 8 | P0 逆向 + 实现 |
| 反馈/客服 | 4 | 有意省略（去商业） |
| 轨迹/收藏 | 4 | P1 逆向 + 实现 |
| 核心：罗盘 | 4 | 已实现/改造中 |
| 其他 | 1 | 按需 |

## 2. LC 已有实现（对照）

- Activity 仅 2 个：`MainActivity`（单页地图+罗盘浮层改造中）、`CompassListActivity`（盘面列表）
- 辅助类：`CompassView`（自绘罗盘）、`CompassData`（数值/读数）、`CompassReadSheet`（读盘弹层）、`SensorMath`、`MapMarker`、`DebugBridge`、`LCAndroid`
- layout 仅 `activity_main.xml`；其余页面均以代码内构建或弹层实现

## 3. 全量组件表

| # | 组件 | 分类 | LC 状态 | 优先级 | 外部可拉起 |
|---|---|---|---|---|---|
| 1 | `ui.pages.setting.feedback.BugFeedbackActivity` | 反馈/客服 | 缺失 | —（有意省略） | False |
| 2 | `ui.pages.setting.feedback.ComplaintFeedbackActivity` | 反馈/客服 | 缺失 | —（有意省略） | False |
| 3 | `ui.pages.setting.feedback.MyFeedbackActivity` | 反馈/客服 | 缺失 | —（有意省略） | False |
| 4 | `ui.pages.setting.feedback.OrderFeedbackActivity` | 反馈/客服 | 缺失 | —（有意省略） | False |
| 5 | `com.beizi.ad.AdActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 6 | `com.beizi.ad.internal.activity.BeiZiDownloadDialogActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 7 | `com.beizi.ad.internal.activity.DownloadAppInfoActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 8 | `com.beizi.ad.v2.activity.BeiZiNewInterstitialActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 9 | `com.beizi.ad.v2.activity.BeiZiNewRewardVideoActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 10 | `com.bytedance.ads.convert.BDBridgeActivity` | 广告SDK | 缺失 | —（有意省略） | True |
| 11 | `com.bytedance.android.openliveplugin.stub.activity.DouyinAuthorizeActivityLiveProcessProxy` | 广告SDK | 缺失 | —（有意省略） | True |
| 12 | `com.bytedance.android.openliveplugin.stub.activity.DouyinAuthorizeActivityProxy` | 广告SDK | 缺失 | —（有意省略） | True |
| 13 | `com.bytedance.android.openliveplugin.stub.activity.StubActivity$Activity_Portrait` | 广告SDK | 缺失 | —（有意省略） | False |
| 14 | `com.bytedance.android.openliveplugin.stub.activity.StubActivity$Activity_Portrait2` | 广告SDK | 缺失 | —（有意省略） | False |
| 15 | `com.bytedance.android.openliveplugin.stub.activity.StubActivity$Activity_Portrait3` | 广告SDK | 缺失 | —（有意省略） | False |
| 16 | `com.bytedance.android.openliveplugin.stub.activity.StubActivity$Activity_Portrait4` | 广告SDK | 缺失 | —（有意省略） | False |
| 17 | `com.bytedance.android.openliveplugin.stub.activity.StubActivity$Activity_Portrait5` | 广告SDK | 缺失 | —（有意省略） | False |
| 18 | `com.bytedance.applog.migrate.MigrateDetectorActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 19 | `com.bytedance.msdk.api.activity.TTDelegateActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 20 | `com.bytedance.msdk.api.activity.TTTransparentActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 21 | `com.bytedance.msdk.core.admanager.reward.rewardagain.GMRewardAgainDialogActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 22 | `com.bytedance.sdk.openadsdk.core.activity.base.TTDelegateActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 23 | `com.bytedance.sdk.openadsdk.core.activity.base.TTNativePageActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 24 | `com.bytedance.sdk.openadsdk.core.activity.base.TTVideoScrollWebPageActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 25 | `com.bytedance.sdk.openadsdk.core.activity.base.TTVideoWebPageActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 26 | `com.bytedance.sdk.openadsdk.core.activity.base.TTWebPageActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 27 | `com.bytedance.sdk.openadsdk.core.component.reward.activity.TTFullScreenVideoActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 28 | `com.bytedance.sdk.openadsdk.core.component.reward.activity.TTFullScreenVideoLandscapeActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 29 | `com.bytedance.sdk.openadsdk.core.component.reward.activity.TTRewardVideoActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 30 | `com.bytedance.sdk.openadsdk.core.component.reward.activity.TTRewardVideoLandscapeActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 31 | `com.bytedance.sdk.openadsdk.downloadnew.ApiDownloadHandleNotificationActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 32 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_Activity` | 广告SDK | 缺失 | —（有意省略） | False |
| 33 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_SingleTask_Activity` | 广告SDK | 缺失 | —（有意省略） | False |
| 34 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_SingleTask_Activity_T` | 广告SDK | 缺失 | —（有意省略） | False |
| 35 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Activity` | 广告SDK | 缺失 | —（有意省略） | False |
| 36 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Activity_T` | 广告SDK | 缺失 | —（有意省略） | False |
| 37 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Landscape_Activity` | 广告SDK | 缺失 | —（有意省略） | False |
| 38 | `com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity` | 广告SDK | 缺失 | —（有意省略） | False |
| 39 | `com.qq.e.ads.ADActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 40 | `com.qq.e.ads.DialogActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 41 | `com.qq.e.ads.LandscapeADActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 42 | `com.qq.e.ads.PortraitADActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 43 | `com.qq.e.ads.RewardvideoLandscapeADActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 44 | `com.qq.e.ads.RewardvideoPortraitADActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 45 | `com.tencent.connect.common.AssistActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 46 | `xyz.adscope.amps.tool.downloadinfo.AMPSDownloadAppInfoActivity` | 广告SDK | 缺失 | —（有意省略） | False |
| 47 | `com.alipay.sdk.app.AlipayResultActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | True |
| 48 | `com.alipay.sdk.app.APayEntranceActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 49 | `com.alipay.sdk.app.H5AuthActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 50 | `com.alipay.sdk.app.H5OpenAuthActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 51 | `com.alipay.sdk.app.H5PayActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 52 | `com.alipay.sdk.app.PayResultActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | True |
| 53 | `ui.pages.bill.BillApplyActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 54 | `ui.pages.bill.BillDetailActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 55 | `ui.pages.bill.BillOrderListActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 56 | `ui.pages.bill.BillRecordActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 57 | `ui.pages.LoginActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 58 | `ui.pages.OpenVipActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | False |
| 59 | `wxapi.WXEntryActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | True |
| 60 | `wxapi.WXPayEntryActivity` | 商业/登录/支付 | 缺失 | —（有意省略） | True |
| 61 | `ui.pages.CompassIntroduceActivity` | 核心：罗盘 | 部分（知识库列表→弹窗） | P0 | False |
| 62 | `ui.pages.CompassListActivity` | 核心：罗盘 | 已实现 | P0 | False |
| 63 | `ui.pages.MainActivity` | 核心：罗盘 | 已实现（K-01 单页改造中） | P0 | False |
| 64 | `ui.pages.SplashActivity` | 核心：罗盘 | 缺失 | P0 | True |
| 65 | `ui.pages.MapDemoActivity` | 设置/工具 | 缺失 | P0 | False |
| 66 | `ui.pages.setting.AccountSettingActivity` | 设置/工具 | 缺失 | P0 | False |
| 67 | `ui.pages.setting.MapSettingActivity` | 设置/工具 | 缺失（B-21 待定项：原版确有设置页） | P0 | False |
| 68 | `ui.pages.setting.MeasureProveActivity` | 设置/工具 | 缺失 | P0 | False |
| 69 | `ui.pages.setting.PhoneUpdateActivity` | 设置/工具 | 缺失 | P0 | False |
| 70 | `ui.pages.setting.SettingActivity` | 设置/工具 | 缺失 | P0 | False |
| 71 | `ui.pages.ToolsManagerActivity` | 设置/工具 | 缺失 | P0 | False |
| 72 | `ui.pages.track.TrackSettingActivity` | 设置/工具 | 缺失 | P0 | False |
| 73 | `ui.pages.AboutActivity` | 发现/内容 | 缺失 | P1 | False |
| 74 | `ui.pages.discovery.DiscoveryActivity` | 发现/内容 | 缺失 | P1 | False |
| 75 | `ui.pages.discovery.DiscoveryDetailActivity` | 发现/内容 | 缺失 | P1 | False |
| 76 | `ui.pages.discovery.EarthDiscoveryActivity` | 发现/内容 | 缺失 | P1 | False |
| 77 | `ui.pages.discovery.GlobalLiveActivity` | 发现/内容 | 缺失 | P1 | False |
| 78 | `ui.pages.discovery.LiveActivity` | 发现/内容 | 缺失 | P1 | False |
| 79 | `ui.pages.discovery.MountainActivity` | 发现/内容 | 缺失 | P1 | False |
| 80 | `ui.pages.discovery.PopulationActivity` | 发现/内容 | 缺失 | P1 | False |
| 81 | `ui.pages.discovery.PopulationIntroActivity` | 发现/内容 | 缺失 | P1 | False |
| 82 | `ui.pages.discovery.StreetDetailActivity` | 发现/内容 | 缺失 | P1 | False |
| 83 | `ui.pages.discovery.StreetDiscoveryActivity` | 发现/内容 | 缺失 | P1 | False |
| 84 | `ui.pages.discovery.WaterActivity` | 发现/内容 | 缺失 | P1 | False |
| 85 | `ui.pages.GeographicImageryActivity` | 发现/内容 | 缺失 | P1 | False |
| 86 | `ui.pages.HistoryImageActivity` | 发现/内容 | 缺失 | P1 | False |
| 87 | `ui.pages.SearchActivity` | 发现/内容 | 缺失 | P1 | False |
| 88 | `ui.pages.SuperMapListActivity` | 发现/内容 | 缺失 | P1 | False |
| 89 | `ui.pages.SuperMapPreviewActivity` | 发现/内容 | 缺失 | P1 | False |
| 90 | `ui.pages.VideoPlayActivity` | 发现/内容 | 缺失 | P1 | False |
| 91 | `ui.pages.VrActivity` | 发现/内容 | 缺失 | P1 | False |
| 92 | `ui.pages.WeatherActivity` | 发现/内容 | 缺失 | P1 | False |
| 93 | `ui.pages.WebActivity` | 发现/内容 | 缺失 | P1 | False |
| 94 | `ui.pages.interest.InterestListActivity` | 轨迹/收藏 | 缺失 | P1 | False |
| 95 | `ui.pages.interest.SaveInterestActivity` | 轨迹/收藏 | 缺失 | P1 | False |
| 96 | `ui.pages.track.TrackListActivity` | 轨迹/收藏 | 缺失 | P1 | False |
| 97 | `ui.pages.track.TrackReplayVideoActivity` | 轨迹/收藏 | 缺失 | P1 | False |
| 98 | `ui.NotifyTrampolineActivity` | 其他 | 缺失 | P2 | False |
| 99 | `cn.android.service.JTransitActivity` | 其他/第三方 | 缺失 | P2 | True |
| 100 | `cn.jpush.android.service.JNotifyActivity` | 其他/第三方 | 缺失 | P2 | True |
| 101 | `cn.jpush.android.ui.PopWinActivity` | 其他/第三方 | 缺失 | P2 | True |
| 102 | `cn.jpush.android.ui.PushActivity` | 其他/第三方 | 缺失 | P2 | True |
| 103 | `com.blankj.utilcode.util.UtilsTransActivity` | 其他/第三方 | 缺失 | P2 | False |
| 104 | `com.blankj.utilcode.util.UtilsTransActivity4MainProcess` | 其他/第三方 | 缺失 | P2 | False |
| 105 | `com.byted.live.lite.Activity_bytelive_singleInstance1` | 其他/第三方 | 缺失 | P2 | False |
| 106 | `com.byted.live.lite.Activity_bytelive_singleTask1` | 其他/第三方 | 缺失 | P2 | False |
| 107 | `com.byted.live.lite.Activity_bytelive_singleTask2` | 其他/第三方 | 缺失 | P2 | False |
| 108 | `com.byted.live.lite.Activity_bytelive_singleTask3` | 其他/第三方 | 缺失 | P2 | False |
| 109 | `com.byted.live.lite.Activity_bytelive_singleTask4` | 其他/第三方 | 缺失 | P2 | False |
| 110 | `com.byted.live.lite.Activity_bytelive_singleTop1` | 其他/第三方 | 缺失 | P2 | False |
| 111 | `com.byted.live.lite.Activity_bytelive_singleTop2` | 其他/第三方 | 缺失 | P2 | False |
| 112 | `com.byted.live.lite.Activity_bytelive_singleTop3` | 其他/第三方 | 缺失 | P2 | False |
| 113 | `com.byted.live.lite.Activity_bytelive_singleTop4` | 其他/第三方 | 缺失 | P2 | False |
| 114 | `com.byted.live.lite.Activity_bytelive_singleTop5` | 其他/第三方 | 缺失 | P2 | False |
| 115 | `com.byted.live.lite.Activity_bytelive_singleTop6` | 其他/第三方 | 缺失 | P2 | False |
| 116 | `com.byted.live.lite.Activity_bytelive_standard` | 其他/第三方 | 缺失 | P2 | False |
| 117 | `com.byted.live.lite.Activity_main_singleInstance1` | 其他/第三方 | 缺失 | P2 | False |
| 118 | `com.byted.live.lite.Activity_main_singleTask1` | 其他/第三方 | 缺失 | P2 | False |
| 119 | `com.byted.live.lite.Activity_main_singleTask2` | 其他/第三方 | 缺失 | P2 | False |
| 120 | `com.byted.live.lite.Activity_main_singleTask3` | 其他/第三方 | 缺失 | P2 | False |
| 121 | `com.byted.live.lite.Activity_main_singleTask4` | 其他/第三方 | 缺失 | P2 | False |
| 122 | `com.byted.live.lite.Activity_main_singleTop1` | 其他/第三方 | 缺失 | P2 | False |
| 123 | `com.byted.live.lite.Activity_main_singleTop2` | 其他/第三方 | 缺失 | P2 | False |
| 124 | `com.byted.live.lite.Activity_main_singleTop3` | 其他/第三方 | 缺失 | P2 | False |
| 125 | `com.byted.live.lite.Activity_main_singleTop4` | 其他/第三方 | 缺失 | P2 | False |
| 126 | `com.byted.live.lite.Activity_main_singleTop5` | 其他/第三方 | 缺失 | P2 | False |
| 127 | `com.byted.live.lite.Activity_main_singleTop6` | 其他/第三方 | 缺失 | P2 | False |
| 128 | `com.byted.live.lite.Activity_main_standard` | 其他/第三方 | 缺失 | P2 | False |
| 129 | `com.google.android.gms.common.api.GoogleApiActivity` | 其他/第三方 | 缺失 | P2 | False |
| 130 | `com.huawei.hms.activity.BridgeActivity` | 其他/第三方 | 缺失 | P2 | False |
| 131 | `com.huawei.hms.activity.EnableServiceActivity` | 其他/第三方 | 缺失 | P2 | False |
| 132 | `com.huawei.hms.hmsscankit.ScanKitActivity` | 其他/第三方 | 缺失 | P2 | False |
| 133 | `com.huawei.hms.support.api.push.TransActivity` | 其他/第三方 | 缺失 | P2 | False |
| 134 | `com.luck.picture.lib.basic.PictureSelectorSupporterActivity` | 其他/第三方 | 缺失 | P2 | False |
| 135 | `com.luck.picture.lib.basic.PictureSelectorTransparentActivity` | 其他/第三方 | 缺失 | P2 | False |
| 136 | `com.lxj.xpopup.util.XPermission$PermissionActivity` | 其他/第三方 | 缺失 | P2 | False |
| 137 | `com.mobile.auth.gatewayauth.activity.AuthWebVeiwActivity` | 其他/第三方 | 缺失 | P2 | False |
| 138 | `com.mobile.auth.gatewayauth.LoginAuthActivity` | 其他/第三方 | 缺失 | P2 | False |
| 139 | `com.mobile.auth.gatewayauth.PrivacyDialogActivity` | 其他/第三方 | 缺失 | P2 | False |
| 140 | `com.ss.android.downloadlib.activity.JumpKllkActivity` | 其他/第三方 | 缺失 | P2 | False |
| 141 | `com.ss.android.downloadlib.activity.TTDelegateActivity` | 其他/第三方 | 缺失 | P2 | False |
| 142 | `com.ss.android.downloadlib.addownload.compliance.AppDetailInfoActivity` | 其他/第三方 | 缺失 | P2 | False |
| 143 | `com.ss.android.downloadlib.addownload.compliance.AppPrivacyPolicyActivity` | 其他/第三方 | 缺失 | P2 | False |
| 144 | `com.ss.android.socialbase.appdownloader.view.DownloadHandleNotificationActivity` | 其他/第三方 | 缺失 | P2 | False |
| 145 | `com.ss.android.socialbase.appdownloader.view.DownloadTaskDeleteActivity` | 其他/第三方 | 缺失 | P2 | False |
| 146 | `com.ss.android.socialbase.appdownloader.view.JumpUnknownSourceActivity` | 其他/第三方 | 缺失 | P2 | False |
| 147 | `com.xiaomi.mipush.sdk.NotificationClickedActivity` | 其他/第三方 | 缺失 | P2 | True |
| 148 | `ui.pages.guide.GuideActivity` | 其他/第三方 | 缺失 | P2 | False |

## 4. 取证方式（每个缺失模块）

| 步骤 | Craft Anything 工具 | 产出 |
|---|---|---|
| 页面清单/入口 | `android-components-list` | 本表 |
| 布局/文案/图标 | `android-resources-read`（解码 res/*.xml 与 arsc） | 布局结构、字符串、颜色 |
| 业务逻辑 | `android-class-show` / `android-method-show`（指向脱壳 DEX） | 方法字节码、调用关系 |
| 谁调谁 | `android-class-users-find` | 调用点 |
| 真机页面/交互 | `runtime_ui_walkthrough_record` 或 adb 截图 + uiautomator dump | 逐页截图与控件树 |
| 动态行为（必要时） | `frida-instrument-run` / `frida-script-run` | 运行时参数与返回值 |
