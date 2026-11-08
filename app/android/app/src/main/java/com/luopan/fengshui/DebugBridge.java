package com.luopan.fengshui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * P0-1 S2 / S8: unified debug bridge (shared with P0-0 K5 and P0-4 M2).
 *
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 94.0
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd accuracy --ei value 1
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd tilt --ef pitch 12 --ef roll 0
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd reset
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd reload
 *   adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd state   # 一行 JSON 到 LCDEBUG（只读）
 *
 * Injected heading/accuracy/tilt persist until {@code reset}; real sensor samples do
 * not clear them (only reset does), otherwise RT-002/004 would be overwritten by the
 * next frame.
 */
public class DebugBridge extends BroadcastReceiver {

    public static final String ACTION = "com.luopan.fengshui.DEBUG";

    public interface Sink {
        void onDebugCommand(String cmd, Intent intent);
    }

    private final Sink sink;

    public DebugBridge(Sink sink) {
        this.sink = sink;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null) return;
        Log.d("LCDEBUG", "cmd=" + cmd);
        sink.onDebugCommand(cmd, intent);
    }
}
