# Live dictation smoke test (P6-1, P6-2, 2026-11-27)

Roadmap section 10, P6-1 and P6-2: the chat binds the session service of 3-Macaw Speech directly (`speech-api`
contract v3, D203, D208) and dictates live into the input box (D204, D209, D215 - D219). Without a usable session the
microphone opens the recognizer sheet as in 1.7.2.

## Commands

```powershell
py .python/device_speech_check.py --serial <serial> --only macaw,live
py .python/device_speech_check.py --serial <serial> --only live-acoustic --min-volume 70
py .python/device_speech_check.py --serial <serial> --only live-permission --revoke-macaw-microphone
py .python/device_speech_check.py --serial <serial> --only live-busy
```

- `macaw` probes the installed 3-Macaw Speech, checks the fallback to the sheet when it cannot dictate live, and opens,
  cancels and finishes real sessions while the chat is in front (nothing is played; the room is recorded briefly).
- `live` drives the chat with a scripted session source (`FakeLiveDictationSource`); nothing is recorded.
- `live-acoustic` plays the test recording through the speaker. It raises the media volume to the given percentage
  when it is lower and restores it afterwards. Ask before running it.
- `live-permission` revokes the microphone permission of 3-Macaw Speech first; the maintainer allows it again in the
  dialog that opens (within 180 s).
- `live-busy` needs the resident listener of 3-Stove Agent on during the run.

Receipts only name what passed and count events; recognized text is never printed, logged or stored (D16).

## Scripted session source (`SpeechLiveDictationDeviceTest`, 7 tests)

| Device | API | Result |
|---|---|---|
| Emulator AVD_API_33 (x86_64) | 33 | 7/7 |
| Sony XQ-AT72 (QV710AF65F) | 31 | 7/7 |
| Sony XQ-DQ72 (QV770340J7) | 33 | 7/7 |
| Xiaomi 23046RP50C (968e9f18) | 35 | 7/7 |

- A short press shows the stop state (stop icon, "Stop voice input" description, "Listening. Tap the stop button to
  finish" hint); partial text is
  gray in the input box and each final replaces it and stays; a second press finishes; 15 s without speech ends quietly.
- A selection is replaced; reaching 16,384 characters ends the session with the truncation or limit message.
- Leaving the chat (`onStop`), recreating it, sending, a new or another conversation and editing a message cancel the
  session and keep the shown text (D215); sending sends what is shown.
- While a reply streams, dictation only edits the input box and never sends.
- `SESSION_BUSY`, `MODEL_MISSING` and a session that died before anything was heard open the sheet once;
  `PERMISSION_DENIED` opens the permission screen of 3-Macaw Speech; a failure after text arrived keeps the text and
  only reports.
- With 3-Macaw Speech unusable (no model) the microphone opens the sheet and no session is opened.

## Probe and fallbacks (`MacawSpeechDeviceTest`, 3 tests)

| Device | API | 3-Macaw Speech | AutoJs6 | Status | Contract | Resident | Session ready / finish ends |
|---|---|---|---|---|---|---|---|
| Emulator AVD_API_33 | 33 | not installed | 6.8.x | NOT_INSTALLED, sheet | - | - | - |
| Emulator AVD_API_33 | 33 | 1.6.3 debug, no models | 6.8.x | NO_RECOGNITION_MODEL, sheet | 3 | no | - |
| Emulator AVD_API_33 | 33 | 1.6.3 debug | uninstalled | NOT_BINDABLE, sheet | - | - | - |
| Sony XQ-AT72 | 31 | 1.6.1 (196) | 6.8.1 | AVAILABLE | 3 | yes | 1914 ms (cold) / 102 ms |
| Sony XQ-DQ72 | 33 | 1.6.1 (196) | 6.8.1 | AVAILABLE | 3 | yes | 1952 ms (cold) / 51 ms |
| Xiaomi 23046RP50C | 35 | 1.6.3 (200) | 6.8.1 | AVAILABLE | 3 | yes | 194 ms (warm) / 101 ms |

Each run passed 3/3; the fallback test is skipped where 3-Macaw Speech dictates live, the session test where it
cannot. "Cold" means 3-Macaw Speech had to load its engines first. The second session of each run opens right after
the first was cancelled (D218).

The voice settings page (`SpeechSettingsDeviceTest`, 2 tests) passed on the emulator in all three states above: the
status row tells live dictation apart from the sheet and, with "Prefer 3-Macaw Speech" on, says why live dictation is
not available (unbindable, update, model, microphone, checking).

## Two sentences through the speaker (`live-acoustic`)

The test recording is `androidTest/assets/test-audio/en.wav` (LibriSpeech, 6.6 s, the same file 3-Macaw Speech locks
for its own tests): the sentence, 2.5 s of silence, the sentence again, 1 s of silence. Times are counted from the
start of playback; CER compares each sentence with the reference after normalization.

| Device | API | 3-Macaw Speech | Volume | Ready | First partial | First final | Finals | CER 1 | CER 2 |
|---|---|---|---|---|---|---|---|---|---|
| Sony XQ-AT72 | 31 | 1.6.1 | 21/30 (from 0) | 1928 ms | 7433 ms | 7997 ms | 2 | 0.000 | 0.000 |
| Sony XQ-DQ72 | 33 | 1.6.1 | 21/30 (from 9) | 140 ms | 7170 ms | 7913 ms | 2 | 0.000 | 0.000 |
| Xiaomi 23046RP50C | 35 | 1.6.3 | 105/150 (from 0) | 1639 ms | 7750 ms | 8410 ms | 2 | 0.000 | 0.000 |

All three phones had only the offline SenseVoice model installed, which 3-Macaw Speech runs per VAD segment: the first
partial arrived 0.55 - 1.13 s after the first sentence ended and its final 1.29 - 1.79 s after it. A streaming model
would show partial text while the sentence is spoken. The volume was restored on all three.

## Permission and a busy microphone (maintainer on the phone)

- XQ-AT72: with the microphone permission of 3-Macaw Speech revoked, a short press opened its
  `MicrophonePermissionActivity`; the maintainer allowed it and the next press dictated live (`permissionScreen`,
  `afterGrant`).
- Xiaomi 23046RP50C: with the resident listener of 3-Stove Agent turned on by the maintainer, a short press got
  `SESSION_BUSY` and opened the recognizer sheet once (answered by the test). The maintainer turned the listener off.

## Found and fixed during these runs

- D219: binding without `BIND_INCLUDE_CAPABILITIES` let Android silence the recording of 3-Macaw Speech on the XQ-AT72
  (`App op 27 missing, silencing record`). The chat now binds with the flags AutoJs6 uses.
- D218: right after its engines loaded, 3-Macaw Speech refused a session opened immediately after a cancelled one with
  `SESSION_BUSY` (3 of 3 cold runs on the XQ-AT72), which would have sent the press to the sheet. A new session now
  waits up to 2 s for the end of the cancelled one; 3 of 3 cold runs then passed.

## Not covered

- ColorOS: the OnePlus 63751f63 was not connected; API 35 was covered by HyperOS on the Xiaomi tablet.
- Partial text during speech needs a streaming recognition model, which none of the phones had.
- A session opened while nothing of 3-Stone AI is visible (a test-only situation; the chat cancels its session in
  `onStop`): on the Xiaomi tablet 3-Macaw Speech reported the session ready, but the ready event did not arrive within
  15 s.
