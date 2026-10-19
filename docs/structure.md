# Source tree

Full file map (CLAUDE.md has the short version).

```
app/src/main/java/com/example/planyourtrip/
  MainActivity.kt              # PlanYourTripTheme { AppNavHost() }; attachBaseContext → withAppLocale()
  PhotoImageLoader.kt          # photoImageLoader(): Coil's app-wide loader (User-Agent, 50 MB disk cache)
  AppLocaleSetup.kt            # Context.withAppLocale() (English + LTR resources), applyDefaultAppLocale()
  AppLog.kt                    # the app's only logger: logcat in debug builds, nothing in release (LogConfig.kt)
  PlanYourTripApplication.kt   # applyDefaultAppLocale() (also on config change), installAppCheck(), creates the
                               # AppContainer, syncManager.start(); SingletonImageLoader.Factory (photoImageLoader);
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
                               # TextLimits (+ limitTo: the longest synced text per field, matching firestore.rules),
                               # DeletedActivity (+ DeletedActivityStore: the "Undo" hand-over),
                               # ChecklistKind + ChecklistItem (+ DeletedChecklistItem), ChecklistItemEntity, ChecklistDao,
                               # ChecklistRepository + RoomChecklistRepository,
                               # Travelers (LEGACY_ME_KEY = "me", Traveler, Settlement, RemoveTravelerResult), TravelerColor,
                               # TravelerEntity/TravelerDao, SettlementEntity/SettlementDao, TravelerRepository +
                               # SettlementRepository (+ RoomTravelerRepository, RoomSettlementRepository),
                               # TravelerMerge (TravelerMergeInput/Changes/Plan, MergeBlock, MergeTravelerResult),
                               # MergeSuggestionStore (+ SharedPreferences…, InMemory…: "Keep separate" answers),
                               # TripMember (+ TripMemberEntity, TripMemberDao, TripMemberRepository + Room…: the cloud's
                               # member documents, read-only, written by the full sync),
                               # TripPlace (+ normalized(), Trip.place: the picked country code + coordinates),
                               # Converters, AppDatabase (v16), Migrations (MIGRATION_6_7, MIGRATION_10_11, MIGRATION_13_14)
  data/document/               # Prep tab documents (this device only): DocumentType, TripDocument, DocumentInput, ViewFile,
                               # DocumentEntity, DocumentDao, DocumentRepository + RoomDocumentRepository (also the sync's
                               # TripCleanup), DocumentFileStore (interface) + AndroidDocumentFileStore (the only code that
                               # reads picked files / touches stored ones), DocumentFiles (pure: checkPickedFile,
                               # MAX_DOCUMENT_BYTES = 20 MB, documentKind, extensionFor, defaultDocumentName)
  data/currency/               # Currency, ExchangeRate, ILS, ExchangeRateEntity/Dao (rate cache),
                               # CurrencyRepository (+ isStale, RATE_MAX_AGE) + RoomCurrencyRepository,
                               # RatesApi + FrankfurterRatesApi (the only rates HTTP code, v2, on HttpGet), FrankfurterJson
                               # (pure parsers) + TravelCurrencies
  data/assistant/              # AssistantRepository (interface; AssistantRequest, ChatTurn, AssistantResponse,
                               # AssistantError), FirebaseAssistantRepository (the only Firebase AI code + JSON schema),
                               # AssistantPrompt (pure: AssistantModel, assistantInstructions, classifyServerMessage),
                               # AssistantJson (pure: parseReply → AssistantReply / SuggestedPlan / SuggestedActivity)
  data/sync/                   # cloud sync: SyncModels (pure: SyncKind, SyncItem, ItemData, LocalItem, SyncStatus),
                               # SyncMerge (pure: planMerge → MergePlan), SyncDocuments (pure: Firestore fields),
                               # LegacyMigration (pure: convertLegacy, planLegacyCopy, isLegacyCopyComplete),
                               # CloudStore (interface) + FirestoreCloudStore (the only Firestore code),
                               # SyncPaths (pure: the Firestore document of every kind, used by FirestoreCloudStore and
                               # FakeCloudStore),
                               # SyncLocalStore (interface) + RoomSyncLocalStore + SyncDao, DeletionEntity (+ ChildRef),
                               # NetworkMonitor + AndroidNetworkMonitor, ProcessForeground (processInForeground: app in
                               # the foreground, ProcessLifecycleOwner), SyncController (+ SignOutResult,
                               # LocalChangeListener, TripCleanup), SyncManager, SyncFlags + SharedPreferencesSyncFlags
                               # (per account: the legacy copy's move to shared trips, FullSyncRecord = the last full
                               # sync's result and the time of the last success),
                               # SharedTripDocuments (paths/field names of members, the invite pointer and invites),
                               # TripAccess (+ tripsToCheck, decideAccess: pure "which trips did I lose")
  data/sharing/                # invites and joining (Firestore only, nothing in Room): InviteCode (pure: alphabet,
                               # SecureRandom generate, normalize, extract from pasted text, display, expiry), Invites
                               # (Invite, InvitePreview, JoinResult, InviteUnavailableException, TripFullException),
                               # InviteRepository (interface) + FirestoreInviteRepository, MemberName (memberName():
                               # display name, never the email)
  data/http/                   # HttpGet (interface: one GET → body, IOException on failure), OkHttpGet (the only HTTP
                               # client code: OkHttp, cancelling the coroutine cancels the call, 10 s timeouts),
                               # appUserAgent (sent with every request and photo download)
  data/place/                  # destination suggestions (nothing in Room): PlaceSuggestion (+ label, detail,
                               # withoutRepeats, toTripPlace, RegionKind), PlaceSearch (interface) + PhotonPlaceSearch (the only
                               # Photon HTTP code), PhotonJson (pure parser + type allowlist), HebrewPlaces + EnglishPlaces
                               # (pure matching) + HebrewPlaceTable (GENERATED data), DestinationSuggestions (instant,
                               # remote, merge; + countryCurrency)
  data/photo/                  # destination photos (this device only, nothing synced): DestinationPhotos (PhotoQuery,
                               # photoKey, Trip.photoQuery, DestinationPhoto, PhotoResult, PhotoOrigin,
                               # DestinationPhotoSource = the one provider interface), PhotoRules (pure:
                               # filters, distance, choosePage, bestPageImage), WikipediaJson (pure parsers),
                               # WikipediaPhotoSource (on HttpGet: the only Wikipedia/Wikidata HTTP
                               # code), DestinationPhotoEntity/Dao, DestinationPhotoRepository + RoomDestinationPhoto…,
                               # PhotoRetryFlags (+ SharedPreferences…: the one-time "no photo" clear)
  data/reminder/               # trip reminders (this device only, nothing synced): TripReminders (pure: reminderTime,
                               # reminderDeadline, planReminders, isDue, shownKey, reminderChanges, reminderMessage,
                               # shouldShowReminder, reminderDestination), ReminderSettings (+ SharedPreferences…:
                               # switch, permission asked, scheduled map, shown keys, battery tip shown),
                               # ReminderDelivery (+ ReminderPoster, DeliveryResult, REMINDER_LOG_TAG: shows each
                               # reminder at most once), ReminderWork (interface) + AndroidReminderWork (the only
                               # AlarmManager/WorkManager code), TripReminderAlarmReceiver, ReminderRestoreReceiver
                               # (restart/update), TripReminderScheduler, TripReminderWorker (the backup),
                               # TripReminderNotifier (the Android ReminderPoster, + ReminderLink), ReminderTrips +
                               # RoomReminderTrips, ReminderPrompt + ReminderPermissionPrompt (+ ReminderSetupRequest),
                               # BatteryTip (pure: LIMITING_BRANDS, shouldOfferBatteryTip, isSamsung),
                               # LastExit (exitKind + lastExitLine pure, readLastExit: the previous process's exit)
  data/user/                   # CurrentUser (AuthUser.greetingName()), UserNames (firstName()/initials())
  ui/navigation/               # Routes.kt (type-safe routes), AppNavHost.kt, SessionViewModel
  ui/auth/                     # AuthScreen (AuthScreen + AuthContent: scaffold, hero, sheet), AuthForm (the fields,
                               # button and mode link; AuthFormFocus: focus on errors, scrolling above the keyboard),
                               # AuthHero (photo, gradient, app icon, status bar scrim and icons), AuthFields
                               # (AuthTextField, strength bar, AuthButton, SwitchModeLine, error texts), AuthPreviews,
                               # AuthViewModel, AuthUiState, AuthValidator (+ passwordStrength)
  ui/home/                     # HomeScreen, HomeTripList (tripSections: Next / Upcoming / Past cards), HomeMessages
                               # (snackbars), HomePreviews, HomeViewModel, HomeUiState, TripGrouping,
                               # join/ (JoinTripViewModel, JoinUiState (+ CodeLookup, JoinError), JoinTripSheet,
                               # JoinTripParts (header, code boxes, lookup status), JoiningCard, JoinTripPreviews),
                               # components/ (NextTripCard, TripCards: upcoming + past list cards,
                               # SwipeToDeleteBox: swipe left → red "Delete" area)
  ui/tripform/                 # TripFormScreen/ViewModel/UiState, TripValidator, DestinationSuggestionList — new trip
                               # AND edit trip
  ui/tripdetails/              # the trip area: TripDetailsScreen (wiring + TripDetailsContent: scaffold, tabs),
                               # TripDetailsActions (remember…Actions), TripDetailsEffects (snackbars, Google Maps
                               # links), TripDetailsOverlays (sheets and dialogs), TripDetailsChrome (nav bar, FABs,
                               # status bar shade, "not found"), TripDetailsCommon (EmptyMessage, cardPadding), OverviewTab / BudgetTab (+ BudgetActions, AddExpenseFab) / PlanTab (the
                               # tabs' LazyListScope builders), TripDetailsPreviews (all previews), TripDetailsViewModel
                               # (wires the parts below and delegates to them), TripData (observeTripData: the trip,
                               # expenses, travelers, members and activities observed once and shared), TripBudget
                               # (budgetPart + BudgetCache: the budget numbers, reused when only a day changes;
                               # expenseSplits: each row's ExpenseSplitInfo), BudgetDialogController (+ validateBudget),
                               # ItinerarySelection (Plan/Overview days), CurrencyCardController (card + picker),
                               # HeaderPhoto (headerPhoto),
                               # TripDetailsUiState (+ TripTab, BudgetDialogState, CurrencyCardState, ItineraryState,
                               # PlanDayState, OverviewDayState),
                               # BudgetCalculations (pure), CurrencyConversion (pure: converter math + typing reducer),
                               # Itinerary (pure: planned days, default day, titles, daily cost),
                               # GoogleMaps (pure: googleMapsSearchUrl = the Overview Map card's link,
                               # googleMapsActivityUrl = a Plan card's "Open in Maps"),
                               # components/ (TripHeader, BudgetCard,
                               # CurrencyCard (+ CurrencyConverter: the two amount fields), CategoryChartCard,
                               # BudgetDialog, FeatureCard,
                               # IconChip/CategoryIcon, TravelerAvatar (+ avatarColors, personName),
                               # ExpenseRow (+ ExpenseSplitInfo), ActivityTimeline (TimelineRow, ActivityCard,
                               # CompactActivityCard, TagPill), ItineraryComponents (ItineraryActions, ItineraryHeader,
                               # DayChipsRow, DaySummaryCard, AddActivityButton, PlanTimeline, DailyPlanProgress),
                               # DayPreviewCard (the Overview day section), OverviewMapIllustration (the Map card's drawn map + pill)),
                               # prep/ (PrepViewModel, PrepUiState (+ ChecklistState, DocumentFormState, PrepMessage),
                               # PrepProgress (pure: prepProgress, listCount, progressMessage, dueState, itemsOf,
                               # suggestionsToAdd), PrepTab (prepTab, PrepActions, PrepProgressCard),
                               # DocumentComponents (cards, DocumentType icons/labels), DocumentDialogs (form + delete),
                               # ChecklistComponents (chips, "Add item…", edit-text dialog), ChecklistRow (row, its
                               # long-press menu, due date, check circle),
                               # PrepHost (rememberPrepActions: file picker, viewer, snackbars; PrepDialogs)),
                               # members/ (MembersState (pure: membersState, MemberEntry), MembersCard (+ MemberActions,
                               # InviteButton, OwnerPill), InviteViewModel, InviteUiState (+ MembersSheetMode,
                               # InviteCodeState, InviteMessage), InviteSheet (InviteHost, InviteSheetContent), InviteCodeSection
                               # (code card, share intent), MembersList (member rows, remove dialog),
                               # InviteSheetPreviews),
                               # split/ (SplitViewModel, SettleAmountValidation (validateSettleAmount), SplitUiState (+
                               # TravelerDialogState, SettleSheetState, SettlePage, SettleAmountError), SplitComponents
                               # (SplitActions, TravelersSection, MergeSuggestionCard), WhoOwesWhom (WhoOwesWhomCard,
                               # TransferRow), SplitDialogs (TravelerDialog, delete-payment dialog), SettleUpSheet),
                               # expense/ (ExpenseFormScreen/ViewModel/UiState (+ ForeignRateState), ExpenseAmountField
                               # (amount, currency switch, conversion line), ExpenseSplitFields, ExpenseFormPreviews,
                               # ExpenseValidator,
                               # ExpenseCurrency (pure: per-currency parsing, conversion to agorot, save rule)),
                               # activity/ (ActivityFormScreen/ViewModel/UiState, ActivityValidator),
                               # assistant/ (AssistantScreen, AssistantMessages (bubbles, typing, error), AssistantInputBar,
                               # AssistantPreviews, AssistantViewModel, AssistantUiState (+ ChatMessage,
                               # PlanCardState, AddStatus), SuggestedPlanCard, AssistantContext (pure: buildTripContext,
                               # AssistantChip + suggestionChips, chatHistory, planCard), Suggestions (pure:
                               # suggestionInputs))
  ui/common/                   # code shared by several features:
                               #   AppLocale (the one app locale, Locale.US, default of every formatter),
                               #   Flags (pure: countryFlag, currencyFlag = the one currency → flag table,
                               #   CURRENCIES_WITHOUT_FLAG),
                               #   UiSharing (WhileUiSubscribed: every ViewModel's stateIn policy, 5 s),
                               #   TripFormatting (dates, cached formatters), TripTexts (datesWithLength,
                               #   statusLabel), TripStatus (tripStatus()), Money (agorot format/parse, percent,
                               #   shekels()), PickerDates (DatePicker UTC conversion), ErrorText (errorSupportingText),
                               #   CurrencyPicker (CurrencyPickerState, filterCurrencies, currencyName),
                               #   SavedFormState (a form's state + its draft in SavedStateHandle),
                               #   TripDays (pure: tripDays, daysInclusive, dayNumber, isWithinTrip, sortActivities,
                               #   activitiesOn),
                               #   CategoryTotals (pure: categoryTotals(amountOf, includeZeroCategories) + CategoryTotal),
                               #   ExpenseCategoryUi / ActivityTypeUi (iconRes()/labelRes()),
                               #   NotificationPermissionHost (+ rememberNotificationPermissionRequest)
  ui/common/split/             # expense splitting, pure: Split (travelerOrder, inListOrder, shares, myShare, myTotal,
                               #   isShared, sharedExpenseCount, balances, simplifyTransfers + Transfer,
                               #   isTravelerUsed), TravelerMerging (isSameName, membersNamed, mergeSuggestions +
                               #   MergeSuggestion, planTravelerMerge), People (Person: You / Named (+ nameOnly) /
                               #   Removed, people, personOf,
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
                               #   CurrencyPickerSheet (searchable currency list with flags, trip form + trip screen),
                               #   FlagEmoji (+ CurrencyCodeLabel "🇪🇺 EUR"), DestinationTitle (flag + destination),
                               #   TimeField (read-only time field + 24-hour TimePicker dialog; activity form),
                               #   EditFormScaffold (+ FormDelete: the expense/activity form shell: top bar with delete,
                               #   loading gate, scrolling fields, Save), ConfirmDeleteDialog (title, body, red Delete,
                               #   Cancel), TintedFilterChip (category/type chip in its TintedColor),
                               #   RefreshBox (PullToRefreshBox with the spinner below the status bar),
                               #   DestinationPhoto (HeroPhotoState, HeroPhoto, drawHeroPhotoScrim, PhotoSquare: the only
                               #   Coil composables)
  ui/theme/                    # Color, ExtendedColors, Shape, Type (Inter + named styles), Dimens, Theme,
                               # HeroPhotoScrim (the photo scrim's alpha levels),
                               # PdfColors (ARGB ints for the shared PDF, built from Color/ExtendedColors),
                               # NotificationColors (the reminder's brand teal as an ARGB int)
app/src/debug/.../AppCheckSetup.kt    # installAppCheck(): App Check debug provider
app/src/release/.../AppCheckSetup.kt  # installAppCheck(): Play Integrity provider
app/src/debug/.../LogConfig.kt        # LOGGING_ENABLED = true (AppLog writes to logcat)
app/src/release/.../LogConfig.kt      # LOGGING_ENABLED = false (release builds log nothing)
app/src/debug/AndroidManifest.xml + .../DebugReminderReceiver.kt  # debug-only "fire the reminders now" (see Commands)
app/schemas/                   # exported Room schemas — commit them
design/home_screen.png         # visual reference for the home screen and the app theme
design/trip_overview.png       # visual reference for the trip screen (Overview tab)
design/trip_plan.png           # visual reference for the Plan tab (see "Plan tab" for the deliberate differences)
design/trip_prep.png           # visual reference for the Prep tab (see "Prep tab" for the deliberate differences)
design/trip_budget.png         # visual reference for the Budget tab (see "Budget tab and expense splitting")
design/trip_assistant.png      # visual reference for the trip assistant (see "AI trip assistant")
design/sign_in.png, sign_up.png  # visual references for Sign In / Sign Up (see "Auth screen behavior")
design/hero_sign_in.jpg, hero_sign_up.jpg  # full-size sources of the auth hero photos (res/drawable-nodpi/auth_hero_*.webp)
third_party/inter/OFL.txt      # Inter font license
firestore.rules                # Firestore security rules (paste into the console)
firebase.json                  # Firebase Emulator config (rules tests only; nothing is deployed from it)
rules-tests/                   # Firestore rules tests: rules.test.js (mocha + @firebase/rules-unit-testing), package.json
design/trip_members.png        # stage 2 reference (built): the Overview's "Traveling together" card (only that card)
design/trip_invite.png         # stage 2 reference: the invite sheet
design/home_join.png           # stage 2 reference: the join sheet
tools/hebrew-places/           # generator of HebrewPlaceTable.kt (source lists + scripts, README.md: how to regenerate)
app/src/main/res/xml/file_paths.xml  # FileProvider paths: cache/shared_pdfs/ and files/documents/
```

