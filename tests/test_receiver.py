import base64
import io
import json
from pathlib import Path
import socket
import sys
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'receiver'), str(ROOT / 'tools')]
from input_device import Keyboard, KEYS
from protocol import pack, unpack, read, write, digest, DeadlineReader
from server import Server
from client import Client


class Device:
    def __init__(self):
        self.events = []

    def emit(self, code, value):
        self.events.append((code, value))


class KeyboardTests(unittest.TestCase):
    def setUp(self):
        self.device = Device()
        self.now = 0
        self.keyboard = Keyboard(self.device, clock=lambda: self.now)

    def test_seven_keys_duplicate_down_repeat_up(self):
        for name, code in KEYS.items():
            for action in ('down', 'down', 'repeat', 'up', 'up', 'repeat'):
                self.keyboard.event(name, action)
            self.assertEqual(self.device.events[-3:], [(code, 1), (code, 2), (code, 0)])

    def test_expire_and_release(self):
        self.keyboard.event('UP', 'down')
        self.now = 2.6
        self.keyboard.expire()
        self.assertEqual(self.device.events[-1], (103, 0))
        self.keyboard.event('OK', 'down')
        self.keyboard.release()
        self.assertFalse(self.keyboard.pressed)

    def test_alias(self):
        self.keyboard.mapping['BACK'] = 28
        self.keyboard.event('OK', 'down')
        self.keyboard.event('BACK', 'down')
        self.keyboard.event('OK', 'up')
        self.keyboard.release()
        self.assertEqual(self.device.events, [(28, 1), (28, 0), (28, 1), (28, 0)])

    def test_invalid(self):
        for key, action in [('HOME', 'down'), ([], 'down'), ('UP', [])]:
            with self.assertRaises(ValueError):
                self.keyboard.event(key, action)


class ProtocolTests(unittest.TestCase):
    def test_roundtrip_and_tampering(self):
        frame = pack(b'key', 'c', 1, {'type': 'ping'})
        self.assertEqual(unpack(b'key', 'c', 1, frame), {'type': 'ping'})
        for key, direction, sequence in [(b'other', 'c', 1), (b'key', 's', 1), (b'key', 'c', 2)]:
            with self.assertRaises(ValueError):
                unpack(key, direction, sequence, frame)
        for field, value in [('seq', True), ('payload', 'bad'), ('mac', '非ASCII'), ('payload', [])]:
            with self.assertRaises(ValueError):
                unpack(b'key', 'c', 1, dict(frame, **{field: value}))

    def test_bad_body(self):
        for payload in ['!invalid!', base64.b64encode(b'[]').decode()]:
            frame = {'seq': 1, 'payload': payload, 'mac': digest(b'k', f'c|1|{payload}')}
            with self.assertRaises(ValueError):
                unpack(b'k', 'c', 1, frame)

    def test_read_boundaries(self):
        for raw in [b'', b'{}', b'[]\n', b'x' * 4096 + b'\n']:
            with self.assertRaises(ValueError):
                read(io.BytesIO(raw))
        self.assertEqual(read(io.BytesIO(b'{}\n')), {})

    def test_deadline_and_buffered_frames(self):
        left, right = socket.socketpair()
        try:
            reader = DeadlineReader(left, timeout=0.15)
            right.sendall(b'{}\n{}\n')
            self.assertEqual(read(reader), {})
            self.assertEqual(read(reader), {})
            right.sendall(b'{')
            start = time.monotonic()
            with self.assertRaises(TimeoutError):
                read(reader)
            self.assertLess(time.monotonic() - start, 0.5)
        finally:
            left.close()
            right.close()


class IntegrationTests(unittest.TestCase):
    def setUp(self):
        self.device = Device()
        self.keyboard = Keyboard(self.device)
        self.token = 'a' * 32
        self.server = Server(('127.0.0.1', 0), self.token, self.keyboard)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={'poll_interval': 0.02})
        self.thread.start()
        self.clients = []

    def tearDown(self):
        for client in self.clients:
            client.close()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def connect(self, token=None):
        client = Client(*self.server.server_address, token or self.token)
        self.clients.append(client)
        return client

    def wait_released(self):
        deadline = time.monotonic() + 1
        while self.keyboard.pressed and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertFalse(self.keyboard.pressed)

    def test_keys_busy_and_disconnect(self):
        client = self.connect()
        with self.assertRaisesRegex(ValueError, 'busy'):
            self.connect()
        for name in KEYS:
            client.send({'type': 'key', 'key': name, 'action': 'down'})
            client.send({'type': 'key', 'key': name, 'action': 'up'})
        self.assertEqual(len(self.device.events), 14)
        client.send({'type': 'key', 'key': 'UP', 'action': 'down'})
        client.close()
        self.wait_released()

    def test_bad_auth_does_not_claim_device(self):
        with self.assertRaises(ValueError):
            self.connect('b' * 32)
        self.connect().send({'type': 'ping'})

    def test_ping_cannot_keep_lost_key_up_pressed(self):
        client = self.connect()
        client.send({'type': 'key', 'key': 'UP', 'action': 'down'})
        for _ in range(7):
            time.sleep(0.4)
            client.send({'type': 'ping'})
        self.assertFalse(self.keyboard.pressed)

    def test_idle_watchdog(self):
        client = self.connect()
        client.send({'type': 'key', 'key': 'OK', 'action': 'down'})
        time.sleep(2.75)
        self.assertFalse(self.keyboard.pressed)

    def test_invalid_message_releases(self):
        client = self.connect()
        client.send({'type': 'key', 'key': 'UP', 'action': 'down'})
        with self.assertRaises(ValueError):
            client.send({'type': 'key', 'key': [], 'action': 'down'})
        self.wait_released()

    def test_flood_releases(self):
        client = self.connect()
        client.send({'type': 'key', 'key': 'UP', 'action': 'down'})
        with self.assertRaises(ValueError):
            for _ in range(110):
                client.send({'type': 'ping'})
        self.wait_released()

    def test_shutdown_closes_active_connection(self):
        client = self.connect()
        client.send({'type': 'key', 'key': 'UP', 'action': 'down'})
        self.server.shutdown()
        self.server.server_close()
        self.assertFalse(self.keyboard.pressed)


if __name__ == '__main__':
    unittest.main()
