# lnreader

Kotlin Multiplatform proof-of-concept for running LNReader JavaScript plugins
outside the original Android host, with:

- a shared `composeApp` module containing the Compose UI, service layer,
  SQLDelight persistence, and QuickJS-based plugin runtime
- a thin `desktopApp` JVM launcher for Linux desktop
- a thin `androidApp` launcher for Android

The desktop and Android apps use the same LNReader plugin bridge code and the
same shared Compose UI. The app now follows a Tachiyomi/Aniyomi-inspired layout
with bottom navigation, a dark Material 3 theme, cover grids, a local Library,
read History, a manual Updates checker, and an Extensions manager for plugin
repositories plus installed sources.

## Module layout

```text
composeApp/
  src/commonMain/kotlin/lnreader/
    data/       SQLDelight database, repositories, update checker
    platform/   expect declarations for IO / JSON / HTML / JS thread setup
    runtime/    shared QuickJS bootstrap + plugin loader
    service/    shared LNReaderService + models
    ui/         shared Material 3 UI + tab navigation
  src/commonMain/sqldelight/lnreader/data/db/
    LibraryHistory.sq
    1.sqm
  src/jvmCommonMain/kotlin/lnreader/platform/
    Platform.jvmCommon.kt
  src/desktopMain/kotlin/lnreader/
    data/       desktop SQLDelight driver actual
    platform/   desktop IO actual
  src/androidMain/kotlin/lnreader/
    data/       Android SQLDelight driver actual
    platform/   Android IO actual
  src/commonMain/resources/
    cheerio-bundle.cjs

desktopApp/
  src/main/kotlin/lnreader/desktop/Main.kt
  src/main/kotlin/lnreader/cli/Cli.kt

androidApp/
  src/main/kotlin/com/lnreader/android/MainActivity.kt
  src/main/AndroidManifest.xml
```

## UI structure

The shared app now has five bottom tabs:

- **Library** — cover grid of locally saved novels backed by SQLDelight
- **Updates** — manual `Check now` flow that re-fetches each library novel,
  compares chapter counts, lists titles with new chapters, then updates the
  stored known count
- **History** — reverse-chronological list of opened chapters; tapping an item
  reopens that chapter directly
- **Browse** — installed-plugin picker + popular feed grid + novel details +
  chapter reader. Install a plugin from Extensions first.
- **Extensions** — repository management plus a combined install/uninstall list
  for all plugins fetched from the user's configured manifest URLs

Novel details include a bookmark toggle that adds/removes the title from the
local Library. Opening a chapter from Browse, Library, or History writes a
history row.

## Data layer

The persistence layer lives under `lnreader.data` and is shared by Android and
Desktop:

- `AppDatabase` wires SQLDelight and exposes repositories
- `DatabaseDriverFactory` provides platform-specific drivers
- `RepositoryRepository` stores user-configured plugin manifest URLs and seeds
  the default LNReader repository on first run only
- `InstalledPluginRepository` stores installed-plugin metadata only; plugin JS
  is still fetched fresh from the stored `url` whenever `LNReaderService`
  loads that plugin
- `LibraryRepository` manages saved novels and known chapter counts
- `HistoryRepository` stores opened chapters in reverse chronological order
- `UpdatesChecker` performs the manual updates scan using installed plugin
  metadata plus `LNReaderService`

Schema tables:

- `Repository(url, name, addedAt)`
- `InstalledPlugin(pluginId, name, site, lang, version, url, iconUrl, repoUrl, installedAt)`
- `LibraryNovel(pluginId, novelPath, name, cover, addedAt, knownChapterCount)`
- `HistoryEntry(pluginId, novelPath, novelName, chapterPath, chapterName, readAt)`

Desktop stores the database at `~/.lnreader/lnreader.db`. Android uses the app's
normal internal database directory.

## Dependencies added

- **Material 3** via `compose.material3`
- **SQLDelight** (`app.cash.sqldelight`) with:
  - `runtime`
  - `coroutines-extensions`
  - `android-driver`
  - `sqlite-driver`
- **Coil 3** for multiplatform cover loading:
  - `io.coil-kt.coil3:coil-compose`
  - `io.coil-kt.coil3:coil-network-okhttp`
- **kotlinx-datetime** for history/update timestamps

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

### Desktop smoke test

```bash
./gradlew :desktopApp:run --args="--smoke-test"
```

The smoke path exercises the real manifest/plugin flow and now also verifies:

- repository seeding / persistence
- installed-plugin persistence
- SQLDelight database creation
- Library insert/remove
- History insert/upsert
- manual Updates check execution
- plugin fetch + `popularNovels()` + `parseNovel()` + `parseChapter()`

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
ANDROID_HOME=$HOME/android-sdk ./gradlew :androidApp:assembleDebug
```

Install it on a connected device:

```bash
ANDROID_HOME=$HOME/android-sdk ./gradlew :androidApp:installDebug
```

## Android SDK notes

- `local.properties` should point to your SDK root (`sdk.dir=...`)
- this project currently compiles with `compileSdk = 36` and `targetSdk = 34`
  because current Compose Android artifacts and `quickjs-kt-android` require a
  newer compile SDK than 34
- `minSdk = 24`

## Validation

Validated locally for this redesign with:

- `:composeApp:compileKotlinDesktop`
- `:desktopApp:run --args="--smoke-test"`
- `:androidApp:assembleDebug`
