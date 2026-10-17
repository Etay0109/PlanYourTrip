# Plan Your Trip — Android app

Trip-planning app. Kotlin + Jetpack Compose + Material 3, single Activity (`MainActivity`) hosting a Navigation
Compose graph. Package `com.example.planyourtrip`, minSdk 26, targetSdk 36.

- **Accounts:** Firebase Authentication (email/password).
- **Data:** trips, budgets, expenses, travelers, settlements, itinerary activities and checklists are stored in Room
  per account and synced with Cloud Firestore. **Room is the only source for the screens**; the app works offline.
- **Shared trips:** `trips/{tripId}` with an owner and ≤ 10 members, personal data under `memberData/{uid}`. Stages 1
  (structure, uid traveler keys, migration) and 2 (single-use 7-day invite codes, joining, leaving, removing members)
  are done; stage 3 (multi-member polish) is not built.
- **Features:** Home (trip cards, swipe to delete), trip screen with 4 tabs (Overview, Budget, Prep, Plan), exchange
  rates (Frankfurter, cached in Room), expense splitting (travelers are names, not accounts; the budget counts only my
  share), AI trip assistant (Gemini via Firebase AI Logic + App Check), "Share trip" PDF, Prep checklists + travel
  documents (documents on this device only), destination suggestions (Photon + a built-in Hebrew table) with flags,
  destination photos from Wikipedia (this device only), Google Maps **links only** (no SDK, no key, no location
  permission), and a local "trip starts tomorrow" notification at 18:00 the day before.

Detailed references live in `docs/` (see "Detailed docs" at the end). Read the relevant file before changing a feature.

## Rules (always follow)
- **Never commit until the user approves.** Show the change and wait for an explicit go-ahead.
- **Never commit screenshots** (personal data: `/screens/` is git-ignored) or
  **secrets** (App Check debug token, keystores, `local.properties`, passwords). Never log passwords (only their length).
- **Never run `./gradlew connectedDebugAndroidTest`** (it uninstalls the app, wiping its data and App Check token).
  Run device tests manually, see Commands.
- **Never pull, edit or push the app's database** (`plan_your_trip.db`) on a phone or emulator, not even one row.
  Change state only through the app (e.g. delete and re-add a trip).
- **Never put a shell variable in `rm`.** Write the literal path and delete only the intended folder's files.
- **Never search or scan outside the project folder** (no `find /`, no home/Music/Photos/iCloud/network volumes). If a
  tool is missing, check known locations directly (e.g. the Android SDK).
- **Never send requests to the real Gemini model** to test the assistant (tiny free quota); use
  `FakeAssistantRepository`. The user tests on the device.
- **Never test against the real Firestore or Wikipedia** in automated tests; use the fakes / the Firebase Emulator.
- **Sync refactors must pass the existing sync tests unedited** (`SyncDocumentsTest`, `SyncMergeTest`,
  `SyncManagerTest`, `TripDaoTest`). If a test would need a change, stop and ask.
- **Phone/emulator steps:** don't install an APK, toggle airplane mode or open the app on a device without the user's
  go-ahead.
- **No long waits:** at most 2 minutes per check loop; for time-based checks move the **emulator** clock
  (`settings put global auto_time 0` + `cmd alarm set-time <epoch ms>`, `cmd alarm set-timezone`), then set
  `auto_time`/`auto_time_zone` back to 1. Emulator only, never the phone.
- **Testing reminders on a phone:** after Run, open the app from the launcher and leave it; **never press Android
  Studio's Stop, and don't Run again, before the reminder time.** Run and Stop force-stop the app (`am force-stop`),
  which deletes its alarms and jobs until it is opened again. Swiping it out of the recent apps is fine.
- **Never seed sample trips into the database** (sample data only in previews and tests). Never use
  `fallbackToDestructiveMigration`.

