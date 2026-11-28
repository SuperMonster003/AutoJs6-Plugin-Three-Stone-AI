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

## P6-3: rounds through the 3-Macaw Speech session (2026-11-28)

Roadmap section 10, P6-3 (D209, D220): when 3-Macaw Speech can dictate live, every round opens a
single utterance session instead of the sheet. The gray text follows any draft in the input box,
the first non-empty final is sent, and 8 s without speech end the conversation. `SESSION_BUSY`, a
missing model or an unusable service fall back to the sheet for that round only.

```powershell
py .python/device_speech_check.py --serial <serial> --only conversation,live-conversation
py .python/device_speech_check.py --serial <serial> --only conversation-local-live
```

### Fake session source (`SpeechLiveConversationDeviceTest`, 11 tests)

The six P5-4 cases now live in `SpeechConversationCases` and run twice: through the sheet
(`SpeechConversationDeviceTest`, unchanged behavior) and through a scripted session source
(`SpeechLiveConversationDeviceTest`), which adds five session-only cases: gray text after the draft
and removed once sent, the exit button and leaving the chat keeping the text, a microphone press
finishing the round, an empty final listening on, `SESSION_BUSY` / `MODEL_MISSING` taking the sheet
for one round and the session again for the next, `PERMISSION_DENIED` opening the permission
screen, and an unusable 3-Macaw Speech keeping the whole conversation on the sheet.

| Device | API | Sheet cases | Session cases | Ten fake rounds through the session: longest listen / first sound, mean round |
|---|---|---|---|---|
| Sony XQ-AT72 (QV710AF65F) | 31 | 6/6 | 11/11 | 100 ms / 81 ms, 146 ms (sheet: 74 ms / 78 ms, 144 ms) |

### Real session and local target (`SpeechConversationRealDeviceTest#localTargetTenRoundsThroughTheSession`)

XQ-AT72 (API 31), the same LiteRT model as P5-4 (`litertlm.ab7838cdfc8f77e54d8ca45eadceb204`,
Gemma 4 E2B IT, CPU profile, downloaded on the device and checked against the pinned digest),
3-Macaw Speech 1.6.1 (versionCode 196) for the session and the engine, offline SenseVoice model,
maximum output 64 tokens. In every round the phone plays the first sentence of `en.wav` (6.6 s)
through its own speaker as soon as the session is ready (at most 125 ms later), media volume 21/30
only while the question plays and 0/30 while reading (as found, as in P5-4). Ten rounds, then the
eleventh heard nothing and ended the conversation (`RecognitionNoMatch`); no round fell back to
the sheet.

| Round | Listen (ms) | End of speech to final (ms) | Send to first sound (ms) | Send to end of reading (ms) | Reply characters | CER |
|---|---|---|---|---|---|---|
| 1 | 8296 | 1397 | 2614 | 18714 | 306 | 0.057 |
| 2 | 8198 | 1290 | 1797 | 19121 | 313 | 0.000 |
| 3 | 8277 | 1393 | 3074 | 19244 | 295 | 0.000 |
| 4 | 8348 | 1419 | 3040 | 18301 | 295 | 0.000 |
| 5 | 8374 | 1405 | 1802 | 19433 | 323 | 0.000 |
| 6 | 8322 | 1365 | 3076 | 18136 | 289 | 0.000 |
| 7 | 8347 | 1406 | 1875 | 17029 | 281 | 0.000 |
| 8 | 8285 | 1367 | 3091 | 18190 | 289 | 0.000 |
| 9 | 8287 | 1325 | 1872 | 18201 | 304 | 0.000 |
| 10 | 8372 | 1441 | 1863 | 17834 | 289 | 0.000 |

Compared with P5-4 on the same phone and model:

- Listen: P5-4 measured the fake recognizer (under 100 ms), not recognition. Through the session a
  round now takes 8.2 - 8.4 s from listening to the sent text, of which 6.6 s is the question
  itself; the final arrives 1.29 - 1.44 s after the speech ends (SenseVoice runs per VAD segment,
  see `dictation-session-smoke.md`), and nothing opens on screen between rounds.
- Send to first sound: 1797 - 3091 ms, median 2245 ms, against 1747 - 4735 ms, median 2511 ms in
  P5-4.
- Send to end of reading: 17.0 - 19.4 s for replies of 281 - 323 characters. P5-4 asked ten
  different short questions (replies of 3 - 102 characters, 3.4 - 30.0 s), so the totals are not
  comparable round by round; here every round asks the same long sentence and the reply reaches
  the 64-token limit.

A first run the same morning (09:42) also sent the ten questions (CER 0.000 in rounds 1 - 9 except 0.011
in round 8; first sound 1775 - 3168 ms, end of reading 16.7 - 19.4 s), but after the last question
the session heard two more utterances from the room and sent them (reading was silent at 0/30, so
it was not the phone's own voice). The test now counts such rounds as `ambientRounds` and leaves
with the exit button at the next ready instead of waiting for silence; the rerun above had none.
