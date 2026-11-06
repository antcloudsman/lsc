package com.luopan.fengshui;

import java.util.Locale;

/**
 * R2-025：坐标输入的四坐标系换算（数据侧，不依赖外部接口，离线可用）。
 *
 * 口径与出处：
 *  - GCJ02：火星坐标，国内地图（天地图/高德）常用；WGS84→GCJ02 用公开的偏移算法（Krasovsky 椭球参数，
 *    我国范围内偏移量 50–500 m，非线性）。
 *  - BD09：百度坐标 = GCJ02 再做一次 BD 偏移（公开算法）。
 *  - WGS84：GPS 原始坐标。
 *  - CGCS2000：国家大地坐标系 2000。它与 WGS84 在厘米级差异（<0.1 m），六位小数下显示同值；
 *    这里按「同值 + 标注」输出，不编造第三位以后的小数差异。
 *
 * 原版对照（`orig/map-jump-beijing.xml`）：四行文本中心 y=638/824/1010/1196、文本高 46，
 * 行间距 186；本类只负责「算值」，行距/排版属 UI（R2-025 UI 部分）。
 */
final class CoordConv {

    /** WGS84 → GCJ02。返回 {lat, lng}。 */
    static double[] wgs84ToGcj02(double lat, double lng) {
        if (outOfChina(lat, lng)) return new double[]{lat, lng};
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    /** GCJ02 → WGS84：用一次反向偏移 + 迭代 2 次收敛（误差 < 1e-6 度）。 */
    static double[] gcj02ToWgs84(double lat, double lng) {
        if (outOfChina(lat, lng)) return new double[]{lat, lng};
        double wLat = lat, wLng = lng;
        for (int i = 0; i < 3; i++) {
            double[] g = wgs84ToGcj02(wLat, wLng);
            wLat += (lat - g[0]);
            wLng += (lng - g[1]);
        }
        return new double[]{wLat, wLng};
    }

    /** GCJ02 → BD09。 */
    static double[] gcj02ToBd09(double lat, double lng) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat / 180.0 * Math.PI * 3000.0);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng / 180.0 * Math.PI * 3000.0);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }

    /** BD09 → GCJ02。 */
    static double[] bd09ToGcj02(double lat, double lng) {
        double x = lng - 0.0065, y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y / 180.0 * Math.PI * 3000.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x / 180.0 * Math.PI * 3000.0);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    /**
     * 输入一个「用户以为的 WGS84」坐标，输出四行显示值。
     * 行序与原版一致：GCJ02 / WGS84 / BD09 / CGCS2000。
     */
    static String rowsJson(double wgsLat, double wgsLng) {
        double[] gcj = wgs84ToGcj02(wgsLat, wgsLng);
        double[] bd = gcj02ToBd09(gcj[0], gcj[1]);
        return String.format(Locale.US,
                "{\"rows\":["
                        + "{\"crs\":\"GCJ02\",\"lat\":%.6f,\"lng\":%.6f},"
                        + "{\"crs\":\"WGS84\",\"lat\":%.6f,\"lng\":%.6f},"
                        + "{\"crs\":\"BD09\",\"lat\":%.6f,\"lng\":%.6f},"
                        + "{\"crs\":\"CGCS2000\",\"lat\":%.6f,\"lng\":%.6f}"
                        + "],\"note\":\"CGCS2000 与 WGS84 厘米级等价（<0.1 m）\"}",
                gcj[0], gcj[1], wgsLat, wgsLng, bd[0], bd[1], wgsLat, wgsLng);
    }

    /**
     * 解析用户输入：接受 `lat,lng` 或 `lng,lat`（按中国范围内哪个像纬度自动判定），
     * 返回 {lat, lng} 或 null。小数位不限，范围校验 -90..90 / -180..180。
     */
    static double[] parsePair(String text) {
        if (text == null) return null;
        String s = text.trim().replace('，', ',').replace('、', ',').replace(' ', ',');
        String[] parts = s.split(",");
        if (parts.length != 2) return null;
        try {
            double a = Double.parseDouble(parts[0].trim());
            double b = Double.parseDouble(parts[1].trim());
            double lat, lng;
            if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
                // 常见写法 lat,lng；若 a 明显像经度（>90）则反过来
                if (Math.abs(a) > 90) {
                    lng = a;
                    lat = b;
                } else {
                    lat = a;
                    lng = b;
                }
            } else if (Math.abs(b) <= 90 && Math.abs(a) <= 180) {
                lng = a;
                lat = b;
            } else {
                return null;
            }
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
            return new double[]{lat, lng};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean outOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordConv() {
    }
}
