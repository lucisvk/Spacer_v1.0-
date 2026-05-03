# Spacer

> **Copyright © 2026  Tyler Morency, Christopher Botis, Jesiah Gilbert, Joshua Santana, Alex Tyan, Jonathan Galvan. All Rights Reserved.**
> Proprietary software — see [LICENSE](LICENSE). Not licensed for reuse,
> redistribution, or modification without written permission.

Spacer is a native Android event-planning app. It helps users create
events, invite friends, coordinate shared items (like potlucks), find
overlapping availability, chat with guests, and discover events
nearby.

## Features

- **Event creation & management** — places, date/time, capacity, hosting controls
- **Friends, invites, and a public feed** with category filters
- **Potluck-style item claiming** for shared events
- **Availability scheduling** with calendar conflict detection (uses the device calendar)
- **Direct messages and event chat** (Supabase Realtime)
- **AI event-planning chatbot** powered by Groq Cloud
- **Find People** with public profiles, friend requests, and blocking
- **Login / sign-up** via email-password, Google OAuth, and Discord OAuth
- **Live event tracking** and push notifications
- **Light / dark Material 3 theme**


## Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **Navigation-Compose** for routing
- **Kotlin Coroutines** + **kotlinx.serialization**
- **Supabase Kotlin SDK** (Postgres, Auth, Realtime, Storage)
- **Retrofit / OkHttp** for non-Supabase HTTP
- **Google Play Services**: OAuth, Maps SDK, Places API (New)
- **Groq Cloud** for the chatbot
- **Gradle (Kotlin DSL)** with the version catalog at `gradle/libs.versions.toml`

### Requirements

| | |
|---|---|
| Platform | Android only — `minSdk` 24, `targetSdk` 35, `compileSdk` 36 |
| IDE | Android Studio Hedgehog (2023.1) or newer |
| JDK | 17 (bundled with current Android Studio) |
| Disk | ~15 GB for SDK, emulator, Gradle cache |

## Setup

### 1. Install tooling

1. Install **JDK 17** (Temurin / Adoptium / Oracle / Zulu).
2. Install **Android Studio** from
   <https://developer.android.com/studio>; accept the *Standard* SDK
   install on first run.
3. In *SDK Manager*, install **Android API 36** and **API 35**.

### 2. Clone and open

```sh
git clone https://github.com/lucisvk/Spacer_v1.0-.git
cd Spacer_v1.0-
```

Open the folder in Android Studio and let Gradle sync finish.

These are read by `app/build.gradle.kts` and exposed via `BuildConfig`
at compile time. Rebuild after editing.

### 4. Set up the database

Spacer uses **Supabase** (managed PostgreSQL).

1. Create a project at <https://supabase.com>.
2. Apply the SQL migrations under [`supabase/migrations/`](supabase/migrations)
   in chronological order, either via the Supabase dashboard SQL
   editor or the Supabase CLI:

   ```sh
   supabase link --project-ref <your-project-ref>
   supabase db push
   ```

3. Copy the project's URL and anon key into `local.properties`
   (`SUPABASE_URL`, `SUPABASE_KEY`).

### 5. Configure Google Cloud (optional but recommended)

In <https://console.cloud.google.com>:

- Create OAuth 2.0 credentials (Web client + Android client). Use the
  Web client ID for `GOOGLE_WEB_CLIENT_ID`. Add your debug/release
  SHA-1 fingerprints to the Android client.
- Enable **Places API (New)** and create an API key restricted to the
  `com.example.spacer` package + your SHA-1s. Use this for
  `PLACES_API_KEY`.
- (Optional) Enable Calendar API for calendar sync.

### 6. Pick a run target

Plug in a physical device with USB debugging enabled, or create an
emulator under *Device Manager → Create device* (Android 7.0+ image).

### 7. Build and run

From Android Studio: pick a run target and click **Run** (▶).

From the command line:

```sh
./gradlew assembleDebug
./gradlew installDebug
```

## Third-party libraries & services

| Component | Purpose |
|---|---|
| Jetpack Compose + Material 3 | Declarative UI toolkit and theming |
| Navigation-Compose | In-app routing |
| Kotlin Coroutines + Serialization | Async work and JSON |
| Supabase Kotlin SDK | Postgres, Auth, Realtime, Storage |
| Retrofit + OkHttp + Gson | HTTP/JSON outside Supabase |
| Google Play Services Auth | Google OAuth sign-in |
| Google Maps SDK + Places API (New) | Venue search and event location |
| Groq Cloud | LLM backend for the in-app chatbot |
| JUnit 4, AndroidX Test, Espresso | Unit + instrumented tests |

