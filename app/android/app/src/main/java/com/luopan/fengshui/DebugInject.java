package com.luopan.fengshui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

/**
 * 二级功能页的**测试钩子**：把一段 JS 注入「当前前台子页的 WebView」。
 *
 * 为什么需要它：调试桥（`DebugBridge` / `cmd js`）只指向主界面那个 `compassMapView`，
 * 而 C 轮 `M-06`（海拔 null 二义）必须在**海拔/山水/水系子页**里构造一个
 * `{"type":"elev","elev":"abc"}` 事件才能看到「解析失败」与「海平面 0 米」是否还混在一起。
 * 没有入口时，验收只能停在“代码看着改对了”，那是阅读证据，不是运行证据。
 *
 * 用法（仅 debug 包；命令与主桥同一个 action，但 **cmd 用 pagejs** 以示区分）：
 * <pre>
 * adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd pagejs \
 *     --es value "LCAndroid.onEvent('{\"type\":\"elev\",\"lat\":39.9,\"lng\":116.4,\"elev\":\"abc\"}')"
 * </pre>
 * 只在子页处于前台时有效（onResume 注册、onPause 反注册），不会跨页误投。
 */
final class DebugInject extends BroadcastReceiver {

    private static WeakReference<WebView> current;

    static void attach(WebView web) {
        current = new WeakReference<>(web);
    }

    static void detach() {
        current = null;
    }

    /** 是否 debug 包：release 不注册，与主桥同一条安全边界。 */
    static boolean allowed(Context ctx) {
        return (ctx.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    static void register(Context ctx, DebugInject receiver) {
        if (!allowed(ctx)) return;
        IntentFilter f = new IntentFilter(DebugBridge.ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, f);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DebugBridge.ACTION.equals(intent.getAction())) return;
        if (!"pagejs".equals(intent.getStringExtra("cmd"))) return;
        String code = intent.getStringExtra("value");
        WebView web = current == null ? null : current.get();
        if (code == null || web == null) {
            android.util.Log.d("LCDEBUG", "pagejs ignored (web=" + (web != null) + ")");
            return;
        }
        web.evaluateJavascript(code, null);
        android.util.Log.d("LCDEBUG", "pagejs -> " + code);
    }
}
