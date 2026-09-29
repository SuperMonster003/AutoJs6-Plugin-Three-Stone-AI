"""Exercise the CI verifier against APK ZIPs, including Java-only 32-bit outputs."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / '.github/scripts/verify_apk_contract.py'


class ApkContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.debug = self.root / 'debug'
        self.debug.mkdir()
        self.elements = []
        for abi in ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64', 'universal']:
            self.elements.append({'filters': [] if abi == 'universal' else [{'filterType': 'ABI', 'value': abi}],
                                  'outputFile': f'{abi}.apk'})
            payload = ['arm64-v8a', 'x86_64'] if abi == 'universal' else [abi] if '64' in abi else []
            self.apk(abi, payload)
        self.metadata()

    def apk(self, abi, native_abis):
        with zipfile.ZipFile(self.debug / f'{abi}.apk', 'w') as archive:
            archive.writestr('classes.dex', b'fixture')
            for native_abi in native_abis:
                archive.writestr(f'lib/{native_abi}/liblitertlm_jni.so', b'fixture')

    def metadata(self):
        (self.debug / 'output-metadata.json').write_text(json.dumps({'elements': self.elements}), encoding='utf-8')

    def verify(self, abi):
        return subprocess.run([sys.executable, str(SCRIPT), '--apk-dir', str(self.root), '--abi', abi],
                              capture_output=True, text=True)

    def test_every_output_accepts_its_native_contract(self):
        for abi in ['armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64', 'universal']:
            with self.subTest(abi=abi):
                result = self.verify(abi)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_32_bit_apk_must_not_accidentally_include_64_bit_runtime(self):
        self.apk('x86', ['x86_64'])
        self.assertNotEqual(0, self.verify('x86').returncode)

    def test_64_bit_apk_must_not_lose_native_runtime(self):
        self.apk('arm64-v8a', [])
        self.assertNotEqual(0, self.verify('arm64-v8a').returncode)

    def test_universal_must_include_both_native_runtimes(self):
        self.apk('universal', ['x86_64'])
        self.assertNotEqual(0, self.verify('universal').returncode)

    def test_missing_32_bit_output_is_rejected(self):
        self.elements = [e for e in self.elements if e['outputFile'] != 'armeabi-v7a.apk']
        self.metadata()
        self.assertNotEqual(0, self.verify('x86_64').returncode)


if __name__ == '__main__':
    unittest.main()
