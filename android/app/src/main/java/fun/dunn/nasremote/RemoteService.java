package fun.dunn.nasremote;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.util.HashSet;

public final class RemoteService extends AccessibilityService {
    static RemoteService instance;
    static boolean foreground;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashSet<Integer> consumed = new HashSet<>();
    private BridgeClient client;
    private long startsAt;
    private long backAt;
    private long repeatAt;
    private final SourceProbe sourceProbe = new SourceProbe();
    private final Handler automatic = new Handler(Looper.getMainLooper());
    private int currentSource = -1;
    private boolean tvVisible;
    private boolean pausedUntilLeave;
    private long retryAt;
    private String lastRuntime = "";
    private final CurrentInputSource sourceReader = new CurrentInputSource();
    private final java.util.concurrent.ExecutorService sourceWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean sourceReadPending;
    private boolean destroyed;
    private long sourceRevision;
    private long nextSourceRead;
    private void refreshSource() {
        if (sourceReadPending || SystemClock.elapsedRealtime() < nextSourceRead) return;
        sourceReadPending = true;
        long revision = sourceRevision;
        sourceWorker.execute(() -> {
            int source = sourceReader.read(this);
            automatic.post(() -> {
                sourceReadPending = false;
                if (destroyed) return;
                nextSourceRead = SystemClock.elapsedRealtime() + 1000;
                getSharedPreferences("source_query", MODE_PRIVATE).edit().putInt("source", source).putLong("time", System.currentTimeMillis()).apply();
                // Do not overwrite a newer broadcast with an older, slower query.
                if (revision != sourceRevision || source < 0) return;
                if (source != currentSource) pausedUntilLeave = false;
                currentSource = source;
                // On this TCL firmware current source is 17 in launcher/settings and 10 on HDMI 3.
                // Reconcile transient window events against live state, never saved history.
                tvVisible = source == targetSource();
                evaluateAutomatic();
            });
        });
    }
    private int selectedHdmi() { return getSharedPreferences("bridge", MODE_PRIVATE).getInt("selected_hdmi", 3); }
    private int targetSource() { return getSharedPreferences("bridge", MODE_PRIVATE).getInt("source_hdmi_" + selectedHdmi(), selectedHdmi() == 3 ? 10 : -1); }
    void targetChanged() { sourceRevision++; currentSource = -1; tvVisible = false; pausedUntilLeave = false; nextSourceRead = 0; stopBridge(); }
    private final Runnable autoCheck = new Runnable() {
        @Override public void run() {
            refreshSource();
            evaluateAutomatic();
            String runtime = "source="+currentSource+" tvVisible="+tvVisible+" paused="+pausedUntilLeave+" "+bridgeStatus();
            if (!runtime.equals(lastRuntime)) {
                lastRuntime = runtime;
                getSharedPreferences("bridge_runtime", MODE_PRIVATE).edit().putString("state", runtime).apply();
            }
            automatic.postDelayed(this, 1000);
        }
    };
    void inputSourceChanged(int source) {
        sourceRevision++;
        currentSource = source;
        // The firmware can report an internal TV window class instead of TVActivity.
        // A fresh source switch is also evidence that the input surface is active.
        tvVisible = targetSource() >= 0 && source == targetSource();
        if (source != targetSource()) pausedUntilLeave = false;
        evaluateAutomatic();
    }
    private void evaluateAutomatic() {
        boolean enabled = getSharedPreferences("bridge", MODE_PRIVATE).getBoolean("auto_enabled", true);
        boolean active = enabled && targetSource() >= 0 && currentSource == targetSource() && tvVisible && !foreground && !pausedUntilLeave
                && ((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        if (!active) { if (client != null) stopBridge(); return; }
        if (client != null && (client.ready || "连接中".equals(client.status))) return;
        if (SystemClock.elapsedRealtime() < retryAt) return;
        android.content.SharedPreferences config = getSharedPreferences("bridge", MODE_PRIVATE);
        String host = config.getString("host", ""), token = config.getString("token", "");
        retryAt = SystemClock.elapsedRealtime()+5000;
        if (client != null) client.stop();
        client = new BridgeClient();
        client.start(host, Integer.parseInt(config.getString("port", "8765")), token);
    }
    void pauseAutomatic() { pausedUntilLeave = true; stopBridge(); }
    private final Runnable backExit = () -> { if (backAt != 0) { pausedUntilLeave = true; stopBridge(); } };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { sourceRevision++; tvVisible = false; stopBridge(); }
    };
    String bridgeStatus() {
        if (startsAt != 0) return "请切到 NAS HDMI，" + Math.max(0, (startsAt-SystemClock.elapsedRealtime()+999)/1000) + " 秒后接管";
        return (targetSource() < 0 ? "请先校准输入源编号" : client == null ? "等待输入源通知" : client.status) + " · HDMI " + selectedHdmi();
    }
    void arm(String host, int port, String token) {
        stopBridge();
        startsAt = SystemClock.elapsedRealtime()+15000;
        handler.postDelayed(() -> {
            if (startsAt == 0) return;
            startsAt = 0;
            if (foreground) return;
            client = new BridgeClient(); client.start(host, port, token);
        }, 15000);
    }
    void stopBridge() {
        startsAt = 0;
        handler.removeCallbacksAndMessages(null);
        backAt = 0;
        if (client != null) { client.stop(); client.status = "转发已停止"; }
    }
    // API 33+ explicitly exports the vendor metadata receiver; older APIs lack that flag.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        setServiceInfo(info);
        KeyLog.connected = true;
        instance = this;
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(sourceProbe, new IntentFilter(SourceProbe.ACTION), Context.RECEIVER_EXPORTED);
        else registerReceiver(sourceProbe, new IntentFilter(SourceProbe.ACTION));
        automatic.post(autoCheck);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        KeyLog.record(this, "SERVICE", event);
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER) { sourceRevision++; tvVisible = false; stopBridge(); return false; }
        if (up && consumed.remove(code)) {
            if (client != null && client.ready) {
                if (code == KeyEvent.KEYCODE_BACK) {
                    if (backAt != 0 && SystemClock.elapsedRealtime()-backAt < 2000) {
                        client.key("BACK", "down"); client.key("BACK", "up");
                    }
                } else client.key(name(code), "up");
            }
            if (code == KeyEvent.KEYCODE_BACK) { backAt = 0; handler.removeCallbacks(backExit); }
            return true;
        }
        if (consumed.contains(code) && (client == null || !client.ready)) return true;
        String key = name(code);
        if (up || key == null || foreground || client == null || !client.ready) return false;
        consumed.add(code);
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) { backAt = SystemClock.elapsedRealtime(); handler.postDelayed(backExit, 2000); }
            return true;
        }
        if (event.getRepeatCount() == 0) client.key(key, "down");
        else if (SystemClock.elapsedRealtime()-repeatAt >= 120) {
            repeatAt = SystemClock.elapsedRealtime(); client.key(key, "repeat");
        }
        return true;
    }
    private static String name(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: return "OK";
            case KeyEvent.KEYCODE_BACK: return "BACK";
            case KeyEvent.KEYCODE_MENU: return "MENU";
            default: return null;
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        // Ignore toast notifications; they do not replace the foreground surface.
        if ("android.widget.Toast".contentEquals(event.getClassName() == null ? "" : event.getClassName())) return;
        sourceRevision++;
        nextSourceRead = 0;
        tvVisible = "com.tcl.tv".contentEquals(event.getPackageName() == null ? "" : event.getPackageName());
        evaluateAutomatic();
    }
    @Override public void onInterrupt() { KeyLog.until = 0; stopBridge(); }
    @Override public void onDestroy() {
        destroyed = true;
        sourceWorker.shutdownNow();
        KeyLog.connected = false;
        KeyLog.until = 0;
        stopBridge(); instance = null; unregisterReceiver(screen); unregisterReceiver(sourceProbe);
        automatic.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
