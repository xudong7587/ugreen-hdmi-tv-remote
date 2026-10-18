"""Restricted adapter for the firmware's local HDMI command socket."""
import http.client
import json
import socket

from input_device import KEYS


class UnixHTTP(http.client.HTTPConnection):
    def __init__(self, path):
        super().__init__('localhost', timeout=1)
        self.path = path

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        try:
            self.sock.connect(self.path)
        except BaseException:
            self.sock.close()
            raise


class NativeHTTP:
    commands = {KEYS[name]: command for name, command in {
        'UP': 'up', 'DOWN': 'down', 'LEFT': 'left', 'RIGHT': 'right',
        'OK': 'confirm', 'BACK': 'back', 'MENU': 'menu',
    }.items()}

    def __init__(self, path, connection_factory=UnixHTTP):
        self.path = path
        self.connection_factory = connection_factory

    def emit(self, code, value):
        if code not in self.commands or value not in (0, 1, 2):
            raise ValueError('unsupported native key/action')
        # Firmware commands are atomic taps. Releasing a key needs no request.
        if value == 0:
            return
        connection = self.connection_factory(self.path)
        try:
            connection.request('POST', '/ugreen/v1/hdmi/desktop/command',
                               json.dumps({'command': self.commands[code]}),
                               {'Content-Type': 'application/json'})
            response = connection.getresponse()
            raw = response.read(4097)
            if response.status != 200 or len(raw) > 4096:
                raise OSError('native command HTTP failure')
            result = json.loads(raw)
            data = result.get('data', {}) if isinstance(result, dict) else {}
            if not isinstance(data, dict) or data.get('accepted') is not True or data.get('executed') is not True:
                raise OSError('native command not executed')
        except (ValueError, http.client.HTTPException) as error:
            raise OSError('invalid native command response') from error
        finally:
            connection.close()

    def close(self):
        pass
