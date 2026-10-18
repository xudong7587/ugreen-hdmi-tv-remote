import json
from pathlib import Path
import socket
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'receiver'))
from discovery import Discovery


class DiscoveryTests(unittest.TestCase):
    def test_offer_echoes_nonce_and_rejects_invalid_queries(self):
        server = Discovery('a'*32, 8765)
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
                client.settimeout(0.2)
                for raw in (b'[]', b'{}', b'x'*1025, b'{"type":"nas-hdmi-discover-v1","nonce":"bad"}'):
                    client.sendto(raw, ('127.0.0.1',8766))
                    with self.assertRaises(socket.timeout): client.recvfrom(2048)
                client.sendto(json.dumps({'type':'nas-hdmi-discover-v1','nonce':'b'*32}).encode(), ('127.0.0.1',8766))
                response = json.loads(client.recvfrom(2048)[0])
                self.assertEqual(response, {'type':'nas-hdmi-offer-v1','nonce':'b'*32,'port':8765,'token':'a'*32})
        finally:
            server.close()
