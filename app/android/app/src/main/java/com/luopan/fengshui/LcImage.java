package com.luopan.fengshui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片下载 + 按目标宽度采样解码（审查 C-02）。
 *
 * 原写法是 `LcHttp.bytes(url) → BitmapFactory.decodeByteArray()`：
 * 整包 JPEG 字节（全尺寸）与全尺寸位图同时在堆里——Commons 上一张 4000×3000 的照片
 * 就是约 48 MB 位图 + 数 MB 字节，来回切页签很容易 OOM。
 *
 * 现改为两遍流式解码，全程不持有整包 byte[]：
 *   1) `inJustDecodeBounds` 只读图片头拿原始宽高（不分配像素）；
 *   2) 按目标宽度算 2 的幂 `inSampleSize`，再解一遍，直接把位图压到目标尺寸量级。
 * 字节上限沿用 {@link LcHttp#MAX_BYTES}（超限抛 IOException，流式抛出，不会先读满再判断）。
 */
final class LcImage {

    private static final String TAG = "LCIMG";

    /** 目标宽度夹取范围：太小浪费画质，太大则失去采样意义。 */
    private static final int MIN_TARGET_W = 64;
    private static final int MAX_TARGET_W = 1280;

    /**
     * 按目标宽度采样解码。
     *
     * @param url     图片地址（http/https）
     * @param targetW 目标宽度 px（卡片实际显示宽度；取 0 或负数时按 {@link #MIN_TARGET_W}）
     * @return 已采样的位图；失败抛异常（调用方负责显示占位，不吞成空白）
     */
    static Bitmap decodeSampled(String url, int targetW) throws Exception {
        int tw = Math.max(MIN_TARGET_W, Math.min(MAX_TARGET_W, targetW));

        BitmapFactory.Options boundsOpt = new BitmapFactory.Options();
        boundsOpt.inJustDecodeBounds = true;
        InputStream head = LcHttp.openStream(url, LcHttp.MAX_BYTES);
        try {
            BitmapFactory.decodeStream(head, null, boundsOpt);
        } finally {
            closeQuietly(head);
        }
        final int ow = boundsOpt.outWidth, oh = boundsOpt.outHeight;
        if (ow <= 0 || oh <= 0) throw new IOException("图片解码失败（读不到尺寸）：" + url);

        int sample = 1;
        while (ow / (sample * 2) >= tw) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inScaled = false;      // 不让密度缩放再放大一次：尺寸由我们自己定

        InputStream body = LcHttp.openStream(url, LcHttp.MAX_BYTES);
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeStream(body, null, opts);
        } finally {
            closeQuietly(body);
        }
        if (bmp == null) throw new IOException("图片解码失败（sample=" + sample + "）：" + url);
        Log.d(TAG, "decode " + ow + "x" + oh + " sample=" + sample + " -> "
                + bmp.getWidth() + "x" + bmp.getHeight() + " " + url);
        return bmp;
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 读完了，关流失败不影响结果
        }
    }

    private LcImage() {
    }
}
