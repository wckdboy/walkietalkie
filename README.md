# WalkieTalkie

**An offline dictaphone with someone to think alongside you.**

Record long thoughts or meetings, follow a live transcription feed, and pause to invite a spoken reflection. Audio, transcripts and conversations stay in private storage on your phone.

## 0.2.3: compatible arm64 speech runtime

The Android arm64 build replaces Moonshine 0.1.5's bundled ONNX Runtime with
an operator-reduced build of the **same ORT 1.23.2 commit, with KleidiAI disabled**.
This avoids the SME instruction-dispatch path implicated by the OnePlus SIGILL.
It preserves Moonshine's ABI and models and may trade some throughput for compatibility.
Speech-process isolation and WAV recovery remain in place.

This is a candidate fix until recording and Kokoro playback pass on the physical
OnePlus. Emulator tests cannot validate arm64 instruction dispatch.
See [speech runtime repair](docs/speech-runtime-repair.md) for evidence and checks.

## 0.2.2: clearer recording and speech isolation

The recorder now keeps a timer and one main control at the bottom. Transcript rows are plain and timestamped; assistant replies retain their Material container. The keyboard is limited to Chat. Model, privacy and diagnostic details live in Settings.

Moonshine decoding runs in a separate private process with a bounded queue. A decoder failure or backlog stops transcription while microphone capture continues saving WAV audio. Pause finalizes audio before waiting for the decoder. Resume opens a fresh decoder process. Copy diagnostics includes only bounded signal and crashing-thread stack metadata from Android's native tombstone when available.

The OnePlus report's exit reason 5 indicates a native crash; the original failing function is still unknown. Speech isolation contains that subsystem, while Qwen/Kokoro reply execution remains in the main process.

## Reliability fixes in 0.2.1

- Recording opens Moonshine without initializing Qwen, Kokoro or an OpenCL backend. Reply models load only when needed.
- Checked speech handles and per-stream completion tracking fix retry/resume behavior. A small separate JNI bridge releases audio buffers after every call, avoiding the upstream 0.1.5 add-audio buffer leak.
- Native teardown is serialized across Activity recreation so an old Activity cannot unload a new engine.
- Microphone format/start errors, audio-focus rejection, null transcript text and stale callbacks after destruction are handled.
- WAV headers recover after interrupted capture. One damaged transcript no longer blocks the rest of the library.
- Settings → Copy diagnostics includes device/version, the last recording operation and Android's previous-exit reason. No audio or transcript is included or uploaded.

The exact exit reported on the OnePlus 15 has not been captured in a device log. These fixes address confirmed code defects; the emulator cannot validate the OnePlus OpenCL/FastRPC drivers.

## What is new in 0.2

- Material 3 Expressive using Google's stable **Material Components 1.14.0** library: morphing buttons, wavy download progress, loading indicators, dynamic wallpaper colors, light/dark themes and accessible touch targets.
- **Record** mode captures continuously until you pause; **Chat** mode takes short turns with the assistant.
- Audio is saved as 16 kHz mono WAV, incrementally written and resumable. The header is checkpointed every two seconds.
- Timestamped transcript cards are separate from assistant replies. Tap a timestamp to play from that position; timestamps are approximate segment boundaries, not word alignment.
- A persistent library with title/transcript search, recording/chat filters, newest/oldest/title/duration sorting, pinning, rename and delete.
- Export transcripts as text or original audio as WAV using Android's document picker.
- Summarize or reflect on a recording. Long transcripts are condensed in chunks, then merged to fit the model context; summaries can omit details and should be checked against the original.
- A **Hugging Face model picker** with verified Qwen 2.5 Instruct Q4_0 downloads: 0.5B (429 MB), 1.5B (1.07 GB) or 3B (2.00 GB). Downloads resume and SHA-256 checks run before use.
- GPU, CPU, experimental Hexagon NPU and mixed GPU/NPU acceleration.
- GitHub Actions builds the APK, runs unit/lint checks, launches it in an Android 16 emulator, exercises the UI and saves screenshots.

## First use

1. Install the APK. It requires Android 12+ and arm64.
2. Tap **Download**, or open Settings → Models to choose a smaller model. The default is Qwen 3B. Speech assets add to the listed download sizes; keep at least 4 GB free for default setup.
3. Keep the app open on Wi-Fi while setup finishes. Then try airplane mode.
4. Choose **Record**, grant microphone permission, and capture your thought or meeting. Tap **Pause** to save the current part. Resume appends to the same audio file.
5. Use **Summary** or **Ask** to invite the assistant. Switch to **Chat** for short, automatic turns.
6. Open the library icon to find, organize, play or export saved sessions.

**Recording is foreground-only in this demo.** Leaving the app pauses capture and saves it. The screen stays awake during recording. There is no wake word, background microphone service, speaker identification or automatic acoustic barge-in. Speech is English. Transcripts and summaries may contain errors.

## Stack

| Stage | Implementation | Hardware |
| --- | --- | --- |
| STT | Moonshine Tiny Streaming, SDK 0.1.5 | CPU |
| LLM | Qwen 2.5 Instruct, Q4_0 GGUF via llama.cpp | Adreno OpenCL / Hexagon NPU / mixed / CPU |
| TTS | Kokoro, af_heart voice through Moonshine | CPU |
| UI | Native Android Views + Material 3 Expressive 1.14.0 | Android 12+ |

