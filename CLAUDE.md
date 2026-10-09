# Plan Your Trip — Android app

Trip-planning app. Kotlin + Jetpack Compose + Material 3, single Activity (`MainActivity`) hosting a
Navigation Compose graph. Firebase Authentication (email/password); trips, budgets, expenses, travelers, settlements and itinerary activities
are stored locally with Room, per account, and synced with Cloud Firestore (Room stays the only source for the screens,
see "Cloud sync"). The cloud is laid out for **shared trips** (`trips/{tripId}` with an owner and members, personal data
under `memberData/{uid}`; see "Shared trips"); stage 1 of 3 is done: the structure, the uid traveler keys and the
migration, with no visible change except pull to refresh on Home and the trip screen. Inviting and joining (stage 2) and
the multi-member polish (stage 3) are not built yet. Exchange rates come from the free Frankfurter API and are cached in Room.
An AI trip assistant (Gemini through Firebase AI Logic, protected by Firebase App Check) chats about the trip and can
add suggested day plans to the itinerary. "Share trip" creates a PDF summary (Android `PdfDocument`) and opens the
share sheet. The trip screen's third tab, **Prep**, holds two checklists ("Before the trip" shared, "Packing" personal;
both synced) and travel documents (PDFs/images copied into private storage, **on this device only**). A trip is deleted
by swiping its Home card to the left or with "Delete trip" in its three-dots menus (see "Deleting a trip"). Expenses
can be split between travelers (names only, not accounts); the budget counts only the user's own share (see "Budget tab
and expense splitting"). Maps are links only: the
Overview's Map card (a drawn illustration) opens Google Maps on the destination and each Plan activity with a location
has an "Open in Maps" button (a URL, no SDK, no key, no location permission).
Package: `com.example.planyourtrip`. minSdk 26, targetSdk 36.

## Commands
- Build: `./gradlew assembleDebug` (also regenerates the Room schema JSON in `app/schemas/`)
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests (needs an emulator): **never** `connectedDebugAndroidTest` (it wipes app data and the App Check
  token). Instead: airplane mode on, `assembleDebug assembleDebugAndroidTest`, `adb install -r` both APKs,
  `adb shell am instrument -w com.example.planyourtrip.test/androidx.test.runner.AndroidJUnitRunner`, then uninstall
  only `com.example.planyourtrip.test`.
- Release build: `./gradlew assembleRelease` (unsigned APK; must compile too, see the Compose BoM note in Conventions)
- Firestore security rules: `firestore.rules` (repo root). Not deployed by the build: paste the file into Firebase
  console → Firestore Database → Rules → Publish after every change.
- Security rules tests (Firebase Emulator, never the real project; project id `demo-planyourtrip`): `cd rules-tests &&
  npm install` once, then `JAVA_HOME=<Android Studio jbr> PATH="$JAVA_HOME/bin:$PATH" npm test` (runs
  `firebase emulators:exec --only firestore "mocha rules.test.js"` with `../firebase.json`). Run after every rules change.
- Firebase config: `app/google-services.json` (project `planyourtrip-79724`, package `com.example.planyourtrip`).
  The Google services plugin only reads it from `app/`.
- Gradle 8.13 fails on the system JDK 24 ("Type T not present"). From the terminal, use Android Studio's JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

## Structure
```
app/src/main/java/com/example/planyourtrip/
  MainActivity.kt              # PlanYourTripTheme { AppNavHost() }; attachBaseContext → withAppLocale()
  AppLocaleSetup.kt            # Context.withAppLocale() (English + LTR resources), applyDefaultAppLocale()
  PlanYourTripApplication.kt   # applyDefaultAppLocale() (also on config change), installAppCheck(), creates the
                               # AppContainer, syncManager.start();
                               # CreationExtras.appContainer() helper
  AppContainer.kt              # manual DI: AuthRepository, AppDatabase, TripRepository, ExpenseRepository,
                               # ActivityRepository, CurrencyRepository, AssistantRepository, Clock,
                               # SyncManager (+ syncController, SyncFlags), TripPdfExporter, ChecklistRepository,
                               # TravelerRepository, SettlementRepository,
                               # DocumentFileStore, DocumentRepository (+ cleanUpDocumentFiles()), app-wide CoroutineScope
  data/auth/                   # AuthRepository (interface), AuthUser, AuthError/AuthException,
                               # FirebaseAuthRepository (the only Firebase Auth code)
  data/trip/                   # Trip, TripColor, TripEntity, TripDao, TripRepository + RoomTripRepository,
                               # Expense (+ ExpenseInput: the values to add/update), ExpenseCategory, ExpenseEntity, ExpenseDao,
                               # ExpenseRepository + RoomExpenseRepository, ActivityType, TripActivity (+ ActivityInput),
                               # ActivityEntity, ActivityDao, ActivityRepository + RoomActivityRepository,
                               # DeletedActivity (+ DeletedActivityStore: the "Undo" hand-over),
                               # ChecklistKind + ChecklistItem (+ DeletedChecklistItem), ChecklistItemEntity, ChecklistDao,
                               # ChecklistRepository + RoomChecklistRepository,
                               # Travelers (LEGACY_ME_KEY = "me", Traveler, Settlement, RemoveTravelerResult), TravelerColor,
                               # TravelerEntity/TravelerDao, SettlementEntity/SettlementDao, TravelerRepository +
                               # SettlementRepository (+ RoomTravelerRepository, RoomSettlementRepository),
                               # Converters, AppDatabase (v14), Migrations (MIGRATION_6_7, MIGRATION_10_11, MIGRATION_13_14)
  data/document/               # Prep tab documents (this device only): DocumentType, TripDocument, DocumentInput, ViewFile,
                               # DocumentEntity, DocumentDao, DocumentRepository + RoomDocumentRepository (also the sync's
                               # TripCleanup), DocumentFileStore (interface) + AndroidDocumentFileStore (the only code that
                               # reads picked files / touches stored ones), DocumentFiles (pure: checkPickedFile,
                               # MAX_DOCUMENT_BYTES = 20 MB, documentKind, extensionFor, defaultDocumentName)
  data/currency/               # Currency, ExchangeRate, ILS, ExchangeRateEntity/Dao (rate cache),
                               # CurrencyRepository (+ isStale, RATE_MAX_AGE) + RoomCurrencyRepository,
                               # RatesApi + FrankfurterRatesApi (the only rates HTTP code), FrankfurterJson (pure parsers)
  data/assistant/              # AssistantRepository (interface; AssistantRequest, ChatTurn, AssistantResponse,
                               # AssistantError), FirebaseAssistantRepository (the only Firebase AI code + JSON schema),
                               # AssistantPrompt (pure: AssistantModel, assistantInstructions, classifyServerMessage),
                               # AssistantJson (pure: parseReply → AssistantReply / SuggestedPlan / SuggestedActivity)
  data/sync/                   # cloud sync: SyncModels (pure: SyncKind, SyncItem, ItemData, LocalItem, SyncStatus),
                               # SyncMerge (pure: planMerge → MergePlan), SyncDocuments (pure: Firestore fields),
                               # LegacyMigration (pure: convertLegacy, planLegacyCopy, isLegacyCopyComplete),
                               # CloudStore (interface) + FirestoreCloudStore (the only Firestore code),
                               # SyncLocalStore (interface) + RoomSyncLocalStore + SyncDao, DeletionEntity (+ ChildRef),
                               # NetworkMonitor + AndroidNetworkMonitor, SyncController (+ SignOutResult,
                               # LocalChangeListener, TripCleanup), SyncManager, SyncFlags + SharedPreferencesSyncFlags
                               # (one-time jobs per account: the legacy copy's move to shared trips)
  data/user/                   # CurrentUser (AuthUser.greetingName()), UserNames (firstName()/initials())
  ui/navigation/               # Routes.kt (type-safe routes), AppNavHost.kt, SessionViewModel
  ui/auth/                     # AuthScreen, AuthViewModel, AuthUiState, AuthValidator
  ui/home/                     # HomeScreen, HomeViewModel, HomeUiState, TripGrouping,
                               # components/ (NextTripCard, TripCards: upcoming + past list cards,
                               # SwipeToDeleteBox: swipe left → red "Delete" area)
  ui/tripform/                 # TripFormScreen/ViewModel/UiState, TripValidator — new trip AND edit trip
  ui/tripdetails/              # the trip area: TripDetailsScreen (scaffold, header, nav bar, FABs; EmptyMessage,
                               # cardPadding), OverviewTab / BudgetTab (+ BudgetActions, AddExpenseFab) / PlanTab (the
                               # tabs' LazyListScope builders), TripDetailsPreviews (all previews), TripDetailsViewModel
                               # (+ expenseSplits: each row's ExpenseSplitInfo),
                               # TripDetailsUiState (+ TripTab, BudgetDialogState, CurrencyCardState, ItineraryState,
                               # PlanDayState, OverviewDayState),
                               # BudgetCalculations (pure), CurrencyConversion (pure: converter math + typing reducer),
                               # Itinerary (pure: planned days, default day, titles, daily cost),
                               # GoogleMaps (pure: googleMapsSearchUrl = the Overview Map card's link,
                               # googleMapsActivityUrl = a Plan card's "Open in Maps"),
                               # components/ (TripHeader, BudgetCard,
                               # CurrencyCard, CategoryChartCard, BudgetDialog, FeatureCard,
                               # IconChip/CategoryIcon, TravelerAvatar (+ avatarColors, personName),
                               # ExpenseRow (+ ExpenseSplitInfo), ActivityTimeline (TimelineRow, ActivityCard,
                               # CompactActivityCard, TagPill), ItineraryComponents (ItineraryActions, ItineraryHeader,
                               # DayChipsRow, DaySummaryCard, AddActivityButton, PlanTimeline, DailyPlanProgress,
                               # DayPreviewCard), OverviewMapIllustration (the Map card's drawn map + pill)),
                               # prep/ (PrepViewModel, PrepUiState (+ ChecklistState, DocumentFormState, PrepMessage),
                               # PrepProgress (pure: prepProgress, listCount, progressMessage, dueState, itemsOf,
                               # suggestionsToAdd), PrepTab (prepTab, PrepActions, PrepProgressCard),
                               # DocumentComponents (cards, form + delete dialogs, DocumentType icons/labels),
                               # ChecklistComponents (chips, rows, "Add item…", edit-text dialog),
                               # PrepHost (rememberPrepActions: file picker, viewer, snackbars; PrepDialogs)),
                               # split/ (SplitViewModel (+ validateSettleAmount), SplitUiState (+ TravelerDialogState,
                               # SettleSheetState, SettlePage, SettleAmountError), SplitComponents (SplitActions,
                               # TravelersSection, WhoOwesWhomCard, TransferRow), SplitDialogs (TravelerDialog,
                               # SettleUpSheet, delete-payment dialog)),
                               # expense/ (ExpenseFormScreen/ViewModel/UiState (+ ForeignRateState), ExpenseValidator,
                               # ExpenseCurrency (pure: per-currency parsing, conversion to agorot, save rule)),
                               # activity/ (ActivityFormScreen/ViewModel/UiState, ActivityValidator),
                               # assistant/ (AssistantScreen, AssistantViewModel, AssistantUiState (+ ChatMessage,
                               # PlanCardState, AddStatus), SuggestedPlanCard, AssistantContext (pure: buildTripContext,
                               # AssistantChip + suggestionChips, chatHistory, planCard), Suggestions (pure:
                               # suggestionInputs))
  ui/common/                   # code shared by several features:
                               #   AppLocale (the one app locale, Locale.US, default of every formatter),
                               #   UiSharing (WhileUiSubscribed: every ViewModel's stateIn policy, 5 s),
                               #   TripFormatting (dates, cached formatters), TripTexts (datesWithLength,
                               #   statusLabel), TripStatus (tripStatus()), Money (agorot format/parse, percent,
                               #   shekels()), PickerDates (DatePicker UTC conversion), ErrorText (errorSupportingText),
                               #   CurrencyPicker (CurrencyPickerState, filterCurrencies, currencyName),
                               #   TripDays (pure: tripDays, daysInclusive, dayNumber, isWithinTrip, sortActivities,
                               #   activitiesOn),
                               #   CategoryTotals (pure: categoryTotals(amountOf, includeZeroCategories) + CategoryTotal),
                               #   ExpenseCategoryUi / ActivityTypeUi (iconRes()/labelRes())
  ui/common/split/             # expense splitting, pure: Split (travelerOrder, inListOrder, shares, myShare, myTotal,
                               #   isShared, sharedExpenseCount, balances, simplifyTransfers + Transfer,
                               #   isTravelerUsed), People (Person: You / Named / Removed, people, personOf,
                               #   avatarLetter, validateTravelerName + TravelerNameError)
ui/common/share/             # "Share trip" PDF: TripPdfContent (pure: model, buildTripPdfContent, pdfFileName),
                               #   PdfPagination (pure: paginate), TripPdfExporter (interface, SharedPdf),
                               #   AndroidTripPdfExporter (the only PdfDocument/FileProvider code), ShareTripViewModel,
                               #   ShareTripUiState, ShareTripDialog (+ ShareTripHost: dialog + share sheet)
  ui/common/delete/            # "Delete trip": DeleteTripViewModel (+ DeleteTripUiState, DeleteDialogState,
                               #   DeleteTripMessage), DeleteTripDialog (+ DeleteTripHost: the confirmation dialog)
  ui/common/account/           # AccountViewModel (+ AccountState, actions()): the profile menu's user, sync status and
                               #   sign out (Home avatar)
  ui/common/refresh/           # RefreshViewModel (+ RefreshState): pull to refresh (a full sync) on Home and the trip screen
  ui/common/components/        # PrimaryButton (optional leadingIcon), BackTopAppBar (optional actions), BrandMark,
                               #   AccountMenu (AccountButton + AccountActions: profile button, menu, sign-out warning),
                               #   DateField (read-only field + DatePickerPopup, also used alone), HeroWaves (gradient decoration),
                               #   TripComponents (LetterSquare, Pill, TripDatesRow),
                               #   TripMoreMenu (+ TripMoreButton: the trip's three-dots menu, "Share trip", "Delete trip"),
                               #   CurrencyPickerSheet (searchable currency list, trip form + trip screen),
                               #   TimeField (read-only time field + 24-hour TimePicker dialog; activity form),
                               #   EditFormScaffold (+ FormDelete: the expense/activity form shell: top bar with delete,
                               #   loading gate, scrolling fields, Save), ConfirmDeleteDialog (title, body, red Delete,
                               #   Cancel), TintedFilterChip (category/type chip in its TintedColor),
                               #   RefreshBox (PullToRefreshBox with the spinner below the status bar)
  ui/theme/                    # Color, ExtendedColors, Shape, Type (Inter + named styles), Dimens, Theme,
                               # PdfColors (ARGB ints for the shared PDF, built from Color/ExtendedColors)
app/src/debug/.../AppCheckSetup.kt    # installAppCheck(): App Check debug provider
app/src/release/.../AppCheckSetup.kt  # installAppCheck(): Play Integrity provider
app/schemas/                   # exported Room schemas — commit them
design/home_screen.png         # visual reference for the home screen and the app theme
design/trip_overview.png       # visual reference for the trip screen (Overview tab)
design/trip_plan.png           # visual reference for the Plan tab (see "Plan tab" for the deliberate differences)
design/trip_prep.png           # visual reference for the Prep tab (see "Prep tab" for the deliberate differences)
design/trip_budget.png         # visual reference for the Budget tab (see "Budget tab and expense splitting")
design/trip_assistant.png      # visual reference for the trip assistant (see "AI trip assistant")
third_party/inter/OFL.txt      # Inter font license
firestore.rules                # Firestore security rules (paste into the console)
firebase.json                  # Firebase Emulator config (rules tests only; nothing is deployed from it)
rules-tests/                   # Firestore rules tests: rules.test.js (mocha + @firebase/rules-unit-testing), package.json
design/trip_members.png        # stage 2 reference: the Overview's "Traveling together" card (only that card)
design/trip_invite.png         # stage 2 reference: the invite sheet
design/home_join.png           # stage 2 reference: the join sheet
app/src/main/res/xml/file_paths.xml  # FileProvider paths: cache/shared_pdfs/ and files/documents/
```

