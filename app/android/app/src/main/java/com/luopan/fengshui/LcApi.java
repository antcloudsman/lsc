package com.luopan.fengshui;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * M4 v2 客户端 API 封装（自建服务端，见 `backend/api.md`）。
 *
 * - 地址固定 `http://127.0.0.1:8787`；真机需先 `adb reverse tcp:8787 tcp:8787`（服务端只绑宿主机回环）。
 * - 鉴权头按契约：`token: <opaque>`（原版头名）+ `Authorization: Bearer <token>`（我方扩展，二者等价）。
 * - 信封 `{code,msg,data}`：**只看 `code == 200`**，不看 HTTP 状态（注册/新建是 201 但 code=200）。
 * - 密码/token 不落日志（LogRedact 纪律）。
 *
 * 全部方法是阻塞调用，调用方放后台线程，UI 线程只收结果。
 */
final class LcApi {

    static final String BASE = "http://127.0.0.1:8787";
    private static final int TIMEOUT_MS = 8000;

    /** 业务错误（信封 code != 200）。data 在 409 冲突时是服务端快照。 */
    static final class ApiError extends Exception {
        final int code;
        final JSONObject data;

        ApiError(int code, String msg, JSONObject data) {
            super(msg == null ? ("HTTP " + code) : msg);
            this.code = code;
            this.data = data;
        }
    }

    private LcApi() {}

    // ---------------- 账号 ----------------

    static JSONObject register(String username, String password, String displayName) throws Exception {
        JSONObject b = new JSONObject();
        b.put("username", username);
        b.put("password", password);
        if (displayName != null && !displayName.trim().isEmpty()) b.put("display_name", displayName.trim());
        return request("POST", "/v2/auth/register", b, null);
    }

    static JSONObject login(String username, String password) throws Exception {
        JSONObject b = new JSONObject();
        b.put("username", username);
        b.put("password", password);
        return request("POST", "/v2/auth/login", b, null);
    }

    static JSONObject logout(String token) throws Exception {
        return request("POST", "/v2/auth/logout", new JSONObject(), token);
    }

    static JSONObject me(String token) throws Exception {
        return request("GET", "/v2/auth/me", null, token);
    }

    static JSONObject updateDisplayName(String token, String displayName) throws Exception {
        JSONObject b = new JSONObject();
        b.put("display_name", displayName);
        return request("PATCH", "/v2/auth/me", b, token);
    }

    static JSONObject changePassword(String token, String current, String next) throws Exception {
        JSONObject b = new JSONObject();
        b.put("current_password", current);
        b.put("new_password", next);
        return request("POST", "/v2/auth/password", b, token);
    }

    // ---------------- 收藏点 ----------------

    static JSONObject listPoints(String token, int since, int limit) throws Exception {
        String path = "/v2/userCollectPoint/list?pageNum=1&limit=" + limit + (since > 0 ? ("&since=" + since) : "");
        return request("GET", path, null, token);
    }

    static JSONObject savePoint(String token, JSONObject point) throws Exception {
        return request("POST", "/v2/userCollectPoint/save", point, token);
    }

    static JSONObject deletePoint(String token, long id) throws Exception {
        return request("DELETE", "/v2/userCollectPoint/delete?id=" + id, null, token);
    }

    // ---------------- 反馈 ----------------

    /** 原版 `AddFeedbackReqModel`：type 只落 2（问题反馈），其它值服务端 400。 */
    static JSONObject addFeedback(String token, String contactWay, String reasons, String remark) throws Exception {
        JSONObject b = new JSONObject();
        b.put("attachments", "");
        b.put("contactWay", contactWay == null ? "" : contactWay);
        b.put("orderNum", "");
        b.put("reasons", reasons == null ? "" : reasons);
        b.put("remark", remark == null ? "" : remark);
        b.put("type", 2);
        return request("POST", "/v2/feedback/add", b, token);
    }

    // ---------------- HTTP ----------------

    private static JSONObject request(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(BASE + path);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                c.setRequestProperty("token", token);
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            byte[] payload = null;
            if (body != null) {
                payload = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(payload);
                }
            }
            int http = c.getResponseCode();
            InputStream in = http >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = readAll(in);
            android.util.Log.d("LCAPI", method + " " + path + " -> " + http);
            if (text == null || text.trim().isEmpty()) {
                if (http >= 400) throw new ApiError(http, null, null);
                return null;
            }
            JSONObject env = new JSONObject(text);
            int code = env.optInt("code", http);
            if (code != 200) {
                throw new ApiError(code, env.optString("msg", null), env.optJSONObject("data"));
            }
            return env.optJSONObject("data");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return null;
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
