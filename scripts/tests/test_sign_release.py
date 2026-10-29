import base64
import importlib.util
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('sign_release', Path(__file__).parents[1] / 'sign-release.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SigningTests(unittest.TestCase):
    def test_missing_secrets_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(RuntimeError, 'Stable signing secrets'):
            MODULE.sign(Path('input.apk'), Path('output.apk'))

    def test_invalid_base64_fails_before_signing(self):
        env = {'ANDROID_SIGNING_KEYSTORE_BASE64': 'not base64!', 'ANDROID_SIGNING_PASSWORD': 'test-only'}
        with patch.dict(os.environ, env, clear=True), self.assertRaisesRegex(RuntimeError, 'valid base64'):
            MODULE.sign(Path('input.apk'), Path('output.apk'))

    def test_key_is_private_cleaned_and_password_not_in_arguments(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            signer = root / 'build-tools/36.0.0/apksigner'
            signer.parent.mkdir(parents=True)
            signer.touch()
            env = {'ANDROID_HOME': str(root), 'RUNNER_TEMP': str(root),
                   'ANDROID_SIGNING_KEYSTORE_BASE64': base64.b64encode(b'fake-test-data').decode(),
                   'ANDROID_SIGNING_PASSWORD': 'test-only-password'}
            keys = []
            def inspect(args, **kwargs):
                self.assertNotIn('test-only-password', args)
                self.assertNotIn('ANDROID_SIGNING_KEYSTORE_BASE64', kwargs['env'])
                if args[1] == 'verify':
                    self.assertNotIn('ANDROID_SIGNING_PASSWORD', kwargs['env'])
                if args[1] == 'sign':
                    key = Path(args[args.index('--ks') + 1]); keys.append(key)
                    self.assertEqual(key.stat().st_mode & 0o777, 0o600)
                    self.assertEqual(key.read_bytes(), b'fake-test-data')
                digest = (Path(__file__).parents[2] / 'release-signing-certificate.sha256').read_text().strip()
                return subprocess.CompletedProcess(args, 0, stdout='Signer #1 certificate SHA-256 digest: ' + digest)
            with patch.dict(os.environ, env, clear=True), patch.object(MODULE.subprocess, 'run', side_effect=inspect) as run:
                MODULE.sign(Path('input.apk'), Path('output.apk'))
                self.assertEqual(run.call_count, 2)
            self.assertTrue(keys)
            self.assertFalse(keys[0].exists())

    def test_signer_failure_cleans_private_key(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            signer = root / 'build-tools/36.0.0/apksigner'
            signer.parent.mkdir(parents=True); signer.touch()
            env = {'ANDROID_HOME': str(root), 'RUNNER_TEMP': str(root),
                   'ANDROID_SIGNING_KEYSTORE_BASE64': base64.b64encode(b'fake-test-data').decode(),
                   'ANDROID_SIGNING_PASSWORD': 'test-only-password'}
            keys = []
            def fail(args, **kwargs):
                keys.append(Path(args[args.index('--ks') + 1]))
                raise subprocess.CalledProcessError(1, args)
            with patch.dict(os.environ, env, clear=True), patch.object(MODULE.subprocess, 'run', side_effect=fail):
                with self.assertRaises(subprocess.CalledProcessError):
                    MODULE.sign(Path('input.apk'), Path('output.apk'))
            self.assertFalse(keys[0].exists())
