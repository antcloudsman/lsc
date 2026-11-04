package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Rotating Feng Shui compass (罗盘). The dial rotates so the mountain at the reading
 * heading appears under the fixed top pointer (红点).
 */
public class CompassView extends View {

    private float heading = 0f;
    private int dialType = 1; // 0..4 (常用罗盘 default)

    public void setTrustEnabled(boolean enabled) {
        this.trustEnabled = enabled;
        invalidate();
    }

    public void setDialType(int t) {
        this.dialType = t;
        invalidate();
    }
    private boolean followSensor = true;
    private boolean trustEnabled = false;

    public interface OnHeadingChangeListener {
        void onHeadingChanged(float heading);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lastAngle = 0f;
    private boolean dragging = false;
    private OnHeadingChangeListener listener;

    public void setOnHeadingChangeListener(OnHeadingChangeListener l) {
        this.listener = l;
    }

    private void notifyHeadingChanged() {
        if (listener != null) listener.onHeadingChanged(heading);
    }

    public CompassView(Context context) {
        super(context);
        init();
    }

    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public float getHeading() {
        return heading;
    }

    public void setSensorHeading(float azimuth) {
        if (followSensor) {
            heading = azimuth;
            invalidate();
        }
    }

    public void reset() {
        followSensor = true;
        heading = 0f;
        invalidate();
        notifyHeadingChanged();
    }

    public void setHeadingManually(float h) {
        followSensor = false;
        heading = normalize(h);
        invalidate();
        notifyHeadingChanged();
    }

    private static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                followSensor = false;
                lastAngle = (float) Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float a = (float) Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
                    float delta = a - lastAngle;
                    if (delta > 180f) delta -= 360f;
                    if (delta < -180f) delta += 360f;
                    heading = normalize(heading - delta);
                    lastAngle = a;
                    invalidate();
                    notifyHeadingChanged();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 8f;

        int bg = Color.argb(210, 24, 20, 16);
        int gold = Color.rgb(220, 190, 130);

        // Face.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(gold);
        canvas.drawCircle(cx, cy, r, paint);

