package fun.dunn.nasremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Local-only input-source metadata; does not read screen text. */
final class SourceProbe extends BroadcastReceiver {
    static final String ACTION = "com.tcl.inputsourcechanged";
    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        StringBuilder result = new StringBuilder();
        result.append(System.currentTimeMillis()).append(' ');
        Bundle extras = intent.getExtras();
        if (extras != null) for (String name : extras.keySet()) {
            Object value = extras.get(name);
            if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                String text = String.valueOf(value);
                result.append(name).append('=').append(text.substring(0, Math.min(120, text.length()))).append(';');
            }
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences("source_probe", Context.MODE_PRIVATE);
        String history = preferences.getString("history", "") + result + "\n";
        if (history.length() > 4096) history = history.substring(history.length()-4096);
        preferences.edit().putString("history", history).apply();
        if (RemoteService.instance != null && "InputSourceChanged".equals(intent.getStringExtra("Type"))) {
            RemoteService.instance.inputSourceChanged(intent.getIntExtra("arg1", -1));
        }
    }
}
