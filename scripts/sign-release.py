#!/usr/bin/env python3
"""Sign a release APK using the project's stable Actions secrets; never generate a key."""
import argparse
import base64
import os
import re
from pathlib import Path
import subprocess
import tempfile


def sign(source, destination):
    expected = (Path(__file__).resolve().parents[1] / 'release-signing-certificate.sha256').read_text().strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise RuntimeError('Approved public signing certificate fingerprint is missing or invalid')
    encoded = os.environ.get('ANDROID_SIGNING_KEYSTORE_BASE64', '')
    password = os.environ.get('ANDROID_SIGNING_PASSWORD', '')
    if not encoded or not password:
        raise RuntimeError('Stable signing secrets are required; refusing to publish a demo-signed APK')
    try:
        keystore = base64.b64decode(encoded, validate=True)
    except ValueError:
        raise RuntimeError('Signing keystore secret is not valid base64') from None
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise RuntimeError('Android SDK path is missing')
    signer = Path(sdk) / 'build-tools/36.0.0/apksigner'
    if not signer.is_file():
        raise RuntimeError('Android Build Tools 36.0.0 are required')
    env = os.environ.copy()
    # Only apksigner needs the password; do not pass the private key secret on.
    env.pop('ANDROID_SIGNING_KEYSTORE_BASE64', None)
    with tempfile.TemporaryDirectory(prefix='walkietalkie-sign-', dir=os.environ.get('RUNNER_TEMP')) as directory:
        key = Path(directory) / 'release.p12'
        fd = os.open(key, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(keystore)
        subprocess.run([str(signer), 'sign', '--ks', str(key), '--ks-key-alias', 'walkietalkie',
                        '--ks-pass', 'env:ANDROID_SIGNING_PASSWORD', '--key-pass', 'env:ANDROID_SIGNING_PASSWORD',
                        '--out', str(destination), str(source)], env=env, check=True)
        verify_env = env.copy()
        verify_env.pop('ANDROID_SIGNING_PASSWORD', None)
        verified = subprocess.run([str(signer), 'verify', '--verbose', '--print-certs', str(destination)],
                                  env=verify_env, check=True, capture_output=True, text=True)
        match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})', verified.stdout)
        if not match or match.group(1).lower() != expected:
            raise RuntimeError('Release signer does not match the approved public certificate')
        print(verified.stdout, end='')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('unsigned_apk', type=Path)
    parser.add_argument('signed_apk', type=Path)
    args = parser.parse_args()
    sign(args.unsigned_apk, args.signed_apk)
