import io
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'receiver'))
from input_device import Keyboard, KEYS
from native_http import NativeHTTP


class Connection:
    def __init__(self, response):
        self.response = response
        self.requests = []
        self.closed = False

    def request(self, *args):
        self.requests.append(args)

    def getresponse(self):
        stream = io.BytesIO(self.response)
        stream.status = 200
        return stream

    def close(self):
        self.closed = True


class NativeTests(unittest.TestCase):
    def test_single_tap_repeat_release_and_whitelist(self):
        connection = Connection(b'{"data":{"accepted":true,"executed":true}}')
        device = NativeHTTP('/test', lambda _: connection)
        keyboard = Keyboard(device)
        for action in ('down', 'down', 'repeat', 'up', 'up'):
            keyboard.event('DOWN', action)
        self.assertEqual(len(connection.requests), 2)
        self.assertEqual(json.loads(connection.requests[0][2]), {'command': 'down'})
        self.assertTrue(connection.closed)
        with self.assertRaises(ValueError):
            device.emit(999, 1)

    def test_rejected_malformed_and_oversized_responses_fail_closed(self):
        for raw in (b'{}', b'[]', b'{"data":null}', b'bad json', b'x'*4097,
                    b'{"data":{"accepted":true,"executed":false}}'):
            with self.subTest(raw=raw[:40]):
                connection = Connection(raw)
                device = NativeHTTP('/test', lambda _: connection)
                with self.assertRaises(OSError):
                    device.emit(KEYS['DOWN'], 1)
                self.assertTrue(connection.closed)


if __name__ == '__main__':
    unittest.main()
