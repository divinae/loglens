# LogLens

Desktop viewer for Android logs (`adb logcat`), built with Kotlin Multiplatform + Compose for Desktop and the JetBrains Jewel (IntelliJ) UI style. Streams live logcat from a connected device, filters, highlights and searches logs, and captures device screenshots and screen recordings.

Inspired by [Android Studio's Logcat](https://developer.android.com/studio/debug/logcat) and [AndLogView](https://github.com/mlopatkin/andlogview).

## Features

- **Live logcat** — stream from the selected device, with automatic device detection and switching.
- **Logcat buffers** — pick `adb logcat -b` buffers (main, system, crash, radio, events, kernel).
- **Columns** — Index, Date, Time, PID, TID, App, Level, Tag, Message; toggleable and resizable (drag).
- **Filters** — SHOW / HIDE / HIGHLIGHT / SHOW_AND_HIGHLIGHT rules per field (message, tag, app, pid, level) with regex and case-sensitive options; colored highlighting; reordering; persisted to disk.
- **Quick filter** — a small query language in the quick-filter bar:
  - `foobar` — substring over the whole line (case-insensitive)
  - `"two words"` — quoted phrase
  - `tag:Foo`, `package:com.foo` (alias `app`/`pkg`), `msg:timeout` (alias `message`)
  - `pid:1234`, `tid:42` — exact numeric match
  - `level:E`, `level>=W` — ranking V<D<I<W<E<F (A==F)
  - `tag:/Foo.*Bar/` — regex on the field
  - `-tag:Spam`, `-foobar` — negation
  - terms are implicitly AND-ed; recent queries kept in history
- **Find bar** — in-view search with match navigation (F3 / Shift+F3).
- **Bookmarks** — pin lines, next/prev navigation, optional always-visible-despite-filters.
- **Selection** — single, range (Shift), drag; copy lines or a single column.
- **Screenshot** — capture the device to a file + clipboard, optionally open in the default viewer.
- **Screen recording** — `screenrecord` with Android Studio-style options (show taps, bitrate, size); auto-open when finished.
- **Open from file** — parse saved logs (`-v threadtime`), Android Studio `.logcat` exports (JSON), and full Android **bugreports / `dumpstate`** dumps — both the flat `.txt` and the `adb bugreport` **`.zip`** (the embedded `bugreport-*.txt` is read straight from the archive). Extracts every embedded logcat section and mines the `ps` section for pid→process names. Multiline messages (e.g. Java stack traces) are folded back into a single record.
- **Save logs** — all or filtered, as plain text or `.logcat` format.
- **Appearance** — System/Light/Dark theme, font size/family/weight (system fonts), soft wrap. HiDPI-aware (Compose renders at native display scale; the app/window icon ships at 256px so it stays crisp).
- Configuration and filters persist across launches.

## Keyboard shortcuts

| Key | Action |
|---|---|
| `Ctrl/Cmd + F` | Find bar / focus search |
| `Ctrl/Cmd + C` | Copy selected lines |
| `Ctrl/Cmd + K` | Clear logs |
| `Ctrl/Cmd + R` | Restart capture (clear + resume) |
| `Esc` | Close find bar |
| `↑ / ↓` | Move selection up / down |
| `B` | Toggle bookmark on selection |
| `F3 / Shift + F3` | Next / previous match or bookmark |

## Requirements

- **JDK 21** to bootstrap Gradle — point `JAVA_HOME` at java-21 (a newer system JDK, e.g. 26, fails). The build toolchain itself is **JetBrains Runtime (JBR) 21**, auto-provisioned by the foojay resolver; it's used for compile, `run`, and the runtime bundled into packages — you don't install JBR yourself.
- **adb** on `PATH` (Android platform-tools) — or set the binary path in the app settings.
- Linux/macOS/Windows (desktop JVM). Screenshots/recordings open the file via `xdg-open` (Linux).

### Wayland

On Linux/Wayland the app runs through **XWayland** (the X11 compatibility layer), not as a native Wayland client. Native Wayland would require selecting the JetBrains Runtime's `WLToolkit` (`-Dawt.toolkit.name=WLToolkit`), but Compose/Skiko currently can't lock the drawing surface under that toolkit and the window fails to create ([JetBrains/skiko#890](https://github.com/JetBrains/skiko/issues/890)). The runtime ships JBR (so `WLToolkit` is already bundled), so re-enabling it once that upstream issue is fixed is a one-flag change — see the reverted attempt in git history (commit `d82d748`, reverted in `c760073`).

## Build and run

```shell
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

Native packages (Deb / Msi / Dmg) with a **bundled JBR runtime** (no JDK needed on the target host) and per-OS icons:

```shell
./gradlew :composeApp:packageDistributionForCurrentOS
```

jpackage only builds for the current OS, so build each format on its native platform. The `.deb` step also needs `dpkg` and `fakeroot` on the build host (absent on Arch by default → `Invalid or unsupported type: [deb]`). For a host-agnostic portable build, use `createDistributable` instead — it writes a self-contained app image (launcher + bundled JBR) under `composeApp/build/compose/binaries/main/app/` that you can archive and run directly.

Icons live in `composeApp/icons/` (`loglens.svg` source → `.png`/`.ico`/`.icns`).

## Configuration

- Settings: `~/.config/loglens/config.json`
- Filters and search history: stored alongside the config.

## Structure

Kotlin Multiplatform targeting Desktop (JVM). Code lives in `composeApp/src/jvmMain/kotlin/com/dv/loglens`:

- `App.kt`, `main.kt` — UI root and entry point.
- `viewmodel/` — `MainViewModel` (capture, incremental filtering, state).
- `model/` — `LogEntry`, `FilterRule`, `LogQuery`, `LogBuffer`, `LogColumn`, `UserConfig`.
- `service/` — `LogcatService`, `DeviceService`, `PackageService`, `ScreenshotService`, `ScreenRecordingService`, `Adb`.
- `parser/` — `LogParser`, `AndroidStudioLogParser`, `BugreportParser`, `LogcatExporter`.
- `ui/` — bars, dialogs, log rows, shortcuts.
- `config/` — `ConfigManager`, `FilterStore`, `SearchHistoryStore`.