## Commands
- Gradle 8.13 fails on the system JDK 24 ("Type T not present"). From the terminal, use Android Studio's JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug` (also regenerates the Room schema JSON in `app/schemas/`)
- Release build: `./gradlew assembleRelease` (unsigned APK; must compile too, see the Compose BoM note in Conventions)
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug` must report "No issues found" (dependency version checks are off in `app/lint.xml`:
  versions are pinned for Kotlin 2.0.21).
- Instrumented tests (needs an emulator; Room tests + Compose layout tests `ui/LayoutPolishTest`, which save pictures to
  the app's `files/layout-checks/`): airplane mode on, `assembleDebug assembleDebugAndroidTest`, `adb install -r` both
  APKs, `adb shell am instrument -w com.example.planyourtrip.test/androidx.test.runner.AndroidJUnitRunner`, then
  uninstall only `com.example.planyourtrip.test`.
- Fire the trip reminders now (debug builds, emulator): `adb shell am broadcast -a
  com.example.planyourtrip.DEBUG_TRIP_REMINDER -p com.example.planyourtrip` (add `--ez force true` to show one again;
  `src/debug/.../DebugReminderReceiver.kt`, guarded by the `DUMP` permission so only adb can send it).
- See the scheduled reminders (debug builds): `adb shell am broadcast -a com.example.planyourtrip.DEBUG_NEXT_REMINDER
  -p com.example.planyourtrip`, then `adb logcat -s TripReminder`; also `adb shell dumpsys alarm | grep -A2
  TRIP_REMINDER_ALARM` (`window=0 exactAllowReason=permission` = exact, `window=+1h` = inexact).
- Firestore security rules: `firestore.rules`. Not deployed by the build: paste into Firebase console → Firestore
  Database → Rules → Publish after every change.
- Security rules tests (Firebase Emulator, project id `demo-planyourtrip`, never the real project): `cd rules-tests &&
  npm install` once, then `JAVA_HOME=<Android Studio jbr> PATH="$JAVA_HOME/bin:$PATH" npm test`. Run after every rules
  change.
- Firebase config: `app/google-services.json` (project `planyourtrip-79724`); the plugin only reads it from `app/`.
- Log tags (debug builds only): `CloudSync`, `TripAssistant`, `TripPdf`, `DestinationPhoto`, `TripReminder`, `App`.
  App code logs only through `AppLog` (never `android.util.Log`); release builds log nothing (`LogConfig.kt` in
  `src/debug|release`).

## Structure (full file map: `docs/structure.md`)
```
app/src/main/java/com/example/planyourtrip/
  MainActivity.kt, PlanYourTripApplication.kt, AppContainer.kt (manual DI), AppLocaleSetup.kt, PhotoImageLoader.kt
  data/auth/       AuthRepository + FirebaseAuthRepository
  data/trip/       trips, expenses, activities, checklists, travelers, settlements, members: models, entities, DAOs,
                   repositories, AppDatabase, Migrations, Converters
  data/document/   Prep documents (this device only), DocumentFileStore + AndroidDocumentFileStore
  data/currency/   rates cache + FrankfurterRatesApi
  data/http/       HttpGet + OkHttpGet (the one cancellable HTTP client), appUserAgent
  data/assistant/  AssistantRepository + FirebaseAssistantRepository, prompt + JSON parsing
  data/sync/       SyncManager, planMerge, SyncDocuments, SyncPaths, CloudStore + FirestoreCloudStore, legacy migration,
                   TripAccess (lost trips)
  data/sharing/    invite codes, InviteRepository + FirestoreInviteRepository
  data/place/      destination suggestions (Photon, Hebrew/English tables)
  data/photo/      destination photos (Wikipedia), Room cache
  data/reminder/   trip reminders (alarm + WorkManager backup)
  data/user/       greeting name, initials
  ui/navigation/   Routes, AppNavHost, SessionViewModel
  ui/auth/ ui/home/ (+ join/) ui/tripform/
  ui/tripdetails/  trip screen + tabs; prep/, members/, split/, expense/, activity/, assistant/, components/
  ui/common/       shared code (formatting, money, flags, split math, share PDF, delete, account menu, refresh,
                   components/)
  ui/theme/        colors, extended colors, shapes, type, dimens
