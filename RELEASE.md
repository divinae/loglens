# Releasing LogLens

jpackage only produces a native package for the OS it runs on, so each format is built on its
own platform. Linux (`.deb` + portable tarball) is automated in CI; macOS (`.dmg`) and Windows
(`.msi`) are built manually until signing certificates are wired in.

## Prerequisites (all platforms)

- **JDK 21** on `PATH` to bootstrap Gradle (a newer system JDK, e.g. 26, breaks the build). The
  Compose toolchain then auto-provisions JetBrains Runtime 25 for compile/package (Jewel 0.37 /
  IntelliJ 262 requires a Java 25 runtime); the bundled runtime in packages is JBR 25.
- A clean working tree on the commit you intend to tag.

## 1. Pre-release checklist

1. Update [`CHANGELOG.md`](CHANGELOG.md): move items from `Unreleased` into a new
   `## [X.Y.Z] - YYYY-MM-DD` section; refresh the compare links at the bottom.
2. Set the version in [`gradle.properties`](gradle.properties) (`appVersion=X.Y.Z`).
3. `./gradlew :composeApp:jvmTest detekt` is green (CI enforces this on every push/MR).
4. Commit, then tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
   The tag triggers the CI `release-linux` job (`.gitlab-ci.yml`).

## 2. Build artifacts per platform

```shell
# Linux (also run automatically by CI on a v* tag) — needs dpkg + fakeroot on the host
./gradlew -PappVersion=X.Y.Z :composeApp:packageDeb :composeApp:createDistributable

# macOS
./gradlew -PappVersion=X.Y.Z :composeApp:packageDmg

# Windows
.\gradlew.bat -PappVersion=X.Y.Z :composeApp:packageMsi
```

Outputs land under `composeApp/build/compose/binaries/main/`. `createDistributable` writes a
host-agnostic app image under `.../main/app/loglens/` (launcher + bundled JBR) that can be zipped
and run directly — the portability fallback when native packaging is unavailable (e.g. no
`dpkg`/`fakeroot`).

## 3. Code signing & notarization (not yet automated)

Current builds are **unsigned**. Until certificates are configured, users will hit OS gatekeepers:

- **macOS** — Gatekeeper blocks unsigned/unnotarized apps. Workaround for end users:
  `xattr -dr com.apple.quarantine /Applications/loglens.app`, or right-click → Open the first time.
  To fix properly: add an Apple Developer ID signing identity + `notarytool` notarization to the
  macOS build.
- **Windows** — SmartScreen warns on unsigned installers; users click *More info → Run anyway*.
  To fix properly: sign the `.msi` with an Authenticode certificate (`signtool`).
- **Linux** — `.deb` is unsigned, which is normal for out-of-distro packages.

## 4. Publish

Attach the built artifacts (`.deb`, portable `.tar.gz`, `.dmg`, `.msi`) to a GitLab Release for
the tag (https://gitlab.com/divinae/logexp/-/releases). The CI `release-linux` job already
uploads the Linux artifacts as job artifacts under `dist/`.
