package com.luopan.fengshui;

import java.util.ArrayList;
import java.util.List;

/**
 * F-15 四项统计（唯一口径，docs/features/F-15-轨迹-免费实现方案.md §4）。
 * 复算脚本 tools/track/track_stats.py 必须与本类逐位一致（同过滤/同分段/同窗口）。
 */
final class TrackStats {

    static final double R_EARTH = 6371008.8;
    static final double ACC_MAX = 30.0;
    static final double JUMP_MPS = 40.0;
    static final long LONG_GAP_MS = 120_000L;
    static final long PAUSE_MS = 60_000L;
    static final double PAUSE_MPS = 0.5;
    static final long MAX_WINDOW_MS = 2_000L;

    static final class Result {
        final double distanceM;
        final double movingSec;
        final double avgKmh;
        final double maxKmh;
        final int segments;
        final int points;
        final long firstT;
        final long lastT;

        Result(double distanceM, double movingSec, double avgKmh, double maxKmh,
               int segments, int points, long firstT, long lastT) {
            this.distanceM = distanceM;
            this.movingSec = movingSec;
            this.avgKmh = avgKmh;
            this.maxKmh = maxKmh;
            this.segments = segments;
            this.points = points;
            this.firstT = firstT;
            this.lastT = lastT;
        }

        boolean hasMax() {
            return !Double.isNaN(maxKmh);
        }

        boolean hasAvg() {
            return !Double.isNaN(avgKmh);
        }
    }

    private TrackStats() {
    }

    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lng2 - lng1);
        double x = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R_EARTH * Math.asin(Math.sqrt(x));
    }

    static Result compute(List<TrackPoint> raw) {
        // 预处理：精度 >30m 丢弃；时间倒序丢弃；跨段/长间隔/跳点断段。
        List<List<TrackPoint>> segs = new ArrayList<>();
        List<TrackPoint> cur = new ArrayList<>();
        TrackPoint prev = null;
        for (TrackPoint p : raw) {
            if (p.acc > ACC_MAX) continue;
            if (prev == null) {
                cur.add(p);
                prev = p;
                continue;
            }
            if (p.t <= prev.t) continue;
            double d = haversine(prev.lat, prev.lng, p.lat, p.lng);
            double dtSec = (p.t - prev.t) / 1000.0;
            boolean brk = p.seg != prev.seg
                    || (p.t - prev.t) > LONG_GAP_MS
                    || (dtSec > 0 && d / dtSec > JUMP_MPS);
            if (brk) {
                if (!cur.isEmpty()) segs.add(cur);
                cur = new ArrayList<>();
            }
            cur.add(p);
            prev = p;
        }
        if (!cur.isEmpty()) segs.add(cur);

        double distanceM = 0;
        double movingSec = 0;
        int points = 0;
        long firstT = 0;
        long lastT = 0;
        boolean first = true;

        for (List<TrackPoint> seg : segs) {
            points += seg.size();
            for (TrackPoint p : seg) {
                if (first) {
                    firstT = p.t;
                    first = false;
                }
                lastT = p.t;
            }
            for (int i = 1; i < seg.size(); i++) {
                TrackPoint a = seg.get(i - 1);
                TrackPoint b = seg.get(i);
                double d = haversine(a.lat, a.lng, b.lat, b.lng);
                double dt = (b.t - a.t) / 1000.0;
                if (d >= 0.5) distanceM += d;
                if (!(dt >= PAUSE_MS / 1000.0 && d / dt < PAUSE_MPS)) movingSec += dt;
            }
        }

        double avg = movingSec > 0 ? (distanceM / movingSec) * 3.6 : Double.NaN;
        double max = maxSpeed(segs);
        return new Result(distanceM, movingSec, avg, max, segs.size(), points, firstT, lastT);
    }

    /** 中心差分平滑：窗口 ≥2s；跨段不参与；峰值需有 ±10% 相邻候选，否则取次高稳定值。 */
    private static double maxSpeed(List<List<TrackPoint>> segs) {
        List<Double> cands = new ArrayList<>();
        for (List<TrackPoint> seg : segs) {
            int n = seg.size();
            if (n < 3) continue;
            for (int i = 1; i < n - 1; i++) {
                int a = i - 1;
                int b = i + 1;
                while ((seg.get(b).t - seg.get(a).t) < MAX_WINDOW_MS && (a > 0 || b < n - 1)) {
                    if (a > 0) a--;
                    if (b < n - 1) b++;
                }
                long win = seg.get(b).t - seg.get(a).t;
                if (win < MAX_WINDOW_MS) continue;
                double d = haversine(seg.get(a).lat, seg.get(a).lng, seg.get(b).lat, seg.get(b).lng);
                cands.add(d / (win / 1000.0));
            }
        }
        if (cands.isEmpty()) return Double.NaN;
        double best = Double.NaN;
        for (int i = 0; i < cands.size(); i++) {
            double v = cands.get(i);
            boolean stable = false;
            for (int j = 0; j < cands.size(); j++) {
                if (i == j) continue;
                double w = cands.get(j);
                if (Math.abs(v - w) <= v * 0.10) {
                    stable = true;
                    break;
                }
            }
            if (stable && (Double.isNaN(best) || v > best)) best = v;
        }
        if (!Double.isNaN(best)) return best * 3.6;
        double fallback = Double.NEGATIVE_INFINITY;
        for (double v : cands) fallback = Math.max(fallback, v);
        return fallback * 3.6;
    }
}
