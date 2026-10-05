# Plan Your Trip — Android app

Trip-planning app. Kotlin + Jetpack Compose + Material 3, single Activity (`MainActivity`) hosting a
Navigation Compose graph. Firebase Authentication (email/password); trips, budgets and expenses are stored locally
with Room, per account. Exchange rates come from the free Frankfurter API and are cached in Room.
Package: `com.example.planyourtrip`. minSdk 26, targetSdk 36.

## Commands
- Build: `./gradlew assembleDebug` (also regenerates the Room schema JSON in `app/schemas/`)
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests (needs an emulator): `./gradlew connectedDebugAndroidTest`
- Firebase config: `app/google-services.json` (project `planyourtrip-79724`, package `com.example.planyourtrip`).
  The Google services plugin only reads it from `app/`.
- Gradle 8.13 fails on the system JDK 24 ("Type T not present"). From the terminal, use Android Studio's JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

## Structure
```
app/src/main/java/com/example/planyourtrip/
  MainActivity.kt              # PlanYourTripTheme { AppNavHost() }
  PlanYourTripApplication.kt   # creates the AppContainer; CreationExtras.appContainer() helper
  AppContainer.kt              # manual DI: AuthRepository, AppDatabase, TripRepository, ExpenseRepository,
                               # CurrencyRepository, Clock
  data/auth/                   # AuthRepository (interface), AuthUser, AuthError/AuthException,
                               # FirebaseAuthRepository (the only Firebase Auth code)
  data/trip/                   # Trip, TripColor, TripEntity, TripDao, TripRepository + RoomTripRepository,
                               # Expense, ExpenseCategory, ExpenseEntity, ExpenseDao,
                               # ExpenseRepository + RoomExpenseRepository, Converters, AppDatabase
  data/currency/               # Currency, ExchangeRate, ILS, ExchangeRateEntity/Dao (rate cache),
                               # CurrencyRepository (+ isStale, RATE_MAX_AGE) + RoomCurrencyRepository,
                               # RatesApi + FrankfurterRatesApi (the only HTTP code), FrankfurterJson (pure parsers)
  data/user/                   # CurrentUser (AuthUser.greetingName()), UserNames (firstName()/initials())
  ui/navigation/               # Routes.kt (type-safe routes), AppNavHost.kt, SessionViewModel
  ui/auth/                     # AuthScreen, AuthViewModel, AuthUiState, AuthValidator
  ui/home/                     # HomeScreen, HomeViewModel, HomeUiState, TripGrouping,
                               # components/ (NextTripCard, TripCards: upcoming + past list cards)
  ui/tripform/                 # TripFormScreen/ViewModel/UiState, TripValidator — new trip AND edit trip
  ui/tripdetails/              # the trip area: TripDetailsScreen (header + 4 tabs), TripDetailsViewModel,
                               # TripDetailsUiState (+ TripTab, BudgetDialogState, CurrencyCardState),
                               # BudgetCalculations (pure), CurrencyConversion (pure: converter math + typing reducer),
                               # ExpenseCategoryUi (icon/label), components/ (TripHeader, BudgetCard, CurrencyCard,
                               # CategoryChartCard, ExpenseRow, BudgetDialog, FeatureCard,
                               # IconChip/CategoryIcon),
                               # expense/ (ExpenseFormScreen/ViewModel/UiState, ExpenseValidator)
  ui/common/                   # code shared by several features:
                               #   TripFormatting (dates, cached formatters), TripTexts (datesWithLength,
                               #   statusLabel), TripStatus (tripStatus()), Money (agorot format/parse, percent,
                               #   shekels()), PickerDates (DatePicker UTC conversion), ErrorText (errorSupportingText),
                               #   CurrencyPicker (CurrencyPickerState, filterCurrencies, currencyName)
  ui/common/components/        # PrimaryButton (optional leadingIcon), BackTopAppBar (optional actions), BrandMark,
                               #   DateField (read-only field + DatePickerDialog), HeroWaves (gradient decoration),
                               #   TripComponents (LetterSquare, Pill, TripDatesRow, ChevronIcon),
                               #   CurrencyPickerSheet (searchable currency list, trip form + trip screen)
  ui/theme/                    # Color, ExtendedColors, Shape, Type (Inter + named styles), Dimens, Theme
app/schemas/                   # exported Room schemas — commit them
design/home_screen.png         # visual reference for the home screen and the app theme
design/trip_overview.png       # visual reference for the trip screen (Overview tab)
third_party/inter/OFL.txt      # Inter font license
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
  outlives a test). `ExpenseFormViewModel` takes the `Clock` for the default expense date.
- **Firebase stays behind interfaces.** Only `data/auth/FirebaseAuthRepository.kt` and `AppContainer.kt` import
  Firebase. ViewModels see `AuthRepository`, `AuthUser` and `AuthError`; tests use `FakeAuthRepository`.
- **ViewModels have no Android `Context`.** Validation returns enum codes (`FieldError`, `TripFieldError`,
  `ExpenseFieldError`, `BudgetFieldError`); the UI maps them to string resources.
- **Package rules.** Each feature package (`auth`, `home`, `tripform`, `tripdetails`) owns its screen, ViewModel,
  `XxxUiState.kt` and validator. Anything used by more than one feature lives in `ui/common` (or
  `ui/common/components` for composables) — features never import from each other.
- **Heavy work stays off the main thread.** Room and Firebase calls are `suspend`/`Flow`; `HomeViewModel` groups
  trips and `TripDetailsViewModel` computes budget totals with `flowOn(computeDispatcher)`.
- **One-shot results are state flags.** `AuthUiState.isAuthenticated`, `TripFormUiState.saved` and
  `ExpenseFormUiState.done` are observed in a `LaunchedEffect` that triggers navigation.
- **Edit forms reuse the create forms.** `TripForm(tripId?)` and `ExpenseForm(tripId, expenseId?)` create when the
  id is null and edit otherwise; the ViewModel loads the item once (`isLoading` hides the fields until then) and
  saving sets `isSaving` synchronously, so double taps save once.

## Data model (Room)
Database `plan_your_trip.db`, `AppDatabase` version 4, three tables:

| `trips` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `owner_id` | TEXT, indexed | Firebase uid of the account that owns the trip (default `""`) |
| `destination` | TEXT | trimmed before insert, never blank |
| `start_date` | INTEGER | `LocalDate` stored as epoch day |
| `end_date` | INTEGER | epoch day, `end_date >= start_date` (validated in the form) |
| `color` | TEXT | `TripColor` enum **name** |
| `budget_agorot` | INTEGER, nullable | total budget in agorot; `null` = no budget (a new trip has none) |
| `currency_code` | TEXT, nullable | ISO 4217 code of the destination currency ("EUR"); `null` = not chosen yet |

| `expenses` column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | auto-generated |
| `trip_id` | INTEGER, indexed | FK → `trips.id`, **`ON DELETE CASCADE`** (deleting a trip deletes its expenses) |
| `name` | TEXT | trimmed, never blank |
| `amount_agorot` | INTEGER | > 0 (validated in the form) |
| `category` | TEXT | `ExpenseCategory` enum **name**; unknown names fall back to `OTHER` |
| `date` | INTEGER | epoch day |

| `exchange_rates` column | Type | Notes |
|---|---|---|
| `currency_code` | TEXT PK | one cached rate per currency, shared by all accounts (rates are public) |
| `rate` | TEXT | shekels per 1 unit, exact `BigDecimal` text ("3.4408"), never a float |
| `rate_date` | INTEGER | epoch day of the rate's date **as returned by the service** |
| `fetched_at` | INTEGER | epoch millis of when this device fetched it (drives the 12-hour refresh) |

- **Money is whole agorot in `Long`** (100 agorot = ₪1), never `Float`/`Double`. Text input is parsed with
  `Money.parseAgorot` (digits, one `.` or `,`, ≤ 2 decimals, ≤ ₪1,000,000,000); display goes through
  `Money.formatAgorot` / `shekels()` ("₪2,550", "₪12.50"). Doubles are used only for displayed percentages.
- `ExpenseCategory` = `FLIGHTS, HOTELS, FOOD, SHOPPING, TRANSPORT, ATTRACTIONS, OTHER`, same naming rules as `TripColor`.
  Icons/labels live in `ui/tripdetails/ExpenseCategoryUi.kt`, colors in `ExtendedColors.categories`.

- `TripEntity` is the Room row; `Trip` is the domain model used by the UI (mapped with `toTrip()`).
  The UI never sees Room types, so storage can change later (e.g. Firestore sync).
- `TripColor` = `SKY, PEACH, MINT, LAVENDER, ROSE, LEMON`. It is assigned automatically when a trip is created,
  round-robin by the number of the owner's existing trips (`TripColor.forIndex(dao.count(ownerId))`), so each
  account starts at the first color. Stored by name, so entries can be
  reordered or appended, but never renamed or removed without a migration (an unknown name falls back to the first
  color). The actual colors live in the theme (`ExtendedColors.pastels`), with light and dark variants.
- **Every query is scoped to one owner** (`observeAll(ownerId)`, `observeById(id, ownerId)`, `count(ownerId)`,
  `update`/`setBudget` with `AND owner_id = :ownerId`), so an account never sees or changes another account's trips.
  Expense queries join/sub-select `trips` on `owner_id`; `addExpense` returns null for a trip the owner doesn't own. `TripRepository` takes `ownerId` explicitly; ViewModels pass
  `authRepository.currentUser.value?.uid` (New Trip does nothing and Details shows nothing without a user).
- `observeAll` returns trips ordered by `start_date, id`. Grouping into home sections happens in Kotlin.
  Expenses are returned newest first (`date DESC, id DESC`).
- **Migrations:** v1 → v2 is an `AutoMigration` that adds `owner_id`. Trips created before v2 get `""` and are shown
  to no account. v2 → v3 is an `AutoMigration` that adds `trips.budget_agorot` (null) and creates `expenses`;
  existing trips are kept. v3 → v4 is an `AutoMigration` that adds `trips.currency_code` (null) and creates
  `exchange_rates`; existing trips and expenses are kept. Room turns on `PRAGMA foreign_keys` itself (the cascade is covered in `TripDaoTest`).
- **Never seed sample trips into the database.** Sample data belongs only in previews and tests.
- **Changing the schema:** bump `version`, add a `Migration` or `AutoMigration`, build, and commit the new JSON in
  `app/schemas/`. Do not use `fallbackToDestructiveMigration` — users would lose their trips.
- There is no "delete trip" UI yet; when added, the FK cascade removes the expenses.

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
(session becomes null) ──▶ Login                   popUpTo(graph) inclusive → whole back stack cleared
```
- `SessionViewModel.startsSignedIn` picks the start destination once. Firebase restores a saved session
  synchronously, so there is no splash screen.
