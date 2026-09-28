# Synced

An offline-first content platform for Android. Browse real articles and their comments, search saved posts and the web at once, pull to refresh, switch themes — everything works with no connection, backed by a local Room cache that is only ever replaced after a fully successful network sync.

- **UI:** Jetpack Compose + Material 3 ("Ink & Slate" design system)
- **Architecture:** MVVM + Clean-ish layering (data / domain / presentation), Hilt DI
- **API:** [Dev.to](https://dev.to/api) (dev/prod flavors) — real articles, authors and comments

## Requirements

| Tool | Version |
|---|---|
| JDK | 17+ (repo pins a local JDK 23 via `gradle.properties` — see note) |
| Android SDK | compileSdk / targetSdk **36**, minSdk **26** |
| Gradle | 8.14.3 (wrapper included) |
| Android Gradle Plugin | 8.13.1 |
| Kotlin | 2.2.21 (KSP 2.2.21-2.0.5) |

> **Note:** `gradle.properties` contains `org.gradle.java.home=C\:\\Users\\hs538\\.jdks\\openjdk-23` (machine-specific). Point it at your own JDK 17/21/23 install or delete the line to use `JAVA_HOME`.

## Setup & build

```powershell
# Debug APK (dev flavor)
.\gradlew.bat :app:assembleDevDebug

# Unit tests (JVM + Robolectric)
.\gradlew.bat :app:testDevDebugUnitTest

# Lint
.\gradlew.bat :app:lintDevDebug
```

Then run the **devDebug** variant from Android Studio, or `adb install app\build\outputs\apk\dev\debug\app-dev-debug.apk`.

## Pointing the app at a different API

The base URL is injected at **build time** through `BuildConfig.API_BASE_URL`, defined per product flavor in `app/build.gradle.kts`:

```kotlin
productFlavors {
    create("dev")  { /* ... */ buildConfigField("String", "API_BASE_URL", "\"https://dev.to/api/\"") }
    create("prod") { /* ... */ buildConfigField("String", "API_BASE_URL", "\"https://dev.to/api/\"") }
}
```

The app needs four things from the API (see `ContentApi`):

| Endpoint | Used for |
|---|---|
| `GET articles?per_page=100` | feed (id, title, description, user.name) |
| `GET articles/{id}` | full body (`body_markdown`) for the detail screen |
| `GET articles/search?q=…` | online search (server-side relevance ranking) |
| `GET comments?a_id={id}` | comments (flattened from nested `children`, HTML stripped) |

To use your own backend:

1. Change the `API_BASE_URL` value (must end with `/`) for either or both flavors.
2. Update the DTOs in `feature/content/data/remote/` and the paths in `ContentApi` if your JSON shapes differ.
3. `NetworkModule` reads the URL from `BuildConfig` — no other code changes needed.

Flavors: `dev` (verbose HTTP logging in debug builds, `.dev` applicationId suffix) and `prod`. Dimensions: `environment`.

## How offline-first Room caching works

Room is the **single source of truth** — every screen renders exclusively from Room flows; the network only ever *writes into* the cache.

```
UI (Compose)  <── observes ──  ViewModel  <── Flow  ──  Room (PostEntity / CommentEntity)
                                   │
                                   └── calls ──> Repository.refresh()
                                                     │  GET articles?per_page=100
                                                     ▼
                                              success? ──yes──> single transaction:
                                                     │            replace posts,
                                                     │            denormalize author name
                                                     └── no ──> keep cache untouched
```

Key rules:

- **Cache is replaced transactionally** only after a sync fully succeeds — a partial response never corrupts what's on screen.
- **Refresh failure keeps content visible.** If the cache has data, a failed refresh shows a snackbar but never an error screen. An error/empty screen appears only when there is nothing cached.
- **Author name is denormalized** onto `PostEntity` at sync time from the article's embedded author, so the feed renders offline without joins or lazy loads.
- **Progressive detail loading:** the feed caches each article's excerpt (`description`); opening a post silently fetches the single article and upgrades the cached body to the full cleaned `body_markdown` (markdown → plain text, links/headings/emphasis stripped). Comments arrive as HTML and are stripped to plain text; nested replies are flattened depth-first.
- **Connectivity-aware:** a `ConnectivityManager`-backed `NetworkMonitor` drives an offline banner and triggers an automatic refresh when the connection returns. Manual pull-to-refresh is the only refresh that reports success/failure; startup/refresh-on-reconnect syncs are silent.
- **Detail screen** loads the cached post instantly, then syncs its comments; a post that isn't cached (e.g. an online search hit) is fetched per-article into memory — never into Room, so the mediator's count-based page math stays intact. A post id missing from both cache and server shows *"This post isn't available."*
- **Search is local + online in one list:** cached matches stream in from Room the moment the query settles (250 ms debounce), while a single `articles/search` request merges the server's relevance-ranked results ahead of them (deduped by id, remote results first). Online results are never persisted — the offline matches keep working, and a failed remote leg falls back to cached matches with an "Offline — saved matches only" note (retryable without retyping).

## Infinite scroll — Paging 3 (offline-first)

The feed is a `Pager` over the Room `posts` table (`ContentRepository.pagedPosts()`), so pagination inherits the offline-first contract: **the list always renders from cache** and the network only writes into it.

```
LazyColumn ── LazyPagingItems ── PagingSource (Room, ORDER BY id DESC)
                     │
                     └── exhausted locally? ──> ContentRemoteMediator
                                                    │  GET articles?per_page=100&page=N+1
                                                    ▼
                                             insert into Room ──> invalidates PagingSource
```

- **`ContentRepositoryImpl.refresh()` still owns page 1** (startup / reconnect / pull-to-refresh) and its transactional cache swap. The mediator *never fetches on `REFRESH`* — it only re-derives pagination state from `COUNT(posts)`, which is what guarantees a Room write can't trigger a network loop.
- **The mediator appends older pages** (page 2, 3, …) only when scrolling reaches the end of what's cached. Next page = `count / 100 + 1`; a partial trailing page (`count % 100 != 0`) or a short server response means "end of feed".
- **`ORDER BY id DESC`** mirrors Dev.to's newest-first page order, so appended (older) pages always land at the bottom of the list.
- **UI tail states:** a "Loading more…" spinner row while the next page fetches, and a "Couldn't load more — Retry" row if it fails (offline, server hiccup) — the loaded list stays on screen either way.
- With no connectivity the feed simply pages through whatever is cached; no error surfaces unless the *initial* load has nothing to show.

## Design system — "Ink & Slate"

Generated with the `ui-ux-pro-max` skill (persisted at `design-system/synced/MASTER.md`), implemented in `app/src/main/java/com/example/synced/core/ui/theme/`:

- **Color** (`Color.kt`): scholarly navy + citation gold. Light: paper `#F8FAFC` / ink `#0F172A`, navy `#1E3A5F`, gold accent `#B45309` (gold fill, white label), hairline borders `#CBD5E1`. Dark: near-black slate `#020617` paper, `#0E1223` cards, lightened gold `#F59E0B` accent, `#334155` hairlines. All text pairs meet WCAG AA (4.5:1). Material You is off by default (toggle in Settings).
- **Typography** (`Type.kt`): **Crimson Pro** (scholarly serif) for display/headline/title roles, **Atkinson Hyperlegible** (a low-vision readability face, static 400/700) for UI titles, body and labels — bundled in `res/font/` so the hierarchy renders offline. Labels carry extra tracking for uppercase meta lines.
- **Spacing** (`Spacing.kt`): 4 / 8 / 16 / 24 / 32 dp scale exposed as `LocalSpacing` (8dp rhythm).
- **Shape** (`Shapes.kt`): crisp Swiss radii — 12dp cards, 8dp controls, no pills.
- **Components** (`ui/components/`): `ContentCard` (numbered index row + uppercase author meta + serif headline, hairline border), `AuthorChip` (tracked uppercase label), pink `AppButton`, `StateMessage`/`LoadingState`/`InlineLoading`, pink `OfflineBanner`.

## Project structure

```
app/src/main/java/com/example/synced/
├── SyncedApp.kt              # @HiltAndroidApp
├── MainActivity.kt           # sets theme, hosts AppContent → AppNavHost
├── core/
│   ├── common/               # AppResult, AppError, ErrorMapper, UiState, UiEvent
│   ├── network/              # NetworkModule, ErrorInterceptor, NetworkMonitor
│   ├── ui/navigation/        # NavHost, routes
│   └── ui/theme/             # Color, Type, Shapes, Spacing, Theme
├── ui/components/            # Reusable Compose components
└── feature/
    ├── content/
    │   ├── data/             # DTOs, entities, DAOs, mappers (incl. HTML/markdown
    │   │                     #   sanitizers), ContentApi, ContentRemoteMediator,
    │   │                     #   repository impl
    │   ├── domain/           # Post, Comment models + repository interfaces
    │   └── presentation/     # list/ (feed) & detail/ screens + ViewModels
    └── settings/             # DataStore-backed settings, theme mode UI
app/src/test/                 # Unit tests (JVM + Robolectric)
design-system/synced/         # Persisted design system (MASTER.md)
```

## Tests

47 unit tests across 6 suites, all JVM-based (Robolectric for Room where needed):

| Suite | Covers |
|---|---|
| `ErrorMapperTest` | IOException / HTTP / unknown → user messages |
| `ContentMappersTest` | DTO ↔ Entity ↔ domain round-trips, HTML/markdown sanitizing, thread flattening, id hashing |
| `ContentRepositoryTest` | offline-first refresh, excerpt→full-body upgrade, local/online search merge + fallback, non-persisting `fetchPost` |
| `ContentRemoteMediatorTest` | paging: refresh stays network-free, append fetches page 2 into Room, end-of-feed on partial pages, offline retry |
| `ContentViewModelTest` | loading→content, error+retry, empty, offline banner, reconnect auto-refresh, search debounce/merge/retry |
| `DetailViewModelTest` | cached post + comments, in-memory fetch of uncached posts, comments failure, missing post, offline |

Tests use fakes (`FakeContentRepository`, `FakeNetworkMonitor`) and `MainDispatcherRule` — no network dependency in CI.

## Tech stack

Compose BOM 2026.06.01 · Material 3 1.4.0 · Hilt 2.57.2 · Room 2.8.5 · Retrofit 2.11.0 · OkHttp 4.12.0 · Moshi 1.15.2 · DataStore 1.2.1 · Navigation-Compose 2.9.7 · Lifecycle 2.10.0 · Turbine 1.2.1 · Robolectric 4.17
