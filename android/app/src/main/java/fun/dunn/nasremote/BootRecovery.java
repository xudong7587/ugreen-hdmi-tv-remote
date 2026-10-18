package fun.dunn.nasremote;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.util.ArrayList;

/** Firmware workaround: rebind only an already-enabled instance of our service. */
public final class BootRecovery extends BroadcastReceiver {
    private static final String KEY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            record(context, "缺少开机恢复授权"); return;
        }
        PendingResult pending = goAsync();
        new Thread(() -> {
            ComponentName own = new ComponentName(context, RemoteService.class);
            boolean removed = false;
            try {
                String before = Settings.Secure.getString(context.getContentResolver(), KEY);
                ArrayList<String> others = new ArrayList<>();
                boolean enabled = false;
                if (before != null) for (String item : before.split(":")) {
                    if (own.equals(ComponentName.unflattenFromString(item))) enabled = true;
                    else if (!item.isEmpty()) others.add(item);
                }
                if (!enabled) { record(context, "用户未启用服务，不自动授权"); return; }
                removed = Settings.Secure.putString(context.getContentResolver(), KEY, String.join(":", others));
                if (!removed) throw new IllegalStateException();
                Thread.sleep(800);
            } catch (Exception error) {
                record(context, "开机恢复未完成");
            } finally {
                if (removed) {
                    try {
                        String latest = Settings.Secure.getString(context.getContentResolver(), KEY);
                        ArrayList<String> values = new ArrayList<>();
                        if (latest != null) for (String item : latest.split(":")) {
                            if (!item.isEmpty() && !own.equals(ComponentName.unflattenFromString(item))) values.add(item);
                        }
                        values.add(own.flattenToString());
                        boolean restored = Settings.Secure.putString(context.getContentResolver(), KEY, String.join(":", values));
                        record(context, restored ? "已请求系统重新绑定" : "恢复授权失败");
                    } catch (Exception error) { record(context, "恢复授权失败"); }
                }
                pending.finish();
            }
        }, "nas-boot-recovery").start();
    }
    private static void record(Context context, String state) {
        context.getSharedPreferences("boot_recovery", Context.MODE_PRIVATE).edit()
                .putString("state", state).putLong("time", System.currentTimeMillis()).apply();
    }
}
