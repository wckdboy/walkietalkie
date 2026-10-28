# WalkieTalkie

**A little more human. A fully offline Android voice demo.**

WalkieTalkie listens with Moonshine, answers with Qwen, and speaks with Kokoro. Built for OnePlus 15 / Snapdragon 8 Elite Gen 5, with selectable Adreno GPU and experimental Hexagon v81 NPU acceleration.

## Stack

| Stage | Implementation | Runs on |
| --- | --- | --- |
| Speech recognition | Moonshine Tiny Streaming, Android SDK 0.1.5 | CPU |
| Language model | Qwen 2.5 3B Instruct, Q4_0 GGUF | Adreno OpenCL, Hexagon NPU, mixed, or CPU |
| Speech output | Kokoro, voice af_heart, through Moonshine | CPU |
| Android app | Java + small llama.cpp JNI bridge | Android 12+, arm64 |

Ollama and CTranslate2 are not bundled. Qwen runs directly through llama.cpp, so conversations need no PC, server, account, or network connection.

## Use the demo

1. Install the APK on the phone.
2. Tap **Download & set up**. Keep WalkieTalkie open on Wi-Fi. Qwen is 1.998 GB; speech models add to this. Keep at least 4 GB free. Interrupted Qwen downloads resume and are checked against a pinned SHA-256.
3. Tap **Tap to speak** and grant microphone permission. Pause to end the turn, or tap **Finish turn**. Typing works too.
4. Replies appear as tokens arrive and are spoken in chunks. Tap **Interrupt** to stop the reply, then tap again to speak.
5. After setup, enable airplane mode to verify offline use.

**Engine** selects CPU, Adreno GPU, Hexagon NPU, or a 50/50 GPU+NPU layer split. GPU is the default. NPU modes are experimental, depend on OEM FastRPC access, and ship the v81 skeleton for this phone. Mixed mode can be slower because layers execute sequentially and transfer intermediate data. Unsupported operations can run on CPU. Speech models use CPU in this demo.

The model is prompted to use short, varied fillers such as “So, yeah” and “Oh, okay.” Disable them in Engine settings. Fillers are generated as part of the reply.

## Latency and limits

No sub-50 ms or end-to-end latency guarantee is made. Metrics distinguish STT decode, first LLM token, TTS chunk synthesis, and first audio queued. Queued audio time is not an acoustic measurement. Loading, shader compilation, silence detection, speaker buffering, heat, and battery conditions affect latency.

- English speech in this version.
- Tap-to-start turns; no wake word, background listening, or automatic acoustic barge-in.
- Microphone released after each turn and when the app leaves the foreground.
- Chat history stays in memory. New chat or process exit clears it.
- Bounded 2,048-token context. Long prompts can request a shorter message or new chat.
- No web browsing, phone control, or other tools.
- GPU/NPU behavior needs validation on the actual OnePlus. Compilation does not establish device performance.
- The native runtime requires an accessible OEM Adreno OpenCL library. Other hardware is outside this demo's target.

## Build

Requirements: JDK 21, Python 3.10+, Android SDK 36, Build Tools 36.0.0, NDK 28.2.13676358, CMake 3.31.6.

~~~sh
python scripts/bootstrap.py
# Set ANDROID_HOME or create local.properties with sdk.dir=/path/to/sdk
./gradlew assembleDebug testDebugUnitTest lintDebug
~~~

Windows: use gradlew.bat. Bootstrap downloads checksum-pinned official llama.cpp b11960 Snapdragon binaries and generates a local demo signing key. This release matches commit 8642541b255bfb5dc39cd351a178be09013a55d6 / v0.8.0.

APK output: app/build/outputs/apk/debug/app-debug.apk. GitHub Actions builds and uploads an APK artifact. Keys differ between checkouts/CI runs; uninstall a demo signed by another key before installing the new one. These are sideload builds, not Play Store releases.

## Source map

- MainActivity.java: setup, chat UI, audio focus, engine settings.
- VoiceEngine.java: microphone, streaming generation, Kokoro playback, interruption.
- ModelStore.java: resumable downloads and Qwen integrity checks.
- Conversation.java: prompt, bounded history and incremental UTF-8 decoding.
- walkietalkie.cpp: accelerator selection, model loading, sampling and cancellation.
- scripts/bootstrap.py: pinned native runtime preparation.

## Privacy

Only model setup uses the internet: Hugging Face for Qwen and Moonshine's CDN for speech assets. No app analytics, accounts, cloud inference, or transcript upload. Internet permission remains available for model downloads. Models are excluded from Android backups.

## Licenses and upstream

Application code is MIT. Dependency and model licenses are separate; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Qwen 2.5 **3B** uses the **Qwen Research License**, not Apache-2.0; review it before use beyond a research/demo context.

- [Moonshine Android SDK](https://moonshine-voice.readthedocs.io/en/latest/quickstart/)
- [llama.cpp Snapdragon backend](https://github.com/ggml-org/llama.cpp/blob/v0.8.0/docs/backend/snapdragon/README.md)
- [Adreno OpenCL backend](https://github.com/ggml-org/llama.cpp/blob/v0.8.0/docs/backend/OPENCL.md)
- [Official Qwen GGUF model](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF)
- [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M)
