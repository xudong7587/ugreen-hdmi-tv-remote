"""Linux uinput ABI, supporting little/big endian native Linux architectures."""
import os
import struct
import time

KEYS = {'UP': 103, 'DOWN': 108, 'LEFT': 105, 'RIGHT': 106,
        'OK': 28, 'BACK': 1, 'MENU': 139}
OPTIONS = {'ESC': 1, 'BACK': 158, 'ENTER': 28, 'MENU': 139,
           'CONTEXT_MENU': 127, 'HOMEPAGE': 172, 'SPACE': 57}


class UInput:
    def __init__(self, path='/dev/uinput'):
        import fcntl
        self.ioctl = fcntl.ioctl
        self.fd = os.open(path, os.O_WRONLY | os.O_NONBLOCK)
        try:
            fcntl.ioctl(self.fd, 0x40045564, 1)  # UI_SET_EVBIT: EV_KEY
            # A complete keyboard capability range helps udev/libinput classify it.
            for code in range(1, 249):
                fcntl.ioctl(self.fd, 0x40045565, code)  # UI_SET_KEYBIT
            descriptor = struct.pack('=80sHHHHI', b'NAS HDMI Remote', 3, 0x1209, 0x0001, 1, 0)
            descriptor += bytes(4 * 64 * 4)  # absmax/min/fuzz/flat
            os.write(self.fd, descriptor)
            fcntl.ioctl(self.fd, 0x5501)  # UI_DEV_CREATE
        except BaseException:
            os.close(self.fd)
            raise

    def emit(self, code, value):
        # timeval is native long; input_event uses native ABI alignment.
        os.write(self.fd, struct.pack('@llHHi', 0, 0, 1, code, value))
        os.write(self.fd, struct.pack('@llHHi', 0, 0, 0, 0, 0))

    def close(self):
        try:
            self.ioctl(self.fd, 0x5502)
        finally:
            os.close(self.fd)


class DryInput:
    def emit(self, code, value):
        print(f'DRY-RUN key={code} value={value}', flush=True)

    def close(self):
        pass


class Keyboard:
    def __init__(self, device, mapping=None, clock=time.monotonic):
        self.device, self.mapping, self.clock = device, dict(KEYS if mapping is None else mapping), clock
        self.pressed = {}

    def event(self, name, action):
        if not isinstance(name, str) or name not in self.mapping or action not in ('down', 'repeat', 'up'):
            raise ValueError('unsupported key/action')
        code = self.mapping[name]
        if action == 'down':
            if name not in self.pressed:
                # Do not permit aliases to leave the same physical key stuck.
                for old in list(self.pressed):
                    if self.mapping[old] == code:
                        self.event(old, 'up')
                self.device.emit(code, 1)
            self.pressed[name] = self.clock()
        elif action == 'repeat':
            if name in self.pressed:
                self.device.emit(code, 2)
                self.pressed[name] = self.clock()
        elif name in self.pressed:
            self.device.emit(code, 0)
            del self.pressed[name]

    def expire(self):
        for name, timestamp in list(self.pressed.items()):
            if self.clock() - timestamp > 2.5:
                self.event(name, 'up')

    def release(self):
        for name in list(self.pressed):
            self.event(name, 'up')
