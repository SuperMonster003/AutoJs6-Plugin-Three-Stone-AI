"""Check the built ABI split and optionally execute discovery on a real page size."""
from pathlib import Path
import argparse
import json
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk-dir', type=Path, required=True)
parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], required=True)
parser.add_argument('--pagesize', type=int, choices=[4096, 16384])
args = parser.parse_args()
metadata_path = args.apk_dir / 'debug/output-metadata.json'
metadata = json.loads(metadata_path.read_text(encoding='utf-8'))
elements = metadata['elements']
expected = {'arm64-v8a', 'x86_64'}
splits = {e['filters'][0]['value']: e for e in elements if e.get('filters')}
assert set(splits) == expected and len(elements) == 3, 'Unexpected ABI output set'
element = splits[args.abi]
apk = metadata_path.parent / element['outputFile']
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'Corrupt APK'
    abis = {n.split('/')[1] for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')}
    assert abis == {args.abi}, f'Actual native payload differs: {abis}'
print(f'Verified {apk.name}: {args.abi}', flush=True)
if args.pagesize is not None:
    def adb(*parts):
        return subprocess.check_output(['adb', *parts], text=True, encoding='utf-8', errors='replace', timeout=300).strip()
    actual = int(adb('shell', 'getconf', 'PAGESIZE'))
    assert actual == args.pagesize, f'Expected {args.pagesize} byte pages, found {actual}'
    assert args.abi in adb('shell', 'getprop', 'ro.product.cpu.abilist').split(',')
    test_path = args.apk_dir / 'androidTest/debug/output-metadata.json'
    test = json.loads(test_path.read_text(encoding='utf-8'))
    assert len(test['elements']) == 1
    test_apk = test_path.parent / test['elements'][0]['outputFile']
    print(adb('install', '-r', '-t', str(apk)), flush=True)
    print(adb('install', '-r', '-t', str(test_apk)), flush=True)
    output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                 metadata['applicationId'] + '.PluginDiscoveryAndroidTest',
                 test['applicationId'] + '/androidx.test.runner.AndroidJUnitRunner')
    print(output, flush=True)
    assert 'OK (2 tests)' in output and 'FAILURES!!!' not in output, 'Instrumentation did not pass both discovery tests'