- `AppNavHost` collects `SessionViewModel.currentUser`; when it becomes null on any screen other than Login (avatar
  menu "Sign out", or Firebase signing the user out), it navigates to Login and clears the back stack.
- Login → Home is driven by `AuthUiState.isAuthenticated`, not by the session flow, so Home opens only after sign up
  has saved the display name.
- `TripDetailsViewModel`, `TripFormViewModel` and `ExpenseFormViewModel` read their ids via
  `SavedStateHandle.toRoute<...>()`.
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
- **Avatar menu:** tapping the avatar opens a `DropdownMenu` with the user's name and email and "Sign out".

## Trip screen (`ui/tripdetails`)
Reference: `design/trip_overview.png` (there is no separate Budget design; the Budget tab reuses the Overview style).
- `Scaffold` with a Material 3 `NavigationBar`: **Overview, Budget, Map, Plan**. Map and Plan show "Coming soon".
- **Header** (`TripHeader`, on every tab, scrolls with the list): hero gradient + `HeroWaves`, only bottom corners
  rounded (`extraLarge`), drawn behind the status bar (`contentWindowInsets = WindowInsets(0)` +
  `statusBarsPadding()`); back circle, status pill ("In 38 days" / "Now", **hidden after the trip ended**), edit
  circle (→ `TripForm(tripId)`), destination (`headlineLarge`, ≤ 3 lines), dates with length.
