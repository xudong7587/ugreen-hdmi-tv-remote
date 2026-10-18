package fun.dunn.nasremote;

import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One session, bounded queue, sequential ACKs; failures never replay keys. */
final class BridgeClient {
    private final ArrayBlockingQueue<JSONObject> queue = new ArrayBlockingQueue<>(32);
    private volatile Socket socket;
    private volatile boolean stopped;
    volatile boolean ready;
    volatile String status = "未连接";
    private InputStream input;
    private OutputStream output;

    void start(String host, int port, String token) {
        new Thread(() -> run(host, port, token), "nas-bridge").start();
    }
    void stop() {
        stopped = true;
        ready = false;
        queue.clear();
        Socket current = socket;
        if (current != null) try { current.close(); } catch (Exception ignored) { }
    }
    boolean key(String name, String action) {
        if (!ready || stopped) return false;
        try {
            if (queue.offer(new JSONObject().put("type", "key").put("key", name).put("action", action))) return true;
        } catch (Exception ignored) { }
        status = "队列已满，已停止";
        stop();
        return false;
    }
    private void run(String host, int port, String token) {
        try {
            status = "连接中";
            try {
                JSONObject offer = LanDiscovery.find();
                host = offer.getString("host"); port = offer.getInt("port"); token = offer.getString("token");
            } catch (Exception discoveryError) {
                if (host.isEmpty() || token.isEmpty()) throw discoveryError;
            }
            if (stopped) return;
            if (!token.matches("[0-9a-f]{32}")) throw new Exception();
            socket = new Socket();
            if (stopped) return;
            socket.connect(new InetSocketAddress(host, port), 3000);
            input = socket.getInputStream(); output = socket.getOutputStream();
            JSONObject hello = read();
            String server = hello.getString("nonce");
            if (hello.getInt("protocol") != 1 || !server.matches("[0-9a-f]{64}")) throw new Exception();
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            String client = hex(random);
            byte[] tokenBytes = token.getBytes(StandardCharsets.US_ASCII);
            write(new JSONObject().put("nonce", client).put("proof", hex(mac(tokenBytes, "client|"+server+"|"+client))));
            if (!equal(hex(mac(tokenBytes, "server|"+server+"|"+client)), read().getString("proof"))) throw new Exception();
            byte[] session = mac(tokenBytes, "session|"+server+"|"+client);
            if (!"ready".equals(unpack(session, 0, read()).getString("status"))) throw new Exception();
            ready = true; status = "已连接";
            long sequence = 1;
            while (!stopped) {
                JSONObject body = queue.poll(500, TimeUnit.MILLISECONDS);
                if (body == null) body = new JSONObject().put("type", "ping");
                if (stopped) break;
                String payload = Base64.encodeToString(body.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                write(new JSONObject().put("seq", sequence).put("payload", payload)
                        .put("mac", hex(mac(session, "c|"+sequence+"|"+payload))));
                if (!"ok".equals(unpack(session, sequence, read()).getString("status"))) throw new Exception();
                sequence++;
            }
        } catch (Exception error) {
            if (!stopped) status = "连接失败或已断开，请检查地址和配对码后重新启动";
        } finally { stop(); }
    }
    private JSONObject unpack(byte[] key, long sequence, JSONObject frame) throws Exception {
        String payload = frame.getString("payload");
        if (frame.getLong("seq") != sequence || !equal(hex(mac(key, "s|"+sequence+"|"+payload)), frame.getString("mac"))) throw new Exception();
        return new JSONObject(new String(Base64.decode(payload, Base64.NO_WRAP), StandardCharsets.UTF_8));
    }
    private JSONObject read() throws Exception {
        long end = android.os.SystemClock.elapsedRealtime() + 3000;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() < 4096) {
            long remaining = end - android.os.SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new Exception();
            socket.setSoTimeout((int) remaining);
            int value = input.read();
            if (value < 0) throw new Exception();
            if (value == 10) return new JSONObject(bytes.toString("UTF-8"));
            bytes.write(value);
        }
        throw new Exception();
    }
    private void write(JSONObject frame) throws Exception {
        output.write((frame.toString()+"\n").getBytes(StandardCharsets.UTF_8)); output.flush();
    }
    private static byte[] mac(byte[] key, String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    }
    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
    private static String hex(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte value : data) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
