package com.luopan.fengshui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;
import java.util.Locale;

/**
 * F-15 轨迹记录前台服务（定位类型 location）。
 *
 * 口径（F-15-轨迹-免费实现方案.md §3）：
 * - GPS 主 + NETWORK 补；不申请「始终允许后台定位」，锁屏靠前台服务；
 * - 默认 1s/3m；静止 >2min 或电量 <15% 非充电 → 5s/10m；
 * - 每点 append+flush，30s fsync；定位断 >60s 记 seg++；
 * - 进程被杀后 meta 仍为 recording，列表页出「未完成轨迹」卡。
 */
public class TrackRecorderService extends Service implements LocationListener {

    static final String ACTION_START = "com.luopan.fengshui.track.START";
    static final String ACTION_RESUME = "com.luopan.fengshui.track.RESUME";
    static final String ACTION_STOP = "com.luopan.fengshui.track.STOP";
    static final String EXTRA_ID = "track_id";

    private static final String CHANNEL = "lc_track";
    private static final int NOTIF_ID = 4301;

    private static volatile boolean running = false;
    private static volatile String currentId = "";
    private static volatile long startedAt = 0L;
    private static volatile double liveDistanceM = 0;
    private static volatile int livePoints = 0;

    static boolean isRecording() {
        return running;
    }

    static String currentId() {
        return currentId;
    }

    static long elapsedMs() {
        return running && startedAt > 0 ? System.currentTimeMillis() - startedAt : 0L;
    }

    static double distanceM() {
        return liveDistanceM;
    }

