# Markdown export and image sharing (2026-11-14)

3-Stone AI 1.7.0 development, roadmap 1.6.0 P7 (P7.1, P7.2; D78 item 4, D83, D149 - D157). No release, VERSION_NAME stays 1.6.0 until the 1.7.0 release card.

## P7.1 What the plugin now does

### Entry points and content (D149)

- The chat toolbar menu ("Export as Markdown", "Share as image") exports the open conversation; both items are disabled while the conversation has no messages and hidden while search is open. The drawer's conversation menu (long press) has the same two items after the existing "Export" (.3sac backup), which is unchanged.
- Both exports read what history holds; the open conversation is saved first. Only the shown path is exported: the selected version of every turn (D148).
- A conversation whose reply is still being generated (the open one, or one the generation coordinator holds) is refused with "Wait for the reply to finish before exporting this conversation" (D155). The menu items stay enabled, since a disabled item could not say why.

### Markdown (D150, D151)

- `ConversationMarkdownExport` writes: `# <title>`, a list with the model, created, updated and exported times (`yyyy-MM-dd HH:mm` with the UTC offset; the exported line ends with "(3-Stone AI <version>)"), then every shown message. User messages are `## User` (localized), replies `## Assistant (<model name>)`, notices a quote block.
- Attachments are written by name only: a bold "Attachments:" line followed by one list item per file.
- Replies are Markdown already and are copied as they are (line breaks as LF). A code fence a reply leaves open (for example one stopped while streaming) is closed with the opening fence's indentation and length. Stopped, failed and cut replies get an italic note, an empty reply an italic placeholder.
- User messages, reasoning and notices are plain text in the chat, so every line is escaped (`\`, backtick, `* _ [ ] < > & | ~`, and `# - + = >` or an ordered marker at the start of a line); leading spaces become no-break spaces and single line breaks hard breaks. `# not a heading` and HTML stay literal text in other readers.
- Reasoning: when a shown reply has reasoning, a dialog with one check box "Include reasoning" opens first, unchecked every time (not remembered); without reasoning the picker opens directly. Included reasoning is a quote block before the reply, headed "Thought for N s" (or "Reasoning" without a duration), with the truncation note when it was cut.
- The system picker (`ActivityResultContracts.CreateDocument("text/markdown")`) suggests the title without characters file systems refuse, cut to 80 characters, plus ".md". The document is written with mode "wt" (falling back to "w"). The pending conversation and the reasoning choice survive a recreation of the chat while the picker is open (`onSaveInstanceState`).

### Image (D152 - D156)

- `ChatTranscriptImageRenderer` lays the conversation out off screen with the chat's own message views, bubble styles, font size and theme: a header with the title and "model | time", every shown message (image thumbnails or file name chips for attachments, the reasoning card expanded when chosen), and a footer "3-Stone AI <version>". Version switches, search highlights and usage lines are not drawn; code blocks and tables that scroll sideways show their visible width.
- Layout width: the portrait screen width, at most 480 dp. A layout taller than 16,384 px is drawn at scale 16,384 / height, so the PNG never exceeds that height. Thumbnails (at most 120) are decoded and the PNG (ARGB_8888, quality 100) is encoded on a worker thread; layout and drawing run on the main thread.
- OutOfMemoryError while drawing halves the scale and retries until the output would be narrower than 360 px; after that, or when decoding thumbnails already runs out of memory, a message says "Not enough memory to create the image. Try exporting as Markdown instead" and nothing crashes.
- The PNG goes to `cache/share/3-Stone-AI-yyyyMMdd-HHmmss.png`. Before a new one is written, images older than 24 hours are deleted and only the newest 2 are kept (3 with the new one). An image that is cancelled or whose screen closes before sharing is deleted.
- `ShareFileProvider` (authority `<package>.share`, not exported, `grantUriPermissions`) serves only `cache-path share/`. It is separate from the attachments provider (`.attachments`), whose paths are unchanged. The share intent is `ACTION_SEND` `image/png` with the uri in `EXTRA_STREAM` and `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION`, opened through `Intent.createChooser`.
- While the image is being made, an indefinite snackbar "Creating the image..." offers Cancel. A second request starts nothing new and only brings the progress back if another message replaced it; closing the chat cancels.

### Text

- New strings (`strings_export.xml`) in all 11 resource directories, sorted by name, ASCII punctuation; the v1.7.0 changelog has one plain new-feature line in all 10 languages, and `py .python/generate_markdown.py` regenerated the documents and the README changelog section.

## P7.2 Tests

### Unit tests

- `ConversationMarkdownExportTest` (JVM, 8 tests): headings, metadata, attachment names and escaped plain text; reasoning only when included, with and without a duration and when cut; only the shown versions of a conversation with branched turns; characters, indentation and line breaks of plain text; an unclosed fence closed while other fences stay; notes for cut and empty replies; file names without forbidden characters, with the fallback and the length limit.
- `ChatImageExportPolicyTest` (JVM, 5 tests): scale 1 up to the height limit and proportional above it; the scaled height never above 16,384; the OOM retry halving down to the minimum width; stale shared images (24 hours, at most 3); image names by time.
- `:app:testDebugUnitTest`: 689 tests, all passing (676 after P6, 13 new); `:app:lintDebug`: 0 errors; `ApplicationTextPunctuationTest` passes (part of the unit tests). One earlier run on the loaded host failed `ReusableResourceCacheTest.reacquiringBeforeIdleTimeoutCancelsStaleEviction` (a 100 ms wall clock idle timeout, unchanged since cfa9e4a and unrelated to P7); the rerun of the same tree passed.

### Instrumentation (`ChatExportDeviceTest`, `.python/device_export_flow.py`)

The conversations are written straight into history; no AI request is made. The test restores the remembered conversation with `commit()` (an `apply()` could be lost when the process ends after the last test) and deletes its conversations, downloads and images.

| Test | What it checks |
| --- | --- |
| `markdownHoldsTheShownVersionAndReasoningOnlyWhenChosen` | Toolbar menu, reasoning dialog unchecked by default; the picker request (MIME, suggested name); the file read back by the shell user holds the title, the user role, both attachment names, the code block, the escaped special text and none of the hidden version's texts; fences closed, no CR. Then the drawer menu with reasoning included (quote block). |
| `fiftyMessagesBecomeAnImageAndTheShareSheetOpensWithinThreeSeconds` | 50 messages (one image attachment, code blocks and tables): time from the request to the share sheet window (its own accessibility event time), PNG size within the limit and as reported, the uri of the `.share` authority under `/share/`, ClipData and grant flag; the shell cannot read the uri without a grant and reads the whole PNG with it; Back closes the sheet. |
| `anImageCanBeCancelledAndASecondRequestKeepsTheFirst` | A second request while the first runs starts nothing new; Cancel stops it, no share sheet opens and no file is left. |
| `aConversationStillGeneratingIsNotExported` | While a reply is generating both exports show the refusal; no picker, no chooser. |
| `theShareProviderServesOnlyItsOwnDirectory` | `cache/share` maps to `/share/...`; files in `cache`, `files` and `cache/share-other` are refused. |
| `theSystemPickerSavesAFileAnotherAppCanOpen` (opt-in, emulator only) | The real documents UI saves the export into Download (its Save button pressed through UiAutomation); the resolver and the shell read the same bytes; the file is deleted through the picker's grant. |

#### Emulator (AVD_API_33 read-only instance emulator-5662, x86_64)

`py .python/device_export_flow.py --serial emulator-5662 --install --snapshot before --run --system-picker --compare before` with the debug build of this checkout: 6 / 6 PASS.

| Item | Result |
| --- | --- |
| Image, 50 messages | 1,836 ms from the request to the share sheet being started (thumbnails 35 ms, layout and draw 345 ms, PNG encoding 1,423 ms) |
| Share sheet shown | 2,455 ms after the request (package `android`) |
| Size | 802 x 16,383 px, from a 1,080 x 22,053 px layout (scaled 0.743), 1,574,989 bytes |
| Other app reads the PNG | pass (only with the grant) |
| Markdown | 612 bytes without reasoning, 685 with; MIME `text/markdown`; read by the shell user: pass |
| System picker | `com.google.android.documentsui` saved 312 bytes into Download, MIME `text/markdown`, read by the shell: pass, deleted: pass |
| Leftovers | 0 (no test file in Download, no new image in `cache/share`, no receipt) |
| State compared with before | conversation history, default target, local model catalog, `files/models`, attachment files and all 7 preference files the same |

The emulator runs in software rendering on a shared host (other sessions' emulators were running), so its times vary between runs: earlier runs of the same build measured 1,242 - 1,842 ms for the image and 1,435 - 3,139 ms for the share sheet. PNG encoding of the 13 megapixel image is most of the image time.

The samples (`chat-export-sample.md`, `chat-export-sample-reasoning.md`, `chat-export-sample.png`, made only from the test's own conversations) were checked by eye: the Markdown renders with headings, the attachment list, the closed code fence and the literal special text; the PNG shows the header, the 50 messages with the thumbnail, code blocks with their visible width and the footer.

#### XQ-DQ72 (Sony, Android 13, API 33, arm64-v8a, remote adb)

The maintainer agreed (relayed by the dispatch session) to install the debug build over the installed 346 with data kept. `ADB_SERVER_SOCKET=tcp:<remote adb server> py .python/device_export_flow.py --serial QV770340J7 --snapshot before --install --run --compare before` with versionCode 350 built from 5fd3508: 5 / 5 PASS, the opt-in system picker test skipped by its assumption (it presses system UI and runs on the emulator only), 15.9 s for the instrumentation, about 100 s for the whole flow including both installs. Nothing on the phone was tapped; no AI request was made.

| Item | Result |
| --- | --- |
| Image, 50 messages | 684 ms from the request to the share sheet being started (thumbnails 23 ms, layout and draw 68 ms, PNG encoding 583 ms) |
| Share sheet shown | 849 ms after the request (package `android`), limit 3,000 ms |
| Size | 814 x 16,384 px, from a 1,096 x 22,055 px layout (scaled 0.743), 1,562,046 bytes |
| Other app reads the PNG | pass (the shell user reads the whole file only with the grant) |
| Markdown | 619 bytes without reasoning, 692 with; MIME `text/markdown`; the file in Download read by the shell user: pass |
| Cancel and second request | pass |
| Leftovers | 0 (no test file in Download, `cache/share` empty, no receipt left in the app's external files) |
| State compared with before | conversation history, default target, services, local model catalog, `files/models` (2 entries), attachment files (none) and all 9 preference files the same |
| Phone afterwards | 3-Stone AI debug 350 stays installed (data kept), the test APK is uninstalled |

The share sheet screenshot taken by the test showed the chooser over the chat with the image preview; it was checked locally and not committed, because the sheet lists the phone's direct share targets. The sample PNG pulled from the phone is 814 x 16,384 px.