Ollama and CTranslate2 are not bundled. Native llama.cpp keeps conversations on the phone. Qwen is prompted to use short, varied conversational fillers; Settings can disable them.

GPU is the default. NPU/mixed modes use the Snapdragon backend's Hexagon v81 skeleton, targeting OnePlus 15 / Snapdragon 8 Elite Gen 5. OEM OpenCL/FastRPC availability determines whether these modes work. Mixed mode executes layers sequentially and can be slower. The packaged runtime requires the OEM OpenCL library even in CPU mode; recording and Moonshine transcription can run in the emulator independently. OnePlus GPU/NPU reply acceleration still requires physical-device testing.

No sub-50 ms latency claim is made. Copied diagnostics show STT decode, first token, TTS chunk and first audio queued. Queued audio is not an acoustic measurement.

## Privacy and storage

Audio, session JSON and downloaded Qwen weights are kept in Android no-backup app storage. There are no analytics, accounts or transcript uploads. Internet access is used for requested model downloads and model-card links. Recording time consumes about **1.92 MB/minute** of WAV storage. Delete from the library to remove audio and transcript, or export first. Uninstalling clears app data.

Hugging Face downloads come from the official public Qwen repositories and require no token. The picker is a compatible catalog, not arbitrary model import. Moonshine/Kokoro assets use the speech SDK's downloader. The app does not upload recordings to Hugging Face.

## Build and emulator

JDK 21, Python 3.10+, Android SDK 36, Build Tools 36.0.0, NDK 28.2.13676358 and CMake 3.31.6.

~~~sh
python -m pip install cmake==3.31.6 ninja packaging numpy sympy flatbuffers
python scripts/build-safe-speech.py --jobs 4
python scripts/bootstrap.py
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
~~~

The native repair build requires Linux or macOS (bash). On Windows, build it in WSL or CI; Gradle itself can use gradlew.bat. Bootstrap retrieves checksum-pinned llama.cpp b11960 Snapdragon binaries, matching commit 8642541b255bfb5dc39cd351a178be09013a55d6 / v0.8.0.

CI preserves the arm64 release APK, then builds a speech-capable x86_64 APK for native emulator testing. The emulator build deliberately omits the Snapdragon-only LLM library. To run those checks on an Android 16 Google APIs x86_64 emulator:

~~~sh
./gradlew -PtestAbi=x86_64 assembleDebug assembleDebugAndroidTest
bash scripts/emulator-smoke.sh
~~~

Emulator checks launch the real app with animations enabled, exercise UI/storage and corrupted metadata recovery, transcribe a pinned upstream speech fixture three times across fresh streams, reject missing microphone permission, then tap the actual Record button with the default GPU preference. They check captured PCM, pause/resume, backgrounding and Activity recreation without Qwen installed, then deliberately SIGABRT the speech process and verify continued recording, a valid WAV header, and recognition recovery on resume. Screenshot transcript/reply text remains fixture data. Physical OnePlus microphone quality and GPU/NPU reply performance are not measured.

Debug APK: app/build/outputs/apk/debug/app-debug.apk. Bootstrap creates a local demo key for debug/test builds only. Release builds are unsigned until the release job signs them with the stable project key from GitHub Actions secrets. Missing signing secrets fail the release; there is no demo-key fallback. See [release signing](docs/release-signing.md).

A newly established stable key does not match an older randomly signed demo. Export recordings before uninstalling an incompatible previous build. Future releases using the same stable key can update each other normally.

## Source map

- MainActivity.java: Material UI, dictaphone controls, transcript feed, library, playback/export, model picker.
- VoiceEngine.java: continuous capture, short conversations, chunked transcript reflection and Kokoro playback.
- SessionStore.java / WavWriter.java: atomic metadata persistence and incremental audio.
- ModelCatalog.java / ModelStore.java: compatible Hugging Face catalog, resuming and integrity checks.
- Conversation.java / walkietalkie.cpp: bounded prompts, decoding and native inference.
- SpeechSession.java / speech.cpp: checked single-stream speech inference and owned audio buffers.
- Diagnostics.java: local Android exit details.
- UiSmokeTest.java / RecordingTest.java: real Android UI/storage, native transcription and microphone regression checks.

## Licenses and primary references

Application code is MIT. Model and dependency licenses are separate; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Qwen 0.5B/1.5B use Apache 2.0; Qwen **3B** uses the **Qwen Research License**.

- [Material Components 1.14.0](https://github.com/material-components/material-components-android/releases/tag/1.14.0)
- [Material 3 Expressive setup](https://github.com/material-components/material-components-android/blob/1.14.0/docs/getting-started.md#material3expressive-themes)
- [Moonshine SDK](https://moonshine-voice.readthedocs.io/en/latest/quickstart/)
- [Snapdragon backend](https://github.com/ggml-org/llama.cpp/blob/v0.8.0/docs/backend/snapdragon/README.md)
- [Qwen 0.5B](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), [1.5B](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF), [3B](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF)
- [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M)
