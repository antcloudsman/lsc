package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 罗盘态「贯穿屏幕的红色十字经线」+ 中心 8px 红色三角（M6 第 1 条）。
 *
 * 原版取证（逆向 M6 Stage 1，`docs/fidelity/m6/reverse-stage1.md`）：
 * - 载体：MapLibre Marker 上的 **CSS 伪元素** `.compass-line`，`background: red`（`#FF0000`）；
 * - 尺寸：`height/width: 2px` —— **CSS px，不是 dp**；宽 150% / 高 100%，随屏幕铺满；
 * - 对齐：`rotationAlignment: "viewport"`，即**屏幕坐标固定、不随 bearing 旋转**（只有锁定态才切 "map"）；
 * - **红三角是十字线的一部分**：`.compass-line-marker` 是 `.compass-line` 的子元素，`left:50%; top:27%`、
 *   8px border —— 它**没有独立开关**，随十字线常显（逆向 §6.8）。所以这里不接 `showPointer`。
 * - 实测（`docs/evidence/original-apk/measurements.md:14`）：纯红 `[255,0,0]`，竖线列 611–616、横线行 1347–1353
 *   → 纯红核心 6 设备像素（2 CSS px × dpr，抗锯齿会吃掉 1–2px，验收线写「≥5px 且贯穿屏幕」）。
 *
 * 为什么不画在 {@link CompassView} 里：它是 922×922 的居中方块（`MainActivity` 按 0.7378 屏宽构造），
 * 十字要「贯穿全屏」就必须覆盖 1228×2578；把它改成 MATCH_PARENT 会让 `r = min(cx,cy)-8` 从 453 变 606，
 * 盘面直径从 906 变 1212，直接破坏 K-01 实测外径。所以十字是**独立一层**，盘面几何一律不动。
 *
 * 「指针开关」（K-01 / P1-33-C 的 `showPointer`）**不归本层**：原版它控的是竖向指针图
 * `<img class="pointer" src="pointer.png">`（23×94 CSS，`setPointerVisible` 改 opacity），
 * 我方对应 `MainActivity` 里的 `pointerView`（81×324）。红三角属十字线，常显。
 *
 * 触摸：本视图不消费任何事件（不覆写 onTouchEvent，默认返回 false），事件穿透到下层地图/盘面。
 */
public class CompassCrosshairView extends View {

    /** 原版 CSS `2px` × density（本机 540dpi → 3.375 → 6.75，取整 7）。 */
    private static final float CSS_PX = 2f;
    /** 原版红三角 8px（CSS，非 dp）+ `top: 27%`（相对 marker 容器＝整个视口）。 */
    private static final float TRIANGLE_CSS_PX = 8f;
    private static final float TRIANGLE_TOP_RATIO = 0.27f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CompassCrosshairView(Context context) {
        super(context);
        paint.setColor(Color.RED);            // #FF0000，与 CSS `red` 同值
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** 圆心＝屏幕几何中心（原版实测 (614,1350)，与 1228×2700 屏的几何中心差 ≤2px）。 */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(CSS_PX * density);
        // 竖线：贯穿整个屏幕高度（不只是圆径 906px）
        canvas.drawLine(cx, 0f, cx, getHeight(), paint);
        // 横线：贯穿整个屏幕宽度
        canvas.drawLine(0f, cy, getWidth(), cy, paint);

        // 红三角：十字线的一部分（`.compass-line-marker`，left:50%、top:27%、8px），常显。
        // 尺寸与位置按 CSS 折算；**朝向**未取证（CSS 用 border 拼三角，`border-bottom` 才是尖朝上），
        // 这里按「指北尖朝上」画，等逆向补一句 border 方向再对齐。
        float half = TRIANGLE_CSS_PX * density / 2f;
        float ty = getHeight() * TRIANGLE_TOP_RATIO;
        Path tri = new Path();
        tri.moveTo(cx, ty - half);
        tri.lineTo(cx - half, ty + half);
        tri.lineTo(cx + half, ty + half);
        tri.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(tri, paint);
    }
}
