# lnreader

Kotlin Multiplatform proof-of-concept for running LNReader JavaScript plugins
outside the original Android host, with:

- a shared `composeApp` module containing the Compose UI, service layer, and
  QuickJS-based plugin runtime
- a thin `desktopApp` JVM launcher for Linux desktop
- a thin `androidApp` launcher for Android

The desktop and Android apps now use the same LNReader plugin bridge code. The
previous GraalJS runtime has been replaced with
[`io.github.dokar3:quickjs-kt`](https://github.com/dokar3/quickjs-kt), which
works on both JVM and Android.

## Module layout

```text
composeApp/
  src/commonMain/kotlin/lnreader/
    platform/   expect declarations for IO / JSON / HTML / JS thread setup
    runtime/    shared QuickJS bootstrap + plugin loader
    service/    shared LNReaderService + models
    ui/         shared Compose UI
  src/jvmCommonMain/kotlin/lnreader/platform/
    Platform.jvmCommon.kt  actuals using OkHttp, Gson, Jsoup, JVM resources
  src/commonMain/resources/
    cheerio-bundle.cjs

desktopApp/
  src/main/kotlin/lnreader/desktop/Main.kt
  src/main/kotlin/lnreader/cli/Cli.kt

androidApp/
  src/main/kotlin/com/lnreader/android/MainActivity.kt
  src/main/AndroidManifest.xml
```

## What changed from the old single-module build

- Root `src/main/kotlin` was removed.
- Root `application` setup was moved into `desktopApp`.
- Shared code moved into `composeApp`.
- Android support was added through `androidApp`.
- GraalJS was removed entirely; QuickJS now powers the shared runtime.

## Runtime notes

The JavaScript bootstrap still provides the same LNReader-oriented shims:

- `fetch()` backed by OkHttp
- `console`
- `Buffer`
- `URL` / `URLSearchParams`
- CommonJS-style `require()`
- `cheerio` / `htmlparser2` from the bundled `cheerio-bundle.cjs`

QuickJS differences vs. the old GraalJS port:

- no host `Context` / `Value` API: plugin calls are now made by evaluating JS
  snippets and serializing results with `JSON.stringify(...)`
- no manual `awaitJsPromise(...)`: `quickjs-kt` suspend `evaluate<T>()`
  already waits for async JS / Promise completion
- host bindings are defined with the DSL (`define`, `function`,
  `asyncFunction`) instead of Graal `HostAccess` / `ProxyExecutable`

## Run

### Desktop UI

```bash
./gradlew :desktopApp:run
```

### Desktop smoke test through the same run task

```bash
./gradlew :desktopApp:run --args="--smoke-test"
```

This exercises: manifest fetch → plugin load → `popularNovels()` →
`parseNovel()` → `parseChapter()`.

### CLI

```bash
./gradlew :desktopApp:runCli -Pargs="https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json allnovel"
```

Optional third and fourth arguments override the novel path and chapter path:

```bash
./gradlew :desktopApp:runCli -Pargs="<manifest-url> <plugin-id> <novel-path> <chapter-path>"
```

### Android

Build the debug APK:

```bash
./gradlew :androidApp:assembleDebug
```

Install it on a connected device:

```bash
./gradlew :androidApp:installDebug
```

## Android SDK notes

- `local.properties` should point to your SDK root (`sdk.dir=...`)
- this project currently compiles with `compileSdk = 36` and `targetSdk = 34`
  because current Compose Android artifacts and `quickjs-kt-android` require a
  newer compile SDK than 34
- `minSdk = 24`

## Validation

Validated locally:

- `:composeApp:compileKotlinDesktop`
- `:desktopApp:run --args="--smoke-test"`
- `:desktopApp:runCli`
- `:androidApp:assembleDebug`

The desktop smoke/CLI run succeeded end-to-end against the real LNReader plugin
manifest and plugin `allnovel`.
