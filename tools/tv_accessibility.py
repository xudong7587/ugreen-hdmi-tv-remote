"""Enable/disable only this project's accessibility service, preserving others."""
import argparse
import shlex
import subprocess

COMPONENT = 'fun.dunn.nasremote.diagnostic/fun.dunn.nasremote.RemoteService'
PACKAGE = 'fun.dunn.nasremote.diagnostic'


def update_services(raw, disable=False):
    values = [] if raw.strip() in ('', 'null') else raw.strip().split(':')
    def is_own(value):
        return value == COMPONENT
    values = [v for v in values if v and not is_own(v)]
    if not disable:
        values.append(COMPONENT)
    return ':'.join(values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--disable', action='store_true')
    args = parser.parse_args()
    base = [args.adb, '-s', args.serial]
    def shell(*parts):
        return subprocess.check_output(base + ['shell', shlex.join(parts)], text=True).strip()
    if not args.disable and not shell('pm', 'path', PACKAGE).startswith('package:'):
        raise SystemExit('Install the TV APK first.')
    current = shell('settings', 'get', 'secure', 'enabled_accessibility_services')
    updated = update_services(current, args.disable)
    shell('settings', 'put', 'secure', 'enabled_accessibility_services', updated)
    if not args.disable:
        shell('settings', 'put', 'secure', 'accessibility_enabled', '1')
    actual = shell('settings', 'get', 'secure', 'enabled_accessibility_services')
    if actual != updated:
        raise SystemExit('Firmware did not preserve the requested setting; inspect TV permissions.')
    print('Service entry updated. Check Bound services using adb shell dumpsys accessibility.')
    print('Also allow app auto-start and associated startup in TV permission management.')


if __name__ == '__main__':
    main()
