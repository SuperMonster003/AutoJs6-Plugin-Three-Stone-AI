# Reading aloud smoke test (P5-3, 2026-10-19)

Roadmap section 10, P5-3: automatic and manual reading through the system `TextToSpeech` interface,
preferring the 3-Macaw Speech engine (D10-D12, D18, D19, D24, D25).

## Commands

```powershell
py .python/device_speech_check.py --serial <serial> --only reading
py .python/device_speech_check.py --serial <serial> --only engine
```

`reading` uses a silent fake engine and a scripted model target. `engine` plays sound through the
speaker at the device's own media volume (not changed by the test); run it on one phone at a time.
The script refuses to run while another app is in the foreground unless `--force` is given, and it
uninstalls the test APK afterwards.

## Behavior with the fake engine (`SpeechReadingDeviceTest`, 5 tests)

| Device | API | Result |
|---|---|---|
| Sony XQ-AT72 (QV710AF65F) | 31 | 5/5 |
| Sony G8441 (BH900ASK9E) | 28 | 5/5 |
| Xiaomi 23046RP50C (968e9f18) | 35 | 5/5 |

- A streamed reply ("Hello there. " / "How are " / "you? " / "Fine.", 250 ms per chunk) is read as
  three sentences; the first sentence was queued about 710 ms before the reply finished on all three.
- The status bar shows "Reading aloud" while reading and disappears afterwards.
- The message menu offers "Read aloud" only for a finished reply and "Stop reading" for the reply
  being read; code blocks and tables are read as one hint each.
- Reading stops on: the status bar action, sending a new message, a new conversation, leaving the
  chat (`onStop`), stopping the generation, and another player taking the audio focus.
- No voice for the interface language: the engine's default voice reads and a message says so once.
  An engine that fails to start is reported and nothing is read.
- Without any engine there is no menu entry and automatic reading does nothing.

## Real engines (`SpeechEngineLatencyDeviceTest`)

Three Chinese sentences (about 40 characters) queued at once, like a finished reply. "Start" is the
time from `speak` to synthesis start, "first audio" to the first audio chunk; "gap" is how long a
sentence's audio arrived after the previous one finished playing (0 means no pause). Cold is the first
reading after creating the engine, warm the second. Milliseconds.

| Device | Engine, voice | Init | Cold start / first audio | Warm start / first audio | Gap cold / warm | Total cold / warm |
|---|---|---|---|---|---|---|
| XQ-AT72, API 31 | Macaw, AISHELL-3 | 343 | 1187 / 1357 | 145 / 299 | 0 / 0 | 13692 / 12287 |
| XQ-AT72, API 31 | Macaw, MeloTTS | 445 | 3950 / 4296 | 189 / 528 | 270 / 267 | 14109 / 10280 |
| XQ-AT72, API 31 | System default (Macaw) | 421 | 149 / 319 | 166 / 334 | 0 / 0 | 13014 / 12547 |
| G8441, API 28 | Macaw, AISHELL-3 | 370 | 1888 / 2215 | 187 / 463 | 0 / 0 | 10568 / 8948 |
| G8441, API 28 | Macaw, MeloTTS | 653 | 6969 / 7724 | 226 / 964 | 1004 / 981 | 18775 / 11921 |
| G8441, API 28 | System default (Google TTS) | 592 | 1973 / 1974 | 12 / 11 | 485 / 0 | 9126 / 6159 |
| XQ-DQ72, API 33 | Macaw, AISHELL-3 | 127 | 84 / 159 | 47 / 114 | 0 / 0 | 7518 / 7207 |
| XQ-DQ72, API 33 | Macaw, MeloTTS | 117 | 2624 / 2809 | 41 / 213 | 0 / 0 | 7860 / 5268 |
| XQ-DQ72, API 33 | System default (Google TTS) | 1247 | 778 / 779 | 6 / 6 | 0 / 0 | 6214 / 5407 |
| 23046RP50C, API 35 | System default (Xiaomi) | 10 | 2 / 39 | 4 / 26 | 0 / 0 | 8924 / 8964 |

Voices are `tts.vits.icefall.zh-aishell3/0` and `tts.vits.melo.zh-en/0` (3-Macaw Speech versionCode 41).
The Xiaomi tablet has 3-Macaw Speech versionCode 22, which has no engine, so only its system engine ran.

As expected from Macaw D35, MeloTTS is slower than real time on the G8441: about one second of silence
between sentences, cold or warm. AISHELL-3 never paused. The first reading after the engine starts
waits for the model to load (MeloTTS 7.7 s to the first audio on the G8441).

Chat with the preferred engine and automatic reading on (streamed reply, first sentence after 150 ms),
from the start of the reading to the first sentence starting: XQ-AT72 2792 ms, G8441 5340 ms,
XQ-DQ72 2213 ms (3-Macaw Speech, default voice, cold), 23046RP50C 232 ms (Xiaomi engine).

Media volume during the runs (left as found): XQ-AT72 0/30, G8441 0/50, XQ-DQ72 9/30, 23046RP50C 0/150.
Timing does not depend on the volume. A real phone call was not placed; the audio focus loss was
produced by another player's `AUDIOFOCUS_GAIN` request.
