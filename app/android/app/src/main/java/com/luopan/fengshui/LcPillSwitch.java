package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.CompoundButton;

/**
 * 地图设置页的开关（原版实测 156×78px @density3.375 ≈ 46.2×23.1dp）。
 *
 * 为什么不用系统 Switch：系统控件尺寸随主题/密度变化，对不上实测；这里自绘轨道+圆钮，
 * 但继承 CompoundButton，保留 checkable/checked 语义，uiautomator 可读状态、行点击可切。
 * 颜色：开 #2E53AC、关 #C0C4CC（截图取样）。
 */
final class LcPillSwitch extends CompoundButton {

    private static final float WIDTH_DP = 46.2f;
    private static final float HEIGHT_DP = 23.1f;
    /** 圆钮与轨道边缘的留白（截图约 8px ≈ 2.4dp）。 */
    private static final float INSET_DP = 2.4f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    LcPillSwitch(Context context) {
        super(context);
        init();
    }

    LcPillSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setButtonDrawable(null);
        setBackground(null);
        setClickable(true);
        setFocusable(true);
        thumbPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(LcUi.dp(getContext(), WIDTH_DP),
                LcUi.dp(getContext(), HEIGHT_DP));
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        trackPaint.setColor(isChecked() ? 0xFF2E53AC : 0xFFC0C4CC);
        canvas.drawRoundRect(0, 0, w, h, r, r, trackPaint);

        float inset = LcUi.dp(getContext(), INSET_DP);
        float thumbR = (h - inset * 2f) / 2f;
        float cx = isChecked() ? (w - r) : r;
        canvas.drawCircle(cx, h / 2f, thumbR, thumbPaint);
    }
}
