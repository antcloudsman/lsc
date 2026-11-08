package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 当前位置符号（M7 第 3 条的另一半）：**蓝色椭圆 + 白色描边 + 圈外一枚同色小三角**。
 *
 * 原版取证（逆向 `dbf40a5` / `5243418`，审核验 `17d2b30` 独立复核）：
 * - 蓝主体 bbox **45×34 / 47×35 设备 px**，主色 **#0077FF** rgb(0,119,255)（精确等色像素 726 个，两人各测一遍一致）；
 * - 通体描一圈白边，白环厚度**没有可靠值**（54×41 与 58×29 两个口径互相矛盾，均不作规格），实现取 3px 量级；
 * - 三角**与主体不连通**（连通域 2 块：主体 789 px、三角 67 px，三角 14×8 设备 px），
 *   原因是白色描边从中间穿过——三角画在描边**外侧**；所以不能画成「水滴」，也不能画成贴着圆的三角；
 * - 三角方位：相对主体「向上偏左 27.3°」；**但 bearing→朝向的映射未取证**（只有一张静态图），
 *   这里按默认尖朝上绘制，台账写「旋转映射未取证」；
 * - 位置：**地图坐标层**（随拖动/缩放），屏幕 bbox 左下 (133,1462) 一带，不是屏幕中心。
 * - **旋转（逆向 `a61da79` 静态取证）**：原版是 MapLibre Marker，`rotationAlignment:"map"`，
 *   `setRotation(target − mapBearing)` 与地图 bearing 抵消 ⇒ **三角屏幕指向角 = 传入的 bearing 值本身**；
 *   MapLibre `setRotation` 顺时针为正、素材三角在正上方 ⇒ **指向角 = +heading，0 = 正上方**，
 *   与地图 bearing 无关，2D/3D 只改形状不改角度（`pitchAlignment:"map"`）。
 *
 * 为什么仍放在原生层（总监 2026-11-08 裁定）：与屏心符号同一层、不进贴地 canvas；
 * 位置由页面 `screenPointOf(lat,lng)` 投影后回传，本视图只负责「把标记画在那个屏幕坐标上」。
 * 已知边界：拖图时标记按 250ms 轮询，可能滞后 1 帧；罗盘态不显示（原版该态是否显示未取证）。
 */
public class LocationPointerView extends View {

    /** 蓝主体：47×35 设备 px（÷3.375 → dp）。 */
    private static final float RX_DP = 47f / 2f / 3.375f;
    private static final float RY_DP = 35f / 2f / 3.375f;
    /** 白描边：3 设备 px（量级，非定值）。 */
    private static final float STROKE_DP = 3f / 3.375f;
    /** 圈外小三角：14×8 设备 px，质心相对主体中心 (−9.2, −17.7) 设备 px。 */
    private static final float TRI_W_DP = 14f / 3.375f;
    private static final float TRI_H_DP = 8f / 3.375f;
    private static final float TRI_DX_DP = -9.2f / 3.375f;
    private static final float TRI_DY_DP = -17.7f / 3.375f;
    /** 视图 68×68 设备 px（整体跨度 58–68 的上沿），主体中心落在 (34, 38)。 */
    private static final int VIEW_DP = Math.round(68f / 3.375f);
    private static final float CX_DP = 34f / 3.375f;
    private static final float CY_DP = 38f / 3.375f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tri = new Path();
    /** 设备朝向（度，0=正北，顺时针为正）；三角按 +heading 旋转。 */
    private float heading = 0f;

    public LocationPointerView(Context context) {
        super(context);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.rgb(0, 119, 255));        // #0077FF
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(STROKE_DP * getResources().getDisplayMetrics().density);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int px = Math.round(VIEW_DP * d);
        setMeasuredDimension(px, px);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float cx = CX_DP * d;
        float cy = CY_DP * d;
        float rx = RX_DP * d;
        float ry = RY_DP * d;

        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, fill);
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, stroke);

        // 圈外独立小三角（描边外侧，与主体不连通）：基准位置质心在 (−9.2, −17.7)、尖朝上，
        // 再整体按 +heading 绕主体中心旋转（原版 marker 旋转等价于整枚标记绕 anchor 转）。
        canvas.save();
        canvas.rotate(heading, cx, cy);
        float tcx = cx + TRI_DX_DP * d;
        float tcy = cy + TRI_DY_DP * d;
        float hw = TRI_W_DP * d / 2f;
        float hh = TRI_H_DP * d / 2f;
        tri.reset();
        tri.moveTo(tcx, tcy - hh);
        tri.lineTo(tcx - hw, tcy + hh);
        tri.lineTo(tcx + hw, tcy + hh);
        tri.close();
        canvas.drawPath(tri, fill);
        canvas.restore();
    }

    /** 设置设备朝向（度）；三角指向角 = +heading，0 = 正上方。 */
    void setHeading(float deg) {
        float h = ((deg % 360f) + 360f) % 360f;
        // 最短角差：359.9° → 0.1° 不该因为「差 359.8 度」被跳过
        float diff = Math.abs(h - heading);
        if (diff > 180f) diff = 360f - diff;
        if (diff < 0.3f) return;
        heading = h;
        invalidate();
    }

    /** 把本视图摆到「地图经纬度投影出来的屏幕点」上；入参是 WebView 的 CSS px。 */
    void showAt(float cssX, float cssY) {
        float d = getResources().getDisplayMetrics().density;
        setX(cssX * d - CX_DP * d);
        setY(cssY * d - CY_DP * d);
        if (getVisibility() != View.VISIBLE) setVisibility(View.VISIBLE);
    }
}
