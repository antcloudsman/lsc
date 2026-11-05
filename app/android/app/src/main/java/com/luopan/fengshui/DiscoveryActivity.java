package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-08 探索（LC地图 新建页，2026-11-05）。
 *
 * 原版：三页签（专题地图 / 环球地理 / 历史战争），页面本身不判 VIP，卡片内容会员专享。
 * LC：三页签全开放、卡片可点、无会员角标；内容用 **Wikimedia Commons（CC）** 素材自建：
 *   - 图片：Commons API（`action=query&generator=search…&prop=imageinfo`）按主题词取图，
 *     卡片上展示作者与许可（Artist / LicenseShortName）；
 *   - 文案：本文件自写的 1–2 句说明（不复用原版文案）；
 *   - 取不到图/接口失败时卡片写「暂无免费数据源」（占位，不造假、不弹付费）。
 * 素材许可：CC BY-SA 等，署名随卡片展示；页面底部常显来源。
 *
 * AT id：lc_discover_back/title/content/tabs/list/note/card_0..4/img_0..4。
 */
public class DiscoveryActivity extends LcFeatureActivity {

    private static final String TAG = "LCDISCOVER";

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final int[] CARD_IDS = {
            R.id.lc_discover_card_0, R.id.lc_discover_card_1, R.id.lc_discover_card_2,
            R.id.lc_discover_card_3, R.id.lc_discover_card_4
    };
    private static final int[] IMG_IDS = {
            R.id.lc_discover_img_0, R.id.lc_discover_img_1, R.id.lc_discover_img_2,
            R.id.lc_discover_img_3, R.id.lc_discover_img_4
    };

    private static final class Topic {
        final String title;
        final String desc;
        final String query;

        Topic(String title, String desc, String query) {
            this.title = title;
            this.desc = desc;
            this.query = query;
        }
    }

    private static final Topic[] MAPS = {
            new Topic("世界气候类型图", "柯本气候分类的全球分布：按气温与降水把陆地划成热带、干旱、温带、寒带等类型。", "Köppen climate classification map"),
            new Topic("世界人口密度图", "按行政单元统计的每平方公里人口数，用来观察人口向沿海与平原集中的格局。", "world population density map"),
            new Topic("世界地形图", "以高程分层设色表现山脉、高原、盆地与平原的全球分布。", "world topography map"),
            new Topic("洋流分布图", "全球表层洋流路径：暖流与寒流如何影响沿岸气候。", "ocean currents map"),
            new Topic("时区图", "按经度划分的时区与日界线，用于换算各地当地时间。", "time zone map")
    };

    private static final Topic[] WORLD = {
            new Topic("撒哈拉沙漠", "世界最大的热沙漠，横跨北非多国，昼夜温差大。", "Sahara desert"),
            new Topic("亚马孙雨林", "南美洲赤道带的热带雨林，生物多样性极高。", "Amazon rainforest"),
            new Topic("大堡礁", "澳大利亚东北岸外的珊瑚礁群，世界自然遗产。", "Great Barrier Reef"),
            new Topic("喜马拉雅山脉", "亚欧板块与印度板块碰撞抬升的山系，珠穆朗玛峰在此。", "Himalaya mountains"),
            new Topic("尼亚加拉瀑布", "北美五大湖水系上的瀑布群，横跨美加边界。", "Niagara Falls")
    };

    private static final Topic[] WAR = {
            new Topic("赤壁之战", "公元 208 年，孙刘联军于长江赤壁一带以火攻破曹军。", "Battle of Red Cliffs"),
            new Topic("滑铁卢战役", "1815 年拿破仑在布鲁塞尔以南战败，帝国终结。", "Battle of Waterloo"),
            new Topic("诺曼底登陆", "1944 年盟军在法国诺曼底海岸登陆，开辟西线战场。", "Normandy landings"),
            new Topic("斯大林格勒战役", "1942–43 年苏德在伏尔加河畔的转折性会战。", "Battle of Stalingrad"),
            new Topic("中途岛海战", "1942 年太平洋战场，美军击沉日军四艘航母。", "Battle of Midway")
    };

