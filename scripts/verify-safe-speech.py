#!/usr/bin/env python3
"""Prove the arm64 APK contains the locally built compatible speech runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INFO = ROOT / '.bootstrap/maven/ai/moonshine/moonshine-voice/0.1.5-wckd-cpu1/build-info.json'


def verify(apk, info):
    expected = json.loads(Path(info).read_text())
    if expected.get('kleidiai') is not False:
        raise ValueError('Build manifest must prove KleidiAI was disabled')
    member = 'lib/arm64-v8a/libonnxruntime.so'
    with zipfile.ZipFile(apk) as archive:
        if archive.namelist().count(member) != 1:
            raise ValueError('APK must contain exactly one arm64 ONNX Runtime')
        actual = hashlib.sha256(archive.read(member)).hexdigest()
    if actual != expected['runtime_sha256']:
        raise ValueError('APK contains an unexpected runtime: do not install or release it')
    return actual


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    print('Verified compatible arm64 ORT SHA-256:', verify(args.apk, INFO))
