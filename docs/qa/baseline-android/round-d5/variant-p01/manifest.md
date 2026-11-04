# AT-P0-0 变体：P0-1 总开关「全关」罗盘页

- 目的：给 AT-P0-0 的「全关逐像素 diff」提供 P0-1 关闭态的罗盘页参照。
- 构建：与主基线相同 87654af（向量盘面，不含 P0-2 位图）；APK 3,397,967 B。
- 设备：JAD-AL00 / Android 12；pm clear 后安装 → 首次校准点「跳过」。
- 采集：同一设备朝向（读数 **273.85°**，非注入），同一会话先后拍 on/off：
  1. compass-p01-on.png：p0_1_enabled 默认 true（prefs 仅有 calibration_done）；
  2. 用 un-as 把 shared_prefs/lc_prefs.xml 的 p0_1_enabled 改为 false，
     m force-stop 后重启（prefs-off.xml 留档）；
  3. compass-p01-off.png：读数 273.85°（raw），「磁北/偏角」行消失。
- 判定：**不要拿 on/off 两张图直接做像素 diff**——罗盘覆盖在活的 WebView 地图上、
  面板半透明，地图瓦片变化会污染整张图（本轮实测 diff 886,847 px、bbox 覆盖全屏）。
  判定 P0-1 关没关，看「偏角/磁北行是否存在」与 p0_1_enabled 值。
- 文件与 SHA 见 SHA256SUMS.txt。
