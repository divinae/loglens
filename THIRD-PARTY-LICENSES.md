# Third-party licenses

LogLens is licensed under **GPL-3.0** (see [`LICENSE`](LICENSE)). It builds on and/or
redistributes the following third-party components. All listed licenses are compatible with
GPL-3.0 distribution (Apache-2.0 is one-way compatible with GPLv3; the JetBrains Runtime is
distributed under GPLv2-with-Classpath-Exception, which permits bundling with a GPLv3 app).

## Bundled at runtime (shipped inside packaged builds)

| Component | License | Notes |
|---|---|---|
| JetBrains Runtime (JBR) 21 | GPLv2 + Classpath Exception | Bundled via jpackage; the runtime image ships its own `legal/` license directory. |
| JetBrains Mono (font) | SIL Open Font License 1.1 | Embedded; see `composeApp/src/jvmMain/resources/fonts/OFL.txt`. |

## Compile/runtime dependencies (resolved by Gradle)

| Component | License |
|---|---|
| Kotlin stdlib & compiler | Apache-2.0 |
| Compose Multiplatform | Apache-2.0 |
| JetBrains Jewel (IntelliJ UI) + IntelliJ platform icons | Apache-2.0 |
| kotlinx-coroutines | Apache-2.0 |
| kotlinx-serialization | Apache-2.0 |
| AndroidX Lifecycle (multiplatform) | Apache-2.0 |

## Build-only tools

| Component | License |
|---|---|
| Gradle | Apache-2.0 |
| Compose Hot Reload (Gradle plugin) | Apache-2.0 |
| Detekt (Gradle plugin) | Apache-2.0 |

External tools invoked at runtime (Android `adb`/platform-tools, `xdg-open`, `kdialog`/`zenity`)
are *not* redistributed — they must be present on the host and keep their own licenses.
