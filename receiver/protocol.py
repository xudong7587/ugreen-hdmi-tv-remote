"""v1 framed HMAC protocol. Integrity/authentication, NOT encryption."""
import base64
import hashlib
import hmac
import json
import re

LIMIT = 4096
NONCE = re.compile(r"^[0-9a-f]{64}$")
TOKEN = re.compile(r"^[0-9a-f]{32}$")


def digest(key, message):
    return hmac.new(key, message.encode('utf-8'), hashlib.sha256).hexdigest()


def proof(key, role, server, client):
    return digest(key, f'{role}|{server}|{client}')


def session_key(key, server, client):
    return bytes.fromhex(proof(key, 'session', server, client))


def pack(key, direction, seq, body):
    payload = base64.b64encode(json.dumps(body, separators=(',', ':')).encode()).decode()
    return {'seq': seq, 'payload': payload, 'mac': digest(key, f'{direction}|{seq}|{payload}')}


def unpack(key, direction, seq, frame):
    if not isinstance(frame, dict):
        raise ValueError('invalid frame')
    if type(frame.get('seq')) is not int or frame['seq'] != seq:
        raise ValueError('sequence mismatch')
    payload = frame.get('payload')
    mac = frame.get('mac')
    if not isinstance(payload, str) or not isinstance(mac, str) or not NONCE.fullmatch(mac):
        raise ValueError('invalid frame')
    if not hmac.compare_digest(digest(key, f'{direction}|{seq}|{payload}'), mac):
        raise ValueError('authentication failed')
    body = json.loads(base64.b64decode(payload, validate=True))
    if not isinstance(body, dict):
        raise ValueError('invalid body')
    return body


def read(stream):
    line = stream.readline(LIMIT + 1)
    if not line or len(line) > LIMIT or not line.endswith(b'\n'):
        raise ValueError('closed or oversized frame')
    value = json.loads(line)
    if not isinstance(value, dict):
        raise ValueError('expected object')
    return value


def write(stream, frame):
    stream.write(json.dumps(frame, separators=(',', ':')).encode() + b'\n')
    stream.flush()


class DeadlineReader:
    """Buffered socket reader with a total deadline per frame, including slow drips."""
    def __init__(self, connection, timeout=3):
        self.connection = connection
        self.timeout = timeout
        self.buffer = bytearray()

    def readline(self, limit):
        import time
        deadline = time.monotonic() + self.timeout
        while True:
            newline = self.buffer.find(b"\n")
            if newline >= 0:
                size = min(newline + 1, limit)
                result = bytes(self.buffer[:size])
                del self.buffer[:size]
                return result
            if len(self.buffer) >= limit:
                raise ValueError('oversized frame')
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('frame deadline exceeded')
            self.connection.settimeout(remaining)
            chunk = self.connection.recv(limit - len(self.buffer))
            if not chunk:
                raise ValueError('connection closed')
            self.buffer.extend(chunk)