    private static final class Info {
        final String url;
        final String artist;
        final String license;

        Info(String url, String artist, String license) {
            this.url = url;
            this.artist = artist;
            this.license = license;
        }
    }

    private final Map<String, Info> infoCache = new HashMap<>();

    /**
     * 位图缓存有界（审查 C-11）：原先是无上限 HashMap，翻三个页签、图片越来越大时只增不减。
     *
     * 上限取两条中最先到的一条（双保险，写进注释便于复核）：
     *   - 张数 ≤ {@link #BMP_MAX_COUNT}（32 张：三个页签共 15 个主题，够来回切换不重下）；
     *   - 字节 ≤ {@link #BMP_MAX_BYTES}（48 MB：按采样后 ~720px 宽的照片算，约 25–30 张占满）。
     * 淘汰只丢引用、**不 recycle**——被淘汰的那张可能正显示在某个 ImageView 上，
     * 立刻回收会触发「trying to use a recycled bitmap」。回收统一放在 onDestroy。
     */
    private static final int BMP_MAX_COUNT = 32;
    private static final long BMP_MAX_BYTES = 48L * 1024 * 1024;

    private final LinkedHashMap<String, Bitmap> bmpCache =
            new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > BMP_MAX_COUNT || cachedBytes() > BMP_MAX_BYTES;
                }
            };

    private boolean disabled;
    private LinearLayout listBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f08");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_discover_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_discover_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_discover_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_discover_content;
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        if (disabled) {
            Toast.makeText(this, R.string.lc_weather_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.lc_setting_bg));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setId(R.id.lc_discover_tabs);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        int[] names = {R.string.lc_discover_tab_map, R.string.lc_discover_tab_world,
                R.string.lc_discover_tab_war};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(12f);
            b.setOnClickListener(v -> renderTab(idx));
            tabs.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setId(R.id.lc_discover_list);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView note = new TextView(this);
        note.setId(R.id.lc_discover_note);
        note.setTextSize(10f);
        note.setTextColor(getColor(R.color.lc_label_gray));
        note.setPadding(dp(12), dp(4), dp(12), dp(8));
        note.setText(R.string.lc_discover_note);
        root.addView(note);

        content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        renderTab(0);
    }

    private void renderTab(int idx) {
        listBox.removeAllViews();
        Topic[] topics = idx == 0 ? MAPS : (idx == 1 ? WORLD : WAR);
        for (int i = 0; i < topics.length && i < CARD_IDS.length; i++) {
            listBox.addView(buildCard(topics[i], i));
        }
    }

    private View buildCard(Topic t, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setId(CARD_IDS[index]);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(getColor(R.color.panel));
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        ImageView iv = new ImageView(this);
        iv.setId(IMG_IDS[index]);
        iv.setAdjustViewBounds(true);
        iv.setMinimumHeight(dp(110));
        iv.setBackgroundColor(0xFFEEF1F4);
        card.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));

        TextView title = new TextView(this);
        title.setText(t.title);
        title.setTextSize(16f);
        title.setTextColor(getColor(R.color.lc_setting_text));
        title.setPadding(0, dp(8), 0, dp(2));
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(t.desc);
        desc.setTextSize(12f);
        desc.setTextColor(getColor(R.color.lc_label_gray));
        card.addView(desc);

        TextView credit = new TextView(this);
        credit.setTextSize(10f);
        credit.setTextColor(getColor(R.color.lc_label_gray));
        credit.setPadding(0, dp(4), 0, 0);
        credit.setText(R.string.lc_discover_loading);
        card.addView(credit);

        card.setOnClickListener(v -> showDetail(t, credit.getText().toString()));

        loadImage(t, iv, credit);
        return card;
    }

    /** 缓存当前占用字节（淘汰判据用；最多 32 张，逐张求和的开销可忽略）。 */
    private long cachedBytes() {
        long sum = 0;
        for (Bitmap b : bmpCache.values()) {
            if (b != null && !b.isRecycled()) sum += b.getByteCount();
        }
        return sum;
    }

    /** 卡片图片的目标宽度 px：屏幕宽 - 列表左右 padding - 卡片左右 padding（列表不会横向滚动）。 */
    private int cardTargetWidthPx() {
        int w = getResources().getDisplayMetrics().widthPixels - dp(12) * 2 - dp(10) * 2;
        return Math.max(dp(120), w);
    }

    private synchronized Bitmap cachedBitmap(String key) {
        return bmpCache.get(key);
    }

    private synchronized void cacheBitmap(String key, Bitmap bmp) {
        bmpCache.put(key, bmp);
    }

    private void loadImage(Topic t, ImageView iv, TextView credit) {
        Bitmap cached = cachedBitmap(t.title);
        if (cached != null && !cached.isRecycled()) {
            iv.setImageBitmap(cached);
            Info ci = infoCache.get(t.title);
            if (ci != null) credit.setText(creditLine(ci));
            return;
        }
        POOL.execute(() -> {
            try {
                Info info = infoCache.get(t.title);
                if (info == null) {
                    info = fetchInfo(t.query);
                    if (info != null) infoCache.put(t.title, info);
                }
                if (info == null) throw new IllegalStateException("no image");
                /* C-02：流式 + 按卡片宽度采样解码，不再 bytes() 整包再 decodeByteArray */
                Bitmap bmp = LcImage.decodeSampled(info.url, cardTargetWidthPx());
                final Info fi = info;
                cacheBitmap(t.title, bmp);
                MAIN.post(() -> {
                    iv.setImageBitmap(bmp);
                    credit.setText(creditLine(fi));
                });
            } catch (Throwable e) {
                Log.w(TAG, "image fail " + t.title + " : " + e);
                MAIN.post(() -> credit.setText(R.string.lc_discover_no_source));
            }
        });
    }

    private Info fetchInfo(String query) {
        try {
            String q = URLEncoder.encode(query, "UTF-8");
            String url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch="
                    + q + "&gsrnamespace=6&gsrlimit=1&prop=imageinfo"
                    + "&iiprop=url%7Cextmetadata&iiurlwidth=640&format=json";
            JSONObject root = new JSONObject(LcHttp.get(url));
            JSONObject queryObj = root.optJSONObject("query");
            if (queryObj == null) return null;
            JSONObject pages = queryObj.optJSONObject("pages");
            if (pages == null) return null;
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                JSONObject page = pages.optJSONObject(keys.next());
                if (page == null) continue;
                org.json.JSONArray ii = page.optJSONArray("imageinfo");
                if (ii == null || ii.length() == 0) continue;
                JSONObject info = ii.optJSONObject(0);
                if (info == null) continue;
                String thumb = info.optString("thumburl", info.optString("url", ""));
                JSONObject meta = info.optJSONObject("extmetadata");
                String artist = meta == null ? "" : stripHtml(meta.optJSONObject("Artist") == null
                        ? "" : meta.optJSONObject("Artist").optString("value", ""));
                String license = meta == null ? "" : (meta.optJSONObject("LicenseShortName") == null
                        ? "" : meta.optJSONObject("LicenseShortName").optString("value", ""));
                if (!thumb.isEmpty()) return new Info(thumb, artist, license);
            }
        } catch (Throwable e) {
            Log.w(TAG, "commons fail " + query + " : " + e);
        }
        return null;
    }

    private static String stripHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "").trim();
    }

    private String creditLine(Info info) {
        StringBuilder sb = new StringBuilder("图片：Wikimedia Commons");
        if (!info.artist.isEmpty()) sb.append(" · ").append(info.artist);
        if (!info.license.isEmpty()) sb.append(" · ").append(info.license);
        return sb.toString();
    }

    private void showDetail(Topic t, String credit) {
        String msg = t.desc + "\n\n" + credit;
        new AlertDialog.Builder(this)
                .setTitle(t.title)
                .setMessage(msg)
                .setPositiveButton(R.string.lc_discover_close, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        /* 与下载线程的 cacheBitmap() 互斥：避免边回收边 put 触发 ConcurrentModificationException */
        synchronized (this) {
            for (Bitmap b : bmpCache.values()) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
            bmpCache.clear();
        }
        super.onDestroy();
    }
}
