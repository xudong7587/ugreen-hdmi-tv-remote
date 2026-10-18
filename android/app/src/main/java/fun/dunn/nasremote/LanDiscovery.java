package fun.dunn.nasremote;

import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LanDiscovery {
    static JSONObject find() throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        byte[] query = new JSONObject().put("type", "nas-hdmi-discover-v1").put("nonce", nonce)
                .toString().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            Set<InetAddress> addresses = new HashSet<>();
            addresses.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface device : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!device.isUp() || device.isLoopback()) continue;
                for (InterfaceAddress address : device.getInterfaceAddresses())
                    if (address.getBroadcast() != null) addresses.add(address.getBroadcast());
            }
            for (InetAddress address : addresses)
                socket.send(new DatagramPacket(query, query.length, address, 8766));
            long end = android.os.SystemClock.elapsedRealtime()+1500;
            while (android.os.SystemClock.elapsedRealtime()<end) {
                socket.setSoTimeout((int)Math.max(1,end-android.os.SystemClock.elapsedRealtime()));
                DatagramPacket packet = new DatagramPacket(new byte[1025], 1025);
                socket.receive(packet);
                if (packet.getLength()>1024 || !packet.getAddress().isSiteLocalAddress()) continue;
                try {
                    JSONObject offer = new JSONObject(new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8));
                    if (!"nas-hdmi-offer-v1".equals(offer.optString("type")) || !nonce.equals(offer.optString("nonce"))
                            || !offer.optString("token").matches("[0-9a-f]{32}")
                            || offer.optInt("port")<1 || offer.optInt("port")>65535) continue;
                    return offer.put("host",packet.getAddress().getHostAddress());
                } catch (org.json.JSONException ignored) { }
            }
        }
        throw new java.io.IOException("NAS discovery timed out");
    }
}