## Architecture decisions
- **Unidirectional data flow.** Each ViewModel exposes an immutable UI state in a `StateFlow`; screens collect it
  with `collectAsStateWithLifecycle()` and send events back as lambdas.
- **Stateful/stateless split.** `XxxScreen` only wires the ViewModel; `XxxContent` takes state + lambdas, so it
  can be previewed and UI-tested without a ViewModel. Preview sample data lives only in preview functions.
- **Pure UI state stays in Compose** (password visibility, "date picker open"); everything else lives in the ViewModel.
- **Manual DI, no framework yet.** `AppContainer` owns singletons. Every ViewModel takes its dependencies as
  constructor parameters and exposes a
  `companion object { val Factory = viewModelFactory { initializer { ... appContainer() ... } } }`;
  screens call `viewModel(factory = XxxViewModel.Factory)`. Tests pass fakes directly. Move to Hilt if wiring grows.
- **Time and dispatchers are injected.** `HomeViewModel` and `TripDetailsViewModel` take a `java.time.Clock` (tests
  fix "today") and a `computeDispatcher` (default `Dispatchers.Default`; tests pass their test dispatcher so no work
  outlives a test). `ExpenseFormViewModel` takes the `Clock` for the default expense date and the rate's 12-hour check.
- **Document files stay behind an interface too.** Only `data/document/AndroidDocumentFileStore.kt` reads picked files
  (`ContentResolver`) and touches `filesDir/documents/`; repositories and `PrepViewModel` see `DocumentFileStore` (uris as
  `String`), tests use `FakeDocumentFileStore`. The picker and the viewer intent live in `PrepHost.kt`.
- **The PDF stays behind an interface too.** Only `ui/common/share/AndroidTripPdfExporter.kt` uses `PdfDocument`,
  `StaticLayout` and `FileProvider`; `ShareTripViewModel` sees `TripPdfExporter`, tests use `FakeTripPdfExporter`.
  Screens only add `ShareTripHost(shareViewModel)` and call `onShareTrip(tripId)`.
- **No map SDK.** Maps are Google Maps URLs opened with `ACTION_VIEW` (`GoogleMaps.kt`); there is no map library,
  place search or location permission in the app.
- **Firebase stays behind interfaces.** Only `data/auth/FirebaseAuthRepository.kt`,
  `data/assistant/FirebaseAssistantRepository.kt`, `data/sync/FirestoreCloudStore.kt`, the two `AppCheckSetup.kt` files
  and `AppContainer.kt` import Firebase. ViewModels see `AuthRepository`/`AuthUser`/`AuthError`,
  `AssistantRepository`/`AssistantError` and `SyncController`/`SyncStatus`; tests use `FakeAuthRepository`,
  `FakeAssistantRepository`, `FakeSyncController`, `FakeCloudStore` and `FakeSyncLocalStore`.
- **ViewModels have no Android `Context`.** Validation returns enum codes (`FieldError`, `TripFieldError`,
  `ExpenseFieldError`, `BudgetFieldError`); the UI maps them to string resources.
- **Package rules.** Each feature package (`auth`, `home`, `tripform`, `tripdetails`) owns its screen, ViewModel,
  `XxxUiState.kt` and validator. Anything used by more than one feature lives in `ui/common` (or
  `ui/common/components` for composables) — features never import from each other.
- **Heavy work stays off the main thread.** Room and Firebase calls are `suspend`/`Flow`; `HomeViewModel` groups
  trips and `TripDetailsViewModel` computes budget totals with `flowOn(computeDispatcher)`.
- **One-shot results are state flags.** `AuthUiState.isAuthenticated`, `TripFormUiState.saved`,
  `ExpenseFormUiState.done` and `ActivityFormUiState.done` are observed in a `LaunchedEffect` that triggers navigation.
- **Edit forms reuse the create forms.** `TripForm(tripId?)`, `ExpenseForm(tripId, expenseId?)` and
  `ActivityForm(tripId, activityId?, dateEpochDay?)` create when the id is null and edit otherwise; the ViewModel loads the item once (`isLoading` hides the fields until then) and
  saving sets `isSaving` synchronously, so double taps save once.

## Language and layout direction (always English, left to right)
The app looks and behaves as in English on every phone, also when the phone's language is Hebrew, Arabic or any
other RTL language. There are no translations (`res/values` only).
- **One locale:** `AppLocale.locale` (`ui/common/AppLocale.kt`, `Locale.US`). Every formatter (`TripFormatting`,
  `Money`, `CurrencyConversion.formatRate`/`currencySymbol`, `ExpenseCurrency.formatOriginal`, `currencyName`) takes
  it as its **default** `locale` parameter — never `Locale.getDefault()`. Formats that must not depend on any locale
  (Firestore "HH:mm", `Money.toInput`) use `Locale.ROOT`. `AppLocaleTest` runs every formatter with a he-IL, ar-EG,
  fa-IR and de-DE JVM default and expects the English output; add new formatters there.
- **Resources:** `MainActivity.attachBaseContext` wraps the context with `withAppLocale()` (`AppLocaleSetup.kt`):
  a configuration context with `setLocale(AppLocale.locale)` + `setLayoutDirection(...)`. Compose reads
  `LocalConfiguration`/`LocalLayoutDirection` from it, so every screen, dialog, menu, bottom sheet, snackbar and the
  Material date/time pickers (month and day names, labels) are English and LTR. Works on all API levels (26+),
  no AppCompat or per-app language API needed.
- **Layout direction, twice more:** the manifest has `android:supportsRtl="false"` (the View system, which Compose's
  `onRtlPropertiesChanged` follows, stays LTR), and `androidResources.localeFilters += "en"` in `app/build.gradle.kts`
  drops the libraries' translations from the APK, so built-in labels are English even outside the wrapped context.
- **JVM default:** `applyDefaultAppLocale()` sets `Locale.setDefault(AppLocale.locale)` for library code that reads
  it, in `Application.onCreate`, `onConfigurationChanged` (Android resets the default when the system language
  changes) and every `withAppLocale()`.
- **User text in Hebrew/Arabic** (destinations, titles, notes, expense names, chat messages) keeps Compose's
  default text direction: in an LTR layout it resolves to `ContentOrLtr`, so a paragraph whose first strong
  character is RTL reads right to left (bidi for mixed text) without mirroring the layout. Never set
  `TextDirection.Ltr` on text that can hold user input. String resources that embed user text start with fixed or
  LTR text ("%1$s · %2$s" = dates · destination), so the line order stays as in English.
- The PDF keeps its own RTL paragraph handling (`FIRSTSTRONG_LTR`); its fixed labels are English strings.
- Money stays "₪2,550" (`money_shekels` = "₪%1$s", grouping from `AppLocale`).

## Data model (Room)
Database `plan_your_trip.db`, `AppDatabase` version 14, nine tables:

| `trips` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `owner_id` | TEXT, indexed | uid of the **signed-in account this local copy belongs to** (default `""`; the name predates shared trips). Every query scopes by it |
| `destination` | TEXT | trimmed before insert, never blank |
| `start_date` | INTEGER | `LocalDate` stored as epoch day |
| `end_date` | INTEGER | epoch day, `end_date >= start_date` (validated in the form) |
| `color` | TEXT | `TripColor` enum **name** |
| `budget_agorot` | INTEGER, nullable | **my own** budget in agorot (personal, `memberData/{uid}`); `null` = no budget (a new trip has none) |
| `currency_code` | TEXT, nullable | ISO 4217 code of the destination currency ("EUR"); `null` = not chosen yet |
| `uuid` | TEXT, unique with `owner_id` | stable id, the same in Room and Firestore (the document id); see sync columns below |
| `updated_at` | INTEGER | epoch millis of the last change on any device |
| `pending_sync` | INTEGER | Boolean, 1 = changed here and not uploaded yet |
| `cloud_owner_id` | TEXT, default `''` | uid of the trip's owner in the cloud (who created it; only they can delete it) |
| `member_ids` | TEXT, default `''` | the members' uids, joined by `,`, in the cloud's order (owner first, then join order) |
| `budget_updated_at` | INTEGER, default 0 | epoch millis of the last budget change; 0 = never set (no `BUDGET` item) |
| `budget_pending` | INTEGER, default 0 | Boolean, the budget still has to be uploaded (`setBudget` never touches `updated_at`/`pending_sync`) |

| `expenses` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** (deleting a trip deletes its expenses) |
| `name` | TEXT | trimmed, never blank |
| `amount_agorot` | INTEGER | > 0; the **shekel** amount the budget uses, also for foreign-currency expenses |
| `category` | TEXT | `ExpenseCategory` enum **name**; unknown names fall back to `OTHER` |
| `date` | INTEGER | epoch day |
| `original_amount` | TEXT, nullable | amount as typed in a foreign currency, exact `BigDecimal` text ("10.00", "1500") |
| `original_currency` | TEXT, nullable | ISO 4217 code of that amount |
| `exchange_rate` | TEXT, nullable | shekels per 1 unit used for the conversion, exact `BigDecimal` text |
| `rate_date` | INTEGER, nullable | epoch day of that rate (from the rates service) |
| `paid_by` | TEXT, default `'me'` | traveler key of the payer: a **member's uid** (the signed-in one is "You") or a traveler `uuid`. The SQL default is the legacy `'me'` (SQLite can't change it); every insert passes the key |
| `split_between` | TEXT, default `'me'` | traveler keys sharing it equally, joined by `,` (`Converters`), never empty; `[payer]` = "not shared" |

The four `original_*`/`exchange_rate`/`rate_date` columns are all set or all null (null = entered in shekels);
they map to `Expense.original: ForeignAmount?`. `paid_by`/`split_between` map to `Expense.paidBy`/`splitBetween`
(an empty split reads as the payer only); the repositories drop duplicates and store an empty split as `[paidBy]`.

**Sync columns:** `trips`, `expenses` and `activities` all have `uuid` (TEXT NOT NULL, a random UUID made at insert;
unique per account: `trips(owner_id, uuid)`, children `(trip_id, uuid)`, so two accounts on one phone can each hold a
copy of a shared trip), `updated_at` (INTEGER, epoch millis, set from the injected `Clock` on every insert/update) and
`pending_sync` (INTEGER, default 1). The Long `id` stays the local primary key (routes, ViewModels and repository
interfaces use it); it differs between devices and is never synced.

| `deletions` column | Type | Notes |
|---|---|---|
| `uuid` | TEXT, PK with `owner_id` | the deleted item's uuid |
| `owner_id` | TEXT, indexed, PK with `uuid` | the account that deleted it |
| `kind` | TEXT | `SyncKind` name: `TRIP`, `TRAVELER`, `EXPENSE`, `ACTIVITY`, `CHECKLIST`, `SETTLEMENT`, `PACKING` (`BUDGET` is never deleted on its own) |
| `trip_uuid` | TEXT, nullable | the trip of a child item (the cloud path needs it); null for a trip |
| `deleted_at` | INTEGER | epoch millis of the delete |

A row exists only while the delete waits to upload (local tombstone).

| `activities` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** (deleting a trip deletes its activities) |
| `date` | INTEGER | epoch day; may fall outside the trip after its dates are edited (never deleted for that) |
| `start_time` | INTEGER, nullable | `LocalTime` as minute of the day (0..1439); `null` = no time |
| `title` | TEXT | trimmed, never blank |
| `note` | TEXT, nullable | trimmed; blank is stored as `null` |
| `type` | TEXT | `ActivityType` enum **name**; unknown names fall back to `OTHER` |
| `location` | TEXT, nullable | free text, trimmed; blank is stored as `null`; optional for every type ("Open in Maps" searches it) |
| `estimated_cost_agorot` | INTEGER, nullable | > 0 when set (validated in the form); `null` = no estimate |
| `booked` | INTEGER | Boolean |

- **Deleting an activity** (`ActivityDao.deleteAndRecord`) records an `ACTIVITY` deletion. `deleteActivity` returns a
  `DeletedActivity` (the activity) and `restoreActivity` ("Undo") inserts it again as a **new** row with a new uuid
  (the recorded delete still uploads, so no uuid is reused).

| `checklist_items` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `list` | TEXT | `ChecklistKind` name: `BEFORE_TRIP`, `PACKING` (unknown → `BEFORE_TRIP`) |
| `text` | TEXT | trimmed, never blank |
| `checked` | INTEGER | Boolean |
| `due_date` | INTEGER, nullable | epoch day; only ever set on `BEFORE_TRIP` items (the DAO's update has `AND list = 'BEFORE_TRIP'`) |
| `created_at` | INTEGER | epoch millis when added (synced); lists are ordered `created_at, id` (creation time, then insertion order), the same on every device. `ChecklistDao.insertInOrder` (one transaction) gives every new item `max(now, latest created_at of the trip + 1)` and suggested items +1 ms each from there, so no two items of a trip tie on a device, even in the same millisecond or with a clock set back; "Undo" keeps the old value, so the item returns to its place |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns as above |

- **Deleting a checklist item** (`ChecklistDao.deleteAndRecord`) records a `CHECKLIST` deletion; `restoreItem` inserts
  a new row with a new uuid (like activities).

| `documents` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `owner_id` | TEXT, indexed | the account that added it |
| `trip_uuid` | TEXT, indexed | the trip's **uuid**, **no foreign key**: a clean sign-out deletes the account's trips but keeps its documents, which belong to the trip again when it comes back from the cloud with the same uuid |
| `uuid` | TEXT, unique | random; also the stored file's name (`<uuid>.pdf`, `.jpg`, …) |
| `name` | TEXT | trimmed, never blank |
| `type` | TEXT | `DocumentType` name: `FLIGHT, LODGING, INSURANCE, TICKET, OTHER` (unknown → `OTHER`) |
| `detail` | TEXT, nullable | optional second line; blank → null |
| `file_name` | TEXT | name inside `filesDir/documents/` |
| `mime_type` | TEXT | `application/pdf` or `image/*` (badge "PDF" / "Photo") |
| `size_bytes` | INTEGER | ≤ 20 MB |
| `created_at` | INTEGER | epoch millis; the cards are ordered `created_at, id` |

Documents are **never synced** (no sync columns). `DocumentDao.observeForTrip` joins `trips` on uuid **and** owner.

| `travelers` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `name` | TEXT | trimmed, never blank, unique in the trip ignoring case, never "You" (checked in the UI) |
| `color` | TEXT | `TravelerColor` name (`ROSE, INDIGO, PURPLE, SKY, GREEN, GREEN_TEAL`; unknown → first), round-robin by the trip's traveler count |
| `created_at` | INTEGER | epoch millis; the list order (`created_at, id`), always after the trip's last traveler (`max(now, latest + 1)`) |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns; `uuid` is also the traveler's **key** in expenses and settlements |

Trip **members are not rows**: a member's key is their account uid (the trip's `member_ids`); the signed-in member is
shown as "You", never renamed or removed here. Before shared trips the owner's key was the fixed text `"me"`
(`LEGACY_ME_KEY`, `data/trip/Travelers.kt`); the v14 migration and the cloud migration replaced it with the owner's uid. `TravelerDao.removeIfUnused`
(one transaction) returns `IN_USE` when an expense of the trip has `paid_by` = the uuid or the uuid in
`split_between` (`instr` on `,list,`) or a settlement uses it; otherwise records a `TRAVELER` deletion and deletes.

