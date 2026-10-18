#!/usr/bin/env python3
"""Explicit LAN probe. Only sends keys named on the command line."""
import argparse
import getpass
import hmac
from pathlib import Path
import secrets
import socket
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'receiver'))
from protocol import NONCE, TOKEN, DeadlineReader, read, write, proof, session_key, pack, unpack
from input_device import KEYS


class Client:
    def __init__(self, host, port, token):
        self.connection = socket.create_connection((host, port), timeout=3)
        self.output = self.connection.makefile('wb', buffering=0)
        self.reader = DeadlineReader(self.connection)
        self.seq = 1
        try:
            hello = read(self.reader)
            server = hello.get('nonce')
            if hello.get('protocol') != 1 or not isinstance(server, str) or not NONCE.fullmatch(server):
                raise ValueError('invalid server hello')
            client = secrets.token_hex(32)
            token = token.encode('ascii')
            write(self.output, {'nonce': client, 'proof': proof(token, 'client', server, client)})
            supplied = read(self.reader).get('proof')
            if not isinstance(supplied, str) or not NONCE.fullmatch(supplied) or not hmac.compare_digest(
                    supplied, proof(token, 'server', server, client)):
                raise ValueError('server authentication failed')
            self.key = session_key(token, server, client)
            status = unpack(self.key, 's', 0, read(self.reader))
            if status.get('status') != 'ready' or status.get('protocol') != 1:
                raise ValueError('receiver busy or incompatible')
        except BaseException:
            self.close()
            raise

    def send(self, body):
        write(self.output, pack(self.key, 'c', self.seq, body))
        result = unpack(self.key, 's', self.seq, read(self.reader))
        if result != {'status': 'ok'}:
            raise ValueError('invalid acknowledgement')
        self.seq += 1

    def close(self):
        self.output.close()
        self.connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--token-file', type=Path)
    parser.add_argument('--key', choices=KEYS, action='append', default=[])
    args = parser.parse_args()
    token = args.token_file.read_text() if args.token_file else getpass.getpass('Pairing code: ')
    token = ''.join(token.split()).replace('-', '').lower()
    if not TOKEN.fullmatch(token):
        parser.error('pairing code must be 32 hexadecimal characters')
    client = Client(args.host, args.port, token)
    try:
        client.send({'type': 'ping'})
        print('Authenticated; receiver ready')
        for key in args.key:
            client.send({'type': 'key', 'key': key, 'action': 'down'})
            time.sleep(0.08)
            client.send({'type': 'key', 'key': key, 'action': 'up'})
            time.sleep(0.15)
        client.send({'type': 'release'})
    finally:
        client.close()


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError) as error:
        raise SystemExit(f'Connection failed: {error}')