        // Degree ticks + numbers.
        for (int d = 0; d < 360; d += 5) {
            float a0 = r * 0.965f;
            float a1 = (d % 30 == 0) ? r * 0.90f : (d % 10 == 0 ? r * 0.925f : r * 0.945f);
            float[] p0 = pos(cx, cy, a0, d, heading);
            float[] p1 = pos(cx, cy, a1, d, heading);
            paint.setColor(Color.rgb(160, 135, 90));
            paint.setStrokeWidth(1f);
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        textPaint.setColor(gold);
        textPaint.setTextSize(11f * density());
        for (int d = 0; d < 360; d += 30) {
            float[] p = pos(cx, cy, r * 0.865f, d, heading);
            drawText(canvas, String.valueOf(d), p[0], p[1], textPaint);
        }

        // 分金 (3°) and 龙 (5°/6°) ticks + labels (detail rings; dial 0 = 二十四山(阴) hides them).
        if (dialType > 0) {
        for (int d = 0; d < 360; d += (int) CompassData.FENJIN_STEP) {
            float[] p0 = pos(cx, cy, r * 0.845f, d, heading);
            float[] p1 = pos(cx, cy, r * 0.825f, d, heading);
            paint.setColor(Color.rgb(120, 100, 70));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        for (int d = 0; d < 360; d += (int) CompassData.DRAGON72_STEP) {
            float[] p0 = pos(cx, cy, r * 0.805f, d, heading);
            float[] p1 = pos(cx, cy, r * 0.785f, d, heading);
            paint.setColor(Color.rgb(110, 130, 90));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        for (int k = 0; k < 60; k++) {
            float d = CompassData.DRAGON60_OFFSET + k * CompassData.DRAGON60_STEP;
            float[] p0 = pos(cx, cy, r * 0.775f, d, heading);
            float[] p1 = pos(cx, cy, r * 0.755f, d, heading);
            paint.setColor(Color.rgb(90, 110, 130));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        // B2: 当前格高亮（只画刻度时用来标出指针所在格）
        drawCellMarker(canvas, cx, cy, r, 6, 0.852f, 0.813f, Color.rgb(255, 216, 120));
        drawCellMarker(canvas, cx, cy, r, 5, 0.818f, 0.782f, Color.rgb(190, 235, 160));
        drawCellMarker(canvas, cx, cy, r, 7, 0.788f, 0.752f, Color.rgb(160, 200, 255));
        }

        // 64 hexagrams (六十四卦) ring: 64 × 5.625° = 360° (综合盘 dial 4 only).
        if (dialType == 4) {
        for (int d = 0; d < 64; d++) {
            float deg = d * CompassData.HEXAGRAM64_DEG;
            float[] p0 = pos(cx, cy, r * 0.745f, deg, heading);
            float[] p1 = pos(cx, cy, r * 0.725f, deg, heading);
            paint.setColor(Color.rgb(150, 120, 150));
            canvas.drawLine(p0[0], p0[1], p1[0], p1[1], paint);
        }
        }

        // Three 24-mountain plates.
        textPaint.setTextSize(17f * density());
        for (int m = 0; m < 24; m++) {
            float[] p = pos(cx, cy, r * 0.715f, m * CompassData.MOUNTAIN_STEP, heading);
            textPaint.setColor(wuxingColor(CompassData.WUXING[m]));
            drawText(canvas, CompassData.MOUNTAINS[m], p[0], p[1], textPaint);
        }
        textPaint.setTextSize(13f * density());
        for (int m = 0; m < 24; m++) {
            float[] p = pos(cx, cy, r * 0.52f, m * CompassData.MOUNTAIN_STEP + CompassData.HUMAN_OFFSET, heading);
            textPaint.setColor(Color.rgb(180, 200, 220));
            drawText(canvas, CompassData.MOUNTAINS[m], p[0], p[1], textPaint);
        }
        for (int m = 0; m < 24; m++) {
            float[] p = pos(cx, cy, r * 0.34f, m * CompassData.MOUNTAIN_STEP + CompassData.HEAVEN_OFFSET, heading);
            textPaint.setColor(Color.rgb(230, 200, 170));
            drawText(canvas, CompassData.MOUNTAINS[m], p[0], p[1], textPaint);
        }

        // B2: bagua text from mechanics.json rings (TYPE_2 后天卦位).
        CompassData.RingDef bagua = CompassData.RINGS[1];
        textPaint.setTextSize(12f * density());
        for (int i = 0; i < bagua.labels.length; i++) {
            float d = bagua.offset + (i + 0.5f) * bagua.interval;
            float[] p = pos(cx, cy, r * 0.18f, d, heading);
            String name = bagua.labels[i].replace("\u200c", "");
            textPaint.setColor(wuxingColor(guaWuxing(name.substring(0, 1))));
            drawText(canvas, name, p[0], p[1], textPaint);
        }

        // P0-1: 天池（仅读数可信包开启时）
        if (trustEnabled) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density());
            paint.setColor(gold);
            canvas.drawCircle(cx, cy, r * 0.085f, paint);
            paint.setStrokeWidth(1f * density());
            canvas.drawLine(cx, cy - r * 0.085f, cx, cy - r * 0.045f, paint);
        }

        // Fixed crosshair + top pointer (screen space).
        paint.setColor(Color.argb(120, 180, 50, 50));
        paint.setStrokeWidth(1f);
        canvas.drawLine(cx, cy - r, cx, cy + r, paint);
        paint.setColor(Color.argb(80, 180, 50, 50));
        canvas.drawLine(cx - r, cy, cx + r, cy, paint);
        paint.setColor(Color.rgb(200, 40, 40));
        paint.setStyle(Paint.Style.FILL);
        float top = cy - r;
        canvas.drawPath(triangle(cx, top, 10f), paint);

        // Center readout.
        CompassData.Reading rd = CompassData.read(heading);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f * density());
        drawText(canvas, String.format("%.1f°", rd.heading), cx, cy - 22f * density(), textPaint);
        textPaint.setTextSize(17f * density());
        textPaint.setColor(wuxingColor(CompassData.WUXING[rd.mountain]));
        drawText(canvas, CompassData.MOUNTAINS[rd.mountain] + "山 · " + CompassData.WUXING[rd.mountain],
                cx, cy + 10f * density(), textPaint);
        textPaint.setColor(gold);
        textPaint.setTextSize(13f * density());
        drawText(canvas, rd.jian + " · " + CompassData.YINYANG[rd.mountain],
                cx, cy + 34f * density(), textPaint);
    }

    /** B2: 指针下当前格的高亮刻度。 */
    private void drawCellMarker(Canvas canvas, float cx, float cy, float r,
                                int type, float outer, float inner, int color) {
        int i = CompassData.ringIndex(type, heading);
        float ang = CompassData.ringCenter(type, i);
        float[] a = pos(cx, cy, r * outer, ang, heading);
        float[] b = pos(cx, cy, r * inner, ang, heading);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * density());
        paint.setColor(color);
        canvas.drawLine(a[0], a[1], b[0], b[1], paint);
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static float[] pos(float cx, float cy, float r, float compassDeg, float heading) {
        double a = Math.toRadians(compassDeg - heading);
        return new float[]{(float) (cx + r * Math.sin(a)), (float) (cy - r * Math.cos(a))};
    }

    private void drawText(Canvas canvas, String s, float x, float y, Paint p) {
        canvas.drawText(s, x, y - (p.ascent() + p.descent()) / 2f, p);
    }

    private static android.graphics.Path triangle(float cx, float top, float half) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, top);
        path.lineTo(cx - half, top + 16f);
        path.lineTo(cx + half, top + 16f);
        path.close();
        return path;
    }

    private static int wuxingColor(String wx) {
        switch (wx) {
            case "木": return Color.rgb(90, 180, 90);
            case "火": return Color.rgb(220, 90, 70);
            case "土": return Color.rgb(190, 150, 80);
            case "金": return Color.rgb(220, 190, 90);
            case "水": return Color.rgb(90, 150, 220);
            default: return Color.WHITE;
        }
    }

    private static String guaWuxing(String gua) {
        switch (gua) {
            case "坎": return "水";
            case "艮": case "坤": return "土";
            case "震": case "巽": return "木";
            case "离": return "火";
            case "乾": case "兑": return "金";
            default: return "";
        }
    }
}
