package com.luopan.fengshui;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 极简 HTTP GET（原生图表数据用）：8 s 超时、UTF-8、响应字节上限。
 *
 * UA 必须是**浏览器形态**：2026-11-05 实测天地图 WAF 对
 * `Luopan/1.0 (Android)` / `okhttp/4.12` / `Dalvik/2.1.0` 一律返回 **403**，
 * 换成 `Mozilla/5.0 (...) Chrome/120 Mobile Safari/537.36` 才是 200。
 * 这不是参数问题——所有「某地搜不到、北京能搜到」的空结果都由此引起。
 *
 * 响应上限（C-03）：{@link #MAX_BYTES} 默认 8 MB，{@code get/bytes} 与
 * {@link #openStream} 共用同一道闸门；超限抛 {@link IOException} 并写明真实上限与 URL，
 * 不再让一个异常大的响应把堆拖垮。
 */
final class LcHttp {

    private static final String TAG = "LCHTTP";
    private static final int TIMEOUT_MS = 8000;

    /** 单次响应最大字节数（默认 8 MB）：get/bytes/openStream 共用。 */
    static final int MAX_BYTES = 8 * 1024 * 1024;

    /**
     * 全局 UA（只此一份，别再写字面量）。
     * 天地图 WAF 对 `Luopan/…`、`okhttp/…`、`Dalvik/…` 返回 403，
     * 对浏览器形态 UA 返回 200（2026-11-05 实测，见 F-06-F-07 诊断）。
     */
    static final String UA =
            "Mozilla/5.0 (Linux; Android 12; LC) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    static String get(String url) throws Exception {
        return get(url, null);
    }

    /** 带额外请求头的 GET（天气接口必须带 Referer，见 WeatherClient 类注释）。 */
    static String get(String url, Map<String, String> headers) throws Exception {
        return new String(bytes(url, MAX_BYTES, headers), StandardCharsets.UTF_8);
    }

    /** 二进制下载（图片/瓦片）：与 get() 共用 UA、超时与字节上限。 */
    static byte[] bytes(String url) throws Exception {
        return bytes(url, MAX_BYTES);
    }

    /**
     * 日志/异常里的 URL 脱敏（MD-01）：只保留 `scheme://host/path`，**剔掉整个 query**。
     *
     * 天地图接口的 `tk=` 就在 query 里；以前失败日志把整条 URL 打进 logcat，
     * 任何能读 logcat 的通道都能把 key 取走。异常文案也走这里，
     * 免得调用方 `Log.w(..., e)` 又把带 tk 的 message 漏出去。
     */
    static String redact(String url) {
        if (url == null) return "";
        try {
            URL u = new URL(url);
            String path = u.getPath() == null ? "" : u.getPath();
            return u.getProtocol() + "://" + u.getHost() + path;
        } catch (Exception e) {
            int q = url.indexOf('?');
            return q < 0 ? url : url.substring(0, q);
        }
    }

    static byte[] bytes(String url, int maxBytes) throws Exception {
        return bytes(url, maxBytes, null);
    }

    static byte[] bytes(String url, int maxBytes, Map<String, String> headers) throws Exception {
        InputStream in = openStream(url, maxBytes, headers);
        try {
            return readAll(in);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // 关闭失败不影响已读到的内容
            }
        }
    }

    /**
     * 打开一个已校验 HTTP 200 的输入流，统一带上 UA/超时/字节上限。
     * 调用方负责 close()——close 时同时断开底层连接。
     *
     * 用于「边读边解析」的场景（如按目标尺寸采样解码图片），
     * 这样不需要先把整个响应读成 byte[] 再解（C-02：避免整包字节驻留内存）。
     * 流超过 {@code maxBytes} 时抛 {@link IOException}，错误文案写明上限与 URL。
     */
    static InputStream openStream(String url, int maxBytes) throws Exception {
        return openStream(url, maxBytes, null);
    }

    /** @param headers 额外请求头（可 null）；UA 固定用 {@link #UA}，这里不接受覆盖 */
    static InputStream openStream(String url, int maxBytes, Map<String, String> headers) throws Exception {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS * 2);
        c.setRequestProperty("User-Agent", UA);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if ("User-Agent".equalsIgnoreCase(e.getKey())) continue;   // UA 只认全局那份
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int code;
        try {
            code = c.getResponseCode();
        } catch (Exception e) {
            c.disconnect();
            Log.w(TAG, "GET open fail " + redact(url) + " : " + e);
            throw e;
        }
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code + " " + redact(url));
        }
        InputStream raw = new FilterInputStream(c.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    c.disconnect();
                }
            }
        };
        return new BoundedStream(raw, maxBytes, url);
    }

    /** 读到 EOF；字节上限由 {@link BoundedStream} 负责（超限抛 IOException）。 */
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /** 计数闸门：超过上限立刻抛，不把多余内容读进内存。 */
    private static final class BoundedStream extends FilterInputStream {

        private final int maxBytes;
        private final String url;
        private long total;

        BoundedStream(InputStream in, int maxBytes, String url) {
            super(in);
            this.maxBytes = maxBytes;
            this.url = url;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count(n);
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long k = super.skip(n);
            if (k > 0) count(k);
            return k;
        }

        private void count(long n) throws IOException {
            total += n;
            if (total > maxBytes) {
                throw new IOException("响应超过上限 " + maxBytes + " 字节（已读 " + total
                        + " 字节）：" + redact(url));
            }
        }
    }

    private LcHttp() {
    }
}
