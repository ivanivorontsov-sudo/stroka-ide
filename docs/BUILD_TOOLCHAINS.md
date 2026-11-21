# Build toolchains and verification

This repository's Android APK is the editor application itself. It is **not** proof that the editor can compile arbitrary user projects. The workflow `.github/workflows/toolchain-smoke.yml` independently builds a clean sample Android app and a clean Windows executable on native GitHub-hosted runners.

## Android sample build

The smoke job uses JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.9 and Android Gradle Plugin 8.7.3. The fixture declares:
- `minSdk 23` — install floor;
- `targetSdk 35` — runtime compatibility behavior;
- `compileSdk 35` — APIs available at compile time.

It runs `assembleDebug`, verifies the APK signature with `apksigner`, checks package metadata with `aapt`, and publishes the APK as a short-lived CI artifact. Debug signing is appropriate for this disposable test fixture only. Production apps need a user-controlled release key; never embed a signing key in source or a public repository.

## Windows sample build

The Windows job uses the native `windows-latest` runner and .NET 8 SDK. It runs `dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true`, checks the PE `MZ` header, then executes the resulting EXE and validates its output. This tests a real Windows binary, not a Linux-hosted cross-compile pretending to be Windows.

A Windows build is OS- and architecture-specific. Additional targets such as `win-arm64` require separate publish jobs and tests. Self-contained EXEs are larger because they include the runtime.

## Embedded local build backend

The editor now has a no-shell local backend for static websites. It classifies a project from its files, packages the site with Android's `ZipOutputStream`, writes the ZIP to `Download/StrokaBuild/stroka-website-build.zip`, and never invokes Termux, Node.js, or a cloud service. The website starter and visual builder provide a responsive multi-section design; each major section is independently selectable, editable, reorderable, and removable before export.

The local backend intentionally does not claim to compile arbitrary Android Gradle projects. Android APK/AAB builds still use the existing Termux toolchain (or GitHub Actions), and Python / «Понятный» app packaging uses the existing tested build script. Windows/Linux targets remain on GitHub-hosted native runners. The default target is Auto: it selects the embedded website packager for a static site and routes other project kinds to their existing supported backend. Termux and GitHub paths are retained.

A general Android-on-Android compiler needs an Android-host ARM64 `aapt2`, a Java/Kotlin compiler, D8, zipalign, apksig, SDK platform artifacts, dependency resolution and a Gradle/AGP compatibility layer. A Linux-host `aapt2` downloaded from the desktop SDK cannot simply execute on Android. Until a complete, license-compatible toolchain is bundled or fetched and verified, the app must not label a ZIP as an APK or report an unverified build as successful.

## What the smoke test does not claim

These CI jobs validate that the pinned toolchains work and produce valid sample artifacts. They do not yet turn this Android editor into a general-purpose remote build service. A production in-app build feature still needs a project-export contract, dependency/SDK selection, a secure remote-build authentication flow, build-log streaming, artifact download, and user-managed signing credentials. Arbitrary Android projects also need Gradle plugin and SDK compatibility checks; arbitrary Windows projects need an identified project type and target runtime.

## References

- Android command-line builds and signing: https://developer.android.com/build/building-cmdline
- Android APK signature verification: https://developer.android.com/tools/apksigner
- Android SDK platform and target behavior: https://developer.android.com/guide/topics/manifest/uses-sdk
- .NET deployment overview: https://learn.microsoft.com/en-us/dotnet/core/deploying/
- .NET single-file deployment: https://learn.microsoft.com/en-us/dotnet/core/deploying/single-file/overview
- GitHub-hosted runner images: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- Worked examples in programming education: https://doi.org/10.1145/3560266
- Scaffolded self-explanation experiment (90 students): https://doi.org/10.1145/3605098.3636007
