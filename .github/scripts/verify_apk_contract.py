"""Check the built ABI split and optionally execute discovery on a real page size."""
from pathlib import Path
import argparse
import json
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk-dir', type=Path, required=True)
parser.add_argument('--abi', choices=['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64', 'universal'], required=True)
parser.add_argument('--pagesize', type=int, choices=[4096, 16384])
args = parser.parse_args()
metadata_path = args.apk_dir / 'debug/output-metadata.json'
metadata = json.loads(metadata_path.read_text(encoding='utf-8'))
elements = metadata['elements']
expected = {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'}
native_abis = {'arm64-v8a', 'x86_64'}
splits = {e['filters'][0]['value']: e for e in elements if e.get('filters')}
assert set(splits) == expected and len(elements) == 5, 'Unexpected ABI output set'
universal = [e for e in elements if not e.get('filters')]
assert len(universal) == 1, 'Expected one universal APK'
element = universal[0] if args.abi == 'universal' else splits[args.abi]
apk = metadata_path.parent / element['outputFile']
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'Corrupt APK'
    abis = {n.split('/')[1] for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
    wanted = native_abis if args.abi == 'universal' else {args.abi} & native_abis
    assert abis == wanted, f'Actual native payload differs: expected {wanted}, found {abis}'
print(f'Verified {apk.name}: {args.abi}', flush=True)
if args.pagesize is not None:
    assert args.abi != 'universal', 'Use a device ABI for instrumentation'
    def adb(*parts):
        return subprocess.check_output(['adb', *parts], text=True, encoding='utf-8', errors='replace',
                                       stderr=subprocess.STDOUT, timeout=300).strip()

    def guest_memory():
        try:
            fields = re.findall(r'^(MemTotal|MemAvailable|SwapTotal|SwapFree):\s+(\d+) kB\r?$',
                                adb('shell', 'cat', '/proc/meminfo'), re.M)
        except subprocess.CalledProcessError:
            return 'unavailable'
        return ', '.join(f'{name}={int(kib) // 1024} MiB' for name, kib in fields)

    def print_failure_evidence(package):
        # "Process crashed." covers Java and native crashes as well as lmkd kills; keep the guest's account.
        print(f'Guest memory after the failure: {guest_memory()}', flush=True)
        interesting = re.compile(r'lowmemorykiller|lmkd|AndroidRuntime|DEBUG\s*:|crash_dump|Crash of app|'
                                 r'has died|am_proc_died|am_kill|' + re.escape(package))
        try:
            log = adb('logcat', '-d', '-v', 'threadtime', '-b', 'main', '-b', 'system', '-b', 'crash')
        except subprocess.CalledProcessError as error:
            log = error.output or ''
        lines = [line for line in log.splitlines() if interesting.search(line)]
        print(f'--- Logcat evidence ({len(lines)} matching lines, last 300 shown) ---', flush=True)
        print('\n'.join(lines[-300:]), flush=True)

    try:
        page_sizes = {int(adb('shell', 'getconf', 'PAGESIZE'))}
    except subprocess.CalledProcessError:
        # Old API 24 images lack getconf. New 16 KB emulation must use bionic's page size.
        page_sizes = {int(kib) * 1024 for kib in re.findall(r'^KernelPageSize:\s+(\d+) kB$',
                                                         adb('shell', 'cat', '/proc/self/smaps'), re.M)}
    assert page_sizes == {args.pagesize}, f'Expected {args.pagesize} byte pages, found {page_sizes}'
    assert args.abi in adb('shell', 'getprop', 'ro.product.cpu.abilist').split(',')
    test_path = args.apk_dir / 'androidTest/debug/output-metadata.json'
    test = json.loads(test_path.read_text(encoding='utf-8'))
    assert len(test['elements']) == 1
    test_apk = test_path.parent / test['elements'][0]['outputFile']
    print(adb('install', '-r', '-t', str(apk)), flush=True)
    print(adb('install', '-r', '-t', str(test_apk)), flush=True)
    print(f'Guest memory before the tests: {guest_memory()}', flush=True)
    try:
        adb('logcat', '-c')
    except subprocess.CalledProcessError:
        pass  # Evidence then also includes boot and installation lines.
    output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 metadata['applicationId'] + '.PluginDiscoveryAndroidTest,' +
                 metadata['applicationId'] + '.AbiCompatibilityAndroidTest',
                 test['applicationId'] + '/androidx.test.runner.AndroidJUnitRunner')
    print(output, flush=True)
    passed = 'OK (6 tests)' in output and 'FAILURES!!!' not in output
    if not passed:
        print_failure_evidence(metadata['applicationId'])
    assert passed, 'Discovery and ABI compatibility tests failed'
