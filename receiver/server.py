#!/usr/bin/env python3
"""A bounded, authenticated LAN keyboard bridge, standard library only."""
import argparse
import hmac
import os
from pathlib import Path
import secrets
import signal
import socket
import socketserver
import threading
import time

from input_device import UInput, DryInput, Keyboard, KEYS, OPTIONS
from native_http import NativeHTTP
from discovery import Discovery
from protocol import NONCE, TOKEN, read, write, proof, session_key, pack, unpack, DeadlineReader


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = False
    block_on_close = True

    def __init__(self, address, token, keyboard):
        super().__init__(address, Handler)
        self.token = token.encode('ascii')
        self.keyboard = keyboard
        self.owner = threading.Lock()
        self.slots = threading.BoundedSemaphore(8)
        self.input_lock = threading.RLock()
        self.connections_lock = threading.Lock()
        self.connections = set()
        self.stopping = threading.Event()
        self.watchdog = threading.Thread(target=self.watch, daemon=True)
        self.watchdog.start()

    def watch(self):
        while not self.stopping.wait(0.1):
            try:
                with self.input_lock:
                    self.keyboard.expire()
            except OSError:
                self.stop_connections()
                return

    def stop_connections(self):
        with self.connections_lock:
            self.stopping.set()
            for connection in self.connections:
                try:
                    connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass

    def server_close(self):
        self.stop_connections()
        super().server_close()
        self.watchdog.join()


    def process_request(self, request, client_address):
        if self.stopping.is_set() or not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        with self.connections_lock:
            self.connections.add(request)
        try:
            super().process_request(request, client_address)
        except BaseException:
            with self.connections_lock:
                self.connections.discard(request)
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            with self.connections_lock:
                self.connections.discard(request)
            self.slots.release()


class Handler(socketserver.StreamRequestHandler):
    timeout = 3

    def handle(self):
        owned = False
        reader = DeadlineReader(self.connection)
        try:
            server_nonce = secrets.token_hex(32)
            write(self.wfile, {'protocol': 1, 'nonce': server_nonce})
            hello = read(reader)
            client_nonce = hello.get('nonce', '')
            supplied = hello.get('proof', '')
            if not isinstance(client_nonce, str) or not NONCE.fullmatch(client_nonce):
                raise ValueError('bad nonce')
            if not isinstance(supplied, str) or not hmac.compare_digest(
                    proof(self.server.token, 'client', server_nonce, client_nonce), supplied):
                raise ValueError('bad pairing code')
            key = session_key(self.server.token, server_nonce, client_nonce)
            write(self.wfile, {'proof': proof(self.server.token, 'server', server_nonce, client_nonce)})
            owned = self.server.owner.acquire(blocking=False)
            write(self.wfile, pack(key, 's', 0, {'status': 'ready' if owned else 'busy',
                                                'protocol': 1}))
            if not owned:
                return
            seq = 1
            window, count = time.monotonic(), 0
            print('Authenticated remote connected', flush=True)
            while True:
                body = unpack(key, 'c', seq, read(reader))
                now = time.monotonic()
                if now - window >= 1:
                    window, count = now, 0
                count += 1
                if count > 100:
                    raise ValueError('rate exceeded')
                with self.server.input_lock:
                    self.server.keyboard.expire()
                    kind = body.get('type')
                    if kind == 'key':
                        self.server.keyboard.event(body.get('key'), body.get('action'))
                    elif kind == 'release':
                        self.server.keyboard.release()
                    elif kind != 'ping':
                        raise ValueError('unknown message')
                write(self.wfile, pack(key, 's', seq, {'status': 'ok'}))
                seq += 1
        except (ValueError, OSError, TypeError, KeyError):
            # Do not log secrets, wire frames, or keystroke history.
            pass
        finally:
            if owned:
                try:
                    with self.server.input_lock:
                        self.server.keyboard.release()
                finally:
                    self.server.owner.release()
                print('Remote disconnected; keys released', flush=True)


def load_token(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        pass
    else:
        with os.fdopen(fd, 'w') as output:
            output.write(secrets.token_hex(16) + '\n')
    token = path.read_text().strip()
    if not TOKEN.fullmatch(token):
        raise ValueError('pairing token must contain 32 lowercase hexadecimal characters')
    return token


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--backend', choices=('uinput', 'native-http'), default='uinput')
    parser.add_argument('--native-socket', default='/run/ugreen/http/display_desktop.http.sock')
    parser.add_argument('--auto-pair', action='store_true', help='Allow trusted LAN clients to discover and pair via UDP 8766')
    parser.add_argument('--host', default='0.0.0.0')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--token-file', default='/data/pairing.key')
    parser.add_argument('--show-code', action='store_true')
    args = parser.parse_args()
    token = load_token(args.token_file)
    if args.show_code:
        print('-'.join(token[i:i + 4] for i in range(0, 32, 4)))
        return
    mapping = dict(KEYS)
    for name in ('OK', 'BACK', 'MENU'):
        value = os.environ.get(name + '_KEY')
        if value and args.backend == 'uinput':
            if value not in OPTIONS:
                parser.error(f'{name}_KEY must be one of {list(OPTIONS)}')
            mapping[name] = OPTIONS[value]
    try:
        device = (DryInput() if args.dry_run else
                  NativeHTTP(args.native_socket) if args.backend == 'native-http' else UInput())
    except OSError as error:
        raise SystemExit(f'Cannot open /dev/uinput: {error}. Load uinput on NAS host; see README.')
    keyboard = Keyboard(device, mapping)
    with Server((args.host, args.port), token, keyboard) as server:
        discovery = Discovery(token, args.port) if args.auto_pair else None
        def stop(*_):
            threading.Thread(target=server.shutdown, daemon=True).start()
        signal.signal(signal.SIGTERM, stop)
        signal.signal(signal.SIGINT, stop)
        print(f'NAS HDMI Remote v0.1.0 listening on {args.host}:{args.port}; '
              f'backend={"dry-run" if args.dry_run else args.backend}', flush=True)
        try:
            server.serve_forever(poll_interval=0.2)
        finally:
            if discovery is not None:
                discovery.close()
            server.server_close()
            try:
                keyboard.release()
            finally:
                device.close()


if __name__ == '__main__':
    main()
