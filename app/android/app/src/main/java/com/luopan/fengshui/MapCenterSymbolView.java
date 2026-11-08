package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * 屏心符号（M7 A 轮，用户报的第 3 条之一）：**四段圆头蓝线 + 中心红点**。
 *
 * 原版取证（`docs/fidelity/m7/symbols-stage1.md`，逆向 M7 Stage 1；审核验像素复测）：
 * - 形状：四段互不相连的圆头短蓝线（上/下/左/右）+ 正中一个红色圆点，四段在中心处断开，中间留给红点；
 * - 红点：13×13 设备 px，色 **#FF5163** rgb(255,81,99)，圆心 (614,1350) = 屏幕正中；
 * - 每段蓝线：长 ≈57 × 粗 7 设备 px，色 **#009DFF** rgb(0,157,255)，圆头端点；
 * - 整体跨度：114×114 设备 px；
 * - 出现条件：55 张原版截图逐张检测 HIT=21 / NONE=33（另 1 张是罗盘竖向指针，不是本符号）——
 *   **2D 与 3D 都有**（`map-3d-mode`）、**罗盘态也有**（`compass-main-01`，地图侧核出）；
 *   弹层/面板/子页不显示（`more-sheet-*`、`map-layer-sheet`、`w3-sheet`、`tool-compass-page` 全 NONE）；
 * - 位置：23 张不同位置/缩放的截图里圆心恒为 (614,1350) → **viewport 对齐，钉屏幕中心**，
 *   不随地图拖动、不随 zoom 缩放、不随 pitch 倾斜。所以它是原生覆盖层，**不进 map.html 的贴地 canvas**
 *   （进了会随 zoom 缩放、随 pitch 变形，两条都与实测相反）。
 *
 * 归因诚实记录：`#009DFF`/`#FF5163` 在项目代码、DEX、colors.xml 里全零命中，
 * `cross.png` 素材虽然形状一致但色值是 `#6488FC`（偏紫）且主图 bundle 未引用它。
 * 所以这里**按可观测像素复刻，绘制归属未定位**——不贴 `cross.png`（贴上去会偏紫、小一半）。
 *
 * 尺寸换算：设备 px ÷ density（本机 540dpi → 3.375）= dp；下面按 dp 写，
 * 换屏时比例不变，本机实测 13 / 7 / 57 / 114 px 逐项对得上。
 *
 * 触摸：不消费事件（不覆写 onTouchEvent，默认 false），事件穿透到下层地图。
 */
public class MapCenterSymbolView extends View {

    /** 红点直径 13px → 3.85dp。 */
    private static final float DOT_DP = 3.85f;
    /** 蓝线粗 7px → 2.07dp。 */
    private static final float STROKE_DP = 2.07f;
    /** 半跨 57px（整体 114px）→ 16.9dp。 */
    private static final float HALF_SPAN_DP = 16.9f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MapCenterSymbolView(Context context) {
        super(context);
        linePaint.setColor(Color.rgb(0, 157, 255));      // #009DFF
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);         // 圆头端点
        dotPaint.setColor(Color.rgb(255, 81, 99));       // #FF5163
        dotPaint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float stroke = STROKE_DP * density;     // 7px
        float half = HALF_SPAN_DP * density;    // 57px
        // 圆头端点：内侧端点留半个笔宽，圆心处正好收口（被红点盖住）；
        // 外侧端点再收半个笔宽，圆头外沿恰好落在 57px 上 → 整体跨度 114px。
        float inner = stroke / 2f;
        float outer = half - stroke / 2f;

        linePaint.setStrokeWidth(stroke);
        canvas.drawLine(cx, cy - inner, cx, cy - outer, linePaint);   // 上
        canvas.drawLine(cx, cy + inner, cx, cy + outer, linePaint);   // 下
        canvas.drawLine(cx - inner, cy, cx - outer, cy, linePaint);   // 左
        canvas.drawLine(cx + inner, cy, cx + outer, cy, linePaint);   // 右

        canvas.drawCircle(cx, cy, DOT_DP * density / 2f, dotPaint);   // 中心红点 13px
    }
}
