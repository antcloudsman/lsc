package com.luopan.fengshui;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * KML 2.2 读写（B 轮 P0：导入导出）。口径按 `docs/product/m7-b-round-spec.md` v1.0（`73955a7`）。
 *
 * **坐标系**（判据依赖它，不许中途改）
 * 1. 导出统一 WGS84（EPSG:4326），并在 `<ExtendedData>` 里显式写 `lc:crs`；
 * 2. 导入按声明转换：`WGS84/EPSG:4326/CGCS2000` 同一系；`GCJ02` 走反算；**声明缺失或无法识别**时
 *    不猜——`crs=null`，界面提示「坐标系未知，按 WGS84 处理」，禁止静默转换；
 * 3. **往返判据只在 WGS84 下判**：导出 → 导入 → 再导出，`coordinates` 逐点差 ≤1e-6 度（≈0.11 m）。
 *    坐标写 `%.8f`（1e-8 度 ≈ 1.1 mm），远严于判据；GCJ02 不可逆，不判误差、只判「有声明有提示」。
 *
 * **导入只认** `Point` / `LineString` / `Polygon`（外环）；`MultiGeometry`/`Style`/`NetworkLink`/
 * `GroundOverlay`/`gx:Track` 一律**跳过并计数**（不报错、不吞掉信息）。
 *
 * 说明：C 轮删掉的 `gcj2bd`/`wgs2gcj` 是死代码（无调用方、无测试），这里**没有复活它们**，
 * 而是新写一份只有 GCJ02→WGS84 单向、带回代收敛判据的实现。
 */
final class KmlIo {

    /** 一个 KML 点（跨工具都在意的字段）。 */
    static final class Point {
        String name;
        String desc;
        final double lat;
        final double lng;
        /** 坐（度，0..360）；NaN = 文件里没带。 */
        double sittingDeg = Double.NaN;
        /** 向（度，0..360）；NaN = 文件里没带。 */
        double facingDeg = Double.NaN;
        /** 点类型（项目口径：favorite）；空 = 未声明。 */
        String kind = "";
        /** 业务 id（收藏点主键）；-1 = 未带。 */
        long id = -1;
        /** 海拔（米）；NaN = 未知（**不写 0 冒充**）。 */
        double elevM = Double.NaN;
        /** 创建时间（毫秒）；-1 = 未带。 */
        long createdAt = -1;

        Point(String name, String desc, double lat, double lng) {
            this.name = name == null ? "" : name;
            this.desc = desc == null ? "" : desc;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** 解析结果：点列 + 声明坐标系（null=未声明/无法识别）+ 错误 + 跳过统计。 */
    static final class Result {
        final List<Point> points = new ArrayList<>();
        String crs;
        String error;
        /** 跳过的几何体/无关节点计数，例如 {"MultiGeometry":2,"gx:Track":1}。 */
        final java.util.LinkedHashMap<String, Integer> skipped = new java.util.LinkedHashMap<>();

        int skippedTotal() {
            int n = 0;
            for (Integer v : skipped.values()) n += v;
            return n;
        }
    }

    private KmlIo() {
    }

    // ---------------- 写 ----------------

    static String toKml(List<Point> points, String exportedIso) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("  <Document>\n");
        sb.append("    <name>山水罗盘 收藏点</name>\n");
        sb.append("    <ExtendedData>\n");
        data(sb, "lc:crs", "WGS84");
        data(sb, "lc:coordinate_order", "lon,lat,alt");
        data(sb, "lc:generator", "LC");
        data(sb, "lc:app", "山水罗盘");
        data(sb, "lc:exported", exportedIso);
        data(sb, "lc:count", String.valueOf(points.size()));
        data(sb, "lc:source", "favorites");
        sb.append("    </ExtendedData>\n");
        for (Point p : points) {
            sb.append("    <Placemark>\n");
            sb.append("      <name>").append(esc(p.name)).append("</name>\n");
            if (!p.desc.isEmpty()) sb.append("      <description>").append(esc(p.desc)).append("</description>\n");
            // 点上业务字段：**每个 Placemark 自带 6 项**（产品口径 B-1），只写在 Document 层
            // 会让导入后丢掉坐向——这个 App 的核心场景就是量坐向，丢了等于导了个半成品。
            // 缺值写空串，不写 0 冒充（0 米与"没有数据"必须可区分，同 M-06 的口径）。
            // 缺失值**一律不写这个键**（不写 0、不写空串）：
            //   写 0 会被当成真实值（M-06 的 elev=0 刚吃过这个亏）；
            //   写空串只是把"不知道"换个样子，读的人还得再判一次。
            //   导入端统一按「键不存在 = 未知」处理。
            StringBuilder pd = new StringBuilder();
            if (p.id >= 0) pdata(pd, "lc:id", String.valueOf(p.id));
            if (!p.kind.isEmpty()) pdata(pd, "lc:type", p.kind);
            if (!Double.isNaN(p.sittingDeg)) {
                pdata(pd, "lc:sit", String.format(java.util.Locale.US, "%.1f", p.sittingDeg));
            }
            if (!Double.isNaN(p.facingDeg)) {
                pdata(pd, "lc:heading", String.format(java.util.Locale.US, "%.1f", p.facingDeg));
            }
            if (!Double.isNaN(p.elevM)) {
                pdata(pd, "lc:elev", String.format(java.util.Locale.US, "%.1f", p.elevM));
            }
            if (p.createdAt >= 0) pdata(pd, "lc:created", String.valueOf(p.createdAt));
            if (pd.length() > 0) {
                sb.append("      <ExtendedData>\n").append(pd).append("      </ExtendedData>\n");
            }
            sb.append("      <Point><coordinates>")
                    .append(fmt(p.lng)).append(',').append(fmt(p.lat));
            // alt：**有值才写**；未知时只写 lon,lat——KML 允许省略 alt，不能拿 0 冒充
            if (!Double.isNaN(p.elevM)) sb.append(',').append(String.format(java.util.Locale.US, "%.1f", p.elevM));
            sb.append("</coordinates></Point>\n");
            sb.append("    </Placemark>\n");
        }
        sb.append("  </Document>\n");
        sb.append("</kml>\n");
        return sb.toString();
    }

