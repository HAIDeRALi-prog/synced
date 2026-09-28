# Plan: "Synced" — Cloud-Synced Content Platform (Native Android, Kotlin)

Build target per `android-content-platform-prompt.md`, with these confirmed decisions:
- **API:** JSONPlaceholder (`https://jsonplaceholder.typicode.com/`) — `/posts`, `/posts/{id}`, `/comments?postId=`, `/users/{id}`
- **UI:** Jetpack Compose + Material 3 · **JSON:** Moshi (codegen) · **App name:** Synced (`com.example.synced`)
- **Structure:** single Gradle module, feature packages · **No Paging 3** (API returns all 100 posts regardless)

## Environment constraints (verified on this machine)

| Item | Found | Implication |
|---|---|---|
| Android SDK | platforms up to `android-36`, build-tools 36.0.0 | `compileSdk 36`, `targetSdk 36`, `minSdk 26` |
| Gradle | cached dist `8.14.3` (no `gradle` on PATH) | use wrapper pinned to **8.14.3**, generate it from the cached dist |
| AGP | cached artifacts up to **8.13.1** | AGP **8.13.1** (AGP 9 needs SDK 37 — not installed) |
| Kotlin | cached up to **2.2.21** | Kotlin **2.2.21** |
| JDK | PATH has only Java 8; JDK **23** at `C:/Users/hs538/.jdks/openjdk-23` | set `org.gradle.java.home` in `gradle.properties` |
| Compose | compose 1.12+ requires compileSdk 37 + AGP 9 | must pin **compose 1.11.x** (BOM ~2026.06.01); fall back to explicit `material3 1.3.2` + `compose-ui 1.11.x` if the BOM's material3 demands SDK 37 |

Phase 0 is a smoke test specifically to validate this toolchain before any feature code is written.

## Version catalog (initial targets; verify at Phase 0)

- AGP 8.13.1 · Gradle 8.14.3 · Kotlin 2.2.21 · KSP `2.2.21-x` (check Maven Central for exact match; this drives Room/Hilt/Moshi processors)
- Compose BOM 2026.06.01 (compose 1.11.x) + `material3`, `ui-tooling-preview`, `foundation`
- `androidx.activity:activity-compose:1.13.0`, `lifecycle-viewmodel-compose:2.11.0`, `navigation-compose:2.10.1`, `core-ktx:1.18.0`
- Room **2.8.5** (`room-runtime`, `room-ktx`, `room-compiler` via KSP) — classic Room, *not* room3
- Hilt (`com.google.dagger`) **2.59.x** + `androidx.hilt:hilt-navigation-compose:1.3.0`
- Retrofit **2.11.0** + `converter-moshi`, OkHttp **4.12.0** + `logging-interceptor`
- Moshi **1.15.2** + `moshi-kotlin-codegen` (KSP)
- DataStore preferences **1.2.1**
- Test: JUnit4, `kotlinx-coroutines-test`, **Turbine**, **MockWebServer**, **Robolectric** (for in-memory Room in unit tests)
- Deliberately excluded: Paging 3 (no server paging), Coil (no images in API), WorkManager (ViewModel observes NetworkMonitor instead), shimmer libs (use `CircularProgressIndicator` + `AnimatedVisibility`)

## Folder structure (single module)

```
app/
├─ gradle/libs.versions.toml
├─ src/main/
│  ├─ AndroidManifest.xml            (INTERNET, ACCESS_NETWORK_STATE)
│  ├─ java/com/example/synced/
│  │  ├─ SyncedApp.kt                (@HiltAndroidApp)
│  │  ├─ MainActivity.kt             (edge-to-edge, setContent { SyncedTheme { AppNavHost } })
│  │  ├─ core/
│  │  │  ├─ common/                  AppResult<T>, UiState, AppError + ErrorMapper, UiEvent
│  │  │  ├─ network/                 NetworkMonitor (callbackFlow<Boolean>), ErrorInterceptor, ProvidersModule (Retrofit/OkHttp/Moshi/Room/DataStore)
│  │  │  └─ ui/                      navigation/AppNavHost.kt, theme/{Color,Theme,Type,Shapes,Spacing}.kt
│  │  ├─ ui/components/              AppButton, ContentCard, StateMessage, OfflineBanner, LoadingState
│  │  └─ feature/
│  │     ├─ content/
│  │     │  ├─ domain/               Post, Comment, ContentRepository (interface), RefreshOutcome
│  │     │  ├─ data/                 PostDto/CommentDto, PostEntity/CommentEntity, PostDao, ContentApi,
│  │     │  │                        mappers (Dto↔Entity↔Domain), ContentRepositoryImpl
│  │     │  └─ presentation/         list/{ContentViewModel,ContentScreen}, detail/{DetailViewModel,DetailScreen}
│  │     └─ settings/
│  │        ├─ domain/               AppSettings, SettingsRepository (interface)
│  │        └─ data/ + presentation/ SettingsDataStore, SettingsRepositoryImpl, SettingsViewModel/Screen
│  └─ res/                           themes.xml (platform Theme.Material.Light.NoActionBar — no Material lib dep)
├─ src/test/                         ContentViewModelTest, DetailViewModelTest, ContentRepositoryTest, ErrorMapperTest (Turbine + Robolectric + MockWebServer + fakes)
```

