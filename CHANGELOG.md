# Changelog

All notable changes to LogLens are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Mirror device** — a toolbar button opens scrcpy in its own window for a live, interactive view
  of the device (requires scrcpy on `PATH`). scrcpy is a separate native app and can't be embedded
  into the LogLens window; LogLens passes its configured adb to scrcpy via the `ADB` env var.
- **Δt column** — an optional, toggleable column showing the elapsed time since the previous
  visible row (`+123ms` / `+1.45s`), for spotting stalls between consecutive logs.

### Changed
- Process lifecycle banners now come from the adb server's `track-jdwp` stream (the Android
  Studio mechanism), so a debugged app's restart banners appear immediately instead of on the
  next `ps` poll; the poll remains as a catch-all for non-debuggable processes. Banners carry
  the process's pid and package, so `package:`/`pid:` filters keep them visible, and a just
  started process's name is polled briefly so the banner never shows a placeholder.

## [1.1.0] - 2026-06-03

### Added
- **Bookmark navigator** — a floating pill below the crash navigator steps between bookmarked
  lines (prev/next with a position counter), same as F3 / Shift+F3.
- **Query language extensions** — `is:crash` / `is:stacktrace` record classes, `age:30s|5m|2h|1d`
  relative-time filter, and per-field regex via `tag~:Foo.*` (alongside the existing `tag:/…/`).
- **Filter syntax help** — `?` button in the quick-filter bar (or F1 in the field) opens a
  reference of the full query grammar.
- **Pause without stopping** — the capture toggle now pauses the display while the adb stream
  keeps buffering; resuming flushes the backlog with no gap (a stopped stream re-reads the
  device ring buffer instead).
- **Process lifecycle banners** — Android-Studio-style `PROCESS STARTED (pid) for package …` /
  `PROCESS ENDED` lines, scoped to processes that logged in the current session.
- **Auto-reconnect** — when the captured device drops and returns, capture resumes from the last
  seen line (`logcat -T`) without clearing logs or duplicating them; `DEVICE DISCONNECTED` /
  `DEVICE RECONNECTED` banners mark the gap.
- **Clickable URLs** — http/https links in the message column are underlined and open in the
  system browser (with an `xdg-open` fallback on Linux, where AWT's BROWSE action is typically
  unavailable; this also fixes the About dialog link there).
- **Expand multiline logs by default** — new setting; foldable multiline records start expanded
  instead of collapsed. Per-row expand/collapse still works in both modes.

### Changed
- Multiline `Log` calls (e.g. HTTP client dumps) are folded into one record the way Android
  Studio groups them: logcat gives every embedded line an identical header down to the
  millisecond, so an exact header match joins the preceding record.
- Collapsed multiline records preview the whole message flattened into one ellipsized line
  instead of just the first line, so fields past the first newline (e.g. a request URL) stay
  visible and clickable without expanding.

### Fixed
- Rows hidden by a filter change are also dropped from the selection, so shortcuts (B, copy,
  arrow navigation) no longer act on lines that are no longer visible.

## [1.0.1] - 2026-06-03

### Fixed
- Clearing logs during live capture no longer stops listening: `adb logcat -c` killed the
  concurrently running logcat reader, so the capture is now stopped, the buffers cleared, and
  the stream resumed automatically.
- Typing in the filter or find field no longer triggers plain-key shortcuts — e.g. a `b` no
  longer toggles a bookmark on the selected line, and ↑/↓ no longer move the log selection
  (the field consumed the typed character but the key event still bubbled to the global handler).
- Release pipeline now uploads the Linux artifacts (`.deb`, portable `.tar.gz`) to the GitLab
  Package Registry and creates the GitLab Release with asset links automatically.

### Changed
- Clear logs is now Ctrl/Cmd+R (was Ctrl/Cmd+K). The separate restart-capture shortcut was
  removed — clearing resumes capture by itself.

## [1.0.0] - 2026-06-02

First public release.

### Added
- **Live logcat** streaming from the selected device, with automatic device detection and switching.
- **Logcat buffers** — selectable `adb logcat -b` buffers (main, system, crash, radio, events, kernel).
- **Columns** — Index, Date, Time, PID, TID, App, Level, Tag, Message; toggleable and resizable.
- **Filters** — SHOW / HIDE / HIGHLIGHT / SHOW_AND_HIGHLIGHT rules per field (message, tag, app, pid,
  level), regex and case-sensitive options, colored highlighting, reordering, persisted to disk.
- **Quick filter** query language (`tag:`, `package:`, `msg:`, `pid:`, `tid:`, `level:`/`level>=`,
  `/regex/`, quoted phrases, `-` negation, implicit AND) with search history.
- **Find bar** — in-view search with match navigation (F3 / Shift+F3).
- **Bookmarks** — pin lines, next/prev navigation, optional always-visible-despite-filters.
- **Crash/ANR detection** and navigation (Java exceptions, ANR, native fatal signals).
- **Open from file** — `-v threadtime` logs, Android Studio `.logcat` JSON exports, and full Android
  bugreports / `dumpstate` dumps (flat `.txt` and `adb bugreport` `.zip`), with pid→process mapping.
- **Save logs** — all or filtered, as plain text or `.logcat` format.
- **Screenshot** capture (to file + clipboard, optional auto-open).
- **Screen recording** via `screenrecord` (show taps, bitrate, size; auto-open when finished).
- **Appearance** — System/Light/Dark theme, font size/family/weight, soft wrap; HiDPI-aware icon.
- Multiline folding of stack traces and repeat-count coalescing.
- Persisted configuration, filters, search history, window geometry, and recent files.
- Native packages (`.dmg`/`.msi`/`.deb`) with a bundled JetBrains Runtime 25 — no host JDK required.

### Build
- Built with Kotlin 2.3.21, Compose Multiplatform 1.11.1, Jewel 0.37.0-262, kotlinx-coroutines 1.11.0.
  Build/runtime toolchain is JetBrains Runtime 25 (Jewel 0.37 / IntelliJ 262 ships Java 25 class files);
  Gradle still bootstraps on JDK 21 and our own code targets Java 21 bytecode.

[Unreleased]: https://gitlab.com/divinae/loglens/-/compare/v1.1.0...main
[1.1.0]: https://gitlab.com/divinae/loglens/-/compare/v1.0.1...v1.1.0
[1.0.1]: https://gitlab.com/divinae/loglens/-/compare/v1.0.0...v1.0.1
[1.0.0]: https://gitlab.com/divinae/loglens/-/tags/v1.0.0