app/src/debug|release/.../AppCheckSetup.kt   App Check debug / Play Integrity provider
app/src/debug|release/.../LogConfig.kt       AppLog on in debug, off in release
app/schemas/      exported Room schemas — commit them
design/           visual references (committed)
firestore.rules, firebase.json, rules-tests/   security rules + emulator tests
tools/hebrew-places/   generator of HebrewPlaceTable.kt (see its README)
```

## Architecture decisions
- **Unidirectional data flow.** Each ViewModel exposes an immutable UI state in a `StateFlow`; screens collect it with
  `collectAsStateWithLifecycle()` and send events back as lambdas. `stateIn` uses `WhileUiSubscribed` (5 s).
- **Stateful/stateless split.** `XxxScreen` only wires the ViewModel; `XxxContent` takes state + lambdas (previewable,
  UI-testable). Preview sample data lives only in preview functions.
- **Pure UI state stays in Compose** (password visibility, "date picker open"); everything else in the ViewModel.
  **Typed text that must keep its cursor** (converter fields, chat input, "Add item…") is Compose state, never routed
  through a `StateFlow` (that caused the "can't type 3+ digits" bug).
- **Manual DI.** `AppContainer` owns singletons. Every ViewModel takes dependencies as constructor parameters and has a
  `companion object { val Factory = viewModelFactory { initializer { ... appContainer() ... } } }`; tests pass fakes.
- **Time and dispatchers are injected** (`java.time.Clock`, `computeDispatcher`), so tests fix "today" and no work
  outlives a test.
- **External services stay behind interfaces.** Only these files import Firebase: `FirebaseAuthRepository`,
  `FirebaseAssistantRepository`, `FirestoreCloudStore`, `FirestoreInviteRepository`, the two `AppCheckSetup.kt` and
  `AppContainer`. Likewise only `AndroidDocumentFileStore` touches document files, only `AndroidTripPdfExporter` uses
  `PdfDocument`/`FileProvider`, only `AndroidReminderWork` uses AlarmManager/WorkManager, Coil appears only in
  `PhotoImageLoader`, `PlanYourTripApplication` and `ui/common/components/DestinationPhoto.kt`, and each HTTP API has
  one class (`FrankfurterRatesApi`, `PhotonPlaceSearch`, `WikipediaPhotoSource`), all on `HttpGet`; only `OkHttpGet`
  makes requests (cancelling cancels the call). Tests use the `Fake…` versions.
- **No map SDK.** Maps are Google Maps URLs opened with `ACTION_VIEW` (`GoogleMaps.kt`).
- **ViewModels have no Android `Context`.** Validation returns enum codes; the UI maps them to string resources.
- **Package rules.** Each feature package owns its screen, ViewModel, `XxxUiState.kt` and validator. Anything used by
  more than one feature lives in `ui/common` (composables in `ui/common/components`); features never import from each
  other.
- **Heavy work off the main thread.** Room/Firebase calls are `suspend`/`Flow`; grouping and totals use
  `flowOn(computeDispatcher)`.
- **One-shot results are state flags** (`isAuthenticated`, `saved`, `done`) observed in a `LaunchedEffect` that
  navigates. **Double taps act once:** `isSaving`/`isDeleting`/`isCreating`/`isJoining` are set synchronously.
- **Edit forms reuse the create forms** (`TripForm(tripId?)`, `ExpenseForm(tripId, expenseId?)`,
  `ActivityForm(tripId, activityId?, dateEpochDay?)`): null id creates, otherwise the item is loaded once (`isLoading`).
- **Navigation calls live only in `AppNavHost`**; screens receive callbacks. Routes are type-safe `@Serializable`
  classes in `Routes.kt`; the trip screen's tabs are Compose state, not destinations. Details: `docs/screens.md`.

## Language and layout direction (details: `docs/language.md`)
The app is **always English and left to right**, also on Hebrew/Arabic phones; no translations (`res/values` only).
- One locale, `AppLocale.locale` (`Locale.US`), is the **default** `locale` parameter of every formatter — never
  `Locale.getDefault()`. Locale-independent formats (Firestore "HH:mm", `Money.toInput`) use `Locale.ROOT`. Add new
  formatters to `AppLocaleTest`.
- `MainActivity.attachBaseContext` wraps the context with `withAppLocale()`; the manifest has
  `android:supportsRtl="false"`; `localeFilters += "en"` drops library translations; `applyDefaultAppLocale()` sets the
  JVM default.
- **User text** (destinations, notes, chat) keeps Compose's default text direction, so Hebrew paragraphs read right to
  left without mirroring the layout. Never set `TextDirection.Ltr` on text that can hold user input. String resources
  that embed user text start with fixed/LTR text.

## Data model (details: `docs/data-model.md`)
Room database `plan_your_trip.db`, `AppDatabase` **version 17**: `trips`, `expenses`, `activities`,
`checklist_items`, `documents`, `travelers`, `settlements`, `trip_members`, `deletions`, `exchange_rates`,
`destination_photos`.
- `TripEntity` (Room) vs `Trip` (domain, `toTrip()`): the UI never sees Room types.
- **Every query is scoped to one account** (`owner_id` = the signed-in uid that owns this local copy); children join or
  sub-select `trips` on it. Two accounts on one phone can each hold a copy of the same shared trip.
- **Money is whole agorot in `Long`** (100 agorot = ₪1), never `Float`/`Double`; exact rates and foreign amounts are
  `BigDecimal` text. Dates are epoch days; times minute of the day.
- **Stored enums by name** (`TripColor`, `ExpenseCategory`, `ActivityType`, `ChecklistKind`, `DocumentType`,
  `TravelerColor`), each with `fromName` + fallback; may be reordered or appended, never renamed/removed without a
  migration.
- **Sync columns** on synced tables: `uuid` (same in Room and Firestore), `updated_at` (from the injected `Clock`),
  `pending_sync`. The Long `id` is local only. Deletes go through `@Transaction deleteAndRecord` (a `deletions`
  tombstone row, then delete); "Undo" re-inserts as a new row with a new uuid.
- The budget is personal (`budget_agorot`, `budget_updated_at`, `budget_pending`); traveler keys in expenses and
  settlements are member uids or traveler uuids (the legacy `"me"` key was migrated in v14).
- Documents, exchange rates and destination photos are never synced. Nothing is in Android backup or device transfer
  (`allowBackup="false"` + `data_extraction_rules.xml`); Firestore is the backup. Deleting a trip cascades to its
  children; its documents (no FK) are deleted by `DocumentRepository.deleteForTrip`.
- **Changing the schema:** bump `version`, add a `Migration` or `AutoMigration`, build, commit the new JSON in
  `app/schemas/`, and add a `MigrationTest` case that proves all data is kept.

## Cloud sync and shared trips (details: `docs/sync.md`)
- Firestore layout: `trips/{tripUuid}` (+ `expenses`, `activities`, `checklist`, `travelers`, `settlements`,
  `members`, `memberData/{uid}` (+ `packing`), `private/invite`), `invites/{code}`, `users/{uid}` (legacy marker).
  Field names live in `SyncDocuments` and must match `firestore.rules`; paths come from `SyncPaths.of`.
- **Shared** with members: trip details, expenses, travelers, settlements, activities, "Before the trip" checklist.
  **Personal:** budget and Packing list. Every member can edit shared data (trust model).
- `SyncManager`: **full sync** on sign-in/app start, reconnect and pull to refresh; **quick sync** ~1 s after a local
  change (only pending items). One sync at a time; uploads trips first in chunks; `markSynced` only if unchanged.
- **Merge** (`planMerge`, pure): match by kind + uuid, newer `updatedAt` wins (ties → cloud / delete), deletes are
  tombstones. Timestamps are device clocks.
- **Lost trips** (left, removed, deleted by the owner) are decided only by a successful full sync; a trip I own is never
  lost. A quick sync never removes anything.
- **Status** is truthful: "All changes saved" only when nothing is pending and the last full sync succeeded.
  **Sign-out** deletes the account's local data only when it is safely in the cloud; otherwise it warns.
- Writing a trip never changes `ownerId`/`memberIds`; one trip per batch (Firestore's 20-lookup limit).
- **Security rules:** change them together with `SyncDocuments` and `rules-tests/rules.test.js`, run `npm test`,
  then publish. Text length limits live in `data/trip/TextLimits.kt` and must match the rules: input fields stop
  there, repositories and `SyncDocuments.toDocument` cut longer text, so a sync is never refused for length.
- Invites: 8-character codes (`InviteCode`), single use, 7 days; joining, leaving and removing members are described in
  `docs/sync.md`.

## Features (details: `docs/screens.md`, `docs/places-and-photos.md`, `docs/reminders.md`)
- **Home:** past / active trips (`groupTrips`), the first active trip is the gradient "Next trip" card; each card has a
  three-dots menu (Share trip, Delete/Leave trip) and swipes left to delete; pull to refresh runs a full sync.
  Destination titles never break inside a word; the dates line never wraps.
- **Trip screen:** hero `TripHeader` + bottom bar **Overview, Budget, Prep, Plan**, sparkle FAB for the assistant on
  every tab; lists end with `FabClearance` so the last card scrolls above the floating buttons.
- **Budget:** counts **my share** only (`ui/common/split`: `shares`, `balances`, `simplifyTransfers`); shares are whole
  agorot with the remainder given in list order, so they always add up exactly. Over 80% spent turns `budgetWarning`.
- **Expense currency:** ILS or one foreign currency; foreign amounts × latest rate, HALF_UP to agorot; the rate is fixed
  at the first save.
- **Plan:** day chips, timeline, activities outside the trip dates are kept (never deleted), "Open in Maps".
- **Prep:** "Before the trip" (shared) + "Packing" (personal) checklists, documents copied to `filesDir/documents/`
  (PDF/images ≤ 20 MB, this device only).
- **Assistant:** structured JSON answers, suggested day plans added to the itinerary; chat kept in memory only.
- **Share trip:** pure `buildTripPdfContent` + `paginate`, drawn by `AndroidTripPdfExporter`; no email or expense list
  in the PDF.
- **Destination suggestions:** instant built-in rows + Photon after 300 ms; a picked place stores country code and
  coordinates; editing the text forgets it. The currency is only chosen by the user (except preselection on a new trip
  after picking a place). Regenerate the Hebrew table with `tools/hebrew-places/`, never guess coordinates.
- **Destination photos:** Wikipedia lookup with strict image rules; no photo = exactly as before. Scrim levels are
  checked by `HeroPhotoTest`.
- **Trip reminders:** 18:00 the evening before, until 23:59 if late, at most once; alarm + WorkManager backup; a
  force-stopped app can't be woken.
- **Auth:** Sign In / Sign Up without a toggle; validation on submit; Firebase errors mapped to `AuthError`.

## Design rules (details: `docs/design.md`)
Reference images in `design/`; match them, with the deliberate differences noted in the docs.
- **Theme tokens only, never hard-coded colors in screens.** Material roles from `MaterialTheme.colorScheme`, brand
  colors from `ExtendedTheme.colors`. Raw color values live only in `ui/theme/Color.kt` and `ExtendedColors.kt`.
- **Never orange** for types/categories/travelers: orange (`budgetWarning`) means a warning. Keep `TintedColorsTest`
  passing when changing colors. Dynamic color is disabled on purpose. A dark scheme exists for every token.
- Typography is bundled Inter via named styles in `Type.kt`; never write `fontSize`/`letterSpacing` in screens.
  Sizes and breakpoints live in `Dimens.kt`. Shapes in `Shape.kt`; buttons are pills.
- **Long text never gets cut off**; check new layouts at 320dp / font scale 1.5.
- Reuse the shared components (`PrimaryButton`, `BackTopAppBar`, `DateField`, `Pill`, `EditFormScaffold`,
  `ConfirmDeleteDialog`, `TintedFilterChip`, …) before writing new UI. Icons are vector drawables in `res/drawable`,
  not `material-icons-extended`.

## Conventions
- All user-visible text lives in `res/values/strings.xml` (with `plurals`); no hard-coded strings in composables.
- All date formatting goes through `TripFormatting`; money through `Money`.
- DatePicker millis are UTC midnight: always convert with `toPickerMillis()` / `pickerMillisToLocalDate()`.
- Keep pure logic in plain Kotlin and unit-test it; test ViewModels with the `Fake…` classes in `app/src/test`. What
  each suite covers: `docs/testing.md`.
- Dependencies go through `gradle/libs.versions.toml`, pinned to **Kotlin 2.0.21**: Room 2.7.2, KSP
  2.0.21-1.0.28, Navigation 2.9.0, serialization 1.7.3 (core and json), **Compose BoM 2025.09.01** (Compose 1.9.2,
  Material 3 1.4.0), **Firebase BoM 34.12.0**, **Coil 3.0.4** + OkHttp 4.12.0, WorkManager 2.10.5. Firebase BoM
  34.13.0+, Coil 3.1+ and WorkManager 2.11+ need a newer Kotlin; check Kotlin/KSP compatibility before upgrading.
  Keep the Compose BoM at least as new as the Compose that Navigation/Activity/`ui-tooling` pull in, and check that
  both `assembleDebug` and `assembleRelease` build after changing Compose versions. Only declare dependencies the code
  uses.

## Detailed docs
- `docs/structure.md` — full file map of every package
- `docs/language.md` — English/LTR setup in detail
- `docs/data-model.md` — every Room table and column, migration history v1 → v17, deleting a trip
- `docs/sync.md` — shared trips, Firestore layout, legacy migration, SyncManager, lost trips, merge table, status and
  sign-out, security rules, known limits, stage 3 plan, invites and joining
- `docs/screens.md` — navigation, Home, trip screen (Overview, Budget + splitting, expense currency, Plan, Prep,
  currency card, assistant, App Check, budget rules), Share PDF, deleting a trip, auth screen, trip form
- `docs/design.md` — colors, hue table, shapes, typography, sizes, spacing, components
- `docs/places-and-photos.md` — destination suggestions, flags, destination photos
- `docs/reminders.md` — trip reminders: scheduling, delivery, permissions, battery tip, known limits
- `docs/testing.md` — what every test suite covers, the fakes
