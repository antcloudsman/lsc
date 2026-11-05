package com.luopan.fengshui;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 极简 HTTP GET（原生图表数据用）：8 s 超时、UTF-8。
 *
 * UA 必须是**浏览器形态**：2026-11-05 实测天地图 WAF 对
 * `Luopan/1.0 (Android)` / `okhttp/4.12` / `Dalvik/2.1.0` 一律返回 **403**，
 * 换成 `Mozilla/5.0 (...) Chrome/120 Mobile Safari/537.36` 才是 200。
 * 这不是参数问题——所有「某地搜不到、北京能搜到」的空结果都由此引起。
 */
final class LcHttp {

    private static final String TAG = "LCHTTP";
    private static final int TIMEOUT_MS = 8000;

    /**
     * 全局 UA（只此一份，别再写字面量）。
     * 天地图 WAF 对 `Luopan/…`、`okhttp/…`、`Dalvik/…` 返回 403，
     * 对浏览器形态 UA 返回 200（2026-11-05 实测，见 F-06-F-07 诊断）。
     */
    static final String UA =
            "Mozilla/5.0 (Linux; Android 12; LC) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    static String get(String url) throws Exception {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    /** 二进制下载（图片/瓦片）：与 get() 共用 UA 与超时。 */
    static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS * 2);
        c.setRequestProperty("User-Agent", UA);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "GET bytes fail " + url + " : " + e);
            throw e;
        } finally {
            c.disconnect();
        }
    }

    private LcHttp() {
    }
}
