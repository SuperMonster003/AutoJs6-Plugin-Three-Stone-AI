# Voice conversation smoke test (P5-4, 2026-10-19)

Roadmap section 10, P5-4 (D13): a long press on the microphone listens, sends the result, reads the
reply while it streams and listens again after the reading. In 1.4.0 every round opens the
recognizer's own window (3-Macaw Speech's sheet when it is preferred).

## Commands

```powershell
py .python/device_speech_check.py --serial <serial> --only conversation
py .python/device_speech_check.py --serial <serial> --only conversation-local
py .python/device_speech_check.py --serial <serial> --only conversation-online
```

`conversation` uses the fake recognizer from the test APK, a scripted target and a silent engine.
`conversation-local` and `conversation-online` use the device's own ready target (LiteRT model or
online profile, maximum output 64 tokens during the test) and the preferred real engine, with the
fake recognizer answering ten fixed questions and canceling the eleventh round; they play sound.
The online variant sends ten short questions to the configured provider and is run only when the
maintainer asks for it. The conversations the tests create are deleted afterwards.

## Exit paths and loop (`SpeechConversationDeviceTest`, 6 tests)

| Device | API | Result | Ten fake rounds: longest listen / first sound, mean round |
|---|---|---|---|
| Sony G8441 (BH900ASK9E) | 28 | 6/6 | 41 ms / 95 ms, 161 ms |
| Sony XQ-AT72 (QV710AF65F) | 31 | 6/6 | 59 ms / 74 ms, 137 ms |
| Xiaomi 23046RP50C (968e9f18) | 35 | 6/6 | 49 ms / 73 ms, 139 ms |

Exit paths checked, with the event that ended the conversation: recognizer canceled
(`RecognitionCanceled`), no speech (`RecognitionNoMatch`), recognizer network error
(`RecognitionFailed`, reported once), failed reply (`GenerationFailed`), exit button while the reply
is generating (`Exit`, the reply still finishes but is not read), leaving the chat while reading
(`LeftChat`, reading stopped), a typed message (`ConversationChanged`). A tap on the status bar
while reading stops the reply and the reading and sends the next sentence. Without any engine the
conversation listens again as soon as a reply is complete. The status bar shows "waiting for the
reply" and "reading, tap to interrupt" in those phases.

## Real local target (`SpeechConversationRealDeviceTest#localTargetTenRounds`)

XQ-AT72 (API 31), LiteRT model `litertlm.ab7838cdfc8f77e54d8ca45eadceb204` on the CPU profile,
3-Macaw Speech engine (versionCode 41, default voice), media volume 0/30 (left as found).
Ten rounds, then the canceled recognizer ended the conversation (`RecognitionCanceled`).

| Round | Send to first sound (ms) | Send to end of reading (ms) | Reply characters |
|---|---|---|---|
| 1 | 4735 | 11343 | 32 |
| 2 | 2291 | 4905 | 11 |
| 3 | 4410 | 17319 | 42 |
| 4 | 1784 | 3516 | 3 |
| 5 | 2156 | 4734 | 10 |
| 6 | 2354 | 30030 | 102 |
| 7 | 3367 | 11332 | 28 |
| 8 | 3339 | 9815 | 30 |
| 9 | 2667 | 8709 | 22 |
| 10 | 1747 | 3373 | 3 |

## Real online target (`SpeechConversationRealDeviceTest#onlineTargetTenRounds`, 2026-10-20)

G8441 (BH900ASK9E, API 28), OpenAI-compatible profile with model `claude-opus-4-8` (selected with
`--target-model`), 3-Macaw Speech engine (versionCode 41, default voice), media volume 0/50 (left
as found). Ten rounds, then the canceled recognizer ended the conversation (`RecognitionCanceled`).

| Round | Send to first sound (ms) | Send to end of reading (ms) | Reply characters |
|---|---|---|---|
| 1 | 4844 | 20221 | 53 |
| 2 | 3358 | 5395 | 1 |
| 3 | 3870 | 25038 | 57 |
| 4 | 3080 | 5390 | 3 |
| 5 | 3750 | 6159 | 4 |
| 6 | 3721 | 24257 | 60 |
| 7 | 3588 | 18013 | 35 |
| 8 | 3201 | 30125 | 62 |
| 9 | 4261 | 20895 | 40 |
| 10 | 3185 | 8470 | 9 |

The other configured online profiles (XQ-DQ72 `gpt-6.1-sol`, XQ-AT72 `gpt-5.6-sol` and
`claude-fable-5-1`, G8441 `claude-fable-5-1`) were refused by their provider with HTTP 403 on the
first round; each run ended correctly with `GenerationFailed` and no further listening.

The listen times of these runs are the fake recognizer's (well under 100 ms) and are not speech
recognition times.

## Spoken by the maintainer (`SpeechManualDeviceTest`)

XQ-DQ72 (QV770340J7, API 33), scripted reply, 3-Macaw Speech versionCode 41 for the sheet and the
engine (default AISHELL-3 voice), media volume 9/30.

| Check | Result |
|---|---|
| `manual-dictation`, preference on | 3-Macaw Speech sheet directly, no chooser; recognized, 6505 ms request to result (speaking included), 8 characters inserted |
| `manual-dictation --prefer-macaw false` | system chooser (Google, 3-Macaw Speech), Google "just once"; recognized, 9999 ms, 7 characters inserted; no default was set |
| `manual-conversation` | 3 rounds; listen 6023 / 6016 / 5425 ms, send to first sound 1649 / 447 / 477 ms, round 4315 / 3165 / 3314 ms; ended by closing the sheet (`RecognitionCanceled`) |
| `manual-assistant` (P5-5) | power button, speech, "3-Stone AI": the question reached a new conversation and was sent; first sound 1863 ms after the reading started. The first attempt was cut off by the cold-start appearance recreation, fixed in `a1252ef` |
