package com.luopan.fengshui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * F-09 天气（LC地图 新建页，2026-11-05）。
 *
 * 口径（00-PLAN §4.0 无门槛）：免费开放、无会员/登录/付费墙；功能开关只作调试回退。
 * 数据：{@link WeatherClient}（中国天气网为主源、Open-Meteo 备源、失败用本地缓存）。
 * 页面骨架：{@link LcFeatureActivity}（52dp 白底标题栏），视觉与设置/工具管理页一致。
 *
 * AT id（稳定）：
 *   lc_weather_back / lc_weather_title / lc_weather_content / lc_weather_city /
 *   lc_weather_status / lc_weather_now_temp / lc_weather_now_desc / lc_weather_now_wind /
 *   lc_weather_now_humidity / lc_weather_aqi / lc_weather_forecast_list / lc_weather_refresh
 */
public class WeatherActivity extends LcFeatureActivity {

    private static final String PREFS = "lc_prefs";
    private static final String KEY_CITY = "weather_city";

    private boolean disabled;

    private Spinner citySpinner;
    private TextView statusView;
    private TextView tempView;
    private TextView descView;
    private TextView windView;
    private TextView humidityView;
    private TextView aqiView;
    private LinearLayout forecastList;

    private WeatherCityTable.City city;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f09");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_weather_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_weather_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_weather_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_weather_content;
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        if (disabled) {
            Toast.makeText(this, R.string.lc_weather_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.lc_setting_bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* 城市选择 */
        LinearLayout cityRow = new LinearLayout(this);
        cityRow.setOrientation(LinearLayout.HORIZONTAL);
        cityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView cityLabel = new TextView(this);
        cityLabel.setText(R.string.lc_weather_city_label);
        cityLabel.setTextSize(15f);
        cityLabel.setTextColor(getColor(R.color.lc_label_gray));
        cityLabel.setPadding(0, 0, dp(10), 0);
        cityRow.addView(cityLabel);

        citySpinner = new Spinner(this);
        citySpinner.setId(R.id.lc_weather_city);
        String[] names = new String[WeatherCityTable.CITIES.length];
        for (int i = 0; i < names.length; i++) names[i] = WeatherCityTable.CITIES[i].name;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        citySpinner.setAdapter(adapter);
        cityRow.addView(citySpinner, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(cityRow);

        statusView = new TextView(this);
        statusView.setId(R.id.lc_weather_status);
        statusView.setTextSize(12f);
        statusView.setTextColor(getColor(R.color.lc_label_gray));
        statusView.setPadding(0, dp(8), 0, dp(8));
        statusView.setText(R.string.lc_weather_loading);
        root.addView(statusView);

        /* 实况卡 */
        LinearLayout nowCard = new LinearLayout(this);
        nowCard.setOrientation(LinearLayout.VERTICAL);
        nowCard.setBackgroundColor(getColor(R.color.panel));
        nowCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout tempRow = new LinearLayout(this);
        tempRow.setOrientation(LinearLayout.HORIZONTAL);
        tempRow.setGravity(Gravity.BOTTOM);
        tempView = new TextView(this);
        tempView.setId(R.id.lc_weather_now_temp);
        tempView.setTextSize(42f);
        tempView.setTextColor(getColor(R.color.lc_setting_text));
        tempView.setText("—");
        tempRow.addView(tempView);
        descView = new TextView(this);
        descView.setId(R.id.lc_weather_now_desc);
        descView.setTextSize(16f);
        descView.setTextColor(getColor(R.color.lc_setting_text));
        descView.setPadding(dp(12), 0, 0, dp(8));
        tempRow.addView(descView);
        nowCard.addView(tempRow);

        windView = new TextView(this);
        windView.setId(R.id.lc_weather_now_wind);
        windView.setTextSize(14f);
        windView.setTextColor(getColor(R.color.lc_setting_text));
        windView.setPadding(0, dp(4), 0, 0);
        windView.setText(getString(R.string.lc_weather_wind_label) + " —");
        nowCard.addView(windView);

        humidityView = new TextView(this);
        humidityView.setId(R.id.lc_weather_now_humidity);
        humidityView.setTextSize(14f);
        humidityView.setTextColor(getColor(R.color.lc_setting_text));
        humidityView.setPadding(0, dp(4), 0, 0);
        humidityView.setText(getString(R.string.lc_weather_humidity_label) + " —");
        nowCard.addView(humidityView);

        aqiView = new TextView(this);
        aqiView.setId(R.id.lc_weather_aqi);
        aqiView.setTextSize(14f);
        aqiView.setTextColor(getColor(R.color.lc_setting_text));
        aqiView.setPadding(0, dp(4), 0, 0);
        aqiView.setText(getString(R.string.lc_weather_aqi_label) + " —");
        nowCard.addView(aqiView);
        root.addView(nowCard);

        /* 刷新 */
        Button refresh = new Button(this);
        refresh.setId(R.id.lc_weather_refresh);
        refresh.setText(R.string.lc_weather_refresh);
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> load());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(12);
        root.addView(refresh, rlp);

        /* 7 日 */
        TextView fcTitle = new TextView(this);
        fcTitle.setText(R.string.lc_weather_forecast);
        fcTitle.setTextSize(15f);
        fcTitle.setTextColor(getColor(R.color.lc_label_gray));
        fcTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(fcTitle);

        forecastList = new LinearLayout(this);
        forecastList.setId(R.id.lc_weather_forecast_list);
        forecastList.setOrientation(LinearLayout.VERTICAL);
        root.addView(forecastList);

        content.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        city = WeatherCityTable.find(prefs.getString(KEY_CITY, WeatherCityTable.CITIES[0].name));
        for (int i = 0; i < WeatherCityTable.CITIES.length; i++) {
            if (WeatherCityTable.CITIES[i].name.equals(city.name)) {
                citySpinner.setSelection(i);
                break;
            }
        }
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                city = WeatherCityTable.CITIES[position];
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_CITY, city.name).apply();
                load();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        load();
    }

