package fun.dunn.nasremote;

import android.content.Context;
import java.lang.reflect.Method;

/** Optional read-only TCL adapter. Missing/rejected vendor APIs stay unknown. */
final class CurrentInputSource {
    private Object manager;
    private Method query;
    int read(Context context) {
        try {
            if (manager == null) {
                Class<?> type = Class.forName("com.tcl.tvmanager.TTvCommonManager");
                manager = type.getMethod("getInstance", Context.class).invoke(null, context.getApplicationContext());
                query = type.getMethod("getCurrentInputSource");
            }
            Object result = query.invoke(manager);
            if (!(result instanceof Enum<?>)) return -1;
            Enum<?> source = (Enum<?>) result;
            // NONE is an API failure/unavailable result, never proof of an active input.
            if (source.name().contains("NONE") || source.name().contains("MAX")) return -1;
            return source.ordinal();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return -1;
        }
    }
}
