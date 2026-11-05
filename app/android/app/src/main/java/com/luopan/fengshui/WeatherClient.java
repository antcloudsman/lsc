package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-09 天气数据层（免费、无 key、无门槛）。
 *
 * 主源：中国天气网 `d1.weather.com.cn/weather_index/{cityid}.html`
 *   - 一份响应里同时有实况（dataSK：温度/天气/风/湿度/空气质量/更新时间）、
 *     7 日预报（fc.f[]：日期/天气码/最高最低/风向风力）、生活指数（dataZS，本页不用）；
 *   - **原站要求**：必须带 `Referer: https://www.weather.com.cn/`，缺了直接 403
 *     （2026-11-05 实测：带 Referer 200，去掉 403）。别把这一行当误加删掉。
 *   - 接口未文档化、限额未公布，可能随版本变更。
 * 备源：Open-Meteo `api.open-meteo.com/v1/forecast` + `air-quality-api.open-meteo.com`
 *   - 无 key，按经纬度取；官方免费（非商业 ≤1 万次/日，需署名）。
 *   - 境外源，仅作主源失败时的降级，不参与底图/瓦片。
 * 离线：最后一次成功响应按城市缓存（SharedPreferences "lc_weather_cache"），
 *   两个网络源都失败时展示缓存并在状态栏标「离线缓存」。
 */
final class WeatherClient {

    private static final String TAG = "LCWEATHER";

