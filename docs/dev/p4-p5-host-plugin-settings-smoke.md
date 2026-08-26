# P4/P5 Host-to-Plugin Settings Smoke

Use this smoke after installing matching AutoJs6 and 3-Stone AI builds with `adb install -r`. The overwrite install must preserve existing app-private plugin configuration.

## Functional path

1. Open AutoJs6 **Developer options**.
2. Tap **AI service settings**.
3. Confirm that the foreground component is 3-Stone AI `AppSettingsActivity` and that no install, enable, or update guide is shown.
4. Open **Online services**.
5. Confirm that configured profiles, credential state, network policy, and the default online target remain intact. The page may show `Credential: Configured`, but must never reveal credential bytes.

The host preference must not open a host-owned provider editor. A missing plugin, Android-disabled application, Plugin Center-disabled plugin, or untrusted/missing settings entry must instead select its one corresponding recovery guide.

## Contract checks

The settings entry has exactly one action and the default category:

```text
org.autojs.plugin.AI_PROVIDER_SETTINGS
android.intent.category.DEFAULT
```

It is exported only behind `org.autojs.permission.PLUGIN`. Starting the action from an untrusted shell UID must fail with a permission denial. Starting it with extras, a data URI, or clip data from an authorized caller must be rejected immediately; the contract carries no provider configuration, target data, credential, result payload, or migration material.

## Recorded G8441 result

On 2026/08/26, G8441 / Android 9 passed the following checks with AutoJs6 `6.8.0` build `5276` and 3-Stone AI `1.1.0` build `55`:

- Both arm64 packages were installed with `adb install -r`, preserving application data.
- Android resolved the action to exactly `io.github.supermonster003.autojs6.plugin.threestoneai/.AppSettingsActivity`.
- Tapping the AutoJs6 preference changed the resumed component from `DeveloperOptionsActivity` to the exact plugin settings component.
- **Online services** opened normally and retained the PoloAPI profile, `claude-opus-4-8` default target, configured credential state, and network policy.
- A shell launch was denied because it lacked `org.autojs.permission.PLUGIN`.
- An authorized launch carrying an unexpected extra was immediately finished, leaving the prior plugin page resumed.

Together with the P3 public API smoke, this confirms that the host retains only the trusted settings launcher and plugin-target facade while configuration, credentials, and inference remain inside 3-Stone AI.
