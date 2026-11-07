package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 保存收藏点（原版 ui.pages.interest.SaveInterestActivity，MODULES.md 第 95 行）。
 *
 * 本地最小闭环（语义由技术总监定，原版字段/表单等 docs/gap-modules/interest.md 落地后再对齐）：
 * 从地图页带入当前坐标与坐向（extras：lc_lat / lc_lng / lc_sitting / lc_facing），
 * 填可选名称 → 保存到 GapDb.interest（时间取保存时刻）。
 * 没有带入坐标时禁用保存并明确提示，不写 0 坐标假数据。
 */
public class SaveInterestActivity extends Activity {

    static final String EXTRA_SITTING = "lc_sitting";
    static final String EXTRA_FACING = "lc_facing";

    private GapDb db;
    private EditText nameInput;
    private boolean hasPoint;
    private double lat;
    private double lng;
    private double sitting;
    private double facing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GapDb(this);

        lat = MapJump.doubleExtra(getIntent(), MapJump.EXTRA_LAT);
        lng = MapJump.doubleExtra(getIntent(), MapJump.EXTRA_LNG);
        sitting = MapJump.doubleExtra(getIntent(), EXTRA_SITTING);
        facing = MapJump.doubleExtra(getIntent(), EXTRA_FACING);
        hasPoint = !Double.isNaN(lat) && !Double.isNaN(lng)
                && !Double.isNaN(sitting) && !Double.isNaN(facing);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.addView(LcUi.toolbar(this, R.id.lc_save_back, R.id.lc_save_title,
                getString(R.string.lc_save_title)));

        LinearLayout form = new LinearLayout(this);
        form.setId(R.id.lc_save_form);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(LcUi.dp(this, 20), LcUi.dp(this, 16),
                LcUi.dp(this, 20), LcUi.dp(this, 16));
        root.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        nameInput = new EditText(this);
        nameInput.setId(R.id.lc_save_name);
        nameInput.setHint(R.string.lc_save_name_hint);
        nameInput.setHintTextColor(getColor(R.color.text_secondary));
        nameInput.setTextColor(getColor(R.color.text_primary));
        nameInput.setTextSize(16f);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        form.addView(nameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (hasPoint) {
            form.addView(valueRow(R.id.lc_save_coords, getString(R.string.lc_save_coords),
                    String.format(Locale.US, "%.6f, %.6f", lat, lng)));
            form.addView(valueRow(R.id.lc_save_sitting, getString(R.string.lc_save_sitting),
                    String.format(Locale.US, "坐%.1f° 向%.1f°", sitting, facing)));
            form.addView(valueRow(R.id.lc_save_time, getString(R.string.lc_save_time),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())));
        } else {
            TextView none = new TextView(this);
            none.setText(R.string.lc_save_no_point);
            none.setTextColor(getColor(R.color.text_secondary));
            none.setTextSize(14f);
            none.setPadding(0, LcUi.dp(this, 16), 0, 0);
            form.addView(none);
        }

        Button save = new Button(this);
        save.setId(R.id.lc_save_confirm);
        save.setText(R.string.lc_save_confirm);
        save.setEnabled(hasPoint);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = LcUi.dp(this, 24);
        form.addView(save, blp);

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private View valueRow(int id, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setId(id);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, LcUi.dp(this, 12), 0, LcUi.dp(this, 4));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(14f);
        l.setTextColor(getColor(R.color.text_secondary));
        row.addView(l, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14f);
        v.setTextColor(getColor(R.color.text_primary));
        row.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void save() {
        if (!hasPoint) return;
        String name = nameInput.getText().toString().trim();
        // owner_uid 在创建时刻写死（LcSession.uid）：拉回/新建的行都必须带归属，否则会被下次同步认领给别的账号。
        db.insertInterest(name, lat, lng, sitting, facing, LcSession.uid(this));
        Toast.makeText(this, R.string.lc_save_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}

