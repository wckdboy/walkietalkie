#!/usr/bin/env python3
"""Rebuild Moonshine's ORT 1.23.2 arm64 runtime without KleidiAI/SME dispatch.

Uses the same ORT ABI and reduced operator set as Moonshine 0.1.5. Only the
arm64 native runtime changes; Java, Moonshine and other ABIs stay byte-identical.
Requires git, CMake, Python build dependencies, and Android SDK/NDK r28c.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.bootstrap' / 'safe-speech'
VERSION = '0.1.5-wckd-cpu1'
ORT_COMMIT = 'a83fc4d58cb48eb68890dd689f94f28288cf2278'
MOONSHINE_COMMIT = '234f60faa0eb388b01cdf7e60aca232af37aefda'
MAVEN = 'https://repo.maven.apache.org/maven2/ai/moonshine/moonshine-voice/0.1.5/'
INPUTS = {
    'moonshine.aar': (MAVEN + 'moonshine-voice-0.1.5.aar', 'ee2d95c21150683c743db8f3aef66281fd5408bcefc94be3ca1d2545ada1f571'),
    'moonshine.pom': (MAVEN + 'moonshine-voice-0.1.5.pom', '7d3139c1bf92df6892bbb4e393d50712863179c3df04d4c2978fd485d45154e4'),
    'operators.config': (f'https://raw.githubusercontent.com/moonshine-ai/moonshine/{MOONSHINE_COMMIT}/core/third-party/onnxruntime/moonshine-required-operators.config', '8e114f397e92e272acb86a0e322f1d38ce9d6f3498c4522f74c7418448095cd6'),
}
RUNTIME = 'jni/arm64-v8a/libonnxruntime.so'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def download(name):
    url, expected = INPUTS[name]
    dest = CACHE / name
    if not dest.exists() or sha(dest) != expected:
        temporary = dest.with_suffix('.download')
        with urllib.request.urlopen(url, timeout=120) as source, temporary.open('wb') as output:
            shutil.copyfileobj(source, output)
        if sha(temporary) != expected:
            raise RuntimeError(f'Checksum mismatch: {name}')
        temporary.replace(dest)
    return dest


def run(*args, cwd=None):
    subprocess.run([str(x) for x in args], cwd=cwd, check=True)


def package(aar, pom, runtime, output):
    """Keep dependency metadata and all AAR entries other than the arm64 ORT."""
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    ET.register_namespace('', ns['m'])
    tree = ET.parse(pom)
    version = tree.getroot().find('m:version', ns)
    if version is None or version.text != '0.1.5':
        raise RuntimeError('Unexpected upstream POM version')
    version.text = VERSION
    replacement = Path(runtime).read_bytes()
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f'moonshine-voice-{VERSION}.aar'
    # Validate first, then stage every byte. A failed rerun leaves a previous
    # valid artifact intact; each final file is replaced atomically.
    with zipfile.ZipFile(aar) as original:
        if original.namelist().count(RUNTIME) != 1:
            raise RuntimeError('Expected exactly one arm64 ORT in upstream AAR')
        with tempfile.TemporaryDirectory(dir=output, prefix='.pack-') as directory:
            stage = Path(directory)
            with zipfile.ZipFile(stage / archive.name, 'w') as patched:
                for item in original.infolist():
                    patched.writestr(item, replacement if item.filename == RUNTIME else original.read(item))
            pom_name = f'moonshine-voice-{VERSION}.pom'
            # ElementTree removes the redirect to upstream Gradle .module metadata.
            tree.write(stage / pom_name, encoding='utf-8', xml_declaration=True)
            (stage / archive.name).replace(archive)
            (stage / pom_name).replace(output / pom_name)
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jobs', type=int, default=4)
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error('--jobs must be positive')
    sdk_env = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_env:
        parser.error('Set ANDROID_HOME or ANDROID_SDK_ROOT to your installed Android SDK')
    sdk = Path(sdk_env).resolve()
    ndk = Path(os.environ.get('ANDROID_NDK_HOME', str(sdk / 'ndk/28.2.13676358'))).resolve()
    if not (ndk / 'source.properties').is_file():
        parser.error(f'Android NDK r28c is missing: {ndk}')
    if '28.2.13676358' not in (ndk / 'source.properties').read_text():
        parser.error('Use NDK 28.2.13676358 for this pinned build')
    CACHE.mkdir(parents=True, exist_ok=True)
    aar, pom, operators = [download(x) for x in INPUTS]
    source = CACHE / 'onnxruntime'
    if not source.exists():
        run('git', 'clone', '--depth', '1', '--branch', 'v1.23.2', '--recursive', '--shallow-submodules',
            'https://github.com/microsoft/onnxruntime.git', source)
    commit = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if commit != ORT_COMMIT:
        raise RuntimeError(f'Unexpected ORT checkout {commit}; use a clean pinned checkout')
    if subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain', '--untracked-files=no'], text=True).strip():
        raise RuntimeError('ORT checkout has local changes; refusing an untracked runtime build')
    build = CACHE / 'build-arm64-no-kleidiai'
    run('bash', source / 'build.sh', '--build_dir', build, '--config', 'Release',
        '--android', '--android_sdk_path', sdk, '--android_ndk_path', ndk,
        '--android_abi', 'arm64-v8a', '--android_api', '26', '--build_shared_lib',
        '--minimal_build', 'extended', 'custom_ops', '--include_ops_by_config', operators,
        '--disable_ml_ops', '--compile_no_warning_as_error', '--no_kleidiai',
        '--cmake_extra_defines', 'onnxruntime_USE_KLEIDIAI=OFF',
        '--skip_tests', '--parallel', args.jobs, cwd=source)
    cmake_cache = (build / 'Release/CMakeCache.txt').read_text()
    if 'onnxruntime_USE_KLEIDIAI:BOOL=OFF' not in cmake_cache:
        raise RuntimeError('Build did not disable KleidiAI; refusing to package')
    runtime = build / 'Release/libonnxruntime.so'
    host = 'darwin-x86_64' if os.uname().sysname == 'Darwin' else 'linux-x86_64'
    # Preserve the original build artifact; strip only the distributable copy.
    # Release linking may already strip symbols: this is not a symbol archive.
    stripped = CACHE / 'libonnxruntime.so'
    shutil.copyfile(runtime, stripped)
    run(ndk / f'toolchains/llvm/prebuilt/{host}/bin/llvm-strip', '--strip-unneeded', stripped)
    data = stripped.read_bytes()
    if b'VERS_1.23.2' not in data or b'_sme' in data or b'/kleidiai-src/' in data.lower():
        raise RuntimeError('Unexpected runtime version or residual KleidiAI/SME implementation')
    output = ROOT / '.bootstrap/maven/ai/moonshine/moonshine-voice' / VERSION
    archive = package(aar, pom, stripped, output)
    manifest = {'moonshine': MOONSHINE_COMMIT, 'ort': ORT_COMMIT, 'kleidiai': False,
                'aar_sha256': sha(archive), 'runtime_sha256': sha(stripped),
                'ndk': '28.2.13676358', 'physical_device_tested': False}
    (output / 'build-info.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Built {archive}\nPhysical OnePlus validation is still required.')


if __name__ == '__main__':
    main()