Exact versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Full attribution is in [NOTICE](NOTICE).

## Testing

The strategy is layered:

1. **Manual exploratory testing** of UI flows on a device + emulator.
2. **Local unit tests** under `app/src/test/` covering pure logic
   (date formatting, availability conflict models, deep-link parsing).
3. **Instrumented tests** under `app/src/androidTest/` covering
   things that need a real Android runtime
   (`SupabaseManagerConfig`, `SessionPrefs`).
4. **CI** runs compile + unit tests on every push (see
   [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)).

### Sample test cases

| # | Area | Scenario | Expected |
|---|---|---|---|
| TC-01 | App boot | Cold start reaches the login screen | Splash → animated splash → Login within ~2 s |
| TC-02 | Auth | Sign-up with valid input | Account created, navigated back to Login |
| TC-03 | Auth | Sign-up with mismatched passwords | Inline validation; no network call |
| TC-04 | Auth | Login with valid email/password | Lands on Home with username greeting |
| TC-05 | Auth | Login with wrong password | Error toast; remain on Login |
| TC-06 | Events | Create an event with a Places venue | Event appears on Home and in Events Hub |
| TC-07 | Events | Capacity-limited public event reaches limit | Further joins are rejected with a clear message |
| TC-08 | Availability | Create event during a busy device-calendar slot | Conflict warning surfaces in the picker |
| TC-09 | Chat | Send a DM | Message appears in real time on the recipient's thread |
| TC-10 | Chatbot | Ask the planner for a suggestion | Groq returns a response within a few seconds |
| TC-11 | Profile | Block a user | Blocked user disappears from Find People results |
| TC-12 | Theme | Toggle dark / light | Colours flip without flicker |
| TC-13 | Backend offline | Submit any action with no network | Friendly error; UI stays responsive |
| TC-14 | Unit | `EventDateFormatsTest` | All date format cases pass |
| TC-15 | Unit | `AvailabilityConflictModelsTest` | Conflict detection cases pass |
| TC-16 | Instrumented | `SupabaseManagerConfigTest` | Reads `BuildConfig` values correctly |

### Running tests

```sh
./gradlew test                       # local unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests on a connected device/emulator
```

## Project structure

```
Spacer/
├── LICENSE / COPYRIGHT / NOTICE
├── README.md
├── build.gradle.kts, settings.gradle.kts
├── local.properties.example         Template for SUPABASE_*, GOOGLE_*, etc.
├── gradle/libs.versions.toml
├── supabase/
│   └── migrations/                  Versioned SQL migrations
├── .github/workflows/               CI (build + unit tests)
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/spacer/
        │   │   ├── MainActivity.kt
        │   │   ├── calendar/        Device calendar integration
        │   │   ├── chatbot/         Groq-powered planner chatbot
        │   │   ├── events/          Events hub, event chat, availability
        │   │   ├── home/            Home / discovery feed
        │   │   ├── location/        Place search and event location flow
        │   │   ├── network/         Supabase + Retrofit clients
        │   │   ├── profile/         Profile, blocked users, settings
        │   │   ├── social/          Find People, friends, blocking
        │   │   ├── tracking/        Live event tracking
        │   │   └── ui/              Theme + shared components
        │   └── res/                 Drawables, strings, themes
        ├── test/                    Local unit tests
        └── androidTest/             Instrumented tests
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| App builds but Supabase calls fail | Check `SUPABASE_URL` / `SUPABASE_KEY` in `local.properties`; rebuild. |
| Place search returns nothing | `PLACES_API_KEY` missing, restricted incorrectly, or Places API (New) not enabled in Google Cloud. |
| Google sign-in fails | `GOOGLE_WEB_CLIENT_ID` is wrong, or your debug SHA-1 isn't on the Android OAuth client. |
| "Cleartext HTTP traffic not permitted" | Use HTTPS endpoints in production. |
| Gradle sync fails on a fresh clone | Confirm JDK 17 and Android SDK API 36. |
| Chatbot returns nothing | Groq API key not configured / billing not enabled. |

## License

Proprietary — All Rights Reserved. See [LICENSE](LICENSE),
[COPYRIGHT](COPYRIGHT), and [NOTICE](NOTICE).
