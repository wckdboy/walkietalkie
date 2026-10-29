# OnePlus speech SIGILL repair candidate

## Evidence

The 0.2.2 report names the `:speech` process, SIGILL, and
`libonnxruntime.so +0x5afb48`. The official Moonshine 0.1.5 AAR's arm64
runtime is ORT 1.23.2, Build ID `9c97f969fa1462bd45f51fa8aba96066a19185cc`.
Disassembling that binary shows `smstart` at `0x5afb40` and `ptrue p0.b` at
`0x5afb44`, followed by the SME2-family `ptrue pn9.b` instruction at the
reported fault offset `0x5afb48`. This is consistent with
execution entering an unsupported SME path. The installed APK's Build ID has
not been obtained, so this is strong supporting evidence, not a proven match.

Upstream context:
- https://github.com/microsoft/onnxruntime/issues/27884
- https://github.com/k2-fsa/sherpa-onnx/issues/3490
- https://github.com/moonshine-ai/moonshine/blob/v0.1.5/scripts/ort-build-common.sh

## Narrow fix

Keep Moonshine 0.1.5, its Java/native APIs, model formats, ORT version, and
operator-reduction configuration. Rebuild only arm64 ONNX Runtime with
`--no_kleidiai` and `onnxruntime_USE_KLEIDIAI=OFF`. ORT uses its other CPU
kernels instead of the KleidiAI SME dispatch path. This may reduce performance;
measure transcription latency and Kokoro playback on the phone before release.

`build-safe-speech.py` pins input hashes and the ORT commit, checks the build's
CMake setting, retains build artifacts, strips the distributable runtime,
and creates a private local Maven module with the original POM dependencies.
It does not publish anything. Only the arm64 ORT entry in the original AAR is
replaced; the other entries are unchanged. The app resolves Moonshine only from
that local repository, so a missing custom build fails rather than silently
falling back to the crashing published artifact.

No binary patching, duplicate native-library `pickFirst`, or stock AAR swapping
is used. Speech isolation and saved-audio recovery are unchanged.

## Build and verify

Use JDK 21, the project's Android SDK packages, NDK 28.2.13676358, Python 3.10+,
git, CMake and the Python packages listed in README. Set `ANDROID_HOME` to the
SDK. `ANDROID_NDK_HOME` optionally selects the same NDK version elsewhere.

```sh
python -m unittest discover -s scripts/tests -v
python scripts/build-safe-speech.py --jobs 4
python scripts/bootstrap.py
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python scripts/verify-safe-speech.py app/build/outputs/apk/debug/app-debug.apk
```

The final command checks the actual APK runtime bytes against the local build
manifest, not just the Gradle dependency declaration. CI runs it before saving
the arm64 APK. The existing x86 emulator workflow remains useful for app
regressions but cannot establish arm64/SME compatibility.

On the physical OnePlus, test repeated fresh recordings, silence and speech,
pause/resume, leaving/reopening the app, a long recording, and Kokoro playback.
Confirm timestamps/transcripts continue and saved WAV files remain playable.
If a crash recurs, retain the full native tombstone, runtime Build ID and
matching symbols from a symbol-enabled rebuild for source-level symbolication.
The default Release build is not a debug-symbol archive. Do not infer OOM
from the unavailable memory sample.
