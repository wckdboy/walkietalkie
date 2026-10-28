#!/usr/bin/env python3
"""Fetch checksum-pinned official Snapdragon runtime and prepare a demo signing key."""
import hashlib, pathlib, subprocess, tarfile, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = "https://github.com/ggml-org/llama.cpp/releases/download/b11960/llama-b11960-bin-android-arm64-snapdragon.tar.gz"
SHA = "b5f404eee508c2429d136cc95bcae2c6e1556612175fe3ccc223ac147a1a17cf"
LIBS = {"libllama.so", "libggml.so", "libggml-base.so", "libggml-cpu.so", "libggml-opencl.so", "libggml-hexagon.so", "libggml-rpc.so", "libggml-htp-v81.so"}
cache = ROOT / ".bootstrap"
cache.mkdir(exist_ok=True)
archive = cache / "snapdragon.tar.gz"
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != SHA:
    print("Downloading official llama.cpp b11960 Snapdragon build...")
    urllib.request.urlretrieve(URL, archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA:
    raise SystemExit("Runtime checksum mismatch")
with tarfile.open(archive) as package:
    for member in package.getmembers():
        name = pathlib.PurePosixPath(member.name)
        target = None
        if name.parent.name == "lib" and name.name in LIBS:
            target = ROOT / "app/src/main/jniLibs/arm64-v8a" / name.name
        elif name.parent.name == "include" and member.isfile():
            target = ROOT / "third_party/llama-include" / name.name
        if target:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(package.extractfile(member).read())
if not (ROOT / "demo.keystore").exists():
    subprocess.run(["keytool", "-genkeypair", "-keystore", str(ROOT / "demo.keystore"), "-storepass", "android", "-keypass", "android", "-alias", "murmur", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=WalkieTalkie Demo", "-noprompt"], check=True)
print("Ready. Run ./gradlew assembleDebug testDebugUnitTest lintDebug")

# Public-domain Dickens reading from the upstream Android integration tests.
# Pinned Git blob identity; used only in the instrumentation APK.
fixture = ROOT / "app/src/androidTest/assets/two_cities.wav"
fixture.parent.mkdir(parents=True, exist_ok=True)
urllib.request.urlretrieve("https://raw.githubusercontent.com/moonshine-ai/moonshine/v0.1.5/language-bindings/android/java/androidTest/assets/two_cities.wav", fixture)
data = fixture.read_bytes()
if hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() != "0cc95ad2e030e08338de52c04ab133fe2afe1bba":
    raise SystemExit("Test audio checksum mismatch")
