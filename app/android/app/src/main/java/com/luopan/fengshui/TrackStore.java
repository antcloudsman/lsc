package com.luopan.fengshui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * F-15 本地轨迹存储：files/tracks/<yyyyMMdd-HHmmss>.meta.json + .jsonl。
 * 只读写本机私有目录；GPX 只做导出（MediaStore Documents/LC/tracks）。
 */
final class TrackStore {

    static final String DIR = "tracks";

    private final Context ctx;

    TrackStore(Context c) {
        this.ctx = c.getApplicationContext();
    }

    File dir() {
        File d = new File(ctx.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static String newId() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    File metaFile(String id) {
        return new File(dir(), id + ".meta.json");
    }

    File pointsFile(String id) {
        return new File(dir(), id + ".jsonl");
    }

    void saveMeta(TrackMeta meta) {
        try {
            File tmp = new File(dir(), meta.id + ".meta.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(meta.toJson().toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
            File dst = metaFile(meta.id);
            if (dst.exists()) dst.delete();
            tmp.renameTo(dst);
        } catch (Throwable ignored) {
        }
    }

    TrackMeta loadMeta(String id) {
        File f = metaFile(id);
        if (!f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line == null) return null;
            return TrackMeta.fromJson(new JSONObject(line));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 每点 append + flush；fsync 由调用方按 30s 节流。 */
    void appendPoint(String id, TrackPoint p, boolean fsync) {
        File f = pointsFile(id);
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write((p.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            if (fsync) out.getFD().sync();
        } catch (Throwable ignored) {
        }
    }

    List<TrackPoint> loadPoints(String id) {
        List<TrackPoint> out = new ArrayList<>();
        File f = pointsFile(id);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                TrackPoint p = TrackPoint.fromLine(line);
                if (p != null) out.add(p);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 列表只读 meta；按开始时间倒序。 */
    List<TrackMeta> list() {
        List<TrackMeta> out = new ArrayList<>();
        File[] files = dir().listFiles((d, name) -> name.endsWith(".meta.json"));
        if (files == null) return out;
        for (File f : files) {
            String name = f.getName();
            String id = name.substring(0, name.length() - ".meta.json".length());
            TrackMeta m = loadMeta(id);
            if (m != null) out.add(m);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.startedAt, a.startedAt));
        return out;
    }

    TrackMeta findRecording() {
        for (TrackMeta m : list()) {
            if ("recording".equals(m.status)) return m;
        }
        return null;
    }

    void delete(String id) {
        metaFile(id).delete();
        pointsFile(id).delete();
    }

    long totalBytes() {
        long n = 0;
        File[] files = dir().listFiles();
        if (files != null) {
            for (File f : files) n += f.length();
        }
        return n;
    }

    void deleteAll() {
        File[] files = dir().listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    /** GPX 1.1：按 seg 分 trkseg；写 Download/LC/tracks（MediaStore），返回 Uri。 */
    Uri exportGpx(String id, List<TrackPoint> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"LC\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<trk><name>").append(id).append("</name>\n");
        int seg = -1;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        for (TrackPoint p : points) {
            if (p.seg != seg) {
                if (seg >= 0) sb.append("</trkseg>\n");
                sb.append("<trkseg>\n");
                seg = p.seg;
            }
            sb.append(String.format(Locale.US,
                    "<trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time></trkpt>\n",
                    p.lat, p.lng, iso.format(new Date(p.t))));
        }
        if (seg >= 0) sb.append("</trkseg>\n");
        sb.append("</trk></gpx>\n");

        String name = id + ".gpx";
        ContentResolver cr = ctx.getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            // MediaStore.Downloads 只允许 Download/ 作根目录（传 Documents 会抛
            // IllegalArgumentException：Primary directory Documents not allowed），
            // 所以落到 Download/LC/tracks/；insert 本身也要包住，失败只提示不崩。
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/LC/tracks");
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out == null) return null;
                    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                return uri;
            } catch (Throwable t) {
                return null;
            }
        }
        File f = new File(ctx.getExternalFilesDir(null), "LC/tracks/" + name);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Throwable t) {
            return null;
        }
        return Uri.fromFile(f);
    }
}