| `settlements` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** |
| `from_traveler` | TEXT | key of who paid (`me` or a traveler uuid) |
| `to_traveler` | TEXT | key of who received it; never equal to `from_traveler` |
| `amount_agorot` | INTEGER | > 0 |
| `date` | INTEGER | epoch day (today when marked as paid) |
| `created_at` | INTEGER | epoch millis; history is `date DESC, created_at DESC, id DESC` |
| + `uuid`, `updated_at`, `pending_sync` | | sync columns |

Deleting a settlement (`SettlementDao.deleteAndRecord`) records a `SETTLEMENT` deletion; the debt it paid reopens.

| `exchange_rates` column | Type | Notes |
|---|---|---|
| `currency_code` | TEXT PK | one cached rate per currency, shared by all accounts (rates are public) |
| `rate` | TEXT | shekels per 1 unit, exact `BigDecimal` text ("3.4408"), never a float |
| `rate_date` | INTEGER | epoch day of the rate's date **as returned by the service** |
| `fetched_at` | INTEGER | epoch millis of when this device fetched it (drives the 12-hour refresh) |

- **Money is whole agorot in `Long`** (100 agorot = ₪1), never `Float`/`Double`. Text input is parsed with
  `Money.parseAgorot` (digits, one `.` or `,`, ≤ 2 decimals, ≤ ₪1,000,000,000; `parsePositiveAgorot` when it must
  be > 0: budget, activity cost, settle amount); display goes through
  `Money.formatAgorot` / `shekels()` ("₪2,550", "₪12.50"). Doubles are used only for displayed percentages.
- `ExpenseCategory` = `FLIGHTS, HOTELS, FOOD, SHOPPING, TRANSPORT, ATTRACTIONS, OTHER`, same naming rules as `TripColor`.
  Icons/labels live in `ui/common/ExpenseCategoryUi.kt`, colors in `ExtendedColors.categories`.
- `ActivityType` = `ACTIVITY` (label "Attraction"), `DINING, SHOPPING, LODGING, TRANSPORT, OTHER` (Shopping: green,
  `ic_shopping_bag`, same hue as the Shopping category), same naming rules. Icons/labels in
  `ui/common/ActivityTypeUi.kt`, colors in `ExtendedColors.activityTypes` (one hue per type, see Design rules).
  The domain model is `TripActivity` (not `Activity`, which would clash with `android.app.Activity`); the form
  saves an `ActivityInput`, which repositories `normalized()` (trim, blank optionals → null).

- `TripEntity` is the Room row; `Trip` is the domain model used by the UI (mapped with `toTrip()`).
  The UI never sees Room types, so storage can change later (e.g. Firestore sync).
- `TripColor` = `SKY, PEACH, MINT, LAVENDER, ROSE, LEMON`. It is assigned automatically when a trip is created,
  round-robin by the number of the owner's existing trips (`TripColor.forIndex(dao.count(ownerId))`), so each
  account starts at the first color. Stored by name, so entries can be
  reordered or appended, but never renamed or removed without a migration (an unknown name falls back to the first
  color). Every stored enum (`TripColor`, `ExpenseCategory`, `ActivityType`, `ChecklistKind`, `DocumentType`,
  `TravelerColor`) has a `fromName(name)` companion with its fallback; Room `Converters` and `SyncDocuments` both use it.
  The actual colors live in the theme (`ExtendedColors.pastels`), with light and dark variants.
- **Every query is scoped to one owner** (`observeAll(ownerId)`, `observeById(id, ownerId)`, `count(ownerId)`,
  `update`/`setBudget` with `AND owner_id = :ownerId`), so an account never sees or changes another account's trips.
  Expense and activity queries join/sub-select `trips` on `owner_id`; `addExpense` / `addActivity` return null
  for a trip the owner doesn't own. `TripRepository` takes `ownerId` explicitly; ViewModels pass
  `authRepository.currentUser.value?.uid` (New Trip does nothing and Details shows nothing without a user).
- `observeAll` returns trips ordered by `start_date, id`. Grouping into home sections happens in Kotlin.
  Expenses are returned newest first (`date DESC, id DESC`).
- **Migrations:** v1 → v2 is an `AutoMigration` that adds `owner_id`. Trips created before v2 get `""` and are shown
  to no account. v2 → v3 is an `AutoMigration` that adds `trips.budget_agorot` (null) and creates `expenses`;
  existing trips are kept. v3 → v4 is an `AutoMigration` that adds `trips.currency_code` (null) and creates
  `exchange_rates`; existing trips and expenses are kept. v4 → v5 is an `AutoMigration` that creates `activities`;
  all existing data is kept. v5 → v6 is an `AutoMigration` that adds the four nullable foreign-amount columns to
  `expenses`, so every existing expense stays a shekel expense. v6 → v7 is the **manual** `MIGRATION_6_7`
