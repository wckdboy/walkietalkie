import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

SPEC = importlib.util.spec_from_file_location('safe_speech', Path(__file__).parents[1] / 'build-safe-speech.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageTests(unittest.TestCase):
    def test_replaces_only_arm64_and_retains_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            aar, pom, runtime = [root / n for n in ('input.aar', 'input.pom', 'lib.so')]
            entries = {MODULE.RUNTIME: b'old-arm64', 'classes.jar': b'java',
                       'jni/x86_64/libonnxruntime.so': b'old-x86',
                       'jni/arm64-v8a/libmoonshine.so': b'moonshine',
                       'AndroidManifest.xml': b'manifest'}
            with zipfile.ZipFile(aar, 'w') as archive:
                for name, data in entries.items():
                    archive.writestr(name, data)
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.1.5</version>'
                           '<dependencies><dependency><artifactId>work-runtime</artifactId></dependency></dependencies></project>')
            runtime.write_bytes(b'new-arm64')
            result = MODULE.package(aar, pom, runtime, root / 'out')
            with zipfile.ZipFile(result) as archive:
                self.assertEqual(set(archive.namelist()), set(entries))
                for name, data in entries.items():
                    self.assertEqual(archive.read(name), b'new-arm64' if name == MODULE.RUNTIME else data)
            xml = ET.parse(root / 'out' / f'moonshine-voice-{MODULE.VERSION}.pom')
            ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
            self.assertEqual(xml.find('m:version', ns).text, MODULE.VERSION)
            self.assertEqual(xml.find('m:dependencies/m:dependency/m:artifactId', ns).text, 'work-runtime')

    def test_invalid_pom_preserves_existing_module(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / 'out'
            output.mkdir()
            artifact = output / f'moonshine-voice-{MODULE.VERSION}.aar'
            artifact.write_bytes(b'previous-valid-artifact')
            (root / 'bad.pom').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>unexpected</version></project>')
            with self.assertRaisesRegex(RuntimeError, 'Unexpected upstream POM'):
                MODULE.package(root / 'not-needed.aar', root / 'bad.pom', root / 'not-needed.so', output)
            self.assertEqual(artifact.read_bytes(), b'previous-valid-artifact')

    def test_missing_runtime_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(root / 'empty.aar', 'w'):
                pass
            (root / 'pom').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.1.5</version></project>')
            (root / 'runtime').write_bytes(b'new')
            with self.assertRaisesRegex(RuntimeError, 'exactly one'):
                MODULE.package(root / 'empty.aar', root / 'pom', root / 'runtime', root / 'out')


if __name__ == '__main__':
    unittest.main()
