# AutoJs6-Plugin-Three-Stone-AI engineering rules

These repository-specific rules apply the AutoJs6 new plugin repository reference dated 2026-09-13. Preserve deeper AGENTS.md constraints when working on vendored code.

- Start with git status, branch, recent commits and relevant diffs. Existing uncommitted work belongs to the user; never reset or silently include it in your commits.
- Inspect, validate and commit each complete change with Conventional Commits unless the user requests otherwise. Before each commit set VERSION_BUILD to the reachable HEAD commit count plus one. Verify equality after committing. Do not auto-increment it during assembly.
- VERSION_NAME follows semantic versioning. Update every current-version changelog JSON and generated document when changing behavior. Never claim an unexecuted device test or an unpublished candidate is released.
- Resolve the platform and native alignment plugins 1.8.3 from public repositories in root settings. The platform plugin must run before build-logic. Do not use Maven local, consumer gradle/data overrides or sibling build substitutions.
- Read Android/Kotlin plugin versions from platform system properties. Keep SDK and application versions in version.properties and preserve signing logic. Java/Kotlin source encoding is UTF-8.
- Builds must be self-contained. Keep local API artifacts with their source and checksum, or use controlled source modules. Do not read sibling project JAR/AAR files at build time.
- sign.properties, local.properties, keystores and migration backups are ignored local files. Never log or commit their secrets. appendDigestToReleasedFiles must assemble and validate the exact signed output set, actual versions and CRC32 before collecting a release.
- Preserve protected Wake metadata, NoDisplay activity, WAKE action and DEFAULT category. Activation does no model loading or networking. Test the Manifest contract and actual service discovery/Binder paths.
- PluginInfo uses installed package versions, localized description, stable identity and explicit true ABI capabilities. Preserve published AIDL order and negotiate additions. Bound inputs, resource ownership and cancellation remain part of the contract.
- Application titles stay English and nontranslatable. Keep all ten locales plus explicit English, sort strings by name, put plurals/arrays in separate files, and use ASCII punctuation and escaped Android quotes.
- Maintain the existing purpose-specific PNG at app/src/main/res/mipmap/ic_launcher.png. README references must resolve to actual assets and the correct repository.
- Edit .readme/.changelog JSON and templates, then run the generator and its true read-only --check mode. Root README is Simplified Chinese. Generated changelogs belong under app/src/main/assets/doc, not .changelog.
- Preserve settings/local release-history behavior, fallback languages, accessibility and failure recovery. Keep networking and data permissions accurate in user documentation.
- Native capabilities require ELF/ZIP alignment checks and real 16 KB execution evidence. Static checks do not substitute for device tests. Record unavailable OEM activation and device matrix coverage explicitly.

## Launcher icon

- The source is `.python/icons/three-stone-original.png` (432 x 432, preserved unchanged from the existing transparent day icon). Use its alpha as the common shape for all variants; normalize glyph colors to `#272727` for light application themes and `#D8D8D8` for dark themes. Preserve the source artwork and regenerate, never hand-edit output PNGs.
- `py .python/generate_launcher_icons.py` deterministically generates the transparent UI/Plugin Center PNGs, adaptive/legacy launcher resources, background colors and resource-shrinking keep rule. `--check` is read-only and rejects stale outputs and obsolete colliding resources. UI/adaptive sizes follow Optical geometry v1; final nonzero alpha is checked against the safe circle.
- `mipmap/ic_launcher.png` and `mipmap-night/ic_launcher.png` are transparent UI/README assets on every Android version. Never add adaptive XML with this resource name. In-app references use `R.mipmap.ic_launcher`; the host plugin center resolves this resource under its own UI configuration.
- The Manifest's `icon` and `roundIcon` both use `@mipmap/ic_launcher_system`. It is an adaptive icon on API 26+ with `ic_launcher_system_foreground` and a single black `ic_launcher_monochrome`, or a filled circular PNG on older APIs. The default dark resource always uses glyph `#D8D8D8` on background `#212121`. Explicit light uses `ic_launcher_system_light` with glyph `#272727` on `#FAFAFA`. Automatic mode uses a real `ic_launcher_system_auto` resource, with default dark and notnight light bitmap XML wrappers and matching anydpi-v26 / notnight-anydpi-v26 adaptive XML. Never use a values resource alias in a Manifest icon: PackageManager eagerly resolves it and freezes the install-time theme. Tests must check the parsed ActivityInfo icon IDs as well as direct resource rendering. Application UI assets still follow the app theme. System themed icons may be recolored by the launcher.
- Do not put a filled background in the adaptive foreground, use inset drawables, disable the stable real Activity, or clear launcher data. Resource-only `LauncherIconResourceTest` checks bitmap transparency, exact glyph colors, adaptive/legacy selection, dark default fallback, and Manifest wiring without changing user preferences. Actual launcher rendering still requires device inspection.
- Settings offer adaptive light, adaptive dark, adaptive automatic (default), and transparent background. Four stable `.launcher.{AdaptiveLight,AdaptiveDark,AdaptiveAuto,Transparent}IconAlias` components point to the unchanged real Activity; only one is enabled after switching. PackageManager persists the choice. Use `DONT_KILL_APP`, enable the target before migrating mutable shortcut ownership and disabling old aliases, and roll back on failure. Automatic and transparent modes explain launcher caching, theme and masking limitations. Transparent mode uses the original `ic_launcher` resource.
- The workspace reference `AUTOJS6_PLUGIN_BLACK_N_WHITE_ADAPTIVE_ICON_AGENTS.md` documents the current scheme and the platform limitations; the former shared `ic_launcher` adaptive/transparent naming and backgrounds are obsolete.

