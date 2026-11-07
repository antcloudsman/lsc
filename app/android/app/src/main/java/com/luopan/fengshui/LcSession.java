package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * M4 会话存储：token 用 **Android Keystore AES/GCM** 加密后放私有 SharedPreferences。
 *
 * 安全口径（总监 M4 红线）：
 * - 明文 token 不落盘、不进日志；SharedPreferences 是 `MODE_PRIVATE`；
 * - 密钥由 Keystore 生成并保管（不可导出），密文 = base64(iv + ciphertext)；
 * - `clear()` 同时删 token 与用户信息，登出后立即回未登录态。
 *
 * 取不到 Keystore（极端 ROM）时降级为「不持久化」：本次进程内有效，不写明文——
 * 宁可让用户重登，也不把 token 明文落盘。
 */
final class LcSession {

    private static final String TAG = "LCSESSION";
    private static final String PREFS = "lc_session";
    private static final String K_TOKEN_ENC = "token_enc";
    private static final String K_USER = "user";
    private static final String K_EXPIRES = "expires_at";
    private static final String KEY_ALIAS = "lc_session_key";

    /** Keystore 不可用时的进程内 token（不落盘）。 */
    private static String volatileToken;

    private LcSession() {}

    static void save(Context context, String token, JSONObject user, String expiresAt) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String enc = encrypt(token);
        if (enc == null) {
            volatileToken = token;
            p.edit().remove(K_TOKEN_ENC).apply();
            Log.w(TAG, "keystore unavailable, token kept in memory only");
        } else {
            volatileToken = null;
            p.edit().putString(K_TOKEN_ENC, enc).apply();
        }
        p.edit()
                .putString(K_USER, user == null ? null : user.toString())
                .putString(K_EXPIRES, expiresAt)
                .apply();
    }

    static String token(Context context) {
        if (volatileToken != null) return volatileToken;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String enc = p.getString(K_TOKEN_ENC, null);
        if (enc == null) return null;
        String t = decrypt(enc);
        if (t == null) {
            // 密钥失效（恢复出厂/清除凭据）：清掉，让用户重登，不留半残状态。
            clear(context);
        }
        return t;
    }

    static JSONObject user(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString(K_USER, null);
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (Throwable t) {
            return null;
        }
    }

    static String expiresAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_EXPIRES, null);
    }

    /** 当前账号数字 uid（服务端 `user.uid`，与收藏点 `userId` 同源）；未登录返回 0。 */
    static int uid(Context context) {
        JSONObject u = user(context);
        return u == null ? 0 : u.optInt("uid", 0);
    }

    static boolean isLoggedIn(Context context) {
        return token(context) != null;
    }

    static void clear(Context context) {
        volatileToken = null;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    // ---------------- AES/GCM over Android Keystore ----------------

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);
        if (Build.VERSION.SDK_INT >= 28) b.setRandomizedEncryptionRequired(true);
        kg.init(b.build());
        return kg.generateKey();
    }

    private static String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Throwable t) {
            Log.w(TAG, "encrypt failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private static String decrypt(String b64) {
        try {
            byte[] all = Base64.decode(b64, Base64.NO_WRAP);
            if (all.length < 13) return null;
            byte[] iv = new byte[12];
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Throwable t) {
            Log.w(TAG, "decrypt failed: " + t.getClass().getSimpleName());
            return null;
        }
    }
}

