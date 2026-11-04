package com.luopan.fengshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * P0-2 读数面板，按逆向 R4b 的「智能读盘」弹层实测结构：
 *
 *   向北 <h>°     坐南 <h+180>°
 *   <14 类读数>：类型名   向：X  坐：Y
 *
 * 数据全部来自 mechanics.json 的 rings[].labels（CompassData.ringText），
 * 与原版 getFaceText/getBackText 同源：face=labels[i(h)]、back=labels[i(h+180)]。
 *
 * 与原版的唯一差别（D-006 + 总监裁决 T-15）：原版 isVip 的 11 行只给 🔒 和「点击查看」，
 * 这里全部显示向/坐 —— 有意解除付费墙，登记为「有意解除」而不是缺口。
 */
public final class CompassReadSheet {

    private CompassReadSheet() {}

    public static void show(Activity activity, float heading) {
        float h = SensorMath.normalize(heading);
        float back = SensorMath.normalize(heading + 180f);
        CompassData.Reading r = CompassData.read(h);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(activity);
        title.setText("智能读盘");
        title.setTextSize(20f);
        title.setTextColor(Color.rgb(220, 190, 130));
        title.setPadding(0, 0, 0, pad / 2);
        root.addView(title);

        TextView head = new TextView(activity);
        head.setText(String.format(java.util.Locale.US,
                "向北 %.1f°    坐南 %.1f°\n坐%s山  向%s山",
                h, back, CompassData.MOUNTAINS[r.sitting], CompassData.MOUNTAINS[r.mountain]));
        head.setTextSize(14f);
        head.setTextColor(Color.rgb(200, 190, 170));
        head.setPadding(0, 0, 0, pad);
        root.addView(head);

        for (int t = 1; t <= 14; t++) {
            String name = clean(CompassData.RINGS[t - 1].text);
            String face = clean(CompassData.ringText(t, h));
            String sit = clean(CompassData.ringText(t, back));

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad / 2, 0, pad / 2);

            TextView nameView = new TextView(activity);
            nameView.setText(name);
            nameView.setTextSize(14f);
            nameView.setTextColor(Color.rgb(200, 190, 170));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f);
            nameView.setLayoutParams(lp);
            row.addView(nameView);

            TextView valueView = new TextView(activity);
            valueView.setText("向：" + face + "    坐：" + sit);
            valueView.setTextSize(15f);
            valueView.setTextColor(Color.rgb(235, 228, 215));
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.7f);
            valueView.setLayoutParams(lp2);
            row.addView(valueView);

            root.addView(row);
        }

        new AlertDialog.Builder(activity)
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .show();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\u200c", "");
    }
}
