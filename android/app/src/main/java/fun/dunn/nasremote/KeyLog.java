package fun.dunn.nasremote;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.util.ArrayDeque;
import java.util.Locale;

final class KeyLog {
    static boolean connected;
    static long until;
    static long started;
    static String stage = "未开始";
    static final ArrayDeque<String> lines = new ArrayDeque<>();
    static int serviceCount;
    static int activityCount;

    static String key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return "OK";
            case KeyEvent.KEYCODE_BACK: return "BACK";
            case KeyEvent.KEYCODE_MENU: return "MENU";
            default: return null;
        }
    }

    static void start(Context context, String name) {
        stage = name;
        started = SystemClock.elapsedRealtime();
        until = started + 90000;
        lines.clear();
        serviceCount = activityCount = 0;
        save(context);
    }

    static void record(Context context, String source, KeyEvent event) {
        String name = key(event.getKeyCode());
        if (name == null || SystemClock.elapsedRealtime() >= until) return;
        if ("SERVICE".equals(source)) serviceCount++; else activityCount++;
        String action = event.getAction() == KeyEvent.ACTION_UP ? "UP" :
                event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : "OTHER";
        lines.addFirst(String.format(Locale.ROOT, "%6.1fs %-7s %-5s %s code=%d scan=%d repeat=%d",
                (SystemClock.elapsedRealtime()-started)/1000.0, source, name, action,
                event.getKeyCode(), event.getScanCode(), event.getRepeatCount()));
        while (lines.size() > 80) lines.removeLast();
        save(context);
    }

    static String report() {
        StringBuilder result = new StringBuilder("测试场景：" + stage + "\n无障碍 SERVICE=" + serviceCount
                + "  应用 ACTIVITY=" + activityCount + "\n");
        for (String line : lines) result.append(line).append('\n');
        return result.toString();
    }

    static void save(Context context) {
        context.getSharedPreferences("diagnostic", Context.MODE_PRIVATE).edit()
                .putString("last_report", report()).apply();
    }
}
