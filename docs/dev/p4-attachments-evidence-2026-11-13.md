# Chat attachments: tests and real requests (2026-11-13)

3-Stone AI 1.7.0 development build 339, roadmap 1.6.0 P4 (P4.1 - P4.4; D70, D71, D131 - D142). No release, VERSION_NAME stays 1.6.0 until the 1.7.0 release card.

## Unit tests (JVM)

`:app:testDebugUnitTest`: 654 tests, all passing (19 of them new for P4).

- ChatAttachmentsTest (12): the metadata codec (round trip, damaged / newer / oversized values read as "no attachments"), metadata validation and file names derived from the ID only, the composer limits (20 per message, 5 MB per picture, 256 KB per text file, 32 MB / 100 pages per PDF, 48 MB per conversation), which attachments each target refuses and whether a message fits one request (Gemini 20 MB, OpenAI compatible without PDF, local models none), when an online persistent session may be reused (D137), the omitted note (at most five names), store paths that stay inside the attachment directory, strict UTF-8 detection for text files (D133), the history keeping attachments, and the replay of earlier attachments newest first within the token budget with notes for unsupported, missing and over-budget files (D134).
- OnlineAiAttachmentsTest (7): the request bodies of the three protocols. Anthropic sends `document` and `image` blocks before the text-file envelopes and the prompt; Gemini sends `inline_data` parts without type fields; OpenAI compatible takes text files and pictures and refuses PDF before any byte is read; a message with attachments and no text sends no empty text part; history attachments are sent again; the token estimates follow the roadmap formulas (picture w x h / 750, PDF 2000 per page, text bytes kept apart from the calibration, D138).
- ApplicationTextPunctuationTest covers the new strings in the 11 resource directories.

## Instrumentation tests on an emulator

AVD_API_33 (x86_64, Android 13), started read-only as emulator-5970, with the scripted backend (ScriptedAiBackend; no network).

- ChatAttachmentDeviceTest, 3 / 3 PASS:
  - `pickedFilesAreSentStoredReplayedOpenedAndDeletedWithTheConversation`: the "+" menu starts the photo picker (several pictures) and the documents picker (`*/*`, several files), whose results are stubbed with a generated PNG, a one-page PDF and a UTF-8 text file. The draft and a recreated activity keep the picked files (D140); the scripted backend receives the three files in the picked order with the same bytes, kinds, MIME types and the PDF page count; the files move into the conversation directory, come back from the history on the next turn and are sent again (D134); tapping the thumbnail hands a `content:` URI with `image/png` to a viewer (D141); deleting the conversation deletes its directory (D136).
  - `aTargetWithoutAttachmentsExplainsWhyAndRefusesToSendThem`: with a local model the "+" is dimmed and only explains why; a file picked earlier stays in the bar, dimmed, the send is refused and nothing reaches the backend; removing it deletes the pending file (D139).
  - `aTextOnlyTargetOffersFilesButNotPicturesAndHoldsBackThePdf`: for a target that takes neither pictures nor PDF the menu shows the disabled "pick pictures" variant with its reason and an enabled "pick files"; a picked PDF is dimmed with a warning, the send is refused, and after removing it only the text file is sent.
- ChatAttachmentPickerEmulatorTest (opt-in `-e systemPickers true`), 1 / 1 PASS: the test puts its own PNG and PDF into the emulator's media store, picks the picture in the system photo picker and the PDF in the documents UI through UiAutomation, and checks that the backend received the same bytes (the PNG within the edge limit is kept byte for byte, D132). The media rows and the empty test folders are removed afterwards. This is the only test that drives the system pickers; on the phone the files are injected directly (D131).
- The whole instrumentation suite (106 tests, 26 minutes) on the same emulator: 100 PASS. The six failures are not caused by P4:
  - SpeechManualDeviceTest (3): manual tests that wait for a person to speak.
  - ServiceDetailDeviceTest `aNewServiceKeepsItsDraftAcrossRecreationAndIsStoredOnlyOnSave`: passes when run alone (4 / 4).
  - ChatReasoningCardDeviceTest `theCardStreamsCollapsesExpandsAndSurvivesReopening` and SpeechReadingDeviceTest `repliesAreReadWhileTheyStream`: time out on this emulator ("the card streams the reasoning", "reading starts"). The baseline before P4 (703ba9e, built in a separate clone and installed on the same emulator) fails both in the same way, so they are timing problems of this read-only emulator, not regressions. They are left for a later card.