- **Overview:** `BudgetCard` (tap → Budget tab; "Set budget" button opens the budget dialog), then `CurrencyCard`,
  then Places ("No places yet" → Map tab) and Daily plan ("No plan yet" → Plan tab) side by side. Not built yet on
  purpose: "First day", assistant FAB — add them when those features exist.
- **Budget:** `BudgetCard` with an edit icon next to the total (opens the dialog), "By category" donut + legend (only
  categories with spending, largest first), "Expenses" title + "+ Add expense" button + rows newest first (tap →
  edit), or an empty state.
- `TripDetailsUiState`: `Loading` (nothing drawn) / `NotFound` (not the user's trip or deleted) / `Loaded`.
- **Budget dialog** state lives in the ViewModel (`budgetDialog: BudgetDialogState?`): prefilled with the current
  budget; empty → `EMPTY`, unparseable or ≤ 0 → `INVALID`; Save stores it with `setBudget`.
- **Expense form:** name, amount (₪ prefix, decimal keyboard), date (`DateField`, default today), category
  (`FilterChip`s, default `OTHER`). Validation on Save: name not blank; amount not empty, parseable and > 0. Editing
  a field clears only its error. In edit mode the top bar has a delete icon → confirmation dialog → delete.

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
- Expenses are unaffected: they stay in shekels.

### Budget rules (`BudgetCalculations.kt`, pure + unit-tested)
- `budgetSummary(...)` is `null` when there is no budget → card shows a short message and "Set budget".
- left = total − spent (negative = over budget). Over budget shows "₪X over budget" (error color) instead of "left".
- `spentPercent` = spent / total × 100 (one decimal, "57.5%"); the bar is clamped to 0..1 and turns
  `budgetWarning` orange when **more than 80%** is spent (exactly 80% stays teal).
- "About ₪X per day for N days": before the trip N = trip length; during it N = days left **including today**;
  after the trip the line is hidden. Also hidden when nothing is left (≤ 0). X = left / N, rounded **down** to whole
  shekels (exact agorot below ₪1).
- `categoryTotals` = per-category sums > 0, largest first (ties by enum order), percent of total spending.

## Design rules
Reference: `design/home_screen.png`. Match it closely when adding or changing screens.
- **Use theme tokens, never hard-coded colors in screens.** Material roles come from `MaterialTheme.colorScheme`;
  brand colors without a Material role come from `ExtendedTheme.colors` (hero gradient and its overlay, wave and glow
  colors, mint dot, badge, past-card colors, pastel letter squares, `budgetWarning` orange, `budgetTrack`,
  `iconChip`, and `categories` — 7 distinct teal/blue shades for expense categories). Raw color values live only in
  `ui/theme/Color.kt` and `ExtendedColors.kt` — no exceptions.
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
  category squares. Add a value there instead of repeating a number in several files.
- **Spacing:** 16dp between cards, 16–24dp inside cards. The home list has an extra 104dp bottom padding so the FAB
  never covers the last card.
- **Reuse the shared components** before writing new UI: `PrimaryButton` (pill button with optional loading
  spinner, optional leading icon), `BackTopAppBar` (optional actions), `BrandMark` (gradient circle with the plane),
  `DateField`, `HeroWaves`, `LetterSquare`, `Pill`, `TripDatesRow`, `ChevronIcon`. The upcoming and past cards share one private `TripListCard` skeleton.
- **Long text never gets cut off.** Dates have no `maxLines` and wrap; destinations wrap to at most 2–3 lines
  with an ellipsis. Text columns use `weight(1f)`. Upcoming cards move the countdown badge under the dates when
  the card is narrower than 360dp or the font scale is above 1.2. Trip lengths and badges use non-breaking spaces
  ("6 days", "In 120 days"), so a line can break only before the length. Check new layouts with the
  320dp / fontScale 1.5 preview in `HomeScreen.kt`.
- Icons are small vector drawables in `res/drawable` (Material Symbols paths), not `material-icons-extended`.

## Auth screen behavior
- Two modes, `SIGN_IN` / `SIGN_UP`, switched with one tap on a Material 3 segmented button.
- Switching modes **keeps** typed email/password and **clears** all errors.
- Sign In: email, password (show/hide), "Sign In" button, "Forgot password?" link.
- Sign Up: full name, email, password, confirm password (each with show/hide), "Sign Up" button.
- A valid form calls `AuthRepository` (Firebase). Sign up creates the account and saves the full name as the display
  name. On success the ViewModel sets `isAuthenticated`, which navigates to Home. "Forgot password?" never navigates.
- **Requests and double taps:** while sign in/up runs, the main button shows a spinner (`isLoading`); while a reset
  runs (`isSendingReset`), the main button and the link are disabled. `onSubmit`/`onForgotPassword` return early
  while `isBusy`, and the loading flag is set synchronously, so double taps send one request.

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
- **Password reset:** the snackbar says "If an account exists for <email>, a password reset link is on its way." —
  Firebase does not reveal whether the email has an account. Reset emails use the device language
  (`auth.useAppLanguage()`).
- Snackbar messages live in `AuthUiState.message` (`AuthMessage.ResetEmailSent` / `AuthMessage.Failure`) and are
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
- "Forgot password?" validates the email first; if invalid, shows the email error instead of calling the repository.
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
- All date formatting goes through `TripFormatting` (never build a `DateTimeFormatter` in a composable).
- Dependencies go through the version catalog `gradle/libs.versions.toml`. Room 2.7.2, KSP 2.0.21-1.0.28,
  Navigation 2.9.0, serialization 1.7.3 and **Firebase BoM 34.12.0** were chosen to match Kotlin 2.0.21; check
  Kotlin/KSP compatibility before upgrading them. Kotlin 2.0 can read library metadata up to Kotlin 2.1:
  `kotlinx-serialization-json` uses the same version as `-core` (1.7.3). `firebase-auth` 24.1.0+ (BoM 34.13.0+) is compiled with Kotlin 2.3 metadata and fails to build
  ("incompatible version of Kotlin ... metadata is 2.3.0"), so upgrading Firebase requires upgrading Kotlin first. Only declare dependencies that code actually uses (`androidx.test:runner` is declared
  explicitly because `AndroidJUnitRunner` needs it).
- Keep pure logic in plain Kotlin and unit-test it: validators, `groupTrips`, `tripStatus`, `TripFormatting`,
  `PickerDates`, `Money`, `BudgetCalculations`,
  `CurrencyConversion` (incl. digit-by-digit typing tests), `CurrencyPicker`, `FrankfurterJson`, `isStale`, `firstName`/`initials`/`greetingName`, `TripColor`, and every
  ViewModel (with `FakeTripRepository`, `FakeExpenseRepository`, `FakeCurrencyRepository` and `FakeAuthRepository` in
  `app/src/test`; set `nextSignInError`, `FakeCurrencyRepository.offline` etc. to simulate backend failures).
  `TripDaoTest` in `app/src/androidTest` covers Room (owner scoping, updates, currency, rate cache, expenses,
  cascade).
