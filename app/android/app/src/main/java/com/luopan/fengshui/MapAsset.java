package com.luopan.fengshui;

import android.net.Uri;

/**
 * `assets/map.html` 的加载地址（合规：天地图 key 不进仓库）。
 *
 * 两个 key 分工（2026-11-05 实测，见 {@link LcHttp} 类注释的 UA×key 矩阵）：
 *  - **服务端 key**（`BuildConfig.TDT_SERVER_KEY`）：原生侧调 api.tianditu.gov.cn；
 *  - **网页端 key**（`BuildConfig.TDT_WEB_KEY`）：WebView 里的瓦片，必须经 query 参数交给 JS——
 *    本类就负责这件事。
 *
 * 地址形态：`file:///android_asset/map.html?tk=<webKey>#in3d&measure=area`
 * **query 必须在 hash 之前**，否则 `#…` 后面的东西都会变成 hash 的一部分。
 * hash 语义完全不变（`#in3d`/`&measure=`/`&lat=` …），页面里用
 * `new URLSearchParams(location.search).get("tk")` 取 key（map.html 顶部）。
 *
 * 取不到 webKey 时只拼裸地址：页面会 console.warn 且不加载瓦片，**不会**回退到硬编码 key。
 */
final class MapAsset {

    /**
     * 回滚开关：true = 走原版那条路（https 虚拟域 + WebViewAssetLoader），
     * false = 旧的 file:///android_asset。
     *
     * 为什么换：原版用 WebViewAssetLoader 把 assets 挂在
     * `https://appassets.androidplatform.net/assets/` 下（逆向 M7 `783c806`：
     * `MapWebView.smali:181-233` 三个 handler + 页址常量），而 `file://` 下
     * `fetch` 被拒、图片进 canvas 会污染（`texImage2D` 抛 SecurityError）。
     * 换域后两者同时消失，且这是回到原版做法，不是绕行。
     */
    static final boolean USE_ASSET_LOADER = true;

    private static final String BASE_FILE = "file:///android_asset/map.html";
    private static final String BASE_ASSET =
            "https://appassets.androidplatform.net/assets/map.html";

    /**
     * **只有主界面那一个 WebView 装了 WebViewAssetLoader**，所以只有它走 {@link #mapUrl}。
     *
     * 其余 8 个二级页（海拔/山脉/水系/渔点/历史影像/叠加/SuperMap/轨迹回放）没有拦截器，
     * 必须继续用这里的 `file://`——2026-11-08 实测：把 `url()` 一起切到虚拟域，
     * 二级页直接 `ERR_NAME_NOT_RESOLVED`，页面整块空白（海拔页当场复现）。
     */
    static String url(String hash) {
        return build(BASE_FILE, hash);
    }

    /** 主界面地图专用：带 AssetLoader 时走 https 虚拟域。 */
    static String mapUrl(String hash) {
        return build(USE_ASSET_LOADER ? BASE_ASSET : BASE_FILE, hash);
    }

    /** @param hash 形如 `#in3d&uimode=map&lat=..&lng=..`；null/空则不带 hash */
    private static String build(String base, String hash) {
        StringBuilder sb = new StringBuilder(base);
        String tk = BuildConfig.TDT_WEB_KEY;
        if (tk != null && !tk.isEmpty()) sb.append("?tk=").append(Uri.encode(tk));
        if (hash != null && !hash.isEmpty()) {
            if (hash.charAt(0) != '#') sb.append('#');
            sb.append(hash);
        }
        return sb.toString();
    }

    private MapAsset() {
    }
}
