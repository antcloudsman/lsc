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
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 12; LC) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", UA);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "GET fail " + url + " : " + e);
            throw e;
        } finally {
            c.disconnect();
        }
    }

    private LcHttp() {
    }
}