No separate `domain` module — repository **interfaces** live in `feature/*/domain`, implementations in `feature/*/data`; ViewModels inject only interfaces (Hilt `@Binds`).

## Offline-first data flow (core requirement)

1. `ContentRepository.observePosts(): Flow<List<Post>>` = Room DAO flow → **single source of truth**; UI never sees raw network results.
2. `refresh(): AppResult<Unit>` = fetch `/posts` + `/users` (denormalize author name onto `PostEntity`) → transactional replace-in-DB → errors mapped by `ErrorMapper`, cache never cleared on failure.
3. `ContentViewModel` collects: Room flow → `UiState.Content`, `NetworkMonitor` → offline banner + auto-refetch on `false→true` edge, `refresh()` one-shot for pull-to-refresh (swipe gesture) and retry (error state).
4. Detail screen: post from Room; comments cached lazily (`CommentEntity` keyed by `postId`, refreshed when online, read from Room when offline) — same pattern, so detail also works offline.
5. `UiState` sealed: `Loading | Content(items, isRefreshing) | Empty | Error(message)`; one-off events (snackbar) via `SharedFlow<UiEvent>`.

## Screens

1. **Home/Feed** — `TopAppBar("Synced")`, `OfflineBanner` (AnimatedVisibility), `PullToRefreshBox` + `LazyColumn` of `ContentCard` (title/body snippet/author label, M3 `Card`), state screens: loading spinner / error+retry (`StateMessage`) / empty.
2. **Detail** — full title, body, author (from cached users join), comments list with their own loading/error handling; back nav.
3. **Settings** — theme mode (System/Light/Dark) + dynamic-color toggle via DataStore; "Clear cached content" action (demonstrates DataStore + repository interplay).

## Design system (Material 3)

- `SyncedTheme`: `dynamicColor` on SDK 31+, static fallback `ColorScheme` for light+dark in `Color.kt`; `Typography` uses M3 type roles only; `Shapes` defined once.
- Spacing scale via `CompositionLocal`: 4/8/16/24/32 dp — no magic numbers; all touch targets ≥48dp, `contentDescription` on icons, WCAG-AA palette.
- Transitions: `AnimatedVisibility`/`Crossfade` for loading↔content and offline↔online only.

## Build variants

`flavorDimensions = ["environment"]`: `dev` / `prod` productFlavors with `BuildConfig.API_BASE_URL` (both default to JSONPlaceholder; README documents swapping it), plus debug-only `HttpLoggingInterceptor` gated on `BuildConfig.DEBUG`.

## Tests (per prompt deliverables)

- **ViewModel (Turbine):** loading→content, refresh failure keeps cached content + error event, offline banner flips with NetworkMonitor, auto-refresh on reconnect, empty state.
- **Repository:** fake `ContentApi` + in-memory Room (Robolectric): first refresh populates cache; offline reads served from Room; network failure leaves cache intact; mappers round-trip.
- **ErrorMapper:** IOException→"No connection…", HTTP 4xx/5xx→messages.

## README (deliverable)

Setup (JDK 17+, SDK 36, how to point at own API), offline-first Room flow explanation, design system (theme/typography/spacing), variant/flavor API switching, folder-structure map.

## Execution phases

0. **Toolchain smoke test** — generate Gradle wrapper from cached 8.14.3 dist, minimal Compose activity, verify `assembleDebug` with the version set above (adjust Compose BOM / KSP if compileSdk or processor mismatch); confirm network access to Google Maven.
1. **Scaffold** — version catalog, app module, flavors, manifest, `gradle.properties` (JDK 23, AndroidX flags), folder tree.
2. **Theme + component library** — `ui/theme/*`, `ui/components/*`.
3. **Core** — `AppResult`/`UiState`/`ErrorMapper`/`NetworkMonitor`, Hilt network/database/datastore modules.
4. **Content feature** — DTOs/entities/DAO/API/mappers → repository impl → ViewModels → screens → navigation.
5. **Settings feature** — DataStore-backed repository, screen, theme wiring.
6. **Tests** — unit suites green.
7. **README** + final verification: `gradlew :app:assembleDevDebug`, `gradlew :app:testDevDebugUnitTest`, `lintDevDebug`.

## Risks / fallbacks

- Compose BOM's material3 requiring SDK 37 → pin `material3 1.3.2` + compose 1.11.x individually.
- KSP version for Kotlin 2.2.21 not available → fall back to `kapt` for Room/Hilt/Moshi (slower, noted in README).
- JSONPlaceholder is sometimes slow/flaky → app must degrade to Room cache (which is the point), tests use MockWebServer so CI doesn't depend on it.
- JDK 23 vs AGP 8.13 unsupported combo → set toolchain/JAVA_HOME to a downloaded JDK 17/21 (fetch Temurin) instead.
