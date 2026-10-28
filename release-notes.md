Recording reliability update for the OnePlus 15 demo.

- Recording no longer loads the GPU language model or Kokoro. They load when you request a reply.
- Fixed native speech audio-buffer ownership, checked failed stream creation, and reset completion tracking across pause/resume.
- Serialized native teardown across Activity recreation; guarded microphone startup, focus rejection, playback callbacks and null transcript updates.
- Recover interrupted WAV headers and keep healthy library items accessible when another transcript is damaged.
- Added Engine → Copy diagnostics. It includes Android's previous-exit reason and device details, with no recordings or transcript text.
- Release is gated on unit/lint checks, real Moonshine transcription, denied microphone permission, actual Record-button capture with the default GPU preference, resume, backgrounding and recreation in an Android 15 emulator with animations enabled.

The exact OnePlus crash has not been confirmed from a device log. The emulator cannot validate OnePlus GPU/NPU drivers. If recording still closes the app, reopen it and share Engine → Copy diagnostics.

Install the APK below on Android 12+ arm64. This demo build uses a newly generated signing key: export any recordings/transcripts you want to keep before uninstalling the previous APK, then install this one. Uninstalling clears app data and downloaded models.

All speech and conversation inference stays on the phone after model download. No sub-50 ms performance claim is made.