    private void load() {
        if (city == null) city = WeatherCityTable.CITIES[0];
        final String want = city.name;
        statusView.setText(R.string.lc_weather_loading);
        WeatherClient.load(this, city, (data, error) -> {
            if (isFinishing() || city == null || !city.name.equals(want)) return;
            if (data == null) {
                statusView.setText(R.string.lc_weather_fail);
                return;
            }
            render(data);
        });
    }

    private void render(WeatherClient.WeatherData d) {
        String source = "Open-Meteo".equals(d.source)
                ? getString(R.string.lc_weather_source_om) : getString(R.string.lc_weather_source_d1);
        StringBuilder st = new StringBuilder(source);
        if (!d.updateTime.isEmpty()) st.append(" · 更新 ").append(d.updateTime);
        if (d.cached) st.append(" · ").append(getString(R.string.lc_weather_cached));
        statusView.setText(st.toString());

        tempView.setText(d.temp.isEmpty() ? "—" : d.temp + "°");
        descView.setText(d.desc);
        windView.setText(getString(R.string.lc_weather_wind_label) + " " + d.wind.trim());
        humidityView.setText(getString(R.string.lc_weather_humidity_label) + " "
                + (d.humidity.isEmpty() ? getString(R.string.lc_weather_na) : d.humidity));
        aqiView.setText(getString(R.string.lc_weather_aqi_label) + " "
                + (d.aqi.isEmpty() ? getString(R.string.lc_weather_na) : d.aqi));

        forecastList.removeAllViews();
        for (WeatherClient.Day day : d.days) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView left = new TextView(this);
            left.setText((day.label.isEmpty() ? "" : day.label + " ") + day.date);
            left.setTextSize(14f);
            left.setTextColor(getColor(R.color.lc_setting_text));
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView mid = new TextView(this);
            mid.setText(day.desc);
            mid.setTextSize(14f);
            mid.setTextColor(getColor(R.color.lc_label_gray));
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView right = new TextView(this);
            String hi = day.high.isEmpty() ? "—" : day.high + "°";
            String lo = day.low.isEmpty() ? "—" : day.low + "°";
            right.setText(hi + " / " + lo);
            right.setTextSize(14f);
            right.setTextColor(getColor(R.color.lc_setting_text));
            right.setGravity(Gravity.END);
            row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            forecastList.addView(row);
        }
    }
}
