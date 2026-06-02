# Changelog

All notable changes to LogLens are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Dependency bump to the 2026.2 line: Compose Multiplatform 1.10.3 → 1.11.1,
  Jewel 0.35.0-261 → 0.37.0-262, IntelliJ icons 261 → 262.4852.74, kotlinx-coroutines 1.10.2 → 1.11.0,
  Detekt 1.23.7 → 1.23.8, Compose Hot Reload 1.1.0 → 1.1.1.
- Bundled runtime / build toolchain bumped JBR 21 → JBR 25 (required by Jewel 0.37 / IntelliJ 262,
  which ships Java 25 class files). Gradle still bootstraps on JDK 21; app code targets Java 21 bytecode.

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
- Native packages (`.dmg`/`.msi`/`.deb`) with a bundled JetBrains Runtime — no host JDK required.

[Unreleased]: https://gitlab.com/divinae/logexp/-/compare/v1.0.0...main
[1.0.0]: https://gitlab.com/divinae/logexp/-/tags/v1.0.0
