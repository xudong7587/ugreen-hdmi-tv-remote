"""Opt-in trusted-LAN discovery and automatic pairing (UDP 8766)."""
import ipaddress
import json
import re
import socket
import threading


class Discovery:
    def __init__(self, token, port):
        self.token, self.port = token, port
        self.stopping = threading.Event()
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.socket.bind(('0.0.0.0', 8766))
        self.socket.settimeout(0.5)
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self):
        while not self.stopping.is_set():
            try:
                raw, peer = self.socket.recvfrom(1025)
                if len(raw) > 1024 or not ipaddress.ip_address(peer[0]).is_private:
                    continue
                query = json.loads(raw)
                if not isinstance(query, dict) or query.get('type') != 'nas-hdmi-discover-v1':
                    continue
                nonce = query.get('nonce')
                if not isinstance(nonce, str) or not re.fullmatch('[0-9a-f]{32}', nonce):
                    continue
                # This mode intentionally pairs any LAN client, without typing a code.
                reply = {'type': 'nas-hdmi-offer-v1', 'nonce': nonce,
                         'port': self.port, 'token': self.token}
                self.socket.sendto(json.dumps(reply).encode(), peer)
            except (ValueError, OSError):
                continue

    def close(self):
        self.stopping.set()
        self.thread.join(timeout=1)
        self.socket.close()
