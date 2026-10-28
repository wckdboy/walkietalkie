An offline dictaphone with a conversational participant, built for OnePlus 15.

### Install
Download **WalkieTalkie-0.2.0-expressive.apk** from Assets below. Allow installation from your browser if Android asks. Complete model setup on Wi-Fi, then use offline.

### New in this demo
- Material 3 Expressive components, wallpaper colors, light/dark mode and large touch targets.
- Continuous recording with resumable WAV audio, a timestamped transcript feed, and separate assistant replies.
- Saved recordings/chats with search, sorting, pin, rename, playback, export and deletion.
- Summarize or reflect on a paused recording.
- Hugging Face model picker: Qwen 2.5 0.5B, 1.5B or 3B, with resumable, checksum-verified downloads.
- CPU, Adreno GPU, experimental NPU and mixed modes.

### Validation
Release publishing follows successful compilation, unit tests, Android lint, and Android 15 emulator UI/storage checks. Emulator screenshots use seeded transcript/reply data. The OnePlus microphone pipeline, long meeting endurance and GPU/NPU performance have not been measured on a physical phone.

### Before testing
- Recording is foreground-only; leaving the app pauses and saves it.
- English speech; no automatic speaker identification.
- Keep 4 GB free for default model setup. WAV audio uses about 1.92 MB/minute.
- Transcript timestamps are approximate segment boundaries. Verify summaries against the original.
- This uses a demo signing key. If Android rejects an upgrade from an earlier differently signed build, export recordings before uninstalling it; uninstalling removes app data and models.
- NPU/mixed modes are experimental. No sub-50 ms claim is made.