(`Migrations.kt`, SQL copied from `7.json`): adds the three sync columns, gives every existing row its own
`UUID.randomUUID()` (SQLite can't), sets `updated_at` to the migration time and `pending_sync = 1` (so the first sync
uploads everything), then creates the unique indexes and `deletions` (`MigrationTest` in androidTest). v7 → v8,
v8 → v9 and v9 → v10 are `AutoMigration`s from the removed Map tab (`places`, `activities.place_id`,
`places.auto_matched`, `activities.place_lookup`, `places.added_on_map`; `MigrationTest.migrate7To8…/8To9…/9To10…`).
v10 → v11 is the **manual** `MIGRATION_10_11` (SQL from `11.json`): an activity linked to a place whose `location` is
null or blank gets the place's name as its location, with `updated_at` = the migration time and `pending_sync = 1` (so
the text uploads; activities that already had a location are untouched); then `activities` is rebuilt without
`place_id`/`place_lookup` (SQLite can't drop an FK column), `places` is dropped and `PLACE` rows are removed from
`deletions`. Every trip, expense and activity is kept (`MigrationTest.migrate10To11…`). v11 → v12 is an
`AutoMigration` that creates `checklist_items` and `documents` empty; all existing data is kept
(`MigrationTest.migrate11To12…`). v12 → v13 is an `AutoMigration` (expense splitting) that adds
`expenses.paid_by` / `split_between` with the default `'me'` (every existing expense becomes "paid by me, not
shared", so every trip keeps exactly its numbers; rows keep their `updated_at`/`pending_sync`, nothing re-uploads)
and creates `travelers` and `settlements` empty (`MigrationTest.migrate12To13…`). v13 → v14 is the **manual**
`MIGRATION_13_14` (shared trips, SQL from `14.json`): `paid_by`, the `me` token in `split_between` and settlement keys
`me` become the trip's `owner_id` (trips with `owner_id = ''` are left as they are); `trips` gets `cloud_owner_id` =
`member_ids` = `owner_id`, `budget_updated_at` = `updated_at`, `budget_pending` = `pending_sync`; unique indexes
become `trips(owner_id, uuid)` / children `(trip_id, uuid)`; `deletions` is rebuilt with the PK `(owner_id, uuid)`.
`updated_at`/`pending_sync` of existing rows are untouched (the cloud migration rewrites the cloud copy the same way),
and the owner stays first in the list order, so every share, balance and transfer keeps its exact value
(`MigrationTest.migrate13To14…`, `SplitTest.theOwnersUid_givesExactlyTheSameNumbersAsTheOldMeKey`). Room turns on
`PRAGMA foreign_keys` itself (the cascade is covered in `TripDaoTest`).
- **Never seed sample trips into the database.** Sample data belongs only in previews and tests.
- **Changing the schema:** bump `version`, add a `Migration` or `AutoMigration`, build, and commit the new JSON in
  `app/schemas/`. Do not use `fallbackToDestructiveMigration` — users would lose their trips.
- **Deleting a trip** (`TripRepository.deleteTrip` → `TripDao.deleteAndRecord`, one transaction): records a `TRIP`
  row in `deletions`, deletes the trip (the FK cascade removes its expenses, activities, checklist items, travelers
  and settlements) and returns the trip's uuid; then `DocumentRepository.deleteForTrip(tripUuid, ownerId)` deletes its documents and their
  files (no FK). Not `removeOrphans`: right after a sign-in, before the first full sync brings the trips back, it would
  also wipe the documents of trips that aren't back yet. If the app dies between the two steps, the next full sync's
  orphan cleanup removes the rest. `DeleteTripViewModel` runs both steps (see "Deleting a trip").

## Cloud sync (`data/sync`)
**Room is the only source for the screens**; the app works offline exactly as before. Firestore holds the **shared
trips** (every member's phone syncs the same trip) and each member's personal data, and carries changes between
devices. The exchange-rate cache, the assistant chat and the trip documents (Prep tab) are never synced.

### Shared trips (stage 1 of 3 done)
- A trip has an **owner** (who created it) and **members** (uids, ≤ 10, owner first, then join order). Today every
  trip has one member, its owner; inviting/joining is stage 2 (plan: see "Shared trips: next stages").
- **Shared** with all members: the trip's details (destination, dates, currency, color), expenses, travelers,
  settlements, activities and the "Before the trip" checklist. **Personal** (only that member, not even the owner):
  their budget (`SyncKind.BUDGET`) and their Packing list (`SyncKind.PACKING`); documents stay on the device as before.
- **Traveler keys are uids**: an expense's payer/split and a settlement's sides are member uids or name-only traveler
  uuids. The **list order** (`travelerOrder(me, memberIds, travelers)`) is the members in the cloud's order, then the
  travelers: the same on every device, so the extra agorot of an uneven split go to the same people everywhere. With
  one member it is "You" first, then the travelers, exactly as before. Display order still puts "You" first.
- `Trip.ownerId` / `memberIds` (Room `cloud_owner_id` / `member_ids`) come from the cloud; an upload never changes them.
- Room keeps one copy per signed-in account (`trips(owner_id, uuid)` unique), so two accounts on one phone can both
  hold the same shared trip.

### Firestore layout (field names in `SyncDocuments` (its private `Field` constants), must match `firestore.rules`)
```
trips/{tripUuid}                               destination, startDate, endDate, currencyCode, color,
                                               ownerId, memberIds (array of uids)          — no budget here
   (trip tombstone)                            ownerId, memberIds, deleted: true, updatedAt (members still read it)
trips/{tripUuid}/expenses/{uuid}               name, amountAgorot, category, date,
                                               originalAmount, originalCurrency, exchangeRate, rateDate,
                                               paidBy (string key), splitBetween (array of keys)
trips/{tripUuid}/activities/{uuid}             date, startTime, title, note, type, location, estimatedCostAgorot, booked
trips/{tripUuid}/checklist/{uuid}              list ("BEFORE_TRIP" only), text, checked, dueDate, createdAt
trips/{tripUuid}/travelers/{uuid}              name, color, createdAt
trips/{tripUuid}/settlements/{uuid}            fromTraveler, toTraveler, amountAgorot, date, createdAt
trips/{tripUuid}/memberData/{uid}              budgetAgorot                       (personal: only that uid)
trips/{tripUuid}/memberData/{uid}/packing/{uuid}  list ("PACKING"), text, checked, dueDate, createdAt   (personal)
every live document also has                   updatedAt (epoch millis, int), deleted: false
users/{uid}                                    sharedTripsMigrated: true   (the legacy copy's migration marker)
users/{uid}/trips/**                           the legacy copy (before shared trips): read until migrated, then deleted
```
- Money is whole agorot (int), dates ISO strings ("2026-11-12"), `startTime` "HH:mm", rates and foreign amounts exact
  decimal strings (`toPlainString()`), enums by name (unknown → same fallback as Room). Every key is written, null
  when empty. A document that can't be read is skipped and logged, never fatal.
- **Deletes are tombstones:** a child document is replaced (no merge) by `{deleted: true, updatedAt}`. A trip is
  deleted with an `update` that removes its data fields and keeps `ownerId`/`memberIds` (so members can still read
  the tombstone); the same write tombstones its shared children and the writer's own budget and Packing items.
- **Writing a trip** (`FirestoreCloudStore.write`): a trip not yet in the cloud (checked with a `get`, allowed for a
  missing document) is created with `ownerId` = the writer and `memberIds = [writer]`; an existing trip is written
  with `set(merge)` of the data fields only — never `ownerId`/`memberIds`, so a phone with a stale member list can't
  undo a membership change. Writes are committed **one trip per batch** (≤ 450 writes): every item's rule looks up its
  trip, and Firestore allows only 20 different document lookups per batch (repeated lookups of one trip count once;
  `rules.test.js` checks 450).
- **Reading:** `fetchAll` = the query `trips where memberIds array-contains <uid>` (the only list the rules allow), then
  each live trip's 5 shared subcollections + `memberData/{uid}` + its `packing`. `fetch` reads trips first; children of
  a trip that doesn't exist are reported missing without reading them.

### Legacy migration (`LegacyMigration.kt`, pure + `SyncManager.migrateLegacy`)
Before shared trips the cloud copy was `users/{uid}/trips/…` (keys `"me"`, budget in the trip document, both
checklists in one subcollection). Once per account (local flag `SyncFlags.isSharedTripsMigrated`, prefs "sync",
key `shared_trips_migrated_<uid>`), **before any other sync work** (full or quick, so a trip not yet in the new layout
can never make the merge delete it here):
1. `fetchLegacy` (paged queries `orderBy(documentId).limit(450)`), `convertLegacy(items, uid)`: `"me"` → uid in payer,
   split and settlements; each trip gets owner = only member = uid; its budget becomes a `BUDGET` item with the trip's
   `updatedAt`; Packing items → `PACKING`; a legacy checklist tombstone → a tombstone of both kinds.
2. `planLegacyCopy(legacy, fetchAll)`: copies only what the new layout lacks or has **older** (never overwrites a newer
   edit from a phone that migrated first), trips first; children of a trip that ends up deleted are skipped.
3. `fetchAll` again; `isLegacyCopyComplete` (= nothing left to copy) or the sync fails and the next one starts over.
4. `markLegacyMigrated` (sets the marker: old app versions can no longer read the legacy copy), `deleteLegacy` (every
   legacy document, old `places` included, children first), then the local flag.
Every step can be repeated; old cloud data is deleted only after step 3. An account without legacy data just gets the
marker. The old one-time removal of place documents (`deleteLegacyPlaces`, prefs key `places_cleanup_<uid>`) is part
of `deleteLegacy` now.

### Write path
- `RoomTripRepository` / `RoomExpenseRepository` / `RoomActivityRepository` (and the checklist, traveler and settlement
  repositories) take the `Clock` and a `LocalChangeListener` (the `SyncManager`). Every insert/update sets
  `updated_at = clock.millis()` and `pending_sync = 1`; `setBudget` sets `budget_updated_at` / `budget_pending`
  instead (the personal budget never conflicts with shared edits); deletes go through the DAOs' `@Transaction
  deleteAndRecord` (insert a `deletions` row, then delete; a Packing item records `PACKING`). Then `onLocalChange()`.

### SyncManager (app-scoped, `Dispatchers.IO`)
- **Full sync** (legacy migration if needed, download everything with `fetchAll`, `planMerge`, apply in one Room
  transaction, upload trips first, mark synced): when the user becomes non-null (sign-in **and** app start,
  `collectLatest` on the uid, so switching accounts cancels it), whenever `NetworkMonitor.isOnline` goes back to true,
  and on **pull to refresh** (`SyncController.refresh()`, false when offline or failed).
- **Quick sync** ~1 s after a local change (conflated channel): only the pending items and their own cloud
  documents (`fetch`), the same `planMerge`, so a newer edit from another device is never overwritten.
- One sync at a time (`Mutex`); every query uses the current uid only. No network → no cloud call, status OFFLINE.
  No realtime listener: other devices see changes at their next sign-in, app start, reconnect or pull to refresh.
- Uploads go in chunks of 100 items, sorted by `SyncKind` (trips first); each chunk is marked synced (`markSynced`:
  `pending_sync = 0` / `budget_pending = 0` / drop the tombstone **only if the time is unchanged**, so an edit made
  during the upload stays pending). A failure anywhere throws `CloudUnavailableException` and leaves the rest pending.
- Firestore uses a **memory cache only** (Room is the one copy on the device, so sign-out really removes the data);
  reads use `Source.SERVER`; each cloud call times out after 20 s.
- **Documents of deleted trips:** after every **successful full sync** `SyncManager` calls
  `TripCleanup.afterFullSync(uid)` = `RoomDocumentRepository.removeOrphans`. Quick syncs, failed or offline syncs and
  sign-out never run it. At app start `AppContainer.cleanUpDocumentFiles()` sweeps files no row uses.

### Merge rules (`planMerge`, pure, `SyncMergeTest`)
Items are matched by **kind and uuid** (a trip's `BUDGET` item has the trip's uuid).

| local | cloud | result |
|---|---|---|
| live, pending | missing | upload (created here / first upload of existing data) |
| live, not pending | missing | delete here (removed from the cloud) |
| missing | live | save here (trips before their children) |
| live | live | newer `updatedAt` wins, tie → cloud |
| live | tombstone | newer of edit and delete wins, tie → delete |
| tombstone | live | newer of delete and edit wins, tie → the cloud's edit (the item comes back) |
| tombstone | missing / tombstone | drop the local tombstone |

Children of a trip that ends up deleted are deleted here and never saved or uploaded. Cloud versions are saved in
`SyncKind` order (`TRIP, TRAVELER, EXPENSE, ACTIVITY, CHECKLIST, SETTLEMENT, BUDGET, PACKING`, stored by name so it may
be reordered), trips first; checklist items and travelers in `createdAt` order, ties by uuid. `RoomSyncLocalStore.apply`
re-checks the time so a cloud version never replaces a newer local change made while the sync ran; saving a trip keeps
the local copy's budget columns, and a membership change (which doesn't change `updatedAt`) is picked up as a tie.
Timestamps are device clocks (no server time), so a device with a very wrong clock can win conflicts it shouldn't.

### Status and sign out
- `SyncController.status`: `SYNCING` while a sync runs (or changes are pending, online and the last attempt didn't
  fail), `SAVED` when nothing is pending, else `OFFLINE`. Shown in the Home avatar menu.
- `signOut(force = false)`: uploads pending changes first; if nothing is left pending it signs out and then deletes
  the account's rows and tombstones from the device (`NonCancellable`, under the sync lock). **Documents are kept**
  on both paths (never uploaded, so deleting them would lose them): hidden by `owner_id`, shown again when the same
  account signs in and its trips come back; the warning dialog says so. If changes can't be
  uploaded it returns `NEEDS_CONFIRMATION` and Home shows the warning dialog. `signOut(force = true)` ("Sign out
  anyway") signs out and **keeps** the data on the device, hidden by owner scoping; it uploads at that account's
  next sign-in here. A second account on the same device only ever syncs its own rows.
- Testing: `FakeCloudStore` (shared trips with members, personal items per uid, the legacy copy + marker; `offline`,
  `failAfterWrites`, `failLegacyDelete`, `onWrite`, `addMember`, `membersOf`; `put(uid, …)`/`docsOf(uid)` = written
  by / visible to that uid, a budget under `"budget:<trip uuid>"`), `FakeSyncLocalStore` (same guards as Room),
  `FakeSyncFlags`, `FakeNetworkMonitor`. **Never test against the real Firestore.** `SyncManagerTest` runs the manager
  in `backgroundScope` and advances virtual time (`advanceUntilIdle` stops when only background work is left).

### Security rules (`firestore.rules`, tests in `rules-tests/rules.test.js`)
- **Trips:** `get` for members (a missing id reads as "no document"); `list` only with `memberIds array-contains <me>`;
  `create` only with `ownerId == me` and `memberIds == [me]`; data updates by members with `ownerId` and `memberIds`
  unchanged; tombstone, hard delete and reviving a deleted trip: owner only; member list (only `memberIds` changing,
  1–10, no duplicates): the owner removes others and stays, a member removes only themselves, **nobody is ever added**
  (joining arrives with stage 2's invite rule); the trip document may never hold `budgetAgorot`.
- **Shared children:** read by members; written by a member *after* the write (`getAfter`, so a new trip and its items
  fit in one batch and leaving + writing in one batch fails); live items only while the trip is live, tombstones
  always; hard delete: owner only.
- **memberData/{uid}** and **…/packing**: read and **delete** by that uid only, even after they left or were removed (so
  they can clean up their own data); create/update by that uid while a member. Nobody else, not even the owner.
- **Field validation** as before (`hasOnly` key lists, ISO dates, end ≥ start, amount > 0, budget/cost null or > 0,
  decimal strings, "HH:mm", texts not empty, `createdAt`/`updatedAt` int, traveler name 1–100, settlement keys
  different) or a tombstone; expense `paidBy`/`splitBetween` are now **required**; shared checklist `list ==
  'BEFORE_TRIP'`, packing `list == 'PACKING'`; trip `memberIds` 1–10 without duplicates and containing the owner.
- **Legacy copy** `users/{uid}/trips/**`: no create/update; delete by its owner; read by its owner until
  `users/{uid}.sharedTripsMigrated == true`, after that only the list query with `limit == 450` (the app's migration
  query) works, so old app versions are cut off while leftovers can still be deleted. The marker can be set, never
  unset or deleted. `members/…` and `invites/…` don't exist yet: everything there is denied.
- Change the rules together with `SyncDocuments` and `rules.test.js`, run `npm test` in `rules-tests/`, then publish.

### Known limits
- **Old app versions** (before shared trips): once the new rules are published they can't write at all (their edits
  stay pending on that phone, status "Offline"). Until that account is migrated they still download; after the marker
  their download is denied, so they keep showing their last data and never delete anything. Updating the phone runs
  the Room migration and uploads its pending edits to the new layout (newer wins). Order: publish the rules, then update
  every phone. Until the rules are published, the new app's migration fails safely (the new paths are denied) and
  retries; nothing is deleted.
- **Rate limits:** the rules can't rate-limit requests, so guessing invite codes (stage 2) and using up the free read
  quota are held back only by the size of the code space, the 7-day single-use codes and App Check. Enforcing App
  Check for Firestore is therefore part of finishing stage 2.
- A member's pending edit to a trip they no longer have access to fails the sync until stage 2's "lost access"
  handling (only possible once members exist).
- Timestamps are device clocks (see "Merge rules").

### Shared trips: next stages (planned, not built)
- **Stage 2 – invites and joining:** `trips/{id}/members/{uid}` `{name, joinedAt, left, inviteCode}` (display name only,
  **never an email**; rules `hasOnly`), Room `trip_members` + `SyncKind.MEMBER`, members in Budget → Travelers with
  "Owner"; Overview "Traveling together" card (`design/trip_members.png`, only the card) with "Invite" (owner only);
  `invites/{code}` `{tripId, inviterUid, inviterName, destination, startDate, endDate, createdAt, expiresAt, status,
  usedBy}` (no email), 8 chars from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` with `SecureRandom`, created only by the owner,
  `get` only while open and unexpired (wrong / expired / used / cancelled look the same), never listed; invite sheet
  (`design/trip_invite.png`: code, copy, "Share code", members with remove / Leave); Home "Join trip" + join sheet
  (`design/home_join.png`: 8 boxes, Paste, preview of destination, dates, inviter, then "Join trip"); joining = one
  batch (invite open → used by me, `memberIds + [me]`, `members/{me}`), each rule checking the others; leaving = one
  batch that also deletes my `memberData` and packing; removed by the owner → my next sync sees no access, deletes my
  `memberData` + packing (allowed by the stage 1 delete rule) and the local copy; non-owners get "Leave trip" instead of
  "Delete trip"; ≤ 10 members; App Check enforced for Firestore.
- **Stage 3 – personal data and polish:** budget card/percentages/donut/PDF/assistant use my budget and my share
  (wired since stage 1), "Your budget" copy; Prep progress = shared "Before the trip" + my Packing; suggested items
  guarded against two members adding them at once; tests with two members editing the same item (newer wins).

## Navigation
Type-safe Navigation Compose routes (`@Serializable`) in `ui/navigation/Routes.kt`:

```
(app start) ──▶ Home if a session is saved, otherwise Login
Login ──(sign in / sign up succeeded)──▶ Home      popUpTo<Login> inclusive → Back on Home exits the app
Home ──(FAB / empty-state button)──▶ TripForm()                  Save / back arrow → pop (to Home)
Home ──(tap any trip card)─────────▶ TripDetails(tripId: Long)   back arrow → pop
TripDetails ──(header edit icon)───▶ TripForm(tripId)            Save / back arrow → pop (to the trip)
TripDetails ──("+ Add expense")────▶ ExpenseForm(tripId)         Save / back arrow → pop
TripDetails ──(tap an expense)─────▶ ExpenseForm(tripId, expenseId)  Save / Delete / back → pop
TripDetails ──("Add activity")─────▶ ActivityForm(tripId, dateEpochDay = selected day)  Save / back → pop
TripDetails ──(tap an activity)────▶ ActivityForm(tripId, activityId)  Save / Delete / back → pop
TripDetails ──(sparkle FAB)────────▶ TripAssistant(tripId)       back arrow → pop (conversation is kept)
TripDetails ──("Delete trip" confirmed)──▶ pop to Home with Home's savedStateHandle["pendingDeleteTripId"] = tripId;
                                   Home deletes it (DeleteTripViewModel.deleteConfirmed) and clears the key
(session becomes null) ──▶ Login                   popUpTo(graph) inclusive → whole back stack cleared
```
- `SessionViewModel.startsSignedIn` picks the start destination once. Firebase restores a saved session
  synchronously, so there is no splash screen.
- `AppNavHost` collects `SessionViewModel.currentUser`; when it becomes null on any screen other than Login (avatar
  menu "Sign out", or Firebase signing the user out), it navigates to Login and clears the back stack.
- Login → Home is driven by `AuthUiState.isAuthenticated`, not by the session flow, so Home opens only after sign up
  has saved the display name.
- `TripDetailsViewModel`, `TripFormViewModel`, `ExpenseFormViewModel` and `ActivityFormViewModel` read their ids via
  `SavedStateHandle.toRoute<...>()`.
- `AssistantViewModel` is created with the **`TripDetails` back stack entry** as its owner
  (`viewModel(navController.getBackStackEntry<TripDetails>(), factory = ...)`), so it reads `TripDetails` from the
  `SavedStateHandle` and the conversation lives exactly as long as the trip screen.
- The trip screen's 4 tabs are **not** destinations: the selected `TripTab` is Compose state (`rememberSaveable`).
- Navigation calls live only in `AppNavHost`; screens receive callbacks (`onAddTrip`, `onTripClick`, `onBack`, ...).

## Home screen rules (`groupTrips` in `ui/home/TripGrouping.kt`)
- **Past:** `end < today`, most recent first. Card shows destination + "MMM yyyy" of the start date, no countdown.
- **Active:** current (`start ≤ today ≤ end`) and future trips, earliest start first (ties by id).
  - The first active trip is the **Next trip** (gradient hero card). The rest are **Upcoming** (white cards).
  - A current trip shows **"Now"** instead of a countdown, in the hero card and as a badge if it lands in Upcoming.
  - Countdown = days from today to the start date (always ≥ 1 for future trips).
- Trip length is inclusive: 12–17 Nov is 6 days.
- Date ranges (`TripFormatting.dateRange`): `12–17 Nov 2026`, `28 Nov – 3 Dec 2026`, `28 Dec 2026 – 3 Jan 2027`,
  and `12 Nov 2026` for a single day.
- The Upcoming title shows the count pill ("2 trips"); the Past title shows "N archived".
- **Empty state:** when there are no trips, show the friendly message and an "Add your first trip" button. The FAB
  stays visible. While Room loads for the first time, the state is `Loading` and nothing is drawn, so the empty
  state never flashes.
- The header greeting and initials come from the signed-in user's `greetingName()`: the display name, or the part
  of the email before "@" when there is no name.
- **Trip card menu:** the cards have **no chevron**. Every card (Next trip, Upcoming, Past) ends with a three-dots
  `TripMoreButton` ("Share trip", see "Share trip (PDF)"; "Delete trip", see "Deleting a trip") at the right edge, vertically centered, no background:
  `onHeroMuted` on the Next trip card, the `chevron` gray on the list cards. The countdown box / badge sits to its
  left; the Next trip card's left side stays label, destination, dates. The button is a 48dp touch target with a
  20dp icon (`Dimens.CardMenuIconSize`); the card rows end with `Dimens.CardMenuEndPadding` (2dp) so the icon is
  `CardMenuIconInset` (16dp) from the card edge on all three cards, and the button's 14dp inset is the gap to the
  countdown. The button consumes its taps (never opens the trip); the menu is right-aligned to the button and
  shifted by the end padding, so it lines up with the card's edge.
- **Card keys** are `"trip-<id>"` in all three sections and every item has `Modifier.animateItem()`, so a deleted card
  fades out and the trip that becomes the Next trip moves up from Upcoming. **Pull to refresh** (`RefreshBox` +
  `RefreshViewModel`) runs a full sync; a failure shows "Couldn't refresh. Check your connection.". Every card sits in a `SwipeToDeleteBox`
  (see "Deleting a trip"); the Scaffold has a `SnackbarHost` for "Trip deleted".
- **Avatar menu** (`AccountButton` + `AccountViewModel` in `ui/common`): tapping the
  avatar opens a `DropdownMenu` with the user's name and email, the sync status (small cloud icon + "All changes
  saved" / "Syncing…" / "Offline – changes will sync later", from `AccountState.syncStatus`) and "Sign out". Sign out
  goes through `SyncController.signOut`; when changes can't be uploaded, an `AlertDialog` warns ("Sign out anyway" /
  Cancel, `AccountState.showSignOutWarning`); it also says the trip documents stay on the device either way. `HomeViewModel` only provides the greeting and the trips.

## Trip screen (`ui/tripdetails`)
Reference: `design/trip_overview.png` (Overview) and `design/trip_budget.png` (Budget tab, see "Budget tab and expense
splitting").
- `Scaffold` with a Material 3 `NavigationBar`: **Overview, Budget, Prep, Plan** (Prep: `ic_checklist`, see "Prep tab").
  **Pull to refresh** on every tab (`RefreshBox`, spinner below the status bar; own `RefreshViewModel`) runs a full
  sync, same message as Home on failure.
  A round `secondary` FAB with a sparkle (`AssistantFab`) sits above the navigation bar on all four tabs and opens
  the assistant; the list has `FabClearance` bottom padding so it never covers the last card. On the Budget tab the
  FAB slot holds a full-width row (`FabPosition.Center`): the primary pill "+ Add expense" (`AddExpenseFab`,
  `ExtendedFloatingActionButton`) at the bottom left and the sparkle at the right, never overlapping.
- **Prep tab:** see "Prep tab" below. `TripTab.PREP` replaced `TripTab.MAP` in the same (third) position.
- **Header** (`TripHeader`, on every tab, scrolls with the list): hero gradient + `HeroWaves`, only bottom corners
  rounded (`extraLarge`), drawn behind the status bar (`contentWindowInsets = WindowInsets(0)` +
  `statusBarsPadding()`); back circle, status pill ("In 38 days" / "Now", **hidden after the trip ended**), edit
  circle (→ `TripForm(tripId)`), three-dots circle (`TripMoreMenu` → "Share trip", "Delete trip"), destination (`headlineLarge`,
  ≤ 3 lines), dates with length. Below 300dp content width or
  above font scale 1.2 the status pill moves to its own line under the buttons.
- **Overview:** `BudgetCard` (tap → Budget tab; "Set budget" button opens the budget dialog), then `CurrencyCard`,
  then the **Map card** and Daily plan (→ Plan tab) side by side (equal height: `IntrinsicSize.Min` row), then the day
  section (`DayPreviewCard`, see "Plan tab").
- **Map card** (`FeatureCard` with `ic_map`, "Map", the destination in gray (≤ 2 lines), then
  `OverviewMapIllustration`): a rounded (`shapes.large`) **drawn** map filling the card's width and the height the Daily
  plan card leaves (`weight(1f)`, at least `Dimens.MapPreviewMinHeight` 88dp). A Compose `Canvas` from theme tokens only
  (land `surfaceContainerHighest`, streets `surfaceContainerLowest`, a river `primaryContainer`, a park
  `secondaryContainer`), a teal `ic_location` pin in the middle and under it a white "Open in Google Maps" pill (teal
  `ic_map` icon; the icon is dropped below 360dp screen width or above font scale 1.2, measured with
  `LocalConfiguration`/`LocalDensity` because the row's intrinsic measurement can't run `BoxWithConstraints`). No
  network, no tiles, the same offline. **Tapping anywhere** opens `googleMapsSearchUrl(destination)` =
  `https://www.google.com/maps/search/?api=1&query=` + the trimmed destination percent-encoded as UTF-8 (spaces `%20`)
  with `ACTION_VIEW`: the Google Maps app when installed, else the browser; `ActivityNotFoundException` → snackbar
  "No maps app found" (`openGoogleMaps` in `TripDetailsScreen`, shared with the Plan cards' "Open in Maps").
- **Budget:** see "Budget tab and expense splitting" below.
- `TripDetailsUiState`: `Loading` (nothing drawn) / `NotFound` (not the user's trip or deleted) / `Loaded`.
- **Budget dialog** state lives in the ViewModel (`budgetDialog: BudgetDialogState?`): prefilled with the current
  budget; empty → `EMPTY`, unparseable or ≤ 0 → `INVALID`; Save stores it with `setBudget`.
- **Expense form:** name, amount (currency symbol prefix, decimal keyboard) with a currency selector, date
  (`DateField` with `maxDate` = the trip's last day, **no** `minDate`: flights and hotels are paid before the trip),
  category (`FilterChip`s in the category colors, default `OTHER`). A new expense defaults to today, or to the trip's
  last day once the trip has ended (`ExpenseValidator.defaultDate`). Validation on Save: name not blank; amount see
  "Expense currency"; date not after the trip's end (`DATE_AFTER_TRIP`, "The date can't be after the last day of
  the trip", under the date field). An existing expense dated after the trip (dates shortened later) is kept as it
  is in the list; opening it shows that error right away, and it can't be saved until the date is fixed. Editing a
  field clears only its error. In edit mode the top bar has a delete icon → confirmation dialog → delete. With other
  travelers, "Paid by" and "Split between" sit under the amount (see "Budget tab and expense splitting").
- **Expense row** (`ExpenseRow`): round category icon, name, then the date and the category label (`TagPill` in the
  category colors) on one `FlowRow`; right side the shekel amount in bold and, for a foreign expense, the original
  ("€10.00", `ExpenseCurrency.formatOriginal`) in small gray text. With other travelers (`ExpenseSplitInfo`) a gray
  line with the payer's 18dp avatar ("Paid by Noa · split 3 ways", "Paid by you · not shared", "Paid by you · for
  Noa") and "Your share ₪60" in `primary` under the amounts. With only me neither is shown.

### Budget tab and expense splitting (`ui/common/split`, `ui/tripdetails/split`)
Reference: `design/trip_budget.png`, **with these deliberate differences**: the usual hero `TripHeader`, bottom bar and
sparkle FAB stay as on every tab; "Newest first" is a plain gray label (there is only one order, so no dropdown arrow);
the By category legend uses round dots, ₪0 rows (expenses I'm not part of) come **after** the others, not first.
- **Layout** (top → bottom): `TripHeader`; `BudgetCard` (my share; on this tab without the wallet chip, edit icon next
  to the total); **Travelers** (title + count `Pill`, a sideways-scrolling row of 56dp round avatars with the name under
  each, "You" first and not tappable, then the dashed "+ Add" circle); **Who owes whom** (only with other travelers);
  **By category** (`CategoryChartCard`: donut left with "TOTAL ₪X" inside, legend right with dots; stacked below
  300dp or above font scale 1.2; categories with spending of mine largest first, then the ones with only ₪0 for me as
  "₪0 0%" without a slice; nothing at all of mine → an empty `budgetTrack` ring); "Expenses" + "Newest first"; rows (or
  the empty state). The "+ Add expense" pill is in the FAB row (see above).
- **Travelers are names only**, not accounts. "You" is the signed-in member's uid, never a row (see the `travelers`
  table and "Shared trips"). List order: the trip's members (owner first; today only "You"), then travelers by
  `created_at, id` (`travelerOrder(me, memberIds, travelers)`; `Person.You(key)`). Avatars (`TravelerAvatar`): "YOU" on
  `primary`/`onPrimary`, otherwise the first letter or digit of the name on the traveler's `ExtendedColors.travelers`
  fill; initials sized by `travelerAvatarTextStyle` (fits the circle at any font scale; decorative, the name is always
  next to it and in the a11y text). A key no traveler has (removed on another device while used here) shows as
  "Removed traveler" with a gray "?" (`Person.Removed`), and the numbers stay exact.
- **Add / rename / remove** (`TravelerDialog`, state in `SplitViewModel`): "+ Add" → name field; tapping a traveler →
  same dialog prefilled with "Remove". Names: trimmed, not blank (`EMPTY`), not another traveler's name ignoring case
  (`DUPLICATE`), not "You" (`RESERVED`) — `validateTravelerName`. A traveler who paid for or shares an expense or
  appears in a settlement (`isTravelerUsed`, checked when the dialog opens and again by the DAO in the delete
  transaction) can't be removed: "Remove" is disabled and the dialog says "Noa paid for or shares an expense, or
  appears in a settlement, so they can't be removed." `isSaving` is set synchronously (double taps save once).
- **Shares** (`shares`, pure): equal split of the shekel `amountAgorot` in whole agorot; the remainder goes one agora
  each to the first travelers in **list order** (keys sorted by list order, unknown keys last by key), so the shares
  always add up exactly to the amount. `myShare` = my entry (0 when I'm not in the split).
- **Budget counts my share only:** `TripDetailsViewModel` computes `myShares` (expense id → my share) and feeds
  `budgetSummary(spentAgorot = sum)` (left, spent + percent, per-day line) and `categoryTotals(amountOf = my share,
  includeZeroCategories = true)`. The Overview `BudgetCard`, the PDF cost section (`buildTripPdfContent(…, travelers)`,
  categories > 0 only) and the assistant's "spent" (`myTotal`) use the same numbers. An expense I'm not part of counts
  0 but is still listed. Settlements never touch the budget.
- **Balances** (`balances`): per key, paid − shares, + settlements paid, − settlements received; always sum to 0.
  **Transfers** (`simplifyTransfers`): the smallest number of payments: the non-zero balances are split into as many
  zero-sum groups as possible (bitmask DP over subsets, ≤ 16 people with a balance; above that one greedy group), each
  group of k settles with k − 1 transfers (largest debtor pays largest creditor). Sorted: rows with "You" first, then
  the larger amount, then list order.
- **Who owes whom** (`WhoOwesWhomCard`): "Based on N shared expenses" (`sharedExpenseCount`: split ≠ `[payer]`);
  rows on `surfaceContainerHighest`: 32dp avatar → arrow → avatar, "Noa owes you" / "You owe Amit" / "Amit owes Noa",
  the amount (teal when owed to me); tonal full-width "Settle up" (`primaryContainer`). No open debt → check icon +
  "All settled up", plus a "History" link when payments exist. Hidden when the trip has only me.
- **Settle up** (`SettleUpSheet`, `ModalBottomSheet`, state `SettleSheetState`): radio rows of the open transfers (a
  single one is picked already); the amount field starts at the full amount (`Money.toInput`) and may be lowered for a
  partial payment (`validateSettleAmount`: `EMPTY`, `INVALID` (≤ 0 / unparseable), `TOO_MUCH` (> the open amount));
  "Mark as paid" saves `Settlement(from, to, amount, today)` and closes the sheet; `isSaving` is synchronous. "History"
  switches the sheet to the payments, newest first ("Noa paid you · ₪100", date), each with a delete icon →
  confirmation → delete (the debt reopens).
- **Expense form:** with other travelers, under the amount: "Paid by" (`FilterChip`s with 18dp avatars, single) and
  "Split between" (a "Not shared" chip = `[payer]`, then one chip per person, multi). New expense: paid by me, split
  between everyone. Changing the payer while "Not shared" keeps it not shared (the split moves to the new payer); an
  empty split → `SPLIT_EMPTY` ("Choose at least one person to split with") on Save. Editing keeps the stored payer and
  split (unknown keys stay, shown as "Removed traveler"). With only me both fields are hidden and the expense saves
  as `me` / `[me]`.
- **One traveler only:** the Travelers section shows "You" and "+ Add"; no Who owes whom card; no split line or
  "Your share" on rows; no split fields in the form. Every number is the same as before splitting existed.

### Expense currency (`expense/ExpenseCurrency.kt`, pure + unit-tested)
- The selector (`SingleChoiceSegmentedButtonRow`) offers **ILS** and one foreign currency: the trip currency (none
  when the trip has no currency or it is ILS → no selector), or, when editing a foreign expense, **that expense's
  own currency** (even if the trip currency changed since). A new expense defaults to the trip currency; an
  edited one to its stored currency. The selector moves under the field below 360dp or above font scale 1.2.
- The form follows `CurrencyRepository.observeRate(code)` for its whole life and calls `refreshRate` once on open
  when `isStale` (no cache or ≥ 12 hours). No cached rate and the fetch fails → `ForeignRateState.Unavailable`:
  the foreign option is disabled (`foreignEnabled = false`) with a short message and the selection moves to ILS —
  **except** for an edited foreign expense, which keeps its currency and stored rate. While the first fetch runs,
  saving a foreign amount reports `NO_RATE`.
- **Save rule** (`resolveAmount`): editing with the same currency and the same amount ("10" = "10.00") keeps the
  stored `amountAgorot` and `ForeignAmount` (rate fixed at the first save). Otherwise ILS → `Money.parseAgorot`, no
  original; foreign → `parseAmount` with the currency's decimals (`java.util.Currency.defaultFractionDigits`:
  JPY 0, EUR 2, KWD 3; unknown → 2), shekels = amount × latest rate, `BigDecimal`, **HALF_UP to whole agorot**, no
  fees, no manual adjustment; ≤ 0 agorot or above `Money.MAX_AGOROT` → `INVALID_AMOUNT`.
- Live line under a valid foreign amount: "≈ ₪40.20 · 1 EUR = ₪4.02 · rate of 2 Oct" (`conversionPreview`,
  `CurrencyConversion.formatRate`, `TripFormatting.dayMonth` of the **service's** rate date); it shows the stored
  rate while an edited amount is unchanged.
- Budget totals, the donut and the Overview budget card only ever sum `amountAgorot`, so they need no conversion.

### Plan tab and itinerary (`Itinerary.kt` pure + unit-tested, `components/ItineraryComponents.kt`)
Reference: `design/trip_plan.png`, **with these deliberate differences**: no top bar ("Plan" title, logo,
profile), no share/map icons, no walking distance / "On Track" / "22 min walk", no "Not scheduled yet" section and
no assistant button inside the tab (the trip screen's sparkle FAB opens the assistant), and **no orange** — each type has its own hue
(`activityTypes`), used for the round type icon, the type label, the timeline dot, the Overview day section
(label, note icon, dot) and the type chips in the activity form. "Booked" is a neutral pill with a check, so it
never looks like a second type label.
- A day is **planned** when it has ≥ 1 activity. Activities outside the trip dates never count.
- **Default day:** today while the trip is happening (`start ≤ today ≤ end`), otherwise (before *or* after the
  trip) the first day. The selected days live in `TripDetailsViewModel` (`planDay`, `overviewDay`, null = default);
  a selection that is no longer inside the trip dates (dates edited) falls back to the default
  (`validDayOrDefault`). The tab itself stays Compose state.
- **Ordering** (`sortActivities`, `ui/common/TripDays.kt`): by date, then start time with **untimed activities last**, then id.
- **Plan tab** (below the usual `TripHeader`): "Itinerary" + "12–17 Nov 2026 · Rome, Italy"; `DayChipsRow`
  ("Day 1 · Thu 12 Nov", selected = filled teal + check icon, opens scrolled to the selected chip);
  `DaySummaryCard` ("DAILY RHYTHM", "N activities", "~₪210 planned" = `dailyCostAgorot`, hidden when no activity
  of the day has a cost); the timeline (`PlanTimeline` → `TimelineRow` + `ActivityCard`: 24-hour time, title,
  round type icon, note box, type pill, "Booked" pill, then the location with an **"Open in Maps"** button) as **one**
  lazy item so the rail line is unbroken; or the empty state; then the full-width `AddActivityButton` (opens the form
  on the selected day).
- **"Open in Maps"** (`ActivityCard(onOpenInMaps)`, Plan tab only, also on "Outside trip dates" cards; not on the
  Overview's compact cards): shown only when the activity's location text isn't blank; a 48dp `IconButton` with
  `ic_map` in `primary`, a11y "Open in Maps", centered under the round type icon. It opens
  `googleMapsActivityUrl(location, destination)` = `googleMapsSearchUrl("<location>, <destination>")` (the destination
  is left out when blank or already contained in the location, case-insensitive) through the same `openGoogleMaps` +
  "No maps app found" snackbar as the Overview Map card. Nothing is looked up in the app.
- **Outside trip dates:** activities whose date is outside the (edited) trip dates are never deleted; they are
  listed last under "Outside trip dates", with their date shown on the card.
- **Daily plan card:** with activities, "5 of 6 days planned" + `DailyPlanProgress`: one segment per day
  (filled = planned) for trips of ≤ 14 days (`MAX_PROGRESS_SEGMENTS`), one continuous bar for longer trips; "5 days
  set" / "1 open" under it. Without any activity it keeps "No plan yet".
- **Overview day section** (`DayPreviewCard`, hidden while the trip has no activities at all): title from
  `daySectionTitle` — "First day" (day 1, before the trip), "Today" (the current day during the trip), else
  "Day N"; date with previous/next arrows (disabled at the ends); up to 3 compact activities, then "+N more"; an
  empty day shows "Nothing planned for this day" + "Add activity". "See full plan" calls `onSeeFullPlan()` (copies
  the day into `planDay`) and switches to the Plan tab.
- **Activity form** (`ActivityForm(tripId, activityId?, dateEpochDay?)`): title, type (`FilterChip`s, default
  `ACTIVITY`), date (`DateField` limited to the trip with `minDate`/`maxDate`; default = the passed day, else the
  default day), optional start time (read-only field → `AlertDialog` with a 24-hour M3 `TimePicker`, written
  when material3 1.3 had no `TimePickerDialog` (1.4 adds one); × clears it; shared `TimeField`), location (free
  text, optional for every type), estimated cost (₪, optional), "Booked" switch row, note.
  The ViewModel loads the trip first (`isLoading`); a trip that isn't the user's sets `done`. Validation on Save:
  title not blank; cost empty or `Money.parseAgorot` > 0. Edit mode: delete icon → confirmation → delete; the form
  hands the `DeletedActivity` to the app-scoped
  `DeletedActivityStore`, and the trip screen (`TripDetailsViewModel.deletedActivity`, this trip only) shows the
  snackbar "Activity deleted" with "Undo" (`SnackbarDuration.Long`, on every tab): Undo → `restoreActivity`, a dismiss
  keeps the delete. In memory only. An existing date outside the trip is kept unless the user picks another one.

### Prep tab (`ui/tripdetails/prep`, `data/document`, checklist in `data/trip`)
Reference: `design/trip_prep.png`, **with these deliberate differences**: the usual hero `TripHeader` stays on top
(back/edit/share on every tab), then the title; the subtitle reads "19–24 Oct 2026 · Rome, Italy" (dates first, see
"Language and layout direction"); the FAB is the sparkle `AssistantFab`, not an arrow; the bottom bar is the app's.
- **Own ViewModel:** `PrepViewModel` (default owner = the `TripDetails` entry, reads `TripDetails` from the
  `SavedStateHandle`; `Clock` + `computeDispatcher` like the others). `TripDetailsScreen` collects it and passes
  `prep: PrepUiState` + `PrepActions` to `TripDetailsContent` (`prepTab(...)` items); `rememberPrepActions` /
  `PrepDialogs` (`PrepHost.kt`) own the file picker, the viewer intent, the dialogs and the snackbars.
- **Layout:** "Trip prep" (`headlineLarge`) + gray subtitle; `PrepProgressCard` ("12 of 20 done", percent `Pill` in
  `primaryContainer`, bar `primary` on `budgetTrack`, "8 things left before your trip" before the trip / "N things left"
  from its first day / "All done…"; both lists empty → "Nothing on your lists yet" + an invitation, no bar);
  Documents; Checklist.
- **Progress** (`PrepProgress.kt`, pure): counts **both** lists; percent rounded **down** (19/20 → 95%, only all done is
  100%). `dueState`: `OVERDUE` = not checked and due date **before** today (due today is not overdue, checked never is),
  shown in `budgetWarning` orange (a11y "Overdue, due 18 Oct"); otherwise gray.
- **Checklist:** chips "Before the trip (5/8)" / "Packing (7/12)" (selected filled `primary`, the other
  `surfaceContainerLowest` + `outlineVariant` border; `FlowRow`, the selected list is Compose `rememberSaveable`
  state). One card: rows in `created_at` order, tap toggles (`Role.Checkbox`, state "Done"/"Not done"), checked = filled
  teal check + gray line-through text; **long press** opens a `DropdownMenu`: Edit text (dialog, VM `editText`), Set /
  Change due date (`DatePickerPopup`, "Before the trip" only, no min/max), Clear due date, Delete → snackbar "Item
  deleted" + Undo (`deletedItem` in the VM, `SnackbarDuration.Long`). Last row "Add item…": a `BasicTextField` whose
  text is Compose state; IME Done → `onAddItem` (false for blank text, the field keeps it), cleared on success, the
  custom `onDone` keeps the keyboard open. An empty list shows a line + "Add suggested items" (`string-array`s
  `prep_suggested_before_trip` / `prep_suggested_packing`, added through `addItems` only while that list is empty;
  a double tap adds once). Nothing is ever added automatically.
- **Documents:** title + count pill + "+ Add" (`TextButton`); a `horizontalScroll` `Row` with `IntrinsicSize.Min`
  (all cards as tall as the tallest) of `DocumentCard`s (`Dimens.DocumentCardWidth`, `shapes.large`): type icon in a
  `primaryContainer` square (`ic_flight`, `ic_hotel`, `ic_shield`, `ic_ticket`, `ic_description`), "PDF"/"Photo"
  badge, three-dots menu (Edit → the same form prefilled: name, type, detail line; Delete → confirmation dialog →
  row and file deleted), name, detail, "View file". Tapping the card opens the file. The last card is the dashed "Add
  document" card. Under the row: "Stored on this device only".
- **Adding:** `OpenDocument` with `application/pdf` + `image/*` → `onFilePicked(uri)` → `DocumentFileStore.inspect`
  → `checkPickedFile`: not a PDF/image → "Only PDF files and images can be added."; size > 20 MB → "This file is larger
  than 20 MB…" (no form); unknown size passes and `copyIn` stops at the limit (`DocumentTooLargeException`, same
  message, the partial copy is deleted). Otherwise `DocumentFormDialog` (name prefilled with the file name without its
  extension, type `OTHER`, detail). Save: blank name → error; `isSaving` set synchronously (double taps save once,
  dismiss ignored); the file is copied **on Save**, then the row inserted (a failed insert deletes the copy). A copy
  error closes the form with a snackbar.
- **Viewing:** `fileToView` → `FileProvider` content uri + mime → `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION`;
  missing file → "This document's file is no longer on this device."; no app → "No app found to open this file".

### Currency card (`CurrencyCard`, `CurrencyConversion.kt`, `data/currency`)
- `TripDetailsViewModel.currencyCard` (`CurrencyCardState?`, null = nothing drawn until the trip loads):
  `NoCurrency` (message + "Choose currency") / `Shekel` (trip currency is ILS: code chip + one-line message, no
  converter, no fetch) / `Loading` (no cached rate, fetching) / `Unavailable` (no cached rate, fetch failed →
  "Try again") / `Rate`.
- `Rate` shows "1 [EUR ▾] = ₪3.44" and an "Updated 2 Oct" pill with the **service's** date
  (`TripFormatting.dayMonth`), never "today". Rates ≥ ₪1 show 2 decimals, smaller ones 4 significant digits.
- The currency code is a **small outlined chip with a dropdown arrow** (labelLarge, 48dp touch target, a11y
  "Change currency, now EUR") on every state that has a code; tapping it (or "Choose currency") opens the shared
  `CurrencyPickerSheet`, a `ModalBottomSheet` with a search field (matches code or name, codes starting with the
  query first) over `/v1/currencies`. The list is kept in memory after the first success; offline → message +
  "Try again". Selecting saves with `TripRepository.setCurrency`.
- **The currency is only ever chosen by the user** (card or trip form) — never guessed from the destination.
- **Rate source:** Frankfurter `GET https://api.frankfurter.dev/v1/latest?base=XXX&symbols=ILS` (no API key),
  `HttpURLConnection` + `kotlinx-serialization-json`; the rate is read from the JSON text into `BigDecimal`. `FrankfurterJson` rejects a response whose `base` isn't the requested code and an empty currency list (both count as failures). The manifest declares `INTERNET`.
- **Refresh:** `TripDetailsScreen` calls `onOverviewShown()` in a `LaunchedEffect(selectedTab, loaded,
  currencyCode)` once the trip is loaded. The ViewModel remembers the code it last showed: a **different currency**
  (picked on the card or in the trip form) is fetched right away (`force`), even with a fresh cache, while the
  cached rate stays visible; the same currency is fetched only when `isStale(fetchedAt, now)` (no cache, or fetched
  ≥ 12 hours ago). `onCurrencySelected` on the card starts the forced fetch itself. With no internet the cached
  rate and its date stay on screen; failures never throw out of `CurrencyRepository` (it returns false/null).
- **Converter:** two fields side by side (trip currency, ILS) with a swap button between them; swap only moves the
  fields (both keep their amounts). Input: digits, one `.`/`,`, ≤ 2 decimals, ≤ 7 integer digits (up to
  9,999,999.99) — any other keystroke is ignored and the field keeps its value. Math is `BigDecimal`:
  ILS = amount × rate, foreign = amount ÷ rate, both HALF_UP to 2 decimals, shown without trailing zeros or
  grouping ("201", "24.88").
- **Converter text is Compose state, not ViewModel state.** `CurrencyCard` keeps a `ConverterInput` (last edited
  side + its exact text + swap) in `rememberSaveable(code)` and updates it synchronously with the pure
  `CurrencyConversion.edit()` / `swap()`; only the *other* field is derived (`converterState(input, rate)`), so
  the field being typed in is never rewritten and a refreshed rate updates the other field. Keyed by the code, so
  a currency change clears both fields. Do not route the text through a `StateFlow`: `BasicTextField(String)`
  resets its cursor when it recomposes with a value that lags behind what was typed — this caused the
  "can't type 3+ digits" bug, together with boxes that were too narrow.
- **Amounts are always fully visible:** the code label sits above the amount, and the amount font steps down
  from 24sp to 10sp (`ConverterAmountStyles` in `Type.kt`, picked with `CurrencyConversion.largestFittingSize` +
  `rememberTextMeasurer`) until the whole number fits its box; the line height stays fixed.
- The converter is independent of expenses; expenses in the trip currency are described in "Expense currency".

### AI trip assistant (`ui/tripdetails/assistant`, `data/assistant`)
Reference: `design/trip_assistant.png`, **with these deliberate differences**: one header only (`BackTopAppBar`, back
arrow + "Trip assistant"); no profile icon, "AI PRO" badge, "Online" status, map or refresh icons; no walking
distances, weather, plan photo, microphone or like/dislike buttons; **no orange** (timeline dots use `activityTypes`).
- **Model:** Firebase AI Logic, Gemini Developer API backend (`Firebase.ai(backend = GenerativeBackend.googleAI())`),
  model names only in `AssistantModel` (`AssistantPrompt.kt`): `PRIMARY` = `gemini-3.5-flash-lite` (default),
  `FALLBACK` = `gemini-3.8-flash`. Both are stable and support structured output (Firebase AI Logic models page,
  2026-10-01). Flash-Lite was chosen as the default for its free daily limit: free tier ~500 requests/day for
  3.5 Flash-Lite and 3.1 Flash-Lite vs ~20 for the Flash models (3.5–3.8). Google's docs don't publish these numbers
  (they point to AI Studio → Rate limits); 20/day for `gemini-3.8-flash` was confirmed by a real quota error, the
  rest comes from third-party summaries, so check AI Studio when choosing again. Each model has its own quota.
  `AssistantRequest.model` picks the model; no API key in the app or repo. `FirebaseAssistantRepository` builds the
  model per request, because the system instruction (`assistantInstructions(tripContext)`) carries the current trip.
- **Never test against the real model** (no emulator chat requests, no direct calls): the free quota is tiny. Use
  `FakeAssistantRepository` in unit tests; the developer tests on the device.
- **Trip context** (`buildTripContext`, English + ISO dates whatever the device language): destination, today, dates +
  length + whether the trip is before/now/after, every day's activities in timeline order ("nothing planned" for empty
  days), activities outside the dates, trip currency (or "not chosen"), budget total/spent/remaining in ₪ (spent = my
  share of each expense, like the Budget tab; or "no budget set", or "over budget by"). Built from the latest Room data for **every** request.
- **Rules in the instructions:** answer in the language of the user's message; respect the remaining budget; never
  repeat planned activities; say when unsure about opening hours, prices etc.; plain text, no markdown.
- **Structured answer** (`responseMimeType = "application/json"` + `responseSchema`):
  `{reply, plan?: {date: yyyy-MM-dd, title, activities: [{time: HH:mm, title, type: ActivityType name, note,
  place?, costShekels?}]}}` (`place` = the one venue's name, saved as the activity's location text). `AssistantJson.parseReply` is lenient: not JSON / neither reply nor plan → null (`UNREADABLE`);
  a ```json fence is stripped; unknown type → `OTHER`; a time that isn't a valid 24-hour H:mm → null (untimed);
  cost → agorot HALF_UP, ≤ 0 or above ₪1,000,000,000 → null; blank titles dropped; invalid date or no activities →
  no plan (the text stays).
- **History:** each request sends the earlier turns (`chatHistory`: answered user messages + the model's raw JSON),
  so a failed message is never sent twice. The conversation is **in memory only** (never in Room).
- **Chips** (only while the conversation is empty, `suggestionChips`): "Plan day N for me" (first trip day without
  activities; left out when all days are planned), "Dinner ideas for day N" (the default day: today during the
  trip, otherwise day 1), "Am I within budget?". A chip sends its text as a message.
- **Chat UI:** user bubbles right (`primary`), answers left (`surfaceContainerLowest`, sparkle avatar) with "AI
  generated · check details before booking" under each; three pulsing dots while `isSending`; input text is Compose
  `rememberSaveable` state (not ViewModel state, see the converter cursor bug); send is disabled while a request runs,
  for blank text and before the trip loads (`onSend` also returns false then, and the field is only cleared on true).
  The input bar pads for the navigation bar and the keyboard (`MainActivity` uses `adjustResize`).
- **Plan card** (`SuggestedPlanCard`): "SUGGESTED PLAN", "Day N · Fri 13 Nov", title, `TimelineRow` per suggestion
  (time, title, note, type `TagPill`, "~₪90" pill, checkbox checked by default; the whole row toggles). "Add to Day N"
  adds the checked ones via `suggestionInputs` (not booked; the venue the model named, `place`, becomes the location
  text and is never looked up) with `ActivityRepository.addActivity`; status
  NEW → ADDING → ADDED ("Added", disabled, toggles locked). Plan tab and Overview update through their Room flows.
  `withinTrip`/`dayNumber` are recomputed from the **current** trip dates; outside the trip there is no button,
  only a note.
- **Errors** (`AssistantError`, card after the conversation with "Try again" = resend the last message without a
  second bubble): `QuotaExceededException` or a message with quota / rate limit / RESOURCE_EXHAUSTED / 429 →
  `RATE_LIMIT` (the free tier allows only ~20 requests a day for this model); a 503 "high demand" / overloaded /
  unavailable message → `BUSY`. After `BUSY` or `RATE_LIMIT` the ViewModel sends the same request **once** to
  `model.other` (still "typing", no delay) and only then shows the last error; "Try again" starts again with
  `PRIMARY`. Other errors are shown right away. A message mentioning App Check / attestation → `APP_CHECK` (`classifyServerMessage`);
  `RequestTimeoutException` or an `IOException` cause → `NO_CONNECTION`; stopped/blocked/unparseable responses →
  `UNREADABLE`; everything else → `UNKNOWN`. Every failure is logged with its exception
  (`adb logcat -s TripAssistant`), and an answer that isn't readable JSON is logged with its raw text.

### App Check
- Firebase enforces App Check for Firebase AI Logic. `PlanYourTripApplication.onCreate()` calls `installAppCheck()`
  before anything else: `src/debug` installs `DebugAppCheckProviderFactory` (`firebase-appcheck-debug`, debug only),
  `src/release` installs `PlayIntegrityAppCheckProviderFactory` (`firebase-appcheck-playintegrity`, release only; the
  app must be linked in Play Console / have its SHA-256 in Firebase).
- **Debug token:** with `firebase-appcheck-debug` 19.0.2 (BoM 34.12.0) it is logged **only when a new token is created**
  (first launch after install or after clearing app data), Logcat level **Debug**, tag
  `com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider`, text "Enter this debug secret into the allow
  list in the Firebase Console for your project: <token>". Register it in Firebase console → App Check → Apps →
  ⋮ → Manage debug tokens. **Never commit the token.**

### Budget rules (`BudgetCalculations.kt`, pure + unit-tested)
- `budgetSummary(...)` is `null` when there is no budget → card shows a short message and "Set budget".
- left = total − spent (negative = over budget). Over budget shows "₪X over budget" (error color) instead of "left".
- `spentPercent` = spent / total × 100 (one decimal, "57.5%"); the bar is clamped to 0..1 and turns
  `budgetWarning` orange when **more than 80%** is spent (exactly 80% stays teal).
- "About ₪X per day for N days": before the trip N = trip length; during it N = days left **including today**;
  after the trip the line is hidden. Also hidden when nothing is left (≤ 0). X = left / N, rounded **down** to whole
  shekels (exact agorot below ₪1).
- spent = **my share** of every expense (`myTotal`, see "Budget tab and expense splitting"), never the full amounts.
- `categoryTotals` (`ui/common/CategoryTotals.kt`, shared with the PDF) = per-category sums of `amountOf` (my share) > 0,
  largest first (ties by enum order), percent of the total; with `includeZeroCategories` (Budget tab) the categories
  whose expenses count 0 follow as ₪0 / 0%.

## Share trip (PDF) (`ui/common/share`)
- **Entry points:** the three-dots menu of every Home trip card and of the trip header → `ShareTripViewModel.onShareTrip
  (tripId)`. Each screen has its own `ShareTripViewModel` (default owner) and renders `ShareTripHost`.
- **Dialog** (`ShareDialogState` in the ViewModel): "Include costs" switch (on by default; the whole row toggles),
  Cancel and "Create PDF" (`PrimaryButton` with its spinner while `isCreating`; switch, Cancel and dismiss are
  ignored then, and `isCreating` is set synchronously so a double tap creates one PDF). A failure keeps the dialog
  open with "Couldn't create the PDF." and the button reads "Try again". On success the dialog closes and
  `pendingShare` is set; `ShareTripHost` starts `ACTION_SEND` in a chooser (`EXTRA_STREAM` + `ClipData` +
  `FLAG_GRANT_READ_URI_PERMISSION`) and calls `onShareLaunched()`.
- **Data:** the ViewModel reads the trip, its expenses and travelers (only when costs are included) and activities once with
  `.first()` for the current uid; another account's trip or no user → failure, nothing exported.
- **Content** (`buildTripPdfContent`, pure, `TripPdfContent`): header (destination, dates, inclusive length); cost
  section only when costs are included **and** there are expenses: total spent (my share), budget and `BudgetDifference`
  (Under / Over / OnBudget) only when there is a budget, `categoryTotals` (largest first, only > 0, amount +
  percent); then one `PdfDay` per trip day ("Day 1 · Thu 12 Nov") with `activitiesOn` order (untimed last) and
  time, title, type, location, note (blank → null). An empty day says "Nothing planned". When no activity falls
  inside the trip, `days = null` and the PDF has one line instead of the section. The model has **no** email,
  expense list, activity costs/booked flag or activities outside the dates, so they can't leak.
- **File name** (`pdfFileName`): "Rome-Italy-12-17-Nov-2026.pdf" — destination (≤ 60 chars) + `dateRange` in
  English, every run of non-letter/non-digit characters → "-" (Unicode letters, so Hebrew stays; combining marks
  only after a letter, so emoji variation selectors go), nothing left → "Trip".
- **Drawing** (`AndroidTripPdfExporter`, `Dispatchers.IO`): A4 595×842 pt, 48 pt margins, footer band. The body is
  a list of measured blocks; `paginate` (pure) never splits a block, keeps a `keepWithNext` block (section title,
  day heading, "By category") on the page of the block after it, and gives a block taller than a page a page of its
  own where it is clipped (only possible with a giant note). Footer on every page: "Created with Plan Your Trip" +
  "Page N of M". Text is `StaticLayout` with `TextDirectionHeuristics.FIRSTSTRONG_LTR` + `ALIGN_NORMAL` (Hebrew and
  Arabic paragraphs lay out right to left, bidi for mixed text). Inter from `res/font`; API 29+ uses
  `Typeface.CustomFallbackBuilder(...).setSystemFallback("sans-serif")`, older versions get the system fallback
  automatically, so characters Inter lacks still render. Colors come from `PdfColors` (hero gradient header,
  activity type hues, error red for over budget).
- **Files:** `cacheDir/shared_pdfs/` is emptied before each new PDF; shared through
  `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, not
  exported). Failures are logged (`adb logcat -s TripPdf`) and rethrown to the ViewModel.

## Deleting a trip (`ui/common/delete`, `ui/home/components/SwipeToDeleteBox.kt`)
- **Entry points:** swipe a Home card (Next trip, Upcoming, Past) to the left; "Delete trip" (red, `ic_delete`) in
  the three-dots menu of every Home card and of the trip header. Each screen has its own `DeleteTripViewModel`
  (default owner) and renders `DeleteTripHost`.
- **Swipe** (`SwipeToDeleteBox`, foundation `AnchoredDraggableState`, end-to-start only): anchors `Closed` 0,
  `Revealed` −`Dimens.SwipeRevealWidth` (96dp), `Dismissed` −card width; 50% positional threshold. A short swipe
  springs back; past the reveal width the card stays open on a red (`colorScheme.error`) area with `ic_delete` +
  "Delete" (`onError`) that can be tapped; a full swipe or a fast fling opens the dialog right away. While the dialog
  is open for that trip (`deletePending`) the card stays; when it closes without deleting, the card animates back.
  The drag takes only horizontal gestures, so a swipe never opens the trip and a tap still does. TalkBack gets a
  "Delete trip" custom action.
- **Dialog** (`DeleteDialogState(tripId, destination, isDeleting)`): "Delete this trip?", the body names the trip and
  what goes with it (`trip_delete_body` starts with U+200E LRM, so an RTL destination at the start doesn't turn the
  English sentence right to left), "Cancel" and a red "Delete". `isDeleting` is set synchronously (double taps delete
  once; Cancel and dismiss are ignored, a spinner replaces "Delete").
- **Home** confirms with `onConfirm()`: delete with the current uid, then the documents; the dialog closes and
  `message` = `DELETED` ("Trip deleted" snackbar) or `FAILED` (no user, another account's trip, an exception;
  "Couldn't delete the trip. Try again.", nothing deleted). A failure in the documents step still counts as deleted.
  Room's flow re-groups the trips (the next upcoming trip becomes the Next trip, the empty state after the last one).
- **Trip screen** confirms with `takeConfirmed()` (nothing deleted there, so it never flashes "not found") and calls
  `onTripDeleted(tripId)`: `AppNavHost` pops back to Home with `pendingDeleteTripId`, and Home runs
  `deleteConfirmed(tripId)` with the usual animation and snackbar.
- **Sync:** the recorded `TRIP` tombstone uploads with the quick sync, or at the next full sync after the connection
  returns; `FirestoreCloudStore.write` tombstones the trip (keeping `ownerId`/`memberIds`), its shared children and my
  own budget and Packing items, and `planMerge` drops the children's own local tombstones. `firestore.rules` allow it
  only for the trip's **owner** (today every trip's only member).

## Design rules
Reference: `design/home_screen.png`. Match it closely when adding or changing screens.
- **Use theme tokens, never hard-coded colors in screens.** Material roles come from `MaterialTheme.colorScheme`;
  brand colors without a Material role come from `ExtendedTheme.colors` (hero gradient and its overlay, wave and glow
  colors, mint dot, badge, past-card colors, pastel letter squares, `budgetWarning` orange (also overdue due dates), `budgetTrack`,
  `iconChip`, `categories`, `activityTypes`, `travelers` and `removedTraveler`). Raw color values live only in `ui/theme/Color.kt` and
  `ExtendedColors.kt` — no exceptions.
- **Activity type and category colors** (`ExtendedColors.activityTypes` / `categories`, both
  `Map<…, TintedColor>`): each entry has a `content` color (icon, label text, chart slice, timeline dot) and a
  light `container` tint, with dark-mode variants. Raw values are the `Hue*` constants in `Color.kt`; a hue means
  the same thing in both maps:

  | Hue | Activity type | Expense category |
  |---|---|---|
  | teal (brand) | Activity | — |
  | rose | Dining | Food |
  | purple | Lodging | Hotels |
  | indigo | Transport | Transport |
  | gray | Other | Other |
  | sky blue | — | Flights |
  | green-teal | — | Attractions |
  | green | — | Shopping |

  **Never orange** (it means a warning: close to or over budget, or an overdue Prep checklist item). Used for: Plan cards (icon, label, dot, note icon),
  Overview day section, activity form chips, donut slices, legend and row icons (`CategoryIcon`, round), row
  category labels, expense form chips. `TintedColorsTest` checks, for both maps in both modes, ≥ 4.5:1
  content-on-tint contrast, ≥ 3:1 on the background, no repeated colors, no `budgetWarning`, and that shared
  meanings share their hue — keep it passing when changing them.
- **Traveler avatar colors** (`ExtendedColors.travelers`, `TintedColor(container = fill, content = initials)`): the
  same `Hue*` constants as above (rose, indigo, purple, sky, green, green-teal), saturated fill + white initials in
  light mode, the light `…Dark` hue + `Night` initials in dark mode; `removedTraveler` = gray. "You" is
  `primary`/`onPrimary`. Never orange; `TintedColorsTest` checks ≥ 4.5:1 initials contrast, no repeats, both modes.
- **Colors:** brand teal (`primary` #0F6E86, darker `secondary` #0B5A6E for the FAB), warm off-white background
  #FBF8FA, white cards (`surfaceContainerLowest`), gray secondary text (`onSurfaceVariant`). A dark scheme exists for
  every token.
- **Dynamic color is disabled** on purpose, so wallpaper colors on Android 12+ never replace the brand teal.
- **Shapes** (`ui/theme/Shape.kt`): `small` 12dp text fields · `medium` 16dp letter squares · `large` 20dp trip
  cards · `extraLarge` 28dp hero card. Buttons and the FAB are pills (`CircleShape`).
- **Typography:** bundled Inter (Regular/Medium/SemiBold/Bold).
  - `headlineLarge` 40 Bold → screen titles ("My Trips", "Plan Your Trip")
  - `headlineMedium` 30 Bold → hero destination
  - `headlineSmall` 24 SemiBold → section titles
  - `titleLarge` 20 SemiBold → card titles
  - `displaySmall` 44 Bold → countdown number
  - `bodyLarge` 17 → dates and body text
  - `labelLarge` → buttons and the "NEXT TRIP" / "TRIP BUDGET" labels (letter-spaced, via `HeroLabelStyle`)
  - Named styles in `Type.kt` for values that are not a Material role: `HeroLabelStyle` (letter-spaced caps on the
    hero card) and `DatePickerHeadlineStyle` (the DatePicker uses `headlineLarge` internally, so the picker gets this
    calmer style through a local `MaterialTheme` override). Never write `fontSize`/`letterSpacing` in screens.
- **Sizes** (`ui/theme/Dimens.kt`): `ScreenPadding` 20dp screen side padding, `AvatarSize` 56dp avatar and letter
  squares, `ButtonHeight` 52dp primary buttons, `IconChipSize` 44dp round icon chips, `CategoryIconSize` 40dp
  category squares, `TouchTarget` 48dp, `CardMenuIconSize` / `CardMenuIconInset` / `CardMenuEndPadding` (home card
  three-dots button), `MapPreviewMinHeight` 88dp (Overview Map card's picture), `SwipeRevealWidth` 96dp (Home card's red "Delete" area),
  `TravelerAvatarSize` 56dp / `TravelerAvatarSmallSize` 32dp / `TravelerAvatarTinySize` 18dp / `TravelerAvatarChipSize` 18dp (traveler avatars).
  Responsive breakpoints: `CompactScreenWidth` 360dp (home badge, expense currency selector, Map card pill icon),
  `NarrowContentWidth` 300dp (header status pill, converter rate pill, category chart) and `LargeFontScale` 1.2
  (`isLargeFontScale()`). Add a value there instead of repeating a number in several files.
- **Spacing:** 16dp between cards, 16–24dp inside cards. The home list has an extra 104dp bottom padding so the FAB
  never covers the last card.
- **Reuse the shared components** before writing new UI: `PrimaryButton` (pill button with optional loading
  spinner, optional leading icon), `BackTopAppBar` (optional actions), `BrandMark` (gradient circle with the plane),
  `DateField` (optional `minDate`/`maxDate`), `HeroWaves`, `LetterSquare`, `Pill`, `TripDatesRow`,
  `TripMoreMenu`/`TripMoreButton`, `EditFormScaffold` (create/edit forms), `ConfirmDeleteDialog`, `TintedFilterChip`.
  Trip-area cards that need a gray fill (note box, day summary, compact activity cards, progress box, unselected
  day chips) use `surfaceContainerHighest`. The upcoming and past cards share one private `TripListCard` skeleton.
- **Long text never gets cut off.** Dates have no `maxLines` and wrap; destinations wrap to at most 2–3 lines
  with an ellipsis. Text columns use `weight(1f)`. Upcoming cards move the countdown badge under the dates when
  the card is narrower than 360dp or the font scale is above 1.2. Trip lengths and badges use non-breaking spaces
  ("6 days", "In 120 days"), so a line can break only before the length. Check new layouts with the
  320dp / fontScale 1.5 preview in `HomeScreen.kt`.
- Icons are small vector drawables in `res/drawable` (Material Symbols paths), not `material-icons-extended`.

## Auth screen behavior
- Two modes, `SIGN_IN` / `SIGN_UP`, switched with one tap on a Material 3 segmented button.
- Switching modes **keeps** typed email/password and **clears** all errors.
- Sign In: email, password (show/hide), "Sign In" button. There is no "Forgot password?" option (removed on
  purpose), so the Sign In form has the same spacing as Sign Up.
- Sign Up: full name, email, password, confirm password (each with show/hide), "Sign Up" button.
- A valid form calls `AuthRepository` (Firebase). Sign up creates the account and saves the full name as the display
  name. On success the ViewModel sets `isAuthenticated`, which navigates to Home.
- **Requests and double taps:** while sign in/up runs, the main button shows a spinner (`isLoading`). `onSubmit`
  returns early while `isLoading`, and the flag is set synchronously, so double taps send one request.

### Backend errors (`AuthError`, mapped in `FirebaseAuthRepository`)
| Firebase exception | `AuthError` | Shown |
|---|---|---|
| `FirebaseAuthInvalidCredentialsException`, `FirebaseAuthInvalidUserException` | `INVALID_CREDENTIALS` | under password: "Wrong email or password" |
| `FirebaseAuthUserCollisionException` | `EMAIL_IN_USE` | under email |
| `FirebaseAuthWeakPasswordException` (checked first: it extends InvalidCredentials) | `WEAK_PASSWORD` | under password |
| `FirebaseNetworkException` | `NO_CONNECTION` | snackbar |
| `FirebaseTooManyRequestsException` | `TOO_MANY_REQUESTS` | snackbar |
| anything else | `UNKNOWN` | snackbar |

- Firebase's email-enumeration protection reports a wrong email and a wrong password the same way, so the app shows
  one message for both.
- Snackbar messages live in `AuthUiState.message` (`AuthMessage.Failure`) and are
  cleared with `onMessageShown()` after display.
- If the account is created but saving the display name fails, sign up still succeeds; the greeting falls back to
  the email prefix.

### Validation rules (`AuthValidator`)
| Field | Sign In | Sign Up |
|---|---|---|
| Full name | — | not blank |
| Email | not blank + valid format | not blank + valid format |
| Password | not empty | ≥ 8 characters |
| Confirm | — | equals password |

- Validation runs **when the submit button is tapped** (not while typing). Editing a field clears only that field's error.
- Errors are shown under the field via `OutlinedTextField.supportingText` + `isError`.
- The 8-char minimum is enforced only on Sign Up: existing accounts may predate the rule, and the backend decides
  whether a sign-in password is correct.
- Email regex is our own `Regex`, not `android.util.Patterns`, because `Patterns` is null in JVM unit tests.
- Email and name are trimmed before being sent to the repository; passwords are sent as-is.

## Trip form (new + edit, `ui/tripform`)
- Fields: destination, start date, end date, trip currency. Edit mode ("Edit Trip", "Save changes") prefills the
  trip and calls `updateTrip`, which stores destination, dates and currency and keeps color and budget. Dates are read-only fields that open a Material 3 `DatePickerDialog`.
- Validation on Save (`TripValidator`): destination not blank; both dates chosen; end not before start (a one-day
  trip is fine). Errors show under each field. Changing either date clears both date errors.
- The end-date picker disables days before the start date, but the ViewModel still validates.
- **DatePicker millis are UTC midnight.** Always convert with `toPickerMillis()` / `pickerMillisToLocalDate()`
  (`ui/common/PickerDates.kt`). Converting through the device time zone shifts dates by a day.
- Past dates are allowed, so finished trips can be logged.
- No budget fields — the budget is set on the trip screen.
- **Trip currency (optional):** read-only field "EUR · Euro" (name from the loaded list, else the platform's
  `java.util.Currency` name) that opens the shared `CurrencyPickerSheet`; a clear (×) icon removes it. A new trip
  starts with **no** currency (nothing preselected, never derived from the destination). `TripFormViewModel` takes
  `CurrencyRepository` only for the list; the code is saved with `addTrip(..., currencyCode)` /
  `updateTrip(..., currencyCode)`.

## Conventions
- **Never log passwords** (only their length) or other secrets.
- All user-visible text lives in `res/values/strings.xml` (with `plurals` for counts) — no hard-coded strings in
  Composables. Text-field errors go through `errorSupportingText(error?.messageRes())`; each feature maps its own
  error enum to string resources.
- All date formatting goes through `TripFormatting` (never build a `DateTimeFormatter` in a composable). New
  locale-dependent formatters default to `AppLocale.locale`, never `Locale.getDefault()` (see "Language and layout
  direction").
- Dependencies go through the version catalog `gradle/libs.versions.toml`. Room 2.7.2, KSP 2.0.21-1.0.28,
  Navigation 2.9.0, serialization 1.7.3, **Compose BoM 2025.09.01** (Compose 1.9.2, Material 3 1.4.0) and
  **Firebase BoM 34.12.0** (incl. `firebase-firestore` 26.2.0, built with Kotlin 2.0.21) were chosen to match Kotlin 2.0.21; check
  Kotlin/KSP compatibility before upgrading them. Keep the Compose BoM at least as new as the Compose version that
  Navigation/Activity and debug-only `ui-tooling` pull in (1.9.2 now): an older BoM leaves release on older Compose
  artifacts than debug, so code like `FlowRow(itemVerticalAlignment)` compiles in debug and fails in release. Check
  that both `assembleDebug` and `assembleRelease` build after changing Compose versions. Kotlin 2.0 can read library metadata up to Kotlin 2.1:
  `kotlinx-serialization-json` uses the same version as `-core` (1.7.3). `firebase-auth` 24.1.0+ (BoM 34.13.0+) is compiled with Kotlin 2.3 metadata and fails to build
  ("incompatible version of Kotlin ... metadata is 2.3.0"), so upgrading Firebase requires upgrading Kotlin first. `firebase-ai` 17.11.0 (in BoM 34.12.0) is built
  with Kotlin 2.0.21 + serialization 1.7.3; check its POM before upgrading the BoM. Only declare dependencies that code actually uses (`androidx.test:runner` is declared
  explicitly because `AndroidJUnitRunner` needs it).
- Keep pure logic in plain Kotlin and unit-test it: expense splitting (`SplitTest`: uneven split with the extra agorot
  in list order, an expense I'm not part of, not shared, paid for someone else, three travelers with crossing debts,
  independent pairs and zero-sum groups (minimum transfers), partial settlement, deleted settlement, everything
  settled, settlements never change my share, single traveler, unknown/removed key, empty split, a random property
  test that shares, category totals and balances always add up, `isTravelerUsed`, name rules, avatar letters;
  `SplitViewModelTest`: add/rename/duplicate/blank/"You", double taps, remove blocked by payer/split/settlement or
  used meanwhile, settle full/partial/single debt/invalid amounts, history delete with confirmation, another account's
  trip; `ExpenseFormTest`: only me, defaults with travelers, not shared + payer change, empty split, editing keeps the
  split, removed traveler; `TripDetailsViewModelTest`: my share in the summary and chart, an existing trip keeps its
  numbers; `TripPdfContentTest`/`ShareTripViewModelTest`/`AssistantViewModelTest`: my share), validators, `groupTrips`, `tripStatus`, `TripFormatting`,
  `PickerDates`, `Money`, `BudgetCalculations`, `AppLocaleTest` (formatters stay English under an RTL default locale), `Itinerary` (`ItineraryTest`: one-day, past and current trips,
  untimed activities, activities outside the dates),
  `CurrencyConversion` (incl. digit-by-digit typing tests), activity type and category color contrast (`TintedColorsTest`), `ExpenseCurrency` (no-decimal currency, no rate, editing with/without changing the amount, switching currency), the expense date limit (`ExpenseFormTest`: default after the trip ended, date after the trip on save, editing an expense already dated after the trip), deleting an activity (`ActivityFormTest`: delete offers Undo, every type saves without a location; `TripDetailsViewModelTest`: Undo restores the activity, only this trip's message, dismiss keeps the delete), `CurrencyPicker`, `FrankfurterJson`, `isStale`, the assistant (`AssistantJsonTest`: malformed answer, unknown type,
  bad times, costs, invalid date; `AssistantPromptTest`; `AssistantContextTest`: context, chips, history;
  `SuggestionsTest`: only some checked, day outside the trip), the shared PDF (`TripPdfContentTest`: costs on/off, no
budget, over/under/on budget, no expenses, no activities, only activities outside the dates, a day with nothing
planned, untimed activities; `PdfFileNameTest`: special characters, emoji, Hebrew, fallback, long names;
`PdfPaginationTest`; `ShareTripViewModelTest`: double tap, failure + try again, costs off, another account's trip),
the sync (`SyncMergeTest`: edited on two devices,
deleted on one and edited on another, first upload, children of a deleted trip, checklist items saved in creation
order, travelers/settlements of a deleted trip, save order with travelers, a trip and its budget merged separately;
`SyncDocumentsTest`: field formats, the trip's owner/members and no budget, the budget and Packing documents, a legacy
trip document and its budget, tombstones, fallbacks, payer/split, an expense document from before splitting,
travelers, settlements; `LegacyMigrationTest`: "me" → uid, owner/members, Packing and budget, checklist tombstones,
the copy plan (empty new layout, repeated run, interrupted copy, newer version never overwritten, trip deleted since,
legacy trip tombstone, child tombstone); `SyncManagerTest`: second account on the same device, offline in the middle
of a sync, reconnect, quick sync, edits during an upload, sign-out paths, the legacy migration (same data, deleted only
after the check, interrupted copy keeps the phone's trips and finishes next time, interrupted after the marker, a newer
edit from an updated phone kept, a quick sync migrates first, each account its own, an account without legacy data),
personal budget/Packing invisible to another member, an upload never changing the members, refresh, checklist items,
travelers + settlements + shared expenses up and down, a deleted trip tombstoning its travelers and settlements, the
documents cleanup (only after a successful full sync); `SyncDocumentsTest` also reads an older activity document with
`placeId`/`placeLookup` and covers checklist fields; `SplitTest.theOwnersUid_givesExactlyTheSameNumbersAsTheOldMeKey`:
a 500-case property test that shares, my share, balances and transfers are identical with "me" and with the uid;
`RefreshViewModelTest`), the security rules (`rules-tests/rules.test.js` on the emulator, see "Security rules"), the Prep tab (`PrepProgressTest`: no items, all done, 12 of 20, rounding down, overdue / due
today / checked, toggling, insertion order, suggested items; `DocumentFilesTest`: PDF/images, unsupported types, the
20 MB limit, unknown size, badge, extension, default name; `PrepViewModelTest`: toggling, add, suggestions once, edit
text, due dates, delete + Undo, another account's trip, too large / unsupported / unreadable / too large while copying,
double tap, edit, delete with confirmation, view a missing file), `ActivityTypeTest`, `GoogleMapsTest` (spaces, comma, Hebrew, special
characters, trimming, decodes back; the activity URL with/without the destination), `AccountViewModelTest` (menu state, sign out, warning, sign out anyway), deleting a trip (`DeleteTripViewModelTest`:
confirm, cancel, double tap, the trip's document files removed and other trips' kept, another account's trip, no
user, failure, confirmed on the trip screen and deleted on Home; `HomeViewModelTest`: the next upcoming trip becomes
the Next trip, the last trip → empty; `SyncManagerTest`: deleted offline, uploaded with its children's tombstones when
the connection returns), `firstName`/`initials`/`greetingName`, `TripColor`, and every
  ViewModel (with `FakeTripRepository`, `FakeExpenseRepository`, `FakeActivityRepository`,
  `FakeTravelerRepository` + `FakeSettlementRepository` (`gate`, `added`; removal checks the fake expenses/settlements),
  `FakeCurrencyRepository`, `FakeChecklistRepository`, `FakeDocumentRepository` + `FakeDocumentFileStore` (`picked`,
  `actualSizes`, `failCopy`), `FakeAssistantRepository` (scripted responses, recorded requests, `gate`), `FakeTripRepository` (`failDelete`, `deleteGate`, `deletes`, trip uuid = `uuidOf(id)`), `FakeTripPdfExporter` (`exports`,
  `fail`, `gate`), `FakeSyncController` (`refreshResult`, `refreshGate`), `FakeCloudStore`,
  `FakeSyncLocalStore`, `FakeSyncFlags`, `FakeNetworkMonitor` and `FakeAuthRepository` in
  `app/src/test`; set `nextSignInError`, `FakeCurrencyRepository.offline` etc. to simulate backend failures). Most
  ViewModel tests sign in as `AuthUser("u1", …)`; `ME` (`app/src/test/.../TestUsers.kt`) is that uid, their traveler key.
  Expenses always pass `paidBy`/`splitBetween` (no default key).
  `TripDaoTest` in `app/src/androidTest` covers Room (owner scoping, updates, currency, rate cache, expenses,
  activities, cascades, sync columns, recorded deletes, restore as a new pending row, checklist order/scoping/due
  dates/restore, documents scoped by owner, kept after sign-out and shown again when the trip returns, orphans and the
  file sweep, suggested items keeping their order with a fixed clock (same millisecond, delete + Undo, clock set
  back), checklist items from the cloud stored in creation order, payer/split columns, travelers (order, colors, owner,
  rename), removal blocked by payer/split/settlement and recorded otherwise, settlements (order, validation, recorded
  deletes), travelers/settlements/splits from the cloud, a deleted trip leaving no expenses/activities/checklist
  items/travelers/settlements/documents/files behind and only its `TRIP` tombstone pending); `MigrationTest` covers v6 → v7, v7 → v8, v8 → v9, v9 → v10, v10 → v11 (place names copied into empty
  locations as pending edits, own locations untouched, places and `PLACE` deletions gone, all trips/expenses/activities
  kept, cascade still works), v11 → v12 (all data kept, new tables empty, checklist cascade, documents without FK) and
  v12 → v13 (all data kept with the same amounts, existing expenses `paid_by`/`split_between` = `me` and not re-uploaded,
  travelers/settlements empty, cascade) and v13 → v14 ("me" → the owner's uid in payer, split lists and settlements,
  ownerless trips untouched, members and budget sync state, nothing re-uploaded, the same uuid for two accounts but not
  twice for one, cascade). `TripDaoTest` also covers a new trip's owner/members, the budget pending on its own, a cloud
  trip keeping my budget, Packing deletes recorded as `PACKING`, and two accounts holding the same shared trip.
