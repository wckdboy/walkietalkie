# WalkieTalkie 0.2.3: arm64 speech compatibility candidate

- Rebuilds Moonshine's ONNX Runtime 1.23.2 for arm64 without KleidiAI, avoiding
  the SME dispatch path implicated by the OnePlus Android 16 SIGILL report.
- Keeps Moonshine 0.1.5 APIs, models, reduced operators, speech-process isolation
  and saved-audio recovery. Other ABIs are unchanged.
- Verifies the runtime bytes inside the arm64 APK before uploading the artifact.
- May trade some CPU throughput for compatibility. Physical OnePlus recording
  and Kokoro playback still need validation; emulator success is not arm64 proof.

Release signing requires the stable project key; it no longer uses a newly
generated demo key each run. The first stable-key APK may not update an older
demo APK. Export recordings before uninstalling a differently signed previous
build. This is a prerelease candidate,
not confirmation that the reported phone crash is resolved.