## Validation

```powershell
py .python/generate_markdown.py
py .python/generate_markdown.py --check
py .python/check_repository.py --pending-commit
.\gradlew.bat --no-daemon '-Djava.vendor=Eclipse Adoptium' '-Djava.vendor.version=Temurin-21.0.12.1+1' :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:appendDigestToReleasedFiles
git diff --check
```

Run the relevant custom Python regression suites after changing their logic. After committing, run check_repository.py without --pending-commit and review git status. Install/activate/upgrade and Binder smoke tests on the exact signed release remain necessary evidence for an actual release.

## Unified standalone settings (2026-09-29)

The maintainer confirmed `D:/idea-projects/AUTOJS6_PLUGIN_STANDALONE_SETTINGS_AGENTS.md` for all standalone plugin settings. Follow that shared specification for appearance row order, flat groups, 16sp titles / 14sp summaries, 24dp padding, common icons, centered 24dp dialogs with fixed Cancel/OK actions, the shared palette and local-only HEX/RGB preview. Language, night mode and theme default to following AutoJs6; absent-host theme falls back to #FFDEAD. Neutral surfaces must not be tinted by the selected seed. Launcher default is AUTO, not DARK; preserve explicit PackageManager selections during normalization and repair mixed states via the update receiver / Activity startup without a duplicate mode preference. Cancel/back/outside dismissal never saves a draft.

## Optical icon standard (2026-10-03)

- Follow `../AUTOJS6_PLUGIN_BLACK_N_WHITE_ADAPTIVE_ICON_AGENTS.md` for every standalone plugin, including the Plugin Center. `.python/icon_geometry.py` v1 is a self-contained copy of the common geometry algorithm; keep its implementation identical across the standalone plugins. Never read sibling checkouts during a build.
- Derive size from the equal-weight combination of visible bounding-box area (alpha >= 16) and alpha-weighted ink area. Target visible size is 0.52 of the canvas, with only documented optical corrections in 0.94-1.06. The adaptive ratio is always the UI ratio multiplied by 72/108. This supersedes older hardcoded UI/adaptive widths in historical notes. Preserve aspect ratio, optical placement and final nonzero-alpha safety checks.
- Current derived widths: UI 0.5994, adaptive 0.3996 (rounded documentation values, not generation constants). Optical scale=1.00 and zero offsets.
- Generate `mipmap/ic_plugin_center.png` and its night counterpart from the same geometry as the transparent UI/launcher mode. They are transparent neutral artwork for installed and catalog entries, independent of the active launcher alias. Keep them through `raw/keep_plugin_center_icon.xml`. Existing separate brand assets retain their original purpose.
- Black, white and neutral grayscale are allowed for every plugin without per-plugin approval. Pure silhouettes default to #272727 / #D8D8D8; shaded artwork may preserve meaningful tonal details with R=G=B and matching day/night alpha. Stamp Mail is one example, not an exception. Keep light-theme artwork dark enough and dark-theme artwork light enough to remain legible. Do not introduce a filled background into the Plugin Center assets.
- Run the icon generator and its read-only `--check`, `.python/tests/test_icon_geometry.py`, existing icon regressions, and review the full set at 36/48/64 px in both themes and in launcher masks. `.github/workflows/icons.yml` verifies Windows/Linux reproducibility. Synthetic previews do not replace actual launcher verification.
