package com.luopan.fengshui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * D5 盘面骨架（差距矩阵 B1 / 产品 T-12 B 方案）：
 *   四组结构：自定义 / 常用 / 简易 / 综合
 *   常用组：5 项（原版 artwork：dial0..dial4_min），可选中生效
 *   简易 / 综合：置灰 + 明确占位（目录来自服务端 GET api/compass/list，离线不可穷举）
 *
 * 不放假条目、不空白、不崩溃（产品口径）。
 */
public class CompassListActivity extends Activity {

    private static final int[] THUMBS = {
            R.drawable.dial0, R.drawable.dial1_min, R.drawable.dial2_min,
            R.drawable.dial3_min, R.drawable.dial4_min
    };

    private int selected = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("lc_prefs", MODE_PRIVATE);
        selected = prefs.getInt("dial_index", 1);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(24, 20, 16));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("罗盘盘面");
        title.setTextColor(Color.rgb(220, 190, 130));
        title.setTextSize(24f);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        addGroup(root, "自定义罗盘", "上传自定义盘面：原版为本地 + 服务端目录，此处暂未实现（有意省略）", false);
        addGroup(root, "常用罗盘", null, true);
        addGroup(root, "简易罗盘", "需联网加载盘面目录（原版来自 GET api/compass/list，离线不可穷举）", false);
        addGroup(root, "综合罗盘", "需联网加载盘面目录（原版来自 GET api/compass/list，离线不可穷举）", false);

        setContentView(scroll);
    }

    private void addGroup(LinearLayout root, String name, String placeholder, boolean enabled) {
        TextView header = new TextView(this);
        header.setText(name);
        header.setTextSize(18f);
        header.setTextColor(enabled ? Color.rgb(235, 228, 215) : Color.rgb(120, 112, 100));
        header.setPadding(0, 24, 0, 8);
        root.addView(header);

        if (!enabled) {
            TextView ph = new TextView(this);
            ph.setText(placeholder);
            ph.setTextSize(13f);
            ph.setTextColor(Color.rgb(120, 112, 100));
            ph.setPadding(0, 4, 0, 12);
            root.addView(ph);
            return;
        }

        for (int i = 0; i < CompassData.COMPASS_TYPES.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 8, 0, 8);
            row.setBackgroundColor(idx == selected
                    ? Color.argb(60, 220, 190, 130) : Color.TRANSPARENT);

            ImageView thumb = new ImageView(this);
            int size = (int) (48 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
            thumb.setLayoutParams(lp);
            thumb.setImageResource(THUMBS[i % THUMBS.length]);
            row.addView(thumb);

            TextView label = new TextView(this);
            label.setText(CompassData.COMPASS_TYPES[i] + (idx == selected ? "（当前）" : ""));
            label.setTextSize(16f);
            label.setTextColor(Color.rgb(235, 228, 215));
            row.addView(label);

            row.setOnClickListener(v -> {
                selected = idx;
                getSharedPreferences("lc_prefs", MODE_PRIVATE).edit()
                        .putInt("dial_index", idx).apply();
                Toast.makeText(this, "已选择 " + CompassData.COMPASS_TYPES[idx],
                        Toast.LENGTH_SHORT).show();
                finish();
            });
            root.addView(row);
        }
    }
}