    private TrackStore store;
    private TrackMeta meta;
    private LocationManager lm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastFixT = 0L;
    private long lastFsync = 0L;
    private long lastMoveT = 0L;
    private double lastMoveLat = Double.NaN;
    private double lastMoveLng = Double.NaN;
    private double lastAccLat = Double.NaN;
    private double lastAccLng = Double.NaN;
    private long lastAccT = 0L;
    private int seq = 0;
    private int seg = 0;
    private long intervalMs = 1000L;
    private float minDistanceM = 3f;
    private boolean highAccuracy = true;
    private boolean lowPower = false;
    private boolean receiverRegistered = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            updateNotification();
            if (meta != null) {
                meta.points = livePoints;
                meta.distanceM = liveDistanceM;
                meta.lastSeq = seq;
                meta.segments = seg + 1;
                store.saveMeta(meta);
            }
            handler.postDelayed(this, 5000);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra("level", -1);
            int scale = intent.getIntExtra("scale", 100);
            int status = intent.getIntExtra("status", -1);
            if (level < 0 || scale <= 0) return;
            int pct = level * 100 / scale;
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            boolean low = pct < 15 && !charging;
            if (low && !lowPower) {
                lowPower = true;
                setSampling(5000L, 10f, "低电 5s/10m");
            } else if (!low && lowPower) {
                lowPower = false;
                setSampling(1000L, 3f, "恢复 1s/3m");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        store = new TrackStore(this);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            receiverRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopRecording();
                return START_NOT_STICKY;
            }
            if (ACTION_START.equals(action) || ACTION_RESUME.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                if (id != null && !id.isEmpty()) begin(id);
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    private void begin(String id) {
        if (running) return;
        android.content.SharedPreferences tp = getSharedPreferences("lc_track", MODE_PRIVATE);
        intervalMs = tp.getLong("intervalMs", 1000L);
        minDistanceM = tp.getFloat("minDistanceM", 3f);
        highAccuracy = tp.getBoolean("highAccuracy", true);
        meta = store.loadMeta(id);
        if (meta == null) {
            meta = TrackMeta.create(id);
        }
        meta.status = "recording";
        if (meta.startedAt == 0L) meta.startedAt = System.currentTimeMillis();
        store.saveMeta(meta);

        currentId = id;
        startedAt = meta.startedAt;
        liveDistanceM = 0;
        livePoints = 0;
        seq = meta.lastSeq;
        seg = Math.max(0, meta.segments - 1);
        lastFixT = 0L;
        lastFsync = 0L;
        lastMoveT = System.currentTimeMillis();
        lowPower = false;

        // 续记：从已有点重算当前里程/点数（不重算历史，仅用于列表显示）。
        List<TrackPoint> old = store.loadPoints(id);
        livePoints = old.size();
        if (!old.isEmpty()) {
            TrackPoint last = old.get(old.size() - 1);
            lastFixT = last.t;
            lastAccLat = last.lat;
            lastAccLng = last.lng;
            lastAccT = last.t;
            for (int i = 1; i < old.size(); i++) {
                TrackPoint a = old.get(i - 1);
                TrackPoint b = old.get(i);
                if (a.seg == b.seg && (b.t - a.t) <= TrackStats.LONG_GAP_MS) {
                    double d = TrackStats.haversine(a.lat, a.lng, b.lat, b.lng);
                    if (d >= 0.5 && d / Math.max(1e-3, (b.t - a.t) / 1000.0) <= TrackStats.JUMP_MPS) {
                        liveDistanceM += d;
                    }
                }
            }
        }

        running = true;
        startForeground(NOTIF_ID, buildNotification());
        requestUpdates();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void requestUpdates() {
        if (lm == null) return;
        if (highAccuracy) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, minDistanceM, this);
            } catch (Throwable ignored) {
            }
        }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Math.max(intervalMs, 5000L), 10f, this);
        } catch (Throwable ignored) {
        }
    }

    private void setSampling(long ms, float dist, String note) {
        intervalMs = ms;
        minDistanceM = dist;
        if (lm != null) {
            try {
                lm.removeUpdates(this);
            } catch (Throwable ignored) {
            }
            requestUpdates();
        }
        if (meta != null) {
            meta.intervalMs = ms;
            meta.minDistanceM = dist;
            meta.samplingChanges.put(note + "@" + System.currentTimeMillis());
            store.saveMeta(meta);
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (!running || meta == null || loc == null) return;
        long now = System.currentTimeMillis();
        if (lastFixT > 0 && now - lastFixT > 60000L) seg++;

        double d = Double.NaN;
        if (!Double.isNaN(lastAccLat) && lastAccT > 0) {
            d = TrackStats.haversine(lastAccLat, lastAccLng, loc.getLatitude(), loc.getLongitude());
            double dt = Math.max(0.001, (now - lastAccT) / 1000.0);
            if (d / dt > TrackStats.JUMP_MPS || (now - lastAccT) > TrackStats.LONG_GAP_MS) {
                seg++;
            } else if (d >= 0.5) {
                liveDistanceM += d;
            }
        }

        float acc = loc.hasAccuracy() ? loc.getAccuracy() : -1f;
        float spd = loc.hasSpeed() ? loc.getSpeed() : -1f;
        String src = LocationManager.GPS_PROVIDER.equals(loc.getProvider()) ? "gps" : "network";
        TrackPoint p = new TrackPoint(now, loc.getLatitude(), loc.getLongitude(), acc, spd, seg, src);
        boolean fsync = now - lastFsync >= 30000L;
        store.appendPoint(meta.id, p, fsync);
        if (fsync) lastFsync = now;

        livePoints++;
        seq++;
        meta.lastSeq = seq;
        meta.points = livePoints;
        meta.distanceM = liveDistanceM;
        meta.segments = seg + 1;
        meta.intervalMs = intervalMs;
        meta.minDistanceM = minDistanceM;
        lastFixT = now;
        lastAccLat = loc.getLatitude();
        lastAccLng = loc.getLongitude();
        lastAccT = now;

        if (!Double.isNaN(d) && d > 5) {
            lastMoveLat = loc.getLatitude();
            lastMoveLng = loc.getLongitude();
            lastMoveT = now;
            if (lowPower && intervalMs > 1000L) {
                lowPower = false;
                setSampling(1000L, 3f, "移动恢复 1s/3m");
            }
        } else if (now - lastMoveT > 120000L && intervalMs <= 1000L && !lowPower) {
            setSampling(5000L, 10f, "静止降频 5s/10m");
        }
    }

    private void stopRecording() {
        if (!running && meta == null) {
            stopSelf();
            return;
        }
        running = false;
        handler.removeCallbacks(tick);
        try {
            if (lm != null) lm.removeUpdates(this);
        } catch (Throwable ignored) {
        }
        if (meta != null) {
            List<TrackPoint> points = store.loadPoints(meta.id);
            TrackStats.Result r = TrackStats.compute(points);
            meta.status = "finished";
            meta.stoppedAt = System.currentTimeMillis();
            meta.points = r.points;
            meta.distanceM = r.distanceM;
            meta.durationSec = r.movingSec;
            meta.avgKmh = r.avgKmh;
            meta.maxKmh = r.maxKmh;
            meta.segments = Math.max(1, r.segments);
            store.saveMeta(meta);
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        currentId = "";
        handler.removeCallbacks(tick);
        if (receiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "轨迹记录",
                        NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, TrackListActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long sec = (System.currentTimeMillis() - startedAt) / 1000;
        String text = String.format(Locale.US, "正在记录轨迹 · %.2f km · %02d:%02d:%02d",
                liveDistanceM / 1000.0, sec / 3600, (sec % 3600) / 60, sec % 60);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("轨迹记录")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