    /** Placemark 级 Data（与 Document 级的 {@link #data} 分开命名，避免看串）。 */
    private static void pdata(StringBuilder sb, String k, String v) {
        sb.append("        <Data name=\"").append(esc(k)).append("\"><value>")
                .append(esc(v)).append("</value></Data>\n");
    }

    private static void data(StringBuilder sb, String k, String v) {
        sb.append("      <Data name=\"").append(esc(k)).append("\"><value>")
                .append(esc(v)).append("</value></Data>\n");
    }

    /** 8 位小数（产品口径）；1e-8 度 ≈ 1.1 mm。 */
    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.8f", v);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ---------------- 读 ----------------

    private static final String[] SKIP_TAGS = {
            "MultiGeometry", "Style", "StyleMap", "NetworkLink", "GroundOverlay", "ScreenOverlay",
            "gx:Track", "gx:MultiTrack", "LookAt", "Camera"
    };

    static Result parse(InputStream in) {
        Result r = new Result();
        try {
            XmlPullParser xp = Xml.newPullParser();
            xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xp.setInput(in, null);
            String curName = "";
            String curDesc = "";
            String pendingDataName = null;
            String geometry = null;          // Point / LineString / Polygon
            double curSitting = Double.NaN, curFacing = Double.NaN;
            String curKind = "";
            long curId = -1, curCreated = -1;
            double curElev = Double.NaN;
            int skipDepth = 0;               // >0 表示处在被跳过的元素内
            int ev;
            while ((ev = xp.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = xp.getName();
                    if (skipDepth > 0) {
                        skipDepth++;
                        continue;
                    }
                    if (isSkipTag(tag)) {
                        r.skipped.merge(tag, 1, Integer::sum);
                        skipDepth = 1;
                        continue;
                    }
                    if ("Data".equals(tag)) {
                        pendingDataName = xp.getAttributeValue(null, "name");
                    } else if ("Placemark".equals(tag)) {
                        curName = "";
                        curDesc = "";
                        curSitting = Double.NaN;
                        curFacing = Double.NaN;
                        curKind = "";
                        curId = -1;
                        curCreated = -1;
                        curElev = Double.NaN;
                    } else if ("name".equals(tag)) {
                        curName = safeText(xp);
                    } else if ("description".equals(tag)) {
                        curDesc = safeText(xp);
                    } else if ("Point".equals(tag) || "LineString".equals(tag) || "Polygon".equals(tag)) {
                        geometry = tag;
                    } else if ("coordinates".equals(tag)) {
                        String text = safeText(xp).trim();
                        // 只认这三种几何；其他（含 gx:coord）忽略
                        if (geometry != null) {
                            addCoords(r, text, curName, curDesc, geometry, curSitting, curFacing, curKind);
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (skipDepth > 0) {
                        skipDepth--;
                    } else {
                        String tag = xp.getName();
                        if ("Point".equals(tag) || "LineString".equals(tag) || "Polygon".equals(tag)) {
                            geometry = null;
                        }
                    }
                } else if (ev == XmlPullParser.TEXT && pendingDataName != null) {
                    String v = xp.getText() == null ? "" : xp.getText().trim();
                    if ("lc:crs".equals(pendingDataName) && !v.isEmpty()) {
                        r.crs = v;
                    } else if ("lc:sitting".equals(pendingDataName)) {
                        try { curSitting = Double.parseDouble(v); } catch (Throwable ignored) { }
                    } else if ("lc:facing".equals(pendingDataName)) {
                        try { curFacing = Double.parseDouble(v); } catch (Throwable ignored) { }
                    } else if ("lc:kind".equals(pendingDataName) || "lc:type".equals(pendingDataName)) {
                        curKind = v;
                    } else if ("lc:sit".equals(pendingDataName)) {
                        try { curSitting = Double.parseDouble(v); } catch (Throwable ignored) { }
                    } else if ("lc:heading".equals(pendingDataName)) {
                        try { curFacing = Double.parseDouble(v); } catch (Throwable ignored) { }
                    } else if ("lc:elev".equals(pendingDataName)) {
                        try { curElev = Double.parseDouble(v); } catch (Throwable ignored) { }
                    } else if ("lc:id".equals(pendingDataName)) {
                        try { curId = Long.parseLong(v); } catch (Throwable ignored) { }
                    } else if ("lc:created".equals(pendingDataName)) {
                        try { curCreated = Long.parseLong(v); } catch (Throwable ignored) { }
                    }
                    pendingDataName = null;
                }
            }
        } catch (Throwable t) {
            r.error = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
        return r;
    }

    private static boolean isSkipTag(String tag) {
        for (String s : SKIP_TAGS) if (s.equals(tag)) return true;
        return false;
    }

    /** `coordinates` 是空格分隔的 `lon,lat[,alt]` 串；LineString/Polygon 的每个顶点都收成点。 */
    private static void addCoords(Result r, String text, String name, String desc, String geometry,
                                  double sittingDeg, double facingDeg, String kind) {
        String[] tuples = text.split("\\s+");
        int idx = 0;
        for (String tuple : tuples) {
            if (tuple.isEmpty()) continue;
            String[] parts = tuple.split(",");
            if (parts.length < 2) continue;
            try {
                double lng = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                if (Math.abs(lat) > 90 || Math.abs(lng) > 180) continue;
                String tag = "Point".equals(geometry) ? name : (name + "#" + (++idx));
                Point np = new Point(tag, desc, lat, lng);
                np.sittingDeg = sittingDeg;
                np.facingDeg = facingDeg;
                np.kind = kind;
                r.points.add(np);
            } catch (NumberFormatException ignored) {
                // 单点坏值跳过，不中断整份文件
            }
        }
    }

    private static String safeText(XmlPullParser xp) {
        try {
            String t = xp.nextText();
            return t == null ? "" : t;
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------------- 坐标系 ----------------

    static boolean isGcj02(String crs) {
        return crs != null && crs.trim().toUpperCase(java.util.Locale.US).contains("GCJ");
    }

    static boolean isKnownWgsFamily(String crs) {
        if (crs == null) return false;
        String v = crs.trim().toUpperCase(java.util.Locale.US);
        return v.contains("WGS") || v.contains("4326") || v.contains("CGCS");
    }

    /** GCJ02 → WGS84（迭代反算，收敛 1e-11 度）。只做这一向：导出永远写 WGS84。 */
    static double[] gcj02ToWgs84(double lat, double lng) {
        double wLat = lat, wLng = lng;
        for (int i = 0; i < 8; i++) {
            double[] g = wgs84ToGcj02(wLat, wLng);
            double dLat = g[0] - lat, dLng = g[1] - lng;
            wLat -= dLat;
            wLng -= dLng;
            if (Math.abs(dLat) < 1e-11 && Math.abs(dLng) < 1e-11) break;
        }
        return new double[]{wLat, wLng};
    }

    static double[] wgs84ToGcj02(double lat, double lng) {
        if (!outOfChina(lat, lng)) return new double[]{lat, lng};
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lng + dLng};
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
}
