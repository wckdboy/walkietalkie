import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('verify_speech', Path(__file__).parents[1] / 'verify-safe-speech.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class VerificationTests(unittest.TestCase):
    def test_correct_runtime_and_rejects_wrong_or_missing_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            info = root / 'info.json'
            info.write_text(json.dumps({'kleidiai': False, 'runtime_sha256': hashlib.sha256(b'safe').hexdigest()}))
            for name, data in [('correct', b'safe'), ('wrong', b'old'), ('missing', None)]:
                apk = root / (name + '.apk')
                with zipfile.ZipFile(apk, 'w') as archive:
                    if data is not None:
                        archive.writestr('lib/arm64-v8a/libonnxruntime.so', data)
                if name == 'correct':
                    self.assertEqual(MODULE.verify(apk, info), hashlib.sha256(b'safe').hexdigest())
                else:
                    with self.assertRaises(ValueError):
                        MODULE.verify(apk, info)


if __name__ == '__main__':
    unittest.main()