## XQ-DQ72

XQ-DQ72 (QV770340J7, Android 13, arm64-v8a) through the remote adb server, with the maintainer's own data: three online services, two entries in `files/models`, the default target on the Anthropic service.

### Consent and upgrade from 336 to 339 (2026-11-14 00:0x)

- The maintainer agreed in this session ("同意", 2026-11-13 23:5x) to install debug build 339 (sources of 5b99a44) over debug build 336 with data kept. Nothing was uninstalled. At 21:4x the remote adb server listed no device, so nothing was done until the consent and the device were both there (23:55).
- `.python/device_attachments_flow.py --snapshot pre339 --install --list --compare before336`: the state recorded through run-as before this card (default target, services document, local model catalog, conversation history, the entries of the nine preference files, names and sizes in `files/models`, number of attachment files) was the same after the install and the first launch. The script prints only same / changed per item, never content, keys, IDs or digests. The 32 MB APK took about 10 minutes over the remote link.
- Online targets listed by the debug build (no request sent): AIGoCode (Codex) gpt-6.1-sol (OpenAI compatible, picture input on, no PDF), Gemini gemini-3.8-flash (picture input off, PDF), Anthropic claude-opus-5-5 / claude-fable-5-1 / claude-fable-5-2 (picture input off, PDF); all ready.

### Requests (00:0x - 00:1x), one each, `--snapshot ready339 --request ... --compare ready339`

ChatAttachmentRealDeviceFlowTest `oneRequestToARealService`, one instrumentation run per request. Each run picks the chosen target in a new conversation, injects the files the test generated itself (no file from the phone's storage, no system picker on the phone), sends one message, checks the stored message and its attachment files, logs only the status, the reply length and which expected words it contains, then deletes the conversation with its files and restores the default target and the preferences it touched. No API key was read or printed.

| Case | Target | Files | Result |
|---|---|---|---|
| Picture (OpenAI compatible `image_url`) | AIGoCode (Codex) / gpt-6.1-sol | a 2-color PNG | PASS, complete after 22 s, 23 characters, names both colors (red, blue) |
| PDF + text file (Anthropic `document` block + text envelope) | Anthropic / claude-opus-5-5 | a one-page PDF with a code word, a UTF-8 text file with a fruit | PASS, complete after 36 s, 40 characters, names both |
| PDF + text file (Gemini `inline_data` + text envelope) | Gemini / gemini-3.8-flash | the same two files | PASS, complete after 117 s, 40 characters, names both |

- Text-file injection was covered by the two PDF requests (D131).
- No Gemini picture was sent: picture input is off for gemini-3.8-flash in the maintainer's service, and this card does not change the maintainer's settings. The Gemini service has no 2.5 model, so the Q15 round (a non-default effort on Gemini 2.5) was not run.
- No request was repeated; there were no failures.

### State left on the device

- `--compare ready339` after the three requests: default target, services, local model catalog, conversation history, the nine preference files and `files/models` are the same as before the requests; no attachment file is stored (`files/attachments` holds only the empty `.pending` directory).
- Debug build 339 stays installed with the maintainer's data; the androidTest APK is uninstalled; nothing was pushed to `/data/local/tmp`.
- Not caused by this card: `cache/litertlm` still holds the XNNPACK cache (1.55 GB) of the model that the P5b flow downloaded and deleted on 2026-11-13 (same size and time as that model), so deleting that model left its cache behind. Left for a later card.
