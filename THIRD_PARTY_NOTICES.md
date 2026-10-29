# Third-party notices

WalkieTalkie's MIT license covers original application code, not upstream libraries or model weights. Weights are downloaded separately.

- llama.cpp / GGML: MIT. Official b11960 Snapdragon binaries, matching v0.8.0. See licenses/llama.cpp.txt and https://github.com/ggml-org/llama.cpp/tree/v0.8.0
- Moonshine Voice 0.1.5: see licenses/moonshine.txt for the upstream license and bundled third-party notices. https://github.com/moonshine-ai/moonshine
- Qwen2.5-3B-Instruct-GGUF: Qwen Research License. https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF
- Kokoro-82M: Apache-2.0 model. https://huggingface.co/hexgrad/Kokoro-82M
- AndroidX, Material Components, OkHttp, Okio, Kotlin standard library: Apache-2.0, resolved through Gradle.

The Hexagon device code comes from the official llama.cpp release. The phone supplies OEM FastRPC and OpenCL drivers; these are not redistributed here.

## UI and optional smaller models

Material Components for Android 1.14.0 and AndroidX Test are Apache-2.0. Qwen2.5-0.5B-Instruct-GGUF and Qwen2.5-1.5B-Instruct-GGUF are Apache-2.0; their model cards are linked in the README. The existing Qwen Research License notice remains applicable to 3B.

## Compatible speech runtime

The arm64 build of Moonshine Voice 0.1.5 replaces only its ONNX Runtime library
with ORT 1.23.2 (commit a83fc4d58cb48eb68890dd689f94f28288cf2278), built with
KleidiAI disabled and Moonshine's reduced operator configuration. ONNX Runtime
is MIT; see licenses/onnxruntime.txt and licenses/onnxruntime-third-party.txt
for upstream license/notices. Other Moonshine AAR entries are unchanged.
