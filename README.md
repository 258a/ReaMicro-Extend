# ReaMicro Extend

ReaMicro Extend is an LSPosed module for adding small behavior extensions to ReaMicro.

This repository contains only the open-source module framework and general hooks. Search providers and optional closed features are not included in the public source tree.

## Features

- Reader-related behavior tweaks.
- Settings UI for module switches.
- External source loading through side-loaded `.rmsource` files.
- Optional bundling of local `.rmsource` files at build time.

## Side-Loaded Sources

Association search providers are loaded from external source files instead of being hardcoded in this repository.

At runtime, the module scans the app private source directory:

```text
/data/data/app.zhendong.reamicro/files/reamicro_sources
```

Supported file types:

```text
.rmsource
.apk
.jar
.dex
```

Only loaded sources appear in the settings page. If no source file exists, no external source switch is shown.

Source results must map to one of the known platform names maintained by the module. Unknown platforms are ignored instead of being added dynamically.

## Bundled Local Sources

For private or local builds, `.rmsource` files can be placed in:

```text
source-files/
```

If this directory exists locally, Gradle packages `source-files/*.rmsource` into the APK assets. On app startup, the module copies bundled source files into the app private source directory, then loads them the same way as manually side-loaded files.

`source-files/` is ignored by Git and is not part of the open-source project.

## Open-Source Boundary

The public repository does not include:

- Concrete association search provider implementations.
- Private source files or generated `.rmsource` packages.
- Closed optional features.
- Private server addresses or credentials.

The source-loading framework is public; individual sources can be distributed separately.

## Build

Requirements:

- Android SDK
- JDK 17

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

If `local.properties` is needed, point it to your Android SDK, for example:

```properties
sdk.dir=C:/Users/<name>/AppData/Local/Android/Sdk
```

## License

No license has been declared yet. Do not assume redistribution rights until a license is added.
