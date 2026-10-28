A quieter recorder, with speech-crash containment.

- Compact timer and Record / Pause / Resume control at the bottom.
- More space for the transcript; removed decorative onboarding, repeated status paragraphs and model details from the recording screen.
- Keyboard appears in Chat only. Play, Summary, Ask and Share remain available after pausing.
- Settings uses one engine selector; privacy details and diagnostics are available on request.
- Moonshine runs in a separate process with a bounded decoding queue. If it stops or falls behind, WAV capture continues and the app shows a short warning. Resume attempts transcription again.
- Native crash diagnostics now show process, signal and bounded stack metadata when Android supplies it. No audio, transcripts, raw memory or system logs are copied.
- Android 16 emulator coverage includes real Moonshine transcription, denied microphone permission, pause/resume, backgrounding, recreation, and deliberate speech-process SIGABRT while recording. Unit/lint checks gate publication.

Your Reason 5 report identifies a native crash, but does not identify its native function. This release contains speech-process failures; physical OnePlus GPU/NPU reply drivers remain unverified.

Install the arm64 APK on Android 12+. **Export any recordings and transcripts you want to keep before uninstalling the previous demo.** CI currently generates a new demo signing key per build, so replacing a previous APK may require uninstalling it. Uninstalling removes local recordings and downloaded models.

All inference stays on the phone after model download. No sub-50 ms performance claim is made.