    /* 原站要求：中国天气网 d1 接口缺 Referer 一律 403（见类注释），不要删。 */
    private static final String REFERER = "https://www.weather.com.cn/";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 12; LC) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    private static final int TIMEOUT_MS = 8000;
    private static final String PREFS = "lc_weather_cache";

    interface Callback {
        void onResult(WeatherData data, String error);
    }

    static final class Day {
        String label = "";
        String date = "";
        String desc = "";
        String high = "";
        String low = "";
        String wind = "";
    }

    static final class WeatherData {
        String city = "";
        String temp = "";
        String desc = "";
        String wind = "";
        String humidity = "";
        String aqi = "";
        String updateTime = "";
        String source = "";
        boolean cached = false;
        final List<Day> days = new ArrayList<>();
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WeatherClient() {
    }

    static void load(final Context ctx, final WeatherCityTable.City city, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            WeatherData data = null;
            String error = null;
            String body = null;
            try {
                body = http("https://d1.weather.com.cn/weather_index/" + city.id + ".html", REFERER);
                data = parseD1(body, city.name);
                data.source = "中国天气网";
                cacheSave(app, city.id, body);
            } catch (Throwable t) {
                Log.w(TAG, "d1 failed: " + t);
            }
            if (data == null) {
                try {
                    data = parseOpenMeteo(
                            http("https://api.open-meteo.com/v1/forecast?latitude=" + city.lat
                                    + "&longitude=" + city.lng
                                    + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                    + "&timezone=auto&forecast_days=7", null),
                            http("https://air-quality-api.open-meteo.com/v1/air-quality?latitude="
                                    + city.lat + "&longitude=" + city.lng
                                    + "&current=european_aqi,pm2_5", null),
                            city.name);
                    data.source = "Open-Meteo";
                } catch (Throwable t) {
                    Log.w(TAG, "open-meteo failed: " + t);
                    error = "network";
                }
            }
            if (data == null) {
                String cached = cacheLoad(app, city.id);
                if (cached != null) {
                    try {
                        data = parseD1(cached, city.name);
                        data.source = "中国天气网";
                        data.cached = true;
                    } catch (Throwable ignored) {
                        // 缓存损坏按失败处理
                    }
                }
            }
            if (data == null && error == null) error = "parse";
            final WeatherData out = data;
            final String err = error;
            MAIN.post(() -> cb.onResult(out, err));
        });
    }

    /* ---------------- HTTP ---------------- */

    private static String http(String url, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        if (referer != null) c.setRequestProperty("Referer", referer);
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
        } finally {
            c.disconnect();
        }
    }

    /* ---------------- 中国天气网解析 ---------------- */

    /** 响应是 `var dataSK ={...};var fc ={...};` 这种 JS，按大括号配对截出 JSON。 */
    static String sliceJson(String body, String varName) {
        String key = "var " + varName + " =";
        int i = body.indexOf(key);
        if (i < 0) return null;
        int start = body.indexOf('{', i + key.length());
        if (start < 0) return null;
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int p = start; p < body.length(); p++) {
            char ch = body.charAt(p);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;
            } else if (ch == '"') {
                inStr = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) return body.substring(start, p + 1);
            }
        }
        return null;
    }

    static WeatherData parseD1(String body, String fallbackCity) throws Exception {
        WeatherData d = new WeatherData();
        d.city = fallbackCity;
        JSONObject sk = new JSONObject(sliceJson(body, "dataSK"));
        d.city = sk.optString("cityname", fallbackCity);
        d.temp = cleanTemp(sk.optString("temp", ""));
        d.desc = sk.optString("weather", "");
        d.wind = sk.optString("WD", "") + " " + sk.optString("WS", "");
        d.humidity = sk.optString("SD", "");
        d.aqi = sk.optString("aqi", "");
        d.updateTime = sk.optString("time", "");

        String fcStr = sliceJson(body, "fc");
        if (fcStr != null) {
            JSONArray f = new JSONObject(fcStr).optJSONArray("f");
            if (f != null) {
                for (int i = 0; i < f.length() && i < 7; i++) {
                    JSONObject o = f.optJSONObject(i);
                    if (o == null) continue;
                    Day day = new Day();
                    day.label = o.optString("fj", "");
                    day.date = o.optString("fi", "");
                    day.desc = cnWeather(o.optString("fa", ""));
                    day.high = cleanTemp(o.optString("fc", ""));
                    day.low = cleanTemp(o.optString("fd", ""));
                    day.wind = o.optString("fe", "") + " " + o.optString("fg", "");
                    d.days.add(day);
                }
            }
        }
        if (d.days.isEmpty()) throw new IllegalStateException("no forecast");
        return d;
    }

    /** 中国天气网天气码（fa/fb）→ 中文。 */
    static String cnWeather(String code) {
        switch (code) {
            case "00": return "晴";
            case "01": return "多云";
            case "02": return "阴";
            case "03": return "阵雨";
            case "04": return "雷阵雨";
            case "05": return "雷阵雨伴冰雹";
            case "06": return "雨夹雪";
            case "07": return "小雨";
            case "08": return "中雨";
            case "09": return "大雨";
            case "10": return "暴雨";
            case "11": return "大暴雨";
            case "12": return "特大暴雨";
            case "13": return "阵雪";
            case "14": return "小雪";
            case "15": return "中雪";
            case "16": return "大雪";
            case "17": return "暴雪";
            case "18": return "雾";
            case "19": return "冻雨";
            case "20": return "沙尘暴";
            case "29": return "浮尘";
            case "30": return "扬沙";
            case "31": return "强沙尘暴";
            case "53": return "霾";
            default: return "";
        }
    }

    /* ---------------- Open-Meteo 备源 ---------------- */

    static WeatherData parseOpenMeteo(String forecast, String air, String cityName) throws Exception {
        WeatherData d = new WeatherData();
        d.city = cityName;
        JSONObject root = new JSONObject(forecast);
        JSONObject cur = root.optJSONObject("current");
        if (cur != null) {
            d.temp = cleanTemp(cur.optString("temperature_2m", ""));
            d.desc = wmoWeather(cur.optInt("weather_code", -1));
            d.humidity = cur.has("relative_humidity_2m")
                    ? cur.optInt("relative_humidity_2m") + "%" : "";
            d.wind = cur.has("wind_speed_10m")
                    ? cur.optDouble("wind_speed_10m") + " km/h" : "";
        }
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray time = daily.optJSONArray("time");
            JSONArray code = daily.optJSONArray("weather_code");
            JSONArray hi = daily.optJSONArray("temperature_2m_max");
            JSONArray lo = daily.optJSONArray("temperature_2m_min");
            int n = time == null ? 0 : time.length();
            for (int i = 0; i < n && i < 7; i++) {
                Day day = new Day();
                String date = time.optString(i, "");
                day.date = date.length() >= 10 ? date.substring(5) : date;
                day.label = (i == 0) ? "今天" : "";
                day.desc = wmoWeather(code == null ? -1 : code.optInt(i, -1));
                day.high = cleanTemp(hi == null ? "" : hi.optString(i, ""));
                day.low = cleanTemp(lo == null ? "" : lo.optString(i, ""));
                d.days.add(day);
            }
        }
        if (air != null) {
            try {
                JSONObject a = new JSONObject(air).optJSONObject("current");
                if (a != null && a.has("european_aqi")) {
                    d.aqi = String.valueOf(a.optInt("european_aqi"));
                }
            } catch (Throwable ignored) {
                // AQI 失败不影响主数据
            }
        }
        if (d.temp.isEmpty() && d.days.isEmpty()) throw new IllegalStateException("empty");
        return d;
    }

    /** WMO 天气码（Open-Meteo）→ 中文。 */
    static String wmoWeather(int code) {
        if (code < 0) return "";
        if (code == 0) return "晴";
        if (code == 1) return "晴间多云";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code >= 61 && code <= 67) return "雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 85 && code <= 86) return "阵雪";
        if (code >= 95) return "雷暴";
        return "";
    }

    static String cleanTemp(String t) {
        if (t == null) return "";
        t = t.trim();
        if (t.isEmpty() || "999".equals(t) || "999.0".equals(t)) return "";
        try {
            double v = Double.parseDouble(t);
            return String.valueOf(Math.round(v));
        } catch (NumberFormatException e) {
            return t;
        }
    }

    /* ---------------- 缓存 ---------------- */

    private static void cacheSave(Context c, String cityId, String body) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("body_" + cityId, body)
                .putLong("time_" + cityId, System.currentTimeMillis())
                .apply();
    }

    private static String cacheLoad(Context c, String cityId) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long t = p.getLong("time_" + cityId, 0);
        if (t == 0) return null;
        return p.getString("body_" + cityId, null);
    }
}
